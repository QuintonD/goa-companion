package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The four tests that make "Hidden information" in docs/ONLINE.md binding. */
class HiddenInformationTest {
    private val engine = TestRules.engine

    // ---- 1: pairs that differ only in hidden information give byte-identical frames --------------

    private fun play(s: EngineSession, drive: (TestGame) -> Unit, reviewers: List<String> = emptyList()): TestGame {
        val game = TestGame()
        game.seed(s)
        game.connectAll(s)
        drive(game)
        game.clock.advance(game.rules.reviewEveryMillis)
        for (seat in reviewers) {
            runBlocking { game.core.review(TestGame.GAME_ID, TestGame.account(seat), TurnRef(1, 1)) }
        }
        return game
    }

    /** Runs [drive] on both games of a pair, reviews included, and compares what [watchers] got, byte by byte. */
    private fun assertSameFrames(
        a: EngineSession,
        b: EngineSession,
        watchers: List<String>,
        drive: (TestGame) -> Unit,
    ) {
        val first = play(a, drive, watchers)
        val second = play(b, drive, watchers)
        for (seat in watchers) {
            val bytes = first.inbox.getValue(seat).bytes()
            assertWithMessage("frames to $seat").that(second.inbox.getValue(seat).bytes()).isEqualTo(bytes)
            assertThat(bytes.size).isGreaterThan(2)
            assertThat(first.inbox.getValue(seat).frames.filterIsInstance<ServerMessage.Reviewed>()).isNotEmpty()
        }
    }

    private fun newGame() = engine.newGame(firstGame()) as EngineSession

    @Test
    fun `different facedown plays give the others the same frames`() {
        fun plays(wasp: String, xargatha: String) = { game: TestGame ->
            game.answer("wasp", Choice.Card(wasp))
            game.answer("xargatha", Choice.Card(xargatha))
            game.answer("arien", Choice.Card("arien-gold"))
        }
        val a = play(newGame(), plays("wasp-gold", "xargatha-red-1"))
        val b = play(newGame(), plays("wasp-blue-1", "xargatha-silver"))
        for (seat in listOf("arien", "brogan")) {
            assertThat(b.inbox.getValue(seat).bytes()).isEqualTo(a.inbox.getValue(seat).bytes())
        }
    }

    @Test
    fun `a teammate sees no more of a facedown card than an enemy`() {
        fun plays(wasp: String) = { game: TestGame ->
            game.answer("xargatha", Choice.Card("xargatha-gold"))
            game.answer("wasp", Choice.Card(wasp))
        }
        val a = play(newGame(), plays("wasp-gold"))
        val b = play(newGame(), plays("wasp-blue-1"))
        assertThat(b.inbox.getValue("xargatha").bytes()).isEqualTo(a.inbox.getValue("xargatha").bytes())
    }

    @Test
    fun `a card locked out of seat order shows no more than a card played in it`() {
        val g = play(newGame(), { it.answer("brogan", Choice.Card("brogan-gold")) })
        val own = g.inbox.getValue("brogan").latest("brogan")
        val mine = own.view.heroes.first { it.heroId == "brogan" }
        assertThat(mine.played?.cardId).isEqualTo("brogan-gold")
        assertThat(mine.hand).doesNotContain("brogan-gold")
        assertThat(own.locked).containsExactly("brogan")
        // He sees nobody else as having played: the fork's placeholders stay in the fork.
        assertThat(own.view.heroes.filter { it.heroId != "brogan" }.all { it.played == null }).isTrue()
        for (seat in listOf("wasp", "xargatha", "arien")) {
            val t = g.inbox.getValue(seat).latest(seat)
            assertThat(t.locked).containsExactly("brogan")
            val brogan = t.view.heroes.first { it.heroId == "brogan" }
            assertThat(brogan.played).isNull()
            assertThat(brogan.hand).isNull()
            assertThat(brogan.handCount).isEqualTo(mine.handCount + 1)
            assertThat(encode(t)).doesNotContain("brogan-gold")
        }
    }

    /** Wasp at level 2 with the Red or the Blue card tucked: the item icon is Defense either way. */
    private fun upgraded(redUpgrade: Boolean, coins: Int = 0, endOfRound: Boolean = false) = position(engine, FOREST) {
        if (endOfRound) {
            clearMinions()
            phase = PositionPhase.EndOfRound
        }
        hero("wasp", Team.RED, Hex(-1, -2)) {
            level = 2
            this.coins = coins
            if (redUpgrade) {
                hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                items("wasp-red-2b")
            } else {
                hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                items("wasp-blue-2a")
            }
        }
        hero("xargatha", Team.RED, Hex(-1, -1))
        hero("arien", Team.BLUE, Hex(2, -3)) { this.coins = if (endOfRound) 1 else 0 }
        hero("brogan", Team.BLUE, Hex(3, -3))
    }

