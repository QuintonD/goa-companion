package dev.quinton.goa.feature.board.list

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText

/**
 * Learning by playing (ADR-035), above the boards: a game in progress to resume, "Play against the
 * computer" (coming soon until heroes are playable) and Puzzles ("N of M solved"; coming soon until
 * a puzzle's heroes are playable).
 */
@Composable
internal fun PlayHub(
    play: PlayHubUi,
    now: Long,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    onPuzzles: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        play.resume?.let { ResumeCard(it, now, onResume) }
        EntryCard(
            icon = Icons.Outlined.SportsEsports,
            title = "Play against the computer",
            text = if (play.available) {
                "The real game on the map: the app runs every rule, the computer plays at a strength from 1 to 10, a coach nudges."
            } else {
                "Coming soon: heroes join as each of their cards is built and tested."
            },
            enabled = play.available,
            onClick = onPlay,
        )
        val puzzles = play.puzzles
        EntryCard(
            icon = Icons.Outlined.Extension,
            title = "Puzzles",
            text = if (puzzles != null) {
                "${puzzles.solved} of ${puzzles.total} solved · hard positions, gentle nudges, the rule behind each answer."
            } else {
                "Coming soon: hard positions to solve, with gentle nudges and the rule behind each answer."
            },
            enabled = puzzles != null,
            onClick = onPuzzles,
        )
    }
}

@Composable
private fun EntryCard(icon: ImageVector, title: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { if (!enabled) stateDescription = "Coming soon" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaledIconSize(28.dp, 1.4f)),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumeCard(resume: ResumeUi, now: Long, onResume: () -> Unit) {
    val names = listOf(resume.red, resume.blue).filter { it.isNotEmpty() }
        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "Game in progress" + (if (names.isNotEmpty()) ": $names. " else ". ") + resume.summary
                    },
            ) {
                Text(
                    if (resume.savedAt >
                        0
                    ) {
                        "GAME IN PROGRESS · ${BoardText.changed(
                            resume.savedAt,
                            now,
                        ).removePrefix("Changed ").uppercase()}"
                    } else {
                        "GAME IN PROGRESS"
                    },
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (resume.red.isNotEmpty() || resume.blue.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs),
                    ) {
                        LineupHeroes(Team.RED, resume.red, size = 26.dp)
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        LineupHeroes(Team.BLUE, resume.blue, size = 26.dp)
                    }
                }
                Text(
                    resume.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Button(
                onClick = onResume,
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (resume.unreadable) "Open" else "Resume")
            }
        }
    }
}
