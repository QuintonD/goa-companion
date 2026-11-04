package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.MapButton
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.ui.FitWhileHidden
import dev.quinton.goa.feature.board.ui.HideablePanel
import dev.quinton.goa.feature.board.ui.PanelCover
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.feature.board.ui.PanelStripHeight
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import kotlinx.coroutines.launch

/**
 * Where the question ends in the panel's content, px, reported by [DecisionPanel]; null when none is asked. The panel
 * stops a little past it, so what follows (what happened, the hands) doesn't take the map's room: it scrolls.
 */
internal val LocalPanelQuestionEnd = staticCompositionLocalOf<((Int?) -> Unit)?> { null }

/**
 * The game table: the map full screen (the Lane up the screen in portrait, left to right in
 * landscape), the round at a glance and every hero's status floating at the top, Fit and Flip at
 * the right, and [panel] (the question, the computer's move, the coach…) at the bottom in portrait
 * or on the right in landscape. The map frames the Lane between them and brings the spaces asked
 * for into view. In landscape the hero strip is portraits only, beside the round at a glance when they all fit there.
 * Under a tall panel the turn track shrinks to small cards: a question's most important moment is on the map.
 *
 * The panel hides ([HideablePanel]) to show the whole board: then it is [peek]'s one line (with
 * [peekTrailing], e.g. Confirm), only the round at a glance stays at the top, and the map fits the
 * whole board unless the person has moved it. Taps on the map keep selecting while it is hidden.
 * Hidden or shown is kept for the screen (and survives rotation); [initialPanelHidden] for previews.
 * [onSpellbook] adds a Spellbook control per hero the person controls that has one (Gydion, ADR-040).
 * [panelTitle] goes on the panel's top strip, pinned as it scrolls ([DecisionWho]: who is asked).
 * [feed] floats under the top of the table, beside the map buttons ([EventFeed]: what just happened).
 */
