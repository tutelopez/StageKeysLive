# ===================================================================
# StageKeysLive (com.tutelopezmusic.stagekeyslive) ProGuard / R8 Rules
# ===================================================================

# Standard Attributes preservation for Crashlytics and Reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# -------------------------------------------------------------------
# JNI / Native C++ Audio Engine (mainstage_audio.cpp, pad_engine.cpp)
# -------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.tutelopezmusic.stagekeyslive.PlatformAudioSynth {
    *;
}

-keep class com.tutelopezmusic.stagekeyslive.AudioEngineKt {
    *;
}

-keep class com.tutelopezmusic.stagekeyslive.AndroidPerformanceMonitor {
    *;
}

-keep class com.tutelopezmusic.stagekeyslive.PerformanceStats {
    *;
}

-keep class com.tutelopezmusic.stagekeyslive.AudioOutputDeviceInfo {
    *;
}

# -------------------------------------------------------------------
# Data Models, State Snapshots & Serialization
# -------------------------------------------------------------------
-keep class com.tutelopezmusic.stagekeyslive.Concert { *; }
-keep class com.tutelopezmusic.stagekeyslive.PatchState { *; }
-keep class com.tutelopezmusic.stagekeyslive.ChannelStripState { *; }
-keep class com.tutelopezmusic.stagekeyslive.PatchChannelSnapshot { *; }
-keep class com.tutelopezmusic.stagekeyslive.MidiTarget { *; }
-keep class com.tutelopezmusic.stagekeyslive.GoogleUserProfile { *; }
-keep class com.tutelopezmusic.stagekeyslive.DriveBackupItem { *; }
-keep class com.tutelopezmusic.stagekeyslive.DriveSyncState { *; }
-keep class com.tutelopezmusic.stagekeyslive.MasterFxSettings { *; }
-keep class com.tutelopezmusic.stagekeyslive.RecordingEvent { *; }
-keep class com.tutelopezmusic.stagekeyslive.ConcertSerializer { *; }
-keep class com.tutelopezmusic.stagekeyslive.PatchSerializer { *; }
-keep class com.tutelopezmusic.stagekeyslive.CommonPersistenceKt { *; }
-keep class com.tutelopezmusic.stagekeyslive.PersistenceKt { *; }

# Keep all com.tutelopezmusic.stagekeyslive public and internal APIs intact
-keep class com.tutelopezmusic.stagekeyslive.** {
    <fields>;
    <methods>;
}

# -------------------------------------------------------------------
# Google Identity, Google Sign-In & Credential Manager
# -------------------------------------------------------------------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.** { *; }

# -------------------------------------------------------------------
# Firebase Auth, Crashlytics & Analytics
# -------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# -------------------------------------------------------------------
# Coil Image Loader (User Avatar & Bitmaps)
# -------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

# -------------------------------------------------------------------
# Compose Icons
# -------------------------------------------------------------------
-keep class compose.icons.** { *; }
