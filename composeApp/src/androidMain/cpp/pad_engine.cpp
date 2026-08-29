#include "pad_engine.h"
#include <android/log.h>
#include <chrono>

#define LOG_TAG "StageKeysPadEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Nombres de archivos para cada clase de nota (0 = C, 1 = C#, etc.)
static const char* NOTE_FILES[12] = {
    "c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"
};

#undef STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

PadEngine::PadEngine()
    : mAssetManager(nullptr),
      mEnabled(false),
      mMasterVolume(0.5f),
      mSampleRate(48000),
      mLastPitchClass(-1)
{
    setCrossfadeSeconds(1.5f);
    setReleaseSeconds(2.5f);
}

PadEngine::~PadEngine() {
    destroy();
}

bool PadEngine::init(AAssetManager* assetManager, int sampleRate) {
    std::lock_guard<std::mutex> lock(mMutex);
    mAssetManager = assetManager;
    mSampleRate = sampleRate;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(2); // Stereo
    builder.setSampleRate(mSampleRate);
    builder.setDataCallback(this);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open PadEngine Oboe stream. Error: %s", oboe::convertToText(result));
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start PadEngine Oboe stream. Error: %s", oboe::convertToText(result));
        return false;
    }
    
    LOGI("PadEngine initialized successfully at %d Hz", mSampleRate);
    return true;
}

void PadEngine::destroy() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
    releaseBankInternal();
}

void PadEngine::releaseBankInternal() {
    for (int i = 0; i < 2; ++i) {
        if (mVoices[i].vorbis) {
            stb_vorbis_close(mVoices[i].vorbis);
            mVoices[i].vorbis = nullptr;
        }
        mVoices[i].active = false;
    }
    
    for (int i = 0; i < 12; ++i) {
        mOggFiles[i].data.clear();
    }
    mCurrentBank = "";
    mLastPitchClass = -1;
}

void PadEngine::loadBankInternal(const std::string& bankName) {
    if (bankName == mCurrentBank) return;
    
    releaseBankInternal();
    mCurrentBank = bankName;
    if (bankName.empty() || !mAssetManager) return;

    int loadedCount = 0;
    for (int i = 0; i < 12; ++i) {
        std::string dirPath = "pads/" + bankName;
        AAssetDir* assetDir = AAssetManager_openDir(mAssetManager, dirPath.c_str());
        if (!assetDir) {
            LOGE("Failed to open asset dir %s", dirPath.c_str());
            break;
        }
        
        std::string foundFile = "";
        std::string note = NOTE_FILES[i];
        
        const char* filename;
        while ((filename = AAssetDir_getNextFileName(assetDir)) != nullptr) {
            std::string fname(filename);
            bool isMatch = false;
            
            if (fname == note + ".ogg") {
                isMatch = true;
            } else if (fname.find(note + "_") == 0) {
                isMatch = true;
            } else if (fname.find("_" + note + ".ogg") != std::string::npos && fname.rfind("_" + note + ".ogg") == fname.length() - note.length() - 5) {
                isMatch = true;
            } else if (fname.find("_" + note + "_") != std::string::npos) {
                isMatch = true;
            }

            if (isMatch) {
                foundFile = dirPath + "/" + fname;
                break;
            }
        }
        AAssetDir_close(assetDir);

        if (foundFile.empty()) {
            LOGW("Could not find ogg file for note %s in %s", NOTE_FILES[i], bankName.c_str());
            continue; // Not found, skip
        }

        AAsset* asset = AAssetManager_open(mAssetManager, foundFile.c_str(), AASSET_MODE_BUFFER);
        if (asset) {
            size_t length = AAsset_getLength(asset);
            const void* buffer = AAsset_getBuffer(asset);
            if (buffer) {
                mOggFiles[i].data.assign((const uint8_t*)buffer, (const uint8_t*)buffer + length);
                loadedCount++;
            }
            AAsset_close(asset);
        }
    }
    LOGI("Loaded %d/12 notes for bank %s", loadedCount, bankName.c_str());
}

