package dev.quinton.goa.feature.timer.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.setup.ModeBlock
import dev.quinton.goa.core.domain.setup.ModeOption
import dev.quinton.goa.core.domain.setup.SeatLayout
import dev.quinton.goa.core.domain.setup.SetupPlan
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.session.TimerController
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
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

data class LayoutOptionUi(
    val id: String,
    val kind: LayoutKind,
    val title: String,
    val teams: String,
    val guidance: String?,
    val official: Boolean,
)

data class ModeOptionUi(
    val mode: GameMode,
    val title: String,
    val available: Boolean,
    val required: Boolean,
    val reason: String?,
    val waves: String,
    val life: String,
)

data class CountersUi(
    val lanes: Int,
    val wavesPerLane: Int,
    /** Life counters per team; null = not specified by the rulebook for this many heroes. */
    val lifePerTeam: Int?,
    val redHeroes: Int?,
    val blueHeroes: Int?,
    val largerTeamHeroes: Int,
    val smallerTeamHeroes: Int,
    val handicapHeroes: Int,
    val doublePilots: Int,
)

data class LinkedMatchUi(val title: String, val redSeats: Int, val blueSeats: Int)

data class MatchSetupUiState(
    val loading: Boolean = true,
    val players: Int = 4,
    val playerRange: IntRange = 2..10,
    val playersLocked: Boolean = false,
    val recommended: Boolean = false,
    val extendedNote: String? = null,
    val layouts: List<LayoutOptionUi> = emptyList(),
    val selectedLayoutId: String? = null,
    val modes: List<ModeOptionUi> = emptyList(),
    val selectedMode: GameMode = GameMode.QUICK,
    val counters: CountersUi? = null,
    val tieBreaker: Team? = null,
    val flips: Int = 0,
    val tieBreakerRule: String = "",
    val firstGameAdvice: String = "",
    val linkedMatch: LinkedMatchUi? = null,
    val presets: List<PresetOption> = emptyList(),
    val selectedPresetId: String? = null,
    val replacesRunningTimer: Boolean = false,
)

sealed interface SetupEvent {
    data object TimerStarted : SetupEvent
}

private data class Choices(
    val players: Int? = null,
    val layoutId: String? = null,
    val mode: GameMode? = null,
    val tieBreaker: Team? = null,
    val flips: Int = 0,
    val presetId: String? = null,
)

