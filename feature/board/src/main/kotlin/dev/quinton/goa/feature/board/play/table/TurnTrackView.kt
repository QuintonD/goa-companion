package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.boxText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * The turn track (ADR-041, docs/DESIGN.md "Play against the computer"): after the reveal the hero
 * strip becomes the turn's order, one 48 dp cell per hero in the order they act, read at arm's length:
 * the hero's portrait (team ring and mark, the action chosen), the initiative as it counts in large
 * figures, the acting hero's name and card, and the state in a word on a band: DONE (with a tick, the cell
 * stepped back), NOW (bronze), NEXT (teal), PASS / OUT, RESOLVING and ANSWERS / DEFENDS while another hero
 * answers the card; never colour alone. A tap opens the hero's card large in the card
 * focus viewer, swiping through the turn's cards; a long press (or TalkBack's action) opens the hero.
 * The moment a turn ends shows the turn's cards themselves ([TurnTrackRow]'s `asCards`).
 */

/**
 * The turn's heroes on one row ([TrackStrip]): under the round at a glance on the same plate in portrait
 * ([framed] false, [TableHud]'s `below`), on a plate of its own otherwise; the cells share the width ([stretch]) and
 * scroll only when a cell would get too narrow; sideways beside the round at a glance, cells of fixed widths.
 * [asCards]: the played cards themselves ([cardWidth] wide, names under them, [compact] smaller), as the turn-over
 * moment shows them. [onHero] opens a hero's game state (long press, TalkBack's action); null = no such action.
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
    stretch: Boolean = true,
    framed: Boolean = true,
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
        val responder = responderId(table)
        TrackStrip(table, track.slots, stretch, framed, modifier) { slot, cellModifier, wide, named, fit ->
            val role = cellRole(slot, responder)
            TrackCell(
                table,
                slot,
                role,
                cellWord(role, slot, table),
                wide = wide,
                named = named,
                cardFit = fit,
                onOpen = open(slot),
                onHero = onHero?.let { o -> { o(slot.heroId) } },
                modifier = cellModifier,
            )
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
 * A hero's part in the turn as its cell on the strip shows it: the state, or, while another hero answers the card
 * being resolved ([responderId]), RESOLVING on that card's hero and ANSWERS (DEFENDS) on the one asked.
 */
private enum class CellRole { ACTS, RESOLVING, ANSWERS, NEXT, DONE, LATER, PASSED, SKIPPED }

private fun cellRole(slot: TrackSlot, responder: String?): CellRole = when {
    slot.heroId == responder -> CellRole.ANSWERS
    slot.state == SlotState.NOW -> if (responder != null) CellRole.RESOLVING else CellRole.ACTS
    slot.state == SlotState.NEXT -> CellRole.NEXT
    slot.state == SlotState.DONE -> CellRole.DONE
    slot.state == SlotState.PASSED -> CellRole.PASSED
    slot.state == SlotState.SKIPPED -> CellRole.SKIPPED
    else -> CellRole.LATER
}

/** The word on a cell's band for [role]. */
private fun cellWord(role: CellRole, slot: TrackSlot, table: TableUi): String = when (role) {
    CellRole.RESOLVING -> "RESOLVING"
    CellRole.ANSWERS -> responderBand(table)
    else -> slotBand(slot.state).orEmpty()
}

/**
 * The strip the cells lie on, one row of [CELL_MIN] height: [stretch]ed, the cells share the width (the acting hero's
 * [WIDE_SHARE] times the others', for its name and card); they scroll, at fixed widths, when a cell would get narrower
 * than [MIN_CELL] or the strip isn't stretched, scrolled so the acting hero is in view. [framed]: on a plate of its own;
 * else it lies on the plate it is put on (the score bar's, [TableHud]'s `below`).
 */
