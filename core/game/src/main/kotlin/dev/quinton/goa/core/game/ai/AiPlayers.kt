package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/**
 * The computer opponents (ADR-035, ADR-039, PRODUCT §9, core/game/ai/AI.md). One engine with
 * switches per strength, 1 to 10 (STRATEGY.md §4): 1–4 play from static heuristics with
 * human-looking slips (fewer as the strength grows); from 5 each option is played out against the
 * enemies' likely plays, with longer playouts, more samples, less noise and smarter playouts at
 * every step up; 9 and 10 remember every card shown since an enemy's last level up, and 10 plays
 * every hero's later choices in a playout by one-ply search. The four named levels are the bands of
 * [AiLevel]; a level alone (a save from before the slider) plays as its [AiLevel.strength].
 *
 * Deterministic for a seed (answers depend on the seed, the decision id and the seat), except that a
 * wall-clock cap (the phone budget) may end a search early on a slow device; [deterministic] removes it.
 */
object AiPlayers {
    /**
     * The production player: wall-clock capped at the strength's device budget. [runner] may run the
     * samples of one answer on several threads (the Android app passes one; the engine has no threads).
     */
    fun forStrength(strength: Int, seed: Long, runner: SampleRunner = SampleRunner.SEQUENTIAL): AiPlayer =
        player(strength, seed, timeCap = true, runner)

    /** Same, without the wall-clock cap: fully reproducible (tests, tournaments). */
    fun deterministic(strength: Int, seed: Long): AiPlayer = player(strength, seed, timeCap = false)

    fun forLevel(level: AiLevel, seed: Long): AiPlayer = forStrength(level.strength, seed)

    fun deterministic(level: AiLevel, seed: Long): AiPlayer = deterministic(level.strength, seed)

    private fun player(
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
     * 51.6%); the static strengths keep the hand-made weights their designed mistakes rest on.
     */
    internal const val LEARNED_FROM: Int = 10

    /**
     * The hand evaluation beside the net (its one-ply looks inside playouts, the static policy, the
     * opponent model): the knobs the net was trained with, v0.8's `TUNED` without the 3v3 knobs. With
     * them the merged strength 10 beat the one carrying the 3v3 knobs 54.2% (47.1–61.1%, 192 3v3
     * games; AI.md "The two parts together"); the net's own inputs never see a profile's knobs.
     */
    internal val TUNED_WITH_NET: EvalKnobs = Features.KNOBS.copy(scales = Features.SCALES)

    /** What each strength does (AI.md "Strengths"); the measured ladder is in AI.md. */
    internal fun profile(strength: Int): LevelProfile = handProfile(strength).let {
        if (AiStrength.coerce(strength) >= LEARNED_FROM) {
            it.copy(learned = EvalSource.LEAVES, knobs = TUNED_WITH_NET)
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
        // on three threads.
        else -> LevelProfile(
            staticOnly = false, playSamples = 16, otherSamples = 6,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 10,
            temperature = 0.15, maxGap = 0.6, slipRate = 0.0, risk = 0.0,
            knobs = TUNED, modelPlays = true, memory = true, greedySelf = true,
            budgetMs = 2000, greedyTeam = true, greedyEnemies = true,
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
        if (profile.hunt != null && team != decision.team) {
            team = decision.team
            searchers = null
            blind = null
        }
        val rng = Random(Searcher.mix(seed, decision.id * 1_000_003L + decision.seat.hashCode()))
        val budget = if (decision.kind == DecisionKind.PLAY_CARD) profile.budgetMs else profile.budgetMs / 3
        val deadline = if (timeCap) System.nanoTime() + budget * 1_000_000L else Long.MAX_VALUE
        if (profile.staticOnly) return staticChoice(es, decision, rng)
        val s = searcher(es.engineData)
        val a = s.analyze(es, decision, rng.nextLong(), cancelled, deadline) ?: return staticChoice(es, decision, rng)
        val byChoice = a.options.associateBy { it.choice }
        val risk = if (decision.kind == DecisionKind.PLAY_CARD) profile.risk else 0.0
        val choices = decision.options.filter { it in byChoice }
        if (choices.isEmpty()) return staticChoice(es, decision, rng)
        val values = choices.map { c -> byChoice.getValue(c).let { it.mean - risk * it.sd } }.toDoubleArray()
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
