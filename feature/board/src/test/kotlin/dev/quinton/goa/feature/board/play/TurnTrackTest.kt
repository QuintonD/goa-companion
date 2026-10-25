package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.game.panelWorthy
import dev.quinton.goa.feature.board.play.table.MomentKind
import dev.quinton.goa.feature.board.play.table.SlotState
import dev.quinton.goa.feature.board.play.table.tableMoment
import dev.quinton.goa.feature.board.play.table.turnLog
import dev.quinton.goa.feature.board.play.table.turnTrack
import org.junit.Test

/**
 * The turn track and the table's moments (ADR-041) from the engine's view: the order the heroes act
 * in (p.7), who is done, acting and next, ties, passes, Emmitt's second card, a hero defeated mid-turn,
 * and hidden information kept hidden (p.4).
 */
class TurnTrackTest {
    private val printed = mapOf("w" to 10, "t" to 9, "a" to 8, "b" to 7, "a2" to 11, "b2" to 11)
    private val names = mapOf("wasp" to "Wasp", "tigerclaw" to "Tigerclaw", "arien" to "Arien", "brogan" to "Brogan")

    private fun hero(
        id: String,
        team: Team,
        played: CardView? = null,
        resolved: List<CardView> = emptyList(),
        initiative: Int? = null,
        at: Hex? = Hex(0, 0),
        alsoPlayed: List<CardView> = emptyList(),
    ) = HeroView(
        heroId = id,
        team = team,
        controller = if (id == "arien") Controller.Human else Controller.Ai(AiLevel.NORMAL),
        at = at,
        level = 1,
        coins = 0,
        hand = null,
        handCount = 4,
        played = played,
        alsoPlayed = alsoPlayed,
        resolved = resolved,
        discard = emptyList(),
        items = emptyList(),
        ultimate = false,
        initiative = initiative,
    )

    private fun view(
        heroes: List<HeroView>,
        order: List<String> = emptyList(),
        acting: String? = null,
        acted: List<String> = emptyList(),
        phase: GamePhase = GamePhase.RESOLVE,
        round: Int = 1,
        turn: Int = 2,
        coin: Team = Team.BLUE,
    ) = GameView(
        mapId = "m",
        viewer = "arien",
        round = round,
        turn = turn,
        phase = phase,
        heroes = heroes,
        minions = emptyList(),
        pieces = emptyList(),
        battleZones = emptyMap(),
        wavesLeft = emptyMap(),
        lifeCounters = emptyMap(),
        tieBreaker = coin,
        initiativeOrder = order,
        acting = acting,
        actedThisTurn = acted,
    )

    private val revealed = GameEvent.CardsRevealed(
        linkedMapOf("wasp" to "w", "tigerclaw" to "t", "arien" to "a", "brogan" to "b"),
        "Cards revealed.",
        mapOf("wasp" to 10, "tigerclaw" to 9, "arien" to 8, "brogan" to 7),
    )
    private val turnStart = GameEvent.TurnStarted(1, 2, "Turn 2 of round 1.")

    private fun track(view: GameView, events: List<GameEvent>) = turnTrack(view, events) { printed[it] }

    @Test
    fun `before the reveal there is no track and other heroes' cards stay unknown`() {
        val v = view(
            listOf(hero("wasp", Team.BLUE, played = CardView(null, facedown = true)), hero("arien", Team.RED)),
            phase = GamePhase.PLAY_CARDS,
        )
        assertThat(track(v, listOf(turnStart))).isNull()
    }

