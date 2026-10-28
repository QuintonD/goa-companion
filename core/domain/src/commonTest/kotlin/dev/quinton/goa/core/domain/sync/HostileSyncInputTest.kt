package dev.quinton.goa.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * Received sync data is only as careful as the phone that wrote it, and a sync file can come from
 * anyone. Crafted input must end in the codec's own errors (shown to the person), never in another
 * exception that escapes the sync, and must behave the same on the JVM and in the browser (ADR-043,
 * web review R2).
 */
class HostileSyncInputTest {
    /** gzip is the identity here; the codec's parsing and validation are what is under test. */
    private object PlainCipher : SyncCipher {
        override suspend fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, iv: ByteArray) =
            iv + plaintext

        override suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
            if (sealed.size <
                SyncCipher.IV_BYTES + SyncCipher.TAG_BYTES
            ) {
                throw SyncCryptoException("Sealed data too short")
            }
            return sealed.copyOfRange(SyncCipher.IV_BYTES, sealed.size)
        }

        override suspend fun gzip(bytes: ByteArray) = bytes

        override suspend fun gunzip(bytes: ByteArray, maxBytes: Int) = bytes
    }

    private fun player(id: String, name: String = id) =
        """{"id":${quote(
            id,
        )},"name":${quote(name)},"color":1,"avatarId":0,"createdAt":1,"archived":false,"updatedAt":1}"""

    private fun match(id: String, playedAt: Long, heroes: List<String>, players: List<String>) = buildString {
        append("""{"id":${quote(id)},"createdAt":1,"playedAt":$playedAt,"status":"completed","mode":"quick",""")
        append(""""winner":"red","participants":[""")
        append(
            heroes.indices.joinToString(",") { i ->
                val team = if (i % 2 == 0) "red" else "blue"
                """{"playerId":${quote(players[i])},"heroId":${quote(heroes[i])},"team":"$team"}"""
            },
        )
        append("""],"updatedAt":1}""")
    }

    private fun document(players: List<String> = emptyList(), matches: List<String> = emptyList(), extra: String = "") =
        """{"format":"goa-sync","version":${SyncDocument.VERSION},"players":[${players.joinToString(",")}],""" +
            """"matches":[${matches.joinToString(",")}]$extra}"""

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Decodes [json] as received data: a [SyncFormatException] or a document, nothing else. */
    private suspend fun receive(json: String): Result<SyncDocument> = try {
        Result.success(SyncFileCodec.decode(json.encodeToByteArray(), PlainCipher))
    } catch (e: SyncFormatException) {
        Result.failure(e)
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        fail("Escaped the codec: ${e::class.simpleName}: ${e.message}")
    }

    @Test
    fun deeplyNestedJsonIsRefusedOrIgnoredWithoutBlowingTheStack() = runTest {
        val depth = 100_000
        val nested = "[".repeat(depth) + "]".repeat(depth)
        // In a field this version doesn't know (skipped) and in place of a known one (refused).
        receive(document(extra = ""","future":$nested"""))
        val refused = receive("""{"format":"goa-sync","version":${SyncDocument.VERSION},"players":$nested}""")
        assertTrue(refused.isFailure)
    }

    @Test
    fun idsThatAreJavaScriptObjectKeysAreOrdinaryIds() = runTest {
        val ids = listOf("__proto__", "constructor", "prototype", "toString", "hasOwnProperty", "valueOf")
        val doc = receive(document(players = ids.map { player(it) })).getOrThrow()
        assertEquals(ids.sorted(), doc.players.map { it.id }.sorted())
        val merged = SyncMerge.merge(SyncDocument.EMPTY, doc)
        assertEquals(ids.sorted(), merged.players.map { it.id }.sorted())
        // A duplicate of one of them is still caught.
        val twice = receive(document(players = listOf(player("__proto__"), player("__proto__", "Other"))))
        assertEquals("The data contains duplicate or empty ids.", twice.exceptionOrNull()?.message)
    }

    @Test
    fun aMatchFromTheEndOfTimeIsReportedNotThrown() = runTest {
        val people = listOf(player("a"), player("b"))
        listOf(Long.MAX_VALUE, Long.MIN_VALUE).forEach { playedAt ->
            // A hero listed twice makes the validator describe the match, date included.
            val bad = match("m", playedAt, listOf("arien", "arien"), listOf("a", "b"))
            val result = receive(document(people, listOf(bad)))
            val message = assertNotNull(result.exceptionOrNull()?.message, "playedAt $playedAt")
            assertTrue("hero arien is listed twice" in message, message)
        }
        val fine =
            receive(document(people, listOf(match("m", Long.MAX_VALUE, listOf("arien", "brogan"), listOf("a", "b")))))
        assertNull(fine.exceptionOrNull())
    }

    @Test
    fun markupInNamesIsKeptAsText() = runTest {
        val name = """<img src=x onerror="alert(1)">"""
        val doc = receive(document(players = listOf(player("p", name)))).getOrThrow()
        assertEquals(name, doc.players.single().name)
    }

    @Test
    fun aSyncFileFromAStrangerEndsInTheCodecsOwnErrors() = runTest {
        val group = GroupCredentials("6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", ByteArray(SyncKeys.KEY_BYTES), "G")
        val depth = 100_000
        val hostile = listOf(
            "",
            "null",
            "[]",
            "{\"format\":" + "[".repeat(depth) + "]".repeat(depth) + "}",
            """{"format":"goa-sync-file","version":1,"groupId":"${group.groupId}","groupName":"G",""" +
                """"documentVersion":${SyncDocument.VERSION},"createdAt":0,"salt":"!!","data":"!!"}""",
            """{"format":"goa-sync-file","version":1,"groupId":"${group.groupId}","groupName":"G",""" +
                """"documentVersion":${SyncDocument.VERSION},"createdAt":0,"salt":"","data":""}""",
            """{"__proto__":{"polluted":true},"format":"goa-sync-file"}""",
        )
        hostile.forEach { text ->
            try {
                SyncFileCodec.decodeFile(text.encodeToByteArray(), group, PlainCipher)
                fail("Accepted: ${text.take(60)}")
            } catch (_: SyncFileException) {
                // The person is told why; nothing else escapes.
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                fail("Escaped the codec for ${text.take(60)}: ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
