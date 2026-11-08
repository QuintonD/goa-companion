package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.LifeCounterIcon
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TintTag
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.game.DefeatPreview
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
internal fun openOffered(
    focus: CardFocusState,
    table: TableUi,
    offered: List<OfferedCard>,
    index: Int,
    onAnswer: (Choice) -> Unit,
    context: String? = null,
    cards: List<FocusCard> = offered.map { focusCard(table, it) },
    asText: Boolean = false,
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
        asText = asText,
    )
}

/** The card Read card opens: the chosen one, else the first the question offers. */
private fun readIndex(offered: List<OfferedCard>, chosen: Choice?): Int =
    offered.indexOfFirst { it.choice != null && it.choice == chosen }.takeIf { it >= 0 }
        ?: offered.indexOfFirst { it.choice != null }.coerceAtLeast(0)

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
    table.decider?.let { HandRuneLine(table, it) }
    val offered = offeredCards(table)
    val chosen = chosenCard(table)
    OfferedRow(table, offered, focus, fan = true, chosen = chosen, onChoose = onSelect) { key ->
        openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer)
    }
    if (offered.any { it.choice != null }) {
        CommitRow(table, chosen, "Play facedown", busy, onAnswer) {
            openOffered(focus, table, offered, readIndex(offered, chosen), onAnswer, asText = true)
        }
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
    val read: (String, Boolean) -> Unit
    if (keep) {
        val pages = keepFocus(table, offered)
        read = { key, asText ->
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
                asText = asText,
            )
        }
        OfferedRow(table, offered, focus, keep = true, chosen = chosen, onChoose = onSelect) { key -> read(key, false) }
    } else {
        read = { key, asText ->
            openOffered(focus, table, offered, offered.indexOfKey(key), onAnswer, asText = asText)
        }
        OfferedRow(table, offered, focus, fan = true, chosen = chosen, onChoose = onSelect) { key -> read(key, false) }
    }
    if (offered.any { it.choice != null }) {
        val verb = when (decision.kind) {
            DecisionKind.KEEP_UPGRADE -> "Keep"
            else -> cardVerb(decision.prompt)
        }
        CommitRow(table, chosen, verb, busy, onAnswer) {
            offered.getOrNull(readIndex(offered, chosen))?.let { read(it.card.id, true) }
        }
    }
    // The asking card's spells that can't be cast now are dimmed with a block mark; the viewer says why (ADR-040).
}

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
    /** The button's words when the question has its own ("Defend · Blink Strike"); else the move is named. */
    label: String? = null,
) {
    val decision = table.decision ?: return
    // The button is under the cards: once one is chosen, it scrolls into view.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(chosen) { if (chosen != null) requester.bringIntoView() }
    val move = when (chosen) {
        null -> null
        is Choice.Card -> table.card(chosen.cardId)?.let { cardAction(decision, it) } ?: table.label(chosen)
        else -> table.label(chosen)
    }
    val words = label ?: move
    PlateButton(
        onClick = { if (chosen != null) onAnswer(chosen) },
        enabled = chosen != null && !busy,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).bringIntoViewRequester(requester),
    ) {
        Text(words ?: waiting, textAlign = TextAlign.Center)
    }
}

/**
 * The foot of every card question: Read card (the chosen card, else the first, opened in the card viewer with its
 * words as text, ADR-071's reader) and the bronze button that answers. One row, so reading never costs the decision
 * its place in view.
 */
@Composable
internal fun CommitRow(
    table: TableUi,
    chosen: Choice?,
    waiting: String,
    busy: Boolean,
    onAnswer: (Choice) -> Unit,
    label: String? = null,
    onRead: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        ReadCardButton(onRead)
        CommitButton(table, chosen, waiting, busy, onAnswer, Modifier.weight(1f), label = label)
    }
}

/**
 * Read card, as the book alone (a 48 dp steel square): the card viewer with the card's words as text, for what a
 * thumbnail prints too small to read. TalkBack and the long-press tooltip say it in words.
 */
