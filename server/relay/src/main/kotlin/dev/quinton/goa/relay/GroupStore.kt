package dev.quinton.goa.relay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** `groups/<groupId>/meta.json`. The server never sees more than this about a group. */
@Serializable
data class GroupMeta(
    /** SHA-256 of the bearer token's ASCII bytes, lowercase hex (set by the first upload). */
    val tokenHash: String,
    val rev: Long,
    /** Epoch milliseconds of the last accepted upload. */
    val updatedAt: Long,
    val size: Long,
)

/** What a PUT asks for: `If-Match: "<rev>"[, ...]` / `If-Match: *`, or `If-None-Match: *`. */
sealed interface Precondition {
    /** [revs] == null means `If-Match: *` (any existing document). */
    data class IfMatch(val revs: Set<Long>?) : Precondition

    data object IfNoneMatchAny : Precondition
}

sealed interface GetResult {
    data object NotFound : GetResult

    data object Unauthorized : GetResult

    data class NotModified(val rev: Long) : GetResult

    data class Found(val rev: Long, val bytes: ByteArray) : GetResult
}

sealed interface PutResult {
    data class Created(val rev: Long) : PutResult

    data class Updated(val rev: Long) : PutResult

    data object Unauthorized : PutResult

    data object PreconditionFailed : PutResult

    /** The relay already holds [RelayConfig.maxGroups] groups; a new one can't register. */
    data object Full : PutResult
}

/**
 * One sealed blob per group on disk. Bodies are opaque: nothing here parses, merges or validates
 * them (ADR-036). Every mutation runs under the group's lock and is written atomically
 * (temp file, fsync, rename), with `meta.json` written last as the commit point.
 */
