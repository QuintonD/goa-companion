package dev.quinton.goa.feature.board.play.table

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.game.FACEDOWN
import dev.quinton.goa.feature.board.play.game.important
import dev.quinton.goa.feature.board.play.game.merged
import dev.quinton.goa.feature.board.play.game.panelWorthy

/*
 * The resolution flow (ADR-046): what happened since the person last chose, one entry per hero's card
 * resolution (and one for the turn's start, one for the end of the round), each a line that opens to
 * its steps. Built only from the engine's events, which are public (p.4): a card appears only where an
 * event names it (`ActionChosen` the revealed card, `Defended` the card revealed and discarded to
 * defend, `CardsChanged.cards` the open cards a change names), never from a hand or a facedown card.
 */

/** What an entry stands for. */
enum class FlowKind {
    /** One hero resolving one card (p.7 "Resolve Your Card"), from its respawn or action to the next. */
    HERO,

    /** The start of a turn: what happened before the first hero acted (effects at the reveal). */
    TURN,

    /** The end of a round (p.8): Minion Battle, push, retrieve, level up, Pity coin. */
    END_OF_ROUND,
}

/** What a step is, for its icon and emphasis. */
enum class StepKind { ACTION, MOVE, PLACE, ATTACK, DEFENSE, CARDS, COINS, DEFEAT, LANE, LEVEL, NOTE }

/**
 * One step of an entry, in the engine's words ([text]). [cards] are the cards it shows as cards (a
 * defense card, the open card a discard names); [success] a defense's result; [important] a line a
 * player must not miss (a hero defeated, a Life counter, a Lane push, a level up, the Minion Battle).
 */
@Immutable
data class FlowStep(
    val text: String,
    val kind: StepKind,
    val cards: List<String> = emptyList(),
    val success: Boolean? = null,
    val important: Boolean = false,
    /** For a defense: the hero that defended (the card's owner). */
    val heroId: String? = null,
    /** For a defense: the Attack total it answered (p.14). */
    val attack: Int? = null,
)

/**
 * One entry. [key] is stable while the entry grows (the log index of its first event). [heroId] and
 * [cardId] the hero and card resolving (HERO; [cardId] null for a hero that stayed off the board);
 * [title] "Brogan" / "Turn 2" / "End of round 1"; [line] the outcome in a few words ("Onslaught →
 * Wasp defended with Lift Up (5 vs 4)"). [open] = shown expanded at first: an enemy defended
 * against, or was defeated by, one of the person's heroes. [mine]: one of the person's heroes.
 */
@Immutable
data class FlowEntry(
    val key: String,
    val kind: FlowKind,
    val heroId: String?,
    val cardId: String?,
    val title: String,
    val line: String,
    val steps: List<FlowStep>,
    val important: Boolean = false,
    val open: Boolean = false,
    val mine: Boolean = false,
    val team: Team? = null,
) {
    /** What TalkBack reads for the collapsed entry: one sentence. */
    val spoken: String get() = "$title: ${line.replace(" → ", ", ").replace(" vs ", " against ")}."
}

/**
 * What happened since the person last chose, oldest first. [fresh] false = nothing new since then, and
 * [entries] is the last thing that happened before (so the panel isn't bare). [earlier] entries since
 * then were left out (the log has them).
 */
@Immutable
data class ResolutionFlow(val entries: List<FlowEntry> = emptyList(), val fresh: Boolean = true, val earlier: Int = 0) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Builds the flow from the whole log [events]: the entries with something new at or after [from] (the
 * log's size before the person's last answer). [teams] gives each hero's team (its keys are the
 * heroes), [mine] the person's heroes, [name] a hero's name, [cardName] a card's name.
 */
