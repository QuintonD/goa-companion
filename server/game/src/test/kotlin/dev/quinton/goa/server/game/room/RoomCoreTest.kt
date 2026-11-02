package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.AnswerOrigin
import dev.quinton.goa.server.game.store.GameResult
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.InMemoryGameStore
import dev.quinton.goa.server.game.store.RunningGame
import dev.quinton.goa.server.game.store.StoredAnswer
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** A store that keeps what happened, for the questions the in-memory one can't answer. */
private class WatchedStore(private val inner: GameStore = InMemoryGameStore()) : GameStore by inner {
    val answers = ArrayList<StoredAnswer>()
    val voids = ArrayList<VoidReason>()
    val results = ArrayList<GameResult>()
    var failAppend = false
    var onAppend: () -> Unit = {}

    override suspend fun appendAnswer(answer: StoredAnswer) {
        if (failAppend) error("disk full")
        onAppend()
        answers += answer
        inner.appendAnswer(answer)
    }

    override suspend fun void(gameId: String, reason: VoidReason, at: Long) {
        voids += reason
        inner.void(gameId, reason, at)
    }

    override suspend fun finish(gameId: String, result: GameResult, match: Match?) {
        results += result
        inner.finish(gameId, result, match)
    }

    suspend fun rewrite(transform: (RunningGame) -> RunningGame): GameStore {
        val copy = InMemoryGameStore()
        for (g in inner.runningGames().map(transform)) {
            copy.createGame(g.game, g.seats)
            g.answers.forEach { copy.appendAnswer(it) }
            g.locks.forEach { copy.putLock(it) }
        }
        return copy
    }
}

class RoomCoreTest {
    private val engine = TestRules.engine

    private fun answers(g: TestGame, script: List<Pair<String, String>>) {
        for ((seat, card) in script) g.answer(seat, Choice.Card(card))
    }

    // ---- 4: any answer order gives the same game as seat order -----------------------------------

    /** Answers depend on the seat, the question and the game's seed, never on the order they come in. */
    private fun chooser(seed: Long, count: HashMap<String, Int>) = { d: Decision ->
        val n = count.merge(d.seat, 1, Int::plus)!!
        RandomGames.choose(d, Random(seed xor d.seat.hashCode().toLong() xor n.toLong()))
    }

    @Test
    fun `any answer order gives the same game as seat order`() {
        val master = Random(404)
        repeat(ORDER_GAMES) { i ->
            val setup = RandomGames.setup(master)
            val seed = master.nextLong()

            // The reference: the engine alone, asked and answered in seat order.
            val reference = engine.newGame(setup) as EngineSession
            val refChoose = chooser(seed, HashMap())
            while (true) {
                val d = reference.pending ?: break
                // The room answers a lone Pass itself: only real questions come out of the chooser.
                reference.answer(d.id, if (d.options == listOf<Choice>(Choice.Pass)) Choice.Pass else refChoose(d))
            }

            val order = Random(seed)
            val store = WatchedStore()
            val game = TestGame(store = store)
            game.start(setup)
            game.connectAll(setup)
            val choose = chooser(seed, HashMap())
            game.drive(choose = choose, pick = { it[order.nextInt(it.size)] })

            val ended = game.inbox.values.first().ended
            assertWithMessage("game $i").that(ended).isNotNull()
            val expected = reference.view(null)
            assertThat(ended!!.winner).isEqualTo(expected.winner)
            assertThat(ended.reason).isEqualTo(expected.winReason)
            val played = store.answers.map { it.record.seat to it.record.choice }
            val saved = EngineSaves.answers(reference)
            assertThat(played).isEqualTo(saved.map { it.seat to it.choice })
            assertThat(store.answers.map { it.record.decisionId }).isEqualTo(saved.map { it.decisionId })
            // Every event, delivered to every seat exactly once, in the one order the engine made them.
            val log = reference.events.map { encodeEvent(it) }
            for ((seat, box) in game.inbox) {
                val received = box.tables(seat).flatMap { it.events }.map { encodeEvent(it) }
                assertThat(received).isEqualTo(log)
            }
        }
    }

    // ---- 5: a restart rebuilds identical frames --------------------------------------------------

    private fun comparable(t: ServerMessage.Table) = encode(t.copy(seq = 0, clock = null, presence = emptyMap()))

