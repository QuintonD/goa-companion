package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.domain.meta.MetaReport
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.UnknownHeroes
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.presentation.catalog.HeroLogSummary
import dev.quinton.goa.core.presentation.catalog.HeroPersonalStats
import dev.quinton.goa.hub.store.GroupData
import dev.quinton.goa.hub.store.GroupStore

/**
 * The hero numbers the Heroes pages show (Elo, usage, pilots), derived from the match log exactly
 * as the phone's `ReplayRatingsRepository` does: Unknown-hero rule with this catalog, then
 * [EloCalculator.replay] and [MetaStats.compute] over the same matches and drafts (ADR-003).
 */
class HeroRecords private constructor(
    val heroRatings: Map<String, Rating>,
    val heroUsage: Map<String, HeroUsage>,
    private val meta: MetaReport,
    private val players: List<Player>,
) {
    /** "Your table" on [heroId]'s page. */
    fun stats(heroId: String): HeroPersonalStats = HeroPersonalStats(
        rating = heroRatings[heroId],
        usage = heroUsage[heroId],
        log = HeroLogSummary.of(meta.pilots(heroId), players),
    )

    companion object {
        fun of(data: GroupData, catalog: Catalog): HeroRecords {
            val known = catalog.heroes.mapTo(HashSet()) { it.id }
            val matches = UnknownHeroes.clearMatches(data.matches, known)
            val drafts = UnknownHeroes.clearDrafts(data.drafts, known)
            val ratings = EloCalculator.replay(matches, data.eloConfig)
            val meta = MetaStats.compute(matches, drafts)
            return HeroRecords(ratings.heroes, meta.usage(), meta, data.players(includeArchived = true))
        }

        @Volatile
        private var cached: Pair<SyncDocument, HeroRecords>? = null

        /** The records for the group's current data, recomputed only when the data changes. */
        fun current(store: GroupStore, catalog: Catalog): HeroRecords {
            val document = store.document.value
            cached?.let { (doc, records) -> if (doc === document) return records }
            return of(GroupData(document), catalog).also { cached = document to it }
        }
    }
}
