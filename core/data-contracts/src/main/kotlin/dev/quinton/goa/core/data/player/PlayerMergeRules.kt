package dev.quinton.goa.core.data.player

import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.sync.PlayerMerge

/**
 * When one player may be merged into another ([dev.quinton.goa.core.data.PlayerMergeRepository]),
 * whatever stores them (DOMAIN.md *Duplicate players*).
 */
object PlayerMergeRules {
    /**
     * [PlayerMergeResult.Merged] when [fromId] may be merged into [toId]: both players exist
     * ([bothExist]), the ids differ, neither is merged away already ([merges]), and they never
     * played in the same match. [matches] (the log as stored) is read only when it comes to that.
     */
    inline fun check(
        fromId: String,
        toId: String,
        bothExist: Boolean,
        merges: PlayerMerges,
        matches: () -> List<Match>,
    ): PlayerMergeResult = when {
        !bothExist || fromId == toId -> PlayerMergeResult.NotPossible

        fromId in merges.mergedAway || toId in merges.mergedAway -> PlayerMergeResult.NotPossible

        else -> {
            val conflicts = PlayerMerges.conflicts(fromId, toId, merges.resolve(matches()))
            if (conflicts.isNotEmpty()) PlayerMergeResult.PlayedTogether(conflicts) else PlayerMergeResult.Merged
        }
    }

    /** The merge to store, stamped after any earlier merge of [fromId] ([previousMergedAt]). */
    fun merge(fromId: String, toId: String, now: Long, previousMergedAt: Long?): PlayerMerge =
        PlayerMerge(fromId, toId, EditStamp.next(now, previousMergedAt))
}
