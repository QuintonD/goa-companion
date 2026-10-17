package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.RandomSync.doc
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCodecTest {
    private val group =
        GroupCredentials("6f1c2d6e-8a3b-4c5d-9e0f-112233445566", ByteArray(32) { it.toByte() }, "Friday nights")
    private val ann = player("ann", "Ann")
    private val bob = player("bob", "Bob")
    private val valid =
        SyncDocument(players = listOf(ann, bob), matches = listOf(match("m1", listOf("ann"), listOf("bob"))))

    @Test
    fun hkdfMatchesRfc5869AndEveryLabelGivesItsOwnKey() {
        // RFC 5869 A.3: IKM = 22 × 0x0b, salt and info empty, L = 42.
        val okm = SyncCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), "", length = 42)
        assertThat(okm.toHex()).isEqualTo(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
        )
        val key = group.key
        assertThat(
            SyncCrypto.hkdf(key, ByteArray(0), "goa-sync/a"),
        ).isNotEqualTo(SyncCrypto.hkdf(key, ByteArray(0), "goa-sync/b"))
    }

    @Test
    fun sealedDataOpensOnlyWithTheSameKeyAndLabel() {
        val key = SyncCrypto.newGroupKey()
        val sealed = SyncCrypto.seal(key, "match log".toByteArray(), "label".toByteArray())
        assertThat(SyncCrypto.open(key, sealed, "label".toByteArray()).toString(Charsets.UTF_8)).isEqualTo("match log")
        assertThrows(SyncCryptoException::class.java) {
            SyncCrypto.open(SyncCrypto.newGroupKey(), sealed, "label".toByteArray())
        }
        assertThrows(SyncCryptoException::class.java) { SyncCrypto.open(key, sealed, "other".toByteArray()) }
        val tampered = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertThrows(SyncCryptoException::class.java) { SyncCrypto.open(key, tampered, "label".toByteArray()) }
    }

    @Test
    fun documentsRoundTripThroughGzip() = repeat(20) { seed ->
        val random = SyncMerge.normalize(Random(seed).doc())
        val doc = valid.copy(drafts = random.drafts, deletions = random.deletions, eloConfig = random.eloConfig)
        assertThat(SyncCodec.decode(SyncCodec.encode(doc))).isEqualTo(doc)
    }

    @Test
    fun anInvalidDocumentIsRefusedWithTheReason() {
        val bad = valid.copy(matches = listOf(match("both", listOf("ann"), listOf("ann"))))
        val error = assertThrows(SyncFormatException::class.java) { SyncCodec.decode(SyncCodec.encode(bad)) }
        assertThat(error.message).contains("Ann is on both teams")
        assertThrows(SyncFormatException::class.java) { SyncCodec.decode("not gzip".toByteArray()) }
    }

    @Test
    fun aSyncFileRoundTripsForItsGroup() {
        val file = SyncCodec.encodeFile(valid, group, createdAt = 5)
        assertThat(SyncCodec.decodeFile(file, group)).isEqualTo(valid)
        // The key never appears in the file.
        assertThat(String(file)).doesNotContain(java.util.Base64.getEncoder().encodeToString(group.key))
    }

    @Test
    fun aSyncFileFromAnotherGroupOrWithAnotherKeyGetsAClearError() {
        val file = SyncCodec.encodeFile(valid, group, createdAt = 5)
        val other = GroupCredentials.create("Tuesday crew")
        assertThat(problemOf(file, other)).isEqualTo(SyncFileProblem.OTHER_GROUP)
        assertThat(message(file, other)).contains("Friday nights")
        assertThat(problemOf(file, null)).isEqualTo(SyncFileProblem.NOT_IN_A_GROUP)
        // Same group id, different key (e.g. a rotated key).
        val rotated = GroupCredentials(group.groupId, SyncCrypto.newGroupKey(), group.name)
        assertThat(problemOf(file, rotated)).isEqualTo(SyncFileProblem.CANT_DECRYPT)
        assertThat(problemOf("{}".toByteArray(), group)).isEqualTo(SyncFileProblem.NOT_A_SYNC_FILE)
        assertThat(problemOf("a backup? no".toByteArray(), group)).isEqualTo(SyncFileProblem.NOT_A_SYNC_FILE)
    }

    @Test
    fun aTamperedSyncFileIsRefused() {
        val text = String(SyncCodec.encodeFile(valid, group, createdAt = 5))
        val data = text.substringAfter("\"data\":\"").substringBefore('"')
        val flipped = data.replaceRange(10, 11, if (data[10] == 'A') "B" else "A")
        assertThat(problemOf(text.replace(data, flipped).toByteArray(), group)).isEqualTo(SyncFileProblem.CANT_DECRYPT)
        // Claiming another document version breaks the authenticated label too.
        val relabelled = text.replace("\"documentVersion\":${SyncDocument.VERSION}", "\"documentVersion\":0")
        assertThat(problemOf(relabelled.toByteArray(), group)).isEqualTo(SyncFileProblem.INVALID)
    }

    @Test
    fun invitesRoundTripAndBrokenOnesAreRefused() {
        val invite = GroupInvite.format(group.withName("Friday nights & co"))
        assertThat(invite).startsWith("goa-sync:1?g=${group.groupId}&k=")
        val parsed = GroupInvite.parse(invite).getOrThrow()
        assertThat(parsed).isEqualTo(group.withName("Friday nights & co"))
        assertThat(GroupInvite.parse("https://example.com").isFailure).isTrue()
        assertThat(GroupInvite.parse("goa-sync:2?g=x").exceptionOrNull()?.message).contains("newer version")
        assertThat(GroupInvite.parse(invite.substringBefore("&k=")).exceptionOrNull()?.message).contains("incomplete")
        val shortKey = "goa-sync:1?g=${group.groupId}&k=AAAA&n=x"
        assertThat(GroupInvite.parse(shortKey).isFailure).isTrue()
        assertThat(GroupCredentials.create("  Friday   nights  ").name).isEqualTo("Friday nights")
        assertThat(group.toString()).doesNotContain(group.key.toHex())
    }

    @Test
    fun theGroupTagIsShortAndStable() {
        assertThat(SyncCrypto.groupTag(group.groupId)).hasLength(8)
        assertThat(SyncCrypto.groupTag(group.groupId)).isEqualTo(SyncCrypto.groupTag(group.groupId))
        assertThat(SyncCrypto.groupTag(group.groupId)).isNotEqualTo(SyncCrypto.groupTag("other"))
    }

    private fun problemOf(bytes: ByteArray, group: GroupCredentials?): SyncFileProblem? =
        runCatching { SyncCodec.decodeFile(bytes, group) }.exceptionOrNull()?.let { (it as SyncFileException).problem }

    private fun message(bytes: ByteArray, group: GroupCredentials?) =
        runCatching { SyncCodec.decodeFile(bytes, group) }.exceptionOrNull()?.message

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
