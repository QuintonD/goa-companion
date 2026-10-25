package dev.quinton.goa.core.domain.meta

import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Two players and how [a] fared: against [b] (head-to-head) or with [b] (teammates). `a < b` by id. */
data class PlayerPairStat(
    val a: String,
    val b: String,
    /** Head-to-head: [a]'s wins against [b]. Teammates: the pair's wins together. */
    val record: WinLoss,
    /** Wilson lower bound of [record]'s win rate. */
    val wilsonLower: Double,
    /** At least [MetaConfig.minSample] games together, so the pair is ranked. */
    val ranked: Boolean,
)

/** How games were won (rated matches with a winner); `null` key = win condition not recorded. */
data class WinConditionMix(val counts: Map<WinCondition?, Int>) {
    val total: Int get() = counts.values.sum()

    fun share(condition: WinCondition?): Double? = if (total == 0) null else (counts[condition] ?: 0).toDouble() / total

    companion object {
        val NONE = WinConditionMix(emptyMap())
    }
}

/** Mean length of rated matches that recorded a duration. */
data class DurationStat(val matches: Int, val meanMinutes: Double)

/** A player's run of identical results up to their latest rated match. */
data class Streak(val playerId: String, val won: Boolean, val length: Int)

/**
 * One evening at the table: the latest rated matches with no gap of [GroupStats.NIGHT_GAP_MS] or
 * more between consecutive ones, so a night that runs past midnight is still one night.
 */
data class GameNight(
    /** The day the night started (its first match). */
    val date: LocalDate,
    val matchIds: List<String>,
    val redWins: Int,
    val blueWins: Int,
    /** Sum of each player's rating changes that night, biggest gain first. */
    val movers: List<Pair<String, Double>>,
)

/**
 * Group statistics from the match log (research #7): how games end, sides, length by mode,
 * player-pair records, streaks and the latest game night. Like [MetaStats], only matches the Elo
 * replay rates count, so every number agrees with the ratings (ADR-003). Pairs are ranked by
 * Wilson lower bound and gated by [MetaConfig.minSample] like heroes.
 */
data class GroupReport(
    val winConditions: WinConditionMix,
    val winConditionsByHero: Map<String, WinConditionMix>,
    /** Per player, how their *wins* came. */
    val winConditionsByPlayer: Map<String, WinConditionMix>,
    /** Team Red's record (wins = Red won). Blue's is the mirror. */
    val red: WinLoss,
    val durationByMode: Map<GameMode, DurationStat>,
    /** Ranked pairs first (Wilson, descending), then the rest by games. */
    val headToHead: List<PlayerPairStat>,
    val teammates: List<PlayerPairStat>,
    /** Current streaks of 2 or more, longest first. */
    val streaks: List<Streak>,
    val lastNight: GameNight?,
) {
    /** [a]'s record against [b] (either order). */
    fun versus(a: String, b: String): WinLoss? {
        val pair = headToHead.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) } ?: return null
        return if (pair.a == a) pair.record else WinLoss(pair.record.games, pair.record.losses)
    }

    companion object {
        val EMPTY = GroupReport(
            WinConditionMix.NONE,
            emptyMap(),
            emptyMap(),
            WinLoss.NONE,
            emptyMap(),
            emptyList(),
            emptyList(),
            emptyList(),
            null,
        )
    }
}

object GroupStats {
    /** A gap this long between consecutive matches starts a new game night. */
    const val NIGHT_GAP_MS: Long = 6 * 60 * 60 * 1000L

