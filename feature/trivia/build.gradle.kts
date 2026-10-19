plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // Screen-state builders shared with the hub web app (ADR-030 §1).
    implementation(projects.core.presentation)
    // BackHandler: Back mid-round asks before leaving (the round's results would go).
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

composeCompiler {
    // The shared screen states from :core:presentation are immutable (they were @Immutable here).
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}
