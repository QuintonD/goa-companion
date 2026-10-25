package dev.quinton.goa.feature.timer.session

import dev.quinton.goa.core.domain.timer.MatchDuration
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team

/** User and system commands on the running session (the phone's controller and the web app's alike). */
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

/**
 * The timer session's rules around [TimerEngine], shared by the phone's `TimerController` and the
 * web app's (ADR-043 "Table, W3"): what each command does to the snapshot, and what an ended
 * session writes into its linked match.
 */
object TimerSessions {
    /** [command] applied to [s] (a user Next is debounced against double taps). */
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

    /**
     * The linked [match] with the ended session's duration (and its mode) written in, or null when
     * there is nothing to write: the session isn't ended, or [onlyIfMissing] and the match has a
     * duration already. [matchElapsedMillis] is the session's readout.
     */
    fun durationUpdate(s: TimerSnapshot, match: Match, matchElapsedMillis: Long, onlyIfMissing: Boolean): Match? {
        if (s.status != TimerStatus.ENDED || s.matchId != match.id) return null
        if (onlyIfMissing && match.durationMin != null) return null
        return match.copy(durationMin = MatchDuration.minutes(matchElapsedMillis), mode = s.mode ?: match.mode)
    }

    /** "Session id : round : step : phase end": one key per countdown, so a phase end alerts once. */
    fun stepKey(s: TimerSnapshot, phaseEnd: Long?): String = "${s.sessionId}:${s.round}:${s.stepIndex}:$phaseEnd"
}
