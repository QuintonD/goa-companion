package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.UnavailableAction
import dev.quinton.goa.core.game.effects.ActionQuery
import dev.quinton.goa.core.game.effects.ChoiceQuery
import dev.quinton.goa.core.game.effects.MoveQuery
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.Trigger
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat

/**
 * The action being performed (p.9–14): what a card script works with. Choices default to the acting
 * hero ("You make all the choices in your card's text, unless the card text instructs the other hero
 * to make a choice", p.10). Everything else is on [g].
 */
class ActionScope internal constructor(
    val g: Game,
    val heroId: String,
    val cardId: String,
    /** The action's type: ATTACK, SKILL, MOVEMENT (a Defense/Skill card played is a SKILL, p.14). */
    val kind: ActionKind,
    val primary: Boolean,
    /** A repeated instance (p.10 "Repeat"): "You cannot repeat a repeated action!" */
    val isRepeat: Boolean = false,
    /** Units this instance may not target ("on a different target / unit / enemy hero"). */
    val excluded: Set<String> = emptySet(),
    /** A repeat "on the same target": the only unit or token this instance may affect. */
    val only: String? = null,
    /** A repeat restricted to some targets ("on a different enemy hero", "on a minion"). */
    val repeatFilter: ((String) -> Boolean)? = null,
) {
    val hero: HeroState get() = g.hero(heroId)
    val card: Card get() = g.card(cardId)

    /** Any action on a basic card is a basic action (p.9). */
    val basic: Boolean get() = g.isBasic(cardId)

    /** Units this action targeted, in order (p.19 "Target": any unit it has an effect on). */
    val targets: MutableList<String> = ArrayList()

    /** Per-action notes for multi-step texts ("if you did not move before the attack"). */
    val notes: MutableMap<String, Any?> = LinkedHashMap()

    /** The Movement of a Movement card was performed ([movementAction]). */
    var movementDone: Boolean = false
        internal set

    /** The hero's space; a script running while the hero is off the board fizzles. */
    val pos: Hex get() = hero.at ?: g.fizzle("${g.heroName(heroId)} is not on the board")

    /** The card's icon value (items and modifiers included, p.16), or null without the icon. */
    fun value(stat: Stat): Int? = g.cardValue(heroId, cardId, stat, kind)

    val range: Int get() = value(Stat.RANGE) ?: 0
    val radius: Int get() = value(Stat.RADIUS) ?: 0

    // ---- choices ----------------------------------------------------------------------------

    fun chooseUnit(
        prompt: String,
        ids: Collection<String>,
        optional: Boolean = false,
        chooser: String = heroId,
    ): String? {
        val opts = ids.distinct().map { Choice.Unit(it) as Choice } + declineIf(optional)
        if (opts.isEmpty()) return null
        return (g.ask(chooser, DecisionKind.CHOOSE_UNIT, prompt, opts, cardId) as? Choice.Unit)?.unitId
    }

    fun chooseToken(
        prompt: String,
        ids: Collection<String>,
        optional: Boolean = false,
        chooser: String = heroId,
    ): String? {
        val opts = ids.distinct().map { Choice.Token(it) as Choice } + declineIf(optional)
        if (opts.isEmpty()) return null
        return (g.ask(chooser, DecisionKind.CHOOSE_TOKEN, prompt, opts, cardId) as? Choice.Token)?.tokenId
    }

    /** A unit or a token ("Move a unit, or a token"): units answer [Choice.Unit], tokens [Choice.Token]. */
    fun chooseObject(
        prompt: String,
        ids: Collection<String>,
        optional: Boolean = false,
        chooser: String = heroId,
    ): String? {
        val opts = ids.distinct().map { if (g.isToken(it)) Choice.Token(it) else Choice.Unit(it) } + declineIf(optional)
        if (opts.isEmpty()) return null
        return when (val c = g.ask(chooser, DecisionKind.CHOOSE_UNIT, prompt, opts, cardId)) {
            is Choice.Unit -> c.unitId
            is Choice.Token -> c.tokenId
            else -> null
        }
    }

    fun chooseSpace(prompt: String, hexes: Collection<Hex>, optional: Boolean = false, chooser: String = heroId): Hex? {
        val opts = hexes.distinct().sortedHexes().map { Choice.Space(it) as Choice } + declineIf(optional)
        if (opts.isEmpty()) return null
        return (g.ask(chooser, DecisionKind.CHOOSE_SPACE, prompt, opts, cardId) as? Choice.Space)?.hex
    }

    fun chooseCard(
        prompt: String,
        cardIds: Collection<String>,
        optional: Boolean = false,
        chooser: String = heroId,
    ): String? {
        val opts = cardIds.distinct().map { Choice.Card(it) as Choice } + declineIf(optional)
        if (opts.isEmpty()) return null
        return (g.ask(chooser, DecisionKind.CHOOSE_CARD, prompt, opts, cardId) as? Choice.Card)?.cardId
    }

    /** "Choose one —" bullets (p.10): returns the index, or null when declined. */
    fun chooseOption(prompt: String, labels: List<String>, optional: Boolean = false, chooser: String = heroId): Int? {
        val opts = labels.mapIndexed { i, l -> Choice.Option(i, l) as Choice } + declineIf(optional)
        return (g.ask(chooser, DecisionKind.CHOOSE_OPTION, prompt, opts, cardId) as? Choice.Option)?.index
    }

    /** An optional step ("you may", p.10): asks Yes / Skip. */
    fun confirm(prompt: String, chooser: String = heroId, decline: String = "Skip"): Boolean =
        g.ask(
            chooser,
            DecisionKind.CONFIRM,
            prompt,
            listOf(Choice.Yes, Choice.Decline(decline)),
            cardId,
            auto = false,
        ) is Choice.Yes

    fun chooseNumber(prompt: String, values: List<Int>, chooser: String = heroId): Int {
        val c = g.ask(
            chooser,
            DecisionKind.CHOOSE_NUMBER,
            prompt,
            values.map {
                Choice.Number(it)
            },
            cardId,
        ) as Choice.Number
        return c.value
    }

    private fun declineIf(optional: Boolean): List<Choice> = if (optional) listOf(Choice.Decline()) else emptyList()

    /** "Choose one" limit with Ultimates that widen it (Misa, Whisper, Mortimer). */
    fun choiceLimit(printed: Int, upTo: Boolean = false): Int {
        val q = ChoiceQuery(heroId, cardId, printed, upTo, basic)
        return maxOf(printed, g.sources().mapNotNull { (b, s) -> b.choiceLimit(g, s, q) }.maxOrNull() ?: printed)
    }

    // ---- targets ----------------------------------------------------------------------------

    /**
     * May this action target [id] (p.10 "Picking Targets", p.18 "Immune")? Excluded units of a
     * repeat are never valid. [asKind] overrides the action type checked.
     */
    fun canTarget(
        id: String,
        asKind: ActionKind = kind,
        ignoreHeavyImmunity: Boolean = false,
        ignoreImmunity: Boolean = false,
    ): Boolean =
        id !in excluded && (only == null || id == only) && repeatFilter?.invoke(id) != false &&
            g.canTarget(id, heroId, asKind, cardId, primary, card.ranged, ignoreHeavyImmunity, ignoreImmunity)

    /** Record that this action targeted [id] (repeats on "a different target" exclude it). */
    fun target(id: String): String {
        if (id !in targets) targets += id
        return id
    }

    fun fizzle(reason: String, source: String? = null): Nothing = g.fizzle(reason, source)

    // ---- the card's own movement --------------------------------------------------------------

    /**
     * The Movement of a Movement card's Primary action ("moving up to the number of spaces on the
     * icon", p.11), a Movement action. Call it from the script when the text says "after movement" /
     * "before or after movement"; otherwise the engine calls it after the text.
     */
    fun movementAction(
        bonus: Int = 0,
        ignoreObstacles: Boolean = false,
        straightLine: Boolean = false,
        stepwise: Boolean = false,
    ): List<Hex> {
        if (movementDone) return emptyList()
        movementDone = true
        val n = (value(Stat.MOVEMENT) ?: 0) + bonus
        return g.movementMoves(heroId, n, cardId, ignoreObstacles, straightLine, stepwise)
    }

    /**
     * p.10 "Repeat": "After you perform this action, you may perform the entire action again (with
     * the same range, restrictions etc.), up to the number of times listed". Asks before each.
     * [differentTarget] excludes every unit targeted so far. Does nothing in a repeated instance.
     */
    @Suppress("LongParameterList")
    fun repeat(
        times: Int = 1,
        differentTarget: Boolean = false,
        sameTarget: Boolean = false,
        filter: ((String) -> Boolean)? = null,
        prompt: String = "Repeat ${card.name}?",
        mandatory: Boolean = false,
    ) {
        if (isRepeat) return
        if (g.repeatForbidden(heroId)) return
        val seen = LinkedHashSet<String>(targets)
        val first = targets.firstOrNull()
        for (i in 1..times) {
            if (!mandatory && !confirm(prompt, decline = "Don't repeat")) return
            val inst = g.performAction(
                heroId,
                cardId,
                kind,
                primary = true,
                isRepeat = true,
                excluded = if (differentTarget) seen + excluded else excluded,
                only = if (sameTarget) first else null,
                repeatFilter = filter,
            ) ?: return
            seen += inst.targets
            targets.addAll(inst.targets.filter { it !in targets })
        }
    }
}

