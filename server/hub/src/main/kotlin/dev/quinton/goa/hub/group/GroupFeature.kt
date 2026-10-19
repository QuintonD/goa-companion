package dev.quinton.goa.hub.group

import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.group.GroupInvites
import dev.quinton.goa.core.presentation.group.InviteFor
import dev.quinton.goa.core.presentation.group.PlayerLink
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.GroupData
import dev.quinton.goa.hub.store.NamedPlayer
import dev.quinton.goa.hub.store.PlayerMergeOutcome
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.web.DeviceCookie
import dev.quinton.goa.hub.web.SignInSlots
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.publicOrigin
import dev.quinton.goa.hub.web.ui.PlayerPalette
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.serialization.Serializable

/** "Yes, I'm <into>" on a browser that was [old] before: the merge it may confirm (one at a time). */
@Serializable
data class PendingMerge(val old: String, val into: String)

/** The Group area's own per-browser state. */
object GroupSlots {
    /** The old-me merge offered after "Yes, I'm …", until it is answered. */
    val PendingMerge = DeviceSlot("group-pending-merge", dev.quinton.goa.hub.group.PendingMerge.serializer())
}

/**
 * Group (ADR-025, ADR-030 §8, ADR-031): the group, "Which player are you?" for this browser
 * (`SharedSlots.Me`, written only here), the invite QR code for anyone or for one player, the
 * signed-in browsers (any of them can be signed out) and when the phones last synced with the hub.
 * Every rule comes from core:presentation's `PlayerLink` and `GroupInvites`, as on the phone.
 */
object GroupFeature : HubFeature {
    const val PATH = "/group"
    const val ME_PATH = "/group/me"
    const val INVITE_PATH = "/group/invite"

    override fun Route.routes(ctx: HubContext) {
        get(PATH) { call.respondGroupPage(ctx) }
        get(ME_PATH) {
            call.respondWhoAreYou(ctx, WhoAreYouForm(next = safeNext(call.request.queryParameters["next"])))
        }
        post("$ME_PATH/choose") { choose(ctx, call) }
        post("$ME_PATH/invited") { answerInvited(ctx, call) }
        post("$ME_PATH/add") { addMe(ctx, call) }
        get("$ME_PATH/merge") { call.respondMergeOffer(ctx, safeNext(call.request.queryParameters["next"])) }
        post("$ME_PATH/merge") { mergeOld(ctx, call) }
        post("$ME_PATH/merge/keep") {
            val next = safeNext(call.receiveParameters()["next"])
            ctx.deviceStore.delete(call.device.id, GroupSlots.PendingMerge)
            call.respondRedirect(next)
        }
        get(INVITE_PATH) { call.respondInvite(ctx) }
        get("$PATH/devices/{id}/sign-out") { call.respondSignOutConfirm(ctx, call.parameters["id"].orEmpty()) }
        post("$PATH/devices/{id}/sign-out") { signOut(ctx, call, call.parameters["id"].orEmpty()) }
    }

    /** This browser's player, read through the merges; null when none is chosen (or it is gone). */
    fun me(ctx: HubContext, deviceId: String, data: GroupData): Player? {
        val id = PlayerLink.me(ctx.deviceStore.read(deviceId, SharedSlots.Me), data.merges) ?: return null
        return data.player(id)
    }

    /** "You're <name>?": the invite's player, when there is something to ask (PlayerLink.invited). */
    internal fun invited(ctx: HubContext, deviceId: String, data: GroupData): Player? = PlayerLink.invited(
        ctx.deviceStore.read(deviceId, SignInSlots.InvitedPlayerId),
        ctx.deviceStore.read(deviceId, SharedSlots.Me),
        data.merges,
        data.players(),
    )

    /** The invite for [chosen], as the phone's sheet builds it; the hub's own address goes in `h=`. */
    internal fun inviteView(ctx: HubContext, call: ApplicationCall, chosen: InviteFor) = ctx.group?.let { group ->
        val data = ctx.groupStore.data()
        GroupInvites.view(
            group,
            call.publicOrigin,
            data.players(includeArchived = true),
            data.merges,
            ctx.deviceStore.read(call.device.id, SharedSlots.Me),
            chosen,
        )
    }

    private suspend fun choose(ctx: HubContext, call: ApplicationCall) {
        val form = call.receiveParameters()
        val next = safeNext(form["next"])
        val data = ctx.groupStore.data()
        val player = form["player"]?.let { id -> data.players().firstOrNull { it.id == data.merges.resolve(id) } }
            ?: return call.respondWhoAreYou(
                ctx,
                WhoAreYouForm(next = next, error = "That player isn't in the group any more."),
            )
        setMe(ctx, call, player.id)
        call.respondRedirect(next)
    }

