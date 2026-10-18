package dev.quinton.goa.core.domain.sync

import java.net.URI
import java.util.Base64

/**
 * The group's hub address (ADR-030): an origin, `https://host[:port]`, no path. Plain `http` is
 * accepted only for a test hub on this machine or the emulator's host (`localhost`, `127.0.0.1`,
 * `10.0.2.2`); everything else must be HTTPS because the hub carries the group's data.
 */
object HubAddress {
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** [text] as a canonical origin (lowercase host, no trailing slash), or null if it isn't one. */
    fun normalize(text: String): String? {
        val raw = text.trim().let { if ("://" in it) it else "https://$it" }
        val uri = runCatching { URI(raw) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase()?.takeIf { it.isNotEmpty() }
        if (uri == null || scheme == null || host == null) return null
        val pathOk = uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"
        val clean = uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null && pathOk
        val schemeOk = scheme == "https" || (scheme == "http" && host in LOCAL_HOSTS)
        val port = uri.port.takeIf { it != -1 && it != defaultPort(scheme) }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port".takeIf { clean && schemeOk }
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80
}

/**
 * The bearer token a phone shows the hub (ADR-030 §4): derived from the group key, never the key
 * itself, so a leaked server log or proxy can't open sync files.
 */
object HubToken {
    const val INFO = "goa-hub/token/v1"

    fun derive(group: GroupCredentials): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        SyncCrypto.hkdf(group.key, group.groupId.toByteArray(Charsets.UTF_8), INFO),
    )

    /** Constant-time check of a presented token. */
    fun matches(group: GroupCredentials, presented: String): Boolean =
        SyncCrypto.same(derive(group).toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
}