@Composable
fun GameTable(
    table: TableUi,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    onHero: (String) -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    peek: PanelPeek = PanelPeek(""),
    peekTrailing: (@Composable () -> Unit)? = null,
    initialPanelHidden: Boolean = false,
    focus: CardFocusState? = null,
    onSpellbook: ((String) -> Unit)? = null,
    panelTitle: (@Composable () -> Unit)? = null,
    /** Goes at the head of the table, above the HUD: an online game's players and clock (ADR-056). */
    topStrip: (@Composable () -> Unit)? = null,
    feed: (@Composable () -> Unit)? = null,
    panel: @Composable ColumnScope.() -> Unit,
) {
    var hidden by rememberSaveable { mutableStateOf(initialPanelHidden) }
    // Where the table and its map buttons are on the screen: the Zone labels keep clear of the buttons.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var buttons by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val landscape = maxWidth > maxHeight
        val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
        val layout =
            remember(table.map, orientation, table.bottomTeam) { MapLayout(table.map, orientation, table.bottomTeam) }
        val lane = remember(layout, table.map) { layout.laneRegion(table.map, table.geometry) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var cover by remember { mutableStateOf(PanelCover()) }
        var topHeight by remember { mutableIntStateOf(0) }
        // The map buttons' column (Fit, Flip, the Spellbook), with the gap to the screen's edge.
        var buttonsWidth by remember { mutableIntStateOf(0) }
        // Choosing a card sideways, the cards are what is read: a wider panel, so they aren't small over an empty one.
        val sideWidth: Dp = when {
            !landscape -> 0.dp
            table.handDecision && table.asking -> minOf(maxWidth * HAND_SIDE_SHARE, HAND_SIDE_PANEL)
            else -> minOf(maxWidth * SIDE_SHARE, SIDE_PANEL)
        }
        // The floating overlays keep clear of the side panel only while it is shown.
        val overlayEnd = if (landscape && !hidden) sideWidth else 0.dp
        // Where the question ends in the panel: the panel needn't be taller than that and a look at what follows.
        var questionEnd by remember { mutableStateOf<Int?>(null) }
        val panelShare = questionEnd?.let { end ->
            val height = with(density) { end.toDp() } + PanelStripHeight + PANEL_PAST_QUESTION
            (height / maxHeight).coerceAtMost(PANEL_SHARE)
        } ?: PANEL_SHARE
        val tallPanel = !landscape && !hidden && with(density) { cover.bottom.toDp() } > maxHeight * TALL_PANEL_SHARE
        val compactTrack = hidden || tallPanel
        val trackCard = when {
            compactTrack -> TRACK_CARD_COMPACT
            maxHeight < TALL_TABLE -> TRACK_CARD_SHORT
            else -> TRACK_CARD_TALL
        }

        LaunchedEffect(cover, topHeight) {
            viewport.updateInsets(bottom = cover.bottom.toFloat(), end = cover.end.toFloat(), top = topHeight.toFloat())
        }
        FitWhileHidden(viewport, hidden)
        // Bring what is asked for into view: the options, the unit they are about, the highlights,
        // between what floats over the map (the round at a glance and the turn track or hero strip at
        // the top, the map buttons at the end side, the panel). Again as those settle (the turn track
        // appears with the reveal, the panel grows), but never once the person has moved the map
        // since the question came: their pan or pinch wins. Not while the panel is hidden and the
        // whole board is in view.
        val decision = table.decision
        val gesturesAtQuestion = remember(decision?.id, table.asking) { viewport.gestures }
        LaunchedEffect(
            decision?.id,
            table.asking,
            viewport.unit,
            viewport.bottomInset,
            viewport.endInset,
            viewport.topInset,
            buttonsWidth,
            hidden,
        ) {
            if (decision == null || viewport.unit == 0f) return@LaunchedEffect
            if (hidden && !viewport.userMoved) return@LaunchedEffect
            if (viewport.gestures != gesturesAtQuestion) return@LaunchedEffect
            // Choosing a card: the person's own heroes (the others choose theirs hidden, p.7).
            if (decision.kind == DecisionKind.PLAY_CARD && !table.asking) return@LaunchedEffect
            val who = if (decision.kind == DecisionKind.PLAY_CARD) table.mine else setOf(decision.seat)
            // Whoever else the question is about: the attacker of a Defend, the hero acting.
            val alsoAbout = listOfNotNull(decision.context.attackerId, table.view.acting).mapNotNull(table::at)
            val hexes = table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at) +
                decision.context.highlight + listOfNotNull(table.subject?.let(table::at)) +
                who.mapNotNull(table::at) + alsoAbout
            val region = layout.bounds(hexes, pad = 1.1f) ?: return@LaunchedEffect
            // The Battle Zone around them when it fits in the free area: it is where the game is. A question answered
            // on the map keeps its spaces comfortable to tap; any other may take the smaller size for it.
            val zones = table.battleZones.toSet()
            val zone = table.map.spaces.filter { it.zone in zones }.map { it.hex }
            val whole = layout.bounds(hexes + zone, pad = 1.1f)
            val onMap = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()
            val hex = with(density) { (if (onMap) ZONE_HEX_TAP else ZONE_HEX_READ).toPx() } / SQRT3
            val framed = whole?.takeIf { w ->
                w.width * hex <= viewport.visible.width - ZONE_MARGIN &&
                    w.height * hex <= viewport.visible.height - ZONE_MARGIN
            } ?: region
            viewport.reveal(framed, framed.center, shrink = OPTIONS_SHRINK, clearEnd = buttonsWidth.toFloat())
        }

        // A new question (another decision) starts at the top of the panel, its header in view; the
        // same question composed again (a selection, a rotation) keeps where the person scrolled to.
        val panelScroll = rememberScrollState()
        var scrolledFor by rememberSaveable { mutableStateOf(decision?.id) }
        LaunchedEffect(decision?.id) {
            if (decision?.id != scrolledFor) {
                scrolledFor = decision?.id
                panelScroll.scrollTo(0)
            }
        }

        GameMapView(
            table,
            layout,
            viewport,
            onTap,
            Modifier.fillMaxSize(),
            lane = lane,
            onLongPressHero = onHero,
            buttons = buttons?.translate(-origin),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = overlayEnd)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topHeight = it.height + with(density) { Spacing.sm.roundToPx() } },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                topStrip?.invoke()
                // After the reveal the turn's cards stay in view, the panel shown or hidden (ADR-041);
                // before it, the hero strip comes back with the panel (one tap).
                val track = table.track
                if (landscape && !hidden) {
                    TableTopRow(table, onHero, focus = focus)
                } else {
                    TableHud(table, if (landscape) Modifier else Modifier.fillMaxWidth(), spread = !landscape)
                    if (track != null) {
                        // [trackCard]: a small phone keeps the map's height, the question framed under it.
                        TurnTrackRow(table, track, onHero, focus = focus, compact = compactTrack, cardWidth = trackCard)
                    } else if (!hidden) {
                        HeroStrip(table, onHero)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.weight(1f)) { feed?.invoke() }
                Column(
                    Modifier
                        .onSizeChanged { buttonsWidth = it.width + with(density) { Spacing.sm.roundToPx() } }
                        .onGloballyPositioned { buttons = it.boundsInRoot() },
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (viewport.isFit) {
                        MapButton(Icons.Outlined.ZoomIn, "Zoom to the lane") { scope.launch { viewport.animateLane() } }
                    } else {
                        MapButton(Icons.Outlined.CenterFocusStrong, "Fit the whole board") {
                            scope.launch { viewport.animateFit() }
                        }
                    }
                    MapButton(
                        Icons.Outlined.SwapVert,
                        "Flip: ${BoardText.team(table.bottomTeam.opponent)} at the bottom",
                        onFlip,
                    )
                    // Gydion's spellbook, one tap at any point of the game (ADR-040): under the map buttons,
                    // so it never covers the round at a glance, the turn track or the hero strip.
                    if (onSpellbook != null) {
                        table.spellbookSeats.forEach { id ->
                            SpellbookButton(table, id, onClick = { onSpellbook(id) })
                        }
                    }
                }
            }
        }

        HideablePanel(
            landscape = landscape,
            hidden = hidden,
            onHiddenChange = { hidden = it },
            peek = peek,
            sideWidth = sideWidth,
            maxShare = panelShare,
            onCover = { cover = it },
            peekTrailing = peekTrailing,
            title = panelTitle,
        ) {
            CompositionLocalProvider(LocalPanelQuestionEnd provides { questionEnd = it }) {
                Column(
                    Modifier
                        .verticalScroll(panelScroll)
                        .padding(start = PANEL_INSET, end = PANEL_INSET, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = panel,
                )
            }
        }
    }
}

