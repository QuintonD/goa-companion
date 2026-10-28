package dev.quinton.goa.feature.board.play

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
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

/**
 * Gydion's sheets after the hands-on pass (2026-10-26), on the real engine: his hero sheet sums the
 * spellbook up (counts, "Open the spellbook") instead of 20 spell cards above the hand; the Spellbook
 * sheet lists the spells by Tier and marks each with the card in the hand that casts it, for his own
 * player only (p.4: cards in hand are hidden; Prepare Spells: only Gydion looks in the spellbook).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GydionSheetsTest {
    @get:Rule val compose = createComposeRule()

    private val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    private val spells = bundledCatalog.heroes.first { it.id == "gydion" }.extras.map { it.id }
    private val removed = listOf("$SPELL-fireball", "$SPELL-shield", "$SPELL-find-familiar")

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    /** Choosing a card (p.7): Gydion holds Cantrip, Prepare Spells and his three Tier I cards. */
    private fun session(): EngineSession = position(engine, MAP) {
        clearMinions()
        round = 2
        hero("gydion", Team.RED, Hex(1, -2)) {
            hand("gydion-gold", "gydion-silver", "gydion-red-1", "gydion-green-1", "gydion-blue-1")
            extra(*(spells - removed.toSet()).toTypedArray(), tag = "spellbook")
            extra(*removed.toTypedArray(), tag = "removed")
            counter("spellbook", spells.size - removed.size)
            counter("spellsRemoved", removed.size)
        }
        hero("wasp", Team.RED, Hex(0, -5)) { hand("wasp-gold", "wasp-silver") }
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold", "arien-blue-1") }
        hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold", "brogan-blue-1") }
        phase = PositionPhase.PlayCards()
    }

    private fun play(mine: String): PlayUi {
        val s = session()
        return PlayUi(
            table = TableUi(
                map = bundledMaps.first { it.id == MAP },
                view = s.view(mine),
                heroes = bundledCatalog.boardHeroes(),
                cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id },
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
    }

    private fun show(mine: String, sheet: PlaySheet): String {
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                GameScreen(
                    state = GameUiState.Playing(play(mine)),
                    onAction = {},
                    onTap = { false },
                    onDelete = {},
                    onNewGame = {},
                    onDone = {},
                    onBack = {},
                    initialSheet = sheet,
                )
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.joinToString("\n") { node ->
            val c = node.config
            listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                c.getOrNull(SemanticsActions.OnClick)?.label,
            ).joinToString(" ")
        }
    }

    @Test
    fun `his own hero sheet - the spellbook as counts, one tap from the sheet, the hand not buried`() {
        val words = show("gydion", PlaySheet.Hero("gydion"))
        assertThat(words).contains("In your spellbook: 19. Cast · removed faceup: 3")
        assertThat(words).contains("Open the spellbook")
        // No spell card on the hero sheet (they are on the Spellbook sheet): Sunburst is in the book.
        assertThat(words).doesNotContain("Sunburst")
        assertThat(words).contains("Elementary Evocation") // in hand
    }

    @Test
    fun `an opponent's view of his hero sheet - the facedown count, no hand, no spell named`() {
        val words = show("arien", PlaySheet.Hero("gydion"))
        assertThat(words).contains("In the spellbook: 19")
        assertThat(words).contains("Facedown: only Gydion can look at the cards in the spellbook.")
        assertThat(words).doesNotContain("Sunburst")
        assertThat(words).doesNotContain("Elementary Evocation")
        assertThat(words).doesNotContain("Cast with")
    }

    @Test
    fun `his Spellbook sheet - by Tier, each spell with the card in his hand that casts it`() {
        val words = show("gydion", PlaySheet.Spellbook("gydion"))
        assertThat(words).contains("Gold (no tier) · 3")
        assertThat(words).contains("Tier I · 2") // Burning Hands, Suggestion; Shield was cast
        assertThat(words).contains("Tier II · 7")
        assertThat(line(words, "Magic Missile")).endsWith("Cast with Cantrip")
        assertThat(line(words, "Burning Hands")).endsWith("Cast with Elementary Evocation")
        assertThat(line(words, "Suggestion")).endsWith("Cast with Elementary Enchantment")
        assertThat(line(words, "Sunburst")).doesNotContain("Cast with")
        // Shield is cast (not in the spellbook): Elementary Abjuration casts nothing now.
        assertThat(words).doesNotContain("Cast with Elementary Abjuration")
    }

    @Test
    fun `no peeking - an opponent's Spellbook sheet names neither the spells in the book nor the hand`() {
        val words = show("arien", PlaySheet.Spellbook("gydion"))
        assertThat(words).doesNotContain("Cast with")
        assertThat(words).doesNotContain("Sunburst")
        assertThat(words).contains("Fireball") // cast, faceup
    }

    /** The TalkBack line of the spell card [name] (its thumbnail). */
    private fun line(words: String, name: String): String =
        words.lines().first { it.trim().startsWith("$name,") }.trim().removeSuffix(" Look at the card")

    private companion object {
        const val MAP = "forgotten-island"
        const val SPELL = "gydion-spell"
    }
}
