import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.goa.android.application)
    alias(libs.plugins.goa.android.compose)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.quinton.goa"
    defaultConfig {
        applicationId = "dev.quinton.goa"
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each instrumented test starts from a fresh install state: the orchestrator runs every
        // test in its own process and clears the app's data (Room DB, DataStore) before it.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }
    // Release signing lives OUTSIDE the repo: keystore.properties (gitignored) points at a keystore
    // kept elsewhere (storeFile, storePassword, keyAlias, keyPassword). Without it, release is unsigned.
    val signingProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    androidResources {
        // Catalog JSON and WebP images are already compressed / small.
        noCompress += listOf("webp")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.feature.catalog)
    implementation(projects.feature.timer)
    implementation(projects.feature.players)
    implementation(projects.feature.draft)
    implementation(projects.feature.settings)
    implementation(projects.feature.rules)
    implementation(projects.feature.sync)
    implementation(projects.feature.trivia)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    // WorkManager with Hilt workers (the hub sync, ADR-030 §5).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)

    testImplementation(projects.core.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}

// The Accessibility Test Framework (behind Compose's enableAccessibilityChecks) drags in an old
// Material Components (View) stack and full Guava, which clash with the versions AGP pins from the
// app's runtime classpath. Compose checks never use Material Views; Guava already contains
// ListenableFuture.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    exclude(group = "com.google.android.material", module = "material")
    exclude(group = "com.google.guava", module = "listenablefuture")
}

/**
 * App Links for invite links (ADR-030 §6): `https://<hub>/join#goa-sync:…` opens the app's join
 * flow. The hub's host differs per owner, so it comes from the Gradle property `goa.hubHost`
 * (gradle.properties, ~/.gradle/gradle.properties, `-Pgoa.hubHost=…`, or local.properties); without
 * it the app declares no link filter. Verification needs the hub's /.well-known/assetlinks.json
 * (README, "The group's hub").
 */
abstract class HubLinksManifest : DefaultTask() {
    @get:Input abstract val host: Property<String>

    @get:OutputFile abstract val manifest: RegularFileProperty

    @TaskAction
    fun write() {
        manifest.get().asFile.writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- Generated from goa.hubHost (app/build.gradle.kts): invite links open the app. -->
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <application>
            |        <activity android:name="dev.quinton.goa.MainActivity">
            |            <intent-filter android:autoVerify="true">
            |                <action android:name="android.intent.action.VIEW" />
            |                <category android:name="android.intent.category.DEFAULT" />
            |                <category android:name="android.intent.category.BROWSABLE" />
            |                <data android:scheme="https" android:host="${host.get()}" android:path="/join" />
            |            </intent-filter>
            |        </activity>
            |    </application>
            |</manifest>
            |""".trimMargin(),
        )
    }
}

val hubHost: String? = run {
    val local = rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }.getProperty("goa.hubHost")
    }
    val raw = (findProperty("goa.hubHost") as String?) ?: local
    raw?.trim()?.removePrefix("https://")?.trimEnd('/')?.lowercase()?.takeIf { it.isNotEmpty() }?.also {
        require(Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""").matches(it)) {
            "goa.hubHost must be a host name like goa-friday.duckdns.org, got \"$it\""
        }
    }
}

if (hubHost != null) {
    androidComponents {
        onVariants { variant ->
            val task = tasks.register<HubLinksManifest>("generate${variant.name.replaceFirstChar(Char::uppercase)}HubLinks") {
                host.set(hubHost)
                manifest.set(layout.buildDirectory.file("generated/hubLinks/${variant.name}/AndroidManifest.xml"))
            }
            variant.sources.manifests.addGeneratedManifestFile(task, HubLinksManifest::manifest)
        }
    }
}
