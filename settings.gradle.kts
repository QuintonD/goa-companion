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
        // Kotlin/JS toolchain for the web app (ADR-043). FAIL_ON_PROJECT_REPOS forbids the Kotlin
        // plugin adding these itself, so its download URLs are unset in the root build script.
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
include(":core:data-contracts")
include(":core:data-testing")
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
include(":web:app")
