package dev.quinton.goa.feature.players

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.backup.BackupCodec
import dev.quinton.goa.core.data.backup.BackupDocument
import dev.quinton.goa.core.data.backup.ImportMode
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.Fixtures.ann
import dev.quinton.goa.feature.players.Fixtures.bob
import dev.quinton.goa.feature.players.Fixtures.cid
import dev.quinton.goa.feature.players.Fixtures.dee
import dev.quinton.goa.feature.players.Fixtures.hero
import dev.quinton.goa.feature.players.backup.BackupViewModel
import dev.quinton.goa.feature.players.match.MatchEditViewModel
import dev.quinton.goa.feature.players.match.MatchError
import dev.quinton.goa.feature.players.match.MatchLogViewModel
import dev.quinton.goa.feature.players.match.MatchNav
import dev.quinton.goa.feature.players.match.RecentlyDeletedMatch
import dev.quinton.goa.feature.players.match.RecentlySavedMatch
import dev.quinton.goa.feature.players.match.RematchLauncher
import dev.quinton.goa.feature.players.meta.HeroSort
import dev.quinton.goa.feature.players.meta.MetaView
import dev.quinton.goa.feature.players.meta.MetaViewModel
import dev.quinton.goa.feature.players.player.PlayerDetailViewModel
import dev.quinton.goa.feature.players.player.PlayerEditViewModel
import dev.quinton.goa.feature.players.players.PlayersViewModel
import dev.quinton.goa.feature.players.ratings.RatingSettingsViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTests {
    @get:Rule val main = MainDispatcherRule()

    private val players = FakePlayerRepository(Fixtures.players)
    private val matches = FakeMatchRepository(Fixtures.matches)
    private val drafts = FakeDraftRecordRepository(Fixtures.drafts)
    private val config = FakeEloConfigRepository()
    private val ratings = FakeRatingsSnapshotRepository(matches, drafts, config)
    private val catalog = FakeCatalogRepository()
    private val deleted = RecentlyDeletedMatch()
    private val saved = RecentlySavedMatch()
    private val prefill = FakeDraftSetupPrefill()
    private val rematches = RematchLauncher(matches, prefill)

    @Test
    fun leaderboardRanksRatedPlayersAndHidesArchivedUntilToggled() = runTest {
        val vm = PlayersViewModel(players, ratings)
        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            val values = state.ranked.map { it.rating.value }
            assertThat(values).isInOrder(Comparator.reverseOrder<Double>())
            assertThat(state.ranked.map { it.rank }).containsExactlyElementsIn(1..state.ranked.size).inOrder()
            assertThat(state.ranked.map { it.player.id }).doesNotContain(Fixtures.fay.id)
            assertThat(state.archived.map { it.player.id }).containsExactly(Fixtures.fay.id)
            assertThat(state.showArchived).isFalse()
            assertThat(state.ratedMatches).isEqualTo(9)
            assertThat(state.ranked.first { it.player.id == ann.id }.trend).isNotNull()
            vm.toggleArchived()
            assertThat(awaitItem().showArchived).isTrue()
        }
    }

    @Test
    fun newPlayerValidatesNameAndSavesWithUnusedColour() = runTest {
        val vm = PlayerEditViewModel(null, players)
        val initial = vm.uiState.value
        assertThat(initial.isNew).isTrue()
        assertThat(initial.color).isNotIn(Fixtures.players.map { it.color })

        vm.save()
        assertThat(vm.uiState.value.nameError).isEqualTo("Enter a name")

        vm.onNameChange("  ann ")
        vm.save()
        assertThat(vm.uiState.value.nameError).contains("already in your group")
        assertThat(vm.uiState.value.saved).isFalse()

        vm.onNameChange("Gus")
        vm.onAvatarChange(99)
        assertThat(vm.uiState.value.avatarId).isEqualTo(23)
        vm.save()
        assertThat(vm.uiState.value.saved).isTrue()
        val gus = players.state.value.single { it.name == "Gus" }
        assertThat(gus.avatarId).isEqualTo(23)
    }

    @Test
    fun playerNamesStayUniqueIncludingArchivedPlayers() = runTest {
        // Fay is archived: reusing her name would split her rating across two ids.
        val vm = PlayerEditViewModel(null, players)
        vm.onNameChange(" FAY ")
        vm.save()
        assertThat(vm.uiState.value.nameError).isEqualTo("“FAY” is already in your group")
        assertThat(players.state.value).isEqualTo(Fixtures.players)

        // Renaming a player to another player's name is refused; keeping their own name is fine.
        val bobEdit = PlayerEditViewModel(bob.id, players)
        bobEdit.onNameChange("ann")
        bobEdit.save()
        assertThat(bobEdit.uiState.value.nameError).contains("already in your group")
        bobEdit.onNameChange("  BOB ")
        bobEdit.save()
        assertThat(bobEdit.uiState.value.saved).isTrue()
    }

    @Test
    fun editingPlayerKeepsIdAndCreatedAt() = runTest {
        val vm = PlayerEditViewModel(bob.id, players)
        assertThat(vm.uiState.value.name).isEqualTo("Bob")
        vm.onNameChange("Bobby")
        vm.onColorChange(0xFFE06FAE)
        vm.save()
        assertThat(
            players.state.value.single {
                it.id == bob.id
            },
        ).isEqualTo(bob.copy(name = "Bobby", color = 0xFFE06FAE))
        assertThat(PlayerEditViewModel("missing", players).uiState.value.notFound).isTrue()
    }

    private fun editor(id: String? = null) = editor(MatchEditRoute(id))

    private fun editor(route: MatchEditRoute) = MatchEditViewModel(
        route,
        matches,
        players,
        catalog,
        ratings,
        deleted,
        saved,
        rematches,
    )

    @Test
    fun newMatchHonoursTimerPrefill() = runTest {
        val vm = editor(MatchEditRoute(prefillDurationMin = 47, prefillMode = "double_lane"))
        val state = vm.uiState.first { !it.loading }
        assertThat(state.isNew).isTrue()
        assertThat(state.form.duration).isEqualTo("47")
        assertThat(state.form.mode).isEqualTo(GameMode.DOUBLE_LANE)
        assertThat(state.form.red).hasSize(2)
        // Pre-filled values are the starting point, not unsaved edits.
        assertThat(state.dirty).isFalse()
    }

    @Test
    fun invalidPrefillIsIgnoredOrClamped() = runTest {
        val bad = editor(MatchEditRoute(prefillDurationMin = 0, prefillMode = "blitz")).uiState.first { !it.loading }
        assertThat(bad.form.duration).isEmpty()
        assertThat(bad.form.mode).isEqualTo(GameMode.QUICK)
        val long = editor(MatchEditRoute(prefillDurationMin = 900, prefillMode = "long")).uiState.first { !it.loading }
        assertThat(long.form.duration).isEqualTo("600")
        assertThat(long.form.mode).isEqualTo(GameMode.LONG)
    }

    @Test
    fun prefillDoesNotTouchAnExistingMatch() = runTest {
        val original = Fixtures.matches.first { it.id == "m09" }
        val state = editor(MatchEditRoute("m09", prefillDurationMin = 12, prefillMode = "long"))
            .uiState.first { !it.loading }
        assertThat(state.form.duration).isEqualTo(original.durationMin.toString())
        assertThat(state.form.mode).isEqualTo(original.mode)
    }

    @Test
    fun whoWonFromTheTimerDefaultsAPendingMatchToCompletedDatedFromTheSessionStart() = runTest {
        val pending = Match(
            "pend",
            createdAt = Fixtures.at(15),
            playedAt = Fixtures.at(15),
            status = MatchStatus.PENDING,
            mode = GameMode.QUICK,
            participants = listOf(
                Participant(Fixtures.ann.id, "test-hero-1", Team.RED),
                Participant(Fixtures.bob.id, "test-hero-2", Team.BLUE),
            ),
            tieBreaker = Team.RED,
        )
        matches.upsert(pending)
        val start = Fixtures.at(15, hour = 19, minute = 5)
        val vm = editor(MatchEditRoute("pend", result = true, prefillPlayedAt = start + 42_000))
        val state = vm.uiState.first { !it.loading }
        assertThat(state.resultFirst).isTrue()
        assertThat(state.form.status).isEqualTo(MatchStatus.COMPLETED)
        assertThat(state.form.playedAt).isEqualTo(start) // whole minutes
        assertThat(state.dirty).isFalse()

        vm.save()
        assertThat(vm.uiState.first { it.saveAttempts == 1 }.errors).contains(MatchError.WinnerRequired)
        vm.setWinner(Team.BLUE)
        vm.setWinCondition(WinCondition.LAST_PUSH)
        vm.uiState.first { it.form.winCondition == WinCondition.LAST_PUSH }
        vm.save()

        // The result shows what changed instead of closing.
        val saved = vm.uiState.first { it.saved?.ratingsReady == true }.saved!!
        assertThat(saved.match.tieBreaker).isEqualTo(Team.RED)
        assertThat(saved.players.map { it.name }).containsExactly("Bob", "Ann").inOrder()
        assertThat(saved.players.first().delta!!).isGreaterThan(0.0)
        assertThat(saved.players.last().delta!!).isLessThan(0.0)
        assertThat(saved.leaderName).isNotNull()
        assertThat(saved.rematchOptions.map { it.kind }).containsExactly(
            RematchKind.SAME_SEATS,
            RematchKind.SWAP_HEROES,
            RematchKind.SWAP_TEAMS,
            null,
        )
        assertThat(matches.state.value.single { it.id == "pend" }.status).isEqualTo(MatchStatus.COMPLETED)
        assertThat(saved.match.playedAt).isEqualTo(start)
    }

    @Test
    fun rematchSavesANewPendingMatchWithoutACoinAndOpensSetup() = runTest {
        val vm = editor("m09")
        vm.uiState.first { !it.loading }
        vm.navigation.test {
            vm.rematch(RematchKind.SWAP_TEAMS)
            val open = awaitItem() as MatchNav.OpenSetup
            val created = matches.state.value.single { it.id == open.matchId }
            val source = Fixtures.matches.single { it.id == "m09" }
            assertThat(created.status).isEqualTo(MatchStatus.PENDING)
            assertThat(created.tieBreaker).isNull()
            assertThat(created.team(Team.RED).map { it.playerId }).isEqualTo(source.team(Team.BLUE).map { it.playerId })

            vm.rematch(null)
            assertThat(awaitItem()).isEqualTo(MatchNav.OpenDraft)
            assertThat(prefill.calls.single().red).isEqualTo(source.team(Team.RED).map { it.playerId }.distinct())
        }
    }

    @Test
    fun aPilotCanBeAddedFromThePickerAndFutureDatesAreRefused() = runTest {
        val vm = editor()
        val seat = vm.uiState.first { !it.loading }.form.red.first()
        vm.addPilot(Team.RED, seat.key, "  Zed ")
        val state = vm.uiState.first { it.form.red.first().playerId != null }
        val zed = players.state.value.single { it.name == "Zed" }
        assertThat(state.form.red.first().playerId).isEqualTo(zed.id)
        // A known name picks that player instead of creating a second one.
        vm.addPilot(Team.BLUE, state.form.blue.first().key, "ann")
        assertThat(vm.uiState.first { it.form.blue.first().playerId != null }.form.blue.first().playerId)
            .isEqualTo(Fixtures.ann.id)

        vm.setPlayedAt(System.currentTimeMillis() + 86_400_000)
        assertThat(vm.uiState.first { MatchError.FutureDate in it.errors }.errors).contains(MatchError.FutureDate)
    }

    @Test
    fun matchLogResumesPendingMatchesAndScrollsToTheSavedOne() = runTest {
        val log = MatchLogViewModel(matches, players, catalog, ratings, deleted, saved, rematches)
        saved.put("m09")
        assertThat(log.uiState.first { !it.loading }.highlightId).isEqualTo("m09")
        log.highlightShown("m09")
        assertThat(log.uiState.first { it.highlightId == null }.highlightId).isNull()
        log.navigation.test {
            log.resume("m09")
            assertThat(awaitItem()).isEqualTo(MatchNav.OpenSetup("m09"))
        }
    }

    @Test
    fun editingAMatchKeepsItsTieBreakerCoin() = runTest {
        val original = Fixtures.matches.first { it.id == "m09" }
        matches.upsert(original.copy(tieBreaker = Team.BLUE))
        val vm = editor("m09")
        vm.uiState.first { !it.loading }
        vm.setNotes("edited")
        vm.uiState.first { it.form.notes == "edited" }
        vm.save()
        vm.uiState.first { it.finished }
        val saved = matches.state.value.single { it.id == "m09" }
        assertThat(saved.notes).isEqualTo("edited")
        assertThat(saved.tieBreaker).isEqualTo(Team.BLUE)
    }

    @Test
    fun pendingMatchWithNoSeatsCanBeSavedAndTypedDurationIsCapped() = runTest {
        val vm = editor()
        val form = vm.uiState.first { !it.loading }.form
        (form.red + form.blue).forEach { vm.removeSeat(if (it in form.red) Team.RED else Team.BLUE, it.key) }
        vm.setStatus(MatchStatus.ABANDONED)
        vm.setNotes("Rained off")
        vm.setDuration("750")
        val state = vm.uiState.first { it.form.notes == "Rained off" }
        assertThat(state.form.duration).isEqualTo("600")
        assertThat(state.errors).isEmpty()
        vm.save()
        vm.uiState.first { it.finished }
        val saved = matches.state.value.single { m -> Fixtures.matches.none { it.id == m.id } }
        assertThat(saved.status).isEqualTo(MatchStatus.ABANDONED)
        assertThat(saved.participants).isEmpty()
        assertThat(saved.durationMin).isEqualTo(600)
    }

    @Test
    fun newMatchStartsWithTwoSeatsPerTeamAndRequiresPilotsAndWinner() = runTest {
        val vm = editor()
        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.form.red).hasSize(2)
            assertThat(state.form.blue).hasSize(2)
            assertThat(state.form.status).isEqualTo(MatchStatus.COMPLETED)
            assertThat(state.heroes).hasSize(12)
            assertThat(state.pilots.map { it.player.id }).doesNotContain(Fixtures.fay.id)
            vm.save()
            state = expectMostRecentItem()
            assertThat(state.showErrors).isTrue()
            assertThat(state.errors).containsAtLeast(MatchError.MissingPilot, MatchError.WinnerRequired)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(matches.state.value).hasSize(Fixtures.matches.size)
    }

    @Test
    fun doublePilotWithHandicapSavesAndOtherRulesAreEnforced() = runTest {
        val vm = editor()
        vm.uiState.first { !it.loading }
        val form = vm.uiState.value.form
        val (r1, r2) = form.red.map { it.key }
        val (b1, b2) = form.blue.map { it.key }
        // Official 3-player setup: Ann double-pilots Red, one hero with its Handicap card.
        vm.setPilot(Team.RED, r1, ann.id)
        vm.setPilot(Team.RED, r2, ann.id)
        vm.toggleHandicap(Team.RED, r2)
        vm.setPilot(Team.BLUE, b1, bob.id)
        vm.setPilot(Team.BLUE, b2, ann.id)
        vm.setHero(Team.RED, r1, hero(1))
        vm.setHero(Team.BLUE, b1, hero(1))
        vm.setDuration("4x5")
        var state = vm.uiState.first { it.form.duration == "45" }
        assertThat(state.form.doublePilots(Team.RED)).containsExactly(ann.id)
        assertThat(
            state.errors.map {
                it::class
            },
        ).containsAtLeast(MatchError.BothTeams::class, MatchError.DuplicateHero::class)
        assertThat(state.errors.filterIsInstance<MatchError.DuplicateHero>().single().heroName).isEqualTo("Test Hero 1")

        vm.setPilot(Team.BLUE, b2, cid.id)
        vm.setHero(Team.BLUE, b1, null)
        vm.setWinner(Team.BLUE)
        vm.setWinCondition(WinCondition.LIFE_COUNTERS)
        vm.setMode(GameMode.LONG)
        vm.setMap("Single lane")
        state = vm.uiState.first { it.form.map == "Single lane" }
        assertThat(state.errors).isEmpty()
        assertThat(state.dirty).isTrue()
        vm.save()
        assertThat(vm.uiState.first { it.saved != null }.saved).isNotNull()

        val saved = matches.state.value.single { m -> Fixtures.matches.none { it.id == m.id } }
        assertThat(saved.winner).isEqualTo(Team.BLUE)
        assertThat(saved.mode).isEqualTo(GameMode.LONG)
        assertThat(saved.durationMin).isEqualTo(45)
        assertThat(saved.participants.map { it.playerId }).containsExactly(ann.id, ann.id, bob.id, cid.id).inOrder()
        assertThat(saved.participants.map { it.handicap }).containsExactly(false, true, false, false).inOrder()
        assertThat(saved.participants[2].heroId).isNull()
    }

    @Test
    fun editingKeepsIdentityAndPendingClearsTheWinner() = runTest {
        val original = Fixtures.matches.first { it.id == "m09" }
        val vm = editor("m09")
        val state = vm.uiState.first { !it.loading }
        assertThat(state.isNew).isFalse()
        assertThat(state.form.red.map { it.playerId }).containsExactly(ann.id, bob.id).inOrder()
        vm.setStatus(MatchStatus.PENDING)
        vm.save()
        vm.uiState.first { it.finished }
        val saved = matches.state.value.single { it.id == "m09" }
        assertThat(saved.createdAt).isEqualTo(original.createdAt)
        assertThat(saved.status).isEqualTo(MatchStatus.PENDING)
        assertThat(saved.winner).isNull()
        assertThat(saved.winCondition).isNull()
        assertThat(saved.participants).isEqualTo(original.participants)
    }

    @Test
    fun swapTeamsFlipsSeatsAndWinner() = runTest {
        val vm = editor("m01")
        vm.uiState.first { !it.loading }
        vm.swapTeams()
        val form = vm.uiState.first { it.form.winner == Team.BLUE }.form
        assertThat(form.red.map { it.playerId }).containsExactly(cid.id, dee.id).inOrder()
    }

    @Test
    fun deleteFromEditorOffersUndoInTheLog() = runTest {
        val vm = editor("m05")
        vm.uiState.first { !it.loading }
        vm.delete()
        vm.uiState.first { it.finished }
        assertThat(matches.state.value.map { it.id }).doesNotContain("m05")

        val log = MatchLogViewModel(matches, players, catalog, ratings, deleted, saved, rematches)
        log.uiState.test {
            var state = awaitItem()
            while (state.loading || state.undo == null) state = awaitItem()
            assertThat(state.undo.id).isEqualTo("m05")
            log.undoDelete()
            state = awaitItem()
            while (state.undo != null || state.total != Fixtures.matches.size) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(matches.state.value.map { it.id }).contains("m05")
    }

    @Test
    fun logGroupsByDayNewestFirstWithDeltasOnce() = runTest {
        val log = MatchLogViewModel(matches, players, catalog, ratings, deleted, saved, rematches)
        val state = log.uiState.first { !it.loading }
        assertThat(state.groups.first().matches.map { it.id }).containsExactly("m10", "m09").inOrder()
        assertThat(state.groups.flatMap { it.matches }).hasSize(10)
        val doublePilot = state.groups.flatMap { it.matches }.single { it.id == "m03" }
        assertThat(doublePilot.red.map { it.doublePilot }).containsExactly(true, true)
        assertThat(doublePilot.red.mapNotNull { it.delta }).hasSize(1)
        assertThat(
            state.groups.flatMap {
                it.matches
            }.single { it.id == "m06" }.red[1].heroName,
        ).isEqualTo("Unknown hero")
        log.delete("m01")
        assertThat(log.uiState.first { it.undo != null }.undo!!.id).isEqualTo("m01")
        log.dismissUndo()
        assertThat(log.uiState.first { it.undo == null }.total).isEqualTo(9)
    }

    @Test
    fun playerDetailShowsHistoryHeroesAndRecentMatches() = runTest {
        val vm = PlayerDetailViewModel(ann.id, players, matches, ratings, catalog)
        val state = vm.uiState.first { !it.loading }
        assertThat(state.player).isEqualTo(ann)
        assertThat(state.history).hasSize(state.rating.games)
        assertThat(state.rating.games).isEqualTo(9)
        assertThat(state.doublePilotGames).isEqualTo(2)
        assertThat(state.heroesAreBest).isTrue()
        // Ann is 3–0 on hero 2 and 5–1 on hero 1.
        assertThat(state.heroes.map { it.heroId }).containsExactly(hero(2), hero(1)).inOrder()
        assertThat(state.recentMatches.map { it.id }.first()).isEqualTo("m10")
        assertThat(state.recentMatches).hasSize(5)
        vm.setArchived(true)
        assertThat(players.state.value.single { it.id == ann.id }.archived).isTrue()
    }

    @Test
    fun aLosingHeroIsNeverCalledBestAndProfilesShowHowAPlayerWins() = runTest {
        val log = listOf(
            Fixtures.match("x1", 1, Team.BLUE, Fixtures.red(cid, 7), Fixtures.blue(dee, 8)),
            Fixtures.match("x2", 2, Team.BLUE, Fixtures.red(cid, 7), Fixtures.blue(dee, 8)),
            Fixtures.match(
                "x3",
                3,
                Team.RED,
                Fixtures.red(cid, 7),
                Fixtures.blue(dee, 8),
                condition = WinCondition.LAST_PUSH,
            ),
        )
        val local = FakeMatchRepository(log)
        val snapshot = FakeRatingsSnapshotRepository(local, FakeDraftRecordRepository(), config)
        val state = PlayerDetailViewModel(cid.id, players, local, snapshot, catalog).uiState.first { !it.loading }
        // Cid is 1–2 on hero 7 (3 games): listed as most played, not "best".
        assertThat(state.heroesAreBest).isFalse()
        assertThat(state.heroes.single().record).isEqualTo(WinLoss(3, 1))
        assertThat(state.winConditions.counts).containsExactly(WinCondition.LAST_PUSH, 1)
        assertThat(state.historyDates).isEqualTo(log.map { it.playedAt })
    }

    @Test
    fun metaShowsGroupStatsAndSortsTheCompactTable() = runTest {
        val vm = MetaViewModel(ratings, players, catalog)
        val state = vm.uiState.first { !it.loading }
        assertThat(state.view).isEqualTo(MetaView.TABLE)
        assertThat(state.winConditions.total).isEqualTo(state.totalMatches)
        assertThat(state.red.games).isEqualTo(state.totalMatches)
        assertThat(state.night).isNotNull()
        assertThat(state.sortedHeroes).isEqualTo(state.ranked + state.unranked)
        vm.setSort(HeroSort.GAMES)
        val byGames = vm.uiState.first { it.sort == HeroSort.GAMES }.sortedHeroes.map { it.meta.record.games }
        assertThat(byGames).isInOrder(Comparator.reverseOrder<Int>())
        vm.setView(MetaView.CARDS)
        assertThat(vm.uiState.first { it.view == MetaView.CARDS }.sort).isEqualTo(HeroSort.GAMES)
    }

    @Test
    fun metaSplitsRankedHeroesAndNamesBestPilots() = runTest {
        val vm = MetaViewModel(ratings, players, catalog)
        val state = vm.uiState.first { !it.loading }
        assertThat(state.totalMatches).isEqualTo(9)
        assertThat(state.draftsWithBans).isEqualTo(1)
        assertThat(state.ranked.all { it.meta.record.games >= 5 }).isTrue()
        val top = state.ranked.first { it.heroId == hero(1) }
        assertThat(top.name).isEqualTo("Test Hero 1")
        assertThat(top.bestPlayer).isEqualTo(ann)
        assertThat(state.unranked.map { it.heroId }).contains(hero(11))
        assertThat(state.largerTeam.games).isEqualTo(2)
    }

    @Test
    fun ratingSettingsPersistAndReset() = runTest {
        val vm = RatingSettingsViewModel(config, ratings)
        vm.uiState.first { !it.loading }
        vm.update { it.copy(heroInfluence = 0.2, seatAdvantage = 500.0) }
        val state = vm.uiState.first { it.config.heroInfluence == 0.2 }
        assertThat(state.config.seatAdvantage).isEqualTo(200.0)
        assertThat(state.isDefault).isFalse()
        assertThat(state.ratedMatches).isEqualTo(9)
        vm.reset()
        assertThat(vm.uiState.first { it.isDefault }.config).isEqualTo(EloConfig.DEFAULT)
    }

    @Test
    fun backupExportsReadsAndImportsAfterConfirmation() = runTest {
        val backup =
            FakeBackupRepository(BackupDocument(exportedAt = 7, players = Fixtures.players, matches = Fixtures.matches))
        val vm = BackupViewModel(backup, players, matches).apply { io = UnconfinedTestDispatcher(testScheduler) }
        vm.uiState.first { it.players == Fixtures.players.size }

        val out = ByteArrayOutputStream()
        vm.export { out }
        assertThat(vm.uiState.first { it.message != null }.message).isEqualTo("Backup saved")
        vm.messageShown()
        assertThat(BackupCodec.decode(out.toString(Charsets.UTF_8.name())).matches).hasSize(10)

        vm.read { ByteArrayInputStream("garbage".toByteArray()) }
        assertThat(vm.uiState.first { it.message != null }.message).contains("isn't a Guards of Atlantis backup")
        vm.messageShown()

        vm.read { ByteArrayInputStream(out.toByteArray()) }
        val pending = vm.uiState.first { it.pendingImport != null }.pendingImport!!
        assertThat(pending.matches).isEqualTo(10)
        assertThat(backup.imports).isEmpty()
        vm.confirmImport(ImportMode.REPLACE)
        assertThat(vm.uiState.first { it.message != null }.message)
            .startsWith("Restored ${Fixtures.players.size} players and 10 matches")
        assertThat(backup.imports.single().second).isEqualTo(ImportMode.REPLACE)
        assertThat(vm.suggestedFileName(Fixtures.at(17))).isEqualTo("goa-backup-2026-10-17.json")
    }
}
