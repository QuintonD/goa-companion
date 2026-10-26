package dev.quinton.goa.feature.trivia.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.trivia.TriviaChallengeStart
import dev.quinton.goa.core.data.trivia.TriviaHomeAction
import dev.quinton.goa.core.data.trivia.TriviaHomeModel
import dev.quinton.goa.core.data.trivia.TriviaHomeUiState
import dev.quinton.goa.core.data.trivia.TriviaQuizRequest
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/** What tapping Challenge does. */
sealed interface ChallengeStart {
    data class Open(val route: TriviaQuizRoute) : ChallengeStart

    /** Playing as Guest: pick who's playing first (a Challenge needs a name on the board). */
    data object PickPlayer : ChallengeStart

    /** Nothing loaded yet. */
    data object NotReady : ChallengeStart
}

/** The quiz route for a request of the shared trivia models (core:data-contracts). */
internal fun TriviaQuizRequest.toRoute() = TriviaQuizRoute(config, factKeys, challenge)

internal fun TriviaQuizRoute.toRequest() = TriviaQuizRequest(config, factKeys, challenge)

/**
 * The trivia home (ADR-028, ADR-032): the shared [TriviaHomeModel] (also the web app's) in this
 * screen's view-model scope, its quiz requests as navigation routes.
 */
@HiltViewModel(assistedFactory = TriviaHomeViewModel.Factory::class)
class TriviaHomeViewModel internal constructor(
    heroId: String?,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    progressRepository: TriviaProgressRepository,
    playerRepository: PlayerRepository,
    groupRepository: GroupRepository,
    /** Builds the state off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted heroId: String?,
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        progressRepository: TriviaProgressRepository,
        playerRepository: PlayerRepository,
        groupRepository: GroupRepository,
    ) : this(
        heroId,
        catalogRepository,
        settingsRepository,
        progressRepository,
        playerRepository,
        groupRepository,
        Dispatchers.Default,
    )

    @AssistedFactory
    interface Factory {
        fun create(heroId: String?): TriviaHomeViewModel
    }

    private val model = TriviaHomeModel(
        viewModelScope,
        heroId,
        catalogRepository,
        settingsRepository,
        progressRepository,
        playerRepository,
        groupRepository,
        computeDispatcher,
    )

    val uiState: StateFlow<TriviaHomeUiState> = model.uiState

    fun onAction(action: TriviaHomeAction) = model.onAction(action)

    /** Saves the setup and returns the quiz to open, or null when it would ask nothing. */
    fun start(): TriviaQuizRoute? = model.start()?.toRoute()

    /** A quiz on [heroId] alone with the current Numbers, Cards and answer settings. */
    fun startHero(heroId: String): TriviaQuizRoute? = model.startHero(heroId)?.toRoute()

    /** Forgets every learned and learning number. */
    fun resetProgress() = model.resetProgress()

    /** The Challenge (ADR-032); Guest is asked to pick a player first. */
    fun startChallenge(): ChallengeStart = when (val start = model.startChallenge()) {
        is TriviaChallengeStart.Open -> ChallengeStart.Open(start.request.toRoute())
        TriviaChallengeStart.PickPlayer -> ChallengeStart.PickPlayer
        TriviaChallengeStart.NotReady -> ChallengeStart.NotReady
    }

    /** Plays the next rounds as [playerIdOrGuest] (a player id or [TriviaProgressRepository.GUEST]). */
    fun playAs(playerIdOrGuest: String, then: (() -> Unit)? = null) = model.playAs(playerIdOrGuest, then)

    /** Picks [playerId] and then opens the Challenge, once the pick is stored (the quiz reads it). */
    fun playAsAndChallenge(playerId: String, open: (TriviaQuizRoute) -> Unit) =
        model.playAsAndChallenge(playerId) { open(it.toRoute()) }

    /** Adds a player from the picker and plays as them; [open] gets the Challenge when asked for. */
    fun addPlayer(name: String, open: ((TriviaQuizRoute) -> Unit)? = null) =
        model.addPlayer(name, open?.let { callback -> { request: TriviaQuizRequest -> callback(request.toRoute()) } })
}

/** As in the player editor. */
internal const val MAX_NAME_LENGTH = TriviaHomeModel.MAX_NAME_LENGTH
