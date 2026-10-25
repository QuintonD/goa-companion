package dev.quinton.goa.core.domain

/**
 * The little concurrency the shared core needs (ADR-043), for core:domain and core:game only: the
 * phone's engine and AI run on several threads; the browser's on one (a Web Worker has its own
 * copy of everything), where a lock is a plain call and a map needs no guarding.
 */
expect inline fun <R> synchronizedOn(lock: Any, block: () -> R): R

/** A map several threads may read and fill at once (`ConcurrentHashMap` on the JVM). */
expect fun <K : Any, V : Any> concurrentMap(): MutableMap<K, V>

/** Monotonic nanoseconds for deadlines (`System.nanoTime()` on the JVM); only differences mean anything. */
expect fun monotonicNanos(): Long
