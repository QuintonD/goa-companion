plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // Group sync (ADR-025): Nearby Connections moves the data phone to phone; the Google code
    // scanner reads invite QR codes without the CAMERA permission; ZXing draws them.
    implementation(libs.play.services.nearby)
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(testFixtures(projects.core.domain))
}
