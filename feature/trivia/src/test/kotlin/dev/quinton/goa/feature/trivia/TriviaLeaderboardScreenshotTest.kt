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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaChallenge
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.FakeTriviaProgressRepository
import dev.quinton.goa.core.testing.FakeTriviaRoundRepository
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.ann
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.kit
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.mo
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.ravi
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.sam
import dev.quinton.goa.feature.trivia.ScreenshotPlayers.zoe
import dev.quinton.goa.feature.trivia.leaderboard.LeaderboardActions
import dev.quinton.goa.feature.trivia.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.trivia.leaderboard.PlayerRoundsContent
import dev.quinton.goa.feature.trivia.leaderboard.TriviaLeaderboardScreen
import dev.quinton.goa.feature.trivia.leaderboard.TriviaLeaderboardUiState
import dev.quinton.goa.feature.trivia.leaderboard.TriviaLeaderboardViewModel
import dev.quinton.goa.feature.trivia.quiz.QuizQuestionUi
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizScreen
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizUiState
import dev.quinton.goa.feature.trivia.quiz.TriviaQuizViewModel
import kotlin.random.Random
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The trivia leaderboard, a player's rounds and the Challenge summary (ADR-032). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TriviaLeaderboardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext())

    private fun setContent(mode: ThemeMode, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
    }

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        setContent(mode, fontScale, content)
        compose.onRoot().captureRoboImage()
    }

    /** Every window, so a dialog over the screen is included. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(mode: ThemeMode, content: @Composable () -> Unit) {
        setContent(mode, 1f, content)
        captureScreenRoboImage()
    }

    // ---- Data -----------------------------------------------------------------------------

    private var next = 0

    @Suppress("LongParameterList")
    private fun challenge(
        player: String,
        correct: Int,
        millis: Long,
        daysAgo: Int,
        id: String = "c${next++}",
    ) = TriviaRound(
        id = id,
        playerId = player,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = NOON - daysAgo * DAY,
        length = 20,
        questions = 20,
        correct = correct,
        bestStreak = correct / 2,
        answerMillis = millis,
        heroCount = 32,
        catalogVersion = 7,
    )

    private fun practice(player: String, correct: Int, questions: Int, streak: Int, daysAgo: Int) = TriviaRound(
        id = "p${next++}",
        playerId = player,
        kind = TriviaRoundKind.PRACTICE,
        playedAt = NOON - daysAgo * DAY,
        length = 0,
        questions = questions,
        correct = correct,
        bestStreak = streak,
        answerMillis = questions * 4_000L,
        heroCount = 3,
        catalogVersion = 7,
    )

    /** A busy group: Ann (this phone's You) and Sam tied for second; Kit's long name. */
    private fun busy(): List<TriviaRound> = listOf(
        challenge("zoe", 19, 130_000, daysAgo = 2),
        challenge("ann", 18, 102_000, daysAgo = 5, id = "ann-best"),
        challenge("sam", 18, 102_000, daysAgo = 5),
        challenge("ann", 14, 95_000, daysAgo = 1),
        challenge("ravi", 15, 88_000, daysAgo = 45),
        challenge("mo", 12, 140_000, daysAgo = 9),
        challenge("kit", 9, 201_000, daysAgo = 12),
        practice("ann", 64, 80, 17, daysAgo = 3),
        practice("sam", 30, 40, 9, daysAgo = 4),
        practice("zoe", 12, 20, 6, daysAgo = 40),
        practice("kit", 7, 10, 4, daysAgo = 8),
        practice("ann", 8, 10, 6, daysAgo = 0),
    )

    private fun leaderboard(
        rounds: List<TriviaRound>,
        tab: LeaderboardTab = LeaderboardTab.CHALLENGE,
        period: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
        open: String? = null,
    ) = TriviaLeaderboardViewModel(
        FakeTriviaRoundRepository(rounds),
        FakePlayerRepository(ScreenshotPlayers.all),
        FakeGroupRepository(GroupState(null, "this-phone", null, "ann", emptyList(), null)),
        EditClock { NOW },
        SavedStateHandle(),
    ).apply {
        selectTab(tab)
        selectPeriod(period)
        open?.let(::openPlayer)
    }

    @Composable
    private fun Board(vm: TriviaLeaderboardViewModel, confirm: String? = null) {
        val state by vm.uiState.collectAsState()
        TriviaLeaderboardScreen(
            state = state,
            actions = LeaderboardActions(onClosePlayer = vm::closePlayer),
            onBack = {},
            initialConfirm = confirm,
        )
    }

    // ---- Leaderboard ----------------------------------------------------------------------

    @Test fun leaderboardEmptyDark() = capture(ThemeMode.DARK) { Board(leaderboard(emptyList())) }

    @Test fun leaderboardEmptyLight() = capture(ThemeMode.LIGHT) { Board(leaderboard(emptyList())) }

    @Test
    fun leaderboardPracticeEmptyDark() =
        capture(ThemeMode.DARK) { Board(leaderboard(emptyList(), tab = LeaderboardTab.PRACTICE)) }

    /** Rounds exist, but none in the last 30 days. */
    @Test
    fun leaderboardRecentEmptyDark() = capture(ThemeMode.DARK) {
        Board(leaderboard(listOf(challenge("ravi", 15, 88_000, daysAgo = 45)), period = LeaderboardPeriod.LAST_30_DAYS))
    }

    @Test
    fun leaderboardOnePlayerDark() =
        capture(ThemeMode.DARK) { Board(leaderboard(listOf(challenge("sam", 17, 102_000, daysAgo = 3)))) }

    @Test fun leaderboardChallengeDark() = capture(ThemeMode.DARK) { Board(leaderboard(busy())) }

    @Test fun leaderboardChallengeLight() = capture(ThemeMode.LIGHT) { Board(leaderboard(busy())) }

    @Test
    fun leaderboardLast30DaysDark() =
        capture(ThemeMode.DARK) { Board(leaderboard(busy(), period = LeaderboardPeriod.LAST_30_DAYS)) }

    @Test
    fun leaderboardPracticeDark() = capture(ThemeMode.DARK) {
        Board(leaderboard(busy(), tab = LeaderboardTab.PRACTICE))
    }

    @Test
    fun leaderboardPracticeLight() =
        capture(ThemeMode.LIGHT) { Board(leaderboard(busy(), tab = LeaderboardTab.PRACTICE)) }

    @Test fun leaderboardChallengeDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Board(leaderboard(busy())) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun leaderboardChallengeW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Board(leaderboard(busy())) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun leaderboardPracticeW384Light() =
        capture(ThemeMode.LIGHT) { Board(leaderboard(busy(), tab = LeaderboardTab.PRACTICE)) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun leaderboardChallengeLandscapeDark() = capture(ThemeMode.DARK) { Board(leaderboard(busy())) }

    // ---- A player's rounds ----------------------------------------------------------------

    @Composable
    private fun Rounds(vm: TriviaLeaderboardViewModel) {
        val state by vm.uiState.collectAsState()
        val ready = state as? TriviaLeaderboardUiState.Ready ?: return
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            ready.selected?.let { PlayerRoundsContent(it, ready.period, onRemove = {}) }
        }
    }

    @Test fun playerRoundsDark() = capture(ThemeMode.DARK) { Rounds(leaderboard(busy(), open = "ann")) }

    @Test fun playerRoundsLight() = capture(ThemeMode.LIGHT) { Rounds(leaderboard(busy(), open = "ann")) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun playerRoundsW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Rounds(leaderboard(busy(), open = "ann")) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun playerRoundsLandscapeDark() = capture(ThemeMode.DARK) { Rounds(leaderboard(busy(), open = "sam")) }

    /** "Remove from leaderboard" asks first, naming the round. */
    @Test
    fun removeRoundDialogDark() =
        screen(ThemeMode.DARK) { Board(leaderboard(busy(), open = "ann"), confirm = "ann-best") }

    // ---- Challenge summary ----------------------------------------------------------------

    private var tick = 0L

    /** A Challenge on the real catalog played as Sam, with [rounds] already on the board. */
    private fun challengeQuiz(rounds: List<TriviaRound>) = TriviaQuizViewModel(
        TriviaQuizRoute(TriviaText.encode(TriviaChallenge.CONFIG), challenge = true),
        FakeCatalogRepository(bundledCatalog),
        FakeSettingsRepository(),
        FakeTriviaProgressRepository(playingAs = "sam"),
        FakePlayerRepository(ScreenshotPlayers.all),
        FakeGroupRepository(GroupState(null, "this-phone", null, "ann", emptyList(), null)),
        FakeTriviaRoundRepository(rounds),
        EditClock { NOW },
        Random(21),
        ticker = { tick },
        newId = { "new-${next++}" },
    )

    private fun TriviaQuizViewModel.play(count: Int, wrong: Set<Int>) {
        repeat(count) { i ->
            tick += 5_100
            val q = (uiState.value as TriviaQuizUiState.Playing).question
            answer(
                when (q) {
                    is QuizQuestionUi.Number -> TriviaAnswer.Number(
                        if (i in wrong) q.choices.first { it != q.value } else q.value,
                    )

                    is QuizQuestionUi.Duel -> TriviaAnswer.Duel(
                        if (i in wrong) DuelPick.entries.first { it != q.right } else q.right,
                    )
                },
            )
            next()
        }
    }

    /** 17 of 20, behind Sam's earlier 18: "2nd of 6 · your best 18/20". */
    private fun finished() = challengeQuiz(busy()).apply { play(20, wrong = setOf(2, 9, 15)) }

    /** 20 of 20: "New best! · 1st of 6". */
    private fun newBest() = challengeQuiz(busy()).apply { play(20, wrong = emptySet()) }

    /** Ended after 12. */
    private fun unfinished() = challengeQuiz(busy()).apply {
        play(12, wrong = setOf(4))
        stop()
    }

    @Composable
    private fun Quiz(vm: TriviaQuizViewModel) {
        val state by vm.uiState.collectAsState()
        TriviaQuizScreen(
            state,
            onAnswer = vm::answer,
            onNext = vm::next,
            onStop = vm::stop,
            onPractise = {},
            onPlayAgain = {},
            onDone = {},
            onBack = {},
        )
    }

    @Test fun challengeSummaryDark() = capture(ThemeMode.DARK) { Quiz(finished()) }

    @Test fun challengeSummaryLight() = capture(ThemeMode.LIGHT) { Quiz(finished()) }

    @Test fun challengeSummaryNewBestDark() = capture(ThemeMode.DARK) { Quiz(newBest()) }

    @Test fun challengeSummaryUnfinishedDark() = capture(ThemeMode.DARK) { Quiz(unfinished()) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun challengeSummaryW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Quiz(finished()) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun challengeSummaryLandscapeDark() = capture(ThemeMode.DARK) { Quiz(newBest()) }

    /** A Challenge question: the title says so. */
    @Test
    fun challengeQuestionDark() = capture(ThemeMode.DARK) {
        Quiz(challengeQuiz(emptyList()).apply { play(3, emptySet()) })
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
        const val DAY = 24L * 60 * 60 * 1000

        /** 3 Oct 2026, 12:00 UTC: the same date in every time zone the tests run in. */
        const val NOON = 1_791_028_800_000L
        const val NOW = NOON + DAY / 4
    }
}
