package dev.quinton.goa.feature.trivia.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.quinton.goa.core.data.trivia.TriviaQuizUiState
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Motion
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
                TextButton(onClick = onStop, modifier = Modifier.semantics { contentDescription = "End round" }) {
                    Text("End")
                }
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
                    action = { OutlinedButton(onClick = onDone) { Text("Back to setup") } },
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
                    QuestionPanel(state.question)
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
                val scroll = rememberScrollState()
                BoxWithConstraints(Modifier.weight(1f)) {
                    val imageMax = imageRoom(maxHeight, padding = Spacing.lg * 2)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                    ) {
                        QuizHeader(state)
                        Spacer(Modifier.height(Spacing.lg))
                        QuestionPanel(state.question)
                        Spacer(Modifier.height(Spacing.lg))
                        AnswerArea(state, onAnswer)
                        Reveal(state, scroll = scroll, imageMaxHeight = imageMax)
                    }
                }
                NextBar(state, onNext)
            }
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
private fun QuizHeader(state: TriviaQuizUiState.Playing) {
    val length = state.length
    Column(Modifier.fillMaxWidth()) {
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
            LinearProgressIndicator(
                progress = { done.toFloat() / length },
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "$done of $length answered"
                },
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun NextBar(state: TriviaQuizUiState.Playing, onNext: () -> Unit) {
    AnimatedVisibility(
        visible = state.answered,
        enter = fadeIn(Motion.standard(Motion.SHORT_MS)),
        exit = fadeOut(Motion.standard(Motion.SHORT_MS)),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                EngravedRule()
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                        .heightIn(min = 56.dp),
                ) {
                    Text(
                        if (state.lastQuestion) "See results" else "Next",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
