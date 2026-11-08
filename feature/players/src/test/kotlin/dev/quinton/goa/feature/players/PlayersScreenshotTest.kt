package dev.quinton.goa.feature.players

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.backup.BackupSummary
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.designsystem.component.LocalPlayerFaces
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.PlayerFaces
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.core.testing.bundledHero
import dev.quinton.goa.feature.players.backup.BackupScreen
import dev.quinton.goa.feature.players.backup.BackupUiState
import dev.quinton.goa.feature.players.backup.ImportDialog
import dev.quinton.goa.feature.players.match.HeroOption
import dev.quinton.goa.feature.players.match.HeroPickerSheet
import dev.quinton.goa.feature.players.match.MatchEditActions
import dev.quinton.goa.feature.players.match.MatchEditScreen
import dev.quinton.goa.feature.players.match.MatchEditUiState
import dev.quinton.goa.feature.players.match.MatchEditing
import dev.quinton.goa.feature.players.match.MatchForm
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.MatchLogViewModel
import dev.quinton.goa.feature.players.match.MatchValidation
import dev.quinton.goa.feature.players.match.PilotOption
import dev.quinton.goa.feature.players.match.PilotPickerSheet
import dev.quinton.goa.feature.players.match.SeatDraft
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The synthetic match log (Fixtures: fake players, results and ratings) played with real bundled
 * heroes, so the screens show the names and official portraits the app shows. "Test Hero N" becomes
 * the N-th hero of [REAL_HEROES]; nothing about a hero is made up.
 */
private object Shots {
    private val REAL_HEROES = listOf(
        "arien", "brogan", "tigerclaw", "sabina", "xargatha", "wasp", "dodger", "rowenna",
        "garrus", "snorri", "misa", "bain", "cutter", "mrak", "whisper",
    )

    fun real(id: String?): String? =
        id?.removePrefix("test-hero-")?.toIntOrNull()?.let { REAL_HEROES[(it - 1) % REAL_HEROES.size] } ?: id

    fun hero(i: Int): String = real(Fixtures.hero(i))!!

    val catalog = Catalog(
        dataVersion = TestCatalog.catalog.dataVersion,
        printing = 2,
        products = TestCatalog.catalog.products,
        heroes = REAL_HEROES.map(::bundledHero),
    )
    val log = Fixtures.matches.map { m ->
        m.copy(participants = m.participants.map { it.copy(heroId = real(it.heroId)) })
    }
    private val drafts = Fixtures.drafts.map { d ->
        d.copy(pool = d.pool.mapNotNull(::real), steps = d.steps.map { it.copy(heroId = real(it.heroId)!!) })
    }
    val snapshot = RatingsSnapshot(
        EloCalculator.replay(log, EloConfig.DEFAULT),
        MetaStats.compute(log, drafts),
        EloConfig.DEFAULT,
        GroupStats.compute(log, EloCalculator.replay(log, EloConfig.DEFAULT)),
    )
    val byId = Fixtures.players.associateBy { it.id }

    /** Each player's most-played hero, as the app derives it at its root. */
    val faces = PlayerFaces.from(snapshot.ratings, catalog)
    val today: LocalDate = LocalDate.of(2026, 10, 15)

    val players: PlayersUiState = PlayersViewModel.leaderboard(Fixtures.players, snapshot, showArchived = false)
    val matches = MatchLogUiState(
        loading = false,
        groups = MatchLogViewModel.group(log, byId, catalog, snapshot.ratings.matchChanges, today),
        total = log.size,
    )
    val meta: MetaUiState = MetaViewModel.build(snapshot, byId, catalog)
    val detail = PlayerDetailViewModel.build(Fixtures.ann.id, Fixtures.players, log, snapshot, catalog)

    val heroes = catalog.heroes.map { HeroOption(it.id, it.name, it.title, it.portrait, it.complexity) }

    /** "Result saved" for the latest fixture match, with its real replayed deltas (the app's own summary). */
    fun saved(): MatchEditUiState {
        val ratings = snapshot.ratings
        val match = log.filter { it.id in ratings.matchChanges }.maxBy { it.playedAt }
        return editor().copy(saved = MatchEditing.savedSummary(match, Fixtures.players, ratings))
    }

