package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.SyncCipher
import dev.quinton.goa.core.domain.sync.SyncCryptoException
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncFormatException
import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.core.domain.sync.SyncVectors
import dev.quinton.goa.core.domain.sync.VersionGap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The shared sync-file logic (envelope, labels, errors) and the invite format behave the same on
 * every platform. The cipher here is a stand-in (an HMAC-checked XOR, never for real data), so
 * the test runs without WebCrypto; the real ciphers are held to [SyncVectors] by
 * `SyncCipherConformance`.
 */
class SyncCodecParityTest {
    /** Not encryption: a keystream XOR with an HMAC tag, enough to check the codec around it. */
    private object StandInCipher : SyncCipher {
        override suspend fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, iv: ByteArray): ByteArray {
            val body = xor(key, iv, plaintext)
            return iv + body + SyncHash.hmac(key, aad, iv, body).copyOf(SyncCipher.TAG_BYTES)
        }

        override suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
            if (sealed.size < SyncCipher.IV_BYTES + SyncCipher.TAG_BYTES) throw SyncCryptoException("short")
            val iv = sealed.copyOfRange(0, SyncCipher.IV_BYTES)
            val body = sealed.copyOfRange(SyncCipher.IV_BYTES, sealed.size - SyncCipher.TAG_BYTES)
            val tag = sealed.copyOfRange(sealed.size - SyncCipher.TAG_BYTES, sealed.size)
            if (!SyncHash.same(tag, SyncHash.hmac(key, aad, iv, body).copyOf(SyncCipher.TAG_BYTES))) {
                throw SyncCryptoException("tag")
            }
            return xor(key, iv, body)
        }

        override suspend fun gzip(bytes: ByteArray): ByteArray = byteArrayOf(0x1f, 0x8b.toByte()) + bytes

        override suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray {
            require(bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) { "not gzip" }
            require(bytes.size - 2 <= maxBytes) { "too large" }
            return bytes.copyOfRange(2, bytes.size)
        }

        private fun xor(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val stream = SyncHash.hkdf(key, iv, "stand-in", maxOf(data.size, 1))
            return ByteArray(data.size) { (data[it].toInt() xor stream[it].toInt()).toByte() }
        }
    }

    private val group = SyncVectors.group()
    private val doc = SyncVectors.fileDocument()

    private suspend fun file(withName: Boolean = true) =
        SyncFileCodec.encodeFile(doc, group, 42L, StandInCipher, SyncVectors.countingRandom(), withName)

    @Test
    fun aFileRoundTripsAndItsEnvelopeIsStable() = runTest {
        val bytes = file()
        assertEquals(doc, SyncFileCodec.decodeFile(bytes, group, StandInCipher))
        assertTranscript(
            "0d2b5a803a0d4e97ddc0bc7dd2738684a12b7426a2ea9d887f66b43bbe1056c2",
            bytes.decodeToString(),
            "sync file envelope",
        )
        assertTrue("\"groupName\":\"\"" in file(withName = false).decodeToString())
    }

    @Test
    fun filesForOtherGroupsAndVersionsAreRefusedWithReasons() = runTest {
        val bytes = file()
        val other = GroupCredentials("0e1d2c3b-4a59-4687-9a6b-5c4d3e2f1a0b", group.key, "Others")
        assertEquals(
            SyncFileProblem.OTHER_GROUP,
            assertFailsWith<SyncFileException> { SyncFileCodec.decodeFile(bytes, other, StandInCipher) }.problem,
        )
        assertEquals(
            SyncFileProblem.NOT_IN_A_GROUP,
            assertFailsWith<SyncFileException> { SyncFileCodec.decodeFile(bytes, null, StandInCipher) }.problem,
        )
        val wrongKey = GroupCredentials(group.groupId, ByteArray(32) { 9 }, group.name)
        assertEquals(
            SyncFileProblem.CANT_DECRYPT,
            assertFailsWith<SyncFileException> { SyncFileCodec.decodeFile(bytes, wrongKey, StandInCipher) }.problem,
        )
        assertEquals(
            SyncFileProblem.NOT_A_SYNC_FILE,
            assertFailsWith<SyncFileException> {
                SyncFileCodec.decodeFile("{\"hello\":1}".encodeToByteArray(), group, StandInCipher)
            }.problem,
        )
        val newer = bytes.decodeToString().replace("\"documentVersion\":2", "\"documentVersion\":3")
        val e = assertFailsWith<SyncFileException> {
            SyncFileCodec.decodeFile(newer.encodeToByteArray(), group, StandInCipher)
        }
        assertEquals(VersionGap.FILE_NEWER, e.version)
        assertFailsWith<SyncFormatException> { SyncFileCodec.decode("plain".encodeToByteArray(), StandInCipher) }
    }

    @Test
    fun invitesAreWrittenAndReadAsThePhonesDo() {
        val invite = GroupInvite.format(group, "HTTPS://Oldboi.example:8443/")
        assertEquals(
            "goa-sync:1?g=6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8" +
                "&n=Friday%20%26%20co&s=https%3A%2F%2Foldboi.example%3A8443",
            invite,
        )
        val parsed = GroupInvite.parseInvite("Join us!\n\n$invite\n").getOrThrow()
        assertEquals(group, parsed.group)
        assertEquals("https://oldboi.example:8443", parsed.serverUrl)
        // The QR code is a link to the web app with the invite in the fragment; every parser reads it.
        val link = GroupInvite.link(invite)
        assertEquals("https://quintond.github.io/goa-companion/#join=$invite", link)
        assertEquals(parsed, GroupInvite.parseInvite(link).getOrThrow())
        assertEquals(parsed, GroupInvite.parseInvite(GroupInvite.shareText(group, parsed.serverUrl)).getOrThrow())
        assertTrue(GroupInvite.link(group) in GroupInvite.shareText(group))
        val unicode = GroupCredentials(group.groupId, group.key, "Café ♛ 日本 + 100%")
        assertEquals(unicode, GroupInvite.parse(GroupInvite.format(unicode)).getOrThrow())
        assertTrue("n=Caf%C3%A9%20%E2%99%9B%20%E6%97%A5%E6%9C%AC%20%2B%20100%25" in GroupInvite.format(unicode))
        // Old-style "+" for a space, unknown fields, a broken key, a newer version.
        val plus = "goa-sync:1?g=${group.groupId}&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=&n=A+B&x=1"
        assertEquals("A B", GroupInvite.parse(plus).getOrThrow().name)
        assertTrue(GroupInvite.parse("goa-sync:1?g=${group.groupId}&k=AAEC&n=x").isFailure)
        assertTrue(GroupInvite.parse("goa-sync:2?g=${group.groupId}").isFailure)
        assertTrue(GroupInvite.parse("goa-sync:1?g=6F1C2B8E-3D4A-4F5B-9C6D-7E8F9A0B1C2D&k=x").isFailure)
        assertEquals("Our group", GroupInvite.parse(plus.replace("n=A+B", "n=%E2%")).getOrThrow().name)
    }

    @Test
    fun newGroupsHaveAUuidAndAFreshKey() {
        val a = GroupCredentials.create("  Friday   night  ")
        val b = GroupCredentials.create("x", SecureBytes { ByteArray(it) { 7 } })
        assertEquals("Friday night", a.name)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(a.groupId))
        assertEquals(32, a.key.size)
        assertContentEquals(ByteArray(32) { 7 }, b.key)
    }

    @Test
    fun serverAddressesNormalizeAsBefore() {
        val cases = mapOf(
            "oldboi.taila4d38d.ts.net:8443" to "https://oldboi.taila4d38d.ts.net:8443",
            " HTTPS://Example.ORG:443/ " to "https://example.org",
            "https://example.org:08443" to "https://example.org:8443",
            "https://example.org:" to "https://example.org",
            "https://192.168.1.20:8443" to "https://192.168.1.20:8443",
            "https://[::1]:8443" to "https://[::1]:8443",
            "https://[2001:DB8::1]" to "https://[2001:db8::1]",
            "http://localhost:8080" to "http://localhost:8080",
            "http://10.0.2.2" to "http://10.0.2.2",
            "https://host.example." to "https://host.example.",
            "https://a-b.c-d.example" to "https://a-b.c-d.example",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, SyncServerAddress.normalize(input), input) }
        val refused = listOf(
            "", "  ", "http://example.org", "https://example.org/path", "https://example.org?x=1",
            "https://example.org#top", "https://user@example.org", "https://exa mple.org", "https://-a.example",
            "https://a-.example", "https://a..b", "https://1.2.3", "https://256.1.1.1", "https://my_host",
            "ftp:/example.org", "https://example.org:http", "https://example.org:99999999999", "1https://x",
            "https://bücher.example", "https://[::1", "https://[1:2:3:4:5:6:7:8:9]", "https://", "https://:8443",
        )
        refused.forEach { assertNull(SyncServerAddress.normalize(it), it) }
        assertNull(SyncServerAddress.normalize("http://localhost", allowLocalHttp = false))
    }
}
