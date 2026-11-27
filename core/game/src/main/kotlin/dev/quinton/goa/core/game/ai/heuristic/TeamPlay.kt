package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Team play (strategy round, Track T; GENERAL §4, G113–G118). The owner: "team play (let's assume we know what
 * cards our team mates are playing). Combos and working together."
 *
 * A COMPUTER teammate's hand and facedown card are known to its team (DESIGN non-negotiable 1); a HUMAN
 * teammate's facedown card stays hidden exactly as before (the determinized copy deals it again, and nothing
 * here reads it: [knownCard] is null for a human until the reveal).
 *
 * Knobs (`HeuristicProfile.tune`): the master switch `team` (on), and per layer `team.<name>`, each on from the
 * strength named at its use. With `team=0` the book answers exactly as before this track.
 * - `team.known` (7): a computer teammate's facedown card is its card this turn in every threat and initiative
 *   read (it was one colour in k, the REVIEW-2-design dilution).
 * - `team.order` (7): the card play's look resolves every computer teammate's known card in initiative order,
 *   before ours (prelude) and after ours (postlude), each with its own book ([CardSim]).
 * - `team.follow` (7): a hit we price is followed by a known teammate card that reaches the same hero: the
 *   card our strip takes is the one their hit no longer meets ([followUp]), in place of the old constant.
 * - `team.joint` (8): the teammate asked first picks its half of the best pair of cards ([joint]).
 * - `team.reserve` (9): two-turn kills: the team keeps a turn-4 finisher for an enemy that will arrive empty-handed
 *   ([reserve]), and a strip is worth its set-up only when someone will finish ([finisherFor]).
 * - `team.tie` (9): a same-team initiative tie is decided by playing the rest of the turn both ways.
 */
internal object TeamPlay {
    /** Track T layer [name]: the master switch `team` and `team.<name>`, by default on from strength [from]. */
    fun on(p: HeuristicProfile, name: String, from: Int): Boolean =
        p.on("team", true) && p.on("team.$name", p.strength >= from)

    fun known(p: HeuristicProfile): Boolean = p.focusFire && on(p, "known", 7)

    fun order(p: HeuristicProfile): Boolean = p.focusFire && on(p, "order", 7)

    fun follow(p: HeuristicProfile): Boolean = p.focusFire && on(p, "follow", 7)

    /** Heroes seated as computer players (every seat but a human's). */
    fun computers(ctx: HContext): Set<String> =
        ctx.session.setup.seats.filter { it.controller != Controller.Human }.map { it.heroId }.toSet()

    /**
     * True when [h]'s facedown card is known to [ctx]'s team before the reveal: this hero's own, or a
     * computer teammate's (`team.known`). Never a human teammate's, never an enemy's.
     */
    fun knowsFacedown(ctx: HContext, h: HeroState): Boolean {
        if (h.team != ctx.team || !known(ctx.profile)) return false
        return h.id == ctx.seat || h.id in computers(ctx)
    }

    /** [h]'s card this turn as its team knows it: revealed, or facedown and [knowsFacedown]. Null when unknown. */
    fun knownCard(ctx: HContext, h: HeroState): String? {
        val played = h.cards.firstOrNull { it.zone == Zone.PLAYED }?.id ?: return null
        return if (ctx.state.revealed || knowsFacedown(ctx, h)) played else null
    }

    /** Effective initiative of [heroId]'s card [cardId] (items included). */
    fun initiative(ctx: HContext, heroId: String, cardId: String): Int =
        ctx.card(cardId).initiative + ctx.itemBonus(heroId, Stat.INITIATIVE)

