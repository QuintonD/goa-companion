package dev.quinton.goa.feature.board.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.backup.AppInfo
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.OnlineGameRoute
import dev.quinton.goa.core.navigation.OnlineHomeRoute
import dev.quinton.goa.core.navigation.OnlineLobbyRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.LobbyUi
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineHomePresenter
import dev.quinton.goa.feature.board.play.online.OnlineHomeUi
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineLobbyPresenter
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardLoad
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardPresenter
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardUi
import dev.quinton.goa.feature.board.play.table.TableTap
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The online screens' ViewModels (ADR-056): the presenters and the driver in core:play hold the logic, shared
 * with the web app. These give them a scope on the main thread and the platform's seams, and stay this thin.
 */

/** The name the server's logs know this build by. */
internal fun clientName(appInfo: AppInfo): String = "android/${appInfo.versionName().orEmpty()}"

/** The online home: the account, a new room, joining by code or QR, the games to go back to. */
@HiltViewModel(assistedFactory = OnlineHomeViewModel.Factory::class)
class OnlineHomeViewModel @AssistedInject constructor(
    @Assisted route: OnlineHomeRoute,
    private val savedState: SavedStateHandle,
    gateway: OnlineGateway,
    identities: OnlineIdentityStore,
    mapRepository: BoardMapRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: OnlineHomeRoute): OnlineHomeViewModel
    }

    private val heroList = MutableStateFlow<Map<String, BoardHero>>(emptyMap())

    /** Every hero's name and portrait: a game's seats are its heroes. */
    val heroes: StateFlow<Map<String, BoardHero>> = heroList.asStateFlow()

    private val presenter = OnlineHomePresenter(gateway, identities, viewModelScope)
    val uiState: StateFlow<OnlineHomeUi> = presenter.state

    private val names = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The maps a room can be on, by id. */
    val mapNames: StateFlow<Map<String, String>> = names.asStateFlow()

    init {
        presenter.start()
        viewModelScope.launch { names.value = mapRepository.maps.first().associate { it.id to it.name } }
        viewModelScope.launch { heroList.value = catalogRepository.catalog.first().boardHeroes() }
        // An invite link waits for an account, and is used once: the route comes back after process death.
        route.invite?.takeIf { savedState.get<Boolean>(KEY_INVITE_USED) != true }?.let { invite ->
            viewModelScope.launch {
                uiState.first { it.stage == AccountStage.READY && !it.working }
                savedState[KEY_INVITE_USED] = true
                presenter.join(invite)
            }
        }
    }

    fun setName(name: String) = presenter.setName(name)

    fun setLinkCode(code: String) = presenter.setLinkCode(code)

    fun createAccount() = presenter.createAccount()

    fun linkDevice() = presenter.linkDevice()

    fun recoveryCodeSaved() = presenter.recoveryCodeSaved()

    fun rename(name: String) = presenter.rename(name)

    fun deleteAccount() = presenter.deleteAccount()

    fun setMap(mapId: String) = presenter.setMap(mapId)

    fun setLength(length: GameLength) = presenter.setLength(length)

    fun setTeamSize(size: Int) = presenter.setTeamSize(size)

    fun setRanked(ranked: Boolean) = presenter.setRanked(ranked)

    fun createRoom() = presenter.createRoom()

    fun setJoinText(text: String) = presenter.setJoinText(text)

    /** Joins by what is in the field, or by what the scanner read. */
    fun join(text: String = uiState.value.joinText) = presenter.join(inviteText(text))

    fun roomOpened() = presenter.roomOpened()

    fun refreshGames() = presenter.refreshGames()

    private companion object {
        const val KEY_INVITE_USED = "invite_used"
    }
}

