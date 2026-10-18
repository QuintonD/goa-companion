package dev.quinton.goa.hub.store

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.Serializable

/** A signed-in browser (ADR-030 §7). [id] is public (shown in Settings); the cookie is the secret. */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)

/**
 * The browsers signed in to the group, `devices/<deviceId>/device.json`. The `goa_device` cookie
 * carries a random 256-bit token; the device id is a hash of it, so the data directory, the device
 * list and every page hold ids that can't be replayed as a cookie.
 */
class Devices(private val root: Path, private val random: SecureRandom = SecureRandom()) {
    /** Creates a device; returns it with the cookie token (shown once, never stored). */
    fun create(name: String, now: Long): Pair<Device, String> {
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val device = Device(id = idOf(token), name = cleanName(name), createdAt = now, lastSeenAt = now)
        val dir = root.resolve(device.id)
        Files.createDirectories(dir)
        AtomicFiles.restrict(dir)
        save(device)
        return device to token
    }

    /** The device a cookie [token] belongs to, or null (signed out, or never issued). */
    fun find(token: String): Device? {
        if (!TOKEN.matches(token)) return null
        return get(idOf(token))
    }

    fun get(id: String): Device? {
        if (!DeviceStore.isDeviceId(id)) return null
        val bytes = AtomicFiles.readOrNull(root.resolve(id).resolve("$RECORD_SLOT.json")) ?: return null
        return SlotJson.decodeFromString(Device.serializer(), bytes.decodeToString())
    }

    /** Every device, most recently seen first. */
    fun list(): List<Device> {
        if (!Files.isDirectory(root)) return emptyList()
        val ids = Files.list(root).use { dirs -> dirs.map { it.fileName.toString() }.toList() }
        return ids.mapNotNull(::get).sortedByDescending { it.lastSeenAt }
    }

    fun rename(id: String, name: String): Device? {
        val device = get(id) ?: return null
        return device.copy(name = cleanName(name)).also(::save)
    }

    /** Records that [device] was used at [now]; written at most every [SEEN_EVERY_MS] to spare the disk. */
    fun touch(device: Device, now: Long): Device {
        if (now - device.lastSeenAt < SEEN_EVERY_MS) return device
        return device.copy(lastSeenAt = now).also(::save)
    }

    /** Signs [id] out: its cookie stops working and its own state is deleted. */
    fun signOut(id: String): Boolean {
        if (!DeviceStore.isDeviceId(id)) return false
        val dir = root.resolve(id)
        if (!Files.isDirectory(dir)) return false
        Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).toList() }.forEach(Files::deleteIfExists)
        return true
    }

    private fun save(device: Device) {
        AtomicFiles.write(
            root.resolve(device.id).resolve("$RECORD_SLOT.json"),
            SlotJson.encodeToString(Device.serializer(), device).toByteArray(),
            private = true,
        )
    }

    companion object {
        const val RECORD_SLOT = "device"
        const val MAX_NAME = 40
        private const val TOKEN_BYTES = 32
        private const val ID_BYTES = 16
        private const val SEEN_EVERY_MS = 5 * 60_000L
        private val TOKEN = Regex("[A-Za-z0-9_-]{43}")

        fun idOf(token: String): String = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.US_ASCII))
            .take(ID_BYTES)
            .joinToString("") { "%02x".format(it) }

        fun cleanName(name: String): String =
            name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).ifEmpty { "Browser" }

        /** A readable default name from a User-Agent ("iPhone · Safari"). */
        fun nameFromUserAgent(userAgent: String?): String {
            val ua = userAgent.orEmpty()
            val device = listOf("iPhone", "iPad", "Android", "Windows", "Macintosh", "Linux")
                .firstOrNull { it in ua }?.let { if (it == "Macintosh") "Mac" else it } ?: "Browser"
            val browser = when {
                "Edg/" in ua -> "Edge"
                "Firefox/" in ua || "FxiOS" in ua -> "Firefox"
                "CriOS" in ua || "Chrome/" in ua -> "Chrome"
                "Safari/" in ua -> "Safari"
                else -> null
            }
            return listOfNotNull(device, browser).joinToString(" · ")
        }
    }
}
