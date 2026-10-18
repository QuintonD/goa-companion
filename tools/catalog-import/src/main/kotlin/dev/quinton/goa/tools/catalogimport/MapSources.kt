package dev.quinton.goa.tools.catalogimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The community map files of goa2-backend (`src/goa2/data/maps/<map>.json`) at the pinned SHA.
 * Integer cube coordinates (q + r + s = 0, axial q/r), one entry per nonterrain space with its
 * zone and spawn tags. Lanes are lists of zone labels from Red's side to Blue's (labels equal the
 * zone ids in every pinned file); `battle_zones` names a lane's starting Battle Zone.
 */

@Serializable
data class BackendMap(
    /** Legacy single-lane form. */
    val lane: List<String>? = null,
    val lanes: Map<String, List<String>>? = null,
    @SerialName("battle_zones") val battleZones: Map<String, String> = emptyMap(),
    @SerialName("zone_definitions") val zoneDefinitions: List<BackendZone>,
    @SerialName("hex_map") val hexMap: List<BackendHex>,
) {
    /** Lane id → zone ids, in file order. */
    val allLanes: Map<String, List<String>>
        get() {
            val labels = zoneDefinitions.associate { (it.label ?: it.id) to it.id }
            val raw = lanes ?: lane?.let { mapOf("lane_1" to it) } ?: emptyMap()
            return raw.mapValues { (_, zones) -> zones.map { labels[it] ?: it } }
        }
}

@Serializable
data class BackendZone(val id: String, val label: String? = null, val color: String? = null)

@Serializable
data class BackendHex(
    val q: Int,
    val r: Int,
    val s: Int? = null,
    @SerialName("zone_id") val zoneId: String,
    val tags: List<String> = emptyList(),
)

/**
 * A map the importer ships. [laneOrder] lists the source's lane ids in the order we number them
 * (default: file order); [evidence] says why the map is an official Wolff Designa board.
 */
data class MapSpec(
    val id: String,
    val file: String,
    val name: String,
    val evidence: String,
    val laneOrder: List<String>? = null,
)

/** A source map we deliberately leave out, with the reason (REPORT.md). */
data class ExcludedMap(val file: String, val reason: String)

object MapCatalog {
    const val DIR = "src/goa2/data/maps"

    private const val STORE = "wolffdesigna.com/products.json (store, read 2026-10-19)"

    /** In app order: the core box's single-lane map first (PRODUCT.md 8), then its double-lane map. */
    val MAPS = listOf(
        MapSpec(
            id = "forgotten-island",
            file = "forgotten_island.json",
            name = "Forgotten Island",
            evidence = "Core box single-lane map (rulebook p.5-6 setup images). Name: Wolff Designa neoprene mat " +
                "\"Forgotten Island\" / \"Magma and Ice\" - \"Forgotten Island side of the mat is the same as one " +
                "of the maps included in the base game\" ($STORE)",
        ),
        MapSpec(
            id = "across-the-river",
            file = "across_the_river.json",
            name = "Across the River",
            evidence = "Core box double-lane map of the 2nd printing (rulebook p.20 image). Name: Wolff Designa " +
                "neoprene mat \"Narrow passages\" / \"Across the River\" - \"Across the river side of the mat is " +
                "the same as one of the maps included in the base game\" ($STORE)",
            // Rulebook p.20 labels the starting Battle Zone of the source's lane_2 "Battle Zone 1".
            laneOrder = listOf("lane_2", "lane_1"),
        ),
        MapSpec(
            id = "narrow-passages",
            file = "narrow_passages.json",
            name = "Narrow Passages",
            evidence = "Official single-lane map on the Wolff Designa neoprene mat \"Narrow passages\" / " +
                "\"Across the River\" ($STORE); not in the core box",
        ),
        MapSpec(
            id = "magma-and-ice",
            file = "magma_and_ice.json",
            name = "Magma and Ice",
            evidence = "Official double-lane map on the Wolff Designa neoprene mat \"Forgotten Island\" / " +
                "\"Magma and Ice\" ($STORE); the 1st printing's core-box double-lane map (BGG thread 3541070)",
        ),
    )

    val EXCLUDED = listOf(
        ExcludedMap(
            "vexing_cliffs.json",
            "Not an official map: Wolff Designa sells exactly four maps (Forgotten Island, Magma and Ice, " +
                "Narrow Passages, Across the River); added to goa2-backend on 2026-07-28 as \"Desert Cliffs\"; " +
                "2 hero spawn points per team and 3 Melee / 2 Ranged starting minions contradict p.6",
        ),
        ExcludedMap("vexing_cliffs_v2.json", "A variant of vexing_cliffs.json (same reasons)"),
    )
}
