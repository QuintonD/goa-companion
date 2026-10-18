package dev.quinton.goa.hub.web

import dev.quinton.goa.core.domain.WallClock
import java.util.ArrayDeque

/**
 * At most [limit] recorded attempts per key in any [windowMs]; reaching the limit also locks the
 * key for [lockoutMs]. In memory; a restart forgets.
 */
class RateLimiter(
    private val limit: Int,
    private val windowMs: Long,
    private val clock: WallClock,
    private val lockoutMs: Long = 0,
) {
    private val attempts = HashMap<String, ArrayDeque<Long>>()
    private val lockedUntil = HashMap<String, Long>()

    /** True if [key] has used up its attempts (or is locked out). */
    @Synchronized
    fun isBlocked(key: String): Boolean {
        val now = clock.nowMillis()
        lockedUntil[key]?.let { if (now < it) return true else lockedUntil.remove(key) }
        val times = attempts[key] ?: return false
        while (times.isNotEmpty() && now - times.peekFirst() >= windowMs) times.pollFirst()
        return times.size >= limit
    }

    /** Records an attempt for [key]. */
    @Synchronized
    fun record(key: String) {
        val now = clock.nowMillis()
        if (attempts.size > MAX_KEYS) attempts.entries.removeIf { (_, times) -> isStale(times, now) }
        val times = attempts.getOrPut(key) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.peekFirst() >= windowMs) times.pollFirst()
        times.addLast(now)
        if (times.size >= limit && lockoutMs > 0) lockedUntil[key] = now + lockoutMs
    }

    private fun isStale(times: ArrayDeque<Long>, now: Long) = times.peekLast()?.let { now - it > windowMs } ?: true

    private companion object {
        const val MAX_KEYS = 10_000
    }
}

/**
 * Failed sign-in attempts (wrong or broken invites; a successful join never counts, so friends
 * joining from one home network aren't locked out). Three limits, so none can be sidestepped:
 * - per client ([clientKey]) when the client can be told apart: [PER_CLIENT] per 10 minutes;
 * - all clients that can't be told apart (behind Funnel without `X-Forwarded-For`) share one
 *   budget: [UNKNOWN_CLIENTS] per minute, then a [LOCKOUT_MS] lockout;
 * - every failure counts against [OVERALL] per minute (then the lockout), so rotating forged
 *   addresses doesn't help either.
 * The group key is 256 bits, so this is about noise and abuse, not about guessing.
 */
class JoinThrottle(clock: WallClock) {
    private val perClient = RateLimiter(PER_CLIENT, TEN_MINUTES, clock)
    private val unknown = RateLimiter(UNKNOWN_CLIENTS, MINUTE, clock, LOCKOUT_MS)
    private val overall = RateLimiter(OVERALL, MINUTE, clock, LOCKOUT_MS)

    fun isBlocked(clientKey: String?): Boolean =
        overall.isBlocked(ALL) || if (clientKey == null) unknown.isBlocked(ALL) else perClient.isBlocked(clientKey)

    fun recordFailure(clientKey: String?) {
        overall.record(ALL)
        if (clientKey == null) unknown.record(ALL) else perClient.record(clientKey)
    }

    companion object {
        const val PER_CLIENT = 10
        const val UNKNOWN_CLIENTS = 10
        const val OVERALL = 30
        const val MINUTE = 60_000L
        const val TEN_MINUTES = 10 * MINUTE
        const val LOCKOUT_MS = 2 * MINUTE
        private const val ALL = "*"
    }
}
