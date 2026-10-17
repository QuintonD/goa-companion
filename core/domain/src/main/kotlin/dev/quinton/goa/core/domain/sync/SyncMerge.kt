package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import kotlinx.serialization.KSerializer

/**
 * Merges two phones' [SyncDocument]s (ADR-025, DOMAIN.md *Group sync*). Every part is a
 * last-writer-wins register per id, so [merge] is commutative, associative and idempotent: any
 * order of pairwise syncs leaves every phone with the same data.
 *
 * - Players, matches, drafts: union by id; the higher `updatedAt` wins. An exact tie keeps the
 *   copy whose [SyncJson] encoding sorts last, which every phone computes alike.
 * - Deletions: the latest per (kind, id). A tombstone beats a match or draft last edited at or
 *   before it; a later edit brings the record back. Tombstones are kept for good (they are tiny),
 *   so a phone that syncs months later still learns of the deletion.
 * - Player merges: one per merged id; the later merge wins, then the larger target id.
 * - Rating constants: the later change wins.
 *
 * The result is canonical (sorted by id, records beaten by a tombstone dropped), so equal data
 * always encodes to equal bytes.
 */
object SyncMerge {
    fun merge(a: SyncDocument, b: SyncDocument): SyncDocument = normalize(
        SyncDocument(
            format = a.format,
            version = a.version,
            players = a.players + b.players,
            matches = a.matches + b.matches,
            drafts = a.drafts + b.drafts,
            deletions = a.deletions + b.deletions,
            playerMerges = a.playerMerges + b.playerMerges,
            eloConfig = newer(a.eloConfig, b.eloConfig, { it.updatedAt }, StampedEloConfig.serializer()),
        ),
    )

    fun mergeAll(documents: List<SyncDocument>): SyncDocument =
        documents.fold(SyncDocument.EMPTY) { acc, doc -> merge(acc, doc) }

    /** One copy per id, tombstoned records dropped, everything sorted: the canonical form of [doc]. */
    fun normalize(doc: SyncDocument): SyncDocument {
        val deletions = doc.deletions
            .groupBy { it.kind to it.id }
            .map { (_, list) -> list.maxBy { it.deletedAt } }
            .sortedWith(compareBy<Deletion>({ it.kind }, { it.id }))
        val deletedAt = deletions.associate { (it.kind to it.id) to it.deletedAt }
        fun alive(kind: DeletionKind, id: String, updatedAt: Long) =
            deletedAt[kind to id]?.let { updatedAt > it } ?: true
        return doc.copy(
            players = latest(doc.players, Player::id, Player::updatedAt, Player.serializer()),
            matches = latest(doc.matches, Match::id, Match::updatedAt, Match.serializer())
                .filter { alive(DeletionKind.MATCH, it.id, it.updatedAt) },
            drafts = latest(doc.drafts, DraftRecord::id, DraftRecord::updatedAt, DraftRecord.serializer())
                .filter { alive(DeletionKind.DRAFT, it.id, it.updatedAt) },
            deletions = deletions,
            playerMerges = doc.playerMerges
                .filter { it.fromId != it.toId }
                .groupBy { it.fromId }
                .map { (_, list) -> list.reduce(::newerMerge) }
                .sortedBy { it.fromId },
        )
    }

    /** The surviving one of two merges of the same id. */
    fun newerMerge(a: PlayerMerge, b: PlayerMerge): PlayerMerge = when {
        a.mergedAt != b.mergedAt -> if (a.mergedAt > b.mergedAt) a else b
        else -> if (a.toId >= b.toId) a else b
    }

    /** What changed from [before] to [after] on one phone (both canonical). */
    fun changes(before: SyncDocument, after: SyncDocument): SyncChanges {
        val players = diff(before.players, after.players, Player::id)
        val matches = diff(before.matches, after.matches, Match::id)
        val drafts = diff(before.drafts, after.drafts, DraftRecord::id)
        val merges = after.playerMerges.toSet() - before.playerMerges.toSet()
        return SyncChanges(
            playersAdded = players.added,
            playersUpdated = players.updated,
            matchesAdded = matches.added,
            matchesUpdated = matches.updated,
            matchesRemoved = matches.removed,
            draftsAdded = drafts.added,
            draftsUpdated = drafts.updated,
            draftsRemoved = drafts.removed,
            playersMerged = merges.size,
            ratingConstantsChanged = before.eloConfig.config != after.eloConfig.config,
        )
    }

    private fun <T> latest(items: List<T>, id: (T) -> String, stamp: (T) -> Long, serializer: KSerializer<T>): List<T> =
        items.groupBy(id).map { (_, copies) -> copies.reduce { a, b -> newer(a, b, stamp, serializer) } }.sortedBy(id)

    private fun <T> newer(a: T, b: T, stamp: (T) -> Long, serializer: KSerializer<T>): T {
        val sa = stamp(a)
        val sb = stamp(b)
        return when {
            sa > sb -> a
            sb > sa -> b
            a == b -> a
            else -> if (SyncJson.encodeToString(serializer, a) >= SyncJson.encodeToString(serializer, b)) a else b
        }
    }

    private data class Diff(val added: Int, val updated: Int, val removed: Int)

    private fun <T> diff(before: List<T>, after: List<T>, id: (T) -> String): Diff {
        val old = before.associateBy(id)
        val new = after.associateBy(id)
        return Diff(
            added = new.keys.count { it !in old },
            updated = new.count { (k, v) -> old[k]?.let { it != v } == true },
            removed = old.keys.count { it !in new },
        )
    }
}

/** What a sync changed on this phone, for the result summary ("+5 matches, +1 player, 2 updated"). */
data class SyncChanges(
    val playersAdded: Int = 0,
    val playersUpdated: Int = 0,
    val matchesAdded: Int = 0,
    val matchesUpdated: Int = 0,
    val matchesRemoved: Int = 0,
    val draftsAdded: Int = 0,
    val draftsUpdated: Int = 0,
    val draftsRemoved: Int = 0,
    val playersMerged: Int = 0,
    val ratingConstantsChanged: Boolean = false,
) {
    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = SyncChanges()
    }
}
