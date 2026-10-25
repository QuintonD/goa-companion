plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by the Android app (jvm) and the web app (js), ADR-043. JVM-only: the Nearby protocol
// (sync/protocol) and the javax.crypto implementation of sync sealing (SyncCrypto, SyncCodec).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            // The in-memory sync transport (protocol tests here, sync feature tests elsewhere).
            implementation(projects.core.domainTestfixtures)
        }
    }
}
