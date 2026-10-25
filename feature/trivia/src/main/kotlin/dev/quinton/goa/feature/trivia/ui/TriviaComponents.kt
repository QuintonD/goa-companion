package dev.quinton.goa.feature.trivia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.trivia.TriviaCardUi
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.designsystem.component.CardColorDot
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.MasterySummary

/** Learned = tide teal, learning = bronze, new = the empty track. */
@Composable
internal fun learnedColor(): Color = MaterialTheme.colorScheme.secondary

@Composable
internal fun learningColor(): Color = MaterialTheme.colorScheme.primary

@Composable
internal fun newColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

/**
 * Learned, learning and new numbers as one segmented bar. Decorative on its own: callers pair it
 * with the counts in words ([MasteryLegend] or a "12/86 learned" line).
 */
@Composable
internal fun MasteryBar(summary: MasterySummary, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val shape = RoundedCornerShape(height / 2)
    val track = newColor()
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(track)
            .clearAndSetSemantics {},
    ) {
        val total = summary.total.coerceAtLeast(1).toFloat()
        if (summary.mastered > 0) {
            Box(Modifier.weight(summary.mastered / total).height(height).background(learnedColor()))
        }
        if (summary.learning > 0) {
            if (summary.mastered > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(summary.learning / total).height(height).background(learningColor()))
        }
        val rest = summary.unseen / total
        if (rest > 0f) Spacer(Modifier.weight(rest))
    }
}

/** "● Learned 41  ● Learning 12  ● New 271" — the bar's colours in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MasteryLegend(summary: MasterySummary, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        LegendItem(learnedColor(), "Learned", summary.mastered)
        LegendItem(learningColor(), "Learning", summary.learning)
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), "New", summary.unseen)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $count" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(scaledIconSize(10.dp)).clip(CircleShape).background(color))
        Spacer(Modifier.width(Spacing.xs + 2.dp))
        Text(
            "$label ${TriviaText.number(count)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "● Red · Tier II · Attack card" under a card name. */
@Composable
internal fun CardKindLine(card: TriviaCardUi, modifier: Modifier = Modifier, showPrimary: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CardColorDot(card.color, size = scaledIconSize(12.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            listOfNotNull(card.label.ifEmpty { null }, card.primary?.takeIf { showPrimary }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The hero a card belongs to: small portrait (or sigil) and name, and Unverified when it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroLine(card: TriviaCardUi, modifier: Modifier = Modifier, portraitSize: Dp = 32.dp) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroPortrait(
                heroId = card.heroId,
                portrait = card.portrait,
                contentDescription = null,
                modifier = Modifier.size(scaledIconSize(portraitSize, maxScale = 1.5f)),
                shape = CircleShape,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                card.heroName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!card.heroVerified) UnverifiedBadge()
    }
}

/** ", unverified data" after a card or hero TalkBack names, when its data isn't verified. */
internal fun spokenUnverified(verified: Boolean): String = TriviaText.spokenUnverified(verified)

internal const val UNVERIFIED_SPOKEN = TriviaText.UNVERIFIED_SPOKEN

/** "Mad Dash, Arien's Red · Tier II Attack card": how TalkBack names a card. */
internal fun spokenCard(card: TriviaCardUi): String = TriviaText.spokenCard(card)

@Composable
internal fun SetupLabel(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
    }
}
