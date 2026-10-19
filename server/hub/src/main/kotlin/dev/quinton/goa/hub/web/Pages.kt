package dev.quinton.goa.hub.web

import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.StaticFiles
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubDocument
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.primaryButton
import dev.quinton.goa.hub.web.ui.textAreaField
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import kotlinx.html.FlowContent
import kotlinx.html.MAIN
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.section

/*
 * The pages outside the signed-in area: sign-in, join, "not joined yet". They use the
 * same document shell without the bottom tabs.
 */

private suspend fun ApplicationCall.respondBare(
    title: String,
    status: HttpStatusCode,
    scripts: List<String> = emptyList(),
    content: MAIN.() -> Unit,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondHtml(status) {
        hubDocument(HubPage(title, showTabs = false, scripts = scripts), signedIn = false, content = content)
    }
}

private fun FlowContent.brandHeader(title: String) {
    section("brand-header") {
        img(src = StaticFiles.url("icons/brand_mark.svg"), alt = "", classes = "brand-mark")
        h2 { +title }
    }
}

/** A page asked for without a device: how to sign in. */
internal suspend fun ApplicationCall.respondSignInPage() {
    respondBare("Sign in", HttpStatusCode.Unauthorized) {
        brandHeader("Guards of Atlantis")
        p("lead") { +"Scan the group's invite QR code to sign in." }
        p {
            +"Ask someone in the group to show the invite on their phone (Group → Invite) and point your camera at it. "
            +"On an iPhone, Share → Add to Home Screen then keeps the app one tap away."
        }
        p { +"Got the invite as a message instead? Open its link, or paste it on the join page." }
        div("actions") { linkButton("Paste an invite", JOIN_PATH) }
    }
}

/** The join page: `join.js` posts the invite from the link's fragment; the form is the paste fallback. */
internal suspend fun ApplicationCall.respondJoinPage(
    error: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondBare("Join the group", status, scripts = listOf("join.js")) {
        brandHeader("Join the group")
        div {
            id = "join-status"
            attributes["role"] = "status"
            attributes["aria-live"] = "polite"
            error?.let { errorState(it) }
        }
        postForm(JOIN_PATH, classes = "stack") {
            id = "join-form"
            textAreaField("invite", "Invite", rows = 4) {
                attributes["autocomplete"] = "off"
                attributes["spellcheck"] = "false"
                placeholder = "Paste the whole invite message here"
            }
            primaryButton("Join")
        }
    }
}

/** Before the hub has joined a group, to anyone but the hub machine itself. */
internal suspend fun ApplicationCall.respondNotJoinedPage() {
    respondBare("Not set up", HttpStatusCode.ServiceUnavailable) {
        brandHeader("Guards of Atlantis")
        p("lead") { +NOT_JOINED }
        p {
            +"Whoever runs it joins it on the server: goa-hub join \"<invite>\", "
            +"with an invite from a phone in the group."
        }
    }
}

/** A page for errors outside the signed-in shell (404 for an unknown path, 403 for a cross-site POST). */
internal suspend fun ApplicationCall.respondPlainError(
    status: HttpStatusCode,
    message: String,
    title: String = status.description,
) {
    respondBare(title, status) {
        brandHeader(title)
        p("lead") { +message }
        div("actions") { linkButton("Go to the app", "/") }
    }
}
