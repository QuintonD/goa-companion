package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.server.game.store.GameSeatRow

/**
 * Whether a finished game of a ranked room counts (docs/ONLINE.md "Rated"), so that a rating can't be farmed.
 * Read from the log and the answers, never stored apart: the ratings stay derived from the log alone.
 */
internal object RatedRule {
    /** Games the same two people may already have played against each other within [WINDOW_MILLIS]. */
    const val MAX_MEETINGS = 10
    const val WINDOW_MILLIS = 7 * 24 * 60 * 60_000L

    /**
     * False when a team gave no answer of a person's own, whatever the computer played for it, or when two
     * people on opposite sides have already met [MAX_MEETINGS] times in rated games. [recent] is the log.
     */
    fun counts(seats: List<GameSeatRow>, humanTeams: Set<Team>, recent: List<Match>, now: Long): Boolean {
        if (!humanTeams.containsAll(seats.map { it.team })) return false
        val red = seats.filter { it.team == Team.RED }.map { it.accountId }
        val blue = seats.filter { it.team == Team.BLUE }.map { it.accountId }
        val counted = recent.filter { now - it.playedAt < WINDOW_MILLIS && EloCalculator.skipReason(it) == null }
        return red.none { a -> blue.any { b -> a != b && meetings(counted, a, b) >= MAX_MEETINGS } }
    }

    private fun meetings(log: List<Match>, a: String, b: String): Int = log.count { match ->
        val teamOfA = match.participants.firstOrNull { it.playerId == a }?.team
        val teamOfB = match.participants.firstOrNull { it.playerId == b }?.team
        teamOfA != null && teamOfB != null && teamOfA != teamOfB
    }
}
