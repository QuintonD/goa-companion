plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    // The in-memory sync transport (protocol tests here, sync feature tests elsewhere).
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.kotest.property)
    testFixturesApi(libs.kotlinx.coroutines.core)
}
