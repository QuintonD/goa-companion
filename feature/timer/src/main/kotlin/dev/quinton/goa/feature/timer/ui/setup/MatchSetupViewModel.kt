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
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.session.TimerController
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
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
    /** Badge for a required mode: "Required", or "Required (house rule)" when only the hero count requires it. */
    val requiredLabel: String? = null,
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
    /** Where counting by heroes differs from the rulebook's per-player counters (house rule label). */
    val houseRuleNote: String? = null,
    /** Rules for the chosen mode (Double lane, p.20). */
    val modeNote: RulesNote? = null,
)

/** Where the Tie Breaker coin shown at setup comes from. */
enum class CoinSource {
    /** Not flipped yet: flip it now. */
    NONE,

    /** Flipped during the draft that created this match; leave it as it is. */
    DRAFT,

    /** Recorded on this match at an earlier setup; leave it as it is. */
    MATCH,

    /** Flipped or set on this screen. */
    HERE,
}

data class LinkedMatchUi(val title: String, val redSeats: Int, val blueSeats: Int)

/**
 * A rematch of [sourceId] (DOMAIN.md *Rematch*). Nothing is saved until Start timer: backing out
 * of setup leaves no pending match behind.
 */
data class RematchRequest(val sourceId: String, val kind: RematchKind)

