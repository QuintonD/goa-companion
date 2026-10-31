package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachPlan
import dev.quinton.goa.core.game.CoachReason
import dev.quinton.goa.core.game.CoachSuggestions
import dev.quinton.goa.core.game.CoachThreat
import dev.quinton.goa.core.game.SuggestedOption
import dev.quinton.goa.core.game.SuggestionConfidence
import dev.quinton.goa.feature.board.R
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.ui.cards.CardFocusState

/** Under the question: think first, the coach is a tap away. It never answers for the person. */
@Composable
internal fun SuggestButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.coach_suggest))
    }
}

/**
 * The coach's look at the person's question (ADR-051, DESIGN "Suggestions"): ranked options with a rough
 * chance, why, and what the playouts then do; what can hit the hero; the round; the hero's own tips. A tap on
 * an option shows it (a space or a piece on the board, a card in the viewer), and chooses nothing.
 */
@Composable
internal fun SuggestionsSheet(play: PlayUi, open: SuggestUi, focus: CardFocusState, onAction: (GameAction) -> Unit) {
    val close = { onAction(GameAction.CloseSuggestions) }
    val name = play.table.hero(open.seat).name
    BoardSheet(onDismiss = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(COACH_SHEET_HEIGHT)) {
            val said = open.suggestions
            SheetTitle(
                stringResource(R.string.coach_suggest_title),
                said?.let { stringResource(R.string.coach_suggest_for, name, it.samples) } ?: name,
                onDone = close,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when {
                    open.looking -> Thinking()

                    said == null -> Text(
                        stringResource(R.string.coach_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> SuggestionsContent(
                        table = play.table,
                        said = said,
                        focus = focus,
                        onShow = { choice ->
                            onAction(GameAction.Select(choice))
                            close()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Thinking() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.coach_thinking), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The sheet's content: the same for a live question and for a mistake in the post-game review. */
@Composable
internal fun ColumnScope.SuggestionsContent(
    table: TableUi,
    said: CoachSuggestions,
    focus: CardFocusState?,
    onShow: ((Choice) -> Unit)?,
) {
    if (said.close) {
        Text(
            stringResource(R.string.coach_close),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    said.options.forEachIndexed { i, option -> OptionCard(table, i + 1, option, said.close, focus, onShow) }
    if (said.threats.isNotEmpty()) {
        Overline(stringResource(R.string.coach_threats))
        said.threats.forEach { ThreatRow(table, it) }
    }
    said.plan?.let { PlanBlock(table, it) }
    if (said.advice.isNotEmpty()) {
        Overline(stringResource(R.string.coach_advice, table.hero(said.seat).name))
        said.advice.forEach { tip ->
            Text("• $tip", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.coach_advice_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.coach_never_chooses),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Overline(text: String) {
    Text(
        text.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun chanceWords(option: SuggestedOption): String = when (option.chance) {
    100 -> stringResource(R.string.coach_chance_won)
    0 -> stringResource(R.string.coach_chance_lost)
    else -> stringResource(R.string.coach_chance, option.chance)
}

@Composable
private fun confidenceWords(first: Boolean, confidence: SuggestionConfidence): String = stringResource(
    when (confidence) {
        SuggestionConfidence.CLEAR -> if (first) R.string.coach_best_clear else R.string.coach_behind_clear
        SuggestionConfidence.LIKELY -> if (first) R.string.coach_best_likely else R.string.coach_behind_likely
        SuggestionConfidence.CLOSE -> R.string.coach_too_close
    },
)

@Composable
private fun OptionCard(
    table: TableUi,
    rank: Int,
    option: SuggestedOption,
    close: Boolean,
    focus: CardFocusState?,
    onShow: ((Choice) -> Unit)?,
) {
    val cardId = (option.choice as? Choice.Card)?.cardId
    val card = cardId?.let(table::card)
    val onBoard = option.choice is Choice.Space || option.choice is Choice.Unit || option.choice is Choice.Token
    val behind = if (option.behind > 0) stringResource(R.string.coach_behind, option.behind) else null
    val chance = chanceWords(option)
    val confidence = confidenceWords(rank == 1, option.confidence)
    val spoken = stringResource(R.string.coach_option_said, rank, option.label, chance, confidence)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (rank == 1 && !close) 2.dp else 1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            ) {
                if (card != null) {
                    TableCard(card, width = 64.dp)
                    Spacer(Modifier.width(Spacing.md))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "$rank. ${option.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(chance, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        confidence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (behind != null) {
                        Text(
                            behind,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Reasons(stringResource(R.string.coach_why), option.reasons)
            Reasons(stringResource(R.string.coach_against), option.against)
            if (option.line.isNotEmpty()) {
                SmallHead(stringResource(R.string.coach_line))
                option.line.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            when {
                onBoard && onShow != null -> TextButton(
                    onClick = { onShow(option.choice) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.coach_show_on_board)) }

                card != null && focus != null -> TextButton(
                    onClick = { focus.show(listOf(card), title = option.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.coach_read_card)) }
            }
        }
    }
}

@Composable
private fun SmallHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
    )
}

@Composable
private fun Reasons(head: String, reasons: List<CoachReason>) {
    if (reasons.isEmpty()) return
    SmallHead(head)
    reasons.forEach { reason ->
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(reason.text, style = MaterialTheme.typography.bodyMedium)
            if (reason.detail.isNotBlank()) {
                Text(
                    reason.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThreatRow(table: TableUi, threat: CoachThreat) {
    val info = table.hero(threat.heroId)
    val team = table.heroView(threat.heroId)?.team
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        HeroPortrait(
            threat.heroId,
            info.portrait,
            null,
            Modifier.size(32.dp).border(1.dp, team?.let { teamColor(it) } ?: GoaTheme.colors.engravedBorder, CircleShape),
            shape = CircleShape,
            framed = false,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(threat.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlanBlock(table: TableUi, plan: CoachPlan) {
    Overline(stringResource(R.string.coach_plan))
    if (plan.order.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            plan.order.forEach { TableCard(table.card(it), width = 56.dp) }
        }
    }
    Text(plan.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics(mergeDescendants = true) {})
    if (plan.detail.isNotBlank()) {
        Text(plan.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val COACH_SHEET_HEIGHT = 0.9f
