package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/** How far a playout runs before the position is evaluated. */
internal enum class Horizon {
    /** Until the card being resolved (or the end-of-round step) is done. */
    ACTION,

    /** Until the end of this turn (through the end of round after turn 4). */
    TURN,

    /** Until the end of the next turn. */
    NEXT_TURN,
}

/** What one level does (STRATEGY.md §4.2), all in one place. */
internal data class LevelProfile(
    /** Easy: no search, static scores only. */
    val staticOnly: Boolean,
    val playSamples: Int,
    val otherSamples: Int,
    val playHorizon: Horizon,
    val otherHorizon: Horizon,
    /** Options searched for big option lists (move destinations), pre-ranked by the static scores. */
    val candidates: Int,
    /** Softmax temperature (coin-eq) over options within [maxGap] of the best; 0 = always the best. */
    val temperature: Double,
    val maxGap: Double,
    /** Easy's "beginner slip": a uniform pick among the top three. */
    val slipRate: Double,
    /** mean − risk · stdev for card plays (Hard+, against the spread of guesses). */
    val risk: Double,
    val knobs: EvalKnobs,
    /** Unrevealed enemy plays drawn from the opponent model instead of uniformly. */
    val modelPlays: Boolean,
    /** Remember cards seen faceup since each enemy's last level up (Expert). */
    val memory: Boolean,
    /** The level's own hero plays its later decisions in a playout by one-ply search (Hard+). */
    val greedySelf: Boolean,
    /** Wall-clock cap per PLAY_CARD decision (device budget); smaller decisions get a third. */
    val budgetMs: Long,
)

/** One option's values over the samples (NaN where the option did not exist in a sample). */
internal class OptionValue(val choice: Choice, val values: DoubleArray) {
    val n: Int get() = values.count { !it.isNaN() }
    val mean: Double get() = values.filter { !it.isNaN() }.average().let { if (it.isNaN()) Double.NEGATIVE_INFINITY else it }
    val sd: Double
        get() {
            val v = values.filter { !it.isNaN() }
            if (v.size < 2) return 0.0
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
        }
}

/** A playout's end, kept for the coach's explanations. */
internal class Leaf(val choice: Choice, val sample: Int, val end: EngineSession, val startEvents: Int, val value: Double)

internal class Analysis(val decision: Decision, val options: List<OptionValue>, val leaves: List<Leaf>, val samples: Int)

/**
 * Evaluates the options of a decision by playouts on determinized copies (never the real session's
 * hidden cards): for each sample, fork, answer the option, play on with the rollout policy to the
 * horizon, evaluate. Shared by the levels and the coach.
 */
