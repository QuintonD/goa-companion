plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// The wire contract of online games (ADR-056, docs/ONLINE.md): shared by the JVM server, the phone
// and the browser. Plain data only: no engine session, no rules, no platform code.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.game)
            api(libs.kotlinx.serialization.json)
        }
    }
}