/**
 * A Defense action (p.14): the defender discarded [cardId] in response to [attack]. Card text adds
 * Defense, ignores minion modifiers, sets a Block result or does other things (step 4).
 */
class DefenseScope internal constructor(
    val g: Game,
    val heroId: String,
    val cardId: String,
    val attack: AttackInfo,
) {
    val hero: HeroState get() = g.hero(heroId)
    val card: Card get() = g.card(cardId)
    val pos: Hex? get() = hero.at

    /** "+N Defense" from the card text (p.14 "Bonus defense from the card text"). */
    var bonus: Int = 0

    /** "Ignore all minion defense modifiers" (Arien's Duelists). */
    var ignoreMinionModifiers: Boolean = false

    /** Block (p.14): true when the Block condition is fulfilled; null = none stated. */
    var blocked: Boolean? = null

    fun block(success: Boolean) {
        blocked = success
    }

    val radius: Int get() = g.cardValue(heroId, cardId, Stat.RADIUS, ActionKind.DEFENSE) ?: 0
    val range: Int get() = g.cardValue(heroId, cardId, Stat.RANGE, ActionKind.DEFENSE) ?: 0

    fun canTarget(id: String): Boolean = g.canTarget(id, heroId, ActionKind.DEFENSE, cardId, primary = true)

    fun chooseUnit(prompt: String, ids: Collection<String>, optional: Boolean = false): String? {
        val opts =
            ids.distinct().map { Choice.Unit(it) as Choice } + if (optional) listOf(Choice.Decline()) else emptyList()
        if (opts.isEmpty()) return null
        return (g.ask(heroId, DecisionKind.CHOOSE_UNIT, prompt, opts, cardId) as? Choice.Unit)?.unitId
    }

    fun chooseSpace(prompt: String, hexes: Collection<Hex>, optional: Boolean = false): Hex? {
        val opts =
            hexes.distinct().sortedHexes().map { Choice.Space(it) as Choice } +
                if (optional) listOf(Choice.Decline()) else emptyList()
        if (opts.isEmpty()) return null
        return (g.ask(heroId, DecisionKind.CHOOSE_SPACE, prompt, opts, cardId) as? Choice.Space)?.hex
    }

    fun confirm(prompt: String): Boolean =
        g.ask(
            heroId,
            DecisionKind.CONFIRM,
            prompt,
            listOf(Choice.Yes, Choice.Decline()),
            cardId,
            auto = false,
        ) is Choice.Yes
}

