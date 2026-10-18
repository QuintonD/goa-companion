package dev.quinton.goa.core.presentation.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.presentation.players.Fixtures.ann
import dev.quinton.goa.core.presentation.players.Fixtures.blue
import dev.quinton.goa.core.presentation.players.Fixtures.bob
import dev.quinton.goa.core.presentation.players.Fixtures.cid
import dev.quinton.goa.core.presentation.players.Fixtures.dee
import dev.quinton.goa.core.presentation.players.Fixtures.hero
import dev.quinton.goa.core.presentation.players.Fixtures.red
import java.time.LocalDate
import org.junit.Test

/** The Players screens' builders (moved from the phone's ViewModels, ADR-030 §1). */
class PlayersBuildersTest {
    private val catalog = TestCatalog.catalog
    private val known = catalog.heroes.mapTo(HashSet()) { it.id }
    private val snapshot = RatingsSnapshot.compute(Fixtures.matches, Fixtures.drafts, EloConfig.DEFAULT, known)
    private val byId = Fixtures.players.associateBy { it.id }

    @Test
    fun snapshotIsTheReplayOfTheLogWithUnknownHeroesCleared() {
        // A hero this catalog doesn't know counts as Unknown hero: out of the hero space only.
        val withStranger = Fixtures.matches.map { m ->
            if (m.id == "m01") m.copy(participants = m.participants.map { it.copy(heroId = "not-in-catalog") }) else m
        }
        val stranger = RatingsSnapshot.compute(withStranger, Fixtures.drafts, EloConfig.DEFAULT, known)
        assertThat(stranger.ratings.heroes).doesNotContainKey("not-in-catalog")
        assertThat(stranger.ratings.replayOrder).isEqualTo(snapshot.ratings.replayOrder)
        assertThat(snapshot.ratings.replayOrder).hasSize(9)
        assertThat(snapshot.meta.totalMatches).isEqualTo(9)
    }

    @Test
    fun leaderboardRanksRatedPlayersAndSplitsUnratedAndArchived() {
        val state = Leaderboard.build(Fixtures.players, snapshot, showArchived = false, meId = bob.id)
        assertThat(state.ranked.map { it.rating.value }).isInOrder(Comparator.reverseOrder<Double>())
        assertThat(state.ranked.map { it.rank }).containsExactlyElementsIn(1..state.ranked.size).inOrder()
        assertThat(state.archived.map { it.player.id }).containsExactly(Fixtures.fay.id)
        assertThat(state.ratedMatches).isEqualTo(9)
        val annRow = state.ranked.first { it.player.id == ann.id }
        assertThat(annRow.trendGames).isEqualTo(TREND_GAMES)
        assertThat(annRow.trend).isWithin(1e-9).of(
            snapshot.ratings.playerHistory[ann.id]!!.takeLast(TREND_GAMES).sumOf { it.delta },
        )
        assertThat(state.ranked.single { it.isMe }.player).isEqualTo(bob)
        assertThat(state.meId).isEqualTo(bob.id)
        // A "me" that isn't in the list (merged away, deleted) isn't shown.
        assertThat(Leaderboard.build(Fixtures.players, snapshot, false, meId = "gone").meId).isNull()
        assertThat(Leaderboard.spoken(annRow)).startsWith("Rank ${annRow.rank}, Ann, rating ")
    }

    @Test
    fun samePeopleCardTextCountsPairs() {
        assertThat(PlayersUiState(samePeople = 0).samePeopleText).isNull()
        assertThat(PlayersUiState(samePeople = 1).samePeopleText).isEqualTo("2 players might be the same person")
        assertThat(PlayersUiState(samePeople = 3).samePeopleText)
            .isEqualTo("3 pairs of players look like the same person")
    }

