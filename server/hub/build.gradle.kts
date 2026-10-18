plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

// The hub's own version: the distribution is goa-hub-<version>.zip (deploy/ expects that name).
version = "0.1.0"

// The group's hub (ADR-030): a Kotlin/JVM server running the same core:domain as the phones.
// `installDist` / `distZip` produce `goa-hub` (bin/goa-hub plus lib/); it needs only a Java 21 runtime.
application {
    applicationName = "goa-hub"
    mainClass.set("dev.quinton.goa.hub.MainKt")
    // Icons are rendered with Java2D; no display is ever needed.
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

distributions {
    main {
        distributionBaseName.set("goa-hub")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.presentation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.html)
    // The invite QR code on /group/invite, drawn as SVG on the server (pure Java, no AWT).
    implementation(libs.zxing.core)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
}

// The app's own files, packaged into the hub's resources at build time (no second copy in git):
// the catalog JSON and official images (served to signed-in devices only, ADR-006), the bundled
// OFL fonts with their licences, and our original crests, sigils and icons from design/.
val bundleAppFiles by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("generated/hub-resources"))
    from(rootProject.file("app/src/main/assets/catalog")) { into("goa-assets/catalog") }
    from(rootProject.file("app/src/main/assets/official")) { into("goa-assets/official") }
    from(rootProject.file("core/designsystem/src/main/res/font")) { into("static/fonts") }
    from(rootProject.file("third_party/fonts")) { into("static/fonts") }
    from(rootProject.file("design/avatars")) { into("static/crests") }
    from(rootProject.file("design/sigils")) { into("static/sigils") }
    from(rootProject.file("design/icons")) { into("static/icons") }
    from(rootProject.file("design/brand")) { into("static/brand") }
}

tasks.named<Jar>("jar") {
    manifest { attributes("Implementation-Title" to "goa-hub", "Implementation-Version" to project.version) }
}

sourceSets {
    main {
        resources.srcDir(bundleAppFiles)
    }
}

tasks.named<JavaExec>("run") {
    // `--args="serve --data D:\.goa-hub-dev --port 8080"` etc.; paths resolve from the repository root.
    workingDir = rootProject.projectDir
    jvmArgs("-Djava.awt.headless=true")
}
