package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.ActionIcon
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.LookAction
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateTone
import dev.quinton.goa.feature.board.ui.PlateToneSurface
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell

/**
 * The question being asked and its answers, by kind (PRODUCT §9: "every question says what is
 * asked and which card asks it; every option is shown on the board or as a button"). The card is
 * the content (ADR-041): the official card the question is about is shown large enough to read,
 * and the answers are tied to it rather than restating what it prints.
 * - actions are compact chips beside the card, with the game's icon and value; only what the card
 *   doesn't say is added (the rule of Fast Travel and Hold; that only the primary applies the text);
 *   under the Primary chip, what it will do before it is chosen: Gydion's spell as its card, its only
 *   target, a warning when it will do nothing (CastPreview.kt);
 * - "Choose one" options, yes / decline and numbers are buttons beside the card;
 * - spaces, units and tokens are tapped on the board (each also a TalkBack node), then confirmed,
 *   with the asking card beside the hint;
 * - cards (to play, discard, defend with, upgrade, keep) open in the card focus viewer: a first tap
 *   zooms the card, a second tap on it (or its Confirm button) answers (DecisionCards.kt);
 * - which card to upgrade shows, beside each, the two cards it would become (UpgradeCards.kt);
 * - Defend shows the attacker's card with the Attack, then the hand's cards with each verdict.
 * [onSelect] marks a tentative choice, [onAnswer] answers, [focus] is the screen's card viewer.
 */
@Composable
fun DecisionPanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    who: Boolean = true,
) {
    val decision = table.decision ?: return
    val source = table.sourceCard
    val layout = panelLayout(decision.kind, source != null)
    val beside = layout == PanelLayout.CARD_BESIDE && source != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PromptHeader(
            table,
            decision,
            focus,
            who = who,
            // Asked by a card: the prompt goes in the card's text box, beside it. Defend says it in its attack header.
            prompt = decision.kind != DecisionKind.DEFEND && !beside,
            askedBy = layout == PanelLayout.CARDS && decision.kind != DecisionKind.DEFEND,
        )
        when {
            decision.kind == DecisionKind.PLAY_CARD -> HandPicker(table, onAnswer, focus, busy)

            decision.kind == DecisionKind.DEFEND -> DefensePanel(table, onSelect, onAnswer, focus, busy)

            decision.kind == DecisionKind.UPGRADE_CARD -> UpgradePicker(table, onAnswer, focus, busy)

            layout == PanelLayout.CARDS -> CardPicker(table, onAnswer, focus, busy)

            layout == PanelLayout.CARD_BESIDE && source != null -> {
                val board = hasBoardOptions(table)
                CardBeside(table, source, focus, large = !board) {
                    QuestionBox(table, source, decision, board)
                    if (board) BoardConfirm(table, onAnswer, busy)
                    Choices(table, table.buttonOptions, onAnswer, busy) { CastPreview(table, focus) }
                    RuleBox(PlateColor.of(source.color), table, decision, focus)
                    SpellReasonsIfNoPrimary(table)
                }
                if (board) BoardChips(table, onSelect)
            }

            else -> {
                if (hasBoardOptions(table)) {
                    BoardHint(table)
                    BoardConfirm(table, onAnswer, busy)
                    BoardChips(table, onSelect)
                }
                Choices(table, table.buttonOptions, onAnswer, busy)
                RuleBox(PlateColor.SILVER, table, decision, focus)
            }
        }
    }
}

private fun hasBoardOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

/** The rule a question rests on that no card prints, in a line of its own. */
private fun footnote(table: TableUi, decision: Decision): String? = when {
    decision.kind == DecisionKind.CHOOSE_ACTION && table.buttonOptions.any { it is Choice.Action } ->
        "Only the primary action applies the card's text (p.7)."

    // A bare "who acts first?" over two portraits told nothing about why it was asked.
    decision.kind == DecisionKind.ACT_FIRST ->
        "Heroes of one team on the same initiative choose which of them acts first; between " +
            "teams the Tie Breaker coin decides, then it flips (p.7)."

    else -> null
}

