package dev.quinton.goa.feature.board.play.setup

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
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.feature.board.setup.LineupPicker
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import dev.quinton.goa.feature.board.setup.lineupSources
import javax.inject.Provider
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * New game against the computer (PRODUCT §9): the line-up as on New board (alternate picks, "Use a
 * draft or match"; only playable heroes can be seated), then who you control, the computer's level
 * (one for all, or per seat), Quick or Long, the map, Coach; the app flips the Tie Breaker coin and
 * [start] creates and saves the game.
 */
@HiltViewModel(assistedFactory = NewGameViewModel.Factory::class)
class NewGameViewModel internal constructor(
    route: NewGameRoute,
    private val savedState: SavedStateHandle,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    draftRepository: DraftRecordRepository,
    matchRepository: MatchRepository,
    settingsRepository: SettingsRepository,
    private val saves: GameSaveRepository,
    private val factory: suspend () -> GameFactory,
    private val clock: EditClock,
    private val random: Random,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted route: NewGameRoute,
        savedState: SavedStateHandle,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        draftRepository: DraftRecordRepository,
        matchRepository: MatchRepository,
        settingsRepository: SettingsRepository,
        saves: GameSaveRepository,
        factory: Provider<GameFactory>,
        clock: EditClock,
    ) : this(
        route,
        savedState,
        catalogRepository,
        mapRepository,
        draftRepository,
        matchRepository,
        settingsRepository,
        saves,
        { withContext(Dispatchers.Default) { factory.get() } },
        clock,
        Random.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(route: NewGameRoute): NewGameViewModel
    }

    private val form = MutableStateFlow(
        savedState.get<String>(KEY_FORM)?.let(NewGameForms::decode) ?: NewGameForm(
            lineup = NewBoardForm(
                red = route.red.distinct(),
                blue = route.blue.distinct().filterNot { it in route.red },
                filling = NewBoardViewModel.nextTeam(route.red.size, route.blue.size, Team.BLUE),
            ),
        ),
    )

    private val engine = flow {
        emit(
            try {
                factory()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            },
        )
    }

    private val inputs = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        settingsRepository.settings.catch { emit(AppSettings()) },
        engine,
        saves.current.catch { emit(StoredGame.None) },
    ) { catalog, maps, settings, engine, stored -> Inputs(catalog, maps, settings, engine, stored) }

    private val sources = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    private class Inputs(
        val catalog: Catalog,
        val maps: List<dev.quinton.goa.core.model.board.BoardMap>,
        val settings: AppSettings,
        val engine: GameFactory?,
        val stored: StoredGame,
    )

    val uiState: StateFlow<NewGameUiState> = combine(inputs, sources, form) { inputs, (drafts, matches), form ->
        val engine = inputs.engine
        val playableMaps = engine?.playableMaps.orEmpty().mapNotNull { id -> inputs.maps.firstOrNull { it.id == id } }
        val playable = engine?.playableHeroes.orEmpty()
        if (engine == null || playable.size < 2 || playableMaps.isEmpty()) return@combine NewGameUiState.ComingSoon
        val heroes = inputs.catalog.boardHeroes()
        val owned = inputs.settings.ownedProducts
        val all = heroes.values.sortedWith(compareBy<BoardHero> { it.id !in playable }.thenBy { it.name })
        val shown = if (form.lineup.showAll) all else all.filter { it.ownedIn(owned) || form.lineup.contains(it.id) }
        val controls = controls(form.lineup)
        val control = form.control?.takeIf { it in controls } ?: controls.firstOrNull()
        val map = (playableMaps.firstOrNull { it.id == form.mapId } ?: playableMaps.first()).let {
            MapOption(it.id, it.name, it.lanes.size, it.verified)
        }
        NewGameUiState.Ready(
            form = form,
            picker = LineupPicker(
                form = form.lineup,
                heroes = shown,
                heroById = heroes,
                hiddenHeroes = all.size - shown.size,
                ownedOnly = owned.isNotEmpty(),
                unavailable = heroes.keys - playable,
            ),
            sources = lineupSources(drafts, matches, heroes, MAX_SOURCES)
                .filter { source -> (source.lineup.red + source.lineup.blue).all { it in playable } },
            maps = playableMaps.map { MapOption(it.id, it.name, it.lanes.size, it.verified) },
            map = map,
            playable = playable.size,
            controls = controls,
            control = control,
            aiSeats = aiSeats(form, control, heroes),
            replacesGame = inputs.stored !is StoredGame.None,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewGameUiState.Loading)

    fun onAction(action: NewGameAction) {
        form.update { reduce(it, action) }
        savedState[KEY_FORM] = NewGameForms.encode(form.value)
    }

    private fun reduce(form: NewGameForm, action: NewGameAction): NewGameForm {
        val unavailable = (uiState.value as? NewGameUiState.Ready)?.picker?.unavailable.orEmpty()
        return NewGameRules.reduce(form, action, unavailable) { if (random.nextBoolean()) Team.RED else Team.BLUE }
    }

    /** The setup for the current form (seats in pick order per team), or null when it can't start. */
    fun setup(): GameSetup? {
        val ready = (uiState.value as? NewGameUiState.Ready)?.takeIf { it.canStart } ?: return null
        return NewGameRules.setup(ready.form, ready.control, ready.map?.id)
    }

    /** Creates the game and saves it as the game in progress; false when the engine refused it. */
    suspend fun start(): Boolean {
        val setup = setup() ?: return false
        val coach = (uiState.value as? NewGameUiState.Ready)?.form?.coach ?: true
        val saved = withContext(Dispatchers.Default) {
            try {
                factory().newGame(setup).save()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return false
        val now = clock.now()
        withContext(NonCancellable) {
            saves.save(GameInProgress(game = saved.copy(savedAt = now), coach = coach, startedAt = now))
        }
        return true
    }

    internal companion object {
        private const val KEY_FORM = "newGame.form"
        private const val MAX_SOURCES = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        fun controls(lineup: NewBoardForm): List<Control> = NewGameRules.controls(lineup)

        fun controls(control: Control, heroId: String, team: Team): Boolean = NewGameRules.controls(
            control,
            heroId,
            team,
        )

        fun aiSeats(form: NewGameForm, control: Control?, heroes: Map<String, BoardHero>): List<AiSeatUi> =
            NewGameRules.aiSeats(form, control, heroes)
    }
}
