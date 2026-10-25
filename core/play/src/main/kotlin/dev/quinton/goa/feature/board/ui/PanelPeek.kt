package dev.quinton.goa.feature.board.ui

import dev.quinton.goa.core.model.match.Team

/**
 * What a hidden panel still says, in one line: "◆ Arien · Choose an action for Dangerous Current".
 * [label] (a hero's name, in [team]'s colour with its shape) leads [text]; [attention] marks a
 * question that waits for the person (a teal dot and a teal border).
 */
data class PanelPeek(
    val text: String,
    val label: String? = null,
    val team: Team? = null,
    val attention: Boolean = false,
)
