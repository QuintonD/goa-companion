package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.NEXT_ACTOR
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.moveCard
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
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
    fun value(ctx: HContext, cardId: String, assume: Map<String, String> = emptyMap()): Double? {
        val d = ctx.decision ?: return null
        val h = ctx.own() ?: return null
        val init = ctx.card(cardId).initiative + ctx.itemBonus(ctx.seat, Stat.INITIATIVE)
        // Track T (`team.order`): every known teammate card in initiative order, before and after ours.
        val team = if (TeamPlay.order(ctx.profile)) teamOrder(ctx, d, init, assume) else null
        val sim = (if (team != null) startFrom(ctx, d, cardId, team.prior.state) else start(ctx, d, cardId))
            ?: return null
        val origin = h.at
        val first = sim.pending
        val look = CardLook(ctx, origin, init, team)
        if (first == null || first.seat != ctx.seat || first.kind != DecisionKind.CHOOSE_ACTION) {
            return look.withTeam(run(ctx, sim, branchesOf(ctx), look))
        }
        var best: Double? = null
        val book = if (ctx.profile.heroPlaybooks) ctx.playbooks(ctx.seat) else DefaultHeroHeuristics
        val actionCtx = HContext(sim, first, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        for (o in first.options) {
            val f = sim.forkForAnswer()
            if (f.answer(first.id, o) !is AnswerResult.Accepted) continue
            val v = look.withTeam(run(ctx, f, branchesOf(ctx), look)) + General.commitment(actionCtx, first, o) +
                actionCtx.inLook { book.adjust(actionCtx, first, o) }
            if (best == null || v > best) best = v
        }
        return best
    }

    private fun start(ctx: HContext, d: Decision, cardId: String): EngineSession? =
        startFrom(ctx, d, cardId, prelude(ctx, d) ?: ctx.state)

    private fun startFrom(ctx: HContext, d: Decision, cardId: String, base: GameState): EngineSession? {
        val data = ctx.session.engineData
        val st = base.copy()
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
    private fun prelude(ctx: HContext, d: Decision): GameState? {
        if (!ctx.profile.focusFire || !ctx.profile.on("allyFirst", true)) return null
        if (ctx.memo.containsKey(PRELUDE)) return ctx.memo[PRELUDE] as GameState?
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

    private fun allyAction(ctx: HContext, d: Decision): GameState? {
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
        return actAlone(ctx, ctx.state, ally, d.id)
    }

    /**
     * [ally]'s action alone on a copy of [from], with its own book; null when it needs a hidden answer,
     * or cannot be finished. The teammate's card is known to its team (a computer's facedown card).
     */
    private fun actAlone(ctx: HContext, from: GameState, ally: String, salt: Long): GameState? {
        val st = from.copy()
        st.revealed = false
        st.step = Step.RESOLVE
        st.acting = null
        st.vars[NEXT_ACTOR] = ally
        val rng = Random(HeuristicAi.mix(salt, ally.hashCode().toLong()))
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
     * whom to hit, which bullet) try the best [HeuristicProfile.lookWidth] answers of the static book and keep the best
     * position; later choices, and anyone else's, take the book's answer. An enemy's defense ends the
     * look: [score] gets the strike priced from the pool.
     */
    private fun run(ctx: HContext, f: EngineSession, branches: Int, look: Look, actions: Boolean = false): Double {
        var n = 0
        var d = f.pending
        while (n < look.cap && d != null && !look.stop(f, d)) {
            if (look.hidden(f, d)) return look.score(f, look.strike(f, d))
            val c = HContext(f, d, d.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            // A sampled turn (F2): our own action is chosen once the cards are revealed, so every action is tried.
            if (actions && d.seat == ctx.seat && f.state.acting == ctx.seat && d.options.size > 1 &&
                d.kind in ACTION_KINDS
            ) {
                actionBranch(ctx, c, f, d, branches, look)?.let { return it }
            }
            if (branches > 0 && d.seat == ctx.seat && d.options.size > 1 && d.kind in BRANCHED) {
                branch(ctx, c, f, d, branches, look)?.let { return it }
            }
            // Our own choices by our book; in a turn played out, everyone else's by theirs.
            val choice = if (look.everyBook && d.seat != ctx.seat && ctx.profile.on("restBook", false)) {
                fullBook(ctx, f, d)
            } else {
                policy(c, d, if (look.everyBook) d.seat else ctx.seat)
            }
            d = if (choice != null && f.answer(d.id, choice) is AnswerResult.Accepted) f.pending else null
            n++
        }
        return look.score(f, 0.0)
    }

    /** The best of every answer to our action's first question [d] (F2), each played to the look's end. */
    private fun actionBranch(
        ctx: HContext,
        c: HContext,
        f: EngineSession,
        d: Decision,
        branches: Int,
        look: Look,
    ): Double? {
        val book = if (c.profile.heroPlaybooks) c.playbooks(d.seat) else DefaultHeroHeuristics
        var best: Double? = null
        for (o in d.options) {
            val g = f.forkForAnswer()
            if (g.answer(d.id, o) !is AnswerResult.Accepted) continue
            val v = run(ctx, g, branches, look) + General.commitment(c, d, o) + c.inLook { book.adjust(c, d, o) }
            if (best == null || v > best) best = v
        }
        return best
    }

    /**
     * The best of the book's first [HeuristicProfile.lookWidth] answers to [d], each finished; null when none
     * was accepted.
     */
    private fun branch(ctx: HContext, c: HContext, f: EngineSession, d: Decision, branches: Int, look: Look): Double? {
        val ranked = ranked(c, d, ctx.seat) ?: return null
        var best: Double? = null
        for (o in ranked.take(ctx.profile.knob("width", ctx.profile.lookWidth.toDouble()).toInt())) {
            val g = f.forkForAnswer()
            if (g.answer(d.id, o) is AnswerResult.Accepted) {
                val v = run(ctx, g, branches - 1, look)
                if (best == null || v > best) best = v
            }
        }
        return best
    }

    /** How a look ends and is scored: the card play's own action, or a choice inside the real turn. */
    private interface Look {
        /** Most answers the look plays before it scores. */
        val cap: Int get() = CAP

        /** Every hero answers with its own playbook (a turn played out), not only the looking hero. */
        val everyBook: Boolean get() = false

        fun stop(f: EngineSession, d: Decision): Boolean

        /** An answer that would read a hidden hand: the look ends there and prices it ([strike]). */
        fun hidden(f: EngineSession, d: Decision): Boolean = Lookahead.hiddenAnswer(f.state, d, viewer)

        /** The team the look is for (what [hidden] protects). */
        val viewer: Team

        fun strike(f: EngineSession, d: Decision): Double

        fun score(f: EngineSession, strike: Double): Double
    }

    /** The card play: the action alone, before the reveal, exposure split by initiative. */
    private class CardLook(val ctx: HContext, val origin: Hex?, val init: Int, val team: TeamOrder? = null) : Look {
        override val viewer get() = ctx.team

        /** Strength 10 (knob `knowLook`): the enemy's hand is known (ADR-062), so its book answers its defense. */
        private val known = ctx.profile.knowHands && ctx.profile.on("knowLook", ctx.profile.knowLook)

        override val everyBook: Boolean get() = known

        override fun hidden(f: EngineSession, d: Decision): Boolean =
            !known && Lookahead.hiddenAnswer(f.state, d, ctx.team)

        override fun stop(f: EngineSession, d: Decision): Boolean = false

        override fun strike(f: EngineSession, d: Decision): Double = strikeOf(ctx, f, d, init, team)

        /** The best leaf of one action's look so far (Track T): the teammates after us play from it alone. */
        private var leaf: EngineSession? = null
        private var leafStrike = 0.0
        private var leafValue = Double.NEGATIVE_INFINITY

        override fun score(f: EngineSession, strike: Double): Double {
            if (team == null) return scoreCard(ctx, f, origin, init, strike)
            if (team.after.isEmpty()) return scoreTeam(ctx, f, origin, init, strike, team, post = false)
            // The branches pick their leaf without the teammates after us; [withTeam] plays them from the best one.
            val v = scoreTeam(ctx, f, origin, init, strike, team, post = false)
            if (v > leafValue) {
                leafValue = v
                leaf = f
                leafStrike = strike
            }
            return v
        }

        /** [v], one action's look, with the teammates after us played from its best leaf. */
        fun withTeam(v: Double): Double {
            val f = leaf
            leaf = null
            leafValue = Double.NEGATIVE_INFINITY
            if (team == null || team.after.isEmpty() || f == null) return v
            return scoreTeam(ctx, f, origin, init, leafStrike, team, post = true)
        }
    }

    /**
     * After the reveal, a choice of the real turn (an action, a target, a space): the rest of this hero's
     * action, scored by [HEval] on the revealed position. Everyone still to act acts after us.
     */
    private class TurnLook(val ctx: HContext, val acting: String?, val turn: Int) : Look {
        override val viewer get() = ctx.team

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
     * F1: after the reveal, the rest of the turn. Every hero still to act resolves its revealed card with
     * its own book; on turn 4 the round ends (minion battle, push, level-ups) and the look stops at the
     * next round's first card play. [HEval] scores where the turn leaves everyone, so a square is worth
     * what happens to it, not what the threat map guesses. An enemy's defense or discard reads a hand:
     * from strength 10 the hand is known (ADR-062) and its book answers it; below, the look stops there
     * and prices it from the pool, unless knob `restHidden` lets the sampled hand answer.
     */
    private class RestLook(val ctx: HContext, val turn: Int) : Look {
        override val viewer get() = ctx.team

        override val cap: Int get() = REST_CAP

        override val everyBook: Boolean get() = true

        private val playHidden = ctx.profile.knowHands || ctx.profile.on("restHidden", false)

        override fun stop(f: EngineSession, d: Decision): Boolean {
            val s = f.state
            return d.kind == DecisionKind.PLAY_CARD || s.round * 8 + s.turn != turn
        }

        override fun hidden(f: EngineSession, d: Decision): Boolean =
            !playHidden && Lookahead.hiddenAnswer(f.state, d, ctx.team)

        override fun strike(f: EngineSession, d: Decision): Double = strikeOf(ctx, f, d, Int.MAX_VALUE)

        override fun score(f: EngineSession, strike: Double): Double {
            val e = HContext(f, f.pending, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            return HEval.evaluate(e, ctx.team) + strike
        }
    }

    /**
     * F2 (REVIEW-2-losses): the card play as a sampled turn. Every other hero's card is drawn from its own
     * book (the opponent model: each card of its pool scored by [value] from its seat, softmax at
     * [MODEL_TEMP] coins with a [MODEL_FLOOR] uniform share); a computer teammate's facedown card is known
     * to its team and kept. For each of [samples] draws (the same draws for every card of ours: common
     * random numbers) the turn is revealed and resolved by the books ([RestLook]), our own action chosen
     * after the reveal as the real one is (every action tried), and [HEval] scores the end of the turn.
     * The pools read are this team's view: the determinized copy, so a hidden hand is the sample's.
     * Returns the mean value per card of [cards] (in order), or null when the turn cannot be sampled.
     */
    fun sampled(ctx: HContext, cards: List<String>, samples: Int): DoubleArray? {
        val d = ctx.decision ?: return null
        val base = ctx.state.copy()
        val data = ctx.session.engineData
        val g0 = Game(data, base, emptyList(), RunMode.PROBE)
        // Who plays what: fixed (a computer teammate's known card), or drawn from a pool.
        val fixed = HashMap<String, String>()
        val drawn = ArrayList<Pair<String, List<Pair<String, Double>>>>()
        val computer = ctx.session.setup.seats.filter { it.controller != Controller.Human }.map { it.heroId }.toSet()
        for (h in base.heroes) {
            if (h.id == ctx.seat) continue
            val played = h.cards.firstOrNull { it.zone == Zone.PLAYED }?.id
            if (played != null && h.team == ctx.team && h.id in computer) {
                fixed[h.id] = played
                continue
            }
            // An unrevealed played card goes back to the pool it came from.
            if (played != null) g0.moveCard(h.id, played, Zone.HAND)
        }
        for (h in base.heroes) {
            if (h.id == ctx.seat || h.id in fixed) continue
            val pool = h.cards.filter { it.zone == Zone.HAND }.map { it.id }
            if (pool.isEmpty()) continue
            drawn += h.id to model(ctx, base, h.id, pool)
        }
        val rng = Random(HeuristicAi.mix(d.id, SAMPLE_SALT))
        val draws = List(samples) { drawn.associate { (hero, p) -> hero to pickWeighted(p, rng) } }
        val out = DoubleArray(cards.size)
        val turn = base.round * 8 + base.turn
        for ((i, card) in cards.withIndex()) {
            var sum = 0.0
            for ((k, draw) in draws.withIndex()) {
                val st = base.copy()
                val g = Game(data, st, emptyList(), RunMode.PROBE)
                g.moveCard(ctx.seat, card, Zone.PLAYED)
                for ((hero, c) in draw) g.moveCard(hero, c, Zone.PLAYED)
                st.step = Step.REVEAL
                st.revealed = false
                st.acting = null
                val sim = EngineSession(data, ctx.session.setup, st, Random(HeuristicAi.mix(d.id, k.toLong())))
                sim.quiet = true
                sim.start()
                val branches = ctx.profile.knob("revealBranch", 0.0).toInt()
                sum += run(ctx, sim, branches, SampleLook(ctx, turn), actions = true)
            }
            out[i] = sum / samples
        }
        return out
    }

    /**
     * Strength 10, knob `predict`: the opponent model for every enemy whose card is not revealed, kept in
     * [ctx]'s memo so each card's look ([scoreCard]) weighs the enemy's cards by it. Their pool is known
     * at 10 (ADR-062); which card of it was played is not, so a played card goes back to the pool.
     */
    fun cardModel(ctx: HContext) {
        if (ctx.memo.containsKey(MODEL)) return
        ctx.memo[MODEL] = null
        if (!ctx.profile.knowHands || ctx.state.revealed) return
        val base = ctx.state.copy()
        val g0 = Game(ctx.session.engineData, base, emptyList(), RunMode.PROBE)
        val out = HashMap<String, Map<String, Double>>()
        for (h in base.heroes) {
            if (h.team == ctx.team) continue
            val played = h.cards.firstOrNull { it.zone == Zone.PLAYED } ?: continue
            if (ctx.profile.omniscient) out[h.id] = mapOf(played.id to 1.0) else g0.moveCard(h.id, played.id, Zone.HAND)
        }
        for (h in base.heroes) {
            if (h.team == ctx.team || h.id in out) continue
            val pool = h.cards.filter { it.zone == Zone.HAND }.map { it.id }
            if (pool.isEmpty()) continue
            out[h.id] = model(ctx, base, h.id, pool).toMap()
        }
        ctx.memo[MODEL] = out
    }

    @Suppress("UNCHECKED_CAST")
    private fun modelOf(ctx: HContext): Map<String, Map<String, Double>>? =
        ctx.memo[MODEL] as Map<String, Map<String, Double>>?

    /** The opponent model: [heroId]'s chance to play each card of [pool], from its own book on [base]. */
    private fun model(ctx: HContext, base: GameState, heroId: String, pool: List<String>): List<Pair<String, Double>> {
        val h = base.hero(heroId) ?: return pool.map { it to 1.0 / pool.size }
        val q = Decision(
            id = ctx.decision?.id ?: 0L,
            seat = heroId,
            team = h.team,
            kind = DecisionKind.PLAY_CARD,
            prompt = "model",
            options = pool.map { Choice.Card(it) },
        )
        val hc = HContext(ctx.session, q, h.team, ctx.profile, ctx.playbooks, finishing = true, stateOverride = base)
        val static = ctx.profile.knob("revealModel", 1.0) <= 0.0
        val book = if (ctx.profile.heroPlaybooks) ctx.playbooks(heroId) else DefaultHeroHeuristics
        val scores = pool.map { id ->
            val o = Choice.Card(id)
            val v = try {
                if (static) {
                    General.score(hc, q, o) + book.adjust(hc, q, o)
                } else {
                    (value(hc, id) ?: General.score(hc, q, o)) + General.commitment(hc, q, o) +
                        hc.inLook { book.adjust(hc, q, o) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                0.0
            }
            v
        }
        val temp = ctx.profile.knob("modelTemp", MODEL_TEMP)
        val floor = ctx.profile.knob("modelFloor", MODEL_FLOOR)
        val top = scores.max()
        val e = scores.map { PortableMath.exp((it - top) / temp) }
        val z = e.sum()
        return pool.indices.map { pool[it] to (1.0 - floor) * e[it] / z + floor / pool.size }
    }

    private fun pickWeighted(p: List<Pair<String, Double>>, rng: Random): String {
        var r = rng.nextDouble() * p.sumOf { it.second }
        for ((id, w) in p) {
            r -= w
            if (r <= 0.0) return id
        }
        return p.last().first
    }

    /** A sampled turn (F2): from the reveal to the end of the turn, every hand answering from the sample. */
    private class SampleLook(val ctx: HContext, val turn: Int) : Look {
        override val viewer get() = ctx.team

        override val cap: Int get() = REST_CAP

        override val everyBook: Boolean get() = true

        override fun stop(f: EngineSession, d: Decision): Boolean {
            val s = f.state
            return (d.kind == DecisionKind.PLAY_CARD && s.step == Step.PLAY) || s.round * 8 + s.turn != turn
        }

        override fun hidden(f: EngineSession, d: Decision): Boolean = false

        override fun strike(f: EngineSession, d: Decision): Double = 0.0

        override fun score(f: EngineSession, strike: Double): Double {
            val e = HContext(f, f.pending, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            return HEval.evaluate(e, ctx.team)
        }
    }

    /** True when a look at [ctx]'s decision plays the rest of the turn ([RestLook]). */
    fun restOn(ctx: HContext): Boolean =
        ctx.profile.on("rest", ctx.profile.turnRest) && ctx.revealed && ctx.state.step == Step.RESOLVE

    /**
     * Value of answering [choice] at [d] with the rest of the turn played out ([RestLook]). Null when it
     * cannot be answered.
     */
    fun rest(ctx: HContext, d: Decision, choice: Choice): Double? {
        val f = ctx.session.forkForAnswer()
        val look = RestLook(ctx, ctx.state.round * 8 + ctx.state.turn)
        return try {
            if (f.answer(d.id, choice) !is AnswerResult.Accepted) return null
            run(ctx, f, if (d.seat == ctx.seat) branchesOf(ctx) else 0, look)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Value of answering [choice] at a decision of this hero's own action after the reveal, the rest of
     * the action played out with a few tries at its next choice. Null when it cannot be answered.
     */
    fun after(ctx: HContext, d: Decision, choice: Choice): Double? {
        val f = ctx.session.forkForAnswer()
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
        val moves = ctx.profile.lookahead >= HeuristicProfile.Look.MOVES
        val default = if (moves) ctx.profile.lookBranches.toDouble() else 0.0
        return ctx.profile.knob("branch", default).toInt()
    }

    /** Our attack on, or discard from, a hidden hand: the pool says what it is worth, not the sampled cards (G57). */
    private fun strikeOf(ctx: HContext, f: EngineSession, d: Decision, init: Int, team: TeamOrder? = null): Double {
        val at = HContext(f, d, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        if (team != null) {
            at.teamHits = team.prior.hits
            at.teamDead = team.prior.dead
            at.teamInit = init
            at.teamSimulated = team.after.toSet()
        }
        // G7: a target whose card acts first may have left.
        val flee = if (init == Int.MAX_VALUE) 0.0 else ctx.profile.knob("flee", FLEE) * ctx.actsBefore(d.seat, init)
        return Lookahead.pooled(at, d) * (1.0 - flee)
    }

    /**
     * REVIEW-2-design #5 (knob `allyAfter`): a computer teammate whose facedown card (known to its team)
     * acts after ours resolves with its own book once our action is done, so our card is scored with
     * the minion it takes and the hero it strips. Enemies acting between us stay in the threat map.
     */
    private fun postlude(ctx: HContext, f: EngineSession, init: Int): GameState? {
        if (!ctx.profile.focusFire || !ctx.profile.on("allyAfter", false) || f.pending != null) return null
        val s = f.state
        val computer = ctx.session.setup.seats.filter { it.controller != Controller.Human }.map { it.heroId }.toSet()
        val ally = s.heroes.filter { a ->
            a.team == ctx.team && a.id != ctx.seat && a.id in computer && a.at != null && a.id !in s.turnRecord.resolved
        }.mapNotNull { a ->
            val played = a.cards.singleOrNull { it.zone == Zone.PLAYED } ?: return@mapNotNull null
            val ini = ctx.card(played.id).initiative + ctx.itemBonus(a.id, Stat.INITIATIVE)
            (a.id to ini).takeIf { ini < init }
        }.maxByOrNull { it.second }?.first ?: return null
        return try {
            actAlone(ctx, s, ally, ctx.decision?.id ?: 0L)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** The position on [f], from the answering team, with our exposure split by initiative. */
    private fun scoreCard(ctx: HContext, f: EngineSession, origin: Hex?, init: Int, strike: Double): Double {
        val end = (postlude(ctx, f, init) ?: f.state).copy()
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
            cardModel = modelOf(ctx),
        )
        val after = end.hero(ctx.seat)?.at
        val exposure = after?.let { ctx.seat to e.exposureSplit(ctx.seat, origin, it, init) }
        // An enemy who acts first and defeats us where we stand takes the strike away (G2, G8).
        val first = if (origin != null) e.defeatBefore(ctx.seat, origin, init) else 0.0
        return HEval.evaluate(e, ctx.team, exposure) + strike * (1.0 - first)
    }

    // ---------------------------------------------------------------------------------------------
    // Track T (`team.order`, G113–G114): teammates' known cards resolved in initiative order.
    // ---------------------------------------------------------------------------------------------

    /**
     * A card play's teammates: [prior] is the position after every known teammate card that acts before ours
     * (and what their priced hits did), [after] the teammates whose known card acts after ours, fastest first.
     */
    internal class TeamOrder(val prior: Prior, val after: List<String>)

    /**
     * A position with the hits priced on the way to it: [strike] the value of teammates' hits that reached a hidden
     * answer (the pool's price), [hits] / [dead] the chance each enemy hero was made to throw a card / defeated.
     */
    internal class Prior(
        val state: GameState,
        val strike: Double,
        val hits: Map<String, Double>,
        val dead: Map<String, Double>,
    )

    /**
     * The teammates of [ctx]'s card play whose card is known to the team (a computer teammate's facedown card, and
     * the cards [assume]d for a teammate still to choose, G115), split by our card's initiative [init]: faster or
     * tied (a same-team tie is the team's to order; the look plays the teammate first) act before, slower after.
     * The before part is resolved once per decision and split ([TEAM_PRE] memo).
     */
    private fun teamOrder(ctx: HContext, d: Decision, init: Int, assume: Map<String, String>): TeamOrder {
        val base = teamBase(ctx, assume)
        val computers = TeamPlay.computers(ctx)
        val allies = base.heroes.filter { a ->
            a.team == ctx.team && a.id != ctx.seat && a.id in computers && a.id !in base.turnRecord.resolved &&
                a.cards.count { it.zone == Zone.PLAYED } == 1
        }.map { a ->
            val card = a.cards.single { it.zone == Zone.PLAYED }.id
            a to (ctx.card(card).initiative + ctx.itemBonus(a.id, Stat.INITIATIVE))
        }.sortedWith(compareByDescending<Pair<HeroState, Int>> { it.second }.thenBy { it.first.seat })
        val before = allies.filter { it.second >= init }.map { it.first.id }
        val after = allies.filter { it.second < init }.map { it.first.id }
        val key = TEAM_PRE + assume.entries.joinToString(",") { "${it.key}=${it.value}" } + "|" + before.joinToString(",")
        val prior = ctx.memo.getOrPut(key) { chain(ctx, base, before, d.id, emptyMap(), emptyMap(), 0.0) } as Prior
        return TeamOrder(prior, after)
    }

    /** [ctx]'s position with the [assume]d cards facedown (a teammate still to choose, G115). */
    private fun teamBase(ctx: HContext, assume: Map<String, String>): GameState {
        if (assume.isEmpty()) return ctx.state
        val key = TEAM_BASE + assume.entries.joinToString(",") { "${it.key}=${it.value}" }
        return ctx.memo.getOrPut(key) {
            val st = ctx.state.copy()
            val g = Game(ctx.session.engineData, st, emptyList(), RunMode.PROBE)
            for ((hero, card) in assume) {
                if (st.hero(hero)?.card(card)?.zone == Zone.HAND) g.moveCard(hero, card, Zone.PLAYED)
            }
            st
        } as GameState
    }

    /** [allies] act alone, in order, from [from]; one that cannot be finished is left to the threat map. */
    @Suppress("LongParameterList")
    private fun chain(
        ctx: HContext,
        from: GameState,
        allies: List<String>,
        salt: Long,
        hits: Map<String, Double>,
        dead: Map<String, Double>,
        strike: Double,
    ): Prior {
        var p = Prior(from, strike, hits, dead)
        for (a in allies) {
            val next = try {
                actPriced(ctx, p, a, salt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (next != null) p = next
        }
        return p
    }

    /**
     * [ally]'s action alone on a copy of [p]'s position, with its own book. An enemy's defense or discard (which
     * reads a hidden hand) is priced from the pool, from our team's side, with the hits already landed ([p]); the
     * action ends there (G114): its card is resolved and the enemy keeps its hand in the position. Null when the
     * action cannot be finished.
     */
    private fun actPriced(ctx: HContext, p: Prior, ally: String, salt: Long): Prior? {
        val st = p.state.copy()
        st.revealed = false
        st.step = Step.RESOLVE
        st.acting = null
        st.vars[NEXT_ACTOR] = ally
        val rng = Random(HeuristicAi.mix(salt, ally.hashCode().toLong()))
        val sim = EngineSession(ctx.session.engineData, ctx.session.setup, st, rng)
        sim.quiet = true
        sim.haltWhen = { s -> ally in s.turnRecord.resolved || s.step != Step.RESOLVE }
        sim.start()
        var n = 0
        while (n < PRELUDE_CAP) {
            val q = sim.pending ?: break
            if (Lookahead.hiddenAnswer(sim.state, q, ctx.team)) {
                val at = HContext(sim, q, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
                at.teamHits = p.hits
                at.teamDead = p.dead
                at.teamInit = Int.MAX_VALUE
                at.teamSimulated = setOf(ctx.seat)
                val strike = Lookahead.pooled(at, q)
                val (hits, dead) = landed(at, q, p)
                val end = sim.state.copy()
                if (!abort(ctx, end, ally)) return null
                return Prior(end, p.strike + strike, hits, dead)
            }
            val c = HContext(sim, q, q.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
            if (q.team == ctx.team) {
                c.teamHits = p.hits
                c.teamDead = p.dead
            }
            val choice = policy(c, q, ally) ?: return null
            if (sim.answer(q.id, choice) !is AnswerResult.Accepted) return null
            n++
        }
        val end = sim.state
        if (sim.pending != null || end.winner != null || ally !in end.turnRecord.resolved) return null
        return Prior(end, p.strike, p.hits, p.dead)
    }

    /** The hit priced at [q] (an enemy's defense or discard): it surely costs them a card unless it defeats them. */
    private fun landed(at: HContext, q: Decision, p: Prior): Pair<Map<String, Double>, Map<String, Double>> {
        val target = q.seat
        val h = at.state.hero(target) ?: return p.hits to p.dead
        val kill = if (q.kind == DecisionKind.DEFEND) {
            at.teamKillChance(target, q.context.attackTotal ?: 0, q.context.rangedAttack == true)
        } else {
            0.0
        }
        if (h.handSize <= 0) return p.hits to p.dead
        val dead = p.dead + (target to ((p.dead[target] ?: 0.0) + kill).coerceAtMost(1.0))
        return (p.hits + (target to 1.0)) to dead
    }

    /** End [hero]'s action on [st] where it stands (a look priced the rest): its card resolved, nobody acting. */
    private fun abort(ctx: HContext, st: GameState, hero: String): Boolean {
        val h = st.hero(hero) ?: return false
        val played = h.cards.filter { it.zone == Zone.PLAYED }.map { it.id }
        val g = Game(ctx.session.engineData, st, emptyList(), RunMode.PROBE)
        for (c in played) g.moveCard(hero, c, Zone.RESOLVED, slot = st.turn, keepEffects = true)
        if (hero !in st.turnRecord.resolved) st.turnRecord.resolved += hero
        st.acting = null
        st.step = Step.RESOLVE
        return true
    }

    /**
     * The card play's position with its teammates: our action's end (ended where a hidden answer was priced, when
     * it was), then each teammate whose known card acts after ours, with its own book, every hit priced with the
     * hits before it; scored as [scoreCard] plus the teammates' priced hits.
     */
    @Suppress("LongParameterList")
    private fun scoreTeam(
        ctx: HContext,
        f: EngineSession,
        origin: Hex?,
        init: Int,
        strike: Double,
        team: TeamOrder,
        post: Boolean = true,
    ): Double {
        var after: Prior? = null
        if (post && team.after.isNotEmpty()) {
            after = try {
                val end = f.state.copy()
                var hits = team.prior.hits
                var dead = team.prior.dead
                val q = f.pending
                if (q != null) {
                    // Our hit ended the look at their answer: it is priced, and the teammates meet what it leaves.
                    val at = HContext(f, q, ctx.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
                    at.teamHits = hits
                    at.teamDead = dead
                    val l = landed(at, q, team.prior)
                    hits = l.first
                    dead = l.second
                }
                if (abort(ctx, end, ctx.seat)) {
                    chain(ctx, end, team.after, ctx.decision?.id ?: 0L, hits, dead, 0.0)
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        val end = (after?.state ?: f.state).copy()
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
            cardModel = modelOf(ctx),
        )
        val stands = end.hero(ctx.seat)?.at
        val exposure = stands?.let { ctx.seat to e.exposureSplit(ctx.seat, origin, it, init) }
        val first = if (origin != null) e.defeatBefore(ctx.seat, origin, init) else 0.0
        return HEval.evaluate(e, ctx.team, exposure) + strike * (1.0 - first) + team.prior.strike +
            (after?.strike ?: 0.0)
    }

    private const val TEAM_PRE = "team.pre:"
    private const val TEAM_BASE = "team.base:"

    /**
     * Knob `restBook` (measurement): another hero's answer inside a played-out turn by the whole book of
     * strengths 5–8 (its one-action looks), not the static scores. Never recursive: no turn played out inside.
     */
    private fun fullBook(ctx: HContext, f: EngineSession, d: Decision): Choice? {
        val ai = ctx.memo.getOrPut(FULL_BOOK) {
            val p = ctx.profile.copy(turnRest = false, revealSamples = 0, tune = ctx.profile.tune - NESTED_KNOBS)
            HeuristicAi(p.strength, 0L, tune = p.tune, profileEdit = { p }, playbooks = ctx.playbooks)
        } as HeuristicAi
        return try {
            ai.answerOn(f, d, Random(HeuristicAi.mix(d.id, d.seat.hashCode().toLong())), emptyMap())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private const val FULL_BOOK = "cardSim.fullBook"
    private val NESTED_KNOBS = setOf("rest", "reveal", "restBook", "escape")

    /** The options in the book's order, or null when the hero's notes override the choice. */
    private fun ranked(c: HContext, d: Decision, seat: String): List<Choice>? = try {
        val book = if (c.profile.heroPlaybooks && d.seat == seat) c.playbooks(d.seat) else DefaultHeroHeuristics
        if (book.override(c, d)?.takeIf { it in d.options } != null) {
            null
        } else {
            // Each option scored once (the scores are pure for this context), then the same stable sort.
            val v = DoubleArray(d.options.size) { i ->
                General.score(c, d, d.options[i]) + book.adjust(c, d, d.options[i])
            }
            d.options.indices.sortedByDescending { v[it] }.map { d.options[it] }
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

    private const val SAMPLE_SALT = 0x51ED_2026L
    private const val MODEL = "cardSim.model"

    /** Opponent model (F2): softmax temperature in coins, and the uniform share every card keeps. */
    private const val MODEL_TEMP = 2.0
    private const val MODEL_FLOOR = 0.15

    /** Our action's first question in a sampled turn: every answer is tried. */
    private val ACTION_KINDS = setOf(DecisionKind.CHOOSE_ACTION, DecisionKind.RESPAWN)

    /** A whole turn, and on turn 4 the round's end: every hero's action and its follow-ups. */
    private const val REST_CAP = 160
    private const val PRELUDE_CAP = 24
    private const val PRELUDE = "cardSim.prelude"

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
