package dev.quinton.goa.feature.board.play.newgame

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
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.data.game.SavedGameRepository
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.navigation.PlayRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.feature.board.setup.LineupPicker
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardUiState
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import dev.quinton.goa.feature.board.setup.lineupSources
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The two steps of New game: the line-up, then who plays what. */
enum class NewGameStep { LINEUP, OPTIONS }

/** Everything chosen so far; kept in the SavedStateHandle (as JSON), so it survives process death. */
@Immutable
@Serializable
data class NewGameForm(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    val filling: Team = Team.RED,
    val showAll: Boolean = false,
    val step: NewGameStep = NewGameStep.LINEUP,
    /** The hero the player controls (null = Red's first pick). */
    val you: String? = null,
    /** The player controls their whole team (p.20 two-player style), not one hero. */
    val wholeTeam: Boolean = false,
    /** Every computer seat's level, unless set per seat under Advanced. */
    val level: AiLevel = AiLevel.NORMAL,
    val perSeat: Map<String, AiLevel> = emptyMap(),
    val advanced: Boolean = false,
    val length: GameLength = GameLength.QUICK,
    val mapId: String? = null,
    val coach: Boolean = true,
    /** The Tie Breaker coin as the app flipped it (p.5 step 3); flipped when the options open. */
    val coin: Team? = null,
) {
    val lineup: NewBoardForm get() = NewBoardForm(red, blue, filling, showAll = showAll)

    fun withLineup(form: NewBoardForm): NewGameForm =
        copy(red = form.red, blue = form.blue, filling = form.filling, showAll = form.showAll)

    /** The hero the player controls: the chosen one if still seated, else Red's first pick. */
    val controlled: String? get() = you?.takeIf { it in red || it in blue } ?: red.firstOrNull() ?: blue.firstOrNull()

    val humanTeam: Team? get() = controlled?.let { if (it in red) Team.RED else Team.BLUE }

    /** The heroes the player answers for. */
    val humanSeats: Set<String>
        get() {
            val you = controlled ?: return emptySet()
            if (!wholeTeam) return setOf(you)
            return (if (you in red) red else blue).toSet()
        }

    fun levelOf(heroId: String): AiLevel = if (advanced) perSeat[heroId] ?: level else level
}

/** One seat on the options page. */
@Immutable
data class SeatUi(val hero: BoardHero, val team: Team, val human: Boolean, val level: AiLevel?)

sealed interface NewGameUiState {
    data object Loading : NewGameUiState

    @Immutable
    data class Ready(
        val form: NewGameForm,
        val picker: LineupPicker,
        /** Finished drafts and matches: "Use a draft or match" (only fully playable line-ups fill). */
        val sources: List<LineupSource>,
        val playable: Set<String>,
        val maps: List<MapOption>,
        val map: MapOption?,
        val seats: List<SeatUi>,
        /** Why the line-up can't be played yet, or null. */
        val problem: String?,
        /** A note about the line-up (1v1 is not the rulebook's way). */
        val note: String?,
        val starting: Boolean = false,
    ) : NewGameUiState {
        val canContinue: Boolean get() = problem == null
    }
}

sealed interface NewGameAction {
    data class Lineup(val action: NewBoardAction) : NewGameAction

    data object Next : NewGameAction

    data object BackToLineup : NewGameAction

    data class You(val heroId: String) : NewGameAction

    data class WholeTeam(val on: Boolean) : NewGameAction

    data class Level(val level: AiLevel) : NewGameAction

    data class SeatLevel(val heroId: String, val level: AiLevel) : NewGameAction

    data class Advanced(val on: Boolean) : NewGameAction

    data class Length(val length: GameLength) : NewGameAction

    data class Map(val mapId: String) : NewGameAction

    data class Coach(val on: Boolean) : NewGameAction
}

