package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.DefenseLine
import dev.quinton.goa.feature.board.play.table.OfferedCard
import dev.quinton.goa.feature.board.play.table.PanelLayout
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.actionLabel
import dev.quinton.goa.feature.board.play.table.actionRule
import dev.quinton.goa.feature.board.play.table.attackMissing
import dev.quinton.goa.feature.board.play.table.cardAction
import dev.quinton.goa.feature.board.play.table.cardVerb
import dev.quinton.goa.feature.board.play.table.defenseLine
import dev.quinton.goa.feature.board.play.table.focusCard
import dev.quinton.goa.feature.board.play.table.focusTitle
import dev.quinton.goa.feature.board.play.table.kindTitle
import dev.quinton.goa.feature.board.play.table.notOffered
import dev.quinton.goa.feature.board.play.table.offeredCards
import dev.quinton.goa.feature.board.play.table.panelLayout
import dev.quinton.goa.feature.board.play.table.sourceReach
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.isSpell
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.play.cards.CardSelection
import dev.quinton.goa.web.play.cards.ItemBadge
import dev.quinton.goa.web.play.cards.verdictClass
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The question being asked and its answers, by kind (PRODUCT §9), card-first (ADR-041) as on the
 * phone's DecisionPanel: the asking card large beside its answers; actions as compact chips with the
 * game's icon and the card's value (only rules no card prints added); "Choose one" options, yes /
 * decline and numbers as buttons; spaces, units and tokens tapped on the board then confirmed (chips
 * too); cards (to play, discard, defend with, upgrade, keep) in the card focus viewer: the first tap
 * zooms, a second tap or its button answers. Defend shows the attacker's card with the Attack, then
 * the hand's cards each with Holds / Falls.
 *
 * Use it for any table (the game, a puzzle): [onSelect] marks a tentative choice (a space, a piece,
 * Don't defend), [onAnswer] answers, [focus] is the screen's card viewer, [busy] holds the answers.
 */
@Composable
fun DecisionPanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean = false,
) {
    val decision = table.decision ?: return
    val source = table.sourceCard
    val layout = panelLayout(decision.kind, source != null)
    Div({
        classes("goa-decision")
        // The question's id: a new question is a new panel (tests wait for it to change).
        attr("data-decision", decision.id.toString())
    }) {
        PromptHeader(
            table,
            decision,
            focus,
            prompt = decision.kind != DecisionKind.DEFEND,
            askedBy = layout == PanelLayout.CARDS && decision.kind != DecisionKind.DEFEND,
        )
        when {
            decision.kind == DecisionKind.PLAY_CARD -> HandPicker(table, onAnswer, focus, busy)

            decision.kind == DecisionKind.DEFEND -> DefensePanel(table, onSelect, onAnswer, focus, busy)

            layout == PanelLayout.CARDS -> CardPicker(table, onAnswer, focus, busy)

            layout == PanelLayout.CARD_BESIDE && source != null -> {
                val board = hasBoardOptions(table)
                Div({ classes("goa-beside", if (board) "small" else "large") }) {
                    AskingCard(table, source, focus, width = if (board) 72 else 120)
                    Div({ classes("goa-beside-answers") }) {
                        if (board) BoardHint(table, onAnswer, busy)
                        Choices(table, table.buttonOptions, onAnswer, busy)
                    }
                }
                if (board) BoardChips(table, onSelect)
            }

            else -> {
                if (hasBoardOptions(table)) {
                    BoardHint(table, onAnswer, busy)
                    BoardChips(table, onSelect)
                }
                Choices(table, table.buttonOptions, onAnswer, busy)
            }
        }
        if (decision.kind == DecisionKind.CHOOSE_ACTION && table.buttonOptions.any { it is Choice.Action }) {
            Quiet("Only the primary action applies the card's text (p.7).")
        }
        if (decision.kind == DecisionKind.ACT_FIRST) {
            Quiet(
                "Heroes of one team on the same initiative choose which of them acts first; between teams " +
                    "the Tie Breaker coin decides, then it flips (p.7).",
            )
        }
        if (decision.kind == DecisionKind.CHOOSE_ACTION && attackMissing(source, decision.options)) {
            Quiet("No Attack: no unit you may target is in reach (p.13), or an effect forbids it.")
        }
    }
}

