plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/*
 * The app's Baseline Profile and its startup measure (ADR-057). Needs a running device or emulator:
 *   :app:generateBaselineProfile                            writes app/src/release/generated/baselineProfiles
 *   :baselineprofile:connectedBenchmarkReleaseAndroidTest   cold start with and without the profile
 */
android {
    namespace = "dev.quinton.goa.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // An emulator's numbers only compare one build with another; they say nothing of a phone.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