/**
 * New game against the computer (PRODUCT §9): a line-up as on New board (alternate picks, "Use a
 * draft or match"; only heroes the engine plays can be seated, the rest "Coming later"), then who
 * you control (one hero by default, or your whole team), the computer's level (one for all, or per
 * seat under Advanced), Quick or Long, a verified map (Forgotten Island by default) and the Coach;
 * the app flips the Tie Breaker coin. Start creates and saves the game and returns its route.
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
    private val provider: GameFactoryProvider,
    private val games: SavedGameRepository,
    private val clock: EditClock,
    private val random: Random,
    private val compute: CoroutineDispatcher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: NewGameRoute,
        savedState: SavedStateHandle,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        draftRepository: DraftRecordRepository,
        matchRepository: MatchRepository,
        settingsRepository: SettingsRepository,
        provider: GameFactoryProvider,
        games: SavedGameRepository,
        clock: EditClock,
    ) : this(
        route,
        savedState,
        catalogRepository,
        mapRepository,
        draftRepository,
        matchRepository,
        settingsRepository,
        provider,
        games,
        clock,
        Random.Default,
        Dispatchers.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(route: NewGameRoute): NewGameViewModel
    }

    private val form = MutableStateFlow(
        savedState.get<String>(KEY_FORM)?.let {
            runCatching { JSON.decodeFromString(NewGameForm.serializer(), it) }.getOrNull()
        }
            ?: NewGameForm(
                red = route.red.distinct().take(NewBoardUiState.MAX_PER_TEAM),
                blue = route.blue.distinct().filterNot { it in route.red }.take(NewBoardUiState.MAX_PER_TEAM),
                filling = NewBoardViewModel.nextTeam(route.red.size, route.blue.size, Team.BLUE),
            ),
    )
    private val starting = MutableStateFlow(false)

    private val engine = flow { emit(provider.get()) }

    private val inputs = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        settingsRepository.settings.catch { emit(AppSettings()) },
        engine,
    ) { catalog, maps, settings, engine ->
        Inputs(catalog.boardHeroes(), maps.associate { it.id to it }, settings.ownedProducts, engine)
    }

    private val sources = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    val uiState: StateFlow<NewGameUiState> = combine(inputs, sources, form, starting) {
            inputs,
            (drafts, matches),
            form,
            starting,
        ->
        build(inputs, drafts.let { lineupSources(it, matches, inputs.heroes, MAX_SOURCES) }, form, starting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewGameUiState.Loading)

    private data class Inputs(
        val heroes: kotlin.collections.Map<String, BoardHero>,
        val maps: kotlin.collections.Map<String, BoardMap>,
        val owned: Set<String>,
        val engine: GameFactory,
    )

    private fun build(
        inputs: Inputs,
        sources: List<LineupSource>,
        form: NewGameForm,
        starting: Boolean,
    ): NewGameUiState {
        val playable = inputs.engine.playableHeroes
        val all = inputs.heroes.values.sortedWith(compareBy<BoardHero> { it.id !in playable }.thenBy { it.name })
        val lineup = form.lineup
        val shown = if (form.showAll) all else all.filter { it.ownedIn(inputs.owned) || lineup.contains(it.id) }
        val picker = LineupPicker(
            form = lineup,
            heroes = shown,
            heroById = inputs.heroes,
            hiddenHeroes = all.size - shown.size,
            ownedOnly = inputs.owned.isNotEmpty(),
            unavailable = all.filter { it.id !in playable }.associate { it.id to COMING_LATER },
        )
        val maps = inputs.engine.playableMaps.mapNotNull { id ->
            inputs.maps[id]?.let { MapOption(it.id, it.name, it.lanes.size, it.verified) }
        }
        val map =
            maps.firstOrNull { it.id == form.mapId } ?: maps.firstOrNull { it.id == DEFAULT_MAP } ?: maps.firstOrNull()
        val humans = form.humanSeats
        val seats = (form.red.map { it to Team.RED } + form.blue.map { it to Team.BLUE }).map { (id, team) ->
            SeatUi(inputs.heroes.hero(id), team, id in humans, if (id in humans) null else form.levelOf(id))
        }
        return NewGameUiState.Ready(
            form = form,
            picker = picker,
            sources = sources,
            playable = playable,
            maps = maps,
            map = map,
            seats = seats,
            problem = problem(form, playable, inputs.heroes, map),
            note = if (form.red.size == 1 && form.blue.size == 1) ONE_V_ONE else null,
            starting = starting,
        )
    }

    private fun problem(
        form: NewGameForm,
        playable: Set<String>,
        heroes: kotlin.collections.Map<String, BoardHero>,
        map: MapOption?,
    ): String? {
        val notYet = (form.red + form.blue).filter { it !in playable }
        return when {
            form.red.isEmpty() || form.blue.isEmpty() -> "Pick at least one hero for each team"

            notYet.isNotEmpty() -> "${notYet.joinToString(", ") {
                heroes.hero(it).name
            }}: coming later, not playable yet"

            form.red.size != form.blue.size -> "Both teams need the same number of heroes"

            map == null -> "No verified map in this build"

            else -> null
        }
    }

    fun onAction(action: NewGameAction) {
        form.update { reduce(it, action) }
        savedState[KEY_FORM] = JSON.encodeToString(NewGameForm.serializer(), form.value)
    }

    private fun reduce(form: NewGameForm, action: NewGameAction): NewGameForm = when (action) {
        is NewGameAction.Lineup -> form.withLineup(NewBoardViewModel.reduce(form.lineup, action.action))
        NewGameAction.Next -> form.copy(step = NewGameStep.OPTIONS, coin = form.coin ?: flip())
        NewGameAction.BackToLineup -> form.copy(step = NewGameStep.LINEUP)
        is NewGameAction.You -> form.copy(you = action.heroId)
        is NewGameAction.WholeTeam -> form.copy(wholeTeam = action.on)
        is NewGameAction.Level -> form.copy(level = action.level)
        is NewGameAction.SeatLevel -> form.copy(perSeat = form.perSeat + (action.heroId to action.level))
        is NewGameAction.Advanced -> form.copy(advanced = action.on)
        is NewGameAction.Length -> form.copy(length = action.length)
        is NewGameAction.Map -> form.copy(mapId = action.mapId)
        is NewGameAction.Coach -> form.copy(coach = action.on)
    }

    private fun flip(): Team = if (random.nextBoolean()) Team.RED else Team.BLUE

    /** The setup the form describes: seats in pick order per team, Red first. */
    internal fun setup(ready: NewGameUiState.Ready): GameSetup? {
        val form = ready.form
        val map = ready.map ?: return null
        if (!ready.canContinue) return null
        val humans = form.humanSeats
        fun seat(id: String, team: Team) =
            SeatSetup(id, team, if (id in humans) Controller.Human else Controller.Ai(form.levelOf(id)))
        return GameSetup(
            mapId = map.id,
            seats = form.red.map { seat(it, Team.RED) } + form.blue.map { seat(it, Team.BLUE) },
            length = form.length,
            tieBreaker = form.coin ?: flip(),
        )
    }

    /** Creates and saves the game; its route, or null when the form isn't ready (or a start is under way). */
    suspend fun start(): PlayRoute? {
        val ready = (uiState.value as? NewGameUiState.Ready)?.takeIf { !starting.value }
        val setup = ready?.let(::setup) ?: return null
        starting.value = true
        val factory = provider.get()
        val now = clock.now()
        // The engine refuses a setup it can't play (a hero not playable yet): stay on the form.
        val saved = withContext(compute) { runCatching { factory.newGame(setup).save() }.getOrNull() }
        val game = saved?.let {
            StoredGame(
                newId(),
                it.copy(savedAt = now),
                PlayOptions(coach = ready.form.coach),
                createdAt = now,
                updatedAt = now,
            )
        }
        if (game == null) {
            starting.value = false
        } else {
            withContext(NonCancellable) { games.save(game) }
        }
        return game?.let { PlayRoute(it.id) }
    }

    internal companion object {
        private const val KEY_FORM = "newGame.form"
        private const val MAX_SOURCES = 10
        private const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAP = "forgotten-island"
        const val COMING_LATER = "coming later"
        const val ONE_V_ONE =
            "1 v 1 is not the rulebook's way: with two players, each controls two heroes (p.20)."
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
