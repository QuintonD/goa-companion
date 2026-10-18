plugins {
    alias(libs.plugins.goa.android.feature)
}

dependencies {
    implementation(projects.core.presentation)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx) // FileProvider for "Share stats page"
    implementation(projects.core.presentation)
}
