package dev.quinton.goa.relay

import kotlin.math.ceil
import kotlin.math.min

/**
 * Token buckets: one per client plus one global. Behind Tailscale Funnel every connection comes
 * from loopback, so the client key is the last `X-Forwarded-For` hop when present (added by the
 * trusted proxy in front of the relay) and the global bucket caps everything regardless.
 */
class RateLimiter(
    perClientPerMinute: Int,
    globalPerMinute: Int,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private class Bucket(val capacity: Double, val perNano: Double, var tokens: Double, var last: Long) {
        fun refill(now: Long) {
            tokens = min(capacity, tokens + (now - last) * perNano)
            last = now
        }

        fun secondsUntilToken(): Long = ceil((1.0 - tokens) / perNano / NANOS_PER_SECOND).toLong().coerceAtLeast(1)
    }

    private val clientCapacity = perClientPerMinute.toDouble()
    private val clientRate = perClientPerMinute / NANOS_PER_MINUTE
    private val clients = HashMap<String, Bucket>()
    private val global = Bucket(globalPerMinute.toDouble(), globalPerMinute / NANOS_PER_MINUTE, globalPerMinute.toDouble(), nanoTime())

    /** Takes a token for [client]; returns `null` when allowed, else the seconds to wait. */
    @Synchronized
    fun acquire(client: String): Long? {
        val now = nanoTime()
        if (clients.size > MAX_TRACKED_CLIENTS) prune(now)
        val bucket = clients.getOrPut(client) { Bucket(clientCapacity, clientRate, clientCapacity, now) }
        bucket.refill(now)
        global.refill(now)
        return when {
            bucket.tokens < 1.0 -> bucket.secondsUntilToken()
            global.tokens < 1.0 -> global.secondsUntilToken()
            else -> {
                bucket.tokens -= 1.0
                global.tokens -= 1.0
                null
            }
        }
    }

    private fun prune(now: Long) {
        clients.values.forEach { it.refill(now) }
        clients.entries.removeIf { it.value.tokens >= it.value.capacity }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MINUTE = 60 * NANOS_PER_SECOND
        const val MAX_TRACKED_CLIENTS = 10_000
    }
}
