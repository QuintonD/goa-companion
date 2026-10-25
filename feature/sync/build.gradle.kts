plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // The screens' rows and words, shared with the web app (ADR-043).
    implementation(projects.core.groupPresentation)
    // Group sync (ADR-025): Nearby Connections moves the data phone to phone; the Google code
    // scanner reads invite QR codes without the CAMERA permission; ZXing draws them.
    implementation(libs.play.services.nearby)
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    // The group's sync server (ADR-036): automatic sync in the background.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.domainTestfixtures)
    testImplementation(libs.androidx.work.testing)
}
