package dev.quinton.goa.core.presentation.players

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

/** "1 match", "3 matches", "0 players". */
fun plural(count: Int, one: String, many: String = one + "s"): String = "$count ${if (count == 1) one else many}"

fun formatPercent(value: Double?): String = value?.let { "${(it * 100).roundToInt()}%" } ?: "—"

fun winLoss(wins: Int, games: Int): String = "$wins–${games - wins}"

/** A record for TalkBack: "1 win, 2 losses". */
fun spokenRecord(wins: Int, losses: Int): String = "${plural(wins, "win")}, ${plural(losses, "loss", "losses")}"

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

/** A match's result as its chip reads: "◆ Red won", "● Blue won", "Abandoned", "No winner", "Pending". */
fun resultLabel(status: MatchStatus, winner: Team?): String = when {
    status == MatchStatus.COMPLETED && winner != null -> "${if (winner == Team.RED) "◆" else "●"} ${winner.label()} won"
    status == MatchStatus.ABANDONED -> "Abandoned"
    status == MatchStatus.COMPLETED -> "No winner"
    else -> "Pending"
}

/** Hero name for UI; ids missing from the catalog (e.g. from an old backup) read "Unknown hero". */
fun Catalog.heroName(heroId: String?): String = when (heroId) {
    null -> "Unknown hero"
    else -> hero(heroId)?.name ?: "Unknown hero"
}

fun Map<String, Player>.playerName(id: String): String = this[id]?.name ?: "Unknown player"

/**
 * Dates as the Players screens show them. [ZoneId] defaults to the device's (the phone); the hub
 * passes its own zone (`HubClock.zone`).
 */
object Dates {
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val nightDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    fun localDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** "Today", "Yesterday" or "Sat 12 Oct 2026". */
    fun dayHeader(
        epochMillis: Long,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = localDate(epochMillis, zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayFormat)
        }
    }

    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String = localDate(
        epochMillis,
        zone,
    ).format(dayFormat)

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormat)

    fun short(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        localDate(epochMillis, zone).format(shortDate)

    /** "Sat 17 Oct", the Meta tab's game-night header. */
    fun night(date: LocalDate): String = date.format(nightDate)

    fun forFileName(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        localDate(epochMillis, zone).format(fileDate)
}
