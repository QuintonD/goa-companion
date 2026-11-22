package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.NebkherHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Nebkher copies a card from his own space, on a later turn. Turn 1 has no previous slot, so
 * gold's copy cannot start and the minion bullet is the only other primary. Illusions are how
 * he gets into range 5 and how red A tags a hero.
 */
object NebkherHeuristics : HeroHeuristics {
    override val heroId: String = "nebkher"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 4,
        aggression = 0.35,
        laneWeight = 0.85,
        upgradeOrder = listOf("nebkher-green-2a", "nebkher-red-2b", "nebkher-blue-2b", "green", "red", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> colour(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> if (d.prompt.startsWith("Place an Illusion")) site(ctx, choice) else 0.0
        DecisionKind.CHOOSE_TOKEN -> if (d.prompt.startsWith(SWAP_PROMPT)) swap(ctx, choice) else 0.0
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchBSupport.sign(state, team, heroId)
        val illusions = BatchBSupport.tokens(state, heroId, NebkherHero.ILLUSION)
        val me = state.hero(heroId) ?: return 0.0
        val hero = me.at
        var value = 0.0
        if (hero != null) {
            val beside = illusions.count { token ->
                state.heroes.any { enemy ->
                    enemy.team != me.team &&
                        enemy.at?.let {
                            HexGrid.distance(it, token.at!!) == 1 && HexGrid.distance(hero, it) <= RED_A_RANGE
                        } == true
                }
            }
            // Research §evalTerms: an Illusion beside an enemy hero in his red range is the red A target, +5
            // while red A is in hand; else it is the green B modifier and a teleport, the old +1.5.
            val redA = me.cards.any { it.id in RED_A && it.zone == Zone.HAND }
            value += beside * if (redA) TAG else 1.5
        }
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        var threat = 0.0
        if (ctx.distance(from, target) <= 5 && ctx.turn >= 2 &&
            BatchBSupport.hold(ctx, heroId) { it.id == "nebkher-gold" } > 0.2
        ) {
            // threatAdjust: copy range, not an attack of 5. A small extra so the halo is not ignored.
            threat += 1.0
        }
        if (BatchBSupport.besideToken(ctx.state, target, NebkherHero.ILLUSION) &&
            ctx.distance(from, target) <= RED_A_RANGE
        ) {
            // threatAdjust: red A walks an Illusion next to a hero in range 5 and hits them.
            threat += 2.0 * BatchBSupport.hold(ctx, heroId) { it.id == "nebkher-red-2a" || it.id == "nebkher-red-3a" }
        }
        return threat
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        // H9: the named colour is discarded next turn, which never crosses into the next round.
        val lateName = if (id in NAMING && ctx.turn >= LAST_TURN) -2.0 else 0.0
        return gold(ctx, id) + green(ctx, id) + laugh(ctx, id) + lateName
    }

    // H1: from turn 2, copy a faceup previous-slot card of an enemy in range 5; else defeat an adjacent minion.
    // On turn 1 there is no previous slot, so only the minion bullet exists.
    private fun gold(ctx: HContext, id: String): Double {
        // The card look resolves the copy and the minion bullet itself.
        if (id != "nebkher-gold" || !ctx.patches) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val range = ctx.card(id).range + ctx.itemBonus(ctx.seat, Stat.RANGE)
        val copy = ctx.enemies().maxOfOrNull { enemy ->
            val pos = enemy.at ?: return@maxOfOrNull 0.0
            if (ctx.distance(at, pos) > range) 0.0 else copyWorth(ctx, enemy.id)
        } ?: 0.0
        val minion = BatchBSupport.bestMinion(ctx, at, 1) * 0.7
        val best = maxOf(copy, minion)
        return if (best > 0.0) best else -2.0
    }

    // H3: out of range, the illusion place is the turn. It is also his movement.
    private fun green(ctx: HContext, id: String): Double {
        if (!id.startsWith("nebkher-green")) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val inRange = ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(at, it) <= 5 } == true }
        return if (inRange < 2 || ctx.turn == 1) 2.0 else 0.0
    }

    // H6: with the ultimate, the laugh itself discards everyone in radius 5.
    private fun laugh(ctx: HContext, id: String): Double {
        if (id != "nebkher-silver") return 0.0
        val hero = ctx.own() ?: return 0.0
        if (!hero.ultimateUnlocked) return 0.0
        val at = hero.at ?: return 0.0
        val victims = ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(at, it) <= 5 } == true }
        return if (victims >= 2) 12.0 else -4.0
    }

    // H9: name the colour the heroes who will be in radius next turn most likely still hold after they play.
    // Prefer a colour that carries a good defense: the card they lose is then the shield.
    private fun colour(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in NAMING || !d.prompt.startsWith("Name a color")) return 0.0
        val named = (choice as? Choice.Option)?.label?.let(::colourOf) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val radius = ctx.card(d.sourceCardId!!).radius + ctx.itemBonus(ctx.seat, Stat.RADIUS) + 2
        var value = 0.0
        for (enemy in ctx.enemies()) {
            val pos = enemy.at ?: continue
            if (ctx.distance(at, pos) > radius) continue
            // One card is played next turn; the named colour survives that with about (n-1)/n.
            val n = ctx.handCount(enemy.id)
            if (n <= 1) continue
            val keep = (n - 1).toDouble() / n
            value += keep * BatchBSupport.hold(ctx, enemy.id) { it.color == named } *
                (2.5 + 0.2 * (ctx.possibleCards(enemy.id).firstOrNull { it.card.color == named }?.card?.defense ?: 0))
        }
        return value
    }

    /**
     * Research rule 3 / §5.3: where an Illusion goes. Beside an enemy hero in his red range it is the red A
     * target (and green B's modifier); two steps from one, red A walks it in. As a teleport it is worth the
     * enemies it puts in Mind Grip's range 5 beyond those his own space has. The card look then takes the
     * best of these sites, and [evalTerms] prices what is left on the board.
     */
    private fun site(ctx: HContext, choice: Choice): Double {
        val hex = (choice as? Choice.Space)?.hex ?: return 0.0
        val me = ctx.own()?.at ?: return 0.0
        var v = 0.0
        for (enemy in ctx.enemies()) {
            val pos = enemy.at ?: continue
            if (ctx.distance(me, pos) > RED_A_RANGE) continue
            v += when (ctx.distance(hex, pos)) {
                1 -> SITE_BESIDE
                2 -> SITE_NEAR
                else -> 0.0
            }
        }
        v += (inGoldRange(ctx, hex) - inGoldRange(ctx, me)).coerceIn(0, 3) * SITE_RANGE
        if (ctx.inBattleZone(hex)) v += SITE_ZONE
        return v
    }

    /** "You may swap with an Illusion token in play": the space it puts him on, against staying. */
    private fun swap(ctx: HContext, choice: Choice): Double {
        val id = (choice as? Choice.Token)?.tokenId ?: return 0.0
        val to = ctx.state.piece(id)?.at ?: return 0.0
        val me = ctx.own()?.at ?: return 0.0
        val range = (inGoldRange(ctx, to) - inGoldRange(ctx, me)) * SITE_RANGE
        val danger = (ctx.exposure(ctx.seat, to) - ctx.exposure(ctx.seat, me)) * SWAP_DANGER
        return range - danger
    }

    private fun inGoldRange(ctx: HContext, at: Hex): Int =
        ctx.enemies().count { enemy -> enemy.at?.let { ctx.distance(at, it) <= GOLD_RANGE } == true }

    /** What copying [enemyId]'s faceup previous-slot card is worth from here (attacks and discards). */
    private fun copyWorth(ctx: HContext, enemyId: String): Double {
        val card = BatchBSupport.previous(ctx, enemyId) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            card.attackCard || card.forcesDiscard -> ctx.strikeFrom(ctx.seat, at, card).coerceIn(0.0, 10.0) * 0.6
            card.skillCard -> 1.5
            else -> 0.0
        }
    }

    private const val RED_A_RANGE = 5
    private const val SWAP_PROMPT = "You may swap with an Illusion"
    private const val GOLD_RANGE = 5
    private val RED_A = setOf("nebkher-red-1", "nebkher-red-2a", "nebkher-red-3a")

    /** An Illusion beside an enemy hero in red range while red A is in hand (research §evalTerms: +5). */
    private const val TAG = 5.0

    /** Placement: beside an enemy hero in red range; two steps away (red A walks it in). */
    private const val SITE_BESIDE = 3.0
    private const val SITE_NEAR = 1.0

    /** Placement or swap: each enemy it adds to Mind Grip's range (research: +4 each, cap 12; halved). */
    private const val SITE_RANGE = 1.5

    /** Placement in a Battle Zone: a teleport into the fight. */
    private const val SITE_ZONE = 0.5

    /** Share of the extra exposure a swap walks into that counts against it. */
    private const val SWAP_DANGER = 0.5
    private const val LAST_TURN = 4
    private val NAMING = setOf("nebkher-blue-1", "nebkher-blue-2a", "nebkher-blue-3a")

    private fun colourOf(label: String): CardColor? = when {
        label.contains("Gold", ignoreCase = true) -> CardColor.GOLD
        label.contains("Silver", ignoreCase = true) -> CardColor.SILVER
        label.contains("Red", ignoreCase = true) -> CardColor.RED
        label.contains("Green", ignoreCase = true) -> CardColor.GREEN
        label.contains("Blue", ignoreCase = true) -> CardColor.BLUE
        else -> null
    }
}
