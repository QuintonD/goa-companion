package dev.quinton.goa.core.domain.timer

import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Timer presets (docs/DOMAIN.md "Timer phases"). The rulebook sets no timers: these are the
 * group's table rules, all editable. Defaults ship in `assets/catalog/timer-presets.json`.
 */

@Serializable
enum class PhaseKind {
    @SerialName("strategy")
    STRATEGY,

    @SerialName("turn")
    TURN,

    /** Per-hero resolution timer; expands to one step per hero seat. */
    @SerialName("action")
    ACTION,

    /**
     * Per-turn step after the reveal: cards resolve one by one in initiative order (rulebook
     * p.7). Usually untimed. Skipped when an Action timer is on, which then is the resolve step.
     */
    @SerialName("resolve")
    RESOLVE,

    @SerialName("upgrade")
    UPGRADE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class PhaseScope {
    /** Once per round, before turn 1. */
    @SerialName("round_start")
    ROUND_START,

    /** Every turn of the round. */
    @SerialName("turn")
    TURN,

    /** During the end of round (attached to a checklist step, or after it). */
    @SerialName("round_end")
    ROUND_END,
}

@Serializable
data class PhaseSpec(
    val id: String,
    val name: String,
    val kind: PhaseKind,
    val scope: PhaseScope,
    /** Countdown length; null = untimed (the step counts up and never alerts). */
    val seconds: Int?,
    val enabled: Boolean = true,
    /** Seconds-remaining thresholds that trigger a cue; 0 = time's up. */
    val cues: List<Int> = DEFAULT_CUES,
    /**
     * [PhaseScope.ROUND_END] only: id of the end-of-round step this timer runs on (the Upgrade
     * timer runs on `level_up`). Null = after the checklist.
     */
    val endStepId: String? = null,
) {
    val timed: Boolean get() = seconds != null

    companion object {
        val DEFAULT_CUES = listOf(10, 5, 0)
    }
}

@Serializable
data class TimerPreset(
    val id: String,
    val name: String,
    val phases: List<PhaseSpec>,
    /** Rulebook: a round is exactly 4 turns. */
    val turnsPerRound: Int = RULEBOOK_TURNS_PER_ROUND,
    /** Move on automatically when a timed phase reaches zero (otherwise overtime counts up). */
    val autoAdvance: Boolean = false,
    /**
     * Data revision of a bundled preset. Stored copies of a bundled preset with a lower revision
     * are upgraded on read by [TimerPresetMigration] (user-made presets are never touched).
     */
    val revision: Int = 0,
    /** Table rule: on-demand "Hurry up" shot clock length; null = no shot clock button. */
    val shotClockSeconds: Int? = DEFAULT_SHOT_CLOCK_SECONDS,
    /** Table rule: time-outs each team may call per round (0 = off). */
    val timeOutsPerRound: Int = 0,
    /** Table rule: seconds a time-out adds to the current phase. */
    val timeOutSeconds: Int = DEFAULT_TIME_OUT_SECONDS,
    /** Opt-in: while the timer screen shows, volume up = Next and volume down = Pause/Resume. */
    val volumeKeys: Boolean = false,
) {
    companion object {
        const val RULEBOOK_TURNS_PER_ROUND = 4
        const val DEFAULT_SHOT_CLOCK_SECONDS = 30
        const val DEFAULT_TIME_OUT_SECONDS = 40
    }
}

/** Contents of `assets/catalog/timer-presets.json`. */
@Serializable
data class TimerPresetCatalog(
    val schemaVersion: Int,
    val defaultPresetId: String,
    val presets: List<TimerPreset>,
)

/** Which part of the round a step belongs to. */
enum class RoundSection { ROUND_START, TURN, ROUND_END }

/**
 * One tap-through step of a round. A step is a timed or untimed phase, a checklist step, or a
 * checklist step with a phase timer attached (Upgrade on Level Up).
 */
data class TimerStep(
    val index: Int,
    val section: RoundSection,
    val phase: PhaseSpec?,
    /** 1-based turn for [RoundSection.TURN] steps. */
    val turn: Int? = null,
    /** 0-based resolution slot for Action steps (the n-th hero to resolve this turn). */
    val actionSlot: Int? = null,
    /** End-of-round checklist step, with its 1-based number. */
    val endStep: EndOfRoundStep? = null,
    val endStepNumber: Int? = null,
) {
    val seconds: Int? get() = phase?.seconds
    val timed: Boolean get() = phase?.seconds != null
    val durationMillis: Long? get() = phase?.seconds?.let { it * MILLIS_PER_SECOND }
    val cues: List<Int> get() = if (timed) phase?.cues.orEmpty() else emptyList()
    val isAction: Boolean get() = phase?.kind == PhaseKind.ACTION
}

internal const val MILLIS_PER_SECOND = 1_000L

/**
 * The fixed step sequence of every round for a preset:
 * ROUND_START phases → for each turn: TURN phases (Action expands per hero; Resolve is dropped
 * while an Action timer is on, because the Action steps are the resolution) → end-of-round
 * checklist with ROUND_END phases attached to their step → unattached ROUND_END phases.
 */
class TimerPlan(
    val preset: TimerPreset,
    val endOfRound: List<EndOfRoundStep>,
    /** Number of hero seats; the Action phase repeats once per seat each turn. */
    val actionSlots: Int,
) {
    val steps: List<TimerStep> = buildSteps()

    /** Index of the first end-of-round step (target of "skip to end of round"). */
    val firstEndOfRoundIndex: Int = steps.indexOfFirst { it.section == RoundSection.ROUND_END }
        .let { if (it < 0) steps.size else it }

    val lastIndex: Int get() = steps.lastIndex

    private fun buildSteps(): List<TimerStep> {
        val enabled = preset.phases.filter { it.enabled }
        val out = mutableListOf<TimerStep>()
        fun add(step: (Int) -> TimerStep) {
            out += step(out.size)
        }
        enabled.filter { it.scope == PhaseScope.ROUND_START }.forEach { phase ->
            add { TimerStep(it, RoundSection.ROUND_START, phase) }
        }
        val actionOn = enabled.any { it.scope == PhaseScope.TURN && it.kind == PhaseKind.ACTION }
        val turnPhases = enabled.filter {
            it.scope == PhaseScope.TURN && !(actionOn && it.kind == PhaseKind.RESOLVE)
        }
        for (turn in 1..preset.turnsPerRound.coerceAtLeast(1)) {
            turnPhases.forEach { phase ->
                if (phase.kind == PhaseKind.ACTION) {
                    repeat(actionSlots.coerceAtLeast(1)) { slot ->
                        add { TimerStep(it, RoundSection.TURN, phase, turn = turn, actionSlot = slot) }
                    }
                } else {
                    add { TimerStep(it, RoundSection.TURN, phase, turn = turn) }
                }
            }
        }
        val endPhases = enabled.filter { it.scope == PhaseScope.ROUND_END }
        val knownEndIds = endOfRound.map { it.id }.toSet()
        endOfRound.forEachIndexed { i, endStep ->
            val attached = endPhases.filter { it.endStepId == endStep.id }
            if (attached.isEmpty()) {
                add { TimerStep(it, RoundSection.ROUND_END, null, endStep = endStep, endStepNumber = i + 1) }
            } else {
                attached.forEach { phase ->
                    add { TimerStep(it, RoundSection.ROUND_END, phase, endStep = endStep, endStepNumber = i + 1) }
                }
            }
        }
        endPhases.filter { it.endStepId == null || it.endStepId !in knownEndIds }.forEach { phase ->
            add { TimerStep(it, RoundSection.ROUND_END, phase) }
        }
        return out
    }
}
