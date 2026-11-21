package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
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
        val look = CardLook(ctx, origin, init)
        if (first == null || first.seat != ctx.seat || first.kind != DecisionKind.CHOOSE_ACTION) {
            return run(ctx, sim, branchesOf(ctx), look)
        }
        var best: Double? = null
        val book = if (ctx.profile.heroPlaybooks) ctx.playbooks(ctx.seat) else DefaultHeroHeuristics
        val actionCtx = HContext(sim, first, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        for (o in first.options) {
            val f = sim.forkSession()
            if (f.answer(first.id, o) !is AnswerResult.Accepted) continue
            val v = run(ctx, f, branchesOf(ctx), look) + General.commitment(actionCtx, first, o) +
                actionCtx.inLook { book.adjust(actionCtx, first, o) }
            if (best == null || v > best) best = v
        }
        return best
    }

    private fun start(ctx: HContext, d: Decision, cardId: String): EngineSession? {
        val data = ctx.session.engineData
        val st = (prelude(ctx, d) ?: ctx.state).copy()
        // Not revealed: a card someone played facedown before us stays hidden in every score below.
        st.revealed = false
        Game(data, st, emptyList(), RunMode.PROBE).moveCard(ctx.seat, cardId, Zone.PLAYED)
        val rng = Random(HeuristicAi.mix(d.id, cardId.hashCode().toLong()))
        st.step = Step.RESOLVE
        st.acting = null
        st.vars[NEXT_ACTOR] = ctx.seat
        val seat = ctx.seat
        val sim = EngineSession(data, ctx.session.setup, st, rng)
        sim.quiet = true
        sim.haltWhen = { s -> seat in s.turnRecord.resolved || s.step != Step.RESOLVE }
        sim.start()
        return sim
    }

    /**
     * Team play at the card play (G14): a computer teammate whose facedown card (known to its team; a
     * human teammate's stays hidden) acts before ours resolves first, with its own book, once per
     * decision. Our card is then looked at on the position that teammate leaves: the minion it takes,
     * the hero it strips, where it stands. Null when no such teammate, or its action cannot be finished.
     */
    private fun prelude(ctx: HContext, d: Decision): dev.quinton.goa.core.game.engine.GameState? {
        if (!ctx.profile.focusFire || !ctx.profile.on("allyFirst", true)) return null
        if (ctx.memo.containsKey(PRELUDE)) return ctx.memo[PRELUDE] as dev.quinton.goa.core.game.engine.GameState?
        val v = try {
            allyAction(ctx, d)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        ctx.memo[PRELUDE] = v
        return v
    }

    private fun allyAction(ctx: HContext, d: Decision): dev.quinton.goa.core.game.engine.GameState? {
        val own = ctx.own() ?: return null
        val ours = own.hand.maxOfOrNull { ctx.card(it.id).initiative + ctx.itemBonus(ctx.seat, Stat.INITIATIVE) }
            ?: return null
        val computer = ctx.session.setup.seats.filter { it.controller != Controller.Human }.map { it.heroId }.toSet()
        // Only a teammate who surely acts before any card of ours: the order does not depend on our choice.
        val ally = ctx.allies().filter { a -> a.id in computer && a.id !in ctx.state.turnRecord.resolved }
            .mapNotNull { a ->
                val played = a.cards.singleOrNull { it.zone == Zone.PLAYED } ?: return@mapNotNull null
                val init = ctx.card(played.id).initiative + ctx.itemBonus(a.id, Stat.INITIATIVE)
                (a.id to init).takeIf { init > ours }
            }.maxByOrNull { it.second }?.first ?: return null
        val st = ctx.state.copy()
        st.revealed = false
        st.step = Step.RESOLVE
        st.acting = null
        st.vars[NEXT_ACTOR] = ally
        val rng = Random(HeuristicAi.mix(d.id, ally.hashCode().toLong()))
        val sim = EngineSession(ctx.session.engineData, ctx.session.setup, st, rng)
        sim.quiet = true
        sim.haltWhen = { s -> ally in s.turnRecord.resolved || s.step != Step.RESOLVE }
        sim.start()
        var n = 0
        while (n < PRELUDE_CAP) {
            val q = sim.pending ?: break
            // An enemy's defense or discard would be answered from the sampled hand: no prelude.
            if (Lookahead.hiddenAnswer(sim.state, q, ctx.team)) return null
            val c = HContext(sim, q, q.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            val choice = policy(c, q, ally) ?: return null
            if (sim.answer(q.id, choice) !is AnswerResult.Accepted) return null
            n++
        }
        val end = sim.state
        if (sim.pending != null || end.winner != null || ally !in end.turnRecord.resolved) return null
        return end
    }

    /**
     * Finish the action on [f]. This hero's first [branches] choices after the action (where to stand,
     * whom to hit, which bullet) try the best [WIDTH] answers of the static book and keep the best
     * position; later choices, and anyone else's, take the book's answer. An enemy's defense ends the
     * look: [score] gets the strike priced from the pool.
     */
    private fun run(ctx: HContext, f: EngineSession, branches: Int, look: Look): Double {
        var n = 0
        var d = f.pending
        while (n < CAP && d != null && !look.stop(f, d)) {
            if (Lookahead.hiddenAnswer(f.state, d, ctx.team)) return look.score(f, look.strike(f, d))
            val c = HContext(f, d, d.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            if (branches > 0 && d.seat == ctx.seat && d.options.size > 1 && d.kind in BRANCHED) {
                branch(ctx, c, f, d, branches, look)?.let { return it }
            }
            val choice = policy(c, d, ctx.seat)
            d = if (choice != null && f.answer(d.id, choice) is AnswerResult.Accepted) f.pending else null
            n++
        }
        return look.score(f, 0.0)
    }

    /** The best of the book's first [WIDTH] answers to [d], each finished; null when none was accepted. */
    private fun branch(ctx: HContext, c: HContext, f: EngineSession, d: Decision, branches: Int, look: Look): Double? {
        val ranked = ranked(c, d, ctx.seat) ?: return null
        var best: Double? = null
        for (o in ranked.take(ctx.profile.knob("width", WIDTH.toDouble()).toInt())) {
            val g = f.forkSession()
            if (g.answer(d.id, o) is AnswerResult.Accepted) {
                val v = run(ctx, g, branches - 1, look)
                if (best == null || v > best) best = v
            }
        }
        return best
    }

    /** How a look ends and is scored: the card play's own action, or a choice inside the real turn. */
    private interface Look {
        fun stop(f: EngineSession, d: Decision): Boolean

        fun strike(f: EngineSession, d: Decision): Double

        fun score(f: EngineSession, strike: Double): Double
    }

    /** The card play: the action alone, before the reveal, exposure split by initiative. */
    private class CardLook(val ctx: HContext, val origin: Hex?, val init: Int) : Look {
        override fun stop(f: EngineSession, d: Decision): Boolean = false

        override fun strike(f: EngineSession, d: Decision): Double = strikeOf(ctx, f, d, init)

        override fun score(f: EngineSession, strike: Double): Double = scoreCard(ctx, f, origin, init, strike)
    }

    /**
     * After the reveal, a choice of the real turn (an action, a target, a space): the rest of this hero's
     * action, scored by [HEval] on the revealed position. Everyone still to act acts after us.
     */
    private class TurnLook(val ctx: HContext, val acting: String?, val turn: Int) : Look {
        override fun stop(f: EngineSession, d: Decision): Boolean {
            val s = f.state
            if (s.acting != acting || s.round * 8 + s.turn != turn) return true
            return d.seat != ctx.seat && d.kind != DecisionKind.DEFEND
        }

        override fun strike(f: EngineSession, d: Decision): Double = strikeOf(ctx, f, d, Int.MAX_VALUE)

        override fun score(f: EngineSession, strike: Double): Double {
            val e = HContext(f, f.pending, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            return HEval.evaluate(e, ctx.team) + strike
        }
    }

    /**
     * Value of answering [choice] at a decision of this hero's own action after the reveal, the rest of
     * the action played out with a few tries at its next choice. Null when it cannot be answered.
     */
    fun after(ctx: HContext, d: Decision, choice: Choice): Double? {
        val f = ctx.session.forkSession()
        val look = TurnLook(ctx, ctx.state.acting, ctx.state.round * 8 + ctx.state.turn)
        return try {
            if (f.answer(d.id, choice) !is AnswerResult.Accepted) return null
            run(ctx, f, branchesOf(ctx), look)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun branchesOf(ctx: HContext): Int {
        val default = if (ctx.profile.lookahead >= HeuristicProfile.Look.MOVES) 1.0 else 0.0
        return ctx.profile.knob("branch", default).toInt()
    }

    /** Our attack on, or discard from, a hidden hand: the pool says what it is worth, not the sampled cards (G57). */
    private fun strikeOf(ctx: HContext, f: EngineSession, d: Decision, init: Int): Double {
        val at = HContext(f, d, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        // G7: a target whose card acts first may have left.
        val flee = if (init == Int.MAX_VALUE) 0.0 else ctx.profile.knob("flee", FLEE) * ctx.actsBefore(d.seat, init)
        return Lookahead.pooled(at, d) * (1.0 - flee)
    }

    /** The position on [f], from the answering team, with our exposure split by initiative. */
    private fun scoreCard(ctx: HContext, f: EngineSession, origin: Hex?, init: Int, strike: Double): Double {
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
        // An enemy who acts first and defeats us where we stand takes the strike away (G2, G8).
        val first = if (origin != null) e.defeatBefore(ctx.seat, origin, init) else 0.0
        return HEval.evaluate(e, ctx.team, exposure) + strike * (1.0 - first)
    }

    /** The options in the book's order, or null when the hero's notes override the choice. */
    private fun ranked(c: HContext, d: Decision, seat: String): List<Choice>? = try {
        val book = if (c.profile.heroPlaybooks && d.seat == seat) c.playbooks(d.seat) else DefaultHeroHeuristics
        if (book.override(c, d)?.takeIf { it in d.options } != null) {
            null
        } else {
            d.options.sortedByDescending { o -> General.score(c, d, o) + book.adjust(c, d, o) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
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
    private const val PRELUDE_CAP = 24
    private const val PRELUDE = "cardSim.prelude"

    /** Answers tried at a branched choice. */
    private const val WIDTH = 4

    /** This hero's own choices after the action that are worth a few tries. */
    private val BRANCHED = setOf(
        DecisionKind.CHOOSE_SPACE,
        DecisionKind.CHOOSE_UNIT,
        DecisionKind.CHOOSE_OPTION,
        DecisionKind.CHOOSE_NUMBER,
        DecisionKind.CHOOSE_TOKEN,
        DecisionKind.CONFIRM,
    )

    /** Share of the strike lost when the target's card acts first (it may have moved). */
    private const val FLEE = 0.3
}