    @Test
    fun logGroupsByDayNewestFirstWithDeltasOnce() {
        val groups = MatchLog.group(
            Fixtures.matches,
            byId,
            catalog,
            snapshot.ratings.matchChanges,
            LocalDate.of(2026, 10, 16),
        )
        assertThat(groups.first().header).isEqualTo("Yesterday")
        assertThat(groups.first().matches.map { it.id }).containsExactly("m10", "m09").inOrder()
        val rows = groups.flatMap { it.matches }
        assertThat(rows).hasSize(10)
        val doublePilot = rows.single { it.id == "m03" }
        assertThat(doublePilot.red.map { it.doublePilot }).containsExactly(true, true)
        assertThat(doublePilot.red.mapNotNull { it.delta }).hasSize(1)
        assertThat(rows.single { it.id == "m06" }.red[1].heroName).isEqualTo("Unknown hero")
        assertThat(rows.single { it.id == "m10" }.rematchOptions).isEmpty()
        assertThat(rows.single { it.id == "m09" }.rematchOptions.map { it.title })
            .containsExactly("Same seats", "Swap heroes", "Swap sides", "Draft again").inOrder()
        assertThat(rows.single { it.id == "m07" }.summary).isEqualTo("Quick · Throne · 71 min")
    }

    @Test
    fun matchSummaryDoesNotRepeatTheModeStoredAsMap() {
        val row = with(MatchLog) { Fixtures.matches.first().copy(map = "quick").toRow(byId, catalog, null) }
        assertThat(row.summary).isEqualTo("Quick · Throne · 55 min")
        val table = with(MatchLog) { Fixtures.matches.first().copy(map = "Kitchen").toRow(byId, catalog, null) }
        assertThat(table.summary).isEqualTo("Quick · Throne · 55 min · Kitchen")
    }

    @Test
    fun playerDetailShowsHistoryBestHeroesAndRecentMatches() {
        val state = PlayerDetail.build(ann.id, Fixtures.players, Fixtures.matches, snapshot, catalog)
        assertThat(state.player).isEqualTo(ann)
        assertThat(state.history).hasSize(state.rating.games)
        assertThat(state.rating.games).isEqualTo(9)
        assertThat(state.doublePilotGames).isEqualTo(2)
        assertThat(state.heroesAreBest).isTrue()
        assertThat(state.heroesTitle).isEqualTo("Best heroes")
        assertThat(state.heroes.map { it.heroId }).containsExactly(hero(2), hero(1)).inOrder()
        assertThat(state.recentMatches.first().id).isEqualTo("m10")
        assertThat(state.recentMatches).hasSize(RECENT_MATCHES)
        assertThat(state.totalMatches).isEqualTo(10)
        // Merge candidates: every other active player, by name, with their match counts.
        assertThat(state.mergeCandidates.map { it.player.name }).containsExactly("Bob", "Cid", "Dee", "Eve").inOrder()
        assertThat(PlayerDetail.build("missing", Fixtures.players, Fixtures.matches, snapshot, catalog).player).isNull()
    }

    @Test
    fun aLosingHeroIsNeverCalledBest() {
        val log = listOf(
            Fixtures.match("x1", 1, Team.BLUE, red(cid, 7), blue(dee, 8)),
            Fixtures.match("x2", 2, Team.BLUE, red(cid, 7), blue(dee, 8)),
            Fixtures.match("x3", 3, Team.RED, red(cid, 7), blue(dee, 8), condition = WinCondition.LAST_PUSH),
        )
        val local = RatingsSnapshot.compute(log, emptyList(), EloConfig.DEFAULT, known)
        val state = PlayerDetail.build(cid.id, Fixtures.players, log, local, catalog)
        assertThat(state.heroesAreBest).isFalse()
        assertThat(state.heroesTitle).isEqualTo("Most played")
        assertThat(state.heroes.single().record).isEqualTo(WinLoss(3, 1))
        assertThat(state.winConditions.counts).containsExactly(WinCondition.LAST_PUSH, 1)
        assertThat(state.historyDates).isEqualTo(log.map { it.playedAt })
    }

    @Test
    fun ratingChartPadsTheRangeAndLabelsAboutFourPoints() {
        assertThat(RatingChartModel.of(emptyList(), 1200.0)).isNull()
        val chart = RatingChartModel.of(listOf(1215.0, 1203.0, 1231.0), 1200.0, listOf(1L, 2L, 3L))!!
        assertThat(chart.values).containsExactly(1200.0, 1215.0, 1203.0, 1231.0).inOrder()
        assertThat(chart.low).isEqualTo(1190.0)
        assertThat(chart.high).isEqualTo(1250.0)
        assertThat(chart.labelSpacing).isEqualTo(1)
        assertThat(chart.label(0)).isEqualTo("Start")
        assertThat(chart.summary)
            .isEqualTo("Rating history over 3 matches: started at 1200, now 1231, peak 1231, low 1200.")
    }