    private suspend fun answerInvited(ctx: HubContext, call: ApplicationCall) {
        val form = call.receiveParameters()
        val next = safeNext(form["next"])
        val deviceId = call.device.id
        if (form["answer"] != "yes") {
            // "Not me": forget the invite's player and pick from the list.
            ctx.deviceStore.delete(deviceId, SignInSlots.InvitedPlayerId)
            return call.respondRedirect(meUrl(next))
        }
        val data = ctx.groupStore.data()
        val invited = invited(ctx, deviceId, data) ?: return call.respondRedirect(meUrl(next))
        val offer = PlayerLink.mergeOffer(
            invited,
            ctx.deviceStore.read(deviceId, SharedSlots.Me),
            data.merges,
            data.players(),
            data.matches,
        )
        setMe(ctx, call, invited.id)
        if (offer == null) return call.respondRedirect(next)
        ctx.deviceStore.write(deviceId, GroupSlots.PendingMerge, PendingMerge(offer.old.id, offer.into.id))
        call.respondRedirect("$ME_PATH/merge?next=${encode(next)}")
    }

    private suspend fun mergeOld(ctx: HubContext, call: ApplicationCall) {
        val next = safeNext(call.receiveParameters()["next"])
        val deviceId = call.device.id
        val pending = ctx.deviceStore.read(deviceId, GroupSlots.PendingMerge) ?: return call.respondRedirect(next)
        ctx.deviceStore.delete(deviceId, GroupSlots.PendingMerge)
        val data = ctx.groupStore.data()
        // Only the merge this browser was offered, and only while it still is that player.
        if (me(ctx, deviceId, data)?.id != pending.into) return call.respondRedirect(next)
        when (val outcome = ctx.groupStore.mergePlayers(pending.old, pending.into)) {
            is PlayerMergeOutcome.PlayedTogether -> call.respondMergeRefused(ctx, outcome, next)
            PlayerMergeOutcome.Merged, PlayerMergeOutcome.NotPossible -> call.respondRedirect(next)
        }
    }

    private suspend fun addMe(ctx: HubContext, call: ApplicationCall) {
        val form = call.receiveParameters()
        val next = safeNext(form["next"])
        val name = form["name"].orEmpty().trim().take(PlayerLink.MAX_NAME)
        val base = WhoAreYouForm(next = next, newName = name)
        if (name.isEmpty()) return call.respondWhoAreYou(ctx, base.copy(nameError = PlayerLink.NAME_MISSING))
        val data = ctx.groupStore.data()
        val check = PlayerLink.addCheck(name, data.players())
        // "Is one of these you?" first; "No, add <name>" confirms (never for a name that is taken).
        if (check != null && (form["confirmed"] != "true" || check.nameTaken)) {
            return call.respondWhoAreYou(ctx, base.copy(addCheck = check))
        }
        // Archived players aren't offered above, but their names stay taken.
        ctx.groupStore.findPlayerByName(name)?.let { existing ->
            return call.respondWhoAreYou(ctx, base.copy(nameError = PlayerLink.nameTaken(existing)))
        }
        val added = ctx.groupStore.upsertPlayerIfNameFree(
            PlayerLink.newPlayer(
                id = UUID.randomUUID().toString(),
                name = name,
                everyone = data.players(includeArchived = true),
                palette = PlayerPalette.swatches.map { it.first },
                crests = PlayerPalette.CRESTS,
                now = ctx.clock.nowMillis(),
            ),
        )
        when (added) {
            // Someone else added the name a moment ago.
            is NamedPlayer.Taken ->
                return call.respondWhoAreYou(ctx, base.copy(nameError = PlayerLink.nameTaken(added.existing)))

            is NamedPlayer.Saved -> setMe(ctx, call, added.player.id)
        }
        call.respondRedirect(next)
    }

    private suspend fun signOut(ctx: HubContext, call: ApplicationCall, id: String) {
        val self = id == call.device.id
        ctx.devices.signOut(id)
        if (self) {
            DeviceCookie.clear(call)
            // Signed out: the start page is the sign-in page now.
            call.respondRedirect("/")
        } else {
            call.respondRedirect(PATH)
        }
    }

    /** Any choice answers the invite's question, too. */
    private fun setMe(ctx: HubContext, call: ApplicationCall, playerId: String) {
        ctx.deviceStore.write(call.device.id, SharedSlots.Me, playerId)
        ctx.deviceStore.delete(call.device.id, SignInSlots.InvitedPlayerId)
        ctx.deviceStore.delete(call.device.id, GroupSlots.PendingMerge)
    }

    /** Where to go after a choice: a path on this hub only (no `//host`, no scheme). */
    internal fun safeNext(next: String?): String =
        next?.takeIf { it.startsWith("/") && !it.startsWith("//") && '\\' !in it && it.length <= MAX_NEXT } ?: PATH

    internal fun meUrl(next: String): String = if (next == PATH) ME_PATH else "$ME_PATH?next=${encode(next)}"

    internal fun encode(text: String): String = java.net.URLEncoder.encode(text, Charsets.UTF_8)

    internal fun Parameters.inviteChoice(): InviteFor = when {
        this["player"]?.isNotBlank() == true -> InviteFor.One(this["player"]!!)
        this["for"] == "anyone" -> InviteFor.Anyone
        else -> InviteFor.Choosing
    }

    private const val MAX_NEXT = 200
}
