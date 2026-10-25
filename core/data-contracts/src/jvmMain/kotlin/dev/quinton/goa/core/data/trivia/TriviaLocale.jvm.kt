package dev.quinton.goa.core.data.trivia

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual object TriviaLocale {
    actual fun number(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    actual fun shortDate(at: Long, now: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(at).atZone(zone)
        val pattern = if (date.year == Instant.ofEpochMilli(now).atZone(zone).year) "d MMM" else "d MMM yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    actual fun spokenDate(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
}