@Composable
private fun TrackStrip(
    table: TableUi,
    slots: List<TrackSlot>,
    stretch: Boolean,
    framed: Boolean,
    modifier: Modifier,
    cell: @Composable (slot: TrackSlot, modifier: Modifier, wide: Boolean, named: Boolean, fit: CardNameFit) -> Unit,
) {
    val wideIndex = slots.indexOfFirst { it.state == SlotState.NOW }
    val wideCard = slots.getOrNull(wideIndex)?.let { playedName(table, it) }.orEmpty()
    val scrolling: @Composable () -> Unit = {
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        // The acting hero in view, one resolved hero before it.
        LaunchedEffect(wideIndex, slots.size) {
            if (wideIndex > 1) {
                scroll.scrollTo(with(density) { ((SIDE_CELL + CELL_GAP) * (wideIndex - 1)).roundToPx() })
            }
        }
        val fit = cardNameFit(wideCard, WIDE_CELL)
        Row(
            Modifier.horizontalScroll(scroll).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            slots.forEachIndexed { i, slot ->
                val wide = i == wideIndex
                cell(slot, Modifier.width(if (wide) WIDE_CELL else SIDE_CELL), wide, false, fit)
            }
        }
    }
    val body: @Composable () -> Unit = {
        if (!stretch) {
            // Measured for its width beside the round at a glance (TableTopRow): no BoxWithConstraints here.
            Box(Modifier.padding(STRIP_PAD)) { scrolling() }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(STRIP_PAD)) {
                val n = slots.size.coerceAtLeast(1)
                val shares = n - 1 + if (wideIndex >= 0) WIDE_SHARE else 1f
                val unit = (maxWidth - CELL_GAP * (n - 1)) / shares
                if (unit >= MIN_CELL) {
                    val fit = cardNameFit(wideCard, unit * WIDE_SHARE)
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                    ) {
                        slots.forEachIndexed { i, slot ->
                            val wide = i == wideIndex
                            cell(slot, Modifier.weight(if (wide) WIDE_SHARE else 1f), wide, unit >= NAMED_CELL, fit)
                        }
                    }
                } else if (wideIndex >= 0 && n > 1 && tightWide(maxWidth, n) >= TIGHT_WIDE_MIN) {
                    // A narrow panel (sideways): the others at their least, the acting hero the rest.
                    val fit = cardNameFit(wideCard, tightWide(maxWidth, n))
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                    ) {
                        slots.forEachIndexed { i, slot ->
                            val wide = i == wideIndex
                            cell(slot, if (wide) Modifier.weight(1f) else Modifier.width(TIGHT_CELL), wide, false, fit)
                        }
                    }
                } else {
                    scrolling()
                }
            }
        }
    }
    if (framed) {
        PlateSurface(
            shape = RoundedCornerShape(8.dp),
            modifier = if (stretch) modifier.fillMaxWidth() else modifier,
            content = body,
        )
    } else {
        Box(if (stretch) modifier.fillMaxWidth() else modifier) { body() }
    }
}

/** The acting hero's cell when the others take [TIGHT_CELL] each of [width]. */
private fun tightWide(width: Dp, n: Int): Dp = width - CELL_GAP * (n - 1) - TIGHT_CELL * (n - 1)

/** The card a hero played, by name, as the track shows it: "Facedown" for a card resolved facedown. */
private fun playedName(table: TableUi, slot: TrackSlot): String? {
    val first = slot.cards.firstOrNull() ?: return null
    return first.cardId?.takeIf { !first.facedown }?.let(table::card)?.name ?: if (first.facedown) "Facedown" else null
}

/** The acting hero's card name on its cell: its size, and on how many lines (two only when one would cut it). */
@Immutable
private data class CardNameFit(val size: TextUnit, val lines: Int)

/**
 * The size the card name takes on a wide cell [cell] wide: 13 sp (with large text up to 1.3×) on one line, a step
 * smaller as far as [ONE_LINE_MIN_SHARE] for one line; else two lines, as large as its longest word allows (down to
 * [CARD_NAME_MIN_SHARE]); never cut.
 */
@Composable
private fun cardNameFit(name: String, cell: Dp): CardNameFit {
    val measurer = rememberTextMeasurer()
    val base = cappedSp(CARD_NAME_SP)
    val style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Medium)
    val px = with(LocalDensity.current) { (cell - CELL_TEXT_INSET).roundToPx() }.coerceAtLeast(1)
    return remember(name, px, base, style) {
        fun width(size: Float) = measurer.measure(name, style.copy(fontSize = size.sp), maxLines = 1).size.width
        var size = base.value
        while (size > base.value * ONE_LINE_MIN_SHARE && width(size) > px) size -= SIZE_STEP
        if (width(size) <= px) return@remember CardNameFit(size.sp, 1)
        val words = name.split(WORD_BREAK).map { it.trimEnd() }.filter { it.isNotEmpty() }
        fun widest(at: Float) = words.maxOf {
            measurer.measure(it, style.copy(fontSize = at.sp), maxLines = 1).size.width
        }
        size = base.value
        while (size > base.value * CARD_NAME_MIN_SHARE && widest(size) > px) size -= SIZE_STEP
        CardNameFit(size.sp, 2)
    }
}

/** A name that may break after its hyphens as well as at its spaces. */
private fun breakable(name: String) = name.replace("-", "-​")