@Suppress("LongParameterList")
fun resolutionFlow(
    events: List<GameEvent>,
    from: Int,
    teams: Map<String, Team>,
    mine: Set<String>,
    name: (String) -> String,
    cardName: (String) -> String?,
    max: Int = MAX_FLOW_ENTRIES,
): ResolutionFlow {
    val since = from.coerceIn(0, events.size)
    val start = flowStart(events, since)
    val segments = segment(events, start, teams.keys, name)
    val builder = EntryBuilder(teams, mine, name, cardName)
    val fresh = segments.filter { builder.isNew(it, since) }
    if (fresh.isEmpty()) {
        val last = segments.lastOrNull { builder.isNew(it, it.from) } ?: return ResolutionFlow(fresh = false)
        return ResolutionFlow(listOf(builder.entry(last).copy(open = false)), fresh = false)
    }
    val kept = fresh.takeLast(max)
    return ResolutionFlow(kept.map(builder::entry), fresh = true, earlier = fresh.size - kept.size)
}

/**
 * Where to start reading: the start of the turn before the one the event before [since] belongs to, so
 * the entry the person's last answer was part of is whole, and there is something to fall back on when
 * nothing happened since (a new turn just started).
 */
private fun flowStart(events: List<GameEvent>, since: Int): Int {
    var turns = 0
    for (i in (since - 1).coerceAtLeast(0) downTo 0) {
        if (events.getOrNull(i) is GameEvent.TurnStarted && ++turns == 2) return roundHeading(events, i)
    }
    return 0
}

/** A turn heading's index, or the round heading right before it. */
private fun roundHeading(events: List<GameEvent>, turn: Int): Int =
    if (turn > 0 && events[turn - 1] is GameEvent.RoundStarted) turn - 1 else turn

/** A run of events: [from] .. [to] (exclusive) in the log, of one [kind] (and [heroId] for HERO). */
private class Segment(
    val kind: FlowKind,
    val from: Int,
    val heroId: String?,
    var cardId: String?,
    val events: MutableList<IndexedValue<GameEvent>> = ArrayList(),
    var acted: Boolean = false,
)

/** Splits the log from [start] into segments: turn starts, each hero's card resolution, the end of a round. */
private fun segment(events: List<GameEvent>, start: Int, heroes: Set<String>, name: (String) -> String): List<Segment> {
    val out = ArrayList<Segment>()
    var current: Segment? = null
    var revealed: Map<String, String?> = emptyMap()
    fun open(kind: FlowKind, at: Int, heroId: String? = null, cardId: String? = null): Segment =
        Segment(kind, at, heroId, cardId).also {
            out += it
            current = it
        }
    for (i in start until events.size) {
        val e = events[i]
        val cur = current
        val seg = when {
            e is GameEvent.RoundStarted -> open(FlowKind.TURN, i)

            e is GameEvent.TurnStarted ->
                if (cur != null && cur.kind == FlowKind.TURN && cur.events.all { it.value is GameEvent.RoundStarted }) {
                    cur
                } else {
                    open(FlowKind.TURN, i)
                }

            e is GameEvent.Note && e.text.startsWith(END_OF_ROUND) -> open(FlowKind.END_OF_ROUND, i)

            e is GameEvent.ActionChosen ->
                if (cur != null && cur.kind == FlowKind.HERO && cur.heroId == e.heroId && !cur.acted) {
                    cur.also { it.cardId = e.cardId }
                } else {
                    open(FlowKind.HERO, i, e.heroId, e.cardId)
                }.also { it.acted = true }

            // A hero respawning starts its resolution (p.7 "Hero Respawn"), before it chooses an action.
            e is GameEvent.Placed && e.from == null && e.pieceId in heroes && cur?.kind != FlowKind.END_OF_ROUND ->
                open(FlowKind.HERO, i, e.pieceId, revealed[e.pieceId])

            e is GameEvent.Note && cur?.kind != FlowKind.END_OF_ROUND ->
                heroes.firstOrNull { e.text.startsWith("${name(it)} $OFF_BOARD") }
                    ?.let { open(FlowKind.HERO, i, it, revealed[it]) } ?: cur ?: open(FlowKind.TURN, i)

            else -> cur ?: open(FlowKind.TURN, i)
        }
        if (e is GameEvent.CardsRevealed) revealed = e.played
        seg.events += IndexedValue(i, e)
    }
    return out
}

