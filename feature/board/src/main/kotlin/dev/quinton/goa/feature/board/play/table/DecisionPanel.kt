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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.quinton.goa.core.designsystem.component.TeamMark
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
import dev.quinton.goa.core.game.UnavailableAction
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
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
) {
    val decision = table.decision ?: return
    val source = table.sourceCard
    val layout = panelLayout(decision.kind, source != null)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

            decision.kind == DecisionKind.UPGRADE_CARD -> UpgradePicker(table, onAnswer, focus, busy)

            layout == PanelLayout.CARDS -> CardPicker(table, onAnswer, focus, busy)

            layout == PanelLayout.CARD_BESIDE && source != null -> {
                val board = hasBoardOptions(table)
                CardBeside(table, source, focus, large = !board) {
                    if (board) BoardHint(table, onAnswer, busy)
                    Choices(table, table.buttonOptions, onAnswer, busy) { CastPreview(table, focus) }
                    Unavailable(table, decision, focus)
                    SpellReasonsIfNoPrimary(table)
                }
                if (board) BoardChips(table, onSelect)
            }

            else -> {
                if (hasBoardOptions(table)) {
                    BoardHint(table, onAnswer, busy)
                    BoardChips(table, onSelect)
                }
                Choices(table, table.buttonOptions, onAnswer, busy)
                Unavailable(table, decision, focus)
            }
        }
        if (decision.kind == DecisionKind.CHOOSE_ACTION && table.buttonOptions.any { it is Choice.Action }) {
            Footnote("Only the primary action applies the card's text (p.7).")
        }
        if (decision.kind == DecisionKind.ACT_FIRST) {
            // A bare "who acts first?" over two portraits told nothing about why it was asked.
            Footnote(
                "Heroes of one team on the same initiative choose which of them acts first; between " +
                    "teams the Tie Breaker coin decides, then it flips (p.7).",
            )
        }
    }
}

/** How the panel lays out a question: the cards it offers, or the asking card beside the answers. */
internal enum class PanelLayout { CARDS, CARD_BESIDE, PLAIN }

internal fun panelLayout(kind: DecisionKind, hasSource: Boolean): PanelLayout = when (kind) {
    DecisionKind.PLAY_CARD, DecisionKind.DEFEND, DecisionKind.CHOOSE_CARD,
    DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE,
    -> PanelLayout.CARDS

    else -> if (hasSource) PanelLayout.CARD_BESIDE else PanelLayout.PLAIN
}

private fun hasBoardOptions(table: TableUi) = table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()

@Composable
private fun Footnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One unavailable action's line: the engine's reason and where the rule is ("… (p.10, FAQ)."). */
internal fun unavailableLine(u: UnavailableAction): String = u.source?.let { "${u.text} ($it)." } ?: "${u.text}."

/**
 * The actions the asking card prints that aren't offered, one line each with the engine's reason
 * (`DecisionContext.unavailable`, ADR-041), so a lone Hold never looks like a broken app. A reason
 * that is another card's effect opens that card when tapped.
 */
