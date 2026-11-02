package dev.quinton.goa.server.game.ratings

import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.server.game.room.MatchMapper
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow

/**
 * The log's [Match] for a finished rated game (docs/ONLINE.md). [lanesOf] is the catalog's number of
 * Lanes on a map: a game on a map with several Lanes is the double-lane mode whatever its length.
 */
class LogMatchMapper(private val lanesOf: (mapId: String) -> Int) : MatchMapper {
    override fun matchOf(
        game: GameRow,
        seats: List<GameSeatRow>,
        winner: Team,
        reason: WinReason,
        finishedAt: Long,
    ): Match = Match(
        id = game.id,
        createdAt = game.createdAt,
        playedAt = finishedAt,
        status = MatchStatus.COMPLETED,
        mode = modeOf(lanesOf(game.mapId), game.length),
        map = game.mapId,
        winner = winner,
        winCondition = conditionOf(reason),
        durationMin = ((finishedAt - game.createdAt) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0),
        participants = seats.sortedBy { it.seatOrder }.map {
            Participant(it.accountId, it.heroId, it.team, it.handicap)
        },
        tieBreaker = game.setup.tieBreaker,
        updatedAt = finishedAt,
    )

    internal companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        fun modeOf(lanes: Int, length: GameLength): GameMode = when {
            lanes > 1 -> GameMode.DOUBLE_LANE
            length == GameLength.LONG -> GameMode.LONG
            else -> GameMode.QUICK
        }

        /** No `else`: a new [WinReason] does not compile until it is mapped here. */
        fun conditionOf(reason: WinReason): WinCondition = when (reason) {
            WinReason.THRONE -> WinCondition.THRONE
            WinReason.LAST_PUSH -> WinCondition.LAST_PUSH
            WinReason.LIFE_COUNTERS -> WinCondition.LIFE_COUNTERS
            WinReason.CARD_TEXT -> WinCondition.OTHER
        }
    }
}
