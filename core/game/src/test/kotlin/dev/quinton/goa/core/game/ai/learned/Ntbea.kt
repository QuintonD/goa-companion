package dev.quinton.goa.core.game.ai.learned

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The N-Tuple Bandit Evolutionary Algorithm (Lucas, Liu and Perez-Liebana 2018) for noisy fitness: a
 * point is one value index per dimension ([sizes]); every evaluation's reward is added to the statistics
 * of each tuple of dimensions (all 1-tuples, all 2-tuples when [pairs], and the whole point), so one
 * noisy game informs every point that shares a value with it. The search moves to the neighbour (a
 * mutation of the current point) with the best upper confidence bound averaged over the tuples, and
 * recommends the evaluated point whose tuple means are best (ADR-063).
 */
internal class Ntbea(
    private val sizes: IntArray,
    private val rng: Random,
    private val explore: Double = 1.0,
    private val neighbours: Int = 50,
    pairs: Boolean = true,
) {
    private val tuples: List<IntArray> = buildList {
        for (i in sizes.indices) add(intArrayOf(i))
        if (pairs) for (i in sizes.indices) for (j in i + 1 until sizes.size) add(intArrayOf(i, j))
        if (sizes.size > 2 || !pairs) add(IntArray(sizes.size) { it })
    }

    /** Per tuple: key of the values → (evaluations, reward sum). */
    private val stats = tuples.map { HashMap<String, DoubleArray>() }

    private var total = 0
    private var rewardSum = 0.0

    /** Every point evaluated, by key, with its evaluations. */
    val evaluated = LinkedHashMap<String, IntArray>()

    private fun key(x: IntArray, tuple: IntArray): String = tuple.joinToString(",") { x[it].toString() }

    fun add(x: IntArray, reward: Double) {
        total++
        rewardSum += reward
        for ((t, tuple) in tuples.withIndex()) {
            val s = stats[t].getOrPut(key(x, tuple)) { DoubleArray(2) }
            s[0] += 1.0
            s[1] += reward
        }
        evaluated.getOrPut(x.joinToString(",")) { x.copyOf() }
    }

    /** Evaluations of the whole point [x]. */
    fun count(x: IntArray): Int = stats.last()[key(x, tuples.last())]?.get(0)?.toInt() ?: 0

    /** The model's estimate of [x]: the mean over the tuples that have seen its values. */
    fun estimate(x: IntArray): Double {
        var sum = 0.0
        var n = 0
        for ((t, tuple) in tuples.withIndex()) {
            val s = stats[t][key(x, tuple)] ?: continue
            sum += s[1] / s[0]
            n++
        }
        return if (n == 0) prior() else sum / n
    }

    private fun prior(): Double = if (total == 0) 0.5 else rewardSum / total

    private fun ucb(x: IntArray): Double {
        var sum = 0.0
        val logN = ln(total + 1.0)
        for ((t, tuple) in tuples.withIndex()) {
            val s = stats[t][key(x, tuple)]
            val n = s?.get(0) ?: 0.0
            val mean = if (s == null) prior() else s[1] / n
            sum += mean + explore * sqrt(logN / (n + EPSILON))
        }
        return sum / tuples.size
    }

    /** A mutation of [x]: each dimension changes with probability 1/D, at least one does. */
    fun mutate(x: IntArray): IntArray {
        val y = x.copyOf()
        val movable = sizes.indices.filter { sizes[it] > 1 }
        var changed = false
        for (i in movable) {
            if (rng.nextDouble() < 1.0 / movable.size) {
                y[i] = other(i, x[i])
                changed = true
            }
        }
        if (!changed && movable.isNotEmpty()) {
            val i = movable[rng.nextInt(movable.size)]
            y[i] = other(i, x[i])
        }
        return y
    }

    private fun other(i: Int, v: Int): Int {
        val r = rng.nextInt(sizes[i] - 1)
        return if (r >= v) r + 1 else r
    }

    /** The next point to evaluate: the neighbour of [x] with the best upper confidence bound. */
    fun next(x: IntArray): IntArray {
        var best = mutate(x)
        var bestValue = ucb(best)
        repeat(neighbours - 1) {
            val y = mutate(x)
            val v = ucb(y)
            if (v > bestValue) {
                best = y
                bestValue = v
            }
        }
        return best
    }

    /** The evaluated point the model rates best (ties: the first evaluated). */
    fun recommend(): IntArray = evaluated.values.maxBy { estimate(it) }

    private companion object {
        const val EPSILON = 0.5
    }
}