@Composable
private fun Unavailable(table: TableUi, decision: Decision, focus: CardFocusState) {
    if (decision.kind != DecisionKind.CHOOSE_ACTION) return
    decision.context.unavailable.forEach { u ->
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                unavailableLine(u),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Who is asked and what: the deciding hero and the kind in the team's colour, the prompt (a live
 * region; Defend says it in its attack header), and for card questions "Asked by <card>" with a
 * thumbnail (tap to read it). Other questions show the asking card large beside their answers.
 */
@Composable
private fun PromptHeader(table: TableUi, decision: Decision, focus: CardFocusState, prompt: Boolean, askedBy: Boolean) {
    val hero = table.decider
    val name = hero?.let { table.hero(it.heroId).name } ?: BoardText.team(decision.team)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamMark(decision.team, markSize = scaledIconSize(9.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "${name.uppercase()} · ${kindTitle(decision.kind).uppercase()}",
                style = GoaTextStyles.overline,
                color = teamColor(decision.team),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (prompt) {
            Text(
                decision.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
    Surface(
        onClick = { focus.show(listOf(card), title = "Asked by") },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
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

/** "Radius 2" / "Range 3" when the question speaks of the asking card's radius or range. */
internal fun sourceReach(prompt: String, card: Card): String? {
    val text = prompt.lowercase()
    return listOfNotNull(
        card.values.radius?.takeIf { "radius" in text }?.let { "Radius $it" },
        card.values.range?.takeIf { "range" in text }?.let { "Range $it" },
    ).joinToString(" · ").ifEmpty { null }
}

/** A short name for each kind of question, for the header. */
fun kindTitle(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "Play a card"
    DecisionKind.ACT_FIRST -> "Initiative tie"
    DecisionKind.RESPAWN -> "Respawn"
    DecisionKind.CHOOSE_ACTION -> "Action"
    DecisionKind.CHOOSE_SPACE -> "Space"
    DecisionKind.CHOOSE_UNIT -> "Unit"
    DecisionKind.CHOOSE_TOKEN -> "Token"
    DecisionKind.CHOOSE_CARD -> "Card"
    DecisionKind.DEFEND -> "Defend"
    DecisionKind.CHOOSE_OPTION -> "Choose one"
    DecisionKind.CONFIRM -> "Optional"
    DecisionKind.CHOOSE_NUMBER -> "Number"
    DecisionKind.REMOVE_MINION -> "Minion Battle"
    DecisionKind.MINION_RETURN -> "Minion back"
    DecisionKind.UPGRADE_CARD -> "Level up"
    DecisionKind.KEEP_UPGRADE -> "Level up"
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
                    border = BorderStroke(1.dp, cardColor(card.color).copy(alpha = 0.8f)),
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

/** Spaces, units or tokens are tapped on the board: the hint, then Confirm with the choice spelled out. */
@Composable
private fun BoardHint(table: TableUi, onAnswer: (Choice) -> Unit, busy: Boolean) {
    val spaces = table.spaceOptions
    val pieces = table.pieceOptions
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.TouchApp,
            null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            when {
                pieces.isEmpty() && spaces.size == 1 -> "Tap the space outlined in teal."
                pieces.isEmpty() -> "Tap one of the ${spaces.size} spaces outlined in teal."
                spaces.isEmpty() -> "Tap an outlined piece on the board, or pick it below."
                else -> "Tap an outlined space or piece on the board."
            } + longPressHint(table),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (selected != null) {
        Button(
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
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
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
                OutlinedButton(onClick = { onAnswer(n) }, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
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

                choice is Choice.Decline || choice is Choice.Pass -> OutlinedButton(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(table.label(choice), textAlign = TextAlign.Center) }

                else -> Surface(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Box48 {
                        Text(
                            table.label(choice),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
    val scheme = MaterialTheme.colorScheme
    val container = if (primary) scheme.primaryContainer else scheme.surface
    val content = if (primary) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = if (primary) null else BorderStroke(1.dp, scheme.outline),
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
                tint = if (primary) content else scheme.primary,
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
                        color = if (primary) content else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** An action chip's words: the action and the card's value for it ("Attack 6", "Movement 4", "Hold"). */
internal fun actionLabel(action: ActionType, card: Card?): String = when (action) {
    ActionType.PRIMARY -> {
        val value = when (card?.primary) {
            PrimaryAction.ATTACK -> card.values.attack
            PrimaryAction.MOVEMENT -> card.values.movement
            else -> null
        }
        listOfNotNull(PlayText.primary(card?.primary), value?.toString()).joinToString(" ")
    }

    else -> PlayText.action(action, card)
}

/** The rule an action follows that its card doesn't print, in a few words with the page; else null. */
internal fun actionRule(action: ActionType): String? = when (action) {
    ActionType.FAST_TRAVEL -> "To your Zone or an adjacent one, no enemy hero or minion in either (p.11)"
    ActionType.HOLD -> "Do nothing (p.12)"
    ActionType.CLEAR -> "Instead of attacking: remove adjacent tokens (p.13)"
    else -> null
}

// ---- Cards ------------------------------------------------------------------------------

/** Defense succeeds when its total is at least the Attack (p.14). */
fun holds(defense: Int?, attack: Int?): Boolean? = if (defense == null || attack == null) null else defense >= attack

/**
 * Whether a defense card stops the attack: a Block by its condition when the engine settles it
 * ([blocks], p.14 "regardless of the attack value"), else its Defense total against the Attack;
 * null when only resolving it tells.
 */
fun defenseVerdict(total: Int?, blocks: Boolean?, attack: Int?): Boolean? = blocks ?: holds(total, attack)

private const val MAX_SPACE_CHIPS = 6
private const val BESIDE_SHARE = 0.36f
private val SPELL_BESIDE = 96.dp
private const val LARGE_TEXT = 1.3f
