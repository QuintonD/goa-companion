package dev.quinton.goa.hub.store

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player

/**
 * The group's data as the phones' repositories *read* it (ADR-025 §6): merged player ids resolved
 * in every match, merged-away players hidden, the same orderings as the Room queries. The
 * Unknown-hero rule is not applied here: apply `UnknownHeroes` with the catalog where ratings and
 * stats are computed, as `ReplayRatingsRepository` does.
 */
class GroupData(val document: SyncDocument) {
    val merges: PlayerMerges = PlayerMerges(document.playerMerges)

    /** Players by name (case-insensitive), then id; archived ones only if asked; merged-away ones never. */
    fun players(includeArchived: Boolean = false): List<Player> = merges.visible(
        document.players
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy<Player, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }),
    )

    /** The player [id] stands for now (following merges), archived or not. */
    fun player(id: String): Player? {
        val resolved = merges.resolve(id)
        return document.players.firstOrNull { it.id == resolved }
    }

    /** The match log, newest first (played, then created, then id), pilots resolved. */
    val matches: List<Match> by lazy {
        merges.resolve(
            document.matches.sortedWith(
                compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id },
            ),
        )
    }

    fun match(id: String): Match? = document.matches.firstOrNull { it.id == id }?.let(merges::resolve)

    /** Finished drafts, newest first. */
    val drafts: List<DraftRecord> by lazy {
        document.drafts.sortedWith(compareByDescending<DraftRecord> { it.createdAt }.thenByDescending { it.id })
    }

    val eloConfig: EloConfig get() = document.eloConfig.config
}
