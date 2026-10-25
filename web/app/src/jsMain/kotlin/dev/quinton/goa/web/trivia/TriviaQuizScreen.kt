package dev.quinton.goa.web.trivia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.trivia.MissUi
import dev.quinton.goa.core.data.trivia.QuizQuestionUi
import dev.quinton.goa.core.data.trivia.TriviaCardUi
import dev.quinton.goa.core.data.trivia.TriviaQuizModel
import dev.quinton.goa.core.data.trivia.TriviaQuizRequest
import dev.quinton.goa.core.data.trivia.TriviaQuizUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LinearProgress
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/** How an answer button looks once the question is answered. Never colour alone: icons too. */
private enum class Mark(val css: String) { OPEN("open"), RIGHT("right"), WRONG("wrong"), OTHER("other") }

/**
 * A quiz round or the Challenge (ADR-028, ADR-032), run by the shared [TriviaQuizModel]: one
 * question at a time, then the round's summary. End shows the summary at any point; Back once a
 * question is answered asks first (the answers are saved, the round's results would go).
 */
@Composable
fun TriviaQuizScreen(route: Route.TriviaQuiz) {
    val services = LocalServices.current
    val content = services.data.content
    val group = rememberTriviaGroup()
    val scope = rememberScreenScope()
    val request = TriviaQuizRequest(route.config, route.factKeys, route.challenge)
    val model = remember {
        TriviaQuizModel(
            scope,
            request,
            content.catalog,
            content.settings,
            content.triviaProgress,
            group.players,
            group.groups,
            group.rounds,
            services.data.clock,
        )
    }
    val state by model.uiState.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    val playing = state as? TriviaQuizUiState.Playing
    val askFirst = playing?.started == true
    AnswerTimeFollowsVisibility(model)

    Screen(
        title = when {
            state is TriviaQuizUiState.Summary -> "Round over"
            playing?.challenge == true -> "Challenge"
            else -> "Card trivia"
        },
        showBack = true,
        onBack = { if (askFirst) confirmLeave = true else services.router.back() },
        actions = {
            if (playing != null) {
                Button({
                    type(ButtonType.Button)
                    classes("goa-trivia-end", "t-label-large")
                    attr("aria-label", "End round")
                    onClick { model.stop() }
                }) { Text("End") }
            }
        },
    ) {
        when (val current = state) {
            TriviaQuizUiState.Loading -> LoadingState("Loading quiz")

            TriviaQuizUiState.Empty -> EmptyState(
                "trivia",
                "Nothing to ask",
                "None of the chosen cards print these numbers. Go back and add a number or more cards.",
            ) { GoaButton("Back to setup", { services.router.back() }, ButtonKind.OUTLINED) }

            is TriviaQuizUiState.Playing -> QuizPlaying(current, model::answer, model::next)

            is TriviaQuizUiState.Summary -> SummaryContent(
                state = current,
                onPractise = {
                    val practise = model.practiseRoute()
                    when {
                        practise == null -> Unit

                        // The same facts and setup again (practising a practice round): restart in place.
                        practise == request -> model.playAgain()

                        else -> services.router.navigate(practise.toRoute(), replace = true)
                    }
                },
                onPlayAgain = model::playAgain,
                onDone = { services.router.back() },
                onLeaderboard = { services.router.navigate(Route.TriviaLeaderboard, replace = true) },
            )
        }
    }
    if (confirmLeave && askFirst) {
        AlertDialog("Leave this round?", onDismiss = { confirmLeave = false }, buttons = {
            GoaButton("Leave", {
                confirmLeave = false
                services.router.back()
            }, ButtonKind.TEXT)
            GoaButton("See results", {
                confirmLeave = false
                model.stop()
            })
        }) { Text("Your answers are saved. The round's results won't be.") }
    }
}

/**
 * Answer time only runs while the quiz is in view: the page hidden (another app, the phone locked)
 * or the screen left pauses it, as Android's lifecycle does.
 */
@Composable
private fun AnswerTimeFollowsVisibility(model: TriviaQuizModel) {
    DisposableEffect(model) {
        val listener: (Event) -> Unit = { model.onVisible(document.asDynamic().visibilityState == "visible") }
        model.onVisible(document.asDynamic().visibilityState == "visible")
        document.addEventListener("visibilitychange", listener)
        onDispose {
            document.removeEventListener("visibilitychange", listener)
            model.onVisible(false)
        }
    }
}

