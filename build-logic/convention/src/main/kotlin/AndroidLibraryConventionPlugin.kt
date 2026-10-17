import com.android.build.api.dsl.LibraryExtension
import dev.quinton.goa.buildlogic.TARGET_SDK
import dev.quinton.goa.buildlogic.configureKotlinAndroid
import dev.quinton.goa.buildlogic.configureQuality
import dev.quinton.goa.buildlogic.lib
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            testOptions.targetSdk = TARGET_SDK
            lint.targetSdk = TARGET_SDK
            // Namespace derived from the module path: :core:data -> dev.quinton.goa.core.data
            namespace = "dev.quinton.goa" + path.replace(':', '.').replace('-', '_')
        }
        configureQuality()
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
