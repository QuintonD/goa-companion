package dev.quinton.goa.core.domain

/** Injectable clocks so engines stay pure and testable with fakes. */
interface ElapsedClock {
    /** Monotonic milliseconds (Android: `SystemClock.elapsedRealtime()`). */
    fun elapsedMillis(): Long
}

interface WallClock {
    /** Epoch milliseconds. */
    fun nowMillis(): Long
}
