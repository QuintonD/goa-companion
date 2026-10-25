package dev.quinton.goa.core.domain

import java.util.concurrent.ConcurrentHashMap

actual inline fun <R> synchronizedOn(lock: Any, block: () -> R): R = synchronized(lock, block)

actual fun <K : Any, V : Any> concurrentMap(): MutableMap<K, V> = ConcurrentHashMap()

actual fun monotonicNanos(): Long = System.nanoTime()
