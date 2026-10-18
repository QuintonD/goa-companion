package dev.quinton.goa.hub.web.ui

import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeatures
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.web.deviceOrNull
import dev.quinton.goa.hub.web.hub
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.html.A
import kotlinx.html.BODY
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.serialization.Serializable

/**
 * The app's bottom navigation, as on the phone (`GoaApp.kt`): Heroes, Timer, Draft, Players.
 * Rules, Card trivia and Settings are top-bar actions there, and Group sits under Settings.
 */
enum class Tab(val label: String, val path: String, val icon: String) {
    HEROES("Heroes", "/heroes", "nav-heroes"),
    TIMER("Timer", "/timer", "nav-timer"),
    DRAFT("Draft", "/draft", "nav-draft"),
    PLAYERS("Players", "/players", "nav-players"),
}

/** A top-bar icon button. [icon]: an [Icons] name. */
data class TopAction(val label: String, val href: String, val icon: String)

object TopActions {
    val Rules = TopAction("Rules", "/rules", Icons.RULES)
    val Trivia = TopAction("Card trivia", "/trivia", Icons.TRIVIA)
    val Settings = TopAction("Settings", "/settings", Icons.SETTINGS)

    /** The phone's top-bar actions for each tab's first screen. */
    fun forTab(tab: Tab?): List<TopAction> = when (tab) {
        Tab.HEROES -> listOf(Rules, Trivia, Settings)
        Tab.TIMER -> listOf(Rules, Settings)
        Tab.DRAFT, Tab.PLAYERS -> listOf(Settings)
        null -> emptyList()
    }
}

/**
 * The browser's theme choice (Settings); [SYSTEM] follows the phone's light/dark setting. A
 * signed-in browser that hasn't chosen gets [DEFAULT], dark, as the app does ("easiest on the eyes
 * at the table"; it also keeps the home-screen app's light status-bar text readable on iOS).
 */
@Serializable
enum class ThemeChoice {
    SYSTEM,
    DARK,
    LIGHT,
    ;

    companion object {
        val DEFAULT = DARK
    }
}

/** Core per-device slots the shell itself reads. */
object ShellSlots {
    /** Written by the Settings page; read by every page. */
    val Theme = DeviceSlot("theme", ThemeChoice.serializer())
}

/**
 * One page of the web app (ADR-030 §8): [title] in the top bar, [tab] highlighted in the bottom
 * bar (null = a page outside the tabs, e.g. Settings), an optional [back] link, the top-bar
 * [actions], and extra [scripts] (paths under `/static/`, loaded `defer`).
 * [showTabs] = false hides the bottom bar (the running timer, the draft board, as on the phone).
 */
data class HubPage(
    val title: String,
    val tab: Tab? = null,
    val back: String? = null,
    val actions: List<TopAction> = TopActions.forTab(tab),
    val showTabs: Boolean = true,
    val scripts: List<String> = emptyList(),
    /** Extra stylesheets after app.css, relative to `static/` (e.g. `timer/timer.css`). */
    val styles: List<String> = emptyList(),
)

/** Renders [page] around [content] (the `<main>` element) and sends it. */
suspend fun ApplicationCall.respondPage(
    page: HubPage,
    status: HttpStatusCode = HttpStatusCode.OK,
    content: MAIN.() -> Unit,
) {
    val device = deviceOrNull
    val theme =
        device?.let { hub.deviceStore.read(it.id, ShellSlots.Theme) ?: ThemeChoice.DEFAULT } ?: ThemeChoice.SYSTEM
    val ctx = HubContext(hub)
    val bars = if (device != null && page.showTabs) HubFeatures.mapNotNull { it.shellBar(ctx, this) } else emptyList()
    response.header(HttpHeaders.CacheControl, "no-store")
    respondHtml(status) { hubDocument(page, theme, signedIn = device != null, bars = bars, content = content) }
}

/**
 * Sends a piece of HTML without the page around it, for htmx swaps
 * (`hx-get="/players/list" hx-target="#list"`). [content] is rendered as is.
 */
suspend fun ApplicationCall.respondFragment(
    status: HttpStatusCode = HttpStatusCode.OK,
    content: FlowContent.() -> Unit,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    val html = createHTML(prettyPrint = false).div { content() }.removePrefix("<div>").removeSuffix("</div>")
    respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"), status)
}

