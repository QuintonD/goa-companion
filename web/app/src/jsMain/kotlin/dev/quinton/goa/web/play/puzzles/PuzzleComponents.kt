package dev.quinton.goa.web.play.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.feature.board.play.puzzles.NudgeUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/*
 * The puzzles' own pieces (PRODUCT §10), as the phone's PuzzleComponents: difficulty in hexes and
 * words, the goal banner, the nudge card, the solved seal, the explanation.
 */

/** Difficulty as three small hexes, filled up to [level], and the word: shape and words, never colour alone. */
@Composable
fun DifficultyMark(level: Int, showLabel: Boolean = true) {
    val label = PuzzleText.difficulty(level)
    Span({
        classes("goa-puzzle-difficulty")
        attr("role", "img")
        attr("aria-label", "Difficulty: $label")
    }) {
        Span({
            classes("goa-puzzle-pips")
            attr("aria-hidden", "true")
            ref { el ->
                el.innerHTML = (1..MAX_DIFFICULTY).joinToString("") { i -> hexPip(filled = i <= level) }
                onDispose { }
            }
        })
        if (showLabel) Span({ attr("aria-hidden", "true") }) { Text(label) }
    }
}

private fun hexPip(filled: Boolean): String {
    val cls = if (filled) "filled" else ""
    return "<svg viewBox=\"-1 -1 2 2\" class=\"$cls\">" +
        "<polygon points=\"0,-0.9 0.78,-0.45 0.78,0.45 0,0.9 -0.78,0.45 -0.78,-0.45\"/></svg>"
}

/** The puzzle's number in a bronze ring (filled once solved). */
@Composable
fun NumberMark(number: Int, solved: Boolean) {
    Span({
        classes("goa-puzzle-number", "t-title-large")
        if (solved) classes("solved")
        attr("aria-hidden", "true")
    }) { Text("$number") }
}

/**
 * The seal on a solved puzzle: a bronze hex with a tide-teal tick and six points of light, which
 * opens out once (original, drawn in SVG; still when the person prefers less motion).
 */
@Composable
fun SolvedSeal(size: Int = SEAL_SIZE) {
    Span({
        classes("goa-puzzle-seal")
        attr("aria-hidden", "true")
        style {
            property("width", "${size}px")
            property("height", "${size}px")
        }
        ref { el ->
            el.innerHTML = SEAL_SVG
            onDispose { }
        }
    })
}

private const val SEAL_SIZE = 64

private const val SEAL_SVG =
    "<svg viewBox=\"-1 -1 2 2\"><g class=\"rays\">" +
        "<path d=\"M0 -0.8V-0.98M0.69 -0.4L0.85 -0.49M0.69 0.4L0.85 0.49M0 0.8V0.98M-0.69 0.4L-0.85 0.49" +
        "M-0.69 -0.4L-0.85 -0.49\"/></g>" +
        "<polygon class=\"hex\" points=\"0,-0.68 0.59,-0.34 0.59,0.34 0,0.68 -0.59,0.34 -0.59,-0.34\"/>" +
        "<path class=\"tick\" d=\"M-0.3 0.02L-0.08 0.24L0.32 -0.22\"/></svg>"

/**
 * The goal, under the table's top row: what to achieve and who the player controls. [compact]
 * (landscape, where the map has little height): one line; a tap opens it in full and folds it again.
 */
@Composable
fun GoalBanner(goal: String, playing: List<String>, compact: Boolean) {
    val you = if (playing.isEmpty()) "" else "You play ${PuzzleText.names(playing)}"
    var open by remember { mutableStateOf(false) }
    val folded = compact && !open
    Button({
        classes("goa-puzzle-goal")
        if (folded) classes("folded")
        attr("type", "button")
        attr("aria-expanded", (!folded).toString())
        attr("aria-label", listOf("Goal: ${goal.trimEnd('.')}.", you).joinToString(" ").trim())
        if (!compact) attr("tabindex", "-1")
        onClick { if (compact) open = !open }
    }) {
        Icon("flag", IconSize.SMALL)
        Span({ classes("goa-puzzle-goal-text") }) {
            Span({ classes("goal") }) { Text(goal) }
            if (you.isNotEmpty()) Span({ classes("you") }) { Text(you) }
        }
    }
}

/**
 * A gentle nudge (not a dialog): "Nudge 2 of 3", the text, the option it points at once it is the
 * last one, "Another nudge" while there are more, and a close button.
 */
@Composable
fun NudgeCard(nudge: NudgeUi, onMore: () -> Unit, onDismiss: () -> Unit) {
    Div({
        classes("goa-puzzle-nudge")
        attr("role", "note")
    }) {
        Div({ classes("goa-puzzle-nudge-head") }) {
            Icon("lightbulb")
            H3({ classes("t-section-head") }) { Text("Nudge ${nudge.level} of ${nudge.total}") }
            IconButton("close", "Hide the nudge", onDismiss)
        }
        P({
            classes("t-body-large")
            attr("aria-live", "polite")
        }) { Text(nudge.text) }
        nudge.hint?.let { P({ classes("t-label-large") }) { Text("Outlined in bronze: $it") } }
        if (nudge.more) GoaButton("Another nudge", onMore, ButtonKind.OUTLINED)
    }
}

/** Why it works, in the puzzle's words, and the rulebook pages it leans on. */
@Composable
fun Explanation(info: PuzzleInfo) {
    Div({ classes("goa-puzzle-explanation") }) {
        H3({ classes("t-section-head") }) { Text("Why it works") }
        P({ classes("t-body-medium") }) { Text(info.explanation) }
        if (info.rules.isNotEmpty()) {
            H3({ classes("t-section-head") }) { Text("Rules") }
            Ul({ classes("goa-puzzle-rules", "t-body-small") }) { info.rules.forEach { Li { Text(it) } } }
        }
    }
}

private const val MAX_DIFFICULTY = 3