    @Test
    fun `a different tucked card under the same icon gives everyone else the same frames`() {
        // Two whole turns played the same way in public, Wasp's hidden cards different.
        assertSameFrames(upgraded(true), upgraded(false), listOf("xargatha", "arien", "brogan")) { game ->
            var wasp = 0
            game.drive(
                choose = { d ->
                    if (d.kind == DecisionKind.PLAY_CARD && d.seat == "wasp") {
                        Choice.Card(listOf("wasp-gold", "wasp-silver")[wasp++])
                    } else {
                        RandomGames.hold(d)
                    }
                },
                stop = { wasp == 2 && game.inbox.getValue("wasp").question("wasp")?.kind == DecisionKind.PLAY_CARD },
            )
        }
    }

    @Test
    fun `a level-up from a different hidden history gives everyone else the same frames`() {
        // Both Wasps upgrade Green and tuck the same icon; Arien levels up beside her, in another order.
        assertSameFrames(
            upgraded(true, coins = 2, endOfRound = true),
            upgraded(false, coins = 2, endOfRound = true),
            listOf("xargatha", "arien", "brogan"),
        ) { game ->
            game.answer("arien", Choice.Card("arien-red-1"))
            val keep = game.inbox.getValue("arien").question("arien")!!
            assertThat(keep.kind).isEqualTo(DecisionKind.KEEP_UPGRADE)
            game.answer("wasp", Choice.Card("wasp-green-1"))
            // The others are told a level-up, not which of its questions: that would count Wasp's cards.
            val stub = game.inbox.getValue("xargatha").latest("xargatha").decision!!
            assertThat(stub.kind).isEqualTo(DecisionKind.UPGRADE_CARD)
            game.answer("wasp", Choice.Card("wasp-green-2b"))
            game.answer("arien", keep.options.first())
            // Only now does the round start: everyone plays again.
            assertThat(game.inbox.getValue("xargatha").question("xargatha")!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        }
    }

    @Test
    fun `different hidden hands give the same frames after the reveal`() {
        fun build(red: Boolean) = position(engine, FOREST) {
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                played("wasp-silver")
                if (red) {
                    hand("wasp-gold", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                    items("wasp-red-2b")
                } else {
                    hand("wasp-gold", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                    items("wasp-blue-2a")
                }
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertSameFrames(build(true), build(false), listOf("xargatha", "arien", "brogan")) { game ->
            game.drive(
                choose = RandomGames::hold,
                stop = { game.inbox.getValue("wasp").question("wasp")?.kind == DecisionKind.PLAY_CARD },
            )
            game.answer("xargatha", game.inbox.getValue("xargatha").question("xargatha")!!.options.first())
        }
    }

    @Test
    fun `a stub carries who and what, and nothing of the question`() {
        val g = play(engine.newGame(duel()) as EngineSession, { it.answer("arien", Choice.Card("arien-gold")) })
        val ask = g.inbox.getValue("wasp").latest("wasp").decision!!
        assertThat(ask.options).isNotEmpty()
        assertThat(ask.id).isNotEqualTo(0)
        val stub = g.inbox.getValue("arien").latest("arien").decision!!
        assertThat(stub.seat).isEqualTo("wasp")
        assertThat(stub.team).isEqualTo(Team.RED)
        assertThat(stub.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(stub.id).isEqualTo(0)
        assertThat(stub.options).isEmpty()
        assertThat(stub.prompt).isEmpty()
        assertThat(stub.sourceCardId).isNull()
    }

    // ---- 2: frames from the real session equal frames from a determinized copy -------------------

    private fun table(s: EngineSession, seat: String, events: Int, locks: Map<String, List<Choice>>) =
        SeatProjector.table(
            s,
            SimultaneousWindow.of(s, locks),
            seat,
            FrameInputs("g", ticket = 1, events = s.events.takeLast(events)),
        )

    /**
     * The engine determinizes from the start of the current step, so its copy does not know what the step
     * has shown since. Both limits are in the sample, not in the frames:
     * - a question during the reveal step (Emmitt's retrieve, an effect "after playing cards"): the played
     *   cards are still facedown at the step's start, so the copy reveals other cards. That is why
     *   `PropertyTest` in core/game blanks `played`. The rest of the frame must still match.
     * - an enemy's card named earlier in this step (a defense, a discard): it may not be in the copy's hand,
     *   so the copy answers something else ("lenient" replay) and its board runs differently. Not compared.
     */
    private fun blankRevealed(view: GameView, team: Team): GameView = view.copy(
        heroes = view.heroes.map { h ->
            if (h.team == team) {
                h
            } else {
                h.copy(played = null, alsoPlayed = emptyList(), initiative = null, activeEffects = emptyList())
            }
        },
        initiativeOrder = emptyList(),
    )

    private class DeterminizedTally {
        val compared = AtomicInteger()
        val midReveal = AtomicInteger()
        val notCompared = AtomicInteger()
        val windows = AtomicInteger()
        val heroes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    private fun compareWithCopy(s: EngineSession, team: Team, d: Decision, rng: Random, tally: DeterminizedTally) {
        val det = s.determinize(team, rng.nextLong()) as EngineSession
        val reveal = s.state.step == Step.REVEAL
        if (reveal) tally.midReveal.incrementAndGet()
        val own = s.setup.seats.filter { it.team == team }.map { it.heroId }
        // In a window, the team's own answers queued too: the fork shows them, only to them.
        val locks = HashMap<String, List<Choice>>()
        val w = SimultaneousWindow.of(s)
        if (w != null) {
            tally.windows.incrementAndGet()
            for (seat in own.filter { it != d.seat && rng.nextBoolean() }) {
                w.forecast(seat).question?.let { locks[seat] = listOf(it.options.first()) }
            }
        }
        for (seat in own) {
            val real = table(s, seat, det.events.size, locks)
            val copy = table(det, seat, det.events.size, locks)
            if (reveal) {
                val strip = { t: ServerMessage.Table ->
                    t.copy(view = blankRevealed(t.view, team), events = emptyList())
                }
                assertThat(encode(strip(copy))).isEqualTo(encode(strip(real)))
            } else {
                assertWithMessage("seat $seat at $d").that(encode(copy)).isEqualTo(encode(real))
            }
            tally.compared.incrementAndGet()
        }
    }

    private fun determinizedGame(i: Int, heroes: List<String>, tally: DeterminizedTally) {
        val rng = Random(i.toLong())
        val setup = RandomGames.setupOf(heroes, rng)
        tally.heroes += heroes
        val teams = setup.seats.associate { it.heroId to it.team }
        val s = engine.newGame(setup) as EngineSession
        val history = ArrayList<Pair<String, Choice>>()
        while (true) {
            val d = s.pending ?: break
            if (rng.nextInt(COMPARE_ONE_IN) == 0) {
                // The answers of this step, by the engine's own count of what it has committed.
                val inStep = history.takeLast((d.id - s.state.decisions).toInt())
                // In a window the team not being asked counts too: its table can't tell what the others hold.
                val viewers = if (SimultaneousWindow.keyOf(s) != null) Team.entries else listOf(d.team)
                for (team in viewers) {
                    if (inStep.none { (seat, c) -> teams[seat] != team && c is Choice.Card }) {
                        compareWithCopy(s, team, d, rng, tally)
                    } else {
                        tally.notCompared.incrementAndGet()
                    }
                }
            }
            val c = RandomGames.choose(d, rng)
            s.answer(d.id, c)
            history += d.seat to c
            check(history.size < MAX_ANSWERS) { "Game $i did not end" }
        }
    }

    @Test
    fun `frames from the real session equal frames from a determinized copy, for each seat of that team`() {
        val tally = DeterminizedTally()
        val failures = ConcurrentHashMap.newKeySet<Throwable>()
        val pool = Executors.newFixedThreadPool(THREADS)
        RandomGames.deal(Random(56), DETERMINIZED_GAMES).forEachIndexed { i, heroes ->
            pool.execute { runCatching { determinizedGame(i, heroes, tally) }.onFailure { failures += it } }
        }
        pool.shutdown()
        assertThat(pool.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue()
        println(
            "DETERMINIZED: ${tally.compared} frames compared, ${tally.windows} windows, ${tally.midReveal} during " +
                "a reveal, ${tally.notCompared} questions not compared, ${tally.heroes.size} heroes",
        )
        failures.firstOrNull()?.let { throw it }
        assertThat(tally.heroes).containsAtLeastElementsIn(engine.playableHeroes)
        assertThat(tally.compared.get()).isGreaterThan(MIN_COMPARED)
    }

    // ---- 3: every card in an event is in the spectator view just before or just after it ---------

    private fun openCards(hero: HeroView): Set<String> =
        (listOfNotNull(hero.played) + hero.alsoPlayed + hero.resolved + hero.discard).mapNotNull { it.cardId }.toSet() +
            hero.activeEffects + hero.extras.filter { it.open }.flatMap { it.cardIds.orEmpty() }

    private fun openCards(view: GameView): Set<String> = view.heroes.flatMap(::openCards).toSet()

    private fun cardsIn(e: GameEvent): Set<String> = when (e) {
        is GameEvent.CardsRevealed -> e.played.values.filterNotNull().toSet()
        is GameEvent.ActionChosen -> setOf(e.cardId)
        is GameEvent.Defended -> setOfNotNull(e.cardId)
        is GameEvent.CardsChanged -> (e.cards + e.discarded).toSet()
        else -> emptySet()
    }

    /**
     * A card discarded faceup and taken back within one run of the engine (Tali's Commune with Spirits into
     * Guardian Spirit, "retrieve it"): public in between, but no view between two answers can show it.
     */
    private fun shownInRun(run: List<GameEvent>): Set<String> {
        val changes = run.filterIsInstance<GameEvent.CardsChanged>()
        return changes.flatMapIndexed { i, e ->
            e.discarded.filter { c -> changes.drop(i + 1).any { later -> c in later.cards } }
        }.toSet()
    }

    /** Printed names in [text], longest first, so "Control Gravity" isn't also a card named "Control". */
    private fun namesIn(text: String, names: List<String>): List<String> {
        var rest = text
        val found = ArrayList<String>()
        for (name in names) {
            val whole = Regex("\\b" + Regex.escape(name) + "\\b")
            if (whole.containsMatchIn(rest)) {
                found += name
                rest = whole.replace(rest, " ")
            }
        }
        return found
    }

    @Test
    fun `every card in an event is in the spectator view just before or just after it`() {
        val master = Random(3)
        var events = 0
        var cards = 0
        var named = 0
        val heroes = HashSet<String>()
        RandomGames.deal(master, EVENT_GAMES).forEachIndexed { i, seated ->
            val setup = RandomGames.setupOf(seated, master)
            heroes += seated
            val s = engine.newGame(setup) as EngineSession
            val printed = setup.seats.flatMap { engine.data.catalogHero(it.heroId).allCards }
            val idsByName = printed.groupBy({ it.name }, { it.id })
            val names = idsByName.keys.sortedByDescending { it.length }
            val ultimates = printed.filter { it.tier == ULTIMATE_TIER }.map { it.id }.toSet()
            val player = Random(i.toLong())
            while (true) {
                val d = s.pending ?: break
                val before = openCards(s.view(null))
                val choice = RandomGames.choose(d, player)
                // Retrieve puts every open card back in hand within a run: look just before it too.
                val atRetrieve = s.forkHalting { it.step == Step.RETRIEVE }
                atRetrieve.answer(d.id, choice)
                val retrieved = openCards(atRetrieve.view(null))
                val from = s.events.size
                s.answer(d.id, choice)
                val run = s.events.drop(from)
                val seen = before + retrieved + openCards(s.view(null)) + shownInRun(run)
                for (e in run) {
                    events++
                    for (c in cardsIn(e)) {
                        cards++
                        assertWithMessage("hidden card $c in event: ${e.text}").that(seen).contains(c)
                    }
                    // "Guess that card's color, then reveal it." (Bain's A Game of Chance): the rule shows it to all.
                    if (RULE_REVEAL.containsMatchIn(e.text)) continue
                    for (name in namesIn(e.text, names)) {
                        named++
                        assertWithMessage("hidden card \"$name\" named in: ${e.text}")
                            .that(idsByName.getValue(name).any { it in seen || it in ultimates })
                            .isTrue()
                    }
                }
            }
        }
        println("EVENTS: $events events, $cards cards, $named named in text, ${heroes.size} heroes")
        assertThat(heroes).containsAtLeastElementsIn(engine.playableHeroes)
        assertThat(cards).isGreaterThan(MIN_EVENT_CARDS)
        assertThat(named).isGreaterThan(MIN_EVENT_CARDS)
    }

    // ---- 4: the code that builds frames can't name what must not leave --------------------------

    @Test
    fun `the wire types and the projector do not mention what must never leave`() {
        val banned = listOf("SavedGame", "GameState", "AnswerRecord", ".humans")
        val online = TestRules.repoFile("core/online/src/main/kotlin")
        val projector = TestRules.repoFile(
            "server/game/src/main/kotlin/dev/quinton/goa/server/game/room/SeatProjector.kt",
        )
        val sources = Files.walk(online).use { paths -> paths.filter { it.toString().endsWith(".kt") }.toList() } +
            listOf(projector)
        assertThat(sources.size).isAtLeast(2)
        for (file in sources) {
            // The code: OnlineProtocol's comments warn against one of these by name.
            val code = Files.readString(file).replace(COMMENT, "")
            for (word in banned) assertWithMessage("$word in $file").that(code).doesNotContain(word)
        }
    }

    private companion object {
        val COMMENT = Regex("(?s)/\\*.*?\\*/|//[^\\n]*")
        val RULE_REVEAL = Regex("^Bain guesses \\w+: \\w+ reveals ")
        const val DETERMINIZED_GAMES = 200
        const val COMPARE_ONE_IN = 25
        const val MIN_COMPARED = 3_000
        const val MAX_ANSWERS = 30_000
        const val THREADS = 3
        const val TIMEOUT_MINUTES = 15L
        const val EVENT_GAMES = 150
        const val MIN_EVENT_CARDS = 1_000
        const val ULTIMATE_TIER = 4
    }
}
