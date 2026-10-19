package dev.quinton.goa.core.presentation.group

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player

/** Who the invite is for: not chosen yet, anyone, or one player (a personal invite, ADR-031). */
sealed interface InviteFor {
    data object Choosing : InviteFor

    data object Anyone : InviteFor

    data class One(val playerId: String) : InviteFor
}

/** "Invite a friend" as the phone's sheet and the hub's `/group/invite` page show it. */
data class InviteView(
    /** Who an invite can be made for: active players by name, this device's own player left out. */
    val players: List<Player>,
    /** Still asking "Who's the invite for?". */
    val choosing: Boolean,
    /** The player a personal invite is for; null = anyone. */
    val player: Player? = null,
    /** What the QR code shows; null while choosing. */
    val invite: String? = null,
    /** "Share as text": what to do with it, then the invite. */
    val shareText: String? = null,
)

/**
 * "Invite a friend" (ADR-025, ADR-031): the invite for anyone or for one player. A personal invite
 * carries the player's id (`p=`); after the first sync the friend's phone or browser asks
 * "You're <name>?" and becomes that player instead of adding a new one.
 */
object GroupInvites {
    /**
     * [everyone]: all visible players, archived included; [meId]: this device's player (raw);
     * [hubUrl]: the group's hub, if any (the invite is then a link to it, ADR-030 §6). A player
     * merged away since the link was made: the invite is for the one they became; one that is
     * gone altogether sends the chooser back.
     */
    fun view(
        group: GroupCredentials,
        hubUrl: String?,
        everyone: List<Player>,
        merges: PlayerMerges,
        meId: String?,
        chosen: InviteFor,
    ): InviteView {
        val choices = InviteView(players = choices(everyone, merges, meId), choosing = true)
        val player = (chosen as? InviteFor.One)?.let { c ->
            everyone.firstOrNull { it.id == merges.resolve(c.playerId) }
        }
        if (chosen == InviteFor.Choosing || (chosen is InviteFor.One && player == null)) return choices
        return choices.copy(
            choosing = false,
            player = player,
            invite = GroupInvite.format(group, hubUrl, player?.id),
            shareText = shareText(group, hubUrl, player),
        )
    }

    /** Who an invite can be made for: active players by name, this device's own player ([meId]) left out. */
    fun choices(everyone: List<Player>, merges: PlayerMerges, meId: String?): List<Player> {
        val me = meId?.let(merges::resolve)
        return everyone.filter { !it.archived && it.id != me }.sortedBy { it.name.lowercase() }
    }

    /** "Share as text": a personal invite starts "For <name>: ". */
    fun shareText(group: GroupCredentials, hubUrl: String?, player: Player?): String {
        val text = GroupInvite.shareText(group, hubUrl, player?.id)
        return player?.let { "For ${it.name}: $text" } ?: text
    }
}