@Composable
private fun QuizPlaying(state: TriviaQuizUiState.Playing, onAnswer: (TriviaAnswer) -> Unit, onNext: () -> Unit) {
    AnswerFeel(state)
    Div({ classes("goa-trivia-quiz") }) {
        Div({ classes("goa-trivia-quiz-question") }) {
            QuizHeader(state)
            QuestionPanel(state.question)
        }
        Div({ classes("goa-trivia-quiz-answers") }) {
            AnswerArea(state, onAnswer)
            Reveal(state)
        }
    }
    if (state.answered) {
        Div({ classes("goa-trivia-nextbar") }) {
            GoaButton(if (state.lastQuestion) "See results" else "Next", onNext, block = true)
        }
    }
}

/**
 * A short buzz for a right or wrong answer where the browser can (Android Chrome); iPhones have no
 * vibration for the web, the tick or cross and the words carry it. Once per answer.
 */
@Composable
private fun AnswerFeel(state: TriviaQuizUiState.Playing) {
    LaunchedEffect(state.number, state.correct) {
        val correct = state.correct ?: return@LaunchedEffect
        val navigator = window.navigator.asDynamic()
        if (state.hapticsEnabled && navigator.vibrate != null) {
            navigator.vibrate(if (correct) RIGHT_BUZZ else WRONG_BUZZ)
        }
    }
}

@Composable
private fun QuizHeader(state: TriviaQuizUiState.Playing) {
    val length = state.length
    Div({ classes("goa-trivia-quiz-header") }) {
        Div({ classes("goa-trivia-quiz-count") }) {
            Span({ classes("t-title-small") }) {
                Text(if (length != null) "Question ${state.number} of $length" else "Question ${state.number}")
            }
            Span({ classes("t-numeric", "c-variant") }) {
                Text(
                    buildString {
                        append("${state.correctCount} right")
                        if (state.streak >= 2) append(" · ${state.streak} in a row")
                    },
                )
            }
        }
        P({ classes("t-label-medium", "c-variant") }) { Text(TriviaText.playingAsLine(state.playerName)) }
        if (length != null) {
            val done = state.number - if (state.answered) 0 else 1
            LinearProgress(done.toDouble() / length, "$done of $length answered")
        }
    }
}

@Composable
private fun QuestionPanel(question: QuizQuestionUi) {
    when (question) {
        is QuizQuestionUi.Number -> {
            GoaCard {
                HeroLine(question.card)
                P({ classes("goa-trivia-card-name", "t-headline-small") }) { Text(question.card.name) }
                CardKindLine(question.card)
            }
            H2({
                classes("goa-trivia-ask", "t-display-small")
                attr("aria-label", "What is its ${TriviaText.stat(question.stat)}?")
            }) { Text("${TriviaText.stat(question.stat)}?") }
        }

        is QuizQuestionUi.Duel -> Div({ classes("goa-trivia-duel-ask") }) {
            H2({ classes("t-headline-small", "c-primary") }) { Text("Which has the higher initiative?") }
            P({ classes("t-body-medium", "c-variant") }) { Text("Tap a card, or Same initiative if they match.") }
        }
    }
}

@Composable
private fun AnswerArea(state: TriviaQuizUiState.Playing, onAnswer: (TriviaAnswer) -> Unit) {
    when (val q = state.question) {
        is QuizQuestionUi.Number -> NumberChoices(q, state.answer as? TriviaAnswer.Number, onAnswer)
        is QuizQuestionUi.Duel -> DuelChoices(q, state.answer as? TriviaAnswer.Duel, onAnswer)
    }
}

@Composable
private fun NumberChoices(q: QuizQuestionUi.Number, answer: TriviaAnswer.Number?, onAnswer: (TriviaAnswer) -> Unit) {
    val recall = q.choices.size > CHOICE_GRID
    Div({
        classes("goa-trivia-choices", if (recall) "recall" else "four")
        attr("role", "group")
        attr("aria-label", "Answers")
    }) {
        q.choices.forEach { value ->
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
                extraClass = "number",
                onClick = { onAnswer(TriviaAnswer.Number(value)) },
            )
        }
    }
}

