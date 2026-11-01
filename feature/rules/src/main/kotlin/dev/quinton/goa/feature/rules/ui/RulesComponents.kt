package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rules.ExcerptLine
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.rules.RulesExcerpt

/**
 * Rules text is read at arm's length across the table: larger than body text, generous leading, in
 * the printed face (Crimson Pro, the rulebook's Minion look) because every excerpt is verbatim.
 */
@Composable
internal fun readingStyle(): TextStyle = GoaTextStyles.printed.copy(fontSize = 20.sp, lineHeight = 27.sp)

/** The colours of a passage: on the parchment page, or on one of the rulebook's coloured boxes. */
private class ExcerptInk(val text: Color, val head: Color, val edge: Color)

@Composable
private fun parchmentInk() = GoaTheme.colors.let { ExcerptInk(it.onParchment, it.parchmentInk, it.parchmentEdge) }

/** "p. 7" in a small outlined tag; TalkBack reads "rulebook page 7". */
@Composable
internal fun PageTag(
    pages: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    edge: Color = GoaTheme.colors.engravedBorder,
) {
    Text(
        RulesText.pages(pages),
        style = GoaTextStyles.numeric.copy(fontSize = 13.sp),
        color = color,
        modifier = modifier
            .border(1.dp, edge, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .semantics { contentDescription = RulesText.spokenPages(pages) },
    )
}

/**
 * A verbatim rulebook passage set as the book sets it: on the page, or in the blue, green or orange
 * box it is printed in ([RulesBoxes]). Its printed heading (if any), the text with bullets and numbered
 * steps as printed, a printed table, and the printed page. Goes on the parchment ([ParchmentPage]).
 */
@Composable
internal fun ExcerptBlock(
    excerpt: RulesExcerpt,
    modifier: Modifier = Modifier,
    style: TextStyle = readingStyle(),
    headStyle: TextStyle = GoaTextStyles.sectionHead.copy(fontSize = 22.sp, lineHeight = 28.sp),
) {
    val box = remember(excerpt) { RulesBoxes.of(excerpt) }
    if (box == null) {
        ExcerptBody(excerpt, parchmentInk(), style, headStyle, modifier.fillMaxWidth())
    } else {
        val ink = ExcerptInk(box.onGround, box.onGround, box.onGround.copy(alpha = 0.6f))
        val boxStyle = if (box.italic) style.copy(fontStyle = FontStyle.Italic) else style
        ExcerptBody(
            excerpt,
            ink,
            boxStyle,
            headStyle,
            modifier
                .fillMaxWidth()
                .background(box.ground, RoundedCornerShape(2.dp))
                .padding(horizontal = 11.dp, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun ExcerptBody(
    excerpt: RulesExcerpt,
    ink: ExcerptInk,
    style: TextStyle,
    headStyle: TextStyle,
    modifier: Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides ink.text) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                excerpt.heading?.let { heading ->
                    // As the rulebook sets its heads: copper small caps.
                    Text(
                        heading,
                        style = headStyle,
                        color = ink.head,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                } ?: Box(Modifier.weight(1f))
                PageTag(listOf(excerpt.page), Modifier.padding(start = Spacing.sm), ink.head, ink.edge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RulesText.excerptLines(excerpt.text).forEach { line -> ExcerptLineText(line, style, ink.head) }
                if (excerpt.table.isNotEmpty()) ExcerptTable(excerpt.table, style, ink.edge)
            }
        }
    }
}

/** A piece of the parchment page, for a passage on a dark plate (the ladder sheet); a box brings its own ground. */
@Composable
internal fun ExcerptOnPlate(excerpt: RulesExcerpt, modifier: Modifier = Modifier) {
    val style = GoaTextStyles.printed
    val head = GoaTextStyles.sectionHead.copy(fontSize = 18.sp, lineHeight = 24.sp)
    if (remember(excerpt) { RulesBoxes.of(excerpt) } != null) {
        ExcerptBlock(excerpt, modifier, style, head)
    } else {
        ExcerptBlock(
            excerpt,
            modifier
                .fillMaxWidth()
                .background(GoaTheme.colors.parchment, RoundedCornerShape(2.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style,
            head,
        )
    }
}

private const val FIRST_COLUMN_WEIGHT = 1.4f

private val TableCellPadding = Spacing.sm

/**
 * A printed table, verbatim: header row in bold, one row per line. Columns share the width
 * (the first, with the longest labels, a little more) as long as every word fits its column;
 * with large text that would break a word ("Coun/ters"), so each body row becomes a small block
 * instead: its first cell as a label, then "Column: value" lines.
 */
@Composable
private fun ExcerptTable(table: List<List<String>>, style: TextStyle, edge: Color) {
    val header = table.first()
    val bodyStyle = GoaTextStyles.printed.copy(fontWeight = style.fontWeight)
    val headerStyle = GoaTextStyles.printed.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Measured width, not BoxWithConstraints: the excerpt sits in an intrinsic-height row.
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .border(1.dp, edge, MaterialTheme.shapes.small)
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
                    val spoken = if (i == 0) header.joinToString(", ") else RulesText.spokenTableRow(header, row)
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
                            .clearAndSetSemantics { contentDescription = RulesText.spokenTableRow(header, row) }
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
private fun ExcerptLineText(line: ExcerptLine, style: TextStyle, markerColor: Color) {
    when (line) {
        is ExcerptLine.Paragraph -> Text(line.text, style = style)
        is ExcerptLine.Bullet -> ListItemText("•", line.text, style, markerColor)
        is ExcerptLine.Numbered -> ListItemText("${line.number}.", line.text, style, markerColor)
    }
}

@Composable
private fun ListItemText(marker: String, text: String, style: TextStyle, markerColor: Color) {
    Row {
        Text(
            marker,
            style = style,
            color = markerColor,
            modifier = Modifier.width(28.dp),
        )
        Text(text, style = style, modifier = Modifier.weight(1f))
    }
}

/** Our own words, never a quote: always labelled "Summary", on a plate in our own face, unlike the book. */
@Composable
internal fun SummaryNote(text: String, modifier: Modifier = Modifier) {
    GoaPlate(modifier.fillMaxWidth()) {
        Text("SUMMARY", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** The rulebook's page as a background: the parchment, its ink as the content colour, in both themes. */
@Composable
internal fun ParchmentPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = GoaTheme.colors
    CompositionLocalProvider(LocalContentColor provides colors.onParchment) {
        Column(modifier.background(colors.parchment), content = content)
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
                        val spoken = RulesText.spokenIconLabel(icon.second, segments.getOrNull(index + 1))
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
