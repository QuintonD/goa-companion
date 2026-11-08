package dev.quinton.goa.feature.board.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.model.board.BoardLane
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.play.HeroStateGame.play
import dev.quinton.goa.feature.board.play.HeroStateGame.session
import dev.quinton.goa.feature.board.play.table.DecisionPanel
import dev.quinton.goa.feature.board.play.table.HeroDashboard
import dev.quinton.goa.feature.board.play.table.ScoreWidget
import dev.quinton.goa.feature.board.play.table.SlotMarkersSection
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.TurnChips
import dev.quinton.goa.feature.board.play.table.dashboardSlots
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The look round's table parts in the cases its full screens don't reach: the hero dashboard with
 * Emmitt's second card in a slot and with a hero that only has a portrait, and the round at a glance with
 * two Lanes' Wave counters on small phones and with large text (the score widget, ADR-083).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TableLookScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = ThemeMode.DARK) {
                    GoaPlateTheme {
                        Column(Modifier.fillMaxWidth().fillMaxHeight().slatePlate(brackets = false)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    private fun revealed(): TableUi = play(session(revealed = true)).table

    /** Two Lanes, as Across the River has; the engine's Wave counters per Lane. */
    private fun twoLanes(): TableUi {
        val table = revealed()
        val lane = table.map.lanes.first()
        val lanes: List<BoardLane> = listOf(lane, lane.copy(id = "second"))
        val waves = lanes.associate { it.id to 3 }
        return table.copy(map = table.map.copy(lanes = lanes), view = table.view.copy(wavesLeft = waves))
    }

    /** Emmitt's Ultimate lets a hero play two cards a turn: two resolved cards in one slot, a second card played. */
    private fun twoCards(): TableUi {
        val table = revealed()
        val heroes = table.view.heroes.map { hero ->
            if (hero.heroId != "wasp") return@map hero
            hero.copy(
                resolved = listOf(CardView("wasp-red-2b", slot = 1), CardView("wasp-blue-2b", slot = 1)),
                played = CardView("wasp-silver"),
                alsoPlayed = listOf(CardView("wasp-gold")),
            )
        }
        return table.copy(view = table.view.copy(heroes = heroes))
    }

    /** Snorri's runes below the Turn slots: Wasp's seat holds them, the frame and the words read the view only. */
    private fun runes(table: TableUi = revealed(), holder: String = "wasp"): TableUi {
        val runes = listOf("axe", "bird", "anvil", "horn").mapIndexed { i, rune ->
            MarkerView("marker_rune_$rune", holder, i + 1)
        }
        val heroes = table.view.heroes.map { if (it.heroId == holder) it.copy(markers = runes) else it }
        return table.copy(view = table.view.copy(heroes = heroes))
    }

    /** Inscribe the Runes as the engine asks it, with two picks made. */
    private fun runeQuestion(): TableUi {
        val table = runes()
        return table.copy(
            decision = Decision(
                id = 1,
                seat = "wasp",
                team = table.heroView("wasp")!!.team,
                kind = DecisionKind.CHOOSE_OPTION,
                prompt = "Which rune goes below turn slot 3? Turn 1: Axe · Turn 2: Bird.",
                options = listOf(Choice.Option(0, "Anvil"), Choice.Option(1, "Horn")),
                context = DecisionContext(optionMarkers = mapOf(0 to "marker_rune_anvil", 1 to "marker_rune_horn")),
            ),
        )
    }

    /** Choosing a card with runes down: the reminder over the hand. */
    private fun runesOverHand(): TableUi {
        val table = play(session()).table
        return runes(table, table.decider!!.heroId)
    }

    @Composable
    private fun Dashboard(table: TableUi, hero: String) {
        val focus = rememberCardFocusState()
        HeroDashboard(table, table.heroView(hero)!!, focus, Modifier.fillMaxWidth())
    }

    @Test fun dashboardTigerclawArt() = capture { Dashboard(revealed(), "tigerclaw") }

    @Test fun dashboardPortraitOnly() = capture { Dashboard(revealed(), "wasp") }

    @Test fun dashboardEmmittsSecondCard() = capture { Dashboard(twoCards(), "wasp") }

    @Test
    fun dashboardRunes() = capture {
        val table = runes()
        val hero = table.heroView("wasp")!!
        Dashboard(table, "wasp")
        Column(Modifier.padding(16.dp)) { SlotMarkersSection(table, hero) }
    }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test
    fun dashboardRunesSmallPhone() = capture { Dashboard(runes(), "wasp") }

    @Test
    fun decisionRuneOptions() = capture {
        DecisionPanel(runeQuestion(), onSelect = {}, onAnswer = {}, focus = rememberCardFocusState())
    }

    @Test
    fun decisionRunesOverHand() = capture {
        DecisionPanel(runesOverHand(), onSelect = {}, onAnswer = {}, focus = rememberCardFocusState())
    }

    @Test
    fun heroChipsRunes() = capture { TurnChips(runes(), onHero = {}, focus = null, modifier = Modifier.padding(8.dp)) }

    @Test
    fun `a second card shares its slot and is not lost`() {
        val table = twoCards()
        val slots = dashboardSlots(table, table.heroView("wasp")!!)
        assertThat(slots[0].cards.map { it.cardId }).containsExactly("wasp-red-2b", "wasp-blue-2b")
        assertThat(slots[2].cards.map { it.cardId }).containsExactly("wasp-silver", "wasp-gold")
    }

    @Test fun scoreTwoLanes() = capture { ScoreWidget(twoLanes(), Modifier.padding(8.dp)) }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test
    fun scoreTwoLanesSmallPhone() = capture { ScoreWidget(twoLanes(), Modifier.padding(8.dp)) }

    @Config(qualifiers = "w384dp-h854dp-xxhdpi")
    @Test
    fun scoreTwoLanesFontScale2() = capture(2f) {
        ScoreWidget(twoLanes(), Modifier.padding(8.dp))
    }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test
    fun heroChipsSmallPhone() = capture {
        val table = play(session()).table
        TurnChips(table, onHero = {}, focus = null, modifier = Modifier.padding(8.dp))
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 20_000L
    }
}