private fun hasBoardOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

/** Who is asked and what (in the team's colour), the prompt (read aloud), and "Asked by <card>". */
@Composable
private fun PromptHeader(table: TableUi, decision: Decision, focus: CardFocusState, prompt: Boolean, askedBy: Boolean) {
    val name = table.decider?.let { table.hero(it.heroId).name } ?: BoardText.team(decision.team)
    P({ classes("t-overline", "goa-decision-who", teamCss(decision.team)) }) {
        TeamMark(decision.team)
        Text(" $name · ${kindTitle(decision.kind)}")
    }
    if (prompt) {
        P({
            classes("t-title-medium", "goa-decision-prompt")
            attr("aria-live", "polite")
        }) { Text(decision.prompt) }
    }
    val card = table.sourceCard
    if (askedBy && card != null) {
        val reach = sourceReach(decision.prompt, card)
        Button({
            classes("goa-asked-by")
            attr("type", "button")
            attr("aria-label", listOfNotNull("Asked by ${card.name}", reach).joinToString(", ") + ". Read the card")
            onClick { focus.show(listOf(card), title = "Asked by") }
        }) {
            CardImage(card, 28)
            Span({ classes("t-label-medium", "goa-quiet") }) { Text("Asked by ") }
            Span({ classes("t-label-large") }) { Text(card.name) }
            reach?.let { Span({ classes("t-label-large", "goa-quiet") }) { Text(" · $it") } }
        }
    }
}

/** The asking card beside its answers; tap it to read it; its reach under it when the question uses it. */
@Composable
private fun AskingCard(table: TableUi, card: Card, focus: CardFocusState, width: Int) {
    val reach = table.decision?.let { sourceReach(it.prompt, card) }
    Div({ classes("goa-asking") }) {
        Button({
            classes("goa-thumb")
            attr("type", "button")
            attr("aria-label", listOfNotNull(spokenCard(card), reach).joinToString(", ") + ". Read the card")
            onClick { focus.show(listOf(card), title = "Asked by") }
        }) { CardImage(card, if (card.isSpell) (width * 0.8).toInt() else width) }
        reach?.let { Span({ classes("t-label-medium", "goa-quiet") }) { Text(it) } }
    }
}

/** Spaces, units or tokens are tapped on the board: the hint, then Confirm with the choice spelled out. */
@Composable
private fun BoardHint(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    val spaces = table.spaceOptions
    val pieces = table.pieceOptions
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
    P({ classes("t-body-medium", "goa-tap-hint") }) {
        Icon("touch", IconSize.SMALL)
        Text(
            when {
                pieces.isEmpty() && spaces.size == 1 -> "Tap the space outlined in teal."
                pieces.isEmpty() -> "Tap one of the ${spaces.size} spaces outlined in teal."
                spaces.isEmpty() -> "Tap an outlined piece on the board, or pick it below."
                else -> "Tap an outlined space or piece on the board."
            },
        )
    }
    if (selected != null) {
        Button({
            classes("goa-button", "filled", "block", "goa-confirm")
            attr("type", "button")
            if (busy) attr("disabled", "")
            onClick { onAnswer(selected) }
        }) { Text("Confirm · ${table.label(selected)}") }
    }
}

/** Units and tokens (and a short list of spaces whose names differ) as chips too. */
@Composable
private fun BoardChips(table: TableUi, onSelect: (Choice) -> Unit) {
    val spaces = table.spaceOptions.values.toList()
    val pieces = table.pieceOptions.values.toList()
    val selected = table.selected
    val spaceChips = spaces.takeIf { list ->
        list.size <= MAX_SPACE_CHIPS && list.map(table::label).distinct().size == list.size
    }.orEmpty()
    val chips = pieces + spaceChips
    if (chips.isEmpty()) return
    Div({
        classes("goa-option-chips")
        attr("role", "radiogroup")
    }) {
        chips.forEach { choice ->
            Button({
                classes("goa-option-chip")
                if (choice == selected) classes("selected")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", (choice == selected).toString())
                onClick { onSelect(choice) }
            }) {
                if (choice == selected) Icon("check", IconSize.SMALL)
                Text(table.label(choice))
            }
        }
    }
}

