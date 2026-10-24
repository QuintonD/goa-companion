package dev.quinton.goa.core.game.effects

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.DefenseScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.PushResult
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.addEffect
import dev.quinton.goa.core.game.engine.canMove
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.discardPile
import dev.quinton.goa.core.game.engine.forceDiscard
import dev.quinton.goa.core.game.engine.move
import dev.quinton.goa.core.game.engine.push
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.tokensOnBoard
import dev.quinton.goa.core.game.engine.unitsOnBoard
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Stat

/*
 * Helpers for card scripts (EFFECTS.md "Primitives"). Each one is a rule of the rulebook in one
 * call; the page is in its comment. Positions are read at call time ("If your Active effect has a
 * Radius, it is always calculated from your current space", p.17).
 */

// ---- who is where --------------------------------------------------------------------------

fun ActionScope.isEnemy(id: String): Boolean = g.isEnemy(heroId, id)

/** p.17 "Friendly": another unit on your team, never you. */
fun ActionScope.isFriendly(id: String): Boolean = g.isFriendly(heroId, id)

fun ActionScope.isHero(id: String): Boolean = g.isHero(id)

fun ActionScope.isMinion(id: String): Boolean = g.isMinion(id)

fun ActionScope.distanceTo(id: String): Int? {
    val me = hero.at ?: return null
    return g.posOf(id)?.let { g.distance(me, it) }
}

/** Units 1..[n] spaces away ("in radius" / "in range", p.9), never you. */
fun ActionScope.unitsWithin(n: Int): List<String> {
    val me = hero.at ?: return emptyList()
    return g.unitsOnBoard().filter { id -> g.posOf(id)?.let { g.distance(me, it) in 1..n } == true }
}

fun ActionScope.unitsInRadius(): List<String> = unitsWithin(radius)

fun ActionScope.unitsInRange(): List<String> = unitsWithin(range)

/** p.17 "Adjacent". */
fun ActionScope.adjacentUnits(): List<String> = unitsWithin(1)

fun ActionScope.tokensWithin(n: Int): List<String> {
    val me = hero.at ?: return emptyList()
    return g.tokensOnBoard().filter { id -> g.posOf(id)?.let { g.distance(me, it) in 1..n } == true }
}

/** p.19 "Straight Line": adjacent units are in a straight line; otherwise along one of the six lines. */
fun ActionScope.inStraightLine(id: String): Boolean {
    val me = hero.at ?: return false
    val p = g.posOf(id) ?: return false
    return HexGrid.inStraightLine(me, p)
}

// ---- Active effects ------------------------------------------------------------------------

/** Start this card's Active effect (p.17). The card's [CardScript.behavior] (or [key]) gives its hooks. */
fun ActionScope.activeEffect(
    timing: Timing,
    key: String? = null,
    data: Map<String, String> = emptyMap(),
): ActiveEffect =
    g.addEffect(heroId, cardId, timing, kind, key, data)

/** For behaviours: the owner's current space. */
fun Game.ownerPos(src: Src): Hex? = src.owner?.let { posOf(it) }

/** For behaviours of card effects: the card's Radius now (items count, p.16). */
fun Game.radiusOf(src: Src): Int = src.owner?.let { o -> src.cardId?.let { cardValue(o, it, Stat.RADIUS) } } ?: 0

/** [id] is in the radius of the effect behind [src], measured from its owner's current space (p.17). */
fun Game.inRadiusOf(src: Src, id: String, radius: Int = radiusOf(src)): Boolean {
    val c = ownerPos(src) ?: return false
    val p = posOf(id) ?: return false
    return distance(c, p) in 1..radius
}

// ---- steps ---------------------------------------------------------------------------------

/**
 * Move yourself as card text says (p.11 "Movement in the Card Text": no Fast Travel; exact distance
 * unless "up to" or "you may"). [optional] for "you may"; "up to N" is min 0.
 */
@Suppress("LongParameterList")
fun ActionScope.moveSelf(
    min: Int,
    max: Int = min,
    optional: Boolean = false,
    straightLine: Boolean = false,
    ignoreObstacles: Boolean = false,
    stepwise: Boolean = false,
    endFilter: ((Hex) -> Boolean)? = null,
): List<Hex> = g.move(
    MoveSpec(heroId, min, max, straightLine, false, ignoreObstacles, endFilter = endFilter, stepwise = stepwise),
    heroId,
    moveText(min, max, straightLine),
    optional,
    cardId,
)

