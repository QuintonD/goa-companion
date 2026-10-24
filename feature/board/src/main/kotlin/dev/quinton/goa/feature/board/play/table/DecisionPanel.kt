package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardText

/**
 * The pending question: what is asked and which card asks it (always), then, when it is the
 * player's, its options by kind: the hand for playing a card ("Play facedown"), the defense panel,
 * cards to choose, spaces and units on the board (picked on the map or from the list, then
 * confirmed), or buttons. [picked] is the option chosen so far on the map or in a list.
 */
@Composable
internal fun DecisionPanel(
    state: TableState,
    decision: DecisionUi,
    picked: Choice?,
    onPick: (Choice?) -> Unit,
    onChoose: (Choice) -> Unit,
    onRead: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PromptHeader(decision, onRead)
        if (!decision.mine) return@Column
        when {
            decision.kind == DecisionKind.DEFEND && decision.defense != null ->
                DefenseContent(decision.defense, picked, onPick, onChoose, onRead)

            decision.kind == DecisionKind.PLAY_CARD -> PlayContent(state, decision, picked, onPick, onChoose, onRead)

            decision.cards.isNotEmpty() -> CardContent(decision, picked, onPick, onChoose, onRead)

            decision.spaces.isNotEmpty() || decision.units.isNotEmpty() ->
                BoardContent(decision, picked, onPick, onChoose)

            else -> Buttons(decision.buttons, onChoose)
        }
    }
}

