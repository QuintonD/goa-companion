import dev.detekt.gradle.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/*
 * The web app's engine host (ADR-043 "Addendum (play, W4)"): the messages between the page and its
 * engine Web Worker, and the worker's side of them (the game session, the computer players, the
 * coach, the puzzles), over the shared core:game. The page uses the messages only; Kotlin/JS keeps
 * the engine out of the page's bundle. Tests run the host on Node (no worker needed).
 */
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    js {
        browser { testTask { enabled = false } }
        // Seeded computer games take more than mocha's 2 s on Node.
        nodejs { testTask { useMocha { timeout = "600s" } } }
        binaries.library()
    }
    sourceSets {
        jsMain.dependencies {
            api(projects.core.play)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

val repoRoot: String = rootDir.absolutePath
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    environment("GOA_REPO_ROOT", repoRoot)
}

extensions.configure<KtlintExtension> {
    version.set("1.8.0")
    android.set(true)
    filter { exclude { it.file.path.contains("build${File.separator}") } }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/jsMain/kotlin", "src/jsTest/kotlin")
    parallel.set(true)
}
