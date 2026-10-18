package dev.quinton.goa.feature.draft

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.draft.board.BoardEvent
import dev.quinton.goa.feature.draft.board.DraftBoardViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DraftBoardViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val house = bundledMethods.methods.first { it.id == "house" }
    private val heroIds = TestCatalog.heroes.map { it.id }
    private val matches = FakeMatchRepository()
    private val records = FakeDraftRecordRepository()

    private fun session(method: DraftMethod = house, seconds: Int? = null): DraftSession {
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d"), OddSeatMode.HANDICAP).getOrThrow()
        return DraftEngine.start(DraftSetup("draft", 0L, method, seats, Team.RED, heroIds, 3L, seconds)).getOrThrow()
    }

    private fun TestScope.viewModel(drafts: FakeDraftRepository): DraftBoardViewModel {
        val vm =
            DraftBoardViewModel(
                drafts,
                FakeCatalogRepository(),
                FakePlayerRepository(
                    listOf("a" to "Ann", "b" to "Bo", "c" to "Cy", "d" to "Di").map { (id, name) ->
                        Player(id, name, 0xFF3FC1B0, 0, 0L)
                    },
                ),
                matches,
                records,
                FakeDraftEnvironment(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val FakeDraftRepository.board get() = (activeState.value as DraftProgress.Board).session

    @Test
    fun `tap then confirm commits a ban for the team on the clock`() = runTest {
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session()))
        val vm = viewModel(drafts)
        val hero = DraftEngine.available(drafts.board).first()
        vm.tapHero(hero)
        assertThat(vm.uiState.value.local.confirm).isEqualTo(hero)
        assertThat(drafts.board.history).isEmpty() // nothing until confirmed
        vm.confirm()
        assertThat(drafts.board.history.single().action).isEqualTo(DraftAction.BAN)
        assertThat(drafts.board.history.single().team).isEqualTo(Team.RED)
        assertThat(vm.uiState.value.local.confirm).isNull()
    }

    @Test
    fun `a confirm for a step that already moved on is not applied to the next step`() = runTest {
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session()))
        val vm = viewModel(drafts)
        val start = drafts.board
        val (first, second) = DraftEngine.available(start)
        vm.tapHero(second) // chosen for step 0 (Red's ban)…
        // …but step 0 is committed elsewhere first (another confirm, a resumed screen).
        drafts.activeState.value =
            DraftProgress.Board(DraftEngine.apply(start, DraftCommand(first)).getOrThrow())
        assertThat(drafts.board.currentStepIndex).isEqualTo(1)
        vm.confirm()
        // Step 1 (Red's pick) must not receive the hero meant as a ban.
        assertThat(drafts.board.history.map { it.heroId }).containsExactly(first)
        assertThat(vm.uiState.value.local.message).isEqualTo("The draft has moved on; choose again")
        assertThat(vm.uiState.value.local.confirm).isNull()
    }

    @Test
    fun `a hero concealed by a hidden step is refused with a neutral message`() = runTest {
        val blindBans = house.copy(
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
                repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
            ),
        )
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session(blindBans)))
        val vm = viewModel(drafts)
        val secret = drafts.board.pool.first()
        vm.acknowledgePrivacy()
        vm.tapHero(secret)
        vm.confirm()
        vm.acknowledgePrivacy()
        vm.tapHero(secret)
        vm.confirm()
        assertThat(drafts.board.history).hasSize(1)
        assertThat(vm.uiState.value.local.message).isEqualTo("Choose a different hero")
    }

    @Test
    fun `single draft seats cannot be rearranged`() = runTest {
        val single = bundledMethods.methods.first { it.id == "single-draft" }
        var s = session(single)
        while (!s.isComplete) s = DraftEngine.apply(s, DraftCommand(DraftEngine.available(s).first())).getOrThrow()
        val drafts = FakeDraftRepository(active = DraftProgress.Board(s))
        val vm = viewModel(drafts)
        vm.selectSeat("red-0")
        vm.selectSeat("red-1")
        assertThat(drafts.board).isEqualTo(s)
        assertThat(vm.uiState.value.local.message).isEqualTo("Each player plays the hero picked from their own hand")
    }

    @Test
    fun `cancel leaves the draft untouched and undo reverts the last step`() = runTest {
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session()))
        val vm = viewModel(drafts)
        val start = drafts.board
        vm.tapHero(start.pool.first())
        vm.cancelConfirm()
        assertThat(drafts.board).isEqualTo(start)
        vm.tapHero(start.pool.first())
        vm.confirm()
        vm.undo()
        assertThat(drafts.board).isEqualTo(start)
    }

    @Test
    fun `illegal choices surface a message`() = runTest {
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session()))
        val vm = viewModel(drafts)
        val hero = drafts.board.pool.first()
        vm.tapHero(hero)
        vm.confirm()
        vm.tapHero(hero)
        vm.confirm()
        assertThat(vm.uiState.value.local.message).isEqualTo("Test Hero ${hero.removePrefix("test-hero-")} is banned")
    }

    @Test
    fun `swapping a hero changes the pool before the first step`() = runTest {
        val full = FakeDraftRepository(active = DraftProgress.Board(session()))
        val fullVm = viewModel(full)
        fullVm.swapOut(full.board.pool.first()) // all 12 test heroes are already in the pool
        assertThat(fullVm.uiState.value.local.message).isEqualTo("Every eligible hero is already in the pool")

        val drafts = FakeDraftRepository(active = DraftProgress.Board(session(house.copy(pool = PoolRule.Random(8)))))
        val vm = viewModel(drafts)
        val before = drafts.board.pool
        vm.swapOut(before.first())
        assertThat(drafts.board.pool.first()).isNotEqualTo(before.first())
        assertThat(drafts.board.pool).hasSize(8)
        vm.reroll()
        assertThat(drafts.board.pool).isNotEqualTo(before)
    }

    @Test
    fun `finishing saves a pending match and the draft record, then clears the draft`() = runTest {
        var s = session()
        while (!s.isComplete) s = DraftEngine.apply(s, DraftCommand(DraftEngine.available(s).first())).getOrThrow()
        val drafts = FakeDraftRepository(active = DraftProgress.Board(s))
        val vm = viewModel(drafts)
        vm.boardEvents.test {
            vm.selectSeat("red-0")
            vm.selectSeat("red-1")
            val swapped = drafts.board
            vm.finish(openMatchSetup = true)
            val event = awaitItem() as BoardEvent.Finished
            assertThat(event.openMatchSetup).isTrue()
            val match = matches.state.value.single()
            assertThat(match.id).isEqualTo(event.matchId)
            assertThat(match.status).isEqualTo(MatchStatus.PENDING)
            assertThat(match.draftId).isEqualTo("draft")
            assertThat(match.participants.map { it.playerId }).containsExactly("a", "b", "c", "d")
            assertThat(match.participants.first { it.playerId == "a" }.heroId)
                .isEqualTo(DraftEngine.assignments(swapped).first { it.seat.id == "red-0" }.heroId)
            assertThat(records.state.value.single().matchId).isEqualTo(match.id)
            assertThat(drafts.activeState.value).isNull()
        }
    }

    @Test
    fun `hidden steps show the pass-the-phone gate until acknowledged`() = runTest {
        val blind = house.copy(
            sequence = SequenceRule.Pattern(repeat = listOf(pick(TeamSlot.A, true), pick(TeamSlot.B, true))),
        )
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session(blind)))
        val vm = viewModel(drafts)
        assertThat(vm.uiState.value.privacyGate).isTrue()
        vm.acknowledgePrivacy()
        assertThat(vm.uiState.value.privacyGate).isFalse()
        vm.tapHero(drafts.board.pool.first())
        vm.confirm()
        assertThat(vm.uiState.value.privacyGate).isTrue() // next team's hidden pick
    }

    @Test
    fun `player draft goes heroes then captains then becomes a complete board`() = runTest {
        val allPick = bundledMethods.methods.first { it.id == "all-pick" }
        val players = listOf("a", "b", "c", "d")
        val start = PlayerDraftSession.start("pd", 0, allPick, players, "a", "b", Team.BLUE, heroIds, 1).getOrThrow()
        val drafts = FakeDraftRepository(active = DraftProgress.Players(start))
        val vm = viewModel(drafts)
        players.forEachIndexed { i, p ->
            vm.chooseHeroFor(p)
            vm.assignHero(heroIds[i])
        }
        val phase = (drafts.activeState.value as DraftProgress.Players).session.phase
        assertThat(phase).isEqualTo(PlayerDraftPhase.CAPTAINS)
        vm.draftPlayer("c") // Blue (Team A) captain first; "d" is then the forced last pick
        assertThat(drafts.activeState.value).isInstanceOf(DraftProgress.Board::class.java)
        vm.undo() // takes back the forced pick and the pick that forced it
        assertThat((drafts.activeState.value as DraftProgress.Players).session.captains.remaining)
            .containsExactly("c", "d")
        vm.draftPlayer("c")
        val board = drafts.board
        assertThat(board.isComplete).isTrue()
        assertThat(board.fromPlayerDraft).isTrue()
        assertThat(board.seatsOf(Team.BLUE).map { it.pilotId }).containsExactly("b", "c")
        assertThat(DraftEngine.assignments(board).first { it.seat.pilotId == "c" }.heroId).isEqualTo(heroIds[2])
    }

    @Test
    fun `a finished player draft keeps everyone's hero and can undo the captains' last pick`() = runTest {
        val randomDraft = bundledMethods.methods.first { it.id == "random-draft" }
        val players = listOf("a", "b", "c", "d")
        val start = PlayerDraftSession.start("pd", 0, randomDraft, players, "a", "b", Team.RED, heroIds, 1)
            .getOrThrow()
        val drafts = FakeDraftRepository(active = DraftProgress.Players(start))
        val vm = viewModel(drafts)
        players.forEach { p ->
            vm.chooseHeroFor(p)
            vm.assignHero(start.selection.available(p).first { h -> h !in chosen(drafts).values })
        }
        val heroes = chosen(drafts)
        vm.draftPlayer("c") // "d" follows automatically: the other captain has no choice left
        val board = drafts.board
        val bySeat = { s: DraftSession -> DraftEngine.assignments(s).associate { it.seat.pilotId to it.heroId } }
        assertThat(bySeat(board)).isEqualTo(heroes)

        // "Deal again", pool swaps and seat swaps are not offered and are refused.
        assertThat(board.canEditPool).isFalse()
        assertThat(board.canSwapSeats).isFalse()
        vm.reroll()
        vm.swapOut(board.pool.first())
        vm.selectSeat("red-0")
        vm.selectSeat("red-1")
        assertThat(drafts.board).isEqualTo(board)
        assertThat(bySeat(drafts.board)).isEqualTo(heroes)

        // The captains' last pick can still be taken back.
        assertThat(vm.uiState.value.canUndo).isTrue()
        vm.undo()
        val progress = drafts.activeState.value as DraftProgress.Players
        assertThat(progress.session.phase).isEqualTo(PlayerDraftPhase.CAPTAINS)
        assertThat(progress.session.captains.remaining).containsExactly("c", "d")
        assertThat(progress.session.selection.choices).isEqualTo(heroes)
    }

    @Test
    fun `undo says what it took back and redo puts it back`() = runTest {
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session()))
        val vm = viewModel(drafts)
        val hero = DraftEngine.available(drafts.board).first()
        vm.tapHero(hero)
        vm.confirm()
        val after = drafts.board
        vm.undo()
        assertThat(drafts.board.history).isEmpty()
        assertThat(vm.uiState.value.local.message).isEqualTo("Undid Ban Test Hero ${hero.removePrefix("test-hero-")}")
        vm.redo()
        assertThat(drafts.board).isEqualTo(after)
        assertThat(vm.uiState.value.local.redo).isNull()
    }

    @Test
    fun `undoing a still-hidden choice never names the hero`() = runTest {
        val blind = house.copy(
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
                repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
            ),
        )
        var s = session(blind)
        s = DraftEngine.apply(s, DraftCommand(s.pool.first())).getOrThrow() // Red's hidden ban
        val drafts = FakeDraftRepository(active = DraftProgress.Board(s))
        val vm = viewModel(drafts)
        vm.undo()
        assertThat(vm.uiState.value.local.message).isEqualTo("Undid a hidden ban")
    }

    @Test
    fun `tapping a hero dealt to someone else explains why`() = runTest {
        val single = bundledMethods.methods.first { it.id == "single-draft" }
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session(single)))
        val vm = viewModel(drafts)
        val other = drafts.board.handOf("blue-0")!!.first()
        vm.tapUnavailable(other)
        assertThat(vm.uiState.value.local.message)
            .isEqualTo("Test Hero ${other.removePrefix("test-hero-")} wasn't dealt to Ann")
    }

    private fun chosen(drafts: FakeDraftRepository): Map<String, String> =
        (drafts.activeState.value as DraftProgress.Players).session.selection.choices
}
