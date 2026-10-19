package dev.quinton.goa.core.domain.trivia

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import org.junit.Test

/** Ranking and tie-break rules of the trivia leaderboard (ADR-032, DOMAIN.md *Card trivia*). */
class TriviaLeaderboardTest {
    private val now = 100L * DAY
    private fun player(id: String, name: String = id.replaceFirstChar(Char::uppercase), archived: Boolean = false) =
        Player(id, name, 1, 0, createdAt = 0, archived = archived)

    private val players = listOf("ann", "bob", "cid", "dee", "eve").map { player(it) }

    private var nextId = 0

    @Suppress("LongParameterList")
    private fun challenge(
        player: String,
        correct: Int,
        millis: Long = 100_000,
        at: Long = now - DAY,
        id: String? = null,
    ) =
        TriviaRound(
            id = id ?: "c${nextId++}",
            playerId = player,
            kind = TriviaRoundKind.CHALLENGE,
            playedAt = at,
            length = 20,
            questions = 20,
            correct = correct,
            bestStreak = correct / 2,
            answerMillis = millis,
            heroCount = 32,
            catalogVersion = 7,
        )

    private fun practice(player: String, correct: Int, questions: Int, streak: Int = 1, at: Long = now - DAY) =
        TriviaRound(
            id = "p${nextId++}",
            playerId = player,
            kind = TriviaRoundKind.PRACTICE,
            playedAt = at,
            length = 0,
            questions = questions,
            correct = correct,
            bestStreak = streak,
            answerMillis = 1_000,
            heroCount = 3,
            catalogVersion = 7,
        )

    private fun board(
        rounds: List<TriviaRound>,
        me: String? = null,
        period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
        merges: PlayerMerges = PlayerMerges.NONE,
        people: List<Player> = players,
    ) = TriviaLeaderboard.build(rounds, people, me, now, period, merges)

