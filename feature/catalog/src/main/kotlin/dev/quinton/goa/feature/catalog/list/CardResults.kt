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
import dev.quinton.goa.core.designsystem.component.ColourRibbon
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.CardResult
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.matchingParagraph
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge

/** One card in the search results, a plate under its colour's ribbon: name, hero and kind, the paragraph that matched. */
@Composable
internal fun CardResultRow(result: CardResult, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val card = result.card
    val snippet = remember(card.text, query) { matchingParagraph(card.text, query) }
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
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${result.hero.name} · ${card.kindLabel(result.isHandicap)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snippet.isNotBlank()) {
                    CardTextView(
                        snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        iconTint = accent,
                        maxLines = SNIPPET_LINES,
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
