package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import kotlin.coroutines.cancellation.CancellationException

/**
 * One ply through the engine (ADR-085): answer the option, finish the current action with the
 * general playbook, then [HEval]. No playout and no second lookahead. Used for choices whose
 * text the static scores cannot read, and at higher strengths to compare a few real outcomes.
 */
object Lookahead {
    /** Choices the static rules would answer blindly (the same cases as the old search). */
    fun opaque(state: GameState, d: Decision): Boolean = when (d.kind) {
        DecisionKind.CHOOSE_OPTION,
        DecisionKind.CHOOSE_TOKEN,
        DecisionKind.CONFIRM,
        DecisionKind.CHOOSE_NUMBER,
        -> true

        DecisionKind.CHOOSE_SPACE -> {
            val subject = d.context.subjectId
            subject == null ||
                (state.hero(subject) == null && state.minion(subject) == null && state.piece(subject)?.heroBody == null)
        }

        DecisionKind.CHOOSE_UNIT -> {
            val team = state.hero(d.seat)?.team ?: d.team
            fun ownBody(id: String) = state.piece(id)?.let { it.heroBody != null && it.team == team } == true
            !d.prompt.startsWith("Target") ||
                d.options.any { o -> o is Choice.Unit && (state.hero(o.unitId)?.team == team || ownBody(o.unitId)) }
        }

        DecisionKind.CHOOSE_CARD -> {
            val own = state.hero(d.seat)
            d.options.any { it is Choice.Card && own?.card(it.cardId) == null }
        }

        else -> false
    }

    /**
     * Position value of answering [choice], from [ctx]'s team. A rejected or broken option is
     * [WORSE] than any real position.
     */
    fun value(ctx: HContext, d: Decision, choice: Choice): Double {
        val fork = ctx.session.forkForAnswer()
        return try {
            val result = fork.answer(d.id, choice)
            if (result !is AnswerResult.Accepted) return WORSE
            val hidden = finish(fork, d.seat, ctx.profile, ctx.playbooks, ctx.team)
            val after = HContext(
                session = fork,
                decision = fork.pending,
                team = ctx.team,
                profile = ctx.profile,
                playbooks = ctx.playbooks,
                seen = ctx.seen,
                finishing = true,
            )
            HEval.evaluate(after, ctx.team) + (hidden?.let { pooled(after, it) } ?: 0.0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WORSE
        }
    }

    /**
     * Answer the rest of this action (and a teammate's defense in it) with the general book. Stops at
     * an answer of [viewer]'s enemy that reads its hidden cards ([hiddenAnswer]) and returns it, for the
     * caller to price from the pool ([pooled]); null when the action finished or stopped elsewhere.
     */
    fun finish(
        session: EngineSession,
        seat: String,
        profile: HeuristicProfile,
        playbooks: (String) -> HeroHeuristics,
        viewer: Team,
    ): Decision? {
        val start = session.state
        val acting = start.acting
        val step = start.step
        val turn = start.round * 8 + start.turn
        var n = 0
        while (n < FINISH_CAP) {
            val d = session.pending ?: return null
            val s = session.state
            if (s.acting != acting || s.step != step || s.round * 8 + s.turn != turn) return null
            if (hiddenAnswer(s, d, viewer)) return d
            if (d.seat != seat && d.kind != DecisionKind.DEFEND) return null
            val ctx = HContext(
                session = session,
                decision = d,
                team = d.team,
                profile = profile,
                playbooks = playbooks,
                finishing = true,
            )
            val choice = try {
                General.best(ctx, d)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
            val result = try {
                session.answer(d.id, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
            if (result !is AnswerResult.Accepted) return null
            n++
        }
        return null
    }

    /**
     * An answer of [viewer]'s enemy that reads its hidden cards: a defense, or a card picked from its
     * hand or a facedown card (a forced discard). Answering it inside a look would play the one sampled
     * hand (DESIGN non-negotiable 1); a look ends there and prices it with [pooled] instead.
     */
    internal fun hiddenAnswer(state: GameState, d: Decision, viewer: Team): Boolean {
        if (d.team == viewer) return false
        if (d.kind == DecisionKind.DEFEND) return true
        if (d.kind != DecisionKind.CHOOSE_CARD) return false
        val h = state.hero(d.seat) ?: return false
        return d.options.any { o ->
            o is Choice.Card && h.card(o.cardId)?.let { it.zone == Zone.HAND || it.faceDown } == true
        }
    }

    /**
     * A [hiddenAnswer] priced from the possible pool, from [at]'s team: the strike on a defense
     * ([HContext.heroStrikeWorth]), the card thrown on a discard ([HContext.discardWorth]), else nothing.
     */
    internal fun pooled(at: HContext, d: Decision): Double {
        if (d.kind == DecisionKind.DEFEND) {
            return at.heroStrikeWorth(d.seat, d.context.attackTotal ?: 0, d.context.rangedAttack == true)
        }
        val h = at.hero(d.seat) ?: return 0.0
        return if (d.prompt.startsWith("Discard")) at.discardWorth(h) else 0.0
    }

    /** Below any position the evaluation returns, including a lost game. */
    const val WORSE: Double = -1.0e6

    private const val FINISH_CAP: Int = 24
}
