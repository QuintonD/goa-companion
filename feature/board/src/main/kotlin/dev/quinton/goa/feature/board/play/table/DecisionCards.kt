package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.PlateButton
import dev.quinton.goa.feature.board.ui.PlateTone
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.CardNote
import dev.quinton.goa.feature.board.ui.cards.CardSelection
import dev.quinton.goa.feature.board.ui.cards.CardVerdict
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardAspect
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.cardWidth
import dev.quinton.goa.feature.board.ui.cards.isSpell
import dev.quinton.goa.feature.board.ui.isLandscape

/*
 * Card questions (play, defend, discard, retrieve, upgrade, keep): a tap on a card chooses it (bronze
 * edge, lifted, a tick; [TableUi.selected]); a tap on the chosen card reads it large in the card focus
 * viewer (docs/DESIGN.md, "Card focus"). Only a bronze button answers, and it names what it does
 * ("Discard Magnetic Dagger", "Defend with Blink Strike"): a tap on a card never both shows and commits.
 */

/** Opens [offered] in the viewer at [index]; its button answers [onAnswer]. */
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
 * Turn step 1 (p.7): the hand as card images. Tap one to choose it (again to read it large); the button plays it
 * facedown. With no cards, Pass.
 */
@Composable
internal fun HandPicker(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean,
) {
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
    table.decider?.let { HandRuneLine(table, it) }
    val offered = offeredCards(table)
    val chosen = chosenCard(table)
    OfferedRow(table, offered, focus, fan = true, chosen = chosen, onChoose = onSelect) { key ->
        openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer)
    }
    if (offered.any { it.choice != null }) {
        TapHint(CHOOSE_HINT)
        CommitButton(table, chosen, "Choose a card to play facedown", busy, onAnswer)
    }
    if (table.buttonOptions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            table.buttonOptions.forEach { choice ->
                PlateButton(
                    onClick = { onAnswer(choice) },
                    enabled = !busy,
                    tone = PlateTone.STEEL,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text(table.label(choice))
                }
            }
        }
    }
}

