package dev.quinton.goa.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** ktlint + detekt on every module. Config lives in the root `config/` directory. */
internal fun Project.configureQuality() {
    pluginManager.apply(libs.pluginId("ktlint"))
    extensions.configure<KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        filter {
            exclude { it.file.path.contains("build${java.io.File.separator}generated") }
        }
    }
    pluginManager.apply(libs.pluginId("detekt"))
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
    }
}
