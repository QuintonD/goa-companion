package dev.quinton.goa.feature.rules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.rules.ui.LevelLadderSheet
import dev.quinton.goa.feature.rules.ui.RulesScreen
import dev.quinton.goa.feature.rules.ui.RulesTopicScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RulesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    /** A level 5 hero defeated, in a match with Bain and Ursafar (whose cards add Life counters). */
    private val ladder = LadderUiState.Ready(
        ladder = bundledReference.ladder,
        selectedLevel = 5,
        exceptions = DefeatExceptions.find(listOf(bundledHero("bain"), bundledHero("ursafar"))).map { (hero, card) ->
            ExceptionCardUi(hero.name, card.name, RulesText.cardLabel(hero, card), card.text)
        },
        matchHeroesOnly = true,
    )

    @Composable
    private fun Ladder(state: LadderUiState = ladder) = Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        LevelLadderSheet(state, onSelectLevel = {}, onOpenRules = {})
    }

    @Composable
    private fun Topic(id: String) =
        RulesTopicScreen(RulesTopicUiState.Ready(bundledReference.topics.first { it.id == id }), onBack = {})

    @Composable
    private fun RulesList() = RulesScreen(
        state = RulesUiState.Ready(
            query = "",
            topics = bundledReference.topics.map { TopicItem(it.id, it.title, it.pages, it.summary) },
            totalTopics = bundledReference.topics.size,
        ),
        onQueryChange = {},
        onOpenLadder = {},
        onOpenTopic = {},
        onBack = {},
    )

    @Test fun ladderDark() = capture(ThemeMode.DARK) { Ladder() }

    @Test fun ladderLight() = capture(ThemeMode.LIGHT) { Ladder() }

    @Test fun ladderDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Ladder() }

    @Test fun ladderNoLevelLight() = capture(ThemeMode.LIGHT) { Ladder(ladder.copy(selectedLevel = null)) }

    @Test fun topicDark() = capture(ThemeMode.DARK) { Topic("turn-sequence") }

    @Test fun topicLight() = capture(ThemeMode.LIGHT) { Topic("turn-sequence") }

    @Test fun topicEndOfRoundDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Topic("end-of-round") }

    @Test fun rulesListDark() = capture(ThemeMode.DARK) { RulesList() }

    @Test fun rulesListLight() = capture(ThemeMode.LIGHT) { RulesList() }
}
