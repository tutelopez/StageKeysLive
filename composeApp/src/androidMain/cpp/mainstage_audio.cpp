#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <map>
#include <string>
#include <thread>
#include <chrono>
#include <vector>
#include <atomic>

#define LOG_TAG "StageKeysAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

#include <fluidsynth.h>
#include <android/asset_manager_jni.h>
#include <oboe/Oboe.h>
#include "pad_engine.h"

#define NUM_PHYSICAL_CHANNELS 32
#define NUM_LOGICAL_CHANNELS 8
#define GLOBAL_POLYPHONY 64
#define SHADOW_SYSTEM_ENABLED 1
#define PREVIEW_CHANNEL (NUM_PHYSICAL_CHANNELS - 1)

class MainstageAudioEngine : public oboe::AudioStreamDataCallback {
private:
    std::mutex synthMutex;
    
    // Internal synth settings per channel
    int sfids[NUM_PHYSICAL_CHANNELS];
    int currentPrograms[NUM_PHYSICAL_CHANNELS];
    
    std::map<std::string, int> loadedSfPaths;
    std::map<int, int> sfidRefCount;

    int activeNotes[NUM_PHYSICAL_CHANNELS]; 
    int physicalActive[NUM_LOGICAL_CHANNELS]; 
    std::vector<int> shadowChannelsOf[NUM_LOGICAL_CHANNELS];
    bool physicalInUse[NUM_PHYSICAL_CHANNELS];
    long long shadowTimestamp[NUM_PHYSICAL_CHANNELS];
    float masterVolume = 0.8f;
    float reverbMix = 0.3f;
    float filterCutoff = 0.5f;
    bool audioReady = false;

    // Scratch/Preview channel state (reserved on physical channel PREVIEW_CHANNEL)
    int previewSfid = -1;
    std::atomic<int> previewGeneration{0};

    // Master Bus Limiter
    std::atomic<bool> limiterEnabled{true};
    std::atomic<bool> limiterTriggered{false};

    fluid_settings_t* fluidSettings = nullptr;
    fluid_synth_t* fluidSynth = nullptr;
    std::shared_ptr<oboe::AudioStream> mStream;
    
    std::atomic<double> smoothedDspCpuLoad{0.0};
    std::atomic<double> peakDspCpuLoad{0.0};
    double actualSampleRate = 48000.0;
    std::string actualSharingMode = "Shared";
    int actualBufferFrames = 256;

    void applyLimiterInterleaved(float* buf, int len) {
        const float threshold = 0.85f;
        const float invScale = 1.0f / (1.0f - threshold);
        bool triggered = false;
        int totalSamples = len * 2;

        for (int i = 0; i < totalSamples; i++) {
            float s = buf[i];
            if (s > threshold) {
                buf[i] = threshold + (1.0f - threshold) * std::tanh((s - threshold) * invScale);
                triggered = true;
            } else if (s < -threshold) {
                buf[i] = -threshold + (1.0f - threshold) * std::tanh((s + threshold) * invScale);
                triggered = true;
            }
        }
        if (triggered) {
            limiterTriggered.store(true, std::memory_order_relaxed);
        }
    }

public:
    MainstageAudioEngine() {
        for (int i = 0; i < NUM_PHYSICAL_CHANNELS; i++) {
            sfids[i] = -1;
            currentPrograms[i] = 0;
            activeNotes[i] = 0;
            physicalInUse[i] = false;
            shadowTimestamp[i] = 0;
        }
        for (int i = 0; i < NUM_LOGICAL_CHANNELS; i++) {
            physicalActive[i] = i; // Map logical channel i to physical channel i initially
            physicalInUse[i] = true;
        }
        // Reserve last channel for preview
        physicalInUse[PREVIEW_CHANNEL] = true;
    }

    ~MainstageAudioEngine() {
        stop();
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) override {
        auto t0 = std::chrono::high_resolution_clock::now();

        float* outBuffer = static_cast<float*>(audioData);
        if (fluidSynth != nullptr) {
            fluid_synth_write_float(fluidSynth, numFrames, outBuffer, 0, 2, outBuffer, 1, 2);
        } else {
            memset(outBuffer, 0, numFrames * 2 * sizeof(float));
        }

        if (isLimiterEnabled()) {
            applyLimiterInterleaved(outBuffer, numFrames);
        }

        auto t1 = std::chrono::high_resolution_clock::now();
        double elapsedSec = std::chrono::duration<double>(t1 - t0).count();
        double sr = (actualSampleRate > 0.0) ? actualSampleRate : 48000.0;
        double budgetSec = (double)numFrames / sr;
        if (budgetSec > 0.0) {
            double instantCpu = (elapsedSec / budgetSec) * 100.0;
            double prev = smoothedDspCpuLoad.load(std::memory_order_relaxed);
            double smoothed = (prev == 0.0) ? instantCpu : ((prev * 0.90) + (instantCpu * 0.10));
            smoothedDspCpuLoad.store(smoothed, std::memory_order_relaxed);
            
            double curPeak = peakDspCpuLoad.load(std::memory_order_relaxed);
            if (instantCpu > curPeak) {
                peakDspCpuLoad.store(instantCpu, std::memory_order_relaxed);
            }
        }

        return oboe::DataCallbackResult::Continue;
    }

