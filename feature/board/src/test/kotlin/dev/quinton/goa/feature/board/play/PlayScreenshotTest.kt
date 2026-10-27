package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.HeroResult
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.PendingNudge
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.RevealedCard
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.panelWorthy
import dev.quinton.goa.feature.board.play.game.recentSince
import dev.quinton.goa.feature.board.play.setup.Control
import dev.quinton.goa.feature.board.play.setup.NewGameAction
import dev.quinton.goa.feature.board.play.setup.NewGameScreen
import dev.quinton.goa.feature.board.play.setup.NewGameStep
import dev.quinton.goa.feature.board.play.setup.NewGameViewModel
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.resolutionFlow
import dev.quinton.goa.feature.board.play.table.turnLog
import dev.quinton.goa.feature.board.setup.NewBoardAction
import kotlin.random.Random
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game against the computer with the bundled map, catalog cards and portraits, driven by the
 * scripted [FakeGameSession]: the table in every kind of question, the computer's move, the wait,
 * the reveal, a coach nudge, the sheets; New game; game over. Light/dark, font scale 2 at 384 dp,
 * landscape, as the board's screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = bundledCatalog
    private val maps = bundledMaps
    private val heroes = catalog.boardHeroes()
    private val cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val all = setOf("arien", "brogan", "wasp", "tigerclaw")

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        before: () -> Unit = {
        },
        /** The whole screen, every window: the card focus viewer is its own. */
        screen: Boolean = false,
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
        if (screen) captureScreenRoboImage() else compose.onRoot().captureRoboImage()
    }

    // ---- The scripted game, stopped at a question ---------------------------------------

    /** A scripted game played (the script's answers) until the [nth] question of [kind]. */
    private fun sessionAt(
        kind: DecisionKind,
        nth: Int = 1,
        humans: Set<String> = all,
        answer: (FakeGameSession) -> Choice = { it.preferred()!! },
    ): FakeGameSession {
        val session = fakeFactory().newGame(fakeSetup(humans = humans)) as FakeGameSession
        var seen = 0
        var guard = 0
        while (guard++ < 80) {
            val d = session.pending ?: error("No $kind")
            if (d.kind == kind && ++seen == nth) return session
            session.answer(d.id, answer(session))
        }
        error("No $kind")
    }

    private fun merged(session: FakeGameSession, humans: Set<String>): GameView {
        val base = session.view(humans.first())
        val own = humans.associateWith { seat -> session.view(seat).heroes.first { it.heroId == seat } }
        return base.copy(heroes = base.heroes.map { own[it.heroId] ?: it })
    }

    private fun table(
        session: FakeGameSession,
        humans: Set<String>,
        selected: Choice? = null,
        bottom: Team = Team.RED,
    ) =
        TableUi(
            map = maps.first { it.id == "forgotten-island" },
            view = merged(session, humans),
            heroes = heroes,
            cards = cards,
            mine = humans,
            decision = session.pending,
            asking = session.pending?.seat in humans,
            selected = selected,
            bottomTeam = bottom,
            turnLog = turnLog(session.events),
        )

    private fun reveal(events: List<GameEvent>): List<RevealedCard> {
        val last = events.filterIsInstance<GameEvent.CardsRevealed>().lastOrNull() ?: return emptyList()
        return last.played.map { (h, c) ->
            RevealedCard(h, c, c?.let(cards::get)?.initiative)
        }.sortedByDescending { it.initiative }
    }

    private fun play(
        session: FakeGameSession,
        humans: Set<String> = all,
        selected: Choice? = null,
        status: TurnStatus = TurnStatus.YourTurn,
        nudge: PendingNudge? = null,
        over: GameOverUi? = null,
        withReveal: Boolean = true,
    ) = PlayUi(
        table = table(session, humans, selected),
        status = status,
        // As the game shows it: what happened since the person's last answer (here, the whole game).
        recent = if (status == TurnStatus.YourTurn) {
            recentSince(session.events, 0, session.setup.seats.map { it.heroId }.toSet()).lines
        } else {
            session.events.filter(::panelWorthy).takeLast(4).map { it.text }
        },
        log = logLines(session.events),
        flow = flowOf(session, humans),
        coach = true,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = true,
        nudge = nudge,
        reveal = if (withReveal &&
            session.view(null).phase == GamePhase.RESOLVE
        ) {
            reveal(session.events)
        } else {
            emptyList()
        },
        over = over,
    )

    /** As the game shows it: what happened since the person's last answer (here, the whole game). */
    private fun flowOf(session: FakeGameSession, humans: Set<String>) = resolutionFlow(
        session.events,
        0,
        session.setup.seats.associate { it.heroId to it.team },
        humans,
        { heroes.hero(it).name },
        { cards[it]?.name },
    )

    @Composable
    private fun Game(play: PlayUi, sheet: PlaySheet? = null, hidden: Boolean = false) {
        GameScreen(
            state = GameUiState.Playing(play),
            onAction = {},
            onTap = { false },
            onDelete = {},
            onNewGame = {},
            onDone = {},
            onBack = {},
            initialSheet = sheet,
            initialPanelHidden = hidden,
        )
    }

    private fun at(kind: DecisionKind, nth: Int = 1, select: (FakeGameSession) -> Choice? = { null }): PlayUi {
        val session = sessionAt(kind, nth)
        return play(session, selected = select(session))
    }

    // ---- Turn step 1: play a card (p.7) -----------------------------------------------

    private fun playCard() = at(DecisionKind.PLAY_CARD) { Choice.Card("arien-red-1") }

    @Test fun tablePlayCardDark() = capture(ThemeMode.DARK) { Game(remember { playCard() }) }

    @Test fun tablePlayCardLight() = capture(ThemeMode.LIGHT) { Game(remember { playCard() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tablePlayCardW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Game(remember { playCard() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tablePlayCardLandscapeDark() = capture(ThemeMode.DARK) { Game(remember { playCard() }) }

    /** Sideways on an S25-sized phone (bars and cutout taken), mid-turn: the top keeps to one row. */
    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun tableChooseActionLandscapeShortDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_ACTION, 3) })
    }

    /** Simultaneous play: your card is down (shown to you only), the computer still chooses. */
    @Test
    fun tableWaitingDark() = capture(ThemeMode.DARK) {
        Game(
            remember {
                val humans = setOf("arien")
                val s = sessionAt(DecisionKind.PLAY_CARD, humans = humans).also {
                    it.answer(it.pending!!.id, Choice.Card("arien-red-1"))
                }
                play(s, humans, status = TurnStatus.Waiting(listOf("brogan", "wasp", "tigerclaw")))
            },
        )
    }

    /** After the reveal: the computer is choosing for Wasp, every played card with its initiative. */
    @Test
    fun tableRevealComputerDark() = capture(ThemeMode.DARK) {
        Game(
            remember {
                val s = sessionAt(DecisionKind.CHOOSE_ACTION, humans = setOf("arien"))
                play(s, setOf("arien"), status = TurnStatus.Computer("wasp", thinking = true))
            },
        )
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableRevealComputerLandscapeLight() = capture(ThemeMode.LIGHT) {
        Game(
            remember {
                val s = sessionAt(DecisionKind.CHOOSE_ACTION, humans = setOf("arien"))
                play(s, setOf("arien"), status = TurnStatus.Computer("wasp"))
            },
        )
    }

    // ---- The turn at a glance (ADR-041) -----------------------------------------------------

    /** Right after the reveal with the panel hidden: the turn's cards stay in view, Wasp acting. */
    @Test
    fun tableRevealHiddenDark() = capture(ThemeMode.DARK) {
        Game(
            remember {
                val s = sessionAt(DecisionKind.CHOOSE_ACTION, humans = setOf("arien"))
                play(s, setOf("arien"), status = TurnStatus.Computer("wasp"))
            },
            hidden = true,
        )
    }

    /** Large text, mid-turn: two cards done, Arien acting, Brogan next. */
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableMidTurnW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) {
        Game(remember { at(DecisionKind.CHOOSE_ACTION, 3) }, hidden = true)
    }

    /** A card on the turn track tapped: Wasp's card large, "Wasp · Done · Movement · Initiative 10". */
    @Test
    fun tableTrackFocusDark() {
        val play = at(DecisionKind.CHOOSE_ACTION, 3)
        capture(ThemeMode.DARK, before = { tapCard("1st to act, Wasp") }, screen = true) { Game(play) }
    }

    /** A tie in one team (turn 4): Wasp done, Arien and Brogan tied on 11 and asked who goes first. */
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableActFirstLandscapeDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.ACT_FIRST) }) }

    /**
     * The turn is over: the next turn starts with everyone playing a card, the cards of the turn just
     * over on their Turn slots in the panel. The scripted game skips from turn 1 to turn 4, so this
     * is round 2's first question shown as turn 2 of round 1, with the view the engine gives then.
     */
    @Test
    fun tableTurnOverDark() = capture(ThemeMode.DARK) { Game(remember { turnOver() }) }

    @Test fun tableTurnOverLight() = capture(ThemeMode.LIGHT) { Game(remember { turnOver() }) }

    /** A new round: who won the push, who levelled up, then the first card of the round. */
    @Test
    fun tableRoundOverDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.PLAY_CARD, 5) }) }

    private fun turnOver(): PlayUi {
        val s = sessionAt(DecisionKind.PLAY_CARD, 5)
        val p = play(s)
        val turn1 = mapOf(
            "wasp" to "wasp-blue-1",
            "tigerclaw" to "tigerclaw-red-1",
            "arien" to "arien-red-1",
            "brogan" to "brogan-red-1",
        )
        val hand = listOf("arien-gold", "arien-silver", "arien-green-1", "arien-blue-1")
        val view = p.table.view.copy(
            round = 1,
            turn = 2,
            heroes = p.table.view.heroes.map { h ->
                h.copy(
                    level = 1,
                    resolved = listOf(dev.quinton.goa.core.game.CardView(turn1.getValue(h.heroId), slot = 1)),
                    hand = if (h.heroId == "arien") hand else h.hand?.let { it.take(4) },
                    handCount = 4,
                    markers = emptyList(),
                    activeEffects = emptyList(),
                    items = emptyList(),
                )
            },
        )
        val log = listOf(
            GameEvent.TurnStarted(1, 1, "Turn 1 of round 1."),
            GameEvent.CardsRevealed(
                linkedMapOf(
                    "wasp" to "wasp-blue-1",
                    "tigerclaw" to "tigerclaw-red-1",
                    "arien" to "arien-red-1",
                    "brogan" to "brogan-red-1",
                ),
                "Cards are revealed.",
            ),
            GameEvent.TurnStarted(1, 2, "Turn 2 of round 1."),
        )
        val decision = s.pending!!.copy(options = hand.map { Choice.Card(it) })
        val turn1Log = listOf(
            log[0],
            log[1],
            GameEvent.ActionChosen("arien", "arien-red-1", ActionType.PRIMARY, "Arien uses Dangerous Current: primary action."),
            GameEvent.Attacked("arien", "tigerclaw", 6, "Arien attacks Tigerclaw: Attack 6."),
            GameEvent.Defeated("tigerclaw", "arien", "Tigerclaw is defeated by Arien."),
            log[2],
        )
        return p.copy(
            table = p.table.copy(view = view, decision = decision, turnLog = log),
            recent = listOf("Arien attacks Tigerclaw with Attack 6.", "Tigerclaw is defeated by Arien."),
            recentStrong = setOf(1),
            flow = resolutionFlow(
                turn1Log,
                0,
                s.setup.seats.associate { it.heroId to it.team },
                setOf("arien"),
                { heroes.hero(it).name },
                { cards[it]?.name },
            ),
        )
    }

    // ---- Every other kind of question ---------------------------------------------------

    @Test fun tableChooseActionDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.CHOOSE_ACTION, 3) }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableChooseActionW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) {
        Game(remember { at(DecisionKind.CHOOSE_ACTION, 3) })
    }

    /** A move: every reachable space outlined, the tapped one with the path to it. */
    @Test
    fun tableChooseSpaceDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_SPACE) { s -> s.pending!!.options.last() } })
    }

    @Test
    fun tableChooseSpaceLight() = capture(ThemeMode.LIGHT) {
        Game(remember { at(DecisionKind.CHOOSE_SPACE) { s -> s.pending!!.options.last() } })
    }

    // ---- The panel hidden: the whole board, the question in the peek bar ------------------

    /** A move with the panel hidden: the whole board, the tapped space confirmed from the peek bar. */
    @Test
    fun tablePanelHiddenDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_SPACE) { s -> s.pending!!.options.last() } }, hidden = true)
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tablePanelHiddenLandscapeDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_SPACE) { s -> s.pending!!.options.last() } }, hidden = true)
    }

    /** Large text: the question still one line in the peek bar. */
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tablePanelHiddenW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) {
        Game(remember { at(DecisionKind.CHOOSE_ACTION, 3) }, hidden = true)
    }

    @Test
    fun tableChooseUnitDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_UNIT) { Choice.Unit("tigerclaw") } })
    }

    /** Defend (p.14): the Attack, each card's Defense total, Holds or Falls, Don't defend. */
    @Test
    fun tableDefendDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.DEFEND) { s -> s.pending!!.options.first() } })
    }

    @Test fun tableDefendLight() = capture(ThemeMode.LIGHT) { Game(remember { at(DecisionKind.DEFEND) }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableDefendW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Game(remember { at(DecisionKind.DEFEND, 2) }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableDefendLandscapeDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.DEFEND, 2) }) }

    @Test fun tableChooseNumberDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.CHOOSE_NUMBER) }) }

    @Test fun tableConfirmDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.CONFIRM) }) }

    @Test fun tableRespawnDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.RESPAWN) }) }

    @Test fun tableActFirstDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.ACT_FIRST) }) }

    @Test fun tableChooseOptionDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.CHOOSE_OPTION) }) }

    @Test fun tableChooseTokenDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.CHOOSE_TOKEN) }) }

    @Test
    fun tableChooseCardDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.CHOOSE_CARD) { s -> s.pending!!.options.first() } })
    }

    @Test fun tableRemoveMinionDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.REMOVE_MINION) }) }

    @Test fun tableMinionReturnDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.MINION_RETURN) }) }

    /** Level up (ADR-045): each card that may be upgraded with the two it would become and their items. */
    @Test
    fun tableUpgradeCardDark() = capture(ThemeMode.DARK) { Game(remember { at(DecisionKind.UPGRADE_CARD) }) }

    /** Keep one, the other becomes an item: the item's stat icon on each card. */
    @Test
    fun tableKeepUpgradeDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.KEEP_UPGRADE) { s -> s.pending!!.options.first() } })
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableKeepUpgradeW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) {
        Game(remember { at(DecisionKind.KEEP_UPGRADE) { s -> s.pending!!.options.first() } })
    }

    // ---- Coach, sheets ------------------------------------------------------------------

    @Test
    fun tableCoachNudgeDark() = capture(ThemeMode.DARK) {
        Game(
            remember {
                val s = sessionAt(DecisionKind.CHOOSE_ACTION, 3)
                play(
                    s,
                    nudge = PendingNudge(
                        CoachNote(
                            "Tigerclaw is next to you",
                            "Holding wastes Dangerous Current's Attack 6 while an enemy hero stands adjacent (p.13).",
                        ),
                        Choice.Action(ActionType.HOLD),
                        "Hold",
                    ),
                )
            },
        )
    }

    /** A hero's open information (p.4): items, markers, Active effects, Turn slots. */
    @Test
    fun tableHeroSheetDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.PLAY_CARD, 5) }, sheet = PlaySheet.Hero("arien"))
    }

    @Test
    fun tableHeroSheetLight() = capture(ThemeMode.LIGHT) {
        Game(remember { at(DecisionKind.DEFEND, 2) }, sheet = PlaySheet.Hero("tigerclaw"))
    }

    @Test fun tableLogSheetDark() = capture(ThemeMode.DARK) {
        Game(remember { at(DecisionKind.UPGRADE_CARD) }, sheet = PlaySheet.Log)
    }

    // ---- Card focus: a card tapped in the panel, large, ready for a second tap --------------

    /** Taps the card called [name] in the panel (its thumbnail or defense row), as a first tap does. */
    private fun tapCard(name: String) {
        compose.onAllNodes(hasContentDescription(name, substring = true) and hasClickAction()).onFirst().performClick()
    }

    private fun firstOption(play: PlayUi): String = play.table.card(play.table.cardOptions.first().cardId)!!.name

    /** Discard a card (Blend Into Shadows): Magnetic Dagger focused, "Tap the card again to discard…". */
    private fun focusedDiscard(mode: ThemeMode, fontScale: Float = 1f) {
        val play = at(DecisionKind.CHOOSE_CARD)
        capture(mode, fontScale, before = { tapCard(firstOption(play)) }, screen = true) { Game(play) }
    }

    @Test fun tableCardFocusDark() = focusedDiscard(ThemeMode.DARK)

    @Test fun tableCardFocusLight() = focusedDiscard(ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableCardFocusW384DarkFontScale2() = focusedDiscard(ThemeMode.DARK, 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableCardFocusLandscapeDark() = focusedDiscard(ThemeMode.DARK)

    /**
     * A phone held sideways once the status bar, the gesture bar and the camera cutout are taken
     * (S25-sized, hands-on 2026-10-25): the card at full height, beside it only what it doesn't print.
     */
    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun tableCardFocusLandscapeShortDark() = focusedDiscard(ThemeMode.DARK)

    /** "Read the card as text" opened (ADR-041): the name, kind, values and the text verbatim. */
    private fun focusedAsText(mode: ThemeMode, fontScale: Float = 1f) {
        val play = at(DecisionKind.CHOOSE_CARD)
        capture(mode, fontScale, before = {
            tapCard(firstOption(play))
            compose.waitForIdle()
            compose.onAllNodes(hasText("Read the card as text") and hasClickAction()).onFirst().performClick()
        }, screen = true) { Game(play) }
    }

    @Test fun tableCardFocusAsTextDark() = focusedAsText(ThemeMode.DARK)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableCardFocusAsTextW384LightFontScale2() = focusedAsText(ThemeMode.LIGHT, 2f)

    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun tableCardFocusAsTextLandscapeShortDark() = focusedAsText(ThemeMode.DARK)

    /** Play a card sideways on a short screen: a Tier I card, its caption, the text and Play facedown. */
    @Config(qualifiers = "w800dp-h330dp-land-xxhdpi")
    @Test
    fun tablePlayCardFocusLandscapeShortLight() {
        val play = at(DecisionKind.PLAY_CARD)
        capture(ThemeMode.LIGHT, before = {
            tapCard(play.table.card(play.table.cardOptions[1].cardId)!!.name)
        }, screen = true) {
            Game(play)
        }
    }

    /** Defend: the card large with its verdict on its corner and "Defense 3 against Attack 6". */
    @Test
    fun tableDefendFocusDark() {
        val play = at(DecisionKind.DEFEND)
        capture(ThemeMode.DARK, before = { tapCard(firstOption(play)) }, screen = true) { Game(play) }
    }

    /** Play a card: the hand large, neighbours peeking, "Tap the card again to play … facedown". */
    @Test
    fun tablePlayCardFocusDark() {
        val play = at(DecisionKind.PLAY_CARD)
        capture(ThemeMode.DARK, before = {
            tapCard(play.table.card(play.table.cardOptions[1].cardId)!!.name)
        }, screen = true) {
            Game(play)
        }
    }

    /** Level up: the card to upgrade with the two of the next Tier it is replaced by. */
    @Test
    fun tableUpgradeFocusDark() {
        val play = at(DecisionKind.UPGRADE_CARD)
        capture(ThemeMode.DARK, before = { tapCard(cards.getValue("arien-red-1").name) }, screen = true) { Game(play) }
    }

    /** Your hand, view only: swipe between, no confirm. */
    @Test
    fun tableHandViewDark() {
        val play = at(DecisionKind.CHOOSE_ACTION, 3)
        val first = play.table.heroView(play.table.mine.first { play.table.heroView(it)?.hand?.isNotEmpty() == true })!!
            .hand!!.first()
        capture(ThemeMode.DARK, before = { tapCard(cards.getValue(first).name) }, screen = true) { Game(play) }
    }

    // ---- Game over ------------------------------------------------------------------------

    private fun over(): PlayUi {
        val s = fakeFactory().newGame(fakeSetup(humans = setOf("arien"))) as FakeGameSession
        while (s.pending != null) s.answer(s.pending!!.id, s.preferred()!!)
        val view = s.view("arien")
        val results = view.heroes.map { h ->
            HeroResult(
                h.heroId,
                h.team,
                h.controller,
                h.level,
                heroDefeats = s.events.filterIsInstance<GameEvent.Defeated>().count { it.byHeroId == h.heroId },
                minionDefeats = 0,
                defeated = s.events.filterIsInstance<GameEvent.Defeated>().count { it.unitId == h.heroId },
                coins = s.events.filterIsInstance<GameEvent.CoinsGained>().filter {
                    it.heroId == h.heroId
                }.sumOf { it.amount },
            )
        }
        return play(
            s,
            setOf("arien"),
            status = TurnStatus.Over,
            over = GameOverUi(
                winner = Team.RED,
                reason = view.winReason!!,
                youWon = true,
                rounds = view.round,
                heroes = results,
                takeBacks = 1,
                nudges = 2,
                rethinks = 1,
                turningPoints = FakeCoach().turningPoints(s.save(), "arien"),
                coach = true,
            ),
        )
    }

    @Test fun gameOverDark() = capture(ThemeMode.DARK) { Game(remember { over() }) }

    @Test fun gameOverLight() = capture(ThemeMode.LIGHT) { Game(remember { over() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun gameOverW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Game(remember { over() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun gameOverLandscapeDark() = capture(ThemeMode.DARK) { Game(remember { over() }) }

    /** The computer won; Coach off: no turning points. */
    @Test
    fun gameOverLostLight() = capture(ThemeMode.LIGHT) {
        Game(
            remember {
                over().let { p ->
                    p.copy(
                        over = p.over!!.copy(winner = Team.BLUE, youWon = false, coach = false, turningPoints = null),
                    )
                }
            },
        )
    }

    @Test
    fun gameIncompatibleLight() = capture(ThemeMode.LIGHT) {
        GameScreen(GameUiState.Unavailable(NoGame.INCOMPATIBLE), {}, { false }, {}, {}, {}, {})
    }

    // ---- New game -------------------------------------------------------------------------

    private fun newGame(
        engine: FakeGameFactory = fakeFactory(),
        route: NewGameRoute = NewGameRoute(),
    ) = NewGameViewModel(
        route,
        SavedStateHandle(),
        FakeCatalogRepository(catalog),
        FakeBoardMapRepository(maps),
        FakeDraftRecordRepository(),
        FakeMatchRepository(),
        FakeSettingsRepository(),
        FakeGameSaveRepository(),
        { engine },
        EditClock { 0L },
        Random(2),
    )

    @Composable
    private fun NewGame(vm: NewGameViewModel, starting: Boolean = false) {
        val state by vm.uiState.collectAsState()
        NewGameScreen(state, onAction = vm::onAction, onStart = {}, onBack = {}, starting = starting)
    }

    private fun picked() = newGame().also { vm ->
        listOf("arien", "wasp", "brogan").forEach { vm.onAction(NewGameAction.Lineup(NewBoardAction.Tap(it))) }
    }

    private fun options(advanced: Boolean = false) = newGame(
        route = NewGameRoute(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw")),
    ).also { vm ->
        vm.onAction(NewGameAction.Step(NewGameStep.OPTIONS))
        vm.onAction(NewGameAction.SetControl(Control.Hero("arien")))
        vm.onAction(NewGameAction.SetStrength(AiLevel.HARD.strength))
        if (advanced) {
            vm.onAction(NewGameAction.SetAdvanced(true))
            vm.onAction(NewGameAction.SetSeatStrength("brogan", 2))
        }
    }

    @Test fun newGameLineupDark() = capture(ThemeMode.DARK) { NewGame(remember { picked() }) }

    @Test fun newGameLineupLight() = capture(ThemeMode.LIGHT) { NewGame(remember { picked() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newGameLineupW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { NewGame(remember { picked() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newGameLineupLandscapeDark() = capture(ThemeMode.DARK) { NewGame(remember { picked() }) }

    @Test fun newGameOptionsDark() = capture(ThemeMode.DARK) { NewGame(remember { options() }) }

    @Test fun newGameOptionsAdvancedLight() = capture(ThemeMode.LIGHT) {
        NewGame(remember { options(advanced = true) })
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newGameOptionsW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { NewGame(remember { options() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newGameOptionsLandscapeLight() = capture(ThemeMode.LIGHT) { NewGame(remember { options() }) }

    /** The strength sliders (ADR-039), scrolled into view: the main one and one per computer seat. */
    private fun strengthInView() = compose.onNodeWithContentDescription("Wasp's strength").performScrollTo()

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newGameStrengthW384DarkFontScale2() = capture(ThemeMode.DARK, 2f, before = ::strengthInView) {
        NewGame(remember { options(advanced = true) })
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newGameStrengthLandscapeLight() = capture(ThemeMode.LIGHT, before = ::strengthInView) {
        NewGame(remember { options(advanced = true).also { it.onAction(NewGameAction.SetSeatStrength("wasp", 10)) } })
    }

    /** Strength 1, the gentlest, says so. */
    @Test
    fun newGameStrengthGentlestLight() = capture(ThemeMode.LIGHT, before = {
        compose.onNodeWithContentDescription("Computer strength").performScrollTo()
    }) {
        NewGame(remember { options().also { it.onAction(NewGameAction.SetStrength(1)) } })
    }

    /** The app flipped the Tie Breaker coin: what it decides, then Start. */
    @Test
    fun newGameCoinDark() = capture(ThemeMode.DARK) {
        NewGame(remember { options().also { it.onAction(NewGameAction.FlipCoin) } })
    }

    /** No hero is playable yet (the placeholder engine): play is coming. */
    @Test
    fun newGameComingSoonDark() = capture(ThemeMode.DARK) {
        NewGame(remember { newGame(FakeGameFactory(maps, playableHeroes = emptySet())) })
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
