package dev.quinton.goa.feature.catalog.model

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import org.junit.Test

class HeroPilotsTest {
    private val ana = Player("ana", "Ana", 0xFF3FC1B0, 1, 0)
    private val ben = Player("ben", "Ben", 0xFFC9A35B, 2, 0)

    private fun match(
        id: String,
        winner: Team?,
        hero: String,
        pilot: String,
        team: Team = Team.RED,
        status: MatchStatus = MatchStatus.COMPLETED,
    ) = Match(
        id = id,
        createdAt = 0,
        playedAt = 0,
        status = status,
        mode = GameMode.QUICK,
        winner = winner,
        participants = listOf(
            Participant(pilot, hero, team),
            Participant("other", "someone-else", team.opponent),
        ),
    )

    @Test
    fun countsOnlyCompletedMatchesWithAWinner() {
        val matches = listOf(
            match("1", Team.RED, "h", "ana"),
            match("2", null, "h", "ana"),
            match("3", Team.RED, "h", "ana", status = MatchStatus.PENDING),
            match("4", Team.BLUE, "h", "ana", status = MatchStatus.ABANDONED),
        )
        val summary = HeroPilots.summarize("h", matches, listOf(ana))
        assertThat(summary.games).isEqualTo(1)
        assertThat(summary.wins).isEqualTo(1)
    }

    @Test
    fun ranksPlayersWithEnoughGamesByWinRate() {
        val matches = listOf(
            match("1", Team.RED, "h", "ana"),
            match("2", Team.BLUE, "h", "ana"),
            match("3", Team.RED, "h", "ana"),
            match("4", Team.RED, "h", "ben"),
            match("5", Team.RED, "h", "ben"),
            match("6", Team.RED, "h", "ben"),
            match("7", Team.RED, "h", "ghost"),
        )
        val summary = HeroPilots.summarize("h", matches, listOf(ana, ben))
        assertThat(summary.bestPilots.map { it.playerId }).containsExactly("ben", "ana").inOrder()
        assertThat(summary.bestPilots.first().winRate).isEqualTo(1.0)
        assertThat(summary.allPilots.map { it.playerId }).contains("ghost")
        assertThat(summary.allPilots.first { it.playerId == "ghost" }.player).isNull()
        assertThat(summary.games).isEqualTo(7)
        assertThat(summary.wins).isEqualTo(6)
    }

    @Test
    fun ignoresOtherHeroes() {
        val summary = HeroPilots.summarize("h", listOf(match("1", Team.RED, "x", "ana")), listOf(ana))
        assertThat(summary.games).isEqualTo(0)
        assertThat(summary.allPilots).isEmpty()
    }
}
