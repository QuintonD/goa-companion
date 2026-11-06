package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.game.FACEDOWN
import dev.quinton.goa.feature.board.play.game.MINION_BATTLE
import dev.quinton.goa.feature.board.play.game.important
import dev.quinton.goa.feature.board.ui.PanelPeek

/** The one line a hidden panel shows for the question: who is asked, and the question or the choice made. */
fun TableUi.questionPeek(): PanelPeek? {
    val d = decision ?: return null
    val name = decider?.let { hero(it.heroId).name } ?: BoardText.team(d.team)
    val chosen = selected?.takeIf { asking && (it is Choice.Space || it is Choice.Unit || it is Choice.Token) }
    return PanelPeek(
        text = chosen?.let(::label) ?: d.prompt,
        label = name,
        team = d.team,
        attention = asking,
    )
}

/**
 * A line of the event log: a round or turn heading, or an event. [notice]: the table's feed shows it as it
 * happens ([feedSince]); [strong] a line a player must not miss ([important]). [actor] is the unit the event is
 * about first (a hero's id when a hero acts or is acted on; a minion's otherwise), [kind] what kind of event it is
 * (the log's icon), and [detail] a bookkeeping line (an action chosen, the reveal, a card played facedown) the log
 * folds under its turn's details.
 */
data class LogLine(
    val text: String,
    val heading: Boolean = false,
    val notice: Boolean = false,
    val strong: Boolean = false,
    val actor: String? = null,
    val kind: LogKind = LogKind.OTHER,
    val detail: Boolean = false,
)

/** What a log line tells, for its icon (never the only cue: the sentence says it). */
enum class LogKind { ATTACK, DEFEND, MOVE, PUSH, DEFEAT, REMOVE, LIFE, LEVEL, COINS, CARDS, ACTION, ROUND, OTHER }

/**
 * The log from the engine's events, oldest first; rounds and turns as headings. The feed leaves out the
 * headings (the title says them), the reveal and the cards played facedown (the turn track shows them);
 * it keeps the actions and moves, which the panel leaves to the board ([panelWorthy]).
 */
fun logLines(events: List<GameEvent>, heroIds: Set<String> = emptySet()): List<LogLine> = events.map { event ->
    val heading = event is GameEvent.RoundStarted || event is GameEvent.TurnStarted
    val facedown = event is GameEvent.Note && event.text.endsWith(FACEDOWN)
    LogLine(
        event.text,
        heading = heading,
        notice = !heading && event !is GameEvent.CardsRevealed && !facedown,
        strong = important(event, heroIds),
        actor = actorOf(event),
        kind = kindOf(event),
        detail = event is GameEvent.ActionChosen || event is GameEvent.CardsRevealed || facedown,
    )
}

private fun actorOf(event: GameEvent): String? = when (event) {
    is GameEvent.ActionChosen -> event.heroId
    is GameEvent.Moved -> event.unitId
    is GameEvent.Placed -> event.pieceId
    is GameEvent.Swapped -> event.a
    is GameEvent.Pushed -> event.unitId
    is GameEvent.Attacked -> event.attackerId
    is GameEvent.Defended -> event.heroId
    is GameEvent.Defeated -> event.unitId
    is GameEvent.Removed -> event.pieceId
    is GameEvent.CoinsGained -> event.heroId
    is GameEvent.CardsChanged -> event.heroId
    is GameEvent.LevelledUp -> event.heroId
    else -> null
}

private fun kindOf(event: GameEvent): LogKind = when (event) {
    is GameEvent.Attacked -> LogKind.ATTACK
    is GameEvent.Defended -> LogKind.DEFEND
    is GameEvent.Moved, is GameEvent.Placed, is GameEvent.Swapped -> LogKind.MOVE
    is GameEvent.Pushed, is GameEvent.LanePushed -> LogKind.PUSH
    is GameEvent.Defeated -> LogKind.DEFEAT
    is GameEvent.Removed -> LogKind.REMOVE
    is GameEvent.LifeSpent -> LogKind.LIFE
    is GameEvent.LevelledUp -> LogKind.LEVEL
    is GameEvent.CoinsGained -> LogKind.COINS
    is GameEvent.CardsChanged -> LogKind.CARDS
    is GameEvent.ActionChosen, is GameEvent.CardsRevealed -> LogKind.ACTION
    is GameEvent.RoundStarted, is GameEvent.TurnStarted, is GameEvent.GameOver -> LogKind.ROUND
    is GameEvent.Note -> if (event.text.startsWith(MINION_BATTLE)) LogKind.DEFEAT else LogKind.OTHER
}

