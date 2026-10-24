plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // BackHandler: Back leaves a tool (Add, Measure) before it leaves the board.
    implementation(libs.androidx.activity.compose)
    // Play against the computer (ADR-035): the engine's public contract.
    implementation(projects.core.game)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
