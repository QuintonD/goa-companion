package dev.quinton.goa.core.domain.meta

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.domain.rating.blue
import dev.quinton.goa.core.domain.rating.match
import dev.quinton.goa.core.domain.rating.red
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import org.junit.Test

class GroupStatsTest {
    private val day = 86_400_000L

    private fun Match.won(condition: WinCondition?, minutes: Int? = null) = copy(
        winCondition = condition,
        durationMin = minutes,
    )

    /** Ann+Bob (Red) vs Cid (+ Cid double-piloting on day 2). */
    private val log = listOf(
        match("m1", 0 * day + 1, Team.RED, red("ann", "a"), red("bob", "b"), blue("cid", "c"), blue("dee", "d"))
            .won(WinCondition.THRONE, 50),
        match("m2", 1 * day + 1, Team.RED, red("ann", "a"), red("bob", "c"), blue("cid", "b"), blue("cid", "d"))
            .won(WinCondition.LAST_PUSH, 70),
        match("m3", 1 * day + 2, Team.BLUE, red("ann", "a"), red("bob", "b"), blue("cid", "c"), blue("dee", "d"))
            .won(null, 40),
        match("m4", 1 * day + 3, Team.BLUE, red("ann", "a"), blue("bob", "b"), mode = GameMode.LONG)
            .won(WinCondition.LIFE_COUNTERS, 90),
        match("pending", 1 * day + 4, null, red("ann", "a"), blue("bob", "b"), status = MatchStatus.PENDING),
    )
    private val ratings = EloCalculator.replay(log, EloConfig.DEFAULT)
    private val report = GroupStats.compute(log, ratings, TimeZone.UTC, MetaConfig(minSample = 3))

    @Test fun winConditionMixCountsRatedMatchesOverallPerHeroAndPerWinner() {
        assertThat(report.winConditions.total).isEqualTo(4)
        assertThat(report.winConditions.counts).containsExactly(
            WinCondition.THRONE,
            1,
            WinCondition.LAST_PUSH,
            1,
            null,
            1,
            WinCondition.LIFE_COUNTERS,
            1,
        )
        assertThat(report.winConditions.share(WinCondition.THRONE)).isEqualTo(0.25)
        // Hero "a" (always Ann, Red) won m1 by Throne and m2 by Last push.
        assertThat(report.winConditionsByHero.getValue("a").counts)
            .containsExactly(WinCondition.THRONE, 1, WinCondition.LAST_PUSH, 1)
        // Bob's wins: m1, m2 (Red) and m4 (Blue). A double pilot counts once per match.
        assertThat(report.winConditionsByPlayer.getValue("bob").total).isEqualTo(3)
        assertThat(report.winConditionsByPlayer.getValue("cid").counts).containsExactly(null, 1)
    }

    @Test fun sideRecordAndDurationByMode() {
        assertThat(report.red).isEqualTo(WinLoss(games = 4, wins = 2))
        assertThat(report.durationByMode.getValue(GameMode.QUICK)).isEqualTo(DurationStat(3, (50 + 70 + 40) / 3.0))
        assertThat(report.durationByMode.getValue(GameMode.LONG)).isEqualTo(DurationStat(1, 90.0))
    }

