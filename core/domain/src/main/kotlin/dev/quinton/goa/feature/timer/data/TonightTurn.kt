package dev.quinton.goa.feature.timer.data

import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerPreset
import kotlinx.serialization.Serializable

/**
 * "Tonight" turn length for one preset, used by the next start without editing the preset (the
 * Timer tab's ±15 s). Shared by the phone and the web app (ADR-043 "Table, W3").
 */
@Serializable
data class TonightTurn(val presetId: String, val seconds: Int) {
    /** [preset] with this turn length applied, if it is for that preset (cues that no longer fit are dropped). */
    fun applyTo(preset: TimerPreset): TimerPreset {
        if (presetId != preset.id) return preset
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

    companion object {
        /** One tap on − or + changes the length by this much. */
        const val STEP_SECONDS = 15
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 300

        /** The preset's own timed Turn length (null when it has none). */
        fun presetSeconds(preset: TimerPreset): Int? =
            preset.phases.firstOrNull { it.enabled && it.kind == PhaseKind.TURN }?.seconds

        /**
         * Tonight's length for [preset]: null (cleared) when [seconds] is null, the preset is unknown, or it
         * equals the preset's own length.
         */
        fun of(preset: TimerPreset?, seconds: Int?): TonightTurn? = when {
            seconds == null || preset == null || seconds == presetSeconds(preset) -> null
            else -> TonightTurn(preset.id, seconds)
        }

        /** [preset] with [tonight] applied, if one is set for it. */
        fun apply(preset: TimerPreset, tonight: TonightTurn?): TimerPreset = tonight?.applyTo(preset) ?: preset
    }
}
