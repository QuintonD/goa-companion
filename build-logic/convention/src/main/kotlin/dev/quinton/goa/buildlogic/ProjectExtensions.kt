package dev.quinton.goa.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

const val COMPILE_SDK = 37
const val TARGET_SDK = 37
const val MIN_SDK = 26
