package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.CardText

/**
 * Converts source card text into the app's inline markup ([CardText]): `{icon}`, `~(reminder)`,
 * `\n` paragraph breaks, a leading `• ` for "Choose one —" options and `**bold**` keywords.
 * Bold comes from source 1 (Stats of Atlantis); goa2-faq bold is not used (see [aligned]).
 */
object TextMarkup {
    const val BULLET = "• "

    private val colourSuffix = Regex("_(red|blue|green|gold|silver|purple)$")
    private val iconToken = Regex("::([a-z_]+)::")

    /** Source icon token (without the `::`) → [CardText.ICONS] name, or null when unknown. */
    fun iconName(token: String): String? {
        if (token in CardText.ICONS) return token
        val base = token.replace(colourSuffix, "")
        val mapped = when (base) {
            "area" -> "radius"
            "life_counters" -> "life"
            "defense_skill" -> "defense"
            else -> base
        }
        return mapped.takeIf { it in CardText.ICONS }
    }

    private fun icons(text: String, unknown: MutableSet<String>): String = iconToken.replace(text) { m ->
        val name = iconName(m.groupValues[1])
        if (name == null) {
            unknown += m.groupValues[1]
            m.value
        } else {
            "{$name}"
        }
    }

    /** Removes SoA's italic toggles that are not reminder openers; keeps `**` bold markers. */
    private fun inlineSoa(line: String, unknown: MutableSet<String>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '~' && (i + 1 >= line.length || line[i + 1] != '(')) {
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return icons(sb.toString(), unknown)
    }

