package dev.quinton.goa.feature.board.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.feature.board.size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * New board: pick each team's heroes (or a draft's or match's in one tap) and a map, then Set up.
 * Picks alternate like a draft: after each one the next goes to the team with fewer heroes (a tie
 * goes to the other team), so "Brogan, Tigerclaw, Arien, Wasp" is Brogan and Arien against Tigerclaw
 * and Wasp; tapping Red or Blue picks for that team next.
 */
@HiltViewModel(assistedFactory = NewBoardViewModel.Factory::class)
class NewBoardViewModel @AssistedInject constructor(
    @Assisted route: NewBoardRoute,
    private val savedState: SavedStateHandle,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    draftRepository: DraftRecordRepository,
    matchRepository: MatchRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: NewBoardRoute): NewBoardViewModel
    }

    private val form = MutableStateFlow(
        NewBoardForm(
            red = savedState.get<List<String>>(KEY_RED) ?: route.red.distinct().take(NewBoardUiState.MAX_PER_TEAM),
            blue = savedState.get<List<String>>(KEY_BLUE)
                ?: route.blue.distinct().filterNot { it in route.red }.take(NewBoardUiState.MAX_PER_TEAM),
            filling = savedState.get<String>(KEY_FILLING)?.let { runCatching { Team.valueOf(it) }.getOrNull() }
                ?: nextTeam(route.red.size, route.blue.size, Team.BLUE),
            mapId = savedState.get<String>(KEY_MAP) ?: route.mapId,
            showAll = savedState.get<Boolean>(KEY_ALL) ?: false,
        ),
    )

    private val sources = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    private val inputs = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        settingsRepository.settings.catch { emit(AppSettings()) },
    ) { catalog, maps, settings -> Triple(catalog.boardHeroes(), maps, settings.ownedProducts) }

    val uiState: StateFlow<NewBoardUiState> = combine(inputs, sources, form) {
            (heroes, maps, owned),
            (drafts, matches),
            form,
        ->
        val all = heroes.values.sortedBy { it.name }
        // Heroes already picked always show, owned or not (a draft may have used a friend's copy).
        val shown = if (form.showAll) all else all.filter { it.ownedIn(owned) || form.contains(it.id) }
        NewBoardUiState.Ready(
            form = form,
            heroes = shown,
            heroById = heroes,
            hiddenHeroes = all.size - shown.size,
            ownedOnly = owned.isNotEmpty(),
            maps = maps.map { it.option() },
            map = defaultMap(maps, form.lineup.size, form.mapId)?.option(),
            sources = lineupSources(drafts, matches, heroes, MAX_SOURCES),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewBoardUiState.Loading)

    fun onAction(action: NewBoardAction) {
        form.update { reduce(it, action) }
        val next = form.value
        savedState[KEY_RED] = next.red
        savedState[KEY_BLUE] = next.blue
        savedState[KEY_FILLING] = next.filling.name
        savedState[KEY_ALL] = next.showAll
        if (next.mapId != null) savedState[KEY_MAP] = next.mapId
    }

    /** The board to open for this line-up and map, or null when there is nothing to set up yet. */
    fun setUp(): BoardRoute? {
        val ready = uiState.value as? NewBoardUiState.Ready ?: return null
        if (!ready.canSetUp) return null
        return BoardRoute(red = ready.form.red, blue = ready.form.blue, mapId = ready.map?.id)
    }

    /** The board for [source] (the Latest card): set up in one tap, on the default map. */
    fun setUp(source: LineupSource): BoardRoute = BoardRoute(red = source.lineup.red, blue = source.lineup.blue)

    private fun BoardMap.option() = MapOption(id, name, lanes.size, verified)

    internal companion object {
        private const val KEY_RED = "newBoard.red"
        private const val KEY_BLUE = "newBoard.blue"
        private const val KEY_FILLING = "newBoard.filling"
        private const val KEY_MAP = "newBoard.map"
        private const val KEY_ALL = "newBoard.showAll"
        private const val MAX_SOURCES = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Who picks next after [last] picked (NewBoardRules, shared with the web app). */
        fun nextTeam(red: Int, blue: Int, last: Team): Team = NewBoardRules.nextTeam(red, blue, last)

        fun reduce(form: NewBoardForm, action: NewBoardAction): NewBoardForm = NewBoardRules.reduce(form, action)
    }
}