@Composable
internal fun ReadCardButton(onRead: () -> Unit) {
    PlateButton(
        onClick = onRead,
        tone = PlateTone.STEEL,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(scaledIconSize(48.dp, 1.2f))
            .semantics { contentDescription = "Read card as text" },
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(scaledIconSize(22.dp, 1.3f)))
    }
}

/**
 * Under a card to choose: its name in the game's type, readable at arm's length where the scan's own print is not (its
 * numbers follow in a strip, [CardNumbers]). TalkBack hears the card on its node already: this is for the eye.
 */
@Composable
internal fun CardCaption(card: Card, width: Dp) {
    Text(
        // A narrow card breaks "Light-Fingered" after its hyphen, never mid-word.
        card.name.replace("-", "-​"),
        style = CardNameStyle,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        // A little air either side: in a fanned hand the cards touch, their names must not.
        modifier = Modifier.width(width).padding(horizontal = 3.dp).clearAndSetSemantics {},
    )
}

/** A card's name under it: Crimson, as the game prints it, at 14 sp. */
private val CardNameStyle = GoaTextStyles.printed.copy(
    fontSize = 14.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.SemiBold,
    // A word wider than a narrow card at large text breaks with a hyphen, not bare ("Aspirin / g").
    hyphens = Hyphens.Auto,
)

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
    val latest by rememberUpdatedState(tap)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(cardWidth(card, width))
            // The name and numbers under the card answer a tap as the card does (its Surface takes its own taps).
            .pointerInput(Unit) { detectTapGestures { latest() } }
            .clearAndSetSemantics {
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
        CardCaption(card, cardWidth(card, width))
        if (!card.isSpell) CardNumbers(card, cardWidth(card, width))
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

// ---- Rule rows --------------------------------------------------------------------------

/**
 * A decision's one rule line: what the choice rests on, with the rulebook page it comes from as a small bronze pill at
 * its end (never "(p.14)" in a button or caption). [verdict] puts its mark first (a tick, a warning), when it has one.
 */
@Composable
internal fun RuleRow(
    text: String,
    page: String?,
    modifier: Modifier = Modifier,
    verdict: Boolean? = null,
    /** It changes with the choice (TalkBack hears it as it does); a fixed note is read in its turn. */
    live: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (live) liveRegion = LiveRegionMode.Polite
            contentDescription = listOfNotNull(text, page?.let { "Rulebook ${it.replace("p.", "page ")}" })
                .joinToString(". ")
        },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            when (verdict) {
                true -> Icons.Outlined.CheckCircle
                false -> Icons.Outlined.ErrorOutline
                null -> Icons.Outlined.Info
            },
            null,
            tint = if (verdict == null) MaterialTheme.colorScheme.onSurfaceVariant else verdictColor(verdict),
            modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(16.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        page?.let {
            Spacer(Modifier.width(Spacing.xs))
            PagePill(it)
        }
    }
}

/**
 * "p.14" as a small tinted pill: where a rule is in the rulebook, out of the way of the words. A tint, not an edge: it
 * often sits on a button or a plate that is the frame already (DESIGN.md "Framing levels").
 */
@Composable
internal fun PagePill(page: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    TintTag(page, modifier, color)
}

@Composable
internal fun verdictColor(verdict: Boolean?): Color = when (verdict) {
    true -> MaterialTheme.colorScheme.secondary
    false -> MaterialTheme.colorScheme.error
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The largest a card in a row of [n] grows: few cards are shown large enough to read. */
private fun largestCard(n: Int): Dp = when {
    n <= 2 -> 112.dp
    n == 3 -> 92.dp
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

/** A row with spells: a landscape card this tall (≈ 90 × 62 dp) is still easy to tap and read. */
private val MIN_SPELL_CARD = 44.dp
private const val DIMMED = 0.45f
