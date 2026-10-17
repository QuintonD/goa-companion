package dev.quinton.goa.core.model.player

import kotlinx.serialization.Serializable

/** A member of the play group. No accounts; [id] is a UUID string. */
@Serializable
data class Player(
    val id: String,
    val name: String,
    /** ARGB colour, e.g. 0xFF3FC1B0. */
    val color: Long,
    /** Index into the original avatar crest set (core:designsystem). */
    val avatarId: Int,
    val createdAt: Long,
    val archived: Boolean = false,
    /**
     * When this player was last edited (epoch ms); group sync keeps the later edit (ADR-025).
     * Set by the repository on every local write; records from before sync count as last edited
     * when they were created.
     */
    val updatedAt: Long = createdAt,
)