    @Test
    fun metaSplitsRankedHeroesNamesBestPilotsAndPicksMovers() {
        val state = Meta.build(snapshot, byId, catalog)
        assertThat(state.totalMatches).isEqualTo(9)
        assertThat(state.draftsWithBans).isEqualTo(1)
        assertThat(state.ranked.all { it.meta.record.games >= 5 }).isTrue()
        val top = state.ranked.first { it.heroId == hero(1) }
        assertThat(top.name).isEqualTo("Test Hero 1")
        assertThat(top.bestPlayer).isEqualTo(ann)
        assertThat(state.unranked.map { it.heroId }).contains(hero(11))
        assertThat(state.largerTeam.games).isEqualTo(2)
        assertThat(state.largerTeamLabel).isEqualTo("Larger team won (2)")
        assertThat(state.winConditions.total).isEqualTo(state.totalMatches)
        assertThat(state.sortedHeroes).isEqualTo(state.ranked + state.unranked)
        assertThat(state.copy(sort = HeroSort.GAMES).sortedHeroes.map { it.meta.record.games })
            .isInOrder(Comparator.reverseOrder<Int>())
        assertThat(state.rankOf[state.ranked.first().heroId]).isEqualTo(1)
        val night = state.night!!
        assertThat(night.shownMovers.size).isAtMost(GameNightUi.TOP_MOVERS + 1)
        assertThat(night.shownMovers.none { Math.round(it.delta) == 0L }).isTrue()
    }

    @Test
    fun moversAreTheTopThreeGainsThenTheBiggestLoss() {
        val night = GameNightUi(
            LocalDate.of(2026, 10, 15),
            matches = 4,
            redWins = 2,
            blueWins = 2,
            movers = listOf(30.0, 20.0, 10.0, 5.0, 0.2, -3.0, -12.0).mapIndexed { i, d -> MoverRow(null, "P$i", d) },
        )
        assertThat(night.shownMovers.map { it.delta }).containsExactly(30.0, 20.0, 10.0, -12.0).inOrder()
    }

    @Test
    fun heroWinsSummaryListsConditionsInOrder() {
        val state = Meta.build(snapshot, byId, catalog)
        val row = state.ranked.first { it.heroId == hero(1) }
        assertThat(row.winsSummary).startsWith("Wins: Throne ")
    }

    @Test
    fun samePeopleSuggestsLookAlikesWithTheirRecords() {
        val anna = Player("p-anna", "Anna", 0xFF3FC1B0, avatarId = 6, createdAt = 10)
        val people = Fixtures.players.filterNot { it.archived } + anna
        val pairs = SamePeople.pairs(people, Fixtures.matches, PlayerMerges(emptyList()), emptySet())
        val pair = pairs.single()
        assertThat(pair.keep.player).isEqualTo(ann)
        assertThat(pair.other.player).isEqualTo(anna)
        assertThat(pair.keep.games).isEqualTo(10)
        assertThat(SamePeople.summary(pair.other)).isEqualTo("No matches yet")
        // "Different people" on this device hides the pair.
        assertThat(SamePeople.pairs(people, Fixtures.matches, PlayerMerges(emptyList()), setOf(pair.key))).isEmpty()
        // Merged already: nothing to ask.
        val merged = PlayerMerges(listOf(PlayerMerge(anna.id, ann.id, 1)))
        assertThat(SamePeople.pairs(merged.visible(people), Fixtures.matches, merged, emptySet())).isEmpty()
        assertThat(SamePeopleUiState(differentCount = 2).askAgainText)
            .isEqualTo("Ask again about the 2 pairs you said were different")
    }

