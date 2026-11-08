package dev.quinton.goa.feature.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaSheetHandle
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import dev.quinton.goa.feature.rules.ui.ExcerptBlock
import dev.quinton.goa.feature.rules.ui.LevelLadderSheet
import dev.quinton.goa.feature.rules.ui.ParchmentPage
import dev.quinton.goa.feature.rules.ui.RulesScreen
import dev.quinton.goa.feature.rules.ui.RulesTopicScreen
import dev.quinton.goa.feature.rules.ui.TopicContents
import dev.quinton.goa.feature.rules.ui.topicSections
import org.junit.Before
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

    @Before fun images() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.waitForIdle()
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

    /** The ladder as the app shows it: a bottom sheet, its drag handle over the content (`GoaSheet`). */
    @Composable
    private fun Ladder(state: LadderUiState = ladder, compare: Boolean = false) = Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        GoaPlateTheme {
            Column {
                GoaSheetHandle()
                LevelLadderSheet(state, onSelectLevel = {}, onOpenRules = {}, onClose = {}, compareInitially = compare)
            }
        }
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

    /** "Compare all levels" open: the Level column pinned, the other six scrolling sideways. */
    @Test fun ladderCompareDark() = capture(ThemeMode.DARK) { Ladder(compare = true) }

    @Test fun ladderCompareDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Ladder(compare = true) }

    /** A topic's Sections sheet: every section by its whole name, the one being read selected. */
    @Test fun topicContentsDark() = capture(ThemeMode.DARK) {
        val topic = bundledReference.topics.first { it.id == "turn-sequence" }
        val sections = topicSections(topic)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            GoaPlateTheme {
                Column {
                    GoaSheetHandle()
                    TopicContents(sections, sections[1]) {}
                }
            }
        }
    }

    @Test fun ladderNoLevelLight() = capture(ThemeMode.LIGHT) { Ladder(ladder.copy(selectedLevel = null)) }

    @Test fun topicDark() = capture(ThemeMode.DARK) { Topic("turn-sequence") }

    @Test fun topicLight() = capture(ThemeMode.LIGHT) { Topic("turn-sequence") }

    @Test fun topicEndOfRoundDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Topic("end-of-round") }

    /** The p.8 diagram, multiplied into the parchment, and the book's blue boxes under it. */
    @Test
    @Config(qualifiers = "w411dp-h2300dp-xxhdpi")
    fun topicPushingDark() = capture(ThemeMode.DARK) { Topic("pushing-the-lane") }

    @Test
    @Config(qualifiers = "w411dp-h2300dp-xxhdpi")
    fun topicPushingLight() = capture(ThemeMode.LIGHT) { Topic("pushing-the-lane") }

    /** A green example box and a blue one with its heading. */
    @Test
    @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    fun topicTableTalkDark() = capture(ThemeMode.DARK) { Topic("table-talk") }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun topicLandscape() = capture(ThemeMode.DARK) { Topic("pushing-the-lane") }

    /** The p.20 Wave/Life counter table, the one verbatim table in the topics. */
    @Test fun excerptTableDark() = capture(ThemeMode.DARK) {
        val excerpt = bundledReference.topics.flatMap { it.excerpts }.first { it.table.isNotEmpty() }
        ParchmentPage(Modifier.fillMaxSize()) { ExcerptBlock(excerpt, Modifier.padding(16.dp)) }
    }

    /** At 1.3 "Counters" no longer fits a column: each row becomes "Wave Counters: 2 x 7" lines. */
    @Test fun excerptTableDarkFontScale13() = capture(ThemeMode.DARK, 1.3f) {
        val excerpt = bundledReference.topics.flatMap { it.excerpts }.first { it.table.isNotEmpty() }
        ParchmentPage(Modifier.fillMaxSize()) { ExcerptBlock(excerpt, Modifier.padding(16.dp)) }
    }

    @Test fun rulesListDark() = capture(ThemeMode.DARK) { RulesList() }

    @Test fun rulesListLight() = capture(ThemeMode.LIGHT) { RulesList() }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 15_000L
    }
}
