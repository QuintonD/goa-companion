package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.domain.monotonicNanos
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.sqrt
import kotlin.random.Random

/** How far a playout runs before the position is evaluated. */
internal enum class Horizon {
    /** Until the card being resolved (or the end-of-round step) is done. */
    ACTION,

    /** Until the end of this turn (through the end of round after turn 4). */
    TURN,

    /** Until the end of the next turn played (after a round's end: the new round's first turn). */
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
    /** The level's own hero plays its later decisions in a playout by one-ply search (Expert). */
    val greedySelf: Boolean,
    /** Wall-clock cap per PLAY_CARD decision (device budget); smaller decisions get a third. */
    val budgetMs: Long,
    /** Teammates (and with [greedyEnemies] the enemies) also play their playout actions by one-ply search. */
    val greedyTeam: Boolean = false,
    val greedyEnemies: Boolean = false,
    /** Opaque choices ([Searcher.opaque]) inside playouts are answered one ply deep instead of statically. */
    val shallow: Boolean = true,
    /** Static strengths: how often a move forgets the minion defense modifiers (STRATEGY §4.2). */
    val blindRate: Double = 0.0,
    /** Softmax temperature of the opponent model's card guess (lower: trusts the rules of thumb more). */
    val opponentTau: Double = Searcher.OPPONENT_TAU,
    /** Options a one-ply look in a playout tries (the best by static score). */
    val greedyMax: Int = Searcher.GREEDY_MAX,
    /** How far an upgrade choice is played out: TURN stops as the new round begins, NEXT_TURN plays its first turn. */
    val upgradeHorizon: Horizon = Horizon.TURN,
    /** A one-sided taste for kills (the exploiter of AI.md "Team sizes"); null = symmetric. */
    val hunt: Hunt? = null,
    /**
     * Tests only: stop sampling once this many engine answers were spent in playouts for a card play
     * (a third for other decisions), as the wall-clock cap would on a phone, but reproducibly. 0 = no cap.
     */
    val workBudget: Long = 0,
    /** Racing margin in coin-equivalents ([Searcher.race]); 0 = every candidate gets every sample. */
    val race: Double = 0.0,
    /** Where the learned evaluation scores positions instead of [knobs]' hand one (AI.md "Learned evaluation"). */
    val learned: EvalSource = EvalSource.HAND,
    /** The net [learned] uses; null = the checked-in one ([LearnedNet.CURRENT]). */
    val net: LearnedNet? = null,
    /**
     * Teammates' hands, items and facedown cards are guessed too: the coach speaks to one player, who
     * may not see them (p.4). A computer team's own search counts them as announced (AI.md "The determinizer").
     */
    val hideTeammates: Boolean = false,
    /** Tests only: what scores the end of a playout instead of [net] (the frozen baseline, `net=v09`). */
    val leafEvaluator: ((EngineData) -> PositionEvaluator)? = null,
    /**
     * Joint team card play (ADR-061): at a card play, each computer teammate still to play this turn
     * tries its best [joint] cards by static score with each of ours, and we play our part of the best
     * plan. 0 = teammates play their static best card in the playouts.
     */
    val joint: Int = 0,
)

/**
 * One option of a search: an answer, and for a joint card play ([LevelProfile.joint]) the cards the
 * computer teammates still to play this turn play in its playouts (table talk before the reveal, p.4).
 */
internal data class Plan(val choice: Choice, val mates: Map<String, String> = emptyMap())

/**
 * How a kill-hunting player weighs the board, relative to the symmetric evaluation: its own heroes'
 * exposure × [selfExposure], the enemy heroes' × [enemyExposure], the enemy's Life counters × [enemyLife].
 */
internal data class Hunt(val selfExposure: Double = 0.0, val enemyExposure: Double = 2.0, val enemyLife: Double = 1.5) {
    fun knobs(base: EvalKnobs, own: Team): EvalKnobs {
        val enemy = if (own == Team.RED) Team.BLUE else Team.RED
        return base.copy(
            exposureByTeam = mapOf(own to selfExposure, enemy to enemyExposure),
            lifeByTeam = mapOf(enemy to enemyLife),
        )
    }
}

/**
 * Runs the samples of one answer (AI.md "Budgets"). The engine has no threads (it must stay
 * multiplatform), so the default runs them one after the other; the Android app supplies a runner on a
 * few threads. Each sample writes only its own column and the values are combined in sample order, so
 * an answer without a wall-clock cap is the same whatever the runner.
 */
