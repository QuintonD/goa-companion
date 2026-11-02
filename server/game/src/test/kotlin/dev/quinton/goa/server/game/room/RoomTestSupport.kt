package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.InMemoryGameStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

const val FOREST = "forgotten-island"
const val RIVER = "across-the-river"

/** The shipped catalog, read as the server reads it; Gradle runs tests from `server/game`. */
object TestRules {
    private val assets: Path =
        listOf("../../app/src/main/assets", "app/src/main/assets").map { Paths.get(it) }.first { Files.isDirectory(it) }

    /** A file of the repository. */
    fun repoFile(path: String): Path =
        listOf(Paths.get("../..", path), Paths.get(path)).first { Files.exists(it) }

    val engine: GameEngine by lazy { RulesLoader.engine(assets) }
}

class FakeClock(var now: Long = 1_000_000) {
    fun advance(millis: Long) {
        now += millis
    }
}

object TestMapper : MatchMapper {
    override fun matchOf(game: GameRow, seats: List<GameSeatRow>, winner: Team, reason: WinReason, finishedAt: Long) =
        Match(
            id = game.id,
            createdAt = game.createdAt,
            playedAt = finishedAt,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            map = game.mapId,
            winner = winner,
            participants = seats.map { Participant(it.accountId, it.heroId, it.team, it.handicap) },
        )
}

fun encode(message: ServerMessage): String = OnlineProtocol.json.encodeToString(ServerMessage.serializer(), message)

/** What one person received, in order. */
class Inbox : SeatSink {
    val frames = ArrayList<ServerMessage>()
    var link: SeatLink? = null

    override fun send(message: ServerMessage) {
        frames += message
    }

    fun tables(seat: String): List<ServerMessage.Table> =
        frames.filterIsInstance<ServerMessage.Table>().filter { it.view.viewer == seat }

    fun latest(seat: String): ServerMessage.Table = tables(seat).last()

    /** This seat's question, if its latest table asks one. */
    fun question(seat: String): Decision? = tables(seat).lastOrNull()?.decision?.takeIf { it.options.isNotEmpty() }

    val ended: ServerMessage.End? get() = frames.filterIsInstance<ServerMessage.End>().lastOrNull()

    fun bytes(): List<String> = frames.map(::encode)
}

/** A game with one person per seat, on a fake clock; every seat's account is `acct-<hero>`. */
class TestGame(
    val store: GameStore = InMemoryGameStore(),
    val clock: FakeClock = FakeClock(),
    val rules: RoomRules = RoomRules(),
    val rulesBuild: String = "build-1",
    engine: GameEngine = TestRules.engine,
) {
    val core = RoomCore(
        engine = engine,
        store = store,
        rulesBuild = rulesBuild,
        mapper = TestMapper,
        names = { it.removePrefix("acct-") },
        rules = rules,
        now = { clock.now },
        computer = Dispatchers.Unconfined,
        newId = { GAME_ID },
    )
    val inbox = LinkedHashMap<String, Inbox>()

    fun start(setup: GameSetup, ranked: Boolean = false): String = runBlocking {
        val players = setup.seats.map { SeatPlayer(it.heroId, account(it.heroId), it.heroId, it.team) }
        core.start("ROOM", setup, players, ranked)
    }

    /** A game from a built position, as if it were found in the store on a restart. */
    fun seed(session: EngineSession, ranked: Boolean = false) = runBlocking {
        store.createGame(row(session, ranked), seatRows(session))
        core.restore()
    }

    fun row(session: EngineSession, ranked: Boolean) = GameRow(
        id = GAME_ID,
        roomCode = "ROOM",
        setup = session.setup,
        start = session.save(),
        rulesBuild = rulesBuild,
        engineVersion = 1,
        ranked = ranked,
        mapId = session.setup.mapId,
        length = session.setup.length,
        createdAt = clock.now,
    )

    fun seatRows(session: EngineSession) = session.setup.seats.mapIndexed { i, s ->
        GameSeatRow(GAME_ID, s.heroId, account(s.heroId), s.team, s.handicap, i)
    }

    fun connect(seat: String, haveEvents: Int = 0): Inbox = runBlocking {
        val box = inbox.getOrPut(seat) { Inbox() }
        box.link = core.connect(GAME_ID, account(seat), haveEvents, box)
        box
    }

    fun connectAll(session: EngineSession) = session.setup.seats.forEach { connect(it.heroId) }

    fun connectAll(setup: GameSetup) = setup.seats.forEach { connect(it.heroId) }

    fun answer(seat: String, choice: Choice) = runBlocking {
        val q = checkNotNull(inbox.getValue(seat).question(seat)) { "$seat has no question" }
        core.answer(GAME_ID, account(seat), q.id, choice)
    }

    fun tick() = runBlocking { core.tick() }

    /** Everyone with a question now, in seat order. */
    fun asked(): List<String> = inbox.keys.filter { inbox.getValue(it).question(it) != null }

    /**
     * Answers with [choose] until nobody has a question or [stop] says so; [pick] says who goes next among
     * those asked, so a test can answer in any order.
     */
    fun drive(
        choose: (Decision) -> Choice,
        pick: (List<String>) -> String = { it.first() },
        stop: () -> Boolean = { false },
        maxAnswers: Int = MAX_ANSWERS,
    ): Int {
        var n = 0
        while (n < maxAnswers && !stop()) {
            val who = asked()
            if (who.isEmpty()) return n
            val seat = pick(who)
            answer(seat, choose(inbox.getValue(seat).question(seat)!!))
            n++
        }
        return n
    }

    companion object {
        const val GAME_ID = "game-1"
        const val MAX_ANSWERS = 40_000

        fun account(seat: String) = "acct-$seat"
    }
}

