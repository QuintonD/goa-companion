package dev.quinton.goa.hub.heroes

import dev.quinton.goa.core.domain.meta.MetaReport
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.presentation.catalog.HeroLogSummary
import dev.quinton.goa.core.presentation.catalog.HeroPersonalStats
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.store.GroupData

/**
 * The hero numbers the Heroes pages show (Elo, usage, pilots), read from the group's one ratings
 * snapshot (`GroupRatings`, [RatingsSnapshot.compute]) exactly as the phone's
 * `ReplayRatingsRepository` serves them (`ratings.heroes`, `meta.usage()`, `meta.pilots`).
 */
class HeroRecords(snapshot: RatingsSnapshot, data: GroupData) {
    val heroRatings: Map<String, Rating> = snapshot.ratings.heroes
    val heroUsage: Map<String, HeroUsage> = snapshot.meta.usage()
    private val meta: MetaReport = snapshot.meta
    private val players: List<Player> = data.players(includeArchived = true)

    /** "Your table" on [heroId]'s page. */
    fun stats(heroId: String): HeroPersonalStats = HeroPersonalStats(
        rating = heroRatings[heroId],
        usage = heroUsage[heroId],
        log = HeroLogSummary.of(meta.pilots(heroId), players),
    )

    companion object {
        /** The records for the group's current data (the snapshot is cached per stored document). */
        fun current(ctx: HubContext): HeroRecords {
            val doc = ctx.groupStore.document.value
            return HeroRecords(ctx.ratings.of(doc), GroupData(doc))
        }
    }
}
