package dev.quinton.goa.feature.catalog.model

import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.player.Player

/** One player's record with a specific hero, from completed matches in the log. */
data class HeroPilotStat(
    val playerId: String,
    val player: Player?,
    val games: Int,
    val wins: Int,
) {
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games
}

/** Personal results for one hero, derived from the match log (never stored — ADR-003). */
data class HeroLogSummary(
    /** Completed matches with a winner that featured the hero. */
    val games: Int,
    val wins: Int,
    /** "Best with hero" (DOMAIN.md): players with ≥ [minGames] games, best win rate first. */
    val bestPilots: List<HeroPilotStat>,
    /** Everyone who has played the hero, most games first — for when nobody qualifies yet. */
    val allPilots: List<HeroPilotStat>,
)

object HeroPilots {
    const val MIN_GAMES = 3

    fun summarize(
        heroId: String,
        matches: List<Match>,
        players: List<Player>,
        minGames: Int = MIN_GAMES,
    ): HeroLogSummary {
        val playersById = players.associateBy { it.id }
        val games = mutableMapOf<String, Int>()
        val wins = mutableMapOf<String, Int>()
        var heroGames = 0
        var heroWins = 0
        matches.asSequence()
            .filter { it.status == MatchStatus.COMPLETED && it.winner != null }
            .forEach { match ->
                val seats = match.participants.filter { it.heroId == heroId }
                if (seats.isEmpty()) return@forEach
                heroGames++
                if (seats.any { it.team == match.winner }) heroWins++
                seats.distinctBy { it.playerId }.forEach { seat ->
                    games[seat.playerId] = (games[seat.playerId] ?: 0) + 1
                    if (seat.team == match.winner) wins[seat.playerId] = (wins[seat.playerId] ?: 0) + 1
                }
            }
        val all = games.map { (id, n) -> HeroPilotStat(id, playersById[id], n, wins[id] ?: 0) }
        return HeroLogSummary(
            games = heroGames,
            wins = heroWins,
            bestPilots = all.filter { it.games >= minGames }
                .sortedWith(compareByDescending<HeroPilotStat> { it.winRate }.thenByDescending { it.games }),
            allPilots = all.sortedWith(compareByDescending<HeroPilotStat> { it.games }.thenByDescending { it.wins }),
        )
    }
}
