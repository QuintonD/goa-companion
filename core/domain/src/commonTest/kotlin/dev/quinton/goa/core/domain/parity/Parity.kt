package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.jvmDoubleString
import kotlin.test.assertEquals

/** A file of the repository (`GOA_REPO_ROOT`, set by the build for both test targets). */
expect fun readRepoFile(path: String): String

/** A shipped catalog file, `app/src/main/assets/catalog/<name>`. */
fun catalogAsset(name: String): String = readRepoFile("app/src/main/assets/catalog/$name")

private val NUMBER = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")

/**
 * [value]'s `toString()` with every number written as the JVM writes a Double, so a transcript
 * reads the same on the JVM and in JavaScript (which writes `1200.0` as `1200`) unless the values
 * themselves differ.
 */
fun canon(value: Any?): String = value.toString().replace(NUMBER) { jvmDoubleString(it.value.toDouble()) }

/**
 * Asserts that [transcript] hashes to [expected], the hash the JVM produced when the test was
 * written: both targets must give the JVM's answers. On a mismatch the transcript is printed, so
 * the JVM and JS runs can be compared line by line.
 */
fun assertTranscript(expected: String, transcript: String, name: String) {
    val actual = SyncHash.hex(SyncHash.sha256(transcript.encodeToByteArray()))
    if (actual != expected) println("TRANSCRIPT $name ($actual)\n$transcript\nEND TRANSCRIPT $name")
    assertEquals(expected, actual, "$name: the transcript differs from the JVM's (printed above)")
}
