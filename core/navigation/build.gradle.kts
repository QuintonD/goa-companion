plugins {
    alias(libs.plugins.goa.android.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
