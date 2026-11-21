package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Facts [GENERAL.md] reasons about, built only from [HContext]'s public view.
 * A hero playbook does not need these; the general book does.
 */

internal fun spareWeightOf(turn: Int): Double = when (turn) {
    1 -> 3.0
    2 -> 2.0
    3 -> 1.5
    else -> 0.8
}

/** No card left that can answer an attack, or the last card is about to be played (G11). */
internal fun heroIsDefenseless(ctx: HContext, heroId: String): Boolean {
    val h = ctx.state.hero(heroId) ?: return false
    if (h.hand.isEmpty()) return true
    if (ctx.state.revealed || ctx.roundOver) return false
    return h.hand.size <= 1 && ctx.playsLeft(heroId) > 0
}

/** Has not resolved a card yet, and still will this turn. */
internal fun heroStillToAct(ctx: HContext, heroId: String): Boolean {
    if (ctx.roundOver) return false
    if (heroId in ctx.state.turnRecord.resolved) return false
    val h = ctx.state.hero(heroId) ?: return false
    if (!ctx.state.revealed || ctx.state.step == Step.PLAY || ctx.state.step == Step.TURN_START) {
        return ctx.playsLeft(heroId) > 0 || h.unresolved.isNotEmpty()
    }
    return h.unresolved.isNotEmpty() && heroId != ctx.state.acting
}

/** An enemy who has not resolved can still aim an attack at a square we end on. */
internal fun stillThreatens(ctx: HContext, enemy: HeroState): Boolean {
    if (enemy.at == null || ctx.roundOver) return false
    if (enemy.id in ctx.state.turnRecord.resolved) return false
    if (ctx.state.revealed) return enemy.unresolved.isNotEmpty()
    return ctx.playsLeft(enemy.id) > 0 || enemy.unresolved.isNotEmpty()
}

internal fun defensePool(ctx: HContext, h: HeroState, without: String?): List<HCard> {
    val cards = if (h.team == ctx.team || ctx.profile.knowHands) {
        h.hand.map { ctx.card(it.id) }
    } else {
        ctx.possibleCards(h.id).map { it.card }
    }
    return cards.filter { it.id != without && (it.canDefend || it.block) }
}

internal fun holdsAgainst(ctx: HContext, heroId: String, card: HCard, total: Int, ranged: Boolean, at: Hex): Boolean {
    if (ranged && card.blocksRanged) return true
    if (!ranged && card.blocksMelee) return true
    val printed = card.defense ?: return false
    if (!card.canDefend && !card.block) return false
    val side = ctx.state.hero(heroId)?.team ?: return false
    val mod = if (card.ignoresMinionModifiers) 0 else ctx.minionModifier(at, side)
    return printed + ctx.itemBonus(heroId, Stat.DEFENSE) + mod >= total
}

/**
 * An enemy who can still act has a possible attack that reaches [at] and beats every defense
 * left after [without] leaves the hand (G2). Respects a card the pool still allows, not one sample.
 */
internal fun unansweredKill(ctx: HContext, heroId: String, at: Hex, without: String? = null): Boolean {
    if (!ctx.profile.threatMap || ctx.roundOver) return false
    val h = ctx.state.hero(heroId) ?: return false
    val pool = defensePool(ctx, h, without)
    for (enemy in ctx.enemies()) {
        if (!stillThreatens(ctx, enemy)) continue
        val from = enemy.at ?: continue
        if (killingCard(ctx, enemy, from, at, heroId, pool) != null) return true
    }
    return false
}

