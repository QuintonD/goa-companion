plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
