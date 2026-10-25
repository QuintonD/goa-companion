package dev.quinton.goa.web.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.spoken
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement

/*
 * Shared pieces of the table tools (W3: draft and timer), styled by goa.css's "table" section
 * (`goa-table-…`). The Android counterparts live in core:designsystem and the two feature modules.
 */

/** "◆ Red" / "● Blue": team identity is never colour-only (docs/DESIGN.md). */
@Composable
fun TeamBadge(team: Team, showLabel: Boolean = true, extraClass: String? = null) {
    Span({
        classes("goa-table-team", teamClass(team))
        extraClass?.let { classes(it) }
        if (!showLabel) attr("aria-label", teamName(team))
    }) {
        Span({ attr("aria-hidden", "true") }) { Text(teamGlyph(team)) }
        if (showLabel) Text(" ${teamName(team)}")
    }
}

/** The CSS class that colours text and borders for [team]. */
fun teamClass(team: Team): String = if (team == Team.RED) "red" else "blue"

/**
 * A player's crest in their palette colour (the colour's dark tone in the dark theme, its light tone
 * in the light one; goa.css picks), or their initial without a crest, as Android's `PlayerAvatar`.
 * Decorative: the name is always next to it.
 */
@Composable
fun PlayerAvatar(player: Player?, size: Int = AVATAR) {
    val swatch = player?.let { PlayerPalette.find(it.color) }
    Span({
        classes("goa-table-avatar")
        attr("aria-hidden", "true")
        style {
            property("width", "${size}px")
            property("height", "${size}px")
            property("font-size", "${size * AVATAR_TEXT}px")
            if (player != null) {
                property("--pc-dark", argbCss(swatch?.argb ?: player.color))
                property("--pc-light", argbCss(swatch?.lightArgb ?: player.color))
            }
        }
    }) {
        val crest = player?.avatarId?.takeIf { it in 0 until PlayerPalette.CREST_COUNT }
        if (crest != null) {
            Span({
                classes("goa-table-avatar-crest")
                style {
                    property("width", "${size * AVATAR_CREST}px")
                    property("height", "${size * AVATAR_CREST}px")
                }
            }) { Icon("crest_" + crest.toString().padStart(2, '0')) }
        } else {
            Text(player?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?")
        }
    }
}

/** `#RRGGBB` for a stored ARGB colour. */
fun argbCss(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

/**
 * Official hero art in our frame (`official/heroes/<id>/portrait.webp`), lazy-loaded; while it
 * loads, without art or if it fails, the hero's initial on the medallion — never a broken image.
 */
@Composable
fun HeroArt(heroId: String, portrait: String?, name: String, extraClass: String? = null, round: Boolean = false) {
    var failed by remember(portrait) { mutableStateOf(false) }
    Span({
        classes("goa-table-art")
        if (round) classes("round")
        extraClass?.let { classes(it) }
        attr("aria-hidden", "true")
        attr("data-hero", heroId)
    }) {
        Span({ classes("goa-table-art-fallback") }) { Text(name.take(1).uppercase()) }
        if (portrait != null && !failed) {
            Img(portrait, "") {
                attr("loading", "lazy")
                attr("decoding", "async")
                attr("draggable", "false")
                addEventListener("error") { failed = true }
            }
        }
    }
}

/** − value + stepper with 44 px targets; announces the value as "4 heroes". */
@Composable
fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    label: String,
    step: Int = 1,
    valueText: String? = null,
) {
    Div({
        classes("goa-table-stepper")
        attr("role", "group")
        attr("aria-label", label)
    }) {
        StepButton("remove", "Fewer $label", enabled = value > range.first) {
            onChange((value - step).coerceAtLeast(range.first))
        }
        Span({
            classes("goa-table-stepper-value", "t-numeric")
            attr("aria-live", "polite")
            attr("aria-label", "$value $label")
        }) { Text(valueText ?: value.toString()) }
        StepButton("add", "More $label", enabled = value < range.last) {
            onChange((value + step).coerceAtMost(range.last))
        }
    }
}

@Composable
fun StepButton(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-table-round-button")
        attr("aria-label", label)
        title(label)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { Icon(icon) }
}

/** "◆ Red shows" / "● Blue shows": a toggle chip in the team's colour (never colour alone: the glyph says it). */
@Composable
fun TeamChip(team: Team, label: String, selected: Boolean, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-chip", "goa-table-team-chip", teamClass(team))
        attr("aria-pressed", selected.toString())
        onClick { onClick() }
    }) {
        if (selected) Icon("check", IconSize.SMALL)
        Span({ attr("aria-hidden", "true") }) { Text(teamGlyph(team)) }
        Text(" $label")
    }
}

