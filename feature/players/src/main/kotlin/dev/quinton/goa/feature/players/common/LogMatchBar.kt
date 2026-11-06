package dev.quinton.goa.feature.players.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.Spacing

/**
 * The match log's main action, docked under the list on a slate bar (as trivia's Start bar) instead
 * of floating over it: a floating button always covered some card's deltas, and at large text a
 * rating (QA, UI hill climb). The list ends above the bar, so nothing is ever under it.
 */
@Composable
fun LogMatchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GoaPlateTheme {
        Column(modifier.fillMaxWidth().slatePlate(RectangleShape, brackets = false)) {
            GoaButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                    .heightIn(min = 52.dp)
                    .semantics { contentDescription = "Log match" },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Log match", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
