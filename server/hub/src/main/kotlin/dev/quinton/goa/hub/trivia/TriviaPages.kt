package dev.quinton.goa.hub.trivia

import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.MasterySummary
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.presentation.trivia.HeroProgressUi
import dev.quinton.goa.core.presentation.trivia.LeaveRound
import dev.quinton.goa.core.presentation.trivia.Mark
import dev.quinton.goa.core.presentation.trivia.MissUi
import dev.quinton.goa.core.presentation.trivia.QuizQuestionUi
import dev.quinton.goa.core.presentation.trivia.TriviaCardUi
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.core.presentation.trivia.TriviaText
import dev.quinton.goa.core.presentation.trivia.legend
import dev.quinton.goa.core.presentation.trivia.spokenCard
import dev.quinton.goa.core.presentation.trivia.spokenOwned
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.heroes.CARD_HEIGHT
import dev.quinton.goa.hub.heroes.CARD_WIDTH
import dev.quinton.goa.hub.heroes.colorDot
import dev.quinton.goa.hub.heroes.heroPortrait
import dev.quinton.goa.hub.heroes.unverifiedBadge
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.hubDialog
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.sectionHeader
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.progress
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.unsafe

/*
 * Card trivia pages. The setup is one GET form: every chip is a submit button carrying an action
 * (`act`) that the hub applies with the phone's reducer (so the last Number or Cards chip can't be
 * turned off), answered as an htmx swap of #trivia-home. A quiz question is a form per answer.
 */

private const val HOME = TriviaFeature.PATH
private const val QUIZ = TriviaFeature.QUIZ

internal fun FlowContent.triviaHome(state: TriviaHomeUiState.Ready) {
    form(action = HOME, method = FormMethod.get, classes = "trivia-home") {
        id = "trivia-home"
        attributes["hx-get"] = HOME
        attributes["hx-target"] = "#trivia-home"
        attributes["hx-swap"] = "outerHTML"
        TriviaSetupForm.fields(state.setup).forEach { (name, value) ->
            input(InputType.hidden, name = name) { this.value = value }
        }
        overallCard(state)
        sectionHeader("New quiz")
        setupBlock(state)
        div("by-hero-head") {
            h2("section-header") { +"By hero" }
            span("muted small") { +"Tap one to quiz it" }
        }
        div("hero-progress-list") { state.heroes.forEach { heroProgressRow(it) } }
        startBar(state)
    }
}

private fun FlowContent.overallCard(state: TriviaHomeUiState.Ready) {
    div("card overall") {
        h2("overall-title") { +state.overallTitle }
        p("muted") { +state.overallSubtitle }
        masteryBar(state.overall, "mastery-bar")
        masteryLegend(state.overall)
        p("muted small") { +"A number counts as learned after three right answers in a row." }
        if (state.canReset) {
            a("$HOME/reset", classes = "btn btn-text reset-link") {
                attributes["data-dialog-open"] = "reset-dialog"
                icon("restart_alt")
                span { +"Reset progress" }
            }
        }
    }
}

/** Learned, learning and new numbers as one segmented bar (decorative: the counts say it in words). */
private fun FlowContent.masteryBar(summary: MasterySummary, classes: String) {
    val total = summary.total.coerceAtLeast(1)
    val learned = BAR_WIDTH * summary.mastered / total
    val learning = BAR_WIDTH * summary.learning / total
    div(classes) {
        attributes["aria-hidden"] = "true"
        // Widths as SVG geometry (numbers only): the CSP forbids inline styles.
        unsafe {
            +(
                "<svg viewBox=\"0 0 $BAR_WIDTH 10\" preserveAspectRatio=\"none\" focusable=\"false\">" +
                    "<rect class=\"bar-learned\" x=\"0\" y=\"0\" width=\"$learned\" height=\"10\"/>" +
                    "<rect class=\"bar-learning\" x=\"$learned\" y=\"0\" width=\"$learning\" height=\"10\"/>" +
                    "</svg>"
                )
        }
    }
}

