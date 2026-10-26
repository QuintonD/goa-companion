package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.BoardText
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

/** A line of the event log: a round or turn heading, or an event. */
data class LogLine(val text: String, val heading: Boolean = false)

/** The log from the engine's events, oldest first; rounds and turns as headings. */
fun logLines(events: List<GameEvent>): List<LogLine> = events.map { event ->
    LogLine(event.text, heading = event is GameEvent.RoundStarted || event is GameEvent.TurnStarted)
}

/** Selection helper for the screens: the choice a tap on the map stands for, if it is an option. */
fun TableUi.choiceFor(tap: TableTap): Choice? = when (tap) {
    is TableTap.Piece -> pieceOptions[tap.pieceId]
    is TableTap.Space -> spaceOptions[tap.hex]
}
