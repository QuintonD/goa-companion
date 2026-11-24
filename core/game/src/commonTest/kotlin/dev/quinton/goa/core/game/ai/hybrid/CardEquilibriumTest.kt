package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.Choice
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wave 4 Track G: the matrix-game solver and the card pick, on games with known answers (JVM and JS). */
class CardEquilibriumTest {
    private fun close(expected: Double, actual: Double, tol: Double = 1e-9, what: String = "") =
        assertTrue(abs(expected - actual) <= tol, "$what: expected $expected, got $actual")

    private fun matrix(vararg rows: DoubleArray, w: DoubleArray? = null): CardEquilibrium.Matrix {
        val n = rows[0].size
        return CardEquilibrium.Matrix(
            rows = rows.indices.map { Choice.Card("c$it") },
            columns = (0 until n).map { mapOf("enemy" to "e$it") },
            columnWeight = w ?: DoubleArray(n) { 1.0 / n },
            payoff = arrayOf(*rows),
            mass = 1.0,
            enemiesDown = 1,
            enemiesToPlay = 0,
        )
    }

    /** The defining property: p guarantees the value against every column, q holds every row to it. */
    private fun assertOptimal(a: Array<DoubleArray>, s: MatrixGame.Solution, tol: Double = 1e-9) {
        close(1.0, s.row.sum(), tol, "row mix sums to 1")
        close(1.0, s.column.sum(), tol, "column mix sums to 1")
        assertTrue(s.row.all { it >= -tol } && s.column.all { it >= -tol }, "mixtures are non-negative")
        for (j in a[0].indices) {
            val v = a.indices.sumOf { s.row[it] * a[it][j] }
            assertTrue(v >= s.value - tol, "row mix holds ${s.value} against column $j (got $v)")
        }
        for (i in a.indices) {
            val v = a[i].indices.sumOf { s.column[it] * a[i][it] }
            assertTrue(v <= s.value + tol, "column mix holds row $i to ${s.value} (got $v)")
        }
    }

    @Test
    fun matchingPenniesIsAnEvenMix() {
        val a = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        val s = MatrixGame.solve(a)
        close(0.5, s.value)
        close(0.5, s.row[0])
        close(0.5, s.column[0])
        assertOptimal(a, s)
        assertTrue(MatrixGame.saddleRows(a).isEmpty())
    }

    @Test
    fun rockPaperScissorsIsUniform() {
        val a = arrayOf(
            doubleArrayOf(0.5, 0.0, 1.0),
            doubleArrayOf(1.0, 0.5, 0.0),
            doubleArrayOf(0.0, 1.0, 0.5),
        )
        val s = MatrixGame.solve(a)
        close(0.5, s.value)
        for (x in s.row) close(1.0 / 3, x)
        for (x in s.column) close(1.0 / 3, x)
        assertOptimal(a, s)
        val pick = CardEquilibrium.pick(matrix(*a), Random(1))
        assertEquals(-1, pick.saddle)
        close(1.585, CardEquilibrium.entropy(pick.played), 1e-3, "log2(3) bits")
    }

    @Test
    fun aSaddlePointIsPlayedPure() {
        // Row minima 0.3 / 0.4, column maxima 0.4 / 0.6: the saddle is row 1, column 0, value 0.4.
        val a = arrayOf(doubleArrayOf(0.3, 0.6), doubleArrayOf(0.4, 0.5))
        val s = MatrixGame.solve(a)
        close(0.4, s.value)
        close(1.0, s.row[1])
        assertOptimal(a, s)
        assertEquals(listOf(1), MatrixGame.saddleRows(a))
        // The model expects column 1 (row 0 is its best reply: 0.6), but the game is played at its saddle.
        val pick = CardEquilibrium.pick(matrix(*a, w = doubleArrayOf(0.0, 1.0)), Random(7))
        assertEquals(0, pick.argmax)
        assertEquals(1, pick.saddle)
        assertEquals(1, pick.row)
    }

    @Test
    fun dominatedCardsGetNoWeight() {
        // Row 2 is strictly dominated by the even mix of rows 0 and 1; column 2 is dominated for the enemy.
        val a = arrayOf(
            doubleArrayOf(0.9, 0.1, 0.95),
            doubleArrayOf(0.1, 0.9, 0.95),
            doubleArrayOf(0.2, 0.2, 0.99),
        )
        val s = MatrixGame.solve(a)
        close(0.5, s.value)
        close(0.0, s.row[2])
        close(0.0, s.column[2])
        assertOptimal(a, s)
        repeat(50) { seed -> assertTrue(CardEquilibrium.pick(matrix(*a), Random(seed)).row != 2) }
    }

