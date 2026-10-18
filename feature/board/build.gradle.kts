plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // BackHandler: Back leaves a tool (Add, Measure) before it leaves the board.
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
