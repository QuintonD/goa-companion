package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import kotlin.random.Random

/**
 * The computer opponents (ADR-035, PRODUCT §9, core/game/ai/AI.md). One engine with switches per
 * level (STRATEGY.md §4): Easy plays from static heuristics with human-looking slips; Normal plays
 * each option out to the end of the action (cards: the turn) against the most likely enemy plays;
 * Hard samples the enemies' hidden cards from an opponent model and plays the whole turn out for each;
 * Expert samples more, looks to the end of the next turn, remembers every card shown since an enemy's
 * last level up, and mixes between near-equal plays so it is hard to read.
 *
 * Deterministic for a seed (answers depend on the seed, the decision id and the seat), except that a
 * wall-clock cap (the phone budget) may end a search early on a slow device; [timeCap] = false
 * removes it.
 */
object AiPlayers {
    fun forLevel(level: AiLevel, seed: Long): AiPlayer = SearchAi(level, profile(level), seed, timeCap = true)

    /** Same, without the wall-clock cap: fully reproducible (tests, tournaments). */
    fun deterministic(level: AiLevel, seed: Long): AiPlayer = SearchAi(level, profile(level), seed, timeCap = false)

    internal fun profile(level: AiLevel): LevelProfile = when (level) {
        AiLevel.EASY -> LevelProfile(
            staticOnly = true, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.ACTION, otherHorizon = Horizon.ACTION, candidates = 1,
            temperature = 1.2, maxGap = 4.0, slipRate = 0.08, risk = 0.0,
            knobs = EvalKnobs(nearestThreatOnly = true, nextTurnThreat = 0.12),
            modelPlays = false, memory = false, greedySelf = false, budgetMs = 50,
        )
        AiLevel.NORMAL -> LevelProfile(
            staticOnly = false, playSamples = 1, otherSamples = 1,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.ACTION, candidates = 6,
            temperature = 0.6, maxGap = 1.5, slipRate = 0.0, risk = 0.0,
            knobs = EvalKnobs(), modelPlays = true, memory = false, greedySelf = false, budgetMs = 300,
        )
        AiLevel.HARD -> LevelProfile(
            staticOnly = false, playSamples = 8, otherSamples = 3,
            playHorizon = Horizon.TURN, otherHorizon = Horizon.TURN, candidates = 8,
            temperature = 0.25, maxGap = 0.8, slipRate = 0.0, risk = 0.3,
            knobs = EvalKnobs(), modelPlays = true, memory = false, greedySelf = true, budgetMs = 1500,
        )
        AiLevel.EXPERT -> LevelProfile(
            staticOnly = false, playSamples = 16, otherSamples = 5,
            playHorizon = Horizon.NEXT_TURN, otherHorizon = Horizon.TURN, candidates = 10,
            temperature = 0.15, maxGap = 0.6, slipRate = 0.0, risk = 0.2,
            knobs = EvalKnobs(nextTurnThreat = 0.28), modelPlays = true, memory = true, greedySelf = true,
            budgetMs = 4000,
        )
    }
}

internal class SearchAi(
    val level: AiLevel,
    private val profile: LevelProfile,
    private val seed: Long,
    private val timeCap: Boolean,
) : AiPlayer {
    private var searchers: Pair<EngineData, Searcher>? = null
    private var blind: Pair<EngineData, Searcher>? = null

    private fun searcher(data: EngineData, modifierBlind: Boolean = false): Searcher {
        val cached = if (modifierBlind) blind else searchers
        if (cached != null && cached.first === data) return cached.second
        val knobs = profile.knobs.copy(modifierBlind = modifierBlind)
        val s = Searcher(Evaluator(data, knobs), profile)
        if (modifierBlind) blind = data to s else searchers = data to s
        return s
    }

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        if (decision.options.size == 1) return decision.options.single()
        val es = session as? EngineSession ?: return Fallback.choose(decision)
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
        // Easy forgets the minion defense modifiers 40% of the time when it moves (STRATEGY §4.2).
        val blindNow = profile.staticOnly && d.kind == DecisionKind.CHOOSE_SPACE && rng.nextDouble() < 0.4
        val s = searcher(es.engineData, blindNow)
        val det = s.determinize(es, d, rng.nextLong())
        val dp = det.pending
        if (dp == null || dp.seat != d.seat || dp.kind != d.kind) return Fallback.choose(d)
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