/** Answers that go at once: action chips, "Choose one" options, yes / decline, numbers, pass. */
@Composable
private fun Choices(table: TableUi, choices: List<Choice>, onAnswer: (Choice) -> Unit, busy: Boolean) {
    if (choices.isEmpty()) return
    val numbers = choices.filterIsInstance<Choice.Number>()
    if (numbers.size == choices.size) {
        Div({ classes("goa-numbers") }) {
            numbers.forEach { n ->
                Button({
                    classes("goa-button", "outlined", "goa-number")
                    attr("type", "button")
                    if (busy) attr("disabled", "")
                    onClick { onAnswer(n) }
                }) { Text("${n.value}") }
            }
        }
        return
    }
    Div({ classes("goa-choices") }) {
        choices.forEach { choice ->
            when (choice) {
                is Choice.Action -> ActionChip(table, choice.action, busy) { onAnswer(choice) }

                is Choice.Decline, Choice.Pass -> Button({
                    classes("goa-button", "outlined", "block")
                    attr("type", "button")
                    if (busy) attr("disabled", "")
                    onClick { onAnswer(choice) }
                }) { Text(table.label(choice)) }

                else -> Button({
                    classes("goa-choice")
                    attr("type", "button")
                    if (busy) attr("disabled", "")
                    onClick { onAnswer(choice) }
                }) { Text(table.label(choice)) }
            }
        }
    }
}

/** One action: the game's icon and the card's value ("Movement 4"), the primary filled and marked. */
@Composable
private fun ActionChip(table: TableUi, action: ActionType, busy: Boolean, onClick: () -> Unit) {
    val card = table.sourceCard
    val primary = action == ActionType.PRIMARY
    val label = actionLabel(action, card)
    val rule = actionRule(action)
    Button({
        classes("goa-action-chip")
        if (primary) classes("primary")
        attr("type", "button")
        attr(
            "aria-label",
            listOfNotNull(label, "primary action, the card's text applies".takeIf { primary }, rule).joinToString(", "),
        )
        if (busy) attr("disabled", "")
        onClick { onClick() }
    }) {
        Icon(actionIconName(action, card))
        Span({ classes("goa-action-text") }) {
            if (primary) Span({ classes("t-overline") }) { Text("Primary") }
            Span({ classes("t-title-small") }) { Text(label) }
            rule?.let { Span({ classes("t-body-small") }) { Text(it) } }
        }
    }
}

// ---- Cards ---------------------------------------------------------------------------------------

/** Closes the viewer when its question is gone; its Confirm waits while the game is busy. */
@Composable
private fun FocusLifecycle(focus: CardFocusState, decision: Decision, busy: Boolean) {
    DisposableEffect(focus, decision.id) { onDispose { focus.closeFor(decision.id) } }
    LaunchedEffect(focus, decision.id, busy) { focus.setCanConfirm(decision.id, !busy) }
}

private fun openOffered(
    focus: CardFocusState,
    table: TableUi,
    offered: List<OfferedCard>,
    index: Int,
    onAnswer: (Choice) -> Unit,
    context: String? = null,
    cards: List<FocusCard> = offered.map { focusCard(table, it) },
) {
    val decision = table.decision ?: return
    focus.open(
        cards,
        index,
        selection = CardSelection(
            action = { cardAction(decision, it) },
            context = context ?: decision.prompt,
            onConfirm = { i -> offered.getOrNull(i)?.choice?.let(onAnswer) },
        ),
        title = focusTitle(table, decision),
        owner = decision.id,
    )
}

