plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    implementation(projects.core.presentation)
    implementation(projects.core.datastore)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)
    // DraftRepositoryTest builds a real preferences DataStore.
    testImplementation(libs.androidx.datastore.preferences)
}
