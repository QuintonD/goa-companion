package dev.quinton.goa.core.presentation.timer

import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.TimerLayout

data class TimerUiState(
    val loading: Boolean = true,
    val start: StartUiState? = null,
    val active: ActiveUiState? = null,
    val summary: SummaryUiState? = null,
)

data class PresetOption(
    val id: String,
    val name: String,
    val summary: String,
    val usesActionTimer: Boolean,
)

/** A pending match on the Timer tab: mode and size, each team's heroes, and when it was set up. */
data class PendingMatchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    /** "Xargatha, Arien" (null when the team has no seats yet). */
    val red: String? = null,
    val blue: String? = null,
)

data class StartUiState(
    val presets: List<PresetOption>,
    val selectedPresetId: String,
    val pendingMatches: List<PendingMatchItem>,
    /** Heroes at the table, used to size the per-hero Action timer when no match is linked. */
    val seats: Int,
    /** Turn length for tonight (null when the selected preset has no timed Turn phase). */
    val tonight: TonightUi? = null,
) {
    val selected: PresetOption? get() = presets.firstOrNull { it.id == selectedPresetId }
}

/** Quick "tonight" turn length on the start card; [adjusted] = differs from the preset. */
data class TonightUi(val seconds: Int, val presetSeconds: Int) {
    val adjusted: Boolean get() = seconds != presetSeconds
}

/** The on-demand "Hurry up" countdown. */
data class ShotClockUi(
    val clock: String,
    val spokenClock: String,
    val fraction: Float,
    /** Reached zero. */
    val over: Boolean,
    /** Final seconds. */
    val warning: Boolean,
)

/** Team time-outs (a table rule): what one adds and how many each team has left this round. */
data class TimeOutsUi(
    val seconds: Int,
    val redLeft: Int,
    val blueLeft: Int,
    /** False on untimed steps, where a time-out has nothing to extend. */
    val usable: Boolean,
)

/** A hero seat of the linked match, for the Action timer. */
data class SeatUi(
    val seat: Int,
    val heroName: String,
    val heroId: String?,
    val portrait: String?,
    val team: Team,
    val playerName: String?,
    val resolved: Boolean,
    val acting: Boolean,
)

enum class ChecklistState { DONE, CURRENT, UPCOMING }

data class ChecklistItemUi(
    val number: Int,
    val title: String,
    val detail: String?,
    val state: ChecklistState,
    /** Name of the timer that runs on this step (e.g. "Upgrade"). */
    val timerName: String?,
    /** The step's id in match-setup.json (`level_up` …). */
    val id: String? = null,
)

enum class RoundRowState { DONE, CURRENT, UPCOMING }

/** One step of the round in the round view (one row per step of the plan). */
data class RoundRowUi(
    /** Index of the step in the round's plan (what "Jump to" moves to). */
    val index: Int,
    /** Section heading the row sits under: "Round start", "Turn 2", "End of round". */
    val group: String,
    /** "Play a card", "Action", "Level Up". */
    val title: String,
    /** "Hero 3 of 6 · Mrak · Ben", "Step 5 of 6 · Upgrade timer"; null when there is nothing to add. */
    val context: String?,
    /** "0:30", or "Untimed". */
    val length: String,
    val state: RoundRowState,
    /** Short name for the jump prompt: "Turn 2 · Play a card". */
    val jumpName: String,
    /** TalkBack, without the state: "Turn 2, Action, Hero 3 of 6". */
    val label: String,
    /** TalkBack length: "30 seconds", "untimed". */
    val spokenLength: String,
) {
    /** "Turn 2, Action, Hero 3 of 6, current, 34 seconds left"; [spokenClock] is the live clock. */
    fun description(spokenClock: String): String = when (state) {
        RoundRowState.DONE -> "$label, done"
        RoundRowState.CURRENT -> "$label, current, $spokenClock"
        RoundRowState.UPCOMING -> "$label, upcoming, $spokenLength"
    }
}

/** Round progress: optional round-start marker, one pip per turn, end-of-round marker. */
data class RoundPipsUi(
    val hasRoundStart: Boolean,
    val turns: Int,
    val section: RoundSection,
    val currentTurn: Int?,
)

data class ActiveUiState(
    val presetName: String,
    val round: Int,
    val title: String,
    val context: String,
    val kind: PhaseKind?,
    val section: RoundSection,
    val clock: String,
    val spokenClock: String,
    val fraction: Float,
    val timed: Boolean,
    val overtime: Boolean,
    /** Final seconds before zero (≤ the first cue threshold): styled as a warning. */
    val warning: Boolean,
    val paused: Boolean,
    val pips: RoundPipsUi,
    val checklist: List<ChecklistItemUi>?,
    val seats: List<SeatUi>?,
    val actionSlot: Int?,
    val actionSlots: Int,
    val nextLabel: String,
    val tieBreaker: Team?,
    val matchClock: String,
    val autoAdvance: Boolean,
    /** Notifications are denied or blocked: no lock-screen countdown or phase-over alert. */
    val alertsOff: Boolean = false,
    /** One-line rules reminder under the title (rulebook paraphrase). */
    val hint: String? = null,
    /** Running "Hurry up" countdown. */
    val shotClock: ShotClockUi? = null,
    /** Length of the preset's "Hurry up" (null = no button). */
    val shotClockSeconds: Int? = null,
    val timeOuts: TimeOutsUi? = null,
    /** Volume up = Next, volume down = Pause/Resume while this screen shows. */
    val volumeKeys: Boolean = false,
    /** Heroes of the linked match (for the level & defeat ladder's card exceptions). */
    val heroIds: List<String> = emptyList(),
    /** Index of the current step in the round's plan. */
    val stepIndex: Int = 0,
    /** Step by step (dial) or the whole round on one list; the device's choice. */
    val layout: TimerLayout = TimerLayout.STEPS,
    /** Every step of this round, in order (filled for [TimerLayout.ROUND] only). */
    val rows: List<RoundRowUi> = emptyList(),
) {
    /** Rule text of the current end-of-round step (e.g. Level Up is mandatory). */
    val currentDetail: String? get() = checklist?.firstOrNull { it.state == ChecklistState.CURRENT }?.detail
}

data class SummaryUiState(
    val duration: String,
    val durationMin: Int,
    val rounds: Int,
    val presetName: String,
    val mode: GameMode?,
    val hasMatch: Boolean,
)
