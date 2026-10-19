package dev.quinton.goa.hub

/**
 * The fields a browser would submit from a form on one of the hub's pages, read from its HTML:
 * hidden, text, date and range inputs, checked radios and checkboxes, the selected option of each
 * select, and textareas. Submit buttons are left out (add the `op` you press). Enough for the
 * markup kotlinx.html writes (double-quoted attributes), so a test can post a page back untouched.
 */
object BrowserForm {
    private val tag = Regex("<(input|select|textarea)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)(?:=\"([^\"]*)\")?")
    private val option = Regex("<option\\b([^>]*)>", RegexOption.IGNORE_CASE)

    /** The fields of the form with `id="[formId]"` (or the first form), in document order. */
    fun fields(html: String, formId: String? = null): List<Pair<String, String>> {
        val start = formId?.let { html.indexOf("id=\"$it\"").takeIf { i -> i >= 0 } }
            ?.let { html.lastIndexOf("<form", it) } ?: html.indexOf("<form")
        require(start >= 0) { "No form in the page" }
        val end = html.indexOf("</form>", start).takeIf { it >= 0 } ?: html.length
        val form = html.substring(start, end)
        val out = ArrayList<Pair<String, String>>()
        tag.findAll(form).forEach { m ->
            val attrs = attributes(m.groupValues[2])
            val name = attrs["name"] ?: return@forEach
            if ("disabled" in attrs) return@forEach
            when (m.groupValues[1].lowercase()) {
                "input" -> when (attrs["type"]?.lowercase() ?: "text") {
                    "submit", "button", "image", "reset", "file" -> Unit
                    "radio", "checkbox" -> if ("checked" in attrs) out += name to (attrs["value"] ?: "on")
                    else -> out += name to attrs["value"].orEmpty()
                }

                "select" -> {
                    val body = form.substring(m.range.last + 1, form.indexOf("</select>", m.range.last))
                    val options = option.findAll(body).map { attributes(it.groupValues[1]) }.toList()
                    val chosen = options.firstOrNull { "selected" in it } ?: options.firstOrNull()
                    chosen?.let { out += name to it["value"].orEmpty() }
                }

                "textarea" -> {
                    val body = form.substring(m.range.last + 1, form.indexOf("</textarea>", m.range.last))
                    out += name to unescape(body)
                }
            }
        }
        return out
    }

    /** The `action` of the form with `id="[formId]"` (or the first form). */
    fun action(html: String, formId: String? = null): String? =
        Regex("<form\\b([^>]*)>").findAll(html).map { attributes(it.groupValues[1]) }
            .firstOrNull { formId == null || it["id"] == formId }?.get("action")

    private fun attributes(text: String): Map<String, String> =
        attribute.findAll(text).associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2]) }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x27;", "'").replace("&amp;", "&")
}
