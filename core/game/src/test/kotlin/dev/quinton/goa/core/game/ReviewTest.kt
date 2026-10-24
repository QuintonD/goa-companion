package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The game-state review (ADR-037): turn by turn, the cards revealed and what happened, and the board
 * at the start of each turn. It shows only what the viewer saw at the time (p.4 "Hidden
 * Information": hands, the deck, tucked items and facedown cards are hidden), never more.
 */
class ReviewTest {
    private val viewer = setOf("wasp")

    /** Every card id [view] shows (own hand and items, everyone's faceup cards). */
    private fun cardIds(view: GameView): Set<String> = view.heroes.flatMap { h ->
        h.hand.orEmpty() + h.items + h.activeEffects +
            (listOfNotNull(h.played) + h.alsoPlayed + h.resolved + h.discard).mapNotNull { it.cardId }
    }.toSet()

    private fun cardIds(turn: TurnReview): Set<String> =
        turn.plays.flatMap { p -> (listOfNotNull(p.card) + p.alsoPlayed).mapNotNull { it.cardId } }.toSet()

    @Test
    fun `turns follow the game and their events partition the log`() {
        val s = TestData.newGame()
        playOut(s, RandomPlayer(7), maxDecisions = 700)
        val turns = s.review(viewer)
        assertThat(turns).isNotEmpty()
        // (1,1), (1,2), (1,3), (1,4), (2,1)… with nothing missing.
        turns.forEachIndexed { i, t ->
            assertThat(t.round to t.turn).isEqualTo(1 + i / 4 to 1 + i % 4)
            assertThat(t.hasStart).isTrue()
            assertThat(t.fromPosition).isFalse()
            assertThat(t.finished).isEqualTo(i < turns.lastIndex || s.pending == null)
            if (t.turn < 4) assertThat(t.endOfRound).isEmpty()
        }
        assertThat(turns.flatMap { it.opening + it.events + it.endOfRound }).isEqualTo(s.events)
        // The reveal ends the opening; a turn played to its end has every hero's card or pass.
        for (t in turns.dropLast(1)) {
            assertThat(t.opening.last()).isInstanceOf(GameEvent.CardsRevealed::class.java)
            assertThat(t.plays.map { it.heroId }).containsExactly("wasp", "xargatha", "arien", "brogan")
            val revealed = t.opening.last() as GameEvent.CardsRevealed
            t.plays.forEach { p -> assertThat(p.card?.cardId).isEqualTo(revealed.played[p.heroId]) }
            assertThat(t.plays.filter { it.card != null }.all { it.initiative != null }).isTrue()
        }
        // The end of round belongs to turn 4; the next round opens the next turn.
        val endOfRound1 = turns.first { it.round == 1 && it.turn == 4 }.endOfRound
        assertThat(endOfRound1).isNotEmpty()
        assertThat(endOfRound1.none { it is GameEvent.RoundStarted || it is GameEvent.TurnStarted }).isTrue()
        assertThat(turns.first { it.round == 2 }.opening.first()).isInstanceOf(GameEvent.RoundStarted::class.java)
    }

    @Test
    fun `the review shows each card as the viewer saw it at the reveal`() {
        for (seed in 1L..3L) {
            val s = TestData.newGame()
            val seen = LinkedHashMap<Pair<Int, Int>, Map<String, CardView?>>()
            var revealsSeen = 0
            playOut(s, RandomPlayer(seed), maxDecisions = 600) {
                val reveals = s.events.count { it is GameEvent.CardsRevealed }
                if (reveals > revealsSeen) {
                    revealsSeen = reveals
                    val v = s.view("wasp")
                    seen[v.round to v.turn] = v.heroes.associate { it.heroId to it.played }
                }
            }
            val turns = s.review(viewer).filter { (it.round to it.turn) in seen }
            assertThat(turns).isNotEmpty()
            for (t in turns) {
                val live = seen.getValue(t.round to t.turn)
                t.plays.forEach { p -> assertThat(p.card).isEqualTo(live[p.heroId]) }
            }
        }
    }

    @Test
    fun `no card the viewer never saw appears in the review or a kept board`() {
        for (seed in 11L..13L) {
            val s = TestData.newGame()
            val seen = HashSet<String>()
            playOut(s, RandomPlayer(seed), maxDecisions = 600) { seen += cardIds(s.view("wasp")) }
            seen += cardIds(s.view("wasp"))
            for (t in s.review(viewer)) {
                assertThat(seen).containsAtLeastElementsIn(cardIds(t))
                val board = s.positionAt(t.round, t.turn, viewer)!!
                assertThat(board.round to board.turn).isEqualTo(t.round to t.turn)
                assertThat(seen).containsAtLeastElementsIn(cardIds(board))
                board.heroes.filter { it.heroId != "wasp" }.forEach { h ->
                    assertThat(h.hand).isNull()
                    assertThat(h.items).isEmpty()
                }
            }
        }
    }

    @Test
    fun `cards played facedown are not in the review before the reveal`() {
        val a = TestData.newGame()
        a.answer(0, Choice.Card("wasp-gold"))
        a.answer(1, Choice.Card("xargatha-red-1"))
        val b = TestData.newGame()
        b.answer(0, Choice.Card("wasp-blue-1"))
        b.answer(1, Choice.Card("xargatha-silver"))
        // Blue is about to play and sees the same game in both: so is the review.
        assertThat(b.view("arien")).isEqualTo(a.view("arien"))
        assertThat(b.review(setOf("arien"))).isEqualTo(a.review(setOf("arien")))
        assertThat(a.review(setOf("arien")).single().plays).isEmpty()
        assertThat(b.positionAt(1, 1, setOf("arien"))).isEqualTo(a.positionAt(1, 1, setOf("arien")))
    }

    /** Wasp at level 2 with the Red or the Blue card tucked: the item icon is Defense either way. */
    private fun upgraded(redUpgrade: Boolean, block: PositionBuilder.() -> Unit = {}): EngineSession =
        position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                if (redUpgrade) {
                    hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                    items("wasp-red-2b")
                } else {
                    hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                    items("wasp-blue-2a")
                }
            }
            hero("xargatha", Team.RED, Hex(-1, -1))
            block()
        }

    @Test
    fun `an enemy's tucked item and hand are hidden in the review and the kept board`() {
        val a = upgraded(redUpgrade = true)
        val b = upgraded(redUpgrade = false)
        // Through the reveal: Wasp plays the same card in both.
        for (s in listOf(a, b)) {
            s.answer(s.pending!!.id, Choice.Card("wasp-gold"))
            s.answer(s.pending!!.id, Choice.Card("xargatha-gold"))
            s.answer(s.pending!!.id, Choice.Card("arien-gold"))
            s.answer(s.pending!!.id, Choice.Card("brogan-gold"))
        }
        val blue = setOf("arien", "brogan")
        assertThat(b.review(blue)).isEqualTo(a.review(blue))
        assertThat(b.positionAt(1, 1, blue)).isEqualTo(a.positionAt(1, 1, blue))
        val wasp = a.positionAt(1, 1, blue)!!.heroes.first { it.heroId == "wasp" }
        assertThat(wasp.items).isEmpty()
        assertThat(wasp.hand).isNull()
        assertThat(wasp.itemStats).hasSize(1)
        // Wasp's own review shows her item and hand.
        val own = a.positionAt(1, 1, viewer)!!.heroes.first { it.heroId == "wasp" }
        assertThat(own.items).containsExactly("wasp-red-2b")
        assertThat(own.hand).contains("wasp-red-2a")
    }

    @Test
    fun `a facedown resolved card stays facedown for everyone but its owner`() {
        val s = position(TestData.engine, TestData.FOREST) {
            turn = 2
            hero("arien", Team.BLUE, Hex(2, -3)) { resolved(1, "arien-red-1", faceDown = true) }
            hero("brogan", Team.BLUE, Hex(3, -3))
            hero("wasp", Team.RED, Hex(-1, -2))
            hero("xargatha", Team.RED, Hex(-1, -1))
        }
        val forWasp = s.positionAt(1, 2, viewer)!!.heroes.first { it.heroId == "arien" }.resolved.single()
        assertThat(forWasp.cardId).isNull()
        assertThat(forWasp.facedown).isTrue()
        val forArien = s.positionAt(1, 2, setOf("arien"))!!.heroes.first { it.heroId == "arien" }.resolved.single()
        assertThat(forArien.cardId).isEqualTo("arien-red-1")
    }

    @Test
    fun `a puzzle's viewers are every seat, so every hand shows`() {
        val s = upgraded(redUpgrade = true)
        val all = s.setup.seats.map { it.heroId }.toSet()
        assertThat(s.positionAt(1, 1, all)!!.heroes.all { it.hand != null }).isTrue()
    }

    @Test
    fun `a position built mid-turn starts the review with its revealed cards`() {
        val s = position(TestData.engine, TestData.FOREST) {
            turn = 3
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-red-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { resolved(3, "brogan-gold") }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-gold") }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-gold") }
            phase = PositionPhase.Resolving()
        }
        val t = s.review(viewer).single()
        assertThat(t.round to t.turn).isEqualTo(1 to 3)
        assertThat(t.fromPosition).isTrue()
        assertThat(t.plays.map { it.card?.cardId })
            .containsExactly("arien-red-1", "wasp-gold", "xargatha-gold", "brogan-gold")
        assertThat(t.plays.first { it.heroId == "brogan" }.resolved).isTrue()
        assertThat(s.positionAt(1, 3, viewer)).isNotNull()
        assertThat(s.positionAt(1, 2, viewer)).isNull()
    }

    @Test
    fun `restore, fork and undo keep the history`() {
        val setup = TestData.firstGame().let {
            it.copy(
                seats = it.seats.map { s ->
                    if (s.team ==
                        Team.BLUE
                    ) {
                        s.copy(controller = Controller.Ai(AiLevel.EASY))
                    } else {
                        s
                    }
                },
            )
        }
        val s = TestData.engine.newGame(setup) as EngineSession
        playOut(s, RandomPlayer(3), maxDecisions = 120)
        val before = s.review(viewer)
        assertThat(TestData.engine.restore(s.save()).review(viewer)).isEqualTo(before)
        assertThat(s.fork().review(viewer)).isEqualTo(before)
        // Undo replays: the history is rebuilt to that point.
        val f = s.forkSession()
        if (f.undoToLastHumanDecision()) {
            val replayed = TestData.engine.restore(f.save()).review(viewer)
            assertThat(f.review(viewer)).isEqualTo(replayed)
        }
    }

    @Test
    fun `the view lists who has acted this turn`() {
        val s = TestData.newGame()
        listOf("wasp-gold", "xargatha-gold", "arien-gold", "brogan-gold").forEach {
            s.answer(s.pending!!.id, Choice.Card(it))
        }
        assertThat(s.view("wasp").actedThisTurn).isEmpty()
        val first = s.pending!!.seat
        while (s.view("wasp").actedThisTurn.isEmpty()) {
            val d = s.pending!!
            s.answer(d.id, d.options.firstOrNull { it == Choice.Action(ActionType.HOLD) } ?: d.options.first())
        }
        assertThat(s.view("wasp").actedThisTurn).containsExactly(first)
        val t = s.review(viewer).single()
        assertThat(t.plays.first().heroId).isEqualTo(first)
        assertThat(t.plays.first().resolved).isTrue()
        assertThat(t.plays.drop(1).none { it.resolved }).isTrue()
    }
}
