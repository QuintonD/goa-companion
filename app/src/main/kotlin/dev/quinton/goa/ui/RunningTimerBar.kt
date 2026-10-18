package dev.quinton.goa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.presentation.timer.RunningTimerUi

/**
 * Compact "timer running" bar above the bottom navigation on every screen but the timer:
 * phase, where in the round, and the clock. Tap returns to the timer.
 */
@Composable
fun RunningTimerBar(state: RunningTimerUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = when {
        state.overtime -> MaterialTheme.colorScheme.error
        state.paused -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondary
    }
    Column(modifier) {
        EngravedRule()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = state.description
                    role = Role.Button
                    onClick(label = "Open the timer") {
                        onClick()
                        true
                    }
                },
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (state.paused) GoaIcons.NavTimer else GoaIcons.NavTimerSelected),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.paused) "${state.phase} · Paused" else state.phase,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.context,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(state.clock, style = GoaTextStyles.timerDigitsSmall, color = accent, maxLines = 1)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
