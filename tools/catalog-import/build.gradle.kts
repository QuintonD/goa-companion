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
    implementation(libs.pdfbox)
}

tasks.named<JavaExec>("run") {
    // Paths are resolved against the repository root.
    workingDir = rootProject.projectDir
}

/**
 * The web app's list thumbnails (WebThumbnails.kt, ADR-043 "Addendum (R3 first load)"): downscaled
 * copies of the card scans and deck symbols in `app/src/main/assets/official`, which `:web:app` serves
 * at `official/thumbs/`. Cached by Gradle: they are made again only when an official image changes.
 */
val webThumbnails = tasks.register<JavaExec>("webThumbnails") {
    group = "web"
    description = "Makes the web app's small copies of the card scans and deck symbols."
    val official = rootProject.layout.projectDirectory.dir("app/src/main/assets/official")
    val output = layout.buildDirectory.dir("webThumbnails")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.quinton.goa.tools.catalogimport.WebThumbnailsKt")
    inputs.files(fileTree(official) { include("cards/*.webp", "heroes/*/symbol.webp") })
        .withPropertyName("officialImages").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output).withPropertyName("thumbnails")
    outputs.cacheIf { true }
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(official.asFile.absolutePath, output.get().asFile.absolutePath)
        },
    )
}
