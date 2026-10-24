package dev.quinton.goa.relay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupStoreTest {
    @TempDir
    lateinit var dir: Path

    private val hash = GroupStore.sha256Hex("a-token-for-the-store-test")

    @Test
    fun `parallel writers on real threads - one winner per revision`() = runBlocking {
        val store = GroupStore(dir)
        assertIs<PutResult.Created>(store.put(GROUP, hash, Precondition.IfNoneMatchAny, byteArrayOf(0)))
        for (round in 1L..5L) {
            val results = (1..32).map { i ->
                async(Dispatchers.IO) { store.put(GROUP, hash, Precondition.IfMatch(setOf(round)), byteArrayOf(i.toByte())) }
            }.awaitAll()
            assertEquals(listOf(PutResult.Updated(round + 1)), results.filterIsInstance<PutResult.Updated>())
            assertEquals(31, results.count { it == PutResult.PreconditionFailed })
        }
        val found = assertIs<GetResult.Found>(store.get(GROUP, hash, emptySet()))
        assertEquals(6, found.rev)
        assertContentEquals(found.bytes, assertIs<GetResult.Found>(GroupStore(dir).get(GROUP, hash, emptySet())).bytes)
    }

    private companion object {
        const val GROUP = "11111111-2222-3333-4444-555555555555"
    }
}
