package dev.quinton.goa.web.table

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/**
 * [block]'s flow, or [fallback] when it can't be read (the group's data or the settings unavailable:
 * storage refused, the catalog offline before it was cached). A table tool keeps working with what it
 * has rather than failing the whole screen; the failure is logged.
 */
fun <T> resilient(what: String, fallback: T, block: () -> Flow<T>): Flow<T> = flow { emitAll(block()) }
    .catch { e ->
        if (e is CancellationException) throw e
        console.warn("$what unavailable", e)
        emit(fallback)
    }

/** Like [resilient], and emits [initial] first so a screen never waits on a slow source. */
fun <T> resilientStarting(what: String, initial: T, block: () -> Flow<T>): Flow<T> =
    resilient(what, initial, block).onStart { emit(initial) }
