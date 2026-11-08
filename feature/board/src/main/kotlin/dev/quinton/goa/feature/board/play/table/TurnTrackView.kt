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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardTextBox
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.DoneLabel
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.mutedOutline
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.HeroView
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
 * scroll only when a cell would get too narrow; sideways beside the round at a glance, cells of fixed widths;
 * [vertical], one under another (sideways, a column at the map's edge).
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
    vertical: Boolean = false,
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
        TrackStrip(table, track.slots, stretch, framed, vertical, modifier) { slot, cellModifier, fit ->
            val role = cellRole(slot, responder)
            TrackCell(
                table,
                slot,
                role,
                cellWord(role, slot, table),
                side = vertical,
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
 * The strip the cells lie on. [stretch]ed, one row of equal cells sharing the width, each stacked: portrait and
 * initiative, the name, the card played ([TrackCell]); they scroll at a fixed width when a cell would get narrower
 * than [MIN_CELL], or when the strip isn't stretched (sideways beside the round at a glance). [vertical]: one cell
 * under another, each portrait beside its words (the column at the map's edge sideways), scrolling when they don't
 * all fit.
 * [framed]: on a plate of its own; else it lies on the plate it is put on (the score bar's, [TableHud]'s `below`).
 */
@Composable
private fun TrackStrip(
    table: TableUi,
    slots: List<TrackSlot>,
    stretch: Boolean,
    framed: Boolean,
    vertical: Boolean,
    modifier: Modifier,
    cell: @Composable (slot: TrackSlot, modifier: Modifier, fit: CardNameFit) -> Unit,
) {
    val names = slots.map { playedName(table, it).orEmpty() }
    val now = slots.indexOfFirst { it.state == SlotState.NOW }
    val scrolling: @Composable () -> Unit = {
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        // The acting hero in view, one resolved hero before it.
        LaunchedEffect(now, slots.size) {
            if (now > 1) scroll.scrollTo(with(density) { ((SCROLL_CELL + CELL_GAP) * (now - 1)).roundToPx() })
        }
        val fit = cardNameFit(names, SCROLL_CELL - STACK_INSET, lines = 2)
        Row(
            Modifier.horizontalEdgeFade(scroll).horizontalScroll(scroll).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            slots.forEach { slot -> cell(slot, Modifier.width(SCROLL_CELL), fit) }
        }
    }
    val body: @Composable () -> Unit = {
        when {
            vertical -> {
                val fit = cardNameFit(names, SIDE_COLUMN - STRIP_PAD * 2 - SIDE_INSET, lines = 2)
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(STRIP_PAD),
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    slots.forEach { slot -> cell(slot, Modifier.fillMaxWidth(), fit) }
                }
            }

            // Measured for its width beside the round at a glance (TableTopRow): no BoxWithConstraints here.
            !stretch -> Box(Modifier.padding(STRIP_PAD)) { scrolling() }

            else -> BoxWithConstraints(Modifier.fillMaxWidth().padding(STRIP_PAD)) {
                val n = slots.size.coerceAtLeast(1)
                val each = (maxWidth - CELL_GAP * (n - 1)) / n
                if (each >= MIN_CELL) {
                    val fit = cardNameFit(names, each - STACK_INSET, lines = 2)
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                    ) {
                        slots.forEach { slot -> cell(slot, Modifier.weight(1f), fit) }
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
            modifier = if (stretch && !vertical) modifier.fillMaxWidth() else modifier,
            content = body,
        )
    } else {
        Box(if (stretch && !vertical) modifier.fillMaxWidth() else modifier) { body() }
    }
}

/** The card a hero played, by name, as the track shows it: "Facedown" for a card resolved facedown. */
private fun playedName(table: TableUi, slot: TrackSlot): String? {
    val first = slot.cards.firstOrNull() ?: return null
    return first.cardId?.takeIf { !first.facedown }?.let(table::card)?.name ?: if (first.facedown) "Facedown" else null
}

/** The card names on a strip: one size for every cell, and how many lines they may take. */
@Immutable
private data class CardNameFit(val size: TextUnit, val lines: Int)

/**
 * The size of the card names on a strip, one for every cell, [width] the room for the words: 12 sp (with large text
 * up to 1.3×), a step smaller only when that is what it takes for every name to sit on [lines] lines without a word
 * broken ("Light-Fingered" breaks after its hyphen, never inside "Fingered"), down to [CARD_NAME_MIN_SHARE]; past
 * that, as many lines as a name needs at the smallest size. Never cut: an ellipsized card name lost three screens.
 */
@Composable
private fun cardNameFit(names: List<String>, width: Dp, lines: Int): CardNameFit {
    val measurer = rememberTextMeasurer()
    val base = cappedSp(CARD_NAME_SP)
    val style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Medium)
    val px = with(LocalDensity.current) { width.roundToPx() }.coerceAtLeast(1)
    return remember(names, px, base, style, lines) {
        fun fits(size: Float): Boolean {
            val at = style.copy(fontSize = size.sp)
            return names.all { name ->
                val words = name.split(WORD_BREAK).map { it.trimEnd() }.filter { it.isNotEmpty() }
                words.all { measurer.measure(it, at, maxLines = 1).size.width <= px } &&
                    measurer.measure(breakable(name), at, constraints = Constraints(maxWidth = px)).lineCount <= lines
            }
        }
        var size = base.value
        while (size > base.value * CARD_NAME_MIN_SHARE && !fits(size)) size -= SIZE_STEP
        CardNameFit(size.sp, if (fits(size)) lines else Int.MAX_VALUE)
    }
}

/** A name that may break after its hyphens as well as at its spaces. */
private fun breakable(name: String) = name.replace("-", "-​")

/**
 * One hero on the strip, read at arm's length. Stacked (a row of cells): the portrait in its team ring (the mark at
 * its foot, the action chosen at its head, crossed out while off the board) beside the initiative as it counts ("=11"
 * while tied), the hero's name under them and the card played under that, on two lines rather than cut; [side] (a
 * column of cells): the portrait beside those words. Across the foot a thin band with the state in a word: NOW
 * (bronze), NEXT (teal), DONE with a tick and the cell stepped back, PASS, OUT; while another hero answers the card,
 * RESOLVING (steel, bronze words) and ANSWERS or DEFENDS (bronze). Never colour alone. A tap opens the card large; a
 * long press (or TalkBack's action) the hero.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCell(
    table: TableUi,
    slot: TrackSlot,
    role: CellRole,
    word: String,
    side: Boolean,
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
        CellRole.RESOLVING -> BorderStroke(1.5.dp, NowBand)
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
    val played = playedName(table, slot)
    val cardName: @Composable (TextAlign) -> Unit = { align ->
        if (played != null) {
            Text(
                breakable(played),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = cardFit.size,
                    lineHeight = cardFit.size * CARD_LINE,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.Medium,
                // Secondary words at full strength; a done hero's in the muted token, never faded (round 5).
                color = if (stepped) GoaTheme.colors.textMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = cardFit.lines,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            // A done hero steps back by losing its ground, keeping a thin muted edge: its words stay legible.
            .then(if (stepped) Modifier.mutedOutline(shape) else Modifier.background(PlateWell))
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
        val content = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 1.dp)
        if (side) {
            Row(content, verticalAlignment = Alignment.CenterVertically) {
                ChipPortrait(table, slot, firstCard, SIDE_PORTRAIT)
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initiative(slot.initiative, slot.tied, muted = stepped)
                        Spacer(Modifier.width(5.dp))
                        HeroName(info.name, Modifier.weight(1f), TextAlign.Start, muted = stepped)
                    }
                    cardName(TextAlign.Start)
                }
            }
        } else {
            Column(content, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChipPortrait(table, slot, firstCard, CELL_PORTRAIT)
                    Spacer(Modifier.width(5.dp))
                    Initiative(slot.initiative, slot.tied, muted = stepped)
                }
                HeroName(info.name, Modifier.fillMaxWidth(), TextAlign.Center, muted = stepped)
                cardName(TextAlign.Center)
            }
        }
        if (!side) Spacer(Modifier.weight(1f))
        CellBand(role, word)
    }
}

/** The hero's name whole on its cell: a step smaller rather than cut ("Tigerclaw" at large text). */
@Composable
private fun HeroName(name: String, modifier: Modifier, align: TextAlign, muted: Boolean = false) {
    val nameSize = cappedSp(NAME_SP)
    BasicText(
        name,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = nameSize,
            lineHeight = nameSize * CARD_LINE,
            letterSpacing = 0.sp,
            fontWeight = if (muted) FontWeight.SemiBold else FontWeight.Bold,
            color = if (muted) GoaTheme.colors.textMuted else LocalContentColor.current,
            textAlign = align,
        ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = nameSize * NAME_MIN_SHARE, maxFontSize = nameSize),
        modifier = modifier,
    )
}

/** The initiative as it counts, in large figures: "=11" while tied with another hero still to act, else "–". */
@Composable
private fun Initiative(initiative: Int?, tied: Boolean, muted: Boolean = false) {
    Text(
        initiative?.let { if (tied) "=$it" else "$it" } ?: "–",
        style = GoaTextStyles.numeric.copy(
            fontSize = cappedSp(INITIATIVE_SP),
            lineHeight = cappedSp(INITIATIVE_SP + 2f),
            fontWeight = FontWeight.Bold,
        ),
        color = when {
            tied -> MaterialTheme.colorScheme.secondary
            muted -> GoaTheme.colors.textMuted
            else -> MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The band across a cell's foot: the state in a word, DONE with a tick; an empty band for a hero acting later. A past
 * state (DONE, PASS, OUT) has no band at all: the tick and the word in the muted token over a hairline.
 */
@Composable
private fun CellBand(role: CellRole, word: String, tick: Boolean = true) {
    val past = tick && (role == CellRole.DONE || role == CellRole.PASSED || role == CellRole.SKIPPED)
    if (past) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GoaTheme.colors.outlineMuted))
            DoneLabel(
                word,
                style = TextStyle(
                    fontSize = fixedSp(BAND_SP),
                    lineHeight = fixedSp(BAND_SP + 2f),
                    letterSpacing = fixedSp(0.5f),
                ),
                tick = role == CellRole.DONE,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        return
    }
    val dark = GoaTheme.isDark
    val (background, content) = when (role) {
        CellRole.ACTS, CellRole.ANSWERS -> NowBand to BandInk

        CellRole.NEXT -> NextBand to BandInk

        CellRole.RESOLVING -> if (dark) SteelBand to NowBand else PaperBand to BronzeInk

        // A hero still to act: no band, the cell keeps its height (an empty band read as a disabled box).
        CellRole.LATER -> Color.Transparent to Color.Transparent

        else -> if (dark) SteelBand to SteelInk else PaperBand to PaperInk
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (role == CellRole.DONE && tick) {
            Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(11.dp))
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
        val badge = if (size < SMALL_PORTRAIT) SMALL_BADGE else CHIP_BADGE
        TeamBadge(slot.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(badge), size = badge)
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

/**
 * Every hero on the turn strip's own cells when no turn is resolving (before the reveal, the end of a round, a puzzle
 * solved; UI hill climb, round 4): the same cells as [TurnTrackRow]'s, in the same places (portrait and initiative,
 * the name, then a second line, a band), so the strip never changes form. Only the second line differs: the level,
 * the coins and the cards in hand instead of the card played. The band says NOW on the acting hero and CARD DOWN on
 * one whose card is down before the reveal (p.7). Equal cells sharing the width; they scroll only past [MIN_CELL],
 * never cut. A tap opens the hero's state (TalkBack: "Open Arien's state"). [framed]: on a plate of its own.
 */
@Composable
internal fun HeroCells(
    table: TableUi,
    onHero: (String) -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val order = table.initiativeOrder
    val heroes = if (order.isNotEmpty()) {
        order.mapNotNull(table::heroView) + table.view.heroes.filter { it.heroId !in order }
    } else {
        table.view.heroes.sortedBy { it.team.ordinal }
    }
    val place: (HeroView) -> Int? = { hero -> order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1) }
    val body: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(STRIP_PAD)) {
            val n = heroes.size.coerceAtLeast(1)
            val each = (maxWidth - CELL_GAP * (n - 1)) / n
            if (each >= MIN_CELL) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    heroes.forEach { hero ->
                        HeroCell(table, hero, place(hero), { onHero(hero.heroId) }, Modifier.weight(1f))
                    }
                }
            } else {
                val scroll = rememberScrollState()
                Row(
                    Modifier.horizontalEdgeFade(scroll).horizontalScroll(scroll).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    heroes.forEach { hero ->
                        HeroCell(table, hero, place(hero), { onHero(hero.heroId) }, Modifier.width(SCROLL_CELL))
                    }
                }
            }
        }
    }
    if (framed) {
        PlateSurface(shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxWidth(), content = body)
    } else {
        Box(modifier.fillMaxWidth()) { body() }
    }
}

/** One hero's cell on [HeroCells]: a [TrackCell] whose second line is the level, coins and hand. */
@Composable
private fun HeroCell(table: TableUi, hero: HeroView, place: Int?, onClick: () -> Unit, modifier: Modifier) {
    val info = table.hero(hero.heroId)
    val acting = table.view.acting == hero.heroId
    val down = cardDown(table, hero)
    val played = table.card(hero.played?.cardId)?.takeIf { hero.played?.facedown != true }
    val initiative = hero.initiative ?: played?.initiative
    val spoken = spokenChip(table, hero, place)
    val shape = RoundedCornerShape(6.dp)
    val open = showGameState(info.name)
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(PlateWell)
            .then(if (acting) Modifier.border(BorderStroke(2.dp, NowBand), shape) else Modifier)
            .clickable(onClickLabel = open, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = open) {
                    onClick()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroCellPortrait(table, hero)
                if (initiative != null) {
                    Spacer(Modifier.width(5.dp))
                    Initiative(initiative, tied = false)
                }
            }
            HeroName(info.name, Modifier.fillMaxWidth().padding(top = 1.dp), TextAlign.Center)
            HeroCellNumbers(hero)
        }
        Spacer(Modifier.weight(1f))
        val role = when {
            acting -> CellRole.ACTS
            down -> CellRole.DONE
            else -> CellRole.LATER
        }
        val word = when (role) {
            CellRole.ACTS -> "NOW"
            CellRole.DONE -> "CARD DOWN"
            else -> ""
        }
        CellBand(role, word, tick = false)
    }
}

