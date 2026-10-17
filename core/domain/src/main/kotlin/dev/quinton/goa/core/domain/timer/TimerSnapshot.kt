package dev.quinton.goa.core.domain.timer

import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class TimerStatus { RUNNING, PAUSED, ENDED }

/** The hero seat (index into the session's seat list) resolving in an Action slot. */
@Serializable
data class ActionActor(val slot: Int, val seat: Int)

/**
 * Persisted timer state. Times are `elapsedRealtime` milliseconds (monotonic, survives Doze,
 * resets on reboot) plus a wall-clock anchor so the session can be rebased after a reboot.
 * Remaining time is always derived from timestamps, never from counted ticks.
 *
 * The snapshot embeds the preset and checklist it was started with, so editing presets
 * mid-game never corrupts a running session.
 */
@Serializable
data class TimerSnapshot(
    val sessionId: String,
    val preset: TimerPreset,
    val endOfRound: List<EndOfRoundStep>,
    val actionSlots: Int,
    val matchId: String? = null,
    val round: Int = 1,
    val stepIndex: Int = 0,
    val status: TimerStatus,
    /** Elapsed time at which the current step (re)started running; non-null iff RUNNING. */
    val runningSince: Long? = null,
    /** Step time accumulated before [runningSince] (from earlier pauses). */
    val accumulatedMillis: Long = 0,
    val matchStartElapsed: Long,
    val matchStartWall: Long,
    val endedElapsed: Long? = null,
    /** Mode chosen at match setup, if setup was used. */
    val mode: GameMode? = null,
    /** Team whose symbol shows on the Tie Breaker coin; flips during play on cross-team ties. */
    val tieBreaker: Team? = null,
    /** Who resolved in each Action slot of the current turn (only when heroes are known). */
    val actors: List<ActionActor> = emptyList(),
    /** Last time the snapshot was written: elapsed + wall pair, used to rebase after a reboot. */
    val anchorElapsed: Long,
    val anchorWall: Long,
    /** Device boot count when [anchorElapsed] was taken (null if unknown). */
    val bootCount: Int? = null,
) {
    @Transient
    val plan: TimerPlan = TimerPlan(preset, endOfRound, actionSlots)

    val step: TimerStep get() = plan.steps[stepIndex.coerceIn(0, plan.lastIndex)]

    /**
     * Returns this snapshot on the current boot's elapsed timeline, re-anchored at
     * ([elapsedNow], [wallNow]). After a reboot (boot count changed, or elapsed time went
     * backwards) every elapsed timestamp is shifted using the wall-clock anchor; time spent
     * powered off counts as elapsed. Without a reboot only the anchor moves, so a wall-clock
     * change (TIME_SET) is absorbed once the re-anchored snapshot is persisted, and a later
     * reboot rebases against the corrected wall clock.
     */
    fun rebased(elapsedNow: Long, wallNow: Long, currentBootCount: Int?): TimerSnapshot {
        val rebooted = (currentBootCount != null && bootCount != null && currentBootCount != bootCount) ||
            elapsedNow < anchorElapsed
        if (!rebooted) {
            return copy(anchorElapsed = elapsedNow, anchorWall = wallNow, bootCount = currentBootCount ?: bootCount)
        }
        // Map old-timeline t to new: t - anchorElapsed + anchorWall (wall time) - wallNow + elapsedNow.
        val shift = elapsedNow - wallNow + anchorWall - anchorElapsed
        return copy(
            runningSince = runningSince?.plus(shift),
            matchStartElapsed = matchStartElapsed + shift,
            endedElapsed = endedElapsed?.plus(shift),
            anchorElapsed = elapsedNow,
            anchorWall = wallNow,
            bootCount = currentBootCount,
        )
    }
}