    bool isAudioReady() const { return audioReady; }

    bool isLimiterEnabled() const { return limiterEnabled.load(std::memory_order_relaxed); }
    void setLimiterEnabled(bool enabled) { limiterEnabled.store(enabled, std::memory_order_relaxed); }
    bool isLimiterActive() { return limiterTriggered.exchange(false, std::memory_order_relaxed); }

    int getXRunCount() {
        if (mStream) {
            auto res = mStream->getXRunCount();
            if (res) {
                return res.value();
            }
        }
        return 0;
    }

    double getDspCpuLoad() {
        return smoothedDspCpuLoad.load(std::memory_order_relaxed);
    }

    double getPeakDspCpuLoad() {
        return peakDspCpuLoad.load(std::memory_order_relaxed);
    }

    void resetPeakDspCpuLoad() {
        peakDspCpuLoad.store(0.0, std::memory_order_relaxed);
    }

    void init(int sampleRate, int bufferFrames, bool isUsbDevice = false, int deviceId = -1) {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;
        actualSampleRate = (sampleRate > 0) ? (double)sampleRate : 48000.0;
        smoothedDspCpuLoad.store(0.0);
        peakDspCpuLoad.store(0.0);

        fluidSettings = new_fluid_settings();
        
        fluid_settings_setstr(fluidSettings, "synth.reverb.active", "yes");
        fluid_settings_setstr(fluidSettings, "synth.chorus.active", "yes");
        fluid_settings_setnum(fluidSettings, "synth.sample-rate", actualSampleRate);
        fluid_settings_setint(fluidSettings, "synth.polyphony", GLOBAL_POLYPHONY);
        fluid_settings_setint(fluidSettings, "synth.midi-channels", NUM_PHYSICAL_CHANNELS);
        
        fluidSynth = new_fluid_synth(fluidSettings);
        if (fluidSynth == nullptr) {
            LOGE("FluidSynth: failed to create synth instance");
            return;
        } 
        LOGI("FluidSynth: synth instance created OK (%d midi-channels, %d polyphony)", NUM_PHYSICAL_CHANNELS, GLOBAL_POLYPHONY);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setFormat(oboe::AudioFormat::Float);
        builder.setChannelCount(2); // Stereo
        builder.setSampleRate((int)actualSampleRate);
        if (deviceId != 0 && deviceId != -1) {
            LOGI("MainstageAudioEngine: Routing to audio deviceId=%d", deviceId);
            builder.setDeviceId(deviceId);
        }
        if (bufferFrames > 0) {
            builder.setFramesPerDataCallback(bufferFrames);
        }
        builder.setDataCallback(this);

        oboe::Result result = oboe::Result::ErrorInternal;
        if (isUsbDevice) {
            LOGI("MainstageAudioEngine: USB device detected, trying Exclusive mode");
            builder.setSharingMode(oboe::SharingMode::Exclusive);
            result = builder.openStream(mStream);
            if (result != oboe::Result::OK) {
                LOGW("MainstageAudioEngine: Exclusive mode failed (%s). Falling back to Shared mode.", oboe::convertToText(result));
                builder.setSharingMode(oboe::SharingMode::Shared);
                result = builder.openStream(mStream);
                actualSharingMode = "Shared";
            } else {
                actualSharingMode = "Exclusive";
                LOGI("MainstageAudioEngine: Opened stream in Exclusive mode OK");
            }
        } else {
            actualSharingMode = "Shared";
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(mStream);
        }

        if (result != oboe::Result::OK || !mStream) {
            LOGE("MainstageAudioEngine: Failed to open Oboe stream. Error: %s", oboe::convertToText(result));
            return;
        }

        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("MainstageAudioEngine: Failed to start Oboe stream. Error: %s", oboe::convertToText(result));
            return;
        }

