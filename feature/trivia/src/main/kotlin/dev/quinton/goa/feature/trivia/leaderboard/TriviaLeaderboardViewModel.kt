package dev.quinton.goa.feature.trivia.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.trivia.LeaderboardTab
import dev.quinton.goa.core.data.trivia.TriviaLeaderboardModel
import dev.quinton.goa.core.data.trivia.TriviaLeaderboardUiState
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The trivia leaderboard (ADR-032): the shared [TriviaLeaderboardModel] (also the web app's) in this
 * screen's view-model scope; the tab, period and open player survive process death in [saved].
 */
@HiltViewModel
class TriviaLeaderboardViewModel internal constructor(
    rounds: TriviaRoundRepository,
    players: PlayerRepository,
    groups: GroupRepository,
    clock: EditClock,
    private val saved: SavedStateHandle,
    /** Builds the boards off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(
        rounds: TriviaRoundRepository,
        players: PlayerRepository,
        groups: GroupRepository,
        clock: EditClock,
        saved: SavedStateHandle,
    ) : this(rounds, players, groups, clock, saved, Dispatchers.Default)

    private val model = TriviaLeaderboardModel(
        viewModelScope,
        rounds,
        players,
        groups,
        clock,
        computeDispatcher,
        initialTab = saved.get<String>(KEY_TAB)?.let(::tabOf) ?: LeaderboardTab.CHALLENGE,
        initialPeriod = saved.get<String>(KEY_PERIOD)?.let(::periodOf) ?: LeaderboardPeriod.ALL_TIME,
        initialPlayer = saved.get<String>(KEY_PLAYER),
    )

    val uiState: StateFlow<TriviaLeaderboardUiState> = model.uiState

    /** The screen is back in view: "Last 30 days" counts from now, not from the last data change. */
    fun onResume() = model.onResume()

    fun messageShown() = model.messageShown()

    fun selectTab(value: LeaderboardTab) {
        saved[KEY_TAB] = value.name
        model.selectTab(value)
    }

    fun selectPeriod(value: LeaderboardPeriod) {
        saved[KEY_PERIOD] = value.name
        model.selectPeriod(value)
    }

    fun openPlayer(playerId: String) {
        saved[KEY_PLAYER] = playerId
        model.openPlayer(playerId)
    }

    fun closePlayer() {
        saved[KEY_PLAYER] = null
        model.closePlayer()
    }

    /** "Remove from leaderboard": deleted with a tombstone, so every phone drops it at its next sync. */
    fun removeRound(id: String) = model.removeRound(id)

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_PERIOD = "period"
        const val KEY_PLAYER = "player"

        fun tabOf(name: String) = LeaderboardTab.entries.firstOrNull { it.name == name }

        fun periodOf(name: String) = LeaderboardPeriod.entries.firstOrNull { it.name == name }
    }
}
