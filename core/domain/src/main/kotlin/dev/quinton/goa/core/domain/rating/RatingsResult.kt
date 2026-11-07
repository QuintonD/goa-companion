package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint

/** Games and wins for some subject (player × hero, larger team, …). */
data class WinLoss(val games: Int = 0, val wins: Int = 0) {
    val losses: Int get() = games - wins

    /** Null when there are no games yet. */
    val winRate: Double? get() = if (games == 0) null else wins.toDouble() / games

    operator fun plus(won: Boolean): WinLoss = WinLoss(games + 1, wins + if (won) 1 else 0)

    companion object {
        val NONE = WinLoss()
    }
}

data class PlayerHeroKey(val playerId: String, val heroId: String)

/** Why a match did not affect any rating. */
enum class SkipReason {
    /** PENDING or ABANDONED. */
    NOT_COMPLETED,

    /** COMPLETED but no winner recorded. */
    NO_WINNER,

    /** A team has no hero seats. */
    EMPTY_TEAM,

    /** The same player sits on both teams — not a playable match. */
    PLAYER_ON_BOTH_TEAMS,
}

/** What one rated match did to the ratings (deltas are after mode multiplier and weights). */
data class MatchRatingChange(
    val matchId: String,
    val playedAt: Long,
    val winner: Team,
    /** Player-space expected score of Team Red before the match. */
    val expectedRed: Double,
    /** Hero-space expected score of Team Red, or null when the hero space was skipped. */
    val expectedRedHero: Double?,
    val playerDeltas: Map<String, Double>,
    val heroDeltas: Map<String, Double>,
)

/**
 * Output of [EloCalculator.replay]. Everything here is derived from the match log and can be
 * thrown away and rebuilt at any time (ADR-003).
 */
data class RatingsResult(
    val players: Map<String, Rating>,
    val heroes: Map<String, Rating>,
    /** Per player, one point per rated match in replay order. */
    val playerHistory: Map<String, List<RatingPoint>>,
    /** Per hero, one point per rated match (hero space) in replay order. */
    val heroHistory: Map<String, List<RatingPoint>>,
    /** Results per player piloting a specific hero (rated matches whose hero space applies, known heroes only). */
    val playerHeroRecords: Map<PlayerHeroKey, WinLoss>,
    /** Rated matches in which each player double-piloted (two or more seats). */
    val doublePilotGames: Map<String, Int>,
    /** Rated matches with uneven seat counts; `wins` = the larger team won. Tunes `seatAdvantage`. */
    val largerTeam: WinLoss,
    /** Rated matches by id. */
    val matchChanges: Map<String, MatchRatingChange>,
    /** Matches ignored by the replay, with the reason. */
    val skipped: Map<String, SkipReason>,
    /** Rated match ids in replay order. */
    val replayOrder: List<String>,
    /** Rating of anyone without a rated game (the config's starting rating). */
    val initialRating: Double = Rating.START,
) {
    private val unrated: Rating get() = Rating.UNRATED.copy(value = initialRating)

    fun player(id: String): Rating = players[id] ?: unrated

    fun hero(id: String): Rating = heroes[id] ?: unrated

    fun record(playerId: String, heroId: String): WinLoss =
        playerHeroRecords[PlayerHeroKey(playerId, heroId)] ?: WinLoss.NONE

    /**
     * The hero each player is shown as: their most-played hero in rated games (most games, then most wins, then hero
     * id), with no two players sharing one. Players claim in order of rated games played (most first, then player id);
     * a player whose hero is already claimed takes their next most-played free one, and a player with none left is
     * absent (the crest). Derived like everything here, so nothing about it is stored or synced.
     */
    fun mostPlayedHeroes(): Map<String, String> {
        val byPlayer = playerHeroRecords.entries.groupBy { it.key.playerId }
        val ranked = compareByDescending<Map.Entry<PlayerHeroKey, WinLoss>> { it.value.games }
            .thenByDescending { it.value.wins }
            .thenBy { it.key.heroId }
        val order = byPlayer.keys.sortedWith(
            compareByDescending<String> { id -> byPlayer.getValue(id).sumOf { it.value.games } }.thenBy { it },
        )
        val taken = HashSet<String>()
        val faces = LinkedHashMap<String, String>()
        order.forEach { id ->
            byPlayer.getValue(id).sortedWith(ranked).map { it.key.heroId }.firstOrNull { it !in taken }?.let {
                taken += it
                faces[id] = it
            }
        }
        return faces
    }

    companion object {
        val EMPTY = RatingsResult(
            players = emptyMap(),
            heroes = emptyMap(),
            playerHistory = emptyMap(),
            heroHistory = emptyMap(),
            playerHeroRecords = emptyMap(),
            doublePilotGames = emptyMap(),
            largerTeam = WinLoss.NONE,
            matchChanges = emptyMap(),
            skipped = emptyMap(),
            replayOrder = emptyList(),
        )
    }
}
