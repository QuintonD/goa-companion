package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Replacement
import dev.quinton.goa.core.game.effects.ReplacementResult
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.SrcKind
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/*
 * Active effects (p.17), markers (p.18), immunity and targeting (p.10, p.13, p.18), and the dispatch
 * of triggers and replacements to the behaviours in force.
 */

/** Tell every behaviour in force about [t], in the p.17 order (coin team first, then seat order). */
fun Game.trigger(t: Trigger) {
    for ((b, s) in sources().toList()) {
        if (!stillInForce(s)) continue
        hook(s) { b.on(this, s, t) }
        if (state.winner != null) throw GameEnded()
    }
}

/**
 * Run a behaviour's text: a mandatory step it cannot do stops that text only (p.10), never the action
 * or step around it.
 */
inline fun Game.hook(src: Src?, block: () -> Unit) {
    try {
        block()
    } catch (f: Fizzle) {
        note("${src?.cardId?.let { cardName(it) } ?: src?.key ?: "An effect"} stops: ${f.message}.")
    }
}

/** Apply a timed Active effect's text once ("End of turn:", "End of round:", "after playing cards", p.17–18). */
fun Game.applyTimed(e: ActiveEffect) {
    e.spent = true
    touch()
    val b = data.registry.behavior(e.key ?: e.cardId) ?: return
    val s = srcOf(e)
    hook(s) { b.applyTimed(this, s) }
}

/** Ask the behaviours whether [r] is replaced; the first that handles it wins. */
fun Game.replacement(r: Replacement): ReplacementResult {
    for ((b, s) in sources().toList()) {
        if (!stillInForce(s)) continue
        var res = ReplacementResult()
        hook(s) { res = b.replace(this, s, r) }
        if (res.handled) return res
    }
    return ReplacementResult()
}

/** A source captured before a trigger may have been cancelled by an earlier one. */
private fun Game.stillInForce(s: Src): Boolean = when (s.kind) {
    SrcKind.EFFECT -> s.effect != null && state.effects.contains(s.effect) && inForce(s.effect)
    SrcKind.PIECE -> s.piece != null && state.pieces.contains(s.piece) && s.piece.at != null
    SrcKind.MARKER -> s.holder != null && state.hero(s.holder)?.markers?.contains(s.marker) == true
    SrcKind.ULTIMATE, SrcKind.HERO -> true
}

// ---- Active effects --------------------------------------------------------------------------

/**
 * Start an Active effect of [cardId] (p.17). "Only one instance of an active effect per card can be
 * active. Repeating an active effect does not duplicate it" (p.10): an existing effect of the same
 * card and key is replaced. [action] is the type of the action that made it (immunity, p.18).
 */
fun Game.addEffect(
    owner: String,
    cardId: String,
    timing: Timing,
    action: ActionKind,
    key: String? = null,
    data: Map<String, String> = emptyMap(),
): ActiveEffect {
    state.effects.removeAll { it.owner == owner && it.cardId == cardId && it.key == key }
    val e = ActiveEffect(state.nextId++, cardId, owner, timing, state.round, state.turn, action, key, LinkedHashMap(data))
    state.effects += e
    touch()
    if (mode != RunMode.PROBE) {
        val what = when (timing) {
            Timing.THIS_TURN -> "this turn"
            Timing.THIS_ROUND -> "this round"
            Timing.NEXT_TURN -> "next turn"
            Timing.NEXT_TURN_AFTER_PLAYING -> "next turn, after playing cards"
            Timing.END_OF_TURN -> "at the end of the turn"
            Timing.END_OF_ROUND -> "at the end of the round"
            Timing.UNTIL_CANCELLED -> "from now on"
        }
        note("${heroName(owner)}'s ${cardName(cardId)} is active $what.")
    }
    return e
}

/** Cancel the Active effects of [cardId] of [owner] (card state change, p.17). */
fun Game.cancelEffectsOf(owner: String, cardId: String) {
    if (state.effects.removeAll { it.owner == owner && it.cardId == cardId }) touch()
}

/** Cancel every Active effect of [owner] (defeat, p.13; retrieve, p.8). */
fun Game.cancelEffectsOf(owner: String) {
    if (state.effects.removeAll { it.owner == owner }) touch()
}

fun Game.cancelEffect(e: ActiveEffect) {
    if (state.effects.remove(e)) touch()
}

/** Card ids with an Active effect in force or pending (shown sideways, p.7). */
fun Game.activeCards(owner: String): List<String> =
    state.effects.filter { it.owner == owner && !it.spent }.map { it.cardId }.distinct()

// ---- markers ---------------------------------------------------------------------------------

/** Give [heroId] a marker (p.18 "Markers"). One marker of a kind per giver: giving it again moves it. */
fun Game.giveMarker(heroId: String, kind: String, from: String?, persistent: Boolean = false): Marker {
    for (h in state.heroes) h.markers.removeAll { it.kind == kind && it.from == from }
    val m = Marker(kind, from, persistent)
    hero(heroId).markers += m
    touch()
    note("${heroName(heroId)} gets the ${kind.removePrefix("marker_").replace('_', ' ')} marker.")
    trigger(Trigger.MarkerGiven(heroId, kind, from))
    return m
}

fun Game.removeMarker(heroId: String, marker: Marker) {
    if (hero(heroId).markers.remove(marker)) touch()
}

