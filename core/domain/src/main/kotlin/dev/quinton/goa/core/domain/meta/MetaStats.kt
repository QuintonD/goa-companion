package dev.quinton.goa.core.domain.meta

import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.HeroUsage
import kotlin.math.sqrt

/** Thresholds for the meta dashboard (docs/DOMAIN.md *Meta stats*). */
data class MetaConfig(
    /** Heroes/pairs with fewer games are listed but not ranked. */
    val minSample: Int = 5,
    /** Games a player needs on a hero to count for "best with hero". */
    val bestWithMinGames: Int = 3,
    /** z for the Wilson interval; 1.96 = 95 %. */
    val z: Double = 1.96,
)

/** A player's record on one hero. */
data class PlayerHeroStat(val playerId: String, val heroId: String, val record: WinLoss)

data class HeroMeta(
    val heroId: String,
    val record: WinLoss,
    /** Drafts (with at least one ban) in which this hero was banned. */
    val bans: Int,
    /** Matches featuring the hero ÷ all rated matches. */
    val pickRate: Double,
    /** Null when the hero has no games. */
    val winRate: Double?,
    /** Null when no draft had bans. */
    val banRate: Double?,
    /** Wilson score lower bound of the win rate; 0 with no games. */
    val wilsonLower: Double,
    /** True when [record] has at least [MetaConfig.minSample] games, so the hero is ranked. */
    val ranked: Boolean,
    val bestPlayer: PlayerHeroStat?,
)

/**
 * Who has played one hero — the catalog's "Best with this hero" (docs/DOMAIN.md *Meta stats*).
 * Counts the same matches as [HeroMeta.record] and the Elo hero space.
 */
data class HeroPilots(
    val heroId: String,
    /** Rated matches featuring the hero. */
    val record: WinLoss,
    /** Games a player needs on the hero to be ranked in [best]. */
    val minGames: Int,
    /** Players with ≥ [minGames] games: win rate, then games (descending), then player id. */
    val best: List<PlayerHeroStat>,
    /** Everyone who played the hero: games, then wins (descending), then player id. */
    val all: List<PlayerHeroStat>,
)

/** Two heroes on the same team and how that team fared. */
data class SynergyPair(val heroA: String, val heroB: String, val record: WinLoss, val wilsonLower: Double)

data class MetaReport(
    /** Rated matches considered (COMPLETED, with a winner, valid teams). */
    val totalMatches: Int,
    /** Drafts containing at least one ban — the ban-rate denominator. */
    val draftsWithBans: Int,
    /** Ranked heroes first (Wilson lower bound, descending), then unranked heroes by games. */
    val heroes: List<HeroMeta>,
    /** Per player, their heroes with ≥ [MetaConfig.bestWithMinGames] games, best first. */
    val bestHeroesByPlayer: Map<String, List<PlayerHeroStat>>,
    /** Ranked pairs of heroes played together (≥ [MetaConfig.minSample] games), best first. */
    val synergies: List<SynergyPair>,
    /** Matches with uneven seat counts; wins = the larger team won. */
    val largerTeam: WinLoss,
    /** Per hero, who has played it ([pilots] fills in heroes nobody played). */
    val pilotsByHero: Map<String, HeroPilots> = emptyMap(),
    /** Games a player needs on a hero for "best with hero" ([MetaConfig.bestWithMinGames]). */
    val bestWithMinGames: Int = MetaConfig().bestWithMinGames,
) {
    fun hero(id: String): HeroMeta? = heroes.firstOrNull { it.heroId == id }

    fun pilots(heroId: String): HeroPilots =
        pilotsByHero[heroId] ?: HeroPilots(heroId, WinLoss.NONE, bestWithMinGames, emptyList(), emptyList())

    /** The shared [HeroUsage] contract (core:model) for every hero seen in matches or bans. */
    fun usage(): Map<String, HeroUsage> = heroes.associate {
        it.heroId to HeroUsage(
            heroId = it.heroId,
            matches = it.record.games,
            wins = it.record.wins,
            bans = it.bans,
            pickRate = it.pickRate,
            winRate = it.winRate ?: 0.0,
            banRate = it.banRate ?: 0.0,
        )
    }

    companion object {
        val EMPTY = MetaReport(0, 0, emptyList(), emptyMap(), emptyList(), WinLoss.NONE)
    }
}

/**
 * Meta statistics derived from the match log and draft records (docs/DOMAIN.md *Meta stats*).
 * Only matches the Elo replay would rate are counted, and hero records (heroes, player × hero,
 * pairs) only where the replay's hero space applies ([EloCalculator.heroSpaceApplies]), so the
 * dashboard and ratings agree.
 */
object MetaStats {
    /** Best win rate first; ties by more games, then player id and hero id (deterministic). */
    private val bestFirst = compareByDescending<PlayerHeroStat> { it.record.winRate ?: 0.0 }
        .thenByDescending { it.record.games }
        .thenBy { it.playerId }
        .thenBy { it.heroId }

    private val mostPlayedFirst = compareByDescending<PlayerHeroStat> { it.record.games }
        .thenByDescending { it.record.wins }
        .thenBy { it.playerId }
        .thenBy { it.heroId }

