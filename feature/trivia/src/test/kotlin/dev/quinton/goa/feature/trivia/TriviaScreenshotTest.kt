package dev.quinton.goa.feature.trivia

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.FakeTriviaProgressRepository
import dev.quinton.goa.feature.trivia.home.HeroOption
import dev.quinton.goa.feature.trivia.home.HeroPickerContent
import dev.quinton.goa.feature.trivia.home.TriviaHomeScreen
import dev.quinton.goa.feature.trivia.home.TriviaHomeViewModel
import dev.quinton.goa.feature.trivia.quiz.QuizQuestionUi
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizScreen
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizUiState
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizViewModel
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real bundled heroes and card numbers. The quiz runs on the real engine with a fixed seed, so
 * every capture shows the same question.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TriviaScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = bundledCatalog

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        before: () -> Unit = {
        },
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
        compose.onRoot().captureRoboImage()
    }

    // ---- Home -----------------------------------------------------------------------------

    /** Some practice behind: Arien fully learned, Brogan part learned and part learning. */
    private fun practised(): Map<String, FactProgress> {
        val arien = TriviaFacts.of(catalog.hero("arien")!!)
        val brogan = TriviaFacts.of(catalog.hero("brogan")!!)
        val dodger = TriviaFacts.of(catalog.hero("dodger")!!)
        fun List<TriviaFact>.at(box: Int) = associate { it.key to FactProgress(it.value, it.plus, box = box) }
        return arien.at(TriviaMastery.MASTERED_BOX) +
            brogan.take(30).at(TriviaMastery.MASTERED_BOX) +
            brogan.drop(30).take(25).at(1) +
            dodger.take(12).at(2)
    }

    private fun homeViewModel(config: TriviaConfig? = null, heroId: String? = null) = TriviaHomeViewModel(
        heroId,
        FakeCatalogRepository(catalog),
        FakeSettingsRepository(),
        FakeTriviaProgressRepository(practised(), config),
    )

    @Composable
    private fun Home(vm: TriviaHomeViewModel) {
        val state by vm.uiState.collectAsState()
        TriviaHomeScreen(state, onAction = vm::onAction, onStart = {}, onStartHero = {}, onReset = {}, onBack = {})
    }

    @Test fun homeDark() = capture(ThemeMode.DARK) { Home(homeViewModel()) }

    @Test fun homeLight() = capture(ThemeMode.LIGHT) { Home(homeViewModel()) }

    @Test fun homeDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Home(homeViewModel()) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun homeFromHeroPageW384Dark() = capture(ThemeMode.DARK) {
        Home(homeViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE, TriviaStat.ATTACK)), heroId = "brogan"))
    }

    /** The per-hero list further down. */
    @Test
    fun homeHeroesDark() = capture(
        ThemeMode.DARK,
        before = { compose.onNode(hasScrollAction()).performScrollToKey("heroes-title") },
    ) { Home(homeViewModel()) }

    /** No Ultimate prints Initiative: Start is off and says why. */
    @Test
    fun homeNothingToAskDark() = capture(
        ThemeMode.DARK,
        before = { compose.onNode(hasScrollAction()).performScrollToKey("start") },
    ) {
        Home(homeViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), groups = setOf(CardGroup.ULTIMATE))))
    }

    @Test
    fun heroPickerDark() = capture(ThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            HeroPickerContent(
                options = catalog.heroes.sortedBy { it.name }.map { HeroOption(it.id, it.name, it.title, it.portrait) },
                selected = listOf("arien", "brogan"),
                ownedOnly = false,
                onToggle = {},
                onAll = {},
                onDone = {},
            )
        }
    }

    // ---- Quiz -----------------------------------------------------------------------------

    private fun quizViewModel(config: TriviaConfig, seed: Int = 7, factKeys: List<String> = emptyList()) =
        TriviaQuizViewModel(
            TriviaQuizRoute(TriviaText.encode(config), factKeys),
            FakeCatalogRepository(catalog),
            FakeSettingsRepository(),
            FakeTriviaProgressRepository(practised()),
            EditClock { 0L },
            Random(seed),
        )

    @Composable
    private fun Quiz(vm: TriviaQuizViewModel) {
        val state by vm.uiState.collectAsState()
        TriviaQuizScreen(
            state,
            onAnswer = vm::answer,
            onNext = vm::next,
            onStop = vm::stop,
            onPractise = {},
            onPlayAgain = vm::playAgain,
            onDone = {},
            onBack = {},
        )
    }

    private fun TriviaQuizViewModel.playing() = uiState.value as TriviaQuizUiState.Playing

    private fun TriviaQuizViewModel.answerRight() = answer(
        when (val q = playing().question) {
            is QuizQuestionUi.Number -> TriviaAnswer.Number(q.value)
            is QuizQuestionUi.Duel -> TriviaAnswer.Duel(q.right)
        },
    )

    private fun TriviaQuizViewModel.answerWrong() = answer(
        when (val q = playing().question) {
            is QuizQuestionUi.Number -> TriviaAnswer.Number(q.choices.firstOrNull { it != q.value } ?: (q.value + 1))
            is QuizQuestionUi.Duel -> TriviaAnswer.Duel(DuelPick.entries.first { it != q.right })
        },
    )

    /** Answers right until a duel is on screen. */
    private fun TriviaQuizViewModel.untilDuel() {
        repeat(MAX_STEPS) {
            if (playing().question is QuizQuestionUi.Duel) return
            answerRight()
            next()
        }
        error("no duel in $MAX_STEPS questions")
    }

    private val choices = TriviaConfig(heroIds = listOf("arien"), duels = false)

    @Test fun quizChoicesDark() = capture(ThemeMode.DARK) { Quiz(quizViewModel(choices)) }

    @Test fun quizChoicesLight() = capture(ThemeMode.LIGHT) { Quiz(quizViewModel(choices)) }

    @Test
    fun quizRecallLight() = capture(ThemeMode.LIGHT) {
        Quiz(
            quizViewModel(
                TriviaConfig(stats = setOf(TriviaStat.ATTACK), answerStyle = AnswerStyle.RECALL, duels = false),
            ),
        )
    }

    @Test
    fun quizAnsweredWrongDark() {
        val vm = quizViewModel(choices)
        capture(ThemeMode.DARK, before = { vm.answerWrong() }) { Quiz(vm) }
    }

    @Test
    fun quizAnsweredRightLight() {
        val vm = quizViewModel(choices, seed = 3)
        capture(ThemeMode.LIGHT, before = { vm.answerRight() }) { Quiz(vm) }
    }

    @Test
    fun quizDuelDark() {
        val vm = quizViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), length = TriviaConfig.ENDLESS))
        vm.untilDuel()
        capture(ThemeMode.DARK) { Quiz(vm) }
    }

    @Test
    fun quizDuelAnsweredWrongDark() {
        val vm = quizViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), length = TriviaConfig.ENDLESS))
        vm.untilDuel()
        capture(ThemeMode.DARK, before = { vm.answerWrong() }) { Quiz(vm) }
    }

    @Test
    fun quizDuelDarkFontScale2() {
        val vm = quizViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), length = TriviaConfig.ENDLESS))
        vm.untilDuel()
        capture(ThemeMode.DARK, 2f) { Quiz(vm) }
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun quizW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Quiz(quizViewModel(choices)) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun quizRecallAnsweredW384Dark() {
        val vm =
            quizViewModel(
                TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), answerStyle = AnswerStyle.RECALL, duels = false),
            )
        capture(ThemeMode.DARK, before = { vm.answerWrong() }) { Quiz(vm) }
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun quizLandscapeAnsweredDark() {
        val vm = quizViewModel(choices)
        capture(ThemeMode.DARK, before = { vm.answerWrong() }) { Quiz(vm) }
    }

    @Test
    fun quizSummaryDark() {
        val vm = quizViewModel(TriviaConfig(heroIds = listOf("brogan"), length = 10), seed = 11)
        repeat(10) { i ->
            if (i % 4 == 1) vm.answerWrong() else vm.answerRight()
            vm.next()
        }
        capture(ThemeMode.DARK) { Quiz(vm) }
    }

    @Test
    fun quizSummaryLightFontScale2() {
        val vm = quizViewModel(TriviaConfig(heroIds = listOf("brogan"), length = 10), seed = 11)
        repeat(10) { i ->
            if (i % 4 == 1) vm.answerWrong() else vm.answerRight()
            vm.next()
        }
        capture(ThemeMode.LIGHT, 2f) { Quiz(vm) }
    }

    @Test
    fun quizEmptyDark() = capture(ThemeMode.DARK) {
        Quiz(quizViewModel(TriviaConfig(stats = setOf(TriviaStat.INITIATIVE), groups = setOf(CardGroup.ULTIMATE))))
    }

    private companion object {
        const val MAX_STEPS = 200
    }
}
