package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.meta.GroupReport
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.meta.MetaReport
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import kotlinx.coroutines.flow.Flow

/** Everything derived from the match log in one consistent snapshot. */
data class RatingsSnapshot(
    val ratings: RatingsResult,
    val meta: MetaReport,
    val config: EloConfig,
    /** Group stats (win conditions, sides, player pairs, streaks, last game night) from the same log. */
    val group: GroupReport = GroupReport.EMPTY,
) {
    companion object {
        val EMPTY = RatingsSnapshot(RatingsResult.EMPTY, MetaReport.EMPTY, EloConfig.DEFAULT)
    }
}

/**
 * The full replay output (histories, player × hero records, per-match deltas, meta), for the
 * players feature. Other features use the narrower [dev.quinton.goa.core.data.RatingsRepository].
 */
interface RatingsSnapshotRepository {
    /** Recomputed off the main thread whenever matches, drafts or the Elo config change. */
    fun observeSnapshot(): Flow<RatingsSnapshot>
}

/**
 * "Best with this hero" for the catalog: [MetaReport.pilots] from the same snapshot the Meta tab
 * shows, so the two never disagree.
 */
interface HeroPilotsRepository {
    fun observeHeroPilots(heroId: String): Flow<HeroPilots>
}

/** The user-editable Elo constants (Settings → Ratings). Changing them recomputes all ratings. */
interface EloConfigRepository {
    val config: Flow<EloConfig>

    suspend fun update(transform: (EloConfig) -> EloConfig)

    suspend fun reset()
}

/**
 * The group's rating constants with their change time (ADR-025): ratings must match on every
 * phone, so the constants travel with group sync and the later change wins.
 */
interface StampedEloConfigStore {
    suspend fun stamped(): StampedEloConfig

    /** Stores constants received from the group, keeping their change time (no new stamp). */
    suspend fun applySynced(config: StampedEloConfig)
}
