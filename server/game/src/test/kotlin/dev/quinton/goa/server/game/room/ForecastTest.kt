package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.junit.Test

/**
 * Forecasts equal the real questions (docs/ONLINE.md "Simultaneous questions"): each seat is given its
 * questions before the engine asks them, answers them in a random order, and then the engine asks
 * exactly those, in seat order, whatever the others hold and whatever they chose.
 */
class ForecastTest {
    private val engine = TestRules.engine

    private class Queued(val question: Decision, val choice: Choice, val hero: HeroView?)

    private class Tally {
        val games = AtomicInteger()
        val windows = AtomicInteger()
        val questions = AtomicInteger()
        val levelQuestions = AtomicInteger()
        val heroes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    /** One window: forecast every seat in random order, then let the engine catch up and compare. */
    private fun playWindow(s: EngineSession, rng: Random, tally: Tally) {
        val seats = s.setup.seats.map { it.heroId }
        val queued = HashMap<String, MutableList<Queued>>()
        val key = checkNotNull(SimultaneousWindow.keyOf(s))
        while (true) {
            val w = checkNotNull(SimultaneousWindow.of(s, queued.mapValues { (_, q) -> q.map { it.choice } }))
            val open = w.open(seats)
            if (open.isEmpty()) break
            val seat = open[rng.nextInt(open.size)]
            val q = w.forecast(seat).question!!
            assertThat(q.seat).isEqualTo(seat)
            assertThat(q.kind).isIn(SimultaneousWindow.KINDS)
            queued.getOrPut(seat) { ArrayList() } += Queued(q, RandomGames.choose(q, rng), null)
            tally.questions.incrementAndGet()
            if (q.kind != DecisionKind.PLAY_CARD) tally.levelQuestions.incrementAndGet()
        }
        // A seat's own view after its locks is what the engine then shows it, without seeing anyone's lock.
        val patched = seats.associateWith { seat ->
            SimultaneousWindow.of(s, mapOf(seat to queued[seat].orEmpty().map { it.choice }))!!.forecast(seat).hero
        }
        while (SimultaneousWindow.keyOf(s) == key) {
            val d = s.pending!!
            val q = queued[d.seat]?.removeFirstOrNull()
            assertWithMessage("${d.seat} was never told about $d").that(q).isNotNull()
            assertWithMessage(
                "forecast for ${d.seat} at $key",
            ).that(q!!.question.copy(id = 0)).isEqualTo(d.copy(id = 0))
            val halted = s.forkHalting { it.step != s.state.step }
            halted.answer(d.id, q.choice)
            if (queued[d.seat].isNullOrEmpty()) {
                val seen = halted.view(d.seat).heroes.first { it.heroId == d.seat }
                assertWithMessage("${d.seat}'s own view at $key").that(patched[d.seat]).isEqualTo(seen)
            }
            assertThat(s.answer(d.id, q.choice)).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        for ((seat, left) in queued) {
            assertWithMessage("$seat was forecast questions the engine did not ask at $key").that(left).isEmpty()
        }
        tally.windows.incrementAndGet()
    }

    private fun playGame(game: Int, heroes: List<String>, tally: Tally) {
        val rng = Random(game.toLong())
        val setup = RandomGames.setupOf(heroes, rng)
        val s = engine.newGame(setup) as EngineSession
        tally.heroes += setup.seats.map { it.heroId }
        var n = 0
        while (true) {
            val d = s.pending ?: break
            if (SimultaneousWindow.keyOf(s) != null) {
                playWindow(s, rng, tally)
            } else {
                assertThat(s.answer(d.id, RandomGames.choose(d, rng))).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            check(++n < MAX_STEPS) { "Game $game did not end: $setup" }
        }
        tally.games.incrementAndGet()
    }

    @Test
    fun `every forecast is the real question, over random games and all heroes`() {
        val all = engine.playableHeroes.toList()
        assertThat(all.size).isAtLeast(32)
        val tally = Tally()
        val pool = Executors.newFixedThreadPool(THREADS)
        val failures = ConcurrentHashMap.newKeySet<Throwable>()
        RandomGames.deal(Random(2026), GAMES).forEachIndexed { i, heroes ->
            pool.execute { runCatching { playGame(i, heroes, tally) }.onFailure { failures += it } }
        }
        pool.shutdown()
        assertThat(pool.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue()
        println(
            "FORECAST: ${tally.games} games, ${tally.windows} windows, ${tally.questions} questions " +
                "(${tally.levelQuestions} level-up), ${tally.heroes.size} heroes",
        )
        failures.firstOrNull()?.let { throw it }
        assertThat(tally.games.get()).isEqualTo(GAMES)
        assertThat(tally.heroes).containsAtLeastElementsIn(all)
        assertThat(tally.levelQuestions.get()).isGreaterThan(0)
    }

    @Test
    fun `Emmitt's Ultimate asks his second card in the same window, on his own first answer`() {
        val s = position(engine, FOREST) {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                ultimate()
                hand("emmitt-gold", "emmitt-red-1", "emmitt-green-1")
            }
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold", "arien-red-1") }
            phase = PositionPhase.PlayCards(0)
        }
        val w0 = SimultaneousWindow.of(s)!!
        assertThat(w0.forecast("arien").question!!.options).containsExactly(
            Choice.Card("arien-gold"),
            Choice.Card("arien-red-1"),
        )
        // Arien locks first: Emmitt's questions don't change, and neither sees the other's card.
        val first = Choice.Card("emmitt-gold")
        val w1 = SimultaneousWindow.of(
            s,
            mapOf("emmitt" to listOf(first), "arien" to listOf(Choice.Card("arien-gold"))),
        )!!
        val second = w1.forecast("emmitt").question!!
        assertThat(second.options).containsExactly(
            Choice.Card("emmitt-red-1"),
            Choice.Card("emmitt-green-1"),
            Choice.Decline("Play only one card"),
        )
        assertThat(w1.locked(listOf("emmitt", "arien"))).containsExactly("arien")
        val hero = w1.forecast("emmitt").hero!!
        assertThat(hero.played?.cardId).isEqualTo("emmitt-gold")
        assertThat(hero.hand).containsExactly("emmitt-red-1", "emmitt-green-1")
        // The second card shows too, in his own view only, and now he is done.
        val w2 = SimultaneousWindow.of(s, mapOf("emmitt" to listOf(first, Choice.Card("emmitt-red-1"))))!!
        assertThat(w2.forecast("emmitt").question).isNull()
        assertThat(w2.forecast("emmitt").hero!!.alsoPlayed.map { it.cardId }).containsExactly("emmitt-red-1")
        // The engine asks the same two questions.
        assertThat(s.pending!!.copy(id = 0)).isEqualTo(w0.forecast("emmitt").question!!.copy(id = 0))
        s.answer(s.pending!!.id, first)
        assertThat(s.pending!!.copy(id = 0)).isEqualTo(second.copy(id = 0))
    }

    @Test
    fun `Emmitt, the last seat, is locked in without his fork crossing the reveal`() {
        val s = position(engine, FOREST) {
            clearMinions()
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold") }
            hero("emmitt", Team.RED, Hex(1, -2)) {
                ultimate()
                hand("emmitt-gold", "emmitt-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        val locks = mapOf("emmitt" to listOf(Choice.Card("emmitt-gold"), Choice.Decline("Play only one card")))
        val forecast = SimultaneousWindow.of(s, locks)!!.forecast("emmitt")
        assertThat(forecast.question).isNull()
        val hero = forecast.hero!!
        assertThat(hero.played?.cardId).isEqualTo("emmitt-gold")
        assertThat(hero.initiative).isNull()
        assertThat(forecast.session.view("emmitt").initiativeOrder).isEmpty()
    }

    @Test
    fun `Takahide's unlock changes only his own hand, and the seat after him is still forecast`() {
        val build = {
            position(engine, FOREST) {
                clearMinions()
                hero("takahide", Team.RED, Hex(1, -2)) {
                    level = 6
                    coins = 13
                }
                hero("arien", Team.BLUE, Hex(4, -9)) {
                    level = 1
                    coins = 1
                }
                phase = PositionPhase.EndOfRound
            }
        }
        val s = build()
        val seats = s.setup.seats.map { it.heroId }
        val w0 = SimultaneousWindow.of(s)!!
        assertThat(w0.key.group).isEqualTo(SimultaneousWindow.LEVEL)
        val arien = w0.forecast("arien").question!!
        assertThat(arien.kind).isEqualTo(DecisionKind.UPGRADE_CARD)
        // His upgrade, kept; the Ultimate unlocks with no question, in his own fork only.
        val upgrade = (w0.forecast("takahide").question!!.options.first() as Choice.Card).cardId
        val w1 = SimultaneousWindow.of(s, mapOf("takahide" to listOf(Choice.Card(upgrade))))!!
        val keep = w1.forecast("takahide").question!!
        assertThat(keep.kind).isEqualTo(DecisionKind.KEEP_UPGRADE)
        val locks = mapOf("takahide" to listOf(Choice.Card(upgrade), keep.options.first()))
        val w2 = SimultaneousWindow.of(s, locks)!!
        assertThat(w2.forecast("takahide").question).isNull()
        assertThat(w2.locked(seats)).containsExactly("takahide")
        val hero = w2.forecast("takahide").hero!!
        assertThat(hero.level).isEqualTo(8)
        assertThat(hero.ultimate).isTrue()
        // "(You now have a total hand size of 6 cards.)"
        assertThat(hero.hand).hasSize(6)
        assertThat(hero.handCount).isEqualTo(6)
        // The same in the engine, and Arien gets the very question he was told about.
        s.answer(s.pending!!.id, Choice.Card(upgrade))
        s.answer(s.pending!!.id, keep.options.first())
        assertThat(s.pending!!.copy(id = 0)).isEqualTo(arien.copy(id = 0))
        assertThat(s.view("takahide").heroes.first { it.heroId == "takahide" }).isEqualTo(hero)
    }

    @Test
    fun `an all-pass turn does not cross the window`() {
        val s = position(engine, FOREST) {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { hand("wasp-gold", exactly = true) }
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold", exactly = true) }
            phase = PositionPhase.TurnStart
        }
        s.answer(s.pending!!.id, Choice.Card("wasp-gold"))
        s.answer(s.pending!!.id, Choice.Card("arien-gold"))
        while (s.state.turn == 1) s.answer(s.pending!!.id, s.pending!!.options.last())
        // Turn 2: both hands empty, a Pass each; a Pass must end at the edge of its own turn.
        assertThat(s.pending!!.options).containsExactly(Choice.Pass)
        val key = SimultaneousWindow.keyOf(s)!!
        assertThat(key).isEqualTo(WindowKey(1, 2, SimultaneousWindow.PLAY))
        val seats = listOf("wasp", "arien")
        val locks = seats.associateWith { listOf(Choice.Pass) }
        val w = SimultaneousWindow.of(s, locks)!!
        assertThat(w.open(seats)).isEmpty()
        for (seat in seats) {
            val forecast = w.forecast(seat)
            assertThat(forecast.question).isNull()
            // The fork stands before the reveal: turn 3's questions are not this window's.
            assertThat(forecast.session.view(seat).turn).isEqualTo(2)
            assertThat(forecast.session.view(seat).round).isEqualTo(1)
        }
        // Without the halt the same answers do run straight into the next turn's PLAY_CARD.
        s.answer(s.pending!!.id, Choice.Pass)
        s.answer(s.pending!!.id, Choice.Pass)
        assertThat(s.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(SimultaneousWindow.keyOf(s)).isEqualTo(WindowKey(1, 3, SimultaneousWindow.PLAY))
    }

    @Test
    fun `a level-up followed by a new round does not cross the window`() {
        val s = position(engine, FOREST) {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -2)) { coins = 1 }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.EndOfRound
        }
        val key = SimultaneousWindow.keyOf(s)!!
        assertThat(key.group).isEqualTo(SimultaneousWindow.LEVEL)
        val seats = listOf("wasp", "arien")
        val w0 = SimultaneousWindow.of(s)!!
        assertThat(w0.open(seats)).containsExactly("wasp")
        val upgrade = w0.forecast("wasp").question!!.options.first()
        val w1 = SimultaneousWindow.of(s, mapOf("wasp" to listOf(upgrade)))!!
        val keep = w1.forecast("wasp").question!!.options.first()
        val w2 = SimultaneousWindow.of(s, mapOf("wasp" to listOf(upgrade, keep)))!!
        // Wasp is done: the next round's PLAY_CARD is not hers to be asked yet, nor anyone's.
        assertThat(w2.open(seats)).isEmpty()
        assertThat(w2.locked(seats)).containsExactly("wasp", "arien")
        val forecast = w2.forecast("wasp")
        assertThat(forecast.session.view("wasp").round).isEqualTo(key.round)
        assertThat(forecast.hero!!.level).isEqualTo(2)
        // No pity coin on the fork for the hero who did not level up, and no new round.
        assertThat(forecast.session.view("wasp").heroes.first { it.heroId == "arien" }.coins).isEqualTo(0)
        // The engine runs on through the pity coin into the new round's PLAY_CARD.
        s.answer(s.pending!!.id, upgrade)
        s.answer(s.pending!!.id, keep)
        assertThat(SimultaneousWindow.keyOf(s)).isEqualTo(WindowKey(key.round + 1, 1, SimultaneousWindow.PLAY))
        assertThat(s.view("wasp").heroes.first { it.heroId == "arien" }.coins).isEqualTo(1)
    }

    private companion object {
        const val GAMES = 1200
        const val THREADS = 3
        const val TIMEOUT_MINUTES = 25L
        const val MAX_STEPS = 30_000
    }
}
