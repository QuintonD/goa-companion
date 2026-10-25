package dev.quinton.goa.core.game.parity

import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.jvmDoubleString
import kotlin.test.assertEquals

/** A file of the repository (`GOA_REPO_ROOT`, set by the build for both test targets). */
expect fun readRepoFile(path: String): String

/** A shipped catalog file, `app/src/main/assets/catalog/<name>`. */
fun catalogAsset(name: String): String = readRepoFile("app/src/main/assets/catalog/$name")

private val NUMBER = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")

/** [value]'s `toString()` with every number written as the JVM writes a Double (see core:domain's parity tests). */
fun canon(value: Any?): String = value.toString().replace(NUMBER) { jvmDoubleString(it.value.toDouble()) }

/** [transcript] must hash to [expected], the JVM's hash; on a mismatch the transcript is printed. */
fun assertTranscript(expected: String, transcript: String, name: String) {
    val actual = SyncHash.hex(SyncHash.sha256(transcript.encodeToByteArray()))
    if (actual != expected) println("TRANSCRIPT $name ($actual)\n$transcript\nEND TRANSCRIPT $name")
    assertEquals(expected, actual, "$name: the transcript differs from the JVM's (printed above)")
}
