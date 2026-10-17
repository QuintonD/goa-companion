package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.feature.rules.RulesText

/** Rules text is read at arm's length across the table: larger than body text, generous leading. */
@Composable
internal fun readingStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)

/** One printed line of an excerpt: a paragraph, a bullet item or a numbered item. */
internal sealed interface ExcerptLine {
    val text: String

    data class Paragraph(override val text: String) : ExcerptLine

    data class Bullet(override val text: String) : ExcerptLine

    data class Numbered(val number: String, override val text: String) : ExcerptLine
}

private val numbered = Regex("^(\\d+)\\.\\s+(.*)$")

internal fun excerptLines(text: String): List<ExcerptLine> = text.split('\n').filter { it.isNotBlank() }.map { line ->
    val match = numbered.find(line)
    when {
        line.startsWith("• ") -> ExcerptLine.Bullet(line.removePrefix("• "))
        match != null -> ExcerptLine.Numbered(match.groupValues[1], match.groupValues[2])
        else -> ExcerptLine.Paragraph(line)
    }
}

/** "p. 7" in a small bronze-outlined tag; TalkBack reads "rulebook page 7". */
@Composable
internal fun PageTag(pages: List<Int>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        RulesText.pages(pages),
        style = GoaTextStyles.numeric.copy(fontSize = 13.sp),
        color = primary,
        modifier = modifier
            .border(1.dp, GoaTheme.colors.engravedBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .semantics { contentDescription = RulesText.spokenPages(pages) },
    )
}

/**
 * A verbatim rulebook passage: its printed heading (if any), a bronze quote rule, the text with
 * bullets and numbered steps as printed, and the printed page.
 */
@Composable
internal fun ExcerptBlock(excerpt: RulesExcerpt, modifier: Modifier = Modifier, style: TextStyle = readingStyle()) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            excerpt.heading?.let { heading ->
                Text(
                    heading,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            } ?: Box(Modifier.weight(1f))
            PageTag(listOf(excerpt.page), Modifier.padding(start = Spacing.sm))
        }
        Row(Modifier.padding(top = Spacing.xs).height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                excerptLines(excerpt.text).forEach { line -> ExcerptLineText(line, style) }
                if (excerpt.table.isNotEmpty()) ExcerptTable(excerpt.table, style)
            }
        }
    }
}

private const val FIRST_COLUMN_WEIGHT = 1.4f

/** TalkBack reads each body row with its column names: "6-8 Players, Wave Counters 2 x 7, …". */
private fun spokenRow(header: List<String>, row: List<String>): String =
    row.mapIndexed { c, cell -> if (c == 0) cell else "${header.getOrElse(c) { "" }} $cell" }.joinToString(", ")

private val TableCellPadding = Spacing.sm

/**
 * A printed table, verbatim: header row in bold, one row per line. Columns share the width
 * (the first, with the longest labels, a little more) as long as every word fits its column;
 * with large text that would break a word ("Coun/ters"), so each body row becomes a small block
 * instead: its first cell as a label, then "Column: value" lines.
 */
