package dev.quinton.goa.feature.timer.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.HouseRuleDifference
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.setup.ModeBlock
import dev.quinton.goa.core.domain.setup.ModeOption
import dev.quinton.goa.core.domain.setup.RulesNote
import dev.quinton.goa.core.domain.setup.SeatLayout
import dev.quinton.goa.core.domain.setup.SetupPlan
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.timer.MatchSetupBuilder
import dev.quinton.goa.core.presentation.timer.MatchSetupChoices
import dev.quinton.goa.core.presentation.timer.MatchSetupUiState
import dev.quinton.goa.core.presentation.timer.RematchRequest
import dev.quinton.goa.core.presentation.timer.TimerUiMapper
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.session.TimerController
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tie Breaker coin source (injectable so tests are deterministic). */
fun interface CoinFlipper {
    fun flip(): Team
}

class RandomCoinFlipper @Inject constructor() : CoinFlipper {
    override fun flip(): Team = MatchSetup.flipTieBreaker(Random.Default)
}

sealed interface SetupEvent {
    data object TimerStarted : SetupEvent
}

@HiltViewModel(assistedFactory = MatchSetupViewModel.Factory::class)
class MatchSetupViewModel @AssistedInject constructor(
    @Assisted private val matchId: String?,
    private val repository: TimerRepository,
    private val controller: TimerController,
    private val matches: MatchRepository,
    private val coin: CoinFlipper,
    @Assisted private val rematch: RematchRequest? = null,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(matchId: String?, rematch: RematchRequest?): MatchSetupViewModel
    }

    /** The id the rematch gets if its timer is started. */
    private val rematchId = UUID.randomUUID().toString()

    private val choices = MutableStateFlow(MatchSetupChoices())
    private val events = Channel<SetupEvent>(Channel.BUFFERED)
    val navigation: Flow<SetupEvent> = events.receiveAsFlow()

    private val rules: Flow<MatchRules> = flowOf(Unit).map { repository.rules() }
    private val match: Flow<Match?> = when {
        matchId != null -> matches.observeMatch(matchId)
        rematch != null -> matches.observeMatch(rematch.sourceId).map { source -> source?.let(::buildRematch) }
        else -> flowOf(null)
    }

    private fun buildRematch(source: Match, at: Long = System.currentTimeMillis()): Match? =
        Rematch.create(source, rematch!!.kind, rematchId, at).getOrNull()

    val uiState: StateFlow<MatchSetupUiState> = combine(
        rules,
        match,
        choices,
        combine(repository.presets, repository.selectedPresetId) { p, s -> p to s },
        controller.snapshot,
    ) { rules, match, choice, (presets, selected), running ->
        MatchSetupBuilder.build(
            rules,
            match,
            choice,
            presets.map(TimerUiMapper::presetOption),
            choice.presetId ?: selected,
            rematch,
        ).copy(
            replacesRunningTimer = running != null && running.status != TimerStatus.ENDED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MatchSetupUiState())

    fun setPlayers(players: Int) = choices.update { it.copy(players = players, layoutId = null) }

    fun selectLayout(id: String) = choices.update { it.copy(layoutId = id) }

    fun selectMode(mode: GameMode) = choices.update { it.copy(mode = mode) }

    fun selectPreset(id: String) = choices.update { it.copy(presetId = id) }

    fun flipCoin() = choices.update { it.copy(tieBreaker = coin.flip(), flips = it.flips + 1) }

    /**
     * Records the side the real coin shows (a correction, or a coin flipped at the table). Picking
     * the side the placed coin already shows is no correction: it keeps (or returns to) that coin.
     */
    fun setCoin(team: Team) {
        viewModelScope.launch {
            val placed = match.first()?.tieBreaker
            choices.update { it.copy(tieBreaker = team.takeIf { t -> t != placed }) }
        }
    }

    fun correctCoin() = choices.update { it.copy(correctingCoin = true) }

    fun startTimer() {
        viewModelScope.launch {
            val state = uiState.first { !it.loading }
            val preset = state.selectedPresetId?.let { repository.preset(it) } ?: repository.selectedPreset()
            val linked = when {
                matchId != null -> matches.observeMatch(matchId).first()

                // A rematch is saved only now, as the game actually starts.
                rematch != null -> matches.observeMatch(rematch.sourceId).first()?.let { buildRematch(it) }

                else -> null
            }
            if (linked != null) {
                // The coin as placed at setup is part of the match record (draft coin, or corrected here).
                val setUp = { m: Match ->
                    m.copy(
                        mode = state.selectedMode,
                        tieBreaker =
                            state.tieBreaker ?: m.tieBreaker,
                    )
                }
                // A pending match changes as stored (its logged pilots kept); a rematch is new.
                if (matchId != null) matches.edit(matchId, setUp) else matches.upsert(setUp(linked))
            }
            val heroes = state.counters?.let { it.largerTeamHeroes + it.smallerTeamHeroes } ?: state.players
            controller.start(
                preset = preset,
                actionSlots = heroes,
                matchId = linked?.id,
                mode = state.selectedMode,
                tieBreaker = state.tieBreaker,
            )
            events.send(SetupEvent.TimerStarted)
        }
    }

    companion object {
        const val DEFAULT_PLAYERS = MatchSetupBuilder.DEFAULT_PLAYERS
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
