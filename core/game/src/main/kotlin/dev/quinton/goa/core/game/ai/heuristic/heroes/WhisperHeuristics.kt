package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Whisper (`heroes/whisper.md`): forced choices, then the step in. Swift Justice snipes only a hero
 * whose discard is empty (or hits a neighbour); Death Seeker and Blood Fury's repeat need an enemy in
 * radius who has discarded; the Reds step only on empty minion spawns in the battle zone.
 */
object WhisperHeuristics : HeroHeuristics {
    override val heroId: String = "whisper"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 2,
        aggression = 0.6,
        laneWeight = 0.5,
        upgradeOrder = listOf("red", "green", "blue"),
        keepDefender = false,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val at = self.at ?: return 0.0
        // Enemies near him who have discarded switch on Death Seeker and Blood Fury's repeat.
        val hurt = state.heroes.count { hero ->
            hero.team != self.team && hero.at != null &&
                HexGrid.distance(at, hero.at!!) in 1..3 && !BatchDSupport.discardEmpty(hero)
        }
        return BatchDSupport.sign(state, heroId, team) * minOf(hurt, 2) * 1.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchDSupport.mayHold(ctx, heroId, GOLD)) return 0.0
        if (ctx.distance(from, target) !in 2..3) return 0.0
        val victim = BatchDSupport.victimAt(ctx, heroId, target) ?: return 0.0
        // H17: the snipe (and its shove) is only on a hero whose discard is still empty.
        return if (BatchDSupport.discardEmpty(victim)) 1.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val card = ctx.card(id)
        return when (id) {
            // H1–H3: a hero in range with an empty discard, or a unit adjacent. Nothing else is a target.
            GOLD, HANDICAP -> {
                val range = ctx.attackReach(ctx.seat, card).coerceAtLeast(1)
                val real = BatchDSupport.strikeOn(ctx, card, { uid, pos ->
                    val d = ctx.distance(at, pos)
                    val hero = ctx.heroBody(uid)?.let { ctx.state.hero(it) }
                    val snipe = hero != null && d in 1..range && BatchDSupport.discardEmpty(hero)
                    snipe || (d == 1 && (id == GOLD || hero != null))
                })
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H4: Death Seeker needs an enemy in radius with a discard; then immunity or a retrieve.
            SILVER -> silver(ctx, at, card.radius.coerceAtLeast(1))

            // H5: the steps are the empty minion spawns in radius (in the battle zone), up to the cap.
            in TRAIL -> {
                val cap = when (id) {
                    "whisper-red-1" -> 1
                    "whisper-red-2a" -> 2
                    else -> 3
                }
                val steps = minOf(cap, BatchDSupport.emptyMinionSpawns(ctx, at, card.radius.coerceAtLeast(1)).size)
                val real = BatchDSupport.strikeOn(ctx, card, { _, pos -> ctx.distance(at, pos) in 1..1 + steps })
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H6: Blood Fury hits a neighbour; with an enemy hero in radius holding a discard after the
            // attack, it repeats on a different neighbour (Blood Frenzy: up to five times).
            in FURY -> {
                val hits = BatchDSupport.strikeList(ctx, card, { _, pos -> ctx.distance(at, pos) == 1 })
                var real = hits.firstOrNull()?.second ?: -0.5
                if (hits.size >= 2 && furyPrimed(ctx, at, card, hits[0].first)) {
                    val repeats = if (id == "whisper-red-3b") FRENZY_REPEATS else 1
                    real += 0.6 * hits.drop(1).take(repeats).sumOf { maxOf(0.0, it.second) }
                }
                BatchDSupport.strikeFix(ctx, card, real)
            }

            // H8: the place is an empty minion spawn in range; worth it beside a target with an attack left.
            in SHADOW -> shadow(ctx, at, id)

            // H10: Lesser Evil: the enemy picks the cheaper of a discard and a minion beside him.
            in EVIL -> BatchDSupport.outlookFix(ctx, card, evil(ctx, at, id))

            else -> 0.0
        }
    }

    /**
     * After the attack an enemy hero in radius has a discard: one already has, or the first target is a
     * hero with a card to lose (a defense goes to the discard; so does a hit's card). A first target
     * with no card to defend with is defeated and leaves the board, so its own discard does not count.
     */
    private fun furyPrimed(ctx: HContext, at: Hex, card: HCard, first: String): Boolean {
        val radius = card.radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)
        val firstHero = ctx.heroBody(first)
        val firstStays = firstHero != null && ctx.handCount(firstHero) > 0
        val others = BatchDSupport.enemyHeroesWithin(ctx, at, 1, radius).filter { it.id != firstHero }
        return firstStays || others.any { !BatchDSupport.discardEmpty(it) }
    }

    private fun silver(ctx: HContext, at: Hex, radius: Int): Double {
        val hurt = BatchDSupport.enemyHeroesWithin(ctx, at, 1, radius).any { !BatchDSupport.discardEmpty(it) }
        if (!hurt) return -2.0
        val discard = ctx.own()?.cards?.any { it.zone == Zone.DISCARD } == true
        val save = minOf(6.0, ctx.exposure(ctx.seat, at) * 0.5)
        return maxOf(if (discard) 2.5 else 0.0, save)
    }

    private fun shadow(ctx: HContext, at: Hex, id: String): Double {
        val range = ctx.card(id).range.coerceAtLeast(1)
        val spots = BatchDSupport.emptyMinionSpawns(ctx, at, range).filter { it != at }
        if (spots.isEmpty()) return -1.5
        val attack = ctx.ownHand().any { it.attackCard }
        val onTarget = spots.any { s -> BatchDSupport.adjacentEnemies(ctx.state, s, ctx.team) > 0 }
        return if (attack && onTarget) 1.5 else 0.0
    }

    private fun evil(ctx: HContext, at: Hex, id: String): Double {
        val range = ctx.card(id).range.coerceAtLeast(1)
        val heroes = BatchDSupport.enemyHeroesWithin(ctx, at, 1, range)
        if (heroes.isEmpty()) return -1.5
        val greater = id == "whisper-blue-3b"
        val minions = ctx.state.minions.filter { m ->
            m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) == 1 && !ctx.heavyImmune(m.id)
        }.map { ctx.minionWorth(it.id) }.sortedDescending()
        val minionCost = minions.take(if (greater) 3 else 1).sumOf { maxOf(0.0, it) }
        // The enemy takes the cheaper bullet, and a bullet with no effect costs them nothing.
        return heroes.maxOf { e ->
            val strip = if (greater && e.hand.isEmpty()) ctx.killWorth(e.id) else ctx.discardWorth(e)
            minOf(strip, minionCost)
        }
    }

    /** H15: Blood Fury for the wave; Shadow Walk's Attack item; Lesser Evil's Attack item. */
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            "whisper-red-2b", "whisper-red-3b" -> 1.0
            "whisper-green-2a", "whisper-green-3a" -> 0.5
            "whisper-blue-2b", "whisper-blue-3b" -> 1.0
            else -> 0.0
        }
    }

    private const val GOLD = "whisper-gold"
    private const val HANDICAP = "whisper-handicap"
    private const val SILVER = "whisper-silver"
    private val TRAIL = setOf("whisper-red-1", "whisper-red-2a", "whisper-red-3a")
    private val FURY = setOf("whisper-red-2b", "whisper-red-3b")
    private const val FRENZY_REPEATS = 5
    private val SHADOW = setOf("whisper-green-1", "whisper-green-2a", "whisper-green-3a")
    private val EVIL = setOf("whisper-blue-2b", "whisper-blue-3b")
}