internal class Searcher(
    val ev: Evaluator,
    val profile: LevelProfile,
    private val keepLeaves: Boolean = false,
) {
    val static = StaticPolicy(ev)

    fun determinize(es: EngineSession, d: Decision, seed: Long): EngineSession {
        val team = d.team
        val humans = es.setup.seats.filter { it.team == team && it.controller == Controller.Human && it.heroId != d.seat }
            .map { it.heroId }.toSet()
        val options = Determinizer.Options(
            seen = if (profile.memory) seenCards(es, team) else emptyMap(),
            playWeight = if (profile.modelPlays) ::playWeights else null,
            hidePlayedOf = humans,
        )
        return es.determinizeWith(team, seed, options)
    }

    /** The opponent model (STRATEGY §3.1): softmax of the hero's own static card scores, 15% uniform. */
    private fun playWeights(state: GameState, heroId: String, pool: List<String>): List<Double> {
        val ctx = ev.context(state)
        val scores = pool.map { static.playScore(ctx, heroId, Choice.Card(it)) }
        val best = scores.maxOrNull() ?: 0.0
        val soft = scores.map { exp((it - best) / OPPONENT_TAU) }
        val sum = soft.sum()
        return soft.map { 0.85 * it / sum + 0.15 / pool.size }
    }

    fun analyze(
        es: EngineSession,
        d: Decision,
        seed: Long,
        cancelled: () -> Boolean,
        deadline: Long,
        only: List<Choice>? = null,
    ): Analysis? {
        val isPlay = d.kind == DecisionKind.PLAY_CARD
        val samples = if (isPlay) profile.playSamples else profile.otherSamples
        val horizon = if (isPlay) profile.playHorizon else profile.otherHorizon
        var candidates: List<Choice>? = only
        val table = LinkedHashMap<Choice, DoubleArray>()
        val leaves = ArrayList<Leaf>()
        var done = 0
        for (k in 0 until samples) {
            if (k > 0 && (cancelled() || System.nanoTime() > deadline)) break
            val det = determinize(es, d, mix(seed, k.toLong()))
            val dp = det.pending ?: continue
            if (dp.seat != d.seat || dp.kind != d.kind) continue
            if (candidates == null) candidates = prune(det, d, dp)
            for (o in candidates) table.getOrPut(o) { DoubleArray(samples) { Double.NaN } }
            var complete = true
            for (o in candidates) {
                if (cancelled()) {
                    complete = false
                    break
                }
                if (o !in dp.options) continue
                val f = det.forkSession()
                val before = f.events.size
                f.answer(dp.id, o)
                playout(f, d.seat, horizon)
                val v = ev.evaluate(f.state, d.team)
                table.getValue(o)[k] = v
                if (keepLeaves) leaves += Leaf(o, k, f, before, v)
            }
            if (!complete) {
                for (arr in table.values) arr[k] = Double.NaN
                break
            }
            done++
        }
        if (done == 0) return null
        return Analysis(d, table.map { (c, v) -> OptionValue(c, v) }, leaves, done)
    }

    /** Big option lists: keep the best few by static score (plus every non-space option). */
    private fun prune(det: EngineSession, d: Decision, dp: Decision): List<Choice> {
        val opts = d.options.filter { it in dp.options }
        if (opts.size <= profile.candidates) return opts
        val scores = static.scores(det.state, dp)
        val ranked = dp.options.indices.sortedByDescending { scores[it] }.map { dp.options[it] }.filter { it in opts }
        val keep = ranked.take(profile.candidates).toMutableList()
        for (o in opts) if (o !is Choice.Space && o !in keep) keep += o
        return opts.filter { it in keep }
    }

    /** Play on to the horizon with the rollout policy. */
    fun playout(f: EngineSession, rootSeat: String, horizon: Horizon, maxSteps: Int = 400) {
        val start = f.state
        val startTurn = turnIndex(start)
        val startStep = start.step
        val startSeat = start.stepSeat
        val startActor = start.acting
        val startRound = start.round
        var n = 0
        while (n < maxSteps) {
            val d = f.pending ?: return
            val s = f.state
            val stop = when (horizon) {
                Horizon.ACTION -> s.round != startRound || s.step != startStep || s.stepSeat != startSeat ||
                    s.acting != startActor || turnIndex(s) != startTurn
                Horizon.TURN -> turnIndex(s) > startTurn
                Horizon.NEXT_TURN -> turnIndex(s) > startTurn + 1
            }
            if (stop) return
            val c = if (profile.greedySelf && d.seat == rootSeat && d.options.size > 1) greedy(f, d) else static.best(s, d)
            f.answer(d.id, c)
            n++
        }
    }

    /** One-ply: each option answered on a fork, the action finished statically, evaluated. */
    private fun greedy(f: EngineSession, d: Decision): Choice {
        val opts = if (d.options.size > GREEDY_MAX) {
            val sc = static.scores(f.state, d)
            d.options.indices.sortedByDescending { sc[it] }.take(GREEDY_MAX).map { d.options[it] }
        } else {
            d.options
        }
        var best = opts[0]
        var bestV = Double.NEGATIVE_INFINITY
        for (o in opts) {
            val g = f.forkSession()
            g.answer(d.id, o)
            finishAction(g, d.seat)
            val v = ev.evaluate(g.state, d.team)
            if (v > bestV) {
                bestV = v
                best = o
            }
        }
        return best
    }

    private fun finishAction(g: EngineSession, seat: String) {
        val start = g.state
        val acting = start.acting
        val step = start.step
        val turn = turnIndex(start)
        var n = 0
        while (n < 40) {
            val d = g.pending ?: return
            val s = g.state
            if (s.acting != acting || s.step != step || turnIndex(s) != turn) return
            if (d.seat != seat && d.kind != DecisionKind.DEFEND) return
            g.answer(d.id, static.best(s, d))
            n++
        }
    }

    companion object {
        const val OPPONENT_TAU: Double = 1.5
        const val GREEDY_MAX: Int = 8

        fun turnIndex(s: GameState): Int = s.round * 8 + s.turn

        fun mix(a: Long, b: Long): Long {
            var x = a * -0x61c8864680b583ebL + b
            x = (x xor (x ushr 33)) * -0xae502812aa7333L
            x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
            return x xor (x ushr 33)
        }

        /** Card ids each enemy hero has shown faceup since its last level up (the public log). */
        fun seenCards(es: EngineSession, team: Team): Map<String, Set<String>> {
            val enemies = es.setup.seats.filter { it.team != team }.map { it.heroId }.toSet()
            val seen = LinkedHashMap<String, MutableSet<String>>()
            for (e in es.events) {
                when (e) {
                    is GameEvent.CardsRevealed -> e.played.forEach { (h, c) -> if (h in enemies && c != null) seen.getOrPut(h) { LinkedHashSet() } += c }
                    is GameEvent.Defended -> if (e.heroId in enemies && e.cardId != null) seen.getOrPut(e.heroId) { LinkedHashSet() } += e.cardId!!
                    is GameEvent.ActionChosen -> if (e.heroId in enemies) seen.getOrPut(e.heroId) { LinkedHashSet() } += e.cardId
                    is GameEvent.LevelledUp -> seen.remove(e.heroId)
                    else -> Unit
                }
            }
            return seen
        }
    }
}

/** Picks among analysed options: best, or a softmax over the near-best (natural variety). */
internal object Picker {
    fun pick(choices: List<Choice>, values: DoubleArray, temperature: Double, maxGap: Double, slipRate: Double, rng: Random): Choice {
        val order = choices.indices.sortedByDescending { values[it] }
        if (slipRate > 0 && rng.nextDouble() < slipRate) {
            val top = order.take(3).filter { values[it] > values[order[0]] - SLIP_GAP }
            return choices[top[rng.nextInt(top.size)]]
        }
        if (temperature <= 0) return choices[order[0]]
        val best = values[order[0]]
        val pool = order.filter { values[it] >= best - maxGap }
        val w = pool.map { exp((values[it] - best) / temperature) }
        var r = rng.nextDouble() * w.sum()
        for ((i, idx) in pool.withIndex()) {
            r -= w[i]
            if (r < 0) return choices[idx]
        }
        return choices[pool.last()]
    }

    private const val SLIP_GAP = 12.0
}
