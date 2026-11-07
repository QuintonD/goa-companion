package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/*
 * The game's own controls (DESIGN.md "Controls"): drawn once here, so every screen's switches, segmented bars, chips
 * and secondary buttons are the same steel and bronze as the plates around them, never stock Material shapes.
 *  - A segmented bar is a recessed steel track; the selected segment is a bronze inset (wash, edge, check).
 *  - A switch's off track is a recess with a steel edge and a light thumb; on, the track is bronze, the thumb
 *    parchment with a check.
 *  - A chip is a small steel plate with a bevelled edge (lit top, shaded foot); selected, bronze ([Selection.kt]).
 *  - A secondary button is a slate plate with a steel bevel ([GoaOutlinedButton]).
 */

/** The steel a control's edge is made of, per theme: its lit top and its shaded foot, and the recess it frames. */
@Immutable
class ControlSteel internal constructor(val lit: Color, val shade: Color, val recess: Color, val face: Color) {
    /** The bevel: lit along the top, shaded along the foot. */
    fun bevel(): Brush = Brush.verticalGradient(listOf(lit, shade))

    companion object {
        internal val Dark = ControlSteel(
            lit = Color(0xFF9AA7AF),
            shade = Color(0xFF63707A),
            recess = Color(0xFF11161A),
            face = Color(0xFF27313A),
        )
        internal val Light = ControlSteel(
            lit = Color(0xFF737D84),
            shade = Color(0xFF4E585F),
            recess = Color(0xFFE6DFD1),
            face = Color(0xFFFCFAF4),
        )

        /** The steel for the current theme. */
        val current: ControlSteel
            @Composable @ReadOnlyComposable
            get() = if (GoaTheme.isDark) Dark else Light
    }
}

/**
 * The app's switch: off, a recessed track with a steel edge and a light thumb; on, a bronze track with a parchment
 * thumb carrying a check, so on and off differ by shape as well as colour. Same parameters as Material's `Switch`
 * (pass `onCheckedChange = null` when the whole row toggles it).
 */
@Composable
fun GoaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val steel = ControlSteel.current
    val bronze = GoaTheme.colors.selection
    val parchment = SWITCH_PARCHMENT
    val offThumb = if (GoaTheme.isDark) Color(0xFFE2E7EA) else Color(0xFF4E585F)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = parchment,
            checkedTrackColor = bronze,
            checkedBorderColor = bronze,
            checkedIconColor = SWITCH_CHECK_INK,
            uncheckedThumbColor = offThumb,
            uncheckedTrackColor = steel.recess,
            uncheckedBorderColor = steel.lit,
            uncheckedIconColor = steel.recess,
            disabledCheckedThumbColor = parchment.copy(alpha = DISABLED_ALPHA),
            disabledCheckedTrackColor = bronze.copy(alpha = DISABLED_ALPHA),
            disabledCheckedBorderColor = bronze.copy(alpha = DISABLED_ALPHA),
            disabledCheckedIconColor = SWITCH_CHECK_INK.copy(alpha = DISABLED_ALPHA),
            disabledUncheckedThumbColor = offThumb.copy(alpha = DISABLED_ALPHA),
            disabledUncheckedTrackColor = steel.recess.copy(alpha = DISABLED_ALPHA),
            disabledUncheckedBorderColor = steel.lit.copy(alpha = DISABLED_ALPHA),
        ),
    )
}

/** Where the segments of a [GoaSegmentedRow] go: each takes an equal share of the bar. */
interface GoaSegmentedRowScope : RowScope

private class SegmentedScopeImpl(row: RowScope) :
    GoaSegmentedRowScope,
    RowScope by row

/**
 * A segmented bar, one of a few (Theme, Length, 1v1 / 2v2 / 3v3): a recessed steel track with a bevelled edge, its
 * segments separated by steel hairlines; the selected one is a bronze inset ([GoaSegment]). Use it instead of
 * Material's `SingleChoiceSegmentedButtonRow`; TalkBack reads it as one group of radio buttons.
 */