/** A row with a title, explanation and an on/off switch (`role="switch"`); the whole row toggles. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-table-switch-row")
        attr("role", "switch")
        attr("aria-checked", checked.toString())
        onClick { onChange(!checked) }
    }) {
        Span({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text(title) }
            subtitle?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        Span({
            classes("goa-table-switch")
            attr("aria-hidden", "true")
        })
    }
}

/**
 * A selectable card (one of a group: `role="radio"` inside a [RadioGroup]): engraved border, teal
 * when selected, a radio mark. [enabled] false keeps the card readable (the reason stays legible).
 */
@Composable
fun RadioCard(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-table-radio-card")
        attr("role", "radio")
        attr("aria-checked", selected.toString())
        if (!enabled) {
            attr("aria-disabled", "true")
            classes("disabled")
        }
        onClick { if (enabled) onClick() }
    }) {
        Span({
            classes("goa-table-radio")
            attr("aria-hidden", "true")
        })
        Span({ classes("goa-table-radio-body") }) { content() }
    }
}

@Composable
fun RadioGroup(label: String, content: ContentBuilder<HTMLDivElement>) {
    Div({
        classes("goa-table-radio-group")
        attr("role", "radiogroup")
        attr("aria-label", label)
    }, content)
}

/** A card with a titled header, an optional subtitle and [trailing] control (the phone's SectionCard). */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleClass: String? = null,
    content: ContentBuilder<HTMLDivElement>,
) {
    Div({ classes("goa-card", "goa-table-section-card") }) {
        Div({ classes("goa-table-section-head") }) {
            Div({ classes("goa-table-row-text") }) {
                H3({
                    classes("t-title-medium", "goa-table-section-title")
                    titleClass?.let { classes(it) }
                }) { Text(title) }
                subtitle?.let { P({ classes("t-body-small", "c-variant") }) { Text(it) } }
            }
            trailing?.invoke()
        }
        content()
    }
}

/** A tinted explanation card (teal), e.g. "Official 3-player setup". */
@Composable
fun InfoCard(title: String, body: String) {
    Div({ classes("goa-table-info-card") }) {
        P({ classes("t-title-small", "goa-table-info-title") }) { Text(title) }
        P({ classes("t-body-medium") }) { Text(body) }
    }
}

/** A short note with an info mark. Nothing for blank text. */
@Composable
fun Note(text: String) {
    if (text.isBlank()) return
    Div({ classes("goa-table-note", "c-variant") }) {
        Icon("info", IconSize.SMALL)
        P({ classes("t-body-small") }) { Text(text) }
    }
}

enum class BadgeKind { PRIMARY, SECONDARY, ERROR }

@Composable
fun Badge(text: String, kind: BadgeKind = BadgeKind.SECONDARY) {
    Span({ classes("goa-table-badge", kind.name.lowercase(), "t-label-medium") }) { Text(text) }
}

/** Upper-case small heading inside a screen (the phone's timer SectionHeader). */
@Composable
fun SmallHeading(text: String) {
    H2({ classes("goa-table-small-heading", "t-overline") }) { Text(text) }
}

/**
 * Compact sequence preview: "Ban ◆ · Pick ● · …" chips. Bans are outlined, picks filled; the
 * [current] step is highlighted and finished steps fade. [wrap] false scrolls sideways (the board).
 */
