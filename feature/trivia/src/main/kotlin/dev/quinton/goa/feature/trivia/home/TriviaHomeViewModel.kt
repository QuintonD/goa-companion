package dev.quinton.goa.feature.trivia.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.MasterySummary
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.feature.trivia.TriviaText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

/** A hero offered in the quiz's hero picker. */
@Immutable
data class HeroOption(val heroId: String, val name: String, val title: String?, val portrait: String?)

/** One hero's row in the progress list: every printed number on its deck cards. */
@Immutable
data class HeroProgressUi(
    val heroId: String,
    val name: String,
    val portrait: String?,
    val progress: MasterySummary,
    /** Numbers a quiz on this hero alone would ask with the current Numbers and Cards. */
    val quizNumbers: Int,
)

sealed interface TriviaHomeUiState {
    data object Loading : TriviaHomeUiState

    /** No hero data bundled (or it couldn't be read). */
    data object Unavailable : TriviaHomeUiState

    @Immutable
    data class Ready(
        /** Every number on the owned heroes' cards. */
        val overall: MasterySummary,
        val setup: TriviaConfig,
        /** Owned heroes (and any chosen hero outside them), by name. */
        val heroOptions: List<HeroOption>,
        /** True when Settings limits the heroes to owned products. */
        val ownedOnly: Boolean,
        /** The numbers the quiz as set up would ask. */
        val selection: MasterySummary,
        val heroes: List<HeroProgressUi>,
        /** Stored progress on any hero (what Reset forgets). */
        val stored: MasterySummary,
    ) : TriviaHomeUiState {
        val canStart: Boolean get() = selection.total > 0
        val initiativeSelected: Boolean get() = TriviaStat.INITIATIVE in setup.stats
        val selectedHeroNames: List<String>
            get() = setup.heroIds.mapNotNull { id -> heroOptions.firstOrNull { it.heroId == id }?.name }
    }
}

sealed interface TriviaHomeAction {
    data class ToggleStat(val stat: TriviaStat) : TriviaHomeAction

    data class ToggleGroup(val group: CardGroup) : TriviaHomeAction

    data class SetAnswerStyle(val style: AnswerStyle) : TriviaHomeAction

    data class SetLength(val length: Int) : TriviaHomeAction

    data class SetDuels(val enabled: Boolean) : TriviaHomeAction

    /** Picks or unpicks one hero; unpicking the last one goes back to all heroes. */
    data class ToggleHero(val heroId: String) : TriviaHomeAction

    data object AllHeroes : TriviaHomeAction
}

@HiltViewModel(assistedFactory = TriviaHomeViewModel.Factory::class)
class TriviaHomeViewModel @AssistedInject constructor(
    @Assisted heroId: String?,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(heroId: String?): TriviaHomeViewModel
    }

    /** The setup being edited; null until the last one has been read. */
    private val setup = MutableStateFlow<TriviaConfig?>(null)

    init {
        viewModelScope.launch {
            val last = runCatching { progressRepository.lastConfig.first() }.getOrNull() ?: TriviaConfig()
            setup.value = if (heroId != null) last.copy(heroIds = listOf(heroId)) else last
        }
    }

    private val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }

    val uiState: StateFlow<TriviaHomeUiState> = combine(
        catalog,
        settingsRepository.settings,
        progressRepository.progress,
        setup,
    ) { catalog, settings, progress, config ->
        if (config == null) return@combine TriviaHomeUiState.Loading
        if (catalog.heroes.isEmpty()) return@combine TriviaHomeUiState.Unavailable
        val owned = ownedHeroes(catalog, settings.ownedProducts)
        val chosen = config.heroIds.mapNotNull(catalog::hero)
        val scope = chosen.ifEmpty { owned }
        val options = (owned + chosen).distinctBy { it.id }.sortedBy { it.name }
            .map { HeroOption(it.id, it.name, it.title, it.portrait) }
        TriviaHomeUiState.Ready(
            overall = TriviaMastery.summary(owned.flatMap { TriviaFacts.of(it) }, progress),
            setup = config.copy(heroIds = chosen.map { it.id }),
            heroOptions = options,
            ownedOnly = settings.ownedProducts.isNotEmpty(),
            selection = TriviaMastery.summary(
                scope.flatMap { TriviaFacts.of(it, config.stats, config.groups) },
                progress,
            ),
            heroes = owned.sortedBy { it.name }.map { hero ->
                HeroProgressUi(
                    heroId = hero.id,
                    name = hero.name,
                    portrait = hero.portrait,
                    progress = TriviaMastery.summary(TriviaFacts.of(hero), progress),
                    quizNumbers = TriviaFacts.of(hero, config.stats, config.groups).size,
                )
            },
            stored = TriviaMastery.summary(catalog.heroes.flatMap { TriviaFacts.of(it) }, progress),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TriviaHomeUiState.Loading)

    fun onAction(action: TriviaHomeAction) {
        setup.update { current -> current?.let { reduce(it, action) } }
    }

    /** Saves the setup and returns the quiz to open, or null when it would ask nothing. */
    fun start(): TriviaQuizRoute? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        if (!ready.canStart) return null
        save(ready.setup)
        return TriviaQuizRoute(TriviaText.encode(ready.setup))
    }

    /** A quiz on [heroId] alone with the current Numbers, Cards and answer settings. */
    fun startHero(heroId: String): TriviaQuizRoute? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        val row = ready.heroes.firstOrNull { it.heroId == heroId } ?: return null
        if (row.quizNumbers == 0) return null
        save(ready.setup)
        return TriviaQuizRoute(TriviaText.encode(ready.setup.copy(heroIds = listOf(heroId))))
    }

    /** Forgets every learned and learning number. */
    fun resetProgress() {
        viewModelScope.launch { progressRepository.reset(null) }
    }

    private fun save(config: TriviaConfig) {
        viewModelScope.launch { progressRepository.saveConfig(config) }
    }

    private fun ownedHeroes(catalog: Catalog, ownedProducts: Set<String>): List<Hero> =
        if (ownedProducts.isEmpty()) catalog.heroes else catalog.heroes.filter { it.product in ownedProducts }

    companion object {
        internal fun reduce(config: TriviaConfig, action: TriviaHomeAction): TriviaConfig = when (action) {
            // The last Number or Cards chip can't be turned off: the quiz always asks something.
            is TriviaHomeAction.ToggleStat -> config.copy(stats = toggle(config.stats, action.stat))
            is TriviaHomeAction.ToggleGroup -> config.copy(groups = toggle(config.groups, action.group))
            is TriviaHomeAction.SetAnswerStyle -> config.copy(answerStyle = action.style)
            is TriviaHomeAction.SetLength -> config.copy(length = action.length)
            is TriviaHomeAction.SetDuels -> config.copy(duels = action.enabled)
            is TriviaHomeAction.ToggleHero -> config.copy(
                heroIds = if (action.heroId in config.heroIds) {
                    config.heroIds - action.heroId
                } else {
                    config.heroIds + action.heroId
                },
            )
            TriviaHomeAction.AllHeroes -> config.copy(heroIds = emptyList())
        }

        private fun <T> toggle(set: Set<T>, item: T): Set<T> = when {
            item !in set -> set + item
            set.size > 1 -> set - item
            else -> set
        }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
