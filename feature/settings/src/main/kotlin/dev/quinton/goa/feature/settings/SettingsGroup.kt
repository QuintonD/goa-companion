package dev.quinton.goa.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.theme.Spacing

/**
 * A section of settings on the section plate (steel edge, teal brackets), as every grouped section in the app is. Its
 * rows sit straight on it, divided by [GroupDivider]: never a framed plate inside (DESIGN.md "Framing levels").
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.xs),
    content: @Composable ColumnScope.() -> Unit,
) {
    GoaPlate(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        content = content,
    )
}

/** A quiet hairline between the rows of a [SettingsGroup], inset to the text. */
@Composable
internal fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA),
    )
}

private const val DIVIDER_ALPHA = 0.12f