interface SampleRunner {
    /** How many samples are started together (the search checks its deadline between batches). */
    val width: Int

    /** Runs every task, possibly at the same time, and returns when all are done. */
    fun runAll(tasks: List<() -> Unit>)

    companion object {
        val SEQUENTIAL: SampleRunner = object : SampleRunner {
            override val width: Int = 1

            override fun runAll(tasks: List<() -> Unit>) = tasks.forEach { it() }
        }
    }
}

/** One option's values over the samples (NaN where the option did not exist in a sample). */
internal class OptionValue(val choice: Choice, val values: DoubleArray) {
    val n: Int get() = values.count { !it.isNaN() }
    val mean: Double get() = values.filter {
        !it.isNaN()
    }.average().let { if (it.isNaN()) Double.NEGATIVE_INFINITY else it }
    val sd: Double
        get() {
            val v = values.filter { !it.isNaN() }
            if (v.size < 2) return 0.0
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
        }
}

/** A playout's end, kept for the coach's explanations. */
internal class Leaf(
    val choice: Choice,
    val sample: Int,
    val end: EngineSession,
    val startEvents: Int,
    val value: Double,
)

internal class Analysis(
    val decision: Decision,
    val options: List<OptionValue>,
    val leaves: List<Leaf>,
    val samples: Int,
    /** Sample indices the search went through (0 until [tried]), done or skipped; [Searcher.extend] repeats them. */
    val tried: Int = samples,
)

