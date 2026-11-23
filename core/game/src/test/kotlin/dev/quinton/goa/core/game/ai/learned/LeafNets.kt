package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.LearnedWeights
import dev.quinton.goa.core.game.ai.LearnedWeights2
import dev.quinton.goa.core.game.ai.LearnedWeights3
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.PositionEvaluator
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.hybrid.HybridAi
import dev.quinton.goa.core.game.engine.EngineData

/**
 * Wave 3 Track E (RESULTS.md "Wave 3 — Track E"): the book and the hybrid search as self-play and
 * tournament sides ([Learned.side]), with a net under test at the hybrid's leaves.
 *
 * - `h10+temp=0.3+fear=0`: the book ([HeuristicAi]) with its measurement knobs.
 * - `x10+K=24+…`: the hybrid search ([HybridAi]); `W2` stands for strength 10's knobs ([AiPlayers.SEARCH_10]).
 *   `+net=<name>` or `+net=avg(a,b,c)` scores its playout ends with saved nets ([Learned.net], `cur1..3` the
 *   checked-in ones) instead of its `leaf`; `+thr=n` gives it n sample threads.
 * - `sN`: the old search.
 */
internal object LeafNets {
    /** The checked-in nets by name. */
    val CHECKED_IN: Map<String, () -> LearnedNet> = mapOf(
        "cur1" to { LearnedWeights.net() },
        "cur2" to { LearnedWeights2.net() },
        "cur3" to { LearnedWeights3.net() },
    )

    /** The same as training data (a warm start, `AI_INIT=cur1`). */
    val CHECKED_IN_DATA: Map<String, () -> Learned.NetData> = mapOf(
        "cur1" to {
            with(LearnedWeights) {
                Learned.NetData(
                    INPUTS, H1, H2, w1(), b1(), w2(), b2(), w3(), wl(), B3, COIN_SCALE, SCHEMA_HASH, HEROES, wh(),
                    CARDS, wc(),
                )
            }
        },
        "cur2" to {
            with(LearnedWeights2) {
                Learned.NetData(
                    INPUTS, H1, H2, w1(), b1(), w2(), b2(), w3(), wl(), B3, COIN_SCALE, SCHEMA_HASH, HEROES, wh(),
                    CARDS, wc(),
                )
            }
        },
        "cur3" to {
            with(LearnedWeights3) {
                Learned.NetData(
                    INPUTS, H1, H2, w1(), b1(), w2(), b2(), w3(), wl(), B3, COIN_SCALE, SCHEMA_HASH, HEROES, wh(),
                    CARDS, wc(),
                )
            }
        },
    )

    /** Strength 10's knobs (Track C's W2) as a spec prefix. */
    private val W2: String by lazy {
        "x10+" + (AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())).entries
            .joinToString("+") { (k, v) -> "$k=${fmt(v)}" }
    }

    private fun fmt(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    fun player(spec0: String, seed: Long): AiPlayer {
        val spec = if (spec0 == "W2" || spec0.startsWith("W2+")) W2 + spec0.removePrefix("W2") else spec0
        val parts = spec.split("+")
        val strength = parts[0].drop(1).toInt()
        var net: String? = null
        var threads = 1
        val knobs = LinkedHashMap<String, Double>()
        for (p in parts.drop(1)) {
            val (k, v) = p.split("=", limit = 2)
            when (k) {
                // `avg(a/b/c)` too, where commas separate specs (the position bench).
                "net" -> net = v.replace('/', ',')
                "thr" -> threads = v.toInt()
                else -> knobs[k] = v.toDouble()
            }
        }
        return when (spec[0]) {
            'h' -> HeuristicAi(strength, seed, tune = knobs)
            'x' -> {
                val runner = if (threads > 1) ThreadRunner(threads) else SampleRunner.SEQUENTIAL
                HybridAi(seed, knobs, runner).also { h -> net?.let { h.leafOverride = evaluator(it) } }
            }
            else -> error("LeafNets: h… or x…, not $spec")
        }
    }

    /** A net name or an ensemble (`avg(a,b,c)`) as a leaf evaluator. */
    fun evaluator(v: String): (EngineData) -> PositionEvaluator =
        EnsembleEvaluator.parse(v) ?: Learned.net(v).let { n -> { data -> NetEvaluator(data, n) } }
}
