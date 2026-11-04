package dev.quinton.goa.core.game.ai

/**
 * The facedown card play as a simultaneous game (STRATEGY §4.2, ADR-061): our candidate plays against the
 * enemy plays the samples drew, solved by regret matching (RM+, linearly averaged). Every sample played every
 * candidate against the same enemy cards (common random numbers), so a sample is a column; samples with the
 * same enemy cards are one column, weighted by how often the opponent model drew it. The enemy plays that
 * model with weight 1 − [free] and best-responds to our mix with weight [free] (a restricted Nash response:
 * 0 is a best reply to the model, 1 the minimax over the drawn plays). The result is our mixed strategy.
 */
internal object RegretMatching {
    const val ITERATIONS: Int = 1000

    /** Below this a play's share is dropped (noise of the iterations), the rest renormalised. */
    private const val FLOOR = 0.03

    /**
     * [values]: per own play, its value in each sample (NaN where not played out); [keys]: per sample, the
     * enemy cards it drew (null = not sampled). Returns the probability of each own play.
     */
    fun solve(values: List<DoubleArray>, keys: List<String?>, free: Double, iterations: Int = ITERATIONS): DoubleArray {
        val rows = values.size
        if (rows == 0) return DoubleArray(0)
        val groups = keys.indices.filter { k ->
            keys[k] != null &&
                values.any { !it.getOrElse(k) { Double.NaN }.isNaN() }
        }
            .groupBy { keys[it]!! }.values.toList()
        if (groups.isEmpty()) return DoubleArray(rows) { 1.0 / rows }
        val total = groups.sumOf { it.size }.toDouble()
        val q = DoubleArray(groups.size) { groups[it].size / total }
        val rowMean =
            DoubleArray(rows) { i -> values[i].filter { !it.isNaN() }.average().let { if (it.isNaN()) 0.0 else it } }
        val a = Array(rows) { i ->
            DoubleArray(groups.size) { j ->
                val v = groups[j].map { values[i].getOrElse(it) { Double.NaN } }.filter { !it.isNaN() }
                if (v.isEmpty()) rowMean[i] else v.average()
            }
        }
        val cols = groups.size
        val rRow = DoubleArray(rows)
        val rCol = DoubleArray(cols)
        val avg = DoubleArray(rows)
        val x = DoubleArray(rows)
        val z = DoubleArray(cols)
        for (t in 1..iterations) {
            normalise(rRow, x)
            normalise(rCol, z)
            // The enemy's play: the model with 1 − free, its best reply (z) with free.
            val y = DoubleArray(cols) { (1 - free) * q[it] + free * z[it] }
            val u = DoubleArray(rows) { i -> (0 until cols).sumOf { a[i][it] * y[it] } }
            val ux = (0 until rows).sumOf { x[it] * u[it] }
            for (i in 0 until rows) rRow[i] = maxOf(0.0, rRow[i] + u[i] - ux)
            // The enemy minimises our value: its regret for column j is how much lower j makes it.
            val c = DoubleArray(cols) { j -> (0 until rows).sumOf { x[it] * a[it][j] } }
            val cz = (0 until cols).sumOf { z[it] * c[it] }
            for (j in 0 until cols) rCol[j] = maxOf(0.0, rCol[j] + cz - c[j])
            for (i in 0 until rows) avg[i] += t * x[i]
        }
        val sum = avg.sum()
        val out = DoubleArray(rows) { avg[it] / sum }
        for (i in out.indices) if (out[i] < FLOOR) out[i] = 0.0
        val kept = out.sum()
        return if (kept <= 0) {
            DoubleArray(rows) { if (it == out.indices.maxBy { k -> avg[k] }) 1.0 else 0.0 }
        } else {
            DoubleArray(rows) { out[it] / kept }
        }
    }

    /** Regret matching's strategy: positive regrets normalised, uniform when none is positive. */
    private fun normalise(r: DoubleArray, into: DoubleArray) {
        val s = r.sum()
        for (i in r.indices) into[i] = if (s > 0) r[i] / s else 1.0 / r.size
    }
}
