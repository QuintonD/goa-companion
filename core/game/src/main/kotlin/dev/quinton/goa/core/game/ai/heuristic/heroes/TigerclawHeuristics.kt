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
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Tigerclaw (`heroes/tigerclaw.md`): an assassin who dashes through a target and leaves. Blink Strike
 * hits only the unit he passes, and only when the far side is empty. Blues block one attack type each
 * (the general book reads that from the engine's block preview, so no DEFEND note is needed here).
 */
object TigerclawHeuristics : HeroHeuristics {
    override val heroId: String = "tigerclaw"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 2,
        aggression = 0.65,
        laneWeight = 0.55,
        upgradeOrder = listOf("green", "red", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(ctx, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        var v = 0.0
        for (hero in state.heroes) {
            val strength = BatchD.poisonStrength(hero)
            if (strength == 0) continue
            // Poison on an enemy taxes the card they answer with. On a friend it is a mistake.
            v += if (hero.team != self.team) 3.0 * strength else -6.0
        }
        // Blend Into Shadows: next turn he is immune to attack actions, which is the free action.
        if (BatchD.effect(state, heroId, setOf(SILVER), pending = true)) v += 3.0
        return BatchD.sign(state, heroId, team) * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        // H18: Blink Strike threatens a unit beside him with an empty hex straight behind it.
        if (ctx.distance(from, target) != 1) return 0.0
        if (!BatchD.mayHold(ctx, heroId, GOLD) && !BatchD.mayHold(ctx, heroId, HANDICAP)) return 0.0
        val landing = Hex(2 * target.q - from.q, 2 * target.r - from.r)
        return if (ctx.game.isEmpty(landing, heroId)) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        return when (id) {
            // H1/H2: Blink hits only a unit with an empty hex straight behind it; no landing, no attack.
            GOLD, HANDICAP -> {
                val real = BatchD.strikeOn(ctx, card, { uid, pos -> blinkable(ctx, at, uid, pos, id == HANDICAP) })
                BatchD.strikeFix(ctx, card, real)
            }

            // H6/H7: Blend needs terrain beside him, and the immunity pays only with a card left to use it.
            SILVER -> silver(ctx, at)

            in STEAL -> steal(ctx, at, id)

            in POISON -> poison(ctx, at, id)

            else -> 0.0
        }
    }

    private fun silver(ctx: HContext, at: Hex): Double {
        if (!BatchD.besideTerrain(ctx, at)) return -1.5
        val follow = ctx.ownHand().any { it.id != SILVER && (it.attackCard || it.id in STEAL) }
        if (!follow) return -2.0
        // H6: worth more when he is standing where an enemy can hit him now.
        return 1.5 + minOf(4.0, ctx.exposure(ctx.seat, at) * 0.3)
    }

    /** H8: the steal needs an enemy hero with a coin within his move plus one. */
    private fun steal(ctx: HContext, at: Hex, id: String): Double {
        val reach = if (id == "tigerclaw-green-1") 2 else 3
        val take = if (id == "tigerclaw-green-3a") 2 else 1
        val self = ctx.own() ?: return 0.0
        var best = -1.5
        for (e in BatchD.enemyHeroesWithin(ctx, at, 1, reach)) {
            if (e.coins <= 0) continue
            val n = minOf(take, e.coins)
            var v = 1.0 * n
            // +1.5 when the coins complete his level, +3 when they cost theirs.
            if (self.coins < self.level && self.coins + n >= self.level) v += 1.5
            if (e.coins >= e.level && e.coins - n < e.level) v += 3.0
            best = maxOf(best, v)
        }
        return best
    }

    /** H12: the marker is worth it on an enemy that someone will still attack. Never a friend. */
    private fun poison(ctx: HContext, at: Hex, id: String): Double {
        val range = ctx.card(id).range.coerceAtLeast(1)
        val enemies = BatchD.enemyHeroesWithin(ctx, at, 1, range)
        if (enemies.isEmpty()) {
            // Only a friend in range is still a legal target, and that is the mistake.
            val friend = ctx.allies().any { a -> a.at?.let { ctx.distance(at, it) in 1..range } == true }
            return if (friend) -8.0 else -1.5
        }
        val attackers = ctx.allies().count { a ->
            enemies.any { e -> a.at != null && ctx.distance(a.at!!, e.at!!) <= 3 }
        }
        val ownAttack = ctx.ownHand().any { it.attackCard && it.id != id }
        return if (attackers > 0 || ownAttack) 2.5 else 0.5
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Unit) return 0.0
        val src = d.sourceCardId ?: return 0.0
        if (src in POISON) {
            val ally = ctx.state.hero(choice.unitId)?.team == ctx.team
            // H12: a friend is a legal target and the wrong one.
            return if (ally) -12.0 else 0.0
        }
        if (src !in BACKSTAB) return 0.0
        val at = ctx.state.hero(choice.unitId)?.at ?: ctx.state.minion(choice.unitId)?.at ?: return 0.0
        // H11: +2 Attack when a friendly unit, never Tigerclaw himself, is adjacent to the target.
        return if (friendlyBeside(ctx, at)) 3.0 else 0.0
    }

    /** H1: the landing picks the target. Score the unit passed through, then a landing away from danger. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val src = d.sourceCardId ?: return 0.0
        if (src != GOLD && src != HANDICAP) return 0.0
        val from = ctx.own()?.at ?: return 0.0
        if (d.context.subjectId != ctx.seat || ctx.distance(from, choice.hex) != 2) return 0.0
        val mid = Hex((from.q + choice.hex.q) / 2, (from.r + choice.hex.r) / 2)
        val unit = ctx.game.occupant(mid) ?: return 0.0
        if (ctx.state.minion(unit) != null) return ctx.minionWorth(unit) * 0.8
        val body = ctx.heroBody(unit) ?: return 0.0
        val card = ctx.card(src)
        return ctx.heroStrikeWorth(body, card.attack + ctx.itemBonus(ctx.seat, Stat.ATTACK), false) * 0.8
    }

    /** H15/H16: the Blue that blocks what they hold; an Initiative item when their Gold ties his 13. */
    private fun keep(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val id = choice.cardId
        var v = 0.0
        val raced = BatchD.enemyGoldInit(ctx) >= 13
        if (raced && id in INITIATIVE_ITEM) v += 2.0
        if (!raced && id == "tigerclaw-green-2a") v += 1.5
        val ranged = BatchD.mostlyRanged(ctx)
        if (ranged && id in RANGED_BLOCK) v += 4.0
        if (!ranged && id in MELEE_BLOCK) v += 4.0
        return v
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        // H21: come back beside terrain so Silver can be played.
        return if (BatchD.besideTerrain(ctx, choice.hex)) 1.0 else 0.0
    }

    /** Blink Strike's line: the unit is adjacent and the hex straight behind it is empty. */
    private fun blinkable(ctx: HContext, from: Hex, unitId: String, pos: Hex, heroOnly: Boolean): Boolean {
        if (ctx.distance(from, pos) != 1) return false
        if (heroOnly && ctx.heroBody(unitId) == null) return false
        val landing = Hex(2 * pos.q - from.q, 2 * pos.r - from.r)
        return ctx.game.isEmpty(landing, ctx.seat)
    }

    private fun friendlyBeside(ctx: HContext, at: Hex): Boolean {
        for (hex in HexGrid.neighbors(at)) {
            val id = ctx.game.occupant(hex) ?: continue
            if (id == ctx.seat) continue
            if (ctx.state.hero(id)?.team == ctx.team) return true
            if (ctx.state.minion(id)?.team == ctx.team) return true
        }
        return false
    }

    private const val GOLD = "tigerclaw-gold"
    private const val HANDICAP = "tigerclaw-handicap"
    private const val SILVER = "tigerclaw-silver"
    private val STEAL = setOf("tigerclaw-green-1", "tigerclaw-green-2a", "tigerclaw-green-3a")
    private val POISON = setOf("tigerclaw-green-2b", "tigerclaw-green-3b")
    private val BACKSTAB = setOf("tigerclaw-red-2b", "tigerclaw-red-3b")
    private val INITIATIVE_ITEM = setOf("tigerclaw-green-2b", "tigerclaw-red-2a", "tigerclaw-green-3b")
    private val RANGED_BLOCK = setOf("tigerclaw-blue-2a", "tigerclaw-blue-3a")
    private val MELEE_BLOCK = setOf("tigerclaw-blue-2b", "tigerclaw-blue-3b")
}
