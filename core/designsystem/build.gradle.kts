plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.compose)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
}
