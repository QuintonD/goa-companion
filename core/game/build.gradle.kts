import dev.quinton.goa.buildlogic.EmbedTextFiles

plugins {
    alias(libs.plugins.goa.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// The game engine (ADR-035): rules, card effects, AI and puzzles. Pure Kotlin, no android.*.
// Shared by the Android app (jvm) and the web app's worker (js), ADR-043.

// Puzzle answer keys stay files under src/main/resources (PuzzleBookTest rewrites them); shared
// code reads them from a generated object, the same on the phone and in the browser.
val embedPuzzleKeys = tasks.register<EmbedTextFiles>("embedPuzzleKeys") {
    sourceDir.set(layout.projectDirectory.dir("src/main/resources/dev/quinton/goa/core/game/puzzles/keys"))
    extension.set("key")
    packageName.set("dev.quinton.goa.core.game.puzzles")
    objectName.set("PuzzleKeyFiles")
    outputDir.set(layout.buildDirectory.dir("generated/puzzleKeys/kotlin"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(embedPuzzleKeys.map { it.outputDir })
            dependencies {
                api(projects.core.model)
                api(projects.core.domain)
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
        }
    }
}

// Wider complexity limits for rules code and card hooks (core/game/detekt.yml, ENGINE.md "Style").
extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
    config.from(file("detekt.yml"))
}

// The opt-in AI runs (AI.md "How to retrain") need more than the default test heap: AI_HEAP=8g.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("AI_HEAP").orNull?.let { maxHeapSize = it }
}
