package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.domain.meta.GroupReport
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaReport
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.sync.UnknownHeroes
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import java.time.ZoneId

/**
 * Everything derived from the match log in one consistent snapshot (ADR-003): the Elo replay,
 * the meta stats and the group stats, computed together from the same inputs, so ratings from one
 * version of the log are never shown with stats from another. The phones' `ReplayRatingsRepository`
 * and the hub (ADR-030) both build it with [compute].
 */
data class RatingsSnapshot(
    val ratings: RatingsResult,
    val meta: MetaReport,
    val config: EloConfig,
    /** Group stats (win conditions, sides, player pairs, streaks, last game night) from the same log. */
    val group: GroupReport = GroupReport.EMPTY,
) {
    companion object {
        val EMPTY = RatingsSnapshot(RatingsResult.EMPTY, MetaReport.EMPTY, EloConfig.DEFAULT)

        /**
         * Replays [matches] (pilots already resolved through the player merges) and computes the meta
         * and group stats. Hero ids not in [knownHeroes] count as Unknown hero; the stored ids stay
         * intact (the rule applies when reading, ADR-025).
         */
        fun compute(
            matches: List<Match>,
            drafts: List<DraftRecord>,
            config: EloConfig,
            knownHeroes: Set<String>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): RatingsSnapshot {
            val known = UnknownHeroes.clearMatches(matches, knownHeroes)
            val ratings = EloCalculator.replay(known, config)
            return RatingsSnapshot(
                ratings = ratings,
                meta = MetaStats.compute(known, UnknownHeroes.clearDrafts(drafts, knownHeroes)),
                config = config,
                group = GroupStats.compute(known, ratings, zone),
            )
        }
    }
}
