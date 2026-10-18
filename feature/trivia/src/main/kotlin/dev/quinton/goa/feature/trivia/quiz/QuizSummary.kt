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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.formatDuration
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.spokenDuration
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.trivia.TriviaCardUi
import dev.quinton.goa.feature.trivia.TriviaText
import dev.quinton.goa.feature.trivia.ui.CardKindLine
import dev.quinton.goa.feature.trivia.ui.HeroLine
import dev.quinton.goa.feature.trivia.ui.spokenUnverified

/**
 * The end of a round: score, best streak, newly learned numbers, the misses and what next. A
 * Challenge adds its answer time and the player's place on the board (ADR-032).
 */
@Composable
internal fun SummaryContent(
    state: TriviaQuizUiState.Summary,
    onPractise: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onLeaderboard: () -> Unit = {},
) {
    val onBoard = state.challenge?.finished == true && state.playerName != null
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
            if (onBoard) {
                Button(onClick = onLeaderboard, modifier = big) {
                    Icon(
                        Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(scaledIconSize(20.dp)),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Leaderboard", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.canPractise) {
                if (onBoard) {
                    OutlinedButton(onClick = onPractise, modifier = big) {
                        Text("Practise the missed ones", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(onClick = onPractise, modifier = big) {
                        Text("Practise the missed ones", style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(onClick = onPlayAgain, modifier = big) {
                    Text("Play again", style = MaterialTheme.typography.titleMedium)
                }
            } else if (onBoard) {
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

/**
 * What a Challenge's summary says about the board: "2nd of 5 · your best 18/20", "New best! · 1st
 * of 3", why it isn't on it, or null while the place is still being worked out.
 */
internal fun challengeLine(state: TriviaQuizUiState.Summary): String? {
    val challenge = state.challenge ?: return null
    return when {
        !challenge.finished -> "Unfinished challenges don't go on the leaderboard."

        state.playerName == null -> "Played as Guest: not on the leaderboard."

        else -> challenge.standing?.let { standing ->
            val place = TriviaText.place(standing.place, standing.tied) + " of ${standing.of}"
            if (challenge.newBest) {
                "New best! · $place"
            } else {
                "$place · your best ${standing.best.correct}/${standing.best.questions}"
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreCard(state: TriviaQuizUiState.Summary) {
    val challenge = state.challenge
    GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xl)) {
        if (challenge != null) {
            Text(
                "CHALLENGE" + (state.playerName?.let { " · ${it.uppercase()}" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            if (state.asked == 0) "No questions answered" else "${state.correct} of ${state.asked} right",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xs))
        val line = challengeLine(state)
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.titleMedium,
                color = if (challenge?.finished == true) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            when {
                state.asked == 0 -> "End a round after answering to see how it went."
                state.misses.isEmpty() -> "Every answer right."
                state.progressSaved -> "Missed numbers come back sooner next time."
                else -> "Played as ${state.playerName ?: "Guest"}: this phone's learning progress stays as it was."
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
                if (challenge != null) {
                    Metric(
                        value = formatDuration(challenge.answerMillis),
                        label = "Answer time",
                        spoken = "Answer time ${spokenDuration(challenge.answerMillis)}",
                    )
                }
                if (state.progressSaved) {
                    Metric(
                        value = "+${state.learned}",
                        label = if (state.learned == 1) "number learned" else "numbers learned",
                        spoken = "${TriviaText.count(state.learned, "number")} learned this round",
                    )
                }
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

/** "Arien's Mad Dash", and ", unverified data" when the hero's data isn't verified. */
private fun spokenOwned(card: TriviaCardUi): String =
    "${card.heroName}'s ${card.name}${spokenUnverified(card.heroVerified)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissRow(miss: MissUi) {
    val other = miss.other
    val names = listOfNotNull(spokenOwned(miss.card), other?.let(::spokenOwned)).joinToString(" vs ")
    val spoken = "$names, ${miss.title}: you answered ${miss.yours}, the answer is ${miss.right}"
    GoaCard(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        contentPadding = PaddingValues(Spacing.md),
    ) {
        // Each card with its own hero: a duel's two cards are often from different heroes.
        MissCard(miss.card, showKind = other == null)
        if (other != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "vs",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            MissCard(other, showKind = false)
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${miss.title}:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(Mark.WRONG)
                Text(miss.yours, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(scaledIconSize(16.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(Mark.RIGHT)
                Text(miss.right, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun MissCard(card: TriviaCardUi, showKind: Boolean) {
    HeroLine(card, portraitSize = 24.dp)
    Spacer(Modifier.height(Spacing.xs))
    Text(card.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    if (showKind) CardKindLine(card, showPrimary = false)
}
