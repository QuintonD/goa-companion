package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
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
            finish(fork, d.seat, ctx.profile, ctx.playbooks)
            val after = HContext(
                session = fork,
                decision = fork.pending,
                team = ctx.team,
                profile = ctx.profile,
                playbooks = ctx.playbooks,
                seen = ctx.seen,
                finishing = true,
            )
            HEval.evaluate(after, ctx.team)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WORSE
        }
    }

    /** Answer the rest of this action (and a defense against it) with the general book. */
    fun finish(
        session: EngineSession,
        seat: String,
        profile: HeuristicProfile,
        playbooks: (String) -> HeroHeuristics,
    ) {
        val start = session.state
        val acting = start.acting
        val step = start.step
        val turn = start.round * 8 + start.turn
        var n = 0
        while (n < FINISH_CAP) {
            val d = session.pending ?: return
            val s = session.state
            if (s.acting != acting || s.step != step || s.round * 8 + s.turn != turn) return
            if (d.seat != seat && d.kind != DecisionKind.DEFEND) return
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
                return
            }
            val result = try {
                session.answer(d.id, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
            if (result !is AnswerResult.Accepted) return
            n++
        }
    }

    /** Below any position the evaluation returns, including a lost game. */
    const val WORSE: Double = -1.0e6

    private const val FINISH_CAP: Int = 24
}