/** Playing [card] leaves us in reach of a hit we cannot answer and cannot out-initiative (G2). */
internal fun trappedBy(ctx: HContext, at: Hex, card: HCard): Boolean {
    if (!unansweredKill(ctx, ctx.seat, at, card.id)) return false
    if (!card.hasMovement) return true
    val ours = card.initiative + ctx.itemBonus(ctx.seat, Stat.INITIATIVE)
    val h = ctx.own() ?: return true
    val pool = defensePool(ctx, h, card.id)
    for (enemy in ctx.enemies()) {
        if (!stillThreatens(ctx, enemy)) continue
        val from = enemy.at ?: continue
        val killing = killingCard(ctx, enemy, from, at, ctx.seat, pool) ?: continue
        val init = killing.initiative + ctx.itemBonus(enemy.id, Stat.INITIATIVE)
        if (init >= ours) return true
    }
    return false
}

private fun killingCard(
    ctx: HContext,
    enemy: HeroState,
    from: Hex,
    at: Hex,
    heroId: String,
    pool: List<HCard>,
): HCard? {
    for (p in ctx.possibleCards(enemy.id)) {
        val c = p.card
        if (!c.attackCard && !c.discardOrDefeat) continue
        if (!worthFearing(ctx, p)) continue
        if (ctx.distance(from, at) > ctx.attackReach(enemy.id, c)) continue
        val total = c.attack + ctx.itemBonus(enemy.id, Stat.ATTACK)
        if (pool.none { holdsAgainst(ctx, heroId, it, total, c.ranged, at) }) return c
    }
    return null
}

/** A hidden card is a real fear when its colour is down to a few candidates. A known hand always is. */
private fun worthFearing(ctx: HContext, p: PossibleCard): Boolean {
    if (p.playingNow || ctx.profile.knowHands) return p.weight > 0.0
    return p.weight >= 0.25
}

internal fun enemiesHaveAttacks(ctx: HContext): Boolean = ctx.enemies().any { enemy ->
    stillThreatens(ctx, enemy) && ctx.possibleCards(enemy.id).any { it.card.attackCard && worthFearing(ctx, it) }
}

/** A teammate can already reach [target] with an attack (G14, G60). */
internal fun allyHunting(ctx: HContext, target: HeroState): Boolean {
    if (!ctx.profile.focusFire) return false
    val pos = target.at ?: return false
    return ctx.allies().any { ally ->
        val at = ally.at ?: return@any false
        attackCards(ctx, ally).any { ctx.distance(at, pos) <= ctx.attackReach(ally.id, it) + 2 }
    }
}

internal fun attackCards(ctx: HContext, h: HeroState): List<HCard> =
    if (h.team == ctx.team || ctx.profile.knowHands) {
        (h.hand + h.unresolved).map { ctx.card(it.id) }.filter { it.attackCard }
    } else {
        ctx.possibleCards(h.id).map { it.card }.filter { it.attackCard }
    }

/**
 * One discard now leaves [enemy] with at most one card on turn 4, and a friendly hero is close
 * enough to finish (G10). Not the case when they are already empty: that kill is this turn.
 */
internal fun stripSetsUp(ctx: HContext, enemy: HeroState): Boolean {
    if (!ctx.profile.twoTurnPlans || ctx.turn !in 2..3) return false
    if (heroIsDefenseless(ctx, enemy.id)) return false
    val at = enemy.at ?: return false
    val arrive = enemy.hand.size - 1 - (4 - ctx.turn)
    if (arrive > 1) return false
    return ctx.state.heroes.any { hero ->
        hero.team == ctx.team && hero.at?.let { ctx.distance(it, at) <= 6 } == true
    }
}

internal fun inAttackReach(ctx: HContext, heroId: String, at: Hex, card: HCard): Boolean {
    val from = ctx.state.hero(heroId)?.at ?: return false
    if (!card.attackCard && !card.forcesDiscard) return false
    return ctx.distance(from, at) <= ctx.attackReach(heroId, card).coerceAtLeast(1)
}

