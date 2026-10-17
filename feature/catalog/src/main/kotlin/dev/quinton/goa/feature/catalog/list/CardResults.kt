package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.feature.catalog.model.CardResult
import dev.quinton.goa.feature.catalog.model.CatalogSearchIndex
import dev.quinton.goa.feature.catalog.ui.CardTextView
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.kindLabel

/**
 * The paragraph of card [text] (markup) that contains the query, so the result shows the
 * words that matched; the first paragraph when the match is in the card's name. A lead-in such
 * as "Choose one —" says nothing on its own, so it comes with the paragraphs after it (the
 * options; the row shows as many lines as fit).
 */
internal fun matchingParagraph(text: String, query: String): String {
    val paragraphs = text.split('\n').filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) return ""
    val tokens = CatalogSearchIndex.tokenize(query)
    val plain = paragraphs.map { CatalogSearchIndex.normalize(CardText.plain(it)).trim() }
    val phrase = tokens.joinToString(" ")
    val index = if (tokens.isEmpty()) {
        0
    } else {
        plain.indexOfFirst { phrase in it }.takeIf { it >= 0 }
            ?: plain.indexOfFirst { p -> tokens.any { it in p } }.takeIf { it >= 0 }
            ?: 0
    }
    val leadIn = plain[index].endsWith('—') || plain[index].endsWith(':') || plain[index].endsWith("--")
    return if (leadIn && index < paragraphs.lastIndex) {
        paragraphs.subList(index, paragraphs.size).joinToString("\n")
    } else {
        paragraphs[index]
    }
}

/** One card in the search results: name, hero and kind, and the paragraph that matched. */
@Composable
internal fun CardResultRow(result: CardResult, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val card = result.card
    val accent = cardColor(card.color)
    val shape = MaterialTheme.shapes.medium
    val snippet = remember(card.text, query) { matchingParagraph(card.text, query) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawRect(accent, size = Size(ACCENT_WIDTH.toPx(), size.height)) }
            .border(1.dp, GoaTheme.colors.engravedBorder, shape)
            .clickable(onClickLabel = "View card", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(start = Spacing.md + ACCENT_WIDTH, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        InitiativeBadge(card.initiative, accent = accent, size = 28.dp, modifier = Modifier.padding(start = Spacing.sm))
    }
}

private val ACCENT_WIDTH = 4.dp
private const val SNIPPET_LINES = 3
