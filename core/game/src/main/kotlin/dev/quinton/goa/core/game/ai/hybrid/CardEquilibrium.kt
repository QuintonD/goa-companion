package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.ai.WinChance
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * The simultaneous card play as a zero-sum matrix game (Wave 4 Track G, review-3/R1 §7.1): rows are our
 * cards, columns the enemy's joint card plays the book's opponent model finds most likely, a cell our
 * team's chance to win after the turn is played out. [MatrixGame] solves it exactly; [pick] plays it.
 * Pure Kotlin, deterministic, no platform library (runs on the JVM and in the browser alike).
 */
internal object CardEquilibrium {
    /**
     * Mixtures below this probability are dropped and the rest renormalised, so a card the solver gives a
     * sliver of weight (a near-tie at a vertex of the LP) is never played. One in ten: what it gives up is
     * at most this share of the gap between that card and the game's value (measured on the decision set,
     * RESULTS.md "Wave 4 — Track G").
     */
    const val MIN_SHARE: Double = 0.10

    /** Joint enemy card plays per matrix when the config sets no column cap (3 × 3 in a 2v2). */
    const val DEFAULT_COLUMNS: Int = 9

    /** A leaf score at or past this is a finished game in a playout (`Evaluator.WIN` less the round). */
    private const val DECIDED = 900.0

    /**
     * A leaf value as the chance to win, in [0, 1] (Track G's own conversion; Track F is adding a shared
     * helper, which should replace this when merged). A finished game is 1 or 0. A learned leaf is
     * logit × coin scale, so σ(value / [coinScale]); the book's hand evaluation goes through [WinChance].
     */
    fun winChance(value: Double, coinScale: Double?, heroesPerSide: Int): Double = when {
        value.isNaN() -> Double.NaN
        value >= DECIDED -> 1.0
        value <= -DECIDED -> 0.0
        coinScale != null -> 1.0 / (1.0 + PortableMath.exp(-value / coinScale))
        else -> WinChance.of(value, heroesPerSide)
    }

    /** The matrix of one card play: [payoff] rows × columns, our win chance; [columnWeight] the model's. */
    class Matrix(
        val rows: List<Choice>,
        /** Per column: enemy hero → the card it plays (only heroes whose card is still unknown). */
        val columns: List<Map<String, String>>,
        /** The opponent model's probability of each column, renormalised over the columns kept. */
        val columnWeight: DoubleArray,
        val payoff: Array<DoubleArray>,
        /** The model's probability mass the columns cover before renormalising. */
        val mass: Double,
        /** Enemy heroes whose card was already down (dealt by the determinizer) and still to play. */
        val enemiesDown: Int,
        val enemiesToPlay: Int,
    )

    /** What [pick] decided and why (diagnostics; the probe reads every field). */
    class Pick(
        val row: Int,
        /** The model's best row: the highest model-weighted mean (the best reply the search plays now). */
        val argmax: Int,
        /** The pure saddle row, or −1. */
        val saddle: Int,
        val solution: MatrixGame.Solution,
        /** The mixture actually sampled from (after [MIN_SHARE]). */
        val played: DoubleArray,
    )

    /**
     * The card to play: with a pure saddle point its row (the equilibrium is pure; among several saddle
     * rows the model's best); otherwise a sample from the equilibrium mixture with shares under [minShare]
     * dropped, drawn with [rng].
     */
    fun pick(m: Matrix, rng: Random, minShare: Double = MIN_SHARE): Pick {
        val a = m.payoff
        val argmax = argmax(a, m.columnWeight)
        val sol = MatrixGame.solve(a)
        val saddles = MatrixGame.saddleRows(a)
        val saddle = saddles.maxWithOrNull(compareBy<Int> { modelValue(a[it], m.columnWeight) }.thenByDescending { it })
            ?: -1
        if (saddle >= 0) {
            val p = DoubleArray(a.size).also { it[saddle] = 1.0 }
            return Pick(saddle, argmax, saddle, sol, p)
        }
        val p = trimmed(sol.row, minShare)
        return Pick(sample(p, rng), argmax, -1, sol, p)
    }

    /** The model-weighted mean of a row. */
    fun modelValue(row: DoubleArray, w: DoubleArray): Double {
        var s = 0.0
        for (j in row.indices) s += w[j] * row[j]
        return s
    }

    /** The row with the best model-weighted mean; the first on ties (the book's own card is first). */
    fun argmax(a: Array<DoubleArray>, w: DoubleArray): Int {
        var best = 0
        var bestV = Double.NEGATIVE_INFINITY
        for (i in a.indices) {
            val v = modelValue(a[i], w)
            if (v > bestV + EPS) {
                bestV = v
                best = i
            }
        }
        return best
    }

    /** Shares under [minShare] dropped, the rest renormalised (the largest share is always kept). */
    fun trimmed(p: DoubleArray, minShare: Double): DoubleArray {
        val top = p.indices.maxByOrNull { p[it] } ?: return p
        val q = DoubleArray(p.size) { if (p[it] >= minShare || it == top) p[it] else 0.0 }
        val s = q.sum()
        for (i in q.indices) q[i] /= s
        return q
    }

