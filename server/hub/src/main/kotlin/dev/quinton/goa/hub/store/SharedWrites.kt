package dev.quinton.goa.hub.store

import dev.quinton.goa.core.domain.player.PlayerNames
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge

/**
 * Web writes to the group's shared data, with exactly the rules of the phones' Room repositories
 * (ADR-025, ADR-030 §3), as pure functions over a [SyncDocument]:
 *
 * - every write stamps `updatedAt = EditStamp.next(now, previous copy, tombstone)`, so an edit
 *   always beats the copy it edited and a later edit brings a deleted record back;
 * - a delete removes the record and writes a [Deletion] tombstone stamped after both;
 * - players are never deleted (archived or merged instead); a merge is a [PlayerMerge] row,
 *   refused when the two played in the same match;
 * - the rating constants are one [StampedEloConfig].
 *
 * Mirrors `RoomPlayerRepository`, `RoomMatchRepository`, `RoomDraftRecordRepository` and
 * `DataStoreEloConfigRepository` (core:data); keep them in step. [GroupStore] runs these under
 * its lock and validates the result before storing it.
 */
object SharedWrites {
    /** `RoomPlayerRepository.upsert`: trimmed name, stamped after the stored copy. */
    fun upsertPlayer(doc: SyncDocument, player: Player, now: Long): Pair<SyncDocument, Player> {
        require(player.name.isNotBlank()) { "Player name must not be blank" }
        val previous = doc.players.firstOrNull { it.id == player.id }
        val stamped = player.copy(name = player.name.trim(), updatedAt = EditStamp.next(now, previous?.updatedAt))
        return doc.copy(players = doc.players.filterNot { it.id == player.id } + stamped) to stamped
    }

    /** `RoomPlayerRepository.archive`: a missing player is ignored. */
    fun archivePlayer(doc: SyncDocument, id: String, archived: Boolean, now: Long): SyncDocument {
        val previous = doc.players.firstOrNull { it.id == id } ?: return doc
        val updated = previous.copy(archived = archived, updatedAt = EditStamp.next(now, previous.updatedAt))
        return doc.copy(players = doc.players.map { if (it.id == id) updated else it })
    }

    /**
     * `RoomPlayerRepository.findByName`: the visible player with this name (trimmed,
     * case-insensitive, archived included), oldest first so existing duplicates keep the original.
     */
    fun findByName(doc: SyncDocument, name: String): Player? {
        val key = PlayerNames.key(name)
        return PlayerMerges(doc.playerMerges).visible(doc.players.sortedWith(compareBy({ it.createdAt }, { it.id })))
            .firstOrNull { PlayerNames.key(it.name) == key }
    }

    /**
     * `RoomPlayerRepository.ensurePlayer`: the player called `candidate.name`, unarchived if it was
     * archived; otherwise [candidate] itself, saved with a trimmed name. Never a second player of
     * one name.
     */
    fun ensurePlayer(doc: SyncDocument, candidate: Player, now: Long): Pair<SyncDocument, Player> {
        require(candidate.name.isNotBlank()) { "Player name must not be blank" }
        val existing = findByName(doc, candidate.name)
        return when {
            existing == null -> upsertPlayer(doc, candidate.copy(name = candidate.name.trim()), now)

            existing.archived -> {
                val next = archivePlayer(doc, existing.id, archived = false, now)
                next to next.players.first { it.id == existing.id }
            }

            else -> doc to existing
        }
    }

    /** `RoomMatchRepository.upsert`: stamped after the stored copy and after any tombstone. */
    fun upsertMatch(doc: SyncDocument, match: Match, now: Long): Pair<SyncDocument, Match> {
        val stamp = EditStamp.next(
            now,
            doc.matches.firstOrNull { it.id == match.id }?.updatedAt,
            tombstone(doc, DeletionKind.MATCH, match.id)?.deletedAt,
        )
        val stamped = match.copy(updatedAt = stamp)
        return doc.copy(matches = doc.matches.filterNot { it.id == match.id } + stamped) to stamped
    }

    /** `RoomMatchRepository.delete`: a missing match is ignored; otherwise removed and tombstoned. */
    fun deleteMatch(doc: SyncDocument, id: String, now: Long): SyncDocument {
        val previous = doc.matches.firstOrNull { it.id == id } ?: return doc
        val deletion = Deletion(
            DeletionKind.MATCH,
            id,
            EditStamp.next(now, previous.updatedAt, tombstone(doc, DeletionKind.MATCH, id)?.deletedAt),
        )
        return doc.copy(
            matches = doc.matches.filterNot { it.id == id },
            deletions = doc.deletions.filterNot { it.kind == DeletionKind.MATCH && it.id == id } + deletion,
        )
    }