void PadEngine::setBank(const std::string& bankName) {
    std::lock_guard<std::mutex> lock(mMutex);
    loadBankInternal(bankName);
}

void PadEngine::setEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mMutex);
    mEnabled = enabled;
    if (!mEnabled) {
        // Fast kill
        for (int i = 0; i < 2; ++i) {
            mVoices[i].targetGain = 0.0f;
            mVoices[i].gainRamp = -1.0f / (mSampleRate * 0.1f); // 100ms fade out
        }
        mLastPitchClass = -1;
    }
}

void PadEngine::setVolume(float volume) {
    std::lock_guard<std::mutex> lock(mMutex);
    mMasterVolume = volume;
}

void PadEngine::setCrossfadeSeconds(float seconds) {
    std::lock_guard<std::mutex> lock(mMutex);
    mCrossfadeFrames = seconds * mSampleRate;
}

void PadEngine::setReleaseSeconds(float seconds) {
    std::lock_guard<std::mutex> lock(mMutex);
    mReleaseFrames = seconds * mSampleRate;
}

void PadEngine::noteOn(int pitchClass) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mEnabled || pitchClass < 0 || pitchClass > 11) return;
    if (mCurrentBank.empty() || mOggFiles[pitchClass].data.empty()) return;
    
    if (pitchClass == mLastPitchClass) return; // Ya está sonando esta clase
    
    // Fade out a la voz activa actual
    int oldestVoiceIdx = 0;
    float oldestGain = 999.0f;
    for (int i = 0; i < 2; ++i) {
        if (mVoices[i].active) {
            mVoices[i].targetGain = 0.0f;
            mVoices[i].gainRamp = -mVoices[i].currentGain / mCrossfadeFrames;
            
            if (mVoices[i].currentGain < oldestGain) {
                oldestGain = mVoices[i].currentGain;
                oldestVoiceIdx = i;
            }
        }
    }
    
    // Elegir la voz inactiva (o la más vieja silenciándose) para la nueva nota
    int freeVoiceIdx = -1;
    for (int i = 0; i < 2; ++i) {
        if (!mVoices[i].active) {
            freeVoiceIdx = i;
            break;
        }
    }
    if (freeVoiceIdx == -1) freeVoiceIdx = oldestVoiceIdx;
    
    PadVoice& v = mVoices[freeVoiceIdx];
    if (v.vorbis) {
        stb_vorbis_close(v.vorbis);
        v.vorbis = nullptr;
    }
    
    int error;
    v.vorbis = stb_vorbis_open_memory(mOggFiles[pitchClass].data.data(), mOggFiles[pitchClass].data.size(), &error, nullptr);
    if (v.vorbis) {
        v.pitchClass = pitchClass;
        v.currentGain = 0.0f;
        v.targetGain = 1.0f;
        v.gainRamp = 1.0f / mCrossfadeFrames;
        v.active = true;
        mLastPitchClass = pitchClass;
    }
}

void PadEngine::noteOff() {
    std::lock_guard<std::mutex> lock(mMutex);
    mLastPitchClass = -1;
    for (int i = 0; i < 2; ++i) {
        if (mVoices[i].active) {
            mVoices[i].targetGain = 0.0f;
            mVoices[i].gainRamp = -mVoices[i].currentGain / mReleaseFrames;
        }
    }
}

void PadEngine::hardKillAll() {
    std::lock_guard<std::mutex> lock(mMutex);
    mLastPitchClass = -1;
    for (int i = 0; i < 2; ++i) {
        if (mVoices[i].active) {
            mVoices[i].currentGain = 0.0f;
            mVoices[i].targetGain = 0.0f;
            mVoices[i].active = false;
            if (mVoices[i].vorbis) {
                stb_vorbis_close(mVoices[i].vorbis);
                mVoices[i].vorbis = nullptr;
            }
        }
    }
}

