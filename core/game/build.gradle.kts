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

// Wider complexity limits for rules code and card hooks (core/game/detekt.yml, ENGINE.md "Style").
extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
    config.from(file("detekt.yml"))
}
