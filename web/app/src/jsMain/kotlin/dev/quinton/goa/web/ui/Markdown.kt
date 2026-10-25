package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The small Markdown subset the bundled documents use (`official/SOURCES.md`): headings,
 * paragraphs and pipe tables, with `code` and bare URLs inline. Pure, so it is unit-tested.
 */
object SimpleMarkdown {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block

        data class Paragraph(val text: String) : Block

        data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    }

    /** An inline run: plain text, `code`, or a link. */
    sealed interface Inline {
        data class Plain(val text: String) : Inline

        data class CodeSpan(val text: String) : Inline

        data class Link(val url: String) : Inline
    }

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        var table: MutableList<List<String>>? = null
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
            table?.let { rows -> if (rows.isNotEmpty()) blocks += Block.Table(rows.first(), rows.drop(1)) }
            table = null
        }
        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()

                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += Block.Heading(level, line.drop(level).trim())
                }

                line.startsWith("|") -> {
                    if (paragraph.isNotEmpty()) flush()
                    val cells = line.trim('|').split('|').map { it.trim() }
                    val separator = cells.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } }
                    if (!separator) (table ?: mutableListOf<List<String>>().also { table = it }).add(cells)
                }

                else -> {
                    if (table != null) flush()
                    paragraph += line
                }
            }
        }
        flush()
        return blocks
    }

    /** `code`, or a URL up to whitespace, ")" or "|", not ending in sentence punctuation. */
    private val inlinePattern = Regex("""`([^`]+)`|(https?://[^\s)|]*[^\s)|.,;:])""")

    fun inline(text: String): List<Inline> {
        val runs = mutableListOf<Inline>()
        var at = 0
        for (match in inlinePattern.findAll(text)) {
            if (match.range.first > at) runs += Inline.Plain(text.substring(at, match.range.first))
            val code = match.groups[1]?.value
            runs += if (code != null) Inline.CodeSpan(code) else Inline.Link(match.value)
            at = match.range.last + 1
        }
        if (at < text.length) runs += Inline.Plain(text.substring(at))
        return runs
    }
}

/** Renders [blocks]; tables become one card-like entry per row (phone width), header cells as labels. */
@Composable
fun MarkdownBlocks(blocks: List<SimpleMarkdown.Block>) {
    blocks.forEach { block ->
        when (block) {
            is SimpleMarkdown.Block.Heading -> H3({ classes("t-title-medium") }) { InlineText(block.text) }

            is SimpleMarkdown.Block.Paragraph -> P({ classes("t-body-medium", "c-variant") }) { InlineText(block.text) }

            is SimpleMarkdown.Block.Table -> Div({ classes("goa-md-table") }) {
                block.rows.forEach { row ->
                    Div({ classes("goa-md-row") }) {
                        row.forEachIndexed { i, cell ->
                            Div({ classes("goa-md-cell", "t-body-small") }) {
                                block.header.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { label ->
                                    Span({ classes("goa-md-label", "c-variant") }) { Text(label) }
                                }
                                Span({ classes("goa-md-value") }) { InlineText(cell) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineText(text: String) {
    SimpleMarkdown.inline(text).forEach { run ->
        when (run) {
            is SimpleMarkdown.Inline.Plain -> Text(run.text)

            is SimpleMarkdown.Inline.CodeSpan -> Code { Text(run.text) }

            is SimpleMarkdown.Inline.Link -> A(run.url, {
                target(ATarget.Blank)
                attr("rel", "noopener")
            }) { Text(run.url) }
        }
    }
}
