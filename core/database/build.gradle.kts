plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.androidx.room3.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