/** The whole document; public so tests and special pages (sign-in, first run) share it. */
fun HTML.hubDocument(
    page: HubPage,
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    signedIn: Boolean = true,
    bars: List<FlowContent.() -> Unit> = emptyList(),
    content: MAIN.() -> Unit,
) {
    lang = "en"
    if (theme != ThemeChoice.SYSTEM) attributes["data-theme"] = theme.name.lowercase()
    head { hubHead(page, theme) }
    val tabs = signedIn && page.showTabs
    val bodyClasses = listOfNotNull("has-tabs".takeIf { tabs }, "has-bars".takeIf { tabs && bars.isNotEmpty() })
    body(bodyClasses.joinToString(" ").ifEmpty { null }) {
        topBar(page)
        main("content") {
            id = "main"
            content()
        }
        if (tabs) {
            div("bottom-dock") {
                div("shell-bars") { bars.forEach { it() } }
                tabBar(page.tab)
            }
        }
    }
}

private fun HEAD.hubHead(page: HubPage, theme: ThemeChoice) {
    meta(charset = "utf-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1, viewport-fit=cover")
    title("${page.title} · Guards of Atlantis")
    meta(name = "robots", content = "noindex, nofollow")
    meta(name = "color-scheme", content = "dark light")
    when (theme) {
        ThemeChoice.DARK -> meta(name = "theme-color", content = DARK_BAR)

        ThemeChoice.LIGHT -> meta(name = "theme-color", content = LIGHT_BAR)

        ThemeChoice.SYSTEM -> {
            meta(name = "theme-color", content = DARK_BAR) { attributes["media"] = "(prefers-color-scheme: dark)" }
            meta(name = "theme-color", content = LIGHT_BAR) { attributes["media"] = "(prefers-color-scheme: light)" }
        }
    }
    meta(name = "apple-mobile-web-app-capable", content = "yes")
    meta(name = "mobile-web-app-capable", content = "yes")
    // Light status-bar text over the page suits dark pages; a light page gets iOS's dark text on its own bar.
    val statusBar = if (theme == ThemeChoice.LIGHT) "default" else "black-translucent"
    meta(name = "apple-mobile-web-app-status-bar-style", content = statusBar)
    meta(name = "apple-mobile-web-app-title", content = "GoA")
    // CSP-safe htmx: no injected <style>, no eval, no scripts from swapped content.
    meta(name = "htmx-config", content = HTMX_CONFIG)
    link(rel = "manifest", href = "/manifest.webmanifest")
    link(rel = "icon", href = "/static/icons/brand_mark.svg", type = "image/svg+xml")
    link(rel = "apple-touch-icon", href = "/apple-touch-icon.png")
    link(rel = "stylesheet", href = StaticFiles.url("app.css"))
    page.styles.forEach { link(rel = "stylesheet", href = StaticFiles.url(it)) }
    script(src = StaticFiles.url("htmx.min.js")) { defer = true }
    script(src = StaticFiles.url("hub.js")) { defer = true }
    page.scripts.forEach { script(src = StaticFiles.url(it)) { defer = true } }
}

private fun BODY.topBar(page: HubPage) {
    header("top-bar") {
        page.back?.let { href ->
            a(href, classes = "icon-button") {
                attributes["aria-label"] = "Back"
                icon(Icons.BACK)
            }
        }
        h1("top-title") { +page.title }
        if (page.actions.isNotEmpty()) {
            nav("top-actions") {
                attributes["aria-label"] = "Page actions"
                page.actions.forEach { action ->
                    a(action.href, classes = "icon-button") {
                        attributes["aria-label"] = action.label
                        attributes["title"] = action.label
                        icon(action.icon)
                    }
                }
            }
        }
    }
}

private fun FlowContent.tabBar(current: Tab?) {
    nav("tab-bar") {
        attributes["aria-label"] = "Main"
        Tab.entries.forEach { tab ->
            val selected = tab == current
            a(tab.path, classes = if (selected) "tab selected" else "tab") {
                if (selected) attributes["aria-current"] = "page"
                tabIcon(tab, selected)
                span("tab-label") { +tab.label }
            }
        }
    }
}

private fun A.tabIcon(tab: Tab, selected: Boolean) {
    span("tab-indicator") { icon(if (selected) "${tab.icon}-filled" else tab.icon) }
}

private const val DARK_BAR = "#0B1622"
private const val LIGHT_BAR = "#F4EFE6"

private const val HTMX_CONFIG =
    """{"includeIndicatorStyles":false,"allowEval":false,"allowScriptTags":false,"selfRequestsOnly":true,""" +
        """"historyCacheSize":0,"refreshOnHistoryMiss":true}"""
