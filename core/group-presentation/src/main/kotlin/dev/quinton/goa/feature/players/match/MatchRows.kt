package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.domain.rating.MatchRatingChange
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.DateLabels
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.common.playerName
import kotlinx.datetime.LocalDate

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
)

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

/** The match log's rows, shared by the phone and the web app (ADR-043). */
object MatchRows {
    /** Newest first, one group per day ("Today", "Yesterday", the date). */
    fun group(
        matches: List<Match>,
        players: Map<String, Player>,
        catalog: Catalog,
        changes: Map<String, MatchRatingChange>,
        labels: DateLabels,
        today: LocalDate,
    ): List<MatchGroup> = matches
        .sortedWith(
            compareByDescending<Match> {
                it.playedAt
            }.thenByDescending { it.createdAt }.thenByDescending { it.id },
        )
        .groupBy { labels.localDate(it.playedAt) }
        .map { (_, dayMatches) ->
            MatchGroup(
                header = labels.dayHeader(dayMatches.first().playedAt, today),
                matches = dayMatches.map { it.toRow(players, catalog, changes[it.id], labels) },
            )
        }

    fun Match.toRow(
        players: Map<String, Player>,
        catalog: Catalog,
        change: MatchRatingChange?,
        labels: DateLabels,
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
            time = labels.time(playedAt),
            red = seats(Team.RED),
            blue = seats(Team.BLUE),
            notes = notes,
            rematchOptions = if (status == MatchStatus.COMPLETED) rematchOptions(this) else emptyList(),
        )
    }
}
