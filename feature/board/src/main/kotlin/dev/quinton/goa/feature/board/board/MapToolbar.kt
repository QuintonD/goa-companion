package dev.quinton.goa.feature.board.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaDarkTheme
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.ui.BarLabelButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The map's own controls, docked as one slim steel pill at the map's end edge (opus #1, sol-b #2): "Fit board" (or
 * "Lane view" once the whole board is shown) and "Flip view", glyphs only at 48 dp each, with the words in a
 * long-press tooltip and for TalkBack, so they read as map furniture rather than a floating panel ([compact] false:
 * each glyph over its name). The board and the table frame the map so no
 * piece stands under it ([MapViewport.updateAvoid]). A flip says what it did for a moment beside the column ("Blue at
 * the bottom"), and TalkBack hears it; the note never changes the column's size, so the map isn't re-framed for it.
 */
@Composable
internal fun MapToolbar(
    viewport: MapViewport,
    bottomTeam: Team,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    /** The glyphs alone, 48 dp each; the words stay in the tooltip and for TalkBack. */
    compact: Boolean = true,
    /**
     * A compact button's size: the game table's are 40 dp (ADR-083), drawn smaller over the map while a touch still
     * finds them within 48 dp (Compose widens a small target's touch area).
     */
    buttonSize: Dp = 48.dp,
    /** More of the map's controls under these, on the same pill (the game table's key, ADR-083). */
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var flips by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf(false) }
    LaunchedEffect(flips) {
        if (flips == 0) return@LaunchedEffect
        notice = true
        delay(FLIP_NOTICE_MS)
        notice = false
    }
    GoaDarkTheme {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Layout(
                content = {
                    Column(
                        Modifier
                            .slatePlate(RoundedCornerShape(if (compact) buttonSize / 2 else 12.dp), brackets = false)
                            .padding(vertical = if (compact) 0.dp else 4.dp, horizontal = if (compact) 0.dp else 2.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
                    ) {
                        if (viewport.isFit) {
                            MapTool("Lane view", Icons.Outlined.ZoomIn, "Zoom to the lane", compact, buttonSize) {
                                scope.launch { viewport.animateLane() }
                            }
                        } else {
                            MapTool(
                                "Fit board",
                                Icons.Outlined.CenterFocusStrong,
                                "Fit the whole board",
                                compact,
                                buttonSize,
                            ) {
                                scope.launch { viewport.animateFit() }
                            }
                        }
                        MapTool(
                            "Flip view",
                            Icons.Outlined.SwapVert,
                            "Flip the view: ${BoardText.team(bottomTeam.opponent)} at the bottom",
                            compact,
                            buttonSize,
                        ) {
                            onFlip()
                            flips++
                        }
                        extra()
                    }
                    AnimatedVisibility(notice, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            "View flipped · ${BoardText.team(bottomTeam)} at the bottom",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .slatePlate(RoundedCornerShape(8.dp), brackets = false)
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
                modifier = modifier,
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val bar = measurables[0].measure(loose)
                val note = measurables.getOrNull(1)?.measure(loose.copy(maxWidth = NOTE_MAX_WIDTH.roundToPx()))
                // Only the column counts: the note floats beside it, level with Flip, outside the column's bounds.
                layout(bar.width, bar.height) {
                    bar.place(0, 0)
                    note?.place(-note.width - Spacing.sm.roundToPx(), bar.height - note.height)
                }
            }
        }
    }
}

/** One control: its glyph over its word, or (compact) the glyph alone with the word in its tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTool(
    label: String,
    icon: ImageVector,
    description: String,
    compact: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    if (!compact) {
        BarLabelButton(
            label,
            icon,
            onClick = onClick,
            description = description,
            modifier = Modifier.widthIn(min = TOOL_WIDTH),
            labelled = true,
        )
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Start),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(minOf(24.dp, size / 2)))
        }
    }
}

private val TOOL_WIDTH = 60.dp
private val NOTE_MAX_WIDTH = 260.dp
private const val FLIP_NOTICE_MS = 1800L
