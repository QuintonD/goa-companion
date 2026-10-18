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
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.lineup
import dev.quinton.goa.feature.board.size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Where a line-up offered by "Use a draft or match" comes from. */
enum class LineupSourceKind(val label: String) {
    DRAFT("Finished draft"),
    PENDING("Pending match"),
    LOGGED("Logged match"),
}

/** A line-up to fill both teams with in one tap. */
@Immutable
data class LineupSource(
    val key: String,
    val kind: LineupSourceKind,
    val at: Long,
    val lineup: BoardLineup,
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
)

/** A bundled map the board can use; [verified] false = badged Unverified. */
@Immutable
data class MapOption(val id: String, val name: String, val lanes: Int, val verified: Boolean)

/** The line-up being chosen. Kept in the SavedStateHandle, so it survives process death. */
@Immutable
data class NewBoardForm(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    /** The team a tapped hero joins. */
    val filling: Team = Team.RED,
    /** null = the default map for this many heroes. */
    val mapId: String? = null,
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
        /** Every catalog hero, by name. */
        val heroes: List<BoardHero>,
        val heroById: Map<String, BoardHero>,
        val maps: List<MapOption>,
        /** The map "Set up" uses (the chosen one, or the default for this many heroes). */
        val map: MapOption?,
        /** Finished drafts, pending and logged matches, newest first. */
        val sources: List<LineupSource>,
    ) : NewBoardUiState {
        fun team(team: Team): List<BoardHero> = form.team(team).map(heroById::hero)

        val canSetUp: Boolean get() = map != null && form.lineup.size > 0

        val full: Boolean get() = form.red.size >= MAX_PER_TEAM && form.blue.size >= MAX_PER_TEAM
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

    data object Clear : NewBoardAction
}

