package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Invites as links with a hub and a player (ADR-030 §6, ADR-031), the hub address and token. */
class HubInviteTest {
    private val group =
        GroupCredentials("6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", ByteArray(32) { it.toByte() }, "Friday & co")

    @Test
    fun anInviteWithAHubIsALinkWhoseFragmentHoldsTheKey() {
        val link = GroupInvite.format(group, hubUrl = "https://goa.example.org/", playerId = "bob-1")
        assertThat(link).startsWith("https://goa.example.org/join#goa-sync:1?g=${group.groupId}&k=")
        val beforeFragment = link.substringBefore('#')
        assertThat(beforeFragment).doesNotContain("k=")

        val parsed = GroupInvite.parseInvite(link).getOrThrow()
        assertThat(parsed.group).isEqualTo(group)
        assertThat(parsed.hubUrl).isEqualTo("https://goa.example.org")
        assertThat(parsed.playerId).isEqualTo("bob-1")
        // Older call sites only want the group.
        assertThat(GroupInvite.parse(link).getOrThrow()).isEqualTo(group)
        assertThat(GroupInvite.format(parsed)).isEqualTo(link)
    }

    @Test
    fun aPlainInviteStaysAsBeforeAndCarriesNoHubOrPlayer() {
        val plain = GroupInvite.format(group)
        assertThat(plain).startsWith("goa-sync:1?")
        assertThat(plain).doesNotContain("&h=")
        val parsed = GroupInvite.parseInvite(plain).getOrThrow()
        assertThat(parsed.hubUrl).isNull()
        assertThat(parsed.playerId).isNull()
        // A personal invite without a hub.
        val personal = GroupInvite.parseInvite(GroupInvite.format(group, playerId = "p1")).getOrThrow()
        assertThat(personal.playerId).isEqualTo("p1")
    }

    @Test
    fun theSharedMessageWithALinkStillParses() {
        val text = GroupInvite.shareText(group, hubUrl = "https://goa.example.org", playerId = "bob")
        assertThat(text).contains("iPhone")
        val parsed = GroupInvite.parseInvite(text).getOrThrow()
        assertThat(parsed.group).isEqualTo(group)
        assertThat(parsed.playerId).isEqualTo("bob")
    }

    @Test
    fun aBadHubOrPlayerIsDroppedButTheGroupStillJoins() {
        val base = GroupInvite.format(group)
        val parsed = GroupInvite.parseInvite("$base&h=${"http://evil.example.org"}&p=").getOrThrow()
        assertThat(parsed.group).isEqualTo(group)
        assertThat(parsed.hubUrl).isNull()
        assertThat(parsed.playerId).isNull()
    }

    @Test
    fun hubAddressesAreHttpsOriginsExceptLocalTestHubs() {
        assertThat(HubAddress.normalize("goa.Example.org")).isEqualTo("https://goa.example.org")
        assertThat(HubAddress.normalize("https://goa.example.org:443/")).isEqualTo("https://goa.example.org")
        assertThat(HubAddress.normalize("https://goa.example.org:8443")).isEqualTo("https://goa.example.org:8443")
        assertThat(HubAddress.normalize("http://10.0.2.2:8080")).isEqualTo("http://10.0.2.2:8080")
        assertThat(HubAddress.normalize("http://localhost:8080")).isEqualTo("http://localhost:8080")
        assertThat(HubAddress.normalize("http://goa.example.org")).isNull()
        assertThat(HubAddress.normalize("https://goa.example.org/join")).isNull()
        assertThat(HubAddress.normalize("https://user@goa.example.org")).isNull()
        assertThat(HubAddress.normalize("ftp://goa.example.org")).isNull()
        assertThat(HubAddress.normalize("")).isNull()
    }

    @Test
    fun theHubTokenIsDerivedStableAndNotTheKey() {
        val token = HubToken.derive(group)
        assertThat(token).isEqualTo(HubToken.derive(group))
        assertThat(token).hasLength(43) // 32 bytes, base64url without padding
        assertThat(HubToken.matches(group, token)).isTrue()
        assertThat(HubToken.matches(group, "")).isFalse()
        assertThat(HubToken.matches(group, token.reversed())).isFalse()
        val other = GroupCredentials(group.groupId, ByteArray(32) { 7 }, group.name)
        assertThat(HubToken.matches(other, token)).isFalse()
        assertThat(token).doesNotContain(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(group.key))
    }
}