/** The first-game line-up (p.5 step 7). */
fun firstGame(tieBreaker: Team = Team.RED, map: String = FOREST) = GameSetup(
    mapId = map,
    seats = listOf(
        SeatSetup("wasp", Team.RED, Controller.Human),
        SeatSetup("xargatha", Team.RED, Controller.Human),
        SeatSetup("arien", Team.BLUE, Controller.Human),
        SeatSetup("brogan", Team.BLUE, Controller.Human),
    ),
    tieBreaker = tieBreaker,
)

fun duel(red: String = "wasp", blue: String = "arien", map: String = FOREST) = GameSetup(
    mapId = map,
    seats = listOf(SeatSetup(red, Team.RED, Controller.Human), SeatSetup(blue, Team.BLUE, Controller.Human)),
    tieBreaker = Team.RED,
)

/** Random games over all heroes, every table size the mode allows (docs/ONLINE.md "A room"). */
object RandomGames {
    private val sizes = listOf(1, 1, 1, 2, 2, 2, 3, 3, 4, 5)

    /** One random line-up. */
    fun setup(rng: Random): GameSetup {
        val size = sizes[rng.nextInt(sizes.size)]
        val heroes = TestRules.engine.playableHeroes.shuffled(rng).take(size * 2)
        return setupOf(heroes, rng)
    }

    /** Line-ups for [games] games: every hero is dealt in turn, so none is left to chance. */
    fun deal(rng: Random, games: Int): List<List<String>> {
        val all = TestRules.engine.playableHeroes.toList()
        var deck = all.shuffled(rng)
        var dealt = 0
        return List(games) {
            val seats = sizes[rng.nextInt(sizes.size)] * 2
            val heroes = ArrayList<String>()
            while (heroes.size < seats) {
                if (dealt == deck.size) {
                    deck = all.shuffled(rng)
                    dealt = 0
                }
                val hero = deck[dealt++]
                if (hero !in heroes) heroes += hero
            }
            heroes
        }
    }

    fun setupOf(heroes: List<String>, rng: Random): GameSetup {
        val size = heroes.size / 2
        return GameSetup(
            mapId = if (size >= 4) RIVER else FOREST,
            seats = heroes.mapIndexed { i, h ->
                SeatSetup(h, if (i % 2 == 0) Team.RED else Team.BLUE, Controller.Human)
            },
            length = if (size < 4 && rng.nextInt(4) == 0) GameLength.LONG else GameLength.QUICK,
            tieBreaker = if (rng.nextBoolean()) Team.RED else Team.BLUE,
        )
    }

    /** A random-legal player, leaning towards doing something, so random games end in reasonable time. */
    fun choose(d: Decision, rng: Random): Choice {
        if (d.kind == DecisionKind.CHOOSE_ACTION) {
            val busy = d.options.filter { (it as Choice.Action).action != ActionType.HOLD }
            if (busy.isNotEmpty() && rng.nextInt(10) < 8) return busy[rng.nextInt(busy.size)]
        }
        if (d.kind == DecisionKind.DEFEND) {
            val cards = d.options.filterIsInstance<Choice.Card>()
            if (cards.isNotEmpty() && rng.nextInt(10) < 7) return cards[rng.nextInt(cards.size)]
        }
        return d.options[rng.nextInt(d.options.size)]
    }

    /** Nothing happens but the cards being played: every hero holds. */
    fun hold(d: Decision): Choice = when {
        d.kind == DecisionKind.CHOOSE_ACTION && Choice.Action(ActionType.HOLD) in d.options ->
            Choice.Action(ActionType.HOLD)

        else -> d.options.firstOrNull { it is Choice.Decline } ?: d.options.first()
    }
}
