package dev.quinton.goa.feature.timer.ui.presets

import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.feature.timer.ui.timer.PresetOption

/** The preset list ("Timer presets"). */
data class PresetsUiState(
    val loading: Boolean = true,
    val presets: List<PresetOption> = emptyList(),
    val selectedId: String? = null,
) {
    val canDelete: Boolean get() = presets.size > 1
}

/** The preset editor. */
data class PresetEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val preset: TimerPreset? = null,
    val endSteps: List<EndOfRoundStep> = emptyList(),
    val issues: List<PresetIssue> = emptyList(),
    val showIssues: Boolean = false,
) {
    fun issuesFor(phaseId: String?): List<PresetIssue> = issues.filter { it.phaseId == phaseId }

    /** [preset] edited by [transform], re-validated against the end-of-round steps. */
    fun edit(transform: (TimerPreset) -> TimerPreset): PresetEditUiState {
        val current = preset ?: return this
        val updated = transform(current)
        return copy(preset = updated, issues = PresetEdits.validate(updated, endSteps))
    }

    companion object {
        /** The editor opened on [existing], or on a new preset made from [template] (the selected preset). */
        fun open(
            existing: TimerPreset?,
            template: TimerPreset?,
            endSteps: List<EndOfRoundStep>,
            newId: String,
        ): PresetEditUiState {
            val preset = existing ?: PresetEdits.newPreset(template, newId)
            return PresetEditUiState(
                loading = false,
                isNew = existing == null,
                preset = preset,
                endSteps = endSteps,
                issues = PresetEdits.validate(preset, endSteps),
            )
        }
    }
}

/**
 * Every edit the preset editor makes, as pure functions on a [TimerPreset] (shared by the phone's
 * `PresetEditViewModel` and the web app, ADR-043 "Table, W3").
 */
object PresetEdits {
    const val DEFAULT_NEW_SECONDS = 60

    /** Cue thresholds offered in the editor (seconds remaining). */
    val CUE_OPTIONS = listOf(60, 30, 10, 5, 3, 0)

    fun validate(preset: TimerPreset, endSteps: List<EndOfRoundStep>): List<PresetIssue> =
        TimerPresetValidator.validate(preset, endSteps.map { it.id }.toSet())

    /**
     * A new preset: a copy of [template] (the selected preset) under [id], with a blank name (the field
     * shows a placeholder instead of text the user has to delete) and auto-advance off.
     */
    fun newPreset(template: TimerPreset?, id: String): TimerPreset =
        (template ?: TimerPreset(id = "", name = "", phases = emptyList())).copy(
            id = id,
            name = "",
            autoAdvance = false,
            revision = 0,
        )

    /** "Duplicate": the same preset under [id], named "<name> copy". */
    fun duplicate(source: TimerPreset, id: String): TimerPreset = source.copy(id = id, name = "${source.name} copy")

    fun setTimeOutsPerRound(preset: TimerPreset, count: Int): TimerPreset =
        preset.copy(timeOutsPerRound = count.coerceIn(TimerPresetValidator.TIME_OUTS_RANGE))

    fun setTimeOutSeconds(preset: TimerPreset, seconds: Int?): TimerPreset =
        preset.copy(timeOutSeconds = seconds ?: preset.timeOutSeconds)

    /** Sets a phase's duration; null = untimed. Cues that no longer fit are dropped. */
    fun setSeconds(
        preset: TimerPreset,
        phaseId: String,
        seconds: Int?,
    ): TimerPreset = editPhase(preset, phaseId) { phase ->
        phase.copy(
            seconds = seconds,
            cues = if (seconds == null) emptyList() else phase.cues.filter { it < seconds }.ifEmpty { listOf(0) },
        )
    }

    fun toggleCue(preset: TimerPreset, phaseId: String, cue: Int): TimerPreset = editPhase(preset, phaseId) { phase ->
        val cues = if (cue in phase.cues) phase.cues - cue else (phase.cues + cue).sortedDescending()
        phase.copy(cues = cues)
    }

