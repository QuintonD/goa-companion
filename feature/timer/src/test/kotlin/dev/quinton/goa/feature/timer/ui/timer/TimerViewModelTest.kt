package dev.quinton.goa.feature.timer.ui.timer

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.timer.FakeCuePlayer
import dev.quinton.goa.feature.timer.MainDispatcherRule
import dev.quinton.goa.feature.timer.ManualTicker
import dev.quinton.goa.feature.timer.TimerHarness
import dev.quinton.goa.feature.timer.session.TimerCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TimerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = TimerHarness()
    private val matches = h.matches
    private val players = FakePlayerRepository(
        listOf(
            Player("p1", "Ana", 0xFF3FC1B0, 0, 0),
            Player("p2", "Ben", 0xFFC9A35B, 1, 0),
        ),
    )
    private val settings = FakeSettingsRepository()
    private val cues = FakeCuePlayer()
    private val ticker = ManualTicker()

    private fun vm() = TimerViewModel(
        controller = h.controller,
        repository = h.repository,
        matches = matches,
        catalogRepository = FakeCatalogRepository(),
        players = players,
        settingsRepository = settings,
        cues = cues,
        ticker = ticker,
    )

    private suspend fun TimerViewModel.awaitActive(): ActiveUiState = uiState.first { it.active != null }.active!!

    @Test
    fun `start screen lists bundled presets with table rules selected`() = runTest {
        val vm = vm()
        val start = vm.uiState.first { it.start != null }.start!!

        assertThat(
            start.presets.map {
                it.name
            },
        ).containsExactly("Table rules", "Speed guards", "Relaxed", "Untimed (counters only)").inOrder()
        assertThat(start.selectedPresetId).isEqualTo("table-rules")
        // Every hero resolving gets their own 0:30, so the start card asks how many heroes play.
        assertThat(start.selected!!.usesActionTimer).isTrue()
        assertThat(start.pendingMatches).isEmpty()
    }

    @Test
    fun `pending matches are offered for setup`() = runTest {
        matches.upsert(
            Match(
                "m1",
                createdAt = 1,
                playedAt = 1,
                status = MatchStatus.PENDING,
                mode = GameMode.LONG,
                participants = listOf(
                    Participant("p1", "test-hero-1", Team.RED),
                    Participant("p2", "test-hero-2", Team.BLUE),
                ),
            ),
        )
        matches.upsert(Match("m2", 2, 2, MatchStatus.COMPLETED, GameMode.QUICK, participants = emptyList()))
        val start = vm().uiState.first { it.start?.pendingMatches?.isNotEmpty() == true }.start!!

        assertThat(start.pendingMatches.map { it.id }).containsExactly("m1")
        assertThat(start.pendingMatches.single().title).isEqualTo("Long · 2 heroes")
    }

    @Test
    fun `starting shows round 1 strategy with the full countdown`() = runTest {
        val vm = vm()
        vm.start()
        val active = vm.awaitActive()

        assertThat(active.round).isEqualTo(1)
        assertThat(active.title).isEqualTo("Pre-round strategy")
        assertThat(active.clock).isEqualTo("1:30")
        assertThat(active.section).isEqualTo(RoundSection.ROUND_START)
        assertThat(active.nextLabel).isEqualTo("Next: Turn 1")
        assertThat(active.paused).isFalse()
    }

    @Test
    fun `ticks refresh the clock and fire cues once, respecting settings`() = runTest {
        settings.state.value = AppSettings(hapticsEnabled = false, soundsEnabled = true)
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        vm.uiState.test {
            skipItems(1)
            h.clocks.advance(79_900)
            ticker.tick()
            h.clocks.advance(200)
            ticker.tick()
            val state = expectMostRecentItem()
            assertThat(state.active!!.clock).isEqualTo("0:10")
            assertThat(state.active!!.warning).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(cues.cues).containsExactly(Triple(10, false, true))
    }

    @Test
    fun `hidden timer plays no cues`() = runTest {
        val vm = vm()
        vm.start()
        vm.awaitActive()
        h.clocks.advance(79_900)
        ticker.tick()
        h.clocks.advance(200)
        ticker.tick()
        assertThat(cues.cues).isEmpty()
    }

    @Test
    fun `overtime counts up`() = runTest {
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        vm.awaitActive()
        h.clocks.advance(95_000)
        ticker.tick()
        val active = vm.uiState.first { it.active?.overtime == true }.active!!

        assertThat(active.clock).isEqualTo("+0:05")
        assertThat(active.spokenClock).isEqualTo("Overtime, 5 seconds")
    }

    /** A user Next, a moment after the step started (a Next within 0.5 s is a double tap). */
    private suspend fun next() {
        h.clocks.advance(1_000)
        h.controller.perform(TimerCommand.Next)
    }

    @Test
    fun `next, back and skip to end of round`() = runTest {
        val vm = vm()
        vm.start()
        vm.awaitActive()
        h.clocks.advance(1_000)
        vm.next()
        assertThat(vm.awaitActive().pips.currentTurn).isEqualTo(1)
        vm.back()
        assertThat(vm.uiState.first { it.active?.section == RoundSection.ROUND_START }.active).isNotNull()
        vm.skipToEndOfRound()
        val end = vm.uiState.first { it.active?.section == RoundSection.ROUND_END }.active!!
        assertThat(end.checklist!!.map { it.state }).containsExactly(
            ChecklistState.CURRENT,
            ChecklistState.UPCOMING,
            ChecklistState.UPCOMING,
            ChecklistState.UPCOMING,
            ChecklistState.UPCOMING,
            ChecklistState.UPCOMING,
        ).inOrder()
        assertThat(end.checklist!![4].timerName).isEqualTo("Upgrade 2:00")
        assertThat(end.timed).isFalse()
    }

    @Test
    fun `the round view follows the saved layout and the header switch saves it`() = runTest {
        val vm = vm()
        vm.start()
        val steps = vm.awaitActive()
        assertThat(steps.layout).isEqualTo(TimerLayout.STEPS)
        assertThat(steps.rows).isEmpty()

        vm.setTimerLayout(TimerLayout.ROUND)
        assertThat(settings.state.value.timerLayout).isEqualTo(TimerLayout.ROUND)
        val round = vm.uiState.first { it.active?.layout == TimerLayout.ROUND }.active!!
        // Table rules: strategy, 4 turns of play + one resolve per hero (4), the 6 end-of-round steps.
        assertThat(round.rows).hasSize(27)
        assertThat(round.rows.map { it.group }.distinct())
            .containsExactly("Round start", "Turn 1", "Turn 2", "Turn 3", "Turn 4", "End of round").inOrder()
        assertThat(round.rows.first().state).isEqualTo(RoundRowState.CURRENT)
        assertThat(round.rows.drop(1).map { it.state }.toSet()).containsExactly(RoundRowState.UPCOMING)
        assertThat(round.rows.map { it.length }.take(3)).containsExactly("1:30", "0:30", "0:30").inOrder()
    }

    @Test
    fun `jumping from the round view moves there, back undoes it, a stale round is ignored`() = runTest {
        settings.state.value = AppSettings(timerLayout = TimerLayout.ROUND)
        val vm = vm()
        vm.start()
        vm.awaitActive()
        h.clocks.advance(20_000)
        vm.jumpTo(round = 1, stepIndex = 25) // Upgrade on Level Up
        val upgrade = vm.uiState.first { it.active?.stepIndex == 25 }.active!!
        assertThat(upgrade.kind).isEqualTo(PhaseKind.UPGRADE)
        assertThat(upgrade.clock).isEqualTo("2:00")
        assertThat(upgrade.rows.take(25).map { it.state }.toSet()).containsExactly(RoundRowState.DONE)
        assertThat(upgrade.rows[25].state).isEqualTo(RoundRowState.CURRENT)

        h.clocks.advance(2_000)
        vm.back()
        val back = vm.uiState.first { it.active?.stepIndex == 0 }.active!!
        // The strategy phase picks up where it was: 1:30 - 20 s - 2 s.
        assertThat(back.clock).isEqualTo("1:08")

        // A confirm for an earlier round's row, landing after the round moved on, does nothing.
        vm.jumpTo(round = 2, stepIndex = 5)
        h.clocks.advance(1_000)
        vm.next()
        assertThat(h.controller.snapshot.first()!!.stepIndex).isEqualTo(1)
    }

    @Test
    fun `level up step runs the upgrade timer`() = runTest {
        val vm = vm()
        vm.start()
        vm.skipToEndOfRound()
        repeat(4) { next() }
        val active = vm.uiState.first { it.active?.kind == PhaseKind.UPGRADE }.active!!

        assertThat(active.title).isEqualTo("Upgrade · Level Up")
        assertThat(active.clock).isEqualTo("2:00")
        assertThat(active.checklist!![4].state).isEqualTo(ChecklistState.CURRENT)
        assertThat(active.context).isEqualTo("End of round · step 5 of 6")
        // Rules F3: the mandatory Level Up text shows under the dial on this timed step.
        assertThat(active.timed).isTrue()
        assertThat(active.currentDetail).isEqualTo(active.checklist!![4].detail)
        assertThat(active.currentDetail).isNotEmpty()
    }

    @Test
    fun `each turn is play a card, then every hero resolves on their own clock`() = runTest {
        val vm = vm()
        vm.start()
        vm.awaitActive()
        next()
        val turn = vm.uiState.first { it.active?.kind == PhaseKind.TURN }.active!!
        assertThat(turn.title).isEqualTo("Play a card")
        assertThat(turn.hint).isEqualTo("Strategy talk out loud until the reveal.")
        assertThat(turn.nextLabel).isEqualTo("Cards revealed")

        next()
        val first = vm.uiState.first { it.active?.kind == PhaseKind.ACTION }.active!!
        assertThat(first.timed).isTrue()
        assertThat(first.clock).isEqualTo("0:30")
        assertThat(first.context).isEqualTo("Turn 1 of 4 · resolution 1 of 4")
        assertThat(first.hint).startsWith("The highest initiative not yet resolved goes next.")
        assertThat(first.nextLabel).isEqualTo("Next hero")

        repeat(3) { next() }
        val last = vm.uiState.first { it.active?.actionSlot == 3 }.active!!
        assertThat(last.clock).isEqualTo("0:30")
        assertThat(last.context).isEqualTo("Turn 1 of 4 · resolution 4 of 4")
        assertThat(last.nextLabel).isEqualTo("Next: Turn 2")
    }

    @Test
    fun `hurry up counts down beside the phase timer and cues`() = runTest {
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        vm.awaitActive()
        h.clocks.advance(120_000) // overtime
        vm.startShotClock()
        val shot = vm.uiState.first { it.active?.shotClock != null }.active!!
        assertThat(shot.shotClock!!.clock).isEqualTo("0:30")
        assertThat(shot.overtime).isTrue()

        h.clocks.advance(19_900)
        ticker.tick()
        h.clocks.advance(200)
        ticker.tick()
        assertThat(cues.cues.map { it.first }).containsExactly(10)
        h.clocks.advance(9_800)
        ticker.tick()
        h.clocks.advance(200)
        ticker.tick()
        val over = vm.uiState.first { it.active?.shotClock?.over == true }.active!!.shotClock!!
        assertThat(over.clock).isEqualTo("0:00")
        assertThat(cues.cues.map { it.first }).containsExactly(10, 0).inOrder()

        vm.cancelShotClock()
        assertThat(vm.uiState.first { it.active?.shotClock == null }.active!!.shotClockSeconds).isEqualTo(30)
    }

    @Test
    fun `tonight's turn length is one tap from the start card`() = runTest {
        val vm = vm()
        assertThat(vm.uiState.first { it.start != null }.start!!.tonight).isEqualTo(TonightUi(30, 30))
        vm.setTonightTurn(45)
        val adjusted = vm.uiState.first { it.start?.tonight?.adjusted == true }.start!!.tonight!!
        assertThat(adjusted.seconds).isEqualTo(45)
        vm.start()
        vm.awaitActive()
        assertThat(h.controller.snapshot.first()!!.preset.phases.single { it.kind == PhaseKind.TURN }.seconds)
            .isEqualTo(45)
        assertThat(h.repository.selectedPreset().phases.single { it.kind == PhaseKind.TURN }.seconds).isEqualTo(30)
    }

    @Test
    fun `toggle pause taps haptics`() = runTest {
        val vm = vm()
        vm.start()
        vm.awaitActive()
        vm.togglePause()
        assertThat(vm.uiState.first { it.active?.paused == true }.active!!.paused).isTrue()
        assertThat(cues.taps).isEqualTo(1)
    }

    @Test
    fun `ending a linked match records its duration and log result opens it`() = runTest {
        matches.upsert(Match("m1", 1, 1, MatchStatus.PENDING, GameMode.QUICK, participants = emptyList()))
        val startedAt = h.controller.start(h.repository.selectedPreset(), 4, matchId = "m1", mode = GameMode.LONG)
            .matchStartWall
        val vm = vm()
        vm.awaitActive()
        h.clocks.advance(47 * 60_000L + 10_000)
        vm.endMatch()
        val summary = vm.uiState.first { it.summary != null }.summary!!
        assertThat(summary.duration).isEqualTo("47 min")
        assertThat(summary.hasMatch).isTrue()
        assertThat(matches.state.value.single().durationMin).isEqualTo(47)
        assertThat(matches.state.value.single().mode).isEqualTo(GameMode.LONG)

        vm.navigation.test {
            vm.logResult()
            assertThat(awaitItem()).isEqualTo(TimerEvent.OpenMatch("m1", startedAt))
        }
        assertThat(h.controller.snapshot.first()).isNull()
    }

    @Test
    fun `log result without a match opens a new prefilled match and saves nothing`() = runTest {
        val vm = vm()
        vm.start()
        vm.awaitActive()
        h.clocks.advance(90 * 60_000L)
        vm.endMatch()
        vm.uiState.first { it.summary != null }
        lateinit var opened: TimerEvent.NewMatch
        vm.navigation.test {
            vm.logResult()
            vm.logResult() // double tap
            opened = awaitItem() as TimerEvent.NewMatch
            assertThat(opened.copy(startedAt = null, matchId = null))
                .isEqualTo(TimerEvent.NewMatch(durationMin = 90, mode = null))
            expectNoEvents()
        }
        assertThat(matches.state.value).isEmpty()
        // The finished session stays (backing out of the result screen loses nothing) …
        val ended = h.controller.snapshot.first()!!
        assertThat(ended.loggingMatchId).isEqualTo(opened.matchId)
        assertThat(vm.uiState.first().summary).isNotNull()
        // … and back on the timer, Log result again opens the same new match.
        vm.setVisible(false)
        vm.setVisible(true)
        vm.navigation.test {
            vm.logResult()
            assertThat((awaitItem() as TimerEvent.NewMatch).matchId).isEqualTo(opened.matchId)
        }

        // Saving that match clears the session.
        val saved = Match(opened.matchId!!, 1, 1, MatchStatus.COMPLETED, GameMode.QUICK, participants = emptyList())
        matches.upsert(saved)
        h.controller.snapshot.first { it == null }
    }

    @Test
    fun `log result passes the session mode only when legal for its hero count`() = runTest {
        h.controller.start(h.repository.selectedPreset(), 4, mode = GameMode.LONG)
        h.clocks.advance(30 * 60_000L)
        h.controller.perform(TimerCommand.EndMatch)
        val vm = vm()
        vm.uiState.first { it.summary != null }
        vm.navigation.test {
            vm.logResult()
            assertThat((awaitItem() as TimerEvent.NewMatch).copy(startedAt = null, matchId = null))
                .isEqualTo(TimerEvent.NewMatch(durationMin = 30, mode = "long"))
        }

        // Double lane needs 6+ heroes: not passed for a 4-hero session.
        h.controller.start(h.repository.selectedPreset(), 4, mode = GameMode.DOUBLE_LANE)
        h.controller.perform(TimerCommand.EndMatch)
        val vm2 = vm()
        vm2.uiState.first { it.summary != null }
        vm2.navigation.test {
            vm2.logResult()
            assertThat((awaitItem() as TimerEvent.NewMatch).copy(startedAt = null, matchId = null))
                .isEqualTo(TimerEvent.NewMatch(durationMin = 1, mode = null))
        }
    }

    @Test
    fun `alerts-off banner shows while notifications are blocked until dismissed`() = runTest {
        h.notifier.enabled = false
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        assertThat(vm.uiState.first { it.active != null }.active!!.alertsOff).isTrue()

        vm.dismissAlertsBanner()
        assertThat(vm.uiState.first { it.active?.alertsOff == false }.active!!.alertsOff).isFalse()
    }

    @Test
    fun `alerts-off banner hides once notifications are allowed again`() = runTest {
        h.notifier.enabled = false
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        assertThat(vm.uiState.first { it.active != null }.active!!.alertsOff).isTrue()

        h.notifier.enabled = true
        vm.setVisible(false)
        vm.setVisible(true) // back from the notification settings
        assertThat(vm.uiState.first { it.active?.alertsOff == false }.active!!.alertsOff).isFalse()
    }

    @Test
    fun `no banner when notifications are allowed`() = runTest {
        val vm = vm()
        vm.setVisible(true)
        vm.start()
        assertThat(vm.awaitActive().alertsOff).isFalse()
    }

    @Test
    fun `action timer lists the linked match heroes and records who resolves`() = runTest {
        matches.upsert(
            Match(
                "m1",
                1,
                1,
                MatchStatus.PENDING,
                GameMode.QUICK,
                participants = listOf(
                    Participant("p1", TestCatalog.heroes[0].id, Team.RED),
                    Participant("p2", TestCatalog.heroes[1].id, Team.BLUE),
                ),
            ),
        )
        val preset = h.repository.selectedPreset().let { p ->
            p.copy(phases = p.phases.map { if (it.kind == PhaseKind.ACTION) it.copy(enabled = true) else it })
        }
        h.controller.start(preset, 2, matchId = "m1")
        next() // turn 1
        next() // action slot 0 (the Action steps replace Resolve)
        val vm = vm()
        val active = vm.uiState.first { it.active?.seats != null }.active!!
        assertThat(active.seats!!.map { it.heroName }).containsExactly("Test Hero 1", "Test Hero 2").inOrder()
        assertThat(active.seats!!.map { it.playerName }).containsExactly("Ana", "Ben").inOrder()
        assertThat(active.title).isEqualTo("Action")

        vm.heroResolves(1)
        val acting = vm.uiState.first { s -> s.active?.seats?.any { it.acting } == true }.active!!
        assertThat(acting.seats!!.single { it.acting }.seat).isEqualTo(1)
        // The title names who is acting.
        assertThat(acting.title).isEqualTo("Test Hero 2 · Ben")
        next()
        // Last slot: the one hero left is picked automatically.
        val last = vm.uiState.first { it.active?.actionSlot == 1 }.active!!
        assertThat(last.seats!![1].resolved).isTrue()
        assertThat(last.seats!![0].acting).isTrue()
        assertThat(last.title).isEqualTo("Test Hero 1 · Ana")

        // The round view names who resolved in each slot of this turn.
        vm.setTimerLayout(TimerLayout.ROUND)
        val rows = vm.uiState.first { it.active?.rows?.isNotEmpty() == true }.active!!.rows
        assertThat(rows[2].context).isEqualTo("Hero 1 of 2 · Test Hero 2 · Ben")
        assertThat(rows[3].context).isEqualTo("Hero 2 of 2 · Test Hero 1 · Ana")
        assertThat(rows[3].description("30 seconds left"))
            .isEqualTo("Turn 1, Action, Hero 2 of 2, Test Hero 1 · Ana, current, 30 seconds left")
        assertThat(rows[5].context).isEqualTo("Hero 1 of 2")
    }

    @Test
    fun `the coin side is asked for, never assumed`() = runTest {
        h.controller.start(h.repository.selectedPreset(), 4)
        val vm = vm()
        assertThat(vm.awaitActive().tieBreaker).isNull()
        vm.flipTieBreaker()
        assertThat(h.controller.snapshot.first()!!.tieBreaker).isNull()
        vm.setTieBreaker(Team.BLUE)
        assertThat(vm.uiState.first { it.active?.tieBreaker == Team.BLUE }.active!!.tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun `seat count drives the action timer when no match is linked`() = runTest {
        val vm = vm()
        vm.setSeats(6)
        vm.setSeats(42)
        assertThat(vm.uiState.first { it.start?.seats == 10 }.start!!.seats).isEqualTo(10)
        vm.start()
        vm.awaitActive()
        assertThat(h.controller.snapshot.first()!!.actionSlots).isEqualTo(10)
    }

    @Test
    fun `auto advance happens on tick when visible`() = runTest {
        val preset = h.repository.selectedPreset().copy(autoAdvance = true)
        h.controller.start(preset, 4)
        val vm = vm()
        vm.setVisible(true)
        vm.awaitActive()
        h.clocks.advance(91_000)
        ticker.tick()
        val active = vm.uiState.first { it.active?.section == RoundSection.TURN }.active!!
        assertThat(active.clock).isEqualTo("0:29")
    }

    @Test
    fun `flip coin toggles the tie breaker side`() = runTest {
        h.controller.start(h.repository.selectedPreset(), 4, tieBreaker = Team.RED)
        val vm = vm()
        assertThat(vm.awaitActive().tieBreaker).isEqualTo(Team.RED)
        vm.flipTieBreaker()
        assertThat(vm.uiState.first { it.active?.tieBreaker == Team.BLUE }.active!!.tieBreaker).isEqualTo(Team.BLUE)
    }
}
