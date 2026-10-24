package dev.quinton.goa.core.domain.sync

import java.net.URI
import java.util.Base64

/**
 * The bearer token a phone shows the group's sync server (ADR-036, docs/SYNC-SERVER.md): derived
 * from the group key, never the key itself, so whoever runs the server can't open the sealed
 * document. `base64url-no-pad(HKDF-SHA256(ikm = group key, salt = groupId UTF-8,
 * info = "goa-relay/token/v1", length = 32))`. The server keeps only its SHA-256.
 */
object SyncServerToken {
    const val INFO = "goa-relay/token/v1"
    const val LENGTH = 32

    fun derive(group: GroupCredentials): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        SyncCrypto.hkdf(group.key, group.groupId.toByteArray(Charsets.UTF_8), INFO, LENGTH),
    )
}

/**
 * A sync server address (ADR-036): an origin, `https://host[:port]`, no path, query or user info.
 * Plain `http` is accepted only for a test server on this machine or the emulator's host
 * (`localhost`, `127.0.0.1`, `10.0.2.2`), and only when [allowLocalHttp] (debug builds); the
 * server carries the group's sealed data and its token, so everything else must be HTTPS.
 */
object SyncServerAddress {
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    private const val HTTPS_PORT = 443
    private const val HTTP_PORT = 80

    /** [text] as a canonical origin (lowercase host, no default port, no trailing slash), or null. */
    fun normalize(text: String, allowLocalHttp: Boolean = true): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val raw = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() }
        if (scheme == null || host == null) return null
        val pathOk = uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"
        val clean = uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null && pathOk
        val schemeOk = scheme == "https" || (scheme == "http" && allowLocalHttp && host in LOCAL_HOSTS)
        val default = if (scheme == "https") HTTPS_PORT else HTTP_PORT
        val port = uri.port.takeIf { it != -1 && it != default }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port".takeIf { clean && schemeOk }
    }

    /** The address without `https://`, as people type and say it. */
    fun display(origin: String): String = origin.removePrefix("https://")
}