/** The second line of a hero's cell: level, coins and the cards in hand, in the card name's size. */
@Composable
private fun HeroCellNumbers(hero: HeroView) {
    val size = cappedSp(CARD_NAME_SP)
    val number = GoaTextStyles.numeric.copy(
        fontSize = size,
        lineHeight = size * CARD_LINE,
        fontWeight = FontWeight.Medium,
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Lv${hero.level}", style = number, color = color, maxLines = 1, softWrap = false)
        CoinIcon(size = scaledIconSize(12.dp, MAX_NAME_SCALE))
        Text("${hero.coins}", style = number, color = color, maxLines = 1, softWrap = false)
        TableCard(null, width = 9.dp, backOf = hero.heroId)
        Text("${hero.handCount}", style = number, color = color, maxLines = 1, softWrap = false)
    }
}

/** A hero cell's portrait: the team ring, the mark at its foot, the rune active, crossed out off the board. */
@Composable
private fun HeroCellPortrait(table: TableUi, hero: HeroView) {
    val info = table.hero(hero.heroId)
    Box {
        HeroPortrait(
            hero.heroId,
            info.portrait,
            null,
            Modifier
                .size(CELL_PORTRAIT)
                .border(2.dp, teamColor(hero.team), CircleShape)
                .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
            shape = CircleShape,
            framed = false,
        )
        if (hero.at == null) {
            Icon(
                Icons.Filled.Close,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).size(CELL_PORTRAIT * 0.7f),
            )
        }
        TeamBadge(hero.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(SMALL_BADGE), size = SMALL_BADGE)
        ActiveMarkerBadge(table, hero, Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-4).dp))
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
private val SMALL_BADGE = 11.dp
private val SMALL_PORTRAIT = 30.dp
private val COMPACT_PORTRAIT_BELOW = 34.dp
private const val CARD_NAME_SP = 12f
private const val NAME_SP = 13f
private const val INITIATIVE_SP = 18f
private const val BAND_SP = 12f

