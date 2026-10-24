package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.DisplaceQuery
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.PassMode
import dev.quinton.goa.core.game.effects.SpaceQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team

/*
 * Board rules (pp.11, 17–19) on the live state, with the hooks of the behaviours in force. The
 * hook-free cases equal core:domain's audited BoardRules (BoardConsistencyTest).
 */

/** Hexes in a canonical order (options are listed in it so re-runs ask identical questions). */
val HEX_ORDER: Comparator<Hex> = compareBy<Hex>({ it.r }, { it.q })

fun Collection<Hex>.sortedHexes(): List<Hex> = sortedWith(HEX_ORDER)

/** p.19 "Terrain": not a space of the map (water and rocks), or a unit that counts as terrain (Stone Gaze). */
fun Game.isTerrain(hex: Hex): Boolean =
    !geo.isSpace(hex) || sources().any { (b, s) -> b.countsAsTerrain(this, s, hex) }

/**
 * p.18 "Obstacle": "Units, terrain …, tokens, as well as any objects represented by miniatures".
 * [moverId] is the unit moving (it is never its own obstacle); spaces some behaviour says "count as
 * obstacles" (for this mover, during the current actor's action) are obstacles too.
 */
fun Game.isObstacle(hex: Hex, moverId: String? = null): Boolean {
    if (!geo.isSpace(hex)) return true
    val occ = occupant(hex)
    if (occ != null && occ != moverId) return true
    val q = SpaceQuery(hex, moverId, actor)
    return sources().any { (b, s) -> b.countsAsObstacle(this, s, q) } || !canInteract(hex)
}

/**
 * Whether the current actor may interact with [hex] ("as if they did not exist": NebKher's Crack in
 * Reality). Spaces it may not interact with are obstacles to it and their units can't be targeted.
 */
fun Game.canInteract(hex: Hex, actorId: String? = actor): Boolean {
    val a = actorId ?: return true
    val q = dev.quinton.goa.core.game.effects.InteractQuery(a, hex)
    return sources().none { (b, s) -> b.forbidsInteraction(this, s, q) }
}

/** p.17 "Empty": a space with no obstacles. */
fun Game.isEmpty(hex: Hex, forUnit: String? = null): Boolean = geo.isSpace(hex) && !isObstacle(hex, forUnit)

/** The board spaces 1..[n] from [center] (p.9 Range/Radius; [BoardRules.within]): spaces, terrain, the water ring. */
fun Game.within(center: Hex, n: Int): List<Hex> =
    HexGrid.within(center, n).filter { geo.isOnBoard(it) }

/** Ids of units (heroes, minions, unit pieces) on the board, heroes first in seat order. */
fun Game.unitsOnBoard(): List<String> = buildList {
    state.heroes.forEach { if (it.at != null) add(it.id) }
    state.pieces.forEach { if (it.at != null && (it.unit || it.heroBody != null)) add(it.id) }
    state.minions.forEach { if (it.at != null) add(it.id) }
}

fun Game.tokensOnBoard(): List<String> = state.pieces.filter { it.at != null && it.token }.map { it.id }

/** Units at distance 1..[n] from [center] (never the unit at [center]). */
fun Game.unitsWithin(center: Hex, n: Int): List<String> =
    unitsOnBoard().filter { id ->
        posOf(id)?.let {
            val d = distance(center, it)
            d in 1..n
        } == true
    }

fun Game.unitsAdjacentTo(center: Hex): List<String> = unitsWithin(center, 1)

fun Game.tokensAdjacentTo(center: Hex): List<String> =
    tokensOnBoard().filter { id -> posOf(id)?.let { distance(center, it) == 1 } == true }

/** The Zone of the hex, or null off the map. */
fun Game.zoneOf(hex: Hex): String? = geo.zoneOf(hex)

/** The current Battle Zone of [lane]. */
fun Game.battleZone(lane: String): String = state.battleZones[lane] ?: geo.lanes.getValue(lane).startingBattleZone

/** p.20: "the Battle Zone" means "a Battle Zone" on the double-lane map. */
fun Game.isInBattleZone(hex: Hex): Boolean {
    val z = zoneOf(hex) ?: return false
    return state.battleZones.values.contains(z)
}

