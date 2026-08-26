#include <jni.h>
#include <oboe/Oboe.h>
#include <math.h>
#include <vector>
#include <mutex>
#include <android/log.h>

#define LOG_TAG "StageKeysAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef USE_FLUIDSYNTH
#include <fluidsynth.h>
#endif

// Simple polyphonic synthesizer voice for desktop/Android fallback
struct SynthVoice {
    int note = -1;
    double phase = 0.0;
    double phaseIncrement = 0.0;
    double amplitude = 0.0;
    double targetAmplitude = 0.0;
    bool active = false;

    void trigger(int noteNumber, double sampleRate) {
        note = noteNumber;
        // Convert MIDI note to frequency: f = 440 * 2^((d-69)/12)
        double frequency = 440.0 * pow(2.0, (noteNumber - 69.0) / 12.0);
        phaseIncrement = (2.0 * M_PI * frequency) / sampleRate;
        phase = 0.0;
        targetAmplitude = 0.25; // voice limit
        amplitude = 0.0; // soft attack
        active = true;
    }

    void release() {
        targetAmplitude = 0.0;
    }

    float renderNextSample() {
        if (!active) return 0.0f;
        
        // Simple linear envelope for attack/release
        amplitude += (targetAmplitude - amplitude) * 0.01;
        if (amplitude < 0.001 && targetAmplitude == 0.0) {
            active = false;
            return 0.0f;
        }

        // Generate simple triangle/sine hybrid sound for warm tine tone
        float sample = sin(phase) * 0.7f + (phase < M_PI ? 1.0f : -1.0f) * 0.3f * (float)(1.0 - (phase / M_PI));
        phase += phaseIncrement;
        if (phase >= 2.0 * M_PI) {
            phase -= 2.0 * M_PI;
        }
        return sample * amplitude;
    }
};

class MainstageAudioEngine : public oboe::AudioStreamDataCallback {
private:
    oboe::AudioStream *stream = nullptr;
    std::mutex synthMutex;
    
    // Internal synth settings
    float masterVolume = 0.8f;
    float reverbMix = 0.3f;
    float filterCutoff = 0.5f;
    int currentProgram = 0;
    bool audioReady = false;

    // Fallback Synth Voices
    static const int MAX_VOICES = 8;
    SynthVoice voices[MAX_VOICES];
    double sampleRate = 48000.0;

#ifdef USE_FLUIDSYNTH
    fluid_settings_t* fluidSettings = nullptr;
    fluid_synth_t* fluidSynth = nullptr;
    int sfid = -1;
#endif

