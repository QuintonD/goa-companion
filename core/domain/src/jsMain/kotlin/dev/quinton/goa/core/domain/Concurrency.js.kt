package dev.quinton.goa.core.domain

actual inline fun <R> synchronizedOn(lock: Any, block: () -> R): R = block()

actual fun <K : Any, V : Any> concurrentMap(): MutableMap<K, V> = HashMap()

private val origin = kotlin.time.TimeSource.Monotonic.markNow()

actual fun monotonicNanos(): Long = origin.elapsedNow().inWholeNanoseconds
