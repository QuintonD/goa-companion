package dev.quinton.goa.web.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The web app's local storage (ADR-043): string values by key, persisted in IndexedDB
 * ([IndexedDbKeyValueStore]) and in memory in tests ([MemoryKeyValueStore]). Streams keep their
 * state under their own key prefix ("timer/…", "players/…"), JSON-encoded with [getJson]/[putJson].
 *
 * Safari may clear a website's storage after 7 days without a visit unless it is on the Home Screen
 * or storage is persistent (Settings asks), which is why synced data also lives with the group.
 */
interface KeyValueStore {
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String)

    suspend fun delete(key: String)

    /** The keys starting with [prefix], sorted. */
    suspend fun keys(prefix: String = ""): List<String>
}

/** The app's JSON: lenient to fields added later, so an older build reads a newer build's values. */
val StoreJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

suspend fun <T> KeyValueStore.getJson(key: String, serializer: KSerializer<T>): T? =
    get(key)?.let { runCatching { StoreJson.decodeFromString(serializer, it) }.getOrNull() }

suspend fun <T> KeyValueStore.putJson(key: String, value: T, serializer: KSerializer<T>) =
    put(key, StoreJson.encodeToString(serializer, value))

/** `store.getJson<Settings>("settings")`; null when missing or unreadable (never throws on old data). */
suspend inline fun <reified T> KeyValueStore.getJson(key: String): T? = getJson(key, serializer<T>())

suspend inline fun <reified T> KeyValueStore.putJson(key: String, value: T) = putJson(key, value, serializer<T>())

/** A view of this store under "[prefix]" (e.g. `store.scoped("timer/")`), so a stream can't clash with another. */
fun KeyValueStore.scoped(prefix: String): KeyValueStore = ScopedKeyValueStore(this, prefix)

private class ScopedKeyValueStore(private val inner: KeyValueStore, private val prefix: String) : KeyValueStore {
    override suspend fun get(key: String) = inner.get(prefix + key)

    override suspend fun put(key: String, value: String) = inner.put(prefix + key, value)

    override suspend fun delete(key: String) = inner.delete(prefix + key)

    override suspend fun keys(prefix: String) = inner.keys(this.prefix + prefix).map { it.removePrefix(this.prefix) }
}

/** In-memory [KeyValueStore] for tests and for browsers without IndexedDB (private mode fallbacks). */
class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    override suspend fun keys(prefix: String): List<String> = values.keys.filter { it.startsWith(prefix) }.sorted()
}
