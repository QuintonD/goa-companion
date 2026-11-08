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

    /** The Continue card's line: "3. Title", or "Full round 3. Title" (numbers repeat across the parts). */
    fun numbered(info: PuzzleInfo): String =
        if (info.fullRound) "Full round ${info.number}. ${info.title}" else "${info.number}. ${info.title}"

    /**
     * A part of the book's progress, in the same words on the Play tab and the puzzle list: "1 of 50 positions",
     * "2 of 22 rounds" (solved; TalkBack adds the word).
     */
    fun partCount(solved: Int, total: Int, rounds: Boolean): String =
        "$solved of $total ${if (rounds) "rounds" else "positions"}"

    /** "Puzzle 3 of 12", "Full round 3 of 30". */
    fun place(info: PuzzleInfo, total: Int): String = "${short(info)} of $total"

    /** The button that takes a miss back: a move, or a whole turn in a full round. */
    fun tryAgain(info: PuzzleInfo): String =
        if (info.fullRound) "Back to the start of the turn" else "Try again from before that move"

    /** What the known card means for this hero, for the strip. */
    fun knownLine(hero: String, play: KnownCardUi): String = when (play.state) {
        KnownCardState.GONE -> "$hero has lost ${play.card?.name ?: "this card"}: it plays anything it has left."
        else -> "${play.card?.name ?: "A card"}: " + (play.plan ?: "no plan shown: it chooses for itself.")
    }

    /** A later turn's line: its plan's space is outlined on the board while the later turns are shown. */
    fun laterLine(hero: String, play: KnownCardUi): String = "Turn ${play.turn}. ${knownLine(hero, play)}"

    /** The convention, once, where a full round is introduced. */
    const val KNOWN_NOTE: String = "You know all five of their cards: the four they play and the one they keep, " +
        "marked –. Where a plan is shown you know what they do with them; its space is outlined on the board on its " +
        "turn, and while the later turns are shown. Where no plan is shown, at every defense, and if you " +
        "spoil a plan, they choose for themselves. Away from the solution they don't always find their best " +
        "answer, so a win there may not be a forced one."

    /**
     * A puzzle's setup one sentence a line, word for word ("Round 3, turn 4." | "Brogan has played…"): split as
     * [whyItWorks] splits, so "p.14" and "(9)" stay whole and the lines read back as the paragraph.
     */
    fun sentences(text: String): List<String> =
        text.trim().replace(SENTENCE, "$1\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

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

    /**
     * "Why it works" as numbered lines to scan, split from the puzzle's own words and never reworded: a full
     * round's paragraphs by turn ("Turn 2", with any paragraph before them as the [WhyItWorks.lead]), a
     * puzzle's one paragraph sentence by sentence.
     */
    fun whyItWorks(explanation: String): WhyItWorks {
        val paragraphs = explanation.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size > 1) {
            val first = paragraphs.indexOfFirst { TURN.containsMatchIn(it) }
            val turns = if (first < 0) emptyList() else paragraphs.drop(first).mapNotNull { TURN.find(it) }
            if (first >= 0 && turns.size == paragraphs.size - first) {
                return WhyItWorks(
                    lead = paragraphs.take(first).joinToString(" ").ifEmpty { null },
                    steps = paragraphs.drop(first).zip(turns) { text, turn ->
                        WhyItWorks.Step("Turn ${turn.groupValues[1]}", text.substring(turn.range.last + 1).trim())
                    },
                )
            }
            return WhyItWorks(null, paragraphs.mapIndexed { i, text -> WhyItWorks.Step("${i + 1}", text) })
        }
        val sentences = explanation.trim().replace(SENTENCE, "$1\n").split("\n").filter { it.isNotBlank() }
        return WhyItWorks(null, sentences.mapIndexed { i, text -> WhyItWorks.Step("${i + 1}", text.trim()) })
    }

    private val TURN = Regex("""^Turn (\d+):""")

    /**
     * A full stop, "!" or "?" and the space after it, before a capital, a digit, a quote or a bracket: "p.13"
     * never splits. No look-behind: the web app runs this too, and older Safari has none.
     */
    private val SENTENCE = Regex("""([.!?])\s+(?=[A-Z0-9"“(])""")

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

/** A puzzle's explanation as lines to scan ([PuzzleText.whyItWorks]): [lead] unnumbered, then the [steps]. */
data class WhyItWorks(val lead: String?, val steps: List<Step>) {
    /** [label]: "1", "2"… or "Turn 1"…; [text] the puzzle's own words. */
    data class Step(val label: String, val text: String)
}