/** New board: pick each team's heroes (or a draft's or match's in one tap) and a map, then Set up. */
@HiltViewModel(assistedFactory = NewBoardViewModel.Factory::class)
class NewBoardViewModel @AssistedInject constructor(
    @Assisted route: NewBoardRoute,
    private val savedState: SavedStateHandle,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    draftRepository: DraftRecordRepository,
    matchRepository: MatchRepository,
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
            filling = savedState.get<String>(KEY_FILLING)?.let { runCatching { Team.valueOf(it) }.getOrNull() } ?: Team.RED,
            mapId = savedState.get<String>(KEY_MAP) ?: route.mapId,
        ),
    )

    private val sources = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    val uiState: StateFlow<NewBoardUiState> = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        sources,
        form,
    ) { catalog, maps, (drafts, matches), form ->
        val heroes = catalog.boardHeroes()
        // A draft whose match is in the log shows the same heroes: offer the match alone, in the
        // draft's pick order (the opening places heroes in pick order, p.20).
        val inLog = matches.mapTo(HashSet()) { it.id }
        val draftOf = drafts.filter { it.matchId != null }.associateBy { it.matchId }
        val draftSources = drafts.filterNot { it.matchId != null && it.matchId in inLog }
            .sortedByDescending { it.createdAt }
            .take(MAX_SOURCES)
            .map { draft -> source("draft:${draft.id}", LineupSourceKind.DRAFT, draft.createdAt, draft.lineup(), heroes) }
        val matchSources = matches.filter { it.status != MatchStatus.ABANDONED }
            .sortedByDescending { it.playedAt }
            .take(MAX_SOURCES)
            .map { match ->
                val kind = if (match.status == MatchStatus.PENDING) LineupSourceKind.PENDING else LineupSourceKind.LOGGED
                val seats = match.lineup()
                val picks = draftOf[match.id]?.lineup()
                val lineup = if (picks != null && picks.red.toSet() == seats.red.toSet() && picks.blue.toSet() == seats.blue.toSet()) picks else seats
                source("match:${match.id}", kind, match.playedAt, lineup, heroes)
            }
        val all = (matchSources + draftSources).filter { it.lineup.size > 0 }.sortedByDescending { it.at }
        val options = maps.map { it.option() }
        NewBoardUiState.Ready(
            form = form,
            heroes = heroes.values.sortedBy { it.name },
            heroById = heroes,
            maps = options,
            map = defaultMap(maps, form.lineup.size, form.mapId)?.option(),
            sources = all,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewBoardUiState.Loading)

    fun onAction(action: NewBoardAction) {
        form.update { reduce(it, action) }
        val next = form.value
        savedState[KEY_RED] = next.red
        savedState[KEY_BLUE] = next.blue
        savedState[KEY_FILLING] = next.filling.name
        if (next.mapId != null) savedState[KEY_MAP] = next.mapId
    }

    /** The board to open for this line-up and map, or null when there is nothing to set up yet. */
    fun setUp(): BoardRoute? {
        val ready = uiState.value as? NewBoardUiState.Ready ?: return null
        if (!ready.canSetUp) return null
        return BoardRoute(red = ready.form.red, blue = ready.form.blue, mapId = ready.map?.id)
    }

    private fun source(
        key: String,
        kind: LineupSourceKind,
        at: Long,
        lineup: BoardLineup,
        heroes: Map<String, BoardHero>,
    ) = LineupSource(key, kind, at, lineup, lineup.red.map(heroes::hero), lineup.blue.map(heroes::hero))

    private fun BoardMap.option() = MapOption(id, name, lanes.size, verified)

    internal companion object {
        private const val KEY_RED = "newBoard.red"
        private const val KEY_BLUE = "newBoard.blue"
        private const val KEY_FILLING = "newBoard.filling"
        private const val KEY_MAP = "newBoard.map"
        private const val MAX_SOURCES = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        fun reduce(form: NewBoardForm, action: NewBoardAction): NewBoardForm = when (action) {
            is NewBoardAction.Tap -> when {
                form.contains(action.heroId) -> form.without(action.heroId)
                form.team(form.filling).size < NewBoardUiState.MAX_PER_TEAM -> form.with(form.filling, action.heroId)
                form.team(form.filling.opponent).size < NewBoardUiState.MAX_PER_TEAM ->
                    form.with(form.filling.opponent, action.heroId).copy(filling = form.filling.opponent)
                else -> form
            }

            is NewBoardAction.Fill -> form.copy(filling = action.team)

            is NewBoardAction.Remove -> form.without(action.heroId)

            is NewBoardAction.Reorder -> {
                val team = if (action.heroId in form.red) Team.RED else Team.BLUE
                val list = form.team(team).toMutableList()
                val from = list.indexOf(action.heroId)
                val to = from + action.by
                if (from < 0 || to !in list.indices) {
                    form
                } else {
                    list.add(to, list.removeAt(from))
                    form.withTeam(team, list)
                }
            }

            is NewBoardAction.SwitchTeam -> {
                val team = when (action.heroId) {
                    in form.red -> Team.RED
                    in form.blue -> Team.BLUE
                    else -> null
                }
                if (team == null || form.team(team.opponent).size >= NewBoardUiState.MAX_PER_TEAM) {
                    form
                } else {
                    form.without(action.heroId).with(team.opponent, action.heroId)
                }
            }

            is NewBoardAction.Use -> form.copy(
                red = action.source.lineup.red.take(NewBoardUiState.MAX_PER_TEAM),
                blue = action.source.lineup.blue.filterNot { it in action.source.lineup.red }.take(NewBoardUiState.MAX_PER_TEAM),
            )

            is NewBoardAction.ChooseMap -> form.copy(mapId = action.mapId)

            NewBoardAction.Clear -> form.copy(red = emptyList(), blue = emptyList(), filling = Team.RED)
        }

        private fun NewBoardForm.with(team: Team, heroId: String) = withTeam(team, team(team) + heroId)

        private fun NewBoardForm.withTeam(team: Team, heroes: List<String>) =
            if (team == Team.RED) copy(red = heroes) else copy(blue = heroes)

        private fun NewBoardForm.without(heroId: String) = copy(red = red - heroId, blue = blue - heroId)
    }
}
