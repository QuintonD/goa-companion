package dev.quinton.goa.core.domain.trivia

import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Rounds this app would never write (ADR-032). A received sync document or a backup file holding
 * one is refused whole, as a bad match is ([dev.quinton.goa.core.domain.sync.SyncValidation]), so a
 * broken score never spreads through the group's leaderboard.
 */
object TriviaRoundValidation {
    /** Why [round] can't be stored ("it has more right answers than questions"), or null. */
    @Suppress("CyclomaticComplexMethod")
    fun problem(round: TriviaRound): String? = with(round) {
        val counts = listOf(length, questions, correct, bestStreak, heroCount, catalogVersion)
        when {
            playerId.isBlank() -> "it has no player"
            playedAt < 0 || answerMillis < 0 || counts.any { it < 0 } -> "it has a negative number"
            questions == 0 -> "it has no answers"
            correct > questions -> "it has more right answers than questions"
            bestStreak > correct -> "its best streak is longer than its right answers"
            length != 0 && questions > length -> "it has more answers than questions in the round"
            kind == TriviaRoundKind.CHALLENGE && (length == 0 || questions != length) -> "it is an unfinished Challenge"
            else -> null
        }
    }

    /**
     * One line per bad round ("• 2026-10-03 (id r1): it has no answers."), oldest first, rounds whose
     * player is not in [playerIds] included ([missing] words it: "player p1 is missing").
     */
    fun lines(rounds: List<TriviaRound>, playerIds: Set<String>, missing: (String) -> String): List<String> =
        rounds.sortedWith(compareBy({ it.playedAt }, { it.id })).mapNotNull { round ->
            val reason = problem(round) ?: round.playerId.takeIf { it !in playerIds }?.let(missing)
            reason?.let { "• ${dateOf(round.playedAt)} (id ${round.id}): trivia round: $it." }
        }

    private fun dateOf(epochMillis: Long): String =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