        audioReady = true;
        actualBufferFrames = mStream->getFramesPerBurst();
        actualSampleRate = mStream->getSampleRate();
        LOGI("MainstageAudioEngine: Oboe stream started successfully (SR=%.0f, Burst=%d, Sharing=%s, DeviceId=%d)",
             actualSampleRate, actualBufferFrames, actualSharingMode.c_str(), mStream->getDeviceId());
    }

    void stop() {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;

        // Reset sfids before destroying the synth to avoid orphaned IDs on restart
        for (int i = 0; i < NUM_PHYSICAL_CHANNELS; i++) {
            sfids[i] = -1;
        }

        if (mStream) {
            mStream->stop();
            mStream->close();
            mStream.reset();
        }
        if (fluidSynth != nullptr) {
            delete_fluid_synth(fluidSynth);
            fluidSynth = nullptr;
        }
        if (fluidSettings != nullptr) {
            delete_fluid_settings(fluidSettings);
            fluidSettings = nullptr;
        }
    }

    bool loadSoundFont(const char* sf2Path, int logicalChannel) {
        std::string path(sf2Path);
        int newSfid = -1;
        int targetPhys = -1;

        {
            std::lock_guard<std::mutex> lock(synthMutex);
            if (fluidSynth == nullptr || logicalChannel < 0 || logicalChannel >= NUM_LOGICAL_CHANNELS) {
                return false;
            }
            
#if SHADOW_SYSTEM_ENABLED
            int oldPhys = physicalActive[logicalChannel];
            
            // Ping-pong if there are active notes on current physical channel
            if (activeNotes[oldPhys] > 0) {
                // Strict Limit: Before assigning a new shadow channel, evict any existing shadow for this logical channel
                while (shadowChannelsOf[logicalChannel].size() >= 1) {
                    int existingShadow = shadowChannelsOf[logicalChannel].front();
                    shadowChannelsOf[logicalChannel].erase(shadowChannelsOf[logicalChannel].begin());
                    if (fluidSynth != nullptr) {
                        fluid_synth_all_sounds_off(fluidSynth, existingShadow);
                    }
                    activeNotes[existingShadow] = 0;
                    physicalInUse[existingShadow] = false;

                    int sfidToRelease = sfids[existingShadow];
                    if (sfidToRelease != -1) {
                        sfidRefCount[sfidToRelease]--;
                        if (sfidRefCount[sfidToRelease] <= 0) {
                            if (fluidSynth != nullptr) {
                                fluid_synth_sfunload(fluidSynth, sfidToRelease, 0);
                            }
                            sfidRefCount.erase(sfidToRelease);
                            for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                                if (it->second == sfidToRelease) it = loadedSfPaths.erase(it);
                                else ++it;
                            }
                        }
                        sfids[existingShadow] = -1;
                    }
                    LOGI("FluidSynth: Evicted existing shadow channel %d for logical %d (Strict 1-shadow limit)", existingShadow, logicalChannel);
                }

                targetPhys = -1;
                
                // 1. Search for a free physical channel (reserve PREVIEW_CHANNEL for preview)
                for (int i = 0; i < PREVIEW_CHANNEL; i++) {
                    if (!physicalInUse[i]) {
                        targetPhys = i;
                        break;
                    }
                }
                
                // 2. If no free channel, evict the oldest shadow across all logical channels
                if (targetPhys == -1) {
                    long long oldestTime = -1;
                    int oldestPhys = -1;
                    int oldestLogicalOwner = -1;
                    
                    for (int l = 0; l < NUM_LOGICAL_CHANNELS; l++) {
                        for (int s : shadowChannelsOf[l]) {
                            if (oldestTime == -1 || shadowTimestamp[s] < oldestTime) {
                                oldestTime = shadowTimestamp[s];
                                oldestPhys = s;
                                oldestLogicalOwner = l;
                            }
                        }
                    }
                    
                    if (oldestPhys != -1) {
                        LOGW("FluidSynth: Canales físicos agotados. Cortando sombra %d del lógico %d", oldestPhys, oldestLogicalOwner);
                        if (fluidSynth != nullptr) {
                            fluid_synth_all_sounds_off(fluidSynth, oldestPhys);
                        }
                        activeNotes[oldestPhys] = 0;
                        targetPhys = oldestPhys;
                        
                        // Remove from its logical owner's shadow list and unload its SF2 if unused
                        auto& list = shadowChannelsOf[oldestLogicalOwner];
                        for (auto it = list.begin(); it != list.end(); ++it) {
                            if (*it == oldestPhys) {
                                list.erase(it);
                                break;
                            }
                        }
                        int sfidToRelease = sfids[oldestPhys];
                        if (sfidToRelease != -1) {
                            sfidRefCount[sfidToRelease]--;
                            if (sfidRefCount[sfidToRelease] <= 0) {
                                if (fluidSynth != nullptr) {
                                    fluid_synth_sfunload(fluidSynth, sfidToRelease, 0);
                                }
                                sfidRefCount.erase(sfidToRelease);
                                for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                                    if (it->second == sfidToRelease) it = loadedSfPaths.erase(it);
                                    else ++it;
                                }
                            }
                            sfids[oldestPhys] = -1;
                        }
                    } else {
                        // Fallback if no shadow channel available
                        targetPhys = oldPhys;
                    }
                }
                
                // Register the old physical channel as a shadow
                shadowChannelsOf[logicalChannel].push_back(oldPhys);
                auto now = std::chrono::steady_clock::now().time_since_epoch();
                shadowTimestamp[oldPhys] = std::chrono::duration_cast<std::chrono::milliseconds>(now).count();
                
                physicalActive[logicalChannel] = targetPhys;
                physicalInUse[targetPhys] = true;
                LOGI("FluidSynth: Promoted channel %d to active (shadow=%d) for logical %d", targetPhys, oldPhys, logicalChannel);
            } else {
                targetPhys = oldPhys;
            }
