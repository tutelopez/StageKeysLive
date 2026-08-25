plugins {
    // this is necessary to avoid the plugins block error in multi-module projects
    kotlin("multiplatform") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
}
