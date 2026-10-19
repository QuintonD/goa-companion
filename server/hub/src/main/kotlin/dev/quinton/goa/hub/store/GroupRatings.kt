package dev.quinton.goa.hub.store

import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.catalog.HubCatalog
import java.time.ZoneId

/**
 * The one derivation of ratings, meta and group stats on the hub (ADR-003): [RatingsSnapshot.compute]
 * over the group's data read as the phones' `ReplayRatingsRepository` reads it (merged players
 * resolved, the Unknown-hero rule with this catalog, the group's constants), in the hub's zone.
 * Every page (Players, Heroes, Draft balancing, Settings → Ratings) reads its numbers from here, so
 * no page replays the log on its own and the web shows what the phones show for the same data.
 * One snapshot is kept per stored document, so a page view replays nothing new.
 */
class GroupRatings(private val store: GroupStore, private val catalog: HubCatalog, private val clock: HubClock) {
    private class Cached(val doc: SyncDocument, val known: Set<String>, val zone: ZoneId, val snapshot: RatingsSnapshot)

    @Volatile
    private var cache: Cached? = null

    /** The snapshot of the stored data now. */
    fun current(): RatingsSnapshot = of(store.document.value)

    /** The snapshot of [doc] (normally a value of `GroupStore.document`). */
    fun of(doc: SyncDocument): RatingsSnapshot {
        val known = catalog.catalog.heroes.mapTo(HashSet()) { it.id }
        val zone = clock.zone
        cache?.takeIf { it.doc === doc && it.known == known && it.zone == zone }?.let { return it.snapshot }
        return compute(GroupData(doc), known, zone).also { cache = Cached(doc, known, zone, it) }
    }

    companion object {
        /** The phones' `ReplayRatingsRepository` inputs, from the hub's data. */
        fun compute(data: GroupData, known: Set<String>, zone: ZoneId): RatingsSnapshot =
            RatingsSnapshot.compute(data.matches, data.drafts, data.eloConfig, known, zone)
    }
}
