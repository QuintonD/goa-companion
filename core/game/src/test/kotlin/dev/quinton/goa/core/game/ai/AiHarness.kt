package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/** Plays AI-vs-AI games for the AI tests (seeded, no wall-clock cap). */
object AiHarness {
    val HEROES = listOf("wasp", "xargatha", "arien", "brogan")

    /** The three ways to split the four first-game heroes into two teams (first pair is Red). */
    val LINEUPS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("wasp", "xargatha") to listOf("arien", "brogan"),
        listOf("wasp", "arien") to listOf("xargatha", "brogan"),
        listOf("wasp", "brogan") to listOf("xargatha", "arien"),
    )

    /**
     * [n] line-ups drawn from every playable hero: consecutive shuffles of the roster cut into fours,
     * so every hero is seated about equally often (Red pair, Blue pair). Seeded and stable.
     */
    fun mixedLineups(n: Int, seed: Long = 20_261_024L): List<Pair<List<String>, List<String>>> {
        val heroes = TestData.engine.playableHeroes.sorted()
        val rng = Random(seed)
        val out = ArrayList<Pair<List<String>, List<String>>>()
        while (out.size < n) {
            for (four in heroes.shuffled(rng).chunked(4)) {
                if (four.size == 4 && out.size < n) out += four.take(2) to four.drop(2)
            }
        }
        return out
    }

    class Result(
        val winner: Team?,
        val decisions: Int,
        val rounds: Int,
        val timings: Map<DecisionKind, MutableList<Long>>,
        val session: EngineSession,
        /** Card-play times (ns) per hero of the timed team. */
        val playBySeat: Map<String, MutableList<Long>> = emptyMap(),
    )

    fun setup(
        red: List<String>,
        blue: List<String>,
        redLevel: AiLevel,
        blueLevel: AiLevel,
        coin: Team,
        length: GameLength = GameLength.QUICK,
    ) = GameSetup(
        TestData.FOREST,
        red.map { SeatSetup(it, Team.RED, Controller.Ai(redLevel)) } +
            blue.map { SeatSetup(it, Team.BLUE, Controller.Ai(blueLevel)) },
        length = length,
        tieBreaker = coin,
    )

    fun play(
        setup: GameSetup,
        players: Map<Team, AiPlayer>,
        maxDecisions: Int = 8000,
        timed: Boolean = false,
        timedTeam: Team? = null,
    ): Result {
        val s = TestData.engine.newGame(setup) as EngineSession
        val timings = LinkedHashMap<DecisionKind, MutableList<Long>>()
        val bySeat = LinkedHashMap<String, MutableList<Long>>()
        var n = 0
        while (n < maxDecisions) {
            val d = s.pending ?: break
            val t0 = System.nanoTime()
            val c = players.getValue(d.team).choose(s, d)
            val dt = System.nanoTime() - t0
            if (timed && d.options.size > 1 && (timedTeam == null || d.team == timedTeam)) {
                timings.getOrPut(d.kind) { ArrayList() } += dt
                if (d.kind == DecisionKind.PLAY_CARD) bySeat.getOrPut(d.seat) { ArrayList() } += dt
            }
            val r = s.answer(d.id, c)
            check(r is AnswerResult.Accepted) { "Rejected $c for $d: $r" }
            n++
        }
        return Result(s.state.winner, n, s.state.round, timings, s, bySeat)
    }
}
