package dev.quinton.goa.core.presentation.draft

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
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** What the board shows: hidden choices never show through, starting cards come from the catalog. */
class BoardPresentationTest {
    private val house = bundledMethods.methods.first { it.id == "house" }
    private val heroIds = FakeCatalog.catalog().heroes.map { it.id }

    private fun session(method: DraftMethod): DraftSession {
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d"), OddSeatMode.HANDICAP).getOrThrow()
        return DraftEngine.start(DraftSetup("d", 0L, method, seats, Team.RED, heroIds, 3L)).getOrThrow()
    }

    @Test
    fun `starting cards are the gold, silver and three tier I cards, found by tier and colour`() {
        val cards = startingCards(FakeCatalog.catalog().heroes.first())
        assertThat(cards.map { it.color })
            .containsExactly(CardColor.GOLD, CardColor.SILVER, CardColor.RED, CardColor.GREEN, CardColor.BLUE)
            .inOrder()
        assertThat(cards.map { it.tier }).containsExactly(null, null, 1, 1, 1).inOrder()
    }

    @Test
    fun `a hero taken in the current hidden run is drawn as still free and not counted as taken`() {
        val blind = house.copy(
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
                repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
            ),
        )
        var s = session(blind)
        val secret = s.pool.first()
        s = DraftEngine.apply(s, DraftCommand(secret)).getOrThrow()
        val hidden = PoolView.of(s)
        assertThat(hidden.taken).isEmpty()
        assertThat(hidden.open.first { it.heroId == secret }.state).isEqualTo(TileState.Available)
        assertThat(hidden.left).isEqualTo(s.pool.size)

        // Once the hidden run is over the ban is public.
        s = DraftEngine.apply(s, DraftCommand(s.pool[1])).getOrThrow()
        val revealed = PoolView.of(s)
        assertThat(revealed.taken.map { it.heroId }).containsExactly(secret, s.pool[1])
        assertThat(revealed.taken.first().state).isEqualTo(TileState.Banned(Team.RED))
    }

    @Test
    fun `dealt hands show the current player's heroes first and dim the rest`() {
        val single = bundledMethods.methods.first { it.id == "single-draft" }
        val s = session(single)
        val view = PoolView.of(s)
        assertThat(view.handOwner).isEqualTo("a")
        assertThat(view.open.map { it.heroId }).containsExactlyElementsIn(s.handOf("red-0")!!)
        assertThat(view.open.map { it.state }.toSet()).containsExactly(TileState.Available)
        assertThat(view.otherHands.map { it.state }.toSet()).containsExactly(TileState.Inactive)
    }

    @Test
    fun `a hidden pick from another hand looks like any other dimmed hero`() {
        val blindSingle = bundledMethods.methods.first { it.id == "single-draft" }.copy(
            pool = PoolRule.DealPerSeat(3),
            sequence = SequenceRule.Pattern(
                repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
            ),
        )
        var s = session(blindSingle)
        val redPick = s.handOf("red-0")!!.first()
        s = DraftEngine.apply(s, DraftCommand(redPick)).getOrThrow() // Blue's turn, Red's pick concealed
        val view = PoolView.of(s)
        assertThat(view.taken).isEmpty()
        assertThat(view.otherHands.first { it.heroId == redPick }.state).isEqualTo(TileState.Inactive)
    }
}