/** The card name's line height, and how far its size may step down to stay on one line. */
private const val CARD_LINE = 1.23f
private const val CARD_NAME_MIN_SHARE = 0.75f
private const val SIZE_STEP = 0.5f
private val WORD_BREAK = Regex("(?<=[ -])")

/** A hero's name may step down to this share of its size to stay whole in a narrow cell. */
private const val NAME_MIN_SHARE = 0.78f
private val STRIP_PAD = 4.dp

/** The steel band of a resolved or later hero, and its words. */
private val SteelBand = Color(0xFF2A343B)
private val SteelInk = Color(0xFFE8E2D6)

/** The same bands on the light theme's paper (ADR-068): a light steel with ink, bronze words a shade deeper. */
private val PaperBand = Color(0xFFD5DCE0)
private val PaperInk = Color(0xFF22303A)
private val BronzeInk = Color(0xFF7A5A1E)

/** A cell's portrait (a column's a little larger), and the gap between cells. */
private val CELL_PORTRAIT = 22.dp
private val SIDE_PORTRAIT = 26.dp
private val CELL_GAP = 4.dp

/** What a stacked cell's words leave of its width: the padding either side. */
private val STACK_INSET = 8.dp

/** What a side cell's words leave of its width: the padding, the portrait and the gap after it. */
private val SIDE_INSET = 39.dp

/** A stacked cell narrower than this would crowd the name and card: the strip scrolls instead. */
private val MIN_CELL = 76.dp

/** Scrolling, or sideways beside the round at a glance: cells of one width. */
private val SCROLL_CELL = 88.dp

/** The column of cells at the map's edge sideways ([GameTable]). */
internal val SIDE_COLUMN = 148.dp
