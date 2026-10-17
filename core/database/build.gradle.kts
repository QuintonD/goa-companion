plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.goa.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
