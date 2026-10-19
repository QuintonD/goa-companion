package dev.quinton.goa.core.presentation.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MatchSetupBuilderTest {
    private val presets = TimerTestData.presets.presets.map(TimerUiMapper::presetOption)

    private fun build(
        choices: MatchSetupChoices = MatchSetupChoices(),
        match: Match? = null,
        rematch: RematchRequest? = null,
    ) = MatchSetupBuilder.build(TimerTestData.rules, match, choices, presets, "table-rules", rematch)

    @Test
    fun `defaults to four players, quick, with the rulebook counters`() {
        val s = build()

        assertThat(s.loading).isFalse()
        assertThat(s.players).isEqualTo(4)
        assertThat(s.selectedMode).isEqualTo(GameMode.QUICK)
        assertThat(s.counters!!.wavesPerLane).isEqualTo(3)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(4)
        assertThat(s.modes.single { it.mode == GameMode.DOUBLE_LANE }.reason)
            .isEqualTo("The rulebook lists Double lane counters from 6 players")
        assertThat(s.coinSource).isEqualTo(CoinSource.NONE)
        assertThat(s.presets.map { it.id }).contains("table-rules")
    }

    @Test
    fun `double pilot at five counts heroes in play and labels the house rule`() {
        val s = build(MatchSetupChoices(players = 5, layoutId = "double-pilot-5"))

        assertThat(s.layouts.map { it.kind }).containsExactly(LayoutKind.HANDICAP, LayoutKind.DOUBLE_PILOT).inOrder()
        assertThat(s.counters!!.lifePerTeam).isEqualTo(5)
        assertThat(s.counters!!.houseRuleNote).isEqualTo(
            "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) Life counters.",
        )
    }

    @Test
    fun `a drafted pending match locks the seats and keeps the draft's coin`() {
        val match = pending(draftId = "d1", tieBreaker = Team.RED)
        val s = build(match = match)

        assertThat(s.playersLocked).isTrue()
        assertThat(s.seatsSource).isEqualTo("From the drafted match")
        assertThat(s.linkedMatch!!.title).isEqualTo("Pending match · 4 heroes")
        assertThat(s.tieBreaker).isEqualTo(Team.RED)
        assertThat(s.coinSource).isEqualTo(CoinSource.DRAFT)
        assertThat(s.coinPlaced).isTrue()

        val corrected = build(MatchSetupChoices(tieBreaker = Team.BLUE), match = match)
        assertThat(corrected.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(corrected.coinSource).isEqualTo(CoinSource.HERE)
    }

    @Test
    fun `a rematch says so`() {
        val s = build(match = pending(), rematch = RematchRequest("m0", RematchKind.SWAP_TEAMS))

        assertThat(s.seatsSource).isEqualTo("Rematch: sides swapped")
        assertThat(s.linkedMatch!!.title).isEqualTo("Rematch · 4 heroes")
    }

    private fun pending(draftId: String? = null, tieBreaker: Team? = null) = Match(
        id = "m1",
        createdAt = 0,
        playedAt = 0,
        status = MatchStatus.PENDING,
        mode = GameMode.QUICK,
        winner = null,
        participants = listOf(
            Participant("a", "arien", Team.RED),
            Participant("b", "brogan", Team.RED),
            Participant("c", "xargatha", Team.BLUE),
            Participant("d", "wasp", Team.BLUE),
        ),
        draftId = draftId,
        tieBreaker = tieBreaker,
    )
}
