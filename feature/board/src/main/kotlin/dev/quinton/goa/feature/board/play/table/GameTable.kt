package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.board.MapToolbar
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.ui.FitWhileHidden
import dev.quinton.goa.feature.board.ui.HideablePanel
import dev.quinton.goa.feature.board.ui.PanelCover
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.feature.board.ui.PanelStripHeight
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.cards.CardFocusState

/**
 * Where the question ends in the panel's content, px, reported by [DecisionPanel]; null when none is asked. The panel
 * stops a little past it, so what follows (what happened, the hands) doesn't take the map's room: it scrolls.
 */
internal val LocalPanelQuestionEnd = staticCompositionLocalOf<((Int?) -> Unit)?> { null }

/**
 * The question's map options as a lettered list (sol #2, sol-b #1): [open] when the panel lists every option by its
 * letter and the map tags each with it; [toggle] opens or closes it (the panel's "List" chip). Always offered while a
 * question is answered on the map; opened by itself when the framing leaves its spaces smaller than a finger.
 */
@Immutable
internal class MapList(val open: Boolean, val toggle: () -> Unit)

internal val LocalMapList = compositionLocalOf { MapList(false) {} }

/**
 * The room a question has in the panel, for a question that scrolls a part of itself so its commit stays in view
 * (Defend's list of cards): [height] is the most it may take from the top of the panel's content, [contentTop] where
 * that content is in the root, px (it moves as the panel scrolls, as the question does).
 */
@Stable
internal class QuestionRoom(val height: Dp, val contentTop: State<Float>)

internal val LocalQuestionRoom = compositionLocalOf<QuestionRoom?> { null }

