package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.domain.board.BoardRules.MovePath
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.OverlaySettings
import dev.quinton.goa.feature.board.board.OverlayUi
import dev.quinton.goa.feature.board.board.cardKindLabel
import dev.quinton.goa.feature.board.board.pieceSubtitle
import dev.quinton.goa.feature.board.board.pieceTitle
import dev.quinton.goa.feature.board.board.tierLabel
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ParchmentPanel
import dev.quinton.goa.web.ui.Segmented
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The selected piece (the phone's SelectionPanel): who it is and where, Measure and Remove, and for a
 * hero a card to measure with and its overlays, each switch drawn with the pattern it puts on the map;
 * a minion or token a Movement N stepper. The panel hides to its peek bar (PlayScaffold).
 */
@Composable
internal fun SelectionPanel(
    state: BoardUiState.Ready,
    piece: BoardPiece,
    onAction: (BoardAction) -> Unit,
    onReadCards: (heroId: String, cardId: String) -> Unit,
) {
    val hero = (piece as? HeroPiece)?.let { state.heroes.hero(it.heroId) }
    Div({ classes("goa-board-panel") }) {
        Div({ classes("goa-board-panel-head") }) {
            PieceAvatar(piece, state.heroes)
            Div({ classes("goa-board-panel-title") }) {
                H2({ classes("t-title-medium") }) {
                    Text(pieceTitle(state, piece))
                    if (hero != null && !hero.verified) Span({ classes("goa-board-unverified") }) { Text("Unverified") }
                }
                P({ classes("t-body-small", "goa-board-muted") }) { Text(pieceSubtitle(state, piece)) }
            }
            IconButton("close", "Deselect", onClick = { onAction(BoardAction.Deselect) })
        }
        if (piece.at != null) {
            ChipRow(wrap = true) {
                val measuring = state.tool == BoardTool.Measure
                Chip(if (measuring) "Measuring" else "Measure", measuring, {
                    onAction(if (measuring) BoardAction.CancelTool else BoardAction.StartMeasure)
                }, icon = "ruler")
                Chip("Remove", false, { onAction(BoardAction.Remove) }, icon = "delete")
            }
        } else {
            ChipRow(wrap = true) { Chip("Remove", false, { onAction(BoardAction.Remove) }, icon = "delete") }
        }
        when {
            piece.at == null -> Note("Waiting in the tray: tap a highlighted space on the board to put it there (p.8).")
            hero != null -> HeroMeasures(state, hero, onAction, onReadCards)
            else -> StepsMeasure(state, piece, onAction)
        }
    }
}

@Composable
private fun HeroMeasures(
    state: BoardUiState.Ready,
    hero: BoardHero,
    onAction: (BoardAction) -> Unit,
    onReadCards: (heroId: String, cardId: String) -> Unit,
) {
    val overlay = state.overlay ?: OverlayUi(OverlaySettings())
    val card = overlay.card
    Overline("Card")
    CardStrip(hero.measurable, card?.id) { onAction(BoardAction.ChooseCard(it)) }
    val first = card?.id ?: hero.measurable.firstOrNull()?.id
    if (first != null) {
        Button({
            classes("goa-button", "text", "goa-board-read")
            attr("type", "button")
            onClick { onReadCards(hero.id, first) }
        }) {
            Icon("cards", IconSize.SMALL)
            Text("Read the cards")
        }
    }
    if (card ==
        null
    ) {
        Note("Pick a card to see where it reaches: its Movement, Range and Radius.")
    } else {
        CardSummary(card)
    }
    Overline("On the board")
    val settings = overlay.settings
    OverlayRow(
        title = "Movement" + (overlay.movementValue?.let { " $it" } ?: ""),
        note = when {
            card == null -> "Pick a card first."
            card.values.movement == null -> "This card has no Movement value."
            else -> "Spaces it can end on, with the fewest spaces each needs (p.11)."
        },
        checked = settings.movement && overlay.movementValue != null,
        enabled = overlay.movementValue != null,
        swatch = "movement",
        bonus = settings.movementBonus.takeIf { card?.values?.movement != null },
        onBonus = { onAction(BoardAction.MovementBonus(it)) },
        onToggle = { onAction(BoardAction.ToggleMovement) },
    )
    if (settings.movement && overlay.movementValue != null) MovementRules(settings, onAction)
    OverlayRow(
        title = "Fast Travel",
        note = if (settings.fastTravel && overlay.fastTravel.isEmpty()) {
            "Not now: an enemy hero or minion is in this Zone, or no space is free (p.11)."
        } else {
            "Own or an adjacent Zone, with no enemy hero or minion in either (p.11)."
        },
        checked = settings.fastTravel,
        enabled = true,
        swatch = "fast",
        onToggle = { onAction(BoardAction.ToggleFastTravel) },
    )
    OverlayRow(
        title = "Range" + (overlay.rangeValue?.let { " $it" } ?: ""),
        note = if (card != null && card.values.range == null) {
            "This card has no Range value."
        } else {
            "Spaces within range, obstacles and the water around the board counted (p.9, p.19)."
        },
        checked = settings.range && overlay.rangeValue != null,
        enabled = overlay.rangeValue != null,
        swatch = "range",
        bonus = settings.rangeBonus.takeIf { card?.values?.range != null },
        onBonus = { onAction(BoardAction.RangeBonus(it)) },
        onToggle = { onAction(BoardAction.ToggleRange) },
    )
    OverlayRow(
        title = "Radius" + (overlay.radiusValue?.let { " $it" } ?: ""),
        note = if (card != null && card.values.radius == null) {
            "This card has no Radius value."
        } else {
            "Spaces within the radius, obstacles and the water around the board counted (p.9, p.19)."
        },
        checked = settings.radius && overlay.radiusValue != null,
        enabled = overlay.radiusValue != null,
        swatch = "radius",
        bonus = settings.radiusBonus.takeIf { card?.values?.radius != null },
        onBonus = { onAction(BoardAction.RadiusBonus(it)) },
        onToggle = { onAction(BoardAction.ToggleRadius) },
    )
    if (card != null && card.text.isNotBlank()) {
        Overline("Card text")
        ParchmentPanel {
            dev.quinton.goa.web.catalog.CardTextView(card.text, "t-printed-small")
        }
        Note("The app measures; it never applies a card's effect. Read the card and move the pieces.")
    }
}

@Composable
private fun StepsMeasure(state: BoardUiState.Ready, piece: BoardPiece, onAction: (BoardAction) -> Unit) {
    val overlay = state.overlay ?: OverlayUi(OverlaySettings(movement = false))
    val settings = overlay.settings
    Overline("On the board")
    OverlayRow(
        title = "Movement ${settings.steps}",
        note = if (piece is MinionPiece) {
            "Where a card's “move N” could take it (p.11)."
        } else {
            "Where it could be moved N spaces (p.11)."
        },
        checked = settings.movement,
        enabled = true,
        swatch = "movement",
        steps = settings.steps,
        onSteps = { onAction(BoardAction.Steps(it)) },
        onToggle = { onAction(BoardAction.ToggleMovement) },
    )
    if (settings.movement) MovementRules(settings, onAction)
    if (overlay.returnTo.isNotEmpty()) {
        Note(
            "Outside its Battle Zone: it goes back by the shortest path of empty spaces to a highlighted " +
                "space (p.18" + (if (state.map.lanes.size > 1) "; bound to its Lane, p.20" else "") + ").",
        )
    }
}

/** "Path" (through empty spaces, through tokens, or ignoring every obstacle) and "In a straight line" (p.18–19). */
@Composable
private fun MovementRules(settings: OverlaySettings, onAction: (BoardAction) -> Unit) {
    Div({ classes("goa-board-rules") }) {
        P({ classes("t-body-medium") }) { Text("Path") }
        Segmented("Path", MovePath.entries, settings.path, { pathLabel(it) }) { onAction(BoardAction.SetPath(it)) }
        Note(
            when (settings.path) {
                MovePath.NORMAL -> "Only through empty spaces (p.11)."

                MovePath.THROUGH_TOKENS ->
                    "Also through tokens, never units or terrain: tokens a card lets units move through, and p.18 Immune."

                MovePath.IGNORE_OBSTACLES ->
                    "Through units, tokens and terrain, never ending on one (p.18 Ignoring Obstacles)."
            },
        )
    }
    SwitchRow(
        title = "In a straight line",
        note = "One of the six directions, stopping at the first obstacle it may not pass (p.19).",
        checked = settings.straightLine,
        enabled = true,
        swatch = null,
        onToggle = { onAction(BoardAction.ToggleStraightLine) },
    )
}

private fun pathLabel(path: MovePath): String = when (path) {
    MovePath.NORMAL -> "Normal"
    MovePath.THROUGH_TOKENS -> "Tokens"
    MovePath.IGNORE_OBSTACLES -> "Ignore all"
}

/** The hero's cards, Basic → Tier III → Ultimate (Gydion's spells after the deck); tap to measure, again to clear. */
@Composable
private fun CardStrip(cards: List<Card>, selectedId: String?, onSelect: (String?) -> Unit) {
    Div({
        classes("goa-board-cards")
        attr("role", "radiogroup")
        attr("aria-label", "Card to measure with")
    }) {
        cards.forEach { card ->
            val selected = card.id == selectedId
            Button({
                classes("goa-board-card", "color-${card.color.name.lowercase()}")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", selected.toString())
                attr(
                    "aria-label",
                    listOfNotNull(
                        card.name,
                        cardKindLabel(card),
                        card.initiative?.let {
                            "Initiative $it"
                        },
                    ).joinToString(", "),
                )
                onClick { onSelect(if (selected) null else card.id) }
            }) {
                Span({ classes("goa-board-card-kind") }) {
                    Span({ classes("goa-board-card-dot") })
                    Text(tierLabel(card))
                    card.initiative?.let { Span({ classes("goa-board-card-init") }) { Text("◆$it") } }
                }
                Span({ classes("goa-board-card-name") }) { Text(card.name) }
            }
        }
    }
}

/** Initiative and printed values of the chosen card, as printed ("Movement 4", "Range 1+"). */
@Composable
private fun CardSummary(card: Card) {
    fun plus(stat: Stat) = if (stat in card.values.plus) "+" else ""
    val values = listOfNotNull(
        card.initiative?.let { "Initiative $it" },
        card.values.attack?.let { "Attack $it${plus(Stat.ATTACK)}" },
        card.values.defense?.let { "Defense $it${plus(Stat.DEFENSE)}" },
        card.values.movement?.let { "Movement $it${plus(Stat.MOVEMENT)}" },
        card.values.range?.let { "Range $it${plus(Stat.RANGE)}" },
        card.values.radius?.let { "Radius $it${plus(Stat.RADIUS)}" },
    )
    Div({ classes("goa-board-values") }) {
        Span({ classes("t-label-large") }) { Text("${card.name} · ${cardKindLabel(card)}") }
        Div({
            classes("goa-board-value-list")
        }) { values.forEach { Span({ classes("goa-board-value") }) { Text(it) } } }
    }
}

@Composable
private fun OverlayRow(
    title: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    swatch: String,
    onToggle: () -> Unit,
    bonus: Int? = null,
    onBonus: (Int) -> Unit = {},
    steps: Int? = null,
    onSteps: (Int) -> Unit = {},
) {
    SwitchRow(title, note, checked, enabled, swatch, onToggle)
    if (bonus != null) {
        Stepper(if (bonus == 0) "Items: none" else "Items: +$bonus", "$title, items plus $bonus", bonus > 0, onBonus)
    }
    if (steps != null) {
        Stepper("Move ${dev.quinton.goa.feature.board.BoardText.spaces(steps)}", "Movement $steps", steps > 1, onSteps)
    }
}

/** A switch with the overlay's pattern ([swatch]: movement, fast, range, radius) beside its name. */
@Composable
private fun SwitchRow(
    title: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    swatch: String?,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("goa-board-switch")
        if (!enabled) classes("disabled")
    }) {
        if (swatch != null) {
            Span({
                classes("goa-board-swatch", swatch)
                attr("aria-hidden", "true")
            })
        }
        Span({ classes("goa-board-switch-text") }) {
            Span({ classes("t-body-large") }) { Text(title) }
            Span({ classes("t-body-small", "goa-board-muted") }) { Text(note) }
        }
        Input(InputType.Checkbox) {
            classes("goa-board-toggle")
            attr("role", "switch")
            checked(checked)
            if (!enabled) disabled()
            onChange { onToggle() }
        }
    }
}

@Composable
private fun Stepper(label: String, spoken: String, canDecrease: Boolean, onChange: (Int) -> Unit) {
    Div({ classes("goa-board-stepper") }) {
        Button({
            classes("goa-icon-button")
            attr("type", "button")
            attr("aria-label", "Less: $spoken")
            if (!canDecrease) disabled()
            onClick { onChange(-1) }
        }) { Text("−") }
        Span({
            classes("t-label-large")
            attr("aria-live", "polite")
        }) { Text(label) }
        Button({
            classes("goa-icon-button")
            attr("type", "button")
            attr("aria-label", "More: $spoken")
            onClick { onChange(1) }
        }) { Icon("add", IconSize.SMALL) }
    }
}

@Composable
internal fun Overline(text: String) {
    H3({ classes("goa-board-overline") }) { Text(text.uppercase()) }
}

@Composable
internal fun Note(text: String) {
    P({ classes("t-body-small", "goa-board-muted", "goa-board-note") }) { Text(text) }
}