@Composable
fun GoaSegmentedRow(modifier: Modifier = Modifier, content: @Composable GoaSegmentedRowScope.() -> Unit) {
    val steel = ControlSteel.current
    val shadow = if (GoaTheme.isDark) RECESS_SHADOW else 0f
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = SEGMENT_HEIGHT)
            .drawWithCache {
                val radius = CornerRadius(SEGMENT_RADIUS.toPx())
                val edge = 1.dp.toPx()
                val bevel = steel.bevel()
                val inner = Size(size.width - edge, size.height - edge)
                onDrawBehind {
                    drawRoundRect(steel.recess, cornerRadius = radius)
                    // The recess's shadow under its top edge (on the slate only), then the steel rim.
                    if (shadow > 0f) {
                        drawLine(
                            Color.Black.copy(alpha = shadow),
                            Offset(radius.x, edge * 2f),
                            Offset(size.width - radius.x, edge * 2f),
                            strokeWidth = edge * 2f,
                        )
                    }
                    drawRoundRect(
                        bevel,
                        topLeft = Offset(edge / 2f, edge / 2f),
                        size = inner,
                        cornerRadius = radius,
                        style = Stroke(edge),
                    )
                }
            }
            .padding(horizontal = SEGMENT_INSET)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedScopeImpl(this).content()
    }
}

/**
 * One segment of a [GoaSegmentedRow]. Selected, a bronze inset: the bronze wash and edge with a leading check
 * ([icon], by default the check when selected; pass `{}` for none). [label] is the segment's words.
 */
@Composable
fun GoaSegmentedRowScope.GoaSegment(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit = { GoaSegmentCheck(selected) },
    label: @Composable () -> Unit,
) {
    val colors = GoaTheme.colors
    val steel = ControlSteel.current
    val ink = MaterialTheme.colorScheme.onSurface
    val wash = colors.selectionFill.compositeOver(steel.face)
    Row(
        modifier
            .weight(1f)
            .fillMaxHeight()
            // The whole bar's height is the 48 dp target; the bronze inset is drawn inside it.
            .heightIn(min = SEGMENT_HEIGHT)
            .drawWithCache {
                val radius = CornerRadius((SEGMENT_RADIUS - SEGMENT_INSET).toPx())
                val edge = SelectedEdge.toPx()
                val inset = SEGMENT_INSET.toPx()
                val inner = Size(size.width, size.height - inset * 2)
                onDrawBehind {
                    if (selected) {
                        drawRoundRect(wash, topLeft = Offset(0f, inset), size = inner, cornerRadius = radius)
                        drawRoundRect(
                            colors.selection,
                            topLeft = Offset(edge / 2f, inset + edge / 2f),
                            size = Size(inner.width - edge, inner.height - edge),
                            cornerRadius = radius,
                            style = Stroke(edge),
                        )
                    }
                }
            }
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .disabled(enabled)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)) {
                icon()
                label()
            }
        }
    }
}

/** A selected segment's leading check in bronze; nothing when not selected. */
@Composable
fun GoaSegmentCheck(selected: Boolean) {
    if (selected) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = GoaTheme.colors.bronzeInk,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.4f)),
        )
        Spacer(Modifier.width(Spacing.xs))
    }
}

/**
 * The steel a slider's or progress bar's remainder runs on: neutral, so bronze reads as "filled / chosen" and teal
 * stays for "next / correct / can act" (DESIGN.md "Colour roles").
 */
val ControlSteel.track: Color get() = if (this === ControlSteel.Light) TRACK_LIGHT else TRACK_DARK

/**
 * The app's slider: the chosen part and the thumb bronze, the remainder a neutral steel track, steps as small ticks.
 * Same parameters as Material's `Slider`.
 */
@Composable
fun GoaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val bronze = GoaTheme.colors.selection
    val steel = ControlSteel.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = bronze,
            activeTrackColor = bronze,
            inactiveTrackColor = steel.track,
            activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = TICK_ALPHA),
            inactiveTickColor = steel.lit,
        ),
    )
}

/**
 * The app's progress bar: bronze for what is done on a neutral steel track. With [progress] null it runs
 * indeterminate (working, no known end). Teal never means "progress": it is for next, correct, can act.
 */
@Composable
fun GoaProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier) {
    val bronze = GoaTheme.colors.selection
    val track = ControlSteel.current.track
    if (progress == null) {
        LinearProgressIndicator(modifier, color = bronze, trackColor = track)
    } else {
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = bronze,
            trackColor = track,
            drawStopIndicator = {},
        )
    }
}

private val TRACK_DARK = Color(0xFF3A464E)
private val TRACK_LIGHT = Color(0xFFD8CFBF)
private const val TICK_ALPHA = 0.6f
private val SEGMENT_HEIGHT = 48.dp
private val SEGMENT_INSET = 3.dp
private val SEGMENT_RADIUS = 10.dp
private const val RECESS_SHADOW = 0.3f
private val SWITCH_PARCHMENT = Color(0xFFF3EBDA)
private val SWITCH_CHECK_INK = Color(0xFF5A3E0C)
