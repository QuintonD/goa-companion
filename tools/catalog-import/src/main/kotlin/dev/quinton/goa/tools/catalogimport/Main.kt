package dev.quinton.goa.tools.catalogimport

import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json

/**
 * Catalog import (`./gradlew :tools:catalog-import:run`, see .claude/skills/import-catalog).
 * Flags (pass with `--args="..."`): `--offline` (cache only), `--no-images` (data only).
 * Paths are relative to the repository root (the run task's working directory).
 */
fun main(args: Array<String>) {
    val offline = "--offline" in args
    val withImages = "--no-images" !in args
    val root = File(".").canonicalFile
    val toolDir = File(root, "tools/catalog-import")
    require(File(toolDir, "sources.lock.json").isFile) {
        "Run from the repository root (missing tools/catalog-import/sources.lock.json)"
    }
    val assetsDir = File(root, "app/src/main/assets")

    val strict = Json { ignoreUnknownKeys = false }
    val lock = strict.decodeFromString(SourcesLock.serializer(), File(toolDir, "sources.lock.json").readText())
    val overrides = strict.decodeFromString(Overrides.serializer(), File(toolDir, "overrides.json").readText())
    overrides.validate()

    val cache = SourceCache(File(toolDir, "cache"), offline)
    val result = Importer(lock, cache, overrides).run(withImages)
    if (result.unknownIcons.isNotEmpty()) {
        System.err.println(
            "Unknown icon tokens: ${result.unknownIcons}. Map them in TextMarkup.iconName or extend CardText.ICONS.",
        )
        exitProcess(2)
    }

    val output = Output(assetsDir)
    val version = output.writeCatalog(result)
    if (withImages) {
        val manifest = File(toolDir, "cache/assets-manifest.tsv")
        val encoded = AssetWriter(assetsDir, cache, manifest).write(result.assetSources)
        output.writeSources(result)
        println("Images: ${result.assetSources.size} total, $encoded re-encoded")
    }
    ReportWriter(result, version, lock, overrides).write(File(toolDir, "REPORT.md"))

    val cards = result.heroes.sumOf { it.allCards.size }
    val verified = result.heroes.count { it.verified }
    val unresolved = result.reports.sumOf { it.unresolved.size }
    println(
        "catalog-import: ${result.heroes.size} heroes, $cards cards, dataVersion $version, $verified verified, " +
            "$unresolved unresolved disagreement(s), ${cache.downloads} download(s)",
    )
}
