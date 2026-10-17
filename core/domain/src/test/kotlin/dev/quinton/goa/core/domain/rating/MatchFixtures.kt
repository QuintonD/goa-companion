package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team

/** Seat on Red: `red("p1", "h1")`. */
fun red(
    playerId: String,
    heroId: String?,
    handicap: Boolean = false,
) = Participant(playerId, heroId, Team.RED, handicap)

fun blue(playerId: String, heroId: String?, handicap: Boolean = false) = Participant(
    playerId,
    heroId,
    Team.BLUE,
    handicap,
)

fun match(
    id: String,
    playedAt: Long,
    winner: Team?,
    vararg seats: Participant,
    status: MatchStatus = MatchStatus.COMPLETED,
    mode: GameMode = GameMode.QUICK,
    createdAt: Long = playedAt,
) = Match(
    id = id,
    createdAt = createdAt,
    playedAt = playedAt,
    status = status,
    mode = mode,
    winner = winner,
    participants = seats.toList(),
)