@Composable
fun SequenceStrip(tokens: List<StepToken>, current: Int? = null, wrap: Boolean = true) {
    if (tokens.isEmpty()) return
    Div({
        classes("goa-table-sequence")
        if (!wrap) classes("scroll")
        attr("role", "img")
        attr("aria-label", tokens.spoken())
    }) {
        tokens.forEachIndexed { i, token ->
            Span({
                classes("goa-table-step-chip", if (token.action.name == "BAN") "ban" else "pick")
                token.team?.let { classes(teamClass(it)) }
                when {
                    current == i -> classes("current")
                    current != null && i < current -> classes("done")
                }
                attr("aria-hidden", "true")
            }) { Text((if (token.hidden) "🔒 " else "") + token.label) }
        }
    }
}

/** A labelled text field. [error] shows under it in the error colour. */
@Composable
fun TextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    capitalize: Boolean = false,
    maxLength: Int? = null,
    onEnter: (() -> Unit)? = null,
) {
    Label(attrs = { classes("goa-table-field") }) {
        Span({ classes("t-label-medium", "c-variant") }) { Text(label) }
        Input(InputType.Text) {
            classes("goa-table-input")
            if (error != null) classes("error")
            value(value)
            placeholder?.let { placeholder(it) }
            maxLength?.let { attr("maxlength", it.toString()) }
            attr("autocomplete", "off")
            attr("autocapitalize", if (capitalize) "words" else "sentences")
            attr("enterkeyhint", if (onEnter != null) "done" else "next")
            onInput { onValue(it.value) }
            if (onEnter != null) {
                onKeyDown { if (it.key == "Enter") onEnter() }
            }
        }
        when {
            error != null -> Span({
                classes("t-body-small", "c-error")
                attr("role", "alert")
            }) { Text(error) }

            supporting != null -> Span({ classes("t-body-small", "c-variant") }) { Text(supporting) }
        }
    }
}

/**
 * The overflow menu of a screen, as an iPhone action sheet: [items] (label, icon, enabled, action),
 * shown in a bottom sheet with one row each.
 */
data class MenuItem(val label: String, val icon: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun MenuSheet(title: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    dev.quinton.goa.web.ui.BottomSheet(title, onDismiss) {
        Div({ classes("goa-table-menu") }) {
            items.forEach { item ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-table-menu-item")
                    if (!item.enabled) disabled()
                    onClick {
                        onDismiss()
                        item.onClick()
                    }
                }) {
                    Icon(item.icon)
                    Span({ classes("t-body-large") }) { Text(item.label) }
                }
            }
        }
    }
}

/**
 * A screen's pinned footer (Next, Start timer, Set up match): sticks above the tab bar. Its height is
 * published as `--table-footer` so messages (snackbars) show above it instead of covering its buttons.
 */
@Composable
fun TableFooter(extraClass: String? = null, content: ContentBuilder<HTMLDivElement>) {
    Div({
        classes("goa-table-footer")
        extraClass?.let { classes(it) }
        ref { element ->
            val style = kotlinx.browser.document.documentElement?.asDynamic()?.style
            val publish = { style?.setProperty("--table-footer", "${element.offsetHeight}px") }
            val observer = ResizeObserver { _, _ -> publish() }
            observer.observe(element)
            publish()
            onDispose {
                observer.disconnect()
                style?.removeProperty("--table-footer")
            }
        }
    }, content)
}

/** A row of buttons at the foot of a dialog: [confirm] filled (or danger), [dismiss] text. */
@Composable
fun DialogButtons(
    dismiss: String,
    onDismiss: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    danger: Boolean = false,
) {
    GoaButton(dismiss, onDismiss, ButtonKind.TEXT)
    GoaButton(confirm, onConfirm, if (danger) ButtonKind.DANGER else ButtonKind.FILLED)
}

/** The browser's ResizeObserver (Safari 13.1+). */
private external class ResizeObserver(callback: (dynamic, dynamic) -> Unit) {
    fun observe(target: org.w3c.dom.Element)

    fun disconnect()
}

private const val AVATAR = 40
private const val AVATAR_TEXT = 0.42
private const val AVATAR_CREST = 0.56
