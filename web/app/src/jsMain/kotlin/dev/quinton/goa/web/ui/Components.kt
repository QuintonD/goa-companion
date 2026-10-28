package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.nav.Routes
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement

/*
 * The web design system's components (docs/DESIGN.md "Steel and bronze", ADR-042), styled by
 * goa.css. Screens build from these; a new pattern goes here first so every stream shares it.
 */

/** Icon sizes: 24 px default, 20 px in dense rows, 48 px in empty states. */
enum class IconSize(val css: String?) { NORMAL(null), SMALL("small"), LARGE("large") }

/**
 * An icon, inline SVG tinted with the text colour. Decorative: the control around it carries the
 * label. The markup is generated from the SVG files (`IconMarkup`, `:web:app:iconMarkup`): web-only
 * icons live in `web/app/src/jsMain/resources/icons/`, the tab icons (`nav_*`) in `design/icons/`.
 */
@Composable
fun Icon(name: String, size: IconSize = IconSize.NORMAL) {
    key(name) {
        Span({
            classes("goa-icon")
            size.css?.let { classes(it) }
            attr("aria-hidden", "true")
            ref { element ->
                element.innerHTML = IconMarkup[name] ?: ""
                onDispose { }
            }
        })
    }
}

/** A round 48 px icon button; [label] is its accessible name and tooltip. */
@Composable
fun IconButton(icon: String, label: String, onClick: () -> Unit, extraClass: String? = null) {
    Button({
        type(ButtonType.Button)
        classes("goa-icon-button")
        extraClass?.let { classes(it) }
        attr("aria-label", label)
        title(label)
        onClick { onClick() }
    }) { Icon(icon) }
}

/** A link to [route]: a real `<a href="#/…">` (long-press, VoiceOver "link"), navigated by the router. */
@Composable
fun RouteLink(
    route: Route,
    attrs: AttrBuilderContext<HTMLAnchorElement>? = null,
    content: ContentBuilder<HTMLAnchorElement>,
) {
    val router = LocalServices.current.router
    A(Routes.hash(route), {
        attrs?.invoke(this)
        onClick { event ->
            // Modified clicks (new tab/window) keep the browser's behaviour.
            val modified = event.ctrlKey || event.metaKey || event.shiftKey || event.altKey
            if (modified || event.button.toInt() != 0) return@onClick
            event.preventDefault()
            router.navigate(route)
        }
    }, content)
}

/** An icon button that is a link to [route]. */
@Composable
fun IconLink(icon: String, label: String, route: Route, extraClass: String? = null) {
    RouteLink(route, {
        classes("goa-icon-button")
        extraClass?.let { classes(it) }
        attr("aria-label", label)
        title(label)
    }) { Icon(icon) }
}

enum class ButtonKind(val css: String) {
    FILLED(
        "filled",
    ),
    TONAL("tonal"),
    OUTLINED("outlined"),
    TEXT("text"),
    DANGER("danger"),
}

@Composable
fun GoaButton(
    text: String,
    onClick: () -> Unit,
    kind: ButtonKind = ButtonKind.FILLED,
    icon: String? = null,
    enabled: Boolean = true,
    block: Boolean = false,
) {
    Button({
        type(ButtonType.Button)
        classes("goa-button", kind.css)
        if (block) classes("block")
        if (!enabled) disabled()
        onClick { onClick() }
    }) {
        icon?.let { Icon(it) }
        Text(text)
    }
}

/**
 * A screen: the steel-banner top bar (title in engraved Cinzel caps, Back when there is somewhere to
 * go Up to, [actions] on the right) over the content. Sets the document title.
 * [flush]: content spans the full width without screen padding (maps, the table). [onBack] replaces
 * the Back button's Up (e.g. to ask before leaving a quiz round); null = Up.
 * [fill]: the screen is the window's height and never scrolls (the board, the game table, a puzzle).
 */
@Composable
fun Screen(
    title: String,
    actions: @Composable () -> Unit = {},
    flush: Boolean = false,
    showBack: Boolean? = null,
    fill: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val router = LocalServices.current.router
    SideEffect { document.title = "$title · Atlantis Companion" }
    Div({
        classes("goa-screen")
        // A board: exactly the window's height (compact title bar), the content lays itself out.
        if (fill) classes("fill")
    }) {
        Header({
            classes("goa-topbar")
            publishHeight("--topbar-height")
        }) {
            Div({ classes("goa-topbar-row") }) {
                if (showBack ?: router.canGoBack) {
                    IconButton("back", "Back", onClick = { onBack?.invoke() ?: router.back() }, extraClass = "nav")
                }
                H1({
                    classes("goa-topbar-title", "t-banner-title")
                    tabIndex(-1)
                }) { Text(title) }
                Div({ classes("goa-topbar-actions") }) { actions() }
            }
        }
        Main({
            classes("goa-content")
            if (flush) classes("flush")
        }) { content() }
    }
}

/** Copper small-caps section head with its fading rule (the rulebook's heads). */
@Composable
fun SectionHeader(title: String) {
    Div({ classes("goa-section-header") }) {
        H2({ classes("t-section-head") }) { Text(title) }
    }
}

/** The app's card: surface, bronze sheen, engraved border. [flush]: no padding (holds list rows). */
@Composable
fun GoaCard(flush: Boolean = false, content: ContentBuilder<org.w3c.dom.HTMLDivElement>) {
    Div({
        classes("goa-card")
        if (flush) classes("flush")
    }, content)
}

