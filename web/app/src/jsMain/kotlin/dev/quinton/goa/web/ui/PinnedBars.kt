package dev.quinton.goa.web.ui

import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.HTMLElement

/**
 * Publishes this element's height on the page as the CSS custom property [name] (`--topbar-height`,
 * `--pinned-top`, `--pinned-footer`) while it is on screen, kept current as it resizes (text size,
 * rotation). goa.css turns them into the page's `scroll-padding`, so a control that takes focus
 * (VoiceOver moving on, Tab, the iPhone keyboard's next-field arrows) scrolls into the part of the
 * screen the sticky top bar and the pinned footers leave free instead of stopping underneath them.
 */
fun AttrsScope<out HTMLElement>.publishHeight(name: String) {
    ref { element ->
        val style = document.documentElement?.asDynamic()?.style
        val publish = { style?.setProperty(name, "${element.offsetHeight}px") }
        val observer = HeightObserver { _, _ -> publish() }
        observer.observe(element)
        publish()
        onDispose {
            observer.disconnect()
            style?.removeProperty(name)
        }
    }
}

@JsName("ResizeObserver")
private external class HeightObserver(callback: (dynamic, dynamic) -> Unit) {
    fun observe(target: HTMLElement)

    fun disconnect()
}