/** The zone a push by [pushing] moves this lane into. A throne is not a place to stand early. */
internal fun nextZoneOf(ctx: HContext, lane: String, pushing: Team): String? {
    val zones = ctx.game.geo.lanes[lane]?.zones ?: return null
    val bz = ctx.state.battleZones[lane] ?: return null
    val i = zones.indexOf(bz)
    if (i < 0) return null
    val n = if (pushing == Team.RED) i + 1 else i - 1
    val id = zones.getOrNull(n) ?: return null
    if (ctx.game.geo.zones[id]?.kind == ZoneKind.THRONE) return null
    return id
}

internal fun stepsToEnemyThrone(ctx: HContext, lane: String, side: Team): Int {
    val zones = ctx.game.geo.lanes[lane]?.zones ?: return 9
    val bz = ctx.state.battleZones[lane] ?: return 9
    val i = zones.indexOf(bz)
    if (i < 0) return 9
    return if (side == Team.RED) zones.lastIndex - i else i
}

/**
 * Where [heroId] wants to be. At match point, the current Battle Zone. With a decided push,
 * the next zone, so we are not stranded when it moves (G51).
 */
internal fun goalZoneOf(ctx: HContext, heroId: String): String? {
    if (!ctx.profile.laneTiming) return null
    val h = ctx.state.hero(heroId) ?: return null
    if (h.at == null) return null
    var best: String? = null
    var bestScore = Double.NEGATIVE_INFINITY
    for ((lane, bz) in ctx.state.battleZones) {
        val margin = ctx.laneMargin(lane)
        val dest = when {
            ctx.matchPoint(lane) || stepsToEnemyThrone(ctx, lane, h.team) <= 1 -> bz
            margin <= -2 -> nextZoneOf(ctx, lane, h.team.opponent) ?: bz
            margin >= 2 -> nextZoneOf(ctx, lane, h.team) ?: bz
            else -> bz
        }
        val score = (if (ctx.matchPoint(lane)) 4.0 else 0.0) - ctx.distanceToZone(h.at!!, dest)
        if (score > bestScore) {
            bestScore = score
            best = dest
        }
    }
    return best
}

internal fun spawnCount(ctx: HContext, zone: String, side: Team): Int =
    ctx.game.geo.minionSpawns(zone).count { it.spawn?.team == side }

/** Defender spawns minus attacker spawns. Positive means the push hands them extra minions (G13). */
internal fun spawnEdge(ctx: HContext, zone: String, attacker: Team): Int =
    spawnCount(ctx, zone, attacker.opponent) - spawnCount(ctx, zone, attacker)

/**
 * Killing one enemy minion on [lane] changes `60 × W` (G58), from [HContext.team]'s side.
 * Raw counts, not push potential: the minion we take now is the one the projection was hoping for.
 */
internal fun laneKillDelta(ctx: HContext, lane: String, side: Team = ctx.team): Double {
    val zones = ctx.game.geo.lanes[lane]?.zones ?: return 0.0
    val bz = ctx.state.battleZones[lane] ?: return 0.0
    val i = zones.indexOf(bz)
    if (i < 0) return 0.0
    val red = ctx.minionCount(Team.RED, lane).toDouble()
    val blue = ctx.minionCount(Team.BLUE, lane).toDouble()
    val waves = ctx.state.wavesLeft[lane] ?: 1
    val k = zones.lastIndex - i
    val w0 = laneWinOf(k, i, waves, pushProb(red, blue))
    val theirs = if (side == Team.RED) blue else red
    val w1 = if (theirs <= 1.0) {
        // The last one pushes now (p.8): one zone on, a fresh wave, the throne when it was next.
        if (side == Team.RED) pushedWin(k - 1, i + 1, waves, true) else pushedWin(k + 1, i - 1, waves, false)
    } else {
        laneWinOf(k, i, waves, if (side == Team.RED) pushProb(red, blue - 1.0) else pushProb(red - 1.0, blue))
    }
    val dRed = w1 - w0
    val d = if (side == Team.RED) dRed else -dRed
    val weight = if (ctx.profile.laneTiming && ctx.matchPoint(lane)) 100.0 else 60.0
    return weight * 2.0 * d
}