// ---- performing actions ----------------------------------------------------------------------

/** The action type of a card's Primary action (p.9; Defense/Skill played = Skill, p.14). */
fun primaryKind(card: Card): ActionKind = when (card.primary) {
    PrimaryAction.ATTACK -> ActionKind.ATTACK
    PrimaryAction.SKILL, PrimaryAction.DEFENSE_SKILL -> ActionKind.SKILL
    PrimaryAction.MOVEMENT -> ActionKind.MOVEMENT
    PrimaryAction.DEFENSE -> ActionKind.DEFENSE
    null -> ActionKind.NONE
}

fun Game.actionAllowed(heroId: String, cardId: String?, kind: ActionKind, primary: Boolean): Boolean =
    actionForbiddenBy(heroId, cardId, kind, primary) == null

/** The first effect in force that forbids this action ("cannot perform skill actions"), or null. */
fun Game.actionForbiddenBy(heroId: String, cardId: String?, kind: ActionKind, primary: Boolean): Src? {
    val q =
        ActionQuery(
            heroId,
            cardId,
            kind,
            primary,
            cardId?.let {
                isBasic(it)
            } ?: false,
            cardId?.let { cardColorOf(heroId, it) },
        )
    return sources().firstOrNull { (b, s) -> b.forbidsAction(this, s, q) }?.second
}

