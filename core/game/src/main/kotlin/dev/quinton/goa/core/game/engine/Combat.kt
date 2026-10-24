package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/** The attack a defender answers (p.14). */
data class AttackInfo(
    val attackerId: String,
    val cardId: String,
    val targetId: String,
    val ranged: Boolean,
    val basic: Boolean,
    val primary: Boolean,
    val total: Int,
)

data class AttackResult(
    /** False when the target was no longer valid after "Before the attack" (p.17: the attack "fails to connect"). */
    val connected: Boolean,
    val targetId: String,
    val defeated: Boolean,
    /** The card the defender discarded, if any. */
    val defenseCard: String? = null,
    val total: Int = 0,
    val defenseTotal: Int? = null,
    /** The target was a hero. */
    val hero: Boolean = false,
)

/**
 * Choose the target of an attack (p.13): enemy units only ("You can never attack or defeat friendly
 * heroes and minions"), not immune, matching [valid]. Mandatory unless [optional]: with no valid
 * target the action fizzles (p.10).
 */
fun ActionScope.attackTarget(prompt: String, optional: Boolean = false, valid: (String) -> Boolean): String? {
    val ids = g.unitsOnBoard().filter { g.isEnemy(heroId, it) && canTarget(it, ActionKind.ATTACK) && valid(it) }
    if (ids.isEmpty()) {
        if (optional) return null
        fizzle("no target")
    }
    val t = chooseUnit(prompt, ids, optional) ?: return null
    return target(t)
}

/** "Target a unit adjacent to you." (p.13: non-ranged attacks "can only target units adjacent to you"). */
fun ActionScope.adjacentAttackTarget(
    prompt: String = "Target a unit adjacent to you",
    optional: Boolean = false,
    extra: (String) -> Boolean = { true },
): String? =
    attackTarget(prompt, optional) { id -> isAdjacent(id) && extra(id) }

/** "Target a unit in range." (p.9: Range counts spaces, obstacles included; no line of sight). */
fun ActionScope.rangedAttackTarget(
    prompt: String = "Target a unit in range",
    optional: Boolean = false,
    rangeValue: Int = range,
    extra: (String) -> Boolean = { true },
): String? =
    attackTarget(prompt, optional) { id -> inRange(id, rangeValue) && extra(id) }

fun ActionScope.isAdjacent(id: String): Boolean {
    val me = hero.at ?: return false
    val p = g.posOf(id) ?: return false
    return g.distance(me, p) == 1
}

fun ActionScope.inRange(id: String, n: Int = range): Boolean {
    val me = hero.at ?: return false
    val p = g.posOf(id) ?: return false
    val d = g.distance(me, p)
    return d in 1..n
}

/**
 * Steps 2–6 of the p.14 "Attack/Defense Detailed Order" (step 1, "Before the attack", and step 7,
 * "After the attack", are the script's code around this call). The target is checked again first:
 * "Before the attack" text "happens even if the attack itself fails to connect due to range or other
 * restrictions" (p.17), so a target no longer valid means the attack does not connect.
 * Attack Total (p.13): "Attack value on your card, +1 for every Attack item you have, +/- Attack from
 * the card text" ([bonus]), plus modifiers in force (e.g. poison).
 */
@Suppress("LongParameterList")
fun ActionScope.attack(
    targetId: String,
    ranged: Boolean = card.ranged && primary,
    valid: ((String) -> Boolean)? = null,
    bonus: () -> Int = { 0 },
    value: Int? = null,
    /** Emmitt: "the enemy hero must use the Initiative value of their card and items instead of the Defense value". */
    defenseFromInitiative: Boolean = false,
    /** "the target cannot perform a primary action to defend". */
    noPrimaryDefense: Boolean = false,
): AttackResult {
    target(targetId)
    val me = hero.at
    val tPos = g.posOf(targetId)
    val stillValid = me != null && tPos != null && g.isEnemy(heroId, targetId) &&
        canTarget(targetId, ActionKind.ATTACK) && (valid?.invoke(targetId) ?: true) &&
        (ranged || g.distance(me, tPos) == 1)
    if (!stillValid) {
        g.note("${g.heroName(heroId)}'s attack on ${g.name(targetId)} fails to connect.")
        return AttackResult(connected = false, targetId = targetId, defeated = false)
    }
    val base = value ?: g.cardValue(heroId, cardId, Stat.ATTACK, ActionKind.ATTACK) ?: 0
    val total = base + bonus()
    g.emit(
        GameEvent.Attacked(
            heroId,
            targetId,
            total,
            "${g.heroName(heroId)} attacks ${g.name(targetId)}: Attack $total.",
        ),
    )
    val defender = g.heroBodyOf(targetId)
    if (defender == null) {
        val defeated = if (g.isMinion(targetId)) g.defeatMinion(targetId, heroId) else false
        g.trigger(Trigger.AttackDone(heroId, targetId, defeated))
        return AttackResult(connected = true, targetId = targetId, defeated = defeated, total = total)
    }
    val info = AttackInfo(heroId, cardId, targetId, ranged, basic, primary, total)
    val result = g.defend(defender, info, defenseFromInitiative, noPrimaryDefense)
    g.trigger(Trigger.AttackDone(heroId, targetId, result.defeated))
    return result
}

