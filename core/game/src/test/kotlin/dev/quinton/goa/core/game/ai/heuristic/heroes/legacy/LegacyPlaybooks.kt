package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks

/**
 * The playbooks as they were before wave 3, Track D (2026-11-22, 64955c18), kept so the new notes can be
 * measured head to head against the old (`AI_H_VS=legacy` in HeroAblationTest). A hero without a legacy
 * copy is its current playbook.
 */
object LegacyPlaybooks {
    private val byId: Map<String, HeroHeuristics> = listOf(
        LegacyRowennaHeuristics,
        LegacyEmmittHeuristics,
        LegacyMortimerHeuristics,
        LegacyHanuHeuristics,
        LegacyNebkherHeuristics,
    ).associateBy { it.heroId }

    fun forHero(id: String): HeroHeuristics = byId[id] ?: HeroPlaybooks.forHero(id)
}
