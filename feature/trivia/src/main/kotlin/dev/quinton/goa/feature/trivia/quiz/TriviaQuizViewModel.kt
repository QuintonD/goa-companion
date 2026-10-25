package dev.quinton.goa.feature.trivia.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.trivia.QuizTicker
import dev.quinton.goa.core.data.trivia.TriviaQuizModel
import dev.quinton.goa.core.data.trivia.TriviaQuizUiState
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.feature.trivia.home.toRequest
import dev.quinton.goa.feature.trivia.home.toRoute
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * A quiz round (ADR-028) or the Challenge (ADR-032): the shared [TriviaQuizModel] (also the web
 * app's) in this screen's view-model scope.
 */
@Suppress("LongParameterList")
@HiltViewModel(assistedFactory = TriviaQuizViewModel.Factory::class)
class TriviaQuizViewModel internal constructor(
    private val route: TriviaQuizRoute,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    progressRepository: TriviaProgressRepository,
    playerRepository: PlayerRepository,
    groupRepository: GroupRepository,
    roundRepository: TriviaRoundRepository,
    clock: EditClock,
    /** Seeded in tests so a round is reproducible. */
    random: Random,
    ticker: QuizTicker = QuizTicker.System,
    newId: () -> String = { UUID.randomUUID().toString() },
    /** Works out the summary's place off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: TriviaQuizRoute,
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        progressRepository: TriviaProgressRepository,
        playerRepository: PlayerRepository,
        groupRepository: GroupRepository,
        roundRepository: TriviaRoundRepository,
        clock: EditClock,
    ) : this(
        route,
        catalogRepository,
        settingsRepository,
        progressRepository,
        playerRepository,
        groupRepository,
        roundRepository,
        clock,
        Random.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(route: TriviaQuizRoute): TriviaQuizViewModel
    }

    private val model = TriviaQuizModel(
        viewModelScope,
        route.toRequest(),
        catalogRepository,
        settingsRepository,
        progressRepository,
        playerRepository,
        groupRepository,
        roundRepository,
        clock,
        random,
        ticker,
        newId,
        computeDispatcher,
    )

    val uiState: StateFlow<TriviaQuizUiState> = model.uiState

    fun answer(answer: TriviaAnswer) = model.answer(answer)

    fun next() = model.next()

    /** Ends the round now and shows the summary. */
    fun stop() = model.stop()

    /** A new round with the same setup (a Challenge: a new draw). */
    fun playAgain() = model.playAgain()

    /** The screen is in view or not: answer time only runs while a question is in view. */
    fun onVisible(visible: Boolean) = model.onVisible(visible)

    /** The round of only the numbers missed in this one; null when nothing was missed. */
    fun practiseRoute(): TriviaQuizRoute? = model.practiseRoute()?.toRoute()

    /** True when [other] would replay this very round (same setup and facts). */
    fun isSameRound(other: TriviaQuizRoute): Boolean = other == route
}
