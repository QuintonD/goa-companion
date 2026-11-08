package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One of the pieces or spaces a single tap could have meant, as the chooser lists it. */
@Immutable
internal data class ChooserEntry(
    val label: String,
    val onClick: () -> Unit,
    /** Leads the row: an option's letter while the list is open, a piece's look… */
    val leading: (@Composable () -> Unit)? = null,
)

/** Where a tap landed (screen px within the map) and what it could have meant. */
@Immutable
internal data class MapChoices(val at: Offset, val entries: List<ChooserEntry>)

/**
 * One tap on the map that hit several pieces or options (they stand closer together than a finger): a small menu at the
 * finger asks "Which one?", a 48 dp row each, rather than guessing or making the person zoom first (sol #2, sol-b #1).
 * Back or a tap outside closes it without choosing.
 */
@Composable
internal fun MapChooser(choices: MapChoices?, onDismiss: () -> Unit) {
    if (choices == null) return
    Box(Modifier.offset { IntOffset(choices.at.x.roundToInt(), choices.at.y.roundToInt()) }.size(1.dp)) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            Text(
                "Which one?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { heading() },
            )
            choices.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = entry.leading,
                    onClick = {
                        onDismiss()
                        entry.onClick()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