    @Test
    fun sliversAreDroppedBeforeSampling() {
        // No saddle (maximin 0, minimax 0.05); the equilibrium plays row 0 only 1/21 of the time.
        val a = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 0.05))
        val s = MatrixGame.solve(a)
        close(1.0 / 21, s.row[0])
        assertOptimal(a, s)
        repeat(50) { seed -> assertEquals(1, CardEquilibrium.pick(matrix(*a), Random(seed)).row) }
    }

    @Test
    fun theSameSeedPlaysTheSameCard() {
        val a = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        val picks = (0 until 400).map { CardEquilibrium.pick(matrix(*a), Random(it.toLong())).row }
        assertEquals(picks, (0 until 400).map { CardEquilibrium.pick(matrix(*a), Random(it.toLong())).row })
        val share = picks.count { it == 0 } / 400.0
        assertTrue(share in 0.4..0.6, "an even mix samples both cards (row 0 share $share)")
    }

    @Test
    fun randomGamesAreSolvedExactly() {
        val rng = Random(20261124)
        repeat(300) { k ->
            val m = 2 + rng.nextInt(6)
            val n = 1 + rng.nextInt(12)
            // Every third game on a coarse grid: ties and degenerate vertices.
            val a = Array(m) {
                DoubleArray(n) { if (k % 3 == 0) rng.nextInt(4) / 3.0 else rng.nextDouble() }
            }
            val s = MatrixGame.solve(a)
            assertOptimal(a, s, 1e-9)
            val maximin = a.maxOf { it.min() }
            val minimax = (0 until n).minOf { j -> a.maxOf { it[j] } }
            assertTrue(s.value >= maximin - 1e-9 && s.value <= minimax + 1e-9, "value between the pure bounds")
            assertEquals(abs(maximin - minimax) <= 1e-9, MatrixGame.saddleRows(a).isNotEmpty())
        }
    }

    @Test
    fun theMatrixMergesTheSearchColumnsByDeal() {
        val a = Choice.Card("a")
        val b = Choice.Card("b")
        val x1 = mapOf("x" to "x1")
        val x2 = mapOf("x" to "x2")
        // Two samples, each with the deals x1 (0.6 of the model) and x2 (0.4); sample weights 1 and 1.
        val cols = listOf(
            CardEquilibriumSearch.DealColumn(x1, 0.6, 0.6, 1),
            CardEquilibriumSearch.DealColumn(x2, 0.4, 0.4, 1),
            CardEquilibriumSearch.DealColumn(x1, 0.6, 0.6, 1),
            CardEquilibriumSearch.DealColumn(x2, 0.4, 0.4, 1),
            // A column one row has no value for (a cut playout) is left out of every row.
            CardEquilibriumSearch.DealColumn(mapOf("x" to "x3"), 0.1, 0.1, 1),
        )
        val values = arrayOf(
            doubleArrayOf(0.8, 0.2, 0.6, 0.4, 0.9),
            doubleArrayOf(0.5, 0.5, 0.5, 0.7, Double.NaN),
        )
        val m = CardEquilibriumSearch.matrix(listOf(a, b), cols, values, listOf(a, b), 2.0)!!
        assertEquals(listOf(x1, x2), m.columns)
        close(0.6, m.columnWeight[0])
        close(0.7, m.payoff[0][0])
        close(0.3, m.payoff[0][1])
        close(0.6, m.payoff[1][1])
        close(1.0, m.mass)
        assertEquals(1, m.enemiesToPlay)
        assertEquals(0, m.enemiesDown)
        // The model-weighted row mean is the search's own weighted mean of that card.
        val search = (0..3).sumOf { cols[it].weight * values[0][it] } / (0..3).sumOf { cols[it].weight }
        close(search, CardEquilibrium.modelValue(m.payoff[0], m.columnWeight))
        // Fewer than two cards among the options: no matrix.
        assertEquals(null, CardEquilibriumSearch.matrix(listOf(a, b), cols, values, listOf(a), 2.0))
    }
}
