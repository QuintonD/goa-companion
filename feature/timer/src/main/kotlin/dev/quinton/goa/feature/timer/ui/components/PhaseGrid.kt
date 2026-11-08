package dev.quinton.goa.feature.timer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.TintTag
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.timer.PhaseDuration
import dev.quinton.goa.feature.timer.ui.timer.TonightUi

/**
 * A preset's phases as a labelled grid, two to a row: the phase's name over its length ("0:30 ×4", "0:30 per hero"),
 * never one line of dot-joined prose. Tonight's change ([tonight]) shows in bronze with a "Tonight" tag and the
 * preset's own length under it. [dense] is the presets list's smaller size (no tag; the start card carries tonight).
 */
@Composable
internal fun PhaseGrid(
    phases: List<PhaseDuration>,
    modifier: Modifier = Modifier,
    tonight: TonightUi? = null,
    dense: Boolean = false,
) {
    Column(modifier) {
        phases.chunked(COLUMNS).forEachIndexed { row, pair ->
            if (row > 0) {
                if (dense) Spacer(Modifier.height(Spacing.xs)) else PlateRowDivider()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEach { phase ->
                    PhaseCell(
                        phase,
                        tonight?.takeIf { phase.perTurn && it.adjusted && it.phaseName == phase.name },
                        dense,
                        Modifier.weight(1f),
                    )
                }
                // An odd last phase keeps its column's width, so the grid's columns stay aligned.
                if (pair.size < COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private const val COLUMNS = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseCell(phase: PhaseDuration, tonight: TonightUi?, dense: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val seconds = tonight?.seconds ?: phase.seconds
    val spoken = buildString {
        append(phase.name).append(", ")
        append(seconds?.let { TimerText.spokenDuration(it.toLong()) } ?: "untimed")
        phase.repeat?.let { append(", ").append(if (it.startsWith("×")) "${it.drop(1)} times a round" else it) }
        tonight?.let { append(", tonight; preset ").append(TimerText.spokenDuration(it.presetSeconds.toLong())) }
    }
    Column(
        modifier
            .padding(vertical = if (dense) 0.dp else Spacing.sm)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        // The tag goes under the name when both don't fit (large text), so the name never breaks word by word.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(phase.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (tonight != null) TonightTag()
        }
        val valueStyle = if (dense) GoaTextStyles.score else GoaTextStyles.score.copy(fontSize = TITLE_SIZE)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (tonight != null) colors.primary else colors.onSurface)) {
                    append(TimerText.durationLabel(seconds))
                }
                phase.repeat?.let {
                    withStyle(
                        SpanStyle(
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        ),
                    ) { append(" $it") }
                }
            },
            style = valueStyle,
        )
        // Only the length in force: the preset's own length and Reset live under "Adjust for tonight".
    }
}

private val TITLE_SIZE = 20.sp

/**
 * "Tonight": the start card's mark on the length changed for tonight only (bronze, with its word); a tint, not an
 * edge, as it sits inside the start plate (DESIGN.md "Framing levels").
 */
@Composable
private fun TonightTag() {
    TintTag("Tonight", style = MaterialTheme.typography.labelSmall)
}
