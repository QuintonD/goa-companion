package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.DateLabels
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.label
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import kotlin.js.Date
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.web.attributes.InputMode
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoComplete
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

/**
 * Dates as this browser writes them (`Intl`, the person's language), for the shared builders
 * ([DateLabels], ADR-043): "Today", "Yesterday", "Sat 12 Oct 2026", "19:30".
 */
@OptIn(ExperimentalTime::class)
object WebDateLabels : DateLabels {
    private val zone get() = TimeZone.currentSystemDefault()

    override fun localDate(epochMillis: Long): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date

    override fun dayHeader(epochMillis: Long, today: LocalDate): String = when (localDate(epochMillis)) {
        today -> "Today"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> day(epochMillis)
    }

    override fun time(epochMillis: Long): String =
        Date(
            epochMillis.toDouble(),
        ).asDynamic().toLocaleTimeString(js("[]"), opts("hour", "2-digit", "minute", "2-digit")) as String

    /** "Sat 12 Oct 2026" (in the browser's language). */
    fun day(epochMillis: Long): String = Date(epochMillis.toDouble()).asDynamic()
        .toLocaleDateString(
            js("[]"),
            opts("weekday", "short", "day", "numeric", "month", "short", "year", "numeric"),
        ) as String

    /** "12 Oct" (the chart's axis). */
    fun short(epochMillis: Long): String =
        Date(
            epochMillis.toDouble(),
        ).asDynamic().toLocaleDateString(js("[]"), opts("day", "numeric", "month", "short")) as String

    /** "Sat 12 Oct" (the last game night). */
    fun night(date: LocalDate): String {
        val noon = Date(date.year, date.month.number - 1, date.day, 12)
        return noon.asDynamic().toLocaleDateString(
            js("[]"),
            opts("weekday", "short", "day", "numeric", "month", "short"),
        ) as String
    }

    fun today(): LocalDate = localDate(Date.now().toLong())

    private fun opts(vararg pairs: String): dynamic {
        val o: dynamic = js("({})")
        for (i in pairs.indices step 2) o[pairs[i]] = pairs[i + 1]
        return o
    }
}

/**
 * The player palette (core/designsystem `PlayerColors`, docs/DESIGN.md): the stored `Player.color`
 * is the dark-theme tone; each swatch has a light-theme tone readable on light surfaces.
 */
object PlayerPalette {
    class Swatch(val name: String, val dark: Long, val light: Long)

    val all: List<Swatch> = listOf(
        Swatch("Tide teal", 0xFF3FC1B0, 0xFF186E62),
        Swatch("Bronze", 0xFFD9B44A, 0xFF7A5E24),
        Swatch("Coral", 0xFFEE7F6F, 0xFFB23F2F),
        Swatch("Sky", 0xFF62A8F2, 0xFF2A62AE),
        Swatch("Amethyst", 0xFFB892E6, 0xFF6B45A0),
        Swatch("Kelp", 0xFF6CC27A, 0xFF2A7037),
        Swatch("Rose", 0xFFEE85B8, 0xFFA83A70),
        Swatch("Ember", 0xFFEE9A52, 0xFF9A4E0E),
        Swatch("Pearl", 0xFFC3CCD6, 0xFF56616C),
        Swatch("Lime", 0xFFAECF55, 0xFF55691A),
    )

    /** The 24 crests' heraldic names (`PlayerCrests.names`). */
    val crestNames: List<String> = listOf(
        "Double chevron", "Lozenge", "Three bezants", "Four-pointed star", "Key", "Crescent",
        "Sun", "Waves", "Mount", "Cross pattée", "Saltire", "Tower",
        "Crown", "Anchor", "Leaf", "Hourglass", "Thunderbolt", "Quatrefoil",
        "Three rings", "Arrowhead", "Hexagon", "Pall", "Chequy", "Frost star",
    )

    fun find(argb: Long): Swatch? = all.firstOrNull { it.dark == argb || it.light == argb }

    /** A new player's colour and crest, as the phone picks them: the first unused colour, crest by count. */
    fun suggestion(existing: List<Player>): Pair<Long, Int> {
        val used = existing.map { it.color }.toSet()
        val color = (all.firstOrNull { it.dark !in used } ?: all[existing.size.mod(all.size)]).dark
        return color to existing.size % crestNames.size
    }

