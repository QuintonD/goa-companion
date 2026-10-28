package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.recentSince
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.turnLog
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Level up's first question (ADR-045) on the scripted game, beside PlayScreenshotTest's dark portrait
 * (`tableUpgradeCardDark`, `tableUpgradeFocusDark`): light, landscape and large text, and an upgrade
 * card in the viewer ("Upgrade of …", "Go to …").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UpgradeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val all = setOf("arien", "brogan", "wasp", "tigerclaw")
    private val upgradeOf = "Upgrade of ${cards.getValue("arien-red-1").name}"

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    /** The scripted game (every seat the person's) at Arien's level up. */
    private fun levelUp(): PlayUi {
        val session = fakeFactory().newGame(fakeSetup(humans = all)) as FakeGameSession
        repeat(80) {
            val d = session.pending ?: error("No level up")
            if (d.kind == DecisionKind.UPGRADE_CARD) return play(session)
            session.answer(d.id, session.preferred()!!)
        }
        error("No level up")
    }

    private fun play(session: FakeGameSession): PlayUi {
        val base = session.view(all.first())
        val own = all.associateWith { seat -> session.view(seat).heroes.first { it.heroId == seat } }
        return PlayUi(
            table = TableUi(
                map = bundledMaps.first { it.id == "forgotten-island" },
                view = base.copy(heroes = base.heroes.map { own[it.heroId] ?: it }),
                heroes = bundledCatalog.boardHeroes(),
                cards = cards,
                mine = all,
                decision = session.pending,
                asking = true,
                turnLog = turnLog(session.events),
            ),
            status = TurnStatus.YourTurn,
            recent = recentSince(session.events, 0, session.setup.seats.map { it.heroId }.toSet()).lines,
            log = logLines(session.events),
            coach = true,
            pace = AiPace.NORMAL,
            takeBacks = 0,
            canTakeBack = true,
        )
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

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, screen: Boolean = false, before: () -> Unit = {}) {
        val play = levelUp()
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { Game(play) }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        if (screen) captureScreenRoboImage() else compose.onRoot().captureRoboImage()
    }

    private fun tapUpgradeCard() {
        compose.onAllNodes(
            hasContentDescription(upgradeOf, substring = true) and hasClickAction(),
        ).onFirst().performClick()
    }

    @Test fun upgradeCardLight() = capture(ThemeMode.LIGHT)

    /** Large text: the panel scrolls; shown at the rows rather than the round's summary above them. */
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun upgradeCardW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) {
        compose.onAllNodes(hasContentDescription(upgradeOf, substring = true)).onFirst().performScrollTo()
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun upgradeCardLandscapeDark() = capture(ThemeMode.DARK)

    /** An upgrade card large: "Upgrade of Dangerous Current", its item, and "Go to" the card that is the choice. */
    @Test
    fun upgradeFocusUpgradeCardDark() = capture(ThemeMode.DARK, screen = true) { tapUpgradeCard() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun upgradeFocusUpgradeCardLandscapeLight() = capture(ThemeMode.LIGHT, screen = true) { tapUpgradeCard() }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