fun Game.repeatForbidden(heroId: String): Boolean = sources().any { (b, s) -> b.forbidsRepeat(this, s, heroId) }

/** The secondary and replacing actions a card offers (p.7, p.9, p.11, p.13). */
fun Game.actionOptions(heroId: String, cardId: String): List<ActionType> =
    actionAvailability(heroId, cardId, explain = false).options

/**
 * What a played card offers when its hero chooses an action: [options], and the actions it prints
 * that are not among them, each with the reason ([unavailable], ADR-041). Both come from the same
 * checks, so a reason can't disagree with the options.
 */
class ActionAvailability(val options: List<ActionType>, val unavailable: List<UnavailableAction>)

/** See [ActionAvailability]; [explain] false skips the reasons (the AI's and other cards' queries). */
@Suppress("CyclomaticComplexMethod")
fun Game.actionAvailability(heroId: String, cardId: String, explain: Boolean = true): ActionAvailability {
    val card = card(cardId)
    val out = ArrayList<ActionType>()
    val missing = ArrayList<UnavailableAction>()
    val pk = primaryKind(card)
    val script = data.registry.script(cardId)
    if (pk == ActionKind.DEFENSE) {
        if (explain) missing += UnavailableAction(ActionType.PRIMARY, DEFENSE_PLAYED, "p.14")
    } else if (pk != ActionKind.NONE && script != null && script.hasPrimary) {
        val by = actionForbiddenBy(heroId, cardId, pk, true)
        val blocked = if (by == null) primaryBlocker(heroId, cardId) else null
        when {
            by != null -> if (explain) missing += forbidden(ActionType.PRIMARY, pk, by)

            blocked == null -> out += ActionType.PRIMARY

            explain -> missing += UnavailableAction(
                ActionType.PRIMARY,
                "Its ${kindName(pk)} can't start: ${blocked.reason}",
                blocked.source ?: FIRST_INSTRUCTION,
            )
        }
    }
    val hasMovement = card.primary == PrimaryAction.MOVEMENT || SecondaryAction.MOVEMENT in card.secondary
    if (SecondaryAction.MOVEMENT in card.secondary) {
        val by = actionForbiddenBy(heroId, cardId, ActionKind.MOVEMENT, false)
        if (by == null) {
            out += ActionType.MOVEMENT
        } else if (explain) {
            missing += forbidden(ActionType.MOVEMENT, ActionKind.MOVEMENT, by)
        }
    }
    val movementAllowed = actionAllowed(heroId, cardId, ActionKind.MOVEMENT, card.primary == PrimaryAction.MOVEMENT)
    if (hasMovement && movementAllowed && canFastTravel(heroId, cardId)) {
        out += ActionType.FAST_TRAVEL
    }
    if (SecondaryAction.ATTACK in card.secondary) {
        val by = actionForbiddenBy(heroId, cardId, ActionKind.ATTACK, false)
        when {
            by != null -> if (explain) missing += forbidden(ActionType.ATTACK, ActionKind.ATTACK, by)
            secondaryAttackTargets(heroId, cardId).isNotEmpty() -> out += ActionType.ATTACK
            explain -> missing += UnavailableAction(ActionType.ATTACK, NO_ADJACENT_TARGET, "p.10")
        }
    }
    val hasAttack = card.primary == PrimaryAction.ATTACK || SecondaryAction.ATTACK in card.secondary
    val pos = hero(heroId).at
    if (hasAttack && pos != null && clearableTokens(heroId, cardId).isNotEmpty() &&
        actionAllowed(heroId, cardId, ActionKind.CLEAR, false)
    ) {
        out += ActionType.CLEAR
    }
    out += ActionType.HOLD
    return ActionAvailability(out, missing)
}

