package dev.quinton.goa.web.rules

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.domain.rules.ExcerptLine
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Ol
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

/** "p. 7" in a small bronze-outlined tag; VoiceOver reads "rulebook page 7". */
@Composable
internal fun PageTag(pages: List<Int>) {
    Span({
        classes("goa-rules-page", "t-numeric")
        attr("aria-label", RulesText.spokenPages(pages))
        attr("role", "note")
    }) { Text(RulesText.pages(pages)) }
}

/**
 * A verbatim rulebook passage: its printed heading (if any), a bronze quote rule, the text with
 * bullets and numbered steps as printed, a printed table, and the printed page. [compact] is the
 * ladder sheet's smaller reading size.
 */
@Composable
internal fun ExcerptBlock(excerpt: RulesExcerpt, compact: Boolean = false) {
    Div({ classes("goa-rules-excerpt") }) {
        Div({ classes("goa-rules-excerpt-head") }) {
            val heading = excerpt.heading
            if (heading != null) {
                H3({ classes("goa-rules-heading") }) { Text(heading) }
            } else {
                Span({ classes("goa-rules-spacer") })
            }
            PageTag(listOf(excerpt.page))
        }
        Div({ classes("goa-rules-quote", if (compact) "t-printed" else "goa-rules-reading") }) {
            ExcerptLines(RulesText.excerptLines(excerpt.text))
            if (excerpt.table.isNotEmpty()) ExcerptTable(excerpt.table)
        }
    }
}

/** Paragraphs as `<p>`, runs of bullets as `<ul>` and numbered steps as `<ol>` (VoiceOver counts them). */
@Composable
private fun ExcerptLines(lines: List<ExcerptLine>) {
    var i = 0
    while (i < lines.size) {
        when (val line = lines[i]) {
            is ExcerptLine.Paragraph -> {
                P { Text(line.text) }
                i++
            }

            is ExcerptLine.Bullet -> {
                val run = lines.drop(i).takeWhile { it is ExcerptLine.Bullet }
                Ul({ classes("goa-rules-items") }) {
                    run.forEach { item -> Li({ attr("data-marker", "•") }) { Text(item.text) } }
                }
                i += run.size
            }

            is ExcerptLine.Numbered -> {
                val run = lines.drop(i).takeWhile { it is ExcerptLine.Numbered }.map { it as ExcerptLine.Numbered }
                Ol({ classes("goa-rules-items") }) {
                    run.forEach { item ->
                        Li({
                            attr("value", item.number)
                            attr("data-marker", "${item.number}.")
                        }) { Text(item.text) }
                    }
                }
                i += run.size
            }
        }
    }
}

/**
 * A printed table, verbatim. Columns while every word fits; at large text (goa.css container query
 * in rem, so it follows Dynamic Type) each body row becomes a small block instead: its first cell as
 * a label, then "Column: value" lines — never a word broken across lines.
 */
@Composable
private fun ExcerptTable(table: List<List<String>>) {
    val header = table.first()
    val body = table.drop(1)
    Div({ classes("goa-rules-table-wrap") }) {
        Table({ classes("goa-rules-table") }) {
            Thead { Tr { header.forEach { Th({ attr("scope", "col") }) { Text(it) } } } }
            Tbody {
                body.forEach { row ->
                    Tr {
                        row.forEachIndexed { c, cell ->
                            if (c == 0) Th({ attr("scope", "row") }) { Text(cell) } else Td { Text(cell) }
                        }
                    }
                }
            }
        }
        Div({ classes("goa-rules-table-blocks") }) {
            body.forEach { row ->
                Div({
                    classes("goa-rules-table-block")
                    attr("role", "group")
                    attr("aria-label", RulesText.spokenTableRow(header, row))
                }) {
                    B { Text(row.firstOrNull().orEmpty()) }
                    row.drop(1).forEachIndexed { c, cell ->
                        Div { Text("${header.getOrElse(c + 1) { "" }}: $cell") }
                    }
                }
            }
        }
    }
}

/** Our own words, never a quote: always labelled "Summary". */
@Composable
internal fun SummaryNote(text: String) {
    Div({ classes("goa-rules-summary") }) {
        Div({ classes("t-overline") }) { Text("Summary") }
        P({ classes("t-body-large") }) { Text(text) }
    }
}

/** The few game icons that appear in defeat-exception card text; others fall back to their name. */
private val CardIcons: Map<String, Pair<String, String>> = mapOf(
    "life" to ("life" to "Life counter"),
    "coin" to ("coin" to "Coin"),
    "marker_bounty" to ("bounty" to "Bounty marker"),
)

/** Card text markup ([CardText]) as printed: icons inline, reminders italic, keywords bold. */
@Composable
internal fun CardTextView(text: String) {
    Segments(CardText.parse(text))
}

@Composable
private fun Segments(segments: List<CardText.Segment>) {
    segments.forEachIndexed { index, segment ->
        when (segment) {
            is CardText.Segment.Text -> Text(segment.value)

            is CardText.Segment.Icon -> {
                val icon = CardIcons[segment.name]
                if (icon == null) {
                    Text(segment.name.replace('_', ' '))
                } else {
                    val spoken = RulesText.spokenIconLabel(icon.second, segments.getOrNull(index + 1)).trim()
                    Span({
                        classes("goa-rules-inline-icon")
                        if (spoken.isNotEmpty()) {
                            attr("role", "img")
                            attr("aria-label", spoken)
                        }
                    }) { Icon(icon.first, IconSize.SMALL) }
                }
            }

            is CardText.Segment.Reminder -> I({ classes("goa-rules-reminder") }) { Text("(${segment.value})") }

            is CardText.Segment.Strong -> B { Segments(segment.segments) }

            CardText.Segment.Break -> Br()
        }
    }
}
