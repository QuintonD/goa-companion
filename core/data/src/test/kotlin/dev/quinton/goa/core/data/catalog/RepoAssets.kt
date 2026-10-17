package dev.quinton.goa.core.data.catalog

import java.io.File

/** The real bundled assets, read straight from the repository (`app/src/main/assets`). */
object RepoAssets {
    val root: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app/src/main/assets").isDirectory }
            ?: error("Repository root with app/src/main/assets not found from ${System.getProperty("user.dir")}")
    }

    val assetsDir: File get() = File(root, "app/src/main/assets")

    fun file(path: String): File = File(assetsDir, path)

    val assets = CatalogAssets { path -> file(path).inputStream() }
}
