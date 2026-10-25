package dev.quinton.goa.web.draft

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.catalog.StatRange
import dev.quinton.goa.feature.draft.ui.CardInfo
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.cardKind
import dev.quinton.goa.feature.draft.ui.description
import dev.quinton.goa.feature.draft.ui.label
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.web.table.HeroArt
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/*
 * The draft's pieces on the web (the phone's DraftComponents and HeroPeek): hero tiles with their
 * draft state, the hero peek, roles, complexity and stat bars.
 */

/**
 * A hero tile: official art in our frame, name, and its draft state (struck/dimmed when banned).
 * [onPeek] (touch and hold, or the context menu) opens the hero peek; a tile without [onClick] opens
 * it on tap too.
 */
@Composable
fun HeroTile(
    hero: HeroInfo,
    state: TileState,
    onClick: (() -> Unit)?,
    onPeek: (() -> Unit)? = null,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    Button({
        type(ButtonType.Button)
        classes("goa-draft-tile")
        if (compact) classes("compact")
        if (selected) classes("selected")
        when (state) {
            TileState.Available -> Unit
            TileState.Inactive -> classes("inactive")
            is TileState.Banned -> classes("banned", teamClass(state.team))
            is TileState.Picked -> classes("picked", teamClass(state.team))
        }
        attr("aria-label", "${hero.name}, ${state.description()}")
        if (onClick == null && onPeek == null) attr("aria-disabled", "true")
        longPress(onPeek)
        onClick { (onClick ?: onPeek)?.invoke() }
    }) {
        Span({ classes("goa-draft-tile-art") }) {
            HeroArt(hero.id, hero.portrait, hero.name)
            when (state) {
                is TileState.Banned -> Span({ classes("goa-draft-tile-state", teamClass(state.team)) }) {
                    Text("${teamGlyph(state.team)} Banned")
                }

                is TileState.Picked -> Span({ classes("goa-draft-tile-state", teamClass(state.team)) }) {
                    Text("${teamGlyph(state.team)} ${teamName(state.team)}")
                }

                else -> Unit
            }
        }
        Span({ classes("goa-draft-tile-name", if (compact) "t-label-medium" else "t-label-large") }) { Text(hero.name) }
    }
}

/**
 * Touch and hold (500 ms, without moving) or the context menu (right click / long press with a mouse)
 * calls [onPeek]; the tap that follows a hold is swallowed. iOS's own callout on images is off in CSS.
 */
private fun AttrsScope<HTMLButtonElement>.longPress(onPeek: (() -> Unit)?) {
    if (onPeek == null) return
    ref { element ->
        val press = PressAndHold(element) { onPeek() }
        onDispose { press.detach() }
    }
}

private class PressAndHold(private val element: HTMLElement, private val onHold: () -> Unit) {
    private var timer: Int? = null
    private var startX = 0.0
    private var startY = 0.0
    private var held = false

    private val down: (Event) -> Unit = { e ->
        val d = e.asDynamic()
        startX = (d.clientX as Number).toDouble()
        startY = (d.clientY as Number).toDouble()
        held = false
        cancel()
        timer = window.setTimeout({
            held = true
            onHold()
        }, HOLD_MS)
    }
    private val move: (Event) -> Unit = { e ->
        val d = e.asDynamic()
        val dx = (d.clientX as Number).toDouble() - startX
        val dy = (d.clientY as Number).toDouble() - startY
        if (dx * dx + dy * dy > SLOP_PX * SLOP_PX) cancel()
    }
    private val up: (Event) -> Unit = { cancel() }
    private val click: (Event) -> Unit = { e ->
        if (held) {
            held = false
            e.preventDefault()
            e.stopImmediatePropagation()
        }
    }
    private val context: (Event) -> Unit = { e ->
        e.preventDefault()
        cancel()
        if (!held) onHold()
        held = false
    }

    init {
        element.addEventListener("pointerdown", down)
        element.addEventListener("pointermove", move)
        element.addEventListener("pointerup", up)
        element.addEventListener("pointercancel", up)
        element.addEventListener("pointerleave", up)
        element.addEventListener("click", click, js("({ capture: true })"))
        element.addEventListener("contextmenu", context)
    }

    private fun cancel() {
        timer?.let { window.clearTimeout(it) }
        timer = null
    }

    fun detach() {
        cancel()
        element.removeEventListener("pointerdown", down)
        element.removeEventListener("pointermove", move)
        element.removeEventListener("pointerup", up)
        element.removeEventListener("pointercancel", up)
        element.removeEventListener("pointerleave", up)
        element.removeEventListener("click", click, js("({ capture: true })"))
        element.removeEventListener("contextmenu", context)
    }

    private companion object {
        const val HOLD_MS = 500
        const val SLOP_PX = 10.0
    }
}

/** A hero's roles as small outlined labels; [teamClass] tints them (a team's covered roles). */
@Composable
fun RoleChips(roles: List<Role>, tint: String? = null) {
    if (roles.isEmpty()) return
    Div({ classes("goa-draft-roles") }) {
        roles.forEach { role ->
            Span({
                classes("goa-draft-role", "t-label-medium")
                tint?.let { classes("tinted", it) }
            }) { Text(role.label) }
        }
    }
}

