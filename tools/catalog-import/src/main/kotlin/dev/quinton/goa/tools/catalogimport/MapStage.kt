package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.SourceRef
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `catalog/maps/index.json`: map ids in app order (the core single-lane map first). */
@Serializable
data class MapIndex(val maps: List<String>)

/**
 * The maps stage (`--maps-only` runs just this): reads the pinned goa2-backend maps, converts
 * them ([MapImport]), renders the overlay check images into `cache/maps/`, writes
 * `assets/catalog/maps/<id>.json` + `index.json` and the "Maps" section of REPORT.md.
 */
class MapStage(lock: SourcesLock, private val cache: SourceCache, private val overrides: Overrides) {
    private val backend = lock.source("goa2-backend")
    private val json = Json { ignoreUnknownKeys = false }

    fun run(): List<MapReport> = MapCatalog.MAPS.map { spec ->
        val path = "${MapCatalog.DIR}/${spec.file}"
        val source = json.decodeFromString(BackendMap.serializer(), cache.text(backend, path))
        val arts = overrides.mapArt[spec.id].orEmpty()
        val refs = listOf(SourceRef("goa2-backend ${spec.file}", backend.blobUrl(path), backend.pinned)) +
            arts.map { SourceRef("Checked against: ${it.name}", it.url) }
        val report = MapImport.convert(
            spec = spec,
            source = source,
            override = overrides.maps[spec.id],
            check = overrides.mapChecks[spec.id],
            sources = refs,
            printing = Importer.PRINTING,
        )
        val overlays = arts.mapIndexed { i, art ->
            val ext = art.url.substringBefore('?').substringAfterLast('.', "img").take(EXT_MAX)
            val file = cache.url(art.url, "maps/art/${spec.id}-$i.$ext")
            val out = cache.output("maps/${spec.id}-$i-overlay.png")
            val err = MapOverlay.render(report.map, file, art, out)
            "`tools/catalog-import/cache/maps/${out.name}` on ${art.name} (worst anchor ${"%.1f".format(err)} px)"
        }
        report.copy(overlays = overlays)
    }

    companion object {
        private const val EXT_MAX = 4

        @OptIn(ExperimentalSerializationApi::class)
        private val pretty = Json {
            prettyPrint = true
            prettyPrintIndent = " "
            encodeDefaults = false
            explicitNulls = false
        }

        /** Writes the map files and the index; deletes map files no longer produced. */
        fun write(assetsDir: File, maps: List<BoardMap>) {
            val dir = File(assetsDir, "catalog/maps")
            dir.mkdirs()
            val files = maps.associate { "${it.id}.json" to pretty.encodeToString(BoardMap.serializer(), it) + "\n" }
            dir.listFiles { f -> f.extension == "json" && f.name != "index.json" }.orEmpty()
                .filter { it.name !in files }.forEach { it.delete() }
            files.forEach { (name, text) -> File(dir, name).writeText(text) }
            File(dir, "index.json").writeText(
                pretty.encodeToString(
                    MapIndex.serializer(),
                    MapIndex(
                        maps.map {
                            it.id
                        },
                    ),
                ) + "\n",
            )
        }

        /** The "## Maps" section of REPORT.md. */
        fun report(reports: List<MapReport>, lock: SourcesLock): String = buildString {
            val src = lock.source("goa2-backend")
            appendLine("## Maps")
            appendLine(
                "Source: `${src.repo}` @ `${src.sha.take(12)}` (`${MapCatalog.DIR}`), corrected in `overrides.json`",
            )
            appendLine(
                "(`maps`). `verified` = every required rulebook check passes **and** the overlay on official art",
            )
            appendLine(
                "was compared space by space (`mapChecks`). Overlays: `tools/catalog-import/cache/maps/`.",
            )
            appendLine()
            appendLine("| Map | Lanes | Spaces | Terrain | Rulebook checks | Verified |")
            appendLine("|---|---|---|---|---|---|")
            reports.forEach { r ->
                val m = r.map
                val req = r.findings.filter { it.required }
                appendLine(
                    "| ${m.name} (`${m.id}`) | ${m.lanes.size} | ${m.spaces.size} | ${m.terrain.size} | " +
                        "${req.count { it.pass }}/${req.size} | ${if (m.verified) "yes" else "**no**"} |",
                )
            }
            appendLine()
            reports.forEach { r ->
                appendLine("### ${r.map.name} (`${r.map.id}`)")
                appendLine("- Official: ${r.spec.evidence}.")
                appendLine("- Zones: " + r.map.zones.joinToString { "`${it.id}`" } + ".")
                appendLine(
                    "- Lanes: " +
                        r.map.lanes.joinToString { "`${it.id}` (Battle Zone starts in `${it.startingBattleZone}`)" } +
                        ".",
                )
                r.findings.forEach { f ->
                    val mark = when {
                        f.pass -> "pass"
                        f.required -> "**FAIL**"
                        else -> "note"
                    }
                    appendLine("- $mark — ${f.check}: ${f.detail}")
                }
                if (r.overrides.isEmpty()) appendLine("- Overrides: none.")
                r.overrides.forEach { appendLine("- Override: $it") }
                r.overlays.forEach { appendLine("- Overlay: $it") }
                val check = r.check
                if (check != null) {
                    appendLine("- Space-by-space check (${check.checked}): ${check.evidence}")
                } else {
                    val why = if (r.overrides.isEmpty()) {
                        "the community data hasn't been matched space by space with official art"
                    } else {
                        "the community data disagreed with the art and was corrected from the art alone (overrides " +
                            "above), so the corrected spaces rest on one source"
                    }
                    appendLine("- **Not verified**: $why. The map ships badged Unverified.")
                }
                appendLine()
            }
            appendLine("### Not shipped")
            MapCatalog.EXCLUDED.forEach { appendLine("- `${it.file}`: ${it.reason}.") }
            appendLine()
        }

        /** Replaces (or appends) the "## Maps" section of an existing REPORT.md. */
        fun replaceSection(report: File, section: String) {
            val text = if (report.isFile) report.readText() else ""
            val start = text.indexOf("\n## Maps\n")
            val updated = if (start < 0) {
                text.trimEnd() + "\n\n" + section
            } else {
                val next = text.indexOf("\n## ", start + 1).takeIf { it >= 0 } ?: text.length
                text.substring(0, start + 1) + section + text.substring(next).trimStart('\n').let {
                    if (it.isEmpty()) "" else "\n" + it
                }
            }
            report.writeText(updated.trimEnd() + "\n")
        }
    }
}
