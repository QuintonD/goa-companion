package dev.quinton.goa.hub.web.ui

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HtmlBlockTag
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dialog
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

/*
 * The web app's components (DESIGN.md, the phone's core:designsystem), as kotlinx.html functions.
 * Styling lives in static/app.css (classes only: the CSP forbids inline styles). htmx attributes
 * can be added by the caller in the block: `primaryButton("Save") { attributes["hx-post"] = "/x" }`.
 */

/** Names of the icons in `static/icons/` (CSS masks tinted with the text colour). */
object Icons {
    const val BACK = "arrow_back"
    const val SETTINGS = "settings"
    const val RULES = "menu_book"
    const val TRIVIA = "quiz"
    const val CLOSE = "close"
    const val ADD = "add"
    const val CHEVRON = "chevron_right"
    const val DOWNLOAD = "download"
    const val ERROR = "error"
    const val INFO = "info"
    const val CHECK = "check"
    const val DELETE = "delete"
    const val EDIT = "edit"
    const val SHARE = "share"
    const val NOTIFICATIONS = "notifications"
    const val LOGOUT = "logout"
    const val DEVICES = "devices"
    const val GROUP = "group"
    const val SEARCH = "search"
}

/** An icon from [Icons] (decorative: pair it with a label or an `aria-label` on the control). */
fun FlowContent.icon(name: String, classes: String? = null) {
    span(listOfNotNull("icon", "icon-$name", classes).joinToString(" ")) { attributes["aria-hidden"] = "true" }
}

/** `GoaCard`: a surface with the engraved border. */
fun FlowContent.card(classes: String? = null, block: HtmlBlockTag.() -> Unit) {
    div(listOfNotNull("card", classes).joinToString(" ")) { block() }
}

/** `SectionHeader`: the Cinzel overline above a group of rows. */
fun FlowContent.sectionHeader(title: String) {
    h2("section-header") { +title }
}

/**
 * A list row: optional [leading] (avatar, sigil), [title], [subtitle], optional [trailing] text,
 * and a chevron when it links somewhere ([href]).
 */
fun FlowContent.listRow(
    title: String,
    subtitle: String? = null,
    href: String? = null,
    trailing: String? = null,
    leading: (FlowContent.() -> Unit)? = null,
) {
    val body: FlowContent.() -> Unit = {
        leading?.let { div("row-leading") { it() } }
        div("row-text") {
            span("row-title") { +title }
            subtitle?.let { span("row-subtitle") { +it } }
        }
        trailing?.let { span("row-trailing") { +it } }
        if (href != null) icon(Icons.CHEVRON, "row-chevron")
    }
    if (href != null) a(href, classes = "row") { body() } else div("row") { body() }
}

/** Button styles: filled bronze, tonal, outlined, plain text, and destructive. */
enum class ButtonStyle(val css: String) {
    PRIMARY("btn btn-primary"),
    TONAL("btn btn-tonal"),
    OUTLINED("btn btn-outlined"),
    TEXT("btn btn-text"),
    DANGER("btn btn-danger"),
}

/** A `<button>`; [type] submit inside forms. Add htmx attributes in [block]. */
fun FlowContent.hubButton(
    label: String,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    type: ButtonType = ButtonType.submit,
    icon: String? = null,
    block: kotlinx.html.BUTTON.() -> Unit = {},
) {
    button(type = type, classes = style.css) {
        icon?.let { icon(it) }
        span { +label }
        block()
    }
}

fun FlowContent.primaryButton(label: String, block: kotlinx.html.BUTTON.() -> Unit = {}) =
    hubButton(label, ButtonStyle.PRIMARY, block = block)

/** A link styled as a button. */
fun FlowContent.linkButton(label: String, href: String, style: ButtonStyle = ButtonStyle.TONAL, icon: String? = null) {
    a(href, classes = style.css) {
        icon?.let { icon(it) }
        span { +label }
    }
}

/** `GoaFilterChip`: a toggle chip; a link when [href] is set, else a button (add htmx in [block]). */
fun FlowContent.chip(
    label: String,
    selected: Boolean = false,
    href: String? = null,
    block: HtmlBlockTag.() -> Unit = {},
) {
    val css = if (selected) "chip selected" else "chip"
    if (href != null) {
        a(href, classes = css) {
            if (selected) attributes["aria-current"] = "true"
            if (selected) icon(Icons.CHECK)
            span { +label }
            block()
        }
    } else {
        button(type = ButtonType.button, classes = css) {
            attributes["aria-pressed"] = selected.toString()
            if (selected) icon(Icons.CHECK)
            span { +label }
            block()
        }
    }
}

/** A row of chips that wraps. */
fun FlowContent.chipRow(block: HtmlBlockTag.() -> Unit) {
    div("chip-row") { block() }
}

/**
 * `PlayerAvatar`: the player's crest in their colour (theme tone), or their initial; an unknown
 * player is a grey "?". [size]: `sm` 32 px, `md` 40 px, `lg` 64 px.
 */
