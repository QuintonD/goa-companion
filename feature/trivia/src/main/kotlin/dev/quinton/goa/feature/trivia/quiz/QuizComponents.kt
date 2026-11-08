package dev.quinton.goa.feature.trivia.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.data.trivia.QuizQuestionUi
import dev.quinton.goa.core.data.trivia.TriviaCardUi
import dev.quinton.goa.core.data.trivia.TriviaQuizUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.designsystem.component.CardTitle
import dev.quinton.goa.core.designsystem.component.CardTitleSize
import dev.quinton.goa.core.designsystem.component.ColourRibbon
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.feature.trivia.ui.CardKindLine
import dev.quinton.goa.feature.trivia.ui.HeroLine
import dev.quinton.goa.feature.trivia.ui.spokenCard

/** How an answer button looks once the question is answered. Never colour alone: icons too. */
internal enum class Mark { OPEN, RIGHT, WRONG, OTHER }

// ---- Question -------------------------------------------------------------------------------

/**
 * The question. For a card number it leads with the card (its name large beside its colour's ribbon, its kind, whose
 * it is) and asks on the title plate ("What is its Defense?"); with [artHeight] the card's painting sits between the
 * two, cropped clear of every printed number, at most that tall ([onArtHeight] hears how tall it is, in pixels). For a
 * duel, the question on its plate.
 */
@Composable
internal fun QuestionPanel(
    question: QuizQuestionUi,
    modifier: Modifier = Modifier,
    artHeight: Dp = 0.dp,
    onArtHeight: (Int) -> Unit = {},
) {
    when (question) {
        is QuizQuestionUi.Number -> GoaPlate(modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColourRibbon(
                        PlateColor.of(question.card.color),
                        Modifier.size(width = scaledIconSize(16.dp), height = scaledIconSize(34.dp)),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        CardTitle(
                            question.card.name,
                            size = CardTitleSize.Title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        CardKindLine(question.card)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                HeroLine(question.card, portraitSize = 24.dp)
            }
            if (artHeight > 0.dp) {
                Spacer(Modifier.height(Spacing.md))
                CardArt(
                    question.card,
                    Modifier.fillMaxWidth().heightIn(max = artHeight).onSizeChanged { onArtHeight(it.height) },
                )
            } else {
                SideEffect { onArtHeight(0) }
            }
            Spacer(Modifier.height(Spacing.md))
            val asked = "What is its ${TriviaText.stat(question.stat)}?"
            TitlePlate(
                asked,
                style = QuestionPlateStyle,
                heading = false,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = asked
                    },
            )
        }

        is QuizQuestionUi.Duel -> Column(modifier.fillMaxWidth()) {
            TitlePlate("Which has the higher initiative?", Modifier.fillMaxWidth(), maxLines = 3)
        }
    }
}

/**
 * The card's own painting, cropped from its scan to the window that holds no printed number (the Initiative, the
 * left-hand ribbon's values, the tier and the text box's values all lie outside it; checked on every deck card's
 * scan), as wide as [modifier] lets it and as tall as the window's pixels allow at [MAX_ART_SCALE] (never taller than
 * [modifier]'s most), so it is never enlarged past sharp nor framed in empty band. A card without a portrait scan
 * (none, or a landscape spellbook page) shows its hero's art instead, filling the band. Decorative for TalkBack: the
 * panel names the card.
 */