private const val BAR_WIDTH = 1000

private fun FlowContent.masteryLegend(summary: MasterySummary) {
    div("legend") {
        summary.legend().forEach { (label, count) ->
            span("legend-item legend-${label.lowercase()}") {
                span("legend-dot") { attributes["aria-hidden"] = "true" }
                span { +"$label ${TriviaText.number(count)}" }
            }
        }
    }
}

/** A toggle chip: submits the setup with [act]; the hub applies it (htmx keeps focus by id). */
private fun FlowContent.actChip(label: String, act: String, selected: Boolean, enabled: Boolean = true) {
    button(type = ButtonType.submit, classes = if (selected) "chip selected act-chip" else "chip act-chip") {
        id = "act-" + act.replace(':', '-')
        name = "act"
        value = act
        attributes["aria-pressed"] = selected.toString()
        if (!enabled) disabled = true
        if (selected) icon(Icons.CHECK)
        span { +label }
    }
}

private fun FlowContent.setupBlock(state: TriviaHomeUiState.Ready) {
    val setup = state.setup
    div("setup") {
        div("setup-item") {
            h3("setup-label") { +"Heroes" }
            heroPicker(state)
        }
        div("setup-item") {
            h3("setup-label") { +"Numbers" }
            div("chip-row") {
                TriviaStat.entries.forEach { actChip(TriviaText.stat(it), "stat:${it.key}", it in setup.stats) }
            }
        }
        div("setup-item") {
            h3("setup-label") { +"Cards" }
            div("chip-row") {
                CardGroup.entries.forEach { actChip(TriviaText.group(it), "group:${it.key}", it in setup.groups) }
            }
        }
        div("setup-item") {
            h3("setup-label") { +"Answer with" }
            div("chip-row") {
                actChip("4 choices", "style:choices", setup.answerStyle == AnswerStyle.CHOICES)
                actChip("Recall", "style:recall", setup.answerStyle == AnswerStyle.RECALL)
            }
            p("muted small") { +state.answerStyleHint }
        }
        div("setup-item") {
            h3("setup-label") { +"Questions" }
            div("chip-row") {
                TriviaConfig.LENGTHS.forEach { actChip(TriviaText.length(it), "len:$it", setup.length == it) }
            }
        }
        val enabled = state.initiativeSelected
        val checked = enabled && setup.duels
        button(type = ButtonType.submit, classes = "switch-row duels") {
            id = "act-duels"
            name = "act"
            value = if (checked) "duels:off" else "duels:on"
            attributes["role"] = "switch"
            attributes["aria-checked"] = checked.toString()
            if (!enabled) disabled = true
            span("switch-text") {
                span("switch-title") { +"Initiative duels" }
                span("muted small") { +state.duelsHint }
            }
            span(if (checked) "switch-track on" else "switch-track") { attributes["aria-hidden"] = "true" }
        }
    }
}

