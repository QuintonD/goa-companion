package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * The turn track (ADR-041, docs/DESIGN.md "Play against the computer"): after the reveal the hero
 * strip becomes the played cards themselves, in the order the heroes act. Each card carries its
 * initiative as it counts, its hero's portrait (team ring) and name with the team's shape, and its
 * state in a word on a band across the card: DONE (with a tick, the card stepped back), NOW (bronze),
 * NEXT (teal), PASS / SKIPPED; never colour alone. A tap opens the card large in the card focus
 * viewer, swiping through the turn's cards; a long press (or TalkBack's action) opens the hero.
 */

/**
 * The turn's cards in a row, scrolling when they don't fit. [compact] (a phone held sideways with
 * the panel shown, or the panel hidden): smaller cards, no names under them. [onHero] opens a hero's
 * game state (long press, TalkBack's action); null = no such action (a recap in the panel).
 */
@Composable
fun TurnTrackRow(
    table: TableUi,
    track: TurnTrack,
    onHero: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    focus: CardFocusState? = null,
    compact: Boolean = false,
) {
    val viewable = trackFocusCards(table, track)
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        track.slots.forEach { slot ->
            val first = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.cardId?.let(table::card)
            val index = viewable.indexOfFirst { it.key == trackKey(slot.heroId, first?.id) }
            TrackSlotView(
                table = table,
                slot = slot,
                compact = compact,
                focus = focus,
                onOpen = {
                    if (focus != null && index >= 0) {
                        focus.open(viewable, index, title = "Round ${track.round} · Turn ${track.turn}")
                    } else {
                        onHero?.invoke(slot.heroId)
                    }
                },
                onHero = onHero?.let { open -> { open(slot.heroId) } },
            )
        }
    }
}

/** The turn's face-up cards for the viewer, each captioned with its hero, state and initiative. */
private fun trackFocusCards(table: TableUi, track: TurnTrack): List<FocusCard> = track.slots.flatMap { slot ->
    slot.cards.mapNotNull { view ->
        val card = view.cardId?.takeIf { !view.facedown }?.let(table::card) ?: return@mapNotNull null
        FocusCard(card, key = trackKey(slot.heroId, card.id), caption = slotCaption(table, slot, card))
    }
}

private fun trackKey(heroId: String, cardId: String?) = "track/$heroId/$cardId"

/** "Wasp · Done · Movement · Initiative 10". */
private fun slotCaption(table: TableUi, slot: TrackSlot, card: Card): String = listOfNotNull(
    table.hero(slot.heroId).name,
    stateWord(slot.state),
    slot.action?.let { actionWord(it, card) },
    slot.initiative?.let { "Initiative $it" },
).joinToString(" · ")

internal fun stateWord(state: SlotState): String = when (state) {
    SlotState.DONE -> "Done"
    SlotState.NOW -> "Now"
    SlotState.NEXT -> "Next"
    SlotState.LATER -> "Later"
    SlotState.PASSED -> "Passed"
    SlotState.SKIPPED -> "Skipped"
}

/** The action as a word: "Movement", "Fast Travel", the primary as the card's type ("Attack"). */
internal fun actionWord(action: ActionType, card: Card?): String = when (action) {
    ActionType.PRIMARY -> PlayText.primary(card?.primary)
    ActionType.MOVEMENT -> "Movement"
    ActionType.FAST_TRAVEL -> "Fast Travel"
    ActionType.ATTACK -> "Attack"
    ActionType.CLEAR -> "Clear"
    ActionType.HOLD -> "Hold"
}

/** The game's icon for an action (the primary's by the card's type). */
internal fun actionIcon(action: ActionType, card: Card?): GameIcon = when (action) {
    ActionType.PRIMARY -> card?.primary?.let(GameIcons::forPrimary) ?: GameIcons.skill
    ActionType.MOVEMENT -> GameIcons.movement
    ActionType.FAST_TRAVEL -> GameIcons.fastTravel
    ActionType.ATTACK -> GameIcons.attack
    ActionType.CLEAR -> GameIcons.clear
    ActionType.HOLD -> GameIcons.hold
}

