package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.game.FACEDOWN
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
 * happens ([feedSince]); [strong] a line a player must not miss ([important]).
 */
data class LogLine(
    val text: String,
    val heading: Boolean = false,
    val notice: Boolean = false,
    val strong: Boolean = false,
)

/**
 * The log from the engine's events, oldest first; rounds and turns as headings. The feed leaves out the
 * headings (the title says them), the reveal and the cards played facedown (the turn track shows them);
 * it keeps the actions and moves, which the panel leaves to the board ([panelWorthy]).
 */
fun logLines(events: List<GameEvent>, heroIds: Set<String> = emptySet()): List<LogLine> = events.map { event ->
    val heading = event is GameEvent.RoundStarted || event is GameEvent.TurnStarted
    LogLine(
        event.text,
        heading = heading,
        notice = !heading && event !is GameEvent.CardsRevealed &&
            !(event is GameEvent.Note && event.text.endsWith(FACEDOWN)),
        strong = important(event, heroIds),
    )
}

/**
 * The log sheet's order (ADR-060): the latest turn first, its newest event first under its heading. A run
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
 * The feed's new lines (ADR-060): what [lines] gained since the log had [seen] lines, newest last. Nothing
 * on the first look ([seen] null: a resumed game) or when the log shrank (a take back): the feed tells
 * what is happening, not the story so far.
 */
fun feedSince(lines: List<LogLine>, seen: Int?): List<IndexedValue<LogLine>> {
    if (seen == null || lines.size <= seen) return emptyList()
    return lines.withIndex().drop(seen).filter { it.value.notice }
}

/** The feed's pace, the same on the phone and the web (ADR-060). */
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
