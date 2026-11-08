package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaProgressBar
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.SectionHeaderPaddingTight
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.DecisionMark
import dev.quinton.goa.core.game.GameReport
import dev.quinton.goa.core.game.ReviewMistake
import dev.quinton.goa.feature.board.R
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import kotlin.math.roundToInt

/**
 * Game over: every decision of the person's looked at again on demand, in the background with its
 * progress and Cancel (ADR-051): accuracy per hero and per kind, and the biggest mistakes, each with the
 * position in words, the sheet's content and a jump to the log.
 */
@Composable
internal fun GameReportSection(
    play: PlayUi,
    onAction: (GameAction) -> Unit,
    focus: CardFocusState?,
    onLogAt: (Int) -> Unit,
) {
    val ui = play.report
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.report_title), padding = SectionHeaderPaddingTight)
        when {
            ui == null -> {
                GoaOutlinedButton(
                    onClick = { onAction(GameAction.StartReport) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.report_start)) }
            }

            ui.running -> Running(ui, onAction)

            ui.report != null -> Report(play, ui.report!!, focus, onLogAt)
        }
    }
}

@Composable
private fun Running(ui: ReportUi, onAction: (GameAction) -> Unit) {
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            if (ui.total > 0) {
                stringResource(R.string.report_running, ui.done, ui.total)
            } else {
                stringResource(R.string.report_starting)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (ui.total > 0) {
            GoaProgressBar({ ui.done.toFloat() / ui.total }, Modifier.fillMaxWidth())
        } else {
            GoaProgressBar(null, Modifier.fillMaxWidth())
        }
    }
    TextButton(
        onClick = { onAction(GameAction.CancelReport) },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.report_cancel)) }
}

@Composable
private fun Report(play: PlayUi, report: GameReport, focus: CardFocusState?, onLogAt: (Int) -> Unit) {
    if (report.decisions.isEmpty()) {
        Text(stringResource(R.string.report_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(
        stringResource(R.string.report_overall, report.overall.percent, report.overall.decisions),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        DecisionMark.entries.map { m ->
            stringResource(R.string.report_marks, markWord(m), report.decisions.count { it.mark == m })
        }.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (report.byHero.size > 1) {
        SmallHead(stringResource(R.string.report_by_hero))
        report.byHero.forEach { row ->
            Text(
                stringResource(R.string.report_row, play.table.hero(row.key).name, row.percent, row.decisions),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    SmallHead(stringResource(R.string.report_by_kind))
    report.byKind.forEach { row ->
        val kind = runCatching { DecisionKind.valueOf(row.key) }.getOrNull()
        Text(
            stringResource(R.string.report_row, kind?.let { kindWord(it) } ?: row.key, row.percent, row.decisions),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    SmallHead(stringResource(R.string.report_mistakes))
    if (report.mistakes.isEmpty()) {
        Text(stringResource(R.string.report_no_mistakes), style = MaterialTheme.typography.bodyMedium)
    }
    report.mistakes.forEach { MistakeCard(play, it, focus, onLogAt) }
}

@Composable
private fun SmallHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
    )
}

@Composable
private fun MistakeCard(play: PlayUi, m: ReviewMistake, focus: CardFocusState?, onLogAt: (Int) -> Unit) {
    var open by rememberSaveable(m.decision.decisionId) { mutableStateOf(false) }
    PlateSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.report_when, m.decision.round, m.decision.turn).uppercase(),
                        style = GoaTextStyles.overline,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        markWord(m.decision.mark),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    m.prompt,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.report_chose, m.chosen) + ", " +
                        stringResource(R.string.report_gave_up, (m.decision.loss * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    m.position,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Row {
                TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (open) R.string.report_hide else R.string.report_show))
                }
                TextButton(
                    onClick = { onLogAt(m.decision.eventIndex) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.report_log)) }
            }
            if (open) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SuggestionsContent(play.table, m.suggestions, focus, onShow = null)
                }
            }
        }
    }
}

@Composable
private fun markWord(mark: DecisionMark): String = stringResource(
    when (mark) {
        DecisionMark.BEST -> R.string.report_mark_best
        DecisionMark.GOOD -> R.string.report_mark_good
        DecisionMark.INACCURACY -> R.string.report_mark_inaccuracy
        DecisionMark.MISTAKE -> R.string.report_mark_mistake
        DecisionMark.BLUNDER -> R.string.report_mark_blunder
    },
)

@Composable
private fun kindWord(kind: DecisionKind): String = stringResource(
    when (kind) {
        DecisionKind.PLAY_CARD -> R.string.kind_play_card
        DecisionKind.ACT_FIRST -> R.string.kind_act_first
        DecisionKind.RESPAWN -> R.string.kind_respawn
        DecisionKind.CHOOSE_ACTION -> R.string.kind_choose_action
        DecisionKind.CHOOSE_SPACE -> R.string.kind_choose_space
        DecisionKind.CHOOSE_UNIT -> R.string.kind_choose_unit
        DecisionKind.CHOOSE_TOKEN -> R.string.kind_choose_token
        DecisionKind.CHOOSE_CARD -> R.string.kind_choose_card
        DecisionKind.DEFEND -> R.string.kind_defend
        DecisionKind.CHOOSE_OPTION -> R.string.kind_choose_option
        DecisionKind.CONFIRM -> R.string.kind_confirm
        DecisionKind.CHOOSE_NUMBER -> R.string.kind_choose_number
        DecisionKind.REMOVE_MINION -> R.string.kind_remove_minion
        DecisionKind.MINION_RETURN -> R.string.kind_minion_return
        DecisionKind.UPGRADE_CARD -> R.string.kind_upgrade_card
        DecisionKind.KEEP_UPGRADE -> R.string.kind_keep_upgrade
    },
)
