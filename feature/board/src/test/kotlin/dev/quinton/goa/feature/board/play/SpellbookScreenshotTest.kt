package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableUi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gydion's spellbook at the game table (ADR-040), on the real rules engine: the hero sheet as Gydion's
 * player sees it (the spells in the spellbook, the ones cast, removed faceup) and as an opponent does
 * (the spellbook facedown, a count); Cantrip's "Choose and cast one spell" with the three spells as
 * landscape cards; a spell large in the card focus viewer, portrait and landscape.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SpellbookScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    private val heroes = bundledCatalog.boardHeroes()
    private val cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val removed = listOf(SPELL + "fireball", SPELL + "shield", SPELL + "find-familiar")

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    /** Gydion has cast three spells; he resolves Cantrip next (Arien adjacent, Brogan 3 spaces away). */
    private fun session(prepared: Boolean = true): EngineSession = position(engine, MAP) {
        clearMinions()
        round = 2
        hero("gydion", Team.RED, Hex(1, -2)) {
            level = 3
            coins = 2
            played("gydion-gold")
            if (prepared) spellbook()
        }
        hero("wasp", Team.RED, Hex(0, -5))
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
        hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") }
        phase = PositionPhase.Resolving(next = "gydion")
    }

    private fun PositionBuilder.HeroBuilder.spellbook() {
        val all = bundledCatalog.heroes.first { it.id == "gydion" }.extras.map { it.id }
        extra(*(all - removed.toSet()).toTypedArray(), tag = "spellbook")
        extra(*removed.toTypedArray(), tag = "removed")
        counter("spellbook", all.size - removed.size)
        counter("spellsRemoved", removed.size)
    }

    /** Gydion's player chose Primary: Cantrip asks which spell to cast. */
    private fun casting(): EngineSession = session().also { s ->
        val d = s.pending!!
        s.answer(d.id, d.options.first { it is Choice.Action && it.action == ActionType.PRIMARY })
        check(s.pending?.kind == DecisionKind.CHOOSE_CARD)
    }

    private fun play(s: EngineSession, mine: String) = PlayUi(
        table = TableUi(
            map = bundledMaps.first { it.id == MAP },
            view = s.view(mine),
            heroes = heroes,
            cards = cards,
            mine = setOf(mine),
            decision = s.pending,
            asking = s.pending?.seat == mine,
        ),
        status = if (s.pending?.seat == mine) TurnStatus.YourTurn else TurnStatus.Computer("gydion"),
        recent = emptyList(),
        log = emptyList(),
        coach = false,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
    )

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        before: () -> Unit = {},
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
        captureScreenRoboImage()
    }

    @Composable
    private fun Game(play: PlayUi, sheet: PlaySheet? = null) {
        GameScreen(
            state = GameUiState.Playing(play),
            onAction = {},
            onTap = { false },
            onDelete = {},
            onNewGame = {},
            onDone = {},
            onBack = {},
            initialSheet = sheet,
        )
    }

    private fun tap(description: String) {
        compose.onAllNodes(hasContentDescription(description, substring = true) and hasClickAction())
            .onFirst()
            .performClick()
    }

    // ---- The hero sheet ---------------------------------------------------------------------

    private fun sheet(mode: ThemeMode, mine: String = "gydion", fontScale: Float = 1f, prepared: Boolean = true) =
        capture(mode, fontScale) { Game(play(session(prepared), mine), PlaySheet.Hero("gydion")) }

    /** Gydion's player: the 19 spells in his spellbook and the three cast, removed faceup. */
    @Test fun spellbookSheetOwnerDark() = sheet(ThemeMode.DARK)

    @Test fun spellbookSheetOwnerLight() = sheet(ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookSheetOwnerW384DarkFontScale2() = sheet(ThemeMode.DARK, fontScale = 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookSheetOwnerLandscapeDark() = sheet(ThemeMode.DARK)

    /** An opponent: the spellbook facedown (a count), the cast spells faceup. */
    @Test fun spellbookSheetOpponentDark() = sheet(ThemeMode.DARK, mine = "arien")

    /** Before Prepare Spells: an empty spellbook, all 22 spell cards not yet added. */
    @Test fun spellbookSheetUnpreparedLight() = sheet(ThemeMode.LIGHT, prepared = false)

    // ---- Choose and cast one spell (Cantrip) ------------------------------------------------

    @Test fun spellbookCastDark() = capture(ThemeMode.DARK) { Game(play(casting(), "gydion")) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookCastW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) { Game(play(casting(), "gydion")) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookCastLandscapeDark() = capture(ThemeMode.DARK) { Game(play(casting(), "gydion")) }

    // ---- A spell in the card focus viewer ---------------------------------------------------

    private fun focused(mode: ThemeMode, fontScale: Float = 1f) =
        capture(mode, fontScale, before = { tap("Magic Missile") }) { Game(play(casting(), "gydion")) }

    @Test fun spellbookCastFocusDark() = focused(ThemeMode.DARK)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookCastFocusLandscapeDark() = focused(ThemeMode.DARK)

    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun spellbookCastFocusLandscapeShortLight() = focused(ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookCastFocusW384DarkFontScale2() = focused(ThemeMode.DARK, 2f)

    /** A cast spell tapped on the hero sheet opens among the others cast. */
    @Test
    fun spellbookSheetFocusDark() = capture(ThemeMode.DARK, before = { tap("Fireball") }) {
        Game(play(session(), "arien"), PlaySheet.Hero("gydion"))
    }

    private companion object {
        const val MAP = "forgotten-island"
        const val SPELL = "gydion-spell-"
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
