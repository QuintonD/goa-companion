package dev.quinton.goa.hub.config

import dev.quinton.goa.core.domain.sync.HubAddress
import dev.quinton.goa.hub.store.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `hub.json`, set with `goa-hub config` (ADR-030 §6). [publicUrl] is the hub's origin as phones
 * and browsers reach it (`https://goa.example.duckdns.org`); [androidCertSha256] are the release
 * certificate fingerprints served in `/.well-known/assetlinks.json`, so invite links open the app.
 * [timeZone] is the IANA zone the hub's dates are shown in (`Europe/Amsterdam`); null = the machine's.
 */
@Serializable
data class HubConfig(
    val publicUrl: String? = null,
    val androidCertSha256: List<String> = emptyList(),
    val timeZone: String? = null,
) {
    /** [timeZone] as a zone, or null when unset (or not a zone any more, e.g. a hand-edited file). */
    val zone: ZoneId? get() = timeZone?.let(::normalizeZone)?.let(ZoneId::of)

    companion object {
        const val ANDROID_PACKAGE = "dev.quinton.goa"

        /** `AA:BB:…` (32 bytes, upper-case, colon-separated), or null if [text] isn't a SHA-256 fingerprint. */
        fun normalizeFingerprint(text: String): String? {
            val hex = text.trim().replace(":", "").replace(" ", "").uppercase()
            if (hex.length != SHA256_HEX || hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
            return hex.chunked(2).joinToString(":")
        }

        /** The canonical origin for [text] ([HubAddress]), or null. */
        fun normalizeUrl(text: String): String? = HubAddress.normalize(text)

        /**
         * The canonical IANA region id for [text] (`europe/amsterdam` → `Europe/Amsterdam`), or null if it
         * isn't one. Fixed offsets (`+02:00`, `UTC+2`) are refused: they would miss the clock change.
         */
        fun normalizeZone(text: String): String? {
            val wanted = text.trim()
            return ZoneId.getAvailableZoneIds().firstOrNull { it.equals(wanted, ignoreCase = true) && '/' in it }
                ?: wanted.takeIf { it.equals("UTC", ignoreCase = true) }?.let { "UTC" }
        }

        private const val SHA256_HEX = 64
    }
}

/** Reads and writes `hub.json`; a missing file is the empty config. Re-read when the file changes. */
class HubConfigStore(private val file: Path) {
    @Volatile
    private var cached: Pair<Long, HubConfig>? = null

    fun get(): HubConfig {
        val modified = if (Files.exists(file)) Files.getLastModifiedTime(file).toMillis() else -1L
        cached?.takeIf { it.first == modified }?.let { return it.second }
        val config =
            AtomicFiles.readOrNull(file)?.let {
                ConfigJson.decodeFromString(HubConfig.serializer(), it.decodeToString())
            }
                ?: HubConfig()
        cached = modified to config
        return config
    }

    @Synchronized
    fun update(transform: (HubConfig) -> HubConfig): HubConfig {
        val next = transform(get())
        AtomicFiles.write(file, ConfigJson.encodeToString(HubConfig.serializer(), next).toByteArray())
        cached = null
        return next
    }
}

internal val ConfigJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
