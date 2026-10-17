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
)