    /** After a restart a person connects again and gets the same table as before it, events and all. */
    private fun assertRestartIsSeamless(game: TestGame, setupSeats: List<String>) = runBlocking {
        val before = setupSeats.associateWith { seat ->
            val box = Inbox()
            val link = game.core.connect(TestGame.GAME_ID, TestGame.account(seat), 0, box)
            link?.close()
            comparable(box.latest(seat))
        }
        val again = TestGame(store = game.store, clock = game.clock)
        again.core.restore()
        assertThat(again.core.runningCount).isEqualTo(1)
        for (seat in setupSeats) {
            val box = Inbox()
            val link = again.core.connect(TestGame.GAME_ID, TestGame.account(seat), 0, box)
            assertThat(link).isNotNull()
            assertWithMessage("frame of $seat after the restart").that(comparable(box.latest(seat)))
                .isEqualTo(before.getValue(seat))
        }
        again
    }

    @Test
    fun `a restart in the middle of a window rebuilds identical frames and keeps the locks`() {
        val game = TestGame()
        game.start(firstGame())
        game.connectAll(firstGame())
        // Out of seat order: Brogan and Arien are locks in the store, Wasp has really played.
        game.answer("brogan", Choice.Card("brogan-gold"))
        game.answer("arien", Choice.Card("arien-red-1"))
        game.answer("wasp", Choice.Card("wasp-silver"))
        val again = assertRestartIsSeamless(game, listOf("wasp", "xargatha", "arien", "brogan"))
        // The locks hold: Xargatha is the only one asked, and her answer reveals everyone's real cards.
        val box = Inbox()
        runBlocking { again.core.connect(TestGame.GAME_ID, TestGame.account("xargatha"), 0, box) }
        assertThat(box.latest("xargatha").locked).containsExactly("wasp", "arien", "brogan")
        val q = box.question("xargatha")!!
        runBlocking {
            again.core.answer(TestGame.GAME_ID, TestGame.account("xargatha"), q.id, Choice.Card("xargatha-gold"))
        }
        val played = box.latest(
            "xargatha",
        ).events.filterIsInstance<dev.quinton.goa.core.game.GameEvent.CardsRevealed>().single().played
        assertThat(played["brogan"]).isEqualTo("brogan-gold")
        assertThat(played["arien"]).isEqualTo("arien-red-1")
        assertThat(played["wasp"]).isEqualTo("wasp-silver")
    }

    @Test
    fun `a restart in the middle of a card rebuilds identical frames`() {
        val game = TestGame()
        val setup = firstGame()
        game.start(setup)
        game.connectAll(setup)
        var moved = 0
        // Random play until someone is deep in a card: the engine's checkpoint is behind the display.
        val rng = Random(9)
        game.drive(
            choose = { RandomGames.choose(it, rng) },
            stop = {
                val d = game.inbox.values.mapNotNull { box ->
                    box.frames.filterIsInstance<ServerMessage.Table>().lastOrNull()?.decision
                }
                val deep = d.any { it.kind == DecisionKind.CHOOSE_SPACE || it.kind == DecisionKind.DEFEND }
                if (deep) moved++
                deep && moved > 3
            },
        )
        assertThat(moved).isGreaterThan(3)
        assertRestartIsSeamless(game, setup.seats.map { it.heroId })
    }

