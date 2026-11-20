package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.heroes.SnorriHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Snorri is blank until the runes are on the dashboard. The active rune is the marker under
 * this turn's slot (rulings/snorri.md 1). The oath has no primary; a failed block defeats him.
 */
object SnorriHeuristics : HeroHeuristics {
    override val heroId: String = "snorri"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 0.45,
        upgradeOrder = listOf(
            "snorri-red-2a",
            "snorri-red-3a",
            "snorri-green-2a",
            "snorri-green-3a",
            "snorri-blue-2a",
            "snorri-blue-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> inscribe(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val hero = state.hero(heroId) ?: return 0.0
        val sign = BatchC.sign(state, heroId, team)
        val runes = BatchC.runeCount(hero)
        // A wrong rune is not a token bonus. No runes past turn 1 is a blank kit.
        val v = when {
            runes == 0 && state.turn > 1 -> -4.0
            BatchC.activeRune(hero, state.turn) != null -> 2.0
            else -> 0.0
        }
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val hero = ctx.state.hero(heroId) ?: return 0.0
        val at = hero.at ?: return 0.0
        val rune = BatchC.activeRune(hero, ctx.state.turn) ?: return 0.0
        val distance = ctx.distance(at, target)
        // Axe gold is Attack 5 adjacent. Horn threatens a second hero at range 2.
        return when (rune) {
            SnorriHero.Rune.AXE -> if (distance == 1) 3.0 else 0.0
            SnorriHero.Rune.HORN -> if (distance == 2) 3.0 else 0.0
            SnorriHero.Rune.BIRD -> if (distance == 2) 1.5 else 0.0
            SnorriHero.Rune.ANVIL -> 0.0
        }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val hero = ctx.own() ?: return 0.0
        val runes = BatchC.runeCount(hero)
        if (id == "snorri-silver") {
            // H1 / H3: place the runes once. Re-inscribing spends the action the attack needed.
            return if (runes == 0) 8.0 else -3.5
        }
        if (id == "snorri-gold" && BatchC.activeRune(hero, ctx.turn) == null) {
            // Gold without a rune is Attack 2 adjacent. Do not lead with it.
            return -3.0
        }
        return oathWalk(id, runes) + goldRune(ctx, id)
    }

    private fun oathWalk(id: String, runes: Int): Double {
        if (id != "snorri-green-1" && id != "snorri-green-2a" && id != "snorri-green-3a") return 0.0
        // H11: the oath is a block, not a skill. Walking with it spends the only answer.
        return if (runes > 0) -3.0 else 0.0
    }

    private fun goldRune(ctx: HContext, id: String): Double {
        if (id != "snorri-gold" && id != "snorri-handicap") return 0.0
        val hero = ctx.own() ?: return 0.0
        val at = hero.at ?: return 0.0
        // H4 / H6: the rune changes what gold is for.
        return when (BatchC.activeRune(hero, ctx.turn)) {
            SnorriHero.Rune.AXE -> if (BatchC.minionsWithin(ctx, at, 1, friendly = false).isNotEmpty()) 3.0 else 2.0
            SnorriHero.Rune.HORN -> if (BatchC.enemiesWithin(ctx, at, 2).size >= 2) 4.0 else 1.0
            SnorriHero.Rune.BIRD -> if (BatchC.minionsWithin(ctx, at, 2, friendly = false).isNotEmpty()) 3.0 else 0.0
            SnorriHero.Rune.ANVIL -> if (BatchC.enemiesWithin(ctx, at, 1).isNotEmpty()) 3.0 else 0.0
            null -> 0.0
        }
    }

    private fun inscribe(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "snorri-silver" || choice !is Choice.Option) return 0.0
        val slot = BatchC.slotNumber(d.prompt) ?: return 0.0
        val axe = choice.label.equals("Axe", ignoreCase = true)
        // H2: the slot he is spending can take the leftover. Later slots want the attack rune.
        return when {
            slot == ctx.turn && choice.label.equals("Anvil", ignoreCase = true) -> 1.0
            slot != ctx.turn && axe -> 4.0
            slot != ctx.turn && choice.label.equals("Horn", ignoreCase = true) -> 2.0
            else -> 0.0
        }
    }

    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val hero = ctx.own() ?: return 0.0
        val rune = BatchC.activeRune(hero, ctx.turn)
        if (src == "snorri-gold" && rune == SnorriHero.Rune.ANVIL && ctx.state.hero(id)?.team != ctx.team) {
            // Anvil pays the coins when a hero is targeted, even if they block.
            return 3.0
        }
        if ((src == "snorri-red-2b" || src == "snorri-red-3b") && rune == SnorriHero.Rune.AXE) {
            return splash(ctx, id)
        }
        return 0.0
    }

    private fun splash(ctx: HContext, targetId: String): Double {
        val at = ctx.state.hero(targetId)?.at ?: ctx.state.minion(targetId)?.at ?: return 0.0
        val hero = ctx.enemies().any { enemy ->
            enemy.id != targetId && enemy.at?.let { ctx.distance(it, at) == 1 } == true
        }
        // Axe on the ranged red: a hero standing on the target discards, or is defeated.
        return if (hero) 4.0 else 0.0
    }

    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "snorri-gold" || choice !is Choice.Yes) return 0.0
        val hero = ctx.own() ?: return 0.0
        if (BatchC.activeRune(hero, ctx.turn) != SnorriHero.Rune.HORN) return 0.0
        val at = hero.at ?: return 0.0
        // H5: horn repeats on a different hero in range 2.
        return if (BatchC.enemiesWithin(ctx, at, 2).size >= 2) 3.0 else -2.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id != "snorri-green-1" && id != "snorri-green-2a" && id != "snorri-green-3a") return 0.0
        // H10: the engine's block preview is the rune match. A failed oath is a defeat.
        return when (d.context.blockPreview[id]) {
            true -> 4.0
            false -> -12.0
            null -> -2.0
        }
    }
}
