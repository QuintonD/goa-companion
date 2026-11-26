package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.monotonicNanos
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.hybrid.HybridAi
import dev.quinton.goa.core.game.ai.hybrid.HybridLeaf
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * The computer opponents (ADR-035, ADR-039, ADR-085, PRODUCT §9). The player the apps, the web
 * worker and the online server's takeover seat is [dev.quinton.goa.core.game.ai.heuristic.HeuristicAi]:
 * a general playbook plus one per hero, strength 1 to 9; strength 10 is a search over that book
 * ([HybridAi], ADR-085 addendum): every question with a choice played out by the book to the end of the
 * turn, scored by [SEARCH_10_LEAF]. The four named levels are the bands of
 * [AiLevel]; a level alone (a save from before the slider) plays as its [AiLevel.strength].
 *
 * The search player (playouts, the learned net) stays for the coach, the puzzles and for measuring
 * the new player against the old one: [searchDeterministic] and [searchForStrength]. Do not seat
 * those in a game the person plays.
 *
 * Deterministic for a seed (answers depend on the seed, the decision id and the seat). Neither the book
 * nor [forStrength]'s strength-10 search has a wall-clock cap; the search stops when its answer is cancelled.
 * The apps' [forApp] gives strength 10 the think time [APP_BUDGET_MS]. The old search player's
 * [searchForStrength] has its own cap.
 */
object AiPlayers {
    /** The seed the apps give the computer players: the phone's (core/data) and the web worker's (ADR-043). */
    const val APP_SEED: Long = 0x60A_2026L

    /**
     * The computer player the apps seat at [strength] (ADR-039, ADR-085): [forStrength] seeded per
     * strength, so a strength is the same player on the phone and in the browser. [runner] runs the
     * strength-10 search's playouts (the same answers on any runner); the book below it does not sample.
     *
     * Strength 10 thinks at most [budgetMs] a searched answer (card play or any other; the search's own
     * deadline, [HybridConfig][dev.quinton.goa.core.game.ai.hybrid.HybridConfig.budgetMs], Wave 4 Track F:
     * ADR-047's 2 s by default). Answers that finish within it are the same on every device and runner; one
     * cut short depends on the device's speed. The player keeps a memo for the turn: the apps keep one player
     * per team and strength for the whole game (as the tournaments measured it), never one per answer, and ask
     * it one question at a time.
     */
    fun forApp(
        strength: Int,
        runner: SampleRunner = SampleRunner.SEQUENTIAL,
        budgetMs: Long = APP_BUDGET_MS,
    ): AiPlayer {
        val s = AiStrength.coerce(strength)
        if (s < SEARCH_FROM || budgetMs <= 0) return forStrength(s, APP_SEED + s, runner)
        return HybridAi(APP_SEED + s, searchKnobs(s), runner, budgetMs)
    }

    /**
     * The app's think time for strength 10's searched answers, milliseconds (the owner's ceiling, ADR-047:
     * about 2 s a card play on the phone; the other searched questions get the same, Track A's measurement
     * shows their 3v3 p90 at 2.1 s). Wave 4 Track A measured the search's single-thread times against it
     * (RESULTS.md "Wave 4 — Track A").
     */
    const val APP_BUDGET_MS: Long = 2_000L

    /**
     * The production player with no wall clock (the same answers on every device; tournaments and tests);
     * [runner] runs strength 10's playouts. The apps' think time is [forApp]'s alone.
     */
    fun forStrength(strength: Int, seed: Long, runner: SampleRunner = SampleRunner.SEQUENTIAL): AiPlayer =
        heuristic(strength, seed, runner)

    /** Fully reproducible (tests, tournaments): the same player as [forStrength], with no clock to cut. */
    fun deterministic(strength: Int, seed: Long): AiPlayer = heuristic(strength, seed, SampleRunner.SEQUENTIAL)

    fun forLevel(level: AiLevel, seed: Long): AiPlayer = forStrength(level.strength, seed)

    fun deterministic(level: AiLevel, seed: Long): AiPlayer = deterministic(level.strength, seed)

    /**
     * The old search player, uncapped (coach comparisons, the ladder, fingerprints). Not the player
     * a game seats.
     */
    fun searchDeterministic(strength: Int, seed: Long): AiPlayer = searchPlayer(strength, seed, timeCap = false)

    /** The old search player with its wall-clock cap. [runner] runs one answer's samples. */
    fun searchForStrength(strength: Int, seed: Long, runner: SampleRunner = SampleRunner.SEQUENTIAL): AiPlayer =
        searchPlayer(strength, seed, timeCap = true, runner)

