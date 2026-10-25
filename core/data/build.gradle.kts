plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    // Saved games and the play engine's bindings (ADR-035).
    api(projects.core.game)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // The group's sync server (ADR-036): one HTTPS origin, no logging interceptor.
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.domainTestfixtures)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.datastore.preferences)
}
