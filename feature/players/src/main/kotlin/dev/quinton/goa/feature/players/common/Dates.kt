package dev.quinton.goa.feature.players.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

// The labels and numbers (formatRating, plural, Team.label…) are shared with the web app in
// core:group-presentation (Formatting.kt, same package); dates stay with java.time here.

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** "Today", "Yesterday" or "Sat 12 Oct 2026". */
    fun dayHeader(epochMillis: Long, today: LocalDate = LocalDate.now(zone)): String {
        val date = localDate(epochMillis)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayFormat)
        }
    }

    fun day(epochMillis: Long): String = localDate(epochMillis).format(dayFormat)

    fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormat)

    fun short(epochMillis: Long): String = localDate(epochMillis).format(shortDate)

    fun forFileName(epochMillis: Long): String = localDate(epochMillis).format(fileDate)
}

/** [Dates] as the shared builders (core:group-presentation) ask for them. */
object PhoneDateLabels : DateLabels {
    override fun localDate(epochMillis: Long): kotlinx.datetime.LocalDate =
        Dates.localDate(epochMillis).toKotlinLocalDate()

    override fun dayHeader(epochMillis: Long, today: kotlinx.datetime.LocalDate): String =
        Dates.dayHeader(epochMillis, today.toJavaLocalDate())

    override fun time(epochMillis: Long): String = Dates.time(epochMillis)
}
