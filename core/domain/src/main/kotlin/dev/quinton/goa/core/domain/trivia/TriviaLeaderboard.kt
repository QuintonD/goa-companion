package dev.quinton.goa.core.domain.trivia

import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind

/** Which rounds the leaderboard counts. */
enum class LeaderboardPeriod {
    ALL_TIME,

    /** Rounds played in the last [TriviaLeaderboard.RECENT_DAYS] days. */
    LAST_30_DAYS,
}

/**
 * A player's row on the Challenge board: their best finished Challenge in the period. [place] is
 * shared by players whose best is tied on score, answer time and date ([tied]).
 */
data class ChallengeRow(
    val place: Int,
    val tied: Boolean,
    val playerId: String,
    val isMe: Boolean,
    val best: TriviaRound,
    /** Finished Challenges this player played in the period. */
    val challenges: Int,
)

/** A player's row on the Practice board: every recorded round in the period, Challenges included. */
data class PracticeRow(
    val place: Int,
    val tied: Boolean,
    val playerId: String,
    val isMe: Boolean,
    val correct: Int,
    val questions: Int,
    val bestStreak: Int,
    val rounds: Int,
) {
    /** Share of right answers, 0..1. */
    val accuracy: Double get() = if (questions == 0) 0.0 else correct.toDouble() / questions
}

data class TriviaBoard(val challenge: List<ChallengeRow>, val practice: List<PracticeRow>) {
    companion object {
        val EMPTY = TriviaBoard(emptyList(), emptyList())
    }
}

/** Where a player stands on the Challenge board after a round ("2nd of 5 · your best 18/20"). */
data class ChallengeStanding(
    val place: Int,
    val tied: Boolean,
    /** Players on the board. */
    val of: Int,
    /** The player's best Challenge, which may be the round just played. */
    val best: TriviaRound,
)

/**
 * The trivia leaderboard (ADR-032, DOMAIN.md *Card trivia*), derived from the recorded rounds and
 * never stored. Pure: the caller passes the clock.
 *
 * - Rounds count for the player their id resolves to through [PlayerMerges] (a merged duplicate's
 *   rounds are one player's). Rounds of a player who isn't known, is merged away or is archived are
 *   left out, as archived players are left out of the Players tab's ranking.
 * - **Challenge:** each player's best finished Challenge: more right answers, then less answer
 *   time, then the earlier round. Players tied on all three share a place ("1, 2, 2, 4").
 * - **Practice:** all rounds, Challenges included, summed per player: more right answers, then a
 *   higher accuracy, then fewer questions; tied on all three = shared place.
 * - Among players sharing a place, rows are listed by name.
 */
object TriviaLeaderboard {
    const val RECENT_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    @Suppress("LongParameterList")
    fun build(
        rounds: List<TriviaRound>,
        players: List<Player>,
        meId: String?,
        now: Long,
        period: LeaderboardPeriod,
        merges: PlayerMerges = PlayerMerges.NONE,
    ): TriviaBoard {
        val counted = counted(rounds, players, now, period, merges)
        val names = players.associate { it.id to it.name.lowercase() }
        val me = meId?.let(merges::resolve)
        return TriviaBoard(
            challenge = challengeRows(counted, names, me),
            practice = practiceRows(counted, names, me),
        )
    }

    /** [playerId]'s place on [board]'s Challenge board, or null when they aren't on it. */
    fun standing(board: TriviaBoard, playerId: String): ChallengeStanding? =
        board.challenge.firstOrNull { it.playerId == playerId }?.let { row ->
            ChallengeStanding(row.place, row.tied, board.challenge.size, row.best)
        }

    /** [playerId]'s rounds (after merges) in [period], newest first. */
    @Suppress("LongParameterList")
    fun recentRounds(
        rounds: List<TriviaRound>,
        playerId: String,
        now: Long,
        period: LeaderboardPeriod,
        merges: PlayerMerges = PlayerMerges.NONE,
        limit: Int = RECENT_LIMIT,
    ): List<TriviaRound> = rounds
        .filter { merges.resolve(it.playerId) == playerId && inPeriod(it, now, period) }
        .sortedWith(compareByDescending<TriviaRound> { it.playedAt }.thenByDescending { it.id })
        .take(limit)

