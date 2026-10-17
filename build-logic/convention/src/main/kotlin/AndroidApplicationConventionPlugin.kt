import com.android.build.api.dsl.ApplicationExtension
import dev.quinton.goa.buildlogic.TARGET_SDK
import dev.quinton.goa.buildlogic.configureKotlinAndroid
import dev.quinton.goa.buildlogic.configureQuality
import dev.quinton.goa.buildlogic.libs
import dev.quinton.goa.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = TARGET_SDK
        }
        configureQuality()
    }
}
