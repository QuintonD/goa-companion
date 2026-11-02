package dev.quinton.goa.server.game.http

import dev.quinton.goa.core.online.ServerMessage
import kotlinx.coroutines.channels.Channel

/**
 * What one socket has yet to send. [offer] never waits, so a slow phone can't hold up its room. Only the
 * latest table of each game is kept, with the events of the tables it replaced, because a client that
 * misses an event can't get it back; everything else stays in order. A client too far behind even so is
 * cut off: it reconnects and resumes from the events it holds.
 */
internal class Outbound(
    private val maxMessages: Int = DEFAULT_MAX_MESSAGES,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    private val queue = ArrayDeque<ServerMessage>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var shut = false

    fun offer(message: ServerMessage) {
        synchronized(queue) {
            if (shut) return
            if (message is ServerMessage.Table) offerTable(message) else queue.addLast(message)
            if (queue.size > maxMessages) shutLocked()
        }
        wake.trySend(Unit)
    }

    private fun offerTable(table: ServerMessage.Table) {
        val replaced = queue.indexOfFirst { it is ServerMessage.Table && it.gameId == table.gameId }
        // At the tail, not in the old one's place: it is news to whatever was sent before it.
        val latest = if (replaced < 0) table else merged(queue.removeAt(replaced) as ServerMessage.Table, table)
        if (latest.events.size > maxEvents) shutLocked() else queue.addLast(latest)
    }

    /** Null once the queue is shut: the socket should close. */
    suspend fun next(): ServerMessage? {
        while (true) {
            synchronized(queue) {
                if (shut) return null
                queue.removeFirstOrNull()?.let { return it }
            }
            wake.receive()
        }
    }

    fun shut() {
        synchronized(queue) { shutLocked() }
        wake.trySend(Unit)
    }

    private fun shutLocked() {
        shut = true
        queue.clear()
    }

    companion object {
        const val DEFAULT_MAX_MESSAGES = 256
        const val DEFAULT_MAX_EVENTS = 4096

        /** [new] with [old]'s events in front of its own, each event once. */
        internal fun merged(old: ServerMessage.Table, new: ServerMessage.Table): ServerMessage.Table {
            val oldEnd = old.eventsFrom + old.events.size
            return when {
                new.events.isEmpty() -> new.copy(eventsFrom = old.eventsFrom, events = old.events)

                new.eventsFrom <= old.eventsFrom || new.eventsFrom > oldEnd -> new

                else -> new.copy(
                    eventsFrom = old.eventsFrom,
                    events = old.events.take(new.eventsFrom - old.eventsFrom) + new.events,
                )
            }
        }
    }
}
