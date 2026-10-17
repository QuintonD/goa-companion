package dev.quinton.goa.feature.timer.ui.timer

import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team

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

data class PendingMatchItem(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class StartUiState(
    val presets: List<PresetOption>,
    val selectedPresetId: String,
    val pendingMatches: List<PendingMatchItem>,
    /** Heroes at the table, used to size the per-hero Action timer when no match is linked. */
    val seats: Int,
) {
    val selected: PresetOption? get() = presets.firstOrNull { it.id == selectedPresetId }
}

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
)

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
)

data class SummaryUiState(
    val duration: String,
    val durationMin: Int,
    val rounds: Int,
    val presetName: String,
    val mode: GameMode?,
    val hasMatch: Boolean,
)
