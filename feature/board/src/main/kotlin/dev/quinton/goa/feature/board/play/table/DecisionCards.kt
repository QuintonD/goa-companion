package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.CardNote
import dev.quinton.goa.feature.board.ui.cards.CardSelection
import dev.quinton.goa.feature.board.ui.cards.CardVerdict
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardAspect
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.cardWidth
import dev.quinton.goa.feature.board.ui.cards.isSpell

/*
 * Card questions (play, defend, discard, retrieve, upgrade, keep) answered with the card focus
 * viewer (docs/DESIGN.md, "Card focus"): the first tap on a card zooms it, a second tap on it (or
 * its Confirm button) answers; back or an upward swipe returns with nothing chosen.
 */

/** Opens [offered] in the viewer at [index], confirming answers [onAnswer]. */
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

/**
 * Keep (p.15) in the viewer: the two cards, each followed by the spells it names (NamedCards.kt), to
 * read; a spell points back to the card that casts it, which is the answer.
 */
internal fun keepFocus(table: TableUi, offered: List<OfferedCard>): FocusPages =
    FocusPages(offered.map { focusCard(table, it) }, offered.map { it.choice }).withNamed(
        named = table::namedBy,
        reason = { namer -> "${namer.card.name} casts this spell. Keep ${namer.card.name} to take it into your hand." },
    )

/** Closes the viewer when its question is gone; Confirm waits while the game is busy. */
@Composable
internal fun FocusLifecycle(focus: CardFocusState, decision: Decision, busy: Boolean) {
    DisposableEffect(focus, decision.id) { onDispose { focus.closeFor(decision.id) } }
    LaunchedEffect(focus, decision.id, busy) { focus.setCanConfirm(decision.id, !busy) }
}

// ---- Play a card, discard, retrieve, upgrade, keep ------------------------------------------

/**
 * Turn step 1 (p.7): the hand as card images. Tap one to see it large, tap it again to play it
 * facedown. With no cards, Pass.
 */
@Composable
internal fun HandPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    table.decider?.takeIf { it.at == null }?.let { hero ->
        Text(
            "${table.hero(hero.heroId).name} is off the board. When this card resolves you may respawn in " +
                "your Throne Zone, then act with it (p.7).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val offered = offeredCards(table)
    OfferedRow(table, offered, focus) { key -> openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer) }
    if (offered.any { it.choice != null }) TapHint("Tap a card to see it large; tap it again to play it facedown.")
    if (table.buttonOptions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            table.buttonOptions.forEach { choice ->
                OutlinedButton(onClick = {
                    onAnswer(choice)
                }, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(table.label(choice))
                }
            }
        }
    }
}