/** "WASP · DEFEND · p.14", the question in full, and the card that asks it. */
@Composable
private fun PromptHeader(decision: DecisionUi, onRead: (Card) -> Unit) {
    val who = decision.seat?.let { "${it.name} · ${it.controller}" }
    Column {
        Text(
            listOfNotNull(who, TableText.kind(decision.kind)).joinToString(" · ").uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            decision.prompt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        decision.source?.let { card ->
            TextButton(
                onClick = { onRead(card) },
                modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                Text("Asked by ${card.name} · read the card", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Turn step 1 (p.7): pick a card from hand, read it, play it facedown. */
@Composable
private fun PlayContent(
    state: TableState,
    decision: DecisionUi,
    picked: Choice?,
    onPick: (Choice?) -> Unit,
    onChoose: (Choice) -> Unit,
    onRead: (Card) -> Unit,
) {
    val chosen = (picked as? Choice.Card)?.cardId
    val card = decision.cards.firstOrNull { it.cardId == chosen }
    if (decision.cards.isNotEmpty()) {
        Text(
            "Your hand · pick the card to play this turn. It stays facedown until everyone has played (p.7).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CardRow(
            decision.cards.map { it.cardId to it.card },
            HAND_CARD,
            onClick = { id -> onPick(decision.cards.first { it.cardId == id }.choice) },
            actionLabel = "Pick",
            selectedId = chosen,
            caption = { id -> decision.cards.first { it.cardId == id }.card?.initiative?.let { "Init. $it" } },
        )
    }
    if (card?.card != null) PickedCard(card.card, onRead)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        if (decision.cards.isNotEmpty()) {
            Button(
                onClick = { card?.let { onChoose(it.choice) } },
                enabled = card != null,
                modifier = Modifier.heightIn(min = 52.dp).weight(1f),
            ) { Text(if (card == null) "Pick a card" else "Play ${card.card?.name ?: "card"} facedown") }
        }
        decision.buttons.forEach { option ->
            OutlinedButton(onClick = { onChoose(option.choice) }, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(option.label)
            }
        }
    }
    if (state.hand.isEmpty() && decision.cards.isEmpty()) {
        Text(
            "You have no cards in hand, so you pass (p.7).",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PickedCard(card: Card, onRead: (Card) -> Unit) {
    NoteSurface(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(cardSummary(card), style = MaterialTheme.typography.titleSmall)
            CardValuesRow(card)
            if (card.text.isNotBlank()) {
                dev.quinton.goa.core.designsystem.cardtext.CardTextView(
                    card.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                )
            }
            TextButton(onClick = { onRead(card) }, modifier = Modifier.heightIn(min = 40.dp)) {
                Text("Read the whole card")
            }
        }
    }
}

/**
 * Attacked (p.14): the attacker and the Attack total, then each card in hand with the Defense total
 * it would give and whether that holds (equal or higher) or falls, in words and icons, never by
 * colour alone; a card whose text decides while resolving (Block, a choice) says so. "Don't defend"
 * is always there: without a card the hero is defeated.
 */
@Composable
private fun DefenseContent(
    defense: DefenseUi,
    picked: Choice?,
    onPick: (Choice?) -> Unit,
    onChoose: (Choice) -> Unit,
    onRead: (Card) -> Unit,
) {
    val attack = defense.attackTotal
    NoteSurface(accent = MaterialTheme.colorScheme.error) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            defense.attacker?.let {
                HeroFace(it, 36.dp)
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${defense.attacker?.name ?: "An enemy"} attacks" + if (defense.ranged == true) " (ranged)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    attack?.let { "Attack $it" } ?: "Attack",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                defense.minionModifier?.takeIf { it != 0 }?.let {
                    Text(
                        "Minion defense modifier ${if (it > 0) "+" else ""}$it, counted below (p.14)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Text(
        "Discard one card to defend: you hold if your Defense total is equal or higher (p.14).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    defense.cards.forEach { option ->
        DefenseRow(option, attack, selected = picked == option.choice, onSelect = { onPick(option.choice) }, onRead)
    }
    val chosen = defense.cards.firstOrNull { it.choice == picked }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(
            onClick = { chosen?.let { onChoose(it.choice) } },
            enabled = chosen != null,
            modifier = Modifier.heightIn(min = 52.dp).weight(1f),
        ) { Text(chosen?.card?.name?.let { "Defend with $it" } ?: "Pick a card") }
        defense.decline?.let { decline ->
            OutlinedButton(
                onClick = { onChoose(decline.choice) },
                modifier = Modifier.heightIn(min = 52.dp).semantics {
                    contentDescription = "${decline.label}: you are defeated"
                },
            ) { Text(decline.label) }
        }
    }
}

@Composable
private fun DefenseRow(
    option: DefenseCardUi,
    attack: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
    onRead: (Card) -> Unit,
) {
    val holds = option.holds(attack)
    val verdict = when (holds) {
        true -> "holds"
        false -> "falls"
        null -> "decided by its text"
    }
    val total = option.total?.let { "Defense total $it" } ?: "Defense total: its text decides"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                contentDescription = "${option.card?.name ?: "Card"}: $total, $verdict"
            },
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CardImage(option.card, 44.dp, selected = selected)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(option.card?.name ?: "Card", style = MaterialTheme.typography.titleSmall)
                Text(total, style = MaterialTheme.typography.bodyMedium)
            }
            Verdict(holds, verdict)
            option.card?.let { card ->
                TextButton(onClick = { onRead(card) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Read") }
            }
        }
    }
}

/** "holds" with a tick, "falls" with a minus, "?" when the text decides: words and shape, not colour alone. */
@Composable
private fun Verdict(holds: Boolean?, text: String) {
    val (icon, tint) = when (holds) {
        true -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.secondary
        false -> Icons.Outlined.RemoveCircleOutline to MaterialTheme.colorScheme.error
        null -> Icons.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(scaledIconSize(18.dp)))
        Spacer(Modifier.width(2.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** Cards to choose (discard, retrieve, upgrade, keep): pick, read, confirm. */
@Composable
private fun CardContent(
    decision: DecisionUi,
    picked: Choice?,
    onPick: (Choice?) -> Unit,
    onChoose: (Choice) -> Unit,
    onRead: (Card) -> Unit,
) {
    val chosen = decision.cards.firstOrNull { it.choice == picked }
    CardRow(
        decision.cards.map { it.cardId to it.card },
        HAND_CARD,
        onClick = { id -> onPick(decision.cards.first { it.cardId == id }.choice) },
        actionLabel = "Pick",
        selectedId = chosen?.cardId,
        caption = { id ->
            decision.cards.first { it.cardId == id }.card?.item?.let { "Item: +1 ${TableText.stat(it)}" }
        },
    )
    if (decision.kind == DecisionKind.KEEP_UPGRADE) {
        Text(
            "The card you keep goes to your hand; the other is tucked under your dashboard as an item (p.15).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    chosen?.card?.let { PickedCard(it, onRead) }
    val verb = when (decision.kind) {
        DecisionKind.UPGRADE_CARD -> "Upgrade"
        DecisionKind.KEEP_UPGRADE -> "Keep"
        else -> "Choose"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { chosen?.let { onChoose(it.choice) } },
            enabled = chosen != null,
            modifier = Modifier.heightIn(min = 52.dp).weight(1f),
        ) { Text(chosen?.card?.name?.let { "$verb $it" } ?: "Pick a card") }
        decision.buttons.forEach { option ->
            OutlinedButton(onClick = { onChoose(option.choice) }, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(option.label)
            }
        }
    }
}

/**
 * Spaces and units on the board: tap a highlighted one on the map (or pick it from the list), see
 * its path, then confirm. Other answers ("Stop moving", "Don't respawn") are buttons below.
 */
@Composable
private fun BoardContent(decision: DecisionUi, picked: Choice?, onPick: (Choice?) -> Unit, onChoose: (Choice) -> Unit) {
    var listed by rememberSaveable(decision.id) { mutableStateOf(false) }
    val option = picked?.let(decision::option)?.takeIf { it.choice !in decision.buttons.map { b -> b.choice } }
    val what = if (decision.spaces.isNotEmpty()) "space" else "piece"
    if (option == null) {
        Text(
            "Tap a highlighted $what on the board (dashed outline), or pick it from the list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        NoteSurface {
            Column {
                Text("PICKED", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.secondary)
                Text(option.label, style = MaterialTheme.typography.titleSmall)
                option.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (decision.paths.isNotEmpty() && picked is Choice.Space && decision.paths[picked.hex] != null) {
                    val steps = decision.paths[picked.hex].orEmpty().size - 1
                    if (steps > 0) {
                        Text(
                            "Path: ${BoardText.spaces(steps)}, numbered on the board",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { option?.let { onChoose(it.choice) } },
            enabled = option != null,
            modifier = Modifier.heightIn(min = 52.dp).weight(1f),
        ) { Text(if (option == null) "Pick a $what" else "Confirm") }
        if (option != null) {
            TextButton(onClick = { onPick(null) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("Clear") }
        }
    }
    Buttons(decision.buttons, onChoose)
    val all = decision.listed
    TextButton(
        onClick = { listed = !listed },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Icon(if (listed) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            when {
                listed -> "Hide the list"
                all.size == 1 -> "List the option"
                else -> "List all ${all.size} options"
            },
        )
    }
    if (listed) {
        all.forEach { item ->
            val selected = item.choice == picked
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) scheme.secondaryContainer else scheme.surface,
                border = BorderStroke(1.dp, if (selected) scheme.secondary else GoaTheme.colors.engravedBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.RadioButton) { onPick(item.choice) },
            ) {
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                    item.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Answers as full-width buttons, each with what it does under its label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Buttons(options: List<OptionUi>, onChoose: (Choice) -> Unit) {
    if (options.isEmpty()) return
    val short = options.all { it.detail == null && it.label.length <= SHORT_LABEL }
    if (short) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { option ->
                OutlinedButton(onClick = { onChoose(option.choice) }, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(option.label)
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            Surface(
                onClick = { onChoose(option.choice) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    option.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val HAND_CARD = 76.dp
private const val SHORT_LABEL = 14
