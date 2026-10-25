plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    // The screens' rows and words, shared with the web app (ADR-043).
    implementation(projects.core.groupPresentation)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx) // FileProvider for "Share stats page"
}
