package dev.quinton.goa.feature.trivia.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.feature.trivia.TriviaCardUi
import dev.quinton.goa.feature.trivia.TriviaText
import dev.quinton.goa.feature.trivia.ui.CardKindLine
import dev.quinton.goa.feature.trivia.ui.HeroLine
import dev.quinton.goa.feature.trivia.ui.spokenCard

/** How an answer button looks once the question is answered. Never colour alone: icons too. */
internal enum class Mark { OPEN, RIGHT, WRONG, OTHER }

// ---- Question -------------------------------------------------------------------------------

@Composable
internal fun QuestionPanel(question: QuizQuestionUi, modifier: Modifier = Modifier) {
    when (question) {
        is QuizQuestionUi.Number -> Column(modifier.fillMaxWidth()) {
            GoaCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                HeroLine(question.card)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    question.card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                CardKindLine(question.card)
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "${TriviaText.stat(question.stat)}?",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        heading()
                        contentDescription = "What is its ${TriviaText.stat(question.stat)}?"
                    },
            )
        }

        is QuizQuestionUi.Duel -> Column(modifier.fillMaxWidth()) {
            Text(
                "Which has the higher initiative?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Tap a card, or Same initiative if they match.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Answers --------------------------------------------------------------------------------

@Composable
internal fun AnswerArea(state: TriviaQuizUiState.Playing, onAnswer: (TriviaAnswer) -> Unit) {
    when (val q = state.question) {
        is QuizQuestionUi.Number -> NumberChoices(q, state.answer as? TriviaAnswer.Number, onAnswer)
        is QuizQuestionUi.Duel -> DuelChoices(q, state.answer as? TriviaAnswer.Duel, onAnswer)
    }
}

@Composable
private fun NumberChoices(q: QuizQuestionUi.Number, answer: TriviaAnswer.Number?, onAnswer: (TriviaAnswer) -> Unit) {
    val recall = q.choices.size > CHOICE_GRID
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (recall) {
            (maxWidth / (RECALL_CELL * fontScale + Spacing.sm)).toInt().coerceIn(2, MAX_COLUMNS)
        } else {
            2
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            q.choices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { value ->
                        val mark = when {
                            answer == null -> Mark.OPEN
                            value == q.value -> Mark.RIGHT
                            value == answer.value -> Mark.WRONG
                            else -> Mark.OTHER
                        }
                        ChoiceButton(
                            text = TriviaText.value(value, q.plus),
                            spoken = TriviaText.spokenValue(value, q.plus),
                            mark = mark,
                            minHeight = if (recall) 64.dp else 76.dp,
                            onClick = { onAnswer(TriviaAnswer.Number(value)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    spoken: String,
    mark: Mark,
    minHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.timerDigitsSmall,
) {
    val colors = markColors(mark)
    Surface(
        onClick = onClick,
        enabled = mark == Mark.OPEN,
        shape = MaterialTheme.shapes.medium,
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(if (mark == Mark.RIGHT || mark == Mark.WRONG) 2.dp else 1.dp, colors.border),
        modifier = modifier
            .heightIn(min = minHeight)
            .semantics {
                contentDescription = spoken
                markState(mark)?.let { stateDescription = it }
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkIcon(mark)
            Text(text, style = style, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DuelChoices(q: QuizQuestionUi.Duel, answer: TriviaAnswer.Duel?, onAnswer: (TriviaAnswer) -> Unit) {
    fun mark(pick: DuelPick) = when {
        answer == null -> Mark.OPEN
        pick == q.right -> Mark.RIGHT
        pick == answer.pick -> Mark.WRONG
        else -> Mark.OTHER
    }
    val stacked = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val first: @Composable (Modifier) -> Unit = { m ->
        DuelTile(q.first, q.firstValue, revealed = answer != null, mark(DuelPick.FIRST), m) {
            onAnswer(TriviaAnswer.Duel(DuelPick.FIRST))
        }
    }
    val second: @Composable (Modifier) -> Unit = { m ->
        DuelTile(q.second, q.secondValue, revealed = answer != null, mark(DuelPick.SECOND), m) {
            onAnswer(TriviaAnswer.Duel(DuelPick.SECOND))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (stacked) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
        ChoiceButton(
            text = "Same initiative",
            spoken = "Same initiative",
            mark = mark(DuelPick.TIE),
            minHeight = 56.dp,
            onClick = { onAnswer(TriviaAnswer.Duel(DuelPick.TIE)) },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DuelTile(
    card: TriviaCardUi,
    value: Int,
    revealed: Boolean,
    mark: Mark,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = markColors(mark)
    val spoken = buildString {
        append(spokenCard(card))
        if (revealed) append(", initiative $value")
    }
    Surface(
        onClick = onClick,
        enabled = mark == Mark.OPEN,
        shape = MaterialTheme.shapes.medium,
        color = if (mark == Mark.OPEN || mark == Mark.OTHER) MaterialTheme.colorScheme.surface else colors.container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (mark == Mark.RIGHT || mark == Mark.WRONG) 2.dp else 1.dp, colors.border),
        modifier = modifier
            .heightIn(min = 140.dp)
            .semantics {
                contentDescription = spoken
                markState(mark)?.let { stateDescription = it }
            },
    ) {
        Column(Modifier.padding(Spacing.md).clearAndSetSemantics {}) {
            HeroLine(card, portraitSize = 28.dp)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                card.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            CardKindLine(card, showPrimary = false)
            if (revealed) {
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkIcon(mark)
                    Text(
                        "Initiative $value",
                        style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---- Reveal ---------------------------------------------------------------------------------

/** After an answer: the feedback line and the official card image(s). */
@Composable
internal fun Reveal(state: TriviaQuizUiState.Playing) {
    val feedback = state.feedback ?: return
    val correct = state.correct == true
    Spacer(Modifier.height(Spacing.lg))
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = if (correct) GoaTheme.colors.verified else MaterialTheme.colorScheme.error
        Box(
            Modifier
                .size(scaledIconSize(28.dp))
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (correct) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(scaledIconSize(18.dp)),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Text(feedback, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    when (val q = state.question) {
        is QuizQuestionUi.Number -> q.card.image?.let {
            Spacer(Modifier.height(Spacing.lg))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CardImage(q.card, Modifier.widthIn(max = 300.dp).fillMaxWidth())
            }
        }

        is QuizQuestionUi.Duel -> if (q.first.image != null || q.second.image != null) {
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CardImage(q.first, Modifier.weight(1f))
                CardImage(q.second, Modifier.weight(1f))
            }
        }
    }
}

/** The official card scan at its own aspect ratio; nothing when there is none or it fails to load. */
@Composable
private fun CardImage(card: TriviaCardUi, modifier: Modifier = Modifier) {
    val path = card.image
    var failed by remember(card.cardId) { mutableStateOf(false) }
    if (path == null || failed) {
        Spacer(modifier)
        return
    }
    var aspect by remember(card.cardId) { mutableFloatStateOf(CARD_ASPECT) }
    AsyncImage(
        model = assetUrl(path),
        contentDescription = "Card image: ${card.name}",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp)),
        onError = { failed = true },
        onSuccess = { success ->
            val size = success.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) aspect = size.width / size.height
        },
    )
}

// ---- Marks ----------------------------------------------------------------------------------

internal data class MarkColors(val container: Color, val content: Color, val border: Color)

@Composable
internal fun markColors(mark: Mark): MarkColors = when (mark) {
    Mark.OPEN -> MarkColors(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
    )
    Mark.RIGHT -> MarkColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.secondary,
    )
    Mark.WRONG -> MarkColors(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
        MaterialTheme.colorScheme.error,
    )
    Mark.OTHER -> MarkColors(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSurfaceVariant,
        GoaTheme.colors.engravedBorder,
    )
}

private fun markState(mark: Mark): String? = when (mark) {
    Mark.OPEN, Mark.OTHER -> null
    Mark.RIGHT -> "Right answer"
    Mark.WRONG -> "Your answer, wrong"
}

@Composable
internal fun MarkIcon(mark: Mark) {
    val (icon, tint) = when (mark) {
        Mark.RIGHT -> Icons.Filled.Check to MaterialTheme.colorScheme.secondary
        Mark.WRONG -> Icons.Filled.Close to MaterialTheme.colorScheme.error
        else -> return
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(scaledIconSize(22.dp)))
    Spacer(Modifier.width(Spacing.xs))
}

private const val CHOICE_GRID = 4
private const val MAX_COLUMNS = 5
private val RECALL_CELL = 64.dp

/** Deck cards are portrait; the real ratio replaces this once the scan loads. */
private const val CARD_ASPECT = 729f / 1026f
