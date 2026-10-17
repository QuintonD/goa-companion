package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.PlayerMerge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything a phone shares with its group (ADR-025): the match log, players, drafts, tombstones,
 * player merges and the group's rating constants. Ratings are not included (ADR-003). App
 * settings, timer presets, draft methods and "who am I" stay on each phone.
 *
 * **Versioning:** phones sync only with the same [VERSION]. Bump it whenever a synced type gains
 * a field: an older app would drop the field on decode, and its copy could then win a tie and
 * erase the field on every phone. Different versions are refused with "update the app".
 */
@Serializable
data class SyncDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val players: List<Player> = emptyList(),
    val matches: List<Match> = emptyList(),
    val drafts: List<DraftRecord> = emptyList(),
    val deletions: List<Deletion> = emptyList(),
    val playerMerges: List<PlayerMerge> = emptyList(),
    val eloConfig: StampedEloConfig = StampedEloConfig.UNSET,
) {
    companion object {
        const val FORMAT = "goa-sync"
        const val VERSION = 1
        val EMPTY = SyncDocument()
    }
}

/**
 * The group's rating constants with the time they were last changed. Ratings must match on every
 * phone, so the constants are group-wide and the later change wins. [updatedAt] 0 = never changed.
 */
@Serializable
data class StampedEloConfig(val config: EloConfig, val updatedAt: Long) {
    companion object {
        val UNSET = StampedEloConfig(EloConfig.DEFAULT, 0)
    }
}

/**
 * The one JSON form of synced data. Its encoding of a record is also the deterministic tie-break
 * when two copies carry the same `updatedAt` ([SyncMerge]), so it must be identical on every
 * phone: defaults and nulls are always written.
 */
val SyncJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
}

/** The next `updatedAt` for a local write: later than [previous] even if the clock went back. */
object EditStamp {
    fun next(now: Long, vararg previous: Long?): Long {
        val latest = previous.filterNotNull().maxOrNull() ?: return now
        return maxOf(now, latest + 1)
    }
}
