package dev.quinton.goa.core.domain.sync

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/**
 * A sync group: [groupId] (UUID), its 256-bit [key] and a display [name]. Only phones holding the
 * key can exchange data with the group (ADR-025).
 */
class GroupCredentials(val groupId: String, key: ByteArray, val name: String) {
    private val secret = key.copyOf()

    val key: ByteArray get() = secret.copyOf()

    /** Non-secret tag in the Nearby endpoint name ([SyncCrypto.groupTag]). */
    val tag: String get() = SyncCrypto.groupTag(groupId)

    fun withName(name: String) = GroupCredentials(groupId, secret, name)

    override fun equals(other: Any?): Boolean =
        other is GroupCredentials && other.groupId == groupId && other.name == name &&
            other.secret.contentEquals(secret)

    override fun hashCode(): Int = groupId.hashCode()

    override fun toString(): String = "GroupCredentials($groupId, $name)" // never the key

    companion object {
        const val MAX_NAME_LENGTH = 40

        fun create(name: String, random: SecureBytes = SecureBytes.System): GroupCredentials =
            GroupCredentials(UUID.randomUUID().toString(), SyncCrypto.newGroupKey(random), cleanName(name))

        fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
    }
}

/**
 * What an invite carries: the [group], and optionally the group's hub ([hubUrl], an origin such as
 * `https://goa.example.org`, ADR-030) and the player it was made for ([playerId], ADR-031).
 */
data class Invite(val group: GroupCredentials, val hubUrl: String? = null, val playerId: String? = null)

/**
 * The invite a QR code carries: `goa-sync:1?g=<groupId>&k=<base64url key>&n=<name>[&h=<hub>][&p=<player>]`.
 * With a hub it is wrapped in a link, `<hub>/join#goa-sync:1?…`: an iPhone's camera opens the web
 * app, the Android scanner reads it as before, and the key stays in the fragment, which browsers
 * never send to a server. Anyone who scans it can join, so it is shown only at the table.
 */
object GroupInvite {
    const val SCHEME = "goa-sync"
    private const val VERSION = "1"
    private const val MAX_PLAYER_ID = 64

    fun format(group: GroupCredentials, hubUrl: String? = null, playerId: String? = null): String {
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(group.key)
        val hub = hubUrl?.let { requireNotNull(HubAddress.normalize(it)) { "Not a hub address: $it" } }
        val invite = buildString {
            append("$SCHEME:$VERSION?g=${group.groupId}&k=$key&n=${encode(group.name)}")
            hub?.let { append("&h=${encode(it)}") }
            playerId?.takeIf(::isPlayerId)?.let { append("&p=${encode(it)}") }
        }
        return hub?.let { "$it/join#$invite" } ?: invite
    }

    fun format(invite: Invite): String = format(invite.group, invite.hubUrl, invite.playerId)

    /**
     * The invite as a message for the share sheet: what to do with it, then the invite on its own
     * line. [parse] finds the invite inside the whole message, so a friend can paste all of it.
     */
    fun shareText(group: GroupCredentials, hubUrl: String? = null, playerId: String? = null): String {
        val invite = format(group, hubUrl, playerId)
        return if (hubUrl == null) {
            "Join “${group.name}” in the Guards of Atlantis companion: install the app, open Settings → " +
                "Group sync and paste this whole message under “Join a group”. It's the key to the group's " +
                "data, so only send it to the group.\n\n$invite"
        } else {
            "Join “${group.name}” in the Guards of Atlantis companion: open this link on your phone. On an " +
                "iPhone it opens the web app (Share → Add to Home Screen keeps it there); on Android with the " +
                "app, paste this whole message under Settings → Group sync → “Join a group”. It's the key to " +
                "the group's data, so only send it to the group.\n\n$invite"
        }
    }

    /** The group in [text] (an invite, or a message containing one), or a user-readable error. */
    fun parse(text: String): Result<GroupCredentials> = parseInvite(text).map { it.group }

    /**
     * The whole invite in [text]. A hub address or player id that doesn't check out is dropped, not
     * an error: the group itself is still joinable.
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
        val key = params["k"]?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == SyncCrypto.KEY_BYTES } ?: throw IllegalArgumentException(BROKEN)
        val name = GroupCredentials.cleanName(params["n"].orEmpty()).ifEmpty { "Our group" }
        Invite(
            group = GroupCredentials(groupId, key, name),
            hubUrl = params["h"]?.let(HubAddress::normalize),
            playerId = params["p"]?.takeIf(::isPlayerId),
        )
    }

    private val INVITE = Regex("$SCHEME:\\S+")

    private const val BROKEN = "This invite is incomplete. Scan the QR code again, or paste the whole message."

    private fun isUuid(text: String) = runCatching { UUID.fromString(text).toString() == text }.getOrDefault(false)

    private fun isPlayerId(text: String) =
        text.isNotEmpty() && text.length <= MAX_PLAYER_ID && text.none { it.isWhitespace() }

    // The String-charset overloads: the Charset ones need Android 13.
    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun decode(text: String) = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault("")
}