/**
 * P(Red wins the lane) just after a push leaves [k] zones to Blue's throne and [m] to Red's. On the
 * last wave that push is the game (p.8).
 */
private fun pushedWin(k: Int, m: Int, waves: Int, redPushed: Boolean): Double = when {
    k <= 0 -> 1.0
    m <= 0 -> 0.0
    waves <= 1 -> if (redPushed) 1.0 else 0.0
    else -> laneWinOf(k, m, waves - 1, 0.5)
}

/** This kill pushes the lane this round into a zone that spawns more defenders (G13). */
internal fun badPush(ctx: HContext, lane: String): Boolean {
    if (!ctx.profile.turnPlans || ctx.matchPoint(lane)) return false
    val next = nextZoneOf(ctx, lane, ctx.team) ?: return false
    if (spawnEdge(ctx, next, ctx.team) < 1) return false
    val ours = ctx.minionCount(ctx.team, lane)
    val theirs = ctx.minionCount(ctx.team.opponent, lane)
    if (theirs <= 1) return true
    return ours >= 2 * (theirs - 1)
}

private fun pushProb(red: Double, blue: Double): Double {
    val a = max(0.0, red)
    val b = max(0.0, blue)
    var p = 1.0 / (1.0 + PortableMath.exp(-1.1 * (a - b)))
    if (a > b && 2 * b - a <= 0.25) p = max(p, 0.95)
    if (b > a && 2 * a - b <= 0.25) p = min(p, 0.05)
    return p
}

/** P(Red wins the lane). The same recursion as [HEval]. */
private fun laneWinOf(k: Int, m: Int, w: Int, p: Double): Double {
    if (k <= 0) return 1.0
    if (m <= 0) return 0.0
    val win = if (k == 1 || w <= 1) 1.0 else laneWinOf(k - 1, m + 1, w - 1, 0.5)
    val lose = if (m == 1 || w <= 1) 0.0 else laneWinOf(k + 1, m - 1, w - 1, 0.5)
    return p * win + (1 - p) * lose
}

/**
 * GENERAL §2 "minion opportunity", priced by the lane: an attack card still to play that reaches an
 * enemy minion from [at] is a share of what that kill does to the lane (the last wave makes it large),
 * a minion two spaces further a smaller share. Expected cards for a hidden hand (the pool's weights),
 * at most two. From [h]'s side; zero once the hero has nothing left to play this round.
 */
internal fun laneThreat(ctx: HContext, h: HeroState, at: Hex, share: Double): Double {
    if (share <= 0.0 || ctx.roundOver || ctx.playsLeft(h.id) <= 0) return 0.0
    val cards: List<Pair<HCard, Double>> = if (h.team == ctx.team || ctx.profile.knowHands) {
        (h.hand + h.unresolved).map { ctx.card(it.id) to 1.0 }
    } else {
        ctx.possibleCards(h.id).map { it.card to it.weight }
    }
    val targets = ctx.state.minions.filter { it.team != h.team && it.at != null && !ctx.heavyImmune(it.id) }
    if (targets.isEmpty()) return 0.0
    var near = 0.0
    var far = 0.0
    var lane: String? = null
    for ((c, w) in cards) {
        if (!c.attackCard) continue
        val reach = ctx.attackReach(h.id, c).coerceAtLeast(1)
        val d = targets.minOf { ctx.distance(at, it.at!!) }
        if (d <= reach) {
            near += w
        } else if (d <= reach + 2) {
            far += w
        }
        if (lane == null) lane = targets.minByOrNull { ctx.distance(at, it.at!!) }?.lane
    }
    val l = lane ?: return 0.0
    val value = laneKillDelta(ctx, l, h.team).coerceAtLeast(0.0)
    return share * value * (minOf(near, 2.0) + 0.35 * minOf(far, 2.0 - minOf(near, 2.0)))
}
