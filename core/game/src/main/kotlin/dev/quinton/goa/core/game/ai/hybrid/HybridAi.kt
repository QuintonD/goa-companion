package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Fallback
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.PositionEvaluator
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HEval
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Heuristic-guided determinized search (prototype, REVIEW-2-search.md; opt-in, not seated by `AiPlayers`).
 *
 * For a searched decision: [HybridConfig.samples] determinizations from the deciding team's view
 * (strength 10's: enemy hand pools known, the facedown card dealt again from the book's opponent model);
 * on each, every candidate (all cards for a card play; the book's best few for other questions, with
 * the book's own answer) is answered on a fork and played out to the horizon by the book for every
 * seat, then scored by the leaf evaluator. All candidates of a sample share the determinization and the
 * book answers deterministically, so the comparison is paired (common random numbers). The best mean
 * wins. Every other decision is h10's answer.
 *
 * No hidden information: samples come from `EngineSession.determinizeWith` with the deciding team's
 * view; the real session is read only through it and for the public log. Deterministic: an answer
 * depends on the seed, the decision id, the seat and the session, whatever [runner] runs the samples.
 */
@OptIn(ExperimentalAtomicApi::class)
class HybridAi(
    private val seed: Long,
    knobs: Map<String, Double> = emptyMap(),
    private val runner: SampleRunner = SampleRunner.SEQUENTIAL,
) : AiPlayer {
    val config: HybridConfig = HybridConfig.fromKnobs(knobs)
    private val bookTune = knobs.filterKeys { it !in HybridConfig.NAMES }

    /** h10: the answer to everything the search does not take, and the fallback. */
    val book: HeuristicAi = HeuristicAi(10, seed, tune = bookTune)
    private val policy = BookPolicy(seed, bookTune)

    @kotlin.concurrent.Volatile
    private var leafCache: Pair<EngineData, PositionEvaluator?>? = null

    private val answered = AtomicLong(0)

    /** Engine answers spent in playouts so far (the work measure). */
    val work: Long get() = answered.load()

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        if (decision.options.isEmpty()) return Fallback.choose(decision)
        if (decision.options.size == 1) return decision.options.single()
        val es = session as? EngineSession ?: return book.choose(session, decision, cancelled)
        if (!searched(es, decision)) return book.choose(session, decision, cancelled)
        return try {
            search(es, decision, cancelled) ?: book.choose(session, decision, cancelled)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            book.choose(session, decision, cancelled)
        }
    }

    private fun searched(es: EngineSession, d: Decision): Boolean = when (config.scope) {
        1 -> d.kind == DecisionKind.PLAY_CARD
        2 -> d.kind == DecisionKind.PLAY_CARD || (d.kind in OWN_ACTION && es.state.acting == d.seat)
        else -> d.kind != DecisionKind.ACT_FIRST
    }

    private fun search(es: EngineSession, d: Decision, cancelled: () -> Boolean): Choice? {
        val rng = Random(HeuristicAi.mix(seed, d.id * 1_000_003L + d.seat.hashCode()))
        val base = rng.nextLong()
        val isPlay = d.kind == DecisionKind.PLAY_CARD
        val n = if (isPlay) config.samples else config.otherSamples
        val horizon = if (isPlay) config.horizon else config.otherHorizon
        val team = d.team
        val enemy = if (team == Team.RED) Team.BLUE else Team.RED
        // The public log: what each side has shown since its last level up.
        val seenByUs = Searcher.seenCards(es, team)
        val seenByThem = Searcher.seenCards(es, enemy)
        val seenOf = { t: Team -> if (t == team) seenByUs else seenByThem }
        val leafEval = leafOf(es.engineData)

        val first = determinize(es, d, HeuristicAi.mix(base, 0), seenByUs, seenOf)
        val dp0 = first.pending?.takeIf { it.seat == d.seat && it.kind == d.kind } ?: return null
        val candidates = candidates(es, d, dp0, first, seenByUs, cancelled)
        if (candidates.size <= 1) return candidates.firstOrNull()
        val values = Array(candidates.size) { DoubleArray(n) { Double.NaN } }
        val priors = Array(candidates.size) { DoubleArray(n) { Double.NaN } }
        val usePrior = isPlay && config.prior != 0.0
        val tasks = (0 until n).map { i ->
            {
                if (!cancelled()) {
                    val det = if (i == 0) first else determinize(es, d, HeuristicAi.mix(base, i.toLong()), seenByUs, seenOf)
                    val dp = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind }
                    if (dp != null) {
                        if (usePrior) {
                            policy.cardValues(det, dp, seenByUs)?.let { pv ->
                                for ((j, c) in candidates.withIndex()) {
                                    val at = dp.options.indexOf(c)
                                    if (at >= 0) priors[j][i] = pv[at]
                                }
                            }
                        }
                        for ((j, c) in candidates.withIndex()) {
                            if (cancelled()) break
                            if (c !in dp.options) continue
                            val f = det.forkForAnswer()
                            if (!BookPolicy.play(f, dp, c)) continue
                            answered.incrementAndFetch()
                            playout(f, team, horizon, seenOf, HeuristicAi.mix(base, i.toLong()))
                            values[j][i] = leaf(f, team, seenByUs, leafEval)
                            observe(d, i, j, f, team)
                        }
                    }
                }
            }
        }
        runner.runAll(tasks)
        if (cancelled()) return null
        val bookAnswer = candidates[0]
        var best: Choice? = null
        var bestV = Double.NEGATIVE_INFINITY
        for ((j, c) in candidates.withIndex()) {
            val v = values[j].filter { !it.isNaN() }
            if (v.isEmpty()) continue
            val m = v.average()
            val sd = if (v.size < 2) 0.0 else sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
            var score = m - config.risk * sd
            if (usePrior) {
                val p = priors[j].filter { !it.isNaN() }
                if (p.isNotEmpty()) score = (1 - config.prior) * score + config.prior * p.average()
            }
            // The book's own answer is first and keeps a tie.
            if (score > bestV) {
                bestV = score
                best = c
            }
        }
        trace?.invoke(
            "${d.seat} ${d.kind} " + candidates.indices.joinToString(" ") { j ->
                val v = values[j].filter { !it.isNaN() }
                "${candidates[j]}=${if (v.isEmpty()) "-" else (kotlin.math.round(v.average() * 10) / 10).toString()}"
            } + " -> $best (book $bookAnswer)",
        )
        return best?.takeIf { it in d.options }
    }

    /** Diagnostics only: one line per searched decision. */
    var trace: ((String) -> Unit)? = null

    /**
     * Calibration only (Track B, HEVAL.md "Leaf ranking"): every scored leaf, with the decision, the
     * sample, the candidate's index and the deciding team. Null in play. Runs on the sample's thread.
     */
    var leafObserver: ((Decision, Int, Int, EngineSession, Team) -> Unit)? = null

    private fun observe(d: Decision, sample: Int, candidate: Int, leaf: EngineSession, team: Team) {
        leafObserver?.invoke(d, sample, candidate, leaf, team)
    }

    /**
     * What is searched: every card for a card play; for another question the book's own answer and its
     * best [HybridConfig.top] by static score. The book's answer comes first.
     */
    private fun candidates(
        es: EngineSession,
        d: Decision,
        dp: Decision,
        det: EngineSession,
        seen: Map<String, Set<String>>,
        cancelled: () -> Boolean,
    ): List<Choice> {
        val own = book.choose(es, d, cancelled)
        val legal = d.options.filter { it in dp.options }
        if (d.kind == DecisionKind.PLAY_CARD || legal.size <= config.top) {
            return (listOf(own) + legal).distinct().filter { it in d.options }
        }
        val sv = policy.staticValues(det, dp, seen)
        val ranked = dp.options.indices.sortedByDescending { sv[it] }.map { dp.options[it] }.filter { it in d.options }
        return (listOf(own) + ranked.take(config.top)).distinct()
    }

    private fun determinize(
        es: EngineSession,
        d: Decision,
        sampleSeed: Long,
        seenByUs: Map<String, Set<String>>,
        seenOf: (Team) -> Map<String, Set<String>>,
    ): EngineSession {
        val humans = es.setup.seats.filter {
            it.team == d.team && it.controller == Controller.Human && it.heroId != d.seat
        }.map { it.heroId }.toSet()
        val data = es.engineData
        val setup = es.setup
        val options = Determinizer.Options(
            seen = seenByUs,
            playWeight = if (config.model > 0) {
                { st: GameState, hero: String, pool: List<String> ->
                    policy.playWeights(data, setup, st, hero, pool, config.model, config.tau, seenOf)
                        ?: List(pool.size) { 1.0 }
                }
            } else {
                null
            },
            hidePlayedOf = humans,
            knowHands = policy.profile.knowHands,
        )
        return es.determinizeWith(d.team, sampleSeed, options, quiet = true)
    }

    /** Play on to [horizon] with the book for every seat (ours with [HybridConfig.ownPolicy]). */
    private fun playout(
        f: EngineSession,
        rootTeam: Team,
        horizon: HybridHorizon,
        seenOf: (Team) -> Map<String, Set<String>>,
        salt: Long,
    ) {
        val start = f.state
        val startTurn = turnIndex(start)
        val startStep = start.step
        val startSeat = start.stepSeat
        val startActor = start.acting
        val startRound = start.round
        var turn = startTurn
        var turnsEnded = 0
        var newRoundTurn = -1
        var n = 0
        while (n < config.maxSteps) {
            val d = f.pending ?: return
            val s = f.state
            if (turnIndex(s) != turn) {
                turn = turnIndex(s)
                turnsEnded++
            }
            if (s.round != startRound && newRoundTurn < 0) newRoundTurn = turnIndex(s)
            val stop = when (horizon) {
                HybridHorizon.ACTION ->
                    s.round != startRound || s.step != startStep || s.stepSeat != startSeat ||
                        s.acting != startActor || turnIndex(s) != startTurn

                HybridHorizon.TURN -> turnsEnded >= 1
                HybridHorizon.TWO_TURNS -> turnsEnded >= 2
                HybridHorizon.ROUND -> s.round != startRound
                HybridHorizon.ROUND_PLUS -> newRoundTurn >= 0 && turnIndex(s) != newRoundTurn
            }
            if (stop) return
            val mode = if (d.team == rootTeam) config.ownPolicy else config.enemyPolicy
            val c = policy.answer(f, d, mode, seenOf(d.team), salt)
            if (!BookPolicy.play(f, d, c)) return
            answered.incrementAndFetch()
            n++
        }
    }

    private fun leaf(f: EngineSession, team: Team, seen: Map<String, Set<String>>, eval: PositionEvaluator?): Double {
        if (eval != null) return eval.evaluate(f.state, team)
        val ctx = HContext(f, f.pending, team, policy.profile, seen = seen, finishing = true)
        // Track B: the leaf's own weight set (`hevLeaf`; the book's set unless a knob says otherwise).
        return HEval.evaluateLeaf(ctx, team)
    }

    private fun leafOf(data: EngineData): PositionEvaluator? {
        leafCache?.let { if (it.first === data) return it.second }
        val e: PositionEvaluator? = when (config.leaf) {
            HybridLeaf.HEVAL -> null
            HybridLeaf.OLD -> Evaluator(data, AiPlayers.TUNED_WITH_NET)
            HybridLeaf.NET -> NetEvaluator(data, LearnedNet.ENSEMBLE)
        }
        leafCache = data to e
        return e
    }

    companion object {
        /** The hero's own action choices (scope 2). */
        private val OWN_ACTION = setOf(
            DecisionKind.CHOOSE_ACTION,
            DecisionKind.CHOOSE_UNIT,
            DecisionKind.CHOOSE_SPACE,
            DecisionKind.CHOOSE_OPTION,
            DecisionKind.CHOOSE_TOKEN,
            DecisionKind.CONFIRM,
            DecisionKind.CHOOSE_NUMBER,
        )

        fun turnIndex(s: GameState): Int = s.round * 8 + s.turn
    }
}
