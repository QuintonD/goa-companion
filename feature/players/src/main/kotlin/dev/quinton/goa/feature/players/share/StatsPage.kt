package dev.quinton.goa.feature.players.share

import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.match.MatchLogViewModel.Companion.toRow
import dev.quinton.goa.feature.players.match.MatchRowUi
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.meta.MetaViewModel
import dev.quinton.goa.feature.players.player.HeroRecordRow
import dev.quinton.goa.feature.players.player.PlayerDetailViewModel
import dev.quinton.goa.feature.players.players.PlayersUiState
import dev.quinton.goa.feature.players.players.PlayersViewModel

/** One player's heroes exactly as their profile lists them: "Best heroes", or "Most played". */
data class PlayerHeroesUi(val name: String, val heroes: List<HeroRecordRow>, val best: Boolean)

/** One match of the log with its day spelled out (the page outlives "Today"). */
data class StatsMatchUi(val day: String, val row: MatchRowUi)

/**
 * Everything on the shareable stats page (ADR-027). Every number comes from the screens' own
 * builders over one [RatingsSnapshot]; nothing here computes a statistic.
 */
data class StatsPage(
    /** "Sat 17 Oct 2026". */
    val generatedOn: String,
    /** "2026-10-17", for the title and file name. */
    val generatedIsoDate: String,
    val appVersion: String,
    /** Day of the last rated match, or null with none. */
    val ratedThrough: String?,
    val leaderboard: PlayersUiState,
    /** Ranked players' heroes, in leaderboard order; players with no heroes are left out. */
    val playerHeroes: List<PlayerHeroesUi>,
    val meta: MetaUiState,
    /** The newest [MATCH_LOG_CAP] matches (all statuses, like the log), newest first. */
    val matches: List<StatsMatchUi>,
    /** All matches in the log, so the page can say how many were left out. */
    val totalMatches: Int,
) {
    companion object {
        /** Recent matches on the page; keeps the file small (well under 1 MB). */
        const val MATCH_LOG_CAP = 100

        fun build(
            players: List<Player>,
            matches: List<Match>,
            snapshot: RatingsSnapshot,
            catalog: Catalog,
            nowMillis: Long,
            appVersion: String,
        ): StatsPage {
            val byId = players.associateBy { it.id }
            val leaderboard = PlayersViewModel.leaderboard(players, snapshot, showArchived = false)
            val playerHeroes = leaderboard.ranked.mapNotNull { row ->
                val detail = PlayerDetailViewModel.build(row.player.id, players, matches, snapshot, catalog)
                if (detail.heroes.isEmpty()) {
                    null
                } else {
                    PlayerHeroesUi(
                        row.player.name,
                        detail.heroes,
                        detail.heroesAreBest,
                    )
                }
            }
            // Same order as the match log.
            val log = matches.sortedWith(
                compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id },
            )
            val changes = snapshot.ratings.matchChanges
            val lastRated = snapshot.ratings.replayOrder.lastOrNull()?.let { changes[it]?.playedAt }
            return StatsPage(
                generatedOn = Dates.day(nowMillis),
                generatedIsoDate = Dates.forFileName(nowMillis),
                appVersion = appVersion,
                ratedThrough = lastRated?.let(Dates::day),
                leaderboard = leaderboard,
                playerHeroes = playerHeroes,
                meta = MetaViewModel.build(snapshot, byId, catalog),
                matches = log.take(MATCH_LOG_CAP).map {
                    StatsMatchUi(Dates.day(it.playedAt), it.toRow(byId, catalog, changes[it.id]))
                },
                totalMatches = matches.size,
            )
        }
    }
}