    @Test
    fun `a crash between saving an answer and clearing its lock does not apply it twice`() = runBlocking<Unit> {
        val game = TestGame()
        game.start(duel())
        game.connectAll(duel())
        game.answer("arien", Choice.Card("arien-gold"))
        val locksBefore = game.store.runningGames().single().locks
        assertThat(locksBefore).hasSize(1)
        // As if the process died just after Wasp's answer ran into Arien's lock being applied and saved.
        val store = game.store
        val g = store.runningGames().single()
        val s = engine.restoreFrom(g.game.start, g.answers.map { it.record }) as EngineSession
        val first = s.pending!!
        s.answer(first.id, Choice.Card("wasp-gold"))
        val second = s.pending!!
        val hash0 = Replay.begin(engine.restoreFrom(g.game.start, emptyList()) as EngineSession)
        val check = engine.restoreFrom(g.game.start, emptyList()) as EngineSession
        val r1 = check.answer(first.id, Choice.Card("wasp-gold")) as dev.quinton.goa.core.game.AnswerResult.Accepted
        val hash1 = GameHashes.events(hash0, r1.events)
        store.appendAnswer(
            StoredAnswer(
                g.game.id,
                1,
                AnswerRecord(first.id, "wasp", Choice.Card("wasp-gold")),
                AnswerOrigin.HUMAN,
                0,
                hash1,
                GameHashes.state(check, r1.events),
            ),
        )
        val r2 = check.answer(second.id, Choice.Card("arien-gold")) as dev.quinton.goa.core.game.AnswerResult.Accepted
        val hash2 = GameHashes.events(hash1, r2.events)
        store.appendAnswer(
            StoredAnswer(
                g.game.id,
                2,
                AnswerRecord(second.id, "arien", Choice.Card("arien-gold")),
                AnswerOrigin.HUMAN,
                0,
                hash2,
                GameHashes.state(check, r2.events),
            ),
        )
        assertThat(store.runningGames().single().locks).hasSize(1)

        val again = TestGame(store = store, clock = game.clock)
        again.core.restore()
        assertThat(store.runningGames().single().locks).isEmpty()
        assertThat(store.runningGames().single().answers).hasSize(2)
    }

    @Test
    fun `a saved lock that no longer fits is dropped, logged, and the seat asked again`() = runBlocking<Unit> {
        val game = TestGame()
        game.start(duel())
        game.connectAll(duel())
        game.answer("arien", Choice.Card("arien-gold"))
        val g = game.store.runningGames().single()
        val lock = g.locks.single()
        game.store.clearLocks(g.game.id, "arien")
        game.store.putLock(lock.copy(choice = Choice.Card("not-in-her-hand")))

        val again = TestGame(store = game.store, clock = game.clock)
        again.core.restore()
        assertThat(game.store.runningGames().single().locks).isEmpty()
        val box = again.connect("arien")
        assertThat(box.question("arien")!!.options).contains(Choice.Card("arien-gold"))
        assertThat(box.latest("arien").locked).isEmpty()
    }

    // ---- 6: a rules change ------------------------------------------------------------------------

    private fun playSome(store: GameStore): TestGame {
        val game = TestGame(store = store)
        game.start(firstGame())
        game.connectAll(firstGame())
        val rng = Random(66)
        game.drive(choose = { RandomGames.choose(it, rng) }, maxAnswers = 60)
        return game
    }

    @Test
    fun `a changed build whose replay matches carries on`() = runBlocking<Unit> {
        val store = WatchedStore()
        val first = playSome(store)
        val before = comparable(first.inbox.getValue("wasp").latest("wasp").copy(eventsFrom = 0, events = emptyList()))
        val fixed = TestGame(store = store, clock = first.clock, rulesBuild = "build-2")
        fixed.core.restore()
        assertThat(fixed.core.runningCount).isEqualTo(1)
        assertThat(store.voids).isEmpty()
        val box = fixed.connect("wasp")
        assertThat(box.frames.filterIsInstance<ServerMessage.End>()).isEmpty()
        assertThat(comparable(box.latest("wasp").copy(eventsFrom = 0, events = emptyList()))).isEqualTo(before)
    }

    /** The catalog with one printed number changed: the rules fix that silently changes a game's past. */
    private fun changedEngine(): GameEngine {
        val base = TestRules.engine.data.catalog
        val heroes = base.heroes.map { hero ->
            hero.copy(
                cards = hero.cards.map {
                    if (it.initiative !=
                        null
                    ) {
                        it.copy(initiative = it.initiative!! + 1)
                    } else {
                        it
                    }
                },
            )
        }
        val maps: List<BoardMap> = TestRules.engine.data.maps.values.toList()
        return GameEngine(CatalogFiles.catalog(indexOf(base), heroes), maps)
    }

    private fun indexOf(c: dev.quinton.goa.core.model.catalog.Catalog) =
        dev.quinton.goa.core.model.catalog.CatalogIndex(
            dataVersion = c.dataVersion,
            printing = c.printing,
            products = c.products,
            heroes = c.heroes.map { it.id },
        )