    /** `RoomDraftRecordRepository.save`: stamped like a match. */
    fun saveDraft(doc: SyncDocument, record: DraftRecord, now: Long): Pair<SyncDocument, DraftRecord> {
        val stamp = EditStamp.next(
            now,
            doc.drafts.firstOrNull { it.id == record.id }?.updatedAt,
            tombstone(doc, DeletionKind.DRAFT, record.id)?.deletedAt,
        )
        val stamped = record.copy(updatedAt = stamp)
        return doc.copy(drafts = doc.drafts.filterNot { it.id == record.id } + stamped) to stamped
    }

    /**
     * Deletes a draft record the way a match is deleted. (The phones have no draft delete yet;
     * the sync format already carries draft tombstones, so this follows the match rule.)
     */
    fun deleteDraft(doc: SyncDocument, id: String, now: Long): SyncDocument {
        val previous = doc.drafts.firstOrNull { it.id == id } ?: return doc
        val deletion = Deletion(
            DeletionKind.DRAFT,
            id,
            EditStamp.next(now, previous.updatedAt, tombstone(doc, DeletionKind.DRAFT, id)?.deletedAt),
        )
        return doc.copy(
            drafts = doc.drafts.filterNot { it.id == id },
            deletions = doc.deletions.filterNot { it.kind == DeletionKind.DRAFT && it.id == id } + deletion,
        )
    }

    /** `RoomPlayerRepository.merge`: same checks, same refusal, same stamp. */
    fun mergePlayers(
        doc: SyncDocument,
        fromId: String,
        toId: String,
        now: Long,
    ): Pair<SyncDocument, PlayerMergeOutcome> {
        val merges = PlayerMerges(doc.playerMerges)
        val ids = doc.players.mapTo(HashSet()) { it.id }
        val possible = fromId in ids && toId in ids && fromId != toId &&
            fromId !in merges.mergedAway && toId !in merges.mergedAway
        if (!possible) return doc to PlayerMergeOutcome.NotPossible
        val matches = merges.resolve(doc.matches.sortedWith(compareBy({ it.playedAt }, { it.createdAt }, { it.id })))
        val conflicts = PlayerMerges.conflicts(fromId, toId, matches)
        if (conflicts.isNotEmpty()) return doc to PlayerMergeOutcome.PlayedTogether(conflicts)
        val previous = doc.playerMerges.firstOrNull { it.fromId == fromId }?.mergedAt
        val merge = PlayerMerge(fromId, toId, EditStamp.next(now, previous))
        return doc.copy(playerMerges = doc.playerMerges.filterNot { it.fromId == fromId } + merge) to
            PlayerMergeOutcome.Merged
    }

    /** `DataStoreEloConfigRepository.update`: sanitized, stamped after the current change. */
    fun updateEloConfig(doc: SyncDocument, now: Long, transform: (EloConfig) -> EloConfig): SyncDocument {
        val next = transform(doc.eloConfig.config).sanitized()
        return doc.copy(eloConfig = StampedEloConfig(next, EditStamp.next(now, doc.eloConfig.updatedAt)))
    }

    /** `DataStoreEloConfigRepository.reset`: the defaults, as a new change. */
    fun resetEloConfig(doc: SyncDocument, now: Long): SyncDocument =
        doc.copy(eloConfig = StampedEloConfig(EloConfig.DEFAULT, EditStamp.next(now, doc.eloConfig.updatedAt)))

    private fun tombstone(doc: SyncDocument, kind: DeletionKind, id: String): Deletion? =
        doc.deletions.filter { it.kind == kind && it.id == id }.maxByOrNull { it.deletedAt }
}

/** The outcome of a player merge (as `PlayerMergeResult` on the phones). */
sealed interface PlayerMergeOutcome {
    data object Merged : PlayerMergeOutcome

    /** The matches both played in (resolved, oldest first); the merge was refused. */
    data class PlayedTogether(val matches: List<Match>) : PlayerMergeOutcome

    /** A player is missing, already merged away, or both ids are the same. */
    data object NotPossible : PlayerMergeOutcome
}
