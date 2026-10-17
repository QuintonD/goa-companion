package dev.quinton.goa.feature.players

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.backup.BackupSummary
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.players.backup.BackupScreen
import dev.quinton.goa.feature.players.backup.BackupUiState
import dev.quinton.goa.feature.players.backup.ImportDialog
import dev.quinton.goa.feature.players.match.HeroOption
import dev.quinton.goa.feature.players.match.HeroPickerSheet
import dev.quinton.goa.feature.players.match.MatchEditActions
import dev.quinton.goa.feature.players.match.MatchEditScreen
import dev.quinton.goa.feature.players.match.MatchEditUiState
import dev.quinton.goa.feature.players.match.MatchForm
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.MatchLogViewModel
import dev.quinton.goa.feature.players.match.MatchValidation
import dev.quinton.goa.feature.players.match.PilotOption
import dev.quinton.goa.feature.players.match.PilotPickerSheet
import dev.quinton.goa.feature.players.match.PlayerChangeUi
import dev.quinton.goa.feature.players.match.SavedResultUi
import dev.quinton.goa.feature.players.match.SeatDraft
import dev.quinton.goa.feature.players.match.rematchOptions
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.meta.MetaView
import dev.quinton.goa.feature.players.meta.MetaViewModel
import dev.quinton.goa.feature.players.player.PlayerDetailActions
import dev.quinton.goa.feature.players.player.PlayerDetailScreen
import dev.quinton.goa.feature.players.player.PlayerDetailViewModel
import dev.quinton.goa.feature.players.player.PlayerEditActions
import dev.quinton.goa.feature.players.player.PlayerEditScreen
import dev.quinton.goa.feature.players.player.PlayerEditUiState
import dev.quinton.goa.feature.players.players.PlayersHomeActions
import dev.quinton.goa.feature.players.players.PlayersHomeScreen
import dev.quinton.goa.feature.players.players.PlayersTab
import dev.quinton.goa.feature.players.players.PlayersUiState
import dev.quinton.goa.feature.players.players.PlayersViewModel
import dev.quinton.goa.feature.players.ratings.RatingSettingsActions
import dev.quinton.goa.feature.players.ratings.RatingSettingsScreen
import dev.quinton.goa.feature.players.ratings.RatingSettingsUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic fixtures only (Fixtures + TestCatalog) — never real game data. */
private object Shots {
    val catalog = TestCatalog.catalog
    val snapshot = RatingsSnapshot(
        EloCalculator.replay(Fixtures.matches, EloConfig.DEFAULT),
        MetaStats.compute(Fixtures.matches, Fixtures.drafts),
        EloConfig.DEFAULT,
        GroupStats.compute(Fixtures.matches, EloCalculator.replay(Fixtures.matches, EloConfig.DEFAULT)),
    )
    val byId = Fixtures.players.associateBy { it.id }
    val today: LocalDate = LocalDate.of(2026, 10, 15)

    val players: PlayersUiState = PlayersViewModel.leaderboard(Fixtures.players, snapshot, showArchived = false)
    val matches = MatchLogUiState(
        loading = false,
        groups = MatchLogViewModel.group(Fixtures.matches, byId, catalog, snapshot.ratings.matchChanges, today),
        total = Fixtures.matches.size,
    )
    val meta: MetaUiState = MetaViewModel.build(snapshot, byId, catalog)
    val detail = PlayerDetailViewModel.build(Fixtures.ann.id, Fixtures.players, Fixtures.matches, snapshot, catalog)

    val heroes = catalog.heroes.map { HeroOption(it.id, it.name, it.title, it.portrait, it.complexity) }

