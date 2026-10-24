package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Seeded, mirrored tournaments (STRATEGY.md §4.5): every line-up is played twice with the levels
 * swapping teams (same seed, same coin), so hero and side strength cancel.
 */
object Tournament {
    class Score(val a: AiLevel, val b: AiLevel) {
        var aWins = 0
        var bWins = 0
        var draws = 0
        val reasons = LinkedHashMap<String, Int>()
        var rounds = 0

        /** Per hero: games in which level a played it, and how many of those a won. */
        val heroGames = sortedMapOf<String, Int>()
        val heroWins = sortedMapOf<String, Int>()

        val games: Int get() = aWins + bWins + draws
        val rate: Double get() = (aWins + 0.5 * draws) / games

        /** 95% Wilson interval of [rate]. */
        val interval: Pair<Double, Double> get() = wilson(rate, games)

        override fun toString(): String {
            val (lo, hi) = interval
            return "$a vs $b: $aWins-$bWins (draws $draws) of $games = ${"%.0f".format(rate * 100)}% " +
                "[95% ${"%.0f".format(lo * 100)}-${"%.0f".format(hi * 100)}%], " +
                "avg ${"%.1f".format(rounds.toDouble() / games)} rounds, $reasons"
        }

        /** a's win rate with each hero (the heroes the stronger level plays worst come first). */
        fun byHero(): String = heroGames.keys.sortedBy { (heroWins[it] ?: 0).toDouble() / heroGames.getValue(it) }
            .joinToString(", ") { "$it ${heroWins[it] ?: 0}/${heroGames.getValue(it)}" }
    }

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
    fun run(
        a: AiLevel,
        b: AiLevel,
        seeds: Int,
        threads: Int = 4,
        firstSeed: Int = 0,
        factory: (AiLevel, Long) -> AiPlayer = { l, s -> AiPlayers.deterministic(l, s) },
    ): Score {
        val specs = ArrayList<Spec>()
        for ((li, lineup) in AiHarness.LINEUPS.withIndex()) {
            for (coin in Team.entries) {
                for (s in firstSeed until firstSeed + seeds) {
                    specs += Spec(lineup, coin, 1000L * li + 100L * coin.ordinal + s)
                }
            }
        }
        return play(a, b, specs, threads, factory)
    }

    /**
     * [lineups] line-ups drawn from all playable heroes ([AiHarness.mixedLineups], starting at
     * [firstLineup]), the coin alternating, each mirrored: 2 × lineups games.
     */
    fun runMixed(
        a: AiLevel,
        b: AiLevel,
        lineups: Int,
        threads: Int = 4,
        firstLineup: Int = 0,
        factory: (AiLevel, Long) -> AiPlayer = { l, s -> AiPlayers.deterministic(l, s) },
    ): Score {
        val all = AiHarness.mixedLineups(firstLineup + lineups)
        val specs = (firstLineup until firstLineup + lineups).map { i ->
            Spec(all[i], if (i % 2 == 0) Team.RED else Team.BLUE, 50_000L + i)
        }
        return play(a, b, specs, threads, factory)
    }

    private fun play(
        a: AiLevel,
        b: AiLevel,
        specs: List<Spec>,
        threads: Int,
        factory: (AiLevel, Long) -> AiPlayer,
    ): Score {
        val score = Score(a, b)
        val jobs = ArrayList<Callable<Pair<AiLevel?, AiHarness.Result>>>()
        val aRed = ArrayList<Boolean>()
        for (spec in specs) {
            val (red, blue) = spec.lineup
            for (aIsRed in listOf(true, false)) {
                aRed += aIsRed
                jobs += Callable {
                    val redLevel = if (aIsRed) a else b
                    val blueLevel = if (aIsRed) b else a
                    val setup = AiHarness.setup(red, blue, redLevel, blueLevel, spec.coin)
                    val r = AiHarness.play(
                        setup,
                        mapOf(
                            Team.RED to factory(redLevel, spec.seed * 2),
                            Team.BLUE to factory(blueLevel, spec.seed * 2 + 1),
                        ),
                    )
                    val winnerLevel = when (r.winner) {
                        Team.RED -> if (aIsRed) a else b
                        Team.BLUE -> if (aIsRed) b else a
                        null -> null
                    }
                    winnerLevel to r
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for ((i, f) in pool.invokeAll(jobs).withIndex()) {
                val (w, r) = f.get()
                // With a == b (self-play), "a" is whoever played Red in the even job.
                val aWon = if (a == b) r.winner == (if (i % 2 == 0) Team.RED else Team.BLUE) else w == a
                when {
                    w == null -> score.draws++
                    aWon -> score.aWins++
                    else -> score.bWins++
                }
                val (red, blue) = specs[i / 2].lineup
                for (hero in if (aRed[i]) red else blue) {
                    score.heroGames[hero] = (score.heroGames[hero] ?: 0) + 1
                    if (w != null && aWon) score.heroWins[hero] = (score.heroWins[hero] ?: 0) + 1
                }
                val reason = r.session.view(null).winReason?.name ?: "UNFINISHED"
                score.reasons[reason] = (score.reasons[reason] ?: 0) + 1
                score.rounds += r.rounds
            }
        } finally {
            pool.shutdown()
        }
        return score
    }
}