/**
 * The game table: the map full screen (the Lane up the screen in portrait, left to right in
 * landscape), small widgets floating over it (ADR-083: the score and the heroes at the top start, the map's
 * controls and its key on demand at the top end), and [panel] (the question, the computer's move, the coach…) at the
 * bottom in portrait or on the right in landscape. The map frames the Lane between them and brings the spaces asked
 * for into view. Sideways with the panel shown, the heroes are a narrow column at the map's start edge
 * ([SIDE_CHIPS]), beside the map rather than over it.
 *
 * The panel hides ([HideablePanel]) to show the whole board: then it is [peek]'s one line (with
 * [peekTrailing], e.g. Confirm), only the score (and the turn's heroes after the reveal) stays at the top, and the
 * map fits the whole board unless the person has moved it. Taps on the map keep selecting while it is hidden.
 * Hidden or shown is kept for the screen (and survives rotation); [initialPanelHidden] for previews.
 * [onSpellbook] adds a Spellbook control per hero the person controls that has one (Gydion, ADR-040).
 * [panelTitle] goes on the panel's top strip, pinned as it scrolls ([DecisionWho]: who is asked).
 * [feed] floats under the widgets, beside the map buttons ([EventFeed]: what just happened).
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
    /** Pinned at the foot of the shown panel, under what scrolls: the hand's tab ([HandTab]). */
    panelFooter: (@Composable () -> Unit)? = null,
    /**
     * The panel's peek while a space or piece is chosen on the map, upright ([BoardPeek]: the commit): the header and
     * this, the rest one drag or tap up. Null: the panel is always whole.
     */
    peekBody: (@Composable ColumnScope.() -> Unit)? = null,
    panel: @Composable ColumnScope.() -> Unit,
) {
    var hidden by rememberSaveable { mutableStateOf(initialPanelHidden) }
    // Where the table and its map buttons are on the screen: the Zone labels keep clear of the buttons.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var buttons by remember { mutableStateOf<Rect?>(null) }
    // The map's own controls alone (Fit, Flip): the views keep every piece clear of them.
    var toolbar by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val landscape = maxWidth > maxHeight
        val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
        val layout =
            remember(table.map, orientation, table.bottomTeam) { MapLayout(table.map, orientation, table.bottomTeam) }
        val lane = remember(layout, table.map) { layout.laneRegion(table.map, table.geometry) }
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
        // Sideways with the panel shown: the heroes (the turn's order after the reveal) in a narrow column at the map's
        // start edge, beside the map rather than over it, so the panel keeps its height for the question (ADR-083).
        val column = landscape && !hidden
        val mapStart = if (column) SIDE_CHIPS else 0.dp
        // A space chosen on the map: the panel starts at its peek (who, what, the commit), so the map has the room; the
        // whole panel is one drag or tap up, and each new question starts at the peek again. Pieces keep the whole
        // panel: their numbered list (distance, lane) is part of the decision.
        val boardChoice = table.asking && !table.handDecision &&
            table.spaceOptions.isNotEmpty() && table.pieceOptions.isEmpty()
        var expanded by rememberSaveable(table.decision?.id) { mutableStateOf(false) }
        // The list, opened or closed by the person for this question; null: as the framing left it (crowded or not).
        var listChoice by rememberSaveable(table.decision?.id) { mutableStateOf<Boolean?>(null) }
        val peeking = peekBody != null && boardChoice && !landscape && !expanded
        // Where the question ends in the panel: the panel needn't be taller than that and a look at what follows.
        var questionEnd by remember { mutableStateOf<Int?>(null) }
        // The hand's tab pinned under the question ([panelFooter]): the panel holds both.
        var footerHeight by remember { mutableIntStateOf(0) }
        // The panel's strip as drawn: the header's two lines make it taller than its 40 dp.
        var stripHeight by remember { mutableIntStateOf(0) }
        // The tab comes on top of the question's share: it never pushes the question's commit below the fold.
        val footer = with(density) { (if (panelFooter != null && !peeking) footerHeight else 0).toDp() } / maxHeight
        val panelShare = footer + (
            questionEnd?.let { end ->
                val strip = maxOf(PanelStripHeight, with(density) { stripHeight.toDp() })
                val height = with(density) { end.toDp() } + strip + PANEL_PAST_QUESTION
                // The whole question with its commit row, never cut at the screen's foot: up to [QUESTION_SHARE].
                (height / maxHeight).coerceAtMost(QUESTION_SHARE)
            } ?: PANEL_SHARE
            )

        // What a question may take of the panel: upright, up to [QUESTION_SHARE] with the strip and the room past it;
        // sideways, the side panel's height under its strip, above the hand's tab.
        val stripDp = maxOf(PanelStripHeight, with(density) { stripHeight.toDp() })
        val questionRoom = if (landscape) {
            maxHeight - stripDp - with(density) { footerHeight.toDp() } - Spacing.sm
        } else {
            maxHeight * QUESTION_SHARE - stripDp - PANEL_PAST_QUESTION
        }
        val contentTop = remember { mutableFloatStateOf(0f) }

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
        // No piece under the map's controls: the views are framed clear of them (opus #8). The reveal below runs again
        // when they move, so a framing animated before the controls were laid out is redone clear of them.
        val pieceCentres = remember(table.view, layout) { table.pieces.mapNotNull { it.at }.map(layout::center) }
        val mapStartPx = with(density) { mapStart.toPx() }
        val controls = toolbar?.translate(-Offset(origin.x + mapStartPx, origin.y))
        LaunchedEffect(controls, pieceCentres) { viewport.updateAvoid(controls, pieceCentres) }

        val decision = table.decision
        // The panel peeking or whole changes the room: what is asked about is framed again above it.
        val gesturesAtQuestion = remember(decision?.id, table.asking, peeking) { viewport.gestures }
        var crowdedFor by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(
            decision?.id,
            table.asking,
            peeking,
            viewport.unit,
            viewport.bottomInset,
            viewport.endInset,
            viewport.topInset,
            buttonsWidth,
            hidden,
            controls,
            pieceCentres,
        ) {
            viewport.updateAvoid(controls, pieceCentres)
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
                who.mapNotNull(table::at) + alsoAbout + listOfNotNull(table.hintAt)
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
            // Options answered on the map are framed as 48 dp targets when they fit (sol #1), else as large as fits.
            val target = with(density) { TOUCH_HEX.toPx() }.takeIf { onMap }
            viewport.reveal(
                framed,
                framed.center,
                shrink = OPTIONS_SHRINK,
                clearEnd = buttonsWidth.toFloat(),
                hexPx = target,
            )
            if (target != null && viewport.hexPx < target * CROWDED_BELOW &&
                table.spaceOptions.size in 2..MAX_AUTO_LISTED
            ) {
                crowdedFor = decision.id
            }
        }

        // Spaces the framing couldn't make finger targets are lettered on the map and listed in the panel (sol #1,
        // sol-b #2). Once for a question it stays (the list makes the panel taller, which only shrinks them further).
        val crowded = table.asking && crowdedFor != null && crowdedFor == decision?.id
        // Any question answered on the map can be listed (sol #2, sol-b #1): the panel's "List" chip.
        val onMap = table.asking && (table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty())
        val listed = onMap && (listChoice ?: crowded)
        val mapList = MapList(listed) {
            listChoice = !listed
            // Opened from the peek: the whole panel, so its rows are in view.
            if (!listed) expanded = true
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

        // The map's own origin on the screen: the map buttons' bounds in its coordinates.
        val startPx = with(density) { mapStart.toPx() }
        val mapOrigin = Offset(origin.x + startPx, origin.y)
        GameMapView(
            table,
            layout,
            viewport,
            onTap,
            Modifier.fillMaxSize().padding(start = mapStart),
            lane = lane,
            onLongPressHero = onHero,
            buttons = buttons?.translate(-mapOrigin),
            listed = listed,
        )
        if (column) {
            Box(Modifier.fillMaxHeight().width(SIDE_CHIPS).padding(Spacing.xs)) {
                TurnChips(table, onHero, focus, vertical = true)
            }
        }

        // The map's key, on demand (ADR-083): lit spaces speak for themselves; the key is a tap on the map's controls.
        val keyItems = mapKeyItems(table, listed)
        var keyOpen by rememberSaveable(decision?.id) { mutableStateOf(false) }
        val mapControls: @Composable () -> Unit = {
            Column(
                Modifier
                    .onSizeChanged { buttonsWidth = it.width + with(density) { Spacing.sm.roundToPx() } }
                    .onGloballyPositioned { buttons = it.boundsInRoot() },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                MapToolbar(
                    viewport,
                    table.bottomTeam,
                    onFlip,
                    Modifier.onGloballyPositioned { toolbar = it.boundsInRoot() },
                    // Glyphs only, a small dock rather than a floating panel (opus #1, sol-b #2): the long press's
                    // tooltip and TalkBack keep the words.
                    compact = true,
                    buttonSize = MAP_BUTTON,
                    extra = {
                        if (keyItems.isNotEmpty()) MapKeyToggle(keyOpen, MAP_BUTTON) { keyOpen = !keyOpen }
                    },
                )
                // Gydion's spellbook, one tap at any point of the game (ADR-040): under the map buttons,
                // so it never covers the round at a glance or the turn's heroes.
                if (onSpellbook != null) {
                    table.spellbookSeats.forEach { id ->
                        SpellbookButton(table, id, onClick = { onSpellbook(id) })
                    }
                }
            }
        }
        // What floats under the widgets: the key when asked for, then the feed of what just happened.
        val under: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (keyOpen && keyItems.isNotEmpty()) MapKey(keyItems)
                feed?.invoke()
            }
        }
        // The heroes at the top: after the reveal always (ADR-041); before it, with the panel shown.
        val chips = table.track != null || !hidden
        val measureTop = Modifier.onSizeChanged { topHeight = it.height + with(density) { Spacing.sm.roundToPx() } }
        // Upright (and sideways with the panel hidden) the score and the heroes are two small plates at the top start,
        // side by side when they fit; the map's controls at the top end (ADR-083). Sideways with the panel shown, the
        // heroes are the column at the map's edge and the score floats alone.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = mapStart, end = overlayEnd)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Column(measureTop, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    topStrip?.invoke()
                    BesideOrBelow(gap = Spacing.xs) {
                        ScoreWidget(table)
                        if (chips && !column) TurnChips(table, onHero, focus)
                    }
                }
                under()
            }
            mapControls()
        }

        CompositionLocalProvider(LocalPanelPeeking provides peeking, LocalMapList provides mapList) {
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
                peekBody = peekBody?.takeIf { boardChoice },
                collapsed = peeking,
                onCollapsedChange = { expanded = !it },
                onStrip = { stripHeight = it },
            ) {
                CompositionLocalProvider(
                    LocalPanelQuestionEnd provides { questionEnd = it },
                    LocalQuestionRoom provides QuestionRoom(questionRoom, contentTop),
                ) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(panelScroll)
                            // Where the content is as it scrolls: a question measures its own place in it.
                            .onGloballyPositioned { contentTop.floatValue = it.positionInRoot().y }
                            .padding(
                                start = PANEL_INSET,
                                end = PANEL_INSET,
                                bottom = if (landscape) Spacing.sm else Spacing.md,
                            ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        panel()
                    }
                }
                if (panelFooter != null) Box(Modifier.onSizeChanged { footerHeight = it.height }) { panelFooter() }
            }
        }
    }
}