/** The question's rule lines in the asking card's text box: the actions it can't offer, then the rule. */
@Composable
private fun RuleBox(color: PlateColor, table: TableUi, decision: Decision, focus: CardFocusState) {
    val note = footnote(table, decision)
    if (note == null && unavailableActions(decision).isEmpty()) return
    CardTextBox(color, Modifier.fillMaxWidth(), cap = "Rule") {
        Unavailable(table, decision, focus)
        note?.let { Text(it, style = PrintedSmall) }
    }
}

/**
 * The question asked by a card, on the card's own text box (mock-up 03): the action on its tab, the
 * rulebook's action symbol, the prompt (a live region) and how to answer on the board.
 */
@Composable
private fun QuestionBox(table: TableUi, card: Card, decision: Decision, board: Boolean) {
    val action = table.track?.slots
        ?.firstOrNull { it.heroId == decision.seat && it.cards.any { view -> view.cardId == card.id } }
        ?.action
    CardTextBox(
        PlateColor.of(card.color),
        Modifier.fillMaxWidth(),
        cap = action?.let { actionWord(it, card) } ?: PlayText.primary(card.primary),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            lookAction(action, card)?.let {
                ActionIcon(it, size = scaledIconSize(28.dp, 1.3f))
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    decision.prompt,
                    style = PrintedQuestion,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (board) Text(boardHint(table), style = PrintedQuestion)
            }
        }
    }
}

/** The rulebook's symbol for the action being resolved, or the card's primary while none is chosen yet. */
private fun lookAction(action: ActionType?, card: Card): LookAction? = when (action) {
    ActionType.MOVEMENT, ActionType.FAST_TRAVEL -> LookAction.MOVEMENT

    ActionType.ATTACK, ActionType.CLEAR -> LookAction.ATTACK

    ActionType.HOLD -> null

    ActionType.PRIMARY, null -> when (card.primary) {
        PrimaryAction.ATTACK -> LookAction.ATTACK
        PrimaryAction.SKILL -> LookAction.SKILL
        PrimaryAction.DEFENSE -> LookAction.DEFENSE
        PrimaryAction.MOVEMENT -> LookAction.MOVEMENT
        else -> null
    }
}

/**
 * The actions the asking card prints that aren't offered, one line each with the engine's reason
 * (`DecisionContext.unavailable`, ADR-041), so a lone Hold never looks like a broken app. A reason
 * that is another card's effect opens that card when tapped.
 */
@Composable
private fun Unavailable(table: TableUi, decision: Decision, focus: CardFocusState) {
    unavailableActions(decision).forEach { u ->
        val by = u.byCardId?.let { table.cards[it] }
        val tap = if (by == null) {
            Modifier
        } else {
            Modifier
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Read ${by.name}") { focus.show(listOf(by), title = "Forbidden by") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(tap)
                .padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                null,
                tint = LocalContentColor.current,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(unavailableLine(u), style = PrintedSmall)
        }
    }
}

/**
 * Who is asked and what: the deciding hero and the kind, small caps in the team's colour with its mark,
 * as on the panel's strip in mock-up 01.
 */
@Composable
fun DecisionWho(table: TableUi, modifier: Modifier = Modifier) {
    val decision = table.decision ?: return
    val name = table.decider?.let { table.hero(it.heroId).name } ?: BoardText.team(decision.team)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TeamMark(decision.team, markSize = scaledIconSize(13.dp, 1.3f))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "$name · ${kindTitle(decision.kind)}",
            style = GoaTextStyles.plateTitle.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = teamColor(decision.team),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [DecisionWho] unless the panel's strip says it ([who]), the prompt on its title plate (a live region;
 * a question asked by a card has it in the card's text box, Defend in its attack header), and for card
 * questions "Asked by <card>" with a thumbnail (tap to read it).
 */
@Composable
private fun PromptHeader(
    table: TableUi,
    decision: Decision,
    focus: CardFocusState,
    who: Boolean,
    prompt: Boolean,
    askedBy: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (who) DecisionWho(table)
        if (prompt) {
            TitlePlate(
                decision.prompt,
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                maxLines = Int.MAX_VALUE,
            )
        }
        val card = table.sourceCard
        if (askedBy && card != null) AskedBy(card, decision, focus)
    }
}