    // ---------------------------------------------------------------
    // Helper: open stream with a given SharingMode; returns true on success
    // ---------------------------------------------------------------
    bool tryOpenStream(oboe::AudioStreamBuilder& builder, oboe::SharingMode sharingMode) {
        builder.setSharingMode(sharingMode);
        oboe::Result result = builder.openStream(&stream);
        if (result == oboe::Result::OK && stream != nullptr) {
            LOGI("Oboe stream opened OK — SharingMode: %s, SampleRate: %d, BufferSize: %d",
                 sharingMode == oboe::SharingMode::Exclusive ? "Exclusive" : "Shared",
                 stream->getSampleRate(),
                 stream->getBufferSizeInFrames());
            return true;
        }
        LOGE("Oboe openStream failed — SharingMode: %s, Error: %s",
             sharingMode == oboe::SharingMode::Exclusive ? "Exclusive" : "Shared",
             oboe::convertToText(result));
        stream = nullptr;
        return false;
    }

public:
    MainstageAudioEngine() {
        // Initialize fallback voices
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i].active = false;
        }
    }

    ~MainstageAudioEngine() {
        stop();
    }

    bool isAudioReady() const { return audioReady; }

    void init() {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;

#ifdef USE_FLUIDSYNTH
        fluidSettings = new_fluid_settings();
        // Optimize fluidsynth settings for low latency mobile rendering
        fluid_settings_setstr(fluidSettings, "synth.reverb.active", "yes");
        fluid_settings_setstr(fluidSettings, "synth.chorus.active", "no");
        fluid_settings_setnum(fluidSettings, "synth.sample-rate", sampleRate);
        fluid_settings_setint(fluidSettings, "synth.polyphony", 64);
        
        fluidSynth = new_fluid_synth(fluidSettings);
        if (fluidSynth == nullptr) {
            LOGE("FluidSynth: failed to create synth instance");
        } else {
            LOGI("FluidSynth: synth instance created OK");
        }
#else
        LOGI("StageKeysAudio: FluidSynth NOT compiled in — using fallback 8-voice oscillator");
#endif

        // Configure Oboe output stream
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setDataCallback(this);

        // [POINT 3 FIX] Try Exclusive first for lowest latency;
        // fall back to Shared if the device/driver doesn't support Exclusive.
        bool opened = tryOpenStream(builder, oboe::SharingMode::Exclusive);
        if (!opened) {
            LOGE("Exclusive mode unavailable — retrying with Shared mode");
            opened = tryOpenStream(builder, oboe::SharingMode::Shared);
        }

        if (opened && stream != nullptr) {
            sampleRate = stream->getSampleRate();
#ifdef USE_FLUIDSYNTH
            fluid_settings_setnum(fluidSettings, "synth.sample-rate", sampleRate);
#endif
            oboe::Result startResult = stream->requestStart();
            if (startResult == oboe::Result::OK) {
                audioReady = true;
                LOGI("Oboe stream started successfully — sampleRate: %.0f", sampleRate);
            } else {
                LOGE("Oboe stream failed to start: %s", oboe::convertToText(startResult));
            }
        } else {
            LOGE("CRITICAL: Could not open Oboe stream in any sharing mode — no audio output");
        }
    }

    void stop() {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;
        if (stream != nullptr) {
            stream->requestStop();
            stream->close();
            stream = nullptr;
        }

#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            delete_fluid_synth(fluidSynth);
            fluidSynth = nullptr;
        }
        if (fluidSettings != nullptr) {
            delete_fluid_settings(fluidSettings);
            fluidSettings = nullptr;
        }
#endif
    }

    void loadSoundFont(const char* sf2Path) {
        std::lock_guard<std::mutex> lock(synthMutex);
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            LOGI("FluidSynth: loading SF2 from path: %s", sf2Path);
            if (sfid != -1) {
                fluid_synth_sfunload(fluidSynth, sfid, 1);
                sfid = -1;
            }
            sfid = fluid_synth_sfload(fluidSynth, sf2Path, 1);
            if (sfid != -1) {
                LOGI("FluidSynth: SF2 loaded OK — sfid=%d", sfid);
                // Program select bank 0, program 0
                fluid_synth_program_select(fluidSynth, 0, sfid, 0, 0);
            } else {
                LOGE("FluidSynth: fluid_synth_sfload FAILED for path: %s", sf2Path);
            }
        } else {
            LOGE("FluidSynth: cannot load SF2 — synth not initialized");
        }
#else
        LOGE("loadSoundFont called but FluidSynth is NOT compiled in — ignoring: %s", sf2Path);
#endif
    }

    void noteOn(int note, int velocity) {
        std::lock_guard<std::mutex> lock(synthMutex);
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            fluid_synth_noteon(fluidSynth, 0, note, velocity);
            return;
        }
#endif
        // Fallback Polyphonic Voice Trigger
        for (int i = 0; i < MAX_VOICES; i++) {
            if (!voices[i].active) {
                voices[i].trigger(note, sampleRate);
                break;
            }
        }
    }

    void noteOff(int note) {
        std::lock_guard<std::mutex> lock(synthMutex);
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            fluid_synth_noteoff(fluidSynth, 0, note);
            return;
        }
#endif
        // Fallback Voice Release
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i].active && voices[i].note == note) {
                voices[i].release();
            }
        }
    }

    void setVolume(float volume) {
        std::lock_guard<std::mutex> lock(synthMutex);
        masterVolume = volume;
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            // Map master volume to FluidSynth core volume parameter
            fluid_synth_set_gain(fluidSynth, volume);
        }
#endif
    }

    void setReverb(float reverb) {
        std::lock_guard<std::mutex> lock(synthMutex);
        reverbMix = reverb;
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            // FluidSynth Reverb Settings: RoomSize, Damping, Width, Level
            fluid_synth_set_reverb_params(fluidSynth, 0.7f, 0.5f, 0.6f, reverb);
        }
