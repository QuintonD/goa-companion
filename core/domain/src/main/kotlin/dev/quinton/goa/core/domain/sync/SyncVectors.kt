package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.model.player.Player
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * Fixed vectors for group sync (ADR-043), made by the Android app's own code (`javax.crypto`,
 * `java.util.zip`) before the core went multiplatform, plus the published ones (SYNC-SERVER.md).
 * Any [SyncCipher] (the browser's WebCrypto one in particular) must pass [SyncCipherConformance];
 * `SyncVectorsTest` holds the shared code to them on the JVM and in JavaScript.
 */
object SyncVectors {
    /** The group of SyncServerTest: key = bytes 0x00..0x1f. */
    const val GROUP_ID = "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d"
    const val GROUP_NAME = "Friday & co"

    fun groupKey(): ByteArray = ByteArray(SyncKeys.KEY_BYTES) { it.toByte() }

    fun group(): GroupCredentials = GroupCredentials(GROUP_ID, groupKey(), GROUP_NAME)

    /** [SyncServerToken.derive] of [group], and the SHA-256 (hex) of it the server stores. */
    const val TOKEN = "3LzQ_IsXcyHp8SiAV9FGqdB_TQmU1C8PXkjjvBbDTIA"
    const val TOKEN_HASH = "f8f51f43e4c066e0ca7149d763ec4149881e6890d9a8330491c10add0af944ff"

    /** docs/SYNC-SERVER.md's vector (same key, this group id), asserted by the relay too. */
    const val RELAY_GROUP_ID = "8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f"
    const val RELAY_TOKEN = "dFROVzWTpa17AzhaeXje8rW5l1okK5Q9nuEeVerIQTI"
    const val RELAY_TOKEN_HASH = "cfb0311773dcc2650c1160044e1f82ed615b49d23428d9b9c935d82d01399e25"

    /** [SyncHash.groupTag] of [GROUP_ID]. */
    const val GROUP_TAG = "8b3dcc9d"

    /** Key of the seal, HKDF and HMAC vectors: byte i = i * 7. */
    fun sealKey(): ByteArray = ByteArray(SyncKeys.KEY_BYTES) { (it * 7).toByte() }

    /** HKDF-SHA256(ikm = [sealKey], salt = "salt", info = "goa-sync/file/v1|x", 32 bytes), hex. */
    const val HKDF_HEX = "343bf7e2372381bb381ed37a28c50744417d8abdbd18842e32b00115de9c01cf"

    /** [SyncHash.hmac] (length-prefixed parts) of "ab", "c" under [sealKey], hex. */
    const val HMAC_HEX = "59c99f4a4a3c0116768643ae9148e412b70afa3c5b878b75f2d78278431e0170"

    /** AES-256-GCM of [SEAL_PLAINTEXT] under [sealKey], AAD [SEAL_AAD], IV [sealIv]: `iv ‖ ct ‖ tag`. */
    const val SEAL_PLAINTEXT = "hello, group"
    const val SEAL_AAD = "aad|1"
    const val SEALED_BASE64 = "oKGio6SlpqeoqaqrsE0ZM5oXE8xh/BA08RaDApEV/ZkoGJtHFJTYTA=="

    fun sealIv(): ByteArray = ByteArray(SyncCipher.IV_BYTES) { (0xa0 + it).toByte() }

    /**
     * [SyncFileCodec.encodeFile] of [fileDocument] for [group] at [FILE_CREATED_AT] without the group
     * name, the "random" bytes counting up from 0x40 (salt, then IV): what a phone uploads to the relay.
     */
    const val FILE_CREATED_AT = 1_700_000_000_000L
    const val FILE =
        "{\"format\":\"goa-sync-file\",\"version\":1,\"groupId\":\"6f1c2b8e-3d4a-4f5b-9c" +
            "6d-7e8f9a0b1c2d\",\"groupName\":\"\",\"documentVersion\":2,\"createdAt\":170000" +
            "0000000,\"salt\":\"QEFCQ0RFRkdISUpLTE1OTw==\",\"data\":\"UFFSU1RVVldYWVpb3SRX" +
            "ENHSPjXL/m6D8rmijXVM+5Aj5dCz1GltszoBwvXGbfLEFZus2jKazEj8YlIn/sxyjehIGj" +
            "6Yi9djP20KyNu+iQdvIVHT+FKqRpVywg7FpIFv+PBo/bFZOnadPHliMz4VekLdw9H46zix" +
            "/S97gm2I1qnNA1kjuOCMTXr1lEXWd2MSdegD7szJGkRiB0khEZNRdKCZ+042FkcIsJrpfB" +
            "ckBmqa39EBxo3VcP1//wjDCtKIYVW5e4nNMLvR+CgxM6tj6qycK9NlgFSZtpmpk88mDC4w" +
            "EYMWOdz/PvxZg5xYBXcYfJlWVbHqHGN36GVZQKbWLnGK58L/z2La8CFnwBo06svSapGYWT" +
            "UJhWETIgN1THP+nwcwGR4KHnYugzlZC3QOapFUrsEbLhp8DoSUlePACji52Ns+6KD6XJVG" +
            "\"}"

    fun fileDocument(): SyncDocument = SyncDocument(
        players = listOf(
            Player(id = "p1", name = "Ann", color = 1, avatarId = 0, createdAt = 0, archived = false, updatedAt = 5),
        ),
    )

    /** [SyncJson] of [fileDocument]: what every platform must write, byte for byte (merge tie-breaks). */
    const val FILE_DOCUMENT_JSON =
        "{\"format\":\"goa-sync\",\"version\":2,\"players\":[{\"id\":\"p1\",\"name\":\"Ann\",\"c" +
            "olor\":1,\"avatarId\":0,\"createdAt\":0,\"archived\":false,\"updatedAt\":5}],\"m" +
            "atches\":[],\"drafts\":[],\"triviaRounds\":[],\"deletions\":[],\"playerMerges\"" +
            ":[],\"eloConfig\":{\"config\":{\"initialRating\":1200.0,\"kProvisional\":40.0," +
            "\"kEstablished\":20.0,\"provisionalGames\":10,\"heroInfluence\":0.5,\"pilotIn" +
            "fluence\":0.5,\"seatAdvantage\":0.0,\"doublePilotWeight\":0.5,\"quickMultipl" +
            "ier\":1.0,\"longMultiplier\":1.0,\"doubleLaneMultiplier\":1.0},\"updatedAt\":" +
            "0}}"

    /** The counting "random" source [FILE] was made with. */
    fun countingRandom(): SecureBytes {
        var next = 0x40
        return SecureBytes { size -> ByteArray(size) { (next++).toByte() } }
    }
}

/** Checks a [SyncCipher] against [SyncVectors]: an empty list means it seals and opens like the phones. */
object SyncCipherConformance {
    @Suppress("TooGenericExceptionCaught", "LongMethod")
    suspend fun problems(cipher: SyncCipher): List<String> {
        val out = mutableListOf<String>()

        suspend fun check(what: String, block: suspend () -> Boolean) {
            val ok = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                out += "$what: threw $e"
                return
            }
            if (!ok) out += what
        }

        suspend fun refuses(what: String, block: suspend () -> Unit) {
            try {
                block()
                out += "$what: accepted"
            } catch (e: CancellationException) {
                throw e
            } catch (_: SyncCryptoException) {
                // As required.
            } catch (e: Throwable) {
                out += "$what: threw $e instead of SyncCryptoException"
            }
        }

        val sealed = Base64.Default.decode(SyncVectors.SEALED_BASE64)
        val plain = SyncVectors.SEAL_PLAINTEXT.encodeToByteArray()
        val aad = SyncVectors.SEAL_AAD.encodeToByteArray()
        val key = SyncVectors.sealKey()
        check("seal gives the phones' bytes") {
            cipher.seal(key, plain, aad, SyncVectors.sealIv()).contentEquals(sealed)
        }
        check("open reads the phones' bytes") { cipher.open(key, sealed, aad).contentEquals(plain) }
        refuses("open with another AAD") { cipher.open(key, sealed, "aad|2".encodeToByteArray()) }
        refuses("open with another key") { cipher.open(SyncVectors.groupKey(), sealed, aad) }
        refuses("open of tampered bytes") {
            cipher.open(key, sealed.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }, aad)
        }
        refuses("open of too short an input") { cipher.open(key, ByteArray(10), aad) }
        val text = ByteArray(5000) { (it % 7).toByte() }
        check("gunzip(gzip(x)) is x") {
            cipher.gunzip(cipher.gzip(text), SyncFileCodec.MAX_PLAIN_BYTES).contentEquals(text)
        }
        check("gunzip refuses more than maxBytes") { runCatching { cipher.gunzip(cipher.gzip(text), 100) }.isFailure }
        check("gunzip refuses what isn't gzip") {
            runCatching { cipher.gunzip("not gzip".encodeToByteArray(), 1000) }.isFailure
        }
        check("decodeFile opens a phone's upload") {
            SyncFileCodec.decodeFile(SyncVectors.FILE.encodeToByteArray(), SyncVectors.group(), cipher) ==
                SyncVectors.fileDocument()
        }
        check("encodeFile, then decodeFile, gives the document back") {
            val bytes = SyncFileCodec.encodeFile(
                SyncVectors.fileDocument(),
                SyncVectors.group(),
                SyncVectors.FILE_CREATED_AT,
                cipher,
                SyncVectors.countingRandom(),
                withGroupName = false,
            )
            SyncFileCodec.decodeFile(bytes, SyncVectors.group(), cipher) == SyncVectors.fileDocument()
        }
        return out
    }
}