/**
 * Evaluates the options of a decision by playouts on determinized copies (never the real session's
 * hidden cards): for each sample, fork, answer the option, play on with the rollout policy to the
 * horizon, evaluate. Shared by the levels and the coach.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class Searcher(
    val ev: Evaluator,
    val profile: LevelProfile,
    private val keepLeaves: Boolean = false,
    private val runner: SampleRunner = SampleRunner.SEQUENTIAL,
) {
    val static = StaticPolicy(ev)

    private val answered = AtomicLong(0)

    /** Engine answers spent in playouts so far (the [LevelProfile.workBudget] measure, [AiBenchmark]'s; any runner). */
    val work: Long get() = answered.load()

    /** What scores the end of a playout ([LevelProfile.learned]). */
    val leaf: PositionEvaluator = when {
        profile.learned == EvalSource.HAND -> ev
        profile.leafEvaluator != null -> profile.leafEvaluator.invoke(ev.data)
        else -> NetEvaluator(ev.data, profile.net ?: LearnedNet.CURRENT)
    }

    /** What scores the one-ply looks inside a playout. */
    private val inner: PositionEvaluator = if (profile.learned == EvalSource.EVERYWHERE) leaf else ev

    fun determinize(es: EngineSession, d: Decision, seed: Long): EngineSession {
        val team = d.team
        val humans = es.setup.seats.filter {
            it.team == team && it.controller == Controller.Human && it.heroId != d.seat
        }
            .map { it.heroId }.toSet()
        val teammates = if (profile.hideTeammates) {
            es.setup.seats.filter { it.team == team && it.heroId != d.seat }.map { it.heroId }.toSet()
        } else {
            emptySet()
        }
        val options = Determinizer.Options(
            seen = if (profile.memory) seenCards(es, team) else emptyMap(),
            playWeight = if (profile.modelPlays) ::playWeights else null,
            hidePlayedOf = humans,
            hideAllOf = teammates,
        )
        // A search copy: no explanations for a player are built at its questions (the same options).
        return es.determinizeWith(team, seed, options, quiet = true)
    }

    /** The opponent model (STRATEGY §3.1): softmax of the hero's own static card scores, 15% uniform. */
    private fun playWeights(state: GameState, heroId: String, pool: List<String>): List<Double> {
        val ctx = ev.context(state)
        val scores = pool.map { static.playScore(ctx, heroId, Choice.Card(it)) }
        val best = scores.maxOrNull() ?: 0.0
        val soft = scores.map { PortableMath.exp((it - best) / profile.opponentTau) }
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
        mustInclude: Choice? = null,
    ): Analysis? {
        val isPlay = d.kind == DecisionKind.PLAY_CARD
        val run = Run(es, d, seed, cancelled, if (isPlay) profile.playSamples else profile.otherSamples)
        val work0 = work
        val workCap = when {
            profile.workBudget <= 0 -> Long.MAX_VALUE
            isPlay -> profile.workBudget
            else -> profile.workBudget / 3
        }
        val overBudget = { cancelled() || monotonicNanos() > deadline || work - work0 > workCap }
        val candidates = only?.map { Plan(it) } ?: run.first(mustInclude, overBudget) ?: return run.result()
        run.batches(candidates, overBudget)
        return run.result()
    }

    /**
     * The plans of a joint card play: every own candidate with every combination of the computer
     * teammates' best [LevelProfile.joint] cards, for the teammates who still play this turn (seat order,
     * p.7). Their hands are open to the team (AI teammates count as having announced them, AI.md "The
     * determinizer"); a human teammate's card stays the playout's guess.
     */
    private fun plans(det: EngineSession, d: Decision, own: List<Choice>): List<Plan> {
        val plain = own.map { Plan(it) }
        if (profile.joint <= 0 || d.kind != DecisionKind.PLAY_CARD || profile.hideTeammates) return plain
        val s = det.state
        val me = s.heroes.indexOfFirst { it.id == d.seat }
        if (me < 0 || s.step != dev.quinton.goa.core.game.engine.Step.PLAY) return plain
        val ai = det.setup.seats.filter { it.controller is Controller.Ai }.map { it.heroId }.toSet()
        val ctx = ev.context(s)
        val mates = s.heroes.filter { it.seat > me && it.team == d.team && it.id in ai && it.hand.isNotEmpty() }
            .map { h ->
                h.id to h.hand.map { it.id }.sorted()
                    .sortedByDescending { static.playScore(ctx, h.id, Choice.Card(it)) }
                    .take(profile.joint)
            }
        if (mates.isEmpty()) return plain
        var combos = listOf(emptyMap<String, String>())
        for ((id, cards) in mates) combos = combos.flatMap { m -> cards.map { c -> m + (id to c) } }
        return own.flatMap { c -> combos.map { Plan(c, it) } }
    }

    /** One [analyze]: the samples' values per option, filled column by column (one column per sample). */
    private inner class Run(
        val es: EngineSession,
        val d: Decision,
        val seed: Long,
        val cancelled: () -> Boolean,
        val samples: Int,
    ) {
        val horizon = horizonOf(d.kind)
        val table = LinkedHashMap<Plan, DoubleArray>()
        val leaves = ArrayList<Leaf>()
        var done = 0
        var k = 0
        var stopped = false

        /** Per own answer, its best plan's values (a joint card play: the teammates' best reply to it). */
        fun result(): Analysis? {
            if (done == 0) return null
            val best = LinkedHashMap<Choice, OptionValue>()
            for ((p, v) in table) {
                val o = OptionValue(p.choice, v)
                val old = best[p.choice]
                if (old == null || o.mean > old.mean) best[p.choice] = o
            }
            return Analysis(d, best.values.toList(), leaves, done, k)
        }

        fun columns(cs: List<Plan>) {
            for (o in cs) table.getOrPut(o) { DoubleArray(samples) { Double.NaN } }
        }

        fun decision(det: EngineSession): Decision? = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind }

        /** The first sample that has the decision fixes the candidates; null if none did (or cancelled). */
        fun first(mustInclude: Choice?, overBudget: () -> Boolean): List<Plan>? {
            while (k < samples && (k == 0 || !overBudget())) {
                val det = determinize(es, d, mix(seed, k.toLong()))
                val dp = decision(det)
                if (dp == null) {
                    k++
                    continue
                }
                val own = prune(det, d, dp).let { c ->
                    if (mustInclude != null && mustInclude !in c && mustInclude in dp.options) c + mustInclude else c
                }
                val cs = plans(det, d, own)
                columns(cs)
                if (!sample(det, dp, d, cs, k, horizon, table, leaves, cancelled)) {
                    for (arr in table.values) arr[k] = Double.NaN
                    stopped = true
                    return null
                }
                done++
                k++
                return cs
            }
            return null
        }

        /** The other samples in batches of the runner's width, each on its own column; racing between batches. */
        fun batches(candidates: List<Plan>, overBudget: () -> Boolean) {
            columns(candidates)
            var live = candidates
            while (!stopped && k < samples && (k == 0 || !overBudget())) {
                val batch = k until minOf(samples, k + runner.width)
                val status = IntArray(batch.count())
                val batchLeaves = Array(status.size) { ArrayList<Leaf>() }
                val cs = live
                runner.runAll(
                    batch.mapIndexed { i, kk ->
                        {
                            val det = determinize(es, d, mix(seed, kk.toLong()))
                            val dp = decision(det)
                            status[i] = when {
                                dp == null -> 0
                                sample(det, dp, d, cs, kk, horizon, table, batchLeaves[i], cancelled) -> 1
                                else -> -1
                            }
                        }
                    },
                )
                for ((i, kk) in batch.withIndex()) {
                    if (stopped || status[i] < 0) {
                        for (arr in table.values) arr[kk] = Double.NaN
                        stopped = true
                        continue
                    }
                    if (status[i] == 1) done++
                    leaves += batchLeaves[i]
                    k = kk + 1
                }
                if (profile.race > 0 && done >= RACE_MIN && live.size > 2) live = race(live, table, k)
            }
        }
    }

    /**
     * [a] (an [analyze] of [d] with no `mustInclude`) as `analyze(…, mustInclude = choice)` would have
     * made it: when the pruning left [choice] out, it is played out on the same determinizations (same
     * seeds, same samples) and appended last, as [analyze] appends it. Every candidate is played on its
     * own fork, so the other options' values don't change. Null if cancelled.
     */
    @Suppress("ReturnCount", "LongParameterList")
    fun extend(
        es: EngineSession,
        d: Decision,
        seed: Long,
        a: Analysis,
        choice: Choice,
        cancelled: () -> Boolean,
    ): Analysis? {
        if (a.options.any { it.choice == choice }) return a
        val size = a.options.firstOrNull()?.values?.size ?: return a
        val horizon = horizonOf(d.kind)
        val values = DoubleArray(size) { Double.NaN }
        val table = mapOf(Plan(choice) to values)
        val leaves = ArrayList(a.leaves)
        var first = true
        for (k in 0 until a.tried) {
            if (cancelled()) return null
            val det = determinize(es, d, mix(seed, k.toLong()))
            val dp = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind } ?: continue
            // [analyze] fixes the candidates at the first sample: the choice joins only if offered there.
            if (first && choice !in dp.options) return a
            first = false
            if (!sample(det, dp, d, listOf(Plan(choice)), k, horizon, table, leaves, cancelled)) return null
        }
        return Analysis(d, a.options + OptionValue(choice, values), leaves, a.samples, a.tried)
    }

    /**
     * [a] with up to [samples] samples: the same candidates and the same seeds, so its first samples are
     * those of [a] and only the new ones are played out (the coach, when the person asks for suggestions).
     * Stops at [deadline] with what it has; on a cancel, null.
     */
    @Suppress("LongParameterList")
    fun deepen(
        es: EngineSession,
        d: Decision,
        seed: Long,
        a: Analysis,
        samples: Int,
        cancelled: () -> Boolean,
        deadline: Long,
    ): Analysis? {
        if (samples <= a.tried) return a
        val horizon = horizonOf(d.kind)
        val options = a.options.map { o ->
            OptionValue(o.choice, DoubleArray(samples) { o.values.getOrElse(it) { Double.NaN } })
        }
        val table = options.associate { Plan(it.choice) to it.values }
        val candidates = options.map { Plan(it.choice) }
        val leaves = ArrayList(a.leaves)
        var done = a.samples
        var k = a.tried
        while (k < samples && monotonicNanos() <= deadline) {
            if (cancelled()) return null
            val batch = k until minOf(samples, k + runner.width)
            val status = IntArray(batch.count())
            val batchLeaves = Array(status.size) { ArrayList<Leaf>() }
            runner.runAll(
                batch.mapIndexed { i, kk ->
                    {
                        val det = determinize(es, d, mix(seed, kk.toLong()))
                        val dp = det.pending?.takeIf { it.seat == d.seat && it.kind == d.kind }
                        status[i] = when {
                            dp == null -> 0
                            sample(det, dp, d, candidates, kk, horizon, table, batchLeaves[i], cancelled) -> 1
                            else -> -1
                        }
                    }
                },
            )
            if (status.any { it < 0 }) return null
            for ((i, kk) in batch.withIndex()) {
                if (status[i] == 1) done++
                leaves += batchLeaves[i]
                k = kk + 1
            }
        }
        return Analysis(d, options, leaves, done, k)
    }

    private fun horizonOf(kind: DecisionKind): Horizon = when (kind) {
        DecisionKind.PLAY_CARD -> profile.playHorizon
        DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> profile.upgradeHorizon
        else -> profile.otherHorizon
    }

    /** One determinization: every candidate answered on a fork and played out. False if cancelled. */
    @Suppress("LongParameterList")
    private fun sample(
        det: EngineSession,
        dp: Decision,
        d: Decision,
        candidates: List<Plan>,
        k: Int,
        horizon: Horizon,
        table: Map<Plan, DoubleArray>,
        leaves: MutableList<Leaf>,
        cancelled: () -> Boolean,
    ): Boolean {
        for (p in candidates) {
            val o = p.choice
            if (cancelled()) return false
            if (o !in dp.options) continue
            val f = det.forkForAnswer()
            val before = f.events.size
            f.answer(dp.id, o)
            answered.incrementAndFetch()
            playout(f, d.seat, d.team, horizon, mates = p.mates)
            val v = leaf.evaluate(f.state, d.team)
            table.getValue(p)[k] = v
            if (keepLeaves) leaves += Leaf(o, k, f, before, v)
        }
        return true
    }

    /**
     * Racing ([LevelProfile.race]): options clearly behind the best after the first samples are not
     * played out any more, so the samples left go to the contenders. With common random numbers the
     * paired differences are tight: an option drops when its mean gap to the best, less two standard
     * errors, exceeds the margin. At least two stay.
     */
    private fun race(live: List<Plan>, table: Map<Plan, DoubleArray>, k: Int): List<Plan> {
        fun mean(c: Plan): Double {
            val v = table.getValue(c)
            var sum = 0.0
            var n = 0
            for (i in 0 until k) {
                if (!v[i].isNaN()) {
                    sum += v[i]
                    n++
                }
            }
            return if (n == 0) Double.NEGATIVE_INFINITY else sum / n
        }
        val best = live.maxBy { mean(it) }
        val b = table.getValue(best)
        val keep = live.filter { c ->
            if (c == best) return@filter true
            val v = table.getValue(c)
            val diffs = (0 until k).filter { !v[it].isNaN() && !b[it].isNaN() }.map { b[it] - v[it] }
            if (diffs.size < RACE_MIN) return@filter true
            val m = diffs.average()
            val sd = sqrt(diffs.sumOf { (it - m) * (it - m) } / (diffs.size - 1))
            m - 2 * sd / sqrt(diffs.size.toDouble()) <= profile.race
        }
        return if (keep.size >= 2) keep else live.sortedByDescending { mean(it) }.take(2)
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
    fun playout(
        f: EngineSession,
        rootSeat: String,
        rootTeam: Team,
        horizon: Horizon,
        maxSteps: Int = 400,
        mates: Map<String, String> = emptyMap(),
    ) {
        val start = f.state
        val startTurn = turnIndex(start)
        val startStep = start.step
        val startSeat = start.stepSeat
        val startActor = start.acting
        val startRound = start.round
        var n = 0
        var turn = startTurn
        var turnsEnded = 0
        while (n < maxSteps) {
            val d = f.pending ?: return
            val s = f.state
            if (turnIndex(s) != turn) {
                turn = turnIndex(s)
                turnsEnded++
            }
            val stop = when (horizon) {
                Horizon.ACTION ->
                    s.round != startRound || s.step != startStep || s.stepSeat != startSeat ||
                        s.acting != startActor || turnIndex(s) != startTurn

                Horizon.TURN -> turnsEnded >= 1

                Horizon.NEXT_TURN -> turnsEnded >= 2
            }
            if (stop) return
            val mine = d.team == rootTeam
            val searchIt = (profile.greedySelf && d.seat == rootSeat) || (profile.greedyTeam && mine) ||
                (profile.greedyEnemies && !mine)
            val c = when {
                d.options.size == 1 -> d.options[0]

                // A facedown play has no immediate effect to look at: the plan's card for a teammate
                // (this turn only), else the static round plan picks it.
                d.kind == DecisionKind.PLAY_CARD ->
                    mates[d.seat]?.takeIf { turnIndex(s) == startTurn }?.let { Choice.Card(it) }
                        ?.takeIf { it in d.options } ?: static.best(s, d)

                searchIt -> greedy(f, d)

                profile.shallow && opaque(s, d) -> greedy(f, d)

                else -> static.best(s, d)
            }
            f.answer(d.id, c)
            answered.incrementAndFetch()
            n++
        }
    }

    /**
     * Choices whose worth only the card text knows (which bullet of "Choose one", where a token goes,
     * which of a hero's bodies acts, whose card to name): the static rules of thumb would answer them
     * blindly, so even the cheap playout looks one step ahead through the engine.
     */
    fun opaque(s: GameState, d: Decision): Boolean = when (d.kind) {
        DecisionKind.CHOOSE_OPTION, DecisionKind.CHOOSE_TOKEN, DecisionKind.CONFIRM, DecisionKind.CHOOSE_NUMBER -> true

        DecisionKind.CHOOSE_SPACE -> {
            val subject = d.context.subjectId
            subject == null ||
                (s.hero(subject) == null && s.minion(subject) == null && s.piece(subject)?.heroBody == null)
        }

        // Attack targets ("Target a unit in range") have good static scores; pushes, swaps, pulls,
        // "which one of you acts", Mind Grip's hero… do not.
        DecisionKind.CHOOSE_UNIT -> {
            val team = s.hero(d.seat)?.team ?: d.team
            fun ownBody(id: String) = s.piece(id)?.let { it.heroBody != null && it.team == team } == true
            !d.prompt.startsWith("Target") ||
                d.options.any { o -> o is Choice.Unit && (s.hero(o.unitId)?.team == team || ownBody(o.unitId)) }
        }

        DecisionKind.CHOOSE_CARD -> {
            val own = s.hero(d.seat)
            d.options.any { it is Choice.Card && own?.card(it.cardId) == null }
        }

        else -> false
    }

    /** One-ply: each option answered on a fork, the action finished statically, evaluated. */
    fun greedy(f: EngineSession, d: Decision): Choice {
        val opts = if (d.options.size > profile.greedyMax) {
            val sc = static.scores(f.state, d)
            d.options.indices.sortedByDescending { sc[it] }.take(profile.greedyMax).map { d.options[it] }
        } else {
            d.options
        }
        var best = opts[0]
        var bestV = Double.NEGATIVE_INFINITY
        for (o in opts) {
            val g = f.forkForAnswer()
            g.answer(d.id, o)
            answered.incrementAndFetch()
            finishAction(g, d.seat)
            val v = inner.evaluate(g.state, d.team)
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
            answered.incrementAndFetch()
            n++
        }
    }

    companion object {
        const val OPPONENT_TAU: Double = 1.5
        const val GREEDY_MAX: Int = 8

        /** Samples before racing may drop an option. */
        const val RACE_MIN: Int = 4

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
                    is GameEvent.CardsRevealed -> e.played.forEach { (h, c) ->
                        if (h in enemies &&
                            c != null
                        ) {
                            seen.getOrPut(h) { LinkedHashSet() } += c
                        }
                    }

                    is GameEvent.Defended -> if (e.heroId in enemies &&
                        e.cardId != null
                    ) {
                        seen.getOrPut(e.heroId) { LinkedHashSet() } += e.cardId!!
                    }

                    is GameEvent.ActionChosen -> if (e.heroId in
                        enemies
                    ) {
                        seen.getOrPut(e.heroId) { LinkedHashSet() } += e.cardId
                    }

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
    fun pick(
        choices: List<Choice>,
        values: DoubleArray,
        temperature: Double,
        maxGap: Double,
        slipRate: Double,
        rng: Random,
    ): Choice {
        val order = choices.indices.sortedByDescending { values[it] }
        if (slipRate > 0 && rng.nextDouble() < slipRate) {
            val top = order.take(3).filter { values[it] > values[order[0]] - SLIP_GAP }
            return choices[top[rng.nextInt(top.size)]]
        }
        if (temperature <= 0) return choices[order[0]]
        val best = values[order[0]]
        val pool = order.filter { values[it] >= best - maxGap }
        // PortableMath: the same weights on every platform, so a seeded pick is too (ADR-043).
        val w = pool.map { PortableMath.exp((values[it] - best) / temperature) }
        var r = rng.nextDouble() * w.sum()
        for ((i, idx) in pool.withIndex()) {
            r -= w[i]
            if (r < 0) return choices[idx]
        }
        return choices[pool.last()]
    }

    private const val SLIP_GAP = 12.0
}
