package dev.quinton.goa.core.presentation.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
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
    }

    @Test
    fun `pending and abandoned matches can be saved with no seats, completed ones cannot`() {
        val empty = valid.copy(red = emptyList(), blue = emptyList(), winner = null, notes = "rained off")
        for (status in listOf(MatchStatus.PENDING, MatchStatus.ABANDONED)) {
            assertThat(MatchValidation.validate(empty.copy(status = status))).isEmpty()
            val match = empty.copy(status = status).toMatch("m", 1, null)
            assertThat(match.participants).isEmpty()
            assertThat(match.notes).isEqualTo("rained off")
        }
        assertThat(MatchValidation.validate(empty)).containsExactly(
            MatchError.EmptyTeam(Team.RED),
            MatchError.EmptyTeam(Team.BLUE),
            MatchError.WinnerRequired,
        )
        // Integrity rules still apply whatever the status.
        val pendingTwice = valid.copy(status = MatchStatus.PENDING)
            .withSeats(Team.BLUE, listOf(SeatDraft(9, "b", "h1")))
        assertThat(MatchValidation.validate(pendingTwice)).containsExactly(MatchError.DuplicateHero("h1", "h1"))
    }

    @Test
    fun `durations up to 600 minutes save and out-of-range values never block saving`() {
        assertThat(MatchValidation.validate(valid.copy(duration = "600"))).isEmpty()
        assertThat(valid.copy(duration = "600").toMatch("m", 1, null).durationMin).isEqualTo(600)
        for (bad in listOf("0", "999", "abc")) {
            assertThat(MatchValidation.validate(valid.copy(duration = bad))).isEmpty()
        }
        assertThat(valid.copy(duration = "0").toMatch("m", 1, null).durationMin).isNull()
        assertThat(valid.copy(duration = "999").toMatch("m", 1, null).durationMin).isEqualTo(600)
        assertThat(MatchForm.durationText(720)).isEqualTo("600")
        assertThat(MatchForm.durationText(0)).isEmpty()
        assertThat(MatchForm.durationText(-3)).isEmpty()
        assertThat(MatchForm.durationText(null)).isEmpty()
        assertThat(MatchForm.durationText(45)).isEqualTo("45")
        var key = 0L
        val stored = valid.toMatch("m", 1, null).copy(durationMin = 900)
        assertThat(MatchForm.from(stored) { key++ }.duration).isEqualTo("600")
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
