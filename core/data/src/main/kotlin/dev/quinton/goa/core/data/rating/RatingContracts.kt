package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.meta.MetaReport
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsResult
import kotlinx.coroutines.flow.Flow

/** Everything derived from the match log in one consistent snapshot. */
data class RatingsSnapshot(
    val ratings: RatingsResult,
    val meta: MetaReport,
    val config: EloConfig,
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

/** The user-editable Elo constants (Settings → Ratings). Changing them recomputes all ratings. */
interface EloConfigRepository {
    val config: Flow<EloConfig>

    suspend fun update(transform: (EloConfig) -> EloConfig)

    suspend fun reset()
}