class GroupStore(
    root: Path,
    private val historyKeep: Int = RelayConfig.HISTORY_KEEP,
    private val maxGroups: Int = RelayConfig.DEFAULT_MAX_GROUPS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val groupsDir: Path = root.resolve("groups")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val creation = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        try {
            Files.createDirectories(groupsDir)
            removeStaleTempFiles()
        } catch (e: IOException) {
            log.warn("data directory {} is not usable yet: {}", groupsDir, e.javaClass.simpleName)
        }
    }

    fun isAvailable(): Boolean = groupsDir.isDirectory() && Files.isWritable(groupsDir)

    suspend fun get(groupId: String, tokenHash: String?, ifNoneMatch: Set<Long>): GetResult {
        if (!groupDir(groupId).isDirectory()) return GetResult.NotFound
        return lockFor(groupId).withLock {
            io {
                val meta = readMeta(groupId) ?: return@io GetResult.NotFound
                when {
                    tokenHash == null || !sameHash(meta.tokenHash, tokenHash) -> GetResult.Unauthorized
                    meta.rev in ifNoneMatch -> GetResult.NotModified(meta.rev)
                    else -> GetResult.Found(meta.rev, Files.readAllBytes(documentFile(groupId)))
                }
            }
        }
    }

    /**
     * An early answer so a doomed upload is refused before its body is read. The lock is held only
     * for this check (never while a body streams in); [put] checks again under the lock.
     */
    suspend fun precheck(groupId: String, tokenHash: String, precondition: Precondition): PutResult? =
        lockFor(groupId).withLock {
            io {
                val meta = readMeta(groupId)
                refusal(meta, tokenHash, precondition) ?: if (meta == null && atCapacity(groupId)) PutResult.Full else null
            }
        }

    suspend fun put(groupId: String, tokenHash: String, precondition: Precondition, bytes: ByteArray): PutResult =
        lockFor(groupId).withLock {
            val meta = io { readMeta(groupId) }
            refusal(meta, tokenHash, precondition)?.let { return@withLock it }
            if (meta == null) {
                creation.withLock {
                    io {
                        if (atCapacity(groupId)) {
                            PutResult.Full
                        } else {
                            write(groupId, tokenHash, rev = 1, bytes)
                            PutResult.Created(1)
                        }
                    }
                }
            } else {
                io {
                    write(groupId, meta.tokenHash, rev = meta.rev + 1, bytes)
                    PutResult.Updated(meta.rev + 1)
                }
            }
        }

    private fun refusal(meta: GroupMeta?, tokenHash: String, precondition: Precondition): PutResult? = when {
        meta == null -> if (precondition is Precondition.IfNoneMatchAny) null else PutResult.PreconditionFailed
        !sameHash(meta.tokenHash, tokenHash) -> PutResult.Unauthorized
        precondition is Precondition.IfNoneMatchAny -> PutResult.PreconditionFailed
        precondition is Precondition.IfMatch && precondition.revs != null && meta.rev !in precondition.revs ->
            PutResult.PreconditionFailed
        else -> null
    }

    private fun write(groupId: String, tokenHash: String, rev: Long, bytes: ByteArray) {
        val dir = groupDir(groupId)
        val history = dir.resolve(HISTORY)
        Files.createDirectories(history)
        writeAtomic(history.resolve("$rev.bin"), bytes)
        writeAtomic(documentFile(groupId), bytes)
        val meta = GroupMeta(tokenHash = tokenHash, rev = rev, updatedAt = clock(), size = bytes.size.toLong())
        writeAtomic(dir.resolve(META), json.encodeToString(GroupMeta.serializer(), meta).toByteArray())
        pruneHistory(history)
    }

    private fun pruneHistory(history: Path) {
        val revs = Files.list(history).use { files ->
            files.toList().mapNotNull { it.name.removeSuffix(".bin").takeIf { n -> it.name.endsWith(".bin") }?.toLongOrNull() }
        }.sorted()
        revs.dropLast(historyKeep).forEach { Files.deleteIfExists(history.resolve("$it.bin")) }
    }

    private fun readMeta(groupId: String): GroupMeta? {
        val file = groupDir(groupId).resolve(META)
        if (!Files.exists(file)) return null
        return try {
            json.decodeFromString(GroupMeta.serializer(), Files.readString(file))
        } catch (e: IllegalArgumentException) {
            throw IOException("unreadable meta.json", e)
        }
    }

    private fun atCapacity(groupId: String): Boolean {
        val registered = Files.list(groupsDir).use { dirs ->
            dirs.filter { it.name != groupId && Files.exists(it.resolve(META)) }.count()
        }
        return registered >= maxGroups
    }

    private fun removeStaleTempFiles() {
        Files.walk(groupsDir, 3).use { paths ->
            paths.filter { it.name.startsWith(TEMP_PREFIX) }.toList()
        }.forEach { Files.deleteIfExists(it) }
    }

    private fun groupDir(groupId: String): Path = groupsDir.resolve(groupId)

    private fun documentFile(groupId: String): Path = groupDir(groupId).resolve(DOCUMENT)

    private fun lockFor(groupId: String): Mutex = locks.computeIfAbsent(groupId) { Mutex() }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private val log = LoggerFactory.getLogger(GroupStore::class.java)
        private const val META = "meta.json"
        private const val DOCUMENT = "document.bin"
        private const val HISTORY = "history"
        private const val TEMP_PREFIX = ".tmp-"

        fun sha256Hex(token: String): String = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }

        /** Constant-time comparison of two hex hashes. */
        fun sameHash(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

        internal fun writeAtomic(target: Path, bytes: ByteArray) {
            val dir = target.parent
            val temp = dir.resolve(TEMP_PREFIX + UUID.randomUUID())
            try {
                FileChannel.open(temp, CREATE_NEW, WRITE).use { channel ->
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temp)
            }
            fsyncDirectory(dir)
        }

        /** Makes the rename durable on Linux; directories can't be opened this way on Windows. */
        private fun fsyncDirectory(dir: Path) {
            try {
                FileChannel.open(dir, READ).use { it.force(true) }
            } catch (_: IOException) {
                // Not supported on this platform (Windows); the rename itself is still atomic.
            }
        }
    }
}