    fun editor(showErrors: Boolean = false): MatchEditUiState {
        val form = if (showErrors) {
            MatchForm(
                playedAt = Fixtures.at(15, 20),
                status = MatchStatus.COMPLETED,
                red = listOf(
                    SeatDraft(0, Fixtures.ann.id, Shots.hero(1)),
                    SeatDraft(1, null, Shots.hero(1)),
                ),
                blue = listOf(SeatDraft(2, Fixtures.bob.id, Shots.hero(3))),
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
                    SeatDraft(0, Fixtures.ann.id, Shots.hero(1)),
                    SeatDraft(1, Fixtures.ann.id, Shots.hero(2), handicap = true),
                ),
                blue = listOf(
                    SeatDraft(2, Fixtures.bob.id, Shots.hero(3)),
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
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalPlayerFaces provides Shots.faces,
            ) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    @Before fun art() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        setContent(mode, fontScale, content)
        compose.waitUntil(ART_TIMEOUT_MS) { BundledAssetImages.idle }
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

    /** The sync server needs a person (ADR-036): a quiet dot on the sync icon. */
    @Test fun leaderboardServerAttentionLight() = shot(ThemeMode.LIGHT) {
        Home(PlayersTab.PLAYERS, Shots.players.copy(inGroup = true, serverNeedsAttention = true))
    }

    /** Group sync: this phone's player wears "You" and the top bar opens their page. */
    @Test fun leaderboardWithYouLight() = shot(ThemeMode.LIGHT) {
        val players = Shots.players
        val me = players.ranked[1].player.id
        Home(
            PlayersTab.PLAYERS,
            players.copy(meId = me, inGroup = true, ranked = players.ranked.map { it.copy(isMe = it.player.id == me) }),
        )
    }

    @Test fun leaderboardFontScale2() = shot(fontScale = 2f) { Home(PlayersTab.PLAYERS) }

    @Test fun leaderboardEmptyDark() = shot { Home(PlayersTab.PLAYERS, PlayersUiState(loading = false)) }

    @Test fun matchLogDark() = shot { Home(PlayersTab.MATCHES) }

    @Test fun matchLogLight() = shot(ThemeMode.LIGHT) { Home(PlayersTab.MATCHES) }

    @Test fun matchLogFontScale2() = shot(fontScale = 2f) { Home(PlayersTab.MATCHES) }

    /** Large text, side by side: hero names wrap the rating change below instead of "Xarga…". */
    @Test fun matchLogLightFontScale13() = shot(ThemeMode.LIGHT, fontScale = 1.3f) { Home(PlayersTab.MATCHES) }

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

    /** As the app opens it after a game: no winner yet, so Save result is off and says why. */
    @Test fun whoWonDark() = shot {
        val state = Shots.editor()
        val form = state.form.copy(winner = null, winCondition = null)
        MatchEditScreen(
            state.copy(resultFirst = true, form = form, errors = MatchValidation.validate(form)),
            MatchEditActions(),
        )
    }

    /** The winner chosen: the bronze trophy and frame, and Save result on. */
    @Test fun whoWonChosenDark() = shot {
        val state = Shots.editor()
        MatchEditScreen(state.copy(resultFirst = true), MatchEditActions())
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
            selected = Shots.hero(3),
            taken = setOf(Shots.hero(1), Shots.hero(2)),
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

/** TalkBack: the winner buttons and win-condition chips are radio buttons that announce "selected". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WhoWonSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun winnerAndWinConditionExposeRadioSelection() {
        val state = Shots.editor()
        compose.setContent {
            GoaTheme { MatchEditScreen(state.copy(resultFirst = true), MatchEditActions()) }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val winner = hasContentDescription("won", substring = true) and radio
        compose.onNode(winner and isSelected()).assertExists()
        compose.onNode(winner and isNotSelected()).assertExists()
        compose.onNode(hasText("Throne") and radio and isSelected()).assertExists()
        compose.onNode(hasText("Last push") and radio and isNotSelected()).assertExists()
    }
}

private const val ART_TIMEOUT_MS = 15_000L
