package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.server.game.store.GameSeatRow
import org.junit.Test

class RatedRuleTest {
    private val now = 100 * RatedRule.WINDOW_MILLIS
    private val both = setOf(Team.RED, Team.BLUE)

    private fun seats(vararg red: String, blue: List<String> = listOf("bob")): List<GameSeatRow> =
        red.mapIndexed { i, id -> GameSeatRow("g", "r$i", id, Team.RED, false, i) } +
            blue.mapIndexed { i, id -> GameSeatRow("g", "b$i", id, Team.BLUE, false, 10 + i) }

    private fun game(
        n: Int,
        at: Long,
        red: List<String> = listOf("ann"),
        blue: List<String> = listOf("bob"),
        status: MatchStatus = MatchStatus.COMPLETED,
    ) = Match(
        id = "m$n",
        createdAt = at - 1,
        playedAt = at,
        status = status,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = red.mapIndexed { i, id -> Participant(id, "r$i", Team.RED) } +
            blue.mapIndexed { i, id -> Participant(id, "b$i", Team.BLUE) },
    )

    private fun games(count: Int, at: Long = now - 1_000) = List(count) { game(it, at + it) }

    @Test
    fun `an ordinary game counts`() {
        assertThat(RatedRule.counts(seats("ann"), both, emptyList(), now)).isTrue()
    }

    @Test
    fun `a team with no answer of a person's own does not count`() {
        assertThat(RatedRule.counts(seats("ann"), setOf(Team.RED), emptyList(), now)).isFalse()
        assertThat(RatedRule.counts(seats("ann"), setOf(Team.BLUE), emptyList(), now)).isFalse()
    }

    @Test
    fun `no answer of a person's own on either side does not count`() {
        assertThat(RatedRule.counts(seats("ann"), emptySet(), emptyList(), now)).isFalse()
    }

    @Test
    fun `the eleventh meeting within seven days does not count`() {
        assertThat(RatedRule.counts(seats("ann"), both, games(9), now)).isTrue()
        assertThat(RatedRule.counts(seats("ann"), both, games(10), now)).isFalse()
    }

    @Test
    fun `meetings older than seven days are forgotten`() {
        assertThat(RatedRule.counts(seats("ann"), both, games(10, at = now - RatedRule.WINDOW_MILLIS - 100), now))
            .isTrue()
        val edge = List(10) { game(it, now - RatedRule.WINDOW_MILLIS) }
        assertThat(RatedRule.counts(seats("ann"), both, edge, now)).isTrue()
        val inside = List(10) { game(it, now - RatedRule.WINDOW_MILLIS + 1) }
        assertThat(RatedRule.counts(seats("ann"), both, inside, now)).isFalse()
    }

    @Test
    fun `meetings count from either side of the table`() {
        val swapped = List(10) { game(it, now - 1_000, red = listOf("bob"), blue = listOf("ann")) }
        assertThat(RatedRule.counts(seats("ann"), both, swapped, now)).isFalse()
    }

    @Test
    fun `playing on the same team is not meeting`() {
        val together = List(10) { game(it, now - 1_000, red = listOf("ann", "bob"), blue = listOf("cid", "dee")) }
        assertThat(RatedRule.counts(seats("ann"), both, together, now)).isTrue()
    }

    @Test
    fun `other people's meetings don't matter, and one pair of a team game is enough`() {
        val others = List(10) { game(it, now - 1_000, red = listOf("cid"), blue = listOf("dee")) }
        assertThat(RatedRule.counts(seats("ann"), both, others, now)).isTrue()
        val team = seats("ann", "cid", blue = listOf("bob", "dee"))
        assertThat(RatedRule.counts(team, both, others, now)).isFalse()
    }

    @Test
    fun `a row the ratings would skip is not a rated game`() {
        val abandoned = List(10) { game(it, now - 1_000, status = MatchStatus.ABANDONED) }
        assertThat(RatedRule.counts(seats("ann"), both, abandoned, now)).isTrue()
    }
}