    /**
     * The leaf of the strength-10 search's playouts: the one switch between the learned nets ([HybridLeaf.NET6],
     * the old ensemble averaged with three nets fine-tuned on the book's and the search's games, the strongest
     * measured so far, ADR-085 addendum "Wave 3"; [HybridLeaf.NET] the old ensemble alone) and the book's own
     * position value ([HybridLeaf.HEVAL]); the owner ships whichever measures strongest. Every other knob is
     * [SEARCH_10].
     */
    val SEARCH_10_LEAF: HybridLeaf = HybridLeaf.NET6

    /**
     * Strength 10's search over the heuristic book ([HybridAi], ADR-085 addendum "Wave 3"): the knobs of
     * [dev.quinton.goa.core.game.ai.hybrid.HybridConfig.fromKnobs] (the leaf is [SEARCH_10_LEAF]). The book
     * exactly as h10 plays it answers in the playouts (`pol` 1); every question with a choice is searched
     * (`scope` 3) to the end of the turn; 24 determinizations a card play, merged where they deal the same
     * position (`K`); the book's own answer plus its best 8 spaces / 6 units / 4 of anything else (`topS`,
     * `topU`, `top`); the enemies' hidden cards (played facedown or still to choose) dealt from the opponent model
     * by enumeration, the 8 most likely joint deals up to 95% of it, the same for either seat colour (`deal`,
     * Wave 4 Track F, RESULTS.md "Wave 4 — Track F"; it replaced 4 enemy replies at Red seats, `rep`); the memo
     * (`tt`);
     * after the reveal a minimax over the rest of the turn, two choices deep over the book's best three
     * (`td`, `tw`; the tree, RESULTS.md "Wave 3 — Track C"). The book (at the root and in the playouts) branches
     * one own choice in its card looks and looks six spaces ahead at a rest (`branch`, `restSpaces`: Track A's
     * F1 book in its cheap form, RESULTS.md "Wave 3 — Track C2"). With six heroes a card play plays at most
     * `6.cap` columns (samples × enemy deals), which bounds the slowest 3v3 card plays. On the last wave a turn-3
     * playout plays on through turn 4 and the round end, where the Minion Battle decides the game (`lpx`, RESULTS.md
     * "Wave 4 — Track P").
     * Any other key goes to the book as a [dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile.tune] knob.
     */
    val SEARCH_10: Map<String, Double> = mapOf(
        "pol" to 1.0,
        "scope" to 3.0,
        "tt" to 1.0,
        "K" to 24.0,
        "topS" to 8.0,
        "topU" to 6.0,
        "deal" to 8.0,
        "td" to 2.0,
        "tw" to 3.0,
        "branch" to 1.0,
        "restSpaces" to 6.0,
        "6.cap" to 8.0,
        "lpx" to 1.0,
    )

    /** The strengths the search plays ([HybridAi]); below them the heuristic book alone ([HeuristicAi]). */
    const val SEARCH_FROM: Int = 10

    /** Strength [SEARCH_FROM] and up: the search over the book, its samples on [runner]; below, the book. */
    private fun heuristic(strength: Int, seed: Long, runner: SampleRunner): AiPlayer {
        val s = AiStrength.coerce(strength)
        if (s < SEARCH_FROM) return HeuristicAi(s, seed)
        return HybridAi(seed, searchKnobs(s), runner)
    }

    /** Strength 10's knobs: [SEARCH_10] with its leaf ([SEARCH_10_LEAF]). */
    /** The search's knobs at [strength] (10 or 11), as the apps seat it: measurements build on them. */
    fun knobsFor(strength: Int): Map<String, Double> = searchKnobs(strength)

    private fun searchKnobs(strength: Int = SEARCH_FROM): Map<String, Double> =
        SEARCH_10 + ("leaf" to SEARCH_10_LEAF.ordinal.toDouble()) +
            if (strength >= AiStrength.ALL_SEEING) mapOf("omni" to 1.0) else emptyMap()

    private fun searchPlayer(
        strength: Int,
        seed: Long,
        timeCap: Boolean,
        runner: SampleRunner = SampleRunner.SEQUENTIAL,
    ): AiPlayer {
        val s = AiStrength.coerce(strength)
        return SearchAi(AiLevel.of(s), profile(s), seed, timeCap, runner)
    }

    internal fun profile(level: AiLevel): LevelProfile = profile(level.strength)