/** Positions of the six directions out of [from] (p.17 "Direction"), first step only. */
fun Game.straightLineFrom(from: Hex, dir: Int, length: Int): List<Hex> {
    val step = HexGrid.DIRECTIONS[dir]
    return (1..length).map { Hex(from.q + step.q * it, from.r + step.r * it) }
}

// ---- movement --------------------------------------------------------------------------------

/**
 * A move of a unit (p.11 Movement action, p.11 "Movement in the Card Text"). Distance is
 * [min]..[max] spaces: "up to N" is 0..N, an exact distance N is N..N ("you must move the exact
 * number of spaces"), "2 or 3 spaces" is 2..3. Exact distances use paths that never enter a space
 * twice ([APP], RULINGS.md).
 */
data class MoveSpec(
    val unitId: String,
    val min: Int,
    val max: Int,
    val straightLine: Boolean = false,
    /** A Movement action: Movement limits apply and they are checked at its start (p.11). */
    val movementAction: Boolean = false,
    /** Force [PassMode.IGNORE_OBSTACLES] ("ignoring obstacles" in the text). */
    val ignoreObstacles: Boolean = false,
    /** Only these straight-line directions (index into [HexGrid.DIRECTIONS]). */
    val directions: Set<Int>? = null,
    /** Extra condition on the end space, e.g. "to a space adjacent to an enemy unit". */
    val endFilter: ((Hex) -> Boolean)? = null,
    /** Ask space by space, so the exact path is the mover's (paths that pass tokens or units matter). */
    val stepwise: Boolean = false,
)

/** How a unit may pass obstacles on this move (p.18), behaviours included. */
fun Game.passMode(spec: MoveSpec): PassMode {
    if (spec.ignoreObstacles) return PassMode.IGNORE_OBSTACLES
    val q = MoveQuery(spec.unitId, spec.movementAction, actor)
    return if (sources().any { (b, s) -> b.passMode(this, s, q) == PassMode.IGNORE_OBSTACLES }) {
        PassMode.IGNORE_OBSTACLES
    } else {
        PassMode.NORMAL
    }
}

private fun Game.passable(hex: Hex, mover: String, mode: PassMode): Boolean = when (mode) {
    PassMode.IGNORE_OBSTACLES -> geo.isOnBoard(hex)

    PassMode.NORMAL -> {
        if (!geo.isSpace(hex)) {
            false
        } else {
            val occ = occupant(hex)
            val blockedByOccupant = occ != null && occ != mover && state.piece(occ)?.passable != true
            !blockedByOccupant &&
                !sources().any { (b, s) -> b.countsAsObstacle(this, s, SpaceQuery(hex, mover, actor)) } &&
                canInteract(hex)
        }
    }
}

/**
 * End spaces of [spec] with one path to each (start excluded from the path). The start itself is
 * included (empty path) when 0 is an allowed distance. Paths go through spaces the mover may pass
 * and end on an empty space (p.18: "a unit can never end its movement on the same space as an obstacle").
 */
fun Game.reach(spec: MoveSpec): Map<Hex, List<Hex>> {
    val start = posOf(spec.unitId) ?: return emptyMap()
    val out = LinkedHashMap<Hex, List<Hex>>()
    if (spec.min <= 0 && spec.endFilter?.invoke(start) != false) out[start] = emptyList()
    if (spec.max <= 0) return out
    val mode = passMode(spec)
    when {
        spec.straightLine -> straightReach(spec, start, mode, out)

        // "Up to N": shortest paths suffice (any end within N steps).
        spec.min <= 1 -> upToReach(spec, start, mode, out)

        // Exact distances (min >= 2): simple paths of exactly k steps, k in min..max.
        else -> exactReach(spec, start, mode, out)
    }
    return out
}

private fun Game.endOk(spec: MoveSpec, h: Hex): Boolean = isEmpty(h, spec.unitId) && spec.endFilter?.invoke(h) != false

