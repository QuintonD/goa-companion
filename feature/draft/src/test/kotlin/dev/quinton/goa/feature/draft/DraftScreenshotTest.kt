package dev.quinton.goa.feature.draft

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.draft.board.BoardActions
import dev.quinton.goa.feature.draft.board.BoardLocal
import dev.quinton.goa.feature.draft.board.BoardUiState
import dev.quinton.goa.feature.draft.board.DraftBoardScreen
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.methods.DraftMethodEditScreen
import dev.quinton.goa.feature.draft.methods.DraftMethodsScreen
import dev.quinton.goa.feature.draft.methods.MethodEditActions
import dev.quinton.goa.feature.draft.methods.MethodEditUiState
import dev.quinton.goa.feature.draft.methods.MethodRow
import dev.quinton.goa.feature.draft.methods.MethodsActions
import dev.quinton.goa.feature.draft.methods.MethodsUiState
import dev.quinton.goa.feature.draft.methods.PreviewLayout
import dev.quinton.goa.feature.draft.methods.previewOf
import dev.quinton.goa.feature.draft.setup.DraftSetupScreen
import dev.quinton.goa.feature.draft.setup.SetupActions
import dev.quinton.goa.feature.draft.setup.SetupStage
import dev.quinton.goa.feature.draft.setup.TeamMode
import dev.quinton.goa.feature.draft.setup.previewSetupState
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.PreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DraftScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val heroes = TestCatalog.heroes.associate { it.id to HeroInfo.of(it) }
    private val players = PreviewData.players.associateBy { it.id }

    private fun capture(theme: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { GoaTheme(themeMode = theme) { content() } }
        compose.onRoot().captureRoboImage()
    }

    private fun session(choices: Int, seconds: Int? = 30, method: DraftMethod = PreviewData.pattern): DraftSession {
        val ids = PreviewData.players.map { it.id }
        val seats = SeatLayouts.build(ids.take(3), ids.drop(3), OddSeatMode.HANDICAP).getOrThrow()
        var s = DraftEngine.start(
            DraftSetup(
                "d",
                0L,
                method,
                seats,
                Team.BLUE,
                TestCatalog.heroes.map {
                    it.id
                },
                seed = 9L,
                stepSeconds = seconds,
            ),
        ).getOrThrow()
        repeat(choices) { s = DraftEngine.apply(s, DraftCommand(DraftEngine.available(s).first())).getOrThrow() }
        return s
    }

    private fun board(session: DraftSession, local: BoardLocal = BoardLocal(remainingSeconds = 24)) =
        BoardUiState(
            loading = false,
            progress = DraftProgress.Board(session),
            heroes = heroes,
            players = players,
            local = local,
        )

    // ---- Setup wizard

    @Test fun setupPlayersDark() = capture {
        DraftSetupScreen(previewSetupState(SetupStage.PLAYERS), SetupActions.None)
    }

    @Test fun setupTeamsDark() = capture { DraftSetupScreen(previewSetupState(SetupStage.TEAMS), SetupActions.None) }

    @Test fun setupTeamsLight() = capture(ThemeMode.LIGHT) {
        DraftSetupScreen(previewSetupState(SetupStage.TEAMS), SetupActions.None)
    }

    @Test fun setupPlayerDraftDark() = capture {
        DraftSetupScreen(previewSetupState(SetupStage.TEAMS, TeamMode.PLAYER_DRAFT), SetupActions.None)
    }

    @Test fun setupHeroesDark() = capture { DraftSetupScreen(previewSetupState(SetupStage.HEROES), SetupActions.None) }

    @Test fun setupCoinDark() = capture { DraftSetupScreen(previewSetupState(SetupStage.COIN), SetupActions.None) }

    @Test fun setupCoinFontScale2() = capture(fontScale = 2f) {
        DraftSetupScreen(previewSetupState(SetupStage.COIN), SetupActions.None)
    }

    // ---- Board

    @Test fun boardStartDark() = capture { DraftBoardScreen(board(session(0)), BoardActions.None) }

    @Test fun boardMidDark() = capture { DraftBoardScreen(board(session(3)), BoardActions.None) }

    @Test fun boardMidLight() = capture(ThemeMode.LIGHT) { DraftBoardScreen(board(session(3)), BoardActions.None) }

    @Test fun boardMidFontScale2() = capture(fontScale = 2f) { DraftBoardScreen(board(session(3)), BoardActions.None) }

    @Test fun boardPrivacyGateDark() = capture {
        val hidden = PreviewData.pattern.copy(
            sequence = SequenceRule.Pattern(
                repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
            ),
        )
        DraftBoardScreen(board(session(1, method = hidden)), BoardActions.None)
    }

    @Test fun boardCompleteDark() = capture {
        val done = session(8)
        DraftBoardScreen(board(done, BoardLocal(selectedSeat = done.seats.first().id)), BoardActions.None)
    }

    @Test fun boardCompleteLight() = capture(ThemeMode.LIGHT) { DraftBoardScreen(board(session(8)), BoardActions.None) }

    @Test fun playerDraftCaptainsDark() = capture {
        val allPick = PreviewData.pattern.copy(
            pool = dev.quinton.goa.core.domain.draft.PoolRule.All,
            sequence = SequenceRule.Pattern(repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B))),
        )
        val ids = PreviewData.players.map { it.id }
        var s = PlayerDraftSession.start(
            "p", 0, allPick, ids, ids[0], ids[1], Team.RED,
            TestCatalog.heroes.map {
                it.id
            },
            1,
        ).getOrThrow()
        ids.forEachIndexed { i, id ->
            s = s.copy(selection = HeroSelections.choose(s.selection, id, TestCatalog.heroes[i].id).getOrThrow())
        }
        s = s.copy(captains = PlayerDraft.pick(s.captains, ids[2]).getOrThrow())
        DraftBoardScreen(
            BoardUiState(loading = false, progress = DraftProgress.Players(s), heroes = heroes, players = players),
            BoardActions.None,
        )
    }

    @Test fun boardEmptyDark() = capture {
        DraftBoardScreen(BoardUiState(loading = false, heroes = heroes, players = players), BoardActions.None)
    }

    // ---- Methods

    private fun methods(): MethodsUiState {
        val layout = PreviewLayout.OPTIONS.first()
        val m = PreviewData.pattern
        return MethodsUiState(
            loading = false,
            layout = layout,
            builtIn = listOf(MethodRow(m, previewOf(m, layout), isDefault = true)),
            custom = listOf(MethodRow(m.copy(id = "c", name = "Blind pairs"), previewOf(m, layout), isDefault = false)),
        )
    }

    @Test fun methodsDark() = capture { DraftMethodsScreen(methods(), MethodsActions.None) }

    @Test fun methodsLight() = capture(ThemeMode.LIGHT) { DraftMethodsScreen(methods(), MethodsActions.None) }

    @Test fun methodEditDark() = capture {
        val pattern = PreviewData.pattern.sequence as SequenceRule.Pattern
        DraftMethodEditScreen(
            MethodEditUiState(
                loading = false,
                isNew = false,
                id = "c",
                name = "Blind pairs",
                opening = pattern.opening,
                repeat = pattern.repeat.mapIndexed { i, s -> if (i == 0) s.copy(hidden = true) else s },
            ),
            MethodEditActions.None,
        )
    }

    @Test fun methodEditFontScale2() = capture(fontScale = 2f) {
        val pattern = PreviewData.pattern.sequence as SequenceRule.Pattern
        DraftMethodEditScreen(
            MethodEditUiState(
                loading = false,
                id = "c",
                name = "Blind pairs",
                opening = pattern.opening,
                repeat = pattern.repeat,
            ),
            MethodEditActions.None,
        )
    }
}