    /** "Result saved" for the latest fixture match, with its real replayed deltas. */
    fun saved(): MatchEditUiState {
        val ratings = snapshot.ratings
        val match = Fixtures.matches.filter { it.id in ratings.matchChanges }.maxBy { it.playedAt }
        val change = ratings.matchChanges.getValue(match.id)
        val ranked = Fixtures.players.filter { !it.archived && ratings.player(it.id).games > 0 }
            .sortedByDescending { ratings.player(it.id).value }
        val lines = match.participants.distinctBy { it.playerId }.map { seat ->
            PlayerChangeUi(
                player = byId[seat.playerId],
                name = byId.getValue(seat.playerId).name,
                team = seat.team,
                won = seat.team == match.winner,
                delta = change.playerDeltas[seat.playerId],
                rating = ratings.player(seat.playerId),
                rank = ranked.indexOfFirst { it.id == seat.playerId }.takeIf { it >= 0 }?.plus(1),
            )
        }.sortedWith(compareByDescending<PlayerChangeUi> { it.won }.thenByDescending { it.delta })
        return editor().copy(
            saved = SavedResultUi(
                match = match,
                ratingsReady = true,
                players = lines,
                leader = null,
                leaderName = ranked.first().name,
                leaderRating = ratings.player(ranked.first().id),
                rematchOptions = rematchOptions(match),
            ),
        )
    }

