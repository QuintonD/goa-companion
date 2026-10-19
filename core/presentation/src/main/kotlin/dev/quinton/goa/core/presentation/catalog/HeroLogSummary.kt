package dev.quinton.goa.feature.catalog.model

import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.meta.MetaConfig
import dev.quinton.goa.core.domain.meta.PlayerHeroStat
import dev.quinton.goa.core.model.player.Player

/** One player's record with a specific hero. */
data class HeroPilotStat(
    val playerId: String,
    val player: Player?,
    val games: Int,
    val wins: Int,
) {
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games
}

/**
 * Personal results for one hero, for display. The numbers come from the shared meta stats
 * ([HeroPilots], the same snapshot as the Meta tab — never computed here, ADR-003).
 */
data class HeroLogSummary(
    /** Rated matches that featured the hero. */
    val games: Int,
    val wins: Int,
    /** "Best with hero" (DOMAIN.md): players with ≥ [minGames] games, best first. */
    val bestPilots: List<HeroPilotStat>,
    /** Everyone who has played the hero, most games first — for when nobody qualifies yet. */
    val allPilots: List<HeroPilotStat>,
    val minGames: Int = MetaConfig().bestWithMinGames,
) {
    companion object {
        val EMPTY = HeroLogSummary(0, 0, emptyList(), emptyList())

        /** Attaches player profiles to [pilots], keeping its order. */
        fun of(pilots: HeroPilots, players: List<Player>): HeroLogSummary {
            val byId = players.associateBy { it.id }
            fun PlayerHeroStat.toUi() = HeroPilotStat(playerId, byId[playerId], record.games, record.wins)
            return HeroLogSummary(
                games = pilots.record.games,
                wins = pilots.record.wins,
                bestPilots = pilots.best.map { it.toUi() },
                allPilots = pilots.all.map { it.toUi() },
                minGames = pilots.minGames,
            )
        }
    }
}