    /**
     * The evaluation the searching strengths 5–9 use: a hero's exposure counts 0.3 of its hand-made
     * weight and its minion opportunities twice (fitted to self-play outcomes and measured, AI.md
     * "Evaluation" and "What changed in v0.7"). In 3v3 (v0.9, AI.md "Team sizes") each attack card
     * left counts 0.35 of a minion of Push Potential instead of 0.5, and a hero with no card left to
     * defend with counts its full exposure: together 57.8% and 53.1% against the v0.8 strength 10 over
     * two sets of 192 held-out games, with fewer heroes lost to a kill-hunting opponent.
     */
    internal val TUNED: EvalKnobs = EvalKnobs(
        scales = mapOf(Term.EXPOSURE to 0.3, Term.OPPORTUNITY to 2.0),
        pushPerAttackBySize = mapOf(3 to 0.35),
        defenselessBySize = mapOf(3 to 1.0),
    )

    /**
     * The strengths from this one up score playout ends with the learned evaluation (ADR-047 part B,
     * AI.md "Learned evaluation"): Expert (10) only. It strengthens every searching strength, but
     * given to 5–10 it compressed the steps between them (6 over 5 55.7%, 8 over 7 56.8%, 10 over 9
     * 51.6%); the static strengths keep the hand-made weights their designed mistakes rest on. Since
     * v0.14 (ADR-060) it is the mean of three seed nets, and it scores the one-ply looks inside playouts
     * too: at the phone's wall clock (half the work, as each answer costs ×1.97) 57.5% (52.0–62.8%,
     * 320 3v3 games) over the same nets at the playout ends alone.
     */
    internal const val LEARNED_FROM: Int = 10

    /**
     * The hand evaluation beside the net (the static policy, the opponent model): the knobs the net was
     * trained with, v0.8's `TUNED` without the 3v3 knobs. With them the merged strength 10 beat the one
     * carrying the 3v3 knobs 54.2% (47.1–61.1%, 192 3v3 games; AI.md "The two parts together"); the net's
     * own inputs never see a profile's knobs.
     */
    internal val TUNED_WITH_NET: EvalKnobs = Features.KNOBS.copy(scales = Features.SCALES)

