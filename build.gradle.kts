import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Kotlin/JS (web app, ADR-043): Node.js and Yarn are resolved from the repositories declared in
// settings.gradle.kts; unsetting the download URLs stops the Kotlin plugin adding project repositories.
allprojects {
    plugins.withType<NodeJsRootPlugin> { the<NodeJsEnvSpec>().downloadBaseUrl.set(null as String?) }
    plugins.withType<NodeJsPlugin> { the<NodeJsEnvSpec>().downloadBaseUrl.set(null as String?) }
    plugins.withType<YarnPlugin> { the<YarnRootEnvSpec>().downloadBaseUrl.set(null as String?) }
}