/** p.10 "Mandatory and Optional Steps" and the FAQ's "you may not perform the action at all". */
private const val FIRST_INSTRUCTION = "p.10, FAQ"

/** p.14: "You cannot perform a Defense action on an already played card!" */
private const val DEFENSE_PLAYED =
    "Its Defense is never performed from a played card, only from your hand when attacked"

/** p.10 "Picking Targets": "You must choose valid targets for your actions." */
private const val NO_ADJACENT_TARGET = "Its Attack has no target: no enemy unit you may target is adjacent"

private fun kindName(kind: ActionKind): String = when (kind) {
    ActionKind.ATTACK -> "Attack"
    ActionKind.MOVEMENT -> "Movement"
    ActionKind.DEFENSE -> "Defense"
    else -> "Skill"
}

/**
 * "Its Skill is forbidden by Arien's Spell Break": the effect in force that forbids the action.
 * Effects in force come from faceup cards, Ultimates, markers and pieces on the board (a card turned
 * facedown loses its effect, p.10), so naming the card reveals nothing hidden.
 */
private fun Game.forbidden(action: ActionType, kind: ActionKind, by: Src): UnavailableAction {
    val owner = by.owner?.let { heroName(it) }
    val card = by.cardId
    val what = when {
        card != null && owner != null -> "$owner's ${cardName(card)}"
        card != null -> cardName(card)
        owner != null -> "an effect of $owner"
        else -> "an effect in force"
    }
    return UnavailableAction(action, "Its ${kindName(kind)} is forbidden by $what", null, card)
}

/**
 * FAQ (source 3): "If you cannot perform the first instruction … you may not perform the action at
 * all." Probed on a copy: possible when the text reaches a real choice or changes the position
 * before stopping; impossible when it stops (fizzles) before doing anything. [excluded] and
 * [repeatFilter] restrict the targets as for [performAction] (a primary performed by another card's
 * text with limits, e.g. Swift's Backup "you cannot target minions").
 */
fun Game.primaryPossible(
    heroId: String,
    cardId: String,
    excluded: Set<String> = emptySet(),
    repeatFilter: ((String) -> Boolean)? = null,
): Boolean = primaryBlocker(heroId, cardId, excluded, repeatFilter) == null

/** Why a Primary action cannot start: the step it stopped at ([Fizzle]'s reason and source). */
class PrimaryBlock(val reason: String, val source: String?)

/** [primaryPossible]'s probe: null when the Primary action can start, else why it can't. */
fun Game.primaryBlocker(
    heroId: String,
    cardId: String,
    excluded: Set<String> = emptySet(),
    repeatFilter: ((String) -> Boolean)? = null,
): PrimaryBlock? {
    val copy = state.copy()
    val probe = Game(data, copy, emptyList(), RunMode.PROBE)
    val before = copy.stamp
    return try {
        probe.performAction(
            heroId,
            cardId,
            primaryKind(card(cardId)),
            primary = true,
            excluded = excluded,
            probing = true,
            repeatFilter = repeatFilter,
        )
        null
    } catch (_: ProbeHit) {
        null
    } catch (f: ProbeFizzled) {
        if (copy.stamp != before) null else PrimaryBlock(f.message.orEmpty(), f.source)
    } catch (_: GameEnded) {
        null
    }
}

