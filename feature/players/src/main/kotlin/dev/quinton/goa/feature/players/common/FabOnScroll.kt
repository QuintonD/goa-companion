package dev.quinton.goa.feature.players.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * An extended FAB that shrinks to its icon while the list underneath scrolls down and grows back
 * when it scrolls up, so it doesn't sit over a card's rows (QA: "Log match" covered the Blue
 * team at large text). Attach [connection] with `Modifier.nestedScroll` around the list.
 */
@Stable
class FabExpansion {
    var expanded by mutableStateOf(true)
        private set

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            when {
                available.y < -SCROLL_SLOP -> expanded = false
                available.y > SCROLL_SLOP -> expanded = true
            }
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_SLOP = 1f
    }
}

@Composable
fun rememberFabExpansion(): FabExpansion = remember { FabExpansion() }

/** "Log match" on the match log; icon-only while scrolling down ([FabExpansion]). */
@Composable
fun LogMatchFab(onClick: () -> Unit, expanded: Boolean) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        text = { Text("Log match") },
        // M3 1.4 leaves the extended FAB's text out of the semantics tree.
        modifier = Modifier.semantics { contentDescription = "Log match" },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}
