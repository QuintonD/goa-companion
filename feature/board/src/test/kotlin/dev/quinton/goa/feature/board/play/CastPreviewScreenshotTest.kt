package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
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
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.castLine
import dev.quinton.goa.feature.board.play.table.noEffectLine
import dev.quinton.goa.feature.board.play.table.spellReasonLines
import dev.quinton.goa.feature.board.play.table.targetLine
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a Gydion card will cast, on its action question before the player commits (ADR-040 extended
 * after the hands-on pass, `DecisionContext.primaryPreview`), on the real rules engine: the spell to
 * be cast as its card under the Skill chip, its only target, the warning when it will do nothing, and
 * each of the card's spells that can't be cast with why. Never on another seat's question.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CastPreviewScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    private val heroes = bundledCatalog.boardHeroes()
    private val cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val spells = bundledCatalog.heroes.first { it.id == "gydion" }.extras.map { it.id }

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    /** Gydion (level 3) resolves [card]; [removed] are cast already; Brogan alone, 3 spaces away. */
    private fun session(card: String, removed: List<String>, brogan: Hex = Hex(4, -2)): EngineSession =
        position(engine, MAP) {
            clearMinions()
            round = 2
            hero("gydion", Team.RED, Hex(1, -2)) {
                level = 3
                coins = 2
                played(card)
                book(removed.map { SPELL + it })
            }
            hero("wasp", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(4, -9)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, brogan) { hand("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }.also { check(it.pending?.kind == DecisionKind.CHOOSE_ACTION) }

    private fun PositionBuilder.HeroBuilder.book(removed: List<String>) {
        extra(*(spells - removed.toSet()).toTypedArray(), tag = "spellbook")
        if (removed.isNotEmpty()) extra(*removed.toTypedArray(), tag = "removed")
        counter("spellbook", spells.size - removed.size)
        counter("spellsRemoved", removed.size)
    }

    /** Lesser Evocation: Burning Hands has no adjacent unit, so it casts Fireball, on Brogan only. */
    private fun fireball() = session("gydion-red-2a", removed = listOf("shield"))

    /** Elementary Enchantment with no enemy hero in radius 3: Suggestion will do nothing. */
    private fun suggestion() = session("gydion-blue-1", removed = emptyList(), brogan = Hex(-1, -5))

    /** Cantrip with Expeditious Retreat cast and nobody in reach: none of its spells can be cast. */
    private fun nothingToCast() = session("gydion-gold", removed = listOf("expeditious-retreat"), brogan = Hex(-1, -5))

    private fun play(s: EngineSession, mine: String) = PlayUi(
        table = table(s, mine),
        status = if (s.pending?.seat == mine) TurnStatus.YourTurn else TurnStatus.Computer("gydion"),
        recent = emptyList(),
        log = emptyList(),
        coach = false,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
    )

    private fun table(s: EngineSession, mine: String) = TableUi(
        map = bundledMaps.first { it.id == MAP },
        view = s.view(mine),
        heroes = heroes,
        cards = cards,
        mine = setOf(mine),
        decision = s.pending,
        asking = s.pending?.seat == mine,
    )

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Composable
    private fun Game(play: PlayUi) {
        GameScreen(
            state = GameUiState.Playing(play),
            onAction = {},
            onTap = { false },
            onDelete = {},
            onNewGame = {},
            onDone = {},
            onBack = {},
        )
    }

    // ---- The words -----------------------------------------------------------------------------

    @Test
    fun `the lines say the spell, its only target, and why not the others`() {
        val t = table(fireball(), "gydion")
        val p = t.decision!!.context.primaryPreview!!
        assertThat(castLine(t, p)).isEqualTo("Casts Fireball")
        assertThat(targetLine(t, p)).isEqualTo("Its only target: Brogan")
        assertThat(noEffectLine(p)).isNull()
        assertThat(spellReasonLines(t, p, primaryOffered = true).map { it.text })
            .containsExactly("Burning Hands: Its Attack can't start: no target adjacent to you (p.10, FAQ).")
    }

    @Test
    fun `Suggestion with nobody in radius - the warning names the rule`() {
        val t = table(suggestion(), "gydion")
        assertThat(noEffectLine(t.decision!!.context.primaryPreview!!)).isEqualTo(
            "No enemy hero in radius 3: Suggestion will do nothing and is removed from the spellbook " +
                "(Prepare Spells).",
        )
    }

    @Test
    fun `no peeking - an opponent watching Gydion choose sees no spell of his`() {
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { Game(play(fireball(), "brogan")) } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Casts Fireball", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Its only target", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Burning Hands:", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the owner sees it`() {
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { Game(play(fireball(), "gydion")) } }
        compose.waitForIdle()
        compose.onNodeWithText("Casts Fireball").assertExists()
        compose.onNodeWithText("Its only target: Brogan").assertExists()
    }

    // ---- Screenshots ---------------------------------------------------------------------------

    @Test fun castPreviewFireballDark() = capture(ThemeMode.DARK) { Game(play(fireball(), "gydion")) }

    @Test fun castPreviewFireballLight() = capture(ThemeMode.LIGHT) { Game(play(fireball(), "gydion")) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun castPreviewFireballLandscapeDark() = capture(ThemeMode.DARK) { Game(play(fireball(), "gydion")) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun castPreviewFireballW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) { Game(play(fireball(), "gydion")) }

    @Test fun castPreviewNoEffectDark() = capture(ThemeMode.DARK) { Game(play(suggestion(), "gydion")) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun castPreviewNoEffectW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Game(play(suggestion(), "gydion")) }

    @Test fun castPreviewNothingToCastDark() = capture(ThemeMode.DARK) { Game(play(nothingToCast(), "gydion")) }

    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun castPreviewNothingToCastLandscapeShortLight() =
        capture(ThemeMode.LIGHT) { Game(play(nothingToCast(), "gydion")) }

    private companion object {
        const val MAP = "forgotten-island"
        const val SPELL = "gydion-spell-"
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
