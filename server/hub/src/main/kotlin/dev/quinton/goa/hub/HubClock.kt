package dev.quinton.goa.hub

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The hub's clocks: wall time for `updatedAt` stamps and dates, monotonic time for the timer
 * engine (the same interfaces the phones inject). Tests pass a fake.
 *
 * [zone] is the group's zone: every date the web shows or reads (match dates, "today", game nights
 * grouped by gap, the group stats) uses it, as each phone uses its own. The hub's [Hub.clock] takes
 * it from `hub.json` (`goa-hub config --time-zone`), else the machine's zone.
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

    companion object {
        /**
         * [base]'s clocks in the zone [zone] gives (read on every use, so a config change applies
         * at once), or [base]'s own zone when it gives null.
         */
        fun zoned(base: HubClock, zone: () -> ZoneId?): HubClock = object : HubClock {
            override val zone: ZoneId get() = zone() ?: base.zone

            override fun nowMillis(): Long = base.nowMillis()

            override fun elapsedMillis(): Long = base.elapsedMillis()
        }
    }
}