private fun Game.straightReach(spec: MoveSpec, start: Hex, mode: PassMode, out: MutableMap<Hex, List<Hex>>) {
    for (dir in HexGrid.DIRECTIONS.indices) {
        if (spec.directions != null && dir !in spec.directions) continue
        val line = straightLineFrom(start, dir, spec.max)
        val blocked = line.indexOfFirst { !passable(it, spec.unitId, mode) }.let { if (it < 0) line.size else it }
        for (i in 0 until blocked) {
            if (i + 1 >= spec.min && endOk(spec, line[i])) out.putIfAbsent(line[i], line.take(i + 1))
        }
    }
}

private fun Game.upToReach(spec: MoveSpec, start: Hex, mode: PassMode, out: MutableMap<Hex, List<Hex>>) {
    val prev = HashMap<Hex, Hex>()
    val dist = HashMap<Hex, Int>()
    dist[start] = 0
    val queue = ArrayDeque<Hex>()
    queue += start
    while (queue.isNotEmpty()) {
        val h = queue.removeFirst()
        val d = dist.getValue(h)
        if (d == spec.max) continue
        for (n in HexGrid.neighbors(h)) {
            if (n !in dist && passable(n, spec.unitId, mode)) {
                dist[n] = d + 1
                prev[n] = h
                queue += n
            }
        }
    }
    for ((h, d) in dist) {
        if (h == start || d < spec.min || !endOk(spec, h)) continue
        val path = ArrayList<Hex>()
        var at = h
        while (at != start) {
            path += at
            at = prev.getValue(at)
        }
        path.reverse()
        out[h] = path
    }
}

private fun Game.exactReach(spec: MoveSpec, start: Hex, mode: PassMode, out: MutableMap<Hex, List<Hex>>) {
    val visited = LinkedHashSet<Hex>()
    visited += start
    fun dfs(at: Hex, path: ArrayList<Hex>) {
        val k = path.size
        if (k >= spec.min && endOk(spec, at)) out.putIfAbsent(at, ArrayList(path))
        if (k == spec.max) return
        for (n in HexGrid.neighbors(at)) {
            if (n in visited || !passable(n, spec.unitId, mode)) continue
            visited += n
            path += n
            dfs(n, path)
            path.removeAt(path.lastIndex)
            visited -= n
        }
    }
    dfs(start, ArrayList())
}

/** Whether [spec] can be performed with at least one move of a non-zero length. */
fun Game.canMove(spec: MoveSpec): Boolean = reach(spec).keys.any { it != posOf(spec.unitId) }

/**
 * Move a unit or token per [spec]; [chooser] picks (usually the acting hero, or the moved hero when
 * the text says so). [optional]: the move may be skipped ("you may", "up to" — the decline label).
 * Returns the path taken (empty = stayed). A mandatory move that can't be made fizzles (p.11).
 */
fun Game.move(
    spec: MoveSpec,
    chooser: String,
    prompt: String,
    optional: Boolean,
    sourceCardId: String? = null,
): List<Hex> {
    val start = posOf(spec.unitId) ?: fizzle("${name(spec.unitId)} is not on the board")
    val byHero = actor
    if (!canDisplace(spec.unitId, Displacement.MOVE, byHero)) {
        if (optional) return emptyList()
        fizzle("${name(spec.unitId)} cannot be moved")
    }
    val options = reach(spec)
    val moves = options.filterKeys { it != start }
    if (moves.isEmpty()) {
        if (optional || spec.min <= 0) return emptyList()
        fizzle("${name(spec.unitId)} cannot move ${spec.min} spaces")
    }
    // Tokens units may move through and that do something (Min's mines): the mover picks the path.
    val pathMatters = spec.stepwise ||
        state.pieces.any { p -> p.passable && p.at?.let { distance(it, start) <= spec.max + 1 } == true }
    val path = if (pathMatters) {
        stepwiseMove(spec, chooser, prompt, optional || spec.min <= 0, sourceCardId)
    } else {
        val stay = optional || spec.min <= 0
        val choices = moves.keys.sortedHexes().map { Choice.Space(it) as Choice } +
            if (stay) listOf(Choice.Decline("Don't move")) else emptyList()
        val answer = ask(
            chooser,
            DecisionKind.CHOOSE_SPACE,
            prompt,
            choices,
            sourceCardId,
            DecisionContext(subjectId = spec.unitId, paths = moves.mapKeys { "${it.key.q},${it.key.r}" }),
        )
        if (answer is Choice.Space) moves.getValue(answer.hex) else emptyList()
    }
    if (path.isEmpty()) {
        note("${name(spec.unitId)} does not move.")
        return path
    }
    setPos(spec.unitId, path.last())
    emit(GameEvent.Moved(spec.unitId, path, "${name(spec.unitId)} moves ${path.size} space${plural(path.size)}."))
    trigger(Trigger.Moved(spec.unitId, path, byHero, push = false))
    return path
}