@Composable
private fun ExcerptTable(table: List<List<String>>, style: TextStyle) {
    val header = table.first()
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = style.fontWeight)
    val headerStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Measured width, not BoxWithConstraints: the excerpt sits in an intrinsic-height row.
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.small)
            .padding(vertical = Spacing.xs),
    ) {
        val totalWeight = FIRST_COLUMN_WEIGHT + (header.size - 1)
        val fits = remember(table, widthPx, density, bodyStyle, headerStyle) {
            val padding = with(density) { (TableCellPadding * 2).toPx() }
            widthPx == 0 || table.withIndex().all { (i, row) ->
                row.withIndex().all { (c, cell) ->
                    val column = widthPx * (if (c == 0) FIRST_COLUMN_WEIGHT else 1f) / totalWeight - padding
                    val cellStyle = if (i == 0) headerStyle else bodyStyle
                    cell.split(' ').all { word -> measurer.measure(word, cellStyle).size.width <= column }
                }
            }
        }
        if (fits) {
            Column(Modifier.fillMaxWidth()) {
                table.forEachIndexed { i, row ->
                    val spoken = if (i == 0) header.joinToString(", ") else spokenRow(header, row)
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
                        row.forEachIndexed { c, cell ->
                            Text(
                                cell,
                                style = if (i == 0) headerStyle else bodyStyle,
                                // The first column holds the longest labels ("9-10 Players").
                                modifier = Modifier
                                    .weight(if (c == 0) FIRST_COLUMN_WEIGHT else 1f)
                                    .padding(horizontal = TableCellPadding, vertical = Spacing.xs),
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                table.drop(1).forEach { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics { contentDescription = spokenRow(header, row) }
                            .padding(horizontal = TableCellPadding, vertical = Spacing.xs),
                    ) {
                        Text(row.firstOrNull().orEmpty(), style = headerStyle)
                        row.drop(1).forEachIndexed { c, cell ->
                            Text("${header.getOrElse(c + 1) { "" }}: $cell", style = bodyStyle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcerptLineText(line: ExcerptLine, style: TextStyle) {
    when (line) {
        is ExcerptLine.Paragraph -> Text(line.text, style = style)
        is ExcerptLine.Bullet -> ListItemText(marker = "•", text = line.text, style = style)
        is ExcerptLine.Numbered -> ListItemText(marker = "${line.number}.", text = line.text, style = style)
    }
}

@Composable
private fun ListItemText(marker: String, text: String, style: TextStyle) {
    Row {
        Text(
            marker,
            style = style,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(text, style = style, modifier = Modifier.weight(1f))
    }
}

/** Our own words, never a quote: always labelled "Summary". */
@Composable
internal fun SummaryNote(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.secondary.copy(alpha = 0.08f), MaterialTheme.shapes.medium)
            .border(1.dp, colors.secondary.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(Spacing.md),
    ) {
        Text("SUMMARY", style = GoaTextStyles.overline, color = colors.secondary)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
    }
}

private const val ICON_PREFIX = "icon:"

/** The few game icons that appear in defeat-exception card text; others fall back to their name. */
private val cardIcons: Map<String, Pair<ImageVector, String>> = mapOf(
    "life" to (Icons.Filled.Favorite to "Life counter"),
    "coin" to (Icons.Filled.MonetizationOn to "Coin"),
    "marker_bounty" to (Icons.Filled.Paid to "Bounty marker"),
)

/** Card text markup ([CardText]) as printed: icons inline, reminders italic, keywords bold. */
internal fun cardTextAnnotated(text: String, reminderColor: Color): AnnotatedString = buildAnnotatedString {
    fun append(segments: List<CardText.Segment>) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is CardText.Segment.Text -> append(segment.value)

                is CardText.Segment.Icon -> {
                    val icon = cardIcons[segment.name]
                    if (icon == null) {
                        append(segment.name.replace('_', ' '))
                    } else {
                        val spoken = spokenIconLabel(icon.second, segments.getOrNull(index + 1))
                        appendInlineContent(ICON_PREFIX + segment.name, alternateText = spoken)
                    }
                }

                is CardText.Segment.Reminder -> withStyle(
                    SpanStyle(fontStyle = FontStyle.Italic, color = reminderColor),
                ) {
                    append("(${segment.value})")
                }

                is CardText.Segment.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(segment.segments)
                }

                CardText.Segment.Break -> append("\n")
            }
        }
    }
    append(CardText.parse(text))
}

/**
 * TalkBack text for an inline icon: blank when the printed word right after it already names it
 * ("{life} Life counter" is read once). Inline content needs non-empty alternate text.
 */
internal fun spokenIconLabel(label: String, next: CardText.Segment?): String {
    val following = (next as? CardText.Segment.Text)?.value?.trimStart().orEmpty()
    return if (following.startsWith(label, ignoreCase = true)) " " else label
}

@Composable
internal fun rememberCardIcons(tint: Color): Map<String, InlineTextContent> = remember(tint) {
    cardIcons.map { (name, icon) ->
        ICON_PREFIX + name to InlineTextContent(
            Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter),
        ) {
            Icon(icon.first, contentDescription = null, tint = tint)
        }
    }.toMap()
}
