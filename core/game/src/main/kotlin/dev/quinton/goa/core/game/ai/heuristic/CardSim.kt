package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.NEXT_ACTOR
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.moveCard
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * The card play looks at the rest of its own turn (ADR-085, GENERAL §3 "card into intent"). For one card:
 * resolve this hero's card alone, now, through the engine, once per action it offers; finish the action
 * with the general book (and the hero's notes); score the result with [HEval]. Nobody else's card is
 * played, so no hidden card is used: an enemy's answer is the threat map, split by initiative
 * ([HContext.exposureSplit]): an enemy whose card may act first meets us where we stand, one acting
 * later meets us where the action leaves us. An attack on a hero is priced from the possible pool at
 * the moment the defender would answer ([HContext.heroStrikeWorth]), never from the sampled hand.
 *
 * One action, no playout, no second look: a handful of engine answers per card.
 */
internal object CardSim {
    /** Best value of playing [cardId] now, over the actions it offers. Null when it cannot be simulated. */
    fun value(ctx: HContext, cardId: String): Double? {
        val d = ctx.decision ?: return null
        val h = ctx.own() ?: return null
        val sim = start(ctx, d, cardId) ?: return null
        val origin = h.at
        val init = ctx.card(cardId).initiative + ctx.itemBonus(ctx.seat, Stat.INITIATIVE)
        val first = sim.pending
        if (first == null || first.seat != ctx.seat || first.kind != DecisionKind.CHOOSE_ACTION) {
            return run(ctx, sim, origin, init)
        }
        var best: Double? = null
        val book = if (ctx.profile.heroPlaybooks) ctx.playbooks(ctx.seat) else DefaultHeroHeuristics
        val actionCtx = HContext(sim, first, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        for (o in first.options) {
            val f = sim.forkSession()
            if (f.answer(first.id, o) !is AnswerResult.Accepted) continue
            val v = run(ctx, f, origin, init) + General.commitment(actionCtx, first, o) +
                book.adjust(actionCtx, first, o)
            if (best == null || v > best) best = v
        }
        return best
    }

    private fun start(ctx: HContext, d: Decision, cardId: String): EngineSession? {
        val data = ctx.session.engineData
        val st = ctx.state.copy()
        // Not revealed: a card someone played facedown before us stays hidden in every score below.
        st.revealed = false
        Game(data, st, emptyList(), RunMode.PROBE).moveCard(ctx.seat, cardId, Zone.PLAYED)
        st.step = Step.RESOLVE
        st.acting = null
        st.vars[NEXT_ACTOR] = ctx.seat
        val seat = ctx.seat
        val sim = EngineSession(data, ctx.session.setup, st, Random(HeuristicAi.mix(d.id, cardId.hashCode().toLong())))
        sim.quiet = true
        sim.haltWhen = { s -> seat in s.turnRecord.resolved || s.step != Step.RESOLVE }
        sim.start()
        return sim
    }

    /** Finish the action on [f]; the position after it, from the answering team. */
    private fun run(ctx: HContext, f: EngineSession, origin: Hex?, init: Int): Double {
        var strike = 0.0
        var n = 0
        while (n < CAP) {
            val d = f.pending ?: break
            if (d.kind == DecisionKind.DEFEND && d.team != ctx.team) {
                // Our attack on a hidden hand: the pool says what it is worth, not the sampled cards.
                val at = HContext(f, d, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
                strike = at.heroStrikeWorth(d.seat, d.context.attackTotal ?: 0, d.context.rangedAttack == true)
                break
            }
            val c = HContext(f, d, d.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            val choice = policy(c, d, ctx.seat) ?: break
            if (f.answer(d.id, choice) !is AnswerResult.Accepted) break
            n++
        }
        val end = f.state.copy()
        end.revealed = false
        end.acting = null
        val e = HContext(
            ctx.session,
            null,
            ctx.team,
            ctx.profile,
            ctx.playbooks,
            seen = ctx.seen,
            finishing = true,
            stateOverride = end,
        )
        val after = end.hero(ctx.seat)?.at
        val exposure = after?.let { ctx.seat to e.exposureSplit(ctx.seat, origin, it, init) }
        return HEval.evaluate(e, ctx.team, exposure) + strike
    }

    /** The general book plus, for the card's own hero, its notes. Null when nothing can answer. */
    private fun policy(c: HContext, d: Decision, seat: String): Choice? = try {
        val book = if (c.profile.heroPlaybooks && d.seat == seat) c.playbooks(d.seat) else DefaultHeroHeuristics
        book.override(c, d)?.takeIf { it in d.options } ?: run {
            var best = d.options.first()
            var bestV = Double.NEGATIVE_INFINITY
            for (o in d.options) {
                val v = General.score(c, d, o) + book.adjust(c, d, o)
                if (v > bestV) {
                    bestV = v
                    best = o
                }
            }
            best
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        d.options.firstOrNull()
    }

    private const val CAP = 16
}
