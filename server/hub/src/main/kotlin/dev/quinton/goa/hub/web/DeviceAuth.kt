package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.store.Device
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText

/** The signed-in browser of a call inside the signed-in area. */
class DevicePrincipal(val device: Device)

/** The browser making this call; only inside the signed-in area (every `HubFeature` route). */
val ApplicationCall.device: Device
    get() = checkNotNull(principal<DevicePrincipal>()) { "Not in the signed-in area" }.device

/** The browser making this call, if it is signed in (also outside the signed-in area). */
val ApplicationCall.deviceOrNull: Device?
    get() = principal<DevicePrincipal>()?.device
        ?: request.cookies[DeviceCookie.NAME]?.let { hub.devices.find(it) }

/**
 * The `goa_device` cookie (ADR-030 §7): a random 256-bit token, `HttpOnly; Secure; SameSite=Lax`,
 * 400 days. Written by hand because Ktor refuses Secure cookies over plain HTTP, and browsers
 * accept them from `http://localhost`, which is how the hub is tried out locally.
 */
object DeviceCookie {
    const val NAME = "goa_device"
    const val MAX_AGE_SECONDS = 400L * 24 * 3600

    fun set(call: ApplicationCall, token: String) {
        call.response.header(
            HttpHeaders.SetCookie,
            "$NAME=$token; Max-Age=$MAX_AGE_SECONDS; Path=/; Secure; HttpOnly; SameSite=Lax",
        )
    }

    fun clear(call: ApplicationCall) {
        call.response.header(HttpHeaders.SetCookie, "$NAME=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax")
    }
}

/**
 * Ktor authentication by the device cookie. Without a known device: a friendly sign-in page
 * (pages), `401` JSON (`/api/…`), or, before the hub has joined a group, "not joined yet"
 * (to every address alike: the hub is published from loopback).
 */
class DeviceAuthProvider internal constructor(config: Config, private val hub: Hub) : AuthenticationProvider(config) {
    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val device = call.request.cookies[DeviceCookie.NAME]?.let(hub.devices::find)
        if (device != null && hub.membership.group() != null) {
            context.principal(DevicePrincipal(hub.devices.touch(device, hub.clock.nowMillis())))
            return
        }
        context.challenge(KEY, AuthenticationFailedCause.NoCredentials) { challenge, failed ->
            failed.respondNotSignedIn()
            challenge.complete()
        }
    }

    companion object {
        const val NAME = "device"
        private const val KEY = "goa.device"
    }
}

internal fun AuthenticationConfig.device(hub: Hub) {
    register(DeviceAuthProvider(DeviceAuthProvider.Config(DeviceAuthProvider.NAME), hub))
}

private suspend fun ApplicationCall.respondNotSignedIn() {
    val api = request.path().startsWith("/api/")
    when {
        hub.membership.group() == null -> if (api) {
            respondText(NOT_JOINED, ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
        } else {
            respondNotJoinedPage()
        }

        api -> respondText(
            """{"error":"sign-in required"}""",
            ContentType.Application.Json,
            HttpStatusCode.Unauthorized,
        )

        else -> {
            if (isHtmx) response.header("HX-Refresh", "true")
            respondSignInPage()
        }
    }
}

internal const val NOT_JOINED = "This hub hasn't joined a group yet — run goa-hub join on the server."
