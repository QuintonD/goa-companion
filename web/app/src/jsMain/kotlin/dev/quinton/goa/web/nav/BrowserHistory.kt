package dev.quinton.goa.web.nav

import kotlinx.browser.window

/** [HistoryPort] over `window.history`: the entry's state is a plain object `{goa, key, depth, tab}`. */
class BrowserHistory : HistoryPort {
    init {
        // The router restores scroll positions itself, after the screen has rendered.
        window.history.asDynamic().scrollRestoration = "manual"
    }

    override fun hash(): String = window.location.hash

    override fun entry(): HistoryEntry? {
        val state = window.history.state.asDynamic()
        if (state == null || state.goa != 1) return null
        val tab = Tab.entries.firstOrNull { it.name == state.tab as? String }
        val key = state.key as? String
        val depth = (state.depth as? Number)?.toInt()
        return if (tab != null && key != null && depth != null) HistoryEntry(key, depth, tab) else null
    }

    override fun push(entry: HistoryEntry, hash: String) = window.history.pushState(entry.toState(), "", hash)

    override fun replace(entry: HistoryEntry, hash: String) = window.history.replaceState(entry.toState(), "", hash)

    override fun back() = window.history.back()

    override fun onPop(listener: () -> Unit) {
        window.addEventListener("popstate", { listener() })
    }

    override fun scrollY(): Double = window.scrollY

    private fun HistoryEntry.toState(): dynamic {
        val state = js("({})")
        state.goa = 1
        state.key = key
        state.depth = depth
        state.tab = tab.name
        return state
    }
}