    /**
     * G113 (`team.follow`): what a teammate whose known card acts after the striker adds to a hit on [target]
     * that does not defeat (probability 1 - [chance]): their hit then meets a hand one card thinner. The best
     * such teammate's gain, times the chance the hit does not defeat. Null when no teammate's card is known
     * and one could still follow (the caller keeps the old constant); 0 when every teammate is known and none
     * follows. Teammates whose action the look plays itself ([HContext.teamSimulated]) are left to the look.
     */
    fun followUp(ctx: HContext, target: HeroState, chance: Double): Double? {
        val at = target.at ?: return 0.0
        val s = ctx.state
        var unknown = false
        var best = 0.0
        for (a in s.heroes) {
            if (a.team != ctx.team || a.id == ctx.seat || a.id == s.acting) continue
            if (a.id in s.turnRecord.resolved || a.id in ctx.teamSimulated) continue
            val from = a.at ?: continue
            val card = knownCard(ctx, a)
            if (card == null) {
                if (a.hasUnresolved || ctx.playsThisTurn(a)) unknown = unknown || reaches(ctx, a, at)
                continue
            }
            val c = ctx.card(card)
            if (!c.attackCard && !c.discardOrDefeat) continue
            // Before the reveal, a teammate whose card acts first has already met the target as it is.
            val strikerInit = ctx.teamInit
            if (!s.revealed && strikerInit != null && strikerInit != Int.MAX_VALUE &&
                initiative(ctx, a.id, card) >= strikerInit
            ) {
                continue
            }
            if (ctx.distance(from, at) > ctx.attackReach(a.id, c).coerceAtLeast(1)) continue
            val total = c.attack + ctx.itemBonus(a.id, Stat.ATTACK)
            val gain = ctx.teamBaseStrike(target, total, c.ranged, 1) - ctx.teamBaseStrike(target, total, c.ranged, 0)
            best = max(best, gain)
        }
        if (best <= 0.0 && unknown) return null
        return (1.0 - chance) * best
    }

    /** [a] holds an attack (hand or facedown) that could reach [at] with a step: the old constant's test. */
    private fun reaches(ctx: HContext, a: HeroState, at: dev.quinton.goa.core.model.board.Hex): Boolean {
        val from = a.at ?: return false
        return (a.hand + a.unresolved).map { ctx.card(it.id) }.any { c ->
            c.attackCard && ctx.distance(from, at) <= ctx.attackReach(a.id, c) + 2
        }
    }

    /**
     * Every teammate who still acts this turn has a card the team knows, so a card play's look (`team.order`)
     * plays them all: a static guess at who joins whom (G14, G15) would count them twice.
     */
    fun covered(ctx: HContext): Boolean {
        if (!order(ctx.profile)) return false
        val s = ctx.state
        return ctx.allies().all { a ->
            a.at == null || a.id in s.turnRecord.resolved || knownCard(ctx, a) != null ||
                (!a.hasUnresolved && !ctx.playsThisTurn(a))
        }
    }

    /**
     * G64 inside a card play's look (before the reveal, `team.order`): a computer teammate still to act holds a known
     * attack card whose only enemy minion in reach is [minionId], and the question offers another enemy minion.
     * Leave it to them: [CLAIMED] (UnitRules' one call into team play).
     */
    fun claimed(ctx: HContext, d: Decision, minionId: String): Double {
        val s = ctx.state
        if (s.revealed || !order(ctx.profile)) return 0.0
        val m = s.minion(minionId) ?: return 0.0
        if (m.team == ctx.team || m.at == null) return 0.0
        val others = d.options.any { o ->
            o is Choice.Unit && o.unitId != minionId && s.minion(o.unitId)?.let { it.team == m.team } == true
        }
        if (!others) return 0.0
        val computers = computers(ctx)
        val taken = ctx.allies().any { a ->
            val from = a.at ?: return@any false
            if (a.id !in computers || a.id in s.turnRecord.resolved || a.id == s.acting) return@any false
            val card = knownCard(ctx, a)?.let { ctx.card(it) } ?: return@any false
            if (!card.attackCard) return@any false
            val reach = ctx.attackReach(a.id, card).coerceAtLeast(1)
            val inReach = s.minions.filter { e ->
                e.team != ctx.team && e.at != null && !ctx.heavyImmune(e.id) && ctx.distance(from, e.at!!) <= reach
            }
            inReach.size == 1 && inReach[0].id == minionId
        }
        return if (taken) CLAIMED else 0.0
    }

    private const val CLAIMED = 8.0

    // ---------------------------------------------------------------------------------------------
    // Two-turn kills (G116, G117): a finisher for turn 4.
    // ---------------------------------------------------------------------------------------------

