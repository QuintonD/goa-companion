package dev.quinton.goa.core.presentation.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
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
import org.junit.Test

/** The board's shared behaviour: confirm, undo/redo, hidden choices, seats, the Player Draft, finishing. */
class DraftBoardTest {
    private val catalog = FakeCatalog.catalog()
    private val heroes = catalog.heroes.associate { it.id to HeroInfo.of(it) }
    private val heroIds = catalog.heroes.map { it.id }
    private val names = mapOf("a" to "Ann", "b" to "Bo", "c" to "Cy", "d" to "Di")
    private val playerName: (String?) -> String = { names[it] ?: "Unknown player" }

    private fun session(method: DraftMethod = method("house")): DraftSession {
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d"), OddSeatMode.HANDICAP).getOrThrow()
        return DraftEngine.start(DraftSetup("d", 0L, method, seats, Team.RED, heroIds, 3L)).getOrThrow()
    }

    private val blind = method("house").copy(
        sequence = SequenceRule.Pattern(
            opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
            repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
        ),
    )

    @Test
    fun `a confirm made for an earlier step is refused instead of landing on the next step`() {
        val s = session()
        val first = DraftBoard.confirm(s, s.pool[0], 0).getOrThrow()
        val stale = DraftBoard.confirm(first, s.pool[1], 0)
        assertThat(stale.exceptionOrNull()).isInstanceOf(DraftError.StaleStep::class.java)
        assertThat(
            DraftBoard.message(stale.exceptionOrNull()!!, heroes),
        ).isEqualTo("The draft has moved on; choose again")
    }

    @Test
    fun `tapping a hero waits for a confirm on the current step and clears any redo`() {
        val s = session()
        val local = DraftBoard.tapHero(
            s,
            BoardLocal(redo = DraftEngine.apply(s, DraftCommand(s.pool[0])).getOrThrow().history.last()),
            s.pool[2],
        )
        assertThat(local.confirm).isEqualTo(s.pool[2])
        assertThat(local.confirmStep).isEqualTo(0)
        assertThat(local.redo).isNull()
    }

    @Test
    fun `undo names the step it took back, and redo puts it back`() {
        var s = session()
        s = DraftBoard.confirm(s, s.pool[0], 0).getOrThrow()
        val outcome = DraftBoard.undo(DraftProgress.Board(s), heroes).getOrThrow()
        assertThat(outcome.message).isEqualTo("Undid Ban ${heroes.info(s.pool[0]).name}")
        val undone = (outcome.progress as DraftProgress.Board).session
        assertThat(undone.history).isEmpty()
        val redone = DraftBoard.redo(undone, outcome.redo!!).getOrThrow()
        assertThat(redone).isEqualTo(s)
    }

    @Test
    fun `undoing a still-hidden choice never names the hero`() {
        var s = session(blind)
        s = DraftBoard.confirm(s, s.pool[0], 0).getOrThrow()
        val outcome = DraftBoard.undo(DraftProgress.Board(s), heroes).getOrThrow()
        assertThat(outcome.message).isEqualTo("Undid a hidden ban")
        assertThat(outcome.message).doesNotContain(heroes.info(s.pool[0]).name)
    }

    @Test
    fun `a hero concealed by a hidden step is refused with a neutral message`() {
        var s = session(blind)
        val secret = s.pool[0]
        s = DraftBoard.confirm(s, secret, 0).getOrThrow()
        val refused = DraftBoard.confirm(s, secret, 1).exceptionOrNull()!!
        assertThat(DraftBoard.message(refused, heroes)).isEqualTo("Choose a different hero")
    }

    @Test
    fun `team views flag concealed choices without naming them and count roles of revealed picks only`() {
        val hiddenPicks = method("house").copy(
            sequence = SequenceRule.Pattern(
                repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
            ),
        )
        var s = session(hiddenPicks)
        val secret = s.pool[0]
        s = DraftBoard.confirm(s, secret, 0).getOrThrow()
        val red = DraftBoard.teams(s, heroes).first { it.team == Team.RED }
        assertThat(red.seats.first().hidden).isTrue()
        assertThat(red.seats.first().heroId).isNull()
        assertThat(red.roles).isEmpty()
        assertThat(DraftBoard.teams(s, heroes).flatMap { t -> t.seats.mapNotNull { it.heroId } }).doesNotContain(secret)
    }

    @Test
    fun `the step banner says whose turn, the run and the pilot`() {
        val s = session(method("all-pick"))
        val banner = DraftBoard.stepBanner(s, playerName)!!
        assertThat(banner.title).isEqualTo("◆ Red — Pick")
        assertThat(banner.detail).isEqualTo("Team A · Step 1 of 4 · for Ann")
    }

    @Test
    fun `seats swap within a team, but Single Draft hands stay with their seat`() {
        var s = session(method("all-pick"))
        while (!s.isComplete) {
            s =
                DraftBoard.confirm(s, DraftEngine.available(s).first(), s.currentStepIndex!!).getOrThrow()
        }
        assertThat(DraftBoard.tapSeat(s, null, "red-0", heroes)).isEqualTo(SeatTap.Select("red-0"))
        assertThat(DraftBoard.tapSeat(s, "red-0", "red-0", heroes)).isEqualTo(SeatTap.Select(null))
        assertThat(DraftBoard.tapSeat(s, "red-0", "red-1", heroes)).isEqualTo(SeatTap.Swap("red-0", "red-1"))

        var single = session(method("single-draft"))
        while (!single.isComplete) {
            single =
                DraftBoard.confirm(
                    single,
                    DraftEngine.available(single).first(),
                    single.currentStepIndex!!,
                ).getOrThrow()
        }
        assertThat(DraftBoard.tapSeat(single, null, "red-0", heroes))
            .isEqualTo(SeatTap.Refused("Each player plays the hero picked from their own hand"))
    }

