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
     * A team size and the table it is played on. The app offers every verified map at every size and
     * starts on Forgotten Island (single lane); the rulebook requires the double lane from 8 players
     * (p.20, DOMAIN "Match setup"), so 4v4 and 5v5 are measured on both.
     */
    data class Format(val size: Int, val mapId: String, val length: GameLength = GameLength.QUICK) {
        val label: String get() = "${size}v$size" +
            if (mapId == TestData.FOREST) "" else " ${mapId.substringBefore('-')}"

        override fun toString(): String = label

        companion object {
            val TWO = Format(2, TestData.FOREST)
            val THREE = Format(3, TestData.FOREST)
            val FOUR = Format(4, TestData.FOREST)
            val FOUR_RIVER = Format(4, TestData.RIVER)
            val FIVE_RIVER = Format(5, TestData.RIVER)

            /** `2v2`, `3v3`, `4v4`, `4v4river`, `5v5river` (`AI_FORMAT`); null = 2v2. */
            fun parse(spec: String?): Format {
                if (spec.isNullOrBlank()) return TWO
                val size = spec.substringBefore('v').toInt()
                val map = if (spec.contains("river")) TestData.RIVER else TestData.FOREST
                val length = if (spec.contains("long")) GameLength.LONG else GameLength.QUICK
                return Format(size, map, length)
            }
        }
    }

    /**
     * [n] line-ups drawn from every playable hero: consecutive shuffles of the roster cut into groups
     * of 2 × [size] (fours for 2v2), so every hero is seated about equally often (the first [size] Red).
     * Seeded and stable; the 2v2 list is the one every earlier measurement used.
     */
    fun mixedLineups(n: Int, seed: Long = 20_261_024L, size: Int = 2): List<Pair<List<String>, List<String>>> {
        val heroes = TestData.engine.playableHeroes.sorted()
        val rng = Random(seed)
        val out = ArrayList<Pair<List<String>, List<String>>>()
        val group = 2 * size
        while (out.size < n) {
            for (g in heroes.shuffled(rng).chunked(group)) {
                if (g.size == group && out.size < n) out += g.take(size) to g.drop(size)
            }
        }
        return out
    }

    /** `AI_DUMP_DIR`: a game whose computer player crashed is saved there to reproduce the decision. */
    private fun dump(s: EngineSession, decisionId: Long, e: Throwable) {
        val dir = System.getenv("AI_DUMP_DIR") ?: return
        val f = java.io.File(dir, "crash-${s.setup.seats.joinToString("-") { it.heroId }}-$decisionId.json")
        f.writeText(
            kotlinx.serialization.json.Json.encodeToString(dev.quinton.goa.core.game.SavedGame.serializer(), s.save()),
        )
        println("DUMP ${f.path}: $e")
    }

    class Result(
        val winner: Team?,
        val decisions: Int,
        val rounds: Int,
        val timings: Map<DecisionKind, MutableList<Long>>,
        val session: EngineSession,
        /** Card-play times (ns) per hero of the timed team. */
        val playBySeat: Map<String, MutableList<Long>> = emptyMap(),
        /** Per timed card play: (engine answers spent in playouts, ns). */
        val playWork: List<Pair<Long, Long>> = emptyList(),
    )

    fun setup(
        red: List<String>,
        blue: List<String>,
        redLevel: AiLevel,
        blueLevel: AiLevel,
        coin: Team,
        length: GameLength = GameLength.QUICK,
        mapId: String = TestData.FOREST,
    ) = GameSetup(
        mapId,
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
        val playWork = ArrayList<Pair<Long, Long>>()
        var n = 0
        while (n < maxDecisions) {
            val d = s.pending ?: break
            val t0 = System.nanoTime()
            val w0 = (players.getValue(d.team) as? SearchAi)?.work ?: 0
            val c = try {
                players.getValue(d.team).choose(s, d)
            } catch (e: IllegalStateException) {
                dump(s, d.id, e)
                throw e
            }
            val dt = System.nanoTime() - t0
            if (timed && d.options.size > 1 && (timedTeam == null || d.team == timedTeam)) {
                timings.getOrPut(d.kind) { ArrayList() } += dt
                if (d.kind == DecisionKind.PLAY_CARD) {
                    bySeat.getOrPut(d.seat) { ArrayList() } += dt
                    val w = ((players.getValue(d.team) as? SearchAi)?.work ?: 0) - w0
                    playWork += w to dt
                }
            }
            val r = s.answer(d.id, c)
            check(r is AnswerResult.Accepted) { "Rejected $c for $d: $r" }
            n++
        }
        return Result(s.state.winner, n, s.state.round, timings, s, bySeat, playWork)
    }
}
