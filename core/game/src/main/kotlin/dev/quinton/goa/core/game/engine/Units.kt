package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.effects.DefeatOutcome
import dev.quinton.goa.core.game.effects.LifeQuery
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team

/** The hero being defeated is the one performing the action: that action ends (its card stays resolved). */
class ActorDefeated(val heroId: String) : ControlSignal()

/** Coins for defeating a minion (p.13): "Ranged and melee minions are worth 2 coins … heavy minions are worth 4". */
fun minionCoins(type: MinionType): Int = if (type == MinionType.HEAVY) 4 else 2

/**
 * Defeat a minion (p.13 "Attacking a Minion", p.17 "Defeat"): collect its coins, remove it, and push
 * the Lane if it was its team's last (p.8 "Triggering a Push"). "You can never defeat a friendly
 * unit. No exceptions." Returns true when it was defeated.
 */
fun Game.defeatMinion(minionId: String, byHero: String?): Boolean {
    val m = state.minion(minionId) ?: return false
    if (byHero != null && teamOf(byHero) == m.team) return false
    val outcome = replacement(Replacement.MinionDefeat(minionId, byHero)).outcome
    if (outcome == DefeatOutcome.PREVENTED) return false
    val at = m.at
    val by = byHero?.let { " by ${heroName(it)}" } ?: ""
    emit(GameEvent.Defeated(minionId, byHero, "${name(minionId)} is defeated$by."))
    byHero?.let { gainCoins(it, minionCoins(m.type), "defeated a ${Game.minionName(m.type)} minion") }
    if (outcome == DefeatOutcome.NOT_REMOVED) {
        note("${name(minionId)} is not removed.")
        return true
    }
    state.minions.remove(m)
    touch()
    trigger(Trigger.MinionDefeated(minionId, m.team, m.type, at, byHero))
    checkPush(m.lane)
    return true
}

/** p.19 "Remove": off the board, no coins; still pushes the Lane if it was the last (p.8). */
fun Game.removeMinion(minionId: String) {
    val m = state.minion(minionId) ?: return
    val label = name(minionId)
    state.minions.remove(m)
    touch()
    emit(GameEvent.Removed(minionId, "$label is removed."))
    checkPush(m.lane)
}

/**
 * p.8 "Triggering a Push": "If you defeat, or remove, the last remaining enemy minion in the Battle
 * Zone, the Lane is immediately Pushed". [APP] Minions of the Lane displaced out of the Battle Zone
 * during the action still count (p.18: displacing a minion "has no impact").
 */
fun Game.checkPush(lane: String) {
    if (state.step == Step.MINION_BATTLE) return
    val red = state.minions.count { it.lane == lane && it.at != null && it.team == Team.RED }
    val blue = state.minions.count { it.lane == lane && it.at != null && it.team == Team.BLUE }
    when {
        red == 0 && blue > 0 -> pushLane(lane, Team.BLUE)
        blue == 0 && red > 0 -> pushLane(lane, Team.RED)
    }
}

/** A team's claim to win from a push, applied unless both teams claim at once (p.20). */
data class WinClaim(val team: Team, val reason: WinReason, val text: String)

/**
 * p.8 "Push the Lane", steps 1–3, for [lane] won by [winner]. On a single-lane map a win ends the
 * game at once. With [collect] (end of round on the double-lane map) win claims are returned instead.
 */
fun Game.pushLane(lane: String, winner: Team, collect: MutableList<WinClaim>? = null) {
    val loser = winner.opponent
    val laneDef = geo.lanes.getValue(lane)
    val current = battleZone(lane)
    val index = laneDef.zones.indexOf(current)
    val next = laneDef.zones.getOrNull(if (winner == Team.RED) index + 1 else index - 1)
    // Step 1: flip a Wave counter.
    val waves = (state.wavesLeft[lane] ?: 0)
    if (waves > 0) state.wavesLeft[lane] = waves - 1
    val left = state.wavesLeft[lane] ?: 0
    emit(
        GameEvent.LanePushed(
            lane, winner, next ?: current,
            "${Game.teamName(winner)} pushes the Lane. A Wave counter is flipped ($left left).",
        ),
    )
    val claims = collect ?: ArrayList()
    val doubleLane = geo.lanes.size > 1
    if (!doubleLane && left == 0) {
        claim(claims, collect, WinClaim(winner, WinReason.LAST_PUSH, "${Game.teamName(winner)} wins the last push."))
    }
    if (left == 0) state.lastWaveFlipped += lane
    // Step 2: the next Zone towards the loser's Throne.
    if (next == null || next == throneZone(loser)) {
        claim(claims, collect, WinClaim(winner, WinReason.THRONE, "${Game.teamName(winner)} pushes the Lane into the enemy Throne."))
        return
    }
    // Step 3: remove the previous Battle Zone's minions of this Lane; respawn in the new one.
    state.minions.removeAll { it.lane == lane && (it.at == null || zoneOf(it.at!!) == current) }
    state.battleZones[lane] = next
    touch()
    respawnWave(lane, next)
    if (doubleLane && state.lastWaveFlipped.isNotEmpty()) zoneCount()?.let { claim(claims, collect, it) }
}

