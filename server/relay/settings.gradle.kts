// Standalone build for the sync relay (ADR-036, docs/SYNC-SERVER.md). Deliberately NOT included in
// the root settings.gradle.kts: the root build needs the Android SDK, and the relay builds on a
// machine (or in a Docker image) without one. Versions are pinned in gradle/libs.versions.toml here,
// and the Gradle wrapper is a copy of the root one so the directory is a self-contained build context.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "goa-relay"
