package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/**
 * A dialog on the dark plate, with `AlertDialog`'s slots. Use it instead of `AlertDialog`, so a dialog
 * belongs to the same world as the sheets, on both themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        GoaPlate(
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.lg),
        ) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = Spacing.md), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.primary,
                        content = icon,
                    )
                }
            }
            if (title != null) {
                Box(Modifier.padding(bottom = Spacing.md).semantics { heading() }) {
                    CompositionLocalProvider(
                        LocalContentColor provides GoaTheme.colors.heading,
                        LocalTextStyle provides GoaTextStyles.plateTitle.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        content = title,
                    )
                }
            }
            if (text != null) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        content = text,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/**
 * A title on the banner: as large as it fits, then smaller, then on two lines, and only then cut. A
 * name people navigate by isn't given up to the first few letters ("New …").
 */
@Composable
fun BannerTitle(text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val width = constraints.maxWidth
        val (style, lines) = remember(text, width, density, measurer) {
            fun fits(candidate: TextStyle, maxLines: Int) = width == Constraints.Infinity || !measurer.measure(
                text,
                candidate,
                constraints = Constraints(maxWidth = width),
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
                density = density,
            ).hasVisualOverflow
            val full = GoaTextStyles.bannerTitle
            val small = full.copy(fontSize = BANNER_SMALL.sp, lineHeight = (BANNER_SMALL + BANNER_LEAD).sp)
            val twoLines = full.copy(fontSize = BANNER_TWO_LINES.sp, lineHeight = (BANNER_TWO_LINES + BANNER_LEAD).sp)
            when {
                fits(full, 1) -> full to 1
                fits(small, 1) -> small to 1
                else -> twoLines to 2
            }
        }
        BevelText(text, style = style, maxLines = lines)
    }
}

private const val BANNER_SMALL = 17
private const val BANNER_TWO_LINES = 14
private const val BANNER_LEAD = 3
