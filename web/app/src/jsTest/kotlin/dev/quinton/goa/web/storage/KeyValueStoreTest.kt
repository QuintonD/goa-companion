package dev.quinton.goa.web.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

@JsModule("fake-indexeddb")
@JsNonModule
private external object FakeIndexedDb {
    val indexedDB: dynamic
    val IDBKeyRange: dynamic
}

@Serializable
private data class TimerPrefs(val sounds: Boolean, val warnAtSeconds: Int = 30)

@Serializable
private data class TimerPrefsV1(val sounds: Boolean)

/** The same contract for the IndexedDB store (on fake-indexeddb in Node) and the in-memory one. */
abstract class KeyValueStoreContract {
    abstract fun newStore(): KeyValueStore

    @Test
    fun putGetAndDelete() = runTest {
        val store = newStore()
        assertNull(store.get("a"))
        store.put("a", "1")
        store.put("a", "2")
        assertEquals("2", store.get("a"))
        store.delete("a")
        assertNull(store.get("a"))
        store.delete("never-there")
    }

    @Test
    fun keysFilterByPrefixAndAreSorted() = runTest {
        val store = newStore()
        listOf("timer/b", "players/x", "timer/a", "timer").forEach { store.put(it, "v") }
        assertEquals(listOf("timer", "timer/a", "timer/b"), store.keys("timer"))
        assertEquals(listOf("timer/a", "timer/b"), store.keys("timer/"))
        assertEquals(4, store.keys().size)
        assertEquals(emptyList(), store.keys("zzz"))
    }

    @Test
    fun scopedStoresDoNotSeeEachOther() = runTest {
        val store = newStore()
        val timer = store.scoped("timer/")
        val draft = store.scoped("draft/")
        timer.put("state", "running")
        draft.put("state", "picking")
        assertEquals("running", timer.get("state"))
        assertEquals("picking", draft.get("state"))
        assertEquals(listOf("state"), timer.keys())
        assertEquals("running", store.get("timer/state"))
    }

    @Test
    fun jsonValuesRoundTripAndToleratesOlderAndNewerShapes() = runTest {
        val store = newStore()
        store.putJson("prefs", TimerPrefs(sounds = true, warnAtSeconds = 10))
        assertEquals(TimerPrefs(true, 10), store.getJson<TimerPrefs>("prefs"))
        // A newer build's extra field doesn't break an older reader, and vice versa.
        assertEquals(TimerPrefsV1(true), store.getJson<TimerPrefsV1>("prefs"))
        store.putJson("old", TimerPrefsV1(false))
        assertEquals(TimerPrefs(false, 30), store.getJson<TimerPrefs>("old"))
    }

    @Test
    fun unreadableJsonIsNullNotACrash() = runTest {
        val store = newStore()
        store.put("prefs", "{not json")
        assertNull(store.getJson<TimerPrefs>("prefs"))
        assertNull(store.getJson<TimerPrefs>("missing"))
    }

    @Test
    fun manyWritesAtOnceAllLand() = runTest {
        val store = newStore()
        (1..50).forEach { store.put("k/$it", "$it") }
        assertEquals(50, store.keys("k/").size)
        assertEquals("37", store.get("k/37"))
    }
}

class MemoryKeyValueStoreTest : KeyValueStoreContract() {
    override fun newStore(): KeyValueStore = MemoryKeyValueStore()
}

class IndexedDbKeyValueStoreTest : KeyValueStoreContract() {
    override fun newStore(): KeyValueStore {
        val global: dynamic = js("globalThis")
        global.indexedDB = FakeIndexedDb.indexedDB
        global.IDBKeyRange = FakeIndexedDb.IDBKeyRange
        return IndexedDbKeyValueStore(dbName = "test-${counter++}")
    }

    @Test
    fun valuesOutliveTheConnection() = runTest {
        val name = "reopen-${counter++}"
        newStore()
        IndexedDbKeyValueStore(dbName = name).put("a", "kept")
        assertEquals("kept", IndexedDbKeyValueStore(dbName = name).get("a"))
    }

    private companion object {
        var counter = 0
    }
}
