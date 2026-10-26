package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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

    private fun tapText(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
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

    // ---- Cantrip before Prepare Spells: only Hold, and why (ADR-041) -------------------------

    /** The engine's reason under Hold: "Its Skill can't start: the spellbook is empty until …". */
    private fun emptyBook(mode: ThemeMode, fontScale: Float = 1f) =
        capture(mode, fontScale) { Game(play(session(prepared = false), "gydion")) }

    @Test fun spellbookEmptyActionDark() = emptyBook(ThemeMode.DARK)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookEmptyActionW384LightFontScale2() = emptyBook(ThemeMode.LIGHT, 2f)

    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun spellbookEmptyActionLandscapeShortLight() = emptyBook(ThemeMode.LIGHT)

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

    // ---- The Spellbook at any point (extends ADR-040) ----------------------------------------

    /** Before the reveal: Gydion's player chooses a card to play (p.7), his spellbook one tap away. */
    private fun choosing(): EngineSession = position(engine, MAP) {
        clearMinions()
        round = 2
        hero("gydion", Team.RED, Hex(1, -2)) {
            level = 3
            coins = 2
            hand("gydion-gold", "gydion-silver", "gydion-red-1", "gydion-green-1", "gydion-blue-1")
            spellbook()
        }
        hero("wasp", Team.RED, Hex(0, -5)) { hand("wasp-gold", "wasp-silver") }
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold", "arien-blue-1") }
        hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold", "brogan-blue-1") }
        phase = PositionPhase.PlayCards()
    }

    private fun table(mode: ThemeMode, fontScale: Float = 1f, hidden: Boolean = false, before: () -> Unit = {}) =
        capture(mode, fontScale, before) {
            GameScreen(
                state = GameUiState.Playing(play(choosing(), "gydion")),
                onAction = {},
                onTap = { false },
                onDelete = {},
                onNewGame = {},
                onDone = {},
                onBack = {},
                initialPanelHidden = hidden,
            )
        }

    /** The Spellbook control under Fit and Flip: Gydion's deck symbol, 19 spells in the spellbook. */
    @Test fun spellbookTableDark() = table(ThemeMode.DARK)

    @Test fun spellbookTableLight() = table(ThemeMode.LIGHT)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookTableLandscapeDark() = table(ThemeMode.DARK)

    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun spellbookTableLandscapeShortLight() = table(ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookTableW384DarkFontScale2() = table(ThemeMode.DARK, fontScale = 2f)

    /** The panel hidden to see the whole board: the control stays. */
    @Test fun spellbookTablePanelHiddenDark() = table(ThemeMode.DARK, hidden = true)

    /** After the reveal, Gydion choosing an action: the turn track at the top, the control under Flip. */
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookTableActionW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) { Game(play(session(), "gydion")) }

    /** One tap on the control opens the Spellbook sheet. */
    @Test fun spellbookTableTapDark() = table(ThemeMode.DARK, before = { tap("Gydion's spellbook") })

    // ---- The Spellbook sheet ----------------------------------------------------------------

    /** Cantrip's cast question with Expeditious Retreat cast: the offered two marked, the third says why. */
    private fun castingWithoutRetreat(): EngineSession = position(engine, MAP) {
        clearMinions()
        round = 2
        hero("gydion", Team.RED, Hex(1, -2)) {
            level = 3
            coins = 2
            played("gydion-gold")
            val all = bundledCatalog.heroes.first { it.id == "gydion" }.extras.map { it.id }
            val gone = removed + (SPELL + "expeditious-retreat")
            extra(*(all - gone.toSet()).toTypedArray(), tag = "spellbook")
            extra(*gone.toTypedArray(), tag = "removed")
            counter("spellbook", all.size - gone.size)
            counter("spellsRemoved", gone.size)
        }
        hero("wasp", Team.RED, Hex(0, -5))
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
        hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") }
        phase = PositionPhase.Resolving(next = "gydion")
    }.also { s ->
        val d = s.pending!!
        s.answer(d.id, d.options.first { it is Choice.Action && it.action == ActionType.PRIMARY })
        check(s.pending?.kind == DecisionKind.CHOOSE_CARD)
    }

    private fun book(mode: ThemeMode, fontScale: Float = 1f, mine: String = "gydion", s: () -> EngineSession) =
        capture(mode, fontScale) { Game(play(s(), mine), PlaySheet.Spellbook("gydion")) }

    @Test fun spellbookBookCastDark() = book(ThemeMode.DARK) { castingWithoutRetreat() }

    @Test fun spellbookBookCastLight() = book(ThemeMode.LIGHT) { castingWithoutRetreat() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookBookCastLandscapeDark() = book(ThemeMode.DARK) { castingWithoutRetreat() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookBookCastW384DarkFontScale2() = book(ThemeMode.DARK, 2f) { castingWithoutRetreat() }

    /** Any time, no question: the spellbook and the cast spells. */
    @Test fun spellbookBookDark() = book(ThemeMode.DARK) { choosing() }

    /** An opponent at this phone opens it from Gydion's hero sheet: the spellbook facedown, a count. */
    @Test fun spellbookBookOpponentDark() = book(ThemeMode.DARK, mine = "arien") { session() }

    /** The cast question's panel: the card's spell that can't be cast now, dimmed, with the engine's reason. */
    @Test fun spellbookCastUnavailableDark() = capture(ThemeMode.DARK) { Game(play(castingWithoutRetreat(), "gydion")) }

    /** The hero sheet's spellbook head opens the sheet ("Open the spellbook"). */
    @Test
    fun spellbookSheetOpenDark() = capture(ThemeMode.DARK, before = { tapText("Open the spellbook") }) {
        Game(play(session(), "arien"), PlaySheet.Hero("gydion"))
    }

    private companion object {
        const val MAP = "forgotten-island"
        const val SPELL = "gydion-spell-"
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
