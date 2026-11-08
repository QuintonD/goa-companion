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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
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
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardFamilyLabel
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.StatReadout
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.bleedHorizontally
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
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
import dev.quinton.goa.feature.board.play.table.PagePill
import dev.quinton.goa.feature.board.ui.ArtBoardPalette
import dev.quinton.goa.feature.board.ui.ArtSwatchGround
import dev.quinton.goa.feature.board.ui.BoardPalette
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.PlateWell
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
                HeroMeasures(state, hero, swatchPalette(state, palette), onAction)
            } else {
                StepsMeasure(state, piece, swatchPalette(state, palette), onAction)
            }
        }
    }
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
    val subtitle = pieceSubtitle(state, piece)
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val measuring = state.tool == BoardTool.Measure
        if (piece.at != null) {
            PanelChip(
                selected = measuring,
                label = if (measuring) "Measuring" else "Measure",
                icon = {
                    Icon(
                        Icons.Outlined.Straighten,
                        contentDescription = null,
                        modifier = Modifier.size(scaledIconSize(18.dp, ICON_GROWTH)),
                    )
                },
                onClick = { onAction(if (measuring) BoardAction.CancelTool else BoardAction.StartMeasure) },
            )
        }
        PanelChip(
            selected = false,
            label = "Remove",
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(scaledIconSize(18.dp, ICON_GROWTH)),
                )
            },
            onClick = { onAction(BoardAction.Remove) },
        )
    }
}

