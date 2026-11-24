package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.Choice

/**
 * Builds the card play's matrix ([CardEquilibrium.Matrix], Wave 4 Track G, knob `eq`) from the search's own
 * columns and values (Wave 4 integration: Track G's own matrix builder replaced by Track F's enumerated deals).
 *
 * Strength 10 knows the enemy hands (ADR-062) but not the enemy's facedown card, so the only uncertainty
 * before the reveal is which card each enemy puts down: a simultaneous choice. The search already plays it
 * out: with card deals ([HybridConfig.deals]) each column is one sample (a determinization) with one joint
 * enemy deal, the most likely deals of the opponent model on the enemies' own view, cards played facedown
 * dealt into the copy and cards still to choose played in the playout ([HybridAi]'s `dealt`). The matrix
 * merges the samples: a column per distinct joint deal, its cell the model-weighted mean over the samples
 * that dealt it (the samples are nature's, the deal is the enemy's), its weight the deal's summed weight.
 * Our cards are the rows (the search's candidates, the book's first). Teammates still to play are answered
 * by the book inside the playout, as in the search: one AI plays the whole team and the teammate's answer
 * comes after ours and sees it, a sequential team choice. Every cell is on the win-chance scale
 * ([dev.quinton.goa.core.game.ai.LeafChance], the search's leaf with `lp`), so the model-weighted mean of a
 * row is exactly the search's own score of that card.
 *
 * Reads only the search's values; deterministic.
 */
internal object CardEquilibriumSearch {
    /**
     * One search column as the matrix sees it: the enemy cards it dealt ([deal], hero → card), its weight in
     * the search, the opponent model's probability it stands for (before the search renormalised a sample's
     * deals: its share of the model's mass), and how many of its enemies were still to choose.
     */
    class DealColumn(val deal: Map<String, String>, val weight: Double, val modelMass: Double, val toPlay: Int)

    /**
     * The matrix of [rows] × the distinct deals of [columns] from [values] (rows × columns, the search's,
     * NaN where a candidate has no value). A column some row has no value for is left out (all rows are
     * compared on the same columns). Null when fewer than two of [options] remain or no column does, or
     * a row is not a card. [sampleWeight]: the samples' total weight, so [CardEquilibrium.Matrix.mass] is
     * the share of the model's probability the kept columns cover.
     */
    @Suppress("ReturnCount")
    fun matrix(
        rows: List<Choice>,
        columns: List<DealColumn>,
        values: Array<DoubleArray>,
        options: List<Choice>,
        sampleWeight: Double,
    ): CardEquilibrium.Matrix? {
        val keepRows = rows.indices.filter { rows[it] is Choice.Card && rows[it] in options }
        if (keepRows.size < 2) return null
        // Distinct deals in first-seen order (the search's column order: deterministic).
        val index = LinkedHashMap<Map<String, String>, Int>()
        val sum = ArrayList<DoubleArray>()
        val weight = ArrayList<Double>()
        var mass = 0.0
        var toPlay = 0
        for ((i, col) in columns.withIndex()) {
            if (keepRows.any { values[it][i].isNaN() }) continue
            val j = index.getOrPut(col.deal) {
                sum += DoubleArray(keepRows.size)
                weight += 0.0
                index.size
            }
            for ((r, row) in keepRows.withIndex()) sum[j][r] += col.weight * values[row][i]
            weight[j] = weight[j] + col.weight
            mass += col.modelMass
            toPlay = maxOf(toPlay, col.toPlay)
        }
        if (index.isEmpty() || weight.sum() <= 0.0) return null
        val keys = index.keys.toList()
        val payoff = Array(keepRows.size) { r -> DoubleArray(keys.size) { j -> sum[j][r] / weight[j] } }
        val total = weight.sum()
        val down = keys.maxOf { it.size } - toPlay
        return CardEquilibrium.Matrix(
            rows = keepRows.map { rows[it] },
            columns = keys,
            columnWeight = DoubleArray(keys.size) { weight[it] / total },
            payoff = payoff,
            mass = if (sampleWeight > 0) (mass / sampleWeight).coerceAtMost(1.0) else 1.0,
            enemiesDown = down.coerceAtLeast(0),
            enemiesToPlay = toPlay,
        )
    }
}