    /**
     * Stats of Atlantis text mirrors the printed line wraps. Lines are joined into paragraphs:
     * `>>` starts an option bullet, `>` continues it, `---` is a divider, a `~(` line is a
     * reminder paragraph, and a line that follows sentence-ending punctuation and starts with a
     * capital starts a new paragraph. Everything else is a wrapped continuation.
     */
    fun fromSoa(description: String, unknownIcons: MutableSet<String> = mutableSetOf()): String {
        val paragraphs = mutableListOf<StringBuilder>()
        var forceBreak = true
        var previousLine = ""
        val lines = description.replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line == "---") {
                forceBreak = true
                continue
            }
            val (startsParagraph, content) = when {
                line.startsWith(">>") -> true to BULLET + line.removePrefix(">>").trim()
                line.startsWith(">") -> false to line.removePrefix(">").trim()
                line.startsWith("~(") -> true to line
                else -> startsNewParagraph(previousLine, line) to line
            }
            val text = inlineSoa(content, unknownIcons)
            if (forceBreak || startsParagraph || paragraphs.isEmpty()) {
                paragraphs += StringBuilder(text)
            } else {
                paragraphs.last().append(' ').append(text)
            }
            forceBreak = false
            previousLine = line
        }
        return tidy(paragraphs.joinToString("\n") { tidyBold(it.toString()) })
    }

    private val boldSpan = Regex("""\*\*(.+?)\*\*""")

    /** `** This turn: **` → `**This turn:**`: no padding inside a bold span. */
    fun tidyBold(paragraph: String): String = boldSpan.replace(paragraph) { m ->
        val inner = m.groupValues[1].trim()
        if (inner.isEmpty()) m.value else "${CardText.STRONG}$inner${CardText.STRONG}"
    }

    private val iconLabel = Regex("""^::[a-z_]+:::""")

    private fun startsNewParagraph(previous: String, line: String): Boolean {
        val prev = previous.replace("**", "").replace(iconToken, "").trimEnd()
        if (prev.isEmpty()) return true
        // `{icon}: …` lines are alternatives keyed by a symbol (Ignatia's coin, Snorri's runes).
        if (iconLabel.containsMatchIn(line)) return true
        // A colon usually continues the sentence on the card ("After the attack: If …").
        val endsSentence = prev.last() in ".!?)" || prev.endsWith("—")
        val first = line.removePrefix("**").firstOrNull() ?: return false
        return endsSentence && (first.isUpperCase() || first == '"' || first == '“')
    }

    /**
     * goa2-faq markdown: `_(…)_` reminder, `- ` option bullets, `::icon::`. Its `**bold**` is
     * dropped: bold keywords come from source 1 (see [aligned]).
     */
    fun fromFaq(text: String, unknownIcons: MutableSet<String> = mutableSetOf()): String {
        // `---` is a printed divider between alternatives: a paragraph break, not words.
        val lines = text.replace("\r\n", "\n").replace(Regex("""\s*---\s*"""), "\n").split('\n').map { it.trim() }
        val paragraphs = lines.filter { it.isNotEmpty() }.map { line ->
            val listItem = line.startsWith("- ") || line.startsWith("• ")
            val body = if (listItem) line.substring(2).trim() else line
            // `- ::rune_horn::: …` (Snorri): the card prints the icon label, no bullet.
            val bullet = listItem && !iconLabel.containsMatchIn(body)
            val converted = body
                .replace(Regex("""_\((.*?)\)_"""), "~($1)")
                .replace("**", "")
            val text = (if (bullet) BULLET else "") + icons(converted, unknownIcons)
            text to (listItem || startsBlock(body))
        }
        return tidy(joinWraps(paragraphs))
    }

    /**
     * goa2-faq keeps some printed line wraps as line breaks in mid-sentence ("discard that
     * card;\notherwise …"). A break is kept only when the previous line ends a sentence or a
     * keyword (`. : ) ! ? —`) or the next line starts a block (option bullet, reminder, icon
     * label, bold keyword; the flag in [lines]); every other break is a wrap, joined with a space.
     */
    fun joinWraps(lines: List<Pair<String, Boolean>>): String {
        val out = mutableListOf<StringBuilder>()
        for ((line, startsBlock) in lines) {
            val previous = out.lastOrNull()?.toString()?.replace(CardText.STRONG, "")?.trimEnd()
            if (previous == null || startsBlock || endsBlock(previous)) {
                out += StringBuilder(line)
            } else {
                out.last().append(' ').append(line)
            }
        }
        return out.joinToString("\n")
    }

    private fun endsBlock(line: String): Boolean = line.isEmpty() || line.last() in ".:)!?—"

    /** Raw goa2-faq line (bold and icon tokens still in place) that starts its own block. */
    private fun startsBlock(line: String): Boolean =
        line.startsWith("_(") || line.startsWith("~(") || line.startsWith("::") || line.startsWith("**")

    /**
     * FAQ rulings are shown as plain paragraphs: markdown bold and `---` dividers are dropped,
     * `- ` list items become `• `, `::icon::` becomes the icon's name.
     */
    fun plainRuling(text: String): String = text.replace("\r\n", "\n")
        .replace("**", "")
        .replace(iconToken) { (iconName(it.groupValues[1]) ?: it.groupValues[1]).replace('_', ' ') }
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "---" }
        .joinToString("\n") { if (it.startsWith("- ")) BULLET + it.removePrefix("- ").trim() else it }

    /** Collapses spaces and removes padding inside parentheses: `( x )` → `(x)`. */
    fun tidy(text: String): String = text.split('\n').joinToString("\n") { p ->
        p.replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("""\(\s+"""), "(")
            .replace(Regex("""\s+\)"""), ")")
            .trim()
    }.trim()

    /** Typography-insensitive form of one word, used to decide whether two texts agree. */
    fun normalizeWord(word: String): String = word
        .replace('“', '"').replace('”', '"').replace('„', '"')
        .replace('‘', '\'').replace('’', '\'')
        .replace('—', '-').replace('–', '-')
        .replace("…", "...")

    private fun words(markup: String): List<Pair<String, Boolean>> = markup.split('\n').flatMap { paragraph ->
        paragraph.split(' ').filter { it.isNotEmpty() }.mapIndexed { index, w -> w to (index == 0) }
    }

    /** The comparable word sequence (paragraph breaks, bullets and bold markers ignored). */
    fun comparable(markup: String): List<String> = words(markup)
        .map { normalizeWord(it.first.replace(CardText.STRONG, "")) }
        .filter { it.isNotEmpty() && it != "•" }

    fun agrees(a: String, b: String): Boolean = comparable(a) == comparable(b)

    /**
     * When [primary] and [reference] contain the same words, returns the reference's wording and
     * paragraph structure (it is not constrained by the printed line wraps) with the primary's
     * bold keywords. Null when the words differ or the primary's bold cannot be carried over
     * (a marker inside a word, or a bold span the reference splits across paragraphs).
     */
    fun aligned(primary: String, reference: String): String? {
        if (!agrees(primary, reference)) return null
        return withBoldOf(primary, reference.replace(CardText.STRONG, ""))
    }

    /** Whether a word opens and/or closes a bold span; null if a marker sits inside the word. */
    private data class WordBold(val opens: Boolean, val closes: Boolean)

    private fun wordBold(word: String): WordBold? {
        val rest = word.removePrefix(CardText.STRONG)
        val core = rest.removeSuffix(CardText.STRONG)
        if (core.isEmpty() || CardText.STRONG in core) return null
        return WordBold(opens = rest.length < word.length, closes = core.length < rest.length)
    }

    /** Copies [source]'s word-level bold markers onto [target], which has the same words. */
    private fun withBoldOf(source: String, target: String): String? {
        if (CardText.STRONG !in source) return target
        val marks = words(source).map { it.first }.filter { it != "•" }.map { wordBold(it) ?: return null }
        val targetWords = target.split('\n').map { paragraph -> paragraph.split(' ').filter { it.isNotEmpty() } }
        if (targetWords.sumOf { p -> p.count { it != "•" } } != marks.size) return null
        val pending = marks.iterator()
        val out = targetWords.joinToString("\n") { paragraph ->
            paragraph.joinToString(" ") { word ->
                val mark = if (word == "•") null else pending.next()
                when {
                    mark == null -> word

                    else -> (if (mark.opens) CardText.STRONG else "") + word +
                        (if (mark.closes) CardText.STRONG else "")
                }
            }
        }
        val literal = CardText.flatten(CardText.parse(out)).any {
            it is CardText.Segment.Text && CardText.STRONG in it.value
        }
        return if (literal) null else out
    }

    /** Readable one-line diff of two texts for REPORT.md: first differing word and context. */
    fun describeDifference(a: String, b: String): String {
        val wa = comparable(a)
        val wb = comparable(b)
        val i = wa.indices.firstOrNull { it >= wb.size || wa[it] != wb[it] } ?: wa.size
        fun around(w: List<String>) = w.subList(maxOf(0, i - 3), minOf(w.size, i + 5)).joinToString(" ")
        return "word ${i + 1}: «${around(wa)}» vs «${around(wb)}»"
    }
}
