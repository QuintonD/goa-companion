package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.rating.MatchRatingChange
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import java.time.LocalDate
import java.time.ZoneId

/** One hero seat as shown in lists. */
data class SeatUi(
    val player: Player?,
    val playerName: String,
    val heroId: String?,
    val heroName: String,
    val portrait: String?,
    val handicap: Boolean,
    val doublePilot: Boolean,
    /** Player-space rating change from this match (only on the pilot's first seat). */
    val delta: Double? = null,
)

data class MatchRowUi(
    val id: String,
    val status: MatchStatus,
    val winner: Team?,
    val mode: GameMode,
    val winCondition: WinCondition?,
    val durationMin: Int?,
    val map: String?,
    val time: String,
    val red: List<SeatUi>,
    val blue: List<SeatUi>,
    val notes: String,
    /** Rematch choices for a completed match; empty otherwise. */
    val rematchOptions: List<RematchOption> = emptyList(),
) {
    /**
     * The line under the result: "Quick · Throne · 55 min · Kitchen table". Older matches stored
     * "Single lane"/"Double lane" as the map: the mode isn't repeated.
     */
    val summary: String
        get() = listOfNotNull(
            mode.label(),
            winCondition?.label(),
            durationMin?.let { "$it min" },
            map?.takeIf { it.isNotBlank() && !it.equals(mode.label(), ignoreCase = true) },
        ).joinToString(" · ")

    fun seats(team: Team): List<SeatUi> = if (team == Team.RED) red else blue
}

data class MatchGroup(val header: String, val matches: List<MatchRowUi>)

data class MatchLogUiState(
    val loading: Boolean = true,
    val groups: List<MatchGroup> = emptyList(),
    val total: Int = 0,
    /** The match just deleted, offered for undo. */
    val undo: Match? = null,
    /** The match just saved in the editor: the list scrolls to it once. */
    val highlightId: String? = null,
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

/** One choice in the Rematch sheet. [kind] null = redraft with the same players (opens the Draft tab). */
data class RematchOption(val kind: RematchKind?, val title: String, val body: String, val unavailable: String? = null)

/** The Rematch sheet's choices for [match], with the reason a kind can't be built. */
fun rematchOptions(match: Match): List<RematchOption> = listOf(
    RematchOption(
        RematchKind.SAME_SEATS,
        "Same seats",
        "Same players, heroes and teams.",
        Rematch.unavailable(match, RematchKind.SAME_SEATS),
    ),
    RematchOption(
        RematchKind.SWAP_HEROES,
        "Swap heroes",
        "Players stay on their team; Red's heroes go to Blue and Blue's to Red.",
        Rematch.unavailable(match, RematchKind.SWAP_HEROES),
    ),
    RematchOption(
        RematchKind.SWAP_TEAMS,
        "Swap sides",
        "Everyone keeps their hero; Red plays Blue and Blue plays Red.",
        Rematch.unavailable(match, RematchKind.SWAP_TEAMS),
    ),
    RematchOption(null, "Draft again", "The draft opens with the same players and teams; pick new heroes."),
)

/** The match log (Players → Matches) and the match rows every Players screen shows. */
object MatchLog {
    /** The log's order: newest played first, then newest created, then id. */
    val NEWEST_FIRST: Comparator<Match> =
        compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id }

    /** [matches] grouped by day ("Today", "Yesterday", "Sat 12 Oct 2026"), newest first. */
    fun group(
        matches: List<Match>,
        players: Map<String, Player>,
        catalog: Catalog,
        changes: Map<String, MatchRatingChange>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MatchGroup> = matches
        .sortedWith(NEWEST_FIRST)
        .groupBy { Dates.localDate(it.playedAt, zone) }
        .map { (_, dayMatches) ->
            MatchGroup(
                header = Dates.dayHeader(dayMatches.first().playedAt, today, zone),
                matches = dayMatches.map { it.toRow(players, catalog, changes[it.id], zone) },
            )
        }

    /**
     * One match as a row: one line per hero seat (a double pilot appears on each of their seats,
     * with their rating change on the first only); unknown heroes and players read "Unknown hero" /
     * "Unknown player".
     */
    fun Match.toRow(
        players: Map<String, Player>,
        catalog: Catalog,
        change: MatchRatingChange?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MatchRowUi {
        fun seats(team: Team): List<SeatUi> {
            val seats = team(team)
            val counts = seats.groupingBy { it.playerId }.eachCount()
            val shownDelta = mutableSetOf<String>()
            return seats.map { seat ->
                SeatUi(
                    player = players[seat.playerId],
                    playerName = players.playerName(seat.playerId),
                    heroId = seat.heroId,
                    heroName = catalog.heroName(seat.heroId),
                    portrait = seat.heroId?.let { catalog.hero(it)?.portrait },
                    handicap = seat.handicap,
                    doublePilot = (counts[seat.playerId] ?: 0) > 1,
                    delta = change?.playerDeltas?.get(seat.playerId)?.takeIf { shownDelta.add(seat.playerId) },
                )
            }
        }
        return MatchRowUi(
            id = id,
            status = status,
            winner = winner,
            mode = mode,
            winCondition = winCondition,
            durationMin = durationMin,
            map = map,
            time = Dates.time(playedAt, zone),
            red = seats(Team.RED),
            blue = seats(Team.BLUE),
            notes = notes,
            rematchOptions = if (status == MatchStatus.COMPLETED) rematchOptions(this) else emptyList(),
        )
    }
}
