# ===================================================================
# StageKeysLive (com.midi.mainstage) ProGuard / R8 Rules
# ===================================================================

# Standard Attributes preservation for Crashlytics and Reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# -------------------------------------------------------------------
# JNI / Native C++ Audio Engine (mainstage_audio.cpp, pad_engine.cpp)
# -------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.midi.mainstage.PlatformAudioSynth {
    *;
}

-keep class com.midi.mainstage.AudioEngineKt {
    *;
}

-keep class com.midi.mainstage.AndroidPerformanceMonitor {
    *;
}

-keep class com.midi.mainstage.PerformanceStats {
    *;
}

-keep class com.midi.mainstage.AudioOutputDeviceInfo {
    *;
}

# -------------------------------------------------------------------
# Data Models, State Snapshots & Serialization
# -------------------------------------------------------------------
-keep class com.midi.mainstage.Concert { *; }
-keep class com.midi.mainstage.PatchState { *; }
-keep class com.midi.mainstage.ChannelStripState { *; }
-keep class com.midi.mainstage.PatchChannelSnapshot { *; }
-keep class com.midi.mainstage.MidiTarget { *; }
-keep class com.midi.mainstage.GoogleUserProfile { *; }
-keep class com.midi.mainstage.DriveBackupItem { *; }
-keep class com.midi.mainstage.DriveSyncState { *; }
-keep class com.midi.mainstage.MasterFxSettings { *; }
-keep class com.midi.mainstage.RecordingEvent { *; }
-keep class com.midi.mainstage.ConcertSerializer { *; }
-keep class com.midi.mainstage.PatchSerializer { *; }
-keep class com.midi.mainstage.CommonPersistenceKt { *; }
-keep class com.midi.mainstage.PersistenceKt { *; }

# Keep all com.midi.mainstage public and internal APIs intact
-keep class com.midi.mainstage.** {
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
