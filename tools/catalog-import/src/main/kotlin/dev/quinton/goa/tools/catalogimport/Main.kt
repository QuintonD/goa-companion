package dev.quinton.goa.tools.catalogimport

import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json

/**
 * Catalog import (`./gradlew :tools:catalog-import:run`, see .claude/skills/import-catalog).
 * Flags (pass with `--args="..."`): `--offline` (cache only), `--no-images` (data only),
 * `--rules-only` (only the rules-reference drift check; `--rules-dump=<dir>` also writes each
 * rulebook page's text layer to `<dir>/p<printed page>.txt`), `--maps-only` (only the board maps:
 * `catalog/maps`, the overlay images in `cache/maps/` and the "Maps" section of REPORT.md).
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
    if ("--rules-only" in args) {
        val dump = args.firstOrNull { it.startsWith("--rules-dump=") }?.substringAfter('=')?.let(::File)
        exitProcess(if (checkRules(root, lock, cache, dump)) 0 else EXIT_RULES)
    }
    if ("--maps-only" in args) {
        importMaps(lock, cache, overrides, assetsDir, File(toolDir, "REPORT.md"))
        return
    }
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
        val rulebook = lock.document("rulebook")
        val pdf = cache.document(rulebook)
        val pdfImages = RulebookImages.extract(pdf, cache.output("documents/rulebook/${rulebook.sha256}-images"))
        val extracted = pdfImages.entries.associate { (spec, file) ->
            spec.out to ExtractedAsset(
                key = "rulebook@${rulebook.sha256}:p${spec.pdfPage}/obj${spec.obj}",
                file = file,
                maxEdge = RULEBOOK_IMAGE_MAX_EDGE,
            )
        }
        val encoded = AssetWriter(assetsDir, cache, manifest).write(result.assetSources, extracted)
        output.writeSources(result, lock.documents, rulebook to pdfImages.keys.toList())
        println("Images: ${result.assetSources.size + extracted.size} total, $encoded re-encoded")
    }
    ReportWriter(result, version, lock, overrides).write(File(toolDir, "REPORT.md"))
    importMaps(lock, cache, overrides, assetsDir, File(toolDir, "REPORT.md"))

    val cards = result.heroes.sumOf { it.allCards.size }
    val verified = result.heroes.count { it.verified }
    val unresolved = result.reports.sumOf { it.unresolved.size }
    println(
        "catalog-import: ${result.heroes.size} heroes, $cards cards, dataVersion $version, $verified verified, " +
            "$unresolved unresolved disagreement(s), ${cache.downloads} download(s)",
    )
    if (!checkRules(root, lock, cache, dumpDir = null)) exitProcess(EXIT_RULES)
}

private const val EXIT_RULES = 3

/** The maps stage: map JSON + index, overlay images in the cache, the "Maps" section of REPORT.md. */
private fun importMaps(lock: SourcesLock, cache: SourceCache, overrides: Overrides, assetsDir: File, report: File) {
    val maps = MapStage(lock, cache, overrides).run()
    MapStage.write(assetsDir, maps.map { it.map })
    MapStage.replaceSection(report, MapStage.report(maps, lock))
    maps.forEach { r ->
        val failed = r.findings.filter { it.required && !it.pass }
        println(
            "maps: ${r.map.id} ${r.map.spaces.size} spaces, ${r.map.terrain.size} terrain, " +
                "verified=${r.map.verified}" + if (failed.isEmpty()) "" else ", ${failed.size} failed check(s)",
        )
    }
}

/**
 * Rules-reference drift guard (docs/DECISIONS.md, "Rules reference"): every excerpt in
 * `rules-reference.json` must still be on its page of the pinned rulebook. Prints the problems;
 * true when there are none.
 */
private fun checkRules(root: File, lock: SourcesLock, cache: SourceCache, dumpDir: File?): Boolean {
    val problems = RulesReferenceCheck.run(root, lock, cache, dumpDir)
    if (problems.isEmpty()) {
        println("rules-reference: every excerpt matches the pinned rulebook")
        return true
    }
    System.err.println("rules-reference: ${problems.size} problem(s) against the pinned rulebook:")
    problems.forEach { System.err.println("  - $it") }
    return false
}
