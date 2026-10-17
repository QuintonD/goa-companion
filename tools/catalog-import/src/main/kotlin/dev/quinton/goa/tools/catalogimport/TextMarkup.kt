package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.CardText

/**
 * Converts source card text into the app's inline markup ([CardText]): `{icon}`, `~(reminder)`,
 * `\n` paragraph breaks and a leading `• ` for "Choose one —" options. Bold (`**…**`) is not
 * part of the markup and is dropped; the words are kept verbatim.
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

    /** Removes `**` bold markers and SoA's italic toggles that are not reminder openers. */
    private fun inlineSoa(line: String, unknown: MutableSet<String>): String {
        val noBold = line.replace("**", "")
        val sb = StringBuilder()
        var i = 0
        while (i < noBold.length) {
            val c = noBold[i]
            if (c == '~' && (i + 1 >= noBold.length || noBold[i + 1] != '(')) {
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
        return tidy(paragraphs.joinToString("\n"))
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

    /** goa2-faq markdown: `_(…)_` reminder, `- ` option bullets, `**bold**`, `::icon::`. */
    fun fromFaq(text: String, unknownIcons: MutableSet<String> = mutableSetOf()): String {
        // `---` is a printed divider between alternatives: a paragraph break, not words.
        val lines = text.replace("\r\n", "\n").replace(Regex("""\s*---\s*"""), "\n").split('\n').map { it.trim() }
        val paragraphs = lines.filter { it.isNotEmpty() }.map { line ->
            val bullet = line.startsWith("- ") || line.startsWith("• ")
            val body = if (bullet) line.substring(2).trim() else line
            val converted = body
                .replace(Regex("""_\((.*?)\)_"""), "~($1)")
                .replace("**", "")
            (if (bullet) BULLET else "") + icons(converted, unknownIcons)
        }
        return tidy(paragraphs.joinToString("\n"))
    }

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

    /** The comparable word sequence (paragraph breaks and bullets ignored). */
    fun comparable(markup: String): List<String> = words(markup).map { normalizeWord(it.first) }.filter { it != "•" }

    fun agrees(a: String, b: String): Boolean = comparable(a) == comparable(b)

    /**
     * When [primary] and [reference] contain the same words, returns the reference's wording and
     * paragraph structure (it is not constrained by the printed line wraps). Otherwise null.
     */
    fun aligned(primary: String, reference: String): String? = if (agrees(primary, reference)) reference else null

    /** Readable one-line diff of two texts for REPORT.md: first differing word and context. */
    fun describeDifference(a: String, b: String): String {
        val wa = comparable(a)
        val wb = comparable(b)
        val i = wa.indices.firstOrNull { it >= wb.size || wa[it] != wb[it] } ?: wa.size
        fun around(w: List<String>) = w.subList(maxOf(0, i - 3), minOf(w.size, i + 5)).joinToString(" ")
        return "word ${i + 1}: «${around(wa)}» vs «${around(wb)}»"
    }
}
