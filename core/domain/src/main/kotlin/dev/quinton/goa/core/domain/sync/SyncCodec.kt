package dev.quinton.goa.core.domain.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable

/** Received sync data that can't be used; [message] is shown to the user. */
open class SyncFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Why a sync file can't be imported. */
enum class SyncFileProblem { NOT_A_SYNC_FILE, NOT_IN_A_GROUP, OTHER_GROUP, CANT_DECRYPT, INVALID }

/** Which side is older when a sync file and this app have different [SyncDocument.VERSION]s. */
enum class VersionGap {
    /** The file was made by a newer app: update this one. */
    FILE_NEWER,

    /** The file was made by an older app: update the phone that made it. */
    FILE_OLDER,
}

/** [version] is set when [problem] is [SyncFileProblem.INVALID] because the versions differ. */
class SyncFileException(
    val problem: SyncFileProblem,
    message: String,
    cause: Throwable? = null,
    val version: VersionGap? = null,
) : SyncFormatException(message, cause)

/**
 * Bytes for [SyncDocument]s: gzip'd [SyncJson] for the Nearby payloads, and an encrypted file for
 * the "Share sync file" fallback (ADR-025). Decoding always validates ([SyncValidation]).
 */
object SyncCodec {
    /** Refuse to inflate more than this (a few thousand matches are a few MB). */
    private const val MAX_PLAIN_BYTES = 32 * 1024 * 1024

    const val FILE_FORMAT = "goa-sync-file"
    const val FILE_EXTENSION = "goasync"
    const val FILE_MIME_TYPE = "application/vnd.dev.quinton.goa.sync"
    private const val FILE_VERSION = 1
    private const val SALT_BYTES = 16

    fun encode(doc: SyncDocument): ByteArray = gzip(
        SyncJson.encodeToString(SyncDocument.serializer(), doc).toByteArray(),
    )

    /** Inflates, parses and validates; throws [SyncFormatException]. */
    @Suppress("ThrowsCount")
    fun decode(bytes: ByteArray): SyncDocument {
        val doc = try {
            SyncJson.decodeFromString(SyncDocument.serializer(), gunzip(bytes).toString(Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            // Includes SerializationException.
            throw SyncFormatException("This isn't group sync data.", e)
        } catch (e: IOException) {
            throw SyncFormatException("The sync data is damaged.", e)
        }
        SyncValidation.problem(doc)?.let { throw SyncFormatException(it) }
        return doc
    }

    /**
     * "Share sync file": [doc] sealed with a key derived from [group]'s key and a fresh salt. The
     * group's name rides in clear only when [withGroupName] (a shared file names its group so a
     * stranger's phone can say which one to join); uploads to the sync server leave it out (ADR-036).
     */
    fun encodeFile(
        doc: SyncDocument,
        group: GroupCredentials,
        createdAt: Long,
        random: SecureBytes = SecureBytes.System,
        withGroupName: Boolean = true,
    ): ByteArray {
        val salt = random.next(SALT_BYTES)
        val sealed = SyncCrypto.seal(fileKey(group, salt), encode(doc), fileAad(group.groupId, doc.version), random)
        val envelope = FileEnvelope(
            groupId = group.groupId,
            groupName = if (withGroupName) group.name else "",
            documentVersion = doc.version,
            createdAt = createdAt,
            salt = b64(salt),
            data = b64(sealed),
        )
        return SyncJson.encodeToString(FileEnvelope.serializer(), envelope).toByteArray()
    }

    /** Opens a sync file for this phone's [group] (null = not in a group); throws [SyncFileException]. */
    @Suppress("ThrowsCount")
    fun decodeFile(bytes: ByteArray, group: GroupCredentials?): SyncDocument {
        val envelope = try {
            SyncJson.decodeFromString(FileEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
                .takeIf { it.format == FILE_FORMAT } ?: throw IllegalArgumentException("format")
        } catch (e: IllegalArgumentException) {
            throw SyncFileException(SyncFileProblem.NOT_A_SYNC_FILE, "This isn't a group sync file.", e)
        }
        problem(envelope, group)?.let { throw it }
        requireNotNull(group)
        val plain = try {
            val salt = unb64(envelope.salt)
            SyncCrypto.open(
                fileKey(group, salt),
                unb64(envelope.data),
                fileAad(envelope.groupId, envelope.documentVersion),
            )
        } catch (e: SyncCryptoException) {
            throw SyncFileException(SyncFileProblem.CANT_DECRYPT, CANT_OPEN, e)
        } catch (e: IllegalArgumentException) {
            throw SyncFileException(SyncFileProblem.CANT_DECRYPT, CANT_OPEN, e)
        }
        return try {
            decode(plain)
        } catch (e: SyncFormatException) {
            throw SyncFileException(SyncFileProblem.INVALID, e.message.orEmpty(), e)
        }
    }

    private fun problem(envelope: FileEnvelope, group: GroupCredentials?): SyncFileException? {
        val newer = envelope.version > FILE_VERSION || envelope.documentVersion > SyncDocument.VERSION
        return when {
            group == null -> SyncFileException(
                SyncFileProblem.NOT_IN_A_GROUP,
                "This file is for the group “${envelope.groupName}”. Join it first: scan its invite QR code.",
            )

            envelope.groupId != group.groupId -> SyncFileException(
                SyncFileProblem.OTHER_GROUP,
                "This file is from another group (“${envelope.groupName}”). " +
                    "This phone syncs with “${group.name}”.",
            )

            newer -> SyncFileException(
                SyncFileProblem.INVALID,
                "This file was made by a newer version of the app. Update the app first.",
                version = VersionGap.FILE_NEWER,
            )

            envelope.documentVersion < SyncDocument.VERSION -> SyncFileException(
                SyncFileProblem.INVALID,
                "This file was made by an older version of the app. Update the phone that made it and share again.",
                version = VersionGap.FILE_OLDER,
            )

            else -> null
        }
    }

    private const val CANT_OPEN =
        "This file can't be opened: it was changed, or it was made with a different key for this group."

    private fun fileKey(group: GroupCredentials, salt: ByteArray) =
        SyncCrypto.hkdf(group.key, salt, "goa-sync/file/v1|${group.groupId}")

    private fun fileAad(groupId: String, version: Int) = "goa-sync/file/v1|$groupId|$version".toByteArray()

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String) = Base64.getDecoder().decode(text)

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_PLAIN_BYTES) throw IOException("Sync data too large")
            }
        }
        return out.toByteArray()
    }

    @Serializable
    private data class FileEnvelope(
        val format: String = FILE_FORMAT,
        val version: Int = FILE_VERSION,
        val groupId: String,
        val groupName: String,
        val documentVersion: Int,
        val createdAt: Long,
        val salt: String,
        val data: String,
    )
}
