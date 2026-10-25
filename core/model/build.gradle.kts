plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by the Android app (jvm) and the web app (js), ADR-043.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
