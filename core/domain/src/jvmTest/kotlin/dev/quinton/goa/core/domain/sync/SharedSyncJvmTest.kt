package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The shared (multiplatform) sync code against what the Android app used before ADR-043: the
 * `javax.crypto` primitives, `java.net` URL handling and the file bytes the old code wrote.
 */
class SharedSyncJvmTest {
    private val rng = Random(20261025)

    private fun bytes(max: Int) = ByteArray(rng.nextInt(max + 1)).also { rng.nextBytes(it) }

    @Test
    fun theJvmCipherPassesTheConformanceVectors() = runTest {
        assertThat(SyncCipherConformance.problems(JvmSyncCipher)).isEmpty()
    }

    @Test
    fun syncCodecStillWritesTheSameBytes() {
        // SyncVectors.FILE was written by SyncCodec before it moved to shared code.
        val file = SyncCodec.encodeFile(
            SyncVectors.fileDocument(),
            SyncVectors.group(),
            SyncVectors.FILE_CREATED_AT,
            SyncVectors.countingRandom(),
            withGroupName = false,
        )
        assertThat(String(file)).isEqualTo(SyncVectors.FILE)
        val sealed = SyncCrypto.seal(
            SyncVectors.sealKey(),
            SyncVectors.SEAL_PLAINTEXT.toByteArray(),
            SyncVectors.SEAL_AAD.toByteArray(),
        ) { SyncVectors.sealIv() }
        assertThat(Base64.getEncoder().encodeToString(sealed)).isEqualTo(SyncVectors.SEALED_BASE64)
        assertThat(SyncCodec.decode(SyncCodec.encode(SyncVectors.fileDocument()))).isEqualTo(SyncVectors.fileDocument())
    }

    @Test
    fun sharedHashesAgreeWithJavaxCrypto() {
        repeat(300) {
            val data = bytes(300)
            // javax.crypto refuses an empty key; sync never uses one.
            val key = bytes(140).takeIf { it.isNotEmpty() } ?: byteArrayOf(1)
            assertThat(SyncHash.sha256(data)).isEqualTo(MessageDigest.getInstance("SHA-256").digest(data))
            val parts = Array(rng.nextInt(4)) { bytes(80) }
            assertThat(SyncHash.hmac(key, *parts)).isEqualTo(SyncCrypto.hmac(key, *parts))
            val info = "label-${rng.nextInt()}-é"
            val length = rng.nextInt(1, 100)
            assertThat(SyncHash.hkdf(key, bytes(40), info, length).size).isEqualTo(length)
            val salt = bytes(40)
            assertThat(SyncHash.hkdf(key, salt, info, length)).isEqualTo(SyncCrypto.hkdf(key, salt, info, length))
            val id = "group-${rng.nextLong()}"
            assertThat(SyncHash.groupTag(id)).isEqualTo(SyncCrypto.groupTag(id))
        }
    }

    @Test
    fun formUrlAgreesWithJavaNet() {
        // Whole code points: a lone surrogate isn't text that either side can round-trip.
        val alphabet =
            "aZ09 .-*_+%&=?/#é€♛😀日~!'()\"\\\n\t".codePoints().toArray().map { String(Character.toChars(it)) } +
                listOf("%4", "%e2%99%9b", "%zz")
        repeat(3000) {
            val text = List(rng.nextInt(12)) { alphabet[rng.nextInt(alphabet.size)] }.joinToString("")
            val encoded = FormUrl.encode(text)
            assertWithMessage(text).that(encoded).isEqualTo(URLEncoder.encode(text, "UTF-8").replace("+", "%20"))
            assertWithMessage(text).that(FormUrl.decode(encoded)).isEqualTo(text)
            val expected = runCatching { URLDecoder.decode(text, "UTF-8") }
            val actual = runCatching { FormUrl.decode(text) }
            assertWithMessage(text).that(actual.getOrNull()).isEqualTo(expected.getOrNull())
        }
        // Escapes as Integer.parseInt reads them.
        assertThat(FormUrl.decode("%+f")).isEqualTo(URLDecoder.decode("%+f", "UTF-8"))
        assertThat(runCatching { FormUrl.decode("%-1") }.isFailure).isTrue()
    }

    /** SyncServerAddress.normalize as it was, on java.net.URI. */
    private fun reference(text: String, allowLocalHttp: Boolean = true): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val raw = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() }
        if (scheme == null || host == null) return null
        val pathOk = uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"
        val clean = uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null && pathOk
        val schemeOk = scheme == "https" ||
            (scheme == "http" && allowLocalHttp && host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
        val default = if (scheme == "https") 443 else 80
        val port = uri.port.takeIf { it != -1 && it != default }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port".takeIf { clean && schemeOk }
    }

    @Test
    fun serverAddressesAgreeWithJavaNetUri() {
        val corpus = listOf(
            "oldboi.taila4d38d.ts.net:8443", " HTTPS://Example.ORG:443/ ", "https://example.org:08443",
            "https://example.org:", "https://192.168.1.20:8443", "https://[::1]:8443", "https://[2001:DB8::1]",
            "http://localhost:8080", "http://10.0.2.2", "https://host.example.", "https://a-b.c-d.example", "",
            "http://example.org", "https://example.org/path", "https://example.org?x=1", "https://example.org#top",
            "https://user@example.org", "https://-a.example", "https://a-.example", "https://a..b", "https://1.2.3",
            "https://256.1.1.1", "https://my_host", "ftp:/example.org", "https://example.org:http",
            "https://example.org:99999999999", "1https://x", "https://bücher.example", "https://[::1",
            "https://[1:2:3:4:5:6:7:8:9]", "https://", "https://:8443", "https://1.", "https://a.1",
            "https://1.a", "https://010.0.0.1", "https://[::ffff:1.2.3.4]", "https://[1::2::3]",
            "https://[1:2:3:4:5:6:7::]", "https://[::]", "https://[fe80::1%25eth0]", "HTTP://LOCALHOST",
            "https://example.org:0", "https://a.b.c.d.e.f.example", "https://x.y-", "wss://example.org",
            "https://example.org//", "https://example.org/%20", "localhost:8443/",
        )
        val alphabet = "ab1.:-/[]@?#%_ "
        val random = List(4000) {
            val body = String(CharArray(rng.nextInt(1, 14)) { alphabet[rng.nextInt(alphabet.length)] })
            if (rng.nextBoolean()) "https://$body" else body
        }
        (corpus + random).forEach { input ->
            for (local in listOf(true, false)) {
                assertWithMessage("'$input' local=$local").that(SyncServerAddress.normalize(input, local))
                    .isEqualTo(reference(input, local))
            }
        }
    }
}