/**
 * One hero on the strip, a 48 dp cell read at arm's length: the portrait in its team ring (the mark at its foot, the
 * action chosen at its head, crossed out while off the board) and the initiative as it counts in large figures ("=11"
 * while tied); the acting hero's [wide] cell adds its name and the card it plays, [named] cells (few heroes, room to
 * spare) the name. Across the foot the state in a word on a band: NOW (bronze), NEXT (teal), DONE with a tick and the
 * cell stepped back, PASS, OUT; while another hero answers the card, RESOLVING (steel, bronze words) and ANSWERS or
 * DEFENDS (bronze). Never colour alone. A tap opens the card large; a long press (or TalkBack's action) the hero.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCell(
    table: TableUi,
    slot: TrackSlot,
    role: CellRole,
    word: String,
    wide: Boolean,
    named: Boolean,
    cardFit: CardNameFit,
    onOpen: () -> Unit,
    onHero: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val info = table.hero(slot.heroId)
    val first = slot.cards.firstOrNull()
    val firstCard = first?.cardId?.takeIf { !first.facedown }?.let(table::card)
    val stepped = role == CellRole.DONE || role == CellRole.SKIPPED || role == CellRole.PASSED
    val edge: BorderStroke? = when (role) {
        CellRole.ACTS, CellRole.ANSWERS -> BorderStroke(2.dp, NowBand)
        CellRole.RESOLVING -> BorderStroke(1.dp, NowBand)
        CellRole.NEXT -> BorderStroke(2.dp, NextBand)
        else -> null
    }
    val spoken = spokenSlot(table, slot).let { said ->
        when (role) {
            CellRole.RESOLVING -> said.replace("acting now", "resolving its card")
            CellRole.ANSWERS -> "$said, ${word.lowercase()} now"
            else -> said
        }
    }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = CELL_MIN)
            .clip(shape)
            .background(PlateWell)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onHero)
            .clearAndSetSemantics {
                contentDescription = spoken
                this.role = Role.Button
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 2.dp)
                .alpha(if (stepped) STEPPED_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide || named) Arrangement.Start else Arrangement.Center,
        ) {
            ChipPortrait(table, slot, firstCard, CELL_PORTRAIT)
            Spacer(Modifier.width(5.dp))
            if (!wide && !named) {
                Initiative(slot)
            } else {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initiative(slot)
                        Spacer(Modifier.width(5.dp))
                        HeroName(info.name, Modifier.weight(1f))
                    }
                    if (wide) {
                        playedName(table, slot)?.let { played ->
                            Text(
                                breakable(played),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = cardFit.size,
                                    lineHeight = cardFit.size * CARD_LINE,
                                    letterSpacing = 0.sp,
                                ),
                                fontWeight = FontWeight.Medium,
                                color = LocalContentColor.current.copy(alpha = CARD_NAME_ALPHA),
                                maxLines = cardFit.lines,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        CellBand(role, word)
    }
}

/** The hero's name whole on its cell: a step smaller rather than cut ("Tigerclaw" at large text). */
@Composable
private fun HeroName(name: String, modifier: Modifier) {
    val nameSize = cappedSp(NAME_SP)
    BasicText(
        name,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = nameSize,
            lineHeight = nameSize * CARD_LINE,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
            color = LocalContentColor.current,
        ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = nameSize * NAME_MIN_SHARE, maxFontSize = nameSize),
        modifier = modifier,
    )
}

