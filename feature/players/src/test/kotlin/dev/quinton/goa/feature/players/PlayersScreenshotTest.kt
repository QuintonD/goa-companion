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
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.backup.BackupSummary
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.presentation.players.HeroOption
import dev.quinton.goa.core.presentation.players.Leaderboard
import dev.quinton.goa.core.presentation.players.MatchForm
import dev.quinton.goa.core.presentation.players.MatchLog
import dev.quinton.goa.core.presentation.players.MatchLogUiState
import dev.quinton.goa.core.presentation.players.MatchValidation
import dev.quinton.goa.core.presentation.players.Meta
import dev.quinton.goa.core.presentation.players.MetaUiState
import dev.quinton.goa.core.presentation.players.MetaView
import dev.quinton.goa.core.presentation.players.PairSide
import dev.quinton.goa.core.presentation.players.PilotOption
import dev.quinton.goa.core.presentation.players.PlayerChangeUi
import dev.quinton.goa.core.presentation.players.PlayerDetail
import dev.quinton.goa.core.presentation.players.PlayersUiState
import dev.quinton.goa.core.presentation.players.SamePeopleOutcome
import dev.quinton.goa.core.presentation.players.SamePeoplePair
import dev.quinton.goa.core.presentation.players.SamePeopleUiState
import dev.quinton.goa.core.presentation.players.SavedResultUi
import dev.quinton.goa.core.presentation.players.SeatDraft
import dev.quinton.goa.core.presentation.players.rematchOptions
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.players.backup.BackupScreen
import dev.quinton.goa.feature.players.backup.BackupUiState
import dev.quinton.goa.feature.players.backup.ImportDialog
import dev.quinton.goa.feature.players.match.HeroPickerSheet
import dev.quinton.goa.feature.players.match.MatchEditActions
import dev.quinton.goa.feature.players.match.MatchEditScreen
import dev.quinton.goa.feature.players.match.MatchEditUiState
import dev.quinton.goa.feature.players.match.PilotPickerSheet
import dev.quinton.goa.feature.players.player.PlayerDetailActions
import dev.quinton.goa.feature.players.player.PlayerDetailScreen
import dev.quinton.goa.feature.players.player.PlayerEditActions
import dev.quinton.goa.feature.players.player.PlayerEditScreen
import dev.quinton.goa.feature.players.player.PlayerEditUiState
import dev.quinton.goa.feature.players.players.PlayersHomeActions
import dev.quinton.goa.feature.players.players.PlayersHomeScreen
import dev.quinton.goa.feature.players.players.PlayersTab
import dev.quinton.goa.feature.players.ratings.RatingSettingsActions
import dev.quinton.goa.feature.players.ratings.RatingSettingsScreen
import dev.quinton.goa.feature.players.ratings.RatingSettingsUiState
import dev.quinton.goa.feature.players.samepeople.SamePeopleActions
import dev.quinton.goa.feature.players.samepeople.SamePeopleScreen
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

    val players: PlayersUiState = Leaderboard.build(Fixtures.players, snapshot, showArchived = false)
    val matches = MatchLogUiState(
        loading = false,
        groups = MatchLog.group(Fixtures.matches, byId, catalog, snapshot.ratings.matchChanges, today),
        total = Fixtures.matches.size,
    )
    val meta: MetaUiState = Meta.build(snapshot, byId, catalog)
    val detail = PlayerDetail.build(Fixtures.ann.id, Fixtures.players, Fixtures.matches, snapshot, catalog)

    val samePeople = SamePeopleUiState(
        loading = false,
        pairs = listOf(
            SamePeoplePair(
                "ann",
                PairSide(Fixtures.ann, 9, Fixtures.at(15)),
                PairSide(Fixtures.ann.copy(id = "p-anna", name = "Anna", avatarId = 4, color = 0xFFEE9A52), 0, null),
            ),
            SamePeoplePair(
                "bob",
                PairSide(Fixtures.bob, 7, Fixtures.at(14)),
                PairSide(Fixtures.bob.copy(id = "p-bobby", name = "Bobby", avatarId = 6), 2, Fixtures.at(3)),
            ),
        ),
        differentCount = 1,
    )

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

    /** The hub sync needs a person (ADR-030 §5): a quiet dot on the sync icon. */
    @Test fun leaderboardHubAttentionLight() = shot(ThemeMode.LIGHT) {
        Home(PlayersTab.PLAYERS, Shots.players.copy(inGroup = true, hubNeedsAttention = true))
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

    /** "Same people?" (ADR-031): a quiet card on top when two players look like one person. */
    @Test fun leaderboardSamePeopleDark() = shot { Home(PlayersTab.PLAYERS, Shots.players.copy(samePeople = 1)) }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun leaderboardSamePeopleLightW384FontScale2() = shot(ThemeMode.LIGHT, 2f) {
        Home(PlayersTab.PLAYERS, Shots.players.copy(samePeople = 3))
    }

    @Test fun samePeopleDark() = shot { SamePeopleScreen(Shots.samePeople, SamePeopleActions()) }

    @Test fun samePeopleLight() = shot(ThemeMode.LIGHT) { SamePeopleScreen(Shots.samePeople, SamePeopleActions()) }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun samePeopleDarkW384FontScale2() = shot(fontScale = 2f) {
        SamePeopleScreen(Shots.samePeople, SamePeopleActions())
    }

    @Test fun samePeopleEmptyLight() = shot(ThemeMode.LIGHT) {
        SamePeopleScreen(SamePeopleUiState(loading = false, differentCount = 2), SamePeopleActions())
    }

    @Test fun samePeopleConfirmDark() = screen {
        SamePeopleScreen(Shots.samePeople, SamePeopleActions(), confirmKey = Shots.samePeople.pairs.first().key)
    }

    @Test fun samePeopleRefusedDark() = screen {
        val refused = SamePeopleOutcome.PlayedTogether(listOf("2026-10-12 · Ann, Bob vs Anna, Dee"))
        SamePeopleScreen(Shots.samePeople.copy(outcome = refused), SamePeopleActions())
    }

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

    /** In a group: "Invite Ann" (a personal invite, ADR-031) sits by Edit in the top bar. */
    @Test fun playerDetailInGroupLight() = shot(ThemeMode.LIGHT) {
        PlayerDetailScreen(Shots.detail.copy(canInvite = true), PlayerDetailActions())
    }

    /** Two people added on two phones with the same colour and crest: the gentle hint under the header. */
    @Test fun playerDetailLookAlikeDark() = shot {
        val twin = Fixtures.bob.copy(color = Fixtures.ann.color, avatarId = Fixtures.ann.avatarId)
        PlayerDetailScreen(Shots.detail.copy(lookAlike = twin), PlayerDetailActions())
    }

    @Test fun playerDetailLookAlikeLightFontScale2() = shot(ThemeMode.LIGHT, fontScale = 2f) {
        val twin = Fixtures.bob.copy(color = Fixtures.ann.color, avatarId = Fixtures.ann.avatarId)
        PlayerDetailScreen(Shots.detail.copy(lookAlike = twin), PlayerDetailActions())
    }

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
