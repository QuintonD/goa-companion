plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // BackHandler: Back on the Heroes tab clears a typed search first.
    implementation(libs.androidx.activity.compose)
}
