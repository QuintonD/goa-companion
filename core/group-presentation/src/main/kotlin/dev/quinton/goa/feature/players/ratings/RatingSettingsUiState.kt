package dev.quinton.goa.feature.players.ratings

import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.rating.EloConfig

data class RatingSettingsUiState(
    val loading: Boolean = true,
    val config: EloConfig = EloConfig.DEFAULT,
    val ratedMatches: Int = 0,
    val skippedMatches: Int = 0,
    /** True while the shown ratings were computed with an older config (recompute in flight). */
    val recomputing: Boolean = false,
) {
    val isDefault: Boolean get() = config == EloConfig.DEFAULT
}

/** Settings → Ratings, shared by the phone and the web app (ADR-043). */
fun ratingSettingsState(config: EloConfig, snapshot: RatingsSnapshot): RatingSettingsUiState = RatingSettingsUiState(
    loading = false,
    config = config,
    ratedMatches = snapshot.ratings.replayOrder.size,
    skippedMatches = snapshot.ratings.skipped.size,
    recomputing = snapshot.config != config,
)