    /** Moves a phase up (-1) or down (+1) among the phases of the same scope. */
    fun move(preset: TimerPreset, phaseId: String, direction: Int): TimerPreset {
        val phases = preset.phases.toMutableList()
        val index = phases.indexOfFirst { it.id == phaseId }
        if (index < 0) return preset
        val scope = phases[index].scope
        val sameScope = phases.indices.filter { phases[it].scope == scope }
        val pos = sameScope.indexOf(index)
        val target = sameScope.getOrNull(pos + direction) ?: return preset
        phases[index] = phases[target].also { phases[target] = phases[index] }
        return preset.copy(phases = phases)
    }

    /** Adds a custom timer to [scope] under [id]. */
    fun addPhase(preset: TimerPreset, scope: PhaseScope, id: String): TimerPreset {
        val phase = PhaseSpec(
            id = id,
            name = when (scope) {
                PhaseScope.ROUND_START -> "Round start timer"
                PhaseScope.TURN -> "Turn timer"
                PhaseScope.ROUND_END -> "End of round timer"
            },
            kind = PhaseKind.CUSTOM,
            scope = scope,
            seconds = DEFAULT_NEW_SECONDS,
            cues = listOf(10, 0),
        )
        return preset.copy(phases = preset.phases + phase)
    }

    /** Removes a custom timer (the built-in kinds can only be switched off). */
    fun removePhase(preset: TimerPreset, phaseId: String): TimerPreset =
        preset.copy(phases = preset.phases.filterNot { it.id == phaseId && it.kind == PhaseKind.CUSTOM })

    fun editPhase(preset: TimerPreset, phaseId: String, transform: (PhaseSpec) -> PhaseSpec): TimerPreset =
        preset.copy(phases = preset.phases.map { if (it.id == phaseId) transform(it) else it })

    /** The preset as saved: the name trimmed. */
    fun forSave(preset: TimerPreset): TimerPreset = preset.copy(name = preset.name.trim())

    /** "Step 5: Level Up", or "After the checklist" when the timer isn't attached to a step. */
    fun endStepLabel(phase: PhaseSpec, endSteps: List<EndOfRoundStep>): String {
        val index = endSteps.indexOfFirst { it.id == phase.endStepId }
        return if (index >= 0) "Step ${index + 1}: ${endSteps[index].title}" else "After the checklist"
    }

    /** The editor's message for a phase's problem; null for preset-level issues (shown elsewhere). */
    fun issueText(issue: PresetIssue): String? = when (issue.kind) {
        PresetIssue.Kind.DURATION_OUT_OF_RANGE ->
            "Duration must be between ${TimerPresetValidator.MIN_SECONDS} s and " +
                "${TimerPresetValidator.MAX_SECONDS / 60} min."

        PresetIssue.Kind.CUE_OUT_OF_RANGE -> "Cues must be shorter than the phase."

        PresetIssue.Kind.BLANK_PHASE_NAME -> "Give this timer a name."

        PresetIssue.Kind.DUPLICATE_PHASE_ID -> "Duplicate timer."

        PresetIssue.Kind.ACTION_NOT_PER_TURN -> "The Action timer runs during turns only."

        PresetIssue.Kind.UNKNOWN_END_STEP -> "Pick an end-of-round step."

        PresetIssue.Kind.BLANK_NAME,
        PresetIssue.Kind.TURNS_OUT_OF_RANGE,
        PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE,
        PresetIssue.Kind.TIME_OUTS_OUT_OF_RANGE,
        -> null
    }

    /** The editor's message for a table-rule problem (Hurry up, time-outs); null for other issues. */
    fun tableRuleIssueText(issue: PresetIssue): String? = when (issue.kind) {
        PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE ->
            "Hurry up must be between ${TimerPresetValidator.MIN_SECONDS} s and 10 min."

        PresetIssue.Kind.TIME_OUTS_OUT_OF_RANGE ->
            "Time-outs: up to ${TimerPresetValidator.TIME_OUTS_RANGE.last}, each up to 10 min."

        else -> null
    }
}