/** A room's lobby. The presenter needs the playable heroes, so it starts once they are known. */
@HiltViewModel(assistedFactory = OnlineLobbyViewModel.Factory::class)
class OnlineLobbyViewModel internal constructor(
    route: OnlineLobbyRoute,
    gateway: OnlineGateway,
    connector: GameConnector,
    identities: OnlineIdentityStore,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    factory: suspend () -> GameFactory,
    client: String,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted route: OnlineLobbyRoute,
        gateway: OnlineGateway,
        connector: GameConnector,
        identities: OnlineIdentityStore,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        factory: Provider<GameFactory>,
        appInfo: AppInfo,
    ) : this(
        route,
        gateway,
        connector,
        identities,
        catalogRepository,
        mapRepository,
        { withContext(Dispatchers.Default) { factory.get() } },
        clientName(appInfo),
    )

    @AssistedFactory
    interface Factory {
        fun create(route: OnlineLobbyRoute): OnlineLobbyViewModel
    }

    private var presenter: OnlineLobbyPresenter? = null
    private val state = MutableStateFlow(LobbyUi(route.code))
    val uiState: StateFlow<LobbyUi> = state.asStateFlow()

    private val heroList = MutableStateFlow<Map<String, BoardHero>>(emptyMap())

    /** Every hero's name and portrait, for the seats and the picker. */
    val heroes: StateFlow<Map<String, BoardHero>> = heroList.asStateFlow()

    private val names = MutableStateFlow<Map<String, String>>(emptyMap())
    val mapNames: StateFlow<Map<String, String>> = names.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = catalogRepository.catalog.first()
            heroList.value = catalog.boardHeroes()
            names.value = mapRepository.maps.first().associate { it.id to it.name }
            val playable = factory().playableHeroes
            val lobby = OnlineLobbyPresenter(
                code = route.code,
                gateway = gateway,
                connector = connector,
                identities = identities,
                playableHeroes = catalog.heroes.map { it.id }.filter { it in playable },
                catalogVersion = { catalog.dataVersion },
                scope = viewModelScope,
                client = client,
            )
            presenter = lobby
            lobby.start()
            lobby.state.collect { state.value = it }
        }
    }

    /** Tries again after a room that couldn't be joined for want of a connection. */
    fun retry() {
        presenter?.start()
    }

    fun pickSeat(team: Team, index: Int) {
        presenter?.pickSeat(team, index)
    }

    fun pickHero(heroId: String?) {
        presenter?.pickHero(heroId)
    }

    fun setReady(ready: Boolean) {
        presenter?.setReady(ready)
    }

    fun startGame() {
        presenter?.startGame()
    }

    fun leave() {
        presenter?.leave()
    }
}

/**
 * A running online game: the driver feeds the same stateless `GameScreen` as a game against the computer, and
 * clearing this ViewModel cancels its scope and closes the socket.
 */
@HiltViewModel(assistedFactory = OnlineGameViewModel.Factory::class)
class OnlineGameViewModel internal constructor(
    route: OnlineGameRoute,
    connector: GameConnector,
    identities: OnlineIdentityStore,
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    clock: EditClock,
    client: String,
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted route: OnlineGameRoute,
        connector: GameConnector,
        identities: OnlineIdentityStore,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        clock: EditClock,
        appInfo: AppInfo,
    ) : this(route, connector, identities, catalogRepository, mapRepository, clock, clientName(appInfo))

    @AssistedFactory
    interface Factory {
        fun create(route: OnlineGameRoute): OnlineGameViewModel
    }

    private val driver = OnlineGameDriver(
        gameId = route.gameId,
        connector = connector,
        identities = identities,
        catalog = { catalogRepository.catalog.first() },
        maps = { mapRepository.maps.first() },
        clock = clock,
        scope = viewModelScope,
        client = client,
    )

    val uiState: StateFlow<OnlineState> = driver.state

    init {
        driver.start()
    }

    fun onAction(action: GameAction) = driver.onAction(action)

    fun tap(tap: TableTap): Boolean = driver.tap(tap)

    /** The app came back to the foreground, or the network returned. */
    fun resumeNow() = driver.resumeNow()

    fun leave() = driver.leave()
}

/** The public leaderboards: the presenter is pure, so this keeps the load, the tab and who is asking. */
@HiltViewModel
class OnlineLeaderboardViewModel @Inject constructor(
    private val repository: LeaderboardRepository,
    private val identities: OnlineIdentityStore,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    private val portraitMap = MutableStateFlow<Map<String, String?>>(emptyMap())

    /** Each hero's portrait by id, for the Heroes tab's podium. */
    val portraits: StateFlow<Map<String, String?>> = portraitMap.asStateFlow()

    private var load: LeaderboardLoad? = null
    private var tab = LeaderboardTab.PLAYERS
    private var accountId: String? = null

    private val state = MutableStateFlow(LeaderboardPresenter.present(null, loading = true))
    val uiState: StateFlow<LeaderboardUi> = state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            portraitMap.value = catalogRepository.catalog.first().heroes.associate { it.id to it.portrait }
        }
    }

    fun refresh() {
        show(loading = true)
        viewModelScope.launch {
            accountId = identities.load()?.accountId
            load = repository.load() ?: load
            show(loading = false)
        }
    }

    fun selectTab(selected: LeaderboardTab) {
        tab = selected
        show(loading = state.value.loading)
    }

    private fun show(loading: Boolean) {
        state.value = LeaderboardPresenter.present(load, tab, accountId, loading)
    }
}
