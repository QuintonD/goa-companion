package dev.quinton.goa.feature.board.play

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.play.HeroStateGame.play
import dev.quinton.goa.feature.board.play.HeroStateGame.session
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.table.HeroTurnState
import dev.quinton.goa.feature.board.play.table.heroTurn
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hero sheet (ADR-044) on the real rules engine: what it says of an enemy before and after the
 * reveal and of the person's own hero, and that nothing hidden (p.4) reaches it — an enemy's tucked
 * cards, hand and facedown card are never named, while the item icons are. Then how it is reached: a
 * long press on a hero's piece that is an option, and TalkBack's custom action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HeroStateSheetTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun show(play: PlayUi, sheet: PlaySheet? = null) {
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
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
        }
        compose.waitForIdle()
    }

    /** Every word on screen: texts and descriptions, merged or not. */
    private fun words(): String {
        val nodes = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.joinToString("\n") { node ->
            val c = node.config
            listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                c.getOrNull(SemanticsActions.CustomActions)?.joinToString(" ") { it.label },
                c.getOrNull(SemanticsActions.OnClick)?.label,
            ).joinToString(" ")
        }
    }

    @Test
    fun `an enemy before the reveal - a card back, item icons, never the tucked cards, hand or the facedown card`() {
        val s = session()
        val play = play(s)
        val wasp = play.table.heroView("wasp")!!
        assertThat(heroTurn(play.table, wasp)?.state).isEqualTo(HeroTurnState.PLAYED)
        assertThat(heroTurn(play.table, wasp)?.cards?.single()?.cardId).isNull()
        show(play, PlaySheet.Hero("wasp"))
        val words = words()
        HeroStateGame.waspHidden.forEach { assertThat(words).doesNotContain(it) }
        assertThat(words).doesNotContain(HeroStateGame.WASP_PLAYED)
        assertThat(words).contains("Played facedown: revealed when everyone has played (p.7).")
        assertThat(words).contains("+1 Initiative")
        assertThat(words).contains("+1 Defense")
        // Open: the resolved Turn slots and the discard.
        assertThat(words).contains("Charged Boomerang")
        assertThat(words).contains("Stop Projectiles")
        assertThat(words).doesNotContain("Tucked under the dashboard")
    }

    @Test
    fun `an enemy after the reveal - the card faceup with its state and the initiative as it counts`() {
        val play = play(session(revealed = true))
        val turn = heroTurn(play.table, play.table.heroView("wasp")!!)!!
        assertThat(turn.state).isEqualTo(HeroTurnState.NOW)
        assertThat(turn.initiative).isEqualTo(14)
        assertThat(turn.printed).isEqualTo(13)
        show(play, PlaySheet.Hero("wasp"))
        val words = words()
        assertThat(words).contains(HeroStateGame.WASP_PLAYED)
        assertThat(words).contains("Initiative 14 · printed 13")
        HeroStateGame.waspHidden.forEach { assertThat(words).doesNotContain(it) }
    }

    @Test
    fun `a hero off the board says when it may respawn`() {
        show(play(session(revealed = true)), PlaySheet.Hero("tigerclaw"))
        assertThat(
            words(),
        ).contains("Off the board: may respawn in their Throne Zone before acting on their next card (p.7).")
    }

    @Test
    fun `the person's own hero - the item icons and which cards are tucked, the hand`() {
        val play = play(session())
        show(play, PlaySheet.Hero("arien"))
        val words = words()
        assertThat(words).contains("+1 Defense ×2")
        assertThat(words).contains("Rogue Wave")
        assertThat(words).contains("Arcane Whirlpool")
        assertThat(words).contains("Tucked under the dashboard")
        assertThat(words).contains("Noble Blade") // in hand
        assertThat(heroTurn(play.table, play.table.heroView("arien")!!)?.state).isEqualTo(HeroTurnState.CHOOSING)
    }

    @Test
    fun `the person's own card before the reveal is shown, said to be facedown to the others`() {
        // Arien plays first here: her card is down while Wasp chooses.
        val s = position(HeroStateGame.engine, HeroStateGame.MAP) {
            clearMinions()
            hero("arien", Team.RED, Hex(1, -2)) { played("arien-blue-1") }
            hero("wasp", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.PlayCards(fromSeat = 1)
        }
        show(play(s), PlaySheet.Hero("arien"))
        val words = words()
        assertThat(words).contains("Aspiring Duelist")
        assertThat(words).contains("Played facedown: the others see it at the reveal (p.7).")
    }

    // ---- Reaching the sheet when a tap on the hero answers the question -------------------------

    /** Arien's Noble Blade asks for a target: Wasp and Tigerclaw are both adjacent, so a tap on either answers. */
    private fun targeting(): EngineSession {
        val s = position(HeroStateGame.engine, HeroStateGame.MAP) {
            clearMinions()
            hero("arien", Team.RED, Hex(1, -2)) { played("arien-gold") }
            hero("wasp", Team.BLUE, Hex(2, -2)) { items("wasp-blue-2a") }
            hero("tigerclaw", Team.BLUE, Hex(1, -1))
            phase = PositionPhase.Resolving(next = "arien")
        }
        s.answer(s.pending!!.id, s.pending!!.options.first { it is Choice.Action && it.action == ActionType.PRIMARY })
        check(s.pending?.kind == DecisionKind.CHOOSE_UNIT) { "Expected a target, got ${s.pending}" }
        check(s.pending!!.options.any { it is Choice.Unit && it.unitId == "wasp" })
        return s
    }

    @Test
    fun `a long press on a hero that is an option opens the hero's game state, the tap still answers`() {
        val play = play(targeting())
        var tapped: Any? = null
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                GameScreen(
                    state = GameUiState.Playing(play),
                    onAction = {},
                    onTap = {
                        tapped = it
                        true
                    },
                    onDelete = {},
                    onNewGame = {},
                    onDone = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
        // The panel says how to reach the hero.
        compose.onAllNodes(
            hasText("Long-press a hero for their game state.", substring = true),
        ).onFirst().assertExists()
        val wasp = compose.onAllNodes(
            hasContentDescription("Wasp, ", substring = true) and hasContentDescription("option", substring = true),
        )
            .onFirst()
        wasp.performTouchInput { longClick() }
        compose.waitForIdle()
        assertThat(tapped).isNull()
        compose.onAllNodes(hasText("Open information (p.4)")).onFirst().assertExists()
        assertThat(words()).contains("+1 Defense")
    }

    @Test
    fun `TalkBack - a hero's space has the custom action Show Wasp's game state`() {
        show(play(targeting()))
        val wasp = compose.onAllNodes(
            hasContentDescription("Wasp, ", substring = true) and hasContentDescription("option", substring = true),
        )
            .onFirst()
        val actions = wasp.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).contains("Show Wasp's game state")
        compose.runOnUiThread { actions.first { it.label == "Show Wasp's game state" }.action() }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Open information (p.4)")).onFirst().assertExists()
    }
}
