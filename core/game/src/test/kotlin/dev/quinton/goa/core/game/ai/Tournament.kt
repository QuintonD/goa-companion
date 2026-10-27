package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Seeded, mirrored tournaments (STRATEGY.md §4.5): every line-up is played twice with the players
 * swapping teams (same seed, same coin), so hero and side strength cancel.
 */
object Tournament {
    /** One contestant: a name for the report and its players (seeded per team and game). */
    class Side(val label: String, val factory: (Long) -> AiPlayer) {
        override fun toString(): String = label
    }

    fun strength(s: Int): Side = Side("S$s") { seed -> AiPlayers.deterministic(s, seed) }

    fun level(l: AiLevel): Side = Side(l.name) { seed -> AiPlayers.deterministic(l, seed) }

    class Score(val a: Side, val b: Side) {
        var aWins = 0
        var bWins = 0
        var draws = 0
        val reasons = LinkedHashMap<String, Int>()
        var rounds = 0

        /** How each side won (win reason → games). */
        val aWinsBy = sortedMapOf<String, Int>()
        val bWinsBy = sortedMapOf<String, Int>()

        /** Heroes of each side defeated, summed over the games. */
        var aDefeats = 0
        var bDefeats = 0

        /** Games that failed (AI_KEEP_GOING): their mirrored pairs are left out of the score. */
        val failures = ArrayList<String>()

        /** Wall time of the games (ms, summed) and the longest one. */
        var wallMs = 0L
        var wallMaxMs = 0L

        /** Per hero: games in which side a played it, and how many of those a won. */
        val heroGames = sortedMapOf<String, Int>()
        val heroWins = sortedMapOf<String, Int>()

        /** One line per game: line-up, the side a played, the winner and how. */
        val lines = ArrayList<String>()

        val games: Int get() = aWins + bWins + draws
        val rate: Double get() = (aWins + 0.5 * draws) / games

        /** 95% Wilson interval of [rate]. */
        val interval: Pair<Double, Double> get() = wilson(rate, games)

        override fun toString(): String {
            val (lo, hi) = interval
            return "$a vs $b: $aWins-$bWins (draws $draws) of $games = ${"%.1f".format(rate * 100)}% " +
                "[95% ${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}%], " +
                "avg ${"%.1f".format(rounds.toDouble() / games)} rounds, $reasons"
        }

        /** Defeats per game per side, how each side won, and the wall time per game. */
        fun details(): String = "defeats/game $a ${"%.2f".format(aDefeats.toDouble() / games)} " +
            "$b ${"%.2f".format(bDefeats.toDouble() / games)}; $a won by $aWinsBy; $b won by $bWinsBy; " +
            "wall ${"%.1f".format(wallMs / 1000.0 / games)} s/game (max ${"%.0f".format(wallMaxMs / 1000.0)} s)" +
            if (failures.isEmpty()) "" else "; FAILED ${failures.size}: ${failures.joinToString(" | ")}"

        /** Adds one finished game: side a played Red when [aRed]; [aWon] null for an unfinished game. */
        fun record(
            lineup: Pair<List<String>, List<String>>,
            aRed: Boolean,
            aWon: Boolean?,
            r: AiHarness.Result,
            ms: Long,
        ) {
            when (aWon) {
                null -> draws++
                true -> aWins++
                false -> bWins++
            }
            val (red, blue) = lineup
            for (hero in if (aRed) red else blue) {
                heroGames[hero] = (heroGames[hero] ?: 0) + 1
                if (aWon == true) heroWins[hero] = (heroWins[hero] ?: 0) + 1
            }
            val reason = r.session.view(null).winReason?.name ?: "UNFINISHED"
            reasons[reason] = (reasons[reason] ?: 0) + 1
            rounds += r.rounds
            when (aWon) {
                true -> aWinsBy[reason] = (aWinsBy[reason] ?: 0) + 1
                false -> bWinsBy[reason] = (bWinsBy[reason] ?: 0) + 1
                null -> Unit
            }
            val aTeam = if (aRed) Team.RED else Team.BLUE
            val teams = r.session.setup.seats.associate { it.heroId to it.team }
            for (e in r.session.events) {
                if (e !is GameEvent.Defeated) continue
                val t = teams[e.unitId] ?: continue
                if (t == aTeam) aDefeats++ else bDefeats++
            }
            wallMs += ms
            wallMaxMs = maxOf(wallMaxMs, ms)
            val result = when (aWon) {
                null -> "draw"
                true -> "$a won"
                false -> "$b won"
            }
            lines += "${red.joinToString("+")} vs ${blue.joinToString("+")}: $a ${if (aRed) "Red" else "Blue"}, " +
                "$result by $reason in round ${r.rounds}"
        }