data class MatchSetupUiState(
    val loading: Boolean = true,
    val players: Int = 4,
    val playerRange: IntRange = 2..10,
    val playersLocked: Boolean = false,
    /** Where locked seats come from: "From the drafted match", "Rematch: same seats", … */
    val seatsSource: String? = null,
    val recommended: Boolean = false,
    val extendedNote: String? = null,
    val layouts: List<LayoutOptionUi> = emptyList(),
    val selectedLayoutId: String? = null,
    val modes: List<ModeOptionUi> = emptyList(),
    val selectedMode: GameMode = GameMode.QUICK,
    val counters: CountersUi? = null,
    val tieBreaker: Team? = null,
    val coinSource: CoinSource = CoinSource.NONE,
    /** The coin was already placed (draft or earlier setup): offer a correction, not a new flip. */
    val coinPlaced: Boolean = false,
    /** "Correct the coin" was tapped: show the Red/Blue choice. */
    val correctingCoin: Boolean = false,
    val flips: Int = 0,
    val tieBreakerRule: List<String> = emptyList(),
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
    val correctingCoin: Boolean = false,
    val presetId: String? = null,
)

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

    private val choices = MutableStateFlow(Choices())
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
        build(rules, match, choice, presets.map(::presetOption), choice.presetId ?: selected).copy(
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
                val updated = linked.copy(mode = state.selectedMode, tieBreaker = state.tieBreaker ?: linked.tieBreaker)
                if (updated != linked || matchId == null) matches.upsert(updated)
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
            // The stored mode (e.g. a draft's default) is kept only if its counters are listed.
            setup.planForSeats(players, red, blue, mode = choice.mode, suggestedMode = match!!.mode)
        } else {
            setup.plan(players, choice.layoutId, choice.mode)
        }
        val layouts = if (locked) listOf(plan.layout) else setup.layouts(players)
        val storedCoin = match?.tieBreaker
        val quickAvailable = plan.modes.any { it.mode == GameMode.QUICK && it.available }
        return MatchSetupUiState(
            loading = false,
            players = players,
            playerRange = setup.playerRange,
            playersLocked = locked,
            seatsSource = when {
                !locked -> null
                rematch != null -> rematchLabel(rematch.kind)
                match?.draftId != null -> "From the drafted match"
                else -> "From the pending match"
            },
            recommended = setup.isRecommended(players),
            extendedNote = if (setup.isExtended(players)) rules.extendedCountNote else null,
            layouts = layouts.map(::layoutOption),
            selectedLayoutId = plan.layout.id,
            modes = plan.modes.map { modeOption(it, rules, players, plan.heroes) },
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
                houseRuleNote = houseRuleNote(setup.houseRuleDifference(plan), rules),
                modeNote = rules.mode(plan.selected.mode).rulesNote,
            ),
            tieBreaker = choice.tieBreaker ?: storedCoin,
            coinSource = when {
                choice.tieBreaker != null -> CoinSource.HERE
                storedCoin != null && match?.draftId != null -> CoinSource.DRAFT
                storedCoin != null -> CoinSource.MATCH
                else -> CoinSource.NONE
            },
            coinPlaced = storedCoin != null,
            correctingCoin = choice.correctingCoin,
            flips = choice.flips,
            tieBreakerRule = rules.tieBreaker,
            // The rulebook's "Quick game if anyone is new" (p.5) only applies when Quick can be chosen.
            firstGameAdvice = if (quickAvailable) rules.firstGameAdvice else "",
            linkedMatch = match?.let {
                LinkedMatchUi(
                    title = when {
                        rematch != null -> "Rematch · ${seats.size} heroes"
                        locked -> "Pending match · ${seats.size} heroes"
                        else -> "Pending match"
                    },
                    redSeats = red,
                    blueSeats = blue,
                )
            },
            presets = presets,
            selectedPresetId = selectedPresetId,
        )
    }

    private fun rematchLabel(kind: RematchKind) = when (kind) {
        RematchKind.SAME_SEATS -> "Rematch: same seats"
        RematchKind.SWAP_HEROES -> "Rematch: heroes swapped"
        RematchKind.SWAP_TEAMS -> "Rematch: sides swapped"
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

    private fun modeOption(option: ModeOption, rules: MatchRules, players: Int, heroes: Int) = ModeOptionUi(
        mode = option.mode,
        title = TimerText.modeLabel(option.mode),
        available = option.available,
        required = option.required,
        requiredLabel = when {
            !option.required -> null
            rules.mode(option.mode).requiredFor(players) -> "Required"
            else -> "Required (house rule)"
        },
        reason = when (option.block) {
            ModeBlock.TOO_FEW_HEROES -> {
                val rule = rules.mode(option.mode)
                rule.listedFromPlayers?.let {
                    "The rulebook lists ${TimerText.modeLabel(option.mode)} counters from $it players"
                } ?: "Needs ${rule.minHeroes} or more heroes"
            }

            ModeBlock.OTHER_MODE_REQUIRED -> requiredElsewhere(rules, players, heroes)

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

    /** Why a mode is blocked because another is required: the rulebook's rule, or the heroes-count house rule. */
    private fun requiredElsewhere(rules: MatchRules, players: Int, heroes: Int): String {
        val required = rules.modes.first { rule -> rule.requiredFromHeroes?.let { heroes >= it } == true }
        val map = if (required.lanes > 1) "the double-lane map" else TimerText.modeLabel(required.mode)
        val from = required.requiredFromPlayers ?: required.requiredFromHeroes
        return if (required.requiredFor(players)) {
            "The rulebook uses $map from $from players"
        } else {
            "House rule: $heroes heroes play on $map. The rulebook requires it from $from players"
        }
    }

    /**
     * "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) Life
     * counters." Null when the rulebook's per-player counters agree (a house-rule requirement is
     * labelled on the mode cards instead).
     */
    private fun houseRuleNote(difference: HouseRuleDifference?, rules: MatchRules): String? {
        val d = difference?.takeIf { it.lifeDiffers || it.modeNotListed } ?: return null
        val life = d.rulebookLife.entries.joinToString(" / ") { (mode, count) ->
            "$count (${TimerText.modeLabel(mode)})"
        }
        return buildList {
            add("House rule: counted as ${d.heroes} heroes.")
            if (d.lifeDiffers && life.isNotEmpty()) add("The rulebook gives ${d.players} players $life Life counters.")
            if (d.modeNotListed) {
                val from = rules.mode(d.mode).listedFromPlayers
                add("The rulebook lists ${TimerText.modeLabel(d.mode)} counters from $from players.")
            }
        }.joinToString(" ")
    }

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
