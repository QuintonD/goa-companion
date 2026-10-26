import dev.detekt.gradle.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/*
 * The engine Web Worker (ADR-043 "Addendum (play, W4)"): `engine-worker.js`, a second bundle next to
 * the page's app.js. It runs the game engine, the computer players, the coach and the puzzle solver
 * off the page's thread, so the UI never blocks; :web:app copies the bundle into the site.
 */
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    js {
        outputModuleName.set("engine-worker")
        browser {
            commonWebpackConfig { outputFileName = "engine-worker.js" }
            testTask { enabled = false }
        }
        binaries.executable()
    }
    sourceSets {
        jsMain.dependencies {
            implementation(projects.web.engine)
        }
    }
}

extensions.configure<KtlintExtension> {
    version.set("1.8.0")
    android.set(true)
    filter { exclude { it.file.path.contains("build${File.separator}") } }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/jsMain/kotlin")
    parallel.set(true)
}
