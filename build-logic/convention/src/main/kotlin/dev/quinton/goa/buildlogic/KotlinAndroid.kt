package dev.quinton.goa.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared Android + Kotlin settings. AGP 9 has Kotlin built in, so the Kotlin Android plugin is
 * never applied; compiler options are set on the compile tasks directly.
 * Bytecode targets Java 17; the build runs on the Android Studio JBR 21 (ADR-010).
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        lint.apply {
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = false
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        }
    }
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        failOnNoDiscoveredTests.set(false)
        systemProperty("robolectric.logging", "stderr")
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}
