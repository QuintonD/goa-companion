package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.board.BoardLane
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.BoardZone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.SourceRef
import dev.quinton.goa.core.model.match.Team

/** One rulebook / data check of a map; [required] checks decide `verified`. */
data class MapFinding(val check: String, val pass: Boolean, val detail: String, val required: Boolean = true)

/** A converted map and everything REPORT.md says about it. */
data class MapReport(
    val spec: MapSpec,
    val map: BoardMap,
    val findings: List<MapFinding>,
    val overrides: List<String>,
    val check: MapCheck?,
    val overlays: List<String> = emptyList(),
) {
    val rulebookPass: Boolean get() = findings.filter { it.required }.all { it.pass }
}

/**
 * Converts a goa2-backend map to the app's [BoardMap] (ADR-033): applies `overrides.json` (maps),
 * names Zones neutrally, orders every Lane from Red's Throne Zone to Blue's, derives the enclosed
 * terrain, and checks the result against the rulebook. Pure; no I/O.
 */
object MapImport {
    private val TAG = Regex("""(Red|Blue)(Hero|Melee|Ranged|Heavy)Spawn""")
    private val DIRECTIONS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    private const val HERO_SPAWNS_PER_TEAM = 3
    private val SINGLE_LANE_MINIONS = mapOf(SpawnKind.MELEE to 4, SpawnKind.RANGED to 1, SpawnKind.HEAVY to 1)

    fun convert(
        spec: MapSpec,
        source: BackendMap,
        override: MapOverride?,
        check: MapCheck?,
        sources: List<SourceRef>,
        printing: Int,
    ): MapReport {
        val (hexes, applied) = applyOverride(source.hexMap, override)
        val spaces = hexes.map { h -> RawSpace(h.q, h.r, h.zoneId, spawnOf(h)) }
        val dupes = spaces.groupBy { it.q to it.r }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "${spec.file}: duplicate hexes $dupes" }

        val throne = Team.entries.associateWith { team ->
            spaces.filter { it.spawn == SpawnPoint(team, SpawnKind.HERO) }.map { it.zone }.distinct().singleOrNull()
                ?: error("${spec.file}: ${team.name.lowercase()} hero spawn points must lie in exactly one Zone")
        }
        val lanes = orderedLanes(spec, source, throne)
        val zoneIds = zoneIds(spaces, lanes, throne)