    fun reserveOn(p: HeuristicProfile): Boolean = p.twoTurnPlans && on(p, "reserve", 9)

    /**
     * Cards [e] holds when turn 4 starts, from public counts: its hand, less this turn's card if it has not
     * played it yet, less one card for each turn between this one and turn 4.
     */
    fun cardsAtTurn4(ctx: HContext, e: HeroState): Int {
        val thisTurn = if (ctx.playsThisTurn(e)) 1 else 0
        return e.handSize - thisTurn - (3 - ctx.turn).coerceAtLeast(0)
    }

    /** Enemies that start turn 4 with at most one card: they play it and any attack that reaches defeats them. */
    fun emptyOnTurn4(ctx: HContext): List<HeroState> {
        if (ctx.turn !in 2..3 || ctx.roundOver) return emptyList()
        return ctx.enemies().filter { e -> e.at != null && cardsAtTurn4(ctx, e) <= 1 && !ctx.defenseless(e.id) }
    }

    /** One of [cards] is an attack (or a discard-or-defeat) that can reach [e] on turn 4 from [heroId]'s space. */
    private fun finishes(ctx: HContext, heroId: String, cards: List<HCard>, e: HeroState): Boolean {
        val from = ctx.state.hero(heroId)?.at ?: return false
        val to = e.at ?: return false
        val slack = if (ctx.turn == 2) 3 else 2
        return cards.any { c ->
            (c.attackCard || c.discardOrDefeat) &&
                ctx.distance(from, to) <= ctx.attackReach(heroId, c).coerceAtLeast(1) + slack
        }
    }

    /**
     * A computer teammate (not [ctx]'s seat) who still holds a turn-4 finisher for [e]: an attack in its hand
     * that it will not spend this turn (its known card this turn left out). A human teammate's hand is not
     * counted (its card this turn is hidden).
     */
    fun teammateFinisher(ctx: HContext, e: HeroState): Boolean {
        val computers = computers(ctx)
        return ctx.allies().any { a ->
            if (a.id !in computers) return@any false
            val keeps = a.hand.map { ctx.card(it.id) }
            // A teammate still to choose this turn spends one card first: at least one other must stay.
            if (ctx.playsThisTurn(a) && keeps.size < 2) return@any false
            finishes(ctx, a.id, keeps, e)
        }
    }

    /** Someone on the team will hold a turn-4 finisher for [e], our hand after playing [card] included. */
    fun finisherFor(ctx: HContext, e: HeroState, card: HCard?): Boolean {
        val mine = ctx.ownHand().filter { it.id != card?.id }
        return finishes(ctx, ctx.seat, mine, e) || teammateFinisher(ctx, e)
    }

    /**
     * G116 (`team.reserve`, from 9): playing [card] now spends the team's last turn-4 finisher for an enemy that
     * arrives at turn 4 empty-handed ([emptyOnTurn4]). The kill it gives up, at the chance it lands (the enemy
     * may leave first, or reach may fall short): [RESERVE_SHARE] of the kill. A second finisher is still worth a
     * little ([SPARE_FINISHER]): they may flee one of us.
     */
    fun reserve(ctx: HContext, card: HCard): Double {
        if (!reserveOn(ctx.profile) || ctx.own()?.at == null) return 0.0
        if (!card.attackCard && !card.discardOrDefeat) return 0.0
        var cost = 0.0
        for (e in emptyOnTurn4(ctx)) {
            if (!finishes(ctx, ctx.seat, ctx.ownHand(), e)) continue
            val mine = ctx.ownHand().filter { it.id != card.id }
            if (finishes(ctx, ctx.seat, mine, e)) continue
            val share = if (teammateFinisher(ctx, e)) {
                ctx.profile.knob("team.spareFinisher", SPARE_FINISHER)
            } else {
                ctx.profile.knob("team.reserveShare", RESERVE_SHARE)
            }
            cost = max(cost, share * ctx.killWorth(e.id))
        }
        return if (cost > 0.0) -cost else 0.0
    }

    // ---------------------------------------------------------------------------------------------
    // Joint card choice (G115, `team.joint`).
    // ---------------------------------------------------------------------------------------------