/** Turns segments into entries: the steps, the outcome line, what stands out. */
private class EntryBuilder(
    private val teams: Map<String, Team>,
    private val mine: Set<String>,
    private val name: (String) -> String,
    private val cardName: (String) -> String?,
) {
    /** Whether [seg] has something the person hasn't seen at or after [since]. */
    fun isNew(seg: Segment, since: Int): Boolean = seg.events.any { (i, e) ->
        i >= since && when (seg.kind) {
            // Another hero's card: even a move is news ("moved 3"); the person's own needs more than their choices.
            FlowKind.HERO -> if (seg.heroId in mine) panelWorthy(e) else e !is GameEvent.Note || !e.text.endsWith(FACEDOWN)
            FlowKind.TURN -> panelWorthy(e)
            FlowKind.END_OF_ROUND -> true
        }
    }

    fun entry(seg: Segment): FlowEntry {
        val events = seg.events.map { it.value }
        val steps = steps(events)
        val important = steps.any { it.important }
        return when (seg.kind) {
            FlowKind.HERO -> {
                val hero = seg.heroId!!
                FlowEntry(
                    key = "flow-${seg.from}",
                    kind = FlowKind.HERO,
                    heroId = hero,
                    cardId = seg.cardId,
                    title = name(hero),
                    line = listOfNotNull(seg.cardId?.let(cardName), heroOutcome(hero, events)).joinToString(" → "),
                    steps = steps,
                    important = important,
                    open = defendedAgainstMine(events),
                    mine = hero in mine,
                    team = teams[hero],
                )
            }

            FlowKind.TURN -> {
                val turn = events.firstNotNullOfOrNull { it as? GameEvent.TurnStarted }
                FlowEntry(
                    key = "flow-${seg.from}",
                    kind = FlowKind.TURN,
                    heroId = null,
                    cardId = null,
                    title = turn?.let { "Turn ${it.turn}" } ?: "Before the first card",
                    line = outline(steps),
                    steps = steps,
                    important = important,
                )
            }

            FlowKind.END_OF_ROUND -> {
                val round = events.firstNotNullOfOrNull { (it as? GameEvent.Note)?.text }
                    ?.removePrefix(END_OF_ROUND)?.trim()?.removeSuffix(".")
                FlowEntry(
                    key = "flow-${seg.from}",
                    kind = FlowKind.END_OF_ROUND,
                    heroId = null,
                    cardId = null,
                    title = "End of round${round?.let { " $it" }.orEmpty()}",
                    line = roundOutcome(events, steps),
                    steps = steps,
                    important = important,
                )
            }
        }
    }

    // ---- steps ----------------------------------------------------------------------------

    private fun steps(events: List<GameEvent>): List<FlowStep> {
        // The card discarded to defend is the defense step's card: its "discards" line is folded in.
        val folded = defenseDiscards(events)
        var attack: Int? = null
        return merged(events.filterIndexed { i, _ -> i !in folded }).mapNotNull { e ->
            if (e is GameEvent.Attacked) attack = e.total
            step(e)?.let { if (it.kind == StepKind.DEFENSE) it.copy(attack = attack) else it }
        }
    }

    /** Indexes of the "X discards Y." lines that are a defense's own discard (p.14 "discard it"). */
    private fun defenseDiscards(events: List<GameEvent>): Set<Int> = buildSet {
        events.forEachIndexed { i, e ->
            if (e !is GameEvent.Defended || e.cardId == null) return@forEachIndexed
            val attack = (i - 1 downTo 0).firstOrNull { events[it] is GameEvent.Attacked } ?: -1
            ((i - 1) downTo (attack + 1)).firstOrNull { j ->
                val c = events[j] as? GameEvent.CardsChanged
                c != null && c.heroId == e.heroId && c.discarded == listOf(e.cardId)
            }?.let(::add)
        }
    }

    private fun step(e: GameEvent): FlowStep? = when (e) {
        is GameEvent.RoundStarted, is GameEvent.TurnStarted, is GameEvent.CardsRevealed -> null
        is GameEvent.Note -> if (e.text.endsWith(FACEDOWN)) null else FlowStep(e.text, StepKind.NOTE, important = important(e))
        is GameEvent.ActionChosen -> FlowStep(e.text, StepKind.ACTION)
        is GameEvent.Moved -> FlowStep(e.text, StepKind.MOVE)
        is GameEvent.Placed, is GameEvent.Swapped, is GameEvent.Pushed, is GameEvent.Removed ->
            FlowStep(e.text, StepKind.PLACE)
        is GameEvent.Attacked -> FlowStep(e.text, StepKind.ATTACK)
        is GameEvent.Defended ->
            FlowStep(e.text, StepKind.DEFENSE, listOfNotNull(e.cardId), success = e.success, heroId = e.heroId)
        is GameEvent.Defeated -> FlowStep(e.text, StepKind.DEFEAT, important = e.unitId in teams)
        is GameEvent.CardsChanged -> FlowStep(e.text, StepKind.CARDS, e.cards, heroId = e.heroId)
        is GameEvent.CoinsGained -> FlowStep(e.text, StepKind.COINS)
        is GameEvent.LanePushed, is GameEvent.LifeSpent, is GameEvent.GameOver ->
            FlowStep(e.text, StepKind.LANE, important = true)
        is GameEvent.LevelledUp -> FlowStep(e.text, StepKind.LEVEL, important = true)
    }

    private fun important(e: GameEvent): Boolean = important(e, teams.keys)

    // ---- the outcome in a few words -----------------------------------------------------------

    /** "Wasp defended with Lift Up (5 vs 4)", "Wasp defeated", "moved 3", "+1 coin"… most telling first. */
    private fun heroOutcome(hero: String, events: List<GameEvent>): String {
        val attacks = ArrayList<String>()
        val explained = HashSet<String>()
        events.forEachIndexed { i, e ->
            if (e is GameEvent.Attacked) {
                val (text, unit) = attackOutcome(e, events.subList(i + 1, events.size))
                attacks += text
                explained += unit
            }
        }
        val defeats = events.filterIsInstance<GameEvent.Defeated>()
            .filter { it.unitId !in explained && it.unitId in teams }
            .map { "${unitName(it.unitId, it.text)} defeated" }
        val discards = events.filterIsInstance<GameEvent.CardsChanged>().mapNotNull { e ->
            when {
                e.discarded.isNotEmpty() && !isDefenseDiscard(e, events) ->
                    "${name(e.heroId)} discarded ${e.discarded.joinToString(" and ") { cardName(it) ?: "a card" }}"
                e.cards.isEmpty() && e.text.contains(DISCARDS_FACEDOWN) -> "${name(e.heroId)} discarded a facedown card"
                else -> null
            }
        }
        val round = events.mapNotNull { e ->
            when (e) {
                is GameEvent.LifeSpent -> e.text.removeSuffix(".")
                is GameEvent.LanePushed -> "${teamWord(e.winner)} pushed the Lane"
                is GameEvent.LevelledUp -> "${name(e.heroId)} reached level ${e.level}"
                is GameEvent.GameOver -> e.text.removeSuffix(".")
                else -> null
            }
        }
        // Gydion's spells (ADR-040): the card says "cast a spell"; which one is the news.
        val casts = events.filterIsInstance<GameEvent.CardsChanged>()
            .filter { it.heroId == hero && CASTS in it.text }
            .map { "cast ${it.text.substringAfter(CASTS).removeSuffix(".")}" }
        val parts = (casts + attacks + defeats + discards + round).toMutableList()
        if (parts.size == casts.size) {
            val moved = events.filterIsInstance<GameEvent.Moved>().filter { it.unitId == hero }.sumOf { it.path.size }
            val action = events.firstNotNullOfOrNull { it as? GameEvent.ActionChosen }?.action
            // Fast Travel places the hero (p.11): "Brogan fast travels to Side Zone 1."
            val travel = events.firstNotNullOfOrNull { e ->
                (e as? GameEvent.Placed)?.takeIf { it.pieceId == hero && FAST_TRAVELS in it.text }
            }
            when {
                travel != null -> parts += "fast travelled to ${travel.text.substringAfter(FAST_TRAVELS).removeSuffix(".")}"
                moved > 0 -> parts += "moved $moved"
            }
            val pushes = events.filterIsInstance<GameEvent.Pushed>()
            if (pushes.isNotEmpty()) parts += "pushed ${pushes.joinToString(" and ") { it.text.substringBefore(" is pushed") }}"
            val coins = events.filterIsInstance<GameEvent.CoinsGained>().filter { it.heroId == hero }.sumOf { it.amount }
            if (coins > 0) parts += "+$coins coin${if (coins == 1) "" else "s"}"
            if (parts.isEmpty()) parts += quiet(hero, action, events)
        }
        return parts.distinct().take(MAX_PARTS).joinToString(" · ")
    }

    /** One attack's result and the unit it was on, from the events after it. */
    private fun attackOutcome(attack: GameEvent.Attacked, after: List<GameEvent>): Pair<String, String> {
        val until = after.indexOfFirst { it is GameEvent.Attacked }.let { if (it < 0) after.size else it }
        val window = after.subList(0, until)
        val target = attack.targetId
        val defense = window.firstNotNullOfOrNull { e ->
            (e as? GameEvent.Defended)?.takeIf { it.attackerId == null || it.attackerId == attack.attackerId }
        }
        val defeated = window.firstNotNullOfOrNull { e ->
            (e as? GameEvent.Defeated)?.takeIf { it.unitId == target || it.unitId == defense?.heroId }
        }
        val who = defense?.heroId?.let(name) ?: defeated?.let { unitName(it.unitId, it.text) } ?: unitName(target, attack.text)
        val unit = defense?.heroId ?: defeated?.unitId ?: target
        val card = defense?.cardId?.let(cardName)
        val numbers = defense?.total?.let { " ($it vs ${attack.total})" }.orEmpty()
        return when {
            defense != null && defense.success && defense.total == null -> "$who blocked with $card" to unit
            defense != null && defense.success -> "$who defended with $card$numbers" to unit
            defense != null && card != null -> "$who defeated: $card fell short$numbers" to unit
            defeated != null -> "$who defeated" to unit
            else -> "attacked $who" to unit
        }
    }

    private fun isDefenseDiscard(e: GameEvent.CardsChanged, events: List<GameEvent>): Boolean =
        events.any { it is GameEvent.Defended && it.heroId == e.heroId && e.discarded == listOfNotNull(it.cardId) }

    /** A card that changed nothing on the board: its action in a word, or the first thing it did. */
    private fun quiet(hero: String, action: ActionType?, events: List<GameEvent>): String = when {
        events.any { it is GameEvent.Note && it.text.startsWith("${name(hero)} $OFF_BOARD") } -> "stayed off the board"
        else -> quietAction(hero, action, events)
    }

    private fun quietAction(hero: String, action: ActionType?, events: List<GameEvent>): String = when (action) {
        ActionType.HOLD -> "held"
        ActionType.CLEAR -> "cleared"
        ActionType.MOVEMENT, ActionType.FAST_TRAVEL -> "did not move"
        else -> events.firstNotNullOfOrNull { e ->
            when (e) {
                is GameEvent.ActionChosen, is GameEvent.Moved -> null
                is GameEvent.Note -> e.text.takeUnless { it.endsWith(FACEDOWN) }
                else -> e.text
            }
        }?.let { shorten(it, name(hero)) } ?: if (action == null) "no action" else "used its ${actionWord(action)}"
    }

    /** "Minion Battle: Red removes 1 · Blue pushed the Lane · Arien reached level 2". */
    private fun roundOutcome(events: List<GameEvent>, steps: List<FlowStep>): String {
        val parts = ArrayList<String>()
        events.forEach { e ->
            when (e) {
                is GameEvent.Note -> if (e.text.startsWith(MINION_BATTLE)) parts += battle(e.text)
                is GameEvent.LanePushed -> parts += "${teamWord(e.winner)} pushed the Lane"
                is GameEvent.LifeSpent -> parts += e.text.removeSuffix(".")
                is GameEvent.LevelledUp -> parts += "${name(e.heroId)} reached level ${e.level}"
                is GameEvent.GameOver -> parts += e.text.removeSuffix(".")
                else -> Unit
            }
        }
        return parts.distinct().take(MAX_PARTS + 1).joinToString(" · ").ifEmpty { outline(steps) }
    }

    /** "Minion Battle: Red 3, Blue 4. Red removes 1." → "Minion Battle: Red removes 1". */
    private fun battle(text: String): String {
        val tail = text.substringAfter(". ", "").removeSuffix(".")
        return if (tail.isNotEmpty() && "removes" in tail) "$MINION_BATTLE: $tail" else text.removeSuffix(".")
    }

    /** An entry with no single outcome: its first line, and how many more. */
    private fun outline(steps: List<FlowStep>): String {
        val first = steps.firstOrNull { it.important } ?: steps.firstOrNull() ?: return ""
        val more = steps.size - 1
        return first.text.removeSuffix(".") + if (more > 0) " · $more more" else ""
    }

    /** "Brogan gains 1 coin." → "gains 1 coin" (the hero is the title). */
    private fun shorten(text: String, heroName: String): String = text.removeSuffix(".").removePrefix("$heroName ")

    /**
     * A unit's name: a hero's, else as its event sentence says it — "Red Melee minion is defeated by
     * Brogan." or "Brogan attacks Red Melee minion: Attack 4." (a defeated minion is off the board).
     */
    private fun unitName(id: String, text: String): String = when {
        id in teams -> name(id)
        " attacks " in text -> text.substringAfter(" attacks ").substringBefore(": Attack")
        else -> text.substringBefore(" is ")
    }

    /** The entry an enemy defended against, or was defeated by, one of the person's heroes: open it. */
    private fun defendedAgainstMine(events: List<GameEvent>): Boolean = events.any { e ->
        when (e) {
            is GameEvent.Defended -> e.heroId !in mine && (e.attackerId in mine || attackerBefore(e, events) in mine)
            is GameEvent.Defeated -> e.unitId in teams && e.unitId !in mine && e.byHeroId in mine
            else -> false
        }
    }

    private fun attackerBefore(d: GameEvent.Defended, events: List<GameEvent>): String? =
        events.subList(0, events.indexOf(d)).lastOrNull { it is GameEvent.Attacked }?.let { (it as GameEvent.Attacked).attackerId }
}

private fun teamWord(team: Team): String = if (team == Team.RED) "Red" else "Blue"

private fun actionWord(action: ActionType): String = when (action) {
    ActionType.PRIMARY -> "primary action"
    ActionType.MOVEMENT -> "movement"
    ActionType.FAST_TRAVEL -> "Fast Travel"
    ActionType.ATTACK -> "attack"
    ActionType.CLEAR -> "Clear"
    ActionType.HOLD -> "Hold"
}

private const val END_OF_ROUND = "End of round"
private const val OFF_BOARD = "stays off the board"
private const val MINION_BATTLE = "Minion Battle"
private const val MAX_PARTS = 3
private const val DISCARDS_FACEDOWN = " discards a facedown card"
private const val CASTS = " casts "
private const val FAST_TRAVELS = " fast travels to "

/** At most this many entries in the panel; the log has the rest. */
const val MAX_FLOW_ENTRIES = 8