/** Text the game prints (card text, rulebook excerpts) on the cards' parchment, in Crimson Pro. */
@Composable
fun ParchmentPanel(content: ContentBuilder<org.w3c.dom.HTMLDivElement>) {
    Div({ classes("goa-parchment", "t-printed") }, content)
}

/** Rows inside a [GoaCard] (flush): a `<ul>` whose items are [ListRow]s. */
@Composable
fun RowList(content: ContentBuilder<org.w3c.dom.HTMLUListElement>) {
    Ul({ classes("goa-list") }, content)
}

/**
 * One list row (≥ 56 px): [leading] icon, title and subtitle, [trailing] text or a chevron when it
 * opens [route]. With [onClick] instead of a route it is a button. Call inside [RowList].
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: String? = null,
    trailing: String? = null,
    route: Route? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        if (leading != null || leadingIcon != null) {
            Span({ classes("goa-row-leading") }) {
                if (leading != null) leading() else Icon(leadingIcon!!)
            }
        }
        Span({ classes("goa-row-text") }) {
            Span({ classes("goa-row-title", "t-title-medium") }) { Text(title) }
            subtitle?.let { Span({ classes("goa-row-subtitle", "t-body-medium") }) { Text(it) } }
        }
        if (trailing != null || route != null) {
            Span({ classes("goa-row-trailing", "t-label-large") }) {
                trailing?.let { Text(it) }
                if (route != null) Icon("chevron_right", IconSize.SMALL)
            }
        }
    }
    Li {
        when {
            route != null -> RouteLink(route, { classes("goa-row") }) { body() }

            onClick != null -> Button({
                type(ButtonType.Button)
                classes("goa-row")
                onClick { onClick() }
            }) { body() }

            else -> Div({ classes("goa-row") }) { body() }
        }
    }
}

/** A filter chip (toggle button); selected = tonal teal fill. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, icon: String? = null) {
    Button({
        type(ButtonType.Button)
        classes("goa-chip")
        attr("aria-pressed", selected.toString())
        onClick { onClick() }
    }) {
        if (selected) Icon("check", IconSize.SMALL) else icon?.let { Icon(it, IconSize.SMALL) }
        Text(label)
    }
}

/** A row of chips that scrolls sideways ([wrap] = wraps onto more lines instead). */
@Composable
fun ChipRow(wrap: Boolean = false, content: ContentBuilder<org.w3c.dom.HTMLDivElement>) {
    Div({
        classes("goa-chip-row")
        if (wrap) classes("wrap")
    }, content)
}

/** A segmented control (one of a few options), a radio group for assistive tech. */
@Composable
fun <T> Segmented(label: String, options: List<T>, selected: T, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    Div({
        classes("goa-segmented")
        attr("role", "radiogroup")
        attr("aria-label", label)
    }) {
        options.forEach { option ->
            Button({
                type(ButtonType.Button)
                attr("role", "radio")
                attr("aria-checked", (option == selected).toString())
                onClick { onSelect(option) }
            }) { Text(optionLabel(option)) }
        }
    }
}

/** A linear progress bar; [fraction] null = indeterminate. [label] names it for assistive tech. */
@Composable
fun LinearProgress(fraction: Double?, label: String) {
    Div({
        classes("goa-progress")
        if (fraction == null) classes("indeterminate")
        attr("role", "progressbar")
        attr("aria-label", label)
        if (fraction != null) {
            attr("aria-valuemin", "0")
            attr("aria-valuemax", "100")
            attr("aria-valuenow", (fraction * 100).toInt().toString())
        }
    }) {
        Div({ if (fraction != null) style { property("width", "${(fraction.coerceIn(0.0, 1.0) * 100)}%") } })
    }
}

/** A centred message for an empty list or a screen with nothing yet, with an optional action. */
@Composable
fun EmptyState(icon: String, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Div({ classes("goa-state") }) {
        Icon(icon, IconSize.LARGE)
        H2({ classes("t-title-large") }) { Text(title) }
        P({ classes("t-body-medium") }) { Text(message) }
        action?.invoke()
    }
}

/** Loading: a spinner with [label] read out (`role=status`). */
@Composable
fun LoadingState(label: String = "Loading") {
    Div({
        classes("goa-state")
        attr("role", "status")
    }) {
        Div({ classes("goa-spinner") })
        P({ classes("t-body-medium") }) { Text(label) }
    }
}

/** Something failed: what and what to do, with Try again. */
@Composable
fun ErrorState(title: String, message: String, onRetry: (() -> Unit)? = null) {
    EmptyState(
        "cloud_off",
        title,
        message,
        onRetry?.let { retry ->
            { GoaButton("Try again", retry, ButtonKind.TONAL, icon = "refresh") }
        },
    )
}

/** Body copy in the secondary text colour. */
@Composable
fun BodyText(text: String, extraClass: String = "t-body-medium") {
    P({
        classes(extraClass, "c-variant")
        style { property("margin", "0") }
    }) { Text(text) }
}

/** Moves keyboard / VoiceOver focus to [element] without scrolling. */
fun focusWithoutScroll(element: HTMLElement) {
    val options: dynamic = js("({ preventScroll: true })")
    element.asDynamic().focus(options)
}
