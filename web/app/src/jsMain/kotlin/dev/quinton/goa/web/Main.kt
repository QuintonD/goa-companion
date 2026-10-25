package dev.quinton.goa.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.quinton.goa.web.nav.Router
import dev.quinton.goa.web.nav.ScrollTarget
import dev.quinton.goa.web.pwa.DeviceStorage
import dev.quinton.goa.web.screens.ScreenFor
import dev.quinton.goa.web.ui.SnackbarHost
import dev.quinton.goa.web.ui.TabBar
import dev.quinton.goa.web.ui.focusWithoutScroll
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

fun main() {
    val services = AppServices.create()
    services.serviceWorker.start()
    document.getElementById("root")?.innerHTML = "" // the static splash in index.html
    renderComposable(rootElementId = "root") {
        CompositionLocalProvider(LocalServices provides services) { App() }
    }
}

/** The shell: the current screen, the bottom tab bar (hidden for full-screen table tools), messages. */
@Composable
fun App() {
    val services = LocalServices.current
    val router = services.router
    val route = router.route
    Div({
        classes("goa-app")
        if (route.showsTabBar) classes("has-tabbar")
    }) {
        // One composition per history entry: a screen opened again starts fresh, Back restores
        // scroll (ScrollRestorer) and retained state (rememberRetained).
        key(router.entry.key) { ScreenFor(route) }
        if (route.showsTabBar) TabBar(router.tab, onSelect = router::selectTab)
        SnackbarHost(services.messages, services.serviceWorker.updateReady, services.serviceWorker::applyUpdate)
    }
    ScrollRestorer(router.scrollTarget)
    FocusTitleOnNavigate(router.entry.key)
    EdgeSwipeBack(router, enabled = route.showsTabBar)
    KeepStorage()
}

/**
 * Scrolls to the target once the new screen is tall enough: a list that loads asynchronously gets
 * a few frames (up to ~1 s) to reach the remembered position.
 */
@Composable
private fun ScrollRestorer(target: ScrollTarget) {
    LaunchedEffect(target.id) {
        repeat(SCROLL_ATTEMPTS) {
            awaitFrame()
            val maxY = (document.documentElement?.scrollHeight ?: 0) - window.innerHeight
            if (maxY >= target.y || it == SCROLL_ATTEMPTS - 1) {
                window.scrollTo(0.0, target.y)
                return@LaunchedEffect
            }
        }
    }
}

/** After a navigation (not the first screen), VoiceOver and keyboard focus start at the new title. */
@Composable
private fun FocusTitleOnNavigate(entryKey: String) {
    val first = remember { mutableListOf(true) }
    LaunchedEffect(entryKey) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        awaitFrame()
        (document.querySelector(".goa-topbar-title") as? HTMLElement)?.let(::focusWithoutScroll)
    }
}

/**
 * A home-screen web app on iOS has no browser chrome and no swipe-back of its own: a swipe from the
 * left edge goes Up, like a native app. In Safari (with its own swipe-back) this stays off.
 */
@Composable
private fun EdgeSwipeBack(router: Router, enabled: Boolean) {
    DisposableEffect(enabled) {
        if (!enabled || !DeviceStorage.standalone) return@DisposableEffect onDispose { }
        var startX = -1.0
        var startY = 0.0
        val start: (Event) -> Unit = { event ->
            val touch = event.asDynamic().touches[0]
            val x = (touch.clientX as Number).toDouble()
            startX = if (x <= EDGE_PX) x else -1.0
            startY = (touch.clientY as Number).toDouble()
        }
        val end: (Event) -> Unit = { event ->
            if (startX >= 0) {
                val touch = event.asDynamic().changedTouches[0]
                val dx = (touch.clientX as Number).toDouble() - startX
                val dy = (touch.clientY as Number).toDouble() - startY
                if (dx > SWIPE_PX && kotlin.math.abs(dy) < dx / 2 && router.canGoBack) router.back()
            }
            startX = -1.0
        }
        document.addEventListener("touchstart", start)
        document.addEventListener("touchend", end)
        onDispose {
            document.removeEventListener("touchstart", start)
            document.removeEventListener("touchend", end)
        }
    }
}

/** Home-screen apps ask once for persistent storage (granted there), so Safari won't clear the data. */
@Composable
private fun KeepStorage() {
    LaunchedEffect(Unit) {
        if (DeviceStorage.standalone && DeviceStorage.persisted() == false) DeviceStorage.persist()
    }
}

private suspend fun awaitFrame() {
    Promise<Double> { resolve, _ -> window.requestAnimationFrame { resolve(it) } }.await()
}

private const val SCROLL_ATTEMPTS = 60
private const val EDGE_PX = 24.0
private const val SWIPE_PX = 72.0
