package dev.quinton.goa.feature.trivia.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.trivia.TriviaText
import dev.quinton.goa.feature.trivia.ui.CardKindLine
import dev.quinton.goa.feature.trivia.ui.HeroLine

/** The end of a round: score, best streak, newly learned numbers, the misses and what next. */
@Composable
internal fun SummaryContent(
    state: TriviaQuizUiState.Summary,
    onPractise: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
    ) {
        ScoreCard(state)
        if (state.misses.isNotEmpty()) {
            SectionHeader("Missed")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.misses.forEach { MissRow(it) }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val big = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            if (state.canPractise) {
                Button(onClick = onPractise, modifier = big) {
                    Text("Practise the missed ones", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onPlayAgain, modifier = big) {
                    Text("Play again", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(onClick = onPlayAgain, modifier = big) {
                    Text("Play again", style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButton(onClick = onDone, modifier = big) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreCard(state: TriviaQuizUiState.Summary) {
    GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xl)) {
        Text(
            if (state.asked == 0) "No questions answered" else "${state.correct} of ${state.asked} right",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            when {
                state.asked == 0 -> "End a round after answering to see how it went."
                state.misses.isEmpty() -> "Every answer right."
                else -> "Missed numbers come back sooner next time."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.asked > 0) {
            Spacer(Modifier.height(Spacing.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Metric(value = "${state.bestStreak}", label = "Best streak")
                Metric(
                    value = "+${state.learned}",
                    label = if (state.learned == 1) "number learned" else "numbers learned",
                    spoken = "${TriviaText.count(state.learned, "number")} learned this round",
                )
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, spoken: String = "$label $value") {
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Text(value, style = GoaTextStyles.timerDigitsSmall, color = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissRow(miss: MissUi) {
    val names = listOfNotNull(miss.card.name, miss.other?.name).joinToString(" vs ")
    val spoken = "$names, ${miss.title}: you answered ${miss.yours}, the answer is ${miss.right}"
    GoaCard(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        contentPadding = PaddingValues(Spacing.md),
    ) {
        HeroLine(miss.card, portraitSize = 24.dp)
        Spacer(Modifier.height(Spacing.xs))
        Text(names, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (miss.other == null) CardKindLine(miss.card, showPrimary = false)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${miss.title}:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
            MarkIcon(Mark.WRONG)
            Text(miss.yours, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.sm).width(scaledIconSize(16.dp)),
            )
            MarkIcon(Mark.RIGHT)
            Text(
                miss.right,
                style = GoaTextStyles.numeric,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
