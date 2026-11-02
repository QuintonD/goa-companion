package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.DecisionKind

/** One seat's current question: the [full] time it was given and the [deadline] that holds now. */
private class SeatClock(val ticket: Long, val full: Long, var deadline: Long)

/** The clocks of one game, from who is asked what and who is connected; no timers, the room ticks them. */
internal class SeatClocks(private val rules: RoomRules) {
    private val clocks = HashMap<String, SeatClock>()

    /** What the others are told in a window: fixed, or its moving would say a seat answered one of several. */
    var windowDeadline: Long? = null
        private set
    private var windowKey: WindowKey? = null

    private fun limit(kind: DecisionKind): Long =
        if (kind == DecisionKind.PLAY_CARD) rules.playCardMillis else rules.otherMillis

    fun deadline(seat: String): Long? = clocks[seat]?.deadline

    /** Starts a clock for each new question and drops those whose question has gone. */
    fun sync(asked: Map<String, AskedQuestion>, window: SimultaneousWindow?, now: Long, away: (String) -> Boolean) {
        clocks.keys.retainAll(asked.keys)
        for ((seat, q) in asked) {
            if (clocks[seat]?.ticket == q.ticket) continue
            val full = now + limit(q.kind)
            clocks[seat] =
                SeatClock(q.ticket, full, if (away(seat)) minOf(full, now + rules.disconnectedMillis) else full)
        }
        if (window?.key != windowKey) {
            windowKey = window?.key
            windowDeadline = window?.let { now + limit(it.publicKind) }
        }
    }

    /** A disconnected seat gets the short clock. */
    fun disconnected(seat: String, now: Long) {
        clocks[seat]?.let { it.deadline = minOf(it.deadline, now + rules.disconnectedMillis) }
    }

    /** Back in time: the question's own clock holds again. */
    fun returned(seat: String) {
        clocks[seat]?.let { it.deadline = maxOf(it.deadline, it.full) }
    }

    /** After a restart or a pause: every question has its whole clock again, whoever is connected. */
    fun restart() {
        clocks.clear()
        windowKey = null
        windowDeadline = null
    }

    /** The seats whose clock has run out, each with the ticket of the question it ran out on. */
    fun expired(now: Long): List<Pair<String, Long>> =
        clocks.filterValues { it.deadline <= now }.map { (seat, clock) -> seat to clock.ticket }.sortedBy { it.first }
}

/** What the clocks need of a question. */
internal class AskedQuestion(val ticket: Long, val kind: DecisionKind)