    /** Shannon entropy in bits. */
    fun entropy(p: DoubleArray): Double = -p.sumOf { if (it > 0) it * ln(it) / ln(2.0) else 0.0 }

    private fun sample(p: DoubleArray, rng: Random): Int {
        var r = rng.nextDouble()
        var last = 0
        for (i in p.indices) {
            if (p[i] <= 0.0) continue
            last = i
            r -= p[i]
            if (r < 0) return i
        }
        return last
    }

    /**
     * The [cap] most likely joint plays of the enemy heroes whose card is unknown, under independent
     * per-hero model weights ([perHero]: hero → (card, probability) in the model's order). Ties keep
     * enumeration order, so the result is deterministic. Returns the columns and their probabilities.
     */
    fun jointColumns(perHero: List<Pair<String, List<Pair<String, Double>>>>, cap: Int): List<Joint> {
        var joint = listOf(emptyMap<String, String>() to 1.0)
        val order = compareByDescending<IndexedValue<Joint>> { it.value.second }.thenBy { it.index }
        for ((hero, cards) in perHero) {
            val next = ArrayList<Joint>(joint.size * cards.size)
            for ((m, p) in joint) for ((card, q) in cards) next += (m + (hero to card)) to p * q
            // Keeping only the best [cap] after each hero is exact: a joint play's probability is a product
            // of non-negative factors, so a prefix outside the top cap cannot end inside it.
            joint = next.withIndex().sortedWith(order).take(maxOf(1, cap)).map { it.value }
        }
        return joint
    }

    private const val EPS = 1e-12
}

/** A joint enemy card play (hero → card) and its probability under the opponent model. */
internal typealias Joint = Pair<Map<String, String>, Double>

/** An exact solver for small two-player zero-sum matrix games (the row player maximises). */
internal object MatrixGame {
    /** Optimal mixtures for both players and the game's value. */
    class Solution(val row: DoubleArray, val column: DoubleArray, val value: Double)

    private const val EPS = 1e-11
    private const val SADDLE_EPS = 1e-9

    /** Rows that are a pure saddle point: their minimum equals the game's pure minimax (empty when none). */
    fun saddleRows(a: Array<DoubleArray>): List<Int> {
        val n = a[0].size
        val minimax = (0 until n).minOf { j -> a.maxOf { it[j] } }
        return a.indices.filter { i -> abs(a[i].min() - minimax) <= SADDLE_EPS }
    }

    /**
     * Linear programming by the simplex method with Bland's rule (no cycling, deterministic). The matrix is
     * shifted positive; the column player's LP max Σw s.t. Bw ≤ 1, w ≥ 0 starts feasible at w = 0; its
     * optimum is 1 / value, and the row player's mixture is the dual, read from the slack columns.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun solve(a: Array<DoubleArray>): Solution {
        val m = a.size
        val n = a[0].size
        require(m > 0 && n > 0 && a.all { it.size == n }) { "a matrix game needs a full matrix" }
        val lo = a.minOf { it.min() }
        val shift = 1.0 - lo
        // Tableau: m constraint rows, then the objective row; columns 0..n-1 the w's, n..n+m-1 the slacks,
        // the last the right-hand side.
        val w = n + m
        val t = Array(m + 1) { DoubleArray(w + 1) }
        for (i in 0 until m) {
            for (j in 0 until n) t[i][j] = a[i][j] + shift
            t[i][n + i] = 1.0
            t[i][w] = 1.0
        }
        for (j in 0 until n) t[m][j] = -1.0
        val basis = IntArray(m) { n + it }
        var guard = 0
        while (guard++ < MAX_PIVOTS) {
            // Bland: the entering variable is the lowest index with a negative reduced cost.
            val enter = (0 until w).firstOrNull { t[m][it] < -EPS } ?: break
            var leave = -1
            var bestRatio = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                if (t[i][enter] <= EPS) continue
                val r = t[i][w] / t[i][enter]
                if (leave < 0 || r < bestRatio - EPS || (abs(r - bestRatio) <= EPS && basis[i] < basis[leave])) {
                    bestRatio = r
                    leave = i
                }
            }
            check(leave >= 0) { "unbounded matrix-game LP" }
            val pv = t[leave][enter]
            for (k in 0..w) t[leave][k] /= pv
            for (i in 0..m) {
                if (i == leave) continue
                val f = t[i][enter]
                if (f == 0.0) continue
                for (k in 0..w) t[i][k] -= f * t[leave][k]
            }
            basis[leave] = enter
        }
        val z = t[m][w]
        val col = DoubleArray(n)
        for (i in 0 until m) if (basis[i] < n) col[basis[i]] = t[i][w]
        val row = DoubleArray(m) { maxOf(0.0, t[m][n + it]) }
        normalise(col)
        normalise(row)
        return Solution(row, col, 1.0 / z - shift)
    }

    private fun normalise(p: DoubleArray) {
        val s = p.sum()
        if (s <= 0) {
            p[0] = 1.0
            return
        }
        for (i in p.indices) p[i] /= s
    }

    private const val MAX_PIVOTS = 10_000
}