/** Card choices other than playing: discard, retrieve, which card to keep (upgrading: UpgradeCards.kt). */
@Composable
internal fun CardPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val offered = offeredCards(table)
    val keep = decision.kind == DecisionKind.KEEP_UPGRADE
    if (keep) {
        val pages = keepFocus(table, offered)
        OfferedRow(table, offered, focus, keep = true) { key ->
            focus.open(
                pages.cards,
                pages.indexOf(key),
                selection = CardSelection(
                    action = { cardAction(decision, it) },
                    context = decision.prompt,
                    onConfirm = { at -> pages.answers.getOrNull(at)?.let(onAnswer) },
                ),
                title = focusTitle(table, decision),
                owner = decision.id,
            )
        }
    } else {
        OfferedRow(table, offered, focus) { key ->
            openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer)
        }
    }
    if (offered.any { it.choice != null }) {
        val verb = when (decision.kind) {
            DecisionKind.KEEP_UPGRADE -> "keep it"
            else -> "${cardVerb(decision.prompt).lowercase()} it"
        }
        TapHint("Tap a card to see it large; tap it again to $verb.")
    }
    // The asking card's spells that can't be cast now, one line each with the engine's reason (ADR-040).
    decision.context.unavailableCards.forEach { u ->
        val name = table.card(u.cardId)?.name ?: return@forEach
        Text(
            "$name: ${unavailableCardLine(table, u.cardId)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (decision.kind == DecisionKind.KEEP_UPGRADE) {
        Text(
            "One goes to your hand; the other is tucked under your hero as an item (p.15, p.16).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TapHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.TouchApp,
            null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun List<OfferedCard>.indexOfKey(key: String): Int = indexOfFirst { it.card.id == key }.coerceAtLeast(0)

/**
 * The cards in a row: the whole hand in view (five cards after a level up) shrinking to fit, down to
 * a size still easy to tap; only beyond that does the row scroll. Few cards are shown larger (two
 * cards to keep side by side, up to 140 dp), so the card itself is what is read (ADR-041). Each one
 * is a TalkBack node that opens the viewer; one the question doesn't offer is dimmed and marked.
 * Keeping (level up), each card shows the item it gives if not kept and the spells it casts.
 * [onOpen] gets the key of the viewer page to open (the card's id, or a named spell's [namedKey]).
 */
@Composable
private fun OfferedRow(
    table: TableUi,
    offered: List<OfferedCard>,
    focus: CardFocusState,
    keep: Boolean = false,
    onOpen: (String) -> Unit,
) {
    val reason = table.decision?.kind?.let(::notOffered)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = offered.size.coerceAtLeast(1)
        // A landscape spell card (Gydion) is as tall as the others and about twice as wide (CardShape.kt).
        val units = offered.sumOf { cardAspect(it.card) / CARD_ASPECT.toDouble() }.toFloat().coerceAtLeast(1f)
        val min = if (offered.any { it.card.isSpell }) MIN_SPELL_CARD else MIN_HAND_CARD
        val width = ((maxWidth - Spacing.sm * (n - 1)) / units).coerceIn(min, largestCard(n))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            offered.forEach { item ->
                val named = if (keep) table.namedBy(item.card) else emptyList()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OfferedThumb(item, width, keep, named, unavailableCardLine(table, item.card.id) ?: reason, focus) {
                        onOpen(item.card.id)
                    }
                    NamedThumbs(FocusCard(item.card), named, width, focus, onOpen)
                }
            }
        }
    }
}

@Composable
private fun OfferedThumb(
    item: OfferedCard,
    width: Dp,
    keep: Boolean,
    named: List<Card>,
    reason: String?,
    focus: CardFocusState,
    onOpen: () -> Unit,
) {
    val card = item.card
    val offered = item.choice != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(cardWidth(card, width)).clearAndSetSemantics {
            contentDescription = listOfNotNull(spokenCard(card), spokenNamed(named)).joinToString(", ")
            stateDescription = listOfNotNull(
                if (keep) card.item?.let { "As an item: +1 ${PlayText.stat(it)}" } else null,
                if (!offered) reason else null,
            ).joinToString(", ")
            role = Role.Button
            onClick(label = "Look at the card") {
                onOpen()
                true
            }
        },
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.cardFocusOrigin(focus, card.id).alpha(if (offered) 1f else DIMMED),
        ) {
            TableCard(card, width = width)
        }
        if (keep) card.item?.let { ItemCaption(it) }
        if (!offered) {
            Icon(
                Icons.Outlined.Block,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaledIconSize(14.dp, 1.3f)),
            )
        }
    }
}

// ---- Defense (p.14) ---------------------------------------------------------------------

/**
 * Attacked: the attacker's card with the Attack total, then the hand's cards themselves (their
 * Defense is printed on them), each with the verdict under it: "Holds" or "Falls" in words and an
 * icon (never colour alone) and the Defense total when items or minions change it; "Don't defend".
 * Tap a card to see it large with its verdict, tap it again to defend with it; "Don't defend" is
 * chosen, then confirmed.
 */