/** ★★☆☆ and the official complexity name (Hero Profile card, p.21). Nothing when unknown. */
@Composable
fun ComplexityStars(complexity: Int?, showLabel: Boolean = true) {
    if (complexity == null) return
    val name = ComplexityFilter.name(complexity)
    Span({
        classes("goa-draft-stars")
        attr("role", "img")
        attr("aria-label", "Complexity $complexity of $MAX_STARS" + (name?.let { ", $it" } ?: ""))
    }) {
        Span({
            classes("goa-draft-star-glyphs")
            attr("aria-hidden", "true")
        }) {
            Text("★".repeat(complexity.coerceIn(0, MAX_STARS)) + "☆".repeat((MAX_STARS - complexity).coerceAtLeast(0)))
        }
        if (showLabel && name != null) {
            Span({
                classes("t-label-medium", "c-variant")
                attr("aria-hidden", "true")
            }) { Text(name) }
        }
    }
}

/** A profile bar: base filled, up to max lighter, on the 1..8 scale of the hero card. */
@Composable
fun StatBar(label: String, range: StatRange?) {
    val base = range?.base
    val max = range?.max
    val description = when {
        base == null -> "$label unknown"
        max == null || max == base -> "$label $base of $STAT_SCALE"
        else -> "$label $base, up to $max, of $STAT_SCALE"
    }
    Div({
        classes("goa-draft-stat")
        attr("role", "img")
        attr("aria-label", description)
    }) {
        Span({
            classes("t-label-large", "c-variant")
            attr("aria-hidden", "true")
        }) { Text(label) }
        Span({
            classes("goa-draft-stat-bar")
            attr("aria-hidden", "true")
        }) {
            for (i in 1..STAT_SCALE) {
                Span({
                    classes(
                        when {
                            base != null && i <= base -> "base"
                            max != null && i <= max -> "max"
                            else -> "empty"
                        },
                    )
                })
            }
        }
        Span({
            classes("t-numeric")
            attr("aria-hidden", "true")
        }) {
            Text(
                when {
                    base == null -> "—"
                    max == null || max == base -> "$base"
                    else -> "$base → $max"
                },
            )
        }
    }
}

/**
 * Hero peek during a draft (touch and hold a tile): complexity, roles, stat bars (base → max), the
 * five starting cards and a link to the full hero page. Catalog data only, never draft state, so it
 * can't reveal a hidden choice.
 */
@Composable
fun HeroPeekSheet(hero: HeroInfo, onDismiss: () -> Unit, onOpenHero: ((String) -> Unit)?) {
    BottomSheet(hero.name, onDismiss) {
        Div({ classes("goa-table-stack") }) {
            Div({ classes("goa-draft-peek-head") }) {
                HeroArt(hero.id, hero.portrait, hero.name, extraClass = "goa-draft-peek-art")
                Div({ classes("goa-table-row-text") }) {
                    Div({ classes("goa-table-inline") }) {
                        H2({ classes("t-title-large", "goa-table-tight") }) { Text(hero.name) }
                        hero.symbol?.let { symbol ->
                            Img(symbol, "") {
                                classes("goa-draft-symbol")
                                attr("aria-hidden", "true")
                                attr("loading", "lazy")
                            }
                        }
                    }
                    if (hero.title.isNotEmpty()) {
                        P({
                            classes("t-body-medium", "c-variant", "goa-table-tight")
                        }) { Text(hero.title) }
                    }
                    ComplexityStars(hero.complexity)
                    RoleChips(hero.roles)
                }
            }
            if (!hero.known) {
                P({ classes("t-body-medium", "c-variant") }) { Text("This hero isn't in the catalog.") }
            } else {
                P({ classes("t-title-small", "c-primary") }) { Text("Stats · base → max") }
                PEEK_STATS.forEach { (label, stat) -> StatBar(label, hero.stats.get(stat)) }
                P({ classes("t-title-small", "c-primary") }) { Text("Starting cards") }
                if (hero.startingCards.isEmpty()) {
                    P({ classes("t-body-medium", "c-variant") }) { Text("Not in the catalog yet.") }
                }
                Div({ classes("goa-draft-cards") }) { hero.startingCards.forEach { StartingCard(it) } }
            }
            if (onOpenHero != null && hero.known) {
                GoaButton("Open hero page", {
                    onOpenHero(hero.id)
                }, ButtonKind.OUTLINED, icon = "external", block = true)
            }
        }
    }
}

/** A starting card as its official scan, named under it; without a scan a colour-edged name tile. */
@Composable
private fun StartingCard(card: CardInfo) {
    Div({ classes("goa-draft-card") }) {
        val image = card.image
        if (image != null) {
            Img(image, "${card.name}, ${cardKind(card)}") {
                classes("goa-draft-card-scan")
                attr("loading", "lazy")
                attr("decoding", "async")
            }
        } else {
            Span({
                classes("goa-draft-card-blank", card.color.name.lowercase())
                attr("role", "img")
                attr("aria-label", "${card.name}, ${cardKind(card)}")
            }) { Text(card.name) }
        }
        Span({
            classes("t-label-medium", "goa-table-center")
            attr("aria-hidden", "true")
        }) { Text(card.name) }
    }
}

private val PEEK_STATS = listOf(
    "Attack" to Stat.ATTACK,
    "Defense" to Stat.DEFENSE,
    "Initiative" to Stat.INITIATIVE,
    "Movement" to Stat.MOVEMENT,
)

private const val MAX_STARS = 4
private const val STAT_SCALE = 8