/** Turn step 1 (p.7): the hand as card images; tap one to see it large, again to play it facedown. */
@Composable
private fun HandPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    table.decider?.takeIf { it.at == null }?.let { hero ->
        P({ classes("t-body-medium", "goa-quiet") }) {
            Text(
                "${table.hero(hero.heroId).name} is off the board. When this card resolves you may respawn in " +
                    "your Throne Zone, then act with it (p.7).",
            )
        }
    }
    val offered = offeredCards(table)
    OfferedRow(table, offered) { i -> openOffered(focus, table, offered, i, onAnswer) }
    if (offered.any { it.choice != null }) TapHint("Tap a card to see it large; tap it again to play it facedown.")
    if (table.buttonOptions.isNotEmpty()) {
        Div({ classes("goa-choices", "row") }) {
            table.buttonOptions.forEach { choice ->
                Button({
                    classes("goa-button", "outlined")
                    attr("type", "button")
                    if (busy) attr("disabled", "")
                    onClick { onAnswer(choice) }
                }) { Text(table.label(choice)) }
            }
        }
    }
}

/** Card choices other than playing: discard, retrieve, which card to upgrade, which to keep. */
@Composable
private fun CardPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val offered = offeredCards(table)
    val keep = decision.kind == DecisionKind.KEEP_UPGRADE
    OfferedRow(table, offered, keep) { i -> openOffered(focus, table, offered, i, onAnswer) }
    if (offered.any { it.choice != null }) {
        val verb = when (decision.kind) {
            DecisionKind.UPGRADE_CARD -> "upgrade it"
            DecisionKind.KEEP_UPGRADE -> "keep it"
            else -> "${cardVerb(decision.prompt).lowercase()} it"
        }
        TapHint("Tap a card to see it large; tap it again to $verb.")
    }
    if (keep) Quiet("One goes to your hand; the other is tucked under your hero as an item (p.15, p.16).")
}

@Composable
private fun TapHint(text: String) {
    P({ classes("t-body-medium", "goa-tap-hint") }) {
        Icon("touch", IconSize.SMALL)
        Text(text)
    }
}

/**
 * The cards in a row, as large as fits (two to keep side by side are shown large, ADR-041); one the
 * question doesn't offer is dimmed and marked. Each opens the viewer.
 */
@Composable
private fun OfferedRow(table: TableUi, offered: List<OfferedCard>, keep: Boolean = false, onOpen: (Int) -> Unit) {
    val reason = table.decision?.kind?.let(::notOffered)
    val width = when {
        offered.size <= 2 -> 140
        offered.size == 3 -> 110
        else -> 84
    }
    Div({
        classes("goa-offered", "n${offered.size.coerceAtMost(MAX_CLASS)}")
        style { property("--card-w", "${width}px") }
    }) {
        offered.forEachIndexed { i, item ->
            val card = item.card
            val offeredNow = item.choice != null
            Div({ classes("goa-offered-cell") }) {
                Button({
                    classes("goa-thumb", "goa-offered-card")
                    if (!offeredNow) classes("dimmed")
                    attr("type", "button")
                    attr(
                        "aria-label",
                        listOfNotNull(
                            spokenCard(card),
                            if (keep) card.item?.let { "As an item: +1 ${PlayText.stat(it)}" } else null,
                            if (!offeredNow) reason else null,
                        ).joinToString(", ") + ". Look at the card",
                    )
                    onClick { onOpen(i) }
                }) { CardImage(card, width, "fluid") }
                if (keep) card.item?.let { ItemBadge(it) }
                if (!offeredNow) Span({ classes("goa-not-offered") }) { Icon("close", IconSize.SMALL) }
            }
        }
    }
}

/**
 * Attacked (p.14): the attacker's card with the Attack total, then the hand's cards, each with its
 * verdict under it ("Holds" / "Falls" in words and a mark) and the Defense total when it differs;
 * "Don't defend" is chosen, then confirmed.
 */
