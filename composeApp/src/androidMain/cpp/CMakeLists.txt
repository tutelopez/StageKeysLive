cmake_minimum_required(VERSION 3.22.1)

project("mainstage_audio")

set(CMAKE_CXX_STANDARD 17)

# -----------------------------------------------------------------------
# FluidSynth v2.6.0 official Android binaries (arm64-v8a, armeabi-v7a,
# x86, x86_64). Extracted from the official GitHub release asset:
# fluidsynth-v2.6.0-android24.zip
# No libomp.so dependency (fixed since FluidSynth 2.4.7 / issue #1512).
# -----------------------------------------------------------------------
set(FLUIDSYNTH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/libs/fluidsynth")
set(FLUIDSYNTH_LIBS "${FLUIDSYNTH_DIR}/lib/${ANDROID_ABI}")

# FluidSynth headers
include_directories("${FLUIDSYNTH_DIR}/include")

# Oboe headers: sourced directly from Oboe 1.8.0 GitHub source (MIT license).
# Downloaded to: composeApp/src/androidMain/cpp/include/oboe/
# We use the FluidSynth-bundled liboboe.so for runtime to guarantee ABI compatibility.
include_directories("${CMAKE_CURRENT_SOURCE_DIR}/include")

# Helper macro: import a prebuilt .so as a CMake IMPORTED target so that
# AGP automatically packages it in the APK's jniLibs for the correct ABI.
macro(import_prebuilt_so TARGET_NAME LIB_FILENAME)
    add_library(${TARGET_NAME} SHARED IMPORTED)
    set_target_properties(${TARGET_NAME} PROPERTIES
        IMPORTED_LOCATION "${FLUIDSYNTH_LIBS}/${LIB_FILENAME}"
    )
endmacro()

# Import FluidSynth and all its bundled dependencies.
import_prebuilt_so(fluidsynth        "libfluidsynth.so")
import_prebuilt_so(fluidsynth_asset  "libfluidsynth-assetloader.so")
import_prebuilt_so(oboe_bundled      "liboboe.so")
import_prebuilt_so(FLAC              "libFLAC.so")
import_prebuilt_so(ogg               "libogg.so")
import_prebuilt_so(opus              "libopus.so")
import_prebuilt_so(sndfile           "libsndfile.so")
import_prebuilt_so(vorbis            "libvorbis.so")
import_prebuilt_so(vorbisenc         "libvorbisenc.so")
import_prebuilt_so(vorbisfile        "libvorbisfile.so")

# -----------------------------------------------------------------------
# Main StageKeysLive native audio library
# USE_FLUIDSYNTH activates the FluidSynth code paths in mainstage_audio.cpp
# (passed also via -DUSE_FLUIDSYNTH=ON from build.gradle.kts defaultConfig)
# -----------------------------------------------------------------------
add_definitions(-DUSE_FLUIDSYNTH)

add_library(mainstage_audio SHARED
    mainstage_audio.cpp
)

target_link_libraries(mainstage_audio
    log
    android
    # FluidSynth and its audio format + audio driver dependencies
    fluidsynth
    fluidsynth_asset
    oboe_bundled
    FLAC
    ogg
    opus
    sndfile
    vorbis
    vorbisenc
    vorbisfile
)