/**
 * An answer button. Once answered, the right/wrong mark sits as a small badge in its corner; a
 * screen reader hears "6, right answer" or "3, your answer, wrong".
 */
@Composable
private fun ChoiceButton(text: String, spoken: String, mark: Mark, extraClass: String, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-choice", extraClass, mark.css)
        attr("aria-label", withMark(spoken, mark))
        if (mark != Mark.OPEN) disabled()
        onClick { onClick() }
    }) {
        Span { Text(text) }
        MarkBadge(mark)
    }
}

@Composable
private fun MarkBadge(mark: Mark) {
    val icon = when (mark) {
        Mark.RIGHT -> "check"
        Mark.WRONG -> "close"
        else -> return
    }
    Span({
        classes("goa-trivia-badge", mark.css)
        attr("aria-hidden", "true")
    }) { Icon(icon, IconSize.SMALL) }
}

@Composable
private fun DuelChoices(q: QuizQuestionUi.Duel, answer: TriviaAnswer.Duel?, onAnswer: (TriviaAnswer) -> Unit) {
    fun mark(pick: DuelPick) = when {
        answer == null -> Mark.OPEN
        pick == q.right -> Mark.RIGHT
        pick == answer.pick -> Mark.WRONG
        else -> Mark.OTHER
    }
    Div({ classes("goa-trivia-duel") }) {
        DuelTile(q.first, q.firstValue, revealed = answer != null, mark(DuelPick.FIRST)) {
            onAnswer(TriviaAnswer.Duel(DuelPick.FIRST))
        }
        DuelTile(q.second, q.secondValue, revealed = answer != null, mark(DuelPick.SECOND)) {
            onAnswer(TriviaAnswer.Duel(DuelPick.SECOND))
        }
    }
    ChoiceButton("Same initiative", "Same initiative", mark(DuelPick.TIE), "tie", onClick = {
        onAnswer(TriviaAnswer.Duel(DuelPick.TIE))
    })
}

@Composable
private fun DuelTile(card: TriviaCardUi, value: Int, revealed: Boolean, mark: Mark, onClick: () -> Unit) {
    val spoken = buildString {
        append(TriviaText.spokenCard(card))
        if (revealed) append(", initiative $value")
    }
    Button({
        type(ButtonType.Button)
        classes("goa-trivia-duel-tile", mark.css)
        attr("aria-label", withMark(spoken, mark))
        if (mark != Mark.OPEN) disabled()
        onClick { onClick() }
    }) {
        HeroLine(card, small = true)
        Span({ classes("goa-trivia-card-name", "t-title-large") }) { Text(card.name) }
        CardKindLine(card, showPrimary = false)
        if (revealed) {
            Span({ classes("goa-trivia-duel-value", "t-title-medium") }) {
                MarkIcon(mark)
                Text("Initiative $value")
            }
        }
    }
}

/**
 * The feedback line and, once answered, the official card scan(s): the proof of the answer. The
 * feedback is a polite live region that is there before the answer, so a screen reader announces
 * it when it fills in. After an answer the page scrolls the scans into view.
 */