/** The initiative as it counts, in large figures: "=11" while tied with another hero still to act, "–" without one. */
@Composable
private fun Initiative(slot: TrackSlot) {
    Text(
        slot.initiative?.let { if (slot.tied) "=$it" else "$it" } ?: "–",
        style = GoaTextStyles.numeric.copy(
            fontSize = cappedSp(INITIATIVE_SP),
            lineHeight = cappedSp(INITIATIVE_SP + 2f),
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.run { if (slot.tied) secondary else onSurface },
        maxLines = 1,
        softWrap = false,
    )
}

/** The band across a cell's foot: the state in a word, DONE with a tick; an empty band for a hero acting later. */
@Composable
private fun CellBand(role: CellRole, word: String) {
    val (background, content) = when (role) {
        CellRole.ACTS, CellRole.ANSWERS -> NowBand to BandInk
        CellRole.NEXT -> NextBand to BandInk
        CellRole.RESOLVING -> SteelBand to NowBand
        else -> SteelBand to SteelInk
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (role == CellRole.DONE) {
            Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text(
            word,
            style = TextStyle(
                fontSize = fixedSp(BAND_SP),
                lineHeight = fixedSp(BAND_SP + 2f),
                fontWeight = FontWeight.Bold,
                letterSpacing = fixedSp(0.5f),
            ),
            color = content,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
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
                compact = size < COMPACT_PORTRAIT_BELOW,
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
private fun StateBand(
    state: SlotState,
    compact: Boolean,
    modifier: Modifier,
    roomy: Boolean = false,
    chip: Boolean = false,
) {
    // A chip keeps its height for a hero still to act: an empty band.
    val word = slotBand(state) ?: if (roomy || compact || chip) "" else return
    val (background, content) = bandColors(state == SlotState.NOW, state == SlotState.NEXT)
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = if (chip) 2.dp else 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == SlotState.DONE) {
            Icon(
                Icons.Filled.Check,
                null,
                tint = content,
                modifier = Modifier.size(
                    if (chip) {
                        14.dp
                    } else if (roomy) {
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
        if (chip || !(compact && state == SlotState.DONE)) {
            Text(
                word,
                style = TextStyle(
                    fontSize = fixedSp(
                        if (chip) {
                            if (compact) 12f else 13f
                        } else if (roomy) {
                            12f
                        } else if (compact) {
                            9f
                        } else {
                            10f
                        },
                    ),
                    fontWeight = if (chip) FontWeight.Bold else FontWeight.SemiBold,
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

/**
 * The moment as one row over the question it brings (the end of a round over its Minion Battle): its title with a
 * flag, folded; a tap opens its text (and the turn's cards) under it. The question and its options stay in view and
 * the moment is never cut at the panel's edge.
 */
@Composable
fun MomentRow(moment: TableMoment, table: TableUi, modifier: Modifier = Modifier, focus: CardFocusState? = null) {
    var open by rememberSaveable(moment.title) { mutableStateOf(false) }
    val toggle = { open = !open }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PlateWell),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = toggle)
                .clearAndSetSemantics {
                    contentDescription = if (open) "${moment.title}. ${moment.text}" else moment.title
                    stateDescription = if (open) "Expanded" else "Collapsed"
                    role = Role.Button
                    onClick(label = if (open) "Collapse" else "Expand") {
                        toggle()
                        true
                    }
                }
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Flag, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                moment.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(moment.text, style = MaterialTheme.typography.bodyMedium)
                if (moment.cards.isNotEmpty()) {
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
    else -> SteelBand to SteelInk
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
private val CHIP_BADGE = 14.dp
private val COMPACT_PORTRAIT_BELOW = 34.dp
private const val STEPPED_ALPHA = 0.72f
private const val CARD_NAME_SP = 13f
private const val NAME_SP = 13f
private const val INITIATIVE_SP = 17f
private const val BAND_SP = 11f

/** The card name's line height, and how far its size may step down to stay on one line. */
private const val CARD_LINE = 1.23f
private const val CARD_NAME_MIN_SHARE = 0.75f
private const val SIZE_STEP = 0.5f
private const val ONE_LINE_MIN_SHARE = 0.85f
private val WORD_BREAK = Regex("(?<=[ -])")

/** A hero's name may step down to this share of its size to stay whole in a narrow cell. */
private const val NAME_MIN_SHARE = 0.78f
private const val CARD_NAME_ALPHA = 0.82f
private val STRIP_PAD = 4.dp

/** The steel band of a resolved or later hero, and its words. */
private val SteelBand = Color(0xFF2A343B)
private val SteelInk = Color(0xFFE8E2D6)

/** A cell: at least a touch target's height, its portrait, the gap between cells. */
private val CELL_MIN = 48.dp
private val CELL_PORTRAIT = 26.dp
private val CELL_GAP = 4.dp

/** What a wide cell's words leave of its width: the padding, the portrait and the gap after it. */
private val CELL_TEXT_INSET = 39.dp

/** The acting hero's cell is this many times as wide as the others: its name and card read whole. */
private const val WIDE_SHARE = 2.2f

/** A cell narrower than this would crowd the portrait and the figures: the strip scrolls instead. */
private val MIN_CELL = 62.dp

/** From this width a cell has room for the hero's name too (two or three heroes on a phone). */
private val NAMED_CELL = 100.dp

/** Scrolling, or sideways beside the round at a glance: cells of fixed widths. */
private val SIDE_CELL = 64.dp

/** A narrow strip (the side panel): the others' cells at their least, if the acting hero keeps this much. */
private val TIGHT_CELL = 60.dp
private val TIGHT_WIDE_MIN = 90.dp
private val WIDE_CELL = 150.dp
