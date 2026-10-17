package dev.quinton.goa.core.domain.timer

/** A problem that blocks saving a preset; [phaseId] null = preset-level. */
data class PresetIssue(val kind: Kind, val phaseId: String? = null) {
    enum class Kind {
        BLANK_NAME,
        BLANK_PHASE_NAME,
        DUPLICATE_PHASE_ID,
        DURATION_OUT_OF_RANGE,
        CUE_OUT_OF_RANGE,
        TURNS_OUT_OF_RANGE,
        ACTION_NOT_PER_TURN,
        UNKNOWN_END_STEP,
        SHOT_CLOCK_OUT_OF_RANGE,
        TIME_OUTS_OUT_OF_RANGE,
    }
}

object TimerPresetValidator {
    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 60 * 60
    const val MAX_CUE_SECONDS = 10 * 60
    val TURNS_RANGE = 1..8
    val TIME_OUTS_RANGE = 0..3

    /** Hurry up and time-out settings (house rules). */
    private fun tableRuleIssues(preset: TimerPreset): List<PresetIssue> = buildList {
        val shot = preset.shotClockSeconds
        if (shot != null && shot !in MIN_SECONDS..MAX_CUE_SECONDS) {
            add(PresetIssue(PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE))
        }
        if (preset.timeOutsPerRound !in TIME_OUTS_RANGE || preset.timeOutSeconds !in MIN_SECONDS..MAX_CUE_SECONDS) {
            add(PresetIssue(PresetIssue.Kind.TIME_OUTS_OUT_OF_RANGE))
        }
    }

    fun validate(preset: TimerPreset, endStepIds: Set<String>): List<PresetIssue> = buildList {
        if (preset.name.isBlank()) add(PresetIssue(PresetIssue.Kind.BLANK_NAME))
        if (preset.turnsPerRound !in TURNS_RANGE) add(PresetIssue(PresetIssue.Kind.TURNS_OUT_OF_RANGE))
        addAll(tableRuleIssues(preset))
        val seen = mutableSetOf<String>()
        preset.phases.forEach { phase ->
            if (!seen.add(phase.id)) add(PresetIssue(PresetIssue.Kind.DUPLICATE_PHASE_ID, phase.id))
            if (phase.name.isBlank()) add(PresetIssue(PresetIssue.Kind.BLANK_PHASE_NAME, phase.id))
            val seconds = phase.seconds
            if (seconds != null && seconds !in MIN_SECONDS..MAX_SECONDS) {
                add(PresetIssue(PresetIssue.Kind.DURATION_OUT_OF_RANGE, phase.id))
            }
            if (phase.cues.any { it !in 0..MAX_CUE_SECONDS || (seconds != null && it >= seconds) }) {
                add(PresetIssue(PresetIssue.Kind.CUE_OUT_OF_RANGE, phase.id))
            }
            if ((phase.kind == PhaseKind.ACTION || phase.kind == PhaseKind.RESOLVE) && phase.scope != PhaseScope.TURN) {
                add(PresetIssue(PresetIssue.Kind.ACTION_NOT_PER_TURN, phase.id))
            }
            if (phase.scope == PhaseScope.ROUND_END && phase.endStepId != null && phase.endStepId !in endStepIds) {
                add(PresetIssue(PresetIssue.Kind.UNKNOWN_END_STEP, phase.id))
            }
        }
    }
}
