plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // The group's hub (ADR-030 §5): one HTTPS endpoint, no logging interceptor.
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
    testImplementation(testFixtures(projects.core.domain))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.datastore.preferences)
}
