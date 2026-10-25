package dev.quinton.goa.core.data

/** Wall-clock time in epoch milliseconds, as the platform reports it ([EditClock.System]). */
internal expect fun epochMillis(): Long