/** Heroes to quiz: All heroes, or any of the owned heroes (and a chosen one outside them). */
private fun FlowContent.heroPicker(state: TriviaHomeUiState.Ready) {
    details("hero-picker") {
        summary("hero-scope") {
            span("hero-scope-text") {
                span { +state.heroScopeSummary }
                span("muted small") { +TriviaText.count(state.heroScopeCount, "hero", "heroes") }
            }
            span("hero-scope-action") { +"Choose" }
        }
        div("picker-body") {
            p("muted small") {
                +if (state.setup.heroIds.isEmpty()) {
                    "All heroes"
                } else {
                    TriviaText.count(state.setup.heroIds.size, "hero", "heroes") + " chosen"
                }
            }
            label("visually-hidden") {
                htmlFor = "hero-filter"
                +"Search heroes"
            }
            input(InputType.search, classes = "picker-filter") {
                id = "hero-filter"
                placeholder = "Search heroes"
                attributes["data-hero-filter"] = ""
                attributes["autocomplete"] = "off"
                // Not part of the setup: the filter only hides rows in the browser.
                attributes["form"] = "no-form"
            }
            button(type = ButtonType.submit, classes = "picker-row") {
                id = "act-all"
                name = "act"
                value = "all"
                attributes["aria-pressed"] = state.setup.heroIds.isEmpty().toString()
                span("picker-check") { if (state.setup.heroIds.isEmpty()) icon(Icons.CHECK) }
                span("picker-text") {
                    span { +"All heroes" }
                    span("muted small") { +state.allHeroesSubtitle }
                }
            }
            state.heroOptions.forEach { option ->
                val chosen = option.heroId in state.setup.heroIds
                button(type = ButtonType.submit, classes = "picker-row") {
                    id = "act-hero-${option.heroId}"
                    name = "act"
                    value = "hero:${option.heroId}"
                    attributes["aria-pressed"] = chosen.toString()
                    attributes["data-name"] = listOfNotNull(option.name, option.title).joinToString(" ").lowercase()
                    span("picker-check") { if (chosen) icon(Icons.CHECK) }
                    heroPortrait(option.heroId, option.portrait, "picker-portrait")
                    span("picker-text") {
                        span { +option.name }
                        option.title?.let { span("muted small") { +it } }
                    }
                    if (!option.verified) unverifiedBadge(compact = true)
                }
            }
        }
    }
}

private fun FlowContent.heroProgressRow(hero: HeroProgressUi) {
    val p = hero.progress
    button(type = ButtonType.submit, classes = "hero-progress") {
        name = "only"
        value = hero.heroId
        // Posts the setup (htmx honours formaction); the hub answers with a full navigation to the quiz.
        attributes["formmethod"] = "post"
        attributes["formaction"] = "$HOME/start"
        if (!hero.canQuiz) disabled = true
        attributes["aria-label"] = buildString {
            append("Quiz ${hero.name}")
            if (!hero.verified) append(", unverified data")
            append(", ${p.mastered} of ${TriviaText.count(p.total, "number")} learned")
            if (p.learning > 0) append(", ${p.learning} learning")
            if (!hero.canQuiz) append(". No numbers with these settings")
        }
        heroPortrait(hero.heroId, hero.portrait, "progress-portrait")
        span("progress-text") {
            span("progress-head") {
                span("progress-name") { +hero.name }
                if (!hero.verified) unverifiedBadge(compact = true)
                span("progress-count") { +"${p.mastered}/${p.total}" }
            }
            masteryBar(p, "mastery-bar thin")
            if (!hero.canQuiz) span("muted small") { +"No numbers with these settings" }
        }
        if (hero.canQuiz) icon(Icons.CHEVRON, "row-chevron")
    }
}

/** Pinned under the list: the live count (or why Start is off) and Start quiz, always in reach. */
private fun FlowContent.startBar(state: TriviaHomeUiState.Ready) {
    div("start-bar") {
        p("start-line") { +state.startLine }
        state.startNote?.let { note ->
            p(if (state.canStart) "start-note" else "start-note error") {
                attributes["role"] = "status"
                +note
            }
        }
        button(type = ButtonType.submit, classes = "btn btn-primary start-button") {
            attributes["formmethod"] = "post"
            attributes["formaction"] = "$HOME/start"
            if (!state.canStart) disabled = true
            icon("play_arrow_fill")
            span { +"Start quiz" }
        }
    }
}

internal fun FlowContent.resetDialog(state: TriviaHomeUiState.Ready) {
    hubDialog("reset-dialog", "Reset progress?") { resetBody(state) }
}

internal fun FlowContent.resetConfirm(state: TriviaHomeUiState.Ready) {
    div("card") {
        h2 { +"Reset progress?" }
        resetBody(state)
    }
}

private fun FlowContent.resetBody(state: TriviaHomeUiState.Ready) {
    p { +state.resetMessage }
    form(action = "$HOME/reset", method = FormMethod.post, classes = "actions") {
        button(type = ButtonType.submit, classes = "btn btn-danger") {
            icon("restart_alt")
            span { +"Reset" }
        }
        a(HOME, classes = "btn btn-text") {
            attributes["data-dialog-close"] = ""
            +"Cancel"
        }
    }
}

