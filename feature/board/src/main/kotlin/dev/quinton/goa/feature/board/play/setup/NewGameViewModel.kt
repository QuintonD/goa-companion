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
import dev.quinton.goa.core.game.AiStrength
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who the person plays: one hero, or every hero of a team (the rest are the computer). */
@Immutable
sealed interface Control {
    data class Hero(val heroId: String) : Control

    data class WholeTeam(val team: Team) : Control
}

/** New game in two steps: the line-up, then the options. */
enum class NewGameStep { LINEUP, OPTIONS }

/** The game being set up. Kept in the SavedStateHandle, so it survives process death. */
@Immutable
data class NewGameForm(
    val lineup: NewBoardForm = NewBoardForm(),
    val step: NewGameStep = NewGameStep.LINEUP,
    /** null = the default: your whole first team (one hero in 1v1). */
    val control: Control? = null,
    /** The computer's strength, 1..10 (ADR-039). */
    val strength: Int = AiStrength.DEFAULT,
    /** Advanced: a strength per computer seat, overriding [strength]. */
    val seatStrengths: Map<String, Int> = emptyMap(),
    val advanced: Boolean = false,
    val length: GameLength = GameLength.QUICK,
    val mapId: String? = null,
    val coach: Boolean = true,
    /** The Tie Breaker coin as the app flipped it (p.5 step 3); null until Start. */
    val coin: Team? = null,
)

/** One computer seat in the advanced list. */
@Immutable
data class AiSeatUi(val hero: BoardHero, val team: Team, val strength: Int)

sealed interface NewGameUiState {
    data object Loading : NewGameUiState

    /** No hero (or no map) can be played yet: the engine is still being built and tested. */
    data object ComingSoon : NewGameUiState

    @Immutable
    data class Ready(
        val form: NewGameForm,
        val picker: LineupPicker,
        val sources: List<LineupSource>,
        /** Playable maps, verified single lane first. */
        val maps: List<MapOption>,
        val map: MapOption?,
        val playable: Int,
        /** The control choices for this line-up, and the chosen one. */
        val controls: List<Control>,
        val control: Control?,
        val aiSeats: List<AiSeatUi>,
        /** A saved game will be replaced by starting this one. */
        val replacesGame: Boolean,
    ) : NewGameUiState {
        val red: List<String> get() = form.lineup.red
        val blue: List<String> get() = form.lineup.blue

        /** Both teams the same size, at least one hero each. */
        val canContinue: Boolean get() = red.isNotEmpty() && red.size == blue.size

        val canStart: Boolean get() = canContinue && map != null && control != null

        fun hero(id: String): BoardHero = picker.heroById.hero(id)
    }
}

sealed interface NewGameAction {
    data class Lineup(val action: NewBoardAction) : NewGameAction

    data class Step(val step: NewGameStep) : NewGameAction

    data class SetControl(val control: Control) : NewGameAction

    data class SetStrength(val strength: Int) : NewGameAction

    data class SetSeatStrength(val heroId: String, val strength: Int) : NewGameAction

    data class SetAdvanced(val open: Boolean) : NewGameAction

    data class SetLength(val length: GameLength) : NewGameAction

    data class SetMap(val mapId: String) : NewGameAction

    data class SetCoach(val on: Boolean) : NewGameAction

    /** The app flips the Tie Breaker coin (p.5 step 3), shown before Start. */
    data object FlipCoin : NewGameAction

    data object CancelCoin : NewGameAction
}

/**
 * New game against the computer (PRODUCT §9): the line-up as on New board (alternate picks, "Use a
 * draft or match"; only playable heroes can be seated), then who you control, the computer's
 * strength 1–10 (one for all, or per seat, ADR-039), Quick or Long, the map, Coach; the app flips
 * the Tie Breaker coin and [start] creates and saves the game.
 */
