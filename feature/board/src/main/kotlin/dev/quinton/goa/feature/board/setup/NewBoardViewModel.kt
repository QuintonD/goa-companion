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

/** A bundled map the board can use; [verified] false = badged Unverified. */
@Immutable
data class MapOption(val id: String, val name: String, val lanes: Int, val verified: Boolean)

/** The line-up being chosen. Kept in the SavedStateHandle, so it survives process death. */
@Immutable
data class NewBoardForm(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    /** The team the next tapped hero joins. */
    val filling: Team = Team.RED,
    /** null = the default map for this many heroes. */
    val mapId: String? = null,
    /** Show every hero, not only the owned products' (Settings). */
    val showAll: Boolean = false,
) {
    fun team(team: Team): List<String> = if (team == Team.RED) red else blue

    val lineup: BoardLineup get() = BoardLineup(red, blue)

    fun contains(heroId: String): Boolean = heroId in red || heroId in blue
}

sealed interface NewBoardUiState {
    data object Loading : NewBoardUiState

    @Immutable
    data class Ready(
        val form: NewBoardForm,
        /** The heroes to pick from, by name: the owned products' unless [NewBoardForm.showAll]. */
        val heroes: List<BoardHero>,
        val heroById: Map<String, BoardHero>,
        /** Heroes the owned-products filter hides right now (0 = none hidden, or showing all). */
        val hiddenHeroes: Int,
        /** The owned-products filter is on (Settings names some products). */
        val ownedOnly: Boolean,
        val maps: List<MapOption>,
        /** The map "Set up" uses (the chosen one, or the default for this many heroes). */
        val map: MapOption?,
        /** Finished drafts, pending and logged matches, newest first. */
        val sources: List<LineupSource>,
    ) : NewBoardUiState {
        fun team(team: Team): List<BoardHero> = form.team(team).map(heroById::hero)

        val canSetUp: Boolean get() = map != null && form.lineup.size > 0

        val full: Boolean get() = form.red.size >= MAX_PER_TEAM && form.blue.size >= MAX_PER_TEAM

        /** The newest draft or match: "Set up" on it is one tap. */
        val latest: LineupSource? get() = sources.firstOrNull()
    }

    companion object {
        /** Five heroes a side at most (p.4). */
        const val MAX_PER_TEAM = 5
    }
}

sealed interface NewBoardAction {
    /** Adds the hero to the team being filled (or takes it off the board when it's already on). */
    data class Tap(val heroId: String) : NewBoardAction

    data class Fill(val team: Team) : NewBoardAction

    data class Remove(val heroId: String) : NewBoardAction

    /** Moves a hero one place earlier ([by] = -1) or later (+1) in its team's pick order. */
    data class Reorder(val heroId: String, val by: Int) : NewBoardAction

    data class SwitchTeam(val heroId: String) : NewBoardAction

    data class Use(val source: LineupSource) : NewBoardAction

    data class ChooseMap(val mapId: String) : NewBoardAction

    data class ShowAll(val all: Boolean) : NewBoardAction

    data object Clear : NewBoardAction
}

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

    val uiState: StateFlow<NewBoardUiState> = combine(inputs, sources, form) { (heroes, maps, owned), (drafts, matches), form ->
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

        /**
         * Who picks next after [last] picked: the team with fewer heroes; on a tie, the other team
         * (alternating like a draft); never a full team.
         */
        fun nextTeam(red: Int, blue: Int, last: Team): Team {
            val next = when {
                red < blue -> Team.RED
                blue < red -> Team.BLUE
                else -> last.opponent
            }
            val size = if (next == Team.RED) red else blue
            return if (size >= NewBoardUiState.MAX_PER_TEAM) next.opponent else next
        }

        fun reduce(form: NewBoardForm, action: NewBoardAction): NewBoardForm = when (action) {
            is NewBoardAction.Tap -> when {
                form.contains(action.heroId) -> form.without(action.heroId)

                form.team(form.filling).size < NewBoardUiState.MAX_PER_TEAM -> form.pick(form.filling, action.heroId)

                form.team(form.filling.opponent).size < NewBoardUiState.MAX_PER_TEAM ->
                    form.pick(form.filling.opponent, action.heroId)

                else -> form
            }

            is NewBoardAction.Fill -> form.copy(filling = action.team)

            is NewBoardAction.Remove -> form.without(action.heroId)

            is NewBoardAction.Reorder -> form.reorder(action.heroId, action.by)

            is NewBoardAction.SwitchTeam -> form.switchTeam(action.heroId)

            is NewBoardAction.Use -> {
                val red = action.source.lineup.red.take(NewBoardUiState.MAX_PER_TEAM)
                val blue = action.source.lineup.blue.filterNot { it in red }.take(NewBoardUiState.MAX_PER_TEAM)
                form.copy(red = red, blue = blue, filling = nextTeam(red.size, blue.size, Team.BLUE))
            }

            is NewBoardAction.ChooseMap -> form.copy(mapId = action.mapId)

            is NewBoardAction.ShowAll -> form.copy(showAll = action.all)

            NewBoardAction.Clear -> form.copy(red = emptyList(), blue = emptyList(), filling = Team.RED)
        }

        /** Adds [heroId] to [team], then hands the next pick on ([nextTeam]). */
        private fun NewBoardForm.pick(team: Team, heroId: String): NewBoardForm {
            val added = with(team, heroId)
            return added.copy(filling = nextTeam(added.red.size, added.blue.size, team))
        }

        private fun NewBoardForm.reorder(heroId: String, by: Int): NewBoardForm {
            val team = if (heroId in red) Team.RED else Team.BLUE
            val list = team(team).toMutableList()
            val from = list.indexOf(heroId)
            val to = from + by
            if (from < 0 || to !in list.indices) return this
            list.add(to, list.removeAt(from))
            return withTeam(team, list)
        }

        private fun NewBoardForm.switchTeam(heroId: String): NewBoardForm {
            val team = when (heroId) {
                in red -> Team.RED
                in blue -> Team.BLUE
                else -> return this
            }
            if (team(team.opponent).size >= NewBoardUiState.MAX_PER_TEAM) return this
            return without(heroId).with(team.opponent, heroId)
        }

        private fun NewBoardForm.with(team: Team, heroId: String) = withTeam(team, team(team) + heroId)

        private fun NewBoardForm.withTeam(team: Team, heroes: List<String>) =
            if (team == Team.RED) copy(red = heroes) else copy(blue = heroes)

        private fun NewBoardForm.without(heroId: String) = copy(red = red - heroId, blue = blue - heroId)
    }
}
