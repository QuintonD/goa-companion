plugins {
    alias(libs.plugins.goa.android.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    api(projects.core.data)
    // The fakes and TestCatalog (multiplatform, so the web app's tests use them too).
    api(projects.core.dataTesting)
    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    // BundledAssetImages: official images for screenshot tests.
    implementation(libs.coil.compose)
}
