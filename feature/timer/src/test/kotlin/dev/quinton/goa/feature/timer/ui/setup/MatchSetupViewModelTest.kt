package dev.quinton.goa.feature.timer.ui.setup

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.timer.CoinSource
import dev.quinton.goa.core.presentation.timer.RematchRequest
import dev.quinton.goa.feature.timer.FixedCoin
import dev.quinton.goa.feature.timer.MainDispatcherRule
import dev.quinton.goa.feature.timer.TimerHarness
import dev.quinton.goa.feature.timer.bundledAsset
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
        assertThat(
            s.modes.single { it.mode == GameMode.DOUBLE_LANE }.reason,
        ).isEqualTo("The rulebook lists Double lane counters from 6 players")
        assertThat(s.firstGameAdvice).contains("Quick")
        assertThat(s.counters!!.houseRuleNote).isNull()
        assertThat(s.counters!!.modeNote).isNull()
        assertThat(s.coinSource).isEqualTo(CoinSource.NONE)
        assertThat(s.coinPlaced).isFalse()
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
        // Rulebook p.20: 5 players use 4-player Life counters; the house count is labelled.
        assertThat(handicap.counters!!.houseRuleNote).isNull()
        assertThat(double.counters!!.houseRuleNote).isEqualTo(
            "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) Life counters.",
        )

        vm.selectMode(GameMode.DOUBLE_LANE)
        val lanes = vm.uiState.first { it.selectedMode == GameMode.DOUBLE_LANE }
        assertThat(lanes.counters!!.houseRuleNote).isEqualTo(
            "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) Life counters. " +
                "The rulebook lists Double lane counters from 6 players.",
        )
        assertThat(lanes.counters!!.modeNote!!.title).contains("p.20")
    }

    @Test
    fun `seven players with a double pilot label double lane as a house-rule requirement`() = runTest {
        val vm = vm()
        vm.setPlayers(7)
        vm.selectLayout("double-pilot-7")
        val s = vm.uiState.first { it.selectedLayoutId == "double-pilot-7" }

        val double = s.modes.single { it.mode == GameMode.DOUBLE_LANE }
        assertThat(double.required).isTrue()
        assertThat(double.requiredLabel).isEqualTo("Required (house rule)")
        assertThat(s.modes.single { it.mode == GameMode.QUICK }.reason).isEqualTo(
            "House rule: 8 heroes play on the double-lane map. The rulebook requires it from 8 players",
        )
        // Same Life counters either way (6-8 players: 6), so no counter note.
        assertThat(s.counters!!.houseRuleNote).isNull()
        assertThat(s.firstGameAdvice).isEmpty()
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
        ).isEqualTo("The rulebook uses the double-lane map from 8 players")
        assertThat(s.modes.single { it.mode == GameMode.DOUBLE_LANE }.requiredLabel).isEqualTo("Required")
        // "Quick game if anyone is new" is not shown when Quick can't be chosen.
        assertThat(s.firstGameAdvice).isEmpty()
        assertThat(s.counters!!.houseRuleNote).isNull()
        assertThat(s.counters!!.modeNote!!.points.joinToString()).contains("a Battle Zone")
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
        assertThat(s.coinSource).isEqualTo(CoinSource.HERE)
        assertThat(s.tieBreakerRule.first()).contains("that player (and only that player) flips the coin")
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
        // A coin flipped at setup is recorded on the linked match.
        assertThat(matches.state.value.single().tieBreaker).isEqualTo(Team.BLUE)
    }

    /** Game night: every rematch option saved a pending match at once; six piled up in one evening. */
    @Test
    fun `a rematch is saved only when its timer starts`() = runTest {
        val source = Match(
            "g1",
            1,
            1,
            MatchStatus.COMPLETED,
            GameMode.QUICK,
            winner = Team.RED,
            tieBreaker = Team.RED,
            participants = listOf(
                Participant("a", "h1", Team.RED),
                Participant("b", "h2", Team.RED),
                Participant("c", "h3", Team.BLUE),
                Participant("d", "h4", Team.BLUE),
            ),
        )
        matches.upsert(source)
        val vm = MatchSetupViewModel(
            null,
            h.repository,
            h.controller,
            matches,
            coin,
            RematchRequest("g1", RematchKind.SWAP_TEAMS),
        )
        val s = vm.loaded()
        assertThat(s.playersLocked).isTrue()
        assertThat(s.seatsSource).isEqualTo("Rematch: sides swapped")
        assertThat(s.linkedMatch!!.title).isEqualTo("Rematch · 4 heroes")
        // A rematch is a new game: the coin is placed again.
        assertThat(s.coinPlaced).isFalse()
        assertThat(matches.state.value.map { it.id }).containsExactly("g1")

        vm.flipCoin()
        vm.uiState.first { it.tieBreaker != null }
        vm.navigation.test {
            vm.startTimer()
            assertThat(awaitItem()).isEqualTo(SetupEvent.TimerStarted)
        }
        val rematch = matches.state.value.single { it.id != "g1" }
        assertThat(rematch.status).isEqualTo(MatchStatus.PENDING)
        assertThat(rematch.team(Team.RED).map { it.playerId }).containsExactly("c", "d")
        assertThat(rematch.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(h.controller.snapshot.first()!!.matchId).isEqualTo(rematch.id)
    }

    @Test
    fun `seats of a drafted match say so`() = runTest {
        assertThat(vm(draftedMatch(teamA = Team.RED)).loaded().seatsSource).isEqualTo("From the drafted match")
    }

    /** A finished draft with Team A = [teamA], turned into its pending match the way the draft board does. */
    private suspend fun draftedMatch(teamA: Team): String {
        val method = DraftJson.decodeMethods(bundledAsset("draft-methods.json")).methods.first { it.id == "all-random" }
        val seats =
            listOf(Seat(Team.RED, 0, "a"), Seat(Team.RED, 1, "b"), Seat(Team.BLUE, 0, "c"), Seat(Team.BLUE, 1, "d"))
        var session = DraftEngine.start(
            DraftSetup(
                id = "d1",
                createdAt = 1,
                method = method,
                seats = seats,
                teamA = teamA,
                candidates = (1..12).map { "hero-$it" },
                seed = 3,
            ),
        ).getOrThrow()
        while (!session.isComplete) {
            session = DraftEngine.apply(session, DraftCommand(DraftEngine.available(session).first())).getOrThrow()
        }
        matches.upsert(DraftEngine.toPendingMatch(session, "m-draft", now = 5).getOrThrow())
        return "m-draft"
    }

    @Test
    fun `a draft with Team A Blue gives setup and timer the Blue coin`() = runTest {
        val vm = vm(draftedMatch(teamA = Team.BLUE))
        val s = vm.loaded()
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(s.coinSource).isEqualTo(CoinSource.DRAFT)
        assertThat(s.coinPlaced).isTrue()
        assertThat(s.flips).isEqualTo(0)

        vm.navigation.test {
            vm.startTimer()
            assertThat(awaitItem()).isEqualTo(SetupEvent.TimerStarted)
        }
        assertThat(h.controller.snapshot.first()!!.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(matches.state.value.single().tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun `correcting the draft coin is saved to the match and used by the timer`() = runTest {
        val vm = vm(draftedMatch(teamA = Team.BLUE))
        vm.loaded()
        vm.correctCoin()
        vm.uiState.first { it.correctingCoin }
        vm.setCoin(Team.RED)
        val s = vm.uiState.first { it.tieBreaker == Team.RED }
        assertThat(s.coinSource).isEqualTo(CoinSource.HERE)
        assertThat(s.flips).isEqualTo(0)

        vm.navigation.test {
            vm.startTimer()
            assertThat(awaitItem()).isEqualTo(SetupEvent.TimerStarted)
        }
        assertThat(h.controller.snapshot.first()!!.tieBreaker).isEqualTo(Team.RED)
        assertThat(matches.state.value.single().tieBreaker).isEqualTo(Team.RED)
        // Back on setup for the same match, the corrected coin is the one recorded.
        val again = vm(matches.state.value.single().id).loaded()
        assertThat(again.tieBreaker).isEqualTo(Team.RED)
        assertThat(again.coinSource).isEqualTo(CoinSource.DRAFT)
    }

    /** Game night: re-tapping the side already showing said "Blue is showing (corrected)". */
    @Test
    fun `picking the side the placed coin already shows is not a correction`() = runTest {
        val vm = vm(draftedMatch(teamA = Team.BLUE))
        vm.loaded()
        vm.correctCoin()
        vm.setCoin(Team.RED)
        vm.uiState.first { it.coinSource == CoinSource.HERE }
        vm.setCoin(Team.BLUE)
        val s = vm.uiState.first { it.coinSource == CoinSource.DRAFT }
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
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