private fun Game.stepwiseMove(
    spec: MoveSpec,
    chooser: String,
    prompt: String,
    mayStop: Boolean,
    source: String?,
): List<Hex> {
    val start = posOf(spec.unitId)!!
    val mode = passMode(spec)
    val path = ArrayList<Hex>()
    val visited = hashSetOf(start)
    var at = start
    var dir: Int? = null
    while (true) {
        val k = path.size
        fun endOk(h: Hex) = isEmpty(h, spec.unitId) && spec.endFilter?.invoke(h) != false
        val canEndHere = (k == 0 && mayStop) || (k >= spec.min && k > 0 && endOk(at))
        val nexts = if (k == spec.max) {
            emptyList()
        } else {
            HexGrid.DIRECTIONS.indices.mapNotNull { d ->
                if (spec.straightLine && dir != null && d != dir) return@mapNotNull null
                if (spec.straightLine && spec.directions != null && d !in spec.directions) return@mapNotNull null
                val n = at + HexGrid.DIRECTIONS[d]
                if (n in visited && !spec.straightLine) return@mapNotNull null
                if (!passable(n, spec.unitId, mode)) return@mapNotNull null
                if (!completable(spec, n, k + 1, visited + n, d, mode)) return@mapNotNull null
                d to n
            }
        }
        if (nexts.isEmpty()) {
            check(canEndHere) { "Stepwise move stuck" }
            return path
        }
        val options =
            nexts.map { Choice.Space(it.second) as Choice }.sortedBy {
                (it as Choice.Space).hex.let { h ->
                    h.r * 1000 +
                        h.q
                }
            } +
                if (canEndHere) listOf(Choice.Decline(if (k == 0) "Don't move" else "Stop here")) else emptyList()
        val answer = ask(
            chooser,
            DecisionKind.CHOOSE_SPACE,
            "$prompt (space ${k + 1})",
            options,
            source,
            DecisionContext(subjectId = spec.unitId),
            auto = false,
        )
        if (answer !is Choice.Space) return path
        val d = nexts.first { it.second == answer.hex }.first
        dir = d
        at = answer.hex
        visited += at
        path += at
    }
}

private fun Game.completable(spec: MoveSpec, at: Hex, k: Int, visited: Set<Hex>, dir: Int, mode: PassMode): Boolean {
    if (k >= spec.min && isEmpty(at, spec.unitId) && spec.endFilter?.invoke(at) != false) return true
    if (k == spec.max) return false
    for (d in HexGrid.DIRECTIONS.indices) {
        if (spec.straightLine && d != dir) continue
        val n = at + HexGrid.DIRECTIONS[d]
        if (!spec.straightLine && n in visited) continue
        if (!passable(n, spec.unitId, mode)) continue
        if (completable(spec, n, k + 1, visited + n, d, mode)) return true
    }
    return false
}

private fun plural(n: Int) = if (n == 1) "" else "s"

/** Set a unit's or token's position (or take it off the board with null). Use the primitives instead. */
fun Game.setPos(id: String, hex: Hex?) {
    state.hero(id)?.let { it.at = hex } ?: state.minion(id)?.let { it.at = hex } ?: state.piece(id)?.let { it.at = hex }
        ?: error("No piece $id")
    touch()
}

/** May [unitId] be displaced [how] by [byHero] (Bulwark, Magnetic Dagger…)? */
fun Game.canDisplace(unitId: String, how: Displacement, byHero: String?): Boolean {
    val q = DisplaceQuery(unitId, how, byHero)
    return sources().none { (b, s) -> b.forbidsDisplacement(this, s, q) }
}

// ---- fast travel -----------------------------------------------------------------------------

