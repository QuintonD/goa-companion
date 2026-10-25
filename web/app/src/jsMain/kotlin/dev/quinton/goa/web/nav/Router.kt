package dev.quinton.goa.web.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** One browser history entry of the app: [depth] counts the app's entries before it (0 = the first). */
data class HistoryEntry(val key: String, val depth: Int, val tab: Tab)

/** The browser's History API and scroll position, behind a port so [Router] is tested without a browser. */
interface HistoryPort {
    fun hash(): String

    /** The app's state on the current entry; null for an entry the app didn't create (a typed hash). */
    fun entry(): HistoryEntry?

    fun push(entry: HistoryEntry, hash: String)

    fun replace(entry: HistoryEntry, hash: String)

    fun back()

    /** Calls [listener] when the person moves through history (back/forward button, swipe, typed hash). */
    fun onPop(listener: () -> Unit)

    fun scrollY(): Double
}

/** Where the page should scroll after the screen for a navigation has rendered. [id] makes repeats distinct. */
data class ScrollTarget(val id: Int, val y: Double)

/**
 * Hash routing over the History API (ADR-043, "Web shell (F2)").
 *
 * - The hash is the address of the screen (`#/heroes/arien`), so reload, bookmarks and "Add to Home
 *   Screen" reopen it. Every navigation is a history entry, so the browser's Back and the iOS
 *   swipe-back walk the app like they walk a website.
 * - The top bar's Back arrow is "up": it returns to the previous screen when that is on the same tab,
 *   otherwise to the screen's [Route.parent] (a deep link opened cold has no previous screen).
 * - Each tab remembers the screen it was last on; tapping the current tab returns to its root.
 * - Scroll positions are kept per history entry (and per screen, for returning through a tab or Up),
 *   so lists reopen where they were.
 */
class Router(
    private val history: HistoryPort,
    private val newKey: () -> String = { Random.nextLong().toString(36).removePrefix("-") },
) {
    var route: Route by mutableStateOf(Route.Catalog)
        private set

    var tab: Tab by mutableStateOf(Tab.HEROES)
        private set

    var entry: HistoryEntry by mutableStateOf(HistoryEntry("", 0, Tab.HEROES))
        private set

    /** Applied by the app shell once the new screen has rendered. */
    var scrollTarget: ScrollTarget by mutableStateOf(ScrollTarget(0, 0.0))
        private set

    /** The app's entries by depth as far as this page load knows them (null: from before a reload). */
    private val known = mutableListOf<HistoryEntry?>()
    private val lastRoute = mutableMapOf<Tab, Route>()
    private val scrollByEntry = mutableMapOf<String, Double>()
    private val scrollByPath = mutableMapOf<String, Double>()
    private val retained = mutableMapOf<String, MutableMap<String, Any>>()

    init {
        val parsed = Routes.parse(history.hash())
        val existing = history.entry()
        val start = existing ?: HistoryEntry(newKey(), 0, parsed.tab ?: Tab.HEROES)
        // Normalise the address (empty hash → #/heroes) without adding an entry.
        if (existing == null || history.hash() != Routes.hash(parsed)) history.replace(start, Routes.hash(parsed))
        show(start, parsed, scrollY = 0.0)
        history.onPop(::onPop)
    }

    /** Opens [route] as a new history entry ([replace]: in place of the current one). */
    fun navigate(route: Route, replace: Boolean = false) = go(route, replace, route.tab ?: tab, scrollY = 0.0)

    /** Up: the previous screen if it is on this tab, else the parent screen (or nothing at a tab's root). */
    fun back() {
        when {
            previousOnThisTab() -> history.back()

            else -> {
                val parent = route.parent ?: return
                go(parent, replace = true, tab = parent.tab ?: tab, scrollY = scrollByPath[parent.path] ?: 0.0)
            }
        }
    }

    /** Whether the current screen has somewhere to go Up to (shows the Back arrow). */
    val canGoBack: Boolean get() = route.parent != null || previousOnThisTab()

    private fun previousOnThisTab(): Boolean = entry.depth > 0 && known.getOrNull(entry.depth - 1)?.tab == tab

    /** The bottom bar: another tab reopens where it was; the current tab returns to its root (or the top). */
    fun selectTab(target: Tab) {
        if (target == tab) {
            if (route == target.root) {
                scrollTarget = ScrollTarget(scrollTarget.id + 1, 0.0)
            } else {
                go(target.root, replace = false, tab = target, scrollY = 0.0)
            }
            return
        }
        val destination = lastRoute[target] ?: target.root
        go(destination, replace = false, tab = target, scrollY = scrollByPath[destination.path] ?: 0.0)
    }

    /**
     * State a screen keeps while its history entry exists (a half-filled form, a chosen filter): back to
     * the entry, it is still there; the entry dropped (navigated away forward), it is gone.
     */
    fun <T : Any> retain(name: String, init: () -> T): T {
        val bag = retained.getOrPut(entry.key) { mutableMapOf() }
        @Suppress("UNCHECKED_CAST")
        return bag.getOrPut(name, init) as T
    }

    private fun go(route: Route, replace: Boolean, tab: Tab, scrollY: Double) {
        if (route == this.route && tab == this.tab && !replace) {
            scrollTarget = ScrollTarget(scrollTarget.id + 1, 0.0)
            return
        }
        saveScroll()
        val depth = if (replace) entry.depth else entry.depth + 1
        val next = HistoryEntry(newKey(), depth, tab)
        if (replace) {
            retained.remove(entry.key)
            history.replace(next, Routes.hash(route))
        } else {
            // Entries after the current one can no longer be reached: drop what they kept.
            while (known.size > depth) known.removeAt(known.lastIndex)?.let { retained.remove(it.key) }
            history.push(next, Routes.hash(route))
        }
        show(next, route, scrollY)
    }

    private fun onPop() {
        saveScroll()
        val parsed = Routes.parse(history.hash())
        val popped = history.entry() ?: HistoryEntry(newKey(), entry.depth + 1, parsed.tab ?: tab).also {
            history.replace(it, Routes.hash(parsed)) // a hash typed into the address bar
        }
        show(popped, parsed, scrollByEntry[popped.key] ?: scrollByPath[parsed.path] ?: 0.0)
    }

    private fun show(next: HistoryEntry, route: Route, scrollY: Double) {
        while (known.size <= next.depth) known.add(null)
        known[next.depth] = next
        entry = next
        this.route = route
        tab = next.tab
        lastRoute[next.tab] = route
        scrollTarget = ScrollTarget(scrollTarget.id + 1, scrollY)
    }

    private fun saveScroll() {
        val y = history.scrollY()
        scrollByEntry[entry.key] = y
        scrollByPath[route.path] = y
    }
}
