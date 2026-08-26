#include <jni.h>
#include <android/log.h>
#include <mutex>

#define LOG_TAG "StageKeysAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include <fluidsynth.h>

class MainstageAudioEngine {
private:
    std::mutex synthMutex;
    
    // Internal synth settings per channel
    int sfids[16];
    int currentPrograms[16];
    float masterVolume = 0.8f;
    float reverbMix = 0.3f;
    float filterCutoff = 0.5f;
    bool audioReady = false;

    fluid_settings_t* fluidSettings = nullptr;
    fluid_synth_t* fluidSynth = nullptr;
    fluid_audio_driver_t* fluidAudioDriver = nullptr;

public:
    MainstageAudioEngine() {
        for (int i = 0; i < 16; i++) {
            sfids[i] = -1;
            currentPrograms[i] = 0;
        }
    }

    ~MainstageAudioEngine() {
        stop();
    }

    bool isAudioReady() const { return audioReady; }

    void init() {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;

        fluidSettings = new_fluid_settings();
        
        // Use Oboe as the audio driver (FluidSynth 2.2+ supports Oboe on Android)
        fluid_settings_setstr(fluidSettings, "audio.driver", "oboe");
        
        // Optimize fluidsynth settings for low latency mobile rendering
        fluid_settings_setstr(fluidSettings, "synth.reverb.active", "yes");
        fluid_settings_setstr(fluidSettings, "synth.chorus.active", "no");
        fluid_settings_setnum(fluidSettings, "synth.sample-rate", 48000.0);
        fluid_settings_setint(fluidSettings, "synth.polyphony", 64);
        
        fluidSynth = new_fluid_synth(fluidSettings);
        if (fluidSynth == nullptr) {
            LOGE("FluidSynth: failed to create synth instance");
            return;
        } 
        LOGI("FluidSynth: synth instance created OK");

        fluidAudioDriver = new_fluid_audio_driver(fluidSettings, fluidSynth);
        if (fluidAudioDriver == nullptr) {
            LOGE("FluidSynth: failed to create Oboe audio driver, falling back to opensles");
            fluid_settings_setstr(fluidSettings, "audio.driver", "opensles");
            fluidAudioDriver = new_fluid_audio_driver(fluidSettings, fluidSynth);
        }

        if (fluidAudioDriver != nullptr) {
            audioReady = true;
            LOGI("FluidSynth: audio driver created successfully");
        } else {
            LOGE("CRITICAL: FluidSynth could not create any audio driver!");
        }
    }

    void stop() {
        std::lock_guard<std::mutex> lock(synthMutex);
        audioReady = false;

        if (fluidAudioDriver != nullptr) {
            delete_fluid_audio_driver(fluidAudioDriver);
            fluidAudioDriver = nullptr;
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

    bool loadSoundFont(const char* sf2Path, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && channel >= 0 && channel < 16) {
            LOGI("FluidSynth: loading SF2 from path: %s for channel: %d", sf2Path, channel);
            if (sfids[channel] != -1) {
                fluid_synth_sfunload(fluidSynth, sfids[channel], 1);
                sfids[channel] = -1;
            }
            sfids[channel] = fluid_synth_sfload(fluidSynth, sf2Path, 1);
            if (sfids[channel] != -1) {
                LOGI("FluidSynth: SF2 loaded OK — sfid=%d for channel=%d", sfids[channel], channel);
                // Program select bank 0, program 0 on this channel
                fluid_synth_program_select(fluidSynth, channel, sfids[channel], 0, currentPrograms[channel]);
                return true;
            } else {
                LOGE("FluidSynth: fluid_synth_sfload FAILED for path: %s", sf2Path);
                return false;
            }
        }
        return false;
    }

    void noteOn(int note, int velocity, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && channel >= 0 && channel < 16) {
            fluid_synth_noteon(fluidSynth, channel, note, velocity);
        }
    }

    void noteOff(int note, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && channel >= 0 && channel < 16) {
            fluid_synth_noteoff(fluidSynth, channel, note);
        }
    }

    void setVolume(float volume) {
        std::lock_guard<std::mutex> lock(synthMutex);
        masterVolume = volume;
        if (fluidSynth != nullptr) {
            fluid_synth_set_gain(fluidSynth, volume);
        }
    }
    
    void setChannelVolume(float volume, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (fluidSynth != nullptr && channel >= 0 && channel < 16) {
             fluid_synth_cc(fluidSynth, channel, 7, (int)(volume * 127.0f));
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

    void setFilterCutoff(float cutoff, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        filterCutoff = cutoff; // We can track per channel if needed, global variable for now
        if (fluidSynth != nullptr && channel >= 0 && channel < 16) {
            fluid_synth_cc(fluidSynth, channel, 74, (int)(cutoff * 127.0f));
        }
    }

    void setPatch(int programNumber, int channel) {
        std::lock_guard<std::mutex> lock(synthMutex);
        if (channel >= 0 && channel < 16) {
            currentPrograms[channel] = programNumber;
            if (fluidSynth != nullptr && sfids[channel] != -1) {
                fluid_synth_program_select(fluidSynth, channel, sfids[channel], 0, programNumber);
            }
        }
    }
};

static MainstageAudioEngine* gEngine = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeNoteOn(JNIEnv *env, jobject thiz, jint note, jint velocity, jint channel) {
    if (gEngine != nullptr) {
        gEngine->noteOn(note, velocity, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeNoteOff(JNIEnv *env, jobject thiz, jint note, jint channel) {
    if (gEngine != nullptr) {
        gEngine->noteOff(note, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    if (gEngine != nullptr) {
        gEngine->setVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetChannelVolume(JNIEnv *env, jobject thiz, jfloat volume, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setChannelVolume(volume, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetReverb(JNIEnv *env, jobject thiz, jfloat reverb) {
    if (gEngine != nullptr) {
        gEngine->setReverb(reverb);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetFilterCutoff(JNIEnv *env, jobject thiz, jfloat cutoff, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setFilterCutoff(cutoff, channel);
    }
}

JNIEXPORT void JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeSetPatch(JNIEnv *env, jobject thiz, jint program_number, jint channel) {
    if (gEngine != nullptr) {
        gEngine->setPatch(program_number, channel);
    }
}

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

JNIEXPORT jboolean JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeLoadSoundFont(JNIEnv *env, jobject thiz, jstring path, jint channel) {
    if (gEngine != nullptr && path != nullptr) {
        const char *sf2Path = env->GetStringUTFChars(path, nullptr);
        bool success = gEngine->loadSoundFont(sf2Path, channel);
        env->ReleaseStringUTFChars(path, sf2Path);
        return success ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_midi_mainstage_PlatformAudioSynth_nativeIsAudioReady(JNIEnv *env, jobject thiz) {
    if (gEngine == nullptr) return JNI_FALSE;
    return gEngine->isAudioReady() ? JNI_TRUE : JNI_FALSE;
}

}