        /** a's win rate with each hero (the heroes a plays worst come first). */
        fun byHero(): String = heroGames.keys.sortedBy { (heroWins[it] ?: 0).toDouble() / heroGames.getValue(it) }
            .joinToString(", ") { "$it ${heroWins[it] ?: 0}/${heroGames.getValue(it)}" }
    }

    private val KEEP_GOING = System.getenv("AI_KEEP_GOING") != null

    /** `AI_PROGRESS=1`: a line per finished game (long runs). */
    private val PROGRESS = System.getenv("AI_PROGRESS") != null

    fun wilson(p: Double, games: Int): Pair<Double, Double> {
        val n = games.toDouble()
        val z = 1.96
        val c = (p + z * z / (2 * n)) / (1 + z * z / n)
        val h = z * sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / (1 + z * z / n)
        return (c - h) to (c + h)
    }

    /** One mirrored pair of games: a line-up, the coin and the seed. */
    class Spec(val lineup: Pair<List<String>, List<String>>, val coin: Team, val seed: Long)

    /** [seeds] games per first-game line-up and coin side, each mirrored: 3 × 2 × 2 × seeds games. */
    fun run(a: Side, b: Side, seeds: Int, threads: Int = 4, firstSeed: Int = 0): Score {
        val specs = ArrayList<Spec>()
        for ((li, lineup) in AiHarness.LINEUPS.withIndex()) {
            for (coin in Team.entries) {
                for (s in firstSeed until firstSeed + seeds) {
                    specs += Spec(lineup, coin, 1000L * li + 100L * coin.ordinal + s)
                }
            }
        }
        return play(a, b, specs, threads)
    }

    /**
     * [lineups] line-ups drawn from all playable heroes ([AiHarness.mixedLineups], starting at
     * [firstLineup]), the coin alternating, each mirrored: 2 × lineups games.
     */
    fun runMixed(
        a: Side,
        b: Side,
        lineups: Int,
        threads: Int = 4,
        firstLineup: Int = 0,
        format: AiHarness.Format = AiHarness.Format.TWO,
    ): Score {
        val all = AiHarness.mixedLineups(firstLineup + lineups, size = format.size)
        val specs = (firstLineup until firstLineup + lineups).map { i ->
            Spec(all[i], if (i % 2 == 0) Team.RED else Team.BLUE, 50_000L + i)
        }
        return play(a, b, specs, threads, format)
    }

    private fun play(
        a: Side,
        b: Side,
        specs: List<Spec>,
        threads: Int,
        format: AiHarness.Format = AiHarness.Format.TWO,
    ): Score {
        val score = Score(a, b)
        // Each job: did side a win (null = unfinished), and the game.
        val jobs = ArrayList<Callable<Triple<Boolean?, AiHarness.Result, Long>>>()
        val aRed = ArrayList<Boolean>()
        for (spec in specs) {
            val (red, blue) = spec.lineup
            for (aIsRed in listOf(true, false)) {
                aRed += aIsRed
                jobs += Callable {
                    // The controllers only mark the seats as computer seats; the players decide.
                    val setup = AiHarness.setup(
                        red,
                        blue,
                        AiLevel.NORMAL,
                        AiLevel.NORMAL,
                        spec.coin,
                        format.length,
                        format.mapId,
                    )
                    val t0 = System.nanoTime()
                    val r = AiHarness.play(
                        setup,
                        mapOf(
                            Team.RED to (if (aIsRed) a else b).factory(spec.seed * 2),
                            Team.BLUE to (if (aIsRed) b else a).factory(spec.seed * 2 + 1),
                        ),
                    )
                    val aWon = r.winner?.let { (it == Team.RED) == aIsRed }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    if (PROGRESS) {
                        println(
                            "GAME ${red + blue} $a ${if (aIsRed) "Red" else "Blue"}: a won $aWon in ${ms / 1000} s",
                        )
                    }
                    Triple(aWon, r, ms)
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = pool.invokeAll(jobs)
            // AI_KEEP_GOING: a failed game (an engine crash) drops its mirrored pair and is reported
            // instead of ending the run (long measurements); by default it fails the run.
            val failedPairs = HashSet<Int>()
            for ((i, f) in futures.withIndex()) {
                try {
                    f.get()
                } catch (e: java.util.concurrent.ExecutionException) {
                    val spec = specs[i / 2]
                    val side = if (aRed[i]) "Red" else "Blue"
                    val game = "${spec.lineup}, coin ${spec.coin}, seed ${spec.seed}, $a $side"
                    if (!KEEP_GOING) throw IllegalStateException("Game failed: $game", e)
                    failedPairs += i / 2
                    score.failures += "$game: ${e.cause}"
                }
            }
            for ((i, f) in futures.withIndex()) {
                if (i / 2 in failedPairs) continue
                val (aWon, r, ms) = f.get()
                score.record(specs[i / 2].lineup, aRed[i], aWon, r, ms)
            }
        } finally {
            pool.shutdown()
        }
        return score
    }
}