/** Enemy heroes or minions of [team] in [zone] (p.11 Fast Travel), pieces that are units included. */
fun Game.enemyUnitsInZone(team: Team, zone: String): Boolean = unitsOnBoard().any { id ->
    teamOf(id) != null && teamOf(id) != team && posOf(id)?.let(::zoneOf) == zone
}

/**
 * p.11 Fast Travel: "any space in the same Zone, or an adjacent Zone" when there are no enemy
 * heroes or minions in the Zone you travel from or to; "You do not need to have a path"; the
 * destination is empty (p.18 "Place").
 */
fun Game.fastTravelDestinations(heroId: String): List<Hex> {
    val h = hero(heroId)
    val from = h.at ?: return emptyList()
    val zone = zoneOf(from) ?: return emptyList()
    if (enemyUnitsInZone(h.team, zone)) return emptyList()
    val zones = (geo.adjacentZones[zone].orEmpty() + zone).filter { !enemyUnitsInZone(h.team, it) }
    return zones.flatMap { geo.zoneSpaces[it].orEmpty() }.filter { it != from && isEmpty(it, heroId) }.sortedHexes()
}

// ---- place, swap, push, remove ---------------------------------------------------------------

/** p.18 "Place": into an empty space; not movement. Checks displacement limits for others' pieces. */
fun Game.place(id: String, to: Hex, byHero: String? = actor) {
    check(isEmpty(to, id)) { "Place into a non-empty space $to" }
    val from = posOf(id)
    setPos(id, to)
    emit(GameEvent.Placed(id, from, to, "${name(id)} is placed${if (from == null) "" else " from $from"} into $to."))
    trigger(Trigger.Placed(id, from, to, byHero))
}

fun Game.canPlace(id: String, byHero: String? = actor): Boolean = canDisplace(id, Displacement.PLACE, byHero)

/** p.19 "Swap": two objects swap places; not movement or placement. */
fun Game.swap(a: String, b: String, byHero: String? = actor) {
    val pa = posOf(a) ?: error("$a not on board")
    val pb = posOf(b) ?: error("$b not on board")
    // Lift both first: one obstacle per space at every moment.
    setPos(a, null)
    setPos(b, pa)
    setPos(a, pb)
    emit(GameEvent.Swapped(a, b, "${name(a)} and ${name(b)} swap places."))
    trigger(Trigger.Swapped(a, b, byHero))
}

fun Game.canSwap(a: String, b: String, byHero: String? = actor): Boolean =
    canDisplace(a, Displacement.SWAP, byHero) && canDisplace(b, Displacement.SWAP, byHero)

/** Result of a push: spaces moved and whether an obstacle stopped it short (Wasp's Kinetic Repulse). */
data class PushResult(val moved: Int, val stoppedByObstacle: Boolean, val to: Hex?)

/**
 * p.18 "Push": "move it in a straight line directly away from the pushing unit the number of spaces
 * equal to the push distance"; ignoring obstacles it passes them "as long as it can reach an empty
 * space"; otherwise "as far as possible; this can result in pushing it 0 spaces". [from] is the
 * pusher's space. Null when not in a straight line (no "directly away"), or the target can't be pushed.
 */
fun Game.push(
    targetId: String,
    from: Hex,
    spaces: Int,
    ignoreObstacles: Boolean = false,
    byHero: String? = actor,
): PushResult? {
    val at = posOf(targetId) ?: return null
    val dir = HexGrid.straightLineDirection(from, at) ?: return null
    if (!canDisplace(targetId, Displacement.PUSH, byHero)) return null
    val line = straightLineFrom(at, dir, spaces)
    var end: Hex? = null
    var endIndex = 0
    var stopped = false
    var passed = false
    for ((i, h) in line.withIndex()) {
        val empty = isEmpty(h, targetId)
        if (empty) {
            end = h
            endIndex = i + 1
        } else if (!ignoreObstacles && pushPassable(h, targetId)) {
            // p.18: "or the obstacle can be 'moved through'" (Min's mines): pushed through it.
            passed = true
        } else if (!ignoreObstacles) {
            stopped = true
            break
        } else if (!geo.isOnBoard(h)) {
            break
        }
    }
    if ((ignoreObstacles || passed) && endIndex < spaces) stopped = true
    if (end != null) {
        setPos(targetId, end)
        val path = line.take(endIndex)
        emit(GameEvent.Pushed(targetId, at, end, "${name(targetId)} is pushed $endIndex space${plural(endIndex)}."))
        trigger(Trigger.Moved(targetId, path, byHero, push = true))
    } else {
        emit(GameEvent.Note("${name(targetId)} cannot be pushed."))
    }
    trigger(Trigger.Pushed(targetId, byHero, endIndex, stopped))
    return PushResult(endIndex, stopped, end ?: at)
}

