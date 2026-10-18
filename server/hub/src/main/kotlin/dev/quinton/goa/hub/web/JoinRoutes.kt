package dev.quinton.goa.hub.web

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.Invite
import dev.quinton.goa.core.domain.sync.SyncCrypto
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.store.DeviceSlot
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Per-device slots written by sign-in. */
object SignInSlots {
    /**
     * The player a personal invite was made for (`p=` in the invite, ADR-031), stored when the
     * browser joins; the Group pages ask "You're <name>?" and clear it once answered.
     */
    val InvitedPlayerId = DeviceSlot("invitedPlayerId", String.serializer())
}

/**
 * Web sign-in (ADR-030 §7). `/join` reads the invite from the link's fragment in the page
 * (`static/join.js`; the key never reaches a server log) and posts it; the hub checks the group
 * id and key in constant time, creates a device and sets the `goa_device` cookie. Failed tries
 * are throttled ([JoinThrottle]). There is no first-run page on the web: until the hub has joined
 * a group (`goa-hub join` on the server), every page says so, from any address (the hub is
 * published from loopback, so an address proves nothing).
 */
internal fun Route.joinRoutes(hub: Hub) {
    get(JOIN_PATH) {
        if (hub.membership.group() == null) return@get call.respondNotJoinedPage()
        call.respondJoinPage()
    }
    post(JOIN_PATH) {
        val group = hub.membership.group() ?: return@post call.respondNotJoinedPage()
        val client = call.clientKey
        if (hub.joinThrottle.isBlocked(client)) {
            return@post call.joinFailed(
                HttpStatusCode.TooManyRequests,
                "Too many tries. Wait a few minutes and scan the invite again.",
            )
        }
        val invite = GroupInvite.parseInvite(call.receiveParameters()["invite"].orEmpty()).getOrElse {
            hub.joinThrottle.recordFailure(client)
            return@post call.joinFailed(HttpStatusCode.BadRequest, it.message ?: "This isn't a group invite.")
        }
        if (!sameGroup(invite.group, group)) {
            hub.joinThrottle.recordFailure(client)
            return@post call.joinFailed(
                HttpStatusCode.Forbidden,
                "This invite is for another group, not “${group.name}”.",
            )
        }
        call.signIn(hub, invite)
        call.joined()
    }
}

/** Signs this browser out (its cookie stops working; its own state is deleted). */
internal fun Route.signOutRoute(hub: Hub) {
    post(SIGN_OUT_PATH) {
        hub.devices.signOut(call.device.id)
        DeviceCookie.clear(call)
        call.respondRedirect(JOIN_PATH)
    }
}

private fun sameGroup(invited: GroupCredentials, group: GroupCredentials): Boolean =
    invited.groupId == group.groupId && SyncCrypto.same(invited.key, group.key)

/** Keeps a browser that is already signed in; otherwise creates its device and sets the cookie. */
private fun ApplicationCall.signIn(hub: Hub, invite: Invite) {
    val existing = request.cookies[DeviceCookie.NAME]?.let(hub.devices::find)
    val device = existing ?: hub.devices.create(
        name = dev.quinton.goa.hub.store.Devices.nameFromUserAgent(request.header(HttpHeaders.UserAgent)),
        now = hub.clock.nowMillis(),
    ).let { (device, token) ->
        DeviceCookie.set(this, token)
        device
    }
    invite.playerId?.let { hub.deviceStore.write(device.id, SignInSlots.InvitedPlayerId, it) }
}

private val ApplicationCall.wantsJson: Boolean
    get() = request.header(HttpHeaders.Accept)?.contains("application/json") == true

private suspend fun ApplicationCall.joined() {
    if (wantsJson) {
        respondText(buildJsonObject { put("redirect", "/") }.toString(), ContentType.Application.Json)
    } else {
        respondRedirect("/")
    }
}

private suspend fun ApplicationCall.joinFailed(status: HttpStatusCode, message: String) {
    if (wantsJson) {
        respondText(buildJsonObject { put("error", message) }.toString(), ContentType.Application.Json, status)
    } else {
        respondJoinPage(message, status)
    }
}

internal const val JOIN_PATH = "/join"
internal const val SIGN_OUT_PATH = "/signout"