/**
 * Two widgets side by side when both fit the width, else the second under the first (and as wide as the width allows,
 * scrolling inside itself).
 */
@Composable
internal fun BesideOrBelow(modifier: Modifier = Modifier, gap: Dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { items, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val first = items.getOrNull(0)?.measure(loose)
        val second = items.getOrNull(1)
        val firstWidth = first?.width ?: 0
        val beside = second != null &&
            firstWidth + gapPx + second.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val placed = second?.measure(
            if (beside) loose.copy(maxWidth = constraints.maxWidth - firstWidth - gapPx) else loose,
        )
        val width = when {
            placed == null -> firstWidth
            beside -> firstWidth + gapPx + placed.width
            else -> maxOf(firstWidth, placed.width)
        }
        val height = when {
            placed == null -> first?.height ?: 0
            beside -> maxOf(first?.height ?: 0, placed.height)
            else -> (first?.height ?: 0) + gapPx + placed.height
        }
        layout(width, height) {
            first?.place(0, if (beside) (height - first.height) / 2 else 0)
            if (placed != null) {
                if (beside) {
                    placed.place(firstWidth + gapPx, (height - placed.height) / 2)
                } else {
                    placed.place(0, (first?.height ?: 0) + gapPx)
                }
            }
        }
    }
}

