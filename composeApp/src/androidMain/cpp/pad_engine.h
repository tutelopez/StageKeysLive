#ifndef PAD_ENGINE_H
#define PAD_ENGINE_H

#include <string>
#include <vector>
#include <mutex>
#include <android/asset_manager.h>
#include <oboe/Oboe.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"
#undef STB_VORBIS_HEADER_ONLY

struct PadVoice {
    stb_vorbis* vorbis = nullptr;
    int pitchClass = -1;
    float currentGain = 0.0f;
    float targetGain = 0.0f;
    float gainRamp = 0.0f; 
    bool active = false;
};

class PadEngine : public oboe::AudioStreamDataCallback {
public:
    PadEngine();
    ~PadEngine();

    bool init(AAssetManager* assetManager, int sampleRate = 48000);
    void destroy();
    
    void setEnabled(bool enabled);
    void setVolume(float volume);
    void setPan(float pan);
    void setBank(const std::string& bankName);
    void noteOn(int pitchClass);
    void noteOff();
    void hardKillAll();
    void setCrossfadeSeconds(float seconds);
    void setReleaseSeconds(float seconds);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) override;

private:
    void loadBankInternal(const std::string& bankName);
    void releaseBankInternal();
    
    AAssetManager* mAssetManager;
    std::shared_ptr<oboe::AudioStream> mStream;
    
    std::mutex mMutex;
    bool mEnabled;
    float mMasterVolume;
    float mPan;
    float mCrossfadeFrames;
    float mReleaseFrames;
    int mSampleRate;
    
    // File buffers in memory for the 12 pitch classes
    struct OggFile {
        std::vector<uint8_t> data;
    };
    OggFile mOggFiles[12];
    std::string mCurrentBank;

    PadVoice mVoices[2];
    int mLastPitchClass;
};

#endif // PAD_ENGINE_H
