package dev.quinton.goa.core.model.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a [Deletion] removed. Players are never deleted (they are archived or merged). */
@Serializable
enum class DeletionKind {
    @SerialName("match")
    MATCH,

    @SerialName("draft")
    DRAFT,
}

/**
 * A tombstone: [id] of [kind] was deleted at [deletedAt] (epoch ms). Group sync carries it so a
 * deletion reaches every phone; it beats any copy of the record last edited at or before
 * [deletedAt], and a later edit brings the record back (ADR-025, DOMAIN.md *Group sync*).
 */
@Serializable
data class Deletion(
    val kind: DeletionKind,
    val id: String,
    val deletedAt: Long,
)

/**
 * Player [fromId] is the same person as [toId] (a duplicate, typically one name created on two
 * phones before they synced). The log is never rewritten: every read resolves [fromId] to [toId],
 * so ratings stay derivable from the match log on every phone (DOMAIN.md *Duplicate players*).
 */
@Serializable
data class PlayerMerge(
    val fromId: String,
    val toId: String,
    val mergedAt: Long,
)
