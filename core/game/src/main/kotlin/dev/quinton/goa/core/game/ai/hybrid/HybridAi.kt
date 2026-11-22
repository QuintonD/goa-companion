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
import dev.quinton.goa.core.game.ai.TeamKills
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HEval
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.puzzles.StateHash
import dev.quinton.goa.core.model.match.Team
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Heuristic-guided determinized search (REVIEW-2-search.md; strength 10 since wave 3, ADR-085 addendum).
 *
 * For a searched decision: [HybridConfig.samples] determinizations from the deciding team's view
 * (strength 10's: enemy hand pools known, the facedown card dealt again from the book's opponent model).
 * Samples that deal the same position are one sample with a weight (after the reveal every hand is
 * known to strength 10, so most questions have a single position). On each, every candidate (all cards
 * for a card play; the book's best few for other questions, with the book's own answer) is answered on a
 * fork and played out to the horizon by the book for every seat, then scored by the leaf evaluator. All
 * candidates of a sample share the determinization and the book answers deterministically, so the
 * comparison is paired (common random numbers). The best weighted mean wins. Every other decision is
 * h10's answer.
 *
 * A playout is a pure function of its position, so its value is remembered for every position it passed
 * (a transposition memo kept for the real turn): a later question whose candidate reaches one of them
 * takes the value without playing it again.
 *
 * No hidden information: samples come from `EngineSession.determinizeWith` with the deciding team's
 * view; the real session is read only through it and for the public log. Deterministic: an answer
 * depends on the seed, the decision id, the seat and the session, whatever [runner] runs the samples
 * (a task only writes its own cell; the memo grows between answers, never during one).
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

    @kotlin.concurrent.Volatile
    private var killEval: Pair<EngineData, Evaluator>? = null

    private val answered = AtomicLong(0)
    private val memoHits = AtomicLong(0)
    private val playouts = AtomicLong(0)
    private val drawn = AtomicLong(0)
    private val distinct = AtomicLong(0)

    /** Engine answers spent in playouts so far (the work measure). */
    val work: Long get() = answered.load()

    /** Diagnostics: playouts started, those the memo answered, samples drawn, distinct positions among them. */
    val stats: List<Long> get() = listOf(playouts.load(), memoHits.load(), drawn.load(), distinct.load())

    /** The transposition memo: position (with what the playout from it means) → leaf value. One real turn. */
    private var memo: HashMap<MemoKey, Double> = HashMap()
    private var memoTurn: Pair<EngineData, Int>? = null

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

    /** One distinct determinized position and how many of the samples dealt it. */
    private class Sample(val det: EngineSession, val dp: Decision, val salt: Long, var weight: Int)

    /** A sample played with the book's enemy replies ([reply] 0) or with enemy card plays drawn ([reply] ≥ 1). */
    private class Column(val sample: Sample, val reply: Int, val weight: Double)

    /** An enemy of [team] has not played its card this turn yet (its choice is still the playout's). */
    private fun enemiesToPlay(s: GameState, team: Team): Boolean =
        !s.revealed && s.heroes.any { it.team != team && it.unresolved.isEmpty() && it.hand.size > 1 }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun search(es: EngineSession, d: Decision, cancelled: () -> Boolean): Choice? {
        val rng = Random(HeuristicAi.mix(seed, d.id * 1_000_003L + d.seat.hashCode()))
        val base = rng.nextLong()
        val isPlay = d.kind == DecisionKind.PLAY_CARD
        val n = if (isPlay) config.samples else config.otherSamples
        val horizon = horizonFor(es.state, isPlay)
        val team = d.team
        val enemy = if (team == Team.RED) Team.BLUE else Team.RED
        // The public log: what each side has shown since its last level up.
        val seenByUs = Searcher.seenCards(es, team)
        val seenByThem = Searcher.seenCards(es, enemy)
        val seenOf = { t: Team -> if (t == team) seenByUs else seenByThem }
        val leafEval = leafOf(es.engineData)
        val memoOn = config.memo && horizon.memoable && config.kill == 0 && !config.leafAverage
        if (memoOn) resetMemo(es)
        val snapshot = if (memoOn) memo else null
        val view = MemoView(team, (seenByUs.hashCode() * 31 + seenByThem.hashCode()), horizon.ordinal)

        val samples = samples(es, d, base, n, seenByUs, seenOf, cancelled) ?: return null
        if (samples.isEmpty()) return null
        drawn.addAndFetch(samples.sumOf { it.weight }.toLong())
        distinct.addAndFetch(samples.size.toLong())
        val first = samples[0]
        val candidates = candidates(es, d, first.dp, first.det, seenByUs, cancelled)
        if (candidates.size <= 1) return candidates.firstOrNull()
        // Columns: each distinct sample, and at a card play with enemies still to choose, [HybridConfig.replies]
        // versions of it: the book's reply (0) and enemy card plays drawn from the opponent model.
        val columns = ArrayList<Column>()
        for (s in samples) {
            val r = if (isPlay && config.replies > 1 && enemiesToPlay(s.det.state, team)) config.replies else 1
            for (k in 0 until r) columns += Column(s, k, s.weight.toDouble() / r)
        }
        val g = columns.size
        val values = Array(candidates.size) { DoubleArray(g) { Double.NaN } }
        val priors = Array(candidates.size) { DoubleArray(g) { Double.NaN } }
        val usePrior = isPlay && config.prior != 0.0
        val found = Array(candidates.size) { arrayOfNulls<List<MemoKey>>(g) }
        // The tree after the reveal ([HybridConfig.treeDepth]): its frontier playouts' paths and values, per task.
        val treeDepth = if (isPlay) config.treeCard else config.treeDepth
        val useTree = treeDepth > 0 && horizon == HybridHorizon.TURN
        val treeFound = Array(candidates.size) { arrayOfNulls<List<Pair<List<MemoKey>, Double>>>(g) }
        val rootTurn = turnIndex(es.state)
        val tasks = ArrayList<() -> Unit>()
        for ((i, col) in columns.withIndex()) {
            val s = col.sample
            if (usePrior) {
                tasks += {
                    if (!cancelled()) {
                        policy.cardValues(s.det, s.dp, seenByUs)?.let { pv ->
                            for ((j, c) in candidates.withIndex()) {
                                val at = s.dp.options.indexOf(c)
                                if (at >= 0) priors[j][i] = pv[at]
                            }
                        }
                    }
                }
            }
            val memoHere = memoOn && col.reply == 0
            for ((j, c) in candidates.withIndex()) {
                if (c !in s.dp.options) continue
                tasks += {
                    if (!cancelled()) {
                        val f = s.det.forkForAnswer()
                        if (BookPolicy.play(f, s.dp, c)) {
                            answered.incrementAndFetch()
                            if (useTree) {
                                val out = if (memoHere) ArrayList<Pair<List<MemoKey>, Double>>() else null
                                val t = TreeRun(
                                    team, seenOf, s.salt, if (memoHere) snapshot else null, view, out,
                                    leafEval, seenByUs, rootTurn, cancelled, col.reply,
                                )
                                values[j][i] = tree(f, treeDepth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, t)
                                treeFound[j][i] = out
                            } else {
                                val path = if (memoHere) ArrayList<MemoKey>() else null
                                values[j][i] = playout(
                                    f, team, horizon, seenOf, s.salt, if (memoHere) snapshot else null, view, path,
                                    leafEval, seenByUs, col.reply,
                                )
                                found[j][i] = path
                            }
                        }
                    }
                }
            }
        }
        runner.runAll(tasks)
        if (cancelled()) return null
        if (memoOn) {
            // The memo grows only here, in task order, so every answer sees the same memo.
            for (j in candidates.indices) {
                for (i in 0 until g) {
                    val path = found[j][i] ?: continue
                    val v = values[j][i]
                    if (v.isNaN()) continue
                    for (k in path) memo[k] = v
                }
            }
            for (j in candidates.indices) {
                for (i in 0 until g) {
                    for ((path, v) in treeFound[j][i] ?: continue) for (k in path) memo[k] = v
                }
            }
        }
        if (config.worstReply > 0.0) robust(values, columns)
        val bookAnswer = candidates[0]
        var best: Choice? = null
        var bestV = Double.NEGATIVE_INFINITY
        for ((j, c) in candidates.withIndex()) {
            var wSum = 0.0
            var sum = 0.0
            for (i in 0 until g) {
                val v = values[j][i]
                if (v.isNaN()) continue
                wSum += columns[i].weight
                sum += columns[i].weight * v
            }
            if (wSum == 0.0) continue
            val m = sum / wSum
            var sq = 0.0
            for (i in 0 until g) {
                val v = values[j][i]
                if (!v.isNaN()) sq += columns[i].weight * (v - m) * (v - m)
            }
            val sd = if (wSum < 2) 0.0 else sqrt(sq / (wSum - 1))
            var score = m - config.risk * sd
            if (usePrior) {
                var pw = 0.0
                var ps = 0.0
                for (i in 0 until g) {
                    val p = priors[j][i]
                    if (p.isNaN()) continue
                    pw += columns[i].weight
                    ps += columns[i].weight * p
                }
                if (pw > 0) score = (1 - config.prior) * score + config.prior * ps / pw
            }
            // The book's own answer is first and keeps a tie.
            if (score > bestV) {
                bestV = score
                best = c
            }
        }
        trace?.invoke(
            "${d.seat} ${d.kind} samples=${samples.size}/$n " + candidates.indices.joinToString(" ") { j ->
                val v = values[j].filter { !it.isNaN() }
                "${candidates[j]}=${if (v.isEmpty()) "-" else (kotlin.math.round(v.average() * 10) / 10).toString()}"
            } + " -> $best (book $bookAnswer)",
        )
        return best?.takeIf { it in d.options }
    }

    /**
     * [HybridConfig.worstReply]: each sample's columns (its enemy replies, consecutive in [columns]) take
     * (1 − w) × their mean + w × their worst, so a card that only works against the likely reply pays for it.
     */
    @Suppress("NestedBlockDepth")
    private fun robust(values: Array<DoubleArray>, columns: List<Column>) {
        val w = config.worstReply.coerceIn(0.0, 1.0)
        for (row in values) {
            var i = 0
            while (i < columns.size) {
                var end = i
                while (end < columns.size && columns[end].sample === columns[i].sample) end++
                var sum = 0.0
                var n = 0
                var min = Double.POSITIVE_INFINITY
                for (k in i until end) {
                    val v = row[k]
                    if (v.isNaN()) continue
                    sum += v
                    n++
                    if (v < min) min = v
                }
                if (n > 1) {
                    val r = (1 - w) * (sum / n) + w * min
                    for (k in i until end) if (!row[k].isNaN()) row[k] = r
                }
                i = end
            }
        }
    }

    /** What one tree under a root candidate needs ([tree]). */
    private class TreeRun(
        val team: Team,
        val seenOf: (Team) -> Map<String, Set<String>>,
        val salt: Long,
        val memo: Map<MemoKey, Double>?,
        val view: MemoView,
        /** The frontier playouts' paths and values, for the memo after the answer. */
        val out: MutableList<Pair<List<MemoKey>, Double>>?,
        val leafEval: PositionEvaluator?,
        val seen: Map<String, Set<String>>,
        /** The real question's turn: the tree ends with it (the turn horizon). */
        val turn: Int,
        val cancelled: () -> Boolean,
        /** The column's enemy replies at a card play (0: the book's), drawn as [playout] draws them. */
        val reply: Int = 0,
    ) {
        val replyRng: Random? = if (reply > 0) Random(HeuristicAi.mix(salt, REPLY_SALT + reply)) else null
    }

    /**
     * Minimax with alpha-beta over the rest of the turn from [f] ([HybridConfig.treeDepth]): [depth] more
     * choices may branch. A question with one option is answered; one the tree does not branch on (a kind
     * outside [HybridConfig.treeKinds], an enemy's with [HybridConfig.treeEnemy] 0) gets the book's answer;
     * at depth 0 the book plays out to the turn's end (the memo's playout) and the leaf scores it. Branches
     * are the book's own answer and its best by static score ([HybridConfig.treeWidth]), the book's first, so
     * the cut-offs come early. Ours maximise, the enemy's minimise; the value is from [TreeRun.team]'s view.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun tree(f: EngineSession, depth: Int, alpha0: Double, beta0: Double, t: TreeRun): Double {
        var steps = 0
        var d: Decision
        while (true) {
            d = f.pending ?: return leaf(f, t.team, t.seen, t.leafEval)
            if (turnIndex(f.state) != t.turn || steps++ > config.maxSteps) return leaf(f, t.team, t.seen, t.leafEval)
            if (d.options.size > 1) {
                if (depth <= 0 || t.cancelled()) {
                    val path = if (t.out != null) ArrayList<MemoKey>() else null
                    val v = playout(
                        f, t.team, HybridHorizon.TURN, t.seenOf, t.salt, t.memo, t.view, path, t.leafEval, t.seen, t.reply,
                    )
                    if (path != null) t.out?.add(path to v)
                    return v
                }
                val ours = d.team == t.team
                // Card plays are simultaneous and hidden: the tree branches only after the reveal.
                val branches = d.kind != DecisionKind.PLAY_CARD &&
                    (config.treeKinds.isEmpty() || d.kind in config.treeKinds) && (ours || config.treeEnemy == 1)
                if (branches) break
            }
            val mode = if (d.team == t.team) config.ownPolicy else config.enemyPolicy
            val drawn = if (t.replyRng != null && d.team != t.team && d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) {
                drawReply(f, d, t.seenOf, t.replyRng)
            } else {
                null
            }
            val c = drawn ?: if (d.options.size == 1) d.options[0] else policy.answer(f, d, mode, t.seenOf(d.team), t.salt)
            if (!BookPolicy.play(f, d, c)) return leaf(f, t.team, t.seen, t.leafEval)
            answered.incrementAndFetch()
        }
        val maxing = d.team == t.team
        val mode = if (maxing) config.ownPolicy else config.enemyPolicy
        val first = policy.answer(f, d, mode, t.seenOf(d.team), t.salt)
        val options = if (d.options.size <= config.treeWidth) {
            (listOf(first) + d.options).distinct()
        } else {
            val sv = policy.staticValues(f, d, t.seenOf(d.team))
            (listOf(first) + d.options.indices.sortedByDescending { sv[it] }.map { d.options[it] })
                .distinct().take(config.treeWidth.coerceAtLeast(1))
        }
        var alpha = alpha0
        var beta = beta0
        var best = if (maxing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        for (c in options) {
            val g = f.forkForAnswer()
            if (!BookPolicy.play(g, d, c)) continue
            answered.incrementAndFetch()
            val v = tree(g, depth - 1, alpha, beta, t)
            if (maxing) {
                if (v > best) best = v
                if (best > alpha) alpha = best
            } else {
                if (v < best) best = v
                if (best < beta) beta = best
            }
            if (alpha >= beta) break
        }
        return if (best.isInfinite()) leaf(f, t.team, t.seen, t.leafEval) else best
    }

    /** Diagnostics only: one line per searched decision. */
    var trace: ((String) -> Unit)? = null

    /**
     * [n] determinizations; those that deal the same position (same state at the step's start, same
     * answers since) are merged into one weighted sample. Null when cancelled.
     */
    private fun samples(
        es: EngineSession,
        d: Decision,
        base: Long,
        n: Int,
        seenByUs: Map<String, Set<String>>,
        seenOf: (Team) -> Map<String, Set<String>>,
        cancelled: () -> Boolean,
    ): List<Sample>? {
        val dets = arrayOfNulls<EngineSession>(n)
        val tasks = (0 until n).map { i ->
            { if (!cancelled()) dets[i] = determinize(es, d, HeuristicAi.mix(base, i.toLong()), seenByUs, seenOf) }
        }
        if (n > 2) runner.runAll(tasks) else tasks.forEach { it() }
        if (cancelled()) return null
        val out = ArrayList<Sample>()
        val byKey = HashMap<PosKey, Sample>()
        for (i in 0 until n) {
            val det = dets[i] ?: continue
            val dp = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind } ?: continue
            if (config.dedupe) {
                val key = posKey(det)
                val same = byKey[key]
                if (same != null) {
                    same.weight++
                    continue
                }
                val s = Sample(det, dp, HeuristicAi.mix(base, i.toLong()), 1)
                byKey[key] = s
                out += s
            } else {
                out += Sample(det, dp, HeuristicAi.mix(base, i.toLong()), 1)
            }
        }
        return out
    }

    private fun horizonFor(s: GameState, isPlay: Boolean): HybridHorizon {
        val byTurn = if (isPlay) config.playHorizonByTurn else config.otherHorizonByTurn
        return byTurn[s.turn] ?: if (isPlay) config.horizon else config.otherHorizon
    }

    /**
     * What is searched: every card for a card play (less those the book's card look scores far below
     * its best, [HybridConfig.prune]); for another question the book's own answer and its best few by
     * static score ([HybridConfig.topFor]). The book's answer comes first.
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
        if (d.kind == DecisionKind.PLAY_CARD) {
            val all = (listOf(own) + legal).distinct().filter { it in d.options }
            if (config.prune <= 0.0 || all.size <= PRUNE_KEEP) return all
            val cv = policy.cardValues(det, dp, seen) ?: return all
            val byCard = dp.options.indices.associate { dp.options[it] to cv[it] }
            val ranked = all.sortedByDescending { byCard[it] ?: Double.NEGATIVE_INFINITY }
            val top = byCard[ranked[0]] ?: return all
            val keep = ranked.filterIndexed { i, c ->
                i < PRUNE_KEEP || (byCard[c] ?: Double.NEGATIVE_INFINITY) >= top - config.prune
            }.toSet()
            return all.filter { it == own || it in keep }
        }
        val top = config.topFor(d.kind)
        if (legal.size <= top) return (listOf(own) + legal).distinct().filter { it in d.options }
        val sv = policy.staticValues(det, dp, seen)
        val ranked = dp.options.indices.sortedByDescending { sv[it] }.map { dp.options[it] }.filter { it in d.options }
        return (listOf(own) + ranked.take(top)).distinct()
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

    /**
     * Play on to [horizon] with the book for every seat (ours with [HybridConfig.ownPolicy]) and return
     * the leaf value. With a [memo], a position it already valued ends the playout at once; [path]
     * collects the positions passed (for the memo).
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    private fun playout(
        f: EngineSession,
        rootTeam: Team,
        horizon: HybridHorizon,
        seenOf: (Team) -> Map<String, Set<String>>,
        salt: Long,
        memo: Map<MemoKey, Double>?,
        view: MemoView,
        path: MutableList<MemoKey>?,
        leafEval: PositionEvaluator?,
        seen: Map<String, Set<String>>,
        reply: Int = 0,
    ): Double {
        playouts.incrementAndFetch()
        val replyRng = if (reply > 0) Random(HeuristicAi.mix(salt, REPLY_SALT + reply)) else null
        val start = f.state
        val startTurn = turnIndex(start)
        val startStep = start.step
        val startSeat = start.stepSeat
        val startActor = start.acting
        val startRound = start.round
        val turnsWanted = horizon.turns
        var turn = startTurn
        var turnsEnded = 0
        var newRoundTurn = -1
        var n = 0
        var limit = config.maxSteps
        var kills: Map<String, String>? = null
        var extended = false
        var leafSum = 0.0
        var leafCount = 0
        while (n < limit) {
            val d = f.pending ?: break
            val s = f.state
            var turnEnd = false
            if (turnIndex(s) != turn) {
                turn = turnIndex(s)
                turnsEnded++
                turnEnd = true
            }
            if (s.round != startRound && newRoundTurn < 0) newRoundTurn = turnIndex(s)
            val stop = when (horizon) {
                HybridHorizon.ACTION ->
                    s.round != startRound || s.step != startStep || s.stepSeat != startSeat ||
                        s.acting != startActor || turnIndex(s) != startTurn

                HybridHorizon.TURN -> turnsEnded >= (if (extended) 2 else 1)
                HybridHorizon.TWO_TURNS -> turnsEnded >= (if (extended) 3 else 2)
                HybridHorizon.ROUND -> s.round != startRound
                HybridHorizon.ROUND_PLUS -> newRoundTurn >= 0 && turnIndex(s) != newRoundTurn
            }
            if (stop && !extended && config.kill > 0 && turnsEnded == turnsWanted && turnsWanted > 0) {
                kills = killPlan(f.engineData, s, rootTeam, startRound)
                if (kills != null) {
                    extended = true
                    limit = n + config.maxSteps
                    continue
                }
            }
            if (stop) break
            if (turnEnd && config.leafAverage) {
                leafSum += leaf(f, rootTeam, seen, leafEval)
                leafCount++
            }
            if (memo != null && path != null && !extended) {
                val key = MemoKey(posKey(f), turnsWanted - turnsEnded, view)
                val known = memo[key]
                if (known != null) {
                    memoHits.incrementAndFetch()
                    return known
                }
                path += key
            }
            val planned = if (extended && d.kind == DecisionKind.PLAY_CARD) {
                kills?.get(d.seat)?.let { Choice.Card(it) }?.takeIf { it in d.options }
            } else {
                null
            }
            val drawn = if (replyRng != null && turnsEnded == 0 && d.team != rootTeam && d.kind == DecisionKind.PLAY_CARD) {
                drawReply(f, d, seenOf, replyRng)
            } else {
                null
            }
            val c = planned ?: drawn ?: run {
                val mode = if (d.team == rootTeam) config.ownPolicy else config.enemyPolicy
                policy.answer(f, d, mode, seenOf(d.team), salt)
            }
            if (!BookPolicy.play(f, d, c)) break
            answered.incrementAndFetch()
            n++
        }
        val end = leaf(f, rootTeam, seen, leafEval)
        return if (leafCount == 0) end else (leafSum + end) / (leafCount + 1)
    }

    /** An enemy's card play drawn from the opponent model (softmax of the book's card scores, 15% uniform). */
    private fun drawReply(f: EngineSession, d: Decision, seenOf: (Team) -> Map<String, Set<String>>, rng: Random): Choice? {
        val pool = d.options.map { (it as? Choice.Card)?.cardId ?: return null }
        val w = policy.playWeights(
            f.engineData, f.setup, f.state, d.seat, pool, maxOf(1, config.replyModel), config.replyTau, seenOf,
        ) ?: return null
        var r = rng.nextDouble() * w.sum()
        for ((i, x) in w.withIndex()) {
            r -= x
            if (r < 0) return d.options[i]
        }
        return d.options.last()
    }

    /**
     * The kill extension (ADR-061's, on the book's playouts): at the playout's end, when the next turn is
     * in the same round and either team can kill an enemy hero on it with several heroes together
     * ([TeamKills]), the playout runs on through that turn with the attackers playing their kill cards.
     * [HybridConfig.kill] 1: both teams' kills; 2: ours only.
     */
    private fun killPlan(data: EngineData, s: GameState, team: Team, round: Int): Map<String, String>? {
        if (s.round != round || s.winner != null) return null
        val ev = killEvaluator(data)
        val enemy = if (team == Team.RED) Team.BLUE else Team.RED
        val ours = TeamKills.chances(ev, s, team).firstOrNull()?.attackers.orEmpty()
        val theirs = if (config.kill == 1) TeamKills.chances(ev, s, enemy).firstOrNull()?.attackers.orEmpty() else emptyMap()
        return (ours + theirs).takeIf { it.isNotEmpty() }
    }

    private fun killEvaluator(data: EngineData): Evaluator {
        killEval?.let { if (it.first === data) return it.second }
        val e = Evaluator(data, AiPlayers.TUNED_WITH_NET)
        killEval = data to e
        return e
    }

    private fun leaf(f: EngineSession, team: Team, seen: Map<String, Set<String>>, eval: PositionEvaluator?): Double {
        if (eval != null) return eval.evaluate(f.state, team)
        val ctx = HContext(f, f.pending, team, policy.profile, seen = seen, finishing = true)
        return HEval.evaluate(ctx, team)
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

    /** The memo holds one real turn (a playout never outlives its turn but for the horizons that skip the memo). */
    private fun resetMemo(es: EngineSession) {
        val now = es.engineData to turnIndex(es.state)
        val was = memoTurn
        if (was == null || was.first !== now.first || was.second != now.second || memo.size > MEMO_CAP) {
            memo = HashMap()
            memoTurn = now
        }
    }

    /** A position: the state at its step's start (as the puzzle solver hashes it) and the answers since. */
    private data class PosKey(val a: Long, val b: Long, val answers: Int, val decisions: Long)

    /** What a playout from a position means: whose view, which public log, how many turn ends are left. */
    private data class MemoView(val team: Team, val seen: Int, val horizon: Int)

    private data class MemoKey(val pos: PosKey, val turnsLeft: Int, val view: MemoView)

    private fun posKey(s: EngineSession): PosKey {
        val (a, b) = StateHash.of(s.checkpointState)
        return PosKey(a, b, s.stepAnswers.hashCode(), s.checkpointState.decisions)
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

        /** Card pruning keeps at least this many cards (and the book's own). */
        private const val PRUNE_KEEP = 3

        private const val MEMO_CAP = 400_000
        private const val REPLY_SALT = 0x2E9_1E5L

        fun turnIndex(s: GameState): Int = s.round * 8 + s.turn
    }
}
