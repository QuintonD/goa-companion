plugins {
    alias(libs.plugins.goa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

// The game server of online play (ADR-056, docs/ONLINE.md). A root-build module, unlike server/relay,
// because it runs :core:game. deploy/game builds the distribution on the host under goa-gradle; the
// Docker image only copies it (ADR-038: no compile inside BuildKit, outside the memory cap).
application {
    mainClass.set("dev.quinton.goa.server.game.MainKt")
    applicationName = "goa-game"
}

// The rules need the catalog and the maps; none of the 36 MB of official images.
distributions {
    main {
        contents {
            from(rootProject.file("app/src/main/assets/catalog")) { into("assets/catalog") }
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.game)
    implementation(projects.core.online)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.property)
}
