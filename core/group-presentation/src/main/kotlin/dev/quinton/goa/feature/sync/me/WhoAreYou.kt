package dev.quinton.goa.feature.sync.me

import dev.quinton.goa.core.model.player.Player

data class WhoAreYouUiState(
    val loading: Boolean = true,
    /** Active players, by name. */
    val players: List<Player> = emptyList(),
    val meId: String? = null,
    val newName: String = "",
    val nameError: String? = null,
    /** Chosen: the screen closes. */
    val done: Boolean = false,
)

/** "Which player are you?" words and limits, shared by the phone and the web app (ADR-043). */
object WhoAreYouText {
    const val MAX_NAME = 24
    const val ENTER_NAME = "Enter your name"

    fun taken(name: String) = "“$name” is already in the group. If that's you, pick it above."
}