    fun jointOn(p: HeuristicProfile): Boolean =
        p.focusFire && p.lookahead >= HeuristicProfile.Look.ACTIONS && on(p, "joint", 8) && order(p)

    /**
     * The computer teammate still to choose its card this turn that this card play plans with: the nearest one
     * to us (3v3 bounds the pairs to one partner; the next teammate asked plans with the one after it). Null when
     * none (the teammate asked last best-responds to the cards already down).
     */
    fun partner(ctx: HContext): HeroState? {
        if (ctx.state.revealed) return null
        val own = ctx.own() ?: return null
        val computers = computers(ctx)
        return ctx.allies().filter { a ->
            a.id in computers && !a.hasUnresolved && a.handSize > 0 && ctx.playsThisTurn(a)
        }.minWithOrNull(
            compareBy<HeroState>({ a ->
                val x = a.at
                val y = own.at
                if (x != null && y != null) ctx.distance(x, y) else 99
            }, { it.seat }),
        )
    }

    /**
     * G115: our card values when a teammate still chooses after us. Our best [JOINT_K] cards (by the one-card
     * [values]) are each played with the teammate's best [JOINT_K] replies (by its own static book), the pair
     * resolved in initiative order ([CardSim.value] with the teammate's card assumed facedown); a card's value is
     * that of its best pair. The other cards are left out. The teammate asked after us sees our card and
     * best-responds with its own look, so it lands on the same plan when the books agree ([JOINT_R] replies).
     * Returns the indices
     * of [d]'s options to choose among and their values, or null when there is no partner.
     */
    fun joint(
        ctx: HContext,
        d: Decision,
        values: DoubleArray,
        extra: (Int) -> Double,
    ): Pair<List<Int>, DoubleArray>? {
        if (!jointOn(ctx.profile) || d.options.size < 2) return null
        val b = partner(ctx) ?: return null
        val k = ctx.profile.knob("team.jointK", JOINT_K.toDouble()).toInt().coerceAtLeast(1)
        val r = ctx.profile.knob("team.jointR", JOINT_R.toDouble()).toInt().coerceAtLeast(1)
        val replies = replies(ctx, d, b).take(r)
        if (replies.size < 1) return null
        val ours = values.indices.sortedByDescending { values[it] }.take(k)
        val out = DoubleArray(ours.size)
        for ((j, i) in ours.withIndex()) {
            val card = (d.options[i] as Choice.Card).cardId
            var best = Double.NEGATIVE_INFINITY
            for (r in replies) {
                val v = CardSim.value(ctx, card, mapOf(b.id to r)) ?: continue
                if (v > best) best = v
            }
            out[j] = if (best == Double.NEGATIVE_INFINITY) values[i] else best + extra(i)
        }
        return ours to out
    }

    /** [b]'s cards in the order its own static book ranks them for this turn (from our team's view). */
    private fun replies(ctx: HContext, d: Decision, b: HeroState): List<String> {
        val pool = b.hand.map { it.id }
        if (pool.size <= 1) return pool
        val q = Decision(
            id = d.id,
            seat = b.id,
            team = b.team,
            kind = DecisionKind.PLAY_CARD,
            prompt = "partner",
            options = pool.map { Choice.Card(it) },
        )
        val hc = HContext(ctx.session, q, b.team, ctx.profile, ctx.playbooks, seen = ctx.seen, finishing = true)
        val book = if (ctx.profile.heroPlaybooks) ctx.playbooks(b.id) else DefaultHeroHeuristics
        val scores = pool.map { id ->
            val o = Choice.Card(id)
            try {
                General.score(hc, q, o) + book.adjust(hc, q, o)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                0.0
            }
        }
        return pool.indices.sortedByDescending { scores[it] }.map { pool[it] }
    }

    /** G116: share of the kill a missing turn-4 finisher gives up, and with a teammate's finisher still in hand. */
    private const val RESERVE_SHARE = 0.4
    private const val SPARE_FINISHER = 0.1

    /** G115: our cards, and the partner's replies, looked at in pairs (3 × 2: the card play's cost about doubles). */
    private const val JOINT_K = 3
    private const val JOINT_R = 2
}
