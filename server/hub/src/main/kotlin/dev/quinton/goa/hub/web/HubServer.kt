package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.feature.HubFeatures
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Tab
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

/**
 * The hub's web application (ADR-030 §4, §7, §8): security headers and the same-origin check on
 * every call, the open routes (health, sync API, join, static files), and the signed-in area
 * where every [HubFeature] lives.
 */
fun Application.hubModule(hub: Hub, features: List<HubFeature> = HubFeatures) {
    attributes.put(HubKey, hub)
    intercept(ApplicationCallPipeline.Plugins) {
        call.securityHeaders()
        val exempt = call.request.path() == SYNC_PATH // bearer token, not a cookie: no CSRF
        if (call.isStateChanging && !exempt && !call.isSameOrigin(hub.config.get().publicUrl)) {
            call.respondPlainError(HttpStatusCode.Forbidden, "This request didn't come from the hub's own pages.")
            finish()
        } else if (declaresTooLarge(call)) {
            call.respondTooLarge()
            finish()
        }
    }
    limitRequestBodies()
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ -> call.respondNotFound() }
        exception<PayloadTooLargeException> { call, _ -> call.respondTooLarge() }
        exception<Throwable> { call, cause ->
            log.error("{} {} failed", call.request.local.method.value, call.request.path(), cause)
            call.respondServerError()
        }
    }
    install(Authentication) { device(hub) }
    routing {
        syncRoutes(hub)
        joinRoutes(hub)
        openStaticRoutes(hub)
        authenticate(DeviceAuthProvider.NAME) {
            get("/") { call.respondRedirect(Tab.HEROES.path) }
            assetRoutes(hub)
            pushRoutes(hub)
            signOutRoute(hub)
            val ctx = HubContext(hub)
            features.forEach { feature -> with(feature) { routes(ctx) } }
        }
    }
    monitor.subscribe(ApplicationStarted) { hub.push.start() }
    monitor.subscribe(ApplicationStopped) { hub.push.close() }
}

/**
 * On every response: no indexing, a strict self-only CSP (no inline script or style: htmx is
 * configured not to inject any), no framing, no referrer leaving the hub, no sniffing.
 */
private fun ApplicationCall.securityHeaders() {
    response.header("X-Robots-Tag", "noindex, nofollow")
    response.header("Content-Security-Policy", CSP)
    response.header("X-Content-Type-Options", "nosniff")
    response.header("X-Frame-Options", "DENY")
    response.header("Referrer-Policy", "same-origin")
    response.header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
    response.header("Cross-Origin-Opener-Policy", "same-origin")
    if (isHttps) response.header(HttpHeaders.StrictTransportSecurity, "max-age=31536000")
}

private suspend fun ApplicationCall.respondNotFound() {
    when {
        request.path().startsWith("/api/") -> respondText("Not found.", status = HttpStatusCode.NotFound)

        deviceOrNull != null -> respondPage(HubPage("Not found", back = "/"), HttpStatusCode.NotFound) {
            emptyState("Nothing here", "This page doesn't exist (any more).", "Go to Heroes", Tab.HEROES.path)
        }

        else -> respondPlainError(HttpStatusCode.NotFound, "This page doesn't exist.")
    }
}

private suspend fun ApplicationCall.respondTooLarge() {
    respondText("The request is too large.", status = HttpStatusCode.PayloadTooLarge)
}

private suspend fun ApplicationCall.respondServerError() {
    if (deviceOrNull != null && !request.path().startsWith("/api/")) {
        respondPage(HubPage("Something went wrong", back = "/"), HttpStatusCode.InternalServerError) {
            errorState("Something went wrong on the hub. Try again; if it keeps happening, tell whoever runs the hub.")
        }
    } else {
        respondText("Something went wrong on the hub.", status = HttpStatusCode.InternalServerError)
    }
}

internal const val CSP =
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; " +
        "connect-src 'self'; manifest-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'none'; " +
        "form-action 'self'; frame-ancestors 'none'"

private val log = LoggerFactory.getLogger("dev.quinton.goa.hub.Web")
