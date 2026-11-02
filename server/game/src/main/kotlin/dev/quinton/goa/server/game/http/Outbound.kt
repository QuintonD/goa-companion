package dev.quinton.goa.server.game.http

import dev.quinton.goa.core.online.ServerMessage
import kotlinx.coroutines.channels.Channel

/**
 * What one socket has yet to send. [offer] never waits, so a slow phone can't hold up its room. Only the
 * latest table of each game is kept, with the events of the tables it replaced, because a client that
 * misses an event can't get it back; everything else stays in order. A client that falls too far behind
 * even so is cut off: it reconnects and resumes from the events it holds. A long table the client asked for,
 * a catch-up, doesn't count as falling behind: it goes out in pages of [pageEvents], whatever its length.
 */
internal class Outbound(
    private val maxMessages: Int = DEFAULT_MAX_MESSAGES,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val pageEvents: Int = DEFAULT_PAGE_EVENTS,
) {
    private val queue = ArrayDeque<ServerMessage>()

    /** Per game, the events merged in while this client was behind: only these count against [maxEvents]. */
    private val behind = HashMap<String, Int>()
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
        if (replaced < 0) {
            behind.remove(table.gameId)
            queue.addLast(table)
            return
        }
        // At the tail, not in the old one's place: it is news to whatever was sent before it.
        val old = queue.removeAt(replaced) as ServerMessage.Table
        val latest = merged(old, table)
        val merged = (behind[table.gameId] ?: 0) + (latest.events.size - old.events.size).coerceAtLeast(0)
        if (merged > maxEvents) {
            shutLocked()
        } else {
            behind[table.gameId] = merged
            queue.addLast(latest)
        }
    }

    /** The next frame of [table]: all of it, or a page with the rest kept at the head of the queue. */
    private fun page(table: ServerMessage.Table): ServerMessage.Table {
        if (table.events.size <= pageEvents) {
            behind.remove(table.gameId)
            return table
        }
        val rest = table.copy(
            eventsFrom = table.eventsFrom + pageEvents,
            events = table.events.subList(pageEvents, table.events.size),
        )
        queue.addFirst(rest)
        return table.copy(events = table.events.subList(0, pageEvents), more = true)
    }

    /** Null once the queue is shut: the socket should close. */
    suspend fun next(): ServerMessage? {
        while (true) {
            synchronized(queue) {
                if (shut) return null
                queue.removeFirstOrNull()?.let { return if (it is ServerMessage.Table) page(it) else it }
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
        behind.clear()
    }

    companion object {
        const val DEFAULT_MAX_MESSAGES = 256
        const val DEFAULT_MAX_EVENTS = 4096
        const val DEFAULT_PAGE_EVENTS = 1000

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