/**
 * The map key's control on the map's own controls (ADR-083): an info glyph that shows the key under the widgets and
 * hides it again (bronze while shown). Only while a question lights the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapKeyToggle(open: Boolean, size: Dp, onToggle: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Start),
        tooltip = { PlainTooltip { Text("Map key") } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(size)
                .semantics { stateDescription = if (open) "Shown" else "Hidden" },
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Map key",
                tint = if (open) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                modifier = Modifier.size(minOf(24.dp, size / 2)),
            )
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
                TurnTrackRow(table, track, onHero, focus = focus, compact = true, stretch = false)
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
    ) { Text(confirmLabel(table, selected), maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

/** The column of hero chips at the map's start edge sideways (ADR-083). */
private val SIDE_CHIPS = 88.dp

/** The map's own controls on the game table, drawn smaller than a finger; Compose widens their touch area. */
private val MAP_BUTTON = 40.dp

/**
 * How much smaller than comfortable (38 dp) a hex may get so that every option of a question is in
 * view at once (about 30 dp: a Move 3's spaces under the turn track on a phone).
 */
private const val OPTIONS_SHRINK = 0.8f
private val SIDE_PANEL = 400.dp
private val PANEL_INSET = 12.dp

/** Room under the question before the panel ends: the rest (what happened) scrolls, never shown cut at the foot. */
private val PANEL_PAST_QUESTION = 12.dp
private val HAND_SIDE_PANEL = 480.dp
private const val HAND_SIDE_SHARE = 0.5f
private val ZONE_HEX_TAP = 38.dp

/** Spaces framed smaller than this share of [TOUCH_HEX] (about 40 dp) are lettered and listed as well. */
private const val CROWDED_BELOW = 0.83f

/** The list opens by itself for at most this many crowded spaces; more stay on the map until "List" is tapped. */
private const val MAX_AUTO_LISTED = 12

/** A space answered on the map is framed at least this wide when the options fit: a finger's target (sol #1). */
internal val TOUCH_HEX = 48.dp
private val ZONE_HEX_READ = 31.dp
private const val SQRT3 = 1.7320508f
private const val ZONE_MARGIN = 48f
private const val SIDE_SHARE = 0.42f
private const val PANEL_SHARE = 0.48f

/** A question sized to its end may take this much, so its commit row is whole (a defence under the two-line header). */
private const val QUESTION_SHARE = 0.56f