/** Move another unit or a token as card text says; you choose the path and the end (p.10). */
@Suppress("LongParameterList")
fun ActionScope.moveOther(
    id: String,
    min: Int,
    max: Int = min,
    optional: Boolean = false,
    straightLine: Boolean = false,
    ignoreObstacles: Boolean = false,
    endFilter: ((Hex) -> Boolean)? = null,
    chooser: String = heroId,
): List<Hex> {
    target(id)
    return g.move(
        MoveSpec(id, min, max, straightLine, false, ignoreObstacles, endFilter = endFilter),
        chooser,
        "Move ${g.name(id)} " + moveText(min, max, straightLine).removePrefix("Move ").lowercase(),
        optional,
        cardId,
    )
}

fun ActionScope.canMoveOther(
    id: String,
    min: Int,
    max: Int = min,
    straightLine: Boolean = false,
    endFilter: (
        (Hex) -> Boolean
    )? = null,
): Boolean =
    g.canMove(MoveSpec(id, min, max, straightLine, endFilter = endFilter))

private fun moveText(min: Int, max: Int, straight: Boolean): String {
    val n = if (min == max) {
        "$min"
    } else if (min == 0) {
        "up to $max"
    } else {
        "$min to $max"
    }
    return "Move $n space${if (max == 1) "" else "s"}${if (straight) " in a straight line" else ""}"
}

/**
 * Push [id] away from you (p.18 "Push"). [spaces] fixed, or ask a distance first with [chooseDistance].
 * Returns null when there is no straight line or it can't be pushed.
 */
fun ActionScope.pushAway(id: String, spaces: Int, ignoreObstacles: Boolean = false): PushResult? {
    target(id)
    return g.push(id, pos, spaces, ignoreObstacles, heroId)
}

/** "up to N spaces": the distance to push or move (0..N or the listed values). */
fun ActionScope.chooseDistance(values: List<Int>, prompt: String = "Choose the distance"): Int =
    if (values.size == 1) values.single() else chooseNumber(prompt, values)

/** "X discards a card, if able" (p.10). */
fun ActionScope.discardIfAble(victim: String, what: String = "a card", filter: (Card) -> Boolean = { true }): String? {
    target(victim)
    return g.forceDiscard(victim, heroId, orDefeated = false, sourceCardId = cardId, what = what, filter = filter)
}

/** "X discards a card, or is defeated" ([APP] RULINGS.md: must discard if able). */
fun ActionScope.discardOrDefeat(
    victim: String,
    what: String = "a card",
    filter: (Card) -> Boolean = { true },
): String? {
    target(victim)
    return g.forceDiscard(victim, heroId, orDefeated = true, sourceCardId = cardId, what = what, filter = filter)
}

/** "You may retrieve a discarded card" (p.10): returns the card retrieved. */
fun ActionScope.mayRetrieve(filter: (String) -> Boolean = { true }, who: String = heroId): String? {
    val pile = g.discardPile(who).filter(filter)
    if (pile.isEmpty()) return null
    val c = chooseCard("You may retrieve a discarded card", pile, optional = true, chooser = who) ?: return null
    g.retrieve(who, c)
    return c
}

/** "up to N" units: chosen one at a time, each different; may stop after any (or at 0). */
fun ActionScope.chooseUpTo(n: Int, prompt: String, ids: List<String>, atLeastOne: Boolean = false): List<String> {
    val picked = ArrayList<String>()
    while (picked.size < n) {
        val left = ids.filter { it !in picked }
        if (left.isEmpty()) break
        val optional = !(atLeastOne && picked.isEmpty())
        val opts = left.map { if (g.isToken(it)) Choice.Token(it) else Choice.Unit(it) } +
            if (optional) listOf(Choice.Decline(if (picked.isEmpty()) "None" else "Done")) else emptyList()
        val c = g.ask(
            heroId,
            DecisionKind.CHOOSE_UNIT,
            "$prompt (${picked.size + 1} of up to $n)",
            opts,
            cardId,
            auto = !optional,
        )
        val id = when (c) {
            is Choice.Unit -> c.unitId
            is Choice.Token -> c.tokenId
            else -> null
        } ?: break
        picked += id
    }
    return picked
}

/** The hero's cards in a zone (p.10). */
fun ActionScope.cardsIn(zone: Zone): List<String> = hero.cards.filter { it.zone == zone }.map { it.id }

/** Units and tokens 1..[n] spaces away. */
fun ActionScope.objectsWithin(n: Int): List<String> = unitsWithin(n) + tokensWithin(n)

/** An Active effect started by a Defense action's text (p.14 step 4; the discarded card carries it, p.7). */
fun DefenseScope.activeEffect(
    timing: Timing,
    data: Map<String, String> = emptyMap(),
    key: String? = null,
): ActiveEffect =
    g.addEffect(heroId, cardId, timing, ActionKind.DEFENSE, key, data)
