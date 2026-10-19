package dev.quinton.goa.feature.board

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.setup.NewBoardScreen
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New board on a 384 dp phone, in the space the app gives it (under the status and top bars, above
 * the tab bar): every hero of the catalog is on screen without scrolling, with the latest draft's
 * card on top, and the first pick doesn't move the grid under the next tap (PRODUCT §8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h692dp-xxhdpi")
class NewBoardFitTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = bundledCatalog
    private val draft = DraftRecord(
        id = "d1",
        createdAt = 1L,
        methodId = "house",
        methodKind = DraftMethodKind.CUSTOM,
        pool = emptyList(),
        teamA = Team.RED,
        steps = listOf("wasp" to Team.BLUE, "arien" to Team.RED)
            .map { (hero, team) -> DraftStepRecord(team, DraftAction.PICK, hero) },
    )

    private fun show() {
        val vm = NewBoardViewModel(
            NewBoardRoute(),
            SavedStateHandle(),
            FakeCatalogRepository(catalog),
            FakeBoardMapRepository(bundledMaps),
            FakeDraftRecordRepository(listOf(draft)),
            FakeMatchRepository(),
            FakeSettingsRepository(),
        )
        compose.setContent {
            val state by remember { vm.uiState }.collectAsState()
            GoaTheme { NewBoardScreen(state, onAction = vm::onAction, onSetUp = {}, onSetUpLatest = {}, onBack = {}) }
        }
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription(catalog.heroes.first().name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun everyHeroIsOnScreenAndTheFirstPickMovesNothing() {
        show()
        compose.onNodeWithContentDescription("Latest", substring = true).assertIsDisplayed()
        catalog.heroes.forEach { hero -> compose.onNodeWithContentDescription(hero.name).assertIsDisplayed() }

        val last = catalog.heroes.maxBy { it.name }.name
        val before = compose.onNodeWithContentDescription(last).getBoundsInRoot()
        compose.onNodeWithContentDescription(catalog.heroes.first().name).performClick()
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithContentDescription(last).getBoundsInRoot())
        catalog.heroes.drop(1).forEach { hero -> compose.onNodeWithContentDescription(hero.name).assertIsDisplayed() }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