#endif
    }

    void setFilterCutoff(float cutoff) {
        std::lock_guard<std::mutex> lock(synthMutex);
        filterCutoff = cutoff;
        // In basic synth, cutoff shifts high frequencies, in fluidsynth we can configure CC 74
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            // Send Brightness/Cutoff MIDI CC 74 to channel 0
            fluid_synth_cc(fluidSynth, 0, 74, (int)(cutoff * 127.0f));
        }
#endif
    }

    void setPatch(int programNumber) {
        std::lock_guard<std::mutex> lock(synthMutex);
        currentProgram = programNumber;
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr && sfid != -1) {
            fluid_synth_program_select(fluidSynth, 0, sfid, 0, programNumber);
        }
#endif
    }

    // Handle raw MIDI CC — called from the MIDI Learn system
    void handleCc(int controller, float floatValue) {
        std::lock_guard<std::mutex> lock(synthMutex);
        // Default: map CC 7=volume, CC 74=filter, CC 91=reverb
        // The caller (Kotlin layer) decides whether to apply or intercept for Learn mode
        switch (controller) {
            case 7:  setVolume(floatValue); break;
            case 74: setFilterCutoff(floatValue); break;
            case 91: setReverb(floatValue); break;
        }
    }

    // Oboe Audio Callback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        float* outputBuffer = static_cast<float*>(audioData);
        memset(outputBuffer, 0, numFrames * 2 * sizeof(float));

        // Render stereo audio frames
#ifdef USE_FLUIDSYNTH
        if (fluidSynth != nullptr) {
            fluid_synth_write_float(fluidSynth, numFrames, outputBuffer, 0, 2, outputBuffer, 1, 2);
            return oboe::DataCallbackResult::Continue;
        }
#endif

        // Render Fallback Synth audio
        for (int i = 0; i < numFrames; i++) {
            float mixedSample = 0.0f;
            for (int v = 0; v < MAX_VOICES; v++) {
                mixedSample += voices[v].renderNextSample();
            }
            // Master gain & soft limiting
            mixedSample = mixedSample * masterVolume;
            if (mixedSample > 0.95f) mixedSample = 0.95f;
            if (mixedSample < -0.95f) mixedSample = -0.95f;

            // Stereo writing (L and R get the same signal in this basic fallback mono voice)
            outputBuffer[i * 2] = mixedSample;     // Left
            outputBuffer[i * 2 + 1] = mixedSample; // Right
        }

        return oboe::DataCallbackResult::Continue;
    }
};

// Global instance of Audio Engine
static MainstageAudioEngine* gEngine = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeNoteOn(JNIEnv *env, jobject thiz, jint note, jint velocity) {
    if (gEngine != nullptr) {
        gEngine->noteOn(note, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeNoteOff(JNIEnv *env, jobject thiz, jint note) {
    if (gEngine != nullptr) {
        gEngine->noteOff(note);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    if (gEngine != nullptr) {
        gEngine->setVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetReverb(JNIEnv *env, jobject thiz, jfloat reverb) {
    if (gEngine != nullptr) {
        gEngine->setReverb(reverb);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetFilterCutoff(JNIEnv *env, jobject thiz, jfloat cutoff) {
    if (gEngine != nullptr) {
        gEngine->setFilterCutoff(cutoff);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetPatch(JNIEnv *env, jobject thiz, jint program_number) {
    if (gEngine != nullptr) {
        gEngine->setPatch(program_number);
    }
}

// JNI Life-cycle functions
JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeInit(JNIEnv *env, jobject thiz) {
    if (gEngine == nullptr) {
        gEngine = new MainstageAudioEngine();
        gEngine->init();
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeClose(JNIEnv *env, jobject thiz) {
    if (gEngine != nullptr) {
        gEngine->stop();
        delete gEngine;
        gEngine = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeLoadSoundFont(JNIEnv *env, jobject thiz, jstring path) {
    if (gEngine != nullptr && path != nullptr) {
        const char *sf2Path = env->GetStringUTFChars(path, nullptr);
        gEngine->loadSoundFont(sf2Path);
        env->ReleaseStringUTFChars(path, sf2Path);
    }
}

// [POINT 3 FIX] Expose audio readiness to Kotlin layer for UI feedback
JNIEXPORT jboolean JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeIsAudioReady(JNIEnv *env, jobject thiz) {
    if (gEngine == nullptr) return JNI_FALSE;
    return gEngine->isAudioReady() ? JNI_TRUE : JNI_FALSE;
}

}