private class ProbeFizzled(fizzle: Fizzle) : ControlSignal(fizzle.message) {
    val source: String? = fizzle.source
}

/**
 * Perform one action of [heroId] with [cardId] (p.7 "Perform One Action"). Card text runs for the
 * Primary action only. Returns the scope (targets etc.), or null when the hero was defeated during it.
 * After the action, minions outside their Battle Zone go back (p.18).
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun Game.performAction(
    heroId: String,
    cardId: String,
    kind: ActionKind,
    primary: Boolean,
    isRepeat: Boolean = false,
    excluded: Set<String> = emptySet(),
    probing: Boolean = false,
    only: String? = null,
    repeatFilter: ((String) -> Boolean)? = null,
): ActionScope? {
    val scope = ActionScope(this, heroId, cardId, kind, primary, isRepeat, excluded, only, repeatFilter)
    val basic = isBasic(cardId)
    actors += heroId
    actions += scope
    var defeated = false
    try {
        trigger(Trigger.BeforeAction(heroId, cardId, kind, primary, basic))
        if (primary) {
            val script = data.registry.script(cardId) ?: fizzle("No script for $cardId")
            script.primary(scope)
            if (card(cardId).primary == PrimaryAction.MOVEMENT && !scope.movementDone) scope.movementAction()
        } else {
            when (kind) {
                ActionKind.MOVEMENT -> {
                    scope.movementDone = true
                    movementMoves(heroId, cardValue(heroId, cardId, Stat.MOVEMENT, ActionKind.MOVEMENT) ?: 0, cardId)
                }

                ActionKind.FAST_TRAVEL -> fastTravel(heroId, cardId)

                ActionKind.CLEAR -> clear(heroId, cardId)

                ActionKind.ATTACK -> secondaryAttack(scope)

                ActionKind.HOLD -> note("${heroName(heroId)} holds.")

                else -> Unit
            }
        }
    } catch (f: Fizzle) {
        if (probing) throw ProbeFizzled(f)
        note("${heroName(heroId)}'s ${cardName(cardId)} stops: ${f.message}.")
    } catch (a: ActorDefeated) {
        if (a.heroId != heroId) throw a
        defeated = true
    } finally {
        actors.removeAt(actors.lastIndex)
        actions.removeAt(actions.lastIndex)
    }
    if (defeated) {
        // The action ended with its hero's defeat: displaced minions still go back (p.18 "after you
        // perform an action").
        returnMinions()
        return null
    }
    trigger(Trigger.AfterAction(heroId, cardId, kind, primary, basic, isRepeat, scope.targets.toList()))
    returnMinions()
    return scope
}

/** Perform the Primary action of another card ("Perform the primary action of your rightmost resolved card"). */
fun Game.performPrimary(heroId: String, cardId: String, excluded: Set<String> = emptySet()): ActionScope? {
    val kind = primaryKind(card(cardId))
    if (kind == ActionKind.DEFENSE || kind == ActionKind.NONE) return null
    if (!actionAllowed(heroId, cardId, kind, true)) return null
    return performAction(heroId, cardId, kind, primary = true, excluded = excluded)
}

/**
 * A Movement action of up to [value] spaces (p.11): limits are "only checked at the start of the
 * movement action"; through empty spaces only (or ignoring obstacles when an effect allows).
 */
@Suppress("LongParameterList")
fun Game.movementMoves(
    heroId: String,
    value: Int,
    cardId: String?,
    ignoreObstacles: Boolean = false,
    straightLine: Boolean = false,
    stepwise: Boolean = false,
): List<Hex> {
    if (!actionAllowed(heroId, cardId, ActionKind.MOVEMENT, false)) {
        note("${heroName(heroId)} cannot perform a movement action.")
        return emptyList()
    }
    val cap = sources().mapNotNull { (b, s) -> b.movementCap(this, s, MoveQuery(heroId, true, heroId)) }.minOrNull()
    val n = if (cap != null) minOf(cap, value) else value
    if (n <= 0) return emptyList()
    return move(
        MoveSpec(
            heroId,
            0,
            n,
            straightLine,
            movementAction = true,
            ignoreObstacles = ignoreObstacles,
            stepwise = stepwise,
        ),
        heroId,
        "Move up to $n space${if (n == 1) "" else "s"}",
        optional = true,
        sourceCardId = cardId,
    )
}