    fun compute(
        matches: Collection<Match>,
        ratings: RatingsResult,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        config: MetaConfig = MetaConfig(),
    ): GroupReport {
        val rated = matches.filter { EloCalculator.skipReason(it) == null }.sortedWith(EloCalculator.REPLAY_ORDER)
        if (rated.isEmpty()) return GroupReport.EMPTY
        val overall = HashMap<WinCondition?, Int>()
        val byHero = HashMap<String, HashMap<WinCondition?, Int>>()
        val byPlayer = HashMap<String, HashMap<WinCondition?, Int>>()
        var red = WinLoss.NONE
        val durations = HashMap<GameMode, MutableList<Int>>()
        val versus = LinkedHashMap<Pair<String, String>, WinLoss>()
        val together = LinkedHashMap<Pair<String, String>, WinLoss>()
        val results = HashMap<String, MutableList<Boolean>>()

        for (match in rated) {
            val winner = requireNotNull(match.winner)
            val condition = match.winCondition
            overall.bump(condition)
            red += winner == Team.RED
            match.durationMin?.let { durations.getOrPut(match.mode) { mutableListOf() } += it }
            // Hero records follow the Elo hero space, like the rest of the meta.
            if (EloCalculator.heroSpaceApplies(match)) {
                match.team(winner).mapNotNull {
                    it.heroId
                }.distinct().forEach { byHero.getOrPut(it) { HashMap() }.bump(condition) }
            }
            val redPlayers = match.team(Team.RED).map { it.playerId }.distinct()
            val bluePlayers = match.team(Team.BLUE).map { it.playerId }.distinct()
            for (p in match.team(winner).map {
                it.playerId
            }.distinct()) {
                byPlayer.getOrPut(p) { HashMap() }.bump(condition)
            }
            for (r in redPlayers) {
                results.getOrPut(r) { mutableListOf() } += winner == Team.RED
                for (b in bluePlayers) {
                    val key = ordered(r, b)
                    val aWon = (if (key.first == r) Team.RED else Team.BLUE) == winner
                    versus[key] = (versus[key] ?: WinLoss.NONE) + aWon
                }
            }
            for (b in bluePlayers) results.getOrPut(b) { mutableListOf() } += winner == Team.BLUE
            for ((team, ids) in listOf(Team.RED to redPlayers, Team.BLUE to bluePlayers)) {
                for (i in ids.indices) {
                    for (j in i + 1 until ids.size) {
                        val key = ordered(ids[i], ids[j])
                        together[key] = (together[key] ?: WinLoss.NONE) + (team == winner)
                    }
                }
            }
        }

        return GroupReport(
            winConditions = WinConditionMix(overall),
            winConditionsByHero = byHero.mapValues { WinConditionMix(it.value) },
            winConditionsByPlayer = byPlayer.mapValues { WinConditionMix(it.value) },
            red = red,
            durationByMode = durations.mapValues { (_, list) -> DurationStat(list.size, list.average()) },
            headToHead = pairs(versus, config),
            teammates = pairs(together, config),
            streaks = results.mapNotNull { (player, list) -> streak(player, list) }
                .filter { it.length >= 2 }
                .sortedWith(
                    compareByDescending<Streak> {
                        it.length
                    }.thenByDescending { it.won }.thenBy { it.playerId },
                ),
            lastNight = lastNight(rated, ratings, zone),
        )
    }

    private fun HashMap<WinCondition?, Int>.bump(condition: WinCondition?) {
        this[condition] = (this[condition] ?: 0) + 1
    }

    private fun ordered(x: String, y: String) = if (x < y) x to y else y to x

    private fun pairs(records: Map<Pair<String, String>, WinLoss>, config: MetaConfig): List<PlayerPairStat> = records
        .map { (key, record) ->
            PlayerPairStat(
                a = key.first,
                b = key.second,
                record = record,
                wilsonLower = MetaStats.wilsonLowerBound(record.wins, record.games, config.z),
                ranked = record.games >= config.minSample,
            )
        }
        .sortedWith(
            compareByDescending<PlayerPairStat> { it.ranked }
                .thenByDescending { if (it.ranked) it.wilsonLower else it.record.games.toDouble() }
                .thenByDescending { it.record.games }
                .thenBy { it.a }
                .thenBy { it.b },
        )

    private fun streak(player: String, results: List<Boolean>): Streak? {
        val last = results.lastOrNull() ?: return null
        return Streak(player, last, results.takeLastWhile { it == last }.size)
    }

    /**
     * The latest game night: the run of rated matches (in play order) ending with the latest one,
     * where no two consecutive matches are [NIGHT_GAP_MS] or more apart. A night that runs past
     * midnight stays one night; [GameNight.date] is the day it started.
     */
    private fun lastNight(rated: List<Match>, ratings: RatingsResult, zone: TimeZone): GameNight? {
        var start = rated.lastIndex
        while (start > 0 && rated[start].playedAt - rated[start - 1].playedAt < NIGHT_GAP_MS) start--
        val night = rated.subList(start, rated.size)
        val date = Instant.fromEpochMilliseconds(night.first().playedAt).toLocalDateTime(zone).date
        val movers = HashMap<String, Double>()
        for (m in night) {
            ratings.matchChanges[m.id]?.playerDeltas?.forEach { (p, d) -> movers[p] = (movers[p] ?: 0.0) + d }
        }
        return GameNight(
            date = date,
            matchIds = night.map { it.id },
            redWins = night.count { it.winner == Team.RED },
            blueWins = night.count { it.winner == Team.BLUE },
            movers = movers.entries.sortedWith(
                compareByDescending<Map.Entry<String, Double>> {
                    it.value
                }.thenBy { it.key },
            )
                .map { it.key to it.value },
        )
    }
}