/**
 * Landscape: the round at a glance and the heroes as portraits; after the reveal, the turn's cards in their place. On
 * one row when they all fit; otherwise on a row of their own, rather than the last of them cut by the panel (they
 * scroll only if they don't fit there either).
 */
@Composable
internal fun TableTopRow(
    table: TableUi,
    onHero: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: CardFocusState? = null,
) {
    Layout(
        content = {
            TableHud(table, compact = true)
            val track = table.track
            if (track != null) {
                TurnTrackRow(table, track, onHero, focus = focus, compact = true)
            } else {
                HeroStrip(table, onHero, compact = true)
            }
        },
        modifier = modifier,
    ) { (hud, strip), constraints ->
        val gap = Spacing.xs.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val hudPlaced = hud.measure(loose)
        val beside = hudPlaced.width + gap + strip.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val stripPlaced = strip.measure(
            if (beside) loose.copy(maxWidth = constraints.maxWidth - hudPlaced.width - gap) else loose,
        )
        val width = if (beside) hudPlaced.width + gap + stripPlaced.width else maxOf(hudPlaced.width, stripPlaced.width)
        val height = if (beside) {
            maxOf(hudPlaced.height, stripPlaced.height)
        } else {
            hudPlaced.height + gap + stripPlaced.height
        }
        layout(width, height) {
            if (beside) {
                hudPlaced.place(0, (height - hudPlaced.height) / 2)
                stripPlaced.place(hudPlaced.width + gap, (height - stripPlaced.height) / 2)
            } else {
                hudPlaced.place(0, 0)
                stripPlaced.place(0, hudPlaced.height + gap)
            }
        }
    }
}

