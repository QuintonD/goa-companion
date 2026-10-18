plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // BackHandler: Back mid-round asks before leaving (the round's results would go).
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
