package dev.quinton.goa.web.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window

/** The in-app theme choice, as on Android (Settings > Theme; dark by default for table lighting). */
enum class ThemeMode(val attr: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    SYSTEM("system", "System"),
}

/**
 * Applies the theme as `<html data-theme>` (goa.css holds both palettes). Kept in localStorage, not
 * IndexedDB, because index.html must read it synchronously before the first paint (no flash of the
 * wrong theme).
 */
class ThemeController {
    var mode: ThemeMode by mutableStateOf(load())
        private set

    init {
        apply(mode)
        window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", { apply(mode) })
    }

    fun select(newMode: ThemeMode) {
        mode = newMode
        try {
            localStorage.setItem(KEY, newMode.attr)
        } catch (_: Throwable) {
            // Storage full or blocked (private mode): the choice lasts for this visit.
        }
        apply(newMode)
    }

    /** True when the dark palette is showing. */
    val isDark: Boolean
        get() = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> !window.matchMedia("(prefers-color-scheme: light)").matches
        }

    private fun apply(mode: ThemeMode) {
        document.documentElement?.setAttribute("data-theme", mode.attr)
        // The browser's toolbar / Android status bar takes the steel banner's top colour.
        document.querySelector("meta[name=\"theme-color\"]")
            ?.setAttribute("content", if (isDark) BANNER_TOP_DARK else BANNER_TOP_LIGHT)
    }

    private companion object {
        const val KEY = "goa.theme"
        const val BANNER_TOP_DARK = "#26496A"
        const val BANNER_TOP_LIGHT = "#2E5C7E"

        fun load(): ThemeMode {
            val stored = try {
                localStorage.getItem(KEY)
            } catch (_: Throwable) {
                null
            }
            return ThemeMode.entries.firstOrNull { it.attr == stored } ?: ThemeMode.DARK
        }
    }
}
