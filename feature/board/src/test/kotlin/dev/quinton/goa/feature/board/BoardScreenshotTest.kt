package dev.quinton.goa.feature.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeBoardRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.board.AddContent
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardOverlay
import dev.quinton.goa.feature.board.board.BoardScreen
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.board.NewPiece
import dev.quinton.goa.feature.board.board.PiecesContent
import dev.quinton.goa.feature.board.list.BoardsScreen
import dev.quinton.goa.feature.board.list.BoardsViewModel
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardScreen
import dev.quinton.goa.feature.board.setup.NewBoardUiState
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import dev.quinton.goa.feature.board.setup.SourcesContent
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board screens with the bundled map and real catalog heroes and portraits. Line-up for every
 * board: Arien and Brogan (Red) against Wasp and Tigerclaw (Blue).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BoardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = bundledCatalog
    private val maps = bundledMaps
    private val lineup = BoardLineup(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw"))
    private val now = LocalDateTime.of(2026, 10, 19, 15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext())

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        before: () -> Unit = {
        },
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    // ---- Boards --------------------------------------------------------------------------

    private fun savedBoards(): List<SavedBoard> {
        val geometry = BoardGeometry.of(maps.first())
        fun board(id: String, name: String, lineup: BoardLineup, hoursAgo: Long) = SavedBoard(
            id = id,
            name = name,
            lineup = lineup,
            state = BoardSetup.opening(geometry, lineup, ids()),
            createdAt = now - hoursAgo * HOUR,
            updatedAt = now - hoursAgo * HOUR,
        )
        return listOf(
            board("b1", "Arien, Brogan vs Wasp, Tigerclaw", lineup, 1),
            board(
                "b2",
                "Friday rematch: can Xargatha reach the back line?",
                BoardLineup(listOf("xargatha", "dodger", "misa"), listOf("sabina", "whisper", "ursafar")),
                30,
            ),
            board("b3", "Snorri opening", BoardLineup(listOf("snorri"), emptyList()), 24 * 9),
        )
    }

    /** Deterministic piece ids for the screenshots (the app uses UUIDs). */
    private fun ids(): () -> String {
        var n = 0
        return { "p${++n}" }
    }

    @Composable
    private fun Boards(boards: List<SavedBoard>, withDraft: Boolean = true) {
        val vm =
            remember {
                BoardsViewModel(
                    FakeCatalogRepository(catalog),
                    FakeBoardMapRepository(maps),
                    FakeBoardRepository(boards),
                    if (withDraft) drafts else FakeDraftRecordRepository(),
                    if (withDraft) matches else FakeMatchRepository(),
                    EditClock { now },
                )
            }
        val state by vm.uiState.collectAsState()
        BoardsScreen(
            state,
            onOpen = {},
            onNew = {},
            onSetUp = {},
            onRename = { _, _ -> },
            onDuplicate = {},
            onDelete = {},
            onBack = {},
        )
    }

    /** No boards and no draft yet (the list opens on New board when empty; this shows after deleting all). */
    @Test fun boardsEmptyDark() = capture(ThemeMode.DARK) { Boards(emptyList(), withDraft = false) }

    /** No boards, but a finished draft: its line-up is one tap away. */
    @Test fun boardsEmptyLight() = capture(ThemeMode.LIGHT) { Boards(emptyList()) }

    @Test fun boardsDark() = capture(ThemeMode.DARK) { Boards(savedBoards()) }

    @Test fun boardsLight() = capture(ThemeMode.LIGHT) { Boards(savedBoards()) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun boardsW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Boards(savedBoards()) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun boardsLandscapeDark() = capture(ThemeMode.DARK) { Boards(savedBoards()) }

    // ---- New board -----------------------------------------------------------------------

    private val drafts = FakeDraftRecordRepository(
        listOf(
            DraftRecord(
                id = "d1",
                createdAt = now - 2 * HOUR,
                methodId = "house",
                methodKind = DraftMethodKind.CUSTOM,
                pool = emptyList(),
                teamA = Team.BLUE,
                steps = listOf("wasp" to Team.BLUE, "arien" to Team.RED, "brogan" to Team.RED, "tigerclaw" to Team.BLUE)
                    .map { (hero, team) -> DraftStepRecord(team, DraftAction.PICK, hero) },
            ),
        ),
    )

    private val matches = FakeMatchRepository(
        listOf(
            Match(
                id = "m1",
                createdAt = now - 26 * HOUR,
                playedAt = now - 26 * HOUR,
                status = MatchStatus.COMPLETED,
                mode = GameMode.QUICK,
                winner = Team.RED,
                participants = listOf(
                    Participant("p1", "xargatha", Team.RED),
                    Participant("p2", "dodger", Team.RED),
                    Participant("p3", "misa", Team.RED),
                    Participant("p4", "sabina", Team.BLUE),
                    Participant("p5", "whisper", Team.BLUE),
                    Participant("p6", "ursafar", Team.BLUE),
                ),
            ),
            Match(
                id = "m2",
                createdAt = now - HOUR,
                playedAt = now - HOUR,
                status = MatchStatus.PENDING,
                mode = GameMode.QUICK,
                participants = listOf(Participant("p1", "snorri", Team.RED), Participant("p2", "gydion", Team.BLUE)),
            ),
        ),
    )

    private fun newBoardViewModel(
        route: NewBoardRoute = NewBoardRoute(),
        settings: AppSettings = AppSettings(),
        withDraft: Boolean = true,
    ) = NewBoardViewModel(
        route,
        SavedStateHandle(),
        FakeCatalogRepository(catalog),
        FakeBoardMapRepository(maps),
        if (withDraft) drafts else FakeDraftRecordRepository(),
        if (withDraft) matches else FakeMatchRepository(),
        FakeSettingsRepository(settings),
    )

    @Composable
    private fun NewBoard(vm: NewBoardViewModel) {
        val state by vm.uiState.collectAsState()
        NewBoardScreen(state, onAction = vm::onAction, onSetUp = {}, onSetUpLatest = {}, onBack = {})
    }

    /** Brogan, Tigerclaw, Arien: the next pick is Blue's (picks alternate like a draft). */
    private fun filled() = newBoardViewModel().also { vm ->
        listOf("brogan", "tigerclaw", "arien").forEach { vm.onAction(NewBoardAction.Tap(it)) }
    }

    /** The latest draft on top: "Set up" is one tap. */
    @Test fun newBoardEmptyDark() = capture(ThemeMode.DARK) { NewBoard(remember { newBoardViewModel() }) }

    /** Nothing drafted yet, the group owns the core box only: its heroes, "Show all" below. */
    @Test
    fun newBoardOwnedLight() = capture(ThemeMode.LIGHT) {
        NewBoard(
            remember {
                val core = catalog.products.first().id
                newBoardViewModel(settings = AppSettings(ownedProducts = setOf(core)), withDraft = false)
            },
        )
    }

    @Test fun newBoardDark() = capture(ThemeMode.DARK) { NewBoard(remember { filled() }) }

    @Test fun newBoardLight() = capture(ThemeMode.LIGHT) { NewBoard(remember { filled() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newBoardW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { NewBoard(remember { filled() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newBoardLandscapeDark() = capture(ThemeMode.DARK) { NewBoard(remember { filled() }) }

    /** "Use a draft or match": the latest draft, a pending and a logged match, newest first. */
    @Test
    fun newBoardSourcesDark() = capture(ThemeMode.DARK) {
        val vm = remember { newBoardViewModel() }
        val state by vm.uiState.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            (state as? NewBoardUiState.Ready)?.let { SourcesContent(it.sources, onUse = {}, onDone = {}) }
        }
    }

    // ---- Board ---------------------------------------------------------------------------

    private fun boardViewModel(
        mapId: String? = null,
        board: BoardLineup = lineup,
        route: BoardRoute = BoardRoute(red = board.red, blue = board.blue, mapId = mapId),
    ): BoardViewModel = BoardViewModel(
        route = route,
        savedState = SavedStateHandle(),
        catalogRepository = FakeCatalogRepository(catalog),
        mapRepository = FakeBoardMapRepository(maps),
        boardRepository = FakeBoardRepository(),
        draftRepository = FakeDraftRecordRepository(),
        settingsRepository = FakeSettingsRepository(),
        clock = EditClock { now },
        newPieceId = ids(),
    )

    private fun BoardViewModel.ready(): BoardUiState.Ready {
        compose.waitUntil(IMAGE_TIMEOUT_MS) { uiState.value is BoardUiState.Ready }
        return uiState.value as BoardUiState.Ready
    }

    private fun BoardViewModel.hero(id: String) = ready().pieces.filterIsInstance<HeroPiece>().first { it.heroId == id }

    @Composable
    private fun Board(vm: BoardViewModel, overlay: BoardOverlay? = null, collapsed: Boolean = false) {
        val state by vm.uiState.collectAsState()
        BoardScreen(
            state,
            onAction = vm::onAction,
            onTap = vm::tap,
            onBack = {},
            initialOverlay = overlay,
            initialCollapsed = collapsed,
        )
    }

    @Test fun boardOpeningDark() = capture(ThemeMode.DARK) { Board(remember { boardViewModel() }) }

    @Test fun boardOpeningLight() = capture(ThemeMode.LIGHT) { Board(remember { boardViewModel() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun boardOpeningW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Board(remember { boardViewModel() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun boardOpeningLandscapeDark() = capture(ThemeMode.DARK) { Board(remember { boardViewModel() }) }

    /** Arien selected with Rogue Wave (Movement 4, Range 2): movement and range overlays. */
    private fun selectedHero(vm: BoardViewModel) {
        vm.onAction(BoardAction.Select(vm.hero("arien").id))
        vm.onAction(BoardAction.ChooseCard("arien-red-2b"))
    }

    @Test fun boardHeroOverlaysDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = { selectedHero(vm) }) { Board(vm) }
    }

    @Test fun boardHeroOverlaysLight() = boardViewModel().let { vm ->
        capture(ThemeMode.LIGHT, before = { selectedHero(vm) }) { Board(vm) }
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun boardHeroOverlaysW384DarkFontScale2() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, 2f, before = { selectedHero(vm) }) { Board(vm) }
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun boardHeroOverlaysLandscapeDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = { selectedHero(vm) }) { Board(vm) }
    }

    /** Fast Travel from the opening: Arien's own and adjacent Zones, none with an enemy. */
    @Test
    fun boardFastTravelDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = {
            vm.onAction(BoardAction.Select(vm.hero("arien").id))
            vm.onAction(BoardAction.ToggleFastTravel)
        }) { Board(vm) }
    }

    /** Measure from Arien to the nearest Blue minion. */
    @Test
    fun boardMeasureDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = {
            val arien = vm.hero("arien")
            vm.onAction(BoardAction.Select(arien.id))
            vm.onAction(BoardAction.StartMeasure)
            val target = vm.ready().pieces.filterIsInstance<MinionPiece>().filter { it.team == Team.BLUE }
                .minBy { HexGrid.distance(it.at!!, arien.at!!) }
            vm.tap(MapTap.Piece(target.id))
        }) { Board(vm) }
    }

    /**
     * Red pushes while Arien stands on a Red Spawn point of the next Zone: that minion waits in the
     * tray, its nearest empty spaces highlighted.
     */
    @Test
    fun boardPushTrayDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = { pushWithWaiting(vm) }) { Board(vm) }
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun boardPushTrayW384LightFontScale2() = boardViewModel().let { vm ->
        capture(ThemeMode.LIGHT, 2f, before = { pushWithWaiting(vm) }) { Board(vm) }
    }

    // ---- Two lanes, unverified maps, tools and dialogs ------------------------------------

    /** Across the River (two Lanes): each Battle Zone labelled with its Lane. */
    @Test
    fun boardTwoLanesDark() = boardViewModel(mapId = "across-the-river", board = sixHeroes).let { vm ->
        capture(ThemeMode.DARK) { Board(vm) }
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun boardTwoLanesLandscapeLight() = boardViewModel(mapId = "across-the-river", board = sixHeroes).let { vm ->
        capture(ThemeMode.LIGHT) { Board(vm) }
    }

    /** Push on a double-lane map: each Lane asked by the name its label shows. */
    @Test
    fun boardTwoLanesPushDark() = boardViewModel(mapId = "across-the-river", board = sixHeroes).let { vm ->
        capture(ThemeMode.DARK) { Board(vm, overlay = BoardOverlay.PUSH) }
    }

    /** A map not checked against the official art: badged Unverified on the board. */
    @Test
    fun boardUnverifiedDark() = boardViewModel(mapId = "narrow-passages").let { vm ->
        capture(ThemeMode.DARK) { Board(vm) }
    }

    @Test
    fun boardAddSheetDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK) {
            val state by vm.uiState.collectAsState()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                (state as? BoardUiState.Ready)?.let { AddContent(it, onAdd = {}, onDone = {}) }
            }
        }
    }

    /** A Red melee minion selected, Movement 2 from its stepper. */
    @Test
    fun boardMinionStepperDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = {
            val minion = vm.ready().pieces.filterIsInstance<MinionPiece>().first {
                it.team == Team.RED && it.type == MinionType.MELEE
            }
            vm.onAction(BoardAction.Select(minion.id))
            vm.onAction(BoardAction.Steps(1))
        }) { Board(vm) }
    }

    /** Add → a token: the banner says what a tap on the board does now. */
    @Test
    fun boardPlaceBannerLight() = boardViewModel().let { vm ->
        capture(ThemeMode.LIGHT, before = { vm.onAction(BoardAction.Add(NewPiece.Token("Mine", Team.BLUE))) }) {
            Board(vm)
        }
    }

    /** The selection panel folded to its header: the map gets the room. */
    @Test
    fun boardCollapsedPanelDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = { selectedHero(vm) }) { Board(vm, collapsed = true) }
    }

    /** Pushing into a Throne Zone: that team wins (p.8). */
    @Test
    fun boardThroneCapturedDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK, before = {
            val lane = vm.ready().map.lanes.single().id
            vm.onAction(BoardAction.Push(lane, Team.BLUE))
            vm.onAction(BoardAction.Push(lane, Team.BLUE))
        }) { Board(vm) }
    }

    /** A board deleted (or unreadable) since it was opened. */
    @Test
    fun boardMissingLight() = boardViewModel(route = BoardRoute(boardId = "gone")).let { vm ->
        capture(ThemeMode.LIGHT) { Board(vm) }
    }

    private val sixHeroes = BoardLineup(
        red = listOf("arien", "brogan", "xargatha"),
        blue = listOf("wasp", "tigerclaw", "sabina"),
    )

    private fun pushWithWaiting(vm: BoardViewModel) {
        val ready = vm.ready()
        val lane = ready.map.lanes.first()
        val geometry = ready.geometry
        val next = lane.zones[lane.zones.indexOf(lane.startingBattleZone) + 1]
        val spawn = geometry.minionSpawns(next).first { it.spawn?.team == Team.RED }.hex
        vm.onAction(BoardAction.Drop(vm.hero("arien").id, spawn))
        vm.onAction(BoardAction.Deselect)
        vm.onAction(BoardAction.Push(lane.id, Team.RED))
        check(vm.ready().waiting.isNotEmpty())
        check(BoardRules.occupancy(vm.ready().board.state)[spawn] is HeroPiece)
    }

    @Test
    fun boardPiecesSheetDark() = boardViewModel().let { vm ->
        capture(ThemeMode.DARK) {
            val state by vm.uiState.collectAsState()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                (state as? BoardUiState.Ready)?.let { PiecesContent(it, onSelect = {}, onDone = {}) }
            }
        }
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun boardPiecesSheetW384LightFontScale2() = boardViewModel().let { vm ->
        capture(ThemeMode.LIGHT, 2f) {
            val state by vm.uiState.collectAsState()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                (state as? BoardUiState.Ready)?.let { PiecesContent(it, onSelect = {}, onDone = {}) }
            }
        }
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
        const val HOUR = 3_600_000L
    }
}
