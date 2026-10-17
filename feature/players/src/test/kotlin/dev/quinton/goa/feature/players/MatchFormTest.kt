package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.players.match.MatchError
import dev.quinton.goa.feature.players.match.MatchForm
import dev.quinton.goa.feature.players.match.MatchValidation
import dev.quinton.goa.feature.players.match.SeatDraft
import org.junit.Test

class MatchFormTest {
    private val valid = MatchForm(
        playedAt = 0,
        winner = Team.RED,
        red = listOf(SeatDraft(0, "a", "h1"), SeatDraft(1, "a", "h2", handicap = true)),
        blue = listOf(SeatDraft(2, "b", "h3"), SeatDraft(3, "c", null)),
    )

    @Test
    fun `double pilot with handicap and an unknown hero is valid`() {
        assertThat(MatchValidation.validate(valid)).isEmpty()
        assertThat(valid.doublePilots(Team.RED)).containsExactly("a")
        assertThat(valid.doublePilots(Team.BLUE)).isEmpty()
    }

    @Test
    fun `each rule is reported`() {
        assertThat(MatchValidation.validate(valid.copy(red = emptyList()))).contains(MatchError.EmptyTeam(Team.RED))
        assertThat(MatchValidation.validate(valid.copy(blue = emptyList()))).contains(MatchError.EmptyTeam(Team.BLUE))
        assertThat(MatchValidation.validate(valid.withSeats(Team.BLUE, listOf(SeatDraft(9, null, "h4")))))
            .contains(MatchError.MissingPilot)
        assertThat(MatchValidation.validate(valid.withSeats(Team.BLUE, listOf(SeatDraft(9, "b", "h1")))))
            .containsExactly(MatchError.DuplicateHero("h1", "h1"))
        assertThat(MatchValidation.validate(valid.withSeats(Team.BLUE, listOf(SeatDraft(9, "a", "h4")))))
            .containsExactly(MatchError.BothTeams("a", "a"))
        assertThat(MatchValidation.validate(valid.copy(winner = null))).containsExactly(MatchError.WinnerRequired)
        assertThat(MatchValidation.validate(valid.copy(duration = "0"))).containsExactly(MatchError.InvalidDuration)
        assertThat(MatchValidation.validate(valid.copy(duration = "999"))).containsExactly(MatchError.InvalidDuration)
    }

    @Test
    fun `pending and abandoned matches need no winner and drop it on save`() {
        for (status in listOf(MatchStatus.PENDING, MatchStatus.ABANDONED)) {
            val form = valid.copy(status = status)
            assertThat(MatchValidation.validate(form)).isEmpty()
            val match = form.toMatch(id = "m", createdAt = 1, draftId = "d")
            assertThat(match.winner).isNull()
            assertThat(match.winCondition).isNull()
            assertThat(match.draftId).isEqualTo("d")
        }
    }

    @Test
    fun `round trip through Match keeps seat order and flags`() {
        val match = valid.copy(map = "  Double lane ", notes = " gg ", duration = "40").toMatch("m", 5, null)
        assertThat(match.map).isEqualTo("Double lane")
        assertThat(match.notes).isEqualTo("gg")
        assertThat(match.durationMin).isEqualTo(40)
        var key = 0L
        val back = MatchForm.from(match) { key++ }
        assertThat(back.red.map { it.playerId to it.handicap }).containsExactly("a" to false, "a" to true).inOrder()
        assertThat(back.blue.map { it.heroId }).containsExactly("h3", null).inOrder()
        assertThat(back.toMatch("m", 5, null)).isEqualTo(match)
    }
}
