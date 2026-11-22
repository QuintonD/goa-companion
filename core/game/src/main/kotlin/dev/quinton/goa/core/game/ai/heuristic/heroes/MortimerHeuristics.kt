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
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.heroes.MortimerHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Mortimer's body is slow. The herd does the work: silver raises it, gold spends only the
 * zombies the kill needs, and green B replaces a minion that already has two beside it.
 */
object MortimerHeuristics : HeroHeuristics {
    override val heroId: String = "mortimer"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.48,
        laneWeight = 0.65,
        upgradeOrder = listOf("mortimer-blue-2a", "mortimer-red-2a", "mortimer-green-2b", "blue", "red", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> avoidSpawn(ctx, d, choice)
        DecisionKind.CHOOSE_OPTION -> if (d.sourceCardId in KNIVES) knifeOption(ctx, d, choice) else 0.0
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchBSupport.sign(state, team, heroId)
        val zombies = BatchBSupport.tokens(state, heroId, MortimerHero.ZOMBIE)
        // evalTerms: HEval already counts each zombie as a lasting token; these are the herd's jobs.
        var value = 0.0
        val me = state.hero(heroId) ?: return 0.0
        val hero = me.at
        if (hero != null) {
            val foes = state.heroes.filter { it.team != me.team && it.at != null }
            // Research §evalTerms: a zombie beside an enemy hero inside his radius 4 is a discard or a retrieve.
            val packed = zombies.count { zombie ->
                foes.any { enemy ->
                    HexGrid.distance(enemy.at!!, zombie.at!!) == 1 && HexGrid.distance(hero, enemy.at!!) <= 4
                }
            }
            value += packed * BESIDE
            // Three zombies on a hero he can reach with the knife: Attack 7.
            val knife = me.cards.any { it.id in KNIVES && it.zone == Zone.HAND }
            if (knife) {
                val loaded = foes.any { enemy ->
                    HexGrid.distance(hero, enemy.at!!) <= 2 &&
                        zombies.count { HexGrid.distance(it.at!!, enemy.at!!) == 1 } >= 3
                }
                if (loaded) value += LOADED
            }
        }
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) > 4) return 0.0
        val beside = BatchBSupport.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).any { zombie ->
            HexGrid.distance(zombie.at!!, target) == 1
        }
        // threatAdjust: the red's discard reaches a hero next to a zombie in radius 4, not next to him.
        return if (beside) 2.5 * BatchBSupport.hold(ctx, heroId) { it.id in DISCARD_REDS } else 0.0
    }

    /** The same, with the victim known: the discard is what that card costs them (research: about +6). */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double {
        val plain = threatAdjust(ctx, enemyHeroId, target)
        if (plain <= 0.0) return plain
        val victim = ctx.hero(victimId) ?: return plain
        return plain / 2.5 * ctx.discardWorth(victim)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        return awaken(ctx, id) + knife(ctx, id) + replace(ctx, id) + whiff(ctx, id)
    }

    // H1: raise the herd while it is under two, and not instead of a minion that is already adjacent.
    private fun awaken(ctx: HContext, id: String): Double {
        if (id != "mortimer-silver") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val herd = BatchBSupport.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).size
        if (herd >= 2) return 0.0
        if (BatchBSupport.enemyMinionWithin(ctx, at, 1)) return -2.0
        return if (herd == 0) 5.0 else 2.5
    }

    /**
     * H3: gold is Attack 4 plus one per zombie removed beside the target (up to three). Statically, the kill
     * those zombies add against the hero's defense pool, priced by [HContext.killWorth]. The card look
     * resolves the knife itself (its bullets by [knifeOption]), so there this is 0.
     */
    private fun knife(ctx: HContext, id: String): Double {
        if (id !in KNIVES || ctx.looking) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val total = ctx.card(id).attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val times = if (id == "mortimer-handicap") 2 else 3
        var best = 0.0
        for (enemy in ctx.enemies()) {
            val pos = enemy.at ?: continue
            if (ctx.distance(at, pos) != 1) continue
            val extra = minOf(times, zombiesBeside(ctx, pos))
            if (extra == 0) continue
            val gain = (killChance(ctx, enemy, total + extra) - killChance(ctx, enemy, total)) * ctx.killWorth(enemy.id)
            best = maxOf(best, gain - extra * ZOMBIE)
        }
        return best
    }

    /**
     * The knife's "Choose up to three times" (research rule 3): remove only the zombies the kill needs. On a
     * minion the attack takes it anyway, so keep the herd. On a hero, a removal is worth the kill it adds
     * when the remaining choices and the zombies beside the target reach it; a move is worth half that when
     * a zombie two steps from the target can join.
     */
    private fun knifeOption(ctx: HContext, d: Decision, choice: Choice): Double {
        val label = (choice as? Choice.Option)?.label
        val removing = label?.startsWith("Remove") == true
        val moving = label?.startsWith("Move") == true
        val target = knifeTarget(ctx) ?: return 0.0
        val hero = ctx.hero(target)?.takeIf { it.team != ctx.team }
        if (hero == null) {
            return when {
                removing -> -ZOMBIE - 1.0
                moving -> -0.5
                else -> 0.5
            }
        }
        val pos = hero.at ?: return 0.0
        val src = d.sourceCardId ?: return 0.0
        val total = ctx.card(src).attack + ctx.itemBonus(ctx.seat, Stat.ATTACK) + removed(ctx)
        val left = choicesLeft(d)
        val beside = zombiesBeside(ctx, pos)
        val now = killChance(ctx, hero, total)
        fun best(reach: Int): Double {
            var v = 0.0
            for (k in 1..minOf(left, reach)) {
                v = maxOf(v, (killChance(ctx, hero, total + k) - now) * ctx.killWorth(hero.id) - k * ZOMBIE)
            }
            return v
        }
        return when {
            removing -> best(beside).takeIf { it > 0.0 } ?: -ZOMBIE
            moving -> if (left >= 2 && joinable(ctx, pos)) 0.5 * best(beside + 1) - 0.5 else -0.5
            else -> 0.0
        }
    }

    /** The knife's target: the last unit answered in this step (it is chosen before the bullets). */
    private fun knifeTarget(ctx: HContext): String? =
        ctx.session.stepAnswers.lastOrNull { it is Choice.Unit }?.let { (it as Choice.Unit).unitId }

    /** Zombies already removed for +1 Attack in this step, after the target was chosen. */
    private fun removed(ctx: HContext): Int {
        val answers = ctx.session.stepAnswers
        val from = answers.indexOfLast { it is Choice.Unit }
        return answers.drop(from + 1).count { (it as? Choice.Option)?.label?.startsWith("Remove") == true }
    }

    /** "Choose an option (2 of up to 3)": 2 left, counting this one. */
    private fun choicesLeft(d: Decision): Int {
        val m = CHOICE_OF.find(d.prompt) ?: return 1
        return (m.groupValues[2].toInt() - m.groupValues[1].toInt() + 1).coerceAtLeast(1)
    }

    /** A zombie two steps from [pos], inside his radius, that one move puts beside it. */
    private fun joinable(ctx: HContext, pos: Hex): Boolean {
        val me = ctx.own()?.at ?: return false
        val radius = ctx.card("mortimer-gold").radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)
        return BatchBSupport.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).any { z ->
            val at = z.at!!
            ctx.distance(at, pos) == 2 && ctx.distance(me, at) <= radius
        }
    }

    /** Chance no defense they may hold meets [total] (a known hand is 0 or 1). */
    private fun killChance(ctx: HContext, e: HeroState, total: Int): Double {
        if (e.hand.isEmpty()) return 1.0
        val pos = e.at ?: return 0.0
        val mod = ctx.minionModifier(pos, e.team)
        val items = ctx.itemBonus(e.id, Stat.DEFENSE)
        return 1.0 - BatchASupport.chanceHolds(ctx, e) { c ->
            (c.block && c.blocksMelee) ||
                (c.canDefend && (c.defense ?: -99) + items + (if (c.ignoresMinionModifiers) 0 else mod) >= total)
        }
    }

    // H6: two zombies on a minion in range is the replace. It pushes without paying coins.
    private fun replace(ctx: HContext, id: String): Double {
        if (id != "mortimer-green-2b" && id != "mortimer-green-3b") return 0.0
        return if (packedMinion(ctx)) 10.0 else -1.0
    }

    // H12: an attack whose only target can still walk is a wasted red.
    private fun whiff(ctx: HContext, id: String): Double {
        if (!id.startsWith("mortimer-red") && id != "mortimer-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = BatchBSupport.enemyMinionWithin(ctx, at, 1) || BatchBSupport.enemyHeroWithin(ctx, at, 1)
        return if (adjacent) 0.0 else -2.0
    }

    /**
     * H1 placement: a minion spawn the next wave uses deletes the zombie (`respawnWave`: the new Battle
     * Zone's spawns, after a push either way). A spawn of the current Battle Zone, or of a hero, keeps it.
     */
    private fun avoidSpawn(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "mortimer-silver") return 0.0
        val hex = (choice as? Choice.Space)?.hex ?: return 0.0
        if (!ctx.game.isMinionSpawnPoint(hex)) return 0.0
        val zone = ctx.zoneOf(hex) ?: return 0.0
        val next = ctx.lanes().any { lane ->
            ctx.nextBattleZone(lane, Team.RED) == zone || ctx.nextBattleZone(lane, Team.BLUE) == zone
        }
        return if (next) -SPAWN else 0.0
    }

    private val DISCARD_REDS = setOf("mortimer-red-1", "mortimer-red-2a", "mortimer-red-3a")
    private val KNIVES = setOf("mortimer-gold", "mortimer-handicap")
    private val CHOICE_OF = Regex("""\((\d+) of up to (\d+)\)""")

    /** What a zombie on the board is worth to him (research §evalTerms: +2 each). */
    private const val ZOMBIE = 2.0

    /** A zombie beside an enemy hero in his radius 4 (research: +3). */
    private const val BESIDE = 3.0

    /** Three zombies on a hero the knife reaches (research: +6). */
    private const val LOADED = 6.0

    /** A zombie on the next wave's spawn (research: −3). */
    private const val SPAWN = 3.0

    private fun zombiesBeside(ctx: HContext, at: Hex): Int =
        BatchBSupport.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).count { zombie ->
            HexGrid.distance(zombie.at!!, at) == 1
        }

    private fun packedMinion(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.state.minions.any { minion ->
            val pos = minion.at ?: return@any false
            minion.team != ctx.team && ctx.distance(at, pos) <= 4 && zombiesBeside(ctx, pos) >= 2
        }
    }
}
