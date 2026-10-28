package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.feature.board.play.review.ReviewTab

/** "Round 3, turn 4: The other team pushed the lane." → "The other team pushed the lane." */
fun withoutWhen(text: String): String =
    text.replaceFirst(Regex("^Round \\d+, turn \\d+: "), "").replaceFirstChar { it.uppercase() }

/** The sheets the game opens (hoisted so screenshots can open one). */
sealed interface PlaySheet {
    data object Log : PlaySheet

    data class Hero(val heroId: String) : PlaySheet

    /** The game-state review (ADR-037). */
    data class Review(val tab: ReviewTab = ReviewTab.NOW) : PlaySheet

    /** A hero's spellbook (Gydion, ADR-040): from the table's Spellbook control or the hero sheet. */
    data class Spellbook(val heroId: String) : PlaySheet
}

/** The game's title for [room] dp of width at font scale 1. */
fun roundTitle(round: Int, turn: Int, room: Float): String = when {
    room >= FULL_TITLE_DP -> "Round $round · Turn $turn"
    room >= SHORT_TITLE_DP -> "R$round · Turn $turn"
    else -> "R$round · T$turn"
}

const val FULL_TITLE_DP = 400f

const val SHORT_TITLE_DP = 300f
