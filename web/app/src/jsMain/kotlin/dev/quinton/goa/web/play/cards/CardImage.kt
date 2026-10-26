package dev.quinton.goa.web.play.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.board.tierLabel
import dev.quinton.goa.feature.board.ui.cards.cardAspect
import dev.quinton.goa.feature.board.ui.cards.isSpell
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * A card as it lies on the table (Android's TableCard): the official card scan, a designed face
 * (name, initiative, tier) when there is no image or it can't load, or the card back when [card] is
 * null (facedown). [width] is a portrait card's width in CSS px; a landscape card (Gydion's spells)
 * keeps the same height and is wider, never squeezed. Decorative: the caller labels it.
 */
@Composable
fun CardImage(card: Card?, width: Int, extraClass: String? = null) {
    val aspect = cardAspect(card)
    val w = if (card?.isSpell == true) (width / cardAspect(null) * aspect).toInt() else width
    Div({
        classes("goa-play-card")
        extraClass?.let { classes(it) }
        attr("aria-hidden", "true")
        style {
            property("width", "${w}px")
            property("aspect-ratio", "$aspect")
            property("border-radius", "${(width * CORNER).toInt()}px")
        }
    }) {
        when {
            card == null -> Div({ classes("goa-card-back") }) {
                Span({ classes("goa-card-back-mark") })
            }

            else -> CardFace(card)
        }
    }
}

@Composable
private fun CardFace(card: Card) {
    var failed by remember(card.id) { mutableStateOf(card.image == null) }
    if (failed) {
        Div({ classes("goa-card-face", "c-${card.color.name.lowercase()}") }) {
            Span({ classes("goa-card-face-init", "t-numeric") }) { Text(card.initiative?.toString().orEmpty()) }
            Span({ classes("goa-card-face-name") }) { Text(card.name) }
            Span({ classes("goa-card-face-tier") }) { Text(tierLabel(card)) }
        }
    } else {
        Img(card.image!!, alt = "", attrs = {
            classes("goa-card-img")
            attr("loading", "lazy")
            attr("decoding", "async")
            attr("draggable", "false")
            addEventListener("error") { failed = true }
        })
    }
}

/** A card's rounded corner as a share of its width. */
private const val CORNER = 0.06f
