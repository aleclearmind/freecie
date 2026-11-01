plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    // 2.0.0-alpha02 is the release built against AGP 8.10 (Android tools 31.10.1).
    id("app.cash.paparazzi") version "2.0.0-alpha02" apply false
}