// ---- Quiz -------------------------------------------------------------------------------------

internal fun FlowContent.quizSection(state: TriviaQuizUiState) {
    section("quiz") {
        id = "quiz"
        when (state) {
            is TriviaQuizUiState.Playing -> {
                attributes["data-started"] = state.started.toString()
                attributes["data-answered"] = state.answered.toString()
                playing(state)
            }

            is TriviaQuizUiState.Summary -> summary(state)

            TriviaQuizUiState.Empty -> section("empty-state") {
                span("empty-mark") { attributes["aria-hidden"] = "true" }
                h2 { +"Nothing to ask" }
                p { +"None of the chosen cards print these numbers. Go back and add a number or more cards." }
                linkButton("Back to setup", HOME, ButtonStyle.OUTLINED)
            }

            TriviaQuizUiState.Loading -> Unit
        }
    }
}

/** A POST form that htmx swaps into #quiz (a plain POST + redirect without JS). */
private fun FlowContent.quizForm(action: String, classes: String? = null, block: kotlinx.html.FORM.() -> Unit) {
    form(action = action, method = FormMethod.post, classes = classes) {
        attributes["hx-post"] = action
        attributes["hx-target"] = "#quiz"
        attributes["hx-swap"] = "outerHTML"
        block()
    }
}

private fun FlowContent.playing(state: TriviaQuizUiState.Playing) {
    div("quiz-header") {
        div("quiz-header-row") {
            p("quiz-title") { +state.title }
            p("quiz-score") { +state.scoreLine }
            quizForm("$QUIZ/end", "end-form") {
                button(type = ButtonType.submit, classes = "btn btn-text") {
                    attributes["aria-label"] = "End round"
                    +"End"
                }
            }
        }
        state.length?.let { length ->
            progress("quiz-progress") {
                attributes["max"] = length.toString()
                attributes["value"] = state.done.toString()
                attributes["aria-label"] = "${state.done} of $length answered"
            }
        }
    }
    when (val q = state.question) {
        is QuizQuestionUi.Number -> {
            div("card question-card") {
                heroLine(q.card)
                h2("question-card-name") { +q.card.name }
                kindLine(q.card)
            }
            h2("question-stat") {
                id = "question"
                attributes["tabindex"] = "-1"
                attributes["aria-label"] = "What is its ${TriviaText.stat(q.stat)}?"
                +"${TriviaText.stat(q.stat)}?"
            }
            numberChoices(state, q)
        }

        is QuizQuestionUi.Duel -> {
            h2("question-duel") {
                id = "question"
                attributes["tabindex"] = "-1"
                +"Which has the higher initiative?"
            }
            p("muted") { +"Tap a card, or Same initiative if they match." }
            duelChoices(state, q)
        }
    }
    reveal(state)
    if (state.answered) {
        quizForm("$QUIZ/next", "next-bar") {
            input(InputType.hidden, name = "n") { value = state.number.toString() }
            button(type = ButtonType.submit, classes = "btn btn-primary next-button") {
                id = "quiz-next"
                +state.nextLabel
            }
        }
    }
}

private fun FlowContent.heroLine(card: TriviaCardUi) {
    div("hero-line") {
        heroPortrait(card.heroId, card.portrait, "line-portrait")
        span("hero-line-name") { +card.heroName }
        if (!card.heroVerified) unverifiedBadge()
    }
}

/** "● Red · Tier II · Attack card" under a card name. */
private fun FlowContent.kindLine(card: TriviaCardUi, showPrimary: Boolean = true) {
    p("kind-line") {
        colorDot(card.color)
        span { +listOfNotNull(card.label.ifEmpty { null }, card.primary?.takeIf { showPrimary }).joinToString(" · ") }
    }
}