#else
            targetPhys = logicalChannel;
#endif

            int oldTargetSfid = sfids[targetPhys];
            
            if (loadedSfPaths.find(path) != loadedSfPaths.end()) {
                newSfid = loadedSfPaths[path];
                LOGI("FluidSynth: Reusing sfid %d for %s on channel %d", newSfid, sf2Path, targetPhys);
            } else {
                newSfid = fluid_synth_sfload(fluidSynth, sf2Path, 0);
                if (newSfid != -1) {
                    loadedSfPaths[path] = newSfid;
                    sfidRefCount[newSfid] = 0;
                    LOGI("FluidSynth: Loaded new SF2 - sfid=%d for path=%s", newSfid, sf2Path);
                }
            }

            if (newSfid != -1) {
                sfids[targetPhys] = newSfid;
                sfidRefCount[newSfid]++;
                fluid_synth_program_select(fluidSynth, targetPhys, newSfid, 0, currentPrograms[logicalChannel]);
            } else {
                LOGE("FluidSynth: fluid_synth_sfload FAILED for path: %s", sf2Path);
                return false;
            }

            if (oldTargetSfid != -1 && oldTargetSfid != newSfid) {
                sfidRefCount[oldTargetSfid]--;
                if (sfidRefCount[oldTargetSfid] <= 0) {
                    fluid_synth_sfunload(fluidSynth, oldTargetSfid, 0);
                    sfidRefCount.erase(oldTargetSfid);
                    for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                        if (it->second == oldTargetSfid) it = loadedSfPaths.erase(it);
                        else ++it;
                    }
                }
            }
        }
        return newSfid != -1;
    }

    void noteOn(int note, int velocity, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            int targetPhys = physicalActive[logicalChannel];
            fluid_synth_noteon(fluidSynth, targetPhys, note, velocity);
            activeNotes[targetPhys]++;
        }
    }

    void noteOff(int note, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            int activePhys = physicalActive[logicalChannel];
            fluid_synth_noteoff(fluidSynth, activePhys, note);
            if (activeNotes[activePhys] > 0) activeNotes[activePhys]--;
            
#if SHADOW_SYSTEM_ENABLED
            for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                fluid_synth_noteoff(fluidSynth, shadowPhys, note);
                if (activeNotes[shadowPhys] > 0) activeNotes[shadowPhys]--;
            }
#endif
        }
    }

    void setVolume(float volume) {
        std::lock_guard<std::mutex> lock(synthMutex);
        masterVolume = volume;
        if (fluidSynth != nullptr) {
            fluid_synth_set_gain(fluidSynth, volume);
        }
    }
    
    void setChannelVolume(float volume, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
             fluid_synth_cc(fluidSynth, physicalActive[logicalChannel], 7, (int)(volume * 127.0f));
#if SHADOW_SYSTEM_ENABLED
             for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                 fluid_synth_cc(fluidSynth, shadowPhys, 7, (int)(volume * 127.0f));
             }
