package dev.quinton.goa.hub.store

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/**
 * Crash-safe file writes (ADR-030 §3): the new content goes to a temp file in the same directory,
 * is flushed to disk, and replaces the target with one atomic rename. A crash at any point leaves
 * either the old file or the new one, never half of each; a temp file left behind is ignored and
 * swept by [sweepTemps].
 */
object AtomicFiles {
    const val TEMP_SUFFIX = ".tmp"
    private const val TEMP_MARK = ".goa-"

    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()
    private val ownerOnly: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
    private val ownerOnlyDir: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")

    /**
     * Writes [bytes] to [target] atomically; [private] (the default: everything the hub keeps is the
     * group's) = owner read/write only where POSIX allows, whatever the process umask. Without
     * [createDirectories] a missing directory is a [java.nio.file.NoSuchFileException].
     */
    fun write(target: Path, bytes: ByteArray, private: Boolean = true, createDirectories: Boolean = true) {
        val dir = target.toAbsolutePath().parent
        if (createDirectories) Files.createDirectories(dir)
        val temp = dir.resolve(".${target.fileName}$TEMP_MARK${UUID.randomUUID()}$TEMP_SUFFIX")
        try {
            FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                if (private) restrict(temp)
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            move(temp, target)
            syncDirectory(dir)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun readOrNull(path: Path): ByteArray? = if (Files.isRegularFile(path)) Files.readAllBytes(path) else null

    /** Deletes temp files a crash left in [dir]; returns how many. */
    fun sweepTemps(dir: Path): Int {
        if (!Files.isDirectory(dir)) return 0
        return Files.list(dir).use { files ->
            files.filter { isTemp(it) }.toList()
        }.count { Files.deleteIfExists(it) }
    }

    fun isTemp(path: Path): Boolean = path.fileName.toString().let {
        it.contains(TEMP_MARK) && it.endsWith(TEMP_SUFFIX)
    }

    /** Owner-only permissions on [path] (files `0600`, directories `0700`); a no-op where POSIX isn't supported. */
    fun restrict(path: Path) {
        if (!posix) return
        Files.setPosixFilePermissions(path, if (Files.isDirectory(path)) ownerOnlyDir else ownerOnly)
    }

    val supportsPosix: Boolean get() = posix

    private fun move(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Makes the rename itself durable (POSIX only; Windows can't open a directory for sync). */
    private fun syncDirectory(dir: Path) {
        if (!posix) return
        try {
            FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Some file systems refuse; the rename is still atomic, just not yet durable.
        }
    }
}
