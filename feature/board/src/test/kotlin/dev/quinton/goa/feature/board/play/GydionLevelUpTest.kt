package dev.quinton.goa.feature.board.play

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.HAND_CARD
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.OTHER_UPGRADE
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.UPGRADE
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.name
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.table.upgradeFocus
import dev.quinton.goa.feature.board.play.table.upgradeRows
import dev.quinton.goa.feature.board.ui.cards.CARD_TAG
import dev.quinton.goa.feature.board.ui.cards.GO_TO_ANSWER_TAG
import dev.quinton.goa.feature.board.ui.cards.focusPosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gydion's level up (ADR-045 extended, hands-on finding 4): the spells each card would cast are shown
 * with it, in the rows and in the card focus viewer, before the colour is chosen; a spell is read,
 * never chosen, and leads back to the card that is the answer. The computer's level up shows none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GydionLevelUpTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<GameAction>()
    private val burningHands = "gydion-spell-burning-hands"
    private val fireball = "gydion-spell-fireball"

    private fun show(p: PlayUi) {
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                GameScreen(
                    state = GameUiState.Playing(p),
                    onAction = { if (it !is GameAction.Review) actions += it },
                    onTap = { false },
                    onDelete = {},
                    onNewGame = {},
                    onDone = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tap(description: String) {
        compose.onAllNodes(hasContentDescription(description, substring = true) and hasClickAction())
            .onFirst()
            .performClick()
        compose.waitForIdle()
    }

    private fun spellOf(namer: String, spell: String) = "A spell ${name(namer)} casts: ${name(spell)}"

    @Test
    fun `level up - each card shows the spells it casts, the hand card's and both upgrades'`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session()))
        for ((namer, spells) in mapOf(
            HAND_CARD to listOf(burningHands),
            UPGRADE to listOf(burningHands, fireball),
            OTHER_UPGRADE to listOf("gydion-spell-vampiric-touch", "gydion-spell-create-undead"),
        )) {
            spells.forEach {
                compose.onAllNodes(hasContentDescription(spellOf(namer, it), substring = true)).onFirst().assertExists()
            }
        }
        // TalkBack hears them on the cards too.
        compose.onAllNodes(hasContentDescription("casts ${name(burningHands)} or ${name(fireball)}", substring = true))
            .onFirst()
            .assertExists()
        assertThat(actions).isEmpty()
    }

    @Test
    fun `level up - a spell is read in the viewer, never chosen, Go to leads to the card that is the choice`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session()))
        tap(spellOf(UPGRADE, fireball))
        compose.onNodeWithText("A spell ${name(UPGRADE)} casts").assertExists()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).isEmpty()
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).assertTextEquals("Go to ${name(HAND_CARD)}")
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(Choice.Card(HAND_CARD)))
    }

    @Test
    fun `level up - under an upgrade card in the viewer, its spells, a tap goes to one`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session()))
        tap("Upgrade of ${name(HAND_CARD)}: ${name(UPGRADE)}")
        compose.onNodeWithText("The spells it chooses from:").assertExists()
        compose.onAllNodes(hasContentDescription(name(fireball)) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("A spell ${name(UPGRADE)} casts").assertExists()
        assertThat(actions).isEmpty()
    }

    @Test
    fun `keep - each card shows its spells, a spell leads back to the card to keep`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session(keep = true)))
        tap(spellOf(OTHER_UPGRADE, "gydion-spell-create-undead"))
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).assertTextEquals("Go to ${name(OTHER_UPGRADE)}")
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(Choice.Card(OTHER_UPGRADE)))
    }

    @Test
    fun `level up - the viewer counts the cards, a spell is said as its card's`() {
        // Re-check: "Card 3 of 24" counted every spell as a card.
        val p = GydionLevelUpGame.play(GydionLevelUpGame.session())
        val rows = upgradeRows(p.table)
        val pages = upgradeFocus(rows).cards
        val cards = rows.sumOf { 1 + it.upgrades.size }
        assertThat(focusPosition(pages, 0)).isEqualTo("Card 1 of $cards")
        val hand = pages.indexOfFirst { it.card.id == HAND_CARD }
        val upgrade = pages.indexOfFirst { it.card.id == UPGRADE }
        assertThat(focusPosition(pages, hand + 1)).endsWith("of $cards · spell 1 of 1")
        val spellAt = pages.indexOfFirst { it.key == "${pages[upgrade].key}>$fireball" }
        val upgradeAt = focusPosition(pages, upgrade)
        assertThat(focusPosition(pages, spellAt)).isEqualTo("$upgradeAt · spell 2 of 2")
        // In the viewer, and TalkBack the same.
        show(p)
        tap(spellOf(UPGRADE, fireball))
        compose.onNodeWithText("$upgradeAt · spell 2 of 2").assertExists()
        compose.onAllNodes(hasContentDescription("$upgradeAt, spell 2 of 2")).onFirst().assertExists()
    }

    @Test
    fun `level up - the cards come before the round's summary in the panel`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session()))
        // Below the sheet's visible part the summary's bounds are clipped away: compare where each starts.
        val question = compose.onAllNodes(hasText("Level up", substring = true)).onFirst().fetchSemanticsNode()
        val summary = compose.onAllNodes(hasText("END OF ROUND", substring = true)).onFirst().fetchSemanticsNode()
        assertThat(question.positionInRoot.y).isLessThan(summary.positionInRoot.y)
    }

    @Test
    fun `no peeking - the computer's level up shows none of Gydion's cards or spells`() {
        show(GydionLevelUpGame.play(GydionLevelUpGame.session(gydionAi = true), mine = "arien"))
        compose.onAllNodes(hasContentDescription("A spell", substring = true)).assertCountEquals(0)
        for (id in listOf(UPGRADE, OTHER_UPGRADE, fireball, "gydion-spell-create-undead")) {
            compose.onAllNodes(hasContentDescription(name(id), substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasText(name(id), substring = true)).assertCountEquals(0)
        }
    }
}