@Composable
private fun Reveal(state: TriviaQuizUiState.Playing) {
    Div({ classes("goa-trivia-reveal") }) {
        P({
            classes("goa-trivia-feedback", "t-title-medium")
            attr("aria-live", "polite")
        }) {
            val feedback = state.feedback
            if (feedback != null) {
                Span({
                    classes("goa-trivia-feedback-mark", if (state.correct == true) "right" else "wrong")
                    attr("aria-hidden", "true")
                }) { Icon(if (state.correct == true) "check" else "close", IconSize.SMALL) }
                Text(feedback)
            }
        }
        if (state.answered) {
            val cards = when (val q = state.question) {
                is QuizQuestionUi.Number -> listOf(q.card)
                is QuizQuestionUi.Duel -> listOf(q.first, q.second)
            }.filter { it.image != null }
            if (cards.isNotEmpty()) {
                Div({
                    classes("goa-trivia-scans")
                    if (cards.size > 1) classes("two")
                    ref { element ->
                        // The scan is the proof of the answer: bring it into view, above the Next bar.
                        window.requestAnimationFrame { scrollToEnd(element) }
                        onDispose { }
                    }
                }) {
                    cards.forEach { card ->
                        Img(card.image!!, alt = "Card image: ${card.name}") {
                            classes("goa-trivia-scan")
                            attr("decoding", "async")
                            // The page grows when a scan loads: bring it into view again; a missing one goes.
                            addEventListener("load") { scrollToEnd(it.nativeEvent.target as HTMLElement) }
                            addEventListener("error") { (it.nativeEvent.target as HTMLElement).style.display = "none" }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scrolls so the end of [element] sits above the pinned Next bar, but never so far that the
 * feedback line ("Not quite. …") goes under the top bar: on a short screen (landscape) the words
 * come first, the scan below them.
 */
private fun scrollToEnd(element: HTMLElement) {
    val bar = (document.querySelector(".goa-trivia-nextbar") as? HTMLElement)?.offsetHeight ?: 0
    val topBar = (document.querySelector(".goa-topbar") as? HTMLElement)?.offsetHeight ?: 0
    val feedback = document.querySelector(".goa-trivia-feedback") as? HTMLElement
    val bottom = element.getBoundingClientRect().bottom + window.scrollY
    val keepFeedback = feedback?.let { it.getBoundingClientRect().top + window.scrollY - topBar - SCROLL_MARGIN }
    val wanted = bottom + bar + SCROLL_MARGIN - window.innerHeight
    val target = if (keepFeedback == null) wanted else minOf(wanted, keepFeedback)
    if (target > window.scrollY) {
        val options: dynamic = js("({})")
        options.top = target
        options.behavior = "smooth"
        window.asDynamic().scrollTo(options)
    }
}

@Composable
private fun MarkIcon(mark: Mark) {
    when (mark) {
        Mark.RIGHT -> Span({ classes("goa-trivia-mark", "right") }) { Icon("check", IconSize.SMALL) }
        Mark.WRONG -> Span({ classes("goa-trivia-mark", "wrong") }) { Icon("close", IconSize.SMALL) }
        else -> Unit
    }
}

/** "6, right answer" / "3, your answer, wrong": the mark is part of what a screen reader reads. */
private fun withMark(spoken: String, mark: Mark): String = when (mark) {
    Mark.OPEN, Mark.OTHER -> spoken
    Mark.RIGHT -> "$spoken, right answer"
    Mark.WRONG -> "$spoken, your answer, wrong"
}

// ---- Summary ------------------------------------------------------------------------------------

/**
 * The end of a round: score, best streak, newly learned numbers, the misses and what next. A
 * Challenge adds its answer time and the player's place on the board; its actions sit right under
 * the score (the phone goes round the table).
 */
@Composable
private fun SummaryContent(
    state: TriviaQuizUiState.Summary,
    onPractise: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    val onBoard = state.challenge?.finished == true && state.playerName != null
    val actionsFirst = state.challenge != null
    Div({ classes("goa-trivia-summary") }) {
        ScoreCard(state)
        if (actionsFirst) SummaryActions(state, onBoard, onPractise, onPlayAgain, onDone, onLeaderboard)
        if (state.misses.isNotEmpty()) {
            SectionHeader("Missed")
            Div({ classes("goa-trivia-misses") }) { state.misses.forEach { MissRow(it) } }
        }
        if (!actionsFirst) SummaryActions(state, onBoard, onPractise, onPlayAgain, onDone, onLeaderboard)
    }
}

@Composable
private fun SummaryActions(
    state: TriviaQuizUiState.Summary,
    onBoard: Boolean,
    onPractise: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onLeaderboard: () -> Unit,
) {
    Div({ classes("goa-trivia-actions") }) {
        if (onBoard) GoaButton("Leaderboard", onLeaderboard, icon = "trophy", block = true)
        when {
            state.canPractise -> {
                GoaButton(
                    "Practise the missed ones",
                    onPractise,
                    if (onBoard) ButtonKind.OUTLINED else ButtonKind.FILLED,
                    block = true,
                )
                GoaButton("Play again", onPlayAgain, ButtonKind.OUTLINED, block = true)
            }

            onBoard -> GoaButton("Play again", onPlayAgain, ButtonKind.OUTLINED, block = true)

            else -> GoaButton("Play again", onPlayAgain, block = true)
        }
        GoaButton("Done", onDone, ButtonKind.TEXT, block = true)
    }
}

@Composable
private fun ScoreCard(state: TriviaQuizUiState.Summary) {
    val challenge = state.challenge
    GoaCard {
        Div({ classes("goa-trivia-score") }) {
            P({ classes("t-label-large", "c-primary") }) {
                Text((if (challenge != null) "Challenge · " else "") + TriviaText.playingAsLine(state.playerName))
            }
            H2({ classes("t-headline-small") }) {
                Text(if (state.asked == 0) "No questions answered" else "${state.correct} of ${state.asked} right")
            }
            P({
                classes("t-title-medium")
                if (challenge?.finished == true) classes("goa-trivia-standing")
                attr("aria-live", "polite")
            }) { TriviaText.challengeLine(state)?.let { Text(it) } }
            P({ classes("t-body-medium", "c-variant") }) {
                Text(
                    when {
                        state.asked == 0 -> "End a round after answering to see how it went."
                        state.misses.isEmpty() -> "Every answer right."
                        state.progressSaved -> "Missed numbers come back sooner next time."
                        else -> "This phone's learning progress stays as it was."
                    },
                )
            }
            if (state.saveFailed) {
                P({
                    classes("t-body-medium", "c-error")
                    attr("role", "alert")
                }) { Text("This round couldn't be saved to the leaderboard.") }
            }
            if (state.asked > 0) {
                Div({ classes("goa-trivia-metrics") }) {
                    Metric("${state.bestStreak}", "Best streak")
                    if (challenge != null) {
                        Metric(
                            TriviaText.answerTime(challenge.answerMillis),
                            "Answer time",
                            "Answer time ${TriviaText.spokenAnswerTime(challenge.answerMillis)}",
                        )
                    }
                    if (state.progressSaved) {
                        Metric(
                            "+${state.learned}",
                            if (state.learned == 1) "number learned" else "numbers learned",
                            "${TriviaText.count(state.learned, "number")} learned this round",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, spoken: String = "$label $value") {
    Div({
        classes("goa-trivia-metric")
        attr("role", "group")
        attr("aria-label", spoken)
    }) {
        Span({
            classes("t-timer-small", "goa-trivia-metric-value")
            attr("aria-hidden", "true")
        }) { Text(value) }
        Span({
            classes("t-label-large", "c-variant")
            attr("aria-hidden", "true")
        }) { Text(label) }
    }
}

@Composable
private fun MissRow(miss: MissUi) {
    val other = miss.other
    val names = listOfNotNull(
        TriviaText.spokenOwned(miss.card),
        other?.let(TriviaText::spokenOwned),
    ).joinToString(" vs ")
    GoaCard {
        Div({
            classes("goa-trivia-miss")
            attr("role", "group")
            attr("aria-label", "$names, ${miss.title}: you answered ${miss.yours}, the answer is ${miss.right}")
        }) {
            // Each card with its own hero: a duel's two cards are often from different heroes.
            MissCard(miss.card, showKind = other == null)
            if (other != null) {
                P({ classes("t-label-large", "c-variant") }) { Text("vs") }
                MissCard(other, showKind = false)
            }
            Div({
                classes("goa-trivia-miss-line")
                attr("aria-hidden", "true")
            }) {
                Span({ classes("t-label-large", "c-variant") }) { Text("${miss.title}:") }
                Span({ classes("goa-trivia-miss-value") }) {
                    MarkIcon(Mark.WRONG)
                    Span({ classes("t-numeric") }) { Text(miss.yours) }
                }
                Icon("arrow_forward", IconSize.SMALL)
                Span({ classes("goa-trivia-miss-value") }) {
                    MarkIcon(Mark.RIGHT)
                    Span({ classes("t-numeric") }) { Text(miss.right) }
                }
            }
        }
    }
}

@Composable
private fun MissCard(card: TriviaCardUi, showKind: Boolean) {
    Div({ attr("aria-hidden", "true") }) {
        HeroLine(card, small = true)
        P({ classes("goa-trivia-card-name", "t-title-medium") }) { Text(card.name) }
        if (showKind) CardKindLine(card, showPrimary = false)
    }
}

private const val CHOICE_GRID = 4
private const val SCROLL_MARGIN = 16
private val RIGHT_BUZZ = arrayOf(20)
private val WRONG_BUZZ = arrayOf(30, 60, 30)
