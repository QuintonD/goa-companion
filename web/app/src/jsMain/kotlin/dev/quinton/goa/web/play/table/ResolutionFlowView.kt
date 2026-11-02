package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.play.table.FlowEntry
import dev.quinton.goa.feature.board.play.table.FlowKind
import dev.quinton.goa.feature.board.play.table.FlowStep
import dev.quinton.goa.feature.board.play.table.ResolutionFlow
import dev.quinton.goa.feature.board.play.table.StepKind
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The resolution flow (ADR-046, the phone's ResolutionFlowView): one line per hero's card resolution since the
 * person last chose, each opening to its steps: the action, moves, each attack, the defending card as the card
 * (tap to read it), discards with the open card, pushes, coins, defeats, effects. An entry where an enemy
 * defended against (or was defeated by) one of the person's heroes starts open; lines a player must not miss are
 * bold with a bar in the accent colour. VoiceOver reads each entry as a sentence and says whether it is expanded.
 */
@Composable
fun ResolutionFlowView(flow: ResolutionFlow, table: TableUi, focus: CardFocusState) {
    if (flow.isEmpty) return
    Div({ classes("goa-flow") }) {
        if (flow.earlier >
            0
        ) {
            Quiet("${flow.earlier} earlier ${if (flow.earlier == 1) "card" else "cards"} in the game log")
        }
        flow.entries.forEach { entry ->
            key(entry.key) { FlowEntryView(entry, table, focus, earlier = !flow.fresh) }
        }
    }
}

@Composable
private fun FlowEntryView(entry: FlowEntry, table: TableUi, focus: CardFocusState, earlier: Boolean) {
    var open by remember(entry.key) { mutableStateOf(entry.open) }
    Div({
        classes("goa-flow-entry")
        if (entry.important) classes("important")
        if (entry.open) classes("opened")
        if (earlier) classes("earlier")
    }) {
        Button({
            classes("goa-flow-head")
            attr("type", "button")
            attr("aria-label", spokenEntry(entry, table, earlier))
            attr("aria-expanded", open.toString())
            onClick { open = !open }
        }) { EntryHeader(entry, table, open, earlier) }
        if (open) {
            Div({ classes("goa-flow-steps") }) {
                entry.steps.forEach { step ->
                    // The action chosen shows the card it was chosen on (tap to read it).
                    val cards = if (step.kind == StepKind.ACTION) listOfNotNull(entry.cardId) else step.cards
                    StepRow(entry, step, cards.mapNotNull(table::card), table, focus)
                }
            }
        }
    }
}

@Composable
private fun EntryHeader(entry: FlowEntry, table: TableUi, open: Boolean, earlier: Boolean) {
    EntryBadge(entry, table)
    entry.cardId?.let(table::card)?.let { card -> CardImage(card, HEADER_CARD) }
    Span({ classes("goa-flow-text") }) {
        val who = buildList {
            add(entry.title.uppercase())
            if (entry.mine) add("YOU")
            if (earlier) add("EARLIER")
        }.joinToString(" · ")
        Span({
            classes("t-overline", "goa-flow-who")
            entry.team?.let { classes(teamCss(it)) }
        }) { Text(who) }
        Span({
            classes("t-body-medium")
            if (entry.important || entry.open) classes("goa-flow-strong")
        }) { Text(entry.line) }
    }
    Icon(if (open) "chevron_up" else "chevron_down", IconSize.SMALL)
}

/** The hero's portrait in its team's ring, or a mark for the turn's start and the end of the round. */
@Composable
private fun EntryBadge(entry: FlowEntry, table: TableUi) {
    val hero = entry.heroId
    if (hero != null) {
        HeroPortrait(table.hero(hero), entry.team, BADGE)
    } else {
        Span({ classes("goa-flow-mark") }) { Icon(if (entry.kind == FlowKind.END_OF_ROUND) "flag" else "g_history") }
    }
}

@Composable
private fun StepRow(
    entry: FlowEntry,
    step: FlowStep,
    cards: List<Card>,
    table: TableUi,
    focus: CardFocusState,
) {
    Div({
        classes("goa-flow-step")
        if (step.important) classes("important")
    }) {
        cards.forEachIndexed { at, card -> StepCard(entry, at, card, step, cards, table, focus) }
        Div({ classes("goa-flow-step-text") }) {
            P({
                classes("t-body-small")
                if (step.important) classes("goa-flow-strong")
            }) { Text(step.text) }
            step.success?.let { held ->
                // Words and shape, not colour alone (as the Defend question's verdicts).
                P({ classes("t-label-medium", "goa-flow-verdict", if (held) "held" else "lost") }) {
                    Icon(if (held) "check" else "g_error", IconSize.SMALL)
                    Text(if (held) "Defended" else "Defeated")
                }
            }
        }
    }
}

/** A card in a step: the defending card, the open card a discard names, the card the action was on. */
@Composable
private fun StepCard(
    entry: FlowEntry,
    at: Int,
    card: Card,
    step: FlowStep,
    cards: List<Card>,
    table: TableUi,
    focus: CardFocusState,
) {
    val owner = step.heroId ?: entry.heroId
    val whose = owner?.let { "${table.hero(it).name}'s " }.orEmpty()
    val what = if (step.kind == StepKind.DEFENSE) "defense card" else "card"
    Button({
        classes("goa-thumb")
        attr("type", "button")
        attr("aria-label", "$whose$what: ${spokenCard(card)}")
        onClick {
            focus.open(
                cards.mapIndexed { i, c -> FocusCard(c, caption = if (i == at) caption(step) else null) },
                index = at,
                title = owner?.let { table.hero(it).name },
            )
        }
    }) { CardImage(card, if (step.kind == StepKind.DEFENSE) DEFENSE_CARD else STEP_CARD) }
}

/** The viewer's caption for a step's card: "Against Attack 4 · Defended". */
private fun caption(step: FlowStep): String? = when (step.kind) {
    StepKind.DEFENSE -> listOfNotNull(
        step.attack?.let { "Against Attack $it" },
        step.text.substringAfter(": ", "").removeSuffix(".").ifEmpty { null },
    ).joinToString(" · ").ifEmpty { null }

    else -> step.text.removeSuffix(".")
}

/** One sentence for VoiceOver: "Brogan, your hero, played Onslaught: Wasp defended with Lift Up (5 against 4)." */
private fun spokenEntry(entry: FlowEntry, table: TableUi, earlier: Boolean): String {
    val prefix = if (earlier) "Earlier: " else ""
    val hero = entry.heroId ?: return prefix + entry.spoken
    val whose = if (entry.mine) "your hero" else table.heroView(hero)?.let { table.controllerLabel(it) }
    val who = listOfNotNull(entry.title, whose).joinToString(", ")
    val card = entry.cardId?.let(table::card)?.name
    val outcome = entry.line.substringAfter(" → ", entry.line).replace(" vs ", " against ")
    val did = if (card != null) "played $card: $outcome" else outcome
    return "$prefix$who, $did."
}

private const val BADGE = 32
private const val HEADER_CARD = 30
private const val STEP_CARD = 40
private const val DEFENSE_CARD = 48
