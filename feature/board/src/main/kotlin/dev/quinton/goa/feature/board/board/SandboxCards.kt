package dev.quinton.goa.feature.board.board

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.ui.cards.CardFocusHost
import dev.quinton.goa.feature.board.ui.cards.CardSelection
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState

/**
 * "Read the cards": the hero's cards in the card focus viewer, large, swiping from one to the next
 * (opening on the card measured with, if any). Tapping the focused card again (or "Measure with …")
 * measures with it. The chips stay a single tap: measuring shows its reach on the map at once.
 */
@Composable
internal fun ReadCardsButton(hero: BoardHero, chosenId: String?, onMeasure: (String) -> Unit) {
    val cards: List<Card> = hero.cards
    if (cards.isEmpty()) return
    val focus = rememberCardFocusState()
    TextButton(
        onClick = {
            focus.open(
                cards.map { FocusCard(it) },
                index = cards.indexOfFirst { it.id == chosenId }.coerceAtLeast(0),
                selection = CardSelection(
                    action = { "Measure with ${it.name}" },
                    context = "The app measures; it never applies a card's effect.",
                    onConfirm = { i -> cards.getOrNull(i)?.let { onMeasure(it.id) } },
                ),
                title = "${hero.name}'s cards",
            )
        },
        contentPadding = PaddingValues(horizontal = Spacing.sm),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.Style, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text("Read the cards")
    }
    CardFocusHost(focus)
}
