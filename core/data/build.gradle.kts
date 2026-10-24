plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    // Play against the computer (ADR-035): the engine contract and GameEngine.
    api(projects.core.game)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
    testImplementation(testFixtures(projects.core.domain))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.datastore.preferences)
}
