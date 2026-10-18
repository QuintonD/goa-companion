package dev.quinton.goa.hub.store

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A typed per-device slot: one JSON file, `devices/<deviceId>/<name>.json`. Each feature declares
 * its own slots next to its code (`val TimerSlot = DeviceSlot("timer", TimerDeviceState.serializer())`),
 * so no shared class needs editing. [name]: a lower-case letter, then letters, digits or `-`
 * (case-insensitive file systems: don't declare two names that differ only in case).
 */
class DeviceSlot<T>(val name: String, val serializer: KSerializer<T>) {
    init {
        require(DeviceStore.isSlotName(name)) { "Bad slot name: $name" }
    }
}

/**
 * Each browser's own state (ADR-030 §3): the things a phone keeps to itself (who am I, settings,
 * timer, draft in progress, trivia progress, push subscriptions). Reads and writes are atomic per
 * file, and [update] runs under a per-device lock so two requests from one browser can't lose a
 * write. A slot that can't be decoded (written by an older hub) reads as absent.
 */
class DeviceStore(private val root: Path) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun <T> read(deviceId: String, slot: DeviceSlot<T>): T? = read(deviceId, slot.name, slot.serializer)

    fun <T> read(deviceId: String, slot: String, serializer: KSerializer<T>): T? {
        val bytes = AtomicFiles.readOrNull(file(deviceId, slot)) ?: return null
        return try {
            SlotJson.decodeFromString(serializer, bytes.decodeToString())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun <T> write(deviceId: String, slot: DeviceSlot<T>, value: T) = write(deviceId, slot.name, slot.serializer, value)

    fun <T> write(deviceId: String, slot: String, serializer: KSerializer<T>, value: T) {
        requireDevice(deviceId)
        AtomicFiles.write(
            file(deviceId, slot),
            SlotJson.encodeToString(serializer, value).toByteArray(),
            private = true,
        )
    }

    fun delete(deviceId: String, slot: DeviceSlot<*>) = delete(deviceId, slot.name)

    fun delete(deviceId: String, slot: String) {
        Files.deleteIfExists(file(deviceId, slot))
    }

    /** Read-modify-write under this device's lock; returning null deletes the slot. */
    suspend fun <T> update(deviceId: String, slot: DeviceSlot<T>, transform: (T?) -> T?): T? =
        lock(deviceId).withLock {
            val next = transform(read(deviceId, slot))
            if (next == null) delete(deviceId, slot) else write(deviceId, slot, next)
            next
        }

    /** Every device that has [slot] stored (e.g. to reschedule pushes at start-up). */
    fun devicesWith(slot: DeviceSlot<*>): List<String> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { dirs ->
            dirs.filter { Files.isRegularFile(it.resolve("${slot.name}.json")) }.map { it.fileName.toString() }.toList()
        }.filter(::isDeviceId)
    }

    internal fun deviceDir(deviceId: String): Path {
        require(isDeviceId(deviceId)) { "Bad device id" }
        return root.resolve(deviceId)
    }

    private fun requireDevice(deviceId: String) {
        check(Files.isDirectory(deviceDir(deviceId))) { "Unknown device" }
    }

    private fun file(deviceId: String, slot: String): Path {
        require(isSlotName(slot)) { "Bad slot name: $slot" }
        return deviceDir(deviceId).resolve("$slot.json")
    }

    private fun lock(deviceId: String) = locks.computeIfAbsent(deviceId) { Mutex() }

    companion object {
        private val SLOT = Regex("[a-z][a-zA-Z0-9-]{0,63}")
        private val DEVICE_ID = Regex("[0-9a-f]{32}")

        fun isSlotName(name: String) = SLOT.matches(name) && name != Devices.RECORD_SLOT

        fun isDeviceId(id: String) = DEVICE_ID.matches(id)
    }
}

internal val SlotJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
