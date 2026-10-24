package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.feature.board.play.table.questionPeek
import dev.quinton.goa.feature.board.ui.PanelPeek

/**
 * The game panel's one line while it is hidden: the coach's nudge, the person's question (or the
 * space or piece they tapped, to confirm), the computer choosing, or the wait for the reveal.
 */
internal fun gamePeek(play: PlayUi): PanelPeek {
    val table = play.table
    play.nudge?.let { return PanelPeek("Coach: ${it.note.text}", attention = true) }
    return when (val status = play.status) {
        TurnStatus.YourTurn -> table.questionPeek() ?: PanelPeek(play.recent.lastOrNull().orEmpty())

        is TurnStatus.Computer -> {
            val hero = table.heroView(status.heroId)
            val name = table.hero(status.heroId).name
            PanelPeek(
                text = if (status.thinking) "thinking…" else "the computer is choosing",
                label = name,
                team = hero?.team,
            )
        }

        is TurnStatus.Waiting -> PanelPeek("Your card is down · waiting for the others to play (p.7)")

        TurnStatus.Over -> PanelPeek(play.recent.lastOrNull().orEmpty())
    }
}
