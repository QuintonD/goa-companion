package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.model.match.Team

/** Words of the puzzles (PRODUCT §10). Gentle throughout: a miss is "Not quite", never "Wrong". */
object PuzzleText {
    /** Every puzzle is difficult (`PuzzleInfo.difficulty` 1..3). */
    fun difficulty(level: Int): String = when {
        level <= 1 -> "Hard"
        level == 2 -> "Harder"
        else -> "Hardest"
    }

    fun names(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private fun deadline(by: Deadline): String = if (by == Deadline.END_OF_TURN) "this turn" else "this round"

    /**
     * Why a move missed, from what followed it: the defense it let through, the hero it left open,
     * else what the goal can no longer reach. [team] is the player's; [name] names heroes, [card] cards.
     */
    fun missReason(
        goal: PuzzleGoal,
        events: List<GameEvent>,
        team: Team?,
        teamOf: (String) -> Team?,
        mine: Set<String>,
        name: (String) -> String,
        card: (String) -> String?,
    ): String {
        val defended = events.filterIsInstance<GameEvent.Defended>().firstOrNull {
            it.success && teamOf(it.heroId) != team
        }
        if (defended != null) {
            val with = defended.cardId?.let(card)?.let { " with $it" }.orEmpty()
            return "Not quite — that lets ${name(defended.heroId)} defend$with."
        }
        val fallen = events.filterIsInstance<GameEvent.Defeated>().firstOrNull { it.unitId in mine }
        if (fallen != null) return "Not quite — that leaves ${name(fallen.unitId)} open to defeat."
        return when (goal) {
            is PuzzleGoal.DefeatHero ->
                "Not quite — after that, ${name(goal.heroId)} can always get through ${deadline(goal.by)}."

            is PuzzleGoal.WinGame -> "Not quite — after that, the other side can always stop the win."

            is PuzzleGoal.PushLane ->
                "Not quite — after that, the other side can always hold the lane ${deadline(goal.by)}."

            is PuzzleGoal.Survive ->
                "Not quite — after that, the other side can always reach you ${deadline(goal.by)}."

            is PuzzleGoal.GainCoins ->
                "Not quite — after that, ${goal.atLeast} coins are out of reach ${deadline(goal.by)}."
        }
    }

    /** "First try · no nudges", "3 tries · 2 nudges". */
    fun record(attempts: Int, nudges: Int): String = listOf(
        if (attempts <= 1) "First try" else "$attempts tries",
        when (nudges) {
            0 -> "no nudges"
            1 -> "1 nudge"
            else -> "$nudges nudges"
        },
    ).joinToString(" · ")
}
