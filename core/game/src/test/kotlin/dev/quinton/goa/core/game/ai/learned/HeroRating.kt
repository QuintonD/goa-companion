package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The per-hero bench's rating fit (ADR-063, AI.md "Stream D"): a logistic model of who wins a game from
 * the seats, P(Red wins) = σ(side + coin + Σ_Red (θ_h + [a plays Red] δ_h) − Σ_Blue (θ_h + [a plays Blue] δ_h)).
 * θ_h is how strong hero h is with these players (team-mates, enemies, side and coin controlled); δ_h is
 * what side a gains over side b when it plays h (its uplift, per seat). Ridge [ridge] on θ and δ (a prior
 * of N(0, 1/ridge) in logits) fixes θ's common level and steadies rare heroes; intervals from the inverse
 * Hessian (normal approximation).
 */
internal object HeroRating {
    /** One game: the line-up, whether side a played Red, whether Red had the coin, Red's points (1, ½, 0). */
    class Game(val red: List<String>, val blue: List<String>, val aRed: Boolean, val coinRed: Boolean, val redPoints: Double) {
        fun line(): String = "${red.joinToString("+")}|${blue.joinToString("+")}|$aRed|$coinRed|$redPoints"

        companion object {
            fun parse(line: String): Game {
                val p = line.split("|")
                return Game(p[0].split("+"), p[1].split("+"), p[2].toBoolean(), p[3].toBoolean(), p[4].toDouble())
            }

            fun of(played: Teams.Played): Game {
                val (red, blue) = played.spec.lineup
                val points = when (played.result.winner) {
                    Team.RED -> 1.0
                    Team.BLUE -> 0.0
                    null -> 0.5
                }
                return Game(red, blue, played.aRed, played.spec.coin == Team.RED, points)
            }
        }
    }

    class Row(val hero: String, val games: Int, val theta: Double, val thetaSe: Double, val delta: Double, val deltaSe: Double)

    class Fit(val rows: List<Row>, val side: Double, val sideSe: Double, val coin: Double, val coinSe: Double, val games: Int)

    fun read(files: List<File>): List<Game> = files.flatMap { f -> f.readLines().filter { it.isNotBlank() }.map(Game::parse) }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun fit(games: List<Game>, ridge: Double = 1.0): Fit {
        val heroes = games.flatMap { it.red + it.blue }.distinct().sorted()
        val index = heroes.withIndex().associate { (i, h) -> h to i }
        val h = heroes.size
        val n = 2 + 2 * h
        // Sparse rows: (indices, values).
        val rows = games.map { g ->
            val idx = ArrayList<Int>()
            val v = ArrayList<Double>()
            idx += 0
            v += 1.0
            idx += 1
            v += if (g.coinRed) 1.0 else -1.0
            for (x in g.red) {
                idx += 2 + index.getValue(x)
                v += 1.0
                if (g.aRed) {
                    idx += 2 + h + index.getValue(x)
                    v += 1.0
                }
            }
            for (x in g.blue) {
                idx += 2 + index.getValue(x)
                v += -1.0
                if (!g.aRed) {
                    idx += 2 + h + index.getValue(x)
                    v += -1.0
                }
            }
            idx.toIntArray() to v.toDoubleArray()
        }
        val prior = DoubleArray(n) { if (it < 2) TINY else ridge }
        val p = DoubleArray(n)
        var hess = Array(n) { DoubleArray(n) }
        for (iteration in 0 until ITERATIONS) {
            val grad = DoubleArray(n) { -prior[it] * p[it] }
            hess = Array(n) { i -> DoubleArray(n).also { r -> r[i] = prior[i] } }
            for ((k, row) in rows.withIndex()) {
                val (idx, v) = row
                var eta = 0.0
                for (j in idx.indices) eta += v[j] * p[idx[j]]
                val mu = 1.0 / (1.0 + exp(-eta))
                val w = mu * (1 - mu)
                val y = games[k].redPoints
                for (a in idx.indices) {
                    grad[idx[a]] += (y - mu) * v[a]
                    for (b in idx.indices) hess[idx[a]][idx[b]] += w * v[a] * v[b]
                }
            }
            val step = solve(hess, grad)
            var change = 0.0
            for (i in 0 until n) {
                p[i] += step[i]
                change = maxOf(change, abs(step[i]))
            }
            if (change < CONVERGED) break
        }
        val cov = invert(hess)
        val seated = heroes.associateWith { x -> games.count { x in it.red || x in it.blue } }
        val out = heroes.mapIndexed { i, x ->
            Row(x, seated.getValue(x), p[2 + i], sqrt(cov[2 + i][2 + i]), p[2 + h + i], sqrt(cov[2 + h + i][2 + h + i]))
        }
        return Fit(out, p[0], sqrt(cov[0][0]), p[1], sqrt(cov[1][1]), games.size)
    }

    /** The fit as a table: heroes by rating, each with its uplift; θ and δ in logits and in Elo. */
    fun report(fit: Fit, a: String, b: String): String {
        val elo = 400 / ln(10.0)
        val sb = StringBuilder()
        sb.appendLine(
            "Hero rating fit: ${fit.games} games, $a (side a) against $b; side (Red) %+.3f ± %.3f, coin %+.3f ± %.3f logits"
                .format(fit.side, 1.96 * fit.sideSe, fit.coin, 1.96 * fit.coinSe),
        )
        sb.appendLine("θ = the hero's strength with these players (Elo per seat; a team adds three), δ = what $a gains over $b with it")
        sb.appendLine(
            "%-12s %6s %22s %22s %18s".format("hero", "games", "θ Elo [95%]", "δ Elo [95%]", "δ ≈ points at even"),
        )
        for (r in fit.rows.sortedBy { it.theta }) {
            sb.appendLine(
                "%-12s %6d %+7.0f [%+5.0f, %+5.0f] %+7.0f [%+5.0f, %+5.0f] %+6.1f [%+5.1f, %+5.1f]".format(
                    r.hero, r.games,
                    r.theta * elo, (r.theta - 1.96 * r.thetaSe) * elo, (r.theta + 1.96 * r.thetaSe) * elo,
                    r.delta * elo, (r.delta - 1.96 * r.deltaSe) * elo, (r.delta + 1.96 * r.deltaSe) * elo,
                    points(r.delta), points(r.delta - 1.96 * r.deltaSe), points(r.delta + 1.96 * r.deltaSe),
                ),
            )
        }
        return sb.toString()
    }

    /** A logit gain as win-rate points at an even game. */
    private fun points(logit: Double): Double = 100 * (1 / (1 + exp(-logit)) - 0.5)

    private fun solve(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val inv = invert(m)
        return DoubleArray(rhs.size) { i -> inv[i].indices.sumOf { j -> inv[i][j] * rhs[j] } }
    }

    /** Gauss–Jordan with partial pivoting (the matrix is symmetric positive definite). */
    private fun invert(m: Array<DoubleArray>): Array<DoubleArray> {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n).also { r -> m[i].copyInto(r); r[n + i] = 1.0 } }
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            val t = a[c]
            a[c] = a[piv]
            a[piv] = t
            val d = a[c][c]
            for (k in 0 until 2 * n) a[c][k] /= d
            for (r in 0 until n) {
                if (r == c || a[r][c] == 0.0) continue
                val f = a[r][c]
                for (k in 0 until 2 * n) a[r][k] -= f * a[c][k]
            }
        }
        return Array(n) { i -> a[i].copyOfRange(n, 2 * n) }
    }

    private const val ITERATIONS = 30
    private const val CONVERGED = 1e-7
    private const val TINY = 1e-6
}
