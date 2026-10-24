package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import java.util.Base64
import org.junit.Test

/** The sync server's token, addresses and the invite's `s=` field (ADR-036, docs/SYNC-SERVER.md). */
class SyncServerTest {
    private val group =
        GroupCredentials("6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", ByteArray(32) { it.toByte() }, "Friday & co")

    /**
     * Fixed vector: group key = bytes 0x00..0x1f, groupId as above. Computed independently (Python
     * hmac/hashlib HKDF-SHA256) from the formula in SYNC-SERVER.md; the server must derive the same.
     */
    @Test
    fun theTokenMatchesTheFixedVector() {
        val token = SyncServerToken.derive(group)
        assertThat(token).isEqualTo("3LzQ_IsXcyHp8SiAV9FGqdB_TQmU1C8PXkjjvBbDTIA")
        // What the server stores: SHA-256 of the token's ASCII, lowercase hex.
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
        assertThat(hash).isEqualTo("f8f51f43e4c066e0ca7149d763ec4149881e6890d9a8330491c10add0af944ff")
    }

    @Test
    fun theTokenIsTheFormulaAndNeverTheKey() {
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            SyncCrypto.hkdf(group.key, group.groupId.toByteArray(Charsets.UTF_8), "goa-relay/token/v1", 32),
        )
        val token = SyncServerToken.derive(group)
        assertThat(token).isEqualTo(expected)
        assertThat(token).hasLength(43)
        assertThat(token).doesNotContain("=")
        assertThat(token).isNotEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(group.key))
        // Another group id with the same key gets another token.
        val other = GroupCredentials("0e1d2c3b-4a59-4687-9a6b-5c4d3e2f1a0b", group.key, group.name)
        assertThat(SyncServerToken.derive(other)).isNotEqualTo(token)
    }

    @Test
    fun addressesAreHttpsOriginsExceptLocalTestServersInDebug() {
        assertThat(SyncServerAddress.normalize("oldboi.Taila4d38d.ts.net:8443"))
            .isEqualTo("https://oldboi.taila4d38d.ts.net:8443")
        assertThat(SyncServerAddress.normalize(" https://goa.example.org:443/ ")).isEqualTo("https://goa.example.org")
        assertThat(SyncServerAddress.normalize("http://10.0.2.2:8787")).isEqualTo("http://10.0.2.2:8787")
        assertThat(SyncServerAddress.normalize("http://localhost:8787")).isEqualTo("http://localhost:8787")
        assertThat(SyncServerAddress.normalize("http://localhost:8787", allowLocalHttp = false)).isNull()
        assertThat(SyncServerAddress.normalize("http://goa.example.org")).isNull()
        assertThat(SyncServerAddress.normalize("https://goa.example.org/v1")).isNull()
        assertThat(SyncServerAddress.normalize("https://goa.example.org/?x=1")).isNull()
        assertThat(SyncServerAddress.normalize("https://user@goa.example.org")).isNull()
        assertThat(SyncServerAddress.normalize("ftp://goa.example.org")).isNull()
        assertThat(SyncServerAddress.normalize("goa example.org")).isNull()
        assertThat(SyncServerAddress.normalize("")).isNull()
        assertThat(SyncServerAddress.display("https://goa.example.org:8443")).isEqualTo("goa.example.org:8443")
    }

    @Test
    fun anInviteCarriesTheServerOnlyWhenAskedAndRoundTrips() {
        val plain = GroupInvite.format(group)
        assertThat(plain).doesNotContain("&s=")
        assertThat(GroupInvite.parseInvite(plain).getOrThrow()).isEqualTo(Invite(group))

        val withServer = GroupInvite.format(group, "https://goa.example.org:8443/")
        assertThat(withServer).contains("&s=https%3A%2F%2Fgoa.example.org%3A8443")
        val parsed = GroupInvite.parseInvite(withServer).getOrThrow()
        assertThat(parsed).isEqualTo(Invite(group, "https://goa.example.org:8443"))
        assertThat(GroupInvite.format(parsed)).isEqualTo(withServer)
        // The whole share message still parses, server included.
        val message = GroupInvite.shareText(group, "https://goa.example.org:8443")
        assertThat(GroupInvite.parseInvite(message).getOrThrow()).isEqualTo(parsed)
    }

    @Test
    fun olderParsersKeepWorkingUnknownFieldsAreIgnoredAndABadServerIsDropped() {
        val withServer = GroupInvite.format(group, "https://goa.example.org")
        // What an app without server sync reads: only g, k and n (the group joins as before).
        assertThat(GroupInvite.parse(withServer).getOrThrow()).isEqualTo(group)
        // Fields from a newer app are ignored, wherever they sit.
        val future = withServer.replace("&n=", "&x=1&zz=%F0%9F%99%82&n=") + "&t=7"
        assertThat(GroupInvite.parseInvite(future).getOrThrow()).isEqualTo(Invite(group, "https://goa.example.org"))
        // A server that doesn't check out is dropped; the group still joins.
        val bad = GroupInvite.format(group) + "&s=" + "http%3A%2F%2Fevil.example.org"
        assertThat(GroupInvite.parseInvite(bad).getOrThrow()).isEqualTo(Invite(group))
        assertThat(GroupInvite.parseInvite(GroupInvite.format(group) + "&s=").getOrThrow().serverUrl).isNull()
    }
}
