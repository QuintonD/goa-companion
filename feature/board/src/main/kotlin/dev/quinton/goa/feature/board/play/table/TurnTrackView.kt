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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.boxText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * The turn track (ADR-041, docs/DESIGN.md "Play against the computer"): after the reveal the hero
 * strip becomes the turn's order, one chip per hero in the order they act, read at arm's length:
 * the hero's portrait (team ring and mark, the action chosen), the initiative as it counts in large
 * figures, the name, and the state in a word on a band: DONE (with a tick, the chip stepped back), NOW
 * (bronze), NEXT (teal), PASS / OUT; never colour alone. A tap opens the hero's card large in the card
 * focus viewer, swiping through the turn's cards; a long press (or TalkBack's action) opens the hero.
 * The moment a turn ends shows the turn's cards themselves ([TurnTrackRow]'s `asCards`).
 */

/**
 * The turn's heroes in a row, sharing the width and scrolling when they don't fit. [compact] (a phone
 * held sideways with the panel shown, or the panel hidden): smaller chips. [asCards]: the played cards
 * themselves ([cardWidth] wide, names under them), as the turn-over moment shows them. [onHero] opens a
 * hero's game state (long press, TalkBack's action); null = no such action (a recap in the panel).
 */
@Composable
fun TurnTrackRow(
    table: TableUi,
    track: TurnTrack,
    onHero: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    focus: CardFocusState? = null,
    compact: Boolean = false,
    cardWidth: Dp = if (compact) COMPACT_CARD else TRACK_CARD,
    asCards: Boolean = false,
) {
    val viewable = trackFocusCards(table, track)
    val open: (TrackSlot) -> () -> Unit = { slot ->
        {
            val first = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.cardId?.let(table::card)
            val index = viewable.indexOfFirst { it.key == trackKey(slot.heroId, first?.id) }
            if (focus != null && index >= 0) {
                focus.open(viewable, index, title = "Round ${track.round} · Turn ${track.turn}")
            } else {
                onHero?.invoke(slot.heroId)
            }
        }
    }
    if (!asCards) {
        val chips: @Composable () -> Unit = {
            track.slots.forEach { slot ->
                TrackChip(table, slot, compact, open(slot), onHero?.let { o -> { o(slot.heroId) } })
            }
        }
        // Sideways beside the round at a glance, a plain row: it is measured for whether it fits there.
        if (compact) {
            Row(
                modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) { chips() }
        } else {
            SharedWidthRow(modifier, gap = 6.dp, content = chips)
        }
        return
    }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        track.slots.forEach { slot ->
            TrackSlotView(
                table = table,
                slot = slot,
                compact = compact,
                cardWidth = cardWidth,
                focus = focus,
                onOpen = open(slot),
                onHero = onHero?.let { o -> { o(slot.heroId) } },
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

/** The game's icon for an action (the primary's by the card's type). */
internal fun actionIcon(action: ActionType, card: Card?): GameIcon = when (action) {
    ActionType.PRIMARY -> card?.primary?.let(GameIcons::forPrimary) ?: GameIcons.skill
    ActionType.MOVEMENT -> GameIcons.movement
    ActionType.FAST_TRAVEL -> GameIcons.fastTravel
    ActionType.ATTACK -> GameIcons.attack
    ActionType.CLEAR -> GameIcons.clear
    ActionType.HOLD -> GameIcons.hold
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackSlotView(
    table: TableUi,
    slot: TrackSlot,
    compact: Boolean,
    cardWidth: Dp,
    focus: CardFocusState?,
    onOpen: () -> Unit,
    onHero: (() -> Unit)?,
) {
    val info = table.hero(slot.heroId)
    val spoken = spokenSlot(table, slot)
    val border = when (slot.state) {
        SlotState.NOW -> BorderStroke(2.dp, NowBand)
        SlotState.NEXT -> BorderStroke(2.dp, NextBand)
        else -> null
    }
    val shape = RoundedCornerShape(6.dp)
    PlateSurface(
        shape = shape,
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
            Modifier.padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SlotCards(table, slot, cardWidth, compact, focus)
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = cardWidth + NAME_EXTRA),
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

/**
 * One hero on the track, read at arm's length: the portrait in its team ring (the mark at its foot, the action chosen
 * at its head, crossed out while off the board), the initiative as it counts in large figures ("=11" while tied), the
 * hero's name, and the state in a word across the foot (NOW bronze, NEXT teal, DONE with a tick and the chip stepped
 * back, PASS, OUT); never colour alone. [compact]: a smaller portrait and figures, the name still under them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackChip(
    table: TableUi,
    slot: TrackSlot,
    compact: Boolean,
    onOpen: () -> Unit,
    onHero: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val info = table.hero(slot.heroId)
    val first = slot.cards.firstOrNull()
    val firstCard = first?.cardId?.takeIf { !first.facedown }?.let(table::card)
    val stepped = slot.state == SlotState.DONE || slot.state == SlotState.SKIPPED || slot.state == SlotState.PASSED
    val border = when (slot.state) {
        SlotState.NOW -> BorderStroke(2.dp, NowBand)
        SlotState.NEXT -> BorderStroke(2.dp, NextBand)
        else -> null
    }
    val shape = RoundedCornerShape(7.dp)
    PlateSurface(
        shape = shape,
        border = border,
        modifier = modifier
            .clip(shape)
            .combinedClickable(onClick = onOpen, onLongClick = onHero)
            .clearAndSetSemantics {
                contentDescription = spokenSlot(table, slot)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier
                    .padding(start = 5.dp, end = 5.dp, top = if (compact) 3.dp else 5.dp, bottom = 2.dp)
                    .alpha(if (stepped) STEPPED_ALPHA else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChipPortrait(table, slot, firstCard, if (compact) CHIP_PORTRAIT_COMPACT else CHIP_PORTRAIT)
                    Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    Text(
                        slot.initiative?.let { if (slot.tied) "=$it" else "$it" } ?: "–",
                        style = GoaTextStyles.numeric.copy(
                            fontSize = cappedSp(if (compact) 16f else 21f),
                            lineHeight = cappedSp(if (compact) 18f else 24f),
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.run { if (slot.tied) secondary else onSurface },
                        maxLines = 1,
                    )
                }
                Text(
                    info.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = cappedSp(if (compact) 10f else 12f),
                        lineHeight = cappedSp(if (compact) 12f else 15f),
                        letterSpacing = 0.sp,
                    ),
                    fontWeight = if (slot.state == SlotState.NOW) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            StateBand(slot.state, compact, Modifier.fillMaxWidth(), roomy = !compact)
        }
    }
}

/** The portrait in the team ring with the mark at its foot and the chosen action's icon at its head. */
@Composable
private fun ChipPortrait(table: TableUi, slot: TrackSlot, card: Card?, size: Dp) {
    val info = table.hero(slot.heroId)
    Box {
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
        TeamBadge(slot.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(CHIP_BADGE), size = CHIP_BADGE)
        slot.action?.let { action ->
            ActionBadge(
                actionIcon(action, card),
                compact = size < CHIP_PORTRAIT,
                Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp),
            )
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
                backOf = slot.heroId,
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
                TableCard(firstCard, origin, width = width, backOf = slot.heroId)
            }
            if (slot.state == SlotState.DONE || slot.state == SlotState.SKIPPED) {
                // Stepped back, as a resolved card moved to the dashboard: dimmed by an overlay, the band says why.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(width * 0.08f))
                        .background(Color.Black.copy(alpha = DONE_SCRIM)),
                )
            }
            slot.initiative?.let { InitiativeBadge(it, slot.tied, compact, Modifier.align(Alignment.TopStart)) }
            slot.action?.let { action ->
                // Under the card's name, which it would hide on the title bar.
                ActionBadge(
                    actionIcon(action, firstCard),
                    compact,
                    Modifier.align(Alignment.TopEnd).offset(y = if (compact) 0.dp else width * ACTION_BADGE_DROP),
                )
            }
            SlotPortrait(table, slot, compact, width, Modifier.align(Alignment.CenterEnd))
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
private fun SlotPortrait(table: TableUi, slot: TrackSlot, compact: Boolean, width: Dp, modifier: Modifier) {
    val info = table.hero(slot.heroId)
    val size = if (compact) 18.dp else width * PORTRAIT_SHARE
    Box(modifier.offset(x = 2.dp, y = (-4).dp)) {
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
private fun StateBand(state: SlotState, compact: Boolean, modifier: Modifier, roomy: Boolean = false) {
    // A chip keeps its height for a hero still to act: an empty band.
    val word = slotBand(state) ?: if (roomy || compact) "" else return
    val (background, content) = bandColors(state == SlotState.NOW, state == SlotState.NEXT)
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == SlotState.DONE) {
            Icon(
                Icons.Filled.Check,
                null,
                tint = content,
                modifier = Modifier.size(
                    if (roomy) {
                        13.dp
                    } else if (compact) {
                        9.dp
                    } else {
                        10.dp
                    },
                ),
            )
            Spacer(Modifier.width(2.dp))
        }
        if (!(compact && state == SlotState.DONE)) {
            Text(
                word,
                style = TextStyle(
                    fontSize = fixedSp(
                        if (roomy) {
                            12f
                        } else if (compact) {
                            9f
                        } else {
                            10f
                        },
                    ),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = fixedSp(0.8f),
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
    Column(modifier.fillMaxWidth()) {
        CardTextBox(
            PlateColor.GOLD,
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            cap = moment.title.uppercase(),
        ) {
            Text(moment.text, style = boxText())
        }
        if (moment.cards.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            TurnTrackRow(
                table,
                TurnTrack(table.view.round, table.view.turn - 1, moment.cards),
                onHero = null,
                focus = focus,
                asCards = true,
            )
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

/** The band across a card's foot on the track and the hero sheet: bronze NOW, teal NEXT, else steel. */
internal fun bandColors(now: Boolean, next: Boolean): Pair<Color, Color> = when {
    now -> NowBand to BandInk
    next -> NextBand to BandInk
    else -> Color(0xFF2A343B) to Color(0xFFE8E2D6)
}

internal val NowBand = Color(0xFFC9A35B)
internal val NextBand = Color(0xFF3FC1B0)
private val BandInk = Color(0xFF0B1622)

private val TRACK_CARD = 48.dp
private val NAME_EXTRA = 8.dp
private const val PORTRAIT_SHARE = 0.44f
private const val ACTION_BADGE_DROP = 0.2f
private val COMPACT_CARD = 32.dp
private val EXTRA_CARD_OFFSET = 6.dp
private const val STACKED_ALPHA = 0.85f
private const val DONE_SCRIM = 0.42f
private const val OFF_BOARD_ALPHA = 0.45f
private const val MAX_NAME_SCALE = 1.3f
private val CHIP_PORTRAIT = 34.dp
private val CHIP_PORTRAIT_COMPACT = 26.dp
private val CHIP_BADGE = 14.dp
private const val STEPPED_ALPHA = 0.55f