    @Test
    fun `a changed build whose replay differs voids the game, in words, unrated`() = runBlocking<Unit> {
        val store = WatchedStore()
        val first = playSome(store)
        assertThat(store.answers.size).isGreaterThan(10)
        val fixed = TestGame(store = store, clock = first.clock, rulesBuild = "build-2", engine = changedEngine())
        fixed.core.restore()
        assertThat(fixed.core.runningCount).isEqualTo(0)
        assertThat(store.voids).containsExactly(VoidReason.RULES_CHANGED)
        assertThat(store.matches()).isEmpty()
        // Told when they come back, once each.
        assertThat(fixed.core.gamesOf(TestGame.account("wasp"))).containsExactly(TestGame.GAME_ID)
        val box = fixed.connect("wasp")
        val end = box.ended!!
        assertThat(end.voided).isEqualTo(VoidReason.RULES_CHANGED)
        assertThat(end.rated).isFalse()
        assertThat(end.winner).isNull()
        assertThat(fixed.core.gamesOf(TestGame.account("wasp"))).isEmpty()
        assertThat(fixed.core.connect(TestGame.GAME_ID, TestGame.account("wasp"), 0, Inbox())).isNull()
    }

    @Test
    fun `an answer that no longer fits voids the game, with no exception reaching the server`() = runBlocking<Unit> {
        val store = WatchedStore()
        val first = playSome(store)
        val tampered = store.rewrite { g ->
            g.copy(
                answers = g.answers.map { a ->
                    if (a.seq ==
                        3L
                    ) {
                        a.copy(record = a.record.copy(decisionId = 9_999))
                    } else {
                        a
                    }
                },
            )
        }
        val fixed = TestGame(store = tampered, clock = first.clock, rulesBuild = "build-2")
        fixed.core.restore()
        assertThat(fixed.core.runningCount).isEqualTo(0)
        assertThat(tampered.runningGames()).isEmpty()
    }

    @Test
    fun `a state hash that differs voids the game even if the text matches`() = runBlocking<Unit> {
        val store = WatchedStore()
        val first = playSome(store)
        assertThat(store.answers.mapNotNull { it.stateHash }).isNotEmpty()
        val tampered = store.rewrite { g ->
            g.copy(answers = g.answers.map { a -> if (a.stateHash != null) a.copy(stateHash = "changed") else a })
        }
        val fixed = TestGame(store = tampered, clock = first.clock, rulesBuild = first.rulesBuild)
        fixed.core.restore()
        assertThat(fixed.core.runningCount).isEqualTo(0)
    }

    // ---- 7: clocks ---------------------------------------------------------------------------------

    private val short = RoomRules(playCardMillis = 60_000, otherMillis = 45_000, disconnectedMillis = 15_000)

    @Test
    fun `clock expiry is answered by the computer, one question at a time, and bounded`() {
        val store = WatchedStore()
        val game = TestGame(store = store, rules = short)
        game.start(duel())
        game.connectAll(duel())
        game.clock.advance(short.playCardMillis - 1)
        game.tick()
        assertThat(store.answers).isEmpty()
        game.clock.advance(1)
        game.tick()
        // Both clocks ran out: one answer each, and the turn has moved on past the reveal.
        assertThat(store.answers.take(2).map { it.origin }).containsExactly(AnswerOrigin.CLOCK, AnswerOrigin.CLOCK)
        assertThat(game.inbox.getValue("wasp").latest("wasp").presence["wasp"]).isEqualTo(Presence.HERE)

        // Nobody touches a thing: the game still finishes, by the rules, on the clock alone.
        var ticks = 0
        var worst = 0L
        val started = System.nanoTime()
        while (game.core.runningCount > 0) {
            game.clock.advance(short.playCardMillis)
            val t0 = System.nanoTime()
            game.tick()
            worst = maxOf(worst, (System.nanoTime() - t0) / 1_000_000)
            assertThat(++ticks).isLessThan(50_000)
        }
        val seconds = (System.nanoTime() - started) / 1e9
        val end = game.inbox.getValue("wasp").ended!!
        println(
            "CLOCK: ${store.answers.size} answers on the clock in ${"%.1f".format(seconds)} s, worst tick $worst ms",
        )
        assertThat(end.winner).isNotNull()
        assertThat(end.voided).isNull()
        assertThat(store.answers.map { it.origin }.toSet()).containsNoneIn(listOf(AnswerOrigin.HUMAN))
        // Two seats, so at most two static answers per tick: no search on this clock.
        assertWithMessage("worst tick in ms").that(worst).isLessThan(WORST_TICK_MILLIS)
    }

