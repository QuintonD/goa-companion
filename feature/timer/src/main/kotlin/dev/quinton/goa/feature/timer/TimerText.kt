package dev.quinton.goa.feature.timer

import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStep
import dev.quinton.goa.core.model.match.GameMode
import kotlin.math.abs

/** Plain-text labels shared by the timer UI, notification and accessibility descriptions. */
object TimerText {
    private const val RESOLVE_HINT =
        "Cards revealed: resolve one by one in initiative order. No strategy talk: only state preferences, no reasons."

    fun stepTitle(step: TimerStep): String = when {
        step.endStep != null && step.phase != null -> "${step.phase!!.name} · ${step.endStep!!.title}"
        step.endStep != null -> step.endStep!!.title
        else -> step.phase?.name.orEmpty()
    }

    /** Where in the round this step sits, e.g. "Turn 3 of 4" or "End of round · step 5 of 6". */
    fun stepContext(step: TimerStep, preset: TimerPreset, endSteps: Int, actionSlots: Int): String = when {
        step.section == RoundSection.ROUND_START -> "Round start"

        step.section == RoundSection.TURN && step.isAction ->
            "Turn ${step.turn} of ${preset.turnsPerRound} · resolution ${(step.actionSlot ?: 0) + 1} of $actionSlots"

        step.section == RoundSection.TURN -> "Turn ${step.turn} of ${preset.turnsPerRound}"

        step.endStepNumber != null -> "End of round · step ${step.endStepNumber} of $endSteps"

        else -> "End of round"
    }

    /** Clock text: remaining time rounds up (a countdown shows 0:01 until it truly hits zero). */
    fun clock(readout: TimerReadout): String {
        val remaining = readout.remainingMillis
        return when {
            remaining == null -> duration(readout.elapsedInStepMillis, roundUp = false)
            remaining < 0 -> "+" + duration(abs(remaining), roundUp = false)
            else -> duration(remaining, roundUp = true)
        }
    }

    fun duration(millis: Long, roundUp: Boolean): String {
        val totalSeconds = if (roundUp) (millis + 999) / 1000 else millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /** "1:30" → "1 minute 30 seconds" for TalkBack. */
    fun spokenDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val parts = buildList {
            if (minutes > 0) add(if (minutes == 1L) "1 minute" else "$minutes minutes")
            if (seconds > 0 || minutes == 0L) add(if (seconds == 1L) "1 second" else "$seconds seconds")
        }
        return parts.joinToString(" ")
    }

    fun spokenClock(readout: TimerReadout): String {
        val remaining = readout.remainingMillis
        return when {
            remaining == null -> "${spokenDuration(readout.elapsedInStepMillis / 1000)} elapsed"
            remaining < 0 -> "Overtime, ${spokenDuration(abs(remaining) / 1000)}"
            else -> "${spokenDuration((remaining + 999) / 1000)} left"
        }
    }

    /**
     * One-line rules reminder under the step title. Paraphrases of the rulebook: table talk p.4
     * ("Between turns, you are free to discuss strategy… but it must be done out loud… Once the
     * Action cards are revealed… strategy discussions must stop… they may simply state their
     * preferences"; "without discussing their rationale"), turn sequence p.7 ("resolve them, one by one, in the
     * initiative order").
     */
    fun stepHint(step: TimerStep): String? = when (step.phase?.kind) {
        PhaseKind.STRATEGY -> "Talk strategy out loud. No notes, no showing cards."
        PhaseKind.TURN -> "Strategy talk out loud until the reveal."
        PhaseKind.RESOLVE -> RESOLVE_HINT
        else -> null
    }

    /** Label of the Next button: what comes next. */
    fun nextLabel(s: TimerSnapshot): String {
        val nextIndex = s.stepIndex + 1
        if (nextIndex > s.plan.lastIndex) return "Start round ${s.round + 1}"
        val next = s.plan.steps[nextIndex]
        val sameTurn = next.section == RoundSection.TURN && next.turn == s.step.turn
        return when {
            s.step.phase?.kind == PhaseKind.TURN && sameTurn &&
                (next.phase?.kind == PhaseKind.RESOLVE || next.isAction) -> "Cards revealed"

            next.isAction -> "Next hero"

            next.section == RoundSection.TURN && !sameTurn -> "Next: Turn ${next.turn}"

            else -> "Next: ${stepTitle(next)}"
        }
    }

    /** What comes next, for notifications: "Next: Turn 2", "Next: Cards revealed", "Next: Round 3". */
    fun nextText(s: TimerSnapshot): String {
        val label = nextLabel(s)
        return when {
            label.startsWith("Next") -> label
            label.startsWith("Start round") -> "Next: Round ${s.round + 1}"
            else -> "Next: $label"
        }
    }

    /** Title of the "time's up" alert, e.g. "Turn 2 · Round 1" or "Upgrade · Level Up · Round 3". */
    fun endedTitle(step: TimerStep, round: Int): String {
        val what = when {
            step.section == RoundSection.TURN && step.phase?.kind == PhaseKind.TURN -> "Turn ${step.turn}"
            step.section == RoundSection.TURN -> "${stepTitle(step)} · Turn ${step.turn}"
            else -> stepTitle(step)
        }
        return "$what · Round $round"
    }

    fun durationLabel(seconds: Int?): String = seconds?.let { duration(it * 1000L, roundUp = false) } ?: "Untimed"

    fun kindLabel(kind: PhaseKind): String = when (kind) {
        PhaseKind.STRATEGY -> "Strategy"
        PhaseKind.TURN -> "Turn"
        PhaseKind.RESOLVE -> "Resolve"
        PhaseKind.ACTION -> "Action"
        PhaseKind.UPGRADE -> "Upgrade"
        PhaseKind.CUSTOM -> "Custom"
    }

    fun modeLabel(mode: GameMode): String = when (mode) {
        GameMode.QUICK -> "Quick"
        GameMode.LONG -> "Long"
        GameMode.DOUBLE_LANE -> "Double lane"
    }

    /**
     * One-line preset summary in play order (round start, per turn, end of round), e.g.
     * "Pre-round strategy 1:30 · Play a card 0:30 ×4 · Resolve · Upgrade 2:00".
     */
    fun presetSummary(preset: TimerPreset): String {
        val enabled = preset.phases.filter { it.enabled }
        val actionOn = enabled.any { it.kind == PhaseKind.ACTION && it.scope == PhaseScope.TURN }
        val shown = enabled.filterNot { actionOn && it.kind == PhaseKind.RESOLVE }.sortedBy { it.scope.ordinal }
        val parts = shown.map { phase ->
            val base = "${phase.name} ${durationLabel(phase.seconds)}".let {
                if (phase.seconds == null) phase.name else it
            }
            when (phase.kind) {
                PhaseKind.TURN -> "$base ×${preset.turnsPerRound}"
                PhaseKind.ACTION -> "$base per hero"
                else -> base
            }
        }
        val summary = if (parts.isEmpty()) "Checklist only" else parts.joinToString(" · ")
        val untimed = enabled.all { it.seconds == null }
        return if (untimed && parts.isNotEmpty()) "Untimed · round and turn counters" else summary
    }
}
