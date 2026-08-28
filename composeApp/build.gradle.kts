plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

// Auto-detect if Android SDK is present on the system
val hasAndroidSdk = project.providers.environmentVariable("ANDROID_HOME").isPresent ||
        project.providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
        File(System.getProperty("user.home"), "AppData/Local/Android/Sdk").exists() ||
        project.file("../local.properties").exists()

val disableAndroid = !hasAndroidSdk

if (!disableAndroid) {
    plugins.apply("com.android.application")
}

kotlin {
    if (!disableAndroid) {
        androidTarget()
    }

    jvm("desktop")

    sourceSets {
        // Use getByName() instead of deprecated 'by getting' delegation
        getByName("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation("br.com.devsrsouza.compose.icons:tabler-icons:1.1.0")
            }
        }

        if (!disableAndroid) {
            getByName("androidMain") {
                dependencies {
                    implementation("androidx.activity:activity-compose:1.8.2")
                    implementation("androidx.appcompat:appcompat:1.6.1")
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
                    // Oboe headers come from cpp/include/oboe/ (downloaded from GitHub 1.8.0).
                    // liboboe.so at runtime comes from FluidSynth v2.6.0 bundle — no Prefab needed.
                }
            }
        }

        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

if (!disableAndroid) {
    configure<com.android.build.api.dsl.ApplicationExtension> {
        namespace = "com.midi.mainstage"
        compileSdk = 34

        buildFeatures {
            // prefab not needed: Oboe is now provided as local headers + FluidSynth-bundled .so
        }

        externalNativeBuild {
            cmake {
                path = File("src/androidMain/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }

        defaultConfig {
            applicationId = "com.midi.mainstage"
            minSdk = 24
            targetSdk = 34
            versionCode = 1
            versionName = "1.0"

            // [POINT 1 FIX] Enable FluidSynth SF2 rendering in native code
            externalNativeBuild {
                cmake {
                    arguments("-DUSE_FLUIDSYNTH=ON", "-DANDROID_STL=c++_shared")
                }
            }

            // FluidSynth v2.6.0 bundle includes all 4 ABIs
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "StageKeysLive"
            packageVersion = "1.0.0"
        }
    }
}
