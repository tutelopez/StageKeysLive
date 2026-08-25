cmake_minimum_required(VERSION 3.22.1)

project("mainstage_audio")

set(CMAKE_CXX_STANDARD 17)

# Locate Oboe through Prefab
find_package(oboe REQUIRED CONFIG)

# Support compiling with FluidSynth flag
if(USE_FLUIDSYNTH)
    add_definitions(-DUSE_FLUIDSYNTH)
    # Note: Link fluidsynth library here if compiled:
    # find_library(FLUIDSYNTH_LIB fluidsynth)
    # target_link_libraries(mainstage_audio ${FLUIDSYNTH_LIB})
endif()

add_library(mainstage_audio SHARED
    mainstage_audio.cpp
)

target_link_libraries(mainstage_audio
    log
    android
    oboe::oboe
)
