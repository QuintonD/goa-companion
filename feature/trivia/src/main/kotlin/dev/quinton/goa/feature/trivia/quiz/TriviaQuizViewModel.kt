package dev.quinton.goa.feature.trivia.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.core.presentation.trivia.TriviaRound
import dev.quinton.goa.core.presentation.trivia.TriviaText
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = TriviaQuizViewModel.Factory::class)
class TriviaQuizViewModel internal constructor(
    private val route: TriviaQuizRoute,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
    private val clock: EditClock,
    /** Seeded in tests so a round is reproducible. */
    private val random: Random,
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: TriviaQuizRoute,
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        progressRepository: TriviaProgressRepository,
        clock: EditClock,
    ) : this(route, catalogRepository, settingsRepository, progressRepository, clock, Random.Default)

    @AssistedFactory
    interface Factory {
        fun create(route: TriviaQuizRoute): TriviaQuizViewModel
    }

    private val config: TriviaConfig = TriviaText.decode(route.config)

    private val state = MutableStateFlow<TriviaQuizUiState>(TriviaQuizUiState.Loading)
    val uiState: StateFlow<TriviaQuizUiState> = state.asStateFlow()

    private var round: TriviaRound? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }.first()
        val settings = runCatching { settingsRepository.settings.first() }.getOrDefault(AppSettings())
        val progress = runCatching { progressRepository.progress.first() }.getOrDefault(emptyMap())
        val started = TriviaRound.start(
            catalog = catalog,
            config = config,
            factKeys = route.factKeys,
            ownedProducts = settings.ownedProducts,
            progress = progress,
            hapticsEnabled = settings.hapticsEnabled,
            random = random,
        )
        round = started
        state.value = started.state
    }

    fun answer(answer: TriviaAnswer) {
        val current = round ?: return
        val updates = current.answer(answer, clock.now()) ?: return
        // Stored right away: leaving mid-round keeps what was learned. NonCancellable, so clearing the
        // view model straight after an answer (Back, End → Done) can't drop the write.
        if (updates.isNotEmpty()) {
            viewModelScope.launch { withContext(NonCancellable) { progressRepository.record(updates) } }
        }
        state.value = current.state
    }

    fun next() {
        val current = round ?: return
        current.next()
        state.value = current.state
    }

    /** Ends the round now and shows the summary. */
    fun stop() {
        val current = round ?: return
        current.stop()
        state.value = current.state
    }

    /** A new round with the same setup. */
    fun playAgain() {
        val current = round ?: return
        current.playAgain()
        state.value = current.state
    }

    /**
     * The round of only the numbers missed in this one: no duels, each number about twice
     * ([TriviaConfig.forPractice]); null when nothing was missed.
     */
    fun practiseRoute(): TriviaQuizRoute? {
        val practice = round?.practice() ?: return null
        return TriviaQuizRoute(TriviaText.encode(practice.config), practice.factKeys)
    }

    /** True when [other] would replay this very round (same setup and facts). */
    fun isSameRound(other: TriviaQuizRoute): Boolean = other == route
}