    @Test
    fun `a disconnected seat gets the short clock, is taken over, and can be returned to`() {
        val store = WatchedStore()
        val game = TestGame(store = store, rules = short)
        val setup = duel()
        game.start(setup)
        game.connectAll(setup)
        game.inbox.getValue("arien").link!!.close()
        val away = game.inbox.getValue("wasp").latest("wasp")
        assertThat(away.presence["arien"]).isEqualTo(Presence.AWAY)
        // Arien's clock is now short, Wasp's is not, and she is told nothing of his: only hers shows.
        assertThat(away.clock!!.seat).isEqualTo("wasp")
        game.clock.advance(short.disconnectedMillis)
        game.tick()
        assertThat(store.answers.size + store.runningLocks()).isEqualTo(1)
        val taken = game.inbox.getValue("wasp").latest("wasp")
        assertThat(taken.presence["arien"]).isEqualTo(Presence.COMPUTER)
        assertThat(taken.locked).containsExactly("arien")
        // During the takeover the seat is still a person's in the view.
        assertThat(taken.view.heroes.first { it.heroId == "arien" }.controller).isEqualTo(Controller.Human)

        // Back at any moment.
        val back = game.connect("arien")
        assertThat(back.frames.filterIsInstance<ServerMessage.GameStart>()).isNotEmpty()
        assertThat(game.inbox.getValue("wasp").latest("wasp").presence["arien"]).isEqualTo(Presence.HERE)
        game.answer("wasp", game.inbox.getValue("wasp").question("wasp")!!.options.first())
        val rng = Random(1)
        game.drive(choose = { RandomGames.choose(it, rng) }, maxAnswers = 6)
        assertThat(store.answers.drop(2).map { it.origin }).contains(AnswerOrigin.HUMAN)
        assertThat(
            store.answers.filter {
                it.record.seat == "arien"
            }.drop(1).map { it.origin },
        ).doesNotContain(AnswerOrigin.CLOCK)
    }

    private suspend fun WatchedStore.runningLockCount(): Int = runningGames().single().locks.size

    private fun WatchedStore.runningLocks(): Int = runBlocking { runningLockCount() }

