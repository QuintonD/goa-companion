package dev.quinton.goa.core.model.catalog

/**
 * Inline markup used in [Card.text] (written by the importer, rendered by the UI):
 * - `{attack}` — an inline game icon; the name is one of [ICONS] (unknown names render as text).
 * - `~(…)` — reminder text, shown in italics. Its content is kept as-is (no nested markup).
 * - `**…**` — a printed bold keyword such as `**This turn:**` or `**Choose one —**`, shown bold.
 *   It may contain icons and reminders but never a paragraph break; a `**` without a closing
 *   `**` in the same paragraph (or an empty `****`) is literal text.
 * - `\n` — paragraph break.
 * Everything else is literal printed text.
 */
object CardText {
    val ICONS: Set<String> = setOf(
        "attack", "defense", "initiative", "movement", "range", "radius", "skill",
        "coin", "hold", "fast_travel", "clear", "ranged", "block", "item", "ultimate",
        "minion", "heavy_minion", "spawn", "life", "wave",
        // Hero-specific tokens, markers and symbols printed in card text (catalog import).
        "tiebreaker_blue", "tiebreaker_orange", "marker_bounty", "marker_poison",
        "rune_anvil", "rune_axe", "rune_bird", "rune_horn",
        "rune_anvil_marker", "rune_axe_marker", "rune_bird_marker", "rune_horn_marker",
        "token_barrier", "token_blast", "token_dud", "token_familiar", "token_glitch", "token_grenade",
        "token_ice", "token_illusion", "token_magma", "token_rock", "token_smoke_bomb", "token_totem",
        "token_tree", "token_zombie",
    )

    /** Opening and closing marker of a [Segment.Strong] span. */
    const val STRONG = "**"

    sealed interface Segment {
        data class Text(val value: String) : Segment

        data class Icon(val name: String) : Segment

        data class Reminder(val value: String) : Segment

        /** Bold printed keyword; [segments] are [Text], [Icon] or [Reminder], never [Break]. */
        data class Strong(val segments: List<Segment>) : Segment

        data object Break : Segment
    }

    fun parse(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) out += Segment.Text(buffer.toString())
            buffer.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> {
                    flush()
                    out += Segment.Break
                    i++
                }

                c == '{' && text.indexOf('}', i) > i -> {
                    val end = text.indexOf('}', i)
                    flush()
                    out += Segment.Icon(text.substring(i + 1, end))
                    i = end + 1
                }

                c == '~' && i + 1 < text.length && text[i + 1] == '(' -> {
                    val end = matchingParen(text, i + 1)
                    if (end < 0) {
                        buffer.append(c)
                        i++
                    } else {
                        flush()
                        out += Segment.Reminder(text.substring(i + 2, end))
                        i = end + 1
                    }
                }

                text.startsWith(STRONG, i) -> {
                    val end = closingStrong(text, i + STRONG.length)
                    if (end < 0) {
                        buffer.append(STRONG)
                    } else {
                        flush()
                        out += Segment.Strong(parse(text.substring(i + STRONG.length, end)))
                    }
                    i = if (end < 0) i + STRONG.length else end + STRONG.length
                }

                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
        return out
    }

    /** Plain text for search and accessibility: icons become words, markup is dropped. */
    fun plain(text: String): String = plain(parse(text))

    private fun plain(segments: List<Segment>): String = segments.joinToString("") {
        when (it) {
            is Segment.Text -> it.value
            is Segment.Icon -> it.name.replace('_', ' ')
            is Segment.Reminder -> "(${it.value})"
            is Segment.Strong -> plain(it.segments)
            Segment.Break -> "\n"
        }
    }

    /** Every segment in reading order with [Segment.Strong] spans expanded into their content. */
    fun flatten(segments: List<Segment>): List<Segment> = segments.flatMap {
        if (it is Segment.Strong) flatten(it.segments) else listOf(it)
    }

    /**
     * Index of the `**` closing a span whose content starts at [from], or -1 when the paragraph
     * ends first or the span would be empty. Markers inside an icon or reminder do not count.
     */
    private fun closingStrong(text: String, from: Int): Int {
        var j = from
        while (j < text.length && text[j] != '\n') {
            when {
                text.startsWith(STRONG, j) -> return if (j > from) j else -1

                text[j] == '{' && text.indexOf('}', j) > j -> j = text.indexOf('}', j) + 1

                text[j] == '~' && j + 1 < text.length && text[j + 1] == '(' -> {
                    val end = matchingParen(text, j + 1)
                    j = if (end < 0) j + 1 else end + 1
                }

                else -> j++
            }
        }
        return -1
    }

    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        for (j in open until text.length) {
            when (text[j]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return j
            }
        }
        return -1
    }
}
