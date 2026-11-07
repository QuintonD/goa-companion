package dev.quinton.goa.feature.board.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 * The map's own controls, docked as one compact steel column at the map's end edge (opus #8, sol #8, sol-b #21): "Fit
 * board" (or "Lane view" once the whole board is shown) and "Flip view", each its glyph over its name, as the game's
 * top bar, with the full words in a long-press tooltip and for TalkBack. The board and the table frame the map so no
 * piece stands under it ([MapViewport.updateAvoid]). A flip says what it did for a moment beside the column ("Blue at
 * the bottom"), and TalkBack hears it; the note never changes the column's size, so the map isn't re-framed for it.
 */
@Composable
internal fun MapToolbar(viewport: MapViewport, bottomTeam: Team, onFlip: () -> Unit, modifier: Modifier = Modifier) {
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
                            .slatePlate(RoundedCornerShape(12.dp), brackets = false)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (viewport.isFit) {
                            BarLabelButton(
                                "Lane view",
                                Icons.Outlined.ZoomIn,
                                onClick = { scope.launch { viewport.animateLane() } },
                                description = "Zoom to the lane",
                                modifier = Modifier.widthIn(min = TOOL_WIDTH),
                            )
                        } else {
                            BarLabelButton(
                                "Fit board",
                                Icons.Outlined.CenterFocusStrong,
                                onClick = { scope.launch { viewport.animateFit() } },
                                description = "Fit the whole board",
                                modifier = Modifier.widthIn(min = TOOL_WIDTH),
                            )
                        }
                        BarLabelButton(
                            "Flip view",
                            Icons.Outlined.SwapVert,
                            onClick = {
                                onFlip()
                                flips++
                            },
                            description = "Flip the view: ${BoardText.team(bottomTeam.opponent)} at the bottom",
                            modifier = Modifier.widthIn(min = TOOL_WIDTH),
                        )
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

private val TOOL_WIDTH = 60.dp
private val NOTE_MAX_WIDTH = 260.dp
private const val FLIP_NOTICE_MS = 1800L
