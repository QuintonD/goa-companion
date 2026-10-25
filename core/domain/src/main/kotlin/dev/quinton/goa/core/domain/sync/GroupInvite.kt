package dev.quinton.goa.core.domain.sync

import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * A sync group: [groupId] (UUID), its 256-bit [key] and a display [name]. Only phones holding the
 * key can exchange data with the group (ADR-025).
 */
class GroupCredentials(val groupId: String, key: ByteArray, val name: String) {
    private val secret = key.copyOf()

    val key: ByteArray get() = secret.copyOf()

    /** Non-secret tag in the Nearby endpoint name ([SyncHash.groupTag]). */
    val tag: String get() = SyncHash.groupTag(groupId)

    fun withName(name: String) = GroupCredentials(groupId, secret, name)

    override fun equals(other: Any?): Boolean =
        other is GroupCredentials && other.groupId == groupId && other.name == name &&
            other.secret.contentEquals(secret)

    override fun hashCode(): Int = groupId.hashCode()

    override fun toString(): String = "GroupCredentials($groupId, $name)" // never the key

    companion object {
        const val MAX_NAME_LENGTH = 40

        fun create(name: String, random: SecureBytes = SecureBytes.System): GroupCredentials =
            GroupCredentials(Uuid.random().toString(), SyncKeys.newGroupKey(random), cleanName(name))

        fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
    }
}

/**
 * What an invite carries: the [group], and the group's sync server ([serverUrl], an origin such as
 * `https://goa.example.org:8443`, ADR-036) when it isn't the app's built-in default.
 */
data class Invite(val group: GroupCredentials, val serverUrl: String? = null)

/**
 * The invite a QR code carries: `goa-sync:1?g=<groupId>&k=<base64url key>&n=<name>[&s=<server>]`.
 * `s` is there only when the group syncs through a server other than the build's default
 * (ADR-036); apps that don't know it ignore it, as [parse] ignores any field it doesn't know.
 * Anyone who scans it can join, so it is shown only at the table.
 */
object GroupInvite {
    const val SCHEME = "goa-sync"
    private const val VERSION = "1"

    fun format(group: GroupCredentials, serverUrl: String? = null): String {
        val key = URL_KEY.encode(group.key)
        val server = serverUrl?.let {
            requireNotNull(SyncServerAddress.normalize(it)) { "Not a server address: $it" }
        }
        return buildString {
            append("$SCHEME:$VERSION?g=${group.groupId}&k=$key&n=${encode(group.name)}")
            server?.let { append("&s=${encode(it)}") }
        }
    }

    fun format(invite: Invite): String = format(invite.group, invite.serverUrl)

    /** The web app (ADR-043); an invite link opens it straight into joining the group. */
    const val WEB_APP_URL = "https://quintond.github.io/goa-companion/"

    /** The fragment before the invite in a [link]. */
    const val JOIN_FRAGMENT = "join="

    /**
     * [invite] (a [format]ted invite) as a link to the web app: `WEB_APP_URL#join=goa-sync:1?…`.
     * The invite rides in the fragment, which a browser never sends to any server, so an iPhone's
     * Camera opens the web app ready to join; every app's [parse] still finds the invite inside it,
     * so the phones' scanners read the same QR code.
     */
    fun link(invite: String): String = "$WEB_APP_URL#$JOIN_FRAGMENT$invite"

    fun link(group: GroupCredentials, serverUrl: String? = null): String = link(format(group, serverUrl))

    /**
     * The invite as a message for the share sheet: what to do with it, then the invite as a [link]
     * on its own line. [parse] finds the invite inside the whole message, so a friend can paste all
     * of it; on an iPhone or a computer the link opens the web app.
     */
    fun shareText(group: GroupCredentials, serverUrl: String? = null): String =
        "Join “${group.name}” in the Guards of Atlantis companion. On Android: install the app, open " +
            "Settings → Group sync and paste this whole message under “Join a group”. On an iPhone or a " +
            "computer: open the link. It's the key to the group's data, so only send it to the group." +
            "\n\n${link(group, serverUrl)}"

    /** The group in [text] (an invite, or a message containing one), or a user-readable error. */
    fun parse(text: String): Result<GroupCredentials> = parseInvite(text).map { it.group }

    /**
     * The whole invite in [text]. A server address that doesn't check out is dropped, not an
     * error: the group itself is still joinable (face to face, or through the default server).
     * Fields this version doesn't know are ignored, so newer invites still join.
     */
    fun parseInvite(text: String): Result<Invite> = runCatching {
        val trimmed = INVITE.find(text)?.value ?: text.trim()
        require(trimmed.startsWith("$SCHEME:")) { "This isn't a Guards of Atlantis group invite." }
        val rest = trimmed.removePrefix("$SCHEME:")
        val version = rest.substringBefore('?')
        require(version == VERSION) { "This invite was made by a newer version of the app. Update the app first." }
        val params = rest.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            it.substringBefore('=') to decode(it.substringAfter('=', ""))
        }
        val groupId = params["g"]?.takeIf(::isUuid) ?: throw IllegalArgumentException(BROKEN)
        val key = params["k"]?.let { runCatching { URL_KEY.decode(it) }.getOrNull() }
            ?.takeIf { it.size == SyncKeys.KEY_BYTES } ?: throw IllegalArgumentException(BROKEN)
        val name = GroupCredentials.cleanName(params["n"].orEmpty()).ifEmpty { "Our group" }
        Invite(
            group = GroupCredentials(groupId, key, name),
            serverUrl = params["s"]?.let { SyncServerAddress.normalize(it) },
        )
    }

    private val INVITE = Regex("$SCHEME:\\S+")

    private const val BROKEN = "This invite is incomplete. Scan the QR code again."

    /** base64url; written without padding, read with or without (as `java.util.Base64` did). */
    private val URL_KEY = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** A canonical lowercase UUID, as `UUID.fromString(text).toString() == text` accepted. */
    private fun isUuid(text: String) = runCatching { Uuid.parse(text).toString() == text }.getOrDefault(false)

    private fun encode(text: String) = FormUrl.encode(text)

    private fun decode(text: String) = runCatching { FormUrl.decode(text) }.getOrDefault("")
}

/**
 * `application/x-www-form-urlencoded` as `java.net.URLEncoder`/`URLDecoder` do it with UTF-8, in
 * shared code (ADR-043), so invites read and write the same on a phone and in the browser. [encode]
 * writes a space as `%20` (never `+`), as invites always have.
 */
internal object FormUrl {
    private const val HEX = "0123456789ABCDEF"

    fun encode(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = (b.toInt() and 0xff).toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in ".-*_" -> append(c)

                else -> {
                    val v = b.toInt() and 0xff
                    append('%').append(HEX[v ushr 4]).append(HEX[v and 0x0f])
                }
            }
        }
    }

    /** Throws [IllegalArgumentException] for a broken `%` escape, like `URLDecoder`. */
    fun decode(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            when (val c = text[i]) {
                '+' -> {
                    out.append(' ')
                    i++
                }

                '%' -> {
                    // Consecutive escapes are one UTF-8 byte sequence.
                    val bytes = ArrayList<Byte>()
                    while (i < text.length && text[i] == '%') {
                        require(i + 3 <= text.length) { "Incomplete escape" }
                        // Integer.parseInt's reading, as URLDecoder: "%+f" is 0x0f, "%-1" is refused.
                        val v = text.substring(i + 1, i + 3).toIntOrNull(16)
                        require(v != null && v >= 0) { "Illegal hex in escape" }
                        bytes += v.toByte()
                        i += 3
                    }
                    out.append(bytes.toByteArray().decodeToString())
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
