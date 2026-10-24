package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.feature.board.play.table.questionPeek
import dev.quinton.goa.feature.board.ui.PanelPeek

/**
 * The puzzle panel's one line while it is hidden: the question (or the space or piece tapped, to
 * confirm), the other side's latest reply, the miss, the seal, or the replay's step.
 */
internal fun puzzlePeek(puzzle: PuzzleUi): PanelPeek {
    val replay = puzzle.replay
    return when {
        replay != null -> PanelPeek(
            text = "Step ${replay.index + 1} of ${replay.count} · ${replay.title}",
            label = "Solution",
        )

        puzzle.phase == PuzzlePhase.SOLVED -> PanelPeek(text = puzzle.info.title, label = "Solved")

        puzzle.phase == PuzzlePhase.REPLYING -> puzzle.replying.let { replying ->
            PanelPeek(text = replying?.lines?.lastOrNull() ?: "…", label = replying?.title)
        }

        puzzle.phase == PuzzlePhase.MISSED -> PanelPeek(text = "Show the panel to try again", label = "Not quite")

        else -> puzzle.table.questionPeek() ?: PanelPeek(puzzle.info.goal.text)
    }
}