@Composable
private fun PanelChip(selected: Boolean, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    GoaFilterChip(selected = selected, onClick = onClick, leadingIcon = icon) { Text(label) }
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
        else -> "Spaces it can end on, with the fewest spaces each needs."
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
        page = "p.11",
    )
    if (settings.movement && overlay.movementValue != null) MovementRules(settings, onAction)
    OverlayRow(
        title = "Fast Travel",
        note = if (settings.fastTravel && overlay.fastTravel.isEmpty()) {
            "Not now: an enemy hero or minion is in this Zone, or no space is free."
        } else {
            "Own or an adjacent Zone, with no enemy hero or minion in either."
        },
        checked = settings.fastTravel,
        enabled = true,
        swatch = { c, m, u -> drawFastTravelCell(c, m, u, palette) },
        palette = palette,
        onToggle = { onAction(BoardAction.ToggleFastTravel) },
        page = "p.11",
    )
    OverlayRow(
        title = "Card range" + (overlay.rangeValue?.let { " $it" } ?: ""),
        note = if (card != null &&
            card.values.range == null
        ) {
            "This card has no Range value."
        } else {
            "Spaces within range, obstacles and the water around the board counted."
        },
        checked = settings.range && overlay.rangeValue != null,
        enabled = overlay.rangeValue != null,
        swatch = { c, m, u -> drawRangeCell(c, m, u, palette) },
        palette = palette,
        bonus = settings.rangeBonus,
        onBonus = { delta: Int -> onAction(BoardAction.RangeBonus(delta)) }.takeIf { card?.values?.range != null },
        onToggle = { onAction(BoardAction.ToggleRange) },
        page = "p.9",
    )
    OverlayRow(
        title = "Radius" + (overlay.radiusValue?.let { " $it" } ?: ""),
        note = if (card != null &&
            card.values.radius == null
        ) {
            "This card has no Radius value."
        } else {
            "Spaces within the radius, obstacles and the water around the board counted."
        },
        checked = settings.radius && overlay.radiusValue != null,
        enabled = overlay.radiusValue != null,
        swatch = { c, m, u -> drawRadiusCell(c, m, u, palette) },
        palette = palette,
        bonus = settings.radiusBonus,
        onBonus = { delta: Int -> onAction(BoardAction.RadiusBonus(delta)) }.takeIf { card?.values?.radius != null },
        onToggle = { onAction(BoardAction.ToggleRadius) },
        page = "p.9",
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
            "Where a card's “move N” could take it."
        } else {
            "Where it could be moved N spaces."
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
        GoaSegmentedRow(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            val paths = MovePath.entries
            paths.forEachIndexed { i, path ->
                GoaSegment(
                    selected = settings.path == path,
                    onClick = { onAction(BoardAction.SetPath(path)) },
                    icon = {},
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
                MovePath.NORMAL -> "Only through empty spaces."

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
        note = "One of the six directions, stopping at the first obstacle it may not pass.",
        checked = settings.straightLine,
        onToggle = { onAction(BoardAction.ToggleStraightLine) },
    )
}

/**
 * The hero's cards, Basic → Tier III → Ultimate; tap to measure with one (again to clear). The
 * chosen card scrolls into view with the card before it still showing, and a fade and chevron at either edge
 * say there are more cards that way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStrip(cards: List<Card>, selectedId: String?, onSelect: (String?) -> Unit) {
    val toggle: (Card) -> Unit = { card -> onSelect(if (card.id == selectedId) null else card.id) }
    // Large text: the chips wrap onto rows, every name whole and none cut at the screen's edge.
    if (LocalDensity.current.fontScale > WRAP_SCALE) {
        // Two to a row, each half the width: a name breaks only between its words.
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = 2,
        ) {
            cards.forEach { card ->
                CardChip(
                    card,
                    selected = card.id == selectedId,
                    onClick = { toggle(card) },
                    modifier = Modifier.weight(1f),
                    wide = true,
                )
            }
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val index = cards.indexOfFirst { it.id == selectedId }
        if (index > 0) listState.animateScrollToItem(index - 1)
    }
    // The rail runs to the panel's edges, its first card on the gutter; a hidden end fades under a chevron.
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .bleedHorizontally(Spacing.lg)
            .horizontalEdgeFade(listState),
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

/**
 * A card to measure with: its family in words beside its colour's dot ("Red · II", "Gold · Basic": never the colour
 * alone, sol #12), initiative and name. Chosen, it takes the one selection look (bronze edge and tint, a tick), never
 * the card's own colour: colour is the card's, bronze is "chosen". [wide]: a share of a row (large text) rather than
 * its own width.
 */
@Composable
private fun CardChip(
    card: Card,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val spoken = buildString {
        append(card.name)
        append(", ")
        append(cardKindLabel(card))
        card.initiative?.let { append(", Initiative $it") }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else PlateWell,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = modifier
            .then(
                if (wide) {
                    Modifier
                } else {
                    Modifier.widthIn(
                        min = 96.dp,
                        max = 168.dp * minOf(LocalDensity.current.fontScale, CHIP_GROWTH).coerceAtLeast(1f),
                    )
                },
            )
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = if (selected) "Measuring" else ""
            },
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)) {
            // The shared family label (its colour's ribbon and the words). Its own line with large text:
            // "Silver Basic", which may break between its words, never inside.
            val family: @Composable (Modifier) -> Unit = { modifier ->
                CardFamilyLabel(
                    card.color,
                    card.tier,
                    modifier,
                    text = if (wide) familyLabel(card).replace("\u00A0·\u00A0", " ") else familyLabel(card),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Large text in a half-width chip: the family on a line of its own under the name, never broken.
                if (!wide) family(Modifier.weight(1f, fill = false))
                card.initiative?.let {
                    if (!wide) Spacer(Modifier.width(6.dp))
                    Icon(
                        GameIcons.initiative.vector,
                        null,
                        Modifier.size(scaledIconSize(12.dp, ICON_GROWTH)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$it",
                        style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    )
                }
            }
            // Two lines before an ellipsis: with large text "Raging Stream" stays whole, not "Raging St…".
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        modifier = Modifier.size(scaledIconSize(16.dp, ICON_GROWTH)),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (wide) family(Modifier)
        }
    }
}

/**
 * A card's family as its chip heads it: the deck colour in a word and the tier ("Red · II", "Gold · Basic",
 * "Purple · Ultimate"), held together by non-breaking spaces; a spell page by its level as printed.
 */
internal fun familyLabel(card: Card): String = if (card.isSpell) {
    tierLabel(card)
} else {
    "${cardColorName(card.color)}\u00A0·\u00A0${tierLabel(card).removePrefix("Tier ")}"
}

/** How much wider a card chip may grow with the font scale. */
private const val CHIP_GROWTH = 1.5f

/** How far the panel's small icons grow with large text, beside text that grows to twice its size. */
private const val ICON_GROWTH = 1.6f

/** From this font scale the card chips wrap rather than scroll. */
private const val WRAP_SCALE = 1.3f

/** Initiative and printed values of the chosen card, as printed ("Movement 4", "Range 1+"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardSummary(card: Card) {
    val values = listOfNotNull(
        card.initiative?.let { Stat.INITIATIVE to "$it" },
        card.values.attack?.let { Stat.ATTACK to "$it${plus(card, Stat.ATTACK)}" },
        card.values.defense?.let { Stat.DEFENSE to "$it${plus(card, Stat.DEFENSE)}" },
        card.values.movement?.let { Stat.MOVEMENT to "$it${plus(card, Stat.MOVEMENT)}" },
        card.values.range?.let { Stat.RANGE to "$it${plus(card, Stat.RANGE)}" },
        card.values.radius?.let { Stat.RADIUS to "$it${plus(card, Stat.RADIUS)}" },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
    ) {
        // The app's one stat readout (icon, value, name), as on the hero page.
        values.forEach { (stat, value) -> StatReadout(stat, value) }
    }
}

private fun plus(card: Card, stat: Stat) = if (stat in card.values.plus) "+" else ""

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
    /** The rulebook page the layer follows, as a small pill after its title (never in its note). */
    page: String? = null,
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
                drawSwatch(if (palette.onArt) ArtSwatchGround else palette.laneA, palette.gap) { c, m, u ->
                    if (enabled) swatch(c, m, u)
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.run { if (enabled) onSurface else onSurfaceVariant },
                    )
                    page?.let {
                        Spacer(Modifier.width(Spacing.sm))
                        PagePill(it)
                    }
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            GoaSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
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
        GoaSwitch(checked = checked, onCheckedChange = null)
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

/** A swatch shows its cue as the map draws it: over a painting, the art palette's. */
private fun swatchPalette(state: BoardUiState.Ready, palette: BoardPalette): BoardPalette =
    if (state.map.art != null) ArtBoardPalette else palette
