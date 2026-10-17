import dev.quinton.goa.buildlogic.configureKotlin
import dev.quinton.goa.buildlogic.configureQuality
import dev.quinton.goa.buildlogic.lib
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module (core:model, core:domain, tools). No android.* allowed. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        configureQuality()
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