#endif
        }
    }

    void setPan(int channel, float panValue) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr) {
            int panCc = (int)(panValue * 127.0f);
            if (panCc < 0) panCc = 0;
            if (panCc > 127) panCc = 127;
            if (channel >= 0 && channel < NUM_LOGICAL_CHANNELS) {
                fluid_synth_cc(fluidSynth, physicalActive[channel], 10, panCc);
#if SHADOW_SYSTEM_ENABLED
                for (int shadowPhys : shadowChannelsOf[channel]) {
                    fluid_synth_cc(fluidSynth, shadowPhys, 10, panCc);
                }
#endif
            } else if (channel >= 0 && channel < NUM_PHYSICAL_CHANNELS) {
                fluid_synth_cc(fluidSynth, channel, 10, panCc);
            }
        }
    }

    void setReverb(float reverb) {
        std::lock_guard<std::mutex> lock(synthMutex);
        reverbMix = reverb;
        if (fluidSynth != nullptr) {
            fluid_synth_set_reverb_group_roomsize(fluidSynth, -1, 0.7);
            fluid_synth_set_reverb_group_damp(fluidSynth, -1, 0.5);
            fluid_synth_set_reverb_group_width(fluidSynth, -1, 0.6);
            fluid_synth_set_reverb_group_level(fluidSynth, -1, (double)reverb);
        }
    }

    void setChannelReverbSend(int logicalChannel, float value) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            int ccVal = (int)(value * 127.0f);
            if (ccVal < 0) ccVal = 0;
            if (ccVal > 127) ccVal = 127;
            fluid_synth_cc(fluidSynth, physicalActive[logicalChannel], 91, ccVal);
#if SHADOW_SYSTEM_ENABLED
            for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                fluid_synth_cc(fluidSynth, shadowPhys, 91, ccVal);
            }
#endif
        }
    }

    void setChannelChorusSend(int logicalChannel, float value) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            int ccVal = (int)(value * 127.0f);
            if (ccVal < 0) ccVal = 0;
            if (ccVal > 127) ccVal = 127;
            fluid_synth_cc(fluidSynth, physicalActive[logicalChannel], 93, ccVal);
#if SHADOW_SYSTEM_ENABLED
            for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                fluid_synth_cc(fluidSynth, shadowPhys, 93, ccVal);
            }
#endif
        }
    }

    void setMasterReverbParams(float roomsize, float damp, float width, float level) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr) {
            fluid_synth_set_reverb_group_roomsize(fluidSynth, -1, (double)roomsize);
            fluid_synth_set_reverb_group_damp(fluidSynth, -1, (double)damp);
            fluid_synth_set_reverb_group_width(fluidSynth, -1, (double)width);
            fluid_synth_set_reverb_group_level(fluidSynth, -1, (double)level);
        }
    }

    void setMasterChorusParams(int nr, float level, float speed, float depth) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr) {
            fluid_synth_set_chorus_group_nr(fluidSynth, -1, nr);
            fluid_synth_set_chorus_group_level(fluidSynth, -1, (double)level);
            fluid_synth_set_chorus_group_speed(fluidSynth, -1, (double)speed);
            fluid_synth_set_chorus_group_depth(fluidSynth, -1, (double)depth);
            fluid_synth_set_chorus_group_type(fluidSynth, -1, 0);
        }
    }

    void setFilterCutoff(float cutoff, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        filterCutoff = cutoff;
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            int ccVal = (int)(cutoff * 127.0f);
            if (ccVal < 0) ccVal = 0;
            if (ccVal > 127) ccVal = 127;
            fluid_synth_cc(fluidSynth, physicalActive[logicalChannel], 74, ccVal);
#if SHADOW_SYSTEM_ENABLED
            for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                 fluid_synth_cc(fluidSynth, shadowPhys, 74, ccVal);
            }
