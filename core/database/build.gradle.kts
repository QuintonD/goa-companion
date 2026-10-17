plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        // Exported schemas are unit-test assets so MigrationTestHelper can read them under Robolectric.
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.androidx.room3.testing)
    // Robolectric can't load the bundled driver's Android .so; tests use the framework driver.
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