    const val RECENT_LIMIT = 30

    fun inPeriod(round: TriviaRound, now: Long, period: LeaderboardPeriod): Boolean = when (period) {
        LeaderboardPeriod.ALL_TIME -> true
        LeaderboardPeriod.LAST_30_DAYS -> round.playedAt >= now - RECENT_DAYS * DAY_MS
    }

    /** A finished Challenge as the board counts it. */
    fun isFinishedChallenge(round: TriviaRound): Boolean =
        round.kind == TriviaRoundKind.CHALLENGE && round.length > 0 && round.questions == round.length

    /** The better Challenge first: more right, then faster, then earlier (then id, for a fixed order). */
    val challengeOrder: Comparator<TriviaRound> = compareByDescending<TriviaRound> { it.correct }
        .thenBy { it.answerMillis }
        .thenBy { it.playedAt }
        .thenBy { it.id }

    private fun counted(
        rounds: List<TriviaRound>,
        players: List<Player>,
        now: Long,
        period: LeaderboardPeriod,
        merges: PlayerMerges,
    ): List<TriviaRound> {
        val eligible = players.filterNot { it.archived || it.id in merges.mergedAway }.mapTo(HashSet()) { it.id }
        return rounds.mapNotNull { round ->
            val id = merges.resolve(round.playerId)
            when {
                id !in eligible || !inPeriod(round, now, period) -> null
                id == round.playerId -> round
                else -> round.copy(playerId = id)
            }
        }
    }

    private fun challengeRows(rounds: List<TriviaRound>, names: Map<String, String>, me: String?): List<ChallengeRow> {
        val byPlayer = rounds.filter(::isFinishedChallenge).groupBy { it.playerId }
        val bests = byPlayer.mapValues { (_, list) -> list.minWith(challengeOrder) }
        fun key(r: TriviaRound) = Triple(r.correct, r.answerMillis, r.playedAt)
        val sorted = bests.values.sortedWith(
            compareByDescending<TriviaRound> { it.correct }
                .thenBy { it.answerMillis }
                .thenBy { it.playedAt }
                .thenBy { names[it.playerId] ?: it.playerId }
                .thenBy { it.playerId },
        )
        return places(sorted, ::key).map { (round, place, tied) ->
            ChallengeRow(
                place = place,
                tied = tied,
                playerId = round.playerId,
                isMe = round.playerId == me,
                best = round,
                challenges = byPlayer.getValue(round.playerId).size,
            )
        }
    }

    private fun practiceRows(rounds: List<TriviaRound>, names: Map<String, String>, me: String?): List<PracticeRow> {
        val totals = rounds.groupBy { it.playerId }.map { (id, list) ->
            PracticeRow(
                place = 0,
                tied = false,
                playerId = id,
                isMe = id == me,
                correct = list.sumOf { it.correct },
                questions = list.sumOf { it.questions },
                bestStreak = list.maxOf { it.bestStreak },
                rounds = list.size,
            )
        }.filter { it.questions > 0 }
        // Accuracy compared exactly (a/b vs c/d as a·d vs c·b), so equal shares tie.
        val accuracy = Comparator<PracticeRow> { a, b ->
            (b.correct.toLong() * a.questions).compareTo(a.correct.toLong() * b.questions)
        }
        val sorted = totals.sortedWith(
            compareByDescending<PracticeRow> { it.correct }
                .then(accuracy)
                .thenBy { it.questions }
                .thenBy { names[it.playerId] ?: it.playerId }
                .thenBy { it.playerId },
        )
        return places(sorted) { it.correct to it.questions }.map { (row, place, tied) ->
            row.copy(place = place, tied = tied)
        }
    }

    /**
     * Standard competition places over [sorted] ("1, 2, 2, 4"): an item shares the place of the
     * first one with the same [key]; [Triple.third] = it shares its place with another.
     */
    private fun <T, K> places(sorted: List<T>, key: (T) -> K): List<Triple<T, Int, Boolean>> {
        val keys = sorted.map(key)
        val counts = keys.groupingBy { it }.eachCount()
        var place = 0
        return sorted.mapIndexed { i, item ->
            if (i == 0 || keys[i] != keys[i - 1]) place = i + 1
            Triple(item, place, counts.getValue(keys[i]) > 1)
        }
    }
}