/**
 * Confirm for a hidden panel's peek bar: the space or piece tapped on the map answers the question
 * without opening the panel. Nothing when no board choice is selected.
 */
@Composable
fun PeekConfirm(table: TableUi, busy: Boolean, onAnswer: (Choice) -> Unit) {
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token } ?: return
    if (!table.asking || table.handDecision) return
    val label = table.label(selected)
    PlateButton(
        onClick = { onAnswer(selected) },
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .semantics { contentDescription = "Confirm · $label" },
    ) { Text("Confirm", maxLines = 1) }
}

/** The event log (a sheet): every event of the game in the engine's words, the latest turn first ([newestFirst]). */
@Composable
fun EventLog(lines: List<LogLine>, modifier: Modifier = Modifier, at: Int? = null) {
    // [at]: the event count at a decision of the review; the line before it is where the game stood.
    val marked = at?.minus(1)?.takeIf { it in lines.indices }
    val ordered = remember(lines) { newestFirst(lines) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex =
            marked?.let { m -> ordered.indexOfFirst { it.index == m } }?.coerceAtLeast(0) ?: 0,
    )
    LazyColumn(
        modifier.fillMaxWidth(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(ordered, key = { it.index }) { (index, line) ->
            if (index == marked) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                ) {
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            } else if (line.heading) {
                Text(
                    line.text.uppercase(),
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = Spacing.screen)
                        .padding(top = Spacing.md)
                        .semantics { heading() },
                )
            } else {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            }
        }
    }
}

/**
 * What just happened: the latest events, newest last, the newest in full contrast; lines at
 * [strong] (indices into [lines]: a defeat, a push, a level up…) in full contrast and bold.
 */
@Composable
fun RecentEvents(lines: List<String>, modifier: Modifier = Modifier, strong: Set<Int> = emptySet()) {
    if (lines.isEmpty()) return
    val skip = (lines.size - RECENT).coerceAtLeast(0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.drop(skip).forEachIndexed { i, line ->
            val last = i == minOf(lines.size, RECENT) - 1
            val bold = (i + skip) in strong
            Row {
                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (bold) FontWeight.SemiBold else null,
                    color = MaterialTheme.colorScheme.run { if (last || bold) onSurface else onSurfaceVariant },
                )
            }
        }
    }
}

private const val RECENT = 4

/**
 * How much smaller than comfortable (38 dp) a hex may get so that every option of a question is in
 * view at once (about 30 dp: a Move 3's spaces under the turn track on a phone).
 */
private const val OPTIONS_SHRINK = 0.8f
private val SIDE_PANEL = 400.dp
private val PANEL_INSET = 12.dp
private val TALL_TABLE = 720.dp
private val TRACK_CARD_TALL = 64.dp
private val TRACK_CARD_SHORT = 50.dp

/** Small cards with their portrait and initiative: still a 48 dp target with the slot's padding. */
private val TRACK_CARD_COMPACT = 42.dp

/** A panel taller than this share of the table takes the turn track down to its small cards. */
private const val TALL_PANEL_SHARE = 0.36f

/** How much of what follows the question the panel shows, so it reads as more to scroll to. */
private val PANEL_PAST_QUESTION = 44.dp
private val HAND_SIDE_PANEL = 480.dp
private const val HAND_SIDE_SHARE = 0.5f
private val ZONE_HEX_TAP = 38.dp
private val ZONE_HEX_READ = 31.dp
private const val SQRT3 = 1.7320508f
private const val ZONE_MARGIN = 48f
private const val SIDE_SHARE = 0.42f
private const val PANEL_SHARE = 0.56f