@Composable
internal fun DefensePanel(
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
    AttackHeader(heading, attack, context.minionModifier, table.sourceCard, decision.prompt, focus)
    val offered = table.cardOptions.mapNotNull { choice -> table.card(choice.cardId)?.let { OfferedCard(it, choice) } }
    val rows = offered.map { item ->
        val id = item.card.id
        defenseLine(context.defensePreview[id], context.blockPreview[id], attack)
    }
    if (attack != null && rows.isNotEmpty() && rows.all { it.verdict.holds == false }) {
        // p.14: a failed defense still discards the card, and the hero is defeated all the same.
        Text(
            "None of your cards stops Attack $attack. Not defending keeps them in your hand (p.14).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (offered.isNotEmpty()) TapHint("Tap a card to see it large; tap it again to defend with it.")
    val focusCards = offered.mapIndexed { i, item -> FocusCard(item.card, verdict = rows[i].verdict) }
    val summary = listOfNotNull(heading, attack?.let { "Attack $it" }).joinToString(" · ")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = offered.size.coerceAtLeast(1)
        val width = ((maxWidth - Spacing.sm * (n - 1)) / n).coerceIn(MIN_HAND_CARD, HAND_CARD)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            offered.forEachIndexed { i, item ->
                DefenseCard(item.card, rows[i], width, focus) {
                    openOffered(focus, table, offered, i, onAnswer, context = summary, cards = focusCards)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        table.buttonOptions.forEach { choice ->
            DeclineRow(table.label(choice), choice == table.selected) { onSelect(choice) }
        }
    }
    val declined = table.selected?.takeIf { it !is Choice.Card && it in table.buttonOptions }
    if (declined != null) {
        // The button is under every row: bring it into view once "Don't defend" is chosen.
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(declined) { requester.bringIntoView() }
        Button(
            onClick = { onAnswer(declined) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bringIntoViewRequester(requester),
        ) {
            Text(table.label(declined))
        }
    }
}

/**
 * The attack: the attacker's card (tap to read it), who attacks and how, the Attack total, and the
 * minion defense modifier counted. It carries the question for TalkBack (a polite live region).
 */
@Composable
private fun AttackHeader(
    heading: String,
    attack: Int?,
    minionModifier: Int?,
    card: Card?,
    prompt: String,
    focus: CardFocusState,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (card != null) {
                Surface(
                    onClick = { focus.show(listOf(card), title = "Attacking card") },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .cardFocusOrigin(focus, card.id)
                        .clearAndSetSemantics {
                            contentDescription = "Attacking card: ${spokenCard(card)}"
                            role = Role.Button
                            onClick(label = "Read the card") {
                                focus.show(listOf(card), title = "Attacking card")
                                true
                            }
                        },
                ) { TableCard(card, width = ATTACK_CARD) }
                Spacer(Modifier.width(Spacing.md))
            }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = prompt
                },
            ) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    attack?.let { "Attack $it" } ?: "Attack decided by the card",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
                minionModifier?.takeIf { it != 0 }?.let { m ->
                    Text(
                        "Minion defense modifier ${if (m > 0) "+$m" else "$m"} is counted below (p.14).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    "Discard a card to defend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** One hand card to defend with: the official card, the verdict under it, the total when it differs. */
@Composable
private fun DefenseCard(card: Card, line: DefenseLine, width: Dp, focus: CardFocusState, onOpen: () -> Unit) {
    val printed = card.values.defense
    val changed = line.total != null && line.total != printed
    val spoken = listOf(card.name, line.detail, line.verdict.label).joinToString(", ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(width)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Look at the card") {
                    onOpen()
                    true
                }
            },
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.cardFocusOrigin(focus, card.id),
        ) { TableCard(card, width = width) }
        Verdict(line.verdict.holds, line.verdict.label)
        if (changed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    GameIcons.defense.vector,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(scaledIconSize(12.dp, 1.3f)),
                )
                Text(
                    " ${line.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                modifier = Modifier.size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text,
            style = if (verdict == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
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

/** The largest a card in a row of [n] grows: few cards are shown large enough to read. */
private fun largestCard(n: Int): Dp = when {
    n <= 2 -> 140.dp
    n == 3 -> 110.dp
    else -> HAND_CARD
}

private val HAND_CARD = 84.dp
private val MIN_HAND_CARD = 60.dp
private val ATTACK_CARD = 56.dp

/** A row with spells: a landscape card this tall (≈ 90 × 62 dp) is still easy to tap and read. */
private val MIN_SPELL_CARD = 44.dp
private const val DIMMED = 0.45f
