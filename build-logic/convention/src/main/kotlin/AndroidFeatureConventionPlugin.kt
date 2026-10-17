import dev.quinton.goa.buildlogic.lib
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** A feature module: library + Compose + Hilt + Navigation 3, with the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("goa.android.library")
        pluginManager.apply("goa.android.compose")
        pluginManager.apply("goa.android.hilt")
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:navigation"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