    @Test fun headToHeadAndTeammateRecordsAreGatedAndWilsonRanked() {
        // Ann vs Cid: m1 W, m2 W, m3 L → 2–1, ranked at minSample 3.
        assertThat(report.versus("ann", "cid")).isEqualTo(WinLoss(3, 2))
        assertThat(report.versus("cid", "ann")).isEqualTo(WinLoss(3, 1))
        // Ann vs Bob only once (m4, Bob won).
        assertThat(report.versus("ann", "bob")).isEqualTo(WinLoss(1, 0))
        val annCid = report.headToHead.single { it.a == "ann" && it.b == "cid" }
        assertThat(annCid.ranked).isTrue()
        assertThat(annCid.wilsonLower).isEqualTo(MetaStats.wilsonLowerBound(2, 3))
        assertThat(report.headToHead.first().ranked).isTrue()
        assertThat(report.headToHead.single { it.a == "ann" && it.b == "bob" }.ranked).isFalse()
        // Ann + Bob together: m1 W, m2 W, m3 L. Cid + Dee: m1 L, m3 W (Cid alone in m2).
        assertThat(report.teammates.single { it.a == "ann" && it.b == "bob" }.record).isEqualTo(WinLoss(3, 2))
        assertThat(report.teammates.single { it.a == "cid" && it.b == "dee" }.record).isEqualTo(WinLoss(2, 1))
    }

    @Test fun streaksAndTheLatestGameNight() {
        // Ann: W W L L → losing streak of 2; Bob: W W L W → no streak; Cid: L L W → none (1).
        assertThat(report.streaks).containsExactly(Streak("ann", won = false, length = 2))
        val night = report.lastNight!!
        assertThat(night.date).isEqualTo(LocalDate(1970, 1, 2))
        assertThat(night.matchIds).containsExactly("m2", "m3", "m4").inOrder()
        assertThat(night.redWins).isEqualTo(1)
        assertThat(night.blueWins).isEqualTo(2)
        val expected = listOf("m2", "m3", "m4").sumOf { ratings.matchChanges.getValue(it).playerDeltas["bob"] ?: 0.0 }
        assertThat(night.movers.toMap().getValue("bob")).isWithin(1e-9).of(expected)
        assertThat(night.movers.map { it.second }).isInOrder(Comparator.reverseOrder<Double>())
    }

    private val hour = 3_600_000L

    /** Rated 2v2 matches at the given wall-clock times (UTC+10, so "night" is local evening). */
    private fun nightLog(vararg localTimes: Pair<String, Long>): List<Match> = localTimes.map { (id, at) ->
        match(id, at, Team.RED, red("ann", "a"), red("bob", "b"), blue("cid", "c"), blue("dee", "d"))
    }

    private val sydney = UtcOffset(hours = 10).asTimeZone()

    /** Local midnight of 1970-01-05 in UTC+10, as epoch millis. */
    private val jan5 = 4 * day - 10 * hour

    @Test fun aGameNightRunsPastMidnight() {
        val log = nightLog(
            "earlier" to jan5 - 2 * day + 20 * hour,
            "n1" to jan5 - 2 * hour,
            "n2" to jan5 - 30 * 60_000L,
            "n3" to jan5 + 40 * 60_000L,
            "n4" to jan5 + 2 * hour + 10 * 60_000L,
        )
        val night = GroupStats.compute(log, EloCalculator.replay(log, EloConfig.DEFAULT), sydney).lastNight!!
        assertThat(night.matchIds).containsExactly("n1", "n2", "n3", "n4").inOrder()
        // Dated by the evening it started, not the day after.
        assertThat(night.date).isEqualTo(LocalDate(1970, 1, 4))
    }

    @Test fun aGapOfSixHoursStartsANewNight() {
        val log = nightLog(
            "lunch" to jan5 + 12 * hour,
            "afternoon" to jan5 + 14 * hour,
            "evening" to jan5 + 20 * hour,
            "late" to jan5 + 23 * hour,
        )
        val night = GroupStats.compute(log, EloCalculator.replay(log, EloConfig.DEFAULT), sydney).lastNight!!
        // 14:00 → 20:00 is exactly 6 hours: the evening is a new night, though on the same day.
        assertThat(night.matchIds).containsExactly("evening", "late").inOrder()
        assertThat(night.date).isEqualTo(LocalDate(1970, 1, 5))
    }

    @Test fun anEmptyLogHasNoStats() {
        assertThat(GroupStats.compute(emptyList(), ratings)).isEqualTo(GroupReport.EMPTY)
    }
}
