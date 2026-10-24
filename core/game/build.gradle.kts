plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// The game engine (ADR-035): rules, card effects, AI and puzzles. Pure Kotlin, no android.*.
dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    testImplementation(libs.kotest.property)
}
