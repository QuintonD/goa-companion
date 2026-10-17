plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.kotest.property)
}