    /**
     * Wilson score interval lower bound for [wins] successes in [games] trials. Ranks win rates
     * so that 3–0 does not beat 30–10.
     */
    fun wilsonLowerBound(wins: Int, games: Int, z: Double = 1.96): Double {
        if (games <= 0) return 0.0
        val n = games.toDouble()
        val p = wins / n
        val z2 = z * z
        val centre = p + z2 / (2 * n)
        val margin = z * sqrt(p * (1 - p) / n + z2 / (4 * n * n))
        return ((centre - margin) / (1 + z2 / n)).coerceIn(0.0, 1.0)
    }

    fun compute(
        matches: Collection<Match>,
        drafts: Collection<DraftRecord>,
        config: MetaConfig = MetaConfig(),
    ): MetaReport {
        val rated = matches.filter { EloCalculator.skipReason(it) == null }.sortedWith(EloCalculator.REPLAY_ORDER)
        val tally = Tally().apply { rated.forEach(::add) }
        val heroRecords = tally.heroRecords
        val playerHero = tally.playerHero
        val pairs = tally.pairs
        val largerTeam = tally.largerTeam
        val banDrafts = drafts.filter { d -> d.steps.any { it.action == DraftAction.BAN } }
        val bans = banDrafts
            .flatMap { d -> d.steps.filter { it.action == DraftAction.BAN }.map { it.heroId }.distinct() }
            .groupingBy { it }
            .eachCount()

        val bestByPlayer = playerHero
            .filter { (_, record) -> record.games >= config.bestWithMinGames }
            .map { (key, record) -> PlayerHeroStat(key.first, key.second, record) }
        val pilotsByHero = playerHero
            .map { (key, record) -> PlayerHeroStat(key.first, key.second, record) }
            .groupBy { it.heroId }
            .mapValues { (heroId, stats) ->
                HeroPilots(
                    heroId = heroId,
                    record = heroRecords[heroId] ?: WinLoss.NONE,
                    minGames = config.bestWithMinGames,
                    best = stats.filter { it.record.games >= config.bestWithMinGames }.sortedWith(bestFirst),
                    all = stats.sortedWith(mostPlayedFirst),
                )
            }

        val total = rated.size
        val heroIds = (heroRecords.keys + bans.keys).distinct()
        val heroes = heroIds.map { heroId ->
            val record = heroRecords[heroId] ?: WinLoss.NONE
            HeroMeta(
                heroId = heroId,
                record = record,
                bans = bans[heroId] ?: 0,
                pickRate = if (total == 0) 0.0 else record.games.toDouble() / total,
                winRate = record.winRate,
                banRate = if (banDrafts.isEmpty()) null else (bans[heroId] ?: 0).toDouble() / banDrafts.size,
                wilsonLower = wilsonLowerBound(record.wins, record.games, config.z),
                ranked = record.games >= config.minSample,
                bestPlayer = pilotsByHero[heroId]?.best?.firstOrNull(),
            )
        }.sortedWith(
            compareByDescending<HeroMeta> { it.ranked }
                .thenByDescending { if (it.ranked) it.wilsonLower else it.record.games.toDouble() }
                .thenByDescending { it.record.games }
                .thenBy { it.heroId },
        )

        val synergies = pairs
            .filter { (_, record) -> record.games >= config.minSample }
            .map { (key, record) ->
                SynergyPair(key.first, key.second, record, wilsonLowerBound(record.wins, record.games, config.z))
            }
            .sortedWith(compareByDescending<SynergyPair> { it.wilsonLower }.thenBy { it.heroA }.thenBy { it.heroB })

        return MetaReport(
            totalMatches = total,
            draftsWithBans = banDrafts.size,
            heroes = heroes,
            bestHeroesByPlayer = bestByPlayer.groupBy {
                it.playerId
            }.mapValues { (_, list) -> list.sortedWith(bestFirst) },
            synergies = synergies,
            largerTeam = largerTeam,
            pilotsByHero = pilotsByHero,
            bestWithMinGames = config.bestWithMinGames,
        )
    }

    /** Per-match accumulation of hero, player × hero, pair and larger-team records. */
    private class Tally {
        val heroRecords = LinkedHashMap<String, WinLoss>()
        val playerHero = LinkedHashMap<Pair<String, String>, WinLoss>()
        val pairs = LinkedHashMap<Pair<String, String>, WinLoss>()
        var largerTeam = WinLoss.NONE

        fun add(match: Match) {
            val winner = requireNotNull(match.winner)
            val red = match.team(Team.RED).size
            val blue = match.team(Team.BLUE).size
            if (red != blue) largerTeam += ((if (red > blue) Team.RED else Team.BLUE) == winner)
            // Same rule as the Elo hero space: a hero listed twice, or a team without a known hero.
            if (!EloCalculator.heroSpaceApplies(match)) return
            for (seat in match.participants) {
                val heroId = seat.heroId ?: continue
                val won = seat.team == winner
                heroRecords[heroId] = (heroRecords[heroId] ?: WinLoss.NONE) + won
                val key = seat.playerId to heroId
                playerHero[key] = (playerHero[key] ?: WinLoss.NONE) + won
            }
            for (team in Team.entries) {
                addPairs(
                    match.team(team).mapNotNull { it.heroId }.sorted(),
                    team == winner,
                )
            }
        }

        private fun addPairs(heroes: List<String>, won: Boolean) {
            for (i in heroes.indices) {
                for (j in i + 1 until heroes.size) {
                    val key = heroes[i] to heroes[j]
                    pairs[key] = (pairs[key] ?: WinLoss.NONE) + won
                }
            }
        }
    }
}