#endif
        }
    }

    void setPatch(int programNumber, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            currentPrograms[logicalChannel] = programNumber;
            int targetPhys = physicalActive[logicalChannel];
            if (fluidSynth != nullptr && sfids[targetPhys] != -1) {
                fluid_synth_program_select(fluidSynth, targetPhys, sfids[targetPhys], 0, programNumber);
            }
        }
    }

    void allNotesOff() {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr) {
            for (int i = 0; i < NUM_PHYSICAL_CHANNELS; ++i) {
                fluid_synth_all_notes_off(fluidSynth, i);
                fluid_synth_all_sounds_off(fluidSynth, i);
                activeNotes[i] = 0;
            }
        }
    }

    void setModulation(float value, int logicalChannel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && logicalChannel >= 0 && logicalChannel < NUM_LOGICAL_CHANNELS) {
            fluid_synth_cc(fluidSynth, physicalActive[logicalChannel], 1, (int)(value * 127.0f));
#if SHADOW_SYSTEM_ENABLED
            for (int shadowPhys : shadowChannelsOf[logicalChannel]) {
                 fluid_synth_cc(fluidSynth, shadowPhys, 1, (int)(value * 127.0f));
            }
#endif
        }
    }

    void previewSoundFont(const char* sf2Path, int note, int velocity, int durationMs) {
        int gen = ++previewGeneration;
        std::string path(sf2Path);
        std::thread([this, path, note, velocity, durationMs, gen]() {
            int tempSfid = -1;
            {
                std::lock_guard<std::mutex> lock(synthMutex);
                if (fluidSynth == nullptr) return;

                // Stop previous sound on preview channel PREVIEW_CHANNEL
                fluid_synth_all_notes_off(fluidSynth, PREVIEW_CHANNEL);
                fluid_synth_all_sounds_off(fluidSynth, PREVIEW_CHANNEL);

                // If previous preview loaded an sfid, release it
                if (previewSfid != -1) {
                    int oldId = previewSfid;
                    previewSfid = -1;
                    sfidRefCount[oldId]--;
                    if (sfidRefCount[oldId] <= 0) {
                        fluid_synth_sfunload(fluidSynth, oldId, 0);
                        sfidRefCount.erase(oldId);
                        for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                            if (it->second == oldId) it = loadedSfPaths.erase(it);
                            else ++it;
                        }
                    }
                }

                // Check cache
                if (loadedSfPaths.find(path) != loadedSfPaths.end()) {
                    tempSfid = loadedSfPaths[path];
                    sfidRefCount[tempSfid]++;
                    LOGI("FluidSynth preview: Reusing sfid %d for %s", tempSfid, path.c_str());
                } else {
                    tempSfid = fluid_synth_sfload(fluidSynth, path.c_str(), 0);
                    if (tempSfid != -1) {
                        loadedSfPaths[path] = tempSfid;
                        sfidRefCount[tempSfid] = 1;
                        LOGI("FluidSynth preview: Loaded new sfid %d for %s", tempSfid, path.c_str());
                    }
                }

                if (tempSfid == -1) {
                    LOGE("FluidSynth preview: failed to load %s", path.c_str());
                    return;
                }

                previewSfid = tempSfid;
                sfids[PREVIEW_CHANNEL] = tempSfid;
                fluid_synth_program_select(fluidSynth, PREVIEW_CHANNEL, tempSfid, 0, 0);
                fluid_synth_cc(fluidSynth, PREVIEW_CHANNEL, 7, 100); // Scratch volume
                fluid_synth_cc(fluidSynth, PREVIEW_CHANNEL, 10, 64); // Center pan
                fluid_synth_noteon(fluidSynth, PREVIEW_CHANNEL, note, velocity);
            }

            // Let the note ring for durationMs
            std::this_thread::sleep_for(std::chrono::milliseconds(durationMs));

            if (previewGeneration.load() == gen) {
                {
                    std::lock_guard<std::mutex> lock(synthMutex);
                    if (fluidSynth != nullptr) {
                        fluid_synth_noteoff(fluidSynth, PREVIEW_CHANNEL, note);
                    }
                }

                // Allow natural envelope release
                std::this_thread::sleep_for(std::chrono::milliseconds(600));

                if (previewGeneration.load() == gen) {
                    std::lock_guard<std::mutex> lock(synthMutex);
                    if (fluidSynth != nullptr && previewSfid != -1) {
                        fluid_synth_all_notes_off(fluidSynth, PREVIEW_CHANNEL);
                        fluid_synth_all_sounds_off(fluidSynth, PREVIEW_CHANNEL);
                        sfids[PREVIEW_CHANNEL] = -1;
                        int sId = previewSfid;
                        previewSfid = -1;
                        sfidRefCount[sId]--;
                        if (sfidRefCount[sId] <= 0) {
                            fluid_synth_sfunload(fluidSynth, sId, 0);
                            sfidRefCount.erase(sId);
                            for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                                if (it->second == sId) it = loadedSfPaths.erase(it);
                                else ++it;
                            }
                            LOGI("FluidSynth preview: Unloaded temp sfid %d", sId);
                        }
                    }
                }
            }
        }).detach();
    }

    void stopPreview() {
        previewGeneration++;
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && previewSfid != -1) {
            fluid_synth_all_notes_off(fluidSynth, PREVIEW_CHANNEL);
            fluid_synth_all_sounds_off(fluidSynth, PREVIEW_CHANNEL);
            sfids[PREVIEW_CHANNEL] = -1;
            int sId = previewSfid;
            previewSfid = -1;
            sfidRefCount[sId]--;
            if (sfidRefCount[sId] <= 0) {
                fluid_synth_sfunload(fluidSynth, sId, 0);
                sfidRefCount.erase(sId);
                for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                    if (it->second == sId) it = loadedSfPaths.erase(it);
                    else ++it;
                }
            }
        }
    }

    std::string getAudioDiagnostics() {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (!audioReady || !mStream) {
            return "NO INICIALIZADO";
        }
        
        int xruns = getXRunCount();
        int burst = mStream->getFramesPerBurst();
        int bufSize = mStream->getBufferSizeInFrames();
        
        char buffer[256];
        snprintf(buffer, sizeof(buffer), "Oboe (%s) | SR: %.0f Hz | Buffer: %d/%d frames | xRuns: %d", 
                 actualSharingMode.c_str(), actualSampleRate, burst, bufSize, xruns);
        return std::string(buffer);
    }

    int getActiveVoiceCount() {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr) {
            return fluid_synth_get_active_voice_count(fluidSynth);
        }
        return 0;
    }


    void releaseShadowChannel(int logicalChannel, int physicalChannel = -1) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (logicalChannel < 0 || logicalChannel >= NUM_LOGICAL_CHANNELS) return;

        auto& list = shadowChannelsOf[logicalChannel];
        std::vector<int> toRelease;

        if (physicalChannel == -1) {
            toRelease = list;
            list.clear();
        } else {
            for (auto it = list.begin(); it != list.end(); ) {
                if (*it == physicalChannel) {
                    toRelease.push_back(*it);
                    it = list.erase(it);
                } else {
                    ++it;
                }
            }
        }

        for (int phys : toRelease) {
            if (fluidSynth != nullptr) {
                fluid_synth_all_sounds_off(fluidSynth, phys);
            }
            activeNotes[phys] = 0;
            physicalInUse[phys] = false;

            int sfidToRelease = sfids[phys];
            if (sfidToRelease != -1) {
                sfidRefCount[sfidToRelease]--;
                if (sfidRefCount[sfidToRelease] <= 0) {
                    if (fluidSynth != nullptr) {
                        fluid_synth_sfunload(fluidSynth, sfidToRelease, 0);
                    }
                    sfidRefCount.erase(sfidToRelease);
                    for (auto it = loadedSfPaths.begin(); it != loadedSfPaths.end(); ) {
                        if (it->second == sfidToRelease) it = loadedSfPaths.erase(it);
                        else ++it;
                    }
                }
                sfids[phys] = -1;
            }
            LOGI("FluidSynth: Explicitly released shadow channel %d for logical %d", phys, logicalChannel);
        }
    }

    int getLogicalChannelCount() const {
        return NUM_LOGICAL_CHANNELS;
    }

    int getGlobalPolyphony() const {
        return GLOBAL_POLYPHONY;
    }
};