@Composable
private fun AskedBy(card: Card, decision: Decision, focus: CardFocusState) {
    // "in radius", "in range" in the question are the card's own values (p.10): say them.
    val reach = sourceReach(decision.prompt, card)
    PlateToneSurface(
        onClick = { focus.show(listOf(card), title = "Asked by") },
        border = BorderStroke(1.dp, cardColor(card.color).copy(alpha = 0.8f)),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription =
                    listOfNotNull("Asked by ${card.name}", reach).joinToString(", ") + ". Read the card"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCard(card, Modifier.cardFocusOrigin(focus, card.id), width = 28.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Asked by ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(card.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            reach?.let {
                Text(
                    " · $it",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The asking card beside what answers it: large (about a third of the panel, up to 150 dp; smaller
 * with large text) when the answers are buttons, smaller beside a board question's hint. Tap the
 * card to read it in the card focus viewer; under it, the card's reach when the question uses it.
 */
@Composable
private fun CardBeside(
    table: TableUi,
    card: Card,
    focus: CardFocusState,
    large: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reach = table.decision?.let { sourceReach(it.prompt, card) }
    val big = LocalDensity.current.fontScale > LARGE_TEXT
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A spell (Gydion) is a landscape card, about twice as wide at the same height: it goes above.
        val spell = card.isSpell
        val width = when {
            !large -> if (big) 56.dp else 72.dp
            spell -> SPELL_BESIDE
            big -> (maxWidth * BESIDE_SHARE).coerceIn(84.dp, 112.dp)
            else -> (maxWidth * BESIDE_SHARE).coerceIn(96.dp, 150.dp)
        }
        val read = { focus.show(listOf(card), title = "Asked by") }
        val shown: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = read,
                    shape = RoundedCornerShape(width * 0.08f),
                    color = Color.Transparent,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .cardFocusOrigin(focus, card.id)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(spokenCard(card), reach).joinToString(", ")
                            role = Role.Button
                            onClick(label = "Read the card") {
                                read()
                                true
                            }
                        },
                ) { TableCard(card, width = width) }
                reach?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (large && (big || spell)) {
            // Large text beside a card leaves the answers a word per line: the card goes above them.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                shown()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                shown()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
            }
        }
    }
}

// ---- Board options ----------------------------------------------------------------------

/** How a board question is answered: spaces, units or tokens are tapped on the board. */
private fun boardHint(table: TableUi): String {
    val spaces = table.spaceOptions
    val pieces = table.pieceOptions
    return when {
        pieces.isEmpty() && spaces.size == 1 -> "Tap the space lit on the map."
        pieces.isEmpty() -> "Tap one of the ${spaces.size} lit spaces."
        spaces.isEmpty() -> "Tap an outlined piece on the board, or pick it below."
        else -> "Tap an outlined space or piece on the board."
    } + longPressHint(table)
}

/** [boardHint] for a question no card asks, on the plate itself. */
@Composable
private fun BoardHint(table: TableUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.TouchApp,
            null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            boardHint(table),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Confirm with the choice spelled out, once one is tapped on the board. */
@Composable
private fun BoardConfirm(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
    if (selected != null) {
        PlateButton(
            onClick = { onAnswer(selected) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Text("Confirm · ${table.label(selected)}", textAlign = TextAlign.Center)
        }
    }
}

/**
 * When a hero's piece is an option (a tap there answers), how to read about that hero instead
 * (ADR-044): a long press always opens the hero's game state.
 */
internal fun longPressHint(table: TableUi): String {
    val heroOption = table.pieceOptions.keys.any { id ->
        table.heroView(id) != null || table.view.pieces.firstOrNull { it.id == id }?.let(::figureOf) != null
    }
    return if (heroOption) " Long-press a hero for their game state." else ""
}

/** Units and tokens (and a short list of spaces whose names differ) as chips too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardChips(table: TableUi, onSelect: (Choice) -> Unit) {
    val spaces = table.spaceOptions.values.toList()
    val pieces = table.pieceOptions.values.toList()
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
    // A chip must say which one it is: spaces whose names repeat ("Red Throne Zone" three times)
    // are only told apart on the board.
    val spaceChips = spaces.takeIf { list ->
        list.size <= MAX_SPACE_CHIPS && list.map(table::label).distinct().size == list.size
    }.orEmpty()
    val chips = pieces + spaceChips
    if (chips.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        chips.forEach { choice -> OptionChip(table.label(choice), choice == selected) { onSelect(choice) } }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else PlateWell,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---- Buttons ----------------------------------------------------------------------------

/**
 * Answers that go at once: actions as compact chips (the game's icon, the value from the card),
 * "Choose one" options, yes / decline, numbers, pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(
    table: TableUi,
    choices: List<Choice>,
    onAnswer: (Choice) -> Unit,
    busy: Boolean,
    afterPrimary: @Composable () -> Unit = {},
) {
    if (choices.isEmpty()) return
    val numbers = choices.filterIsInstance<Choice.Number>()
    if (numbers.size == choices.size) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            numbers.forEach { n ->
                PlateButton(
                    onClick = { onAnswer(n) },
                    enabled = !busy,
                    tone = PlateTone.STEEL,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text("${n.value}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        choices.forEach { choice ->
            when {
                choice is Choice.Action -> {
                    ActionChip(table, choice.action, busy) { onAnswer(choice) }
                    if (choice.action == ActionType.PRIMARY) afterPrimary()
                }

                choice is Choice.Decline || choice is Choice.Pass -> PlateButton(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    tone = PlateTone.STEEL,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(table.label(choice), textAlign = TextAlign.Center) }

                else -> PlateToneSurface(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    tone = PlateTone.GOLD,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Box48 {
                        Text(
                            table.label(choice),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Box48(content: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * One action as a chip: the game's icon and the value the card prints ("Movement 4"), the primary
 * filled and marked; a rule the card doesn't print in a few words with its page (Fast Travel's
 * condition, Hold, Clear). TalkBack reads it whole.
 */
@Composable
private fun ActionChip(table: TableUi, action: ActionType, busy: Boolean, onClick: () -> Unit) {
    val card = table.sourceCard
    val primary = action == ActionType.PRIMARY
    val label = actionLabel(action, card)
    val rule = actionRule(action)
    val tone = if (primary) PlateTone.GOLD else PlateTone.STEEL
    val content = tone.content
    PlateToneSurface(
        onClick = onClick,
        enabled = !busy,
        tone = tone,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription =
                    listOfNotNull(label, "primary action, the card's text applies".takeIf { primary }, rule)
                        .joinToString(", ")
                role = Role.Button
                if (busy) disabled()
                onClick(label = "Choose") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                actionIcon(action, card).vector,
                null,
                tint = if (primary) content else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                if (primary) Text("PRIMARY", style = GoaTextStyles.overline, color = content, maxLines = 1)
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                )
                rule?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                    )
                }
            }
        }
    }
}

// ---- Cards ------------------------------------------------------------------------------

private val PrintedQuestion = GoaTextStyles.printed.copy(fontSize = 17.sp, lineHeight = 21.sp)
private val PrintedSmall = GoaTextStyles.printed.copy(fontSize = 15.sp, lineHeight = 18.sp)
private const val MAX_SPACE_CHIPS = 6
private const val BESIDE_SHARE = 0.36f
private val SPELL_BESIDE = 96.dp
private const val LARGE_TEXT = 1.3f
