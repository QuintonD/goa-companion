package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import kotlinx.coroutines.launch

/**
 * The game table: the map full screen (the Lane up the screen in portrait, left to right in
 * landscape), the round at a glance and every hero's status floating at the top, Fit and Flip at
 * the right, and [panel] (the question, the computer's move, the coach…) at the bottom in portrait
 * or on the right in landscape. The map frames the Lane between them and brings the spaces asked
 * for into view. [banner] (a puzzle's goal) floats under the hero strip, and the map keeps clear of it;
 * it is told to be compact (one line) in landscape, where the map has little height. There the hero
 * strip is portraits only, beside the round at a glance, so the top keeps to about two rows.
 *
 * The panel hides ([HideablePanel]) to show the whole board: then it is [peek]'s one line (with
 * [peekTrailing], e.g. Confirm), only the round at a glance stays at the top, and the map fits the
 * whole board unless the person has moved it. Taps on the map keep selecting while it is hidden.
 * Hidden or shown is kept for the screen (and survives rotation); [initialPanelHidden] for previews.
 * [onSpellbook] adds a Spellbook control per hero the person controls that has one (Gydion, ADR-040).
 */
@Composable
fun GameTable(
    table: TableUi,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    onHero: (String) -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    banner: (@Composable (compact: Boolean) -> Unit)? = null,
    peek: PanelPeek = PanelPeek(""),
    peekTrailing: (@Composable () -> Unit)? = null,
    initialPanelHidden: Boolean = false,
    focus: CardFocusState? = null,
    onSpellbook: ((String) -> Unit)? = null,
    panel: @Composable ColumnScope.() -> Unit,
) {
    var hidden by rememberSaveable { mutableStateOf(initialPanelHidden) }
    BoxWithConstraints(modifier.fillMaxSize()) {
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
        val sideWidth: Dp = if (landscape) minOf(maxWidth * SIDE_SHARE, SIDE_PANEL) else 0.dp
        // The floating overlays keep clear of the side panel only while it is shown.
        val overlayEnd = if (landscape && !hidden) sideWidth else 0.dp

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
            val hexes = table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at) +
                decision.context.highlight + listOfNotNull(table.subject?.let(table::at)) +
                who.mapNotNull(table::at)
            val region = layout.bounds(hexes, pad = 1.1f) ?: return@LaunchedEffect
            viewport.reveal(region, region.center, shrink = OPTIONS_SHRINK, clearEnd = buttonsWidth.toFloat())
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

        GameMapView(table, layout, viewport, onTap, Modifier.fillMaxSize(), lane = lane, onLongPressHero = onHero)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = overlayEnd)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                Modifier.onSizeChanged { topHeight = it.height + with(density) { Spacing.sm.roundToPx() } },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // After the reveal the turn's cards stay in view, the panel shown or hidden (ADR-041);
                // before it, the hero strip comes back with the panel (one tap).
                val track = table.track
                if (landscape && !hidden) {
                    TableTopRow(table, onHero, focus = focus)
                } else {
                    TableHud(table)
                    if (track != null) {
                        TurnTrackRow(table, track, onHero, focus = focus, compact = hidden)
                    } else if (!hidden) {
                        HeroStrip(table, onHero)
                    }
                }
                if (!hidden) banner?.invoke(landscape)
            }
            Column(
                Modifier
                    .align(Alignment.End)
                    .onSizeChanged { buttonsWidth = it.width + with(density) { Spacing.sm.roundToPx() } },
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

        HideablePanel(
            landscape = landscape,
            hidden = hidden,
            onHiddenChange = { hidden = it },
            peek = peek,
            sideWidth = sideWidth,
            maxShare = PANEL_SHARE,
            onCover = { cover = it },
            peekTrailing = peekTrailing,
        ) {
            Column(
                Modifier
                    .verticalScroll(panelScroll)
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = panel,
            )
        }
    }
}

/**
 * Landscape: the round at a glance and the heroes as portraits on one row (the portraits scroll
 * if they don't fit beside it); after the reveal, the turn's cards in their place.
 */
@Composable
internal fun TableTopRow(
    table: TableUi,
    onHero: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: CardFocusState? = null,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableHud(table, compact = true)
        val track = table.track
        if (track != null) {
            TurnTrackRow(table, track, onHero, Modifier.weight(1f, fill = false), focus = focus, compact = true)
        } else {
            HeroStrip(table, onHero, Modifier.weight(1f, fill = false), compact = true)
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
    Button(
        onClick = { onAnswer(selected) },
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .semantics { contentDescription = "Confirm · $label" },
    ) { Text("Confirm", maxLines = 1) }
}

/** The one line a hidden panel shows for the question: who is asked, and the question or the choice made. */
fun TableUi.questionPeek(): PanelPeek? {
    val d = decision ?: return null
    val name = decider?.let { hero(it.heroId).name } ?: BoardText.team(d.team)
    val chosen = selected?.takeIf { asking && (it is Choice.Space || it is Choice.Unit || it is Choice.Token) }
    return PanelPeek(
        text = chosen?.let(::label) ?: d.prompt,
        label = name,
        team = d.team,
        attention = asking,
    )
}

/** A line of the event log: a round or turn heading, or an event. */
@Immutable
data class LogLine(val text: String, val heading: Boolean = false)

/** The log from the engine's events, oldest first; rounds and turns as headings. */
fun logLines(events: List<GameEvent>): List<LogLine> = events.map { event ->
    LogLine(event.text, heading = event is GameEvent.RoundStarted || event is GameEvent.TurnStarted)
}

/** The event log (a sheet): every event of the game in the engine's words, newest at the bottom. */
@Composable
fun EventLog(lines: List<LogLine>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxWidth(),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(lines.asReversed()) { line ->
            if (line.heading) {
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

/** Selection helper for the screens: the choice a tap on the map stands for, if it is an option. */
fun TableUi.choiceFor(tap: TableTap): Choice? = when (tap) {
    is TableTap.Piece -> pieceOptions[tap.pieceId]
    is TableTap.Space -> spaceOptions[tap.hex]
}

private const val RECENT = 4

/**
 * How much smaller than comfortable (38 dp) a hex may get so that every option of a question is in
 * view at once (about 30 dp: a Move 3's spaces under the turn track on a phone).
 */
private const val OPTIONS_SHRINK = 0.8f
private val SIDE_PANEL = 400.dp
private const val SIDE_SHARE = 0.42f
private const val PANEL_SHARE = 0.56f