    @Test
    fun `right after the reveal every card is in acting order, the first acting and the second next`() {
        val v = view(
            listOf(
                hero("arien", Team.RED, CardView("a"), initiative = 8),
                hero("brogan", Team.RED, CardView("b"), initiative = 7),
                hero("wasp", Team.BLUE, CardView("w"), initiative = 10),
                hero("tigerclaw", Team.BLUE, CardView("t"), initiative = 9),
            ),
            order = listOf("wasp", "tigerclaw", "arien", "brogan"),
            acting = "wasp",
        )
        val t = track(v, listOf(turnStart, revealed))!!
        assertThat(t.slots.map { it.heroId }).containsExactly("wasp", "tigerclaw", "arien", "brogan").inOrder()
        assertThat(t.slots.map { it.state })
            .containsExactly(SlotState.NOW, SlotState.NEXT, SlotState.LATER, SlotState.LATER).inOrder()
        assertThat(t.slots.map { it.place }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(t.slots.map { it.initiative }).containsExactly(10, 9, 8, 7).inOrder()

        val moment = tableMoment(v, listOf(turnStart, revealed), t, { names.getValue(it) }, setOf("arien"))!!
        assertThat(moment.kind).isEqualTo(MomentKind.REVEALED)
        assertThat(moment.text).isEqualTo("Wasp acts first with initiative 10.")
    }

    @Test
    fun `mid-turn the resolved card is done with its action, the acting hero now, the next one next`() {
        val v = view(
            listOf(
                hero("wasp", Team.BLUE, resolved = listOf(CardView("w0", slot = 1), CardView("w", slot = 2))),
                hero("tigerclaw", Team.BLUE, CardView("t"), initiative = 9),
                hero("arien", Team.RED, CardView("a"), initiative = 8),
                hero("brogan", Team.RED, CardView("b"), initiative = 7),
            ),
            // The engine lists only the unresolved cards.
            order = listOf("tigerclaw", "arien", "brogan"),
            acting = "tigerclaw",
            acted = listOf("wasp"),
        )
        val events = listOf(
            turnStart,
            revealed,
            GameEvent.ActionChosen("wasp", "w", ActionType.MOVEMENT, "Wasp chooses movement."),
            GameEvent.Moved("wasp", emptyList(), "Wasp moves 1 space."),
            GameEvent.ActionChosen("tigerclaw", "t", ActionType.PRIMARY, "Tigerclaw chooses its primary."),
        )
        val t = track(v, events)!!
        val wasp = t.slots.first()
        assertThat(wasp.state).isEqualTo(SlotState.DONE)
        // The card on this turn's slot, not the one from turn 1.
        assertThat(wasp.cards.map { it.cardId }).containsExactly("w")
        assertThat(wasp.action).isEqualTo(ActionType.MOVEMENT)
        assertThat(wasp.initiative).isEqualTo(10)
        assertThat(t.acting?.heroId).isEqualTo("tigerclaw")
        assertThat(t.acting?.action).isEqualTo(ActionType.PRIMARY)
        assertThat(t.next?.heroId).isEqualTo("arien")
        // The reveal's moment is over once a hero has acted.
        assertThat(tableMoment(v, events, t, { names.getValue(it) })).isNull()
    }

    @Test
    fun `a card resolved facedown stays facedown on the track`() {
        val v = view(
            listOf(
                hero("wasp", Team.BLUE, resolved = listOf(CardView(null, facedown = true, slot = 2))),
                hero("arien", Team.RED, CardView("a"), initiative = 8),
            ),
            order = listOf("arien"),
            acting = "arien",
            acted = listOf("wasp"),
        )
        val wasp = track(v, listOf(turnStart, revealed))!!.slots.first()
        assertThat(wasp.cards.single().cardId).isNull()
        assertThat(wasp.cards.single().facedown).isTrue()
    }

    @Test
    fun `heroes of one team tied on initiative are both marked and their team chooses`() {
        val reveal = GameEvent.CardsRevealed(linkedMapOf("arien" to "a2", "brogan" to "b2"), "Cards revealed.")
        val v = view(
            listOf(
                hero("arien", Team.RED, CardView("a2"), initiative = 11),
                hero("brogan", Team.RED, CardView("b2"), initiative = 11),
            ),
            order = listOf("arien", "brogan"),
        )
        val t = track(v, listOf(turnStart, reveal))!!
        assertThat(t.slots.map { it.tied }).containsExactly(true, true)
        assertThat(t.slots.map { it.state }).containsExactly(SlotState.NEXT, SlotState.LATER).inOrder()
        val moment = tableMoment(v, listOf(turnStart, reveal), t, { names.getValue(it) })!!
        assertThat(moment.text)
            .isEqualTo("Arien and Brogan tie on initiative 11: their team chooses who acts first (p.7).")
    }

    @Test
    fun `a tie across teams says the coin decides and who flips it`() {
        val reveal = GameEvent.CardsRevealed(linkedMapOf("wasp" to "a2", "arien" to "b2"), "Cards revealed.")
        val v = view(
            listOf(
                hero("wasp", Team.BLUE, CardView("a2"), initiative = 11),
                hero("arien", Team.RED, CardView("b2"), initiative = 11),
            ),
            order = listOf("wasp", "arien"),
            acting = "wasp",
            coin = Team.BLUE,
        )
        val t = track(v, listOf(turnStart, reveal))!!
        // The acting hero's tie is settled; the one still waiting is not tied with anyone.
        assertThat(t.slots.map { it.tied }).containsExactly(false, false)
        val moment = tableMoment(v, listOf(turnStart, reveal), t, { names.getValue(it) })!!
        assertThat(moment.text).isEqualTo(
            "Wasp and Arien tie on initiative 11. The Tie Breaker coin shows Blue: " +
                "Wasp acts first, then flips it (p.7).",
        )
    }

    @Test
    fun `a pass, a card that left the table, Emmitt's second card and a hero defeated mid-turn`() {
        val reveal = GameEvent.CardsRevealed(
            linkedMapOf("emmitt" to "e1", "arien" to "a", "wasp" to "w", "brogan" to null),
            "Cards revealed.",
        )
        val v = view(
            listOf(
                hero("emmitt", Team.BLUE, CardView("e1"), initiative = 9, alsoPlayed = listOf(CardView("e2"))),
                hero("arien", Team.RED, CardView("a"), initiative = 8, at = null),
                hero("wasp", Team.BLUE),
                hero("brogan", Team.RED),
            ),
            order = listOf("emmitt", "arien"),
            acting = "emmitt",
        )
        val events = listOf(
            turnStart,
            reveal,
            GameEvent.Defeated("arien", "emmitt", "Arien is defeated by Emmitt."),
        )
        val t = track(v, events)!!
        assertThat(t.slots.map { it.heroId }).containsExactly("emmitt", "arien", "wasp", "brogan").inOrder()
        assertThat(t.slots[0].cards.map { it.cardId }).containsExactly("e1", "e2").inOrder()
        assertThat(t.slots[1].offBoard).isTrue()
        assertThat(t.slots[1].defeated).isTrue()
        assertThat(t.slots[1].state).isEqualTo(SlotState.NEXT)
        assertThat(t.slots[2].state).isEqualTo(SlotState.SKIPPED)
        assertThat(t.slots[2].place).isNull()
        assertThat(t.slots[3].state).isEqualTo(SlotState.PASSED)
        assertThat(t.slots[3].cards).isEmpty()
    }

    @Test
    fun `the start of the next turn shows the cards of the turn just over on their slots`() {
        val v = view(
            listOf(
                hero("arien", Team.RED, resolved = listOf(CardView("a", slot = 1))),
                hero("wasp", Team.BLUE, resolved = listOf(CardView("w", slot = 1))),
            ),
            phase = GamePhase.PLAY_CARDS,
            turn = 2,
        )
        val log = listOf(
            GameEvent.TurnStarted(1, 1, "Turn 1 of round 1."),
            GameEvent.CardsRevealed(linkedMapOf("wasp" to "w", "arien" to "a"), "Cards revealed."),
            GameEvent.TurnStarted(1, 2, "Turn 2 of round 1."),
        )
        val moment = tableMoment(v, log, null, { names.getValue(it) })!!
        assertThat(moment.kind).isEqualTo(MomentKind.TURN_OVER)
        assertThat(moment.title).isEqualTo("Turn 1 is over")
        assertThat(moment.cards.map { it.heroId }).containsExactly("wasp", "arien").inOrder()
    }

    @Test
    fun `a new round says the push and the level ups of the round just over`() {
        val v = view(listOf(hero("arien", Team.RED)), phase = GamePhase.PLAY_CARDS, round = 2, turn = 1)
        val log = turnLog(
            listOf(
                GameEvent.TurnStarted(1, 3, "Turn 3 of round 1."),
                GameEvent.TurnStarted(1, 4, "Turn 4 of round 1."),
                GameEvent.LanePushed("lane", Team.RED, "z3", "Red pushes the lane."),
                GameEvent.LevelledUp("arien", 2, "Arien levels up to 2."),
                GameEvent.RoundStarted(2, "Round 2 begins."),
                GameEvent.TurnStarted(2, 1, "Turn 1 of round 2."),
            ),
        )
        assertThat(log.first()).isEqualTo(GameEvent.TurnStarted(1, 4, "Turn 4 of round 1."))
        val moment = tableMoment(v, log, null, { names.getValue(it) })!!
        assertThat(moment.kind).isEqualTo(MomentKind.ROUND_OVER)
        assertThat(moment.title).isEqualTo("Round 1 is over")
        assertThat(moment.text).isEqualTo(
            "Red won the push · Arien reached level 2 · Round 2: everyone plays a card facedown (p.7).",
        )
        val ending = tableMoment(v.copy(phase = GamePhase.END_OF_ROUND, round = 1), log, null, { it })!!
        assertThat(ending.kind).isEqualTo(MomentKind.ROUND_ENDING)
    }

    @Test
    fun `the first turn of the game has no moment before the reveal`() {
        val v = view(listOf(hero("arien", Team.RED)), phase = GamePhase.PLAY_CARDS, turn = 1)
        assertThat(tableMoment(v, listOf(GameEvent.TurnStarted(1, 1, "")), null, { it })).isNull()
    }

    @Test
    fun `the panel leaves out what the table shows`() {
        assertThat(panelWorthy(revealed)).isFalse()
        assertThat(panelWorthy(GameEvent.ActionChosen("w", "w", ActionType.HOLD, "Wasp chooses hold."))).isFalse()
        assertThat(panelWorthy(GameEvent.Moved("w", emptyList(), "Wasp moves 1 space."))).isFalse()
        assertThat(panelWorthy(GameEvent.Note("Wasp plays a card facedown."))).isFalse()
        assertThat(panelWorthy(GameEvent.Attacked("a", "w", 6, "Arien attacks Wasp."))).isTrue()
        assertThat(panelWorthy(GameEvent.Defeated("w", "a", "Wasp is defeated."))).isTrue()
    }
}
