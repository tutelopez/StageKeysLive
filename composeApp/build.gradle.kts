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
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
            }
        }
        
        if (!disableAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation("androidx.activity:activity-compose:1.8.2")
                    implementation("androidx.appcompat:appcompat:1.6.1")
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("google.oboe:oboe:1.8.0")
                }
            }
        }
        
        val desktopMain by getting {
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
            prefab = true
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
            packageName = "MainstageAndroid"
            packageVersion = "1.0.0"
        }
    }
}