@Composable
private fun DefensePanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean,
) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val context = decision.context
    val attack = context.attackTotal
    val attacker = context.attackerId?.let(table::pieceName)
    val heading = listOfNotNull(
        attacker?.let { "$it attacks" },
        context.rangedAttack?.let { if (it) "ranged" else "melee" },
    ).joinToString(" · ").ifEmpty { "Attacked" }
    Div({
        classes("goa-attack")
        attr("aria-live", "polite")
    }) {
        table.sourceCard?.let { card ->
            Button({
                classes("goa-thumb")
                attr("type", "button")
                attr("aria-label", "Attacking card: ${spokenCard(card)}")
                onClick { focus.show(listOf(card), title = "Attacking card") }
            }) { CardImage(card, 56) }
        }
        Div {
            P({ classes("t-label-large") }) { Text(heading) }
            P({ classes("t-headline-small") }) { Text(attack?.let { "Attack $it" } ?: "Attack decided by the card") }
            context.minionModifier?.takeIf { it != 0 }?.let { m ->
                P({ classes("t-body-small") }) {
                    Text("Minion defense modifier ${if (m > 0) "+$m" else "$m"} is counted below (p.14).")
                }
            }
            P({ classes("t-body-small") }) { Text("Discard a card to defend") }
        }
    }
    val offered = table.cardOptions.mapNotNull { choice -> table.card(choice.cardId)?.let { OfferedCard(it, choice) } }
    val rows: List<DefenseLine> = offered.map { item ->
        defenseLine(context.defensePreview[item.card.id], context.blockPreview[item.card.id], attack)
    }
    if (attack != null && rows.isNotEmpty() && rows.all { it.verdict.holds == false }) {
        P({ classes("t-body-medium", "goa-quiet") }) {
            Text("None of your cards stops Attack $attack. Not defending keeps them in your hand (p.14).")
        }
    }
    if (offered.isNotEmpty()) TapHint("Tap a card to see it large; tap it again to defend with it.")
    val focusCards = offered.mapIndexed { i, item -> FocusCard(item.card, verdict = rows[i].verdict) }
    val summary = listOfNotNull(heading, attack?.let { "Attack $it" }).joinToString(" · ")
    Div({
        classes("goa-offered", "n${offered.size.coerceAtMost(MAX_CLASS)}")
        style { property("--card-w", "84px") }
    }) {
        offered.forEachIndexed { i, item ->
            val line = rows[i]
            val card = item.card
            Div({ classes("goa-offered-cell") }) {
                Button({
                    classes("goa-thumb", "goa-offered-card")
                    attr("type", "button")
                    attr(
                        "aria-label",
                        listOf(card.name, line.detail, line.verdict.label).joinToString(", ") + ". Look at the card",
                    )
                    onClick { openOffered(focus, table, offered, i, onAnswer, context = summary, cards = focusCards) }
                }) { CardImage(card, 84, "fluid") }
                Span({ classes("goa-verdict", "small", verdictClass(line.verdict.holds)) }) {
                    if (line.verdict.holds !=
                        null
                    ) {
                        Icon(if (line.verdict.holds == true) "check" else "close", IconSize.SMALL)
                    }
                    Text(line.verdict.label)
                }
                if (line.total != null && line.total != card.values.defense) {
                    Span({ classes("t-label-medium", "goa-quiet") }) {
                        Icon("g_defense", IconSize.SMALL)
                        Text(" ${line.total}")
                    }
                }
            }
        }
    }
    val declined = table.selected?.takeIf { it !is Choice.Card && it in table.buttonOptions }
    table.buttonOptions.forEach { choice ->
        Button({
            classes("goa-decline")
            if (choice == table.selected) classes("selected")
            attr("type", "button")
            attr("role", "radio")
            attr("aria-checked", (choice == table.selected).toString())
            onClick { onSelect(choice) }
        }) {
            Span({ classes("t-title-small") }) { Text(table.label(choice)) }
            Span({ classes("t-body-small", "goa-quiet") }) { Text("Keep your cards; the attack succeeds (p.14).") }
        }
    }
    if (declined != null) {
        Button({
            classes("goa-button", "filled", "block", "goa-confirm")
            attr("type", "button")
            if (busy) attr("disabled", "")
            onClick { onAnswer(declined) }
            ref { el ->
                el.scrollIntoView(js("({ block: 'nearest', behavior: 'smooth' })"))
                onDispose { }
            }
        }) { Text(table.label(declined)) }
    }
}

private const val MAX_SPACE_CHIPS = 6
private const val MAX_CLASS = 6
