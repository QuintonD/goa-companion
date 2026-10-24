import java.util.Properties

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
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each instrumented test starts from a fresh install state: the orchestrator runs every
        // test in its own process and clears the app's data (Room DB, DataStore) before it.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // The group sync server every new group uses unless it picks another (ADR-036): Gradle
        // property goa.syncServer (gradle.properties, ~/.gradle, -P); empty = no built-in server.
        // http works only for a local test server in debug builds (10.0.2.2 / localhost).
        val syncServer = providers.gradleProperty("goa.syncServer").orNull?.trim().orEmpty()
        val origin = Regex("""(https://[A-Za-z0-9.-]+|http://(10\.0\.2\.2|localhost|127\.0\.0\.1))(:\d+)?/?""")
        require(syncServer.isEmpty() || origin.matches(syncServer)) {
            "goa.syncServer must be an https origin like https://host:8443, got \"$syncServer\""
        }
        buildConfigField("String", "SYNC_SERVER_URL", "\"${syncServer.trimEnd('/')}\"")
    }
    buildFeatures {
        buildConfig = true
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
    implementation(projects.feature.board)

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
    // WorkManager with Hilt workers (the sync server, ADR-036).
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