static MainstageAudioEngine* gEngine = nullptr;
static PadEngine* gPadEngine = nullptr;
static AAssetManager* gAssetManager = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeReleaseShadowChannel(JNIEnv *env, jobject thiz, jint logicalChannel, jint physicalChannel) {
    if (gEngine != nullptr) {
        gEngine->releaseShadowChannel(logicalChannel, physicalChannel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeNoteOn(JNIEnv *env, jobject thiz, jint note, jint velocity, jint channel) {
    if (gEngine != nullptr) {
        gEngine->noteOn(note, velocity, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeNoteOff(JNIEnv *env, jobject thiz, jint note, jint channel) {
    if (gEngine != nullptr) {
        gEngine->noteOff(note, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    if (gEngine != nullptr) {
        gEngine->setVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetChannelVolume(JNIEnv *env, jobject thiz, jfloat volume, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setChannelVolume(volume, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetPan(JNIEnv *env, jobject thiz, jint channel, jfloat pan) {
    if (gEngine != nullptr) {
        gEngine->setPan(channel, pan);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetReverb(JNIEnv *env, jobject thiz, jfloat reverb) {
    if (gEngine != nullptr) {
        gEngine->setReverb(reverb);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetFilterCutoff(JNIEnv *env, jobject thiz, jfloat cutoff, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setFilterCutoff(cutoff, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetPatch(JNIEnv *env, jobject thiz, jint program_number, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setPatch(program_number, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeInit(JNIEnv *env, jobject thiz, jint sampleRate, jint bufferFrames, jboolean isUsbDevice, jint deviceId) {
    if (gEngine != nullptr) {
        gEngine->stop();
    } else {
        gEngine = new MainstageAudioEngine();
    }
    gEngine->init(sampleRate, bufferFrames, isUsbDevice == JNI_TRUE, deviceId);

    if (gPadEngine != nullptr) {
        gPadEngine->destroy();
    } else {
        gPadEngine = new PadEngine();
    }
    if (gAssetManager != nullptr) {
        gPadEngine->init(gAssetManager, sampleRate, isUsbDevice == JNI_TRUE, deviceId);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeClose(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        gEngine->stop();
        delete gEngine;
        gEngine = nullptr;
    }
    if (gPadEngine != nullptr) {
        gPadEngine->destroy();
        delete gPadEngine;
        gPadEngine = nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeLoadSoundFont(JNIEnv *env, jobject thiz, jstring path, jint channel) {
    if (gEngine != nullptr && path != nullptr) {
        const char *sf2Path = env->GetStringUTFChars(path, nullptr);
        bool success = gEngine->loadSoundFont(sf2Path, channel);
        env->ReleaseStringUTFChars(path, sf2Path);
        return success ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeIsAudioReady(JNIEnv *env, jobject thiz) {
    if (gEngine == nullptr) return JNI_FALSE;
    return gEngine->isAudioReady() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeAllNotesOff(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        gEngine->allNotesOff();
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetModulation(JNIEnv *env, jobject thiz, jfloat value, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setModulation(value, channel);
    }
}

JNIEXPORT jstring JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetAudioDiagnostics(JNIEnv *env, jobject thiz) {
    if (gEngine == nullptr) {
        return env->NewStringUTF("NO INICIALIZADO");
    }
    std::string diag = gEngine->getAudioDiagnostics();
    return env->NewStringUTF(diag.c_str());
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetAssetManager(JNIEnv *env, jobject thiz, jobject assetManager) {
    gAssetManager = AAssetManager_fromJava(env, assetManager);
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadSetEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    if (gPadEngine != nullptr) gPadEngine->setEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadSetVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    if (gPadEngine != nullptr) gPadEngine->setVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadSetPan(JNIEnv *env, jobject thiz, jfloat pan) {
    if (gPadEngine != nullptr) gPadEngine->setPan(pan);
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadSetBank(JNIEnv *env, jobject thiz, jstring bankName) {
    if (gPadEngine != nullptr && bankName != nullptr) {
        const char *bankStr = env->GetStringUTFChars(bankName, nullptr);
        gPadEngine->setBank(bankStr);
        env->ReleaseStringUTFChars(bankName, bankStr);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadNoteOn(JNIEnv *env, jobject thiz, jint pitchClass) {
    if (gPadEngine != nullptr) gPadEngine->noteOn(pitchClass);
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadNoteOff(JNIEnv *env, jobject thiz) {
    if (gPadEngine != nullptr) gPadEngine->noteOff();
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePadHardKillAll(JNIEnv *env, jobject thiz) {
    if (gPadEngine != nullptr) gPadEngine->hardKillAll();
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativePreviewSoundFont(JNIEnv *env, jobject thiz, jstring sf2Path, jint note, jint velocity, jint durationMs) {
    if (gEngine != nullptr && sf2Path != nullptr) {
        const char *path = env->GetStringUTFChars(sf2Path, nullptr);
        gEngine->previewSoundFont(path, note, velocity, durationMs);
        env->ReleaseStringUTFChars(sf2Path, path);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeStopPreview(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        gEngine->stopPreview();
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetChannelReverbSend(JNIEnv *env, jobject thiz, jint channel, jfloat value) {
    if (gEngine != nullptr) {
        gEngine->setChannelReverbSend(channel, value);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetChannelChorusSend(JNIEnv *env, jobject thiz, jint channel, jfloat value) {
    if (gEngine != nullptr) {
        gEngine->setChannelChorusSend(channel, value);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetMasterReverbParams(JNIEnv *env, jobject thiz, jfloat roomsize, jfloat damp, jfloat width, jfloat level) {
    if (gEngine != nullptr) {
        gEngine->setMasterReverbParams(roomsize, damp, width, level);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetMasterChorusParams(JNIEnv *env, jobject thiz, jint nr, jfloat level, jfloat speed, jfloat depth) {
    if (gEngine != nullptr) {
        gEngine->setMasterChorusParams(nr, level, speed, depth);
    }
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeSetMasterLimiterEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    if (gEngine != nullptr) {
        gEngine->setLimiterEnabled(enabled);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeIsMasterLimiterActive(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->isLimiterActive() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetActiveVoiceCount(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getActiveVoiceCount();
    }
    return 0;
}

JNIEXPORT jdouble JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetDspCpuLoad(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getDspCpuLoad();
    }
    return 0.0;
}

JNIEXPORT jint JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetLogicalChannelCount(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getLogicalChannelCount();
    }
    return NUM_LOGICAL_CHANNELS;
}

JNIEXPORT jint JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetGlobalPolyphony(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getGlobalPolyphony();
    }
    return GLOBAL_POLYPHONY;
}

JNIEXPORT jint JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetXRunCount(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getXRunCount();
    }
    return 0;
}

JNIEXPORT jdouble JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeGetPeakDspCpuLoad(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        return gEngine->getPeakDspCpuLoad();
    }
    return 0.0;
}

JNIEXPORT void JNICALL
Java_com_tutelopezmusic_stagekeyslive_PlatformAudioSynth_nativeResetPeakDspCpuLoad(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        gEngine->resetPeakDspCpuLoad();
    }
}

}