/** One term of a Defense total, for the log ("+ 1 (adjacent melee minion)"). */
private data class Term(val value: Int, val why: String)

/**
 * p.14 "Minion Defense Modifiers": "+1 for every adjacent friendly melee or heavy minion. -1 for every
 * adjacent enemy melee or heavy minion. -1 for every enemy ranged minion in radius of 2 spaces around
 * you." Presence, not targeting: immune minions count (p.18).
 */
fun Game.minionModifier(at: Hex, team: Team, actorId: String?): Int = minionTerms(at, team, actorId).sumOf { it.value }

private fun Game.minionTerms(at: Hex, team: Team, actorId: String?): List<Term> {
    val terms = ArrayList<Term>()
    val minions = ArrayList<Triple<Team, MinionType, Hex>>()
    state.minions.forEach { m -> m.at?.let { minions += Triple(m.team, m.type, it) } }
    for (p in state.pieces) {
        val pAt = p.at ?: continue
        for ((b, s) in sources()) {
            b.pieceCountsAsMinion(this, s, p, actorId)?.let { (t, type) -> minions += Triple(t, type, pAt) }
        }
    }
    for ((t, type, pos) in minions) {
        val d = distance(at, pos)
        when {
            type != MinionType.RANGED && d == 1 && t == team ->
                terms +=
                    Term(1, "adjacent friendly ${Game.minionName(type)} minion")

            type != MinionType.RANGED && d == 1 && t != team ->
                terms +=
                    Term(-1, "adjacent enemy ${Game.minionName(type)} minion")

            type == MinionType.RANGED && t != team && d in 1..2 -> terms += Term(-1, "enemy ranged minion in radius 2")
        }
    }
    return terms
}

/** Cards in [heroId]'s hand that have a Defense action (p.14), plus extras an effect allows. */
fun Game.defenseCards(heroId: String): List<String> {
    val h = hero(heroId)
    val hand = h.cards.filter { it.zone == Zone.HAND }.map { it.id }.filter { id ->
        val c = card(id)
        c.primary == PrimaryAction.DEFENSE || c.primary == PrimaryAction.DEFENSE_SKILL ||
            SecondaryAction.DEFENSE in c.secondary
    }
    val extra = sources().flatMap { (b, s) -> b.extraDefenseCards(this, s, heroId) }
    return (hand + extra).distinct()
}

private fun isPrimaryDefense(card: dev.quinton.goa.core.model.catalog.Card): Boolean =
    card.primary == PrimaryAction.DEFENSE || card.primary == PrimaryAction.DEFENSE_SKILL

/** The defense total [cardId] would give (step 5), or null when its text decides only as it resolves. */
private fun Game.previewDefense(
    heroId: String,
    cardId: String,
    info: AttackInfo,
    fromInitiative: Boolean,
    noPrimary: Boolean,
    minion: List<Term>,
): Int? {
    val card = card(cardId)
    if (card.block) return null
    val copy = Game(data, state.copy(), emptyList(), RunMode.PROBE)
    return try {
        val scope = DefenseScope(copy, heroId, cardId, info)
        val textual =
            (card.primary == PrimaryAction.DEFENSE || card.primary == PrimaryAction.DEFENSE_SKILL) && !noPrimary
        if (textual) data.registry.script(cardId)?.defense(scope)
        copy.defenseTotal(heroId, cardId, scope, fromInitiative, minion, info.attackerId).first
    } catch (_: ControlSignal) {
        null
    }
}

/**
 * Step 5: the Defense total. [minion] are the minion defense modifiers calculated at step 2, before
 * the Defense text (p.14 order), so a Defense text that moves the defender doesn't change them.
 */
private fun Game.defenseTotal(
    heroId: String,
    cardId: String,
    scope: DefenseScope,
    fromInitiative: Boolean,
    minion: List<Term>,
    attackerId: String,
): Pair<Int, List<Term>> {
    val terms = ArrayList<Term>()
    val stat = if (fromInitiative) Stat.INITIATIVE else Stat.DEFENSE
    val printed = printedValue(heroId, cardId, stat) ?: 0
    terms += Term(printed, if (fromInitiative) "Initiative" else "Defense")
    val items = itemBonus(heroId, stat).takeIf { printedValue(heroId, cardId, stat) != null } ?: 0
    if (items != 0) terms += Term(items, "item${if (items == 1) "" else "s"}")
    val delta = statDelta(dev.quinton.goa.core.game.effects.StatQuery(heroId, stat, cardId, ActionKind.DEFENSE))
    if (delta != 0) terms += Term(delta, "effects")
    if (scope.bonus != 0) terms += Term(scope.bonus, "card text")
    if (!scope.ignoreMinionModifiers) terms += minion
    for ((b, s) in sources()) {
        val v = b.defenseBonus(this, s, heroId, attackerId)
        if (v != 0) terms += Term(v, "effects")
    }
    return terms.sumOf { it.value } to terms
}

