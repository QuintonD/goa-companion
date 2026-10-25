package dev.quinton.goa.feature.board.ui.cards

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.min
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT

/*
 * The shape of a card scan. Deck cards (and Handicap cards) are printed portrait: the bundled scans
 * are 729 × 1026 or 546 × 768 ([CARD_ASPECT]). Gydion's spell cards — a hero's `extras`, catalog id
 * `<hero>-spell-<name>` (tools/catalog-import CardMapping.spellId) — are printed landscape: 737 × 510.
 * A landscape card is never squeezed into a portrait frame: it keeps its own aspect, and in a row of
 * cards it keeps the row's height (so it is wider). CardShapeTest checks every bundled scan.
 */

/**
 * How wide [card] is when it lies among portrait cards [portraitWidth] wide: the same height, so a
 * landscape card is about twice as wide.
 */
internal fun cardWidth(card: Card?, portraitWidth: Dp): Dp =
    if (card?.isSpell == true) portraitWidth / CARD_ASPECT * LANDSCAPE_CARD_ASPECT else portraitWidth

/** The largest size of a card with [aspect] inside [maxWidth] × [maxHeight]. */
internal fun fitCard(aspect: Float, maxWidth: Dp, maxHeight: Dp): DpSize {
    val width = min(maxWidth, maxHeight * aspect)
    return DpSize(width, width / aspect)
}
