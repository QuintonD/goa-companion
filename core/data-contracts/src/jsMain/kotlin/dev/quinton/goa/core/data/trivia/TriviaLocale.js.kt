package dev.quinton.goa.core.data.trivia

import kotlin.js.Date

/** The browser's locale through `Intl` (Safari, Chrome and Node all have it). */
internal actual object TriviaLocale {
    actual fun number(n: Int): String = n.asDynamic().toLocaleString() as String

    actual fun shortDate(at: Long, now: Long): String {
        val date = Date(at.toDouble())
        val sameYear = date.getFullYear() == Date(now.toDouble()).getFullYear()
        val options: dynamic = js("({})")
        options.day = "numeric"
        options.month = "short"
        if (!sameYear) options.year = "numeric"
        return date.asDynamic().toLocaleDateString(undefined, options) as String
    }

    actual fun spokenDate(at: Long): String {
        val options: dynamic = js("({})")
        options.day = "numeric"
        options.month = "long"
        options.year = "numeric"
        return Date(at.toDouble()).asDynamic().toLocaleDateString(undefined, options) as String
    }
}