private fun markClass(mark: Mark): String = "mark-" + mark.name.lowercase()

/** "6, right answer" / "3, your answer, wrong": the mark is part of what a screen reader reads. */
private fun withMark(spoken: String, mark: Mark): String = when (mark) {
    Mark.RIGHT -> "$spoken, right answer"
    Mark.WRONG -> "$spoken, your answer, wrong"
    else -> spoken
}

private fun FlowContent.markIcon(mark: Mark) {
    when (mark) {
        Mark.RIGHT -> span("mark-icon right") { icon(Icons.CHECK) }
        Mark.WRONG -> span("mark-icon wrong") { icon(Icons.CLOSE) }
        else -> Unit
    }
}

private fun FlowContent.numberChoices(state: TriviaQuizUiState.Playing, q: QuizQuestionUi.Number) {
    val recall = q.choices.size > 4
    quizForm("$QUIZ/answer", if (recall) "choices recall" else "choices") {
        input(InputType.hidden, name = "n") { value = state.number.toString() }
        q.choices.forEach { value ->
            val mark = state.mark(value)
            button(type = ButtonType.submit, classes = "choice ${markClass(mark)}") {
                id = "choice-$value"
                name = "value"
                this.value = value.toString()
                if (state.answered) disabled = true
                attributes["aria-label"] = withMark(TriviaText.spokenValue(value, q.plus), mark)
                markIcon(mark)
                span("choice-value") { +TriviaText.value(value, q.plus) }
            }
        }
    }
}

private fun FlowContent.duelChoices(state: TriviaQuizUiState.Playing, q: QuizQuestionUi.Duel) {
    quizForm("$QUIZ/answer", "duel") {
        input(InputType.hidden, name = "n") { value = state.number.toString() }
        div("duel-tiles") {
            duelTile(state, q.first, q.firstValue, DuelPick.FIRST)
            duelTile(state, q.second, q.secondValue, DuelPick.SECOND)
        }
        val tie = state.mark(DuelPick.TIE)
        button(type = ButtonType.submit, classes = "choice tie ${markClass(tie)}") {
            id = "choice-tie"
            name = "pick"
            value = DuelPick.TIE.name
            if (state.answered) disabled = true
            attributes["aria-label"] = withMark("Same initiative", tie)
            markIcon(tie)
            span { +"Same initiative" }
        }
    }
}

private fun FlowContent.duelTile(state: TriviaQuizUiState.Playing, card: TriviaCardUi, value: Int, pick: DuelPick) {
    val mark = state.mark(pick)
    button(type = ButtonType.submit, classes = "duel-tile ${markClass(mark)}") {
        id = "choice-${pick.name.lowercase()}"
        name = "pick"
        this.value = pick.name
        if (state.answered) disabled = true
        val spoken = spokenCard(card) + if (state.answered) ", initiative $value" else ""
        attributes["aria-label"] = withMark(spoken, mark)
        heroLine(card)
        span("duel-name") { +card.name }
        kindLine(card, showPrimary = false)
        if (state.answered) {
            span("duel-value") {
                markIcon(mark)
                span { +"Initiative $value" }
            }
        }
    }
}

/** The feedback line (a live region present before the answer) and, once answered, the card scan(s). */
private fun FlowContent.reveal(state: TriviaQuizUiState.Playing) {
    div("reveal") {
        id = "reveal"
        p(if (state.correct == false) "feedback wrong" else "feedback") {
            id = "feedback"
            attributes["role"] = "status"
            attributes["aria-live"] = "polite"
            state.feedback?.let {
                if (state.correct == true) markIcon(Mark.RIGHT) else markIcon(Mark.WRONG)
                span { +it }
            }
        }
        if (!state.answered) return@div
        val cards = when (val q = state.question) {
            is QuizQuestionUi.Number -> listOf(q.card)
            is QuizQuestionUi.Duel -> listOf(q.first, q.second)
        }
        val images = cards.filter { it.image != null }
        if (images.isEmpty()) return@div
        div(if (images.size > 1) "scans two" else "scans") {
            images.forEach { card ->
                img(src = HubCatalog.url(card.image!!), alt = "Card image: ${card.name}") {
                    attributes["width"] = CARD_WIDTH
                    attributes["height"] = CARD_HEIGHT
                    attributes["decoding"] = "async"
                }
            }
        }
    }
}