// ---- values ----------------------------------------------------------------------------------

/** The value of [stat] printed on [cardId] (behaviours may replace it, e.g. Hanu's "set … to 11"). */
fun Game.printedValue(unitId: String, cardId: String, stat: Stat): Int? {
    val card = card(cardId)
    val base = if (stat == Stat.INITIATIVE) card.initiative else card.values.get(stat)
    val q = StatQuery(unitId, stat, cardId)
    var override: Int? = null
    for ((b, s) in sources()) b.printedValue(this, s, q)?.let { override = it }
    return override ?: base
}

/** Item bonus on [stat] for [heroId] (p.16: +1 per matching item; doubled by some effects). */
fun Game.itemBonus(heroId: String, stat: Stat): Int {
    val h = state.hero(heroId) ?: return 0
    val items = h.cards.count { it.zone == Zone.ITEM && card(it.id).item == stat }
    if (items == 0) return 0
    val mult = sources().maxOfOrNull { (b, s) -> b.itemMultiplier(this, s, heroId) } ?: 1
    return items * maxOf(1, mult)
}

/** Sum of behaviour modifiers for [q] (poison, Weakness, Xargatha's Ultimate…). */
fun Game.statDelta(q: StatQuery): Int = sources().sumOf { (b, s) -> b.statDelta(this, s, q) }

/**
 * The value of an icon on a card for its owner now (p.16 "Item Bonus": items affect "the value of
 * the Initiative, Attack, Defense, Movement, Range, or Radius icons on a card"; "If an Action card
 * has no matching icon, the item has no effect"), plus modifiers. Null when the card has no such icon.
 * Card-text bonuses are added by the script, never here (items "never affect values in the card text").
 */
fun Game.cardValue(heroId: String, cardId: String, stat: Stat, action: ActionKind? = null): Int? {
    val printed = printedValue(heroId, cardId, stat) ?: return null
    return printed + itemBonus(heroId, stat) + statDelta(StatQuery(heroId, stat, cardId, action))
}

// ---- immunity and targets --------------------------------------------------------------------

/**
 * p.13 "Heavy Minion Immunity": "As long as there are any friendly non-heavy minions in the Battle
 * Zone, the heavy minion is Immune and can't be affected by any actions (including actions of
 * friendly heroes)". Checked in the heavy minion's own Lane's Battle Zone (p.20).
 */
fun Game.heavyImmune(minionId: String): Boolean {
    val m = state.minion(minionId) ?: return false
    if (m.type != MinionType.HEAVY) return false
    val zone = battleZone(m.lane)
    return state.minions.any { o ->
        o.team == m.team && o.type != MinionType.HEAVY && o.at?.let(::zoneOf) == zone
    }
}

/**
 * p.18 "Immune": is [q.targetId] immune to this action? "You are never immune to your own actions."
 * [ignoreHeavyImmunity]: the card says "ignore heavy minion immunity".
 */
fun Game.isImmune(q: AffectQuery, ignoreHeavyImmunity: Boolean = false, ignoreImmunity: Boolean = false): Boolean {
    if (ignoreImmunity) return false
    if (q.actorId != null && heroBodyOf(q.targetId) == q.actorId) return false
    if (!ignoreHeavyImmunity && heavyImmune(q.targetId) && q.actorId != null) return true
    return sources().any { (b, s) -> b.immune(this, s, q) }
}

/**
 * Can [targetId] be targeted by the current action of [actorId] (p.10 "Picking Targets")? Immune
 * units cannot be targeted; some effects forbid a target without immunity (e.g. a Smoke bomb line).
 */
fun Game.canTarget(
    targetId: String,
    actorId: String,
    kind: ActionKind,
    cardId: String?,
    primary: Boolean = true,
    ranged: Boolean = false,
    ignoreHeavyImmunity: Boolean = false,
    ignoreImmunity: Boolean = false,
): Boolean {
    val basic = cardId?.let { isBasic(it) } ?: false
    val q = AffectQuery(targetId, actorId, kind, primary, basic, cardId, ranged)
    if (isImmune(q, ignoreHeavyImmunity, ignoreImmunity)) return false
    return sources().none { (b, s) -> b.forbidsTarget(this, s, q) }
}

/**
 * For behaviours: is [unitId] affected by the Active effect behind [src] (p.18: a unit immune to an
 * action type "is not affected … by actions of that type (and their active effects)")?
 */
fun Game.affectedBy(src: Src, unitId: String): Boolean {
    val e = src.effect ?: return true
    val q = AffectQuery(unitId, e.owner, e.action, primary = true, basic = isBasic(e.cardId), cardId = e.cardId, fromEffect = true)
    return !isImmune(q)
}

/** Basic cards are the Gold and Silver cards (p.9); the Handicap card replaces one of them. */
fun Game.isBasic(cardId: String): Boolean {
    val c = data.cardOrNull(cardId) ?: return false
    return c.tier == null && (c.color == CardColor.GOLD || c.color == CardColor.SILVER)
}

/** A Lane push or other win in the middle of a run (p.8: "the game ends immediately"). */
fun Game.win(team: Team, reason: dev.quinton.goa.core.game.WinReason, text: String) {
    if (state.winner != null) return
    state.winner = team
    state.winReason = reason
    state.step = Step.GAME_OVER
    emit(GameEvent.GameOver(team, reason, text))
    throw GameEnded()
}
