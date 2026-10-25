package dev.quinton.goa.core.domain.sync

import kotlin.io.encoding.Base64

/**
 * The bearer token a phone shows the group's sync server (ADR-036, docs/SYNC-SERVER.md): derived
 * from the group key, never the key itself, so whoever runs the server can't open the sealed
 * document. `base64url-no-pad(HKDF-SHA256(ikm = group key, salt = groupId UTF-8,
 * info = "goa-relay/token/v1", length = 32))`. The server keeps only its SHA-256.
 */
object SyncServerToken {
    const val INFO = "goa-relay/token/v1"
    const val LENGTH = 32

    fun derive(group: GroupCredentials): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
        SyncHash.hkdf(group.key, group.groupId.encodeToByteArray(), INFO, LENGTH),
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
    @Suppress("ReturnCount") // One early exit per way an address can fail.
    fun normalize(text: String, allowLocalHttp: Boolean = true): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val raw = if ("://" in trimmed) trimmed else "https://$trimmed"
        // Only `scheme://authority` with an empty or "/" path can be an origin; anything with a
        // query, fragment, longer path or user info is refused, as java.net.URI's reading was.
        val match = ORIGIN.matchEntire(raw) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val authority = match.groupValues[2]
        if ('@' in authority) return null
        val (hostText, portText) = splitHostPort(authority) ?: return null
        val host = UriHost.parse(hostText)?.lowercase() ?: return null
        val portNumber = if (portText.isEmpty()) -1 else portText.toIntOrNull() ?: return null
        val schemeOk = scheme == "https" || (scheme == "http" && allowLocalHttp && host in LOCAL_HOSTS)
        val default = if (scheme == "https") HTTPS_PORT else HTTP_PORT
        val port = portNumber.takeIf { it != -1 && it != default }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port".takeIf { schemeOk }
    }

    private val ORIGIN = Regex("([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)/?")

    /** `host[:port]` (port digits only, may be empty); an IPv6 host keeps its brackets. */
    private fun splitHostPort(authority: String): Pair<String, String>? {
        val hostEnd = if (authority.startsWith("[")) {
            authority.indexOf(']') + 1
        } else {
            authority.indexOf(':').let {
                if (it < 0) authority.length else it
            }
        }
        if (hostEnd <= 0) return null
        val rest = authority.substring(hostEnd)
        if (rest.isNotEmpty() && (rest[0] != ':' || rest.drop(1).any { it !in '0'..'9' })) return null
        return authority.substring(0, hostEnd) to rest.drop(1)
    }

    /** The address without `https://`, as people type and say it. */
    fun display(origin: String): String = origin.removePrefix("https://")
}

/**
 * A server-based URI host as `java.net.URI` parses one (RFC 2396 with RFC 2732 IPv6 literals):
 * an IPv4 address, a bracketed IPv6 address, or a hostname of alphanumeric labels with inner
 * hyphens whose last label starts with a letter. Shared code (ADR-043); `SyncServerAddressTest`
 * holds it to `java.net.URI` on the JVM.
 */
internal object UriHost {
    private const val IPV4_PARTS = 4
    private const val IPV6_GROUPS = 8
    private const val MAX_OCTET = 255

    /** [text] when it is a valid host, else null. */
    fun parse(text: String): String? = text.takeIf {
        when {
            it.isEmpty() -> false
            it.startsWith("[") -> it.endsWith("]") && isIpv6Reference(it.substring(1, it.length - 1))
            isIpv4Shaped(it) -> isIpv4(it) || isHostname(it)
            else -> isHostname(it)
        }
    }

    private fun isIpv4Shaped(text: String) = text.all { it in '0'..'9' || it == '.' }

    private fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == IPV4_PARTS && parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && (p.toIntOrNull() ?: Int.MAX_VALUE) <= MAX_OCTET
        }
    }

    private fun isHostname(text: String): Boolean {
        val labels = text.removeSuffix(".").split('.')
        val alnum: (Char) -> Boolean = { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        val labelsOk = labels.all { l ->
            l.isNotEmpty() && alnum(l.first()) && alnum(l.last()) && l.all { alnum(it) || it == '-' }
        }
        val last = labels.last()
        return labelsOk && (labels.size == 1 || last.first().isLetter())
    }

    /** An IPv6 address, optionally with a `%scope` of letters, digits, `_` and `.`. */
    private fun isIpv6Reference(text: String): Boolean {
        val scope = text.substringAfter('%', missingDelimiterValue = "")
        val scopeOk = '%' !in text ||
            (scope.isNotEmpty() && scope.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "_." })
        return scopeOk && isIpv6(text.substringBefore('%'))
    }

    @Suppress("ReturnCount") // One early exit per rule of RFC 4291's text form.
    private fun isIpv6(text: String): Boolean {
        if (text.isEmpty() || text.count { it == ':' } < 2) return false
        val v4 = text.substringAfterLast(':').takeIf { '.' in it }
        if (v4 != null && !isIpv4(v4)) return false
        val head = if (v4 != null) text.dropLast(v4.length) + "0:0" else text
        val compressed = head.indexOf("::")
        if (compressed >= 0 && head.indexOf("::", compressed + 1) >= 0) return false
        val groups = if (compressed >= 0) {
            val left = head.substring(0, compressed).split(':').filter { it.isNotEmpty() }
            val right = head.substring(compressed + 2).split(':').filter { it.isNotEmpty() }
            if (head.substring(0, compressed).let { it.isNotEmpty() && (it.startsWith(":") || it.endsWith(":")) }) {
                return false
            }
            if (left.size + right.size >= IPV6_GROUPS) return false
            left + right
        } else {
            head.split(':').also { if (it.size != IPV6_GROUPS) return false }
        }
        return groups.all { g -> g.length in 1..4 && g.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } }
    }
}
