package dev.quinton.goa.feature.rules

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.domain.rules.RulesSearch
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.feature.rules.data.RulesReferenceSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Loads the bundled reference once; emits null if it can't be read (a build defect, see tests). */
private fun RulesReferenceSource.flow(): Flow<RulesReference?> = flow<RulesReference?> { emit(load()) }
    .catch { emit(null) }

// ---- Topic list -------------------------------------------------------------------------------

@Immutable
data class TopicItem(val id: String, val title: String, val pages: List<Int>, val summary: String?)

sealed interface RulesUiState {
    data object Loading : RulesUiState

    data object Unavailable : RulesUiState

    @Immutable
    data class Ready(val query: String, val topics: List<TopicItem>, val totalTopics: Int) : RulesUiState
}

@HiltViewModel
class RulesViewModel @Inject constructor(source: RulesReferenceSource) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<RulesUiState> = combine(source.flow(), query) { reference, q ->
        if (reference == null) {
            RulesUiState.Unavailable
        } else {
            RulesUiState.Ready(
                query = q,
                topics = RulesSearch.filter(reference.topics, q).map(::item),
                totalTopics = reference.topics.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesUiState.Loading)

    fun setQuery(value: String) {
        query.value = value
    }

    private fun item(topic: RulesTopic) = TopicItem(topic.id, topic.title, topic.pages.sorted(), topic.summary)
}

// ---- Topic detail -----------------------------------------------------------------------------

sealed interface RulesTopicUiState {
    data object Loading : RulesTopicUiState

    data object NotFound : RulesTopicUiState

    @Immutable
    data class Ready(val topic: RulesTopic) : RulesTopicUiState
}

@HiltViewModel(assistedFactory = RulesTopicViewModel.Factory::class)
class RulesTopicViewModel @AssistedInject constructor(
    @Assisted topicId: String,
    source: RulesReferenceSource,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(topicId: String): RulesTopicViewModel
    }

    val uiState: StateFlow<RulesTopicUiState> = source.flow().map { reference ->
        reference?.topics?.firstOrNull { it.id == topicId }?.let { RulesTopicUiState.Ready(it) }
            ?: RulesTopicUiState.NotFound
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesTopicUiState.Loading)
}

// ---- Level & defeat ladder --------------------------------------------------------------------

/** A catalog card whose printed text changes a defeat's cost or reward. [text] is the card's markup. */
@Immutable
data class ExceptionCardUi(
    val heroName: String,
    val cardName: String,
    /** "Tier III · Red", "Handicap", "Spellbook" … */
    val label: String,
    val text: String,
)

sealed interface LadderUiState {
    data object Loading : LadderUiState

    data object Unavailable : LadderUiState

    @Immutable
    data class Ready(
        val ladder: LevelLadder,
        /** Level picked under "Hero defeated", or null. */
        val selectedLevel: Int?,
        val exceptions: List<ExceptionCardUi>,
        /** True when [exceptions] cover the heroes of the current match, false = every hero. */
        val matchHeroesOnly: Boolean,
    ) : LadderUiState
}

@HiltViewModel(assistedFactory = LevelLadderViewModel.Factory::class)
class LevelLadderViewModel @AssistedInject constructor(
    @Assisted heroIds: List<String>,
    source: RulesReferenceSource,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(heroIds: List<String>): LevelLadderViewModel
    }

    private val selected = MutableStateFlow<Int?>(null)

    private val catalog: Flow<Catalog> = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }

    val uiState: StateFlow<LadderUiState> = combine(source.flow(), catalog, selected) { reference, catalog, level ->
        if (reference == null) return@combine LadderUiState.Unavailable
        val matchHeroes = heroIds.distinct().mapNotNull(catalog::hero)
        val heroes = matchHeroes.ifEmpty { catalog.heroes }
        LadderUiState.Ready(
            ladder = reference.ladder,
            selectedLevel = level,
            exceptions = DefeatExceptions.find(heroes).map { (hero, card) ->
                ExceptionCardUi(hero.name, card.name, RulesText.cardLabel(hero, card), card.text)
            },
            matchHeroesOnly = matchHeroes.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LadderUiState.Loading)

    /** Picks the defeated hero's level; picking it again clears the selection. */
    fun selectLevel(level: Int) {
        selected.value = if (selected.value == level) null else level
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