@HiltViewModel(assistedFactory = MatchSetupViewModel.Factory::class)
class MatchSetupViewModel @AssistedInject constructor(
    @Assisted private val matchId: String?,
    private val repository: TimerRepository,
    private val controller: TimerController,
    private val matches: MatchRepository,
    private val coin: CoinFlipper,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(matchId: String?): MatchSetupViewModel
    }

    private val choices = MutableStateFlow(Choices())
    private val events = Channel<SetupEvent>(Channel.BUFFERED)
    val navigation: Flow<SetupEvent> = events.receiveAsFlow()

    private val rules: Flow<MatchRules> = flowOf(Unit).map { repository.rules() }
    private val match: Flow<Match?> = matchId?.let { matches.observeMatch(it) } ?: flowOf(null)

    val uiState: StateFlow<MatchSetupUiState> = combine(
        rules,
        match,
        choices,
        combine(repository.presets, repository.selectedPresetId) { p, s -> p to s },
        controller.snapshot,
    ) { rules, match, choice, (presets, selected), running ->
        build(rules, match, choice, presets.map(::presetOption), choice.presetId ?: selected).copy(
            replacesRunningTimer = running != null && running.status != TimerStatus.ENDED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MatchSetupUiState())

    fun setPlayers(players: Int) = choices.update { it.copy(players = players, layoutId = null) }

    fun selectLayout(id: String) = choices.update { it.copy(layoutId = id) }

    fun selectMode(mode: GameMode) = choices.update { it.copy(mode = mode) }

    fun selectPreset(id: String) = choices.update { it.copy(presetId = id) }

    fun flipCoin() = choices.update { it.copy(tieBreaker = coin.flip(), flips = it.flips + 1) }

    fun startTimer() {
        viewModelScope.launch {
            val state = uiState.first { !it.loading }
            val preset = state.selectedPresetId?.let { repository.preset(it) } ?: repository.selectedPreset()
            val linked = matchId?.let { matches.observeMatch(it).first() }
            if (linked != null &&
                linked.mode != state.selectedMode
            ) {
                matches.upsert(linked.copy(mode = state.selectedMode))
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

    private fun build(
        rules: MatchRules,
        match: Match?,
        choice: Choices,
        presets: List<PresetOption>,
        selectedPresetId: String,
    ): MatchSetupUiState {
        val setup = MatchSetup(rules)
        val seats = match?.participants.orEmpty()
        val locked = seats.isNotEmpty()
        val red = seats.count { it.team == Team.RED }
        val blue = seats.count { it.team == Team.BLUE }
        val players = when {
            locked -> match!!.playerIds.size.coerceIn(setup.playerRange)
            else -> (choice.players ?: DEFAULT_PLAYERS).coerceIn(setup.playerRange)
        }
        val plan: SetupPlan = if (locked) {
            setup.planForSeats(players, red, blue, choice.mode ?: match!!.mode)
        } else {
            setup.plan(players, choice.layoutId, choice.mode)
        }
        val layouts = if (locked) listOf(plan.layout) else setup.layouts(players)
        return MatchSetupUiState(
            loading = false,
            players = players,
            playerRange = setup.playerRange,
            playersLocked = locked,
            recommended = setup.isRecommended(players),
            extendedNote = if (setup.isExtended(players)) rules.extendedCountNote else null,
            layouts = layouts.map(::layoutOption),
            selectedLayoutId = plan.layout.id,
            modes = plan.modes.map { modeOption(it, rules) },
            selectedMode = plan.selected.mode,
            counters = CountersUi(
                lanes = plan.selected.lanes,
                wavesPerLane = plan.selected.waveCountersPerLane,
                lifePerTeam = plan.selected.lifeCountersPerTeam,
                redHeroes = if (locked) red else null,
                blueHeroes = if (locked) blue else null,
                largerTeamHeroes = plan.layout.largerTeamHeroes,
                smallerTeamHeroes = plan.layout.smallerTeamHeroes,
                handicapHeroes = if (locked) seats.count { it.handicap } else plan.layout.handicapHeroes,
                doublePilots = plan.layout.doublePilots,
            ),
            tieBreaker = choice.tieBreaker,
            flips = choice.flips,
            tieBreakerRule = rules.tieBreaker,
            firstGameAdvice = rules.firstGameAdvice,
            linkedMatch = match?.let {
                LinkedMatchUi(
                    title = if (locked) "Pending match · ${seats.size} heroes" else "Pending match",
                    redSeats = red,
                    blueSeats = blue,
                )
            },
            presets = presets,
            selectedPresetId = selectedPresetId,
        )
    }

    private fun layoutOption(layout: SeatLayout) = LayoutOptionUi(
        id = layout.id,
        kind = layout.kind,
        title = when (layout.kind) {
            LayoutKind.STANDARD -> "Even teams"
            LayoutKind.RULEBOOK -> "Rulebook setup"
            LayoutKind.HANDICAP -> "Handicap"
            LayoutKind.DOUBLE_PILOT -> "Double pilot"
        },
        teams = "${layout.largerTeamHeroes} vs ${layout.smallerTeamHeroes} heroes",
        guidance = layout.guidance,
        official = layout.kind != LayoutKind.DOUBLE_PILOT,
    )

    private fun modeOption(option: ModeOption, rules: MatchRules) = ModeOptionUi(
        mode = option.mode,
        title = TimerText.modeLabel(option.mode),
        available = option.available,
        required = option.required,
        reason = when (option.block) {
            ModeBlock.TOO_FEW_HEROES -> "Needs ${rules.mode(option.mode).minHeroes} or more heroes"
            ModeBlock.OTHER_MODE_REQUIRED -> "Double lane is required at this size"
            null -> null
        },
        waves = if (option.lanes > 1) {
            "${option.lanes} × ${option.waveCountersPerLane} Wave counters"
        } else {
            "${option.waveCountersPerLane} Wave counters"
        },
        life = option.lifeCountersPerTeam?.let { "$it Life counters per team" }
            ?: "Life counters not specified by the rulebook",
    )

    private fun presetOption(p: dev.quinton.goa.core.domain.timer.TimerPreset) = PresetOption(
        id = p.id,
        name = p.name,
        summary = TimerText.presetSummary(p),
        usesActionTimer = p.phases.any { it.enabled && it.kind == PhaseKind.ACTION },
    )

    companion object {
        const val DEFAULT_PLAYERS = 4
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
