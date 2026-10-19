package dev.quinton.goa.core.presentation.timer

import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.core.domain.timer.TimerPresetMigration
import dev.quinton.goa.core.domain.timer.TimerPresetValidator

/**
 * A device's preset list, as the phone's TimerRepository keeps it and the hub keeps it per browser:
 * user-saved presets seeded from the bundled defaults, stored copies of bundled presets upgraded on
 * read ([TimerPresetMigration]), and "restore defaults" bringing back any bundled preset removed.
 * Pure list operations; the caller stores the result.
 */
object TimerPresets {
    /** The presets in use: [stored] (upgraded) when there are any, else the bundled ones. */
    fun effective(stored: List<TimerPreset>?, catalog: TimerPresetCatalog): List<TimerPreset> =
        stored?.takeIf { it.isNotEmpty() }?.let { TimerPresetMigration.upgrade(it, catalog.presets) } ?: catalog.presets

    /** The selected preset id, falling back to the bundled default (or the first preset). */
    fun selectedId(list: List<TimerPreset>, storedId: String?, catalog: TimerPresetCatalog): String =
        list.firstOrNull { it.id == storedId }?.id
            ?: list.firstOrNull { it.id == catalog.defaultPresetId }?.id
            ?: list.first().id

    /** Inserts or replaces [preset] (keeping its position). */
    fun save(list: List<TimerPreset>, preset: TimerPreset): List<TimerPreset> = if (list.any { it.id == preset.id }) {
        list.map { if (it.id == preset.id) preset else it }
    } else {
        list + preset
    }

    /** [list] without [id]; null when refused (the last preset, or an unknown id). */
    fun delete(list: List<TimerPreset>, id: String): List<TimerPreset>? {
        if (list.size <= 1 || list.none { it.id == id }) return null
        return list.filterNot { it.id == id }
    }

    /** Bundled presets missing from [list] come back, first. */
    fun restoreDefaults(list: List<TimerPreset>, catalog: TimerPresetCatalog): List<TimerPreset> =
        catalog.presets.filter { bundled -> list.none { it.id == bundled.id } } + list

    /** A copy of [source] under [newId], named "<name> copy". */
    fun duplicate(
        source: TimerPreset,
        newId: String,
    ): TimerPreset = source.copy(id = newId, name = "${source.name} copy")

    /** The preset's own timed Turn length (what "tonight" adjusts); null when it has none. */
    fun turnSeconds(preset: TimerPreset): Int? =
        preset.phases.firstOrNull { it.enabled && it.kind == PhaseKind.TURN }?.seconds

    /** [preset] with tonight's turn length [seconds] applied to its timed Turn phases (cues that don't fit go). */
    fun withTonight(preset: TimerPreset, seconds: Int?): TimerPreset {
        if (seconds == null) return preset
        return preset.copy(
            phases = preset.phases.map { phase ->
                if (phase.kind == PhaseKind.TURN && phase.seconds != null) {
                    phase.copy(seconds = seconds, cues = phase.cues.filter { it < seconds })
                } else {
                    phase
                }
            },
        )
    }

    /** "Tonight" turn lengths the start card offers. */
    const val TONIGHT_STEP_SECONDS = 15
    const val TONIGHT_MIN_SECONDS = 15
    const val TONIGHT_MAX_SECONDS = 300
}

/**
 * The preset editor's changes (the phone's PresetEditViewModel and the hub's preset editor). Each
 * returns the edited preset; the caller validates with [TimerPresetValidator] before saving.
 */
object PresetEdits {
    const val DEFAULT_NEW_SECONDS = 60
    private const val SECONDS_PER_MINUTE = 60

    /** Cue thresholds offered in the editor (seconds remaining). */
    val CUE_OPTIONS = listOf(60, 30, 10, 5, 3, 0)

    /** A new preset based on [template] (the selected one): blank name, so the field shows a placeholder. */
    fun newPreset(template: TimerPreset?, id: String): TimerPreset =
        (template ?: TimerPreset(id = "", name = "", phases = emptyList())).copy(
            id = id,
            name = "",
            autoAdvance = false,
            revision = 0,
        )

    fun editPhase(preset: TimerPreset, phaseId: String, transform: (PhaseSpec) -> PhaseSpec): TimerPreset =
        preset.copy(phases = preset.phases.map { if (it.id == phaseId) transform(it) else it })

    /** Sets the duration; null = untimed. Cues that no longer fit are dropped. */
    fun setSeconds(phase: PhaseSpec, seconds: Int?): PhaseSpec = phase.copy(
        seconds = seconds,
        cues = if (seconds == null) emptyList() else phase.cues.filter { it < seconds }.ifEmpty { listOf(0) },
    )

    fun toggleCue(phase: PhaseSpec, cue: Int): PhaseSpec {
        val cues = if (cue in phase.cues) phase.cues - cue else (phase.cues + cue).sortedDescending()
        return phase.copy(cues = cues)
    }

    fun setTimeOutsPerRound(preset: TimerPreset, count: Int): TimerPreset =
        preset.copy(timeOutsPerRound = count.coerceIn(TimerPresetValidator.TIME_OUTS_RANGE))

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

    /** What blocks saving, said under the phase it concerns; null for preset-level issues shown elsewhere. */
    fun issueText(issue: PresetIssue): String? = when (issue.kind) {
        PresetIssue.Kind.DURATION_OUT_OF_RANGE ->
            "Duration must be between ${TimerPresetValidator.MIN_SECONDS} s and " +
                "${TimerPresetValidator.MAX_SECONDS / SECONDS_PER_MINUTE} min."

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

    /** Only custom phases can be removed (the built-in kinds are switched off instead). */
    fun removePhase(preset: TimerPreset, phaseId: String): TimerPreset =
        preset.copy(phases = preset.phases.filterNot { it.id == phaseId && it.kind == PhaseKind.CUSTOM })
}