    @Test
    fun `everyone away pauses the clocks and 24 hours abandons the game, unrated`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store, rules = short)
        game.start(duel(), ranked = true)
        game.connectAll(duel())
        game.inbox.values.forEach { it.link!!.close() }
        game.clock.advance(short.allAwayPauseMillis + 1)
        game.tick()
        val answered = store.answers.size + store.runningLocks()
        // Paused: an hour goes by and nothing is answered.
        repeat(6) {
            game.clock.advance(10 * 60_000)
            game.tick()
        }
        assertThat(store.answers.size + store.runningLocks()).isEqualTo(answered)
        assertThat(game.core.runningCount).isEqualTo(1)

        // Someone comes back: the question has its whole clock again.
        val box = Inbox()
        val link = game.core.connect(TestGame.GAME_ID, TestGame.account("wasp"), 0, box)
        val deadline = box.latest("wasp").clock!!.deadlineAt
        assertThat(deadline).isEqualTo(game.clock.now + short.playCardMillis)
        game.tick()
        assertThat(store.answers.size + store.runningLocks()).isEqualTo(answered)

        // And goes again: this time for good, and a day later the game is void.
        link!!.close()
        game.clock.advance(short.abandonMillis + 1)
        game.tick()
        assertThat(game.core.runningCount).isEqualTo(0)
        assertThat(store.voids).containsExactly(VoidReason.ABANDONED)
    }

    @Test
    fun `nobody back within 24 hours abandons the game, unrated, even a ranked one`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store, rules = short)
        game.start(duel(), ranked = true)
        game.connectAll(duel())
        game.inbox.values.forEach { it.link!!.close() }
        game.clock.advance(short.abandonMillis + 1)
        game.tick()
        assertThat(game.core.runningCount).isEqualTo(0)
        assertThat(store.voids).containsExactly(VoidReason.ABANDONED)
        assertThat(store.matches()).isEmpty()
        val box = game.connect("wasp")
        assertThat(box.ended!!.voided).isEqualTo(VoidReason.ABANDONED)
        assertThat(box.ended!!.rated).isFalse()
    }

    @Test
    fun `a restart gives the current question its whole clock`() = runBlocking<Unit> {
        val game = TestGame(rules = short)
        game.start(duel())
        game.connectAll(duel())
        game.clock.advance(50_000)
        val again = TestGame(store = game.store, clock = game.clock, rules = short)
        again.core.restore()
        // Nobody has reconnected, yet they are not on the disconnected clock.
        game.clock.advance(short.disconnectedMillis + 1)
        again.tick()
        assertThat(again.store.runningGames().single().answers).isEmpty()
        game.clock.advance(short.playCardMillis)
        again.tick()
        assertThat(again.store.runningGames().single().answers).isNotEmpty()
    }

    @Test
    fun `the computer answering for a seat can't see what anyone else played`() {
        fun computerAnswer(wasp: String, xargatha: String, arien: String): List<Choice> {
            val store = WatchedStore()
            val game = TestGame(store = store, rules = short)
            game.start(firstGame())
            game.connectAll(firstGame())
            // Out of seat order on purpose: some of these are real plays, some still locks.
            game.answer("arien", Choice.Card(arien))
            game.answer("wasp", Choice.Card(wasp))
            game.answer("xargatha", Choice.Card(xargatha))
            game.clock.advance(short.playCardMillis)
            game.tick()
            return store.answers.filter { it.record.seat == "brogan" && it.origin == AnswerOrigin.CLOCK }
                .map { it.record.choice }
        }
        val reference = computerAnswer("wasp-gold", "xargatha-gold", "arien-gold")
        assertThat(reference).hasSize(1)
        for ((wasp, xargatha, arien) in listOf(
            Triple("wasp-blue-1", "xargatha-gold", "arien-gold"),
            Triple("wasp-gold", "xargatha-silver", "arien-gold"),
            Triple("wasp-gold", "xargatha-gold", "arien-red-1"),
            Triple("wasp-silver", "xargatha-red-1", "arien-silver"),
        )) {
            assertThat(computerAnswer(wasp, xargatha, arien)).isEqualTo(reference)
        }
    }

    // ---- 8: leaving -------------------------------------------------------------------------------

    @Test
    fun `leave hands the seat to the computer and the game finishes with a real result`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store)
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        // Played, then left: a team that never answered at all wouldn't be rated.
        game.answer("arien", game.inbox.getValue("arien").question("arien")!!.options.first())
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))
        assertThat(game.core.gamesOf(TestGame.account("arien"))).isEmpty()
        assertThat(game.core.connect(TestGame.GAME_ID, TestGame.account("arien"), 0, Inbox())).isNull()
        val wasp = game.inbox.getValue("wasp")
        assertThat(wasp.latest("wasp").presence["arien"]).isEqualTo(Presence.COMPUTER)
        assertThat(wasp.latest("wasp").locked).containsExactly("arien")
        assertThat(
            wasp.latest("wasp").view.heroes.first {
                it.heroId == "arien"
            }.controller,
        ).isEqualTo(Controller.Human)

        val rng = Random(8)
        var answered = 0
        while (game.core.runningCount > 0) {
            val q = wasp.question("wasp")
            if (q != null) {
                game.answer("wasp", RandomGames.choose(q, rng))
            } else {
                game.tick()
            }
            assertThat(++answered).isLessThan(TestGame.MAX_ANSWERS)
        }
        val end = wasp.ended!!
        assertThat(end.winner).isNotNull()
        assertThat(end.rated).isTrue()
        assertThat(store.results.single().rated).isTrue()
        assertThat(store.matches().single().winner).isEqualTo(end.winner)
        // The computer's answers stay in the record as the computer's.
        assertThat(store.answers.filter { it.record.seat == "arien" }.map { it.origin }.drop(1).toSet())
            .containsNoneIn(listOf(AnswerOrigin.HUMAN))
    }

    @Test
    fun `leaving before answering anything leaves a game that isn't rated`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store)
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))
        val wasp = game.inbox.getValue("wasp")
        val rng = Random(8)
        var answered = 0
        while (game.core.runningCount > 0) {
            val q = wasp.question("wasp")
            if (q != null) game.answer("wasp", RandomGames.choose(q, rng)) else game.tick()
            assertThat(++answered).isLessThan(TestGame.MAX_ANSWERS)
        }
        assertThat(wasp.ended!!.winner).isNotNull()
        assertThat(wasp.ended!!.rated).isFalse()
        assertThat(store.matches()).isEmpty()
    }

    @Test
    fun `who has answered survives a restart, and the end says what was decided`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store)
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.answer("wasp", game.inbox.getValue("wasp").question("wasp")!!.options.first())
        game.answer("arien", game.inbox.getValue("arien").question("arien")!!.options.first())

        val again = TestGame(store = store, clock = game.clock)
        again.core.restore()
        again.connectAll(setup)
        val rng = Random(3)
        again.drive(choose = { RandomGames.choose(it, rng) })
        assertThat(again.inbox.getValue("wasp").ended!!.rated).isTrue()
    }

    @Test
    fun `a game that ended unheard is one to go back to but doesn't count as one being played`() = runBlocking<Unit> {
        val game = TestGame(rules = short)
        game.start(duel())
        game.clock.advance(short.abandonMillis + 1)
        game.tick()
        val wasp = TestGame.account("wasp")
        assertThat(game.core.gamesOf(wasp)).containsExactly(TestGame.GAME_ID)
        assertThat(game.core.runningGamesOf(wasp)).isEmpty()
    }

    @Test
    fun `when everyone has left the computer finishes the game a little per tick`() = runBlocking<Unit> {
        val store = WatchedStore()
        val game = TestGame(store = store)
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.core.leave(TestGame.GAME_ID, TestGame.account("wasp"))
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))
        var ticks = 0
        while (game.core.runningCount > 0) {
            game.tick()
            assertThat(++ticks).isLessThan(5_000)
        }
        assertThat(ticks).isGreaterThan(1)
        // Nobody played it: nothing to rate.
        assertThat(store.matches()).isEmpty()
        assertThat(store.results.single().rated).isFalse()
    }

    // ---- answers and frames -------------------------------------------------------------------------

    @Test
    fun `an answer is on disk before any frame about it goes out`() {
        val store = WatchedStore()
        val game = TestGame(store = store)
        game.start(duel())
        game.connectAll(duel())
        val sizes = HashMap<String, Int>()
        store.onAppend = { game.inbox.forEach { (seat, box) -> sizes[seat] = box.frames.size } }
        val waspFrames = game.inbox.getValue("wasp").frames.size
        game.answer("wasp", Choice.Card("wasp-gold"))
        assertThat(sizes.getValue("wasp")).isEqualTo(waspFrames)
        assertThat(sizes.getValue("arien")).isEqualTo(game.inbox.getValue("arien").frames.size - 1)
    }

    @Test
    fun `a failed save leaves the game as it was and says so`() {
        val store = WatchedStore()
        val game = TestGame(store = store)
        game.start(duel())
        game.connectAll(duel())
        store.failAppend = true
        game.answer("wasp", Choice.Card("wasp-gold"))
        val ack = game.inbox.getValue("wasp").frames.filterIsInstance<ServerMessage.Ack>().last()
        assertThat(ack.accepted).isFalse()
        assertThat(game.inbox.getValue("wasp").question("wasp")).isNotNull()
        store.failAppend = false
        game.answer("wasp", Choice.Card("wasp-gold"))
        assertThat(game.inbox.getValue("wasp").frames.filterIsInstance<ServerMessage.Ack>().last().accepted).isTrue()
    }

    @Test
    fun `tickets are per seat, a repeat is harmless, and a wrong one is refused`() = runBlocking<Unit> {
        val game = TestGame()
        game.start(duel())
        game.connectAll(duel())
        val wasp = game.inbox.getValue("wasp")
        val arien = game.inbox.getValue("arien")
        val ticket = wasp.question("wasp")!!.id
        val other = arien.question("arien")!!.id
        assertThat(other).isNotEqualTo(ticket)
        assertThat(wasp.question("wasp")!!.options).isNotEmpty()
        // Not your seat's ticket.
        game.core.answer(TestGame.GAME_ID, TestGame.account("wasp"), other, Choice.Card("wasp-gold"))
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Ack>().last().accepted).isFalse()
        // A card that is not an option.
        game.core.answer(TestGame.GAME_ID, TestGame.account("wasp"), ticket, Choice.Card("arien-gold"))
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Ack>().last().accepted).isFalse()
        game.core.answer(TestGame.GAME_ID, TestGame.account("wasp"), ticket, Choice.Card("wasp-gold"))
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Ack>().last().accepted).isTrue()
        val answers = game.store.runningGames().single().answers.size
        game.core.answer(TestGame.GAME_ID, TestGame.account("wasp"), ticket, Choice.Card("wasp-gold"))
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Ack>().last().accepted).isTrue()
        assertThat(game.store.runningGames().single().answers.size).isEqualTo(answers)
    }

    @Test
    fun `a frame goes out only when something changes, with sequence numbers per seat`() {
        val game = TestGame()
        game.start(firstGame())
        game.connectAll(firstGame())
        val before = game.inbox.getValue("brogan").tables("brogan").size
        game.tick()
        game.tick()
        assertThat(game.inbox.getValue("brogan").tables("brogan").size).isEqualTo(before)
        game.answer("wasp", Choice.Card("wasp-gold"))
        game.answer("xargatha", Choice.Card("xargatha-gold"))
        val seqs = game.inbox.getValue("brogan").tables("brogan").map { it.seq }
        assertThat(seqs).isInStrictOrder()
    }

    @Test
    fun `a reconnect gets only the events it lacks`() {
        val game = TestGame()
        game.start(duel())
        game.connectAll(duel())
        val wasp = game.inbox.getValue("wasp")
        val total = wasp.tables("wasp").flatMap { it.events }.size
        assertThat(total).isGreaterThan(1)
        val again = Inbox()
        runBlocking { game.core.connect(TestGame.GAME_ID, TestGame.account("wasp"), total - 1, again) }
        val t = again.latest("wasp")
        assertThat(t.eventsFrom).isEqualTo(total - 1)
        assertThat(t.events).hasSize(1)
    }

    @Test
    fun `reviews are limited to one every five seconds per person`() = runBlocking<Unit> {
        val game = TestGame()
        game.start(duel())
        game.connectAll(duel())
        val wasp = game.inbox.getValue("wasp")
        game.core.review(TestGame.GAME_ID, TestGame.account("wasp"), null)
        game.core.review(TestGame.GAME_ID, TestGame.account("wasp"), null)
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Reviewed>()).hasSize(1)
        game.clock.advance(game.rules.reviewEveryMillis)
        game.core.review(TestGame.GAME_ID, TestGame.account("wasp"), null)
        assertThat(wasp.frames.filterIsInstance<ServerMessage.Reviewed>()).hasSize(2)
    }

    @Test
    fun `a table frame stays within its size budget`() {
        val rng = Random(12)
        var worst = 0
        var total = 0L
        var count = 0
        // The largest tables going, 5v5 on the double-lane map, every hero's cards in time.
        repeat(BUDGET_GAMES) {
            val heroes = TestRules.engine.playableHeroes.shuffled(rng).take(10)
            val setup = RandomGames.setupOf(heroes, rng)
            val game = TestGame()
            game.start(setup)
            game.connectAll(setup)
            game.drive(choose = { RandomGames.choose(it, rng) })
            for ((seat, box) in game.inbox) {
                // The first table on a connection carries the whole log; the budget is for the play after it.
                for (t in box.tables(seat).drop(1)) {
                    val size = encode(t).encodeToByteArray().size
                    worst = maxOf(worst, size)
                    total += size
                    count++
                }
            }
        }
        println("TABLE: mean ${total / count} bytes, worst $worst bytes over $count frames")
        assertWithMessage("mean frame").that(total / count).isLessThan(TYPICAL_FRAME_BYTES)
        assertWithMessage("worst frame").that(worst).isLessThan(WORST_FRAME_BYTES)
    }

    private companion object {
        const val ORDER_GAMES = 60
        const val BUDGET_GAMES = 3
        const val WORST_TICK_MILLIS = 5_000L
        const val TYPICAL_FRAME_BYTES = 8_000L
        const val WORST_FRAME_BYTES = 20_000
    }
}

/** The answers the engine holds, for comparing with what the room saved. */
private object EngineSaves {
    fun answers(s: EngineSession): List<AnswerRecord> {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val payload = json.parseToJsonElement(s.save().payload)
        val array = (payload as kotlinx.serialization.json.JsonObject).getValue("answers")
        return json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(AnswerRecord.serializer()),
            array,
        )
    }
}

private fun encodeEvent(e: dev.quinton.goa.core.game.GameEvent): String =
    dev.quinton.goa.core.online.OnlineProtocol.json.encodeToString(dev.quinton.goa.core.game.GameEvent.serializer(), e)