        val boardLanes = lanes.entries.mapIndexed { i, (sourceLane, zones) ->
            val start =
                source.battleZones[sourceLane] ?: zones.getOrNull(zones.size / 2)?.takeIf { zones.size % 2 == 1 }
                    ?: error("${spec.file}: lane $sourceLane has no starting Battle Zone")
            BoardLane(
                id = "lane-${i + 1}",
                zones = zones.map(zoneIds::getValue),
                startingBattleZone = zoneIds.getValue(start),
            )
        }
        val zones = zoneIds.map { (sourceZone, id) ->
            val team = Team.entries.firstOrNull { throne[it] == sourceZone }
            val kind = when {
                team != null -> ZoneKind.THRONE
                lanes.values.any { sourceZone in it } -> ZoneKind.LANE
                else -> ZoneKind.SIDE
            }
            BoardZone(id = id, kind = kind, team = team)
        }
        val boardSpaces = spaces.sortedWith(compareBy({ it.r }, { it.q }))
            .map { BoardSpace(it.q, it.r, zoneIds.getValue(it.zone), it.spawn) }
        val partial = BoardMap(
            id = spec.id,
            name = spec.name,
            lanes = boardLanes,
            zones = zones,
            spaces = boardSpaces,
            terrain = enclosedTerrain(boardSpaces.map { it.hex }.toSet()),
            printing = printing,
            verified = false,
            sources = sources,
        )
        val findings = checks(partial)
        val report = MapReport(spec, partial, findings, applied, check)
        return report.copy(map = partial.copy(verified = report.rulebookPass && check != null))
    }

    private data class RawSpace(val q: Int, val r: Int, val zone: String, val spawn: SpawnPoint?)

    private fun spawnOf(hex: BackendHex): SpawnPoint? {
        val spawns = hex.tags.map { tag ->
            val m = TAG.matchEntire(tag) ?: error("Unknown map tag '$tag' at (${hex.q},${hex.r})")
            SpawnPoint(Team.valueOf(m.groupValues[1].uppercase()), SpawnKind.valueOf(m.groupValues[2].uppercase()))
        }
        require(spawns.size <= 1) { "Two spawn points on (${hex.q},${hex.r}): ${hex.tags}" }
        return spawns.singleOrNull()
    }

    /** The source hexes with [override] applied, and a line per change for REPORT.md. */
    fun applyOverride(hexes: List<BackendHex>, override: MapOverride?): Pair<List<BackendHex>, List<String>> {
        if (override == null) return hexes to emptyList()
        val log = mutableListOf<String>()
        val removed = override.removeSpaces.map(MapOverride::hex).toSet()
        val byHex = hexes.associateBy { it.q to it.r }
        removed.forEach { hex ->
            val h =
                requireNotNull(byHex[hex]) { "overrides.json removes ${hex.first},${hex.second}, not in the source" }
            log += "(${hex.first},${hex.second}) ${h.zoneId}: space removed (printed terrain)" +
                h.tags.joinToString(prefix = "; had ", separator = ", ").takeIf { h.tags.isNotEmpty() }.orEmpty()
        }
        val spawns = override.spawns.mapKeys { MapOverride.hex(it.key) }
        val out = hexes.filter { (it.q to it.r) !in removed }.map { h ->
            val key = h.q to h.r
            if (key !in spawns) return@map h
            val tag = spawns[key]?.let(MapOverride::spawn)
            val before = h.tags.joinToString().ifEmpty { "none" }
            log += "(${h.q},${h.r}) ${h.zoneId}: spawn $before → ${tag ?: "none"}"
            h.copy(tags = listOfNotNull(tag))
        }
        val unknown = spawns.keys - hexes.map { it.q to it.r }.toSet()
        require(unknown.isEmpty()) { "overrides.json sets spawns on hexes not in the source: $unknown" }
        return out to log
    }

    /** Lanes in [MapSpec.laneOrder], each from Red's Throne Zone to Blue's (reversed if the source runs backwards). */
    private fun orderedLanes(spec: MapSpec, source: BackendMap, throne: Map<Team, String>): Map<String, List<String>> {
        val all = source.allLanes
        require(all.isNotEmpty()) { "${spec.file}: no lanes" }
        val order = spec.laneOrder ?: all.keys.toList()
        require(order.toSet() == all.keys) { "${spec.file}: laneOrder $order vs lanes ${all.keys}" }
        return order.associateWith { id ->
            val zones = all.getValue(id)
            when {
                zones.first() == throne[Team.RED] && zones.last() == throne[Team.BLUE] -> zones
                zones.first() == throne[Team.BLUE] && zones.last() == throne[Team.RED] -> zones.reversed()
                else -> error("${spec.file}: lane $id doesn't run from one Throne Zone to the other: $zones")
            }
        }
    }

    /**
     * Neutral Zone ids: `red-throne` / `blue-throne`; `lane-<n>-<k>` for the k-th Zone of lane n
     * counted from Red's side; other Zones `red-side`, `blue-side` or `middle-side` by which Throne
     * Zone is fewer Zones away (numbered `-1`, `-2` when a class has several).
     */
    private fun zoneIds(
        spaces: List<RawSpace>,
        lanes: Map<String, List<String>>,
        throne: Map<Team, String>,
    ): LinkedHashMap<String, String> {
        val ids = LinkedHashMap<String, String>()
        ids[throne.getValue(Team.RED)] = "red-throne"
        lanes.values.forEachIndexed { i, zones ->
            zones.drop(1).dropLast(1).forEachIndexed { k, zone ->
                require(zone !in ids) { "Zone $zone is in two lanes" }
                ids[zone] = "lane-${i + 1}-${k + 1}"
            }
        }
        ids[throne.getValue(Team.BLUE)] = "blue-throne"
        val adjacency = zoneAdjacency(spaces.map { Triple(it.q, it.r, it.zone) })
        val others = spaces.map { it.zone }.distinct().filter { it !in ids }.sorted()
        val classes = others.groupBy { zone ->
            val toRed = zoneDistance(adjacency, zone, throne.getValue(Team.RED))
            val toBlue = zoneDistance(adjacency, zone, throne.getValue(Team.BLUE))
            when {
                toRed < toBlue -> "red-side"
                toBlue < toRed -> "blue-side"
                else -> "middle-side"
            }
        }
        classes.toSortedMap().forEach { (name, zones) ->
            zones.forEachIndexed { i, zone -> ids[zone] = if (zones.size == 1) name else "$name-${i + 1}" }
        }
        return ids
    }

    /** Zone adjacency (p.17 "Adjacent"): a space of one Zone borders a space of the other. */
    fun zoneAdjacency(spaces: List<Triple<Int, Int, String>>): Map<String, Set<String>> {
        val zoneAt = spaces.associate { (q, r, z) -> (q to r) to z }
        val out = HashMap<String, MutableSet<String>>()
        spaces.forEach { (q, r, z) ->
            out.getOrPut(z) { mutableSetOf() }
            DIRECTIONS.forEach { (dq, dr) ->
                val other = zoneAt[(q + dq) to (r + dr)]
                if (other != null && other != z) out.getValue(z) += other
            }
        }
        return out
    }

    private fun zoneDistance(adjacency: Map<String, Set<String>>, from: String, to: String): Int {
        val seen = mutableMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val z = queue.removeFirst()
            if (z == to) return seen.getValue(z)
            adjacency[z].orEmpty().filter { it !in seen }.forEach {
                seen[it] = seen.getValue(z) + 1
                queue += it
            }
        }
        return Int.MAX_VALUE
    }

    /**
     * Printed terrain the map encloses (holes): hexes that are not spaces and can't be reached from
     * outside the map's bounding box (one ring larger) without crossing a space.
     */
    fun enclosedTerrain(spaces: Set<Hex>): List<Hex> {
        if (spaces.isEmpty()) return emptyList()
        val qs = spaces.map { it.q }
        val rs = spaces.map { it.r }
        val qRange = (qs.min() - 1)..(qs.max() + 1)
        val rRange = (rs.min() - 1)..(rs.max() + 1)
        val outside = HashSet<Hex>()
        val stack = ArrayDeque<Hex>()
        for (q in qRange) {
            stack += Hex(q, rRange.first)
            stack += Hex(q, rRange.last)
        }
        for (r in rRange) {
            stack += Hex(qRange.first, r)
            stack += Hex(qRange.last, r)
        }
        while (stack.isNotEmpty()) {
            val h = stack.removeLast()
            val inBox = h.q in qRange && h.r in rRange
            if (!inBox || h in outside || h in spaces) continue
            outside += h
            DIRECTIONS.forEach { (dq, dr) -> stack += Hex(h.q + dq, h.r + dr) }
        }
        return rRange.flatMap { r -> qRange.map { q -> Hex(q, r) } }.filter { it !in spaces && it !in outside }
    }

    /** The rulebook and consistency checks of a converted map (REPORT.md "Maps"). */
    fun checks(map: BoardMap): List<MapFinding> {
        val ctx = CheckContext(map)
        return buildList {
            addAll(ctx.structure())
            map.lanes.forEach { addAll(ctx.lane(it)) }
            addAll(ctx.global())
        }
    }

    private class CheckContext(val map: BoardMap) {
        val zoneIds = map.zones.map { it.id }.toSet()
        val byZone = map.spaces.groupBy { it.zone }
        val adjacency = zoneAdjacency(map.spaces.map { Triple(it.q, it.r, it.zone) })

        fun count(zone: String, team: Team, kind: SpawnKind) =
            byZone[zone].orEmpty().count { it.spawn == SpawnPoint(team, kind) }

        fun minions(zone: String, team: Team) = SINGLE_LANE_MINIONS.keys.sumOf { count(zone, team, it) }

        fun structure(): List<MapFinding> = buildList {
            add(
                MapFinding(
                    "Every space's Zone exists and every Zone has spaces",
                    map.spaces.all { it.zone in zoneIds } && zoneIds.all { byZone[it].orEmpty().isNotEmpty() },
                    "${map.zones.size} Zones, ${map.spaces.size} spaces",
                ),
            )
            Team.entries.forEach { team ->
                val throne = map.zones.singleOrNull { it.kind == ZoneKind.THRONE && it.team == team }?.id
                val heroes = map.spaces.filter { it.spawn == SpawnPoint(team, SpawnKind.HERO) }
                add(
                    MapFinding(
                        "p.6 / p.20: $HERO_SPAWNS_PER_TEAM hero spawn points for ${team.label} in its Throne Zone",
                        throne != null && heroes.size == HERO_SPAWNS_PER_TEAM && heroes.all { it.zone == throne },
                        "${heroes.size} in ${heroes.map { it.zone }.distinct()}",
                    ),
                )
            }
        }

        fun lane(lane: BoardLane): List<MapFinding> = buildList {
            val inner = lane.zones.drop(1).dropLast(1)
            val connected = lane.zones.zipWithNext().all { (a, b) -> b in adjacency[a].orEmpty() }
            val ends = lane.zones.first() == "red-throne" && lane.zones.last() == "blue-throne"
            add(
                MapFinding(
                    "p.3: ${lane.id} runs from Red's Throne Zone to Blue's through adjacent Zones",
                    ends && connected,
                    lane.zones.joinToString(" → "),
                ),
            )
            val single = map.lanes.size == 1
            val middle = lane.zones.indexOf(lane.startingBattleZone) == lane.zones.size / 2
            add(
                MapFinding(
                    "p.3 / p.20: ${lane.id} starts its Battle Zone in the Lane" + if (single) ", in the middle" else "",
                    lane.startingBattleZone in inner && (!single || middle),
                    lane.startingBattleZone,
                ),
            )
            inner.forEach { add(zoneSpawns(it, starting = it == lane.startingBattleZone)) }
            Team.entries.forEach { team ->
                val pre = if (team == Team.RED) inner.first() else inner.last()
                if (pre != lane.startingBattleZone) add(preThrone(pre, team))
            }
        }

        private fun zoneSpawns(zone: String, starting: Boolean): MapFinding {
            val counts = Team.entries.joinToString("; ") { team -> "${team.label} " + minionCounts(zone, team) }
            return if (starting && map.lanes.size == 1) {
                MapFinding(
                    "p.6 step 10: starting Battle Zone $zone has 4 Melee, 1 Ranged, 1 Heavy spawn points per team",
                    Team.entries.all { team -> SINGLE_LANE_MINIONS.all { (k, n) -> count(zone, team, k) == n } },
                    counts,
                )
            } else {
                MapFinding(
                    "${if (starting) "Starting Battle Zone" else "Zone"} $zone has minion spawn points for both teams",
                    Team.entries.all { team -> minions(zone, team) > 0 },
                    counts,
                )
            }
        }

        private fun preThrone(pre: String, team: Team): MapFinding {
            val own = minions(pre, team)
            val enemy = minions(pre, team.opponent)
            return MapFinding(
                "p.8 (said of the core single-lane map, \"the defending team has more minion spawn points in " +
                    "their Pre-Throne Zone\"): ${team.label} defends $pre",
                own > enemy,
                "${team.label} $own vs ${team.opponent.label} $enemy",
                required = false,
            )
        }

        fun global(): List<MapFinding> = buildList {
            if (map.lanes.size > 1) {
                add(
                    MapFinding(
                        "p.20: the double-lane map has two Lanes, each with its own starting Battle Zone",
                        map.lanes.size == 2 && map.lanes.map { it.startingBattleZone }.distinct().size == 2,
                        map.lanes.joinToString { "${it.id}: ${it.startingBattleZone}" },
                    ),
                )
            }
            val reachable = mutableSetOf("red-throne")
            val queue = ArrayDeque(listOf("red-throne"))
            while (queue.isNotEmpty()) {
                adjacency[queue.removeFirst()].orEmpty().filter { reachable.add(it) }.forEach { queue += it }
            }
            add(
                MapFinding(
                    "Every Zone can be reached from the Red Throne Zone",
                    reachable == zoneIds,
                    "${reachable.size}/${zoneIds.size}",
                ),
            )
            val terrain = map.terrain.toSet()
            add(
                MapFinding(
                    "Terrain holes and spaces are disjoint",
                    map.spaces.none { it.hex in terrain },
                    "${terrain.size} enclosed terrain spaces",
                ),
            )
        }

        private fun minionCounts(zone: String, team: Team): String =
            listOf(SpawnKind.MELEE, SpawnKind.RANGED, SpawnKind.HEAVY).joinToString("/") { kind ->
                "${count(zone, team, kind)}${kind.name.first()}"
            }
    }

    private val Team.label: String get() = if (this == Team.RED) "Red" else "Blue"
}
