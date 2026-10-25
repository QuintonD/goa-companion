pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Kotlin/JS toolchain (ADR-043): Node.js and Yarn distributions, nothing else.
        ivy("https://nodejs.org/dist/") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "guards-of-atlantis"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:domain")
include(":core:domain-testfixtures")
include(":core:game")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:testing")
include(":core:navigation")
include(":feature:catalog")
include(":feature:timer")
include(":feature:players")
include(":feature:draft")
include(":feature:settings")
include(":feature:rules")
include(":feature:sync")
include(":feature:trivia")
include(":feature:board")
include(":tools:catalog-import")
