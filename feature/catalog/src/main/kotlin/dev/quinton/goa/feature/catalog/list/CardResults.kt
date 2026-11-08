package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.component.CardTitle
import dev.quinton.goa.core.designsystem.component.ColourRibbon
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.CardResult
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.matchingParagraph
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge

/** One card in the results, a plate under its colour's ribbon: name, hero and kind, the paragraph that matched. */
@Composable
internal fun CardResultRow(result: CardResult, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val card = result.card
    val snippet = remember(card.text, query) { matchingParagraph(card.text, query) }
    CardPlateRow(
        card,
        kind = "${result.hero.name} · ${card.kindLabel(result.isHandicap)}",
        text = snippet,
        textLines = SNIPPET_LINES,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * A card as a row: a plate under its colour's ribbon, its name ([CardTitle]), its kind, its text (up to [textLines]
 * lines; the whole of it by default) and its Initiative. One TalkBack node that opens the card.
 */
@Composable
internal fun CardPlateRow(
    card: Card,
    kind: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textLines: Int = Int.MAX_VALUE,
) {
    GoaPlateTheme {
        val accent = cardColor(card.color)
        val shape = MaterialTheme.shapes.medium
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .slatePlate(shape, brackets = false)
                .clip(shape)
                .clickable(onClickLabel = "View card", role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(start = Spacing.sm, end = Spacing.md, bottom = Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            ColourRibbon(PlateColor.of(card.color))
            Spacer(Modifier.width(Spacing.sm))
            Column(
                Modifier.weight(1f).padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CardTitle(card.name, maxLines = 2)
                Text(
                    kind,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (text.isNotBlank()) {
                    CardTextView(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        iconTint = accent,
                        maxLines = textLines,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            InitiativeBadge(
                card.initiative,
                accent = accent,
                size = 28.dp,
                modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm),
            )
        }
    }
}

private const val SNIPPET_LINES = 3