oboe::DataCallbackResult PadEngine::onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) {
    auto startTime = std::chrono::high_resolution_clock::now();
    
    float* output = static_cast<float*>(audioData);
    // Clear buffer
    for (int i = 0; i < numFrames * 2; ++i) {
        output[i] = 0.0f;
    }

    if (!mMutex.try_lock()) {
        // If we can't get the lock, output silence (prevents blocking audio thread)
        return oboe::DataCallbackResult::Continue;
    }
    
    float masterVol = mMasterVolume;
    
    for (int v = 0; v < 2; ++v) {
        PadVoice& voice = mVoices[v];
        if (!voice.active || !voice.vorbis) continue;
        
        const int MAX_FRAMES_PER_CHUNK = 512;
        float buffer[MAX_FRAMES_PER_CHUNK * 2];
        int framesNeeded = numFrames;
        int framesRead = 0;
        
        while (framesRead < framesNeeded) {
            int toRead = framesNeeded - framesRead;
            if (toRead > MAX_FRAMES_PER_CHUNK) toRead = MAX_FRAMES_PER_CHUNK;
            // stb_vorbis_get_samples_float_interleaved returns number of frames read
            int read = stb_vorbis_get_samples_float_interleaved(voice.vorbis, 2, buffer, toRead * 2);
            if (read == 0) {
                // Loop
                stb_vorbis_seek_start(voice.vorbis);
                continue;
            }
            
            // Mix into output
            for (int i = 0; i < read; ++i) {
                // Apply gain envelope
                if (voice.gainRamp > 0.0f && voice.currentGain < voice.targetGain) {
                    voice.currentGain += voice.gainRamp;
                    if (voice.currentGain > voice.targetGain) voice.currentGain = voice.targetGain;
                } else if (voice.gainRamp < 0.0f && voice.currentGain > voice.targetGain) {
                    voice.currentGain += voice.gainRamp;
                    if (voice.currentGain < voice.targetGain) voice.currentGain = voice.targetGain;
                }
                
                float finalGain = voice.currentGain * masterVol;
                output[(framesRead + i) * 2]     += buffer[i * 2]     * finalGain;
                output[(framesRead + i) * 2 + 1] += buffer[i * 2 + 1] * finalGain;
            }
            framesRead += read;
        }
        
        if (voice.targetGain == 0.0f && voice.currentGain <= 0.0001f) {
            voice.active = false;
            if (voice.vorbis) {
                stb_vorbis_close(voice.vorbis);
                voice.vorbis = nullptr;
            }
        }
    }
    
    mMutex.unlock();

    // FAILSAFE DEGRADATION MECHANISM
    auto endTime = std::chrono::high_resolution_clock::now();
    double elapsedMs = std::chrono::duration<double, std::milli>(endTime - startTime).count();
    double budgetMs = (numFrames / (double)mSampleRate) * 1000.0;
    
    if (elapsedMs > budgetMs * 0.75) {
        // CPU danger: degrade!
        std::lock_guard<std::mutex> lock(mMutex);
        int activeCount = 0;
        int oldestActiveIdx = -1;
        float oldestGain = 999.0f;
        for (int i = 0; i < 2; ++i) {
            if (mVoices[i].active) {
                activeCount++;
                if (mVoices[i].currentGain < oldestGain && mVoices[i].targetGain == 0.0f) {
                    oldestGain = mVoices[i].currentGain;
                    oldestActiveIdx = i;
                }
            }
        }
        
        if (activeCount > 1 && oldestActiveIdx != -1) {
            LOGW("PadEngine: Audio thread taking %.2f ms (budget: %.2f ms). CPU overload! Degrading: hard killing older voice.", elapsedMs, budgetMs);
            mVoices[oldestActiveIdx].active = false;
            mVoices[oldestActiveIdx].currentGain = 0.0f;
            if (mVoices[oldestActiveIdx].vorbis) {
                stb_vorbis_close(mVoices[oldestActiveIdx].vorbis);
                mVoices[oldestActiveIdx].vorbis = nullptr;
            }
        }
    }

    return oboe::DataCallbackResult::Continue;
}