private fun Game.claim(claims: MutableList<WinClaim>, collect: MutableList<WinClaim>?, c: WinClaim) {
    if (collect == null) {
        win(c.team, c.reason, c.text)
    } else if (claims.none { it.team == c.team }) {
        claims += c
    }
}

/**
 * p.20: "After the last Wave counter on either Lane is flipped, each team counts how many Zones on
 * each Lane are between their Throne and that lane's Battle Zone. The team with more total Zones …
 * wins." [APP] A tie wins for no one and play goes on; the count is made again after every push.
 */
fun Game.zoneCount(): WinClaim? {
    var red = 0
    var blue = 0
    for (lane in geo.map.lanes) {
        val i = lane.zones.indexOf(battleZone(lane.id))
        red += i - 1
        blue += lane.zones.size - 2 - i
    }
    return when {
        red > blue -> WinClaim(Team.RED, WinReason.LAST_PUSH, "Red holds more Zones after the last Wave counter.")
        blue > red -> WinClaim(Team.BLUE, WinReason.LAST_PUSH, "Blue holds more Zones after the last Wave counter.")
        else -> null
    }
}

/**
 * p.8 step 3: "Respawn one minion of the matching type in each Spawn point in the new Battle Zone
 * (If a Spawn point is occupied by a token, first remove that token, then respawn the minion)";
 * "If a Spawn point is occupied by a non-token obstacle, the owning team must place that minion in
 * the nearest empty space of their choice in the Battle Zone (starting with the team whose symbol is
 * on the tie breaker coin)". [APP] "Nearest" is measured from that Spawn point.
 */
private fun Game.respawnWave(lane: String, zone: String) {
    val waiting = ArrayList<Pair<MinionState, Hex>>()
    for (space in geo.minionSpawns(zone)) {
        val spawn = space.spawn ?: continue
        val type = spawn.kind.minion ?: continue
        val occ = occupant(space.hex)
        if (occ != null && state.piece(occ)?.token == true) removePiece(occ)
        val m = MinionState(state.newId("m"), spawn.team, type, null, lane)
        state.minions += m
        if (occupant(space.hex) == null) {
            m.at = space.hex
            touch()
        } else {
            waiting += m to space.hex
        }
    }
    val order = waiting.filter { it.first.team == state.tieBreaker } + waiting.filter { it.first.team != state.tieBreaker }
    for ((m, from) in order) {
        val options = nearestEmptyIn(zone, from)
        if (options.isEmpty()) {
            state.minions.remove(m)
            touch()
            continue
        }
        val hex = chooseMinionSpace(m, options, "Place your ${Game.minionName(m.type)} minion (its Spawn point is taken)")
        m.at = hex
        touch()
        emit(GameEvent.Placed(m.id, null, hex, "${name(m.id)} is placed in $hex."))
    }
}

private fun Game.chooseMinionSpace(m: MinionState, options: Collection<Hex>, prompt: String): Hex {
    val choice = ask(
        teamSeat(m.team), DecisionKind.MINION_RETURN, prompt,
        options.sortedHexes().map { Choice.Space(it) },
        context = dev.quinton.goa.core.game.DecisionContext(subjectId = m.id),
    ) as Choice.Space
    return choice.hex
}

/** Empty spaces of [zone] nearest to [from] (p.18 "Nearest": count spaces including obstacles). */
fun Game.nearestEmptyIn(zone: String, from: Hex): List<Hex> {
    val empty = geo.zoneSpaces[zone].orEmpty().filter { isEmpty(it) }
    val best = empty.minOfOrNull { HexGrid.distance(from, it) } ?: return emptyList()
    return empty.filter { HexGrid.distance(from, it) == best }
}

/** The live position as a core:domain [BoardState] (to reuse the audited [BoardRules]). */
fun Game.boardState(): BoardState {
    val pieces = ArrayList<BoardPiece>()
    state.heroes.forEach { h -> h.at?.let { pieces += HeroPiece(h.id, it, h.id, h.team) } }
    state.minions.forEach { m -> m.at?.let { pieces += MinionPiece(m.id, it, m.team, m.type, m.lane) } }
    state.pieces.forEach { p -> p.at?.let { pieces += TokenPiece(p.id, it, p.label, p.team) } }
    return BoardState(state.mapId, pieces, LinkedHashMap(state.battleZones))
}

/**
 * p.18 "Minions Outside of the Battle Zone": "If any minion miniature ends up outside the Battle Zone
 * after you perform an action, move it by the shortest path of empty spaces to an empty space in the
 * same Battle Zone … If there are multiple valid shortest paths, the minion's team decides. If there is
 * no path …, place that minion in the nearest empty space of the Battle Zone"; "the team whose symbol
 * is currently revealed on the Tie Breaker coin moves their minion first". p.20: its own Lane's Zone.
 */
