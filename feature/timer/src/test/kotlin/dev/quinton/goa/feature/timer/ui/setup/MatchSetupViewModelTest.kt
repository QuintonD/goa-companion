package dev.quinton.goa.feature.timer.ui.setup

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.FixedCoin
import dev.quinton.goa.feature.timer.MainDispatcherRule
import dev.quinton.goa.feature.timer.TimerHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MatchSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = TimerHarness()
    private val matches = h.matches
    private val coin = FixedCoin(Team.BLUE)

    private fun vm(matchId: String? = null) = MatchSetupViewModel(matchId, h.repository, h.controller, matches, coin)

    private suspend fun MatchSetupViewModel.loaded() = uiState.first { !it.loading }

    @Test
    fun `defaults to four players, quick, with the rulebook counters`() = runTest {
        val s = vm().loaded()

        assertThat(s.players).isEqualTo(4)
        assertThat(s.recommended).isTrue()
        assertThat(s.extendedNote).isNull()
        assertThat(s.selectedMode).isEqualTo(GameMode.QUICK)
        assertThat(s.counters!!.wavesPerLane).isEqualTo(3)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(4)
        assertThat(s.modes.single { it.mode == GameMode.DOUBLE_LANE }.available).isFalse()
        assertThat(s.modes.single { it.mode == GameMode.DOUBLE_LANE }.reason).isEqualTo("Needs 6 or more heroes")
        assertThat(s.firstGameAdvice).contains("Quick")
    }

    @Test
    fun `five players offer handicap or double pilot with different counters`() = runTest {
        val vm = vm()
        vm.setPlayers(5)
        val handicap = vm.uiState.first { it.players == 5 }
        assertThat(
            handicap.layouts.map {
                it.kind
            },
        ).containsExactly(LayoutKind.HANDICAP, LayoutKind.DOUBLE_PILOT).inOrder()
        assertThat(handicap.layouts.map { it.official }).containsExactly(true, false).inOrder()
        assertThat(handicap.extendedNote).isNotNull()
        assertThat(handicap.counters!!.lifePerTeam).isEqualTo(4)
        assertThat(handicap.counters!!.handicapHeroes).isEqualTo(3)

        vm.selectLayout("double-pilot-5")
        val double = vm.uiState.first { it.selectedLayoutId == "double-pilot-5" }
        assertThat(double.counters!!.lifePerTeam).isEqualTo(5)
        assertThat(double.counters!!.doublePilots).isEqualTo(1)
        assertThat(double.modes.single { it.mode == GameMode.DOUBLE_LANE }.available).isTrue()
    }

    @Test
    fun `eight players force double lane`() = runTest {
        val vm = vm()
        vm.setPlayers(8)
        vm.selectMode(GameMode.QUICK)
        val s = vm.uiState.first { it.players == 8 }

        assertThat(s.selectedMode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(s.modes.single { it.mode == GameMode.DOUBLE_LANE }.required).isTrue()
        assertThat(
            s.modes.single {
                it.mode == GameMode.QUICK
            }.reason,
        ).isEqualTo("Double lane is required at this size")
        assertThat(s.counters!!.lanes).isEqualTo(2)
        assertThat(s.counters!!.wavesPerLane).isEqualTo(7)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(6)
    }

    @Test
    fun `seven players single lane shows life counters as not specified`() = runTest {
        val vm = vm()
        vm.setPlayers(7)
        vm.selectMode(GameMode.LONG)
        val s = vm.uiState.first { it.players == 7 && it.selectedMode == GameMode.LONG }

        assertThat(s.counters!!.lifePerTeam).isNull()
        assertThat(
            s.modes.single {
                it.mode == GameMode.LONG
            }.life,
        ).isEqualTo("Life counters not specified by the rulebook")
    }

    @Test
    fun `seven players with the official handicap layout default to double lane with life counters`() = runTest {
        val vm = vm()
        vm.setPlayers(7)
        val s = vm.uiState.first { it.players == 7 }

        assertThat(s.selectedLayoutId).isEqualTo("handicap-7")
        assertThat(s.selectedMode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(6)
    }

    @Test
    fun `a drafted seven-hero match stored as quick opens on double lane`() = runTest {
        val seats = (0 until 7).map { i -> Participant("p$i", "h$i", if (i < 4) Team.RED else Team.BLUE) }
        matches.upsert(Match("m7", 1, 1, MatchStatus.PENDING, GameMode.QUICK, participants = seats))
        val s = vm("m7").loaded()

        assertThat(s.selectedMode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(6)
    }

    @Test
    fun `coin flip shows the side and counts flips for the animation`() = runTest {
        val vm = vm()
        vm.loaded()
        vm.flipCoin()
        val s = vm.uiState.first { it.flips == 1 }
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(s.tieBreakerRule).contains("Tie Breaker")
    }

    @Test
    fun `linked match locks seats, updates the mode and starts a linked timer`() = runTest {
        matches.upsert(
            Match(
                "m1",
                1,
                1,
                MatchStatus.PENDING,
                GameMode.QUICK,
                participants = listOf(
                    Participant("a", "h1", Team.RED),
                    Participant("b", "h2", Team.RED),
                    Participant("c", "h3", Team.RED),
                    Participant("d", "h4", Team.BLUE),
                    Participant("e", "h5", Team.BLUE),
                    Participant("e", "h6", Team.BLUE),
                ),
            ),
        )
        val vm = vm("m1")
        val s = vm.loaded()
        assertThat(s.playersLocked).isTrue()
        assertThat(s.players).isEqualTo(5)
        assertThat(s.selectedLayoutId).isEqualTo("double-pilot-5")
        assertThat(s.counters!!.redHeroes).isEqualTo(3)
        assertThat(s.counters!!.lifePerTeam).isEqualTo(5)

        vm.selectMode(GameMode.LONG)
        vm.flipCoin()
        vm.uiState.first { it.selectedMode == GameMode.LONG && it.tieBreaker != null }
        vm.navigation.test {
            vm.startTimer()
            assertThat(awaitItem()).isEqualTo(SetupEvent.TimerStarted)
        }
        assertThat(matches.state.value.single().mode).isEqualTo(GameMode.LONG)
        val session = h.controller.snapshot.first()!!
        assertThat(session.matchId).isEqualTo("m1")
        assertThat(session.actionSlots).isEqualTo(6)
        assertThat(session.mode).isEqualTo(GameMode.LONG)
        assertThat(session.tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun `starting with a chosen preset uses it`() = runTest {
        val vm = vm()
        vm.loaded()
        vm.selectPreset("relaxed")
        vm.uiState.first { it.selectedPresetId == "relaxed" }
        vm.startTimer()
        assertThat(h.controller.snapshot.first { it != null }!!.preset.id).isEqualTo("relaxed")
    }
}
