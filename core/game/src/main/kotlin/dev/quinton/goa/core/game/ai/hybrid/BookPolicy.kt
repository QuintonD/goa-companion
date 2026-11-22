package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.ai.Fallback
import dev.quinton.goa.core.game.ai.heuristic.CardSim
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.model.match.Team
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * The heuristic book as a playout policy, candidate generator and opponent model (REVIEW-2-search.md).
 * Every answer is the book's best (no softmax, no G17 mixing), so two candidates played out on the same
 * determinization meet the same replies unless their own positions differ (common random numbers).
 *
 * It answers on a copy that is already determinized (a search sample or a fork of one): nothing here
 * reads the real session.
 */
internal class BookPolicy(seed: Long, tune: Map<String, Double>) {
    private val quiet = tune + mapOf("temp" to 0.0, "mix" to 0.0)

    /** h10 as it plays, on the given copy. */
    val full = HeuristicAi(10, seed, tune = quiet)

    /** h10's rules with looks only where the text is opaque: card plays from static scores. */
    val static = HeuristicAi(10, seed, tune = quiet, profileEdit = { it.copy(lookahead = HeuristicProfile.Look.OPAQUE) })

    val profile: HeuristicProfile get() = full.profile

    /** The book's answer to [d], the pending question of [session]. */
    fun answer(
        session: EngineSession,
        d: Decision,
        mode: PolicyMode,
        seen: Map<String, Set<String>>,
        salt: Long,
    ): Choice {
        if (d.options.size == 1) return d.options[0]
        val ai = if (mode == PolicyMode.FULL) full else static
        return try {
            ai.answerOn(session, d, Random(HeuristicAi.mix(salt, d.id)), seen).takeIf { it in d.options }
                ?: Fallback.choose(d)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Fallback.choose(d)
        }
    }

    /** The book's static values of [d]'s options on [session] (general rules plus the hero's notes). */
    fun staticValues(session: EngineSession, d: Decision, seen: Map<String, Set<String>>): DoubleArray {
        val ctx = HContext(session, d, d.team, profile, seen = seen)
        val book = HeroPlaybooks.forHero(d.seat)
        val forced = try {
            book.override(ctx, d)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return DoubleArray(d.options.size) { i ->
            val o = d.options[i]
            try {
                if (forced != null) {
                    if (o == forced) 1.0e6 else 0.0
                } else {
                    General.score(ctx, d, o) + book.adjust(ctx, d, o)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Double.NEGATIVE_INFINITY
            }
        }
    }

    /**
     * The book's own card-play values on a determinized copy (h10's card look, [CardSim] plus what the
     * look cannot see). Null when a card cannot be simulated.
     */
    fun cardValues(session: EngineSession, d: Decision, seen: Map<String, Set<String>>): DoubleArray? {
        if (d.kind != DecisionKind.PLAY_CARD || d.options.any { it !is Choice.Card }) return null
        val ctx = HContext(session, d, d.team, profile, seen = seen)
        val book = HeroPlaybooks.forHero(d.seat)
        return try {
            DoubleArray(d.options.size) { i ->
                val o = d.options[i] as Choice.Card
                val sim = CardSim.value(ctx, o.cardId) ?: return null
                sim + General.commitment(ctx, d, o) + ctx.inLook { book.adjust(ctx, d, o) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The opponent model for an unrevealed played card ([dev.quinton.goa.core.game.engine.Determinizer.Options.playWeight]):
     * a softmax of the hero's own book values over its hand pool, 15% uniform. [state] is the
     * determinizer's copy with the pool back in hand; a bare session over it gives the book its data.
     */
    fun playWeights(
        data: EngineData,
        setup: GameSetup,
        state: GameState,
        heroId: String,
        pool: List<String>,
        model: Int,
        tau: Double,
        seen: (Team) -> Map<String, Set<String>>,
    ): List<Double>? {
        if (model <= 0 || pool.size <= 1) return null
        val h = state.hero(heroId) ?: return null
        return try {
            val session = EngineSession(data, setup, state.copy())
            val d = Decision(
                id = -1L - pool.size,
                seat = heroId,
                team = h.team,
                kind = DecisionKind.PLAY_CARD,
                prompt = "Play a card",
                options = pool.map { Choice.Card(it) },
            )
            val ctx = HContext(session, d, h.team, profile, seen = seen(h.team), stateOverride = state)
            val values = if (model >= 2) {
                cardValuesOn(ctx, d) ?: staticOn(ctx, d)
            } else {
                staticOn(ctx, d)
            }
            val best = values.max()
            val soft = values.map { PortableMath.exp((it - best) / tau) }
            val sum = soft.sum()
            soft.map { UNIFORM_SHARE / pool.size + (1 - UNIFORM_SHARE) * it / sum }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun staticOn(ctx: HContext, d: Decision): DoubleArray {
        val book = HeroPlaybooks.forHero(d.seat)
        return DoubleArray(d.options.size) { i -> General.score(ctx, d, d.options[i]) + book.adjust(ctx, d, d.options[i]) }
    }

    private fun cardValuesOn(ctx: HContext, d: Decision): DoubleArray? {
        val book = HeroPlaybooks.forHero(d.seat)
        return DoubleArray(d.options.size) { i ->
            val o = d.options[i] as Choice.Card
            (CardSim.value(ctx, o.cardId) ?: return null) + General.commitment(ctx, d, o) +
                ctx.inLook { book.adjust(ctx, d, o) }
        }
    }

    companion object {
        private const val UNIFORM_SHARE = 0.15

        /** Answers [d] on [session], falling back to the first legal option when the answer is refused. */
        fun play(session: EngineSession, d: Decision, c: Choice): Boolean {
            if (session.answer(d.id, c) is AnswerResult.Accepted) return true
            val alt = d.options.firstOrNull { it != c } ?: return false
            return session.answer(d.id, alt) is AnswerResult.Accepted
        }
    }
}
