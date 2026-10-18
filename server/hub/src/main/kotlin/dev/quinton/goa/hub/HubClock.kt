package dev.quinton.goa.hub

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The hub's clocks: wall time for `updatedAt` stamps and dates, monotonic time for the timer
 * engine (the same interfaces the phones inject). Tests pass a fake.
 */
interface HubClock : WallClock, ElapsedClock {
    val zone: ZoneId

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()

    object System : HubClock {
        override val zone: ZoneId get() = ZoneId.systemDefault()

        override fun nowMillis(): Long = java.lang.System.currentTimeMillis()

        override fun elapsedMillis(): Long = java.lang.System.nanoTime() / NANOS_PER_MILLI

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