/** A space a pushed unit may be pushed through without ignoring obstacles: a piece units may move through. */
private fun Game.pushPassable(hex: Hex, mover: String): Boolean {
    if (!geo.isSpace(hex)) return false
    val occ = occupant(hex) ?: return false
    if (state.piece(occ)?.passable != true) return false
    return sources().none { (b, s) -> b.countsAsObstacle(this, s, SpaceQuery(hex, mover, actor)) } && canInteract(hex)
}

/** p.19 "Remove": take a piece off the board; no coins. Minions: see [removeMinion]. */
fun Game.removePiece(id: String) {
    val p = state.piece(id) ?: error("No piece $id")
    setPos(id, null)
    state.pieces.remove(p)
    touch()
    emit(GameEvent.Removed(id, "${p.label} is removed."))
}

/**
 * Place a token from [owner]'s supply (p.18 "Place": "take it from the supply first. If you need to
 * place more tokens than are available in the supply, first remove up to that number of tokens of
 * the same type from the board, if able"). [takeFrom] picks which board token to recycle when the
 * supply is empty (null = fail).
 */
fun Game.placeToken(placed: String, owner: String?, to: Hex, takeFrom: String? = null): String? {
    val kind = sources().firstNotNullOfOrNull { (b, s) -> b.tokenKind(this, s, placed, actor) } ?: placed
    val tk = data.registry.tokenKind(kind)
    val label = tk?.label ?: kind
    val onBoard = state.pieces.filter { it.kind == kind && it.at != null && (owner == null || it.owner == owner) }
    val supply = tk?.supply
    if (supply != null && onBoard.size >= supply) {
        val recycle = takeFrom ?: return null
        removePiece(recycle)
    }
    if (!isEmpty(to)) return null
    val team = owner?.let { teamOf(it) }
    val piece = PieceState(
        id = state.newId("t"),
        kind = kind,
        label = label,
        at = null,
        owner = owner,
        team = team,
        removeAtEndOfRound = tk?.removeAtEndOfRound ?: true,
        passable = tk?.passable ?: false,
    )
    state.pieces += piece
    touch()
    place(piece.id, to)
    return piece.id
}

/**
 * p.19 "Spawn": like Place, but from the supply; "If a unit Spawns in a Spawn Point covered by a
 * token, remove the token first, then spawn the unit." [piece] must not be on the board yet; it is
 * added to the state. Spaces to offer: empty spaces, and spawn points holding a token.
 */
fun Game.spawnPiece(piece: PieceState, to: Hex) {
    occupant(to)?.let { occ -> if (isSpawnPoint(to) && state.piece(occ)?.token == true) removePiece(occ) }
    if (state.pieces.none { it.id == piece.id }) state.pieces += piece
    piece.at = null
    touch()
    place(piece.id, to)
}

fun Game.canSpawnInto(hex: Hex): Boolean =
    isEmpty(hex) || (isSpawnPoint(hex) && occupant(hex)?.let { state.piece(it)?.token } == true)

/** Throne Zones and Spawn points (p.6, p.19). */
fun Game.isSpawnPoint(hex: Hex): Boolean = geo.spaces[hex]?.spawn != null

fun Game.isMinionSpawnPoint(hex: Hex): Boolean = geo.spaces[hex]?.spawn?.kind?.minion != null

/** p.17 "An empty Spawn point is a Spawn point in an empty space." */
fun Game.isEmptySpawnPoint(hex: Hex): Boolean = isSpawnPoint(hex) && isEmpty(hex)

fun Game.throneZone(team: Team): String = geo.map.zones.first { it.kind == ZoneKind.THRONE && it.team == team }.id
