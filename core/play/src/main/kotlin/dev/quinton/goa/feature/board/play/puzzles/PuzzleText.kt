package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.model.match.Team

/** Words of the puzzles (PRODUCT §10). Gentle throughout: a miss is "Not quite", never "Wrong". */
object PuzzleText {
    /** Every puzzle is difficult (`PuzzleInfo.difficulty` 1..3). */
    fun difficulty(level: Int): String = when {
        level <= 1 -> "Hard"
        level == 2 -> "Harder"
        else -> "Hardest"
    }

    /** The top bar's name of a puzzle: its place in its part of the book. */
    fun short(info: PuzzleInfo): String =
        if (info.fullRound) "Full round ${info.number}" else "Puzzle ${info.number}"

    /** "Puzzle 3 of 12", "Full round 3 of 30". */
    fun place(info: PuzzleInfo, total: Int): String = "${short(info)} of $total"

    /** The button that takes a miss back: a move, or a whole turn in a full round. */
    fun tryAgain(info: PuzzleInfo): String =
        if (info.fullRound) "Back to the start of the turn" else "Try again from before that move"

    /** What the known card means for this hero, for the strip. */
    fun knownLine(hero: String, play: KnownCardUi): String = when (play.state) {
        KnownCardState.GONE -> "$hero has lost ${play.card?.name ?: "this card"}: it plays anything it has left."
        else -> "${play.card?.name ?: "A card"}: " + (play.plan ?: "free, it answers as hard as it can.")
    }

    /** The convention, once, where a full round is introduced. */
    const val KNOWN_NOTE: String = "You know their cards all round and what they do with them where a plan " +
        "is shown; a plan's space is outlined on the board on its turn. Where no plan is shown, or you spoil " +
        "one, they answer as hard as they can."

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
