package dev.quinton.goa.feature.players.common

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

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

/** Hero name for UI; ids missing from the catalog (e.g. from an old backup) read "Unknown hero". */
fun Catalog.heroName(heroId: String?): String = when (heroId) {
    null -> "Unknown hero"
    else -> hero(heroId)?.name ?: "Unknown hero"
}

fun Map<String, Player>.playerName(id: String): String = this[id]?.name ?: "Unknown player"

/**
 * Dates and times as this device writes them: the phone with `java.time` in its locale
 * (`Dates`), the browser with `Intl` (ADR-043). The shared builders only group and label with it.
 */
interface DateLabels {
    /** The calendar day [epochMillis] falls on, in this device's time zone. */
    fun localDate(epochMillis: Long): LocalDate

    /** "Today", "Yesterday" or the day ("Sat 12 Oct 2026"). */
    fun dayHeader(epochMillis: Long, today: LocalDate): String

    /** The time of day ("19:30"). */
    fun time(epochMillis: Long): String
}
