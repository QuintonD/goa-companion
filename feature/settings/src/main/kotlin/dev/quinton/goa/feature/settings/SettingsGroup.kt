package dev.quinton.goa.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.PlateStyle
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.Spacing

/**
 * A secondary group of settings: the design system's secondary plate (`PlateStyle.Secondary`: flat, a quiet hairline,
 * no brackets), which leaves the full steel edge and teal brackets to a screen's main content. One group per section,
 * rows divided by [GroupDivider], never a framed plate inside.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.xs),
    content: @Composable ColumnScope.() -> Unit,
) {
    GoaPlateTheme {
        Column(
            modifier.fillMaxWidth().slatePlate(GroupShape, style = PlateStyle.Secondary).padding(contentPadding),
            content = content,
        )
    }
}

/** A quiet hairline between the rows of a [SettingsGroup], inset to the text. */
@Composable
internal fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA),
    )
}

private val GroupShape = RoundedCornerShape(12.dp)
private const val DIVIDER_ALPHA = 0.1f
