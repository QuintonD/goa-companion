package dev.quinton.goa.feature.board.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardColorDot
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.board.BoardRules.MovePath
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette
import dev.quinton.goa.feature.board.ui.cards.isSpell
import dev.quinton.goa.feature.board.ui.drawFastTravelCell
import dev.quinton.goa.feature.board.ui.drawMovementCell
import dev.quinton.goa.feature.board.ui.drawRadiusCell
import dev.quinton.goa.feature.board.ui.drawRangeCell
import dev.quinton.goa.feature.board.ui.drawSwatch

/**
 * Details of the selected piece: who it is and where, Measure and Remove, and (for a hero) a card
 * to measure with and its overlays, each switch labelled with the pattern it draws on the map.
 * The panel around it hides (BoardScreen); hidden, the piece's name and Zone stay in its peek bar.
 */
@Composable
internal fun SelectionPanel(
    state: BoardUiState.Ready,
    piece: BoardPiece,
    onAction: (BoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = boardPalette()
    val hero = (piece as? HeroPiece)?.let { state.heroes.hero(it.heroId) }
    Column(modifier) {
        PanelHeader(state, piece, hero, palette, onAction)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        ) {
            PieceActions(state, piece, onAction)
            if (piece.at == null) {
                Note("Waiting in the tray: tap a highlighted space on the board to put it there (p.8).")
            } else if (hero != null) {
                HeroMeasures(state, hero, palette, onAction)
            } else {
                StepsMeasure(state, piece, palette, onAction)
            }
        }
    }
}

/** The selected piece's name: the hero, the minion's type, or the token's label. */
internal fun pieceTitle(state: BoardUiState.Ready, piece: BoardPiece): String = when (piece) {
    is HeroPiece -> state.heroes.hero(piece.heroId).name
    is MinionPiece -> BoardText.minion(piece.type)
    is TokenPiece -> "Token: ${piece.label}"
}

