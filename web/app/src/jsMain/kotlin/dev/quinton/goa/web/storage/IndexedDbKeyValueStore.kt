package dev.quinton.goa.web.storage

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [KeyValueStore] in IndexedDB: one object store [storeName] in database [dbName], string keys and
 * string values. The connection is opened lazily and reopened when Safari closes it (it does, for a
 * backgrounded home-screen app) or another tab upgrades the database.
 */
class IndexedDbKeyValueStore(
    private val dbName: String = "goa",
    private val storeName: String = "kv",
) : KeyValueStore {
    private var connection: dynamic = null

    override suspend fun get(key: String): String? = transaction(READ) { it.get(key) } as? String

    override suspend fun put(key: String, value: String) {
        transaction(WRITE) { it.put(value, key) }
    }

    override suspend fun delete(key: String) {
        transaction(WRITE) { it.delete(key) }
    }

    override suspend fun keys(prefix: String): List<String> {
        val range: dynamic = if (prefix.isEmpty()) null else idbKeyRange().bound(prefix, prefix + "￿")
        val keys = transaction(READ) { it.getAllKeys(range) }.unsafeCast<Array<Any?>>()
        return keys.mapNotNull { it as? String }.sorted()
    }

    /**
     * Runs one request on the store and waits for it (and, for writes, for the commit). Retries once:
     * Safari closes a backgrounded app's connection, and the first try then fails.
     */
    @Suppress("TooGenericExceptionCaught") // IndexedDB fails with plain JS DOMExceptions
    private suspend fun transaction(mode: String, request: (store: dynamic) -> dynamic): dynamic {
        var failure: Throwable? = null
        repeat(2) {
            try {
                val db = open()
                val tx = db.transaction(storeName, mode)
                val committed = if (mode == WRITE) completion(tx) else null
                val result = awaitRequest(request(tx.objectStore(storeName)))
                committed?.invoke()
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failure = e
                connection = null
            }
        }
        throw failure ?: IllegalStateException("IndexedDB failed")
    }

    private suspend fun open(): dynamic {
        val existing = connection
        if (existing != null) return existing
        val factory: dynamic = js("globalThis.indexedDB") ?: throw IllegalStateException("IndexedDB is not available")
        val request = factory.open(dbName, VERSION)
        request.onupgradeneeded = { _: dynamic ->
            val db = request.result
            if (!(db.objectStoreNames.contains(storeName) as Boolean)) db.createObjectStore(storeName)
        }
        val db = awaitRequest(request)
        db.onversionchange = { _: dynamic ->
            db.close()
            connection = null
        }
        db.onclose = { _: dynamic -> connection = null }
        connection = db
        return db
    }

    /** Starts listening for [tx]'s end before any request runs; the returned function waits for it. */
    private fun completion(tx: dynamic): suspend () -> Unit {
        var done = false
        var error: Throwable? = null
        var waiter: ((Result<Unit>) -> Unit)? = null
        tx.oncomplete = { _: dynamic ->
            done = true
            waiter?.invoke(Result.success(Unit))
        }
        val fail = { _: dynamic ->
            val failure = IllegalStateException("IndexedDB write failed: ${tx.error?.message ?: "aborted"}")
            error = failure
            waiter?.invoke(Result.failure(failure))
        }
        tx.onerror = fail
        tx.onabort = fail
        return {
            error?.let { throw it }
            if (!done) {
                suspendCancellableCoroutine { cont -> waiter = { cont.resumeWith(it) } }
            }
        }
    }

    private suspend fun awaitRequest(request: dynamic): dynamic = suspendCancellableCoroutine { cont ->
        request.onsuccess = { _: dynamic -> cont.resume(request.result) }
        request.onerror = { _: dynamic ->
            cont.resumeWithException(IllegalStateException("IndexedDB: ${request.error?.message}"))
        }
    }

    private fun idbKeyRange(): dynamic = js("globalThis.IDBKeyRange")

    private companion object {
        const val VERSION = 1
        const val READ = "readonly"
        const val WRITE = "readwrite"
    }
}