/** Card choices other than playing: discard, retrieve, which card to keep (upgrading: UpgradeCards.kt). */
@Composable
internal fun CardPicker(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean,
) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val offered = offeredCards(table)
    val keep = decision.kind == DecisionKind.KEEP_UPGRADE
    val chosen = chosenCard(table)
    if (keep) {
        val pages = keepFocus(table, offered)
        OfferedRow(table, offered, focus, keep = true, chosen = chosen, onChoose = onSelect) { key ->
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
        OfferedRow(table, offered, focus, fan = true, chosen = chosen, onChoose = onSelect) { key ->
            openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer)
        }
    }
    if (offered.any { it.choice != null }) {
        val verb = when (decision.kind) {
            DecisionKind.KEEP_UPGRADE -> "keep"
            else -> cardVerb(decision.prompt).lowercase()
        }
        TapHint(CHOOSE_HINT)
        CommitButton(table, chosen, "Choose a card to $verb", busy, onAnswer)
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

/** The hint under every row of cards to choose from. */
internal const val CHOOSE_HINT = "Tap a card to choose it; tap it again to read it large."

/** The card the person has chosen in the panel and not yet committed ([TableUi.selected]), if it is an option. */
internal fun chosenCard(table: TableUi): Choice.Card? =
    (table.selected as? Choice.Card)?.takeIf { it in table.cardOptions }

/**
 * The one control that answers a card question: a bronze button naming the commitment ("Discard Magnetic Dagger",
 * "Play Shock facedown"), there before a card is chosen ([waiting], dimmed), so the panel doesn't jump.
 */
@Composable
internal fun CommitButton(
    table: TableUi,
    chosen: Choice?,
    waiting: String,
    busy: Boolean,
    onAnswer: (Choice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decision = table.decision ?: return
    // The button is under the cards: once one is chosen, it scrolls into view.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(chosen) { if (chosen != null) requester.bringIntoView() }
    val label = when (chosen) {
        null -> null
        is Choice.Card -> table.card(chosen.cardId)?.let { cardAction(decision, it) } ?: table.label(chosen)
        else -> table.label(chosen)
    }
    PlateButton(
        onClick = { if (chosen != null) onAnswer(chosen) },
        enabled = chosen != null && !busy,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).bringIntoViewRequester(requester),
    ) {
        Text(label ?: waiting, textAlign = TextAlign.Center)
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
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun List<OfferedCard>.indexOfKey(key: String): Int = indexOfFirst { it.card.id == key }.coerceAtLeast(0)

/**
 * The cards in a row: the whole hand in view (five cards after a level up) shrinking to fit, down to
 * a size still easy to tap; only beyond that does the row scroll. Few cards are shown larger (two
 * cards to keep side by side, up to 140 dp), so the card itself is what is read (ADR-041). Each one
 * is a TalkBack node that opens the viewer; one the question doesn't offer is dimmed and marked.
 * Keeping (level up), each card shows the item it gives if not kept and the spells it casts.
 * [fan] lays them as a hand held in a gentle fan (mock-up 01), each still a whole tap target and one
 * TalkBack node. [onOpen] gets the key of the viewer page to open (the card's id, or a named spell's [namedKey]).
 */
@Composable
private fun OfferedRow(
    table: TableUi,
    offered: List<OfferedCard>,
    focus: CardFocusState,
    keep: Boolean = false,
    fan: Boolean = false,
    chosen: Choice.Card? = null,
    onChoose: ((Choice) -> Unit)? = null,
    onOpen: (String) -> Unit,
) {
    val reason = table.decision?.kind?.let(::notOffered)
    val spread = fan && offered.none { it.card.isSpell }
    // Sideways the panel is tall and wide: a hand takes its width, up to what still fits its height.
    val largest = if (isLandscape()) LANDSCAPE_HAND_CARD else null
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = offered.size.coerceAtLeast(1)
        // A landscape spell card (Gydion) is as tall as the others and about twice as wide (CardShape.kt).
        val units = offered.sumOf { cardAspect(it.card) / CARD_ASPECT.toDouble() }.toFloat().coerceAtLeast(1f)
        val min = if (offered.any { it.card.isSpell }) MIN_SPELL_CARD else MIN_HAND_CARD
        val gap = if (spread) 0.dp else Spacing.sm
        val edge = if (spread) FAN_EDGE else 0.dp
        val width = ((maxWidth - edge * 2 - gap * (n - 1)) / units).coerceIn(min, largest ?: largestCard(n))
        // Only a row that overflows scrolls: a fanned card's corners poke past its row, and a scroller
        // would try to bring them into view and never could (TalkBack and tests both ask it to).
        val overflows = width * units + gap * (n - 1) + edge * 2 > maxWidth
        val scrolling = if (overflows) Modifier.horizontalScroll(rememberScrollState()) else Modifier
        Row(
            Modifier
                .fillMaxWidth()
                .then(scrolling)
                .padding(start = edge, end = edge, top = LIFT, bottom = if (spread) FAN_DROP else 0.dp),
            horizontalArrangement = if (spread) {
                Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(gap)
            },
        ) {
            offered.forEachIndexed { i, item ->
                val named = if (keep) table.namedBy(item.card) else emptyList()
                // About 3.2° per card from the centre, the outer ones slightly lower.
                val fromCentre = i - (offered.size - 1) / 2f
                val tilt = if (spread) Modifier.fanned(fromCentre) else Modifier
                Column(tilt, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val isChosen = item.choice != null && item.choice == chosen
                    val choose = item.choice?.takeIf { !isChosen }?.let { c -> onChoose?.let { { it(c) } } }
                    OfferedThumb(
                        item,
                        width,
                        keep,
                        named,
                        unavailableCardLine(table, item.card.id) ?: reason,
                        focus,
                        chosen = isChosen,
                        onChoose = choose,
                    ) {
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
    chosen: Boolean = false,
    /** Chooses this card; null when a tap reads it instead (it is chosen already, or no option). */
    onChoose: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val card = item.card
    val offered = item.choice != null
    val tap = onChoose ?: onOpen
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(cardWidth(card, width)).clearAndSetSemantics {
            contentDescription = listOfNotNull(spokenCard(card), spokenNamed(named)).joinToString(", ")
            stateDescription = listOfNotNull(
                "Chosen".takeIf { chosen },
                if (keep) card.item?.let { "As an item: +1 ${PlayText.stat(it)}" } else null,
                if (!offered) reason else null,
            ).joinToString(", ")
            role = Role.Button
            selected = chosen
            onClick(label = if (onChoose != null) "Choose" else "Look at the card") {
                tap()
                true
            }
            if (onChoose != null) {
                customActions = listOf(
                    CustomAccessibilityAction("Look at the card") {
                        onOpen()
                        true
                    },
                )
            }
        },
    ) {
        ChoosableCard(card, width, chosen, offered, focus, tap)
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

/**
 * A card to choose from in the panel: the official card, dimmed when the question doesn't offer it; chosen, it is
 * lifted with a bronze edge and a bronze tick at its corner (chosen in shape too, never by colour alone).
 */
@Composable
internal fun ChoosableCard(
    card: Card,
    width: Dp,
    chosen: Boolean,
    offered: Boolean,
    focus: CardFocusState,
    onTap: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(width * 0.08f),
    border: BorderStroke? = null,
) {
    Box {
        Surface(
            onClick = onTap,
            shape = shape,
            color = Color.Transparent,
            shadowElevation = if (chosen) 8.dp else 3.dp,
            border = if (chosen) BorderStroke(3.dp, ChosenEdge) else border,
            modifier = Modifier
                .cardFocusOrigin(focus, card.id)
                .graphicsLayer { translationY = if (chosen) -LIFT.toPx() else 0f }
                .alpha(if (offered) 1f else DIMMED),
        ) {
            TableCard(card, width = width)
        }
        if (chosen) ChosenTick(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = -LIFT - 5.dp))
    }
}

/** The bronze tick on a chosen card's corner. */
@Composable
private fun ChosenTick(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(scaledIconSize(22.dp, 1.3f))
            .background(ChosenEdge, CircleShape)
            .border(1.5.dp, ChosenInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, null, tint = ChosenInk, modifier = Modifier.size(scaledIconSize(15.dp, 1.3f)))
    }
}

internal val ChosenEdge = Color(0xFFC9A35B)
private val ChosenInk = Color(0xFF0B1622)

/** How far a chosen card rises out of its row. */
internal val LIFT = 8.dp

// ---- Defense (p.14) ---------------------------------------------------------------------

/**
 * Attacked: the attacker's card with the Attack total, then the hand's cards themselves (their
 * Defense is printed on them), each with the verdict under it: "Holds" or "Falls" in words and an
 * icon (never colour alone) and the Defense total as it counts. A tap chooses a card (again: read it
 * large); "Don't defend" is chosen the same way. What is chosen shows its outcome in the engine's
 * numbers ("Defense 3 = card 2 + minion 1 against Attack 6"), and the bronze button answers.
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
    val focusCards = offered.mapIndexed { i, item -> FocusCard(item.card, verdict = rows[i].verdict) }
    val summary = listOfNotNull(heading, attack?.let { "Attack $it" }).joinToString(" · ")
    val chosen = chosenCard(table)
    // The whole decision in view without scrolling: the cards and "Don't defend" in one row, the outcome in a
    // line, the button (the hint is the button's own words until something is chosen).
    val declines = table.buttonOptions
    // Under narrow cards the words stop growing at 1.3× (as the attack's header): "Falls" never breaks mid-word.
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, ATTACK_MAX_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val n = (offered.size + declines.size).coerceAtLeast(1)
            val width = ((maxWidth - Spacing.sm * (n - 1)) / n).coerceIn(MIN_HAND_CARD, HAND_CARD)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = LIFT),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                offered.forEachIndexed { i, item ->
                    val isChosen = item.choice == chosen
                    val read = {
                        openOffered(focus, table, offered, i, onAnswer, context = summary, cards = focusCards)
                    }
                    DefenseCard(
                        item.card,
                        rows[i],
                        width,
                        focus,
                        chosen = isChosen,
                        onChoose = item.choice?.takeIf { !isChosen }?.let { c -> { onSelect(c) } },
                        onOpen = read,
                    )
                }
                declines.forEach { choice ->
                    DeclineTile(table.label(choice), choice == table.selected, width) { onSelect(choice) }
                }
            }
        }
    }
    val declined = table.selected?.takeIf { it !is Choice.Card && it in table.buttonOptions }
    val picked = chosen?.let { c -> offered.indexOfFirst { it.choice == c }.takeIf { it >= 0 } }
    if (picked != null) {
        val items = table.decider?.itemStats?.count { it == Stat.DEFENSE } ?: 0
        DefenseOutcome(offered[picked].card, rows[picked], attack, context.minionModifier, items, context.rangedAttack)
    }
    CommitButton(table, chosen ?: declined, "Tap a card to defend with, or Don't defend", busy, onAnswer)
}

/**
 * What defending with the chosen card comes to, from the engine's numbers: the verdict in words and shape, the
 * Defense total and how it is made against the Attack; for a card whose text decides, that text verbatim and the
 * attack it is weighed against (melee or ranged, its value), as the engine knows them now.
 */
@Composable
private fun DefenseOutcome(card: Card, line: DefenseLine, attack: Int?, minion: Int?, items: Int, ranged: Boolean?) {
    val sum = defenseSum(line.total, card.values.defense, minion, items)
    val against = attack?.let { " against Attack $it" }.orEmpty()
    val result = when (line.verdict.holds) {
        true -> if (line.verdict.label == "Blocks") "Blocks: the attack is stopped" else "Holds: the attack fails"
        false -> "Falls: the attack succeeds"
        null -> "Decided by its text as it resolves"
    }
    val numbers = when {
        line.total == null -> line.detail
        sum != null -> "$sum$against"
        else -> "Defense ${line.total}$against"
    }
    val color = when (line.verdict.holds) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // One or two lines over the button: what the choice comes to, then how it is counted (p.14).
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        if (line.verdict.holds != null) {
            Icon(
                if (line.verdict.holds == true) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                null,
                tint = color,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$result (p.14) · $numbers",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (line.verdict.holds == null) {
                val text = CardText.plain(card.text).trim()
                val kind = ranged?.let { if (it) "ranged" else "melee" }
                val attackLine = listOfNotNull(kind?.let { "a $it attack" }, attack?.let { "Attack $it" })
                Text(
                    listOfNotNull(
                        text.takeIf { it.isNotEmpty() }?.let { "“$it”" },
                        attackLine.takeIf { it.isNotEmpty() }?.let { "This is ${it.joinToString(", ")}." },
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The attack: the attacker's card (tap to read it), who attacks and how, the Attack total, and the
 * minion defense modifier counted, on the text box in the attacking card's colour, as every question asked by a card.
 * It carries the question for TalkBack (a polite live region). Its text stops growing at 1.3×: with more it pushed
 * the cards to defend with off the screen.
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
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, ATTACK_MAX_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        AttackBox(heading, attack, minionModifier, card, prompt, focus)
    }
}

@Composable
private fun AttackBox(
    heading: String,
    attack: Int?,
    minionModifier: Int?,
    card: Card?,
    prompt: String,
    focus: CardFocusState,
) {
    val color = card?.let { PlateColor.of(it.color) } ?: PlateColor.RED
    CardTextBox(color, Modifier.fillMaxWidth(), cap = "Attack") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card != null) {
                Surface(
                    onClick = { focus.show(listOf(card), title = "Attacking card") },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
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
                Text(heading, style = MaterialTheme.typography.labelLarge)
                Text(
                    attack?.let { "Attack $it" } ?: "Attack decided by the card",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                minionModifier?.takeIf { it != 0 }?.let { m ->
                    Text(
                        "Minion defense ${if (m > 0) "+$m" else "$m"} counted below (p.14)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * One hand card to defend with: the official card, under it the verdict in words and shape and its Defense total as
 * it counts ("Defense 3 vs 6"; a Block or a card whose text decides says so). A tap chooses it, again reads it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefenseCard(
    card: Card,
    line: DefenseLine,
    width: Dp,
    focus: CardFocusState,
    chosen: Boolean,
    onChoose: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    val spoken = listOf(card.name, line.verdict.detail, line.verdict.label).joinToString(", ")
    val tap = onChoose ?: onOpen
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(width)
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = if (chosen) "Chosen" else ""
                role = Role.Button
                selected = chosen
                onClick(label = if (onChoose != null) "Choose" else "Look at the card") {
                    tap()
                    true
                }
                if (onChoose != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Look at the card") {
                            onOpen()
                            true
                        },
                    )
                }
            },
    ) {
        ChoosableCard(card, width, chosen, offered = true, focus = focus, onTap = tap)
        // The verdict and the total on one line when they fit (the row stays short enough for the button to be
        // in view), the total under it when not.
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // "By its text" under a narrow card: the outcome line and the viewer say it in full.
            Verdict(line.verdict.holds, if (line.verdict.holds == null) "By its text" else line.verdict.label)
            line.total?.let { total ->
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        GameIcons.defense.vector,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(scaledIconSize(13.dp, 1.3f)),
                    )
                    Text(
                        "$total",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** "Holds" with a tick or "Falls" with a warning sign: words and shape, not colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Verdict(verdict: Boolean?, text: String) {
    val color = when (verdict) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Under a narrow card with large text the word goes under its icon rather than breaking ("Fal / ls").
    FlowRow(horizontalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
        if (verdict != null) {
            Icon(
                if (verdict) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                null,
                tint = color,
                modifier = Modifier.padding(end = 3.dp).size(scaledIconSize(16.dp, 1.3f)),
            )
        }
        Text(
            text,
            style = if (verdict == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // A verdict word never breaks; only "By its text" may take two lines.
            softWrap = verdict == null,
        )
    }
}

/**
 * Not defending, as a tile the size of a card at the end of the row (so the whole choice is in one row, in view):
 * chosen like a card, lifted with the bronze edge and tick.
 */
@Composable
private fun DeclineTile(label: String, selected: Boolean, width: Dp, onClick: () -> Unit) {
    Box(Modifier.width(width)) {
        Surface(
            shape = RoundedCornerShape(width * 0.08f),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else PlateWell,
            border = BorderStroke(
                if (selected) 3.dp else 1.dp,
                if (selected) ChosenEdge else GoaTheme.colors.engravedBorder,
            ),
            modifier = Modifier
                .width(width)
                .heightIn(min = width / CARD_ASPECT)
                .graphicsLayer { translationY = if (selected) -LIFT.toPx() else 0f }
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.selected = selected
                    contentDescription = "$label: keep your cards; the attack succeeds (p.14)"
                },
        ) {
            Column(
                Modifier.padding(horizontal = 2.dp, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Block,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Keep your cards (p.14)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (selected) ChosenTick(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = -LIFT - 5.dp))
    }
}

/** The largest a card in a row of [n] grows: few cards are shown large enough to read. */
private fun largestCard(n: Int): Dp = when {
    n <= 2 -> 140.dp
    n == 3 -> 110.dp
    else -> HAND_CARD
}

/** A hand card's place in the fan, [fromCentre] cards from the middle. */
private fun Modifier.fanned(fromCentre: Float): Modifier = graphicsLayer {
    val away = kotlin.math.abs(fromCentre)
    rotationZ = fromCentre * FAN_DEGREES
    translationY = away * (away + 1f) * 1.dp.toPx()
}

private val HAND_CARD = 84.dp
private val LANDSCAPE_HAND_CARD = 124.dp
private const val FAN_DEGREES = 3.2f

/** Room for the outer cards' corners and for how far they drop. */
private val FAN_EDGE = 6.dp
private val FAN_DROP = 6.dp
private val MIN_HAND_CARD = 60.dp
private val ATTACK_CARD = 46.dp
private const val ATTACK_MAX_SCALE = 1.3f

/** A row with spells: a landscape card this tall (≈ 90 × 62 dp) is still easy to tap and read. */
private val MIN_SPELL_CARD = 44.dp
private const val DIMMED = 0.45f
