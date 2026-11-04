package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The per-hero bench's rating fit (ADR-063, AI.md "Stream D"): a logistic model of who wins a game from
 * the seats, P(Red wins) = σ(side + coin + Σ_Red (θ_h + [a plays Red] (α + δ_h))
 * − Σ_Blue (θ_h + [a plays Blue] (α + δ_h))).
 * θ_h is how strong hero h is with these players (team-mates, enemies, side and coin controlled); α is what
 * side a gains over side b per seat, and α + δ_h what it gains when it plays h (that hero's uplift). Ridge
 * [ridge] on θ and δ (a prior of N(0, 1/ridge) in logits) fixes θ's common level and draws a rare hero's
 * uplift towards α; intervals from the inverse Hessian (normal approximation).
 */
internal object HeroRating {
    /** One game: the line-up, whether side a played Red, whether Red had the coin, Red's points (1, ½, 0). */
    class Game(
        val red: List<String>,
        val blue: List<String>,
        val aRed: Boolean,
        val coinRed: Boolean,
        val redPoints: Double,
    ) {
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

    class Row(
        val hero: String,
        val games: Int,
        val theta: Double,
        val thetaSe: Double,
        val delta: Double,
        val deltaSe: Double,
    )

    class Fit(
        val rows: List<Row>,
        val side: Double,
        val sideSe: Double,
        val coin: Double,
        val coinSe: Double,
        val arm: Double,
        val armSe: Double,
        val data: List<Game>,
        val heroes: List<String>,
    ) {
        val games: Int get() = data.size
    }

    fun read(files: List<File>): List<Game> = files.flatMap { f ->
        f.readLines().filter { it.isNotBlank() }.map(Game::parse)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun fit(games: List<Game>, ridge: Double = 1.0): Fit {
        val heroes = games.flatMap { it.red + it.blue }.distinct().sorted()
        val index = heroes.withIndex().associate { (i, h) -> h to i }
        val h = heroes.size
        val n = 3 + 2 * h
        // Sparse rows: (indices, values).
        val rows = games.map { g ->
            val idx = ArrayList<Int>()
            val v = ArrayList<Double>()
            idx += 0
            v += 1.0
            idx += 1
            v += if (g.coinRed) 1.0 else -1.0
            idx += 2
            v += (if (g.aRed) 1.0 else -1.0) * g.red.size
            for (x in g.red) {
                idx += 3 + index.getValue(x)
                v += 1.0
                if (g.aRed) {
                    idx += 3 + h + index.getValue(x)
                    v += 1.0
                }
            }
            for (x in g.blue) {
                idx += 3 + index.getValue(x)
                v += -1.0
                if (!g.aRed) {
                    idx += 3 + h + index.getValue(x)
                    v += -1.0
                }
            }
            idx.toIntArray() to v.toDoubleArray()
        }
        val prior = DoubleArray(n) { if (it < 3) TINY else ridge }
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

        // Only contrasts are identified (a common shift of every θ cancels; α trades against the mean δ),
        // so θ is reported against the mean hero and the arm as α + mean δ, each with its variance wᵀ C w.
        fun combo(w: DoubleArray): Pair<Double, Double> {
            var mean = 0.0
            var variance = 0.0
            for (i in 0 until n) {
                if (w[i] == 0.0) continue
                mean += w[i] * p[i]
                for (j in 0 until n) if (w[j] != 0.0) variance += w[i] * w[j] * cov[i][j]
            }
            return mean to sqrt(maxOf(0.0, variance))
        }
        val out = heroes.mapIndexed { i, x ->
            val theta = combo(
                DoubleArray(n) { j ->
                    if (j in
                        3 until 3 + h
                    ) {
                        (if (j == 3 + i) 1.0 else 0.0) - 1.0 / h
                    } else {
                        0.0
                    }
                },
            )
            val uplift = combo(DoubleArray(n) { j -> if (j == 2 || j == 3 + h + i) 1.0 else 0.0 })
            Row(x, seated.getValue(x), theta.first, theta.second, uplift.first, uplift.second)
        }
        val arm = combo(
            DoubleArray(n) { j ->
                if (j == 2) {
                    1.0
                } else if (j >= 3 + h) {
                    1.0 / h
                } else {
                    0.0
                }
            },
        )
        return Fit(out, p[0], sqrt(cov[0][0]), p[1], sqrt(cov[1][1]), arm.first, arm.second, games, heroes)
    }

    /** The fit as a table: heroes by rating, each with its uplift; θ and δ in logits and in Elo. */
    fun report(fit: Fit, a: String, b: String): String {
        val elo = 400 / ln(10.0)
        val sb = StringBuilder()
        sb.appendLine(
            (
                "Hero rating fit: ${fit.games} games, $a (side a) against $b; " +
                    "side (Red) %+.0f ± %.0f, coin %+.0f ± %.0f, " +
                    "$a per seat %+.0f ± %.0f Elo"
                ).format(
                fit.side * elo,
                1.96 * fit.sideSe * elo,
                fit.coin * elo,
                1.96 * fit.coinSe * elo,
                fit.arm * elo,
                1.96 * fit.armSe * elo,
            ),
        )
        sb.appendLine(
            "θ = the hero's strength with these players against the mean hero (Elo per seat; a team adds three), " +
                "δ = what $a gains over $b with it (per seat, drawn towards the mean)",
        )
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
        sb.appendLine()
        sb.appendLine("Raw uplift ($a's win rate with the hero less $b's, the same games; ±1.96 se):")
        for (x in fit.heroes) {
            var aw = 0.0
            var an = 0
            var bw = 0.0
            var bn = 0
            for (g in fit.data) {
                val red = x in g.red
                if (!red && x !in g.blue) continue
                val heroPoints = if (red) g.redPoints else 1 - g.redPoints
                if (red == g.aRed) {
                    aw += heroPoints
                    an++
                } else {
                    bw += heroPoints
                    bn++
                }
            }
            val ra = aw / an
            val rb = bw / bn
            val se = sqrt(ra * (1 - ra) / an + rb * (1 - rb) / bn)
            sb.appendLine(
                "%-12s a %5.1f%% of %d, b %5.1f%% of %d, uplift %+5.1f [%+5.1f, %+5.1f]".format(
                    x,
                    100 * ra,
                    an,
                    100 * rb,
                    bn,
                    100 * (ra - rb),
                    100 * (ra - rb - 1.96 * se),
                    100 * (ra - rb + 1.96 * se),
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
        val a = Array(n) { i ->
            DoubleArray(2 * n).also { r ->
                m[i].copyInto(r)
                r[n + i] = 1.0
            }
        }
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