    fun editor(showErrors: Boolean = false): MatchEditUiState {
        val form = if (showErrors) {
            MatchForm(
                playedAt = Fixtures.at(15, 20),
                status = MatchStatus.COMPLETED,
                red = listOf(
                    SeatDraft(0, Fixtures.ann.id, Fixtures.hero(1)),
                    SeatDraft(1, null, Fixtures.hero(1)),
                ),
                blue = listOf(SeatDraft(2, Fixtures.bob.id, Fixtures.hero(3))),
            )
        } else {
            MatchForm(
                playedAt = Fixtures.at(15, 20),
                mode = GameMode.QUICK,
                map = "Single lane",
                status = MatchStatus.COMPLETED,
                winner = Team.RED,
                winCondition = WinCondition.THRONE,
                duration = "52",
                notes = "Ann double-piloted with a Handicap card.",
                red = listOf(
                    SeatDraft(0, Fixtures.ann.id, Fixtures.hero(1)),
                    SeatDraft(1, Fixtures.ann.id, Fixtures.hero(2), handicap = true),
                ),
                blue = listOf(
                    SeatDraft(2, Fixtures.bob.id, Fixtures.hero(3)),
                    SeatDraft(3, Fixtures.cid.id, null),
                ),
            )
        }
        return MatchEditUiState(
            loading = false,
            isNew = false,
            form = form,
            pilots = Fixtures.players.filterNot { it.archived }.map { PilotOption(it, snapshot.ratings.player(it.id)) },
            heroes = heroes,
            catalog = catalog,
            playersById = byId,
            errors = MatchValidation.validate(form, { catalog.hero(it)?.name ?: it }, { byId[it]?.name ?: it }),
            showErrors = showErrors,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlayersScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun setContent(mode: ThemeMode, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        setContent(mode, fontScale, content)
        compose.onRoot().captureRoboImage()
    }

    /** Captures every window, so dialogs and bottom sheets are included. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun screen(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        setContent(mode, 1f, content)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Composable
    private fun Home(tab: PlayersTab, players: PlayersUiState = Shots.players) = PlayersHomeScreen(
        tab = tab,
        players = players,
        matches = Shots.matches,
        meta = Shots.meta,
        actions = PlayersHomeActions(),
        snackbarHostState = remember { SnackbarHostState() },
    )

    @Test fun leaderboardDark() = shot { Home(PlayersTab.PLAYERS) }

    @Test fun leaderboardLight() = shot(ThemeMode.LIGHT) { Home(PlayersTab.PLAYERS) }

    @Test fun leaderboardFontScale2() = shot(fontScale = 2f) { Home(PlayersTab.PLAYERS) }

    @Test fun leaderboardEmptyDark() = shot { Home(PlayersTab.PLAYERS, PlayersUiState(loading = false)) }

    @Test fun matchLogDark() = shot { Home(PlayersTab.MATCHES) }

    @Test fun matchLogLight() = shot(ThemeMode.LIGHT) { Home(PlayersTab.MATCHES) }

    @Test fun matchLogFontScale2() = shot(fontScale = 2f) { Home(PlayersTab.MATCHES) }

    @Test fun metaDark() = shot { Home(PlayersTab.META) }

    @Test fun metaLight() = shot(ThemeMode.LIGHT) { Home(PlayersTab.META) }

    @Test fun playerDetailDark() = shot { PlayerDetailScreen(Shots.detail, PlayerDetailActions()) }

    @Test fun playerDetailLight() = shot(ThemeMode.LIGHT) { PlayerDetailScreen(Shots.detail, PlayerDetailActions()) }

    @Test fun playerDetailFontScale2() = shot(fontScale = 2f) {
        PlayerDetailScreen(Shots.detail, PlayerDetailActions())
    }

    @Test fun playerEditDark() = shot {
        PlayerEditScreen(
            PlayerEditUiState(loading = false, isNew = true, name = "Gus", color = 0xFFB892E6, avatarId = 5),
            PlayerEditActions(),
        )
    }

    @Test fun matchEditDark() = shot { MatchEditScreen(Shots.editor(), MatchEditActions()) }

    @Test fun matchEditLight() = shot(ThemeMode.LIGHT) { MatchEditScreen(Shots.editor(), MatchEditActions()) }

    @Test fun matchEditFontScale2() = shot(fontScale = 2f) { MatchEditScreen(Shots.editor(), MatchEditActions()) }

    @Test fun matchEditErrorsDark() = shot { MatchEditScreen(Shots.editor(showErrors = true), MatchEditActions()) }

    @Test fun whoWonDark() = shot {
        val state = Shots.editor()
        MatchEditScreen(
            state.copy(resultFirst = true, form = state.form.copy(winner = null, winCondition = null)),
            MatchEditActions(),
        )
    }

    @Test fun resultSavedDark() = shot { MatchEditScreen(Shots.saved(), MatchEditActions()) }

    @Test fun resultSavedFontScale2() = shot(fontScale = 2f) { MatchEditScreen(Shots.saved(), MatchEditActions()) }

    @Test fun metaCardsDark() = shot {
        PlayersHomeScreen(
            tab = PlayersTab.META,
            players = Shots.players,
            matches = Shots.matches,
            meta = Shots.meta.copy(view = MetaView.CARDS),
            actions = PlayersHomeActions(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }

    @Test fun metaTableFontScale2() = shot(fontScale = 2f) { Home(PlayersTab.META) }

    @Test fun pilotPickerEmptyGroupDark() = screen {
        PilotPickerSheet(
            team = Team.RED,
            pilots = emptyList(),
            selected = null,
            sameTeam = emptySet(),
            otherTeam = emptySet(),
            onPick = {},
            onDismiss = {},
            onAddPlayer = {},
        )
    }

    @Test fun heroPickerDark() = screen {
        HeroPickerSheet(
            heroes = Shots.heroes,
            selected = Fixtures.hero(3),
            taken = setOf(Fixtures.hero(1), Fixtures.hero(2)),
            onPick = {},
            onDismiss = {},
        )
    }

    @Test fun backupDark() = shot {
        BackupScreen(BackupUiState(players = 6, matches = 10), dev.quinton.goa.feature.players.backup.BackupActions())
    }

    @Test fun importDialogDark() = screen {
        ImportDialog(
            BackupSummary(
                1,
                Fixtures.at(12),
                players = 6,
                matches = 42,
                drafts = 7,
                hasEloConfig = true,
                extras = emptySet(),
            ),
            onConfirm = { _, _ -> },
            onCancel = {},
        )
    }

    @Test fun ratingSettingsDark() = shot {
        RatingSettingsScreen(
            RatingSettingsUiState(
                loading = false,
                config = EloConfig(seatAdvantage = 15.0),
                ratedMatches = 9,
                skippedMatches = 1,
            ),
            RatingSettingsActions(),
        )
    }

    @Test fun ratingSettingsFontScale2() = shot(fontScale = 2f) {
        RatingSettingsScreen(RatingSettingsUiState(loading = false, ratedMatches = 9), RatingSettingsActions())
    }
}