    @Test
    fun `swapping a pool hero draws an eligible replacement, and says so when there is none`() {
        val s = session()
        val swapped = DraftBoard.swapOut(s, s.pool[0], 5L).getOrThrow()
        assertThat(swapped.pool).doesNotContain(s.pool[0])
        assertThat(swapped.pool.size).isEqualTo(s.pool.size)

        val everyone = session(method("house").copy(pool = PoolRule.All))
        val refused = DraftBoard.swapOut(everyone, everyone.pool[0], 5L).exceptionOrNull()!!
        assertThat(DraftBoard.message(refused, heroes)).isEqualTo("Every eligible hero is already in the pool")
    }

    @Test
    fun `finishing gives a pending match with the coin and the record that points at it`() {
        var s = session()
        while (!s.isComplete) {
            s =
                DraftBoard.confirm(s, DraftEngine.available(s).first(), s.currentStepIndex!!).getOrThrow()
        }
        val finished = DraftBoard.finish(s, "match-1", 5_000L).getOrThrow()
        assertThat(finished.match.status).isEqualTo(MatchStatus.PENDING)
        assertThat(finished.match.draftId).isEqualTo(s.id)
        assertThat(finished.match.tieBreaker).isEqualTo(Team.RED)
        assertThat(finished.match.participants.map { it.heroId }).containsExactlyElementsIn(s.pickedIds)
        assertThat(finished.record.matchId).isEqualTo("match-1")
        assertThat(finished.record.steps.map { it.heroId }).isEqualTo(s.history.map { it.heroId })
        assertThat(finished).isEqualTo(
            FinishedDraft(
                DraftEngine.toPendingMatch(s, "match-1", 5_000L).getOrThrow(),
                DraftEngine.toRecord(s, "match-1"),
            ),
        )
    }

    @Test
    fun `player draft makes the forced last pick, hands off to a board, and undo takes both picks back`() {
        val players = listOf("a", "b", "c", "d", "e")
        var p = PlayerDraftSession.start(
            "pd", 0L, method("all-pick"), players, redCaptain = "a", blueCaptain = "b", teamA = Team.BLUE,
            candidates = heroIds, seed = 4L,
        ).getOrThrow()
        players.forEachIndexed { i, id ->
            p = (DraftBoard.assignHero(p, id, p.selection.pool[i]).getOrThrow() as DraftProgress.Players).session
        }
        assertThat(p.phase).isEqualTo(PlayerDraftPhase.CAPTAINS)
        // Blue (Team A) picks c, Red picks d: e is left and joins Blue without a choice.
        p = (DraftBoard.draftPlayer(p, "c").getOrThrow() as DraftProgress.Players).session
        val handOff = DraftBoard.draftPlayer(p, "d").getOrThrow() as DraftProgress.Board
        assertThat(handOff.session.isComplete).isTrue()
        assertThat(handOff.session.fromPlayerDraft).isTrue()
        assertThat(handOff.playerDraft!!.captains.picks.last().auto).isTrue()
        assertThat(handOff.playerDraft!!.captains.blue).containsExactly("b", "c", "e")

        // Undo on the handed-off board goes back to the captains, before Red's pick and the forced one.
        val back = DraftBoard.undo(handOff, heroes).getOrThrow()
        val restored = (back.progress as DraftProgress.Players).session
        assertThat(restored.captains.remaining).containsExactly("d", "e")
        assertThat(restored.selection.choices).hasSize(5)
        assertThat(back.message).isNull()
    }

    @Test
    fun `player draft heroes can be changed until teams are drafted`() {
        var p = PlayerDraftSession.start(
            "pd", 0L, method("all-pick"), listOf("a", "b", "c", "d"), "a", "b", Team.RED, heroIds, 4L,
        ).getOrThrow()
        p = (DraftBoard.assignHero(p, "a", p.selection.pool[0]).getOrThrow() as DraftProgress.Players).session
        val taken = DraftBoard.assignHero(p, "b", p.selection.pool[0]).exceptionOrNull()!!
        assertThat(
            DraftBoard.message(taken, heroes),
        ).isEqualTo("${heroes.info(p.selection.pool[0]).name} is already taken")
        p = (DraftBoard.clearHero(p, "a").getOrThrow() as DraftProgress.Players).session
        assertThat(p.selection.choices).isEmpty()
        assertThat(DraftBoard.undoPlayerPick(p).exceptionOrNull()).isEqualTo(DraftError.NothingToUndo)
    }

    @Test
    fun `texts for the gate, the confirm and the completion screen`() {
        val s = session(blind)
        assertThat(DraftBoard.privacyText(s)!!.title).isEqualTo("Pass the phone to ◆ Red")
        assertThat(DraftBoard.confirmText(s, playerName)).isEqualTo("◆ Red removes this hero from the draft.")
        assertThat(DraftBoard.completionHint(s)).isEqualTo("Tap two heroes on the same team to swap who plays them.")
        assertThat(DraftBoard.sequenceTokens(s).first()).isEqualTo(StepToken("Ban ◆", DraftAction.BAN, Team.RED, true))
    }

    @Test
    fun `board state gates hidden steps and offers undo only when there is something to undo`() {
        val s = session(blind)
        val state = BoardUiState(loading = false, progress = DraftProgress.Board(s))
        assertThat(state.privacyGate).isTrue()
        assertThat(state.canUndo).isFalse()
        assertThat(state.copy(local = BoardLocal(privacyAcknowledged = 0)).privacyGate).isFalse()
    }
}
