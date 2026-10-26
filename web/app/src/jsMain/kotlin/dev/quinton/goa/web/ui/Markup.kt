package dev.quinton.goa.web.ui

/**
 * [text] made safe for markup the app builds as a string (inline SVG set with `innerHTML`), in text
 * and in a double- or single-quoted attribute. Names come from invites and synced documents, so a
 * quote must never end an attribute and start another (`" onload="…`): web review R2.
 */
fun escapeMarkup(text: String): String = buildString(text.length) {
    text.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
