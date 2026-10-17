package dev.quinton.goa.core.model.catalog

/**
 * Inline markup used in [Card.text] (written by the importer, rendered by the UI):
 * - `{attack}` — an inline game icon; the name is one of [ICONS] (unknown names render as text).
 * - `~(…)` — reminder text, shown in italics.
 * - `\n` — paragraph break.
 * Everything else is literal printed text.
 */
object CardText {
    val ICONS: Set<String> = setOf(
        "attack", "defense", "initiative", "movement", "range", "radius", "skill",
        "coin", "hold", "fast_travel", "clear", "ranged", "block", "item", "ultimate",
        "minion", "heavy_minion", "spawn", "life", "wave",
    )

    sealed interface Segment {
        data class Text(val value: String) : Segment

        data class Icon(val name: String) : Segment

        data class Reminder(val value: String) : Segment

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
    fun plain(text: String): String = parse(text).joinToString("") {
        when (it) {
            is Segment.Text -> it.value
            is Segment.Icon -> it.name.replace('_', ' ')
            is Segment.Reminder -> "(${it.value})"
            Segment.Break -> "\n"
        }
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
