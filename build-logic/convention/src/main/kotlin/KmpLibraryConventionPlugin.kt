import dev.detekt.gradle.extensions.DetektExtension
import dev.quinton.goa.buildlogic.configureKotlin
import dev.quinton.goa.buildlogic.configureQuality
import dev.quinton.goa.buildlogic.lib
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

/**
 * Pure Kotlin module shared by the Android app (jvm) and the web app (js), ADR-043: core:model,
 * core:domain, core:game. No android.* and no java.* in common code.
 *
 * The existing directories stay where they are so the branches editing them still merge:
 * `src/main/kotlin` is commonMain and `src/test/kotlin` is jvmTest (the big JVM suites, JUnit 4 +
 * Truth). `src/main/resources` is not packaged: shared code can't read classpath resources, so a
 * module embeds what it needs ([dev.quinton.goa.buildlogic.EmbedTextFiles]). Code that needs a
 * platform goes in `src/jvmMain/kotlin` / `src/jsMain/kotlin`; tests that must agree on both
 * targets go in `src/commonTest/kotlin` and run on the JVM and on Node.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            js {
                // The web app bundles these through its own webpack build; tests run on Node
                // (no Karma, no headless Chrome). Seeded AI games need more than mocha's 2 s.
                browser {
                    testTask { enabled = false }
                }
                nodejs {
                    testTask { useMocha { timeout = "1800s" } }
                }
                binaries.library()
            }
            compilerOptions {
                freeCompilerArgs.addAll("-Xconsistent-data-class-copy-visibility", "-Xexpect-actual-classes")
                optIn.addAll(
                    "kotlin.RequiresOptIn",
                    "kotlin.uuid.ExperimentalUuidApi",
                    "kotlin.time.ExperimentalTime",
                    "kotlin.io.encoding.ExperimentalEncodingApi",
                    "kotlinx.serialization.ExperimentalSerializationApi",
                )
            }
            sourceSets.apply {
                named("commonMain") { kotlin.srcDir("src/main/kotlin") }
                named("commonTest") {
                    dependencies {
                        implementation(libs.lib("kotlin-test"))
                        implementation(libs.lib("kotlinx-coroutines-test"))
                    }
                }
                named("jvmTest") {
                    kotlin.srcDir("src/test/kotlin")
                    resources.srcDir("src/test/resources")
                    dependencies {
                        implementation(libs.lib("junit"))
                        implementation(libs.lib("truth"))
                    }
                }
            }
        }
        configureKotlin()
        // Shared tests read the shipped catalog from the repository, on the JVM and on Node.
        val root = rootDir.absolutePath
        tasks.withType<Test>().configureEach { environment(REPO_ROOT_ENV, root) }
        tasks.withType<KotlinJsTest>().configureEach { environment(REPO_ROOT_ENV, root) }
        // `:core:game:test` keeps meaning the JVM suites (scripts and skills use it); `allTests`
        // runs them and the shared tests on Node. `--tests` filters go to `jvmTest` / `jsNodeTest`.
        tasks.register("test") {
            group = "verification"
            description = "Runs the JVM tests (jvmTest), as before the module went multiplatform."
            dependsOn("jvmTest")
        }
        configureQuality()
        // detekt's default inputs are src/main and src/test; add the multiplatform source sets.
        extensions.configure<DetektExtension> {
            source.setFrom(
                listOf("main", "test", "commonMain", "commonTest", "jvmMain", "jvmTest", "jsMain", "jsTest")
                    .map { "src/$it/kotlin" },
            )
        }
    }
}

/** The repository's root directory, for tests that read `app/src/main/assets` (jvm and Node). */
private const val REPO_ROOT_ENV = "GOA_REPO_ROOT"
