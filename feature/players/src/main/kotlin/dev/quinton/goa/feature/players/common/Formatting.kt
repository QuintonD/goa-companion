package dev.quinton.goa.feature.players.common

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatRating(value: Double): String = value.roundToInt().toString()

/** "+12", "−8", "±0" — always signed, with a real minus sign. */
fun formatDelta(delta: Double): String {
    val rounded = delta.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "−${abs(rounded)}"
        else -> "±0"
    }
}

fun formatPercent(value: Double?): String = value?.let { "${(it * 100).roundToInt()}%" } ?: "—"

fun winLoss(wins: Int, games: Int): String = "$wins–${games - wins}"

fun Team.label(): String = if (this == Team.RED) "Red" else "Blue"

fun GameMode.label(): String = when (this) {
    GameMode.QUICK -> "Quick"
    GameMode.LONG -> "Long"
    GameMode.DOUBLE_LANE -> "Double lane"
}

fun WinCondition.label(): String = when (this) {
    WinCondition.THRONE -> "Throne"
    WinCondition.LAST_PUSH -> "Last push"
    WinCondition.LIFE_COUNTERS -> "Life counters"
    WinCondition.OTHER -> "Other"
}

fun MatchStatus.label(): String = when (this) {
    MatchStatus.PENDING -> "Pending"
    MatchStatus.COMPLETED -> "Completed"
    MatchStatus.ABANDONED -> "Abandoned"
}

/** Hero name for UI; ids missing from the catalog (e.g. from an old backup) read "Unknown hero". */
fun Catalog.heroName(heroId: String?): String = when (heroId) {
    null -> "Unknown hero"
    else -> hero(heroId)?.name ?: "Unknown hero"
}

fun Map<String, Player>.playerName(id: String): String = this[id]?.name ?: "Unknown player"

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
