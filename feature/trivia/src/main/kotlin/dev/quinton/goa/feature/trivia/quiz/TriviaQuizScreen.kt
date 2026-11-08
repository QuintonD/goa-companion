package dev.quinton.goa.feature.trivia.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.quinton.goa.core.data.trivia.QuizQuestionUi
import dev.quinton.goa.core.data.trivia.TriviaQuizUiState
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaProgressBar
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.TriviaAnswer

/**
 * A quiz round: one question at a time, then the round's summary. End (top bar) shows the summary
 * at any point. Leaving with Back once a question is answered asks first: the answers are already
 * saved, but the round's results would go.
 */
@Composable
fun TriviaQuizScreen(
    state: TriviaQuizUiState,
    onAnswer: (TriviaAnswer) -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onPractise: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialConfirmLeave: Boolean = false,
    onLeaderboard: () -> Unit = {},
    /** The screen came into view (true) or left it (false): answer time pauses meanwhile. */
    onVisibleChange: (Boolean) -> Unit = {},
) {
    val playing = state as? TriviaQuizUiState.Playing
    var confirmLeave by rememberSaveable { mutableStateOf(initialConfirmLeave) }
    val askFirst = playing?.started == true
    val back = { if (askFirst) confirmLeave = true else onBack() }
    BackHandler(enabled = askFirst) { confirmLeave = true }
    LifecycleResumeEffect(Unit) {
        onVisibleChange(true)
        onPauseOrDispose { onVisibleChange(false) }
    }
    GoaScreen(
        title = when {
            state is TriviaQuizUiState.Summary -> "Round over"
            playing?.challenge == true -> "Challenge"
            else -> "Card trivia"
        },
        onBack = back,
        modifier = modifier,
        actions = {
            if (playing != null) {
                BannerTextButton(
                    "End",
                    onClick = onStop,
                    modifier = Modifier.semantics { contentDescription = "End round" },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                TriviaQuizUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = "Loading quiz" },
                )

                TriviaQuizUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.Quiz,
                    title = "Nothing to ask",
                    message = "None of the chosen cards print these numbers. Go back and add a number or more cards.",
                    modifier = Modifier.align(Alignment.Center),
                    action = { GoaOutlinedButton(onClick = onDone) { Text("Back to setup") } },
                )

                is TriviaQuizUiState.Playing -> QuizPlaying(state, onAnswer, onNext)

                is TriviaQuizUiState.Summary -> SummaryContent(
                    state,
                    onPractise,
                    onPlayAgain,
                    onDone,
                    Modifier.fillMaxSize(),
                    onLeaderboard = onLeaderboard,
                )
            }
        }
    }
    if (confirmLeave && askFirst) {
        ConfirmDialog(
            title = "Leave this round?",
            message = "Your answers are saved. The round's results won't be.",
            confirmLabel = "See results",
            onConfirm = {
                confirmLeave = false
                onStop()
            },
            dismissLabel = "Leave",
            onDismissButton = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false },
            icon = Icons.AutoMirrored.Outlined.ExitToApp,
        )
    }
}

@Composable
private fun QuizPlaying(state: TriviaQuizUiState.Playing, onAnswer: (TriviaAnswer) -> Unit, onNext: () -> Unit) {
    AnswerHaptics(state)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.screen),
                ) {
                    QuizHeader(state)
                    Spacer(Modifier.height(Spacing.lg))
                    QuestionPanel(state.question, artHeight = LANDSCAPE_ART)
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    val scroll = rememberScrollState()
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val imageMax = imageRoom(maxHeight, padding = Spacing.screen * 2)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(Spacing.screen),
                        ) {
                            AnswerArea(state, onAnswer)
                            Reveal(state, scroll = scroll, imageMaxHeight = imageMax)
                        }
                    }
                    NextBar(state, onNext)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // Pinned: where the round is and the score stay in sight, also once answered.
                QuizHeader(
                    state,
                    Modifier
                        .padding(horizontal = Spacing.screen)
                        .padding(top = Spacing.md, bottom = Spacing.sm),
                )
                if (state.question is QuizQuestionUi.Number) {
                    NumberQuestion(state, onAnswer, Modifier.weight(1f))
                } else {
                    DuelQuestion(state, onAnswer, Modifier.weight(1f))
                }
                NextBar(state, onNext)
            }
        }
    }
}

/**
 * A card-number question upright. The question (the card's name, whose it is, its painting cropped clear of every
 * printed number, "What is its Defense?") and the answers under it never change size or place when it is answered:
 * the painting takes the room the screen leaves before the answer and keeps it after. The verdict and the whole scan,
 * as large as round 3 had it (up to 300 dp wide), are added under the answers, and the list scrolls to them.
 */