fun Game.returnMinions() {
    val out = state.minions.filter { m -> m.at != null && zoneOf(m.at!!) != battleZone(m.lane) }
    if (out.isEmpty()) return
    val ordered = out.filter { it.team == state.tieBreaker } + out.filter { it.team != state.tieBreaker }
    for (m in ordered) {
        val at = m.at ?: continue
        if (zoneOf(at) == battleZone(m.lane)) continue
        val options = BoardRules.returnToBattleZone(geo, boardState(), at, battleZone(m.lane))
        if (options.isEmpty()) continue
        val hex = if (options.size == 1) options.single() else chooseMinionSpace(m, options, "Return your minion to the Battle Zone")
        m.at = hex
        touch()
        emit(GameEvent.Moved(m.id, listOf(hex), "${name(m.id)} returns to the Battle Zone."))
    }
}

/**
 * p.19 "Respawn" (by card text): "place the minion miniature on the board into an empty space. You can
 * only respawn a minion if there are more minion spawn points of that type and color (empty or not),
 * than there are minion miniatures of that type and color present in that Battle Zone".
 */
fun Game.canRespawnMinion(team: Team, type: MinionType, lane: String): Boolean {
    val zone = battleZone(lane)
    val kind = SpawnKind.entries.first { it.minion == type }
    val points = geo.minionSpawns(zone).count { it.spawn?.team == team && it.spawn?.kind == kind }
    val present = state.minions.count { it.team == team && it.type == type && it.lane == lane && it.at?.let(::zoneOf) == zone }
    return points > present
}

fun Game.respawnMinion(team: Team, type: MinionType, lane: String, at: Hex): String {
    check(isEmpty(at)) { "Respawn into a non-empty space" }
    val m = MinionState(state.newId("m"), team, type, at, lane)
    state.minions += m
    touch()
    emit(GameEvent.Placed(m.id, null, at, "${name(m.id)} respawns in $at."))
    return m.id
}

// ---- heroes ------------------------------------------------------------------------------------

/**
 * p.13 "When a Hero is Defeated": removed from the board; an unresolved card "is resolved, without
 * performing an action"; its Active effects are cancelled and markers returned; the defeater collects
 * coins equal to the hero's level and the defeater's friendly heroes assist coins equal to its lowest
 * card Tier; its team spends Life counters equal to its lowest card Tier, and spending the last loses.
 */
fun Game.defeatHero(heroId: String, byHero: String?) {
    val h = hero(heroId)
    val onBoard = h.at != null || state.pieces.any { it.heroBody == heroId && it.at != null }
    if (!onBoard) return
    val by = byHero?.let { " by ${heroName(it)}" } ?: ""
    emit(GameEvent.Defeated(heroId, byHero, "${heroName(heroId)} is defeated$by."))
    h.at = null
    state.pieces.filter { it.heroBody == heroId }.forEach { state.pieces.remove(it) }
    touch()
    for (c in h.cards.filter { it.zone == Zone.PLAYED }) {
        moveCard(heroId, c.id, Zone.RESOLVED, slot = state.turn, keepEffects = true)
        note("${heroName(heroId)}'s ${cardName(c.id)} is resolved without an action.")
    }
    cancelEffectsOf(heroId)
    h.markers.removeAll { !it.persistent }
    touch()
    val tier = lowestTier(heroId)
    if (byHero != null && teamOf(byHero) != h.team) {
        gainCoins(byHero, h.level, "defeated ${heroName(heroId)}")
        for (f in state.heroes) {
            if (f.team == teamOf(byHero) && f.id != byHero) gainCoins(f.id, tier, "assist")
        }
    }
    val q = LifeQuery(heroId, byHero, tier)
    val cost = tier + sources().sumOf { (b, s) -> b.extraLifeCost(this, s, q) }
    val left = maxOf(0, (state.lifeCounters[h.team] ?: 0) - cost)
    state.lifeCounters[h.team] = left
    emit(GameEvent.LifeSpent(h.team, cost, left, "${Game.teamName(h.team)} spends $cost Life counter${if (cost == 1) "" else "s"} ($left left)."))
    state.heroDefeatedThisRound = true
    trigger(Trigger.HeroDefeated(heroId, byHero))
    if (left == 0) win(h.team.opponent, WinReason.LIFE_COUNTERS, "${Game.teamName(h.team)} has spent its last Life counter.")
    if (heroId in actors) throw ActorDefeated(heroId)
}

/**
 * p.7 "Hero Respawn": "you may choose to respawn on any empty Spawn point in your Throne Zone …
 * If no empty Spawn points are available, respawn in the nearest empty space, adjacent to a Spawn
 * point in your Throne Zone" ([BoardRules.heroRespawn]).
 */
fun Game.respawnSpaces(heroId: String): List<Hex> =
    BoardRules.heroRespawn(geo, boardState(), hero(heroId).team).filter { isEmpty(it) }.sortedHexes()
