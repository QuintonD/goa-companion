package dev.quinton.goa.core.domain.match

import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team

/** Ways to play a finished match again with the same group (docs/DOMAIN.md *Rematch*). */
enum class RematchKind {
    /** Same players, same heroes, same teams. */
    SAME_SEATS,

    /** Players keep their team and seat; the two teams' heroes trade places. */
    SWAP_HEROES,

    /** Every seat (player and hero) moves to the other team. */
    SWAP_TEAMS,
}

/**
 * Builds the pending match for a rematch [APP]. Pure; the caller saves it and opens Match setup.
 *
 * Seats are hero seats ([Participant]); a double pilot's seats always stay on one team, because
 * players never change team except all together ([RematchKind.SWAP_TEAMS]). A Handicap flag
 * belongs to the *seat*, not the hero: it balances the seat layout (the larger team in a 5/7/9
 * game, the solo player's second hero at 3 players [RB p.20]) or a player's skill, so it stays
 * with the seat when heroes move and moves with the seat when teams swap. The result is checked
 * against the same integrity rules as the match editor.
 */
object Rematch {
    /** Why [kind] can't be built from [match], or null if it can. */
    fun unavailable(match: Match, kind: RematchKind): String? {
        val red = match.team(Team.RED)
        val blue = match.team(Team.BLUE)
        return when {
            red.isEmpty() || blue.isEmpty() -> "Both teams need a hero seat."

            kind == RematchKind.SWAP_HEROES && red.size != blue.size ->
                "Heroes can only trade teams when both teams have the same number of heroes."

            kind == RematchKind.SWAP_HEROES && match.participants.all { it.heroId == null } ->
                "No heroes were recorded for this match."

            else -> null
        }
    }

    /**
     * A new PENDING match: same mode and map, no result, no duration, no notes, no draft and no
     * Tie Breaker coin (a rematch is a new game: the coin is placed again at setup).
     */
    fun create(match: Match, kind: RematchKind, id: String, now: Long): Result<Match> = runCatching {
        unavailable(match, kind)?.let { throw IllegalArgumentException(it) }
        val red = match.team(Team.RED)
        val blue = match.team(Team.BLUE)
        val seats = when (kind) {
            RematchKind.SAME_SEATS -> red + blue

            RematchKind.SWAP_TEAMS -> blue.map { it.copy(team = Team.RED) } + red.map { it.copy(team = Team.BLUE) }

            RematchKind.SWAP_HEROES ->
                red.zip(blue) { r, b -> r.copy(heroId = b.heroId) } +
                    blue.zip(red) { b, r -> b.copy(heroId = r.heroId) }
        }
        problem(seats)?.let { throw IllegalStateException(it) }
        Match(
            id = id,
            createdAt = now,
            playedAt = now,
            status = MatchStatus.PENDING,
            mode = match.mode,
            map = match.map,
            participants = seats,
        )
    }

    private fun problem(seats: List<Participant>): String? {
        val heroes = seats.mapNotNull { it.heroId }
        val red = seats.filter { it.team == Team.RED }.map { it.playerId }.toSet()
        return when {
            heroes.size != heroes.toSet().size -> "A hero would be picked twice."
            seats.any { it.team == Team.BLUE && it.playerId in red } -> "A player would be on both teams."
            else -> null
        }
    }
}