    fun css(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}

/** Sets the player's colour tones for CSS (`--pc` picks the theme's one, see goa.css "group (W2)"). */
private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.playerColor(argb: Long?) {
    classes("goa-w2-pc")
    if (argb == null) return
    val swatch = PlayerPalette.find(argb)
    style {
        property("--pc-d", PlayerPalette.css(swatch?.dark ?: argb))
        property("--pc-l", PlayerPalette.css(swatch?.light ?: argb))
    }
}

/** The player's crest in their colour, in a ring (`PlayerAvatar`); a monogram without a crest. */
@Composable
fun PlayerAvatar(player: Player?, size: Int = 40) {
    Span({
        classes("goa-w2-avatar")
        playerColor(player?.color)
        attr("aria-hidden", "true")
        style {
            property("width", "${size}px")
            property("height", "${size}px")
            property("--crest", "${(size * CREST_SCALE).roundToInt()}px")
        }
    }) {
        val crest = player?.avatarId?.takeIf { it in PlayerPalette.crestNames.indices }
        if (crest != null) {
            Icon("crest_" + crest.toString().padStart(2, '0'))
        } else {
            Span({ classes("goa-w2-monogram") }) {
                Text(player?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?")
            }
        }
    }
}

private const val CREST_SCALE = 0.56

/** Team identity is never colour alone: ◆ Red / ● Blue (docs/DESIGN.md). */
@Composable
fun TeamBadge(team: Team, showLabel: Boolean = true) {
    Span({ classes("goa-w2-team", if (team == Team.RED) "c-team-red" else "c-team-blue", "t-label-large") }) {
        Text(if (team == Team.RED) "◆" else "●")
        if (showLabel) Text(" " + team.label())
    }
}

/** Match result: "◆ Red won", "● Blue won", "Pending", "Abandoned", "No winner". */
@Composable
fun ResultChip(status: MatchStatus, winner: Team?) {
    val (text, kind) = when {
        status == MatchStatus.COMPLETED && winner != null ->
            "${if (winner == Team.RED) "◆" else "●"} ${winner.label()} won" to
                (if (winner == Team.RED) "red" else "blue")

        status == MatchStatus.ABANDONED -> "Abandoned" to "muted"

        status == MatchStatus.COMPLETED -> "No winner" to "error"

        else -> "Pending" to "pending"
    }
    Span({ classes("goa-w2-result", kind, "t-label-medium") }) { Text(text) }
}

/** Rating change with an arrow and a signed number ("↗ +24"), then what it covers ("last 5"). */
@Composable
fun DeltaIndicator(delta: Double?, caption: String? = null, label: String = "rating change") {
    if (delta == null) return
    val rounded = delta.roundToInt()
    val kind = when {
        rounded > 0 -> "up"
        rounded < 0 -> "down"
        else -> "flat"
    }
    Span({
        classes("goa-w2-delta", kind, "t-label-medium")
        attr("aria-label", "$label ${formatDelta(delta)}" + (caption?.let { ", $it" } ?: ""))
    }) {
        Span({ attr("aria-hidden", "true") }) {
            Text(
                when (kind) {
                    "up" -> "↗ "
                    "down" -> "↘ "
                    else -> "→ "
                },
            )
            Text(formatDelta(delta))
            caption?.let { Span({ classes("c-variant") }) { Text(" $it") } }
        }
    }
}

/** A small outlined pill: "Provisional", "Double pilot", "H", "×2" — text, never colour alone. */
@Composable
fun Pill(text: String, kind: String? = null) {
    Span({
        classes("goa-w2-pill", "t-label-medium")
        kind?.let { classes(it) }
    }) { Text(text) }
}

/** Tiles of one big number and its caption ("12 / Games"). */
@Composable
fun StatRow(tiles: List<Pair<String, String>>) {
    Div({ classes("goa-w2-stats") }) {
        tiles.forEach { (value, caption) ->
            Div({ classes("goa-w2-stat") }) {
                Span({ classes("goa-w2-stat-value", "t-title-large") }) { Text(value) }
                Span({ classes("goa-w2-stat-caption", "t-label-medium", "c-variant") }) { Text(caption) }
            }
        }
    }
}

/** The hero's portrait (catalog image), or its initial when there is none ("?" for an unknown hero). */
@Composable
fun HeroThumb(name: String?, portrait: String?, size: Int = 32) {
    Span({
        classes("goa-w2-hero")
        attr("aria-hidden", "true")
        style {
            property("width", "${size}px")
            property("height", "${size}px")
        }
    }) {
        if (portrait != null) {
            Img(portrait, alt = "") {
                attr("loading", "lazy")
                attr("decoding", "async")
                attr("width", size.toString())
                attr("height", size.toString())
            }
        } else {
            Text(name?.take(1)?.uppercase() ?: "?")
        }
    }
}

/**
 * A labelled text field (Material's outlined field): label above, the field, then [error] or
 * [supporting] under it. [inputMode]/[type] give iOS the right keyboard; never auto-capitalised
 * or corrected unless [words].
 */
@Composable
fun TextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    error: String? = null,
    supporting: String? = null,
    placeholder: String? = null,
    maxLength: Int? = null,
    inputMode: InputMode? = null,
    type: InputType<String> = InputType.Text,
    words: Boolean = false,
    id: String,
    onEnter: (() -> Unit)? = null,
) {
    Div({
        classes("goa-w2-field")
        if (error != null) classes("error")
    }) {
        Label(forId = id, attrs = { classes("t-label-large") }) { Text(label) }
        Input(type) {
            id(id)
            value(value)
            onInput { onValue(it.value) }
            placeholder?.let { placeholder(it) }
            maxLength?.let { maxLength(it) }
            inputMode?.let { inputMode(it) }
            autoComplete(org.jetbrains.compose.web.attributes.AutoComplete.off)
            attr("autocapitalize", if (words) "words" else "none")
            attr("autocorrect", "off")
            attr("spellcheck", "false")
            if (error != null) {
                attr("aria-invalid", "true")
                attr("aria-describedby", "$id-help")
            } else if (supporting != null) {
                attr("aria-describedby", "$id-help")
            }
            if (onEnter != null) {
                onKeyDown { if (it.key == "Enter") onEnter() }
            }
        }
        (error ?: supporting)?.let {
            Span({
                id("$id-help")
                classes("goa-w2-help", "t-body-small")
            }) { Text(it) }
        }
    }
}

/** A multi-line text field (notes). */
@Composable
fun TextAreaField(label: String, value: String, onValue: (String) -> Unit, id: String, maxLength: Int? = null) {
    Div({ classes("goa-w2-field") }) {
        Label(forId = id, attrs = { classes("t-label-large") }) { Text(label) }
        TextArea(value) {
            id(id)
            onInput { onValue(it.value) }
            maxLength?.let { maxLength(it) }
            attr("rows", "3")
            attr("autocapitalize", "sentences")
        }
    }
}

/** An on/off switch row ("Sync through the server"): a real checkbox with the switch role. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, id: String) {
    Label(forId = id, attrs = { classes("goa-w2-switch-row") }) {
        Span({ classes("t-body-large") }) { Text(label) }
        Input(InputType.Checkbox) {
            id(id)
            checked(checked)
            attr("role", "switch")
            classes("goa-w2-switch")
            onChange { onChange(it.value) }
        }
    }
}

/** A small round icon in a list row's leading slot. */
@Composable
fun LeadingIcon(name: String) {
    Icon(name, IconSize.NORMAL)
}

/**
 * One of a few choices as chips that wrap (a radio group): the phone's segmented row, which there
 * turns into chips with large text; here always chips, so "Abandoned" never runs off the screen.
 */
@Composable
fun <T> ChoiceChips(label: String, options: List<T>, selected: T, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    Div({
        classes("goa-chip-row", "wrap")
        attr("role", "radiogroup")
        attr("aria-label", label)
    }) {
        options.forEach { option ->
            val checked = option == selected
            org.jetbrains.compose.web.dom.Button({
                attr("type", "button")
                classes("goa-chip")
                attr("role", "radio")
                attr("aria-checked", checked.toString())
                onClick { onSelect(option) }
            }) {
                if (checked) Icon("check", IconSize.SMALL)
                Text(optionLabel(option))
            }
        }
    }
}
