package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PendingNudge
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.ui.cards.CARD_TAG
import dev.quinton.goa.feature.board.ui.cards.GO_TO_ANSWER_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Card questions answered through the card focus viewer on the game screen: the first tap on a card
 * in the panel zooms it and answers nothing; the second tap answers with that card (the same
 * [GameAction.Answer] the Confirm button sends, so the Coach looks at it the same way); back answers
 * nothing; a swipe moves to the next card; a Coach nudge replaces the viewer like any answer's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CardFocusDecisionTest {
    @get:Rule val compose = createComposeRule()

    private val heroes = bundledCatalog.boardHeroes()
    private val cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val all = setOf("arien", "brogan", "wasp", "tigerclaw")
    private val actions = mutableListOf<GameAction>()

    /** What the person did (the review sheet's open / closed reports aside). */
    private fun record(action: GameAction) {
        if (action !is GameAction.Review) actions += action
    }

    private fun sessionAt(kind: DecisionKind, humans: Set<String> = all): FakeGameSession {
        val session = fakeFactory().newGame(fakeSetup(humans = humans)) as FakeGameSession
        repeat(80) {
            val d = session.pending ?: error("No $kind")
            if (d.kind == kind) return session
            session.answer(d.id, session.preferred()!!)
        }
        error("No $kind")
    }

    private fun play(session: FakeGameSession, mine: Set<String> = all): PlayUi {
        val base = session.view(mine.first())
        val own = mine.associateWith { seat -> session.view(seat).heroes.first { it.heroId == seat } }
        val view: GameView = base.copy(heroes = base.heroes.map { own[it.heroId] ?: it })
        return PlayUi(
            table = TableUi(
                map = bundledMaps.first { it.id == "forgotten-island" },
                view = view,
                heroes = heroes,
                cards = cards,
                mine = mine,
                decision = session.pending,
                asking = session.pending?.seat in mine,
            ),
            status = session.pending?.seat?.takeIf { it !in mine }?.let { TurnStatus.Computer(it, true) }
                ?: TurnStatus.YourTurn,
            recent = emptyList(),
            log = logLines(session.events),
            coach = true,
            pace = AiPace.NORMAL,
            takeBacks = 0,
            canTakeBack = true,
        )
    }

    private var state by mutableStateOf<PlayUi?>(null)

    private fun show(
        kind: DecisionKind,
        mine: Set<String> = all,
        onAction: (GameAction) -> Unit = { record(it) },
    ): PlayUi {
        val p = play(sessionAt(kind, mine), mine)
        state = p
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                GameScreen(
                    state = GameUiState.Playing(state!!),
                    onAction = onAction,
                    onTap = { false },
                    onDelete = {},
                    onNewGame = {},
                    onDone = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
        return p
    }

    private fun option(p: PlayUi, i: Int) = p.table.cardOptions[i]

    private fun tapInPanel(p: PlayUi, i: Int) {
        val name = cards.getValue(option(p, i).cardId).name
        compose.onAllNodes(hasContentDescription(name, substring = true) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `discard - first tap zooms, second tap answers with that card`() {
        val p = show(DecisionKind.CHOOSE_CARD)
        tapInPanel(p, 0)
        compose.onNodeWithTag(CARD_TAG).assertExists()
        assertThat(actions).isEmpty()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(option(p, 0)))
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `defend - back answers nothing, a swipe then a tap defends with the next card`() {
        val p = show(DecisionKind.DEFEND)
        tapInPanel(p, 0)
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
        assertThat(actions).isEmpty()
        tapInPanel(p, 0)
        compose.onNodeWithTag(CARD_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(option(p, 1)))
    }

    @Test
    fun `play a card - the second tap plays it facedown`() {
        val p = show(DecisionKind.PLAY_CARD)
        tapInPanel(p, 2)
        compose.onNodeWithText("Play ${cards.getValue(option(p, 2).cardId).name} facedown").assertExists()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(option(p, 2)))
    }

    @Test
    fun `a Coach nudge intercepts the second tap as it does the Confirm button`() {
        lateinit var p: PlayUi
        p = show(DecisionKind.CHOOSE_CARD) { action ->
            record(action)
            // As the ViewModel does: the answer goes to the coach first, who nudges.
            if (action is GameAction.Answer) {
                state = p.copy(
                    table = p.table.copy(asking = false),
                    nudge = PendingNudge(CoachNote("Keep it", "A note."), action.choice, "the card"),
                )
            }
        }
        tapInPanel(p, 0)
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Coach: Keep it").assertExists()
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Choose anyway").performClick()
        assertThat(actions).containsExactly(GameAction.Answer(option(p, 0)), GameAction.ChooseAnyway).inOrder()
    }

    // ---- Level up (ADR-045): the upgrade cards are shown before the colour is chosen ----------

    private val current = "arien-red-1"
    private val upgrade = "arien-red-2a"

    private fun tapUpgrade(id: String) {
        val name = "Upgrade of ${cards.getValue(current).name}: ${cards.getValue(id).name}"
        compose.onAllNodes(hasContentDescription(name, substring = true) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `level up - each card shows the two it would become, before anything is chosen`() {
        show(DecisionKind.UPGRADE_CARD)
        for (c in listOf("red", "green", "blue")) {
            for (k in listOf("2a", "2b")) {
                val name = "Upgrade of ${cards.getValue("arien-$c-1").name}: ${cards.getValue("arien-$c-$k").name}"
                compose.onAllNodes(hasContentDescription(name, substring = true)).onFirst().assertExists()
            }
        }
        assertThat(actions).isEmpty()
    }

    @Test
    fun `level up - an upgrade card is read, never chosen, Go to leads to the card that is the choice`() {
        show(DecisionKind.UPGRADE_CARD)
        tapUpgrade(upgrade)
        compose.onNodeWithText("Upgrade of ${cards.getValue(current).name}").assertExists()
        // The second tap on an upgrade card answers nothing (it shakes): only the hand card is the choice.
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).isEmpty()
        compose.onNodeWithTag(CARD_TAG).assertExists()
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).assertTextEquals("Go to ${cards.getValue(current).name}")
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Upgrade ${cards.getValue(current).name}").assertExists()
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(Choice.Card(current)))
    }

    @Test
    fun `level up - the second tap on the hand card upgrades it`() {
        val p = show(DecisionKind.UPGRADE_CARD)
        tapInPanel(p, 1)
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(option(p, 1)))
    }

    @Test
    fun `no peeking - a computer's level up shows none of its deck`() {
        // Arien is the computer's: the person (Wasp) sees that Arien is choosing, not the cards.
        show(DecisionKind.UPGRADE_CARD, mine = setOf("wasp"))
        compose.onAllNodes(hasContentDescription("Upgrade of", substring = true)).assertCountEquals(0)
        for (id in listOf("arien-red-2a", "arien-red-2b", "arien-green-2a", "arien-blue-2b")) {
            compose.onAllNodes(hasContentDescription(cards.getValue(id).name, substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasText(cards.getValue(id).name, substring = true)).assertCountEquals(0)
        }
    }
}