/**
 * One turn of the log sheet: its headings ("Round 2 begins.", "Turn 1 of round 2.") and its events in the order
 * they happened. Lines keep their index in the log, so a review can mark where the game stood.
 */
data class LogTurn(val headings: List<IndexedValue<LogLine>>, val events: List<IndexedValue<LogLine>>)

/**
 * The log sheet's turns (ADR-065, revised by the hill climb): the latest turn first, and within a turn its events in
 * play order, so cause reads before effect ("Arien attacks Tigerclaw", then "Tigerclaw is defeated"). A run of
 * headings stays together at the head of the events after it; lines before the first heading are the oldest turn.
 */
fun logTurns(lines: List<LogLine>): List<LogTurn> {
    val turns = ArrayList<LogTurn>()
    var headings = ArrayList<IndexedValue<LogLine>>()
    var events = ArrayList<IndexedValue<LogLine>>()
    lines.withIndex().forEach { line ->
        if (!line.value.heading) {
            events += line
            return@forEach
        }
        if (events.isNotEmpty()) {
            turns += LogTurn(headings, events)
            headings = ArrayList()
            events = ArrayList()
        }
        headings += line
    }
    if (headings.isNotEmpty() || events.isNotEmpty()) turns += LogTurn(headings, events)
    return turns.asReversed()
}

/**
 * The log sheet's order (ADR-065): the latest turn first, its newest event first under its heading. A run
 * of headings ("Round 2 begins.", "Turn 1 of round 2.") stays together, in its own order, at the head of
 * the events after it; lines before the first heading are the oldest section. Each line keeps its index
 * in [lines], so a review can mark where the game stood.
 */
fun newestFirst(lines: List<LogLine>): List<IndexedValue<LogLine>> {
    val sections = ArrayList<Pair<List<IndexedValue<LogLine>>, List<IndexedValue<LogLine>>>>()
    var headings = ArrayList<IndexedValue<LogLine>>()
    var events = ArrayList<IndexedValue<LogLine>>()
    lines.withIndex().forEach { line ->
        if (!line.value.heading) {
            events += line
            return@forEach
        }
        if (events.isNotEmpty()) {
            sections += headings to events
            headings = ArrayList()
            events = ArrayList()
        }
        headings += line
    }
    if (headings.isNotEmpty() || events.isNotEmpty()) sections += headings to events
    return sections.asReversed().flatMap { (head, body) -> head + body.asReversed() }
}

/**
 * The feed's new lines (ADR-065): what [lines] gained since the log had [seen] lines, newest last. Nothing
 * on the first look ([seen] null: a resumed game) or when the log shrank (a take back): the feed tells
 * what is happening, not the story so far.
 */
fun feedSince(lines: List<LogLine>, seen: Int?): List<IndexedValue<LogLine>> {
    if (seen == null || lines.size <= seen) return emptyList()
    return lines.withIndex().drop(seen).filter { it.value.notice }
}

/** The feed's pace, the same on the phone and the web (ADR-065). */
object FeedPacing {
    /** At most this many notices at once, the newest on top; a burst keeps its latest. */
    const val MAX = 3

    /** How long a notice stays; a line not to miss ([LogLine.strong]) longer. */
    const val SHOW_MS = 6_000L
    const val STRONG_MS = 9_000L

    /** A notice fades as newer ones come in: its opacity at each place from the top. */
    fun alpha(place: Int): Float = FADE.getOrElse(place) { FADE.last() }

    private val FADE = listOf(1f, 0.72f, 0.45f)
}

/** Selection helper for the screens: the choice a tap on the map stands for, if it is an option. */
fun TableUi.choiceFor(tap: TableTap): Choice? = when (tap) {
    is TableTap.Piece -> pieceOptions[tap.pieceId]
    is TableTap.Space -> spaceOptions[tap.hex]
}
