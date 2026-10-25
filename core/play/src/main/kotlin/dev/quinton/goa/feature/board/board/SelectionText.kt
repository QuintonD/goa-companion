package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero

/*
 * The sandbox selection's words (feature:board SelectionPanel), shared by the phone and the web app.
 */

/** The selected piece's name: the hero, the minion's type, or the token's label. */
fun pieceTitle(state: BoardUiState.Ready, piece: BoardPiece): String = when (piece) {
    is HeroPiece -> state.heroes.hero(piece.heroId).name
    is MinionPiece -> BoardText.minion(piece.type)
    is TokenPiece -> "Token: ${piece.label}"
}

/** Where the selected piece is: its Zone ("in the tray" while it waits) and Battle Zone. */
fun pieceWhere(state: BoardUiState.Ready, piece: BoardPiece): String {
    val zone = piece.at?.let { state.geometry.zoneOf(it) }
    val battleLane = state.map.lanes.firstOrNull {
        (state.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone
    }
    return listOfNotNull(
        if (piece.at == null) "in the tray" else BoardText.zone(state.map, zone),
        battleLane?.let { BoardText.battleZone(state.map, it.id) },
    ).joinToString(" · ")
}

/** The panel header's line: team, a minion's Lane (double-lane maps, p.20), Zone and Battle Zone. */
fun pieceSubtitle(state: BoardUiState.Ready, piece: BoardPiece): String {
    val team: Team? = when (piece) {
        is HeroPiece -> piece.team
        is MinionPiece -> piece.team
        is TokenPiece -> piece.team
    }
    // A minion on a double-lane map is bound to its Lane (p.20).
    val boundTo = (piece as? MinionPiece)?.let { BoardText.lane(state.map, state.geometry.laneOf(it)) }
    return listOfNotNull(
        team?.let { "${BoardText.team(it)} team" },
        boundTo?.let { "$it minion" },
        pieceWhere(state, piece),
    ).joinToString(" · ")
}

/** "Gold", "Silver", "Red"… a card's colour in words. */
fun cardColorWord(color: CardColor): String = when (color) {
    CardColor.GOLD -> "Gold"
    CardColor.SILVER -> "Silver"
    CardColor.RED -> "Red"
    CardColor.GREEN -> "Green"
    CardColor.BLUE -> "Blue"
    CardColor.PURPLE -> "Purple"
    CardColor.OTHER -> "Other"
}

/** "Basic Gold", "Tier II Red"; a spell card "Gold spell", "Tier II Red spell", "Purple spell" (Wish). */
fun cardKindLabel(card: Card): String = if (card.spellCard) {
    listOfNotNull(
        card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)}" },
        cardColorWord(card.color),
        "spell",
    ).joinToString(" ")
} else {
    "${tierLabel(card)} ${cardColorWord(card.color)}"
}

/** "Basic", "Tier I", "Tier II", "Tier III", "Ultimate"; a spell card "Spell", "Tier II spell". */
fun tierLabel(card: Card): String = when {
    // Spellbook pages carry their printed spell level, null for the gold cantrips (catalog Card.tier).
    card.spellCard -> card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)} spell" } ?: "Spell"

    card.color == CardColor.GOLD || card.color == CardColor.SILVER -> "Basic"

    card.color == CardColor.PURPLE || card.tier == ULTIMATE_TIER -> "Ultimate"

    else -> card.tier?.let { tier -> "Tier ${tierNumeral(tier)}" } ?: "Other"
}

/** A spell card of a hero's spellbook (Gydion's 22, catalog id `<hero>-spell-<name>`). */
private val Card.spellCard: Boolean get() = id.startsWith("$heroId-spell-")

private fun tierNumeral(tier: Int): String = listOf("I", "II", "III", "IV").getOrElse(tier - 1) { tier.toString() }

private const val ULTIMATE_TIER = 4
