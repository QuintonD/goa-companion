package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.ai.heuristic.heroes.ArienHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.BainHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.BroganHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.BrynnHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.CutterHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.DodgerHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.EmmittHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.GarrusHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.GydionHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.HanuHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.IgnatiaHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.MinHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.MisaHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.MortimerHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.MrakHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.NebkherHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.RazzleHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.RowennaHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.SabinaHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.SilverarrowHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.SnorriHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.SwiftHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.TakahideHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.TaliHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.TigerclawHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.TrinketsHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.UrsafarHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.WaspHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.WhisperHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.WidgetHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.WukHeuristics
import dev.quinton.goa.core.game.ai.heuristic.heroes.XargathaHeuristics
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * How a hero wants to play, read by the general playbook. Numbers are hints, not rules:
 * [aggression] 0 stands off heroes, 1 hunts them; [laneWeight] scales standing in the Battle Zone;
 * [upgradeOrder] is colour names (`"red"`) or card ids, best first; [keepDefender] holds the
 * best defense card while a spare remains.
 */
data class HeroStyle(
    val role: HeroRole = HeroRole.FIGHTER,
    val idealDistance: Int = 1,
    val aggression: Double = 0.5,
    val laneWeight: Double = 1.0,
    val upgradeOrder: List<String> = listOf("red", "blue", "green"),
    val keepDefender: Boolean = true,
) {
    companion object {
        val DEFAULT: HeroStyle = HeroStyle()
    }
}

/** The job the general book reads. A hero is one of these, not a blend. */
enum class HeroRole {
    TANK,
    FIGHTER,
    RANGED,
    SUPPORT,
    ASSASSIN,
    PUSHER,
    SETUP,
}

/**
 * One hero's playbook (ADR-085). The defaults play nothing of their own: the general book
 * carries a hero that has no notes yet. Scores are coin-equivalents added to the general score.
 * Do not re-implement a card's rules here; the engine resolves those.
 */
interface HeroHeuristics {
    /** Catalog id, for example `"arien"`. */
    val heroId: String

    val style: HeroStyle get() = HeroStyle.DEFAULT

    /** Return one of [d]'s options to play it outright, or null to let scoring decide. */
    fun override(ctx: HContext, d: Decision): Choice? = null

    /** Added to the general score of [choice]. Dispatch on `d.kind` and `d.sourceCardId`. */
    fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = 0.0

    /**
     * This hero's own terms of the position, from [team]'s point of view (positive is good for
     * [team]): tokens, pieces, counters, a set-up the general evaluation cannot see.
     */
    fun evalTerms(state: GameState, team: Team, heroId: String): Double = 0.0

    /**
     * Extra danger when this hero, as an enemy, threatens [target] beyond printed reach
     * (Brogan's straight lines, a body on another space). Coin-equivalents, usually small.
     */
    fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double = 0.0

    /**
     * [threatAdjust] when the threatened hero is known: [victimId] would stand on [target]. A threat that
     * depends on the victim (an execute of an empty hand) overrides this; the default is the plain one.
     */
    fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double =
        threatAdjust(ctx, enemyHeroId, target)
}

/** A hero the registry does not know. The general book plays them. */
object DefaultHeroHeuristics : HeroHeuristics {
    override val heroId: String = "default"
}

/**
 * Every hero playbook. Unknown ids get [DefaultHeroHeuristics]. Hero files register here once;
 * filling in a playbook edits only that hero's file.
 */
object HeroPlaybooks {
    private val byId: Map<String, HeroHeuristics> = listOf(
        ArienHeuristics,
        BainHeuristics,
        BroganHeuristics,
        BrynnHeuristics,
        CutterHeuristics,
        DodgerHeuristics,
        EmmittHeuristics,
        GarrusHeuristics,
        GydionHeuristics,
        HanuHeuristics,
        IgnatiaHeuristics,
        MinHeuristics,
        MisaHeuristics,
        MortimerHeuristics,
        MrakHeuristics,
        NebkherHeuristics,
        RazzleHeuristics,
        RowennaHeuristics,
        SabinaHeuristics,
        SilverarrowHeuristics,
        SnorriHeuristics,
        SwiftHeuristics,
        TakahideHeuristics,
        TaliHeuristics,
        TigerclawHeuristics,
        TrinketsHeuristics,
        UrsafarHeuristics,
        WaspHeuristics,
        WhisperHeuristics,
        WidgetHeuristics,
        WukHeuristics,
        XargathaHeuristics,
    ).associateBy { it.heroId }

    fun forHero(id: String): HeroHeuristics = byId[id] ?: DefaultHeroHeuristics

    fun ids(): Set<String> = byId.keys
}
