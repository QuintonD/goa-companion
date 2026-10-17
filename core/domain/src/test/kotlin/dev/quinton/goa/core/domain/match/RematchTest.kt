package dev.quinton.goa.core.domain.match

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import org.junit.Test

class RematchTest {
    /** 3 players as 2v2 seats: Ann double-pilots Red, her second hero carries the Handicap card. */
    private val threePlayers = Match(
        id = "m1",
        createdAt = 1,
        playedAt = 1,
        status = MatchStatus.COMPLETED,
        mode = GameMode.LONG,
        map = "Back room",
        winner = Team.RED,
        winCondition = WinCondition.THRONE,
        durationMin = 55,
        notes = "close one",
        draftId = "d1",
        participants = listOf(
            Participant("ann", "arien", Team.RED),
            Participant("ann", "brogan", Team.RED, handicap = true),
            Participant("bob", "wasp", Team.BLUE),
            Participant("cid", "xargatha", Team.BLUE),
        ),
        tieBreaker = Team.BLUE,
    )

    private fun rematch(kind: RematchKind, match: Match = threePlayers) =
        Rematch.create(match, kind, id = "new", now = 99).getOrThrow()

    @Test fun aRematchIsANewPendingGameWithoutResultDraftOrCoin() {
        val m = rematch(RematchKind.SAME_SEATS)
        assertThat(m.id).isEqualTo("new")
        assertThat(m.status).isEqualTo(MatchStatus.PENDING)
        assertThat(m.playedAt).isEqualTo(99)
        assertThat(m.createdAt).isEqualTo(99)
        assertThat(m.mode).isEqualTo(GameMode.LONG)
        assertThat(m.map).isEqualTo("Back room")
        assertThat(listOf(m.winner, m.winCondition, m.durationMin, m.draftId, m.tieBreaker)).containsExactly(
            null,
            null,
            null,
            null,
            null,
        )
        assertThat(m.notes).isEmpty()
        assertThat(m.participants).isEqualTo(threePlayers.participants)
    }

    @Test fun swapTeamsMovesWholeSeatsKeepingTheDoublePilotAndHandicapTogether() {
        val m = rematch(RematchKind.SWAP_TEAMS)
        assertThat(m.team(Team.BLUE)).containsExactly(
            Participant("ann", "arien", Team.BLUE),
            Participant("ann", "brogan", Team.BLUE, handicap = true),
        )
        assertThat(m.team(Team.RED).map { it.playerId }).containsExactly("bob", "cid")
    }

    @Test fun swapHeroesKeepsPlayersAndHandicapSeatsAndTradesHeroes() {
        val m = rematch(RematchKind.SWAP_HEROES)
        assertThat(m.team(Team.RED)).containsExactly(
            Participant("ann", "wasp", Team.RED),
            Participant("ann", "xargatha", Team.RED, handicap = true),
        ).inOrder()
        assertThat(m.team(Team.BLUE)).containsExactly(
            Participant("bob", "arien", Team.BLUE),
            Participant("cid", "brogan", Team.BLUE),
        ).inOrder()
    }

    @Test fun swapHeroesNeedsEvenTeams() {
        val uneven = threePlayers.copy(participants = threePlayers.participants + Participant("dee", "tali", Team.BLUE))
        assertThat(Rematch.unavailable(uneven, RematchKind.SWAP_HEROES)).contains("same number")
        assertThat(Rematch.create(uneven, RematchKind.SWAP_HEROES, "x", 0).isFailure).isTrue()
        assertThat(Rematch.unavailable(uneven, RematchKind.SWAP_TEAMS)).isNull()
        assertThat(Rematch.unavailable(uneven, RematchKind.SAME_SEATS)).isNull()
    }

    @Test fun unknownHeroSeatsStayUnknown() {
        val unknown = threePlayers.copy(
            participants = threePlayers.participants.map { if (it.heroId == "wasp") it.copy(heroId = null) else it },
        )
        val m = rematch(RematchKind.SWAP_HEROES, unknown)
        assertThat(m.team(Team.RED).map { it.heroId }).containsExactly(null, "xargatha").inOrder()
    }
}