@Composable
internal fun CardArt(card: TriviaCardUi, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val bitmap = card.image?.let { rememberOfficialBitmap(it) }
    val portraitScan = bitmap != null && bitmap.height > bitmap.width
    when {
        card.image == null || (bitmap != null && !portraitScan) -> HeroPortrait(
            heroId = card.heroId,
            portrait = card.portrait,
            contentDescription = null,
            modifier = modifier.fillMaxHeight().clip(shape),
        )

        bitmap == null -> Box(modifier.fillMaxHeight().clip(shape).background(MaterialTheme.colorScheme.surface))

        else -> Canvas(
            modifier
                .height(
                    with(LocalDensity.current) {
                        (bitmap.height * (ART_WINDOW_BOTTOM - ART_WINDOW_TOP) * MAX_ART_SCALE).toDp()
                    },
                )
                .clearAndSetSemantics {},
        ) {
            val left = bitmap.width * ART_WINDOW_LEFT
            val top = bitmap.height * ART_WINDOW_TOP
            val windowWidth = bitmap.width * (ART_WINDOW_RIGHT - ART_WINDOW_LEFT)
            val windowHeight = bitmap.height * (ART_WINDOW_BOTTOM - ART_WINDOW_TOP)
            // Filling the box would enlarge the scan's few pixels past sharp: never more than MAX_ART_SCALE of them.
            // The painting is then smaller than its band, centred in it.
            val scale = minOf(maxOf(size.width / windowWidth, size.height / windowHeight), MAX_ART_SCALE)
            // Cropped to the box's shape inside the window, centred: never past the window's edges.
            val w = (size.width / scale).coerceAtMost(windowWidth)
            val h = (size.height / scale).coerceAtMost(windowHeight)
            val dst = Size(w * scale, h * scale)
            val at = Offset((size.width - dst.width) / 2, (size.height - dst.height) / 2)
            val corner = ART_CORNER.toPx()
            clipPath(Path().apply { addRoundRect(RoundRect(Rect(at, dst), CornerRadius(corner))) }) {
                drawImage(
                    bitmap,
                    srcOffset = IntOffset(
                        (left + (windowWidth - w) / 2).toInt(),
                        (top + (windowHeight - h) / 2).toInt(),
                    ),
                    srcSize = IntSize(w.toInt(), h.toInt()),
                    dstOffset = IntOffset(at.x.toInt(), at.y.toInt()),
                    dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

/** The painting's window on a portrait deck card's scan (fractions of its width and height): no printed number. */
private const val ART_WINDOW_LEFT = 0.25f
private const val ART_WINDOW_TOP = 0.12f
private const val ART_WINDOW_RIGHT = 0.96f
private const val ART_WINDOW_BOTTOM = 0.50f

/** The most a card painting is enlarged, in screen pixels per scan pixel: beyond it the print turns soft. */
private const val MAX_ART_SCALE = 1.6f
private val ART_CORNER = 10.dp

// ---- Answers --------------------------------------------------------------------------------

@Composable
internal fun AnswerArea(state: TriviaQuizUiState.Playing, onAnswer: (TriviaAnswer) -> Unit) {
    when (val q = state.question) {
        is QuizQuestionUi.Number -> NumberChoices(q, state.answer as? TriviaAnswer.Number, onAnswer)
        is QuizQuestionUi.Duel -> DuelChoices(q, state.answer as? TriviaAnswer.Duel, onAnswer)
    }
}

@Composable
private fun NumberChoices(
    q: QuizQuestionUi.Number,
    answer: TriviaAnswer.Number?,
    onAnswer: (TriviaAnswer) -> Unit,
) {
    val recall = q.choices.size > CHOICE_GRID
    val style = GoaTextStyles.timerDigitsSmall
    val labels = q.choices.map { TriviaText.value(it, q.plus) }
    val widest = widestLabel(labels, style)
    val badge = scaledIconSize(BADGE_SIZE)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The room the widest label needs: itself, centred, clear of the corner mark on both sides.
        val cell = maxOf(widest + (badge + BADGE_INSET) * 2, MIN_CELL)
        val fit = ((maxWidth + Spacing.sm) / (cell + Spacing.sm)).toInt()
        val columns = fit.coerceIn(1, if (recall) MAX_COLUMNS else 2)
        // A thumb's target at arm's length, the same height answered or not.
        val tile = if (recall) RECALL_TILE else CHOICE_TILE
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            q.choices.zip(labels).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { (value, label) ->
                        val mark = when {
                            answer == null -> Mark.OPEN
                            value == q.value -> Mark.RIGHT
                            value == answer.value -> Mark.WRONG
                            else -> Mark.OTHER
                        }
                        ChoiceButton(
                            text = label,
                            spoken = TriviaText.spokenValue(value, q.plus),
                            mark = mark,
                            minHeight = tile,
                            onClick = { onAnswer(TriviaAnswer.Number(value)) },
                            modifier = Modifier.weight(1f),
                            style = style,
                            cornerMark = true,
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Width of the widest of [labels] on one line in [style] at the current font scale. */
@Composable
private fun widestLabel(labels: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, density) {
        val px = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        with(density) { px.toDp() }
    }
}

/**
 * An answer button. Its label never wraps; once answered, the right/wrong mark sits as a small
 * badge in the top corner ([cornerMark]) or before the label. TalkBack hears "6, right answer" or
 * "3, your answer, wrong".
 */
@Composable
private fun ChoiceButton(
    text: String,
    spoken: String,
    mark: Mark,
    minHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.timerDigitsSmall,
    cornerMark: Boolean = false,
) {
    GoaPlateTheme {
        val colors = markColors(mark)
        Surface(
            onClick = onClick,
            enabled = mark == Mark.OPEN,
            shape = MaterialTheme.shapes.medium,
            color = if (mark == Mark.OPEN || mark == Mark.OTHER) Color.Transparent else colors.container,
            contentColor = colors.content,
            border = BorderStroke(if (mark == Mark.RIGHT || mark == Mark.WRONG) 2.dp else 1.dp, colors.border),
            modifier = modifier
                .slatePlate(MaterialTheme.shapes.medium, brackets = false)
                .heightIn(min = minHeight)
                .semantics { contentDescription = withMark(spoken, mark) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!cornerMark) MarkIcon(mark)
                    Text(text, style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                }
                if (cornerMark) {
                    MarkBadge(mark, Modifier.align(Alignment.TopEnd).padding(top = BADGE_INSET, end = BADGE_INSET))
                }
            }
        }
    }
}

/** The right/wrong mark as a small filled disc for a button's corner; nothing while open. */
@Composable
private fun MarkBadge(mark: Mark, modifier: Modifier = Modifier) {
    val (icon, tone) = when (mark) {
        Mark.RIGHT -> Icons.Filled.Check to MaterialTheme.colorScheme.secondary
        Mark.WRONG -> Icons.Filled.Close to MaterialTheme.colorScheme.error
        else -> return
    }
    Box(
        modifier.size(scaledIconSize(BADGE_SIZE)).clip(CircleShape).background(tone),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(scaledIconSize(BADGE_ICON)),
        )
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
    GoaPlateTheme {
        val colors = markColors(mark)
        val spoken = buildString {
            append(spokenCard(card))
            if (revealed) append(", initiative $value")
        }
        Surface(
            onClick = onClick,
            enabled = mark == Mark.OPEN,
            shape = MaterialTheme.shapes.medium,
            color = if (mark == Mark.OPEN || mark == Mark.OTHER) Color.Transparent else colors.container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(if (mark == Mark.RIGHT || mark == Mark.WRONG) 2.dp else 1.dp, colors.border),
            modifier = modifier
                .slatePlate(MaterialTheme.shapes.medium, brackets = false)
                .heightIn(min = 140.dp)
                .semantics { contentDescription = withMark(spoken, mark) },
        ) {
            Column(Modifier.padding(Spacing.md).clearAndSetSemantics {}) {
                HeroLine(card, portraitSize = 28.dp)
                Spacer(Modifier.height(Spacing.sm))
                CardTitle(
                    card.name,
                    size = CardTitleSize.Large,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
                Spacer(Modifier.height(Spacing.xs))
                CardKindLine(card, showPrimary = false)
                if (revealed) {
                    Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarkIcon(mark)
                        Text(
                            "Initiative $value",
                            style = GoaTextStyles.numeric.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// ---- Reveal ---------------------------------------------------------------------------------

/**
 * The feedback line and, once answered, the official card image(s). The feedback is one polite
 * live region that is there before the answer, so TalkBack announces it when it fills in. After an
 * answer the list scrolls to its end, where this block sits (the scan is the proof of the answer),
 * unless the player is touching the list ([scroll]).
 */
@Composable
internal fun Reveal(
    state: TriviaQuizUiState.Playing,
    scroll: ScrollState,
    /** The scans are kept this short at most, so they fit the visible list with the feedback. */
    imageMaxHeight: Dp = Dp.Infinity,
) {
    var loaded by remember(state.number) { mutableIntStateOf(0) }
    val scans = when (val q = state.question) {
        is QuizQuestionUi.Number -> listOfNotNull(q.card.image).size
        is QuizQuestionUi.Duel -> listOfNotNull(q.first.image, q.second.image).size
    }
    // On the answer and again once every scan has loaded (the list grows), always to the list's end:
    // an absolute target, so where it lands never depends on which scan loaded first or when.
    val allLoaded = loaded >= scans
    LaunchedEffect(state.number, state.answered, allLoaded) {
        if (!state.answered) return@LaunchedEffect
        withFrameNanos {}
        if (!scroll.isScrollInProgress) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxWidth()) {
        if (state.answered) Spacer(Modifier.height(Spacing.lg))
        FeedbackLine(state.feedback, state.correct)
        if (!state.answered) return@Column
        val onLoaded: () -> Unit = { loaded += 1 }
        // Width- or height-bound, whichever is tighter: the scan keeps its own ratio.
        val bounds = Modifier.widthIn(max = 300.dp).heightIn(max = imageMaxHeight)
        when (val q = state.question) {
            is QuizQuestionUi.Number -> q.card.image?.let {
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CardImage(q.card, bounds, onLoaded)
                }
            }

            is QuizQuestionUi.Duel -> if (q.first.image != null || q.second.image != null) {
                Spacer(Modifier.height(Spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(q.first, q.second).forEach { card ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            CardImage(card, bounds, onLoaded)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

/** "Right! …" / "Not quite. …" with a tick or cross; an empty live region before the answer. */
@Composable
private fun FeedbackLine(feedback: String?, correct: Boolean?) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feedback != null) {
            val tone = if (correct == true) GoaTheme.colors.verified else MaterialTheme.colorScheme.error
            Box(
                Modifier
                    .size(scaledIconSize(28.dp))
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                // The text says it in words ("Right!" / "Not quite."): the icon adds nothing to hear.
                Icon(
                    if (correct == true) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(scaledIconSize(18.dp)),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(feedback, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The official card scan at its own aspect ratio; nothing when there is none or it fails to load. */
@Composable
private fun CardImage(card: TriviaCardUi, modifier: Modifier = Modifier, onLoaded: () -> Unit = {}) {
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
            onLoaded()
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

/** "6, right answer" / "3, your answer, wrong": the mark is part of what TalkBack reads. */
internal fun withMark(spoken: String, mark: Mark): String = when (mark) {
    Mark.OPEN, Mark.OTHER -> spoken
    Mark.RIGHT -> "$spoken, right answer"
    Mark.WRONG -> "$spoken, your answer, wrong"
}

/** The tick or cross before a label. Silent: every caller says it in its own description ([withMark]). */
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

/** The narrowest an answer button gets, whatever its label. */
private val MIN_CELL = 64.dp

/** A number button's height: four choices two by two; recall's many choices a row of up to five. */
private val CHOICE_TILE = 88.dp
private val RECALL_TILE = 64.dp

/** The corner mark: a disc this size, this far in from the button's corner. */
private val BADGE_SIZE = 16.dp
private val BADGE_ICON = 12.dp
private val BADGE_INSET = 4.dp

/** Deck cards are portrait; the real ratio replaces this once the scan loads. */
private const val CARD_ASPECT = 729f / 1026f

/** The question on its title plate, large enough to read from across the table. */
private val QuestionPlateStyle = GoaTextStyles.plateTitle.copy(fontSize = 22.sp, lineHeight = 28.sp)