    @Test
    fun mergeCopyNamesBothPlayersAndTheMatchesTheyShared() {
        assertThat(MergeCopy.confirmMessage("Bob", 1, "Ann", 3)).startsWith("Bob (1 match) becomes Ann (3 matches).")
        val shared = Fixtures.matches.filter { ann.id in it.playerIds && bob.id in it.playerIds }
        val lines = MergeCopy.playedTogether(shared.reversed(), byId)
        assertThat(lines).hasSize(shared.size)
        assertThat(lines.first()).isEqualTo("2026-10-03 · Ann, Bob vs Cid, Dee")
    }

    @Test
    fun savedSummaryListsWinnersFirstWithRanksAndTheLeader() {
        val match = Fixtures.matches.single { it.id == "m09" }
        val saved = MatchEditor.savedSummary(match, Fixtures.players, snapshot.ratings)
        assertThat(saved.ratingsReady).isTrue()
        assertThat(saved.players.takeWhile { it.won }.map { it.team }.distinct()).containsExactly(Team.RED)
        assertThat(saved.players.map { it.name }).containsExactly("Ann", "Bob", "Cid", "Dee")
        val leader = Leaderboard.build(Fixtures.players, snapshot, false).ranked.first()
        assertThat(saved.leaderName).isEqualTo(leader.player.name)
        assertThat(saved.players.mapNotNull { it.rank }).isNotEmpty()
        assertThat(saved.rematchOptions).hasSize(4)
    }

    @Test
    fun editorRulesMatchThePhone() {
        assertThat(MatchEditor.durationInput("1h 2")).isEqualTo("12")
        assertThat(MatchEditor.durationInput("999")).isEqualTo("600")
        assertThat(
            MatchEditor.gameModeOrNull("double_lane"),
        ).isEqualTo(dev.quinton.goa.core.model.match.GameMode.DOUBLE_LANE)
        assertThat(MatchEditor.gameModeOrNull("blitz")).isNull()
        assertThat(MatchEditor.toMinute(125_000)).isEqualTo(120_000)
        val pending = Fixtures.matches.single { it.id == "m10" }
        val done = pending.copy(status = MatchStatus.COMPLETED, winner = Team.RED)
        assertThat(MatchEditor.isNewResult(done, original = pending, resultFlow = false)).isTrue()
        assertThat(MatchEditor.isNewResult(done, original = done, resultFlow = false)).isFalse()
        assertThat(MatchEditor.isNewResult(done, original = done, resultFlow = true)).isTrue()
        assertThat(MatchEditor.isNewResult(pending, original = null, resultFlow = false)).isFalse()
        val form = MatchForm.from(Fixtures.matches.single { it.id == "m03" }) { 0L }
        assertThat(MatchEditor.pilotNames(form, Team.RED, byId)).containsExactly("Ann")
        val archivedSeated = form.copy(red = form.red + SeatDraft(9, Fixtures.fay.id))
        assertThat(MatchEditor.pilotOptions(Fixtures.players, archivedSeated, snapshot.ratings).map { it.player.id })
            .contains(Fixtures.fay.id)
        assertThat(MatchEditor.pilotOptions(Fixtures.players, form, snapshot.ratings).map { it.player.id })
            .doesNotContain(Fixtures.fay.id)
        val twice = form.copy(blue = form.blue.map { it.copy(heroId = hero(1)) })
        assertThat(MatchEditor.seatProblem(twice, Team.BLUE, twice.blue.first(), showErrors = false))
            .isEqualTo("Hero picked twice")
    }

    @Test
    fun newPlayersGetAnUnusedColourAndTheNextCrest() {
        val palette = listOf(1L, 2L, 3L)
        val people = listOf(ann.copy(color = 1L), bob.copy(color = 3L))
        assertThat(PlayerLook.newColor(people, palette)).isEqualTo(2L)
        assertThat(PlayerLook.newColor(people + cid.copy(color = 2L), palette)).isEqualTo(1L)
        assertThat(PlayerLook.newCrest(people, 24)).isEqualTo(2)
        assertThat(PlayerLook.nameError("", null, null)).isEqualTo("Enter a name")
        assertThat(PlayerLook.nameError("ann", ann, null)).isEqualTo("“ann” is already in your group")
        assertThat(PlayerLook.nameError("Ann", ann, ann.id)).isNull()
        assertThat(PlayerLook.nameInput("x".repeat(40))).hasLength(MAX_NAME_LENGTH)
    }
}
