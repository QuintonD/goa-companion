package dev.quinton.goa.core.presentation.timer

import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.model.match.Team

/** User and system commands on the running session (the phone's controller and the hub's web timer). */
sealed interface TimerCommand {
    data object TogglePause : TimerCommand

    data object Pause : TimerCommand

    data object Resume : TimerCommand

    data object Next : TimerCommand

    data object Back : TimerCommand

    data object Restart : TimerCommand

    data object SkipToEndOfRound : TimerCommand

    data object EndMatch : TimerCommand

    data object AdvanceDue : TimerCommand

    data object FlipTieBreaker : TimerCommand

    /** Which team's symbol shows on the coin (null = stop tracking). */
    data class SetTieBreaker(val team: Team?) : TimerCommand

    data class HeroResolves(val seat: Int) : TimerCommand

    data object StartShotClock : TimerCommand

    data object CancelShotClock : TimerCommand

    data class TimeOut(val team: Team) : TimerCommand

    /** Round view: jump to step [stepIndex] of [round]; ignored once the session is in another round. */
    data class JumpTo(val round: Int, val stepIndex: Int) : TimerCommand
}

/** What each [TimerCommand] does to a session: one mapping onto the pure [TimerEngine]. */
object TimerCommands {
    fun apply(engine: TimerEngine, s: TimerSnapshot, command: TimerCommand): TimerSnapshot = when (command) {
        TimerCommand.TogglePause -> engine.togglePause(s)
        TimerCommand.Pause -> engine.pause(s)
        TimerCommand.Resume -> engine.resume(s)
        TimerCommand.Next -> engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS)
        TimerCommand.Back -> engine.back(s)
        TimerCommand.Restart -> engine.restartStep(s)
        TimerCommand.SkipToEndOfRound -> engine.skipToEndOfRound(s)
        TimerCommand.EndMatch -> engine.endMatch(s)
        TimerCommand.AdvanceDue -> engine.advanceDue(s)
        TimerCommand.FlipTieBreaker -> engine.flipTieBreaker(s)
        is TimerCommand.HeroResolves -> engine.heroResolves(s, command.seat)
        is TimerCommand.SetTieBreaker -> engine.setTieBreaker(s, command.team)
        TimerCommand.StartShotClock -> engine.startShotClock(s)
        TimerCommand.CancelShotClock -> engine.cancelShotClock(s)
        is TimerCommand.TimeOut -> engine.timeOut(s, command.team)
        is TimerCommand.JumpTo -> if (s.round == command.round) engine.jumpTo(s, command.stepIndex) else s
    }

    /** Identifies one phase end of one step, so each phase end alerts at most once. */
    fun stepKey(s: TimerSnapshot, phaseEnd: Long?): String = "${s.sessionId}:${s.round}:${s.stepIndex}:$phaseEnd"

    /** The session moved to another step (same session), e.g. a re-sync auto-advanced past a phase end. */
    fun advanced(from: TimerSnapshot, to: TimerSnapshot): Boolean =
        from.sessionId == to.sessionId && (from.round != to.round || from.stepIndex != to.stepIndex)
}
