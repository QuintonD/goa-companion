package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class MapImportTest {
    private val spec = MapSpec("test-map", "test.json", "Test", evidence = "test")

    /**
     * A small single-lane map along q: RedBase (q 0..1), RedBeach (2..3), Mid (4..6), BlueBeach
     * (7..8), BlueBase (9..10), five rows (r -2..2); Mid's (5,0) is a hole; a Jungle space at
     * (3,-3) touches RedBeach only.
     */
    private fun source(lane: List<String> = listOf("RedBase", "RedBeach", "Mid", "BlueBeach", "BlueBase")): BackendMap {
        val hexes = mutableListOf<BackendHex>()
        fun zone(q: Int) = when (q) {
            in 0..1 -> "RedBase"
            in 2..3 -> "RedBeach"
            in 4..6 -> "Mid"
            in 7..8 -> "BlueBeach"
            else -> "BlueBase"
        }
        for (q in 0..10) for (r in -2..2) if (!(q == 5 && r == 0)) hexes += BackendHex(q, r, zoneId = zone(q))
        hexes += BackendHex(3, -3, zoneId = "Jungle")
        val tags = mapOf(
            (0 to -1) to "RedHeroSpawn", (0 to 0) to "RedHeroSpawn", (0 to 1) to "RedHeroSpawn",
            (10 to -1) to "BlueHeroSpawn", (10 to 0) to "BlueHeroSpawn", (10 to 1) to "BlueHeroSpawn",
            (4 to -2) to "RedMeleeSpawn", (4 to -1) to "RedMeleeSpawn", (4 to 0) to "RedMeleeSpawn",
            (4 to 1) to "RedMeleeSpawn", (4 to 2) to "RedRangedSpawn", (5 to -2) to "RedHeavySpawn",
            (6 to -2) to "BlueMeleeSpawn", (6 to -1) to "BlueMeleeSpawn", (6 to 0) to "BlueMeleeSpawn",
            (6 to 1) to "BlueMeleeSpawn", (6 to 2) to "BlueRangedSpawn", (5 to 2) to "BlueHeavySpawn",
            (2 to 0) to "RedMeleeSpawn", (3 to 0) to "BlueMeleeSpawn", (7 to 0) to "BlueMeleeSpawn",
            (8 to 0) to "RedMeleeSpawn",
        )
        val tagged = hexes.map { h -> tags[h.q to h.r]?.let { h.copy(tags = listOf(it)) } ?: h }
        val zones = listOf("RedBase", "RedBeach", "Mid", "BlueBeach", "BlueBase", "Jungle").map { BackendZone(it) }
        return BackendMap(lane = lane, zoneDefinitions = zones, hexMap = tagged)
    }

    private fun convert(source: BackendMap, override: MapOverride? = null, check: MapCheck? = null) =
        MapImport.convert(spec, source, override, check, emptyList(), printing = 2)

    @Test
    fun `zones get neutral ids, thrones their team, the lane runs red to blue`() {
        val map = convert(source()).map
        assertThat(map.zones.map { it.id }).containsExactly(
            "red-throne",
            "lane-1-1",
            "lane-1-2",
            "lane-1-3",
            "blue-throne",
            "red-side",
        ).inOrder()
        assertThat(
            map.zones.first {
                it.id == "red-throne"
            }.let { it.kind to it.team },
        ).isEqualTo(ZoneKind.THRONE to Team.RED)
        assertThat(map.zones.first { it.id == "red-side" }.kind).isEqualTo(ZoneKind.SIDE)
        val lane = map.lanes.single()
        assertThat(lane.id).isEqualTo("lane-1")
        assertThat(
            lane.zones,
        ).containsExactly("red-throne", "lane-1-1", "lane-1-2", "lane-1-3", "blue-throne").inOrder()
        assertThat(lane.startingBattleZone).isEqualTo("lane-1-2")
    }

    @Test
    fun `a lane listed from blue to red is reversed`() {
        val map = convert(source(lane = listOf("BlueBase", "BlueBeach", "Mid", "RedBeach", "RedBase"))).map
        assertThat(map.lanes.single().zones.first()).isEqualTo("red-throne")
        assertThat(map.spaces.first { it.q == 2 && it.r == 0 }.zone).isEqualTo("lane-1-1")
    }

    @Test
    fun `a lane that misses a throne fails loudly`() {
        assertThrows(IllegalStateException::class.java) {
            convert(source(lane = listOf("RedBase", "RedBeach", "Mid", "BlueBeach")))
        }
    }

    @Test
    fun `spawn tags become spawn points and enclosed gaps become terrain`() {
        val map = convert(source()).map
        assertThat(map.spaces.first { it.q == 5 && it.r == -2 }.spawn).isEqualTo(SpawnPoint(Team.RED, SpawnKind.HEAVY))
        assertThat(map.terrain).containsExactly(Hex(5, 0))
        assertThat(map.spaces.map { it.hex }).containsNoDuplicates()
    }

    @Test
    fun `enclosed terrain ignores gaps open to the outside`() {
        val ring = (HexDirections.map { (dq, dr) -> Hex(dq, dr) } + Hex(5, 5)).toSet()
        assertThat(MapImport.enclosedTerrain(ring)).containsExactly(Hex(0, 0))
    }

    @Test
    fun `checks pass for a map that follows p6 and fail when a starting minion is missing`() {
        val good = convert(source())
        assertThat(good.findings.filter { it.required && !it.pass }).isEmpty()
        val bad = convert(source(), MapOverride(spawns = mapOf("5,-2" to null), reason = "r", evidence = "e"))
        val failed = bad.findings.filter { it.required && !it.pass }.map { it.check }
        assertThat(failed.single()).contains("p.6 step 10")
        assertThat(bad.map.verified).isFalse()
    }

    @Test
    fun `verified needs both the rulebook checks and the space-by-space record`() {
        assertThat(convert(source()).map.verified).isFalse()
        assertThat(convert(source(), check = MapCheck("overlay matches", "2026-10-19")).map.verified).isTrue()
    }

    @Test
    fun `p8 pre-throne comparison is reported but not required`() {
        val findings = convert(source()).findings.filter { it.check.startsWith("p.8") }
        assertThat(findings).hasSize(2)
        assertThat(findings.none { it.required }).isTrue()
    }

    @Test
    fun `overrides change spawns, remove spaces and are logged`() {
        val override = MapOverride(
            spawns = mapOf("3,1" to "red-ranged", "2,0" to null),
            removeSpaces = listOf("8,1"),
            reason = "r",
            evidence = "e",
        )
        val report = convert(source(), override)
        val spaces = report.map.spaces.associateBy { it.hex }
        assertThat(spaces.getValue(Hex(3, 1)).spawn).isEqualTo(SpawnPoint(Team.RED, SpawnKind.RANGED))
        assertThat(spaces.getValue(Hex(2, 0)).spawn).isNull()
        assertThat(Hex(8, 1) in spaces).isFalse()
        assertThat(report.overrides).hasSize(3)
    }

    @Test
    fun `an override on a hex the source lacks is rejected`() {
        val override = MapOverride(spawns = mapOf("40,40" to "red-melee"), reason = "r", evidence = "e")
        assertThrows(IllegalArgumentException::class.java) { convert(source(), override) }
    }

    @Test
    fun `spawn override values map to source tags`() {
        assertThat(MapOverride.spawn("blue-heavy")).isEqualTo("BlueHeavySpawn")
        assertThrows(IllegalArgumentException::class.java) { MapOverride.spawn("green-melee") }
        assertThat(MapOverride.hex(" -3, 7")).isEqualTo(-3 to 7)
    }

    @Test
    fun `an unknown tag in the source fails loudly`() {
        val src = source().let { s ->
            s.copy(
                hexMap = s.hexMap.map {
                    if (it.q == 3 &&
                        it.r == 1
                    ) {
                        it.copy(tags = listOf("Turret"))
                    } else {
                        it
                    }
                },
            )
        }
        assertThrows(IllegalStateException::class.java) { convert(src) }
    }

    @Test
    fun `lanes and battle zones parse from the multi-lane form`() {
        val text = """
            {"lanes": {"lane_1": ["A", "B"]}, "battle_zones": {"lane_1": "B"},
             "zone_definitions": [{"id": "A", "label": "A", "color": "#fff"}, {"id": "B", "label": "B"}],
             "hex_map": [{"q": 0, "r": 0, "s": 0, "zone_id": "A", "tags": ["RedHeroSpawn"]}]}
        """.trimIndent()
        val map = Json.decodeFromString(BackendMap.serializer(), text)
        assertThat(map.allLanes).isEqualTo(mapOf("lane_1" to listOf("A", "B")))
        assertThat(map.battleZones).isEqualTo(mapOf("lane_1" to "B"))
    }

    @Test
    fun `similarity and homography fits recover a known transform`() {
        val anchors = listOf(0 to 0, 4 to 0, 0 to 4, -3 to 5).map { (q, r) ->
            val p = MapOverlay.plane(q, r)
            // Scale 20, rotate 90°, shift (100, 50): x' = -20 y + 100, y' = 20 x + 50.
            listOf(q.toDouble(), r.toDouble(), -20 * p[1] + 100, 20 * p[0] + 50)
        }
        val (similarity, err1) = MapOverlay.fit(anchors, "similarity")
        val (homography, err2) = MapOverlay.fit(anchors + listOf(listOf(2.0, 2.0) + transform(2, 2)), "homography")
        assertThat(err1).isLessThan(1e-6)
        assertThat(err2).isLessThan(1e-6)
        val p = MapOverlay.plane(7, -2)
        assertThat(similarity.apply(p[0], p[1])[0]).isWithin(1e-6).of(-20 * p[1] + 100)
        assertThat(homography.apply(p[0], p[1])[1]).isWithin(1e-6).of(20 * p[0] + 50)
    }

    private fun transform(q: Int, r: Int): List<Double> {
        val p = MapOverlay.plane(q, r)
        return listOf(-20 * p[1] + 100, 20 * p[0] + 50)
    }

    @Test
    fun `the maps section replaces itself in REPORT md`() {
        val dir = createTempDirectory("report").toFile()
        val report = File(dir, "REPORT.md")
        report.writeText("# Report\n\n## Heroes\nx\n\n## Maps\nold\n")
        MapStage.replaceSection(report, "## Maps\nnew\n")
        assertThat(report.readText()).isEqualTo("# Report\n\n## Heroes\nx\n\n## Maps\nnew\n")
        report.writeText("# Report\n\n## Heroes\nx\n")
        MapStage.replaceSection(report, "## Maps\nnew\n")
        assertThat(report.readText()).isEqualTo("# Report\n\n## Heroes\nx\n\n## Maps\nnew\n")
        dir.deleteRecursively()
    }

    private companion object {
        val HexDirections = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
    }
}
