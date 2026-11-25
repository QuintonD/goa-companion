package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team

/**
 * A search leaf as a win probability (Wave 4 Track F, review-3/V2-code.md §2.4): the one scale on which
 * values from different positions may be averaged. A net's logit and a coin score are log-odds-like, and a
 * finished game scores ±(1000 − round) on the coin scale, so a plain mean of coin values lets one finished
 * column swamp the others (a 1-in-4 loss at −1000 outweighs three columns the net gives 88%). On this scale a
 * finished game is exactly 1 or 0, a net's position is σ(logit), and a mean is an expected chance to win.
 *
 * Callers: the hybrid search's leaf ([dev.quinton.goa.core.game.ai.hybrid.HybridAi]), and anything that
 * builds expectations or payoff matrices over leaves (Track G's card equilibrium).
 */
internal object LeafChance {
    /**
     * Separates finished games by how soon they end: a win now is worth a hair more than a win later, a loss
     * later a hair more than a loss now. Small enough that no non-finished position is ever reordered
     * against a finished one in a mean that matters (rounds are under 20; 20 × 1e-6 ≪ any real difference).
     */
    private const val SOONER = 1.0e-6

    /** [team]'s value of a finished game: 1 for a win (less a hair per round), 0 for a loss; null if not over. */
    fun terminal(state: GameState, team: Team): Double? {
        val w = state.winner ?: return null
        return if (w == team) 1.0 - SOONER * state.round else SOONER * state.round
    }

    /** P(win) of a logit (a net's log-odds). */
    fun ofLogit(logit: Double): Double = Evaluator.sigmoid(logit)

    /**
     * [ofLogit]'s inverse, for recording a chance on a log-odds scale (the search-value targets, Wave 4 Track H):
     * the chance is held within [LOGIT_FLOOR] of 0 and 1 first, so a finished game is a large finite logit (±13.8).
     */
    fun toLogit(p: Double): Double {
        val q = p.coerceIn(LOGIT_FLOOR, 1.0 - LOGIT_FLOOR)
        return kotlin.math.ln(q / (1.0 - q))
    }

    /**
     * P(win) of a coin-scale score from [team]'s side ([Evaluator]'s, or the book's
     * [dev.quinton.goa.core.game.ai.heuristic.HEval]), through the Platt fit of [WinChance] for the game's
     * size. A finished [state] scores by [terminal]. The fit is for the hand evaluation with the coach's knobs;
     * for HEval it is the closest calibrated mapping there is (same coin unit), not a fit of its own.
     */
    fun ofScore(score: Double, state: GameState, team: Team): Double {
        terminal(state, team)?.let { return it }
        return unfinished(WinChance.of(score, heroesPerSide(state)))
    }

    /** An unfinished position's chance, kept strictly between every finished loss and win. */
    fun unfinished(p: Double): Double = p.coerceIn(SOONER * MAX_ROUNDS, 1.0 - SOONER * MAX_ROUNDS)

    /** [eval]'s P(win) for [team] at [state] ([PositionEvaluator.chance]). */
    fun of(eval: PositionEvaluator, state: GameState, team: Team): Double = eval.chance(state, team)

    fun heroesPerSide(state: GameState): Int = (state.heroes.size / 2).coerceAtLeast(1)

    /** Unfinished positions stay strictly between every loss and every win ([terminal]'s hair). */
    private const val MAX_ROUNDS = 100

    /** [toLogit]'s bound on a chance's distance from 0 and 1. */
    private const val LOGIT_FLOOR = 1.0e-6
}
