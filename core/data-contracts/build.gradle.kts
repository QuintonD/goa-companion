plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// The data contracts (ADR-043 "Data contracts, F3"): the repository interfaces every screen reads
// through, their pure data types, and the implementations that are pure logic over other contracts
// (ratings replayed from the match log, player-name rules). Package dev.quinton.goa.core.data, as
// when they lived in core:data. The Android app implements them in core:data (Room, DataStore,
// Hilt); the web app in web/app (IndexedDB). No android.*, no java.*, no javax.inject here.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.domain)
            // GameInProgress wraps the engine's SavedGame (ADR-035). The web app's main bundle only
            // keeps what it references (Kotlin/JS drops unused code), so the engine stays in the worker.
            api(projects.core.game)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.core.dataTesting)
            implementation(libs.turbine)
        }
    }
}
