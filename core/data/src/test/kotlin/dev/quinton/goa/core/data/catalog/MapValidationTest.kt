package dev.quinton.goa.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Validates the bundled board maps (`catalog/maps`, docs/DOMAIN.md "Board map JSON"; ADR-033)
 * against the schema, the rulebook's setup (p.3, p.6, p.20) and internal consistency. Decoded
 * with the catalog's strict JSON settings, like the app.
 */
class MapValidationTest {
    private val ids: List<String> by lazy {
        CatalogLoader.json.parseToJsonElement(RepoAssets.file("catalog/maps/index.json").readText())
            .jsonObject.getValue("maps").jsonArray.map { it.jsonPrimitive.content }
    }

    private val maps: List<BoardMap> by lazy {
        ids.map {
            CatalogLoader.json.decodeFromString(
                BoardMap.serializer(),
                RepoAssets.file("catalog/maps/$it.json").readText(),
            )
        }
    }

    private val directions = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))

    private fun neighbors(h: Hex) = directions.map { h + it }

    private fun zoneAdjacency(map: BoardMap): Map<String, Set<String>> {
        val zoneAt = map.spaces.associate { it.hex to it.zone }
        return map.zones.associate { zone ->
            zone.id to map.spaces.filter { it.zone == zone.id }
                .flatMap { s -> neighbors(s.hex).mapNotNull { zoneAt[it] } }
                .filterTo(HashSet()) { it != zone.id }
        }
    }

    @Test
    fun `index lists exactly the map files, ids unique and kebab-case, core single-lane map first`() {
        val files = RepoAssets.file("catalog/maps").listFiles { f -> f.extension == "json" && f.name != "index.json" }
            .orEmpty().map { it.nameWithoutExtension }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids.toSet()).isEqualTo(files.toSet())
        assertThat(ids).isNotEmpty()
        maps.forEach { m ->
            assertThat(m.id).matches("[a-z0-9]+(-[a-z0-9]+)*")
            assertThat(m.name).isNotEmpty()
        }
        assertThat(maps.map { it.id }).isEqualTo(ids)
        assertThat(maps.first().lanes).hasSize(1)
    }

    @Test
    fun `the core box maps ship verified`() {
        val verified = maps.filter { it.verified }.map { it.id }
        assertThat(verified).containsAtLeast("forgotten-island", "across-the-river")
        assertThat(maps.first().verified).isTrue()
    }

    @Test
    fun `strict decoding rejects an unknown key`() {
        val text = RepoAssets.file(
            "catalog/maps/${ids.first()}.json",
        ).readText().replaceFirst("{", "{\n \"unexpected\": 1,")
        assertThat(runCatching { CatalogLoader.json.decodeFromString(BoardMap.serializer(), text) }.isFailure).isTrue()
    }

    @Test
    fun `every map is 2nd printing data with its sources`() {
        maps.forEach { m ->
            assertWithMessage(m.id).that(m.printing).isEqualTo(2)
            assertWithMessage(m.id).that(m.sources).isNotEmpty()
            assertWithMessage(m.id).that(m.sources.first().url.orEmpty()).contains("goa2-backend")
        }
    }

    @Test
    fun `spaces are unique, in known Zones, and terrain is disjoint from them`() {
        maps.forEach { m ->
            val hexes = m.spaces.map { it.hex }
            assertWithMessage(m.id).that(hexes).containsNoDuplicates()
            val zoneIds = m.zones.map { it.id }
            assertWithMessage(m.id).that(zoneIds).containsNoDuplicates()
            m.spaces.forEach { assertWithMessage("${m.id} ${it.hex}").that(it.zone).isIn(zoneIds) }
            assertWithMessage(m.id).that(m.terrain).containsNoDuplicates()
            assertWithMessage(m.id).that(m.terrain.toSet().intersect(hexes.toSet())).isEmpty()
            zoneIds.forEach { z -> assertWithMessage("${m.id} $z").that(m.spaces.any { it.zone == z }).isTrue() }
        }
    }

    @Test
    fun `terrain holes are enclosed by spaces`() {
        maps.forEach { m ->
            val spaces = m.spaces.map { it.hex }.toSet()
            val terrain = m.terrain.toSet()
            m.terrain.forEach { h ->
                // Every neighbour of a hole is a space or another hole: holes never touch the open water.
                neighbors(h).forEach { n -> assertWithMessage("${m.id} $h").that(n in spaces || n in terrain).isTrue() }
            }
        }
    }

    @Test
    fun `Zone kinds - one Throne Zone per team, Lane Zones are exactly those in a Lane`() {
        maps.forEach { m ->
            Team.entries.forEach { team ->
                assertWithMessage("${m.id} $team").that(
                    m.zones.count {
                        it.kind == ZoneKind.THRONE && it.team == team
                    },
                ).isEqualTo(1)
            }
            assertWithMessage(m.id).that(m.zones.filter { it.kind != ZoneKind.THRONE }.all { it.team == null }).isTrue()
            val inLanes = m.lanes.flatMap { it.zones }.toSet()
            m.zones.forEach { z ->
                when (z.kind) {
                    ZoneKind.LANE -> assertWithMessage("${m.id} ${z.id}").that(z.id).isIn(inLanes)
                    ZoneKind.SIDE -> assertWithMessage("${m.id} ${z.id}").that(z.id).isNotIn(inLanes)
                    ZoneKind.THRONE -> Unit
                }
            }
        }
    }

    @Test
    fun `each Lane runs from Red's Throne Zone to Blue's through adjacent Zones, Battle Zone inside (p3)`() {
        maps.forEach { m ->
            val zones = m.zones.associateBy { it.id }
            val adjacency = zoneAdjacency(m)
            assertWithMessage(m.id).that(m.lanes.map { it.id }).containsNoDuplicates()
            m.lanes.forEach { lane ->
                val first = zones.getValue(lane.zones.first())
                val last = zones.getValue(lane.zones.last())
                assertWithMessage(
                    "${m.id} ${lane.id}",
                ).that(first.kind to first.team).isEqualTo(ZoneKind.THRONE to Team.RED)
                assertWithMessage(
                    "${m.id} ${lane.id}",
                ).that(last.kind to last.team).isEqualTo(ZoneKind.THRONE to Team.BLUE)
                lane.zones.zipWithNext().forEach { (a, b) ->
                    assertWithMessage("${m.id} ${lane.id} $a-$b").that(adjacency.getValue(a)).contains(b)
                }
                assertWithMessage(
                    "${m.id} ${lane.id}",
                ).that(lane.startingBattleZone).isIn(lane.zones.drop(1).dropLast(1))
                if (m.lanes.size == 1) {
                    // p.3: "all minions are placed in the Zone in the middle of the Lane".
                    assertWithMessage(
                        m.id,
                    ).that(lane.zones.indexOf(lane.startingBattleZone)).isEqualTo(lane.zones.size / 2)
                }
            }
        }
    }

    @Test
    fun `three hero spawn points per team, in its Throne Zone (p6, p20)`() {
        maps.forEach { m ->
            Team.entries.forEach { team ->
                val throne = m.zones.single { it.kind == ZoneKind.THRONE && it.team == team }.id
                val heroes = m.spaces.filter { it.spawn == SpawnPoint(team, SpawnKind.HERO) }
                assertWithMessage("${m.id} $team").that(heroes).hasSize(3)
                assertWithMessage("${m.id} $team").that(heroes.all { it.zone == throne }).isTrue()
            }
        }
    }

    @Test
    fun `single-lane starting Battle Zone has 4 Melee, 1 Ranged, 1 Heavy spawn points per team (p6)`() {
        maps.filter { it.lanes.size == 1 }.forEach { m ->
            val zone = m.lanes.single().startingBattleZone
            Team.entries.forEach { team ->
                val kinds = m.spaces.filter {
                    it.zone == zone && it.spawn?.team == team
                }.groupingBy { it.spawn!!.kind }.eachCount()
                assertWithMessage("${m.id} $team")
                    .that(kinds).isEqualTo(mapOf(SpawnKind.MELEE to 4, SpawnKind.RANGED to 1, SpawnKind.HEAVY to 1))
            }
        }
    }

    @Test
    fun `double-lane maps have two Lanes with their own starting Battle Zones (p20)`() {
        maps.filter { it.lanes.size > 1 }.forEach { m ->
            assertWithMessage(m.id).that(m.lanes).hasSize(2)
            assertWithMessage(m.id).that(m.lanes.map { it.startingBattleZone }.toSet()).hasSize(2)
            val shared = m.lanes[0].zones.drop(1).dropLast(1).intersect(m.lanes[1].zones.toSet())
            assertWithMessage(m.id).that(shared).isEmpty()
        }
    }

    @Test
    fun `every Lane Zone between the Thrones has minion spawn points for both teams, none for heroes`() {
        maps.forEach { m ->
            m.lanes.flatMap { it.zones.drop(1).dropLast(1) }.forEach { zone ->
                val spawns = m.spaces.filter { it.zone == zone }.mapNotNull { it.spawn }
                Team.entries.forEach { team ->
                    assertWithMessage("${m.id} $zone $team").that(
                        spawns.any {
                            it.team == team && it.kind.minion != null
                        },
                    ).isTrue()
                }
                assertWithMessage("${m.id} $zone").that(spawns.none { it.kind == SpawnKind.HERO }).isTrue()
            }
            m.spaces.filter { it.spawn?.kind?.minion != null }.forEach { s ->
                val zone = m.zones.single { it.id == s.zone }
                assertWithMessage("${m.id} ${s.hex}").that(zone.kind).isEqualTo(ZoneKind.LANE)
            }
        }
    }

    @Test
    fun `every Zone can be reached from every other`() {
        maps.forEach { m ->
            val adjacency = zoneAdjacency(m)
            val seen = mutableSetOf(m.zones.first().id)
            val queue = ArrayDeque(seen.toList())
            while (queue.isNotEmpty()) {
                adjacency.getValue(queue.removeFirst()).filter { seen.add(it) }.forEach {
                    queue +=
                        it
                }
            }
            assertWithMessage(m.id).that(seen).isEqualTo(m.zones.map { it.id }.toSet())
        }
    }
}