@Composable
private fun NumberQuestion(
    state: TriviaQuizUiState.Playing,
    onAnswer: (TriviaAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = state.question as QuizQuestionUi.Number
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // The panel without its painting, and the answers, as measured; the painting fills what they leave.
        var foot by remember(state.number) { mutableIntStateOf(0) }
        var panel by remember(state.number) { mutableIntStateOf(0) }
        var artPx by remember(state.number) { mutableIntStateOf(0) }
        val rest = panel - if (artPx > 0) artPx + with(density) { Spacing.md.roundToPx() } else 0
        var kept by remember(state.number) { mutableStateOf<Dp?>(null) }
        // The list's padding and gap, and a margin over Next.
        val room = with(density) { maxHeight - Spacing.md * 4 - rest.toDp() - foot.toDp() }
        val art = kept ?: if (room < MIN_ART) 0.dp else room.coerceAtMost(MAX_ART)
        // Answered: the painting keeps its height, so nothing above the verdict moves.
        if (state.answered && kept == null && rest > 0) SideEffect { kept = art }
        val imageMax = imageRoom(maxHeight, padding = Spacing.md * 2)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        ) {
            QuestionPanel(
                question,
                artHeight = art,
                // The painting is often shorter than its band (never enlarged past sharp): the rest is the panel
                // without the painting as drawn.
                onArtHeight = { artPx = it },
                modifier = Modifier.onSizeChanged { panel = it.height },
            )
            Spacer(Modifier.height(Spacing.md))
            Box(Modifier.onSizeChanged { foot = it.height }) { AnswerArea(state, onAnswer) }
            Reveal(state, scroll = scroll, imageMaxHeight = imageMax)
        }
    }
}

/** A duel upright: the question, the two cards to tap, and once answered the verdict with both scans under them. */
@Composable
private fun DuelQuestion(
    state: TriviaQuizUiState.Playing,
    onAnswer: (TriviaAnswer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val imageMax = imageRoom(maxHeight, padding = Spacing.md * 2)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        ) {
            QuestionPanel(state.question)
            Spacer(Modifier.height(Spacing.md))
            AnswerArea(state, onAnswer)
            Reveal(state, scroll = scroll, imageMaxHeight = imageMax)
        }
    }
}

/**
 * The tallest a card scan may be so the feedback line and the whole scan fit the visible list
 * ([viewport] high, less its [padding]) once scrolled into view; never below a legible minimum.
 */
@Composable
private fun imageRoom(viewport: Dp, padding: Dp): Dp {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    return (viewport - padding - FEEDBACK_ROOM * fontScale).coerceAtLeast(MIN_IMAGE_HEIGHT)
}

/** The card's painting in the question: none below this (large text), never taller than this. */
private val MIN_ART = 96.dp
private val MAX_ART = 320.dp

/** The card's painting beside the answers when held sideways. */
private val LANDSCAPE_ART = 120.dp

/** Room for the feedback line (up to two lines) and its spacing above the scan. */
private val FEEDBACK_ROOM = 96.dp
private val MIN_IMAGE_HEIGHT = 160.dp

/** A confirm tick for a right answer, a reject buzz for a wrong one; once per answer. */
@Composable
private fun AnswerHaptics(state: TriviaQuizUiState.Playing) {
    val haptics = LocalHapticFeedback.current
    var felt by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.number, state.correct) {
        val correct = state.correct ?: return@LaunchedEffect
        if (felt == state.number) return@LaunchedEffect
        felt = state.number
        if (state.hapticsEnabled) {
            haptics.performHapticFeedback(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        }
    }
}

@Composable
private fun QuizHeader(state: TriviaQuizUiState.Playing, modifier: Modifier = Modifier) {
    val length = state.length
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (length != null) "Question ${state.number} of $length" else "Question ${state.number}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                buildString {
                    append("${state.correctCount} right")
                    if (state.streak >= 2) append(" · ${state.streak} in a row")
                },
                style = GoaTextStyles.numeric,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            playingAsLine(state.playerName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (length != null) {
            Spacer(Modifier.height(Spacing.sm))
            val done = state.number - if (state.answered) 0 else 1
            GoaProgressBar(
                { done.toFloat() / length },
                Modifier.fillMaxWidth().semantics { contentDescription = "$done of $length answered" },
            )
        }
    }
}

@Composable
private fun NextBar(state: TriviaQuizUiState.Playing, onNext: () -> Unit) {
    GoaPlateTheme {
        Column(Modifier.slatePlate(RectangleShape, brackets = false)) {
            // Always in its slot, so the answers above never move: off, and saying what it waits for, until answered.
            GoaButton(
                onClick = onNext,
                enabled = state.answered,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    when {
                        !state.answered -> "Answer to go on"
                        state.lastQuestion -> "See results"
                        else -> "Next"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
