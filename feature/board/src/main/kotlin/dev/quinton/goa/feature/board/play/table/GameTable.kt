package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.MapButton
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import kotlinx.coroutines.launch

/**
 * The game table: the map full screen (the Lane up the screen in portrait, left to right in
 * landscape), the round at a glance and every hero's status floating at the top, Fit and Flip at
 * the right, and [panel] (the question, the computer's move, the coach…) at the bottom in portrait
 * or on the right in landscape. The map frames the Lane between them and brings the spaces asked
 * for into view.
 */
@Composable
fun GameTable(
    table: TableUi,
    viewport: MapViewport,
    onTap: (TableTap) -> Unit,
    onHero: (String) -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
        val layout = remember(table.map, orientation, table.bottomTeam) { MapLayout(table.map, orientation, table.bottomTeam) }
        val lane = remember(layout, table.map) { layout.laneRegion(table.map, table.geometry) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var panelSize by remember { mutableIntStateOf(0) }
        var topHeight by remember { mutableIntStateOf(0) }
        val sideWidth: Dp = if (landscape) minOf(maxWidth * SIDE_SHARE, SIDE_PANEL) else 0.dp
        val sidePx = with(density) { sideWidth.toPx() }

        LaunchedEffect(panelSize, topHeight, landscape, sidePx) {
            viewport.updateInsets(
                bottom = if (landscape) 0f else panelSize.toFloat(),
                end = if (landscape) sidePx else 0f,
                top = topHeight.toFloat(),
            )
        }
        // Bring what is asked for into view: the options, the unit they are about, the highlights.
        val decision = table.decision
        LaunchedEffect(decision?.id, table.asking, viewport.unit, viewport.bottomInset, viewport.endInset) {
            if (decision == null || viewport.unit == 0f) return@LaunchedEffect
            val hexes = table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at) +
                decision.context.highlight + listOfNotNull(table.subject?.let(table::at))
            val region = layout.bounds(hexes, pad = 1.1f) ?: return@LaunchedEffect
            viewport.reveal(region, region.center)
        }

        GameMapView(table, layout, viewport, onTap, Modifier.fillMaxSize(), lane = lane)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = sideWidth)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                Modifier.onSizeChanged { topHeight = it.height + with(density) { Spacing.sm.roundToPx() } },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TableHud(table)
                HeroStrip(table, onHero)
            }
            Column(Modifier.align(Alignment.End), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (viewport.isFit) {
                    MapButton(Icons.Outlined.ZoomIn, "Zoom to the lane") { scope.launch { viewport.animateLane() } }
                } else {
                    MapButton(Icons.Outlined.CenterFocusStrong, "Fit the whole board") {
                        scope.launch { viewport.animateFit() }
                    }
                }
                MapButton(Icons.Outlined.SwapVert, "Flip: ${BoardText.team(table.bottomTeam.opponent)} at the bottom", onFlip)
            }
        }

        val panelModifier = if (landscape) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(sideWidth)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * PANEL_SHARE)
        }
        Surface(
            modifier = panelModifier.onSizeChanged { panelSize = if (landscape) it.width else it.height },
            shape = if (landscape) {
                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            },
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = panel,
            )
        }
    }
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
                Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.screen))
            }
        }
    }
}

/** What just happened: the latest events, newest last, the newest in full contrast. */
@Composable
fun RecentEvents(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.takeLast(RECENT).forEachIndexed { i, line ->
            val last = i == minOf(lines.size, RECENT) - 1
            Row {
                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
private val SIDE_PANEL = 400.dp
private const val SIDE_SHARE = 0.46f
private const val PANEL_SHARE = 0.56f
