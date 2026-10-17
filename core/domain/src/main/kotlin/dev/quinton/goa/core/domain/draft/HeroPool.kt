package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlinx.serialization.Serializable

/** Which heroes are eligible before the method's [PoolRule] draws from them. */
@Serializable
enum class PoolSource { ALL, OWNED, CUSTOM }

object HeroPool {
    /**
     * Eligible hero ids in catalog order.
     * @param ownedProducts product ids the group owns; empty = every product (AppSettings).
     * @param customSelection hero ids for [PoolSource.CUSTOM].
     * @param maxComplexity complexity filter [RB]; null = no limit.
     */
    fun candidates(
        heroes: List<Hero>,
        source: PoolSource,
        ownedProducts: Set<String>,
        customSelection: Set<String>,
        maxComplexity: Int?,
    ): List<String> {
        val bySource = when (source) {
            PoolSource.ALL -> heroes
            PoolSource.OWNED -> if (ownedProducts.isEmpty()) heroes else heroes.filter { it.product in ownedProducts }
            PoolSource.CUSTOM -> heroes.filter { it.id in customSelection }
        }
        return ComplexityFilter.filter(bySource, maxComplexity).map { it.id }
    }
}

/** The Tie Breaker coin decides Team A (docs/DOMAIN.md). */
object TieBreakerCoin {
    fun flip(random: Random): Team = if (random.nextBoolean()) Team.RED else Team.BLUE
}