    @Test
    fun `the challenge board ranks each player's best by score, then less time, then the earlier round`() {
        val annBest = challenge("ann", 18, millis = 90_000)
        val rounds = listOf(
            challenge("ann", 12),
            annBest,
            challenge("bob", 18, millis = 80_000), // same score, faster: ahead of Ann
            challenge("cid", 18, millis = 90_000, at = now - 5 * DAY), // same score and time, earlier: ahead of Ann
            challenge("dee", 19, millis = 200_000),
        )
        val rows = board(rounds, me = "ann").challenge
        assertThat(rows.map { it.playerId }).containsExactly("dee", "bob", "cid", "ann").inOrder()
        assertThat(rows.map { it.place }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(rows.none { it.tied }).isTrue()
        val ann = rows.last()
        assertThat(ann.best).isEqualTo(annBest)
        assertThat(ann.challenges).isEqualTo(2)
        assertThat(ann.isMe).isTrue()
        assertThat(rows.count { it.isMe }).isEqualTo(1)
    }

    @Test
    fun `answer times compare in tenths of a second, as the board shows them`() {
        assertThat(TriviaLeaderboard.tenths(102_349)).isEqualTo(1_023)
        assertThat(TriviaLeaderboard.tenths(102_350)).isEqualTo(1_024)
        val rows = board(
            listOf(
                // 1:42.3 both (102 260 and 102 340 ms): the earlier round wins, no shared place.
                challenge("bob", 18, millis = 102_340, at = 5),
                challenge("ann", 18, millis = 102_260, at = 9),
                // 1:42.4: slower in tenths, even though it is only 0.07 s behind Bob.
                challenge("cid", 18, millis = 102_410, at = 1),
                // Same tenths and the same moment as Ann: a full tie.
                challenge("dee", 18, millis = 102_290, at = 9),
            ),
        ).challenge
        assertThat(rows.map { it.playerId }).containsExactly("bob", "ann", "dee", "cid").inOrder()
        assertThat(rows.map { it.place }).containsExactly(1, 2, 2, 4).inOrder()
    }

    @Test
    fun `players tied on score, time and date share a place and the next place is skipped`() {
        val rows = board(
            listOf(
                challenge("eve", 15, millis = 60_000, at = 7),
                challenge("bob", 15, millis = 60_000, at = 7),
                challenge("ann", 17),
                challenge("cid", 10),
            ),
        ).challenge
        // Tied rows are listed by name.
        assertThat(rows.map { it.playerId }).containsExactly("ann", "bob", "eve", "cid").inOrder()
        assertThat(rows.map { it.place }).containsExactly(1, 2, 2, 4).inOrder()
        assertThat(rows.map { it.tied }).containsExactly(false, true, true, false).inOrder()
        val eve = challenge("eve", 15, millis = 1, at = 7)
        val tiedBoard = board(listOf(eve, challenge("bob", 15, millis = 1, at = 7)))
        assertThat(TriviaLeaderboard.standing(tiedBoard, "eve"))
            .isEqualTo(ChallengeStanding(place = 1, tied = true, of = 2, best = eve))
        assertThat(TriviaLeaderboard.standing(tiedBoard, "ann")).isNull()
    }

    @Test
    fun `an unfinished challenge never counts on the challenge board but counts as practice`() {
        val unfinished = challenge("ann", 9).copy(questions = 12)
        val result = board(listOf(unfinished, challenge("bob", 5)))
        assertThat(result.challenge.map { it.playerId }).containsExactly("bob")
        assertThat(result.practice.map { it.playerId }).containsExactly("ann", "bob").inOrder()
    }

    @Test
    fun `the practice board sums all rounds, challenges too, by right answers, accuracy, fewer questions`() {
        val rows = board(
            listOf(
                practice("ann", 30, 40, streak = 12),
                challenge("ann", 15), // Ann: 45 of 60
                practice("bob", 45, 50), // 45 of 50: same right answers, better accuracy
                practice("cid", 50, 100),
                practice("dee", 9, 10),
                practice("eve", 18, 20), // 18/20 = 9/10 but more answers: ahead of Dee
            ),
        ).practice
        assertThat(rows.map { it.playerId }).containsExactly("cid", "bob", "ann", "eve", "dee").inOrder()
        assertThat(rows.map { it.place }).containsExactly(1, 2, 3, 4, 5).inOrder()
        val ann = rows.first { it.playerId == "ann" }
        assertThat(ann.correct).isEqualTo(45)
        assertThat(ann.questions).isEqualTo(60)
        assertThat(ann.accuracy).isWithin(1e-9).of(0.75)
        assertThat(ann.bestStreak).isEqualTo(12)
        assertThat(ann.rounds).isEqualTo(2)
    }

    @Test
    fun `practice ties need the same right answers and questions`() {
        val rows = board(listOf(practice("bob", 9, 10), practice("ann", 9, 10), practice("cid", 9, 12))).practice
        assertThat(rows.map { it.playerId to it.place }).containsExactly("ann" to 1, "bob" to 1, "cid" to 3).inOrder()
        assertThat(rows.map { it.tied }).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun `last 30 days counts only recent rounds, and a best from long ago drops out`() {
        val old = challenge("ann", 20, at = now - 40 * DAY)
        val recent = challenge("ann", 11, at = now - 3 * DAY)
        val edge = challenge("bob", 12, at = now - 30 * DAY)
        val rounds = listOf(old, recent, edge, practice("cid", 5, 5, at = now - 31 * DAY))
        val all = board(rounds)
        assertThat(all.challenge.first().best).isEqualTo(old)
        assertThat(all.practice.map { it.playerId }).contains("cid")
        val month = board(rounds, period = LeaderboardPeriod.LAST_30_DAYS)
        assertThat(
            month.challenge.map {
                it.playerId to it.best
            },
        ).containsExactly("bob" to edge, "ann" to recent).inOrder()
        assertThat(month.practice.map { it.playerId }).doesNotContain("cid")
        assertThat(TriviaLeaderboard.playerRounds(rounds, "ann", now, LeaderboardPeriod.LAST_30_DAYS))
            .containsExactly(recent)
        assertThat(TriviaLeaderboard.playerRounds(rounds, "ann", now, LeaderboardPeriod.ALL_TIME))
            .containsExactly(recent, old).inOrder()
    }

    @Test
    fun `unknown, archived and merged-away players - rounds go to whom they belong or nowhere`() {
        val merges = PlayerMerges(listOf(PlayerMerge("ann-2", "ann", 1)))
        val people = players.map { if (it.id == "eve") it.copy(archived = true) else it } + player("ann-2", "Ann")
        val rounds = listOf(
            challenge("ann", 10),
            challenge("ann-2", 16), // Ann's duplicate: her best now
            practice("ann-2", 4, 4),
            challenge("ghost", 20), // a player this phone doesn't know
            challenge("eve", 19), // archived: not ranked
        )
        val result = board(rounds, me = "ann-2", merges = merges, people = people)
        assertThat(result.challenge.map { it.playerId }).containsExactly("ann")
        val ann = result.challenge.single()
        assertThat(ann.best.correct).isEqualTo(16)
        assertThat(ann.best.playerId).isEqualTo("ann")
        assertThat(ann.challenges).isEqualTo(2)
        // "You" set to the merged-away id still marks the player it was merged into.
        assertThat(ann.isMe).isTrue()
        assertThat(result.practice.single().rounds).isEqualTo(3)
        assertThat(TriviaLeaderboard.playerRounds(rounds, "ann", now, LeaderboardPeriod.ALL_TIME, merges)).hasSize(3)
        assertThat(board(emptyList())).isEqualTo(TriviaBoard.EMPTY)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
