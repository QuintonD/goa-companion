plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // Screen-state builders shared with the hub web app (ADR-030 §1).
    implementation(projects.core.presentation)
    // BackHandler: Back on the Heroes tab clears a typed search first.
    implementation(libs.androidx.activity.compose)
}
