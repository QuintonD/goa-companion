import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import dev.quinton.goa.buildlogic.lib
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose + Roborazzi screenshot testing. Apply after the application/library plugin.
 * Screenshot tests run on Robolectric (JVM): `recordRoborazziDebug` / `verifyRoborazziDebug`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        pluginManager.apply(libs.pluginId("roborazzi"))
        extensions.getByType<CommonExtension>().buildFeatures.compose = true
        // Baselines are committed next to the tests so verifyRoborazziDebug works on a clean checkout.
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            val bom = libs.lib("androidx-compose-bom")
            add("implementation", platform(bom))
            add("testImplementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-graphics"))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("roborazzi-junit-rule"))
            add("testImplementation", libs.lib("androidx-test-core"))
            add("testImplementation", libs.lib("androidx-test-ext-junit"))
        }
    }
}
