plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    implementation(projects.core.datastore)
    // Screen-state builders shared with the hub's web timer (ADR-030 §1); app's shell reads TimerText.
    api(projects.core.presentation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
