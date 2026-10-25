package dev.quinton.goa.feature.players

import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.players.common.DateLabels
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.match.MatchEditing
import dev.quinton.goa.feature.players.match.MatchError
import dev.quinton.goa.feature.players.match.MatchForm
import dev.quinton.goa.feature.players.match.MatchRows
import dev.quinton.goa.feature.players.match.MatchValidation
import dev.quinton.goa.feature.players.match.SeatDraft
import dev.quinton.goa.feature.players.meta.MetaBoard
import dev.quinton.goa.feature.players.players.Leaderboard
import dev.quinton.goa.feature.players.ratings.RatingSettingsText
import dev.quinton.goa.feature.sync.SyncWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The Players and Group screens' rows and words are built by this shared code on the phone (JVM)
 * and in the browser (Node here, WebKit in the app), ADR-043: the same log gives the same ranks,
 * ratings, words and validation on both.
 */
class GroupPresentationParityTest {
    private val heroes = TestCatalog.heroes.map { it.id }
    private val people = listOf("Ann", "Bob", "Cid", "Dee").mapIndexed { i, name ->
        Player("p$i", name, 0xFF3FC1B0, i, createdAt = i.toLong())
    }

    /** Twelve completed matches over two days (UTC), varied teams, heroes and winners. */
    private val log: List<Match> = (0 until 12).map { n ->
        val at = 1_790_000_000_000L + n * 3_600_000L + (n / 6) * 86_400_000L
        val red = listOf(people[n % 4].id, people[(n + 1) % 4].id)
        val blue = listOf(people[(n + 2) % 4].id, people[(n + 3) % 4].id)
        Match(
            id = "m$n",
            createdAt = at,
            playedAt = at,
            status = MatchStatus.COMPLETED,
            mode = if (n % 3 == 0) GameMode.LONG else GameMode.QUICK,
            winner = if (n % 3 == 1) Team.BLUE else Team.RED,
            winCondition = WinCondition.entries[n % WinCondition.entries.size],
            durationMin = 30 + n,
            participants = red.mapIndexed { i, p -> Participant(p, heroes[(n + i) % heroes.size], Team.RED) } +
                blue.mapIndexed { i, p -> Participant(p, heroes[(n + i + 5) % heroes.size], Team.BLUE) },
        )
    }

    private val snapshot: RatingsSnapshot = run {
        val ratings = EloCalculator.replay(log, EloConfig.DEFAULT)
        RatingsSnapshot(
            ratings,
            MetaStats.compute(log, emptyList()),
            EloConfig.DEFAULT,
            GroupStats.compute(log, ratings, TimeZone.UTC),
        )
    }

    private val utc = object : DateLabels {
        override fun localDate(epochMillis: Long): LocalDate =
            LocalDate.fromEpochDays((epochMillis / 86_400_000L).toInt())

        override fun dayHeader(epochMillis: Long, today: LocalDate) = localDate(epochMillis).toString()

        override fun time(epochMillis: Long) = "${(epochMillis / 3_600_000L) % 24}h"
    }

    @Test
    fun theLeaderboardRanksAndTrendsAgree() {
        val board = Leaderboard.build(people, snapshot, showArchived = false, meId = "p1")
        assertEquals(
            "1 Cid 1219 −8/5 | 2 Bob 1212 +64/5 | 3 Dee 1188 −64/5 | 4 Ann 1181 +8/5",
            board.ranked.joinToString(" | ") {
                "${it.rank} ${it.player.name} ${formatRating(
                    it.rating.value,
                )} ${formatDelta(it.trend!!)}/${it.trendGames}"
            },
        )
        assertEquals("p1", board.meId)
        assertEquals(12, board.ratedMatches)
    }

    @Test
    fun theMatchLogGroupsByDayNewestFirst() {
        val groups = MatchRows.group(
            log,
            people.associateBy {
                it.id
            },
            TestCatalog.catalog,
            snapshot.ratings.matchChanges,
            utc,
            LocalDate(2026, 9, 1),
        )
        assertEquals(listOf(2, 4, 6), groups.map { it.matches.size }) // UTC days
        assertEquals("m11", groups.first().matches.first().id)
        val first = groups.first().matches.first()
        assertEquals(listOf("Dee", "Ann"), first.red.map { it.playerName })
        assertEquals(
            snapshot.ratings.matchChanges.getValue("m11").playerDeltas[first.red.first().player!!.id],
            first.red.first().delta,
        )
        assertEquals("1h", first.time)
    }

    @Test
    fun theMetaBoardAgrees() {
        val meta = MetaBoard.build(snapshot, people.associateBy { it.id }, TestCatalog.catalog)
        assertEquals(12, meta.totalMatches)
        assertEquals(snapshot.meta.heroes.count { it.ranked }, meta.ranked.size)
        assertEquals(8, meta.red.wins)
        assertEquals(4, meta.red.losses)
    }

    @Test
    fun theMatchFormRulesAndWordsAgree() {
        val form = MatchForm(
            playedAt = 10,
            red = listOf(SeatDraft(0, "p0", heroes[0]), SeatDraft(1, "p1", heroes[0])),
            blue = listOf(SeatDraft(2, "p0", heroes[1]), SeatDraft(3, null, null)),
        )
        val errors = MatchValidation.validate(form, { "Hero $it" }, { "Player $it" }, now = 10)
        assertEquals(
            "The match can't be in the future. Check the date and time.",
            MatchValidation.validate(form.copy(playedAt = 10 + 6 * 60_000L), now = 10).first().message,
        )
        assertEquals(
            listOf(
                "Choose a pilot for every seat.",
                "Hero ${heroes[0]} is picked twice. Each hero can only be in a match once.",
                "Player p0 can't play for both teams.",
                "Choose the winning team of a completed match.",
            ),
            errors.map(MatchError::message),
        )
        assertEquals("600", MatchEditing.duration(form, "9999").duration)
        assertEquals("42", MatchEditing.duration(form, "4a2").duration)
    }

    @Test
    fun syncAndRatingWordsAgree() {
        assertEquals(
            "+2 matches, +3 trivia rounds",
            SyncWords.summary(SyncChanges(matchesAdded = 2, triviaRoundsAdded = 3)),
        )
        assertEquals("Already up to date", SyncWords.summary(SyncChanges.NONE))
        assertEquals("3 h ago", SyncWords.ago(0, 3 * 3_600_000L + 59_000))
        assertEquals("1 day ago", SyncWords.ago(0, 86_400_000L))
        assertEquals("0.5", RatingSettingsText.decimal2(0.5))
        assertEquals("1.0", RatingSettingsText.decimal2(1.0))
        assertEquals("0.25", RatingSettingsText.decimal2(0.25))
        assertEquals("×1.5", RatingSettingsText.sections[1].settings[1].format(0.5))
        assertEquals("−20 pts", RatingSettingsText.sections[1].settings[0].format(-20.0))
    }
}
