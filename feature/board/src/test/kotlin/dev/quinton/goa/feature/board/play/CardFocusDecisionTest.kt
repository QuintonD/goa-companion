package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import dev.quinton.goa.feature.board.ui.cards.CONFIRM_TAG
import dev.quinton.goa.feature.board.ui.cards.GO_TO_ANSWER_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Card questions on the game screen: a tap on a card in the panel chooses it ([GameAction.Select]) and answers
 * nothing; a tap on the chosen card reads it in the card focus viewer, where a tap on the card answers nothing
 * either; only a bronze button naming the choice answers ([GameAction.Answer], so the Coach looks at it the same
 * way), in the panel or in the viewer; back answers nothing; a Coach nudge intercepts the answer.
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

    /** As the ViewModel does for a choice not yet committed: the table shows it selected. */
    private fun applying(action: GameAction) {
        record(action)
        if (action is GameAction.Select) state = state!!.copy(table = state!!.table.copy(selected = action.choice))
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
        onAction: (GameAction) -> Unit = { applying(it) },
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

    private fun name(p: PlayUi, i: Int) = cards.getValue(option(p, i).cardId).name

    private fun tapInPanel(p: PlayUi, i: Int) {
        compose.onAllNodes(hasContentDescription(name(p, i), substring = true) and hasClickAction()).onFirst()
            .performClick()
        compose.waitForIdle()
    }

    /** The panel's bronze button (the viewer's has its tag). */
    private fun panelButton(text: String) =
        // A defence's button names the card in one line ("Defend · Blink Strike").
        compose.onAllNodes(hasText(text, substring = true) and hasClickAction() and !hasTestTag(CONFIRM_TAG))
            .onFirst()
            .performScrollTo()

    @Test
    fun `discard - a tap chooses, a tap on the chosen card reads it, only the button answers`() {
        val p = show(DecisionKind.CHOOSE_CARD)
        tapInPanel(p, 0)
        assertThat(actions).containsExactly(GameAction.Select(option(p, 0)))
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
        tapInPanel(p, 0)
        compose.onNodeWithTag(CARD_TAG).assertExists()
        // The large card is for reading: a tap on it answers nothing.
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Select(option(p, 0)))
        compose.onNodeWithTag(CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Select(option(p, 0)), GameAction.Answer(option(p, 0))).inOrder()
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `defend - back answers nothing, the button defends with the card chosen and says how it ends`() {
        val p = show(DecisionKind.DEFEND)
        tapInPanel(p, 0)
        tapInPanel(p, 0)
        compose.onNodeWithTag(CARD_TAG).assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
        assertThat(actions.filterIsInstance<GameAction.Answer>()).isEmpty()
        tapInPanel(p, 1)
        // The chosen row says how its total is counted ("Defense 3 = card 2 + minion 1 vs Attack 6").
        compose.onAllNodes(hasContentDescription(" = card ", substring = true)).onFirst().assertExists()
        panelButton("Defend · ${name(p, 1)}").performClick()
        compose.waitForIdle()
        assertThat(actions.last()).isEqualTo(GameAction.Answer(option(p, 1)))
    }

    @Test
    fun `play a card - the button names the card and plays it facedown`() {
        val p = show(DecisionKind.PLAY_CARD)
        tapInPanel(p, 2)
        panelButton("Play ${name(p, 2)} facedown").performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Select(option(p, 2)), GameAction.Answer(option(p, 2))).inOrder()
    }

    @Test
    fun `a Coach nudge intercepts the button's answer`() {
        lateinit var p: PlayUi
        p = show(DecisionKind.CHOOSE_CARD) { action ->
            applying(action)
            // As the ViewModel does: the answer goes to the coach first, who nudges.
            if (action is GameAction.Answer) {
                state = state!!.copy(
                    table = state!!.table.copy(asking = false),
                    nudge = PendingNudge(CoachNote("Keep it", "A note."), action.choice, "the card"),
                )
            }
        }
        tapInPanel(p, 0)
        panelButton("Discard ${name(p, 0)}").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Coach: Keep it").assertExists()
        compose.onNodeWithText("Choose anyway").performClick()
        assertThat(actions).containsExactly(
            GameAction.Select(option(p, 0)),
            GameAction.Answer(option(p, 0)),
            GameAction.ChooseAnyway,
        ).inOrder()
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
        compose.onNodeWithTag(CARD_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).isEmpty()
        compose.onNodeWithTag(CARD_TAG).assertExists()
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).assertTextEquals("Go to ${cards.getValue(current).name}")
        compose.onNodeWithTag(GO_TO_ANSWER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CONFIRM_TAG).assertTextEquals("Upgrade ${cards.getValue(current).name}")
        compose.onNodeWithTag(CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Answer(Choice.Card(current)))
    }

    @Test
    fun `level up - a tap chooses the hand card, the button upgrades it`() {
        val p = show(DecisionKind.UPGRADE_CARD)
        tapInPanel(p, 1)
        panelButton("Upgrade ${name(p, 1)}").performClick()
        compose.waitForIdle()
        assertThat(actions).containsExactly(GameAction.Select(option(p, 1)), GameAction.Answer(option(p, 1))).inOrder()
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
