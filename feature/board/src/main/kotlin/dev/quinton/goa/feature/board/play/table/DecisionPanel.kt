package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
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
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/**
 * The question being asked and its answers, by kind (PRODUCT §9: "every question says what is
 * asked and which card asks it; every option is shown on the board or as a button"):
 * - spaces, units and tokens are tapped on the board (each also a TalkBack node), then confirmed;
 * - cards (to play, discard, defend with, upgrade, keep) open in the card focus viewer: a first tap
 *   zooms the card, a second tap on it (or its Confirm button) answers (DecisionCards.kt);
 * - Defend is a defense panel with each card's Defense total against the Attack, read the same way;
 * - actions, "Choose one" options, yes / decline and numbers are buttons that answer at once.
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PromptHeader(table, decision, focus)
        when (decision.kind) {
            DecisionKind.PLAY_CARD -> HandPicker(table, onAnswer, focus, busy)

            DecisionKind.DEFEND -> DefensePanel(table, onSelect, onAnswer, focus, busy)

            DecisionKind.CHOOSE_CARD, DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE ->
                CardPicker(table, onAnswer, focus, busy)

            else -> BoardOptions(table, onSelect, onAnswer, busy)
        }
        if (decision.kind != DecisionKind.DEFEND && decision.kind != DecisionKind.PLAY_CARD) {
            Buttons(table, table.buttonOptions, onAnswer, busy)
        }
        if (decision.kind == DecisionKind.ACT_FIRST) {
            // A bare "who acts first?" over two portraits told nothing about why it was asked.
            Text(
                "Heroes of one team on the same initiative choose which of them acts first; between " +
                    "teams the Tie Breaker coin decides, then it flips (p.7).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (decision.kind == DecisionKind.CHOOSE_ACTION && attackMissing(table.sourceCard, decision.options)) {
            Text(
                "No Attack: no unit you may target is in reach (p.13), or an effect forbids it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The card attacks (an Attack value), but neither its attack nor its Attack primary is offered. */
internal fun attackMissing(card: Card?, options: List<Choice>): Boolean {
    if (card?.values?.attack == null) return false
    val actions = options.filterIsInstance<Choice.Action>().map { it.action }
    val primaryAttacks = card.primary == PrimaryAction.ATTACK && ActionType.PRIMARY in actions
    return ActionType.ATTACK !in actions && !primaryAttacks
}

/** Who is asked, what, and which card's text asks it (tap the card to read it). */
@Composable
private fun PromptHeader(table: TableUi, decision: Decision, focus: CardFocusState) {
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
        Text(
            decision.prompt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        table.sourceCard?.let { card ->
            // "in radius", "in range" in the question are the card's own values (p.10): say them.
            val reach = sourceReach(decision.prompt, card)
            Surface(
                onClick = { focus.show(listOf(card), title = "Asked by") },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, cardColor(card.color).copy(alpha = 0.8f)),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics {
                        contentDescription = listOfNotNull("Asked by ${card.name}", reach).joinToString(", ") +
                            ". Read the card"
                    },
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableCard(card, Modifier.cardFocusOrigin(focus, card.id), width = 22.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Asked by ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        card.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

// ---- Board options ----------------------------------------------------------------------

/**
 * Spaces, units or tokens: tapped on the board. Units and tokens (and a short list of spaces) are
 * also listed as chips; the tapped one is confirmed with its name and distance spelled out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardOptions(table: TableUi, onSelect: (Choice) -> Unit, onAnswer: (Choice) -> Unit, busy: Boolean) {
    val spaces = table.spaceOptions.values.toList()
    val pieces = table.pieceOptions.values.toList()
    if (spaces.isEmpty() && pieces.isEmpty()) return
    val selected = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.TouchApp, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            when {
                pieces.isEmpty() && spaces.size == 1 -> "Tap the space outlined in teal."
                pieces.isEmpty() -> "Tap one of the ${spaces.size} spaces outlined in teal."
                spaces.isEmpty() -> "Tap an outlined piece on the board, or pick it below."
                else -> "Tap an outlined space or piece on the board."
            },
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
    // A chip must say which one it is: spaces whose names repeat ("Red Throne Zone" three times)
    // are only told apart on the board.
    val spaceChips = spaces.takeIf { list ->
        list.size <= MAX_SPACE_CHIPS && list.map(table::label).distinct().size == list.size
    }.orEmpty()
    val chips = pieces + spaceChips
    if (chips.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            chips.forEach { choice -> OptionChip(table.label(choice), choice == selected) { onSelect(choice) } }
        }
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

/** Buttons that answer at once: actions (with what each does), options, yes / decline, numbers, pass. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(table: TableUi, choices: List<Choice>, onAnswer: (Choice) -> Unit, busy: Boolean) {
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
            val note = (choice as? Choice.Action)?.let { PlayText.actionNote(it.action) }
            val decline = choice is Choice.Decline || choice is Choice.Pass
            val label = table.label(choice)
            if (decline) {
                OutlinedButton(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(label) }
            } else {
                Surface(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (note != null) {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Cards ------------------------------------------------------------------------------

/** The cards of the next Tier in [card]'s colour (p.15): what upgrading it offers. */
internal fun upgradesOf(table: TableUi, card: Card): List<Card> {
    val tier = card.tier ?: return emptyList()
    return table.cards.values
        .filter { it.heroId == card.heroId && it.color == card.color && it.tier == tier + 1 }
        .sortedBy { it.id }
}

/** Defense succeeds when its total is at least the Attack (p.14). */
fun holds(defense: Int?, attack: Int?): Boolean? = if (defense == null || attack == null) null else defense >= attack

/**
 * Whether a defense card stops the attack: a Block by its condition when the engine settles it
 * ([blocks], p.14 "regardless of the attack value"), else its Defense total against the Attack;
 * null when only resolving it tells.
 */
fun defenseVerdict(total: Int?, blocks: Boolean?, attack: Int?): Boolean? = blocks ?: holds(total, attack)

private const val MAX_SPACE_CHIPS = 6