@HiltViewModel(assistedFactory = NewGameViewModel.Factory::class)
class NewGameViewModel internal constructor(
    route: NewGameRoute,
    private val savedState: SavedStateHandle,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    draftRepository: DraftRecordRepository,
    matchRepository: MatchRepository,
    private val settingsRepository: SettingsRepository,
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

    /** A restored form keeps its own strength; a fresh one starts at the strength of the last game started. */
    private var strengthChosen = savedState.contains(KEY_FORM)

    init {
        if (!strengthChosen) {
            viewModelScope.launch {
                val last = settingsRepository.settings.catch { emit(AppSettings()) }.first().computerStrength
                if (!strengthChosen) form.update { it.copy(strength = AiStrength.coerce(last)) }
            }
        }
    }

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
        if (action is NewGameAction.SetStrength) strengthChosen = true
        form.update { reduce(it, action) }
        savedState[KEY_FORM] = NewGameForms.encode(form.value)
    }

    private fun reduce(form: NewGameForm, action: NewGameAction): NewGameForm = when (action) {
        is NewGameAction.Lineup -> {
            val ready = uiState.value as? NewGameUiState.Ready
            val blocked = (action.action as? NewBoardAction.Tap)?.heroId?.let { id ->
                ready != null && id in ready.picker.unavailable && !form.lineup.contains(id)
            } == true
            if (blocked) form else form.copy(lineup = NewBoardViewModel.reduce(form.lineup, action.action), coin = null)
        }

        is NewGameAction.Step -> form.copy(step = action.step)

        is NewGameAction.SetControl -> form.copy(control = action.control)

        is NewGameAction.SetStrength -> form.copy(
            strength = AiStrength.coerce(action.strength),
            seatStrengths = emptyMap(),
        )

        is NewGameAction.SetSeatStrength ->
            form.copy(seatStrengths = form.seatStrengths + (action.heroId to AiStrength.coerce(action.strength)))

        is NewGameAction.SetAdvanced -> form.copy(advanced = action.open)

        is NewGameAction.SetLength -> form.copy(length = action.length)

        is NewGameAction.SetMap -> form.copy(mapId = action.mapId)

        is NewGameAction.SetCoach -> form.copy(coach = action.on)

        NewGameAction.FlipCoin -> form.copy(coin = if (random.nextBoolean()) Team.RED else Team.BLUE)

        NewGameAction.CancelCoin -> form.copy(coin = null)
    }

    /** The setup for the current form (seats in pick order per team), or null when it can't start. */
    fun setup(): GameSetup? {
        val ready = (uiState.value as? NewGameUiState.Ready)?.takeIf { it.canStart } ?: return null
        val coin = ready.form.coin
        val map = ready.map
        val control = ready.control
        if (coin == null || map == null || control == null) return null
        fun seat(heroId: String, team: Team) = SeatSetup(
            heroId = heroId,
            team = team,
            controller = if (controls(control, heroId, team)) {
                Controller.Human
            } else {
                Controller.Ai(ready.form.seatStrengths[heroId] ?: ready.form.strength)
            },
        )
        return GameSetup(
            mapId = map.id,
            seats = ready.red.map { seat(it, Team.RED) } + ready.blue.map { seat(it, Team.BLUE) },
            length = ready.form.length,
            tieBreaker = coin,
        )
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
            val strength = form.value.strength
            settingsRepository.update { it.copy(computerStrength = strength) }
        }
        return true
    }

    internal companion object {
        private const val KEY_FORM = "newGame.form"
        private const val MAX_SOURCES = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        /** One hero each, or a whole team when it has more than one hero (p.20: two heroes per player). */
        fun controls(lineup: NewBoardForm): List<Control> {
            if (lineup.red.isEmpty() || lineup.red.size != lineup.blue.size) return emptyList()
            val teams = if (lineup.red.size > 1) Team.entries.map { Control.WholeTeam(it) } else emptyList()
            return teams + (lineup.red + lineup.blue).map { Control.Hero(it) }
        }

        fun controls(control: Control, heroId: String, team: Team): Boolean = when (control) {
            is Control.Hero -> control.heroId == heroId
            is Control.WholeTeam -> control.team == team
        }

        fun aiSeats(form: NewGameForm, control: Control?, heroes: Map<String, BoardHero>): List<AiSeatUi> {
            if (control == null) return emptyList()
            return Team.entries.flatMap { team ->
                form.lineup.team(team).filterNot { controls(control, it, team) }.map { id ->
                    AiSeatUi(heroes.hero(id), team, form.seatStrengths[id] ?: form.strength)
                }
            }
        }
    }
}