/** Where the selected piece is: its Zone ("in the tray" while it waits) and Battle Zone. */
internal fun pieceWhere(state: BoardUiState.Ready, piece: BoardPiece): String {
    val zone = piece.at?.let { state.geometry.zoneOf(it) }
    val battleLane = state.map.lanes.firstOrNull {
        (state.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone
    }
    return listOfNotNull(
        if (piece.at == null) "in the tray" else BoardText.zone(state.map, zone),
        battleLane?.let { BoardText.battleZone(state.map, it.id) },
    ).joinToString(" · ")
}

@Composable
private fun PanelHeader(
    state: BoardUiState.Ready,
    piece: BoardPiece,
    hero: BoardHero?,
    palette: BoardPalette,
    onAction: (BoardAction) -> Unit,
) {
    val title = when (piece) {
        is HeroPiece -> hero?.name ?: "Unknown hero"
        is MinionPiece -> BoardText.minion(piece.type)
        is TokenPiece -> "Token: ${piece.label}"
    }
    val zone = piece.at?.let { state.geometry.zoneOf(it) }
    val team = when (piece) {
        is HeroPiece -> piece.team
        is MinionPiece -> piece.team
        is TokenPiece -> piece.team
    }
    val battleLane = state.map.lanes.firstOrNull {
        (state.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone
    }
    // A minion on a double-lane map is bound to its Lane (p.20).
    val boundTo = (piece as? MinionPiece)?.let { BoardText.lane(state.map, state.geometry.laneOf(it)) }
    val subtitle = listOfNotNull(
        team?.let { "${BoardText.team(it)} team" },
        boundTo?.let { "$it minion" },
        if (piece.at == null) "in the tray" else BoardText.zone(state.map, zone),
        battleLane?.let { BoardText.battleZone(state.map, it.id) },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.xs, bottom = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PieceToken(piece, hero, scaledIconSize(40.dp, maxScale = 1.4f), palette)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (hero != null && !hero.verified) {
                    Spacer(Modifier.width(Spacing.sm))
                    UnverifiedBadge()
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onAction(BoardAction.Deselect) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Deselect")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceActions(state: BoardUiState.Ready, piece: BoardPiece, onAction: (BoardAction) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(bottom = Spacing.sm),
    ) {
        val measuring = state.tool == BoardTool.Measure
        if (piece.at != null) {
            PanelChip(
                selected = measuring,
                label = if (measuring) "Measuring" else "Measure",
                icon = { Icon(Icons.Outlined.Straighten, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { onAction(if (measuring) BoardAction.CancelTool else BoardAction.StartMeasure) },
            )
        }
        PanelChip(
            selected = false,
            label = "Remove",
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = { onAction(BoardAction.Remove) },
        )
    }
}

@Composable
private fun PanelChip(selected: Boolean, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = icon,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GoaTheme.colors.engravedBorder,
            selectedBorderColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

@Composable
private fun HeroMeasures(
    state: BoardUiState.Ready,
    hero: BoardHero,
    palette: BoardPalette,
    onAction: (BoardAction) -> Unit,
) {
    val overlay = state.overlay ?: OverlayUi(OverlaySettings())
    val card = overlay.card
    Overline("Card")
    CardStrip(hero.measurable, card?.id) { onAction(BoardAction.ChooseCard(it)) }
    ReadCardsButton(hero, card?.id) { onAction(BoardAction.ChooseCard(it)) }
    if (card == null) {
        Note("Pick a card to see where it reaches: its Movement, Range and Radius.")
    } else {
        CardSummary(card)
    }
    Overline("On the board")
    val settings = overlay.settings
    val movementNote = when {
        card == null -> "Pick a card first."
        card.values.movement == null -> "This card has no Movement value."
        else -> "Spaces it can end on, with the fewest spaces each needs (p.11)."
    }
    OverlayRow(
        title = "Movement" + (overlay.movementValue?.let { " $it" } ?: ""),
        note = movementNote,
        checked = settings.movement && overlay.movementValue != null,
        enabled = overlay.movementValue != null,
        swatch = { c, m, u -> drawMovementCell(c, m, palette) },
        palette = palette,
        bonus = settings.movementBonus,
        onBonus = { delta: Int ->
            onAction(BoardAction.MovementBonus(delta))
        }.takeIf { card?.values?.movement != null },
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
        swatch = { c, m, u -> drawFastTravelCell(c, m, u, palette) },
        palette = palette,
        onToggle = { onAction(BoardAction.ToggleFastTravel) },
    )
    OverlayRow(
        title = "Range" + (overlay.rangeValue?.let { " $it" } ?: ""),
        note = if (card != null &&
            card.values.range == null
        ) {
            "This card has no Range value."
        } else {
            "Spaces within range, obstacles and the water around the board counted (p.9, p.19)."
        },
        checked = settings.range && overlay.rangeValue != null,
        enabled = overlay.rangeValue != null,
        swatch = { c, m, u -> drawRangeCell(c, m, u, palette) },
        palette = palette,
        bonus = settings.rangeBonus,
        onBonus = { delta: Int -> onAction(BoardAction.RangeBonus(delta)) }.takeIf { card?.values?.range != null },
        onToggle = { onAction(BoardAction.ToggleRange) },
    )
    OverlayRow(
        title = "Radius" + (overlay.radiusValue?.let { " $it" } ?: ""),
        note = if (card != null &&
            card.values.radius == null
        ) {
            "This card has no Radius value."
        } else {
            "Spaces within the radius, obstacles and the water around the board counted (p.9, p.19)."
        },
        checked = settings.radius && overlay.radiusValue != null,
        enabled = overlay.radiusValue != null,
        swatch = { c, m, u -> drawRadiusCell(c, m, u, palette) },
        palette = palette,
        bonus = settings.radiusBonus,
        onBonus = { delta: Int -> onAction(BoardAction.RadiusBonus(delta)) }.takeIf { card?.values?.radius != null },
        onToggle = { onAction(BoardAction.ToggleRadius) },
    )
    if (card != null && card.text.isNotBlank()) {
        Overline("Card text")
        CardQuote(card)
        Note("The app measures; it never applies a card's effect. Read the card and move the pieces.")
    }
}

@Composable
private fun StepsMeasure(
    state: BoardUiState.Ready,
    piece: BoardPiece,
    palette: BoardPalette,
    onAction: (BoardAction) -> Unit,
) {
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
        swatch = { c, m, _ -> drawMovementCell(c, m, palette) },
        palette = palette,
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

/**
 * How the card lets it move: "Path" (only through empty spaces, through tokens, or ignoring every
 * obstacle) and "In a straight line". It always ends on an empty space (p.18).
 */
@Composable
private fun MovementRules(settings: OverlaySettings, onAction: (BoardAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = Spacing.xs)) {
        Text("Path", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            val paths = MovePath.entries
            paths.forEachIndexed { i, path ->
                SegmentedButton(
                    selected = settings.path == path,
                    onClick = { onAction(BoardAction.SetPath(path)) },
                    shape = SegmentedButtonDefaults.itemShape(i, paths.size),
                    icon = {},
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        when (path) {
                            MovePath.NORMAL -> "Normal"
                            MovePath.THROUGH_TOKENS -> "Tokens"
                            MovePath.IGNORE_OBSTACLES -> "Ignore all"
                        },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            when (settings.path) {
                MovePath.NORMAL -> "Only through empty spaces (p.11)."

                MovePath.THROUGH_TOKENS ->
                    "Also through tokens, never units or terrain: tokens a card lets units move through, " +
                        "and p.18 Immune."

                MovePath.IGNORE_OBSTACLES ->
                    "Through units, tokens and terrain, never ending on one (p.18 Ignoring Obstacles)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SwitchRow(
        title = "In a straight line",
        note = "One of the six directions, stopping at the first obstacle it may not pass (p.19).",
        checked = settings.straightLine,
        onToggle = { onAction(BoardAction.ToggleStraightLine) },
    )
}

/**
 * The hero's cards, Basic → Tier III → Ultimate; tap to measure with one (again to clear). The
 * chosen card scrolls into view with the card before it still showing, and a fade at either edge
 * says there are more cards that way.
 */
@Composable
private fun CardStrip(cards: List<Card>, selectedId: String?, onSelect: (String?) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val index = cards.indexOfFirst { it.id == selectedId }
        if (index > 0) listState.animateScrollToItem(index - 1)
    }
    val fade = MaterialTheme.colorScheme.surface
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val edge = 28.dp.toPx()
                if (listState.canScrollBackward) {
                    drawRect(
                        Brush.horizontalGradient(listOf(fade, fade.copy(alpha = 0f)), startX = 0f, endX = edge),
                        size = Size(edge, size.height),
                    )
                }
                if (listState.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(fade.copy(alpha = 0f), fade),
                            startX = size.width - edge,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - edge, 0f),
                        size = Size(edge, size.height),
                    )
                }
            },
    ) {
        items(cards, key = { it.id }) { card ->
            CardChip(card, selected = card.id == selectedId, onClick = {
                onSelect(
                    if (card.id ==
                        selectedId
                    ) {
                        null
                    } else {
                        card.id
                    },
                )
            })
        }
    }
}

@Composable
private fun CardChip(card: Card, selected: Boolean, onClick: () -> Unit) {
    val accent = cardColor(card.color)
    val spoken = buildString {
        append(card.name)
        append(", ")
        append(cardKindLabel(card))
        card.initiative?.let { append(", Initiative $it") }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .widthIn(min = 96.dp, max = 168.dp)
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = if (selected) "Measuring" else ""
            },
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardColorDot(card.color, size = 10.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    tierLabel(card),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                card.initiative?.let {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        GameIcons.initiative.vector,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$it",
                        style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    )
                }
            }
            Text(card.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Initiative and printed values of the chosen card, as printed ("Movement 4", "Range 1+"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardSummary(card: Card) {
    val values = listOfNotNull(
        card.initiative?.let { GameIcons.initiative to "Initiative $it" },
        card.values.attack?.let { GameIcons.attack to "Attack $it${plus(card, Stat.ATTACK)}" },
        card.values.defense?.let { GameIcons.defense to "Defense $it${plus(card, Stat.DEFENSE)}" },
        card.values.movement?.let { GameIcons.movement to "Movement $it${plus(card, Stat.MOVEMENT)}" },
        card.values.range?.let { GameIcons.range to "Range $it${plus(card, Stat.RANGE)}" },
        card.values.radius?.let { GameIcons.radius to "Radius $it${plus(card, Stat.RADIUS)}" },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
    ) {
        values.forEach { (icon, text) -> ValueLabel(icon, text) }
    }
}

private fun plus(card: Card, stat: Stat) = if (stat in card.values.plus) "+" else ""

@Composable
private fun ValueLabel(icon: GameIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(
            icon.vector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(16.dp)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The card's printed text, verbatim, with its icons and bold keywords (as the card viewer shows it). */
@Composable
private fun CardQuote(card: Card) {
    val accent = cardColor(card.color)
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(accent.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(Spacing.md))
        CardTextView(
            card.text,
            style = MaterialTheme.typography.bodyMedium,
            iconTint = accent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OverlayRow(
    title: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    swatch: DrawScope.(List<Offset>, Offset, Float) -> Unit,
    palette: BoardPalette,
    onToggle: () -> Unit,
    bonus: Int = 0,
    onBonus: ((Int) -> Unit)? = null,
    steps: Int? = null,
    onSteps: ((Int) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(scaledIconSize(28.dp, maxScale = 1.4f)).clearAndSetSemantics {}) {
                drawSwatch(palette.laneA, palette.gap) { c, m, u -> if (enabled) swatch(c, m, u) }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.run { if (enabled) onSurface else onSurfaceVariant },
                )
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (onBonus != null && checked) {
            Stepper(
                label = if (bonus == 0) "Items: none" else "Items: +$bonus",
                spoken = "$title, items plus $bonus",
                canDecrease = bonus > 0,
                onChange = onBonus,
            )
        }
        if (onSteps != null && steps != null) {
            Stepper(
                label = "Move ${BoardText.spaces(steps)}",
                spoken = "Movement $steps",
                canDecrease = steps > 1,
                onChange = onSteps,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, spoken: String, canDecrease: Boolean, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(-1) }, enabled = canDecrease) {
            Icon(Icons.Outlined.Remove, contentDescription = "Less: $spoken")
        }
        IconButton(onClick = { onChange(1) }) {
            Icon(Icons.Outlined.Add, contentDescription = "More: $spoken")
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 40.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Overline(text: String) {
    Text(
        text.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs).semantics { heading() },
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = Spacing.xs),
    )
}

/** "Basic Gold", "Tier II Red"; a spell card "Gold spell", "Tier II Red spell", "Purple spell" (Wish). */
internal fun cardKindLabel(card: Card): String = if (card.isSpell) {
    listOfNotNull(
        card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)}" },
        cardColorName(card.color),
        "spell",
    ).joinToString(" ")
} else {
    "${tierLabel(card)} ${cardColorName(card.color)}"
}

/** "Basic", "Tier I", "Tier II", "Tier III", "Ultimate"; a spell card "Spell", "Tier II spell". */
internal fun tierLabel(card: Card): String = when {
    // Spellbook pages carry their printed spell level, null for the gold cantrips (catalog Card.tier).
    card.isSpell -> card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)} spell" } ?: "Spell"

    card.color == CardColor.GOLD || card.color == CardColor.SILVER -> "Basic"

    card.color == CardColor.PURPLE || card.tier == ULTIMATE_TIER -> "Ultimate"

    else -> card.tier?.let { tier -> "Tier ${tierNumeral(tier)}" } ?: "Other"
}

private fun tierNumeral(tier: Int): String = listOf("I", "II", "III", "IV").getOrElse(tier - 1) { tier.toString() }

private const val ULTIMATE_TIER = 4