private fun FlowContent.summary(state: TriviaQuizUiState.Summary) {
    div("card score-card") {
        h2("score-title") {
            id = "question"
            attributes["tabindex"] = "-1"
            +state.title
        }
        p("muted") { +state.subtitle }
        if (state.asked > 0) {
            div("score-metrics") {
                p("score-metric") {
                    attributes["aria-label"] = "Best streak ${state.bestStreak}"
                    span("score-value") { +state.bestStreak.toString() }
                    span("score-label") { +"Best streak" }
                }
                p("score-metric") {
                    attributes["aria-label"] = "${TriviaText.count(state.learned, "number")} learned this round"
                    span("score-value") { +"+${state.learned}" }
                    span("score-label") { +state.learnedLabel }
                }
            }
        }
    }
    if (state.misses.isNotEmpty()) {
        sectionHeader("Missed")
        div("misses") { state.misses.forEach { missRow(it) } }
    }
    div("summary-actions") {
        if (state.canPractise) {
            form(action = "$QUIZ/practise", method = FormMethod.post) {
                button(type = ButtonType.submit, classes = "btn btn-primary") { +"Practise the missed ones" }
            }
        }
        form(action = "$QUIZ/again", method = FormMethod.post) {
            button(
                type = ButtonType.submit,
                classes = if (state.canPractise) "btn btn-outlined" else "btn btn-primary",
            ) {
                +"Play again"
            }
        }
        a(HOME, classes = "btn btn-text") { +"Done" }
    }
}

private fun FlowContent.missRow(miss: MissUi) {
    val other = miss.other
    val names = listOfNotNull(spokenOwned(miss.card), other?.let(::spokenOwned)).joinToString(" vs ")
    div("card miss") {
        attributes["role"] = "group"
        attributes["aria-label"] = "$names, ${miss.title}: you answered ${miss.yours}, the answer is ${miss.right}"
        missCard(miss.card, showKind = other == null)
        if (other != null) {
            p("muted vs") { +"vs" }
            missCard(other, showKind = false)
        }
        p("miss-line") {
            span("muted") { +"${miss.title}:" }
            span("miss-yours") {
                markIcon(Mark.WRONG)
                span { +miss.yours }
            }
            icon("arrow_forward", "miss-arrow")
            span("miss-right") {
                markIcon(Mark.RIGHT)
                span { +miss.right }
            }
        }
    }
}

private fun FlowContent.missCard(card: TriviaCardUi, showKind: Boolean) {
    heroLine(card)
    p("miss-name") { +card.name }
    if (showKind) kindLine(card, showPrimary = false)
}

/** "Leave this round?" (trivia.js opens it from Back once a question is answered). */
internal fun FlowContent.leaveDialog() {
    hubDialog("leave-dialog", LeaveRound.TITLE) { leaveBody() }
}

/** The same question as a page, for browsers without JS. */
internal fun FlowContent.leaveConfirm() {
    div("card") {
        h2 { +LeaveRound.TITLE }
        leaveBody()
        a(QUIZ, classes = "btn btn-text") { +"Stay" }
    }
}

private fun FlowContent.leaveBody() {
    p { +LeaveRound.MESSAGE }
    div("actions") {
        form(action = "$QUIZ/end", method = FormMethod.post) {
            button(type = ButtonType.submit, classes = "btn btn-primary") { +LeaveRound.SEE_RESULTS }
        }
        form(action = "$QUIZ/leave", method = FormMethod.post) {
            button(type = ButtonType.submit, classes = "btn btn-outlined") { +LeaveRound.LEAVE }
        }
    }
}
