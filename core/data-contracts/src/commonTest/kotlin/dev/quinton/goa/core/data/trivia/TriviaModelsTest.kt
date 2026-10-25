package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.FakeTriviaProgressRepository
import dev.quinton.goa.core.testing.FakeTriviaRoundRepository
import dev.quinton.goa.core.testing.TestCatalog
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The trivia screens' shared logic (the phone's ViewModels wrap it, the web app runs it as is),
 * checked on the JVM and on Node: the feature:trivia suites cover the details on the phone.
 */
class TriviaModelsTest {
    @Test
    fun timesReadAsOnThePhone() {
        assertEquals("1:42.3", TriviaText.answerTime(102_300))
        assertEquals("0:05.0", TriviaText.answerTime(4_960))
        assertEquals("1 minute 42.3 seconds", TriviaText.spokenAnswerTime(102_300))
        assertEquals("4:05", TriviaText.roundTime(245_999))
        assertEquals("1:02:03", TriviaText.roundTime(3_723_000))
        assertEquals("1 hour 2 minutes 3 seconds", TriviaText.spokenRoundTime(3_723_000))
        assertEquals("0 seconds", TriviaText.spokenRoundTime(0))
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 22).map(TriviaText::ordinal),
        )
        assertEquals("Joint 2nd", TriviaText.place(2, tied = true))
    }

    @Test
    fun aNewPlayerGetsTheFirstFreeColourAndTheNextCrest() {
        val taken = Player("a", "Ann", PlayerPalette.all[0].argb, avatarId = 0, createdAt = 1)
        assertEquals(PlayerPalette.all[1].argb to 1, PlayerPalette.forNew(listOf(taken)))
        assertEquals(PlayerPalette.all[0].argb to 0, PlayerPalette.forNew(emptyList()))
    }

    @Test
    fun resetKeysComeFromTheHeroesCards() {
        val hero = TestCatalog.heroes.first()
        val keys = TriviaFacts.keysOf(TestCatalog.catalog, setOf(hero.id, "no-such-hero"))
        assertEquals(hero.cards.size * 6, keys.size)
        assertTrue(TriviaFacts.of(hero).all { it.key in keys })
    }

    @Test
    fun theHomeOpensOnOneHeroAndStartsItsQuiz() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val progress = FakeTriviaProgressRepository()
        val model = TriviaHomeModel(
            scope,
            heroId = "test-hero-3",
            catalogRepository = FakeCatalogRepository(),
            settingsRepository = FakeSettingsRepository(),
            progressRepository = progress,
            playerRepository = FakePlayerRepository(),
            groupRepository = FakeGroupRepository(),
            computeDispatcher = StandardTestDispatcher(testScheduler),
        )
        val ready = model.uiState.first { it is TriviaHomeUiState.Ready } as TriviaHomeUiState.Ready
        assertEquals(listOf("test-hero-3"), ready.setup.heroIds)
        assertTrue(ready.playingAs.isGuest)

        val request = assertNotNull(model.start())
        assertEquals(listOf("test-hero-3"), TriviaText.decode(request.config).heroIds)
        scope.advanceUntilIdle()
        assertEquals(listOf("test-hero-3"), progress.configState.value?.heroIds)
        scope.coroutineContext.cancelChildren()
    }

    @Test
    fun aRoundPlayedAsAPlayerIsRecordedForTheLeaderboard() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val sam = Player("sam", "Sam", PlayerPalette.all[1].argb, avatarId = 1, createdAt = 1)
        val progress = FakeTriviaProgressRepository(playingAs = "sam")
        val rounds = FakeTriviaRoundRepository()
        val config = TriviaConfig(heroIds = listOf("test-hero-1"), length = 5, duels = false)
        val model = TriviaQuizModel(
            scope,
            TriviaQuizRequest(TriviaText.encode(config)),
            FakeCatalogRepository(),
            FakeSettingsRepository(),
            progress,
            FakePlayerRepository(listOf(sam)),
            FakeGroupRepository(),
            rounds,
            EditClock { NOW },
            Random(7),
            ticker = { 0 },
            newId = { "round-1" },
            computeDispatcher = StandardTestDispatcher(testScheduler),
        )
        scope.advanceUntilIdle()
        repeat(config.length) {
            val playing = assertIs<TriviaQuizUiState.Playing>(model.uiState.value)
            assertEquals("Sam", playing.playerName)
            val question = assertIs<QuizQuestionUi.Number>(playing.question)
            model.answer(TriviaAnswer.Number(question.value))
            scope.advanceUntilIdle()
            model.next()
        }
        scope.advanceUntilIdle()
        val summary = assertIs<TriviaQuizUiState.Summary>(model.uiState.value)
        assertEquals(5, summary.correct)
        assertEquals("round-1", rounds.state.value.single().id)
        assertEquals("sam", rounds.state.value.single().playerId)
        assertTrue(progress.state.value.isNotEmpty())
        scope.coroutineContext.cancelChildren()
    }

    @Test
    fun theLeaderboardRanksTheRecordedRounds() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val sam = Player("sam", "Sam", PlayerPalette.all[1].argb, avatarId = 1, createdAt = 1)
        val rounds = FakeTriviaRoundRepository()
        val quizScope = TestScope(StandardTestDispatcher(testScheduler))
        val quiz = TriviaQuizModel(
            quizScope,
            TriviaQuizRequest(
                TriviaText.encode(TriviaConfig(heroIds = listOf("test-hero-2"), length = 5, duels = false)),
            ),
            FakeCatalogRepository(),
            FakeSettingsRepository(),
            FakeTriviaProgressRepository(playingAs = "sam"),
            FakePlayerRepository(listOf(sam)),
            FakeGroupRepository(),
            rounds,
            EditClock { NOW },
            Random(3),
            ticker = { 0 },
            computeDispatcher = StandardTestDispatcher(testScheduler),
        )
        quizScope.advanceUntilIdle()
        repeat(5) {
            val question = (quiz.uiState.value as TriviaQuizUiState.Playing).question as QuizQuestionUi.Number
            quiz.answer(TriviaAnswer.Number(question.value))
            quiz.next()
        }
        quizScope.advanceUntilIdle()

        val board = TriviaLeaderboardModel(
            scope,
            rounds,
            FakePlayerRepository(listOf(sam)),
            FakeGroupRepository(),
            EditClock { NOW },
            StandardTestDispatcher(testScheduler),
            initialTab = LeaderboardTab.PRACTICE,
        )
        val ready = board.uiState.first { it is TriviaLeaderboardUiState.Ready } as TriviaLeaderboardUiState.Ready
        val row = ready.rows.single()
        assertEquals("sam", row.player.id)
        assertEquals("5", row.main)
        assertEquals("right", row.mainCaption)
        scope.coroutineContext.cancelChildren()
        quizScope.coroutineContext.cancelChildren()
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