/**
 * Whether [heroId] may Fast Travel now: allowed by the effects in force and a destination exists.
 * Fast Travel "is a placement, not movement" (goa2-faq), so effects that stop a hero being placed
 * by itself stop it too (Wasp's Magnetic Dagger).
 */
fun Game.canFastTravel(heroId: String, cardId: String?): Boolean =
    actionAllowed(heroId, cardId, ActionKind.FAST_TRAVEL, false) &&
        sources().none { (b, s) -> b.forbidsFastTravel(this, s, heroId) } &&
        canDisplace(heroId, dev.quinton.goa.core.game.effects.Displacement.PLACE, heroId) &&
        fastTravelDestinations(heroId).isNotEmpty()

/** p.11 Fast Travel: a placement into the same or an adjacent Zone with no enemies in either. */
fun Game.fastTravel(heroId: String, cardId: String?, optional: Boolean = false) {
    if (!canFastTravel(heroId, cardId)) return
    val dests = fastTravelDestinations(heroId)
    if (dests.isEmpty()) return
    val opts =
        dests.map { Choice.Space(it) as Choice } +
            if (optional) listOf(Choice.Decline("Don't fast travel")) else emptyList()
    val answer =
        ask(
            heroId,
            DecisionKind.CHOOSE_SPACE,
            "Fast travel to a space",
            opts,
            cardId,
            DecisionContext(subjectId = heroId),
        )
    if (answer is Choice.Space) {
        val from = hero(heroId).at
        setPos(heroId, answer.hex)
        val text = "${heroName(heroId)} fast travels to ${spaceName(answer.hex)}."
        emit(GameEvent.Placed(heroId, from, answer.hex, text))
        trigger(Trigger.Placed(heroId, from, answer.hex, heroId))
    }
}

/**
 * Tokens adjacent to [heroId] that a Clear action may remove: p.18 "Immune": an immune token "is not
 * affected by your actions" (Tali's Venerated Totem: "Totem tokens are immune to enemy actions").
 */
fun Game.clearableTokens(heroId: String, cardId: String?): List<String> {
    val pos = hero(heroId).at ?: return emptyList()
    return tokensAdjacentTo(pos).filter { canTarget(it, heroId, ActionKind.CLEAR, cardId, primary = false) }
}

/** p.13 Clear: "you may remove any number of tokens adjacent to you". */
fun Game.clear(heroId: String, cardId: String?) {
    while (true) {
        val tokens = clearableTokens(heroId, cardId)
        if (tokens.isEmpty()) return
        val opts = tokens.map { Choice.Token(it) as Choice } + Choice.Decline("Stop clearing")
        val c = ask(heroId, DecisionKind.CHOOSE_TOKEN, "Remove an adjacent token", opts, cardId, auto = false)
        if (c !is Choice.Token) return
        removePiece(c.tokenId)
    }
}

/** Targets of a secondary Attack action: "non-ranged" (Mrak's reminder), so adjacent enemy units. */
fun Game.secondaryAttackTargets(heroId: String, cardId: String): List<String> {
    val pos = hero(heroId).at ?: return emptyList()
    return unitsAdjacentTo(pos).filter {
        isEnemy(heroId, it) &&
            canTarget(it, heroId, ActionKind.ATTACK, cardId, primary = false)
    }
}

private fun Game.secondaryAttack(scope: ActionScope) {
    val t = scope.attackTarget("Attack a unit adjacent to you") { id ->
        scope.hero.at?.let { posOf(id)?.let { p -> distance(it, p) == 1 } } == true
    } ?: return
    scope.attack(t, ranged = false)
}
