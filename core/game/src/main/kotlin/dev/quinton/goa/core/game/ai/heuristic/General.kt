package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.MinionState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.upgradesOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * The general playbook (ADR-085, STRATEGY §3). Every [DecisionKind] gets a score in
 * coin-equivalents. A hero playbook adds to it; it does not replace it unless it overrides.
 * One-ply lookahead is the caller's job: these scores do not fork.
 */
object General {
    fun scores(ctx: HContext, d: Decision): DoubleArray = DoubleArray(d.options.size) { score(ctx, d, d.options[it]) }

    fun best(ctx: HContext, d: Decision): Choice {
        val s = scores(ctx, d)
        var bi = 0
        for (i in 1 until s.size) if (s[i] > s[bi]) bi = i
        return d.options[bi]
    }

    fun score(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> playCard(ctx, choice)
        DecisionKind.CHOOSE_ACTION -> action(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE, DecisionKind.MINION_RETURN -> space(ctx, d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.CHOOSE_CARD -> cardChoice(ctx, d, choice)
        DecisionKind.REMOVE_MINION -> remove(ctx, choice)
        DecisionKind.UPGRADE_CARD -> upgrade(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(ctx, d, choice)
        DecisionKind.CHOOSE_TOKEN -> token(ctx, choice)
        DecisionKind.ACT_FIRST -> actFirst(ctx, choice)
        DecisionKind.CONFIRM -> if (choice is Choice.Yes) 0.35 else 0.0
        DecisionKind.CHOOSE_NUMBER -> if (choice is Choice.Number) choice.value * 0.2 else 0.0
        DecisionKind.CHOOSE_OPTION -> 0.0
    }

    // ---- card ---------------------------------------------------------------------------

    /** Gold, green and silver first; red attacks late; blue is the spare (STRATEGY §1.5, §3.1). */
    private fun playCard(ctx: HContext, o: Choice): Double {
        if (o is Choice.Pass) return 0.0
        if (o !is Choice.Card) return 0.0
        val h = ctx.own() ?: return 0.0
        val card = ctx.card(o.cardId)
        val at = h.at ?: return -card.rating
        var v = colourTempo(card, ctx.turn)
        val outlook = outlook(ctx, h, at, card)
        v += outlook
        v += safety(ctx, h, at, card)
        v += winNow(ctx, h, at, card)
        v += tieBreaker(ctx, h, card)
        // Keep the big attack for a kill or a push. Spending it on turn 1 for an ordinary minion loses the lane later.
        if (ctx.turn <= 2 && card.color == CardColor.RED && card.attackCard && outlook < 12.0) v -= 3.5
        return v
    }

    private fun colourTempo(card: HCard, turn: Int): Double = when (card.color) {
        CardColor.BLUE -> when {
            turn >= 4 -> 0.8
            turn == 3 -> -0.15
            else -> -1.35
        }

        CardColor.RED -> when {
            turn <= 1 -> -0.95
            turn == 2 -> -0.1
            else -> 0.5
        }

        CardColor.GOLD -> if (turn <= 2) 0.4 else 0.15

        CardColor.GREEN, CardColor.SILVER -> if (turn <= 2) 0.55 else 0.15

        else -> 0.0
    }

    private fun outlook(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        val strike = if (card.attackCard || card.forcesDiscard) ctx.strikeFrom(h.id, at, card) else 0.0
        val move = if (card.hasMovement) moveOutlook(ctx, at, card) else 0.0
        val skill = if (card.skillCard && !card.attackCard) skillOutlook(ctx, at, card) else 0.0
        var v = max(strike, max(move, skill))
        if (strike > 0.5 && move > 0.3) v += 0.35
        return v
    }

    private fun moveOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val far = ctx.distanceToBattleZone(at)
        if (far <= 0) return 0.15
        return min(far * 0.4, card.movement * 0.55)
    }

    private fun skillOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val r = max(card.range, card.radius) + 1
        val nearHero = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) <= r } == true }
        val nearMinion = ctx.state.minions.any { m ->
            m.team != ctx.team && m.at?.let { ctx.distance(at, it) <= r } == true
        }
        return if (nearHero || nearMinion) 1.4 else 0.3
    }

    private fun safety(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        var v = 0.0
        val exposure = ctx.exposure(h.id, at)
        val bestDef = ctx.ownHand().filter { it.canDefend }.maxOfOrNull { it.defense ?: 0 } ?: 0
        if (card.canDefend && (card.defense ?: 0) >= bestDef && ctx.spare(h.id) <= 1 && ctx.turn < 4) {
            val hold = if (ctx.style(h.id).keepDefender) 1.4 else 0.55
            v -= hold + min(5.0, exposure * 0.22)
        }
        if (exposure > 6.0 && card.hasMovement) v += escape(ctx, h, at, card, exposure)
        return v
    }

    private fun escape(ctx: HContext, h: HeroState, at: Hex, card: HCard, exposure: Double): Double {
        if (!ctx.profile.initiativeReading) return card.initiative * 0.06
        val incoming = ctx.incomingInitiative(at) ?: return min(2.0, exposure * 0.08)
        val ours = card.initiative + ctx.itemBonus(h.id, Stat.INITIATIVE)
        return when {
            ours > incoming -> 4.8
            ours == incoming && ctx.state.tieBreaker == ctx.team -> 2.2
            else -> -0.3
        }
    }

    /** A card that takes the last minion at match point, or a hero with nothing to defend with. */
    private fun winNow(ctx: HContext, h: HeroState, at: Hex, card: HCard): Double {
        if (!card.attackCard && !card.forcesDiscard) return 0.0
        val reach = ctx.attackReach(h.id, card)
        var v = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || ctx.heavyImmune(m.id) || ctx.distance(at, pos) > reach) continue
            if (ctx.minionCount(m.team, m.lane) == 1 && ctx.matchPoint(m.lane)) v = max(v, 6.0)
        }
        return v
    }

    private fun tieBreaker(ctx: HContext, h: HeroState, card: HCard): Double {
        if (!ctx.profile.initiativeReading) return 0.0
        val at = h.at ?: return 0.0
        val incoming = ctx.incomingInitiative(at) ?: return 0.0
        val ours = card.initiative + ctx.itemBonus(h.id, Stat.INITIATIVE)
        return if (ours == incoming && ctx.state.tieBreaker != ctx.team) -0.9 else 0.0
    }

    // ---- action, space, unit -------------------------------------------------------------

    private fun action(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Action) return 0.0
        val h = ctx.own() ?: return 0.0
        val at = h.at ?: return if (o.action == ActionType.HOLD) 0.0 else -1.0
        val cardId = d.sourceCardId ?: h.unresolved.firstOrNull()?.id ?: return 0.0
        val card = ctx.card(cardId)
        val here = ctx.placement(h.id, at)
        val base = when (o.action) {
            ActionType.HOLD -> 0.0
            ActionType.MOVEMENT -> bestMove(ctx, h.id, at, cardId) - here
            ActionType.FAST_TRAVEL -> bestTravel(ctx, h.id, here, card)
            ActionType.PRIMARY -> primary(ctx, h.id, at, card, here)
            ActionType.ATTACK -> ctx.strikeFrom(h.id, at, card)
            ActionType.CLEAR -> clearValue(ctx, at)
        }
        return base + planBonus(ctx, o.action)
    }

    private fun bestMove(ctx: HContext, heroId: String, at: Hex, cardId: String): Double {
        val dests = ctx.movementDestinations(heroId, cardId)
        if (dests.isEmpty()) return ctx.placement(heroId, at)
        return dests.maxOf { ctx.placement(heroId, it) }
    }

    private fun bestTravel(ctx: HContext, heroId: String, here: Double, card: HCard): Double {
        val dests = ctx.fastTravelDestinations(heroId)
        val best = dests.maxOfOrNull { ctx.placement(heroId, it) } ?: here
        val tax = if (card.color == CardColor.RED || card.color == CardColor.GOLD) 1.7 else 0.25
        return best - here - tax
    }

    private fun primary(ctx: HContext, heroId: String, at: Hex, card: HCard, here: Double): Double = when {
        card.attackCard || card.forcesDiscard -> ctx.strikeFrom(heroId, at, card) + 0.45
        card.hasMovement && card.movement > 0 -> bestMove(ctx, heroId, at, card.id) - here + 0.4
        else -> skillOutlook(ctx, at, card) + 0.2
    }

    private fun clearValue(ctx: HContext, at: Hex): Double {
        var v = 0.15
        for (p in ctx.state.pieces) {
            val pos = p.at ?: continue
            if (!p.token || ctx.distance(at, pos) != 1) continue
            val owner = p.team ?: p.owner?.let { ctx.state.hero(it)?.team }
            if (owner != null && owner != ctx.team) v += 0.9
        }
        return v
    }

    private fun planBonus(ctx: HContext, action: ActionType): Double {
        if (!ctx.profile.turnPlans) return 0.0
        val serves = when (ctx.plan.intent) {
            Intent.KILL, Intent.FARM, Intent.PUSH, Intent.CLEAR_MINION ->
                action == ActionType.PRIMARY || action == ActionType.ATTACK

            Intent.FAST_TRAVEL -> action == ActionType.FAST_TRAVEL

            Intent.RETREAT, Intent.RESPAWN ->
                action == ActionType.MOVEMENT || action == ActionType.FAST_TRAVEL

            Intent.SET_UP -> action == ActionType.PRIMARY

            Intent.DEFEND_LANE -> action == ActionType.MOVEMENT || action == ActionType.HOLD
        }
        return if (serves) 2.2 else 0.0
    }

    private fun space(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Space) return if (o is Choice.Decline) -0.45 else 0.0
        val subject = d.context.subjectId ?: d.seat
        val body = ctx.heroBody(subject)
        if (body != null) {
            val owner = ctx.state.hero(body)?.team ?: return 0.0
            val sign = if (owner == ctx.team) 1.0 else -1.0
            return sign * ctx.placement(body, o.hex)
        }
        val minion = ctx.state.minion(subject) ?: return 0.0
        return minionSpace(ctx, minion, o.hex)
    }

    /** Where a minion should stand: the defense it gives or takes from the heroes around it. */
    private fun minionSpace(ctx: HContext, m: MinionState, hex: Hex): Double {
        var v = 0.0
        for (h in ctx.state.heroes) {
            val at = h.at ?: continue
            val dist = ctx.distance(at, hex)
            val effect = when {
                m.type != MinionType.RANGED && dist == 1 -> if (m.team == h.team) 1.0 else -1.0
                m.type == MinionType.RANGED && dist in 1..2 && m.team != h.team -> -1.0
                else -> 0.0
            }
            v += if (h.team == ctx.team) effect else -effect
            if (m.team != ctx.team && h.team == ctx.team && dist == 1) v += 0.35
        }
        return v
    }

    private fun respawn(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Space) {
            val spaces = d.options.filterIsInstance<Choice.Space>()
            val camped = spaces.isNotEmpty() && ctx.profile.threatMap && spaces.all {
                ctx.exposure(ctx.seat, it.hex) > ctx.killWorth(ctx.seat) * 0.75
            }
            return if (camped) 4.0 else -8.0
        }
        return ctx.placement(ctx.seat, o.hex)
    }

    private fun unit(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Unit) return if (o is Choice.Decline) -0.4 else 0.0
        var v = unitValue(ctx, d, o.unitId)
        if (ctx.profile.turnPlans && o.unitId == ctx.plan.targetId) v += 1.8
        return v
    }

    private fun unitValue(ctx: HContext, d: Decision, id: String): Double {
        ctx.state.minion(id)?.let { return ctx.minionWorth(id) }
        val body = ctx.heroBody(id) ?: return 0.4
        val src = d.sourceCardId?.let { ctx.card(it) }
        val total = (src?.attack ?: 0) + ctx.itemBonus(d.seat, Stat.ATTACK)
        return ctx.heroStrikeWorth(body, total, src?.ranged == true)
    }

    // ---- defend, discard, remove ---------------------------------------------------------

    private fun defend(ctx: HContext, d: Decision, o: Choice): Double {
        val total = d.context.attackTotal ?: 0
        val ranged = d.context.rangedAttack == true
        val sure = d.options.filterIsInstance<Choice.Card>()
            .filter { holds(ctx, d, it.cardId, total, ranged) }
            .map { it.cardId }
            .toSet()
        if (o !is Choice.Card) return decline(ctx, sure)
        val keep = keepCost(ctx, d.seat, o.cardId)
        val preview = d.context.defensePreview[o.cardId]
        val card = ctx.card(o.cardId)
        return when {
            o.cardId in sure && sacrifice(ctx, sure) && card.color == CardColor.RED -> -1.0
            o.cardId in sure -> 24.0 - keep
            preview == null && !card.blocksRanged && !card.blocksMelee -> 2.5 - keep * 0.15
            else -> -18.0 - keep
        }
    }

    private fun decline(ctx: HContext, sure: Set<String>): Double = when {
        sure.isEmpty() -> 0.5
        sacrifice(ctx, sure) -> 2.0
        else -> -30.0
    }

    /** A level 1–3 hero at match-point timing may keep the Red and take the defeat (STRATEGY §1.10). */
    private fun sacrifice(ctx: HContext, sure: Set<String>): Boolean {
        if (!ctx.profile.twoTurnPlans || sure.size != 1) return false
        val h = ctx.own() ?: return false
        if (h.level > 3 || ctx.life(h.team) < 4 || ctx.turn < 3 || ctx.playsLeft(h.id) <= 0) return false
        val card = ctx.card(sure.single())
        return card.color == CardColor.RED && card.attackCard
    }

    private fun holds(ctx: HContext, d: Decision, cardId: String, total: Int, ranged: Boolean): Boolean {
        if (d.context.blockPreview[cardId] == true) return true
        val preview = d.context.defensePreview[cardId]
        if (preview != null) return preview >= total
        val card = ctx.card(cardId)
        return (ranged && card.blocksRanged) || (!ranged && card.blocksMelee)
    }

    private fun keepCost(ctx: HContext, seat: String, cardId: String): Double {
        val card = ctx.card(cardId)
        var v = card.rating
        if (ctx.playsLeft(seat) > 0 && card.attackCard) v += 2.2
        if (card.color == CardColor.BLUE && ctx.turn < 4 && ctx.style(seat).keepDefender) v += 0.8
        return v
    }

    private fun cardChoice(ctx: HContext, d: Decision, o: Choice): Double {
        val h = ctx.own()
        if (o !is Choice.Card) {
            val fromHand = d.options.filterIsInstance<Choice.Card>().all { h?.card(it.cardId)?.zone == Zone.HAND }
            return if (fromHand) 0.15 else -2.0
        }
        val zone = h?.card(o.cardId)?.zone
        val rating = ctx.card(o.cardId).rating
        return if (zone == Zone.HAND || zone == null) -rating else rating
    }

    /** Remove the minion the enemy would kill anyway. A removal pays no coins; a kill pays two. */
    private fun remove(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val m = ctx.state.minion(o.unitId) ?: return 0.0
        val at = m.at ?: return 0.0
        val enemyDist = ctx.state.heroes.filter { it.team != m.team && it.at != null }
            .minOfOrNull { ctx.distance(it.at!!, at) } ?: 9
        var v = -enemyDist.toDouble()
        val guards = ctx.state.heroes.any { hero ->
            hero.team == m.team && hero.at?.let { ctx.distance(it, at) == 1 } == true
        }
        if (guards && m.type != MinionType.RANGED) v -= 2.2
        val zonesEnemy = ctx.state.heroes.any { hero ->
            hero.team != m.team && hero.at?.let { ctx.distance(it, at) in 1..2 } == true
        }
        if (m.type == MinionType.RANGED && zonesEnemy) v -= 3.0
        return v
    }

    private fun actFirst(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val card = ctx.state.hero(o.unitId)?.unresolved?.firstOrNull()?.let { ctx.card(it.id) } ?: return 0.0
        var v = 0.0
        if (card.forcesDiscard) v += 2.0
        if (card.attackCard) v += 0.9
        if (card.hasMovement && !card.attackCard) v -= 0.35
        return v
    }

    private fun token(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Token) return if (o is Choice.Decline) -0.15 else 0.0
        val p = ctx.state.piece(o.tokenId) ?: return 0.0
        val owner = p.team ?: p.owner?.let { ctx.state.hero(it)?.team }
        return if (owner != null && owner != ctx.team) 0.85 else -0.45
    }

    // ---- level up ------------------------------------------------------------------------

    private fun upgrade(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val h = ctx.own() ?: return 0.0
        val next = ctx.session.engineData.upgradesOf(h, o.cardId)
        if (next.isEmpty()) return -5.0
        val best = next.maxOf { kept -> keepValue(ctx, d.seat, kept, next - kept) }
        return best - ctx.card(o.cardId).rating + upgradeBias(ctx, d.seat, o.cardId)
    }

    private fun keep(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val others = d.options.filterIsInstance<Choice.Card>().map { it.cardId } - o.cardId
        return keepValue(ctx, d.seat, o.cardId, others) + upgradeBias(ctx, d.seat, o.cardId) * 0.25
    }

    private fun keepValue(ctx: HContext, seat: String, keep: String, items: List<String>): Double =
        ctx.card(keep).rating + items.sumOf { itemValue(ctx, seat, ctx.card(it).item) }

    /** An item's worth: how many of the hero's round cards carry the matching icon (STRATEGY §1.11). */
    private fun itemValue(ctx: HContext, seat: String, stat: Stat?): Double {
        val h = ctx.state.hero(seat) ?: return 0.0
        val cards = h.cards.filter { it.zone in ROUND_ZONES }.map { ctx.card(it.id) }
        return when (stat) {
            Stat.ATTACK -> cards.count { it.attackCard } * 1.0
            Stat.DEFENSE -> cards.count { it.canDefend && it.defense != null } * 0.7
            Stat.INITIATIVE -> cards.size * 0.35
            Stat.MOVEMENT -> cards.count { it.hasMovement } * 0.35
            Stat.RANGE -> cards.count { it.ranged } * 0.75
            Stat.RADIUS -> cards.count { it.radius > 0 } * 0.4
            null -> 0.0
        }
    }

    private fun upgradeBias(ctx: HContext, seat: String, cardId: String): Double {
        val card = ctx.card(cardId)
        val order = ctx.style(seat).upgradeOrder
        val colour = card.color.name.lowercase()
        val rank = order.indexOfFirst { it.equals(colour, ignoreCase = true) || it == cardId }
        if (rank < 0) return if (card.color == CardColor.RED) 0.35 else 0.0
        return 1.3 - rank * 0.4
    }

    private val ROUND_ZONES = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
}
