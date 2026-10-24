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

/**
 * The question being asked and its answers, by kind (PRODUCT §9: "every question says what is
 * asked and which card asks it; every option is shown on the board or as a button"):
 * - spaces, units and tokens are tapped on the board (each also a TalkBack node), then confirmed;
 * - cards (to play, to upgrade, to keep) are picked from their images, then confirmed;
 * - Defend is a defense panel with each card's Defense total against the Attack;
 * - actions, "Choose one" options, yes / decline and numbers are buttons that answer at once.
 * [onSelect] marks a tentative choice, [onAnswer] answers, [onCard] opens a card to read it.
 */
@Composable
fun DecisionPanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    onCard: (Card) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val decision = table.decision ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PromptHeader(table, decision, onCard)
        when (decision.kind) {
            DecisionKind.PLAY_CARD -> HandPicker(table, onSelect, onAnswer, onCard, busy)

            DecisionKind.DEFEND -> DefensePanel(table, onSelect, onAnswer, onCard, busy)

            DecisionKind.CHOOSE_CARD, DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE ->
                CardPicker(table, onSelect, onAnswer, onCard, busy)

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
private fun PromptHeader(table: TableUi, decision: Decision, onCard: (Card) -> Unit) {
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
                onClick = { onCard(card) },
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
                    TableCard(card, width = 22.dp)
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

/**
 * Turn step 1 (p.7): the hand as card images. Tap one to see its values and text, then "Play
 * facedown". With no cards, Pass.
 */
@Composable
private fun HandPicker(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    onCard: (Card) -> Unit,
    busy: Boolean,
) {
    val options = table.cardOptions
    val selected = table.selected as? Choice.Card
    table.decider?.takeIf { it.at == null }?.let { hero ->
        Text(
            "${table.hero(hero.heroId).name} is off the board. When this card resolves you may respawn in " +
                "your Throne Zone, then act with it (p.7).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CardStrip(table, options, selected, onSelect)
    val card = table.card(selected?.cardId)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        table.buttonOptions.forEach { choice ->
            OutlinedButton(onClick = { onAnswer(choice) }, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(table.label(choice))
            }
        }
        if (options.isNotEmpty()) {
            Button(
                onClick = { selected?.let(onAnswer) },
                enabled = selected != null && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(
                    if (card !=
                        null
                    ) {
                        "Play ${card.name} facedown"
                    } else {
                        "Pick a card to play facedown"
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // Under the button, so choosing a card never pushes "Play" out of the panel.
    if (card != null) CardPreview(card, onCard)
}

/** Card choices other than playing: discard, retrieve, which card to upgrade, which to keep. */
@Composable
private fun CardPicker(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    onCard: (Card) -> Unit,
    busy: Boolean,
) {
    val decision = table.decision ?: return
    val options = table.cardOptions
    val selected = table.selected as? Choice.Card
    CardStrip(table, options, selected, onSelect)
    val card = table.card(selected?.cardId)
    if (card != null) {
        // The button first, so the preview below never pushes it out of the panel.
        Button(
            onClick = { selected?.let(onAnswer) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                when (decision.kind) {
                    DecisionKind.UPGRADE_CARD -> "Upgrade ${card.name}"
                    DecisionKind.KEEP_UPGRADE -> "Keep ${card.name}"
                    else -> "Confirm · ${card.name}"
                },
            )
        }
        if (decision.kind == DecisionKind.KEEP_UPGRADE) {
            // p.15: the card kept goes to hand, the other is tucked as an item.
            val other = options.firstOrNull { it != selected }?.let { table.card(it.cardId) }
            Text("${card.name} goes to your hand.", style = MaterialTheme.typography.bodyMedium)
            other?.item?.let { stat ->
                ItemLine(stat, "${other.name} becomes an item: +1 ${PlayText.stat(stat)} (p.16)")
            }
        } else if (decision.kind == DecisionKind.UPGRADE_CARD && upgradesOf(table, card).size == 2) {
            UpgradePreview(card, upgradesOf(table, card), onCard)
        } else {
            CardPreview(card, onCard)
        }
    } else if (decision.kind == DecisionKind.KEEP_UPGRADE) {
        Text(
            "One goes to your hand; the other is tucked under your hero as an item (p.15, p.16).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The cards of the next Tier in [card]'s colour (p.15): what upgrading it offers. */
internal fun upgradesOf(table: TableUi, card: Card): List<Card> {
    val tier = card.tier ?: return emptyList()
    return table.cards.values
        .filter { it.heroId == card.heroId && it.color == card.color && it.tier == tier + 1 }
        .sortedBy { it.id }
}

/** Upgrading a card: the two of the next Tier, one to keep, the other an item (p.15, p.16). */
@Composable
private fun UpgradePreview(card: Card, next: List<Card>, onCard: (Card) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${card.name} is replaced by one of these: you keep one, the other becomes an item (p.15, p.16).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            next.forEach { option ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = spokenCard(option) +
                            (option.item?.let { ". As an item: +1 ${PlayText.stat(it)}" } ?: "")
                    },
                ) {
                    Surface(onClick = { onCard(option) }, shape = RoundedCornerShape(6.dp)) {
                        TableCard(option, width = UPGRADE_CARD)
                    }
                    option.item?.let { ItemIcon(it, 14.dp) }
                }
            }
        }
    }
}

/** Card images in a row, selectable; each one a TalkBack node with its values. */
@Composable
private fun CardStrip(table: TableUi, options: List<Choice.Card>, selected: Choice.Card?, onSelect: (Choice) -> Unit) {
    val keep = table.decision?.kind == DecisionKind.KEEP_UPGRADE
    // The whole hand in view (five cards after a level up): the cards shrink to fit, down to a size
    // still easy to tap; only beyond that does the row scroll.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = options.size.coerceAtLeast(1)
        val fit = (maxWidth - Spacing.sm * (n - 1) - STRIP_SLACK) / n
        val width = fit.coerceIn(MIN_HAND_CARD, HAND_CARD)
        CardRow(table, options, selected, onSelect, keep, width)
    }
}

@Composable
private fun CardRow(
    table: TableUi,
    options: List<Choice.Card>,
    selected: Choice.Card?,
    onSelect: (Choice) -> Unit,
    keep: Boolean,
    cardWidth: Dp,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { choice ->
            val card = table.card(choice.cardId)
            val chosen = choice == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(choice) }
                    .clearAndSetSemantics {
                        contentDescription = card?.let(::spokenCard) ?: "Unknown card"
                        if (keep) card?.item?.let { stateDescription = "As an item: +1 ${PlayText.stat(it)}" }
                        this.selected = chosen
                        onClick(label = "Choose") {
                            onSelect(choice)
                            true
                        }
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        if (chosen) 3.dp else 1.dp,
                        if (chosen) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
                    ),
                    modifier = Modifier.padding(top = if (chosen) 0.dp else 6.dp, bottom = if (chosen) 6.dp else 0.dp),
                ) {
                    TableCard(card, width = cardWidth)
                }
                if (keep) card?.item?.let { ItemIcon(it, 14.dp) }
                if (chosen) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** The selected card's values and text, short, with "Read the card" for all of it. */
@Composable
private fun CardPreview(card: Card, onCard: (Card) -> Unit) {
    Column {
        Text(
            listOfNotNull(
                card.name,
                card.initiative?.let { "Initiative $it" },
                card.values.attack?.let { "Attack $it" },
                card.values.defense?.let { "Defense $it" },
                card.values.movement?.let { "Movement $it" },
                card.values.range?.let { "Range $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
        )
        if (card.text.isNotBlank()) {
            CardTextView(
                card.text,
                style = MaterialTheme.typography.bodySmall,
                iconTint = cardColor(card.color),
                maxLines = PREVIEW_LINES,
            )
        }
        TextButton(onClick = { onCard(card) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Read the card")
        }
    }
}

// ---- Defense (p.14) ---------------------------------------------------------------------

/**
 * Attacked: the attacker and the Attack total, each hand card's Defense total (the engine's
 * preview: card value, items, minion modifiers, text bonuses it can compute) marked "Holds" or
 * "Falls" in words and an icon, never colour alone; "Don't defend". Pick one, then confirm.
 */
@Composable
private fun DefensePanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    onCard: (Card) -> Unit,
    busy: Boolean,
) {
    val decision = table.decision ?: return
    val context = decision.context
    val attack = context.attackTotal
    val attacker = context.attackerId?.let(table::pieceName)
    val selected = table.selected
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                listOfNotNull(
                    attacker?.let { "$it attacks" },
                    context.rangedAttack?.let { if (it) "ranged" else "melee" },
                ).joinToString(" · ")
                    .ifEmpty { "Attacked" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                attack?.let { "Attack $it" } ?: "Attack decided by the card",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            context.minionModifier?.takeIf { it != 0 }?.let { m ->
                Text(
                    "Minion defense modifier ${if (m > 0) "+$m" else "$m"} is counted below (p.14).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
    val verdicts = table.cardOptions.associate { choice ->
        choice.cardId to
            defenseVerdict(context.defensePreview[choice.cardId], context.blockPreview[choice.cardId], attack)
    }
    if (attack != null && verdicts.isNotEmpty() && verdicts.values.all { it == false }) {
        // p.14: a failed defense still discards the card, and the hero is defeated all the same.
        Text(
            "None of your cards stops Attack $attack. Not defending keeps them in your hand (p.14).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        table.cardOptions.forEach { choice ->
            val card = table.card(choice.cardId)
            DefenseRow(
                card,
                context.defensePreview[choice.cardId],
                context.blockPreview[choice.cardId],
                verdicts[choice.cardId],
                choice == selected,
                onClick = { onSelect(choice) },
                onRead = { card?.let(onCard) },
            )
        }
        table.buttonOptions.forEach { choice ->
            DeclineRow(table.label(choice), choice == selected) { onSelect(choice) }
        }
    }
    if (selected != null) {
        val card = (selected as? Choice.Card)?.let { table.card(it.cardId) }
        // The button is under every row: bring it into view once a row is chosen.
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(selected) { requester.bringIntoView() }
        Button(
            onClick = { onAnswer(selected) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bringIntoViewRequester(requester),
        ) {
            Text(card?.let { "Defend with ${it.name}" } ?: table.label(selected))
        }
    }
}

/** Defense succeeds when its total is at least the Attack (p.14). */
fun holds(defense: Int?, attack: Int?): Boolean? = if (defense == null || attack == null) null else defense >= attack

/**
 * Whether a defense card stops the attack: a Block by its condition when the engine settles it
 * ([blocks], p.14 "regardless of the attack value"), else its Defense total against the Attack;
 * null when only resolving it tells.
 */
fun defenseVerdict(total: Int?, blocks: Boolean?, attack: Int?): Boolean? = blocks ?: holds(total, attack)

@Composable
private fun DefenseRow(
    card: Card?,
    total: Int?,
    blocks: Boolean?,
    verdict: Boolean?,
    selected: Boolean,
    onClick: () -> Unit,
    onRead: () -> Unit,
) {
    val verdictText = when (verdict) {
        true -> if (blocks == true) "Blocks" else "Holds"
        false -> "Falls"
        null -> "Decided by its text"
    }
    val detail = when (blocks) {
        true -> "Block: stops this attack whatever its value"
        false -> "Block: not against this attack"
        null -> total?.let { "Defense $it" } ?: "Defense decided while it resolves"
    }
    val spoken = listOfNotNull(card?.name ?: "Unknown card", detail, verdictText).joinToString(", ")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                this.selected = selected
                onClick(label = "Choose") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = onRead, shape = RoundedCornerShape(4.dp)) { TableCard(card, width = 34.dp) }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    card?.name ?: "Unknown card",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Verdict(verdict, verdictText)
        }
    }
}

/** "Holds" with a tick or "Falls" with a warning sign: words and shape, not colour alone. */
@Composable
private fun Verdict(verdict: Boolean?, text: String) {
    val color = when (verdict) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (verdict != null) {
            Icon(
                if (verdict) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                null,
                tint = color,
                modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = if (verdict == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeclineRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                "Keep your cards; the attack succeeds (p.14).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val HAND_CARD = 84.dp
private val MIN_HAND_CARD = 60.dp

/** The selected card's 3 dp border on both sides. */
private val STRIP_SLACK = 6.dp
private val UPGRADE_CARD = 72.dp
private const val MAX_SPACE_CHIPS = 6
private const val PREVIEW_LINES = 4
