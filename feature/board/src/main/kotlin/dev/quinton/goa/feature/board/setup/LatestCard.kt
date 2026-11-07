package dev.quinton.goa.feature.board.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.list.LineupHeroes
import dev.quinton.goa.feature.board.ui.PlateSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The newest finished draft or logged match, with its line-up: "Open in sandbox" opens its board in one tap
 * (Boards and New board). [onEdit] (New board) fills the teams with it instead, to change them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LatestCard(
    source: LineupSource,
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val names = listOf(source.red, source.blue).filter { it.isNotEmpty() }
        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } }
    val what = "Latest ${source.kind.label.lowercase()}"
    PlateSurface(brackets = true, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "$what, ${day(source.at)}: $names" },
            ) {
                // One line ("LATEST DRAFT · 19 OCT"); when the button leaves too little room the date takes the next
                // line, without the dot, rather than being cut.
                var wraps by remember(what, source.at) { mutableStateOf(false) }
                Text(
                    what.uppercase() + (if (wraps) "\n" else " · ") + day(source.at),
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.primary,
                    onTextLayout = { if (!wraps && it.lineCount > 1) wraps = true },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    LineupHeroes(Team.RED, source.red, size = 26.dp)
                    if (source.red.isNotEmpty() && source.blue.isNotEmpty()) {
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LineupHeroes(Team.BLUE, source.blue, size = 26.dp)
                }
                Text(
                    names,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GoaButton(
                    onClick = onSetUp,
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Open the latest line-up in the sandbox" },
                ) { Text("Open in sandbox") }
                if (onEdit != null) {
                    TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 40.dp)) { Text("Edit") }
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

internal fun day(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayFormat)