/**
 * Steps 2–6 for a hero target (p.14). The defender "may reveal exactly one card from your hand and
 * discard it, to perform its Defense action"; card text applies when Defense is its Primary action
 * (or Defense/Skill); "If your Defense total is equal or higher than the Attack Total, you
 * successfully defend"; Block: "If the Block condition is fulfilled, you successfully defend,
 * regardless of the attack value. If not, you are defeated"; "You cannot defend without discarding".
 */
internal fun Game.defend(
    defender: String,
    info: AttackInfo,
    fromInitiative: Boolean,
    noPrimary: Boolean,
): AttackResult {
    val h = hero(defender)
    val at = posOf(info.targetId)!!
    // Step 2: "Calculate minion defense modifier", before the defender reveals a card.
    val minion = minionTerms(at, h.team, info.attackerId)
    val modifier = minion.sumOf { it.value }
    // "the target cannot perform a primary action to defend" (Tigerclaw's Ballista): cards whose Defense
    // action is their Primary action (Defense, Defense/Skill, p.14) cannot be used; secondary Defense can.
    val cards = defenseCards(defender).filter { !noPrimary || !isPrimaryDefense(card(it)) }
    if (cards.isEmpty()) {
        note("${heroName(defender)} has no card to defend with.")
        defeatHero(defender, info.attackerId)
        return AttackResult(true, info.targetId, defeated = true, total = info.total, hero = true)
    }
    val answer = askAlways(
        defender,
        DecisionKind.DEFEND,
        "${heroName(info.attackerId)} attacks you with Attack ${info.total}. Discard a card to defend?",
        info.cardId,
        context = {
            DecisionContext(
                attackerId = info.attackerId,
                attackTotal = info.total,
                rangedAttack = info.ranged,
                defensePreview = cards.associateWith {
                    previewDefense(defender, it, info, fromInitiative, noPrimary, minion)
                },
                minionModifier = modifier,
                subjectId = info.targetId,
            )
        },
    ) { cards.map { Choice.Card(it) as Choice } + Choice.Decline("Don't defend") }
    if (answer !is Choice.Card) {
        note("${heroName(defender)} does not defend.")
        defeatHero(defender, info.attackerId)
        return AttackResult(true, info.targetId, defeated = true, total = info.total, hero = true)
    }
    val cardId = answer.cardId
    val card = card(cardId)
    if (h.card(cardId)?.zone ==
        Zone.HAND
    ) {
        discard(defender, cardId, allowReplacement = false)
    } else {
        moveCard(defender, cardId, Zone.DISCARD)
    }
    val scope = DefenseScope(this, defender, cardId, info)
    val textual = (card.primary == PrimaryAction.DEFENSE || card.primary == PrimaryAction.DEFENSE_SKILL) && !noPrimary
    actors += defender
    try {
        // A Defense action is an action (p.14), with or without card text: "before you perform an action"
        // hooks see it (Silverarrow's Wild Hunt); primary = the card's Defense text applies.
        trigger(Trigger.BeforeAction(defender, cardId, ActionKind.DEFENSE, textual, isBasic(cardId)))
        if (textual) data.registry.script(cardId)?.defense(scope)
    } catch (f: Fizzle) {
        note("${heroName(defender)}'s ${card.name} text stops: ${f.message}.")
    } catch (a: ActorDefeated) {
        // The defender was defeated while performing its Defense action: it is removed, nothing to total.
        if (a.heroId != defender) throw a
        return AttackResult(true, info.targetId, defeated = true, defenseCard = cardId, total = info.total, hero = true)
    } finally {
        actors.removeAt(actors.lastIndex)
    }
    val (total, terms) = defenseTotal(defender, cardId, scope, fromInitiative, minion, info.attackerId)
    val success = if (card.block) scope.blocked == true else total >= info.total
    val how = if (card.block) {
        if (success) "Blocked" else "The Block fails"
    } else {
        val parts = terms.mapIndexed { i, t ->
            val v = if (i ==
                0
            ) {
                "${t.why} ${t.value}"
            } else {
                "${if (t.value >= 0) "+" else "-"} ${kotlin.math.abs(t.value)} (${t.why})"
            }
            v
        }
        "${parts.joinToString(" ")} = $total. ${if (success) "Defended" else "Not enough"}"
    }
    emit(
        GameEvent.Defended(
            defender,
            cardId,
            if (card.block) null else total,
            success,
            "${heroName(defender)} defends with ${card.name}: $how.",
        ),
    )
    if (!success) defeatHero(defender, info.attackerId)
    return AttackResult(
        true,
        info.targetId,
        defeated = !success,
        defenseCard = cardId,
        total = info.total,
        defenseTotal = total,
        hero = true,
    )
}