/** What TalkBack hears for a slot. */
internal fun spokenSlot(table: TableUi, slot: TrackSlot): String {
    val hero = table.heroView(slot.heroId)
    val cards = slot.cards.map { view ->
        view.cardId?.takeIf { !view.facedown }?.let(table::card)?.name ?: "a facedown card"
    }
    return buildList {
        slot.place?.let { add("${PlayText.ordinal(it)} to act") }
        add(table.hero(slot.heroId).name)
        add(BoardText.team(slot.team))
        hero?.let { add(table.controllerLabel(it)) }
        add(
            when (slot.state) {
                SlotState.DONE -> "done"
                SlotState.NOW -> "acting now"
                SlotState.NEXT -> "next"
                SlotState.LATER -> "later"
                SlotState.PASSED -> "passed, no card"
                SlotState.SKIPPED -> "its card left the table unresolved"
            },
        )
        slot.action?.let { add("used ${actionWord(it, table.card(slot.cards.firstOrNull()?.cardId))}") }
        if (cards.isNotEmpty()) add(cards.joinToString(" and "))
        slot.initiative?.let { add("initiative $it") }
        if (slot.tied) add("tied on initiative")
        if (slot.defeated) add("defeated this turn")
        if (slot.offBoard && slot.state != SlotState.DONE && slot.cards.isNotEmpty()) {
            add("off the board: may respawn when its card resolves")
        } else if (slot.offBoard) {
            add("off the board")
        }
    }.joinToString(", ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackSlotView(
    table: TableUi,
    slot: TrackSlot,
    compact: Boolean,
    focus: CardFocusState?,
    onOpen: () -> Unit,
    onHero: (() -> Unit)?,
) {
    val info = table.hero(slot.heroId)
    val spoken = spokenSlot(table, slot)
    val scheme = MaterialTheme.colorScheme
    val border = when (slot.state) {
        SlotState.NOW -> BorderStroke(2.dp, scheme.primary)
        SlotState.NEXT -> BorderStroke(1.5.dp, scheme.secondary)
        else -> BorderStroke(1.dp, GoaTheme.colors.engravedBorder)
    }
    val cardWidth = if (compact) COMPACT_CARD else TRACK_CARD
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = scheme.surface.copy(alpha = 0.95f),
        border = border,
        modifier = Modifier
            .clip(shape)
            .combinedClickable(onClick = onOpen, onLongClick = onHero)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Look at the card") {
                    onOpen()
                    true
                }
                if (onHero != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(showGameState(info.name)) {
                            onHero()
                            true
                        },
                    )
                }
            },
    ) {
        Column(
            Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SlotCards(table, slot, cardWidth, compact, focus)
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = NAME_WIDTH),
                ) {
                    TeamMark(slot.team, markSize = 7.dp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        info.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = cappedSp(11f)),
                        fontWeight = if (slot.state == SlotState.NOW) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The card (Emmitt's second card peeking behind it), its initiative, the hero, the state band. */
@Composable
private fun SlotCards(table: TableUi, slot: TrackSlot, width: Dp, compact: Boolean, focus: CardFocusState?) {
    val first = slot.cards.firstOrNull()
    val firstCard = first?.cardId?.takeIf { !first.facedown }?.let(table::card)
    val extra = slot.cards.size > 1
    Box(Modifier.width(width + if (extra) EXTRA_CARD_OFFSET else 0.dp)) {
        if (extra) {
            val second = slot.cards[1]
            TableCard(
                second.cardId?.takeIf { !second.facedown }?.let(table::card),
                Modifier.align(Alignment.TopEnd).alpha(STACKED_ALPHA),
                width = width,
            )
        }
        Box(
            Modifier
                .padding(top = if (extra) EXTRA_CARD_OFFSET else 0.dp)
                .width(width)
                .aspectRatio(CARD_ASPECT),
        ) {
            if (slot.cards.isEmpty()) {
                EmptyCard(Modifier.fillMaxSize())
            } else {
                val origin = if (focus != null && firstCard != null) {
                    Modifier.cardFocusOrigin(focus, trackKey(slot.heroId, firstCard.id))
                } else {
                    Modifier
                }
                TableCard(firstCard, origin, width = width)
            }
            if (slot.state == SlotState.DONE || slot.state == SlotState.SKIPPED) {
                // Stepped back, as a resolved card moved to the dashboard: dimmed by an overlay, the band says why.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(width * 0.08f))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = DONE_SCRIM)),
                )
            }
            slot.initiative?.let { InitiativeBadge(it, slot.tied, compact, Modifier.align(Alignment.TopStart)) }
            slot.action?.let { action ->
                ActionBadge(actionIcon(action, firstCard), compact, Modifier.align(Alignment.TopEnd))
            }
            SlotPortrait(table, slot, compact, Modifier.align(Alignment.CenterEnd))
            StateBand(slot.state, compact, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** No card: a hollow outline where the card would lie. */
@Composable
private fun EmptyCard(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    )
}

/** The initiative as it counts (p.7), over the printed one; "=" when tied with another hero still to act. */
@Composable
private fun InitiativeBadge(initiative: Int, tied: Boolean, compact: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.background,
        border = BorderStroke(1.dp, if (tied) scheme.secondary else scheme.primary),
        modifier = modifier.padding(1.dp),
    ) {
        Text(
            if (tied) "=$initiative" else "$initiative",
            style = GoaTextStyles.numeric.copy(fontSize = fixedSp(if (compact) 9f else 11f)),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** The action the hero chose, as the game's icon. */
@Composable
private fun ActionBadge(icon: GameIcon, compact: Boolean, modifier: Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background, modifier = modifier.padding(1.dp)) {
        Icon(
            icon.vector,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(2.dp).size(if (compact) 9.dp else 12.dp),
        )
    }
}

/** The hero's portrait in its team ring, over the card's art; crossed out while off the board. */
@Composable
private fun SlotPortrait(table: TableUi, slot: TrackSlot, compact: Boolean, modifier: Modifier) {
    val info = table.hero(slot.heroId)
    val size = if (compact) 18.dp else 24.dp
    Box(modifier.offset(x = 2.dp)) {
        HeroPortrait(
            slot.heroId,
            info.portrait,
            null,
            Modifier
                .size(size)
                .border(2.dp, teamColor(slot.team), CircleShape)
                .alpha(if (slot.offBoard) OFF_BOARD_ALPHA else 1f),
            shape = CircleShape,
            framed = false,
        )
        if (slot.offBoard) {
            Icon(
                Icons.Filled.Close,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).size(size * 0.7f),
            )
        }
    }
}

/** The state in a word across the foot of the card: DONE (tick), NOW, NEXT, PASS, OUT; none for later. */
@Composable
private fun StateBand(state: SlotState, compact: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (word, background, content) = when (state) {
        SlotState.NOW -> Triple("NOW", scheme.primary, scheme.onPrimary)
        SlotState.NEXT -> Triple("NEXT", scheme.secondaryContainer, scheme.onSecondaryContainer)
        SlotState.DONE -> Triple("DONE", scheme.surfaceVariant, scheme.onSurface)
        SlotState.PASSED -> Triple("PASS", scheme.surfaceVariant, scheme.onSurfaceVariant)
        SlotState.SKIPPED -> Triple("OUT", scheme.surfaceVariant, scheme.onSurfaceVariant)
        SlotState.LATER -> return
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == SlotState.DONE) {
            Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(if (compact) 8.dp else 10.dp))
            if (!compact) Spacer(Modifier.width(1.dp))
        }
        if (!(compact && state == SlotState.DONE)) {
            Text(
                word,
                style = TextStyle(
                    fontSize = fixedSp(if (compact) 8f else 9f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = fixedSp(0.4f),
                ),
                color = content,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The moment the table is at, in words (ADR-041): "All cards revealed · Wasp acts first", "Turn 2 is
 * over" with the turn's cards on their slots, "End of round 1". A polite live region.
 */
@Composable
fun MomentCard(moment: TableMoment, table: TableUi, modifier: Modifier = Modifier, focus: CardFocusState? = null) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Column(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(moment.title.uppercase(), style = GoaTextStyles.overline, color = scheme.primary)
                Text(moment.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (moment.cards.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                TurnTrackRow(
                    table,
                    TurnTrack(table.view.round, table.view.turn - 1, moment.cards),
                    onHero = null,
                    focus = focus,
                )
            }
        }
    }
}

/** A size in sp that ignores the font scale: text on a card stays inside the card. */
@Composable
private fun fixedSp(value: Float): TextUnit = with(LocalDensity.current) { value.dp.toSp() }

/** A size in sp that grows with the font scale up to 1.3×: names under the cards stay one line. */
@Composable
private fun cappedSp(value: Float): TextUnit {
    val density = LocalDensity.current
    val scale = minOf(density.fontScale, MAX_NAME_SCALE)
    return with(density) { (value * scale).dp.toSp() }
}

private val TRACK_CARD = 48.dp
private val NAME_WIDTH = 70.dp
private val COMPACT_CARD = 32.dp
private val EXTRA_CARD_OFFSET = 6.dp
private const val STACKED_ALPHA = 0.85f
private const val DONE_SCRIM = 0.5f
private const val OFF_BOARD_ALPHA = 0.45f
private const val MAX_NAME_SCALE = 1.3f
