plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("dev.quinton.goa.tools.catalogimport.MainKt")
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.scrimage.core)
    implementation(libs.scrimage.webp)
}

tasks.named<JavaExec>("run") {
    // Paths are resolved against the repository root.
    workingDir = rootProject.projectDir
}