    /** What each strength does (AI.md "Strengths"); the measured ladder is in AI.md. */
    internal fun profile(strength: Int): LevelProfile = handProfile(strength).let {
        if (AiStrength.coerce(strength) >= LEARNED_FROM) {
            it.copy(learned = EvalSource.EVERYWHERE, knobs = TUNED_WITH_NET)
        } else {
            it
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")
    private fun handProfile(strength: Int): LevelProfile = when (AiStrength.coerce(strength)) {
        // A true first game: looks at the nearest enemy only, forgets minion modifiers most of the
        // time it moves, and often picks the second or third best option.
        1 -> LevelProfile(
            staticOnly = true, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.ACTION, otherHorizon = Horizon.ACTION, candidates = 1,
            temperature = 2.0, maxGap = 6.0, slipRate = 0.16, risk = 0.0,
            knobs = EvalKnobs(nearestThreatOnly = true, nextTurnThreat = 0.08),
            modelPlays = false, memory = false, greedySelf = false, budgetMs = 50, blindRate = 0.7,
        )

        // Easy, as before the slider.
        2 -> LevelProfile(
            staticOnly = true, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.ACTION, otherHorizon = Horizon.ACTION, candidates = 1,
            temperature = 1.2, maxGap = 4.0, slipRate = 0.08, risk = 0.0,
            knobs = EvalKnobs(nearestThreatOnly = true, nextTurnThreat = 0.12),
            modelPlays = false, memory = false, greedySelf = false, budgetMs = 50, blindRate = 0.4,
        )

        // Between Easy and the careful player: still the nearest enemy only, fewer slips.
        3 -> LevelProfile(
            staticOnly = true, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.ACTION, otherHorizon = Horizon.ACTION, candidates = 1,
            temperature = 0.9, maxGap = 3.0, slipRate = 0.05, risk = 0.0,
            knobs = EvalKnobs(nearestThreatOnly = true, nextTurnThreat = 0.15),
            modelPlays = false, memory = false, greedySelf = false, budgetMs = 50, blindRate = 0.25,
        )

        // Static rules of thumb like Easy, but it sees every enemy and rarely slips.
        4 -> LevelProfile(
            staticOnly = true, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.ACTION, otherHorizon = Horizon.ACTION, candidates = 1,
            temperature = 0.6, maxGap = 2.0, slipRate = 0.03, risk = 0.0,
            knobs = EvalKnobs(nextTurnThreat = 0.18),
            modelPlays = true, memory = false, greedySelf = false, budgetMs = 50, blindRate = 0.1,
        )

        // The first that looks ahead: its three likeliest options played out once, against uniform
        // guesses of the enemies' cards, chosen loosely.
        5 -> LevelProfile(
            staticOnly = false, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.ACTION, candidates = 3,
            temperature = 1.0, maxGap = 3.0, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = false, memory = false, greedySelf = false, budgetMs = 200,
        )

        // Normal: each option played out once, a move to the end of its action.
        6 -> LevelProfile(
            staticOnly = false, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.ACTION, candidates = 6,
            temperature = 0.6, maxGap = 1.5, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = true, memory = false, greedySelf = false, budgetMs = 300,
        )

        // Every choice played out to the end of the turn (the biggest single step, AI.md).
        7 -> LevelProfile(
            staticOnly = false, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 6,
            temperature = 0.5, maxGap = 1.3, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = true, memory = false, greedySelf = false, budgetMs = 500,
        )

        // Hard: six guesses of the enemies' hidden cards, risk-averse card plays.
        8 -> LevelProfile(
            staticOnly = false, playSamples = 6, otherSamples = 2,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 8,
            temperature = 0.25, maxGap = 0.8, slipRate = 0.0, risk = 0.3,
            knobs = TUNED, modelPlays = true, memory = false, greedySelf = false, budgetMs = 1000,
        )

        // Expert's samples and memory; only its own later choices in a playout are searched.
        9 -> LevelProfile(
            staticOnly = false, playSamples = 16, otherSamples = 6,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 10,
            temperature = 0.15, maxGap = 0.6, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = true, memory = true, greedySelf = true, budgetMs = 1500,
        )

        // Expert. Playouts to the end of the next turn measured worse than to the end of this one
        // (AI.md): it spends its time on more samples and on smarter playouts for every hero instead.
        // At most 2 s a card play on the phone (the owner's ceiling, ADR-047); the app runs its samples
        // on three threads. At the owner's request it knows the cards in the enemies' hands, not which one
        // they played facedown (ADR-062).
        else -> LevelProfile(
            staticOnly = false, playSamples = 16, otherSamples = 6,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 10,
            temperature = 0.15, maxGap = 0.6, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = true, memory = true, greedySelf = true,
            budgetMs = 2000, greedyTeam = true, greedyEnemies = true, knowHands = true,
        )
    }
}

internal class SearchAi(
    val level: AiLevel,
    private val profile: LevelProfile,
    private val seed: Long,
    private val timeCap: Boolean,
    private val runner: SampleRunner = SampleRunner.SEQUENTIAL,
) : AiPlayer {
    private var searchers: Pair<EngineData, Searcher>? = null
    private var blind: Pair<EngineData, Searcher>? = null

    /** Engine answers spent in playouts so far ([LevelProfile.workBudget]; timing tests). */
    internal val work: Long get() = (searchers?.second?.work ?: 0) + (blind?.second?.work ?: 0)

    /** Card-play playouts that ended their turn, and those the kill extension played on (ADR-061; tests). */
    internal val kills: Pair<Long, Long>
        get() = searchers?.second?.kills ?: (0L to 0L)

    /** The team of the last decision: the exploiter's one-sided taste depends on it. */
    private var team: Team? = null

    private fun searcher(data: EngineData, modifierBlind: Boolean = false): Searcher {
        val cached = if (modifierBlind) blind else searchers
        if (cached != null && cached.first === data) return cached.second
        var knobs = profile.knobs.copy(modifierBlind = modifierBlind)
        val own = team
        if (profile.hunt != null && own != null) knobs = profile.hunt.knobs(knobs, own)
        val s = Searcher(Evaluator(data, knobs), profile, runner = runner)
        if (modifierBlind) blind = data to s else searchers = data to s
        return s
    }

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        if (decision.options.size == 1) return decision.options.single()
        val es = session as? EngineSession ?: return Fallback.choose(decision)
        @Suppress("TooGenericExceptionCaught") // whatever a sampled position that real play never reaches throws
        return try {
            search(es, decision, cancelled)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Fallback.choose(decision)
        }
    }

    /**
     * Tests only (the oracle exploiter, ADR-061): the answer this player is most likely to give, its best
     * option or the most likely play of its mix, without its noise.
     */
    internal fun mode(es: EngineSession, decision: Decision): Choice {
        if (decision.options.size == 1) return decision.options.single()
        return search(es, decision, { false }, mode = true)
    }

    private fun search(es: EngineSession, decision: Decision, cancelled: () -> Boolean, mode: Boolean = false): Choice {
        if (profile.hunt != null && team != decision.team) {
            team = decision.team
            searchers = null
            blind = null
        }
        val rng = Random(Searcher.mix(seed, decision.id * 1_000_003L + decision.seat.hashCode()))
        val budget = if (decision.kind == DecisionKind.PLAY_CARD) profile.budgetMs else profile.budgetMs / 3
        val deadline = if (timeCap) monotonicNanos() + budget * 1_000_000L else Long.MAX_VALUE
        if (profile.staticOnly) return staticChoice(es, decision, rng)
        val s = searcher(es.engineData)
        val a = s.analyze(es, decision, rng.nextLong(), cancelled, deadline) ?: return staticChoice(es, decision, rng)
        val byChoice = a.options.associateBy { it.choice }
        val risk = if (decision.kind == DecisionKind.PLAY_CARD) profile.risk else 0.0
        val choices = decision.options.filter { it in byChoice }
        if (choices.isEmpty()) return staticChoice(es, decision, rng)
        val values = choices.map { c -> byChoice.getValue(c).let { it.mean - risk * it.sd } }.toDoubleArray()
        if (decision.kind == DecisionKind.PLAY_CARD && profile.rm > 0 && a.enemyCards.isNotEmpty()) {
            // A mixed card play (ADR-061): drawn from the regret-matching solution, not the softmax.
            val x = RegretMatching.solve(choices.map { byChoice.getValue(it).values }, a.enemyCards, profile.rm)
            if (mode) return choices[x.indices.maxBy { x[it] }]
            var r = rng.nextDouble()
            for (i in x.indices) {
                r -= x[i]
                if (r < 0) return choices[i]
            }
            return choices[x.indices.maxBy { x[it] }]
        }
        if (mode) return choices[values.indices.maxBy { values[it] }]
        return Picker.pick(choices, values, profile.temperature, profile.maxGap, 0.0, rng)
    }

    /** Easy, and the fallback of every level: static scores on one determinized copy. */
    private fun staticChoice(es: EngineSession, d: Decision, rng: Random): Choice {
        // The static strengths forget the minion defense modifiers some of the time when they move (STRATEGY §4.2).
        val blindNow =
            profile.staticOnly && d.kind == DecisionKind.CHOOSE_SPACE && rng.nextDouble() < profile.blindRate
        val s = searcher(es.engineData, blindNow)
        val det = s.determinize(es, d, rng.nextLong())
        val dp = det.pending
        if (dp == null || dp.seat != d.seat || dp.kind != d.kind) return Fallback.choose(d)
        // What only the card text knows (a bullet, where a token goes) is looked at one step ahead.
        if (profile.shallow && s.opaque(det.state, dp)) s.greedy(det, dp).takeIf { it in d.options }?.let { return it }
        val scores = s.static.scores(det.state, dp)
        val byChoice = dp.options.withIndex().associate { (i, c) -> c to scores[i] }
        val choices = d.options.filter { it in byChoice }
        if (choices.isEmpty()) return Fallback.choose(d)
        val values = choices.map { byChoice.getValue(it) }.toDoubleArray()
        // Never slip on a defense it can make or on a push one minion away (those feel broken, not human).
        val critical = d.kind == DecisionKind.DEFEND || values.max() >= CRITICAL
        val slip = if (critical) 0.0 else profile.slipRate
        val temp = if (critical) 0.0 else profile.temperature
        return Picker.pick(choices, values, temp, profile.maxGap, slip, rng)
    }

    companion object {
        private const val CRITICAL = 12.0
    }
}

/** Answers from the decision alone (no engine session): sensible, never clever. */
internal object Fallback {
    fun choose(d: Decision): Choice = when (d.kind) {
        DecisionKind.DEFEND -> {
            val total = d.context.attackTotal ?: 0
            d.options.filterIsInstance<Choice.Card>()
                .filter { (d.context.defensePreview[it.cardId] ?: Int.MIN_VALUE) >= total }
                .minByOrNull { d.context.defensePreview[it.cardId] ?: 0 }
                ?: d.options.first { it is Choice.Decline }
        }

        DecisionKind.RESPAWN -> d.options.firstOrNull { it is Choice.Space } ?: d.options.first()

        DecisionKind.CONFIRM -> d.options.firstOrNull { it is Choice.Yes } ?: d.options.first()

        else -> d.options.firstOrNull { it !is Choice.Decline } ?: d.options.first()
    }
}