fun FlowContent.playerAvatar(player: Player?, size: String = "md") {
    val colour = player?.let { PlayerPalette.cssClass(it.color) } ?: PlayerPalette.UNKNOWN
    val crest = player?.avatarId?.takeIf { it in 0 until PlayerPalette.CRESTS }
    span("avatar avatar-$size $colour") {
        attributes["aria-hidden"] = "true"
        if (crest != null) {
            span("crest crest-%02d".format(crest))
        } else {
            span("avatar-initial") { +(player?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?") }
        }
    }
}

/** A player's name in their colour (theme tone), as in the app's lists. */
fun FlowContent.playerName(player: Player?) {
    span("player-name ${player?.let { PlayerPalette.cssClass(it.color) } ?: PlayerPalette.UNKNOWN}") {
        +(player?.name ?: "Unknown player")
    }
}

/** `TeamBadge`: never colour-only (◆ Red / ● Blue). */
fun FlowContent.teamBadge(team: Team, showLabel: Boolean = true) {
    val (css, mark, label) = if (team == Team.RED) Triple("team-red", "◆", "Red") else Triple("team-blue", "●", "Blue")
    span("team-badge $css") {
        span("team-mark") {
            attributes["aria-hidden"] = "true"
            +mark
        }
        if (showLabel) span { +label } else attributes["aria-label"] = label
    }
}

/** `EmptyState`: a centred title, message and optional action link. */
fun FlowContent.emptyState(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    actionHref: String? = null,
) {
    section("empty-state") {
        span("empty-mark") { attributes["aria-hidden"] = "true" }
        h2 { +title }
        message?.let { p { +it } }
        if (actionLabel != null && actionHref != null) linkButton(actionLabel, actionHref, ButtonStyle.PRIMARY)
    }
}

/** An error message (role=alert), with an optional retry link. */
fun FlowContent.errorState(message: String, retryHref: String? = null) {
    div("error-state") {
        attributes["role"] = "alert"
        icon(Icons.ERROR)
        p { +message }
        retryHref?.let { linkButton("Try again", it, ButtonStyle.TEXT) }
    }
}

/** An inline notice (info). */
fun FlowContent.notice(message: String) {
    div("notice") {
        attributes["role"] = "status"
        icon(Icons.INFO)
        p { +message }
    }
}

/** A labelled text field; [error] shows under it and marks the field invalid. */
@Suppress("LongParameterList")
fun FlowContent.textField(
    name: String,
    label: String,
    value: String? = null,
    type: InputType = InputType.text,
    required: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    block: kotlinx.html.INPUT.() -> Unit = {},
) {
    val fieldId = "f-$name"
    div(if (error != null) "field invalid" else "field") {
        label {
            htmlFor = fieldId
            +label
        }
        input(type = type, name = name) {
            id = fieldId
            value?.let { this.value = it }
            placeholder?.let { this.placeholder = it }
            this.required = required
            if (error != null) {
                attributes["aria-invalid"] = "true"
                attributes["aria-describedby"] = "$fieldId-error"
            }
            block()
        }
        error?.let {
            p("field-error") {
                id = "$fieldId-error"
                +it
            }
        }
    }
}

/** A labelled multi-line field. */
fun FlowContent.textAreaField(
    name: String,
    label: String,
    value: String? = null,
    rows: Int = 3,
    block: kotlinx.html.TEXTAREA.() -> Unit = {},
) {
    val fieldId = "f-$name"
    div("field") {
        label {
            htmlFor = fieldId
            +label
        }
        textArea(rows = rows.toString()) {
            id = fieldId
            this.name = name
            block()
            value?.let { +it }
        }
    }
}

/** A labelled `<select>`; [options] are value to label. */
fun FlowContent.selectField(
    name: String,
    label: String,
    options: List<Pair<String, String>>,
    selected: String? = null,
) {
    val fieldId = "f-$name"
    div("field") {
        label {
            htmlFor = fieldId
            +label
        }
        select {
            id = fieldId
            this.name = name
            options.forEach { (value, text) ->
                option {
                    this.value = value
                    this.selected = value == selected
                    +text
                }
            }
        }
    }
}

/** A checkbox (or switch, [switch] = true) with its label. */
fun FlowContent.checkboxField(
    name: String,
    label: String,
    checked: Boolean,
    switch: Boolean = false,
    value: String = "true",
) {
    label(if (switch) "check switch" else "check") {
        input(type = InputType.checkBox, name = name) {
            this.value = value
            this.checked = checked
            if (switch) attributes["role"] = "switch"
        }
        span { +label }
    }
}

/** A POST form (same-origin by construction: the hub checks `Origin` on every POST). */
fun FlowContent.postForm(action: String, classes: String? = null, block: kotlinx.html.FORM.() -> Unit) {
    form(action = action, method = FormMethod.post, classes = classes) { block() }
}

/**
 * A modal `<dialog>` with a title and a close button. Open it from any element with
 * `data-dialog-open="<id>"` (handled by `static/hub.js`; no inline script). Put the actions in [block].
 */
fun FlowContent.hubDialog(id: String, title: String, block: HtmlBlockTag.() -> Unit) {
    dialog("dialog") {
        this.id = id
        div("dialog-head") {
            h2 { +title }
            button(type = ButtonType.button, classes = "icon-button") {
                attributes["data-dialog-close"] = ""
                attributes["aria-label"] = "Close"
                icon(Icons.CLOSE)
            }
        }
        div("dialog-body") { block() }
    }
}

/** A button that opens the [hubDialog] with [dialogId]. */
fun FlowContent.dialogButton(
    label: String,
    dialogId: String,
    style: ButtonStyle = ButtonStyle.TONAL,
    icon: String? = null,
) {
    hubButton(label, style, ButtonType.button, icon) { attributes["data-dialog-open"] = dialogId }
}
