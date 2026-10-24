package dev.quinton.goa.core.game

import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The shipped catalog, maps and counter table, read from `app/src/main/assets/catalog` like the app reads them. */
object TestData {
    private val json = Json { ignoreUnknownKeys = false }

    fun asset(name: String): String {
        val candidates = listOf("../../app/src/main/assets/catalog/$name", "app/src/main/assets/catalog/$name")
        val f =
            candidates.map(::File).firstOrNull { it.exists() }
                ?: error("Asset $name not found from ${File(".").absolutePath}")
        return f.readText()
    }

    @Serializable
    private data class MapIndex(val maps: List<String>)

    val catalog: Catalog by lazy {
        val index = json.decodeFromString(CatalogIndex.serializer(), asset("index.json"))
        val heroes = index.heroes.map { json.decodeFromString(Hero.serializer(), asset("heroes/$it.json")) }
        Catalog(index.dataVersion, index.printing, index.products, heroes)
    }

    val maps: List<BoardMap> by lazy {
        json.decodeFromString(MapIndex.serializer(), asset("maps/index.json")).maps.map {
            json.decodeFromString(BoardMap.serializer(), asset("maps/$it.json"))
        }
    }

    val matchRules: MatchRules by lazy { MatchRules.parse(asset("match-setup.json")) }

    val engine: GameEngine by lazy { GameEngine(catalog, maps, matchRules) }

    const val FOREST = "forgotten-island"
    const val RIVER = "across-the-river"

    /** The rulebook's first-game line-up (p.5 step 7): Wasp and Xargatha (Red) vs Arien and Brogan (Blue). */
    fun firstGame(
        tieBreaker: Team = Team.RED,
        length: GameLength = GameLength.QUICK,
        controller: Controller = Controller.Human,
        map: String = FOREST,
    ) = GameSetup(
        mapId = map,
        seats = listOf(
            SeatSetup("wasp", Team.RED, controller),
            SeatSetup("xargatha", Team.RED, controller),
            SeatSetup("arien", Team.BLUE, controller),
            SeatSetup("brogan", Team.BLUE, controller),
        ),
        length = length,
        tieBreaker = tieBreaker,
    )

    fun newGame(setup: GameSetup = firstGame()): EngineSession = engine.newGame(setup) as EngineSession
}

/** Answers every decision at random from its options (seeded): a random-legal player. */
class RandomPlayer(seed: Long, private val bias: Boolean = true) {
    private val rng = Random(seed)

    fun choose(d: Decision): Choice {
        if (bias) {
            // Prefer doing something over holding or declining, so random games end in reasonable time.
            if (d.kind == DecisionKind.CHOOSE_ACTION) {
                val busy = d.options.filter { (it as Choice.Action).action != ActionType.HOLD }
                if (busy.isNotEmpty() && rng.nextInt(10) < 8) return busy[rng.nextInt(busy.size)]
            }
            if (d.kind == DecisionKind.DEFEND) {
                val cards = d.options.filterIsInstance<Choice.Card>()
                if (cards.isNotEmpty() && rng.nextInt(10) < 7) return cards[rng.nextInt(cards.size)]
            }
        }
        return d.options[rng.nextInt(d.options.size)]
    }
}

/** Play [session] with [player] until the game ends or [maxDecisions]; returns the decisions answered. */
fun playOut(
    session: GameSession,
    player: RandomPlayer,
    maxDecisions: Int = 20_000,
    onDecision: (Decision) -> Unit = {},
): Int {
    var n = 0
    while (n < maxDecisions) {
        val d = session.pending ?: return n
        onDecision(d)
        val r = session.answer(d.id, player.choose(d))
        check(r is AnswerResult.Accepted) { "Rejected: $r for $d" }
        n++
    }
    return n
}
