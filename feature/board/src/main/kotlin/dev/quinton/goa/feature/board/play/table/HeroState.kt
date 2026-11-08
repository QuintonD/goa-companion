package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.component.GoaDarkTheme
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.emptySlotOutline
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * A hero's game state on the hero sheet (ADR-044): the real dashboard with this round's cards in its
 * Turn slots, the items (icons for everyone, the tucked cards for the hero's own player), where the hero
 * stands. What it says comes from HeroFacts.kt, built only from the engine's view: it can't show more
 * than the table does.
 */

// ---- Sections ---------------------------------------------------------------------------------

/** What this turn's state means, in lines beside the card's band: the card itself lies on the dashboard. */
@Composable
internal fun ThisTurnSection(table: TableUi, hero: HeroView) {
    val turn = heroTurn(table, hero) ?: return
    val lines = turnLines(table, hero, turn)
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeroSection("This turn · Round ${table.view.round}, turn ${table.view.turn}")
        StateTag(turn.state)
        lines.forEachIndexed { i, line ->
            val counted = line.startsWith("Initiative")
            Text(
                line,
                style = if (counted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (counted) FontWeight.SemiBold else null,
                color = if (i == 0 || counted) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** The state in a word, as the turn track's band (never colour alone). */
@Composable
private fun StateTag(state: HeroTurnState) {
    val (background, content) = bandColors(state == HeroTurnState.NOW, state == HeroTurnState.NEXT)
    Text(
        turnWord(state),
        style = GoaTextStyles.overline,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * The hero's dashboard (rulebook p.16, mock-up 05), redrawn at the phone's own density (ADR-058: the printed frame is
 * too soft at this size): a steel frame with the plate's teal brackets over the hero's art (dimmed: a backdrop, not
 * the content), "Hero the Title" on the cards' parchment title plate, a parchment "TURN n" tab over each of the four
 * slots and the round's cards lying in them, each slot one TalkBack node. Tap a card to read it. Its words scale with
 * the frame, not the font scale (TalkBack and the lines under it say them). Markers on a slot (Snorri's runes) lie on
 * a strip under it.
 */
@Composable
internal fun HeroDashboard(table: TableUi, hero: HeroView, focus: CardFocusState, modifier: Modifier = Modifier) {
    val info = table.hero(hero.heroId)
    val slots = dashboardSlots(table, hero)
    val title = "${info.name} · Round ${table.view.round}"
    val viewable = slots.flatMap { slot ->
        slot.cards.mapNotNull { view ->
            val card = view.cardId?.takeIf { !view.facedown }?.let(table::card) ?: return@mapNotNull null
            FocusCard(card, key = "$title/${card.id}", caption = "Turn ${slot.turn}")
        }
    }
    val shape = RoundedCornerShape(FRAME_CORNER)
    BoxWithConstraints(modifier.clip(shape).background(PlateGround)) {
        val w = maxWidth
        val scale = w.value / FRAME_REFERENCE
        val pad = FRAME_PAD * scale
        val gap = SLOT_GAP * scale
        val column = (w - pad * 2 - gap * 3) / 4
        val cardWidth = column * CARD_SHARE
        val markers = slots.any { it.marker != null }
        DashboardArt(hero.heroId, info.portrait, Modifier.matchParentSize())
        // The painting is the backdrop, not the content: dimmed under a slate veil so the slots read over it.
        Box(Modifier.matchParentSize().background(PlateGround.copy(alpha = ART_VEIL)))
        Column(
            Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val frame = DashboardFrame(size, FRAME_CORNER.toPx(), scale * density)
                    onDrawBehind { frame.draw(this) }
                }
                .padding(start = pad, end = pad, top = pad * 0.8f, bottom = pad * 1.1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val name = listOf(info.name, info.title).filter { it.isNotBlank() }.joinToString(" ")
            val nameSize = dpToSp(NAME_DP * scale)
            TitlePlate(
                name,
                style = GoaTextStyles.plateTitle.copy(fontSize = nameSize, lineHeight = nameSize * 1.2f),
                heading = false,
                maxLines = 1,
                modifier = Modifier.widthIn(max = w * NAME_PLATE_SHARE).clearAndSetSemantics {},
            )
            Spacer(Modifier.height(TITLE_GAP * scale))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                slots.forEach { slot ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TurnTab(slot.turn, scale, Modifier.width(cardWidth))
                        Spacer(Modifier.height(TAB_GAP * scale))
                        SlotOnFrame(hero.heroId, table, slot, cardWidth, scale, focus, title, viewable, Modifier)
                        if (markers) {
                            val size = w * MARKER_SHARE
                            Box(Modifier.padding(top = TAB_GAP * scale).height(w * MARKER_ACTIVE_SHARE)) {
                                slot.marker?.let { marker ->
                                    SlotMarkerToken(
                                        marker,
                                        scale,
                                        Modifier.align(Alignment.Center),
                                        if (marker.active) w * MARKER_ACTIVE_SHARE else size,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A slot's "TURN n" tab: a small parchment plate with a bronze edge, in the cards' Cinzel. */
@Composable
private fun TurnTab(turn: Int, scale: Float, modifier: Modifier) {
    val size = dpToSp(TURN_DP * scale)
    val shape = RoundedCornerShape(3.dp)
    Text(
        "Turn $turn",
        style = GoaTextStyles.plateTitle.copy(
            fontSize = size,
            lineHeight = size * 1.25f,
            fontWeight = FontWeight.Bold,
            color = GoaTheme.colors.onParchment,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(Brush.verticalGradient(listOf(TabParchmentTop, TabParchmentLow)), shape)
            .border(1.dp, TabEdge, shape)
            .padding(vertical = 1.dp)
            .clearAndSetSemantics {},
    )
}

/**
 * The dashboard's frame, drawn as vectors so it is sharp at any size: a dark rim, a steel band lit from above, a
 * bronze hairline inside it, and the plate's teal corner brackets.
 */
private class DashboardFrame(private val size: Size, private val corner: Float, private val px: Float) {
    private val steel = Brush.verticalGradient(listOf(SteelLight, SteelMid, SteelDark, SteelMid))
    private val band = maxOf(2f, 2.5f * px)
    private val inset = band * 2.4f
    private val arm = 13f * px
    private val bracket = maxOf(1.5f, 2f * px)

    fun draw(scope: DrawScope) = with(scope) {
        val rim = band * 1.8f
        drawRoundRect(
            FrameShadow,
            topLeft = Offset(rim / 2f, rim / 2f),
            size = Size(size.width - rim, size.height - rim),
            cornerRadius = CornerRadius(corner),
            style = Stroke(rim),
        )
        drawRoundRect(
            steel,
            topLeft = Offset(band / 2f, band / 2f),
            size = Size(size.width - band, size.height - band),
            cornerRadius = CornerRadius(corner),
            style = Stroke(band),
        )
        drawRoundRect(
            BronzeLine,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(maxOf(0f, corner - inset / 2f)),
            style = Stroke(maxOf(1f, px)),
        )
        val e = inset + bracket * 2.5f
        listOf(
            Offset(e, e) to Offset(1f, 1f),
            Offset(size.width - e, e) to Offset(-1f, 1f),
            Offset(e, size.height - e) to Offset(1f, -1f),
            Offset(size.width - e, size.height - e) to Offset(-1f, -1f),
        ).forEach { (at, dir) ->
            drawLine(BracketTeal, at, at + Offset(arm * dir.x, 0f), strokeWidth = bracket, cap = StrokeCap.Round)
            drawLine(BracketTeal, at, at + Offset(0f, arm * dir.y), strokeWidth = bracket, cap = StrokeCap.Round)
        }
    }
}

/** The cards in one slot of the frame (a second card peeks out behind the first), its band and state. */
@Composable
private fun SlotOnFrame(
    heroId: String,
    table: TableUi,
    slot: DashboardSlot,
    width: Dp,
    scale: Float,
    focus: CardFocusState,
    title: String,
    viewable: List<FocusCard>,
    modifier: Modifier,
) {
    val faces = slot.cards.map { view -> view.cardId?.takeIf { !view.facedown }?.let(table::card) }
    val first = faces.firstOrNull()
    val show = faces.firstNotNullOfOrNull { it }?.let { card ->
        val at = viewable.indexOfFirst { it.key == "$title/${card.id}" }.coerceAtLeast(0)
        ({ focus.open(viewable, at, title = title) })
    }
    val spoken = buildList {
        add("Turn ${slot.turn}")
        if (slot.cards.isEmpty() && slot.word == null) add("empty slot")
        slot.word?.let { word -> add(word.lowercase()) }
        faces.forEach { add(it?.let(::spokenCard) ?: "facedown card") }
        slot.marker?.let { add(spokenSlotMarker(it)) }
    }.joinToString(", ")
    val shape = RoundedCornerShape(width * 0.08f)
    val extra = if (slot.cards.size > 1) width * SECOND_CARD else 0.dp
    Box(
        modifier
            .width(width + extra)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (show != null) {
                    role = Role.Button
                    onClick(label = "Look at the card") {
                        show()
                        true
                    }
                }
            },
    ) {
        if (slot.cards.size > 1) {
            // Emmitt's Ultimate: the frame holds the second card, peeking out behind the first.
            TableCard(faces[1], Modifier.align(Alignment.TopEnd).alpha(SECOND_ALPHA), width = width, backOf = heroId)
        }
        Box(
            Modifier
                .width(width)
                .aspectRatio(CARD_ASPECT)
                .clip(shape)
                .then(if (show != null) Modifier.clickable(onClick = show) else Modifier)
                .then(if (first != null) Modifier.cardFocusOrigin(focus, "$title/${first.id}") else Modifier),
        ) {
            if (slot.cards.isNotEmpty()) {
                TableCard(first, width = width, backOf = heroId)
                if (slot.stepped) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = STEPPED_SCRIM)))
            } else {
                // An empty slot: a dark well with a dashed steel edge and its turn's number, never mistaken for a card.
                EmptySlot(slot.turn, scale, shape)
            }
            val (background, content) = bandColors(slot.now, slot.next)
            slot.word?.let { word ->
                val size = dpToSp(BAND_SP * scale)
                Text(
                    word,
                    style = TextStyle(
                        fontSize = size,
                        lineHeight = size * 1.4f,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = size * 0.06f,
                        color = content,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(background),
                )
            }
            if (slot.now || slot.next) Box(Modifier.fillMaxSize().border(2.dp, background, shape))
        }
    }
}

/**
 * An empty Turn slot: the slate a little deeper than the frame (not a black hole), a dashed muted edge and the turn's
 * number in the muted token (DESIGN.md "Muted states"), so it reads as a place to fill and stays legible in dim light.
 */
@Composable
private fun EmptySlot(turn: Int, scale: Float, shape: RoundedCornerShape) {
    val number = dpToSp(EMPTY_NUMBER_SP * scale)
    // The dashboard is the dark slate in both themes: its muted tokens are the dark theme's.
    GoaDarkTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(PlateGround.copy(alpha = EMPTY_FILL))
                .emptySlotOutline(shape, inset = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$turn",
                style = GoaTextStyles.plateTitle.copy(
                    fontSize = number,
                    lineHeight = number * 1.1f,
                    fontWeight = FontWeight.Bold,
                ),
                color = GoaTheme.colors.textMuted,
            )
        }
    }
}

/**
 * A marker below its Turn slot, on the frame's strip as on the table ("below each of your turn slots"): the
 * active one larger, ringed and labelled, never by colour alone. TalkBack hears it with the slot.
 */
@Composable
private fun SlotMarkerToken(marker: SlotMarker, scale: Float, modifier: Modifier, size: Dp) {
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        OfficialIcon(
            markerArt(marker.kind),
            Modifier
                .size(size)
                .then(if (marker.active) Modifier.border(2.dp, MarkerRing, CircleShape) else Modifier),
        )
        if (marker.active) {
            val text = dpToSp(MARKER_LABEL_SP * scale)
            Text(
                "ACTIVE",
                style = TextStyle(fontSize = text, lineHeight = text * 1.3f, fontWeight = FontWeight.Bold),
                color = MarkerLabelInk,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 3.dp)
                    .background(MarkerRing, RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/** What the markers below the Turn slots mean now, beside the frame that shows them. */
@Composable
internal fun SlotMarkersSection(table: TableUi, hero: HeroView) {
    val summary = slotMarkers(table, hero) ?: return
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeroSection("Runes")
        SlotMarkersLine(summary.activeKind, summary.headline, emphasised = true)
        summary.next?.let { Quiet(it) }
        summary.notes.forEach { Quiet(it) }
    }
}

/** The hand's reminder: which rune this turn's card will read, and the next one. */
@Composable
internal fun HandRuneLine(table: TableUi, hero: HeroView) {
    val summary = slotMarkers(table, hero) ?: return
    SlotMarkersLine(summary.activeKind, summary.line, emphasised = false)
}

@Composable
private fun SlotMarkersLine(kind: String?, text: String, emphasised: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        kind?.let {
            OfficialIcon(markerArt(it), Modifier.size(scaledIconSize(LINE_MARKER, 1.3f)).clearAndSetSemantics {})
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text,
            style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasised) FontWeight.SemiBold else null,
        )
    }
}

/**
 * What lies under the frame: the art the rulebook prints for this hero where it has one (Tigerclaw); any
 * other hero's portrait covering the frame, cropped around the head and darkened toward the edges, so
 * the frame and the cards stay what is read.
 */
@Composable
private fun DashboardArt(heroId: String, portrait: String?, modifier: Modifier) {
    val art = LookArt.dashboardArt(heroId)
    when {
        art != null -> OfficialIcon(art, modifier.clearAndSetSemantics {})

        portrait != null -> Box(modifier) {
            AsyncImage(
                model = assetUrl(portrait),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, PORTRAIT_BIAS),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val edges = Brush.radialGradient(
                        0.15f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Black.copy(alpha = 0.78f),
                        center = size.center,
                        radius = size.maxDimension * 0.58f,
                    )
                    onDrawBehind { drawRect(edges) }
                },
            )
        }
    }
}

/** A size given in dp as sp: the frame's words scale with the frame, not with the font scale. */
@Composable
private fun dpToSp(value: Float): TextUnit = with(LocalDensity.current) { value.dp.toSp() }

/**
 * Items (p.16, open information): the official item icon of each stat with what they add ("+1
 * Defense ×2"), for every hero. The hero's own player also sees which cards are tucked (p.4: hidden
 * from everyone else); tap one to read it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemsSection(table: TableUi, hero: HeroView, focus: CardFocusState) {
    if (hero.itemStats.isEmpty() && hero.items.isEmpty()) return
    HeroSection("Items", page = "p.16")
    if (hero.itemStats.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemGroups(hero.itemStats).forEach { (stat, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = 32.dp).clearAndSetSemantics {
                        val items = if (count > 1) ", $count items" else ", 1 item"
                        contentDescription = "+1 ${PlayText.stat(stat)}$items"
                    },
                ) {
                    OfficialIcon(
                        OfficialArt.itemIcon(stat),
                        Modifier.size(width = ITEM_ICON_WIDTH, height = ITEM_ICON_HEIGHT),
                    ) {
                        ItemIcon(stat)
                    }
                    Spacer(Modifier.width(3.dp))
                    Text("+$count", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    if (hero.items.isNotEmpty()) {
        val name = table.hero(hero.heroId).name
        // A puzzle shows the other side's too, and there "only you" isn't so.
        Quiet(
            if (hero.heroId in table.mine) {
                "Tucked under the dashboard: only you see which cards."
            } else {
                "Tucked under the dashboard: open in this puzzle."
            },
        )
        CardRow(
            hero.items.map { CardView(it) },
            table,
            focus,
            "$name · Items",
            captions = hero.items.map { id -> table.card(id)?.item?.let { "+1 ${PlayText.stat(it)}" }.orEmpty() },
        )
    }
}

/** Markers on the hero and the tokens it owns on the board (open information). */
@Composable
internal fun MarkersSection(table: TableUi, hero: HeroView) {
    val tokens = table.view.pieces.filter { it.ownerHeroId == hero.heroId && figureOf(it) == null }
    // Below a slot, a marker is on the dashboard and in its own section.
    val markers = hero.markers.filter { it.slot == null }
    if (markers.isEmpty() && tokens.isEmpty()) return
    HeroSection("Markers and tokens")
    markers.forEach { marker ->
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            OfficialIcon(
                markerArt(marker.kind),
                Modifier.size(scaledIconSize(LINE_MARKER, 1.3f)).clearAndSetSemantics {},
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(markerLine(table, hero, marker), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (tokens.isNotEmpty()) {
        Text(
            "On the board: " + tokens.groupingBy { it.label }.eachCount().entries.joinToString(", ") { (label, n) ->
                if (n == 1) label else "$label × $n"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun HeroSection(title: String, page: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = GoaTextStyles.sectionHead,
            color = GoaTheme.colors.heading,
            modifier = Modifier.semantics { heading() },
        )
        // The rulebook page as a small pill after the head, never in its words.
        page?.let {
            Spacer(Modifier.width(Spacing.sm))
            PagePill(it)
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val LINE_MARKER = 28.dp
private val MarkerRing = Color(0xFFC9A35B)
private val MarkerLabelInk = Color(0xFF0B1622)
private val ITEM_ICON_WIDTH = 36.dp
private val ITEM_ICON_HEIGHT = 27.dp

// The redrawn dashboard's proportions, in dp at the reference width (a phone held upright).
private const val FRAME_REFERENCE = 411f
private val FRAME_CORNER = 10.dp
private val FRAME_PAD = 14.dp
private val SLOT_GAP = 10.dp
private val TITLE_GAP = 8.dp
private val TAB_GAP = 4.dp

/** A slot's card takes this share of its column; Emmitt's second card peeks out in the rest. */
private const val CARD_SHARE = 0.85f
private const val NAME_PLATE_SHARE = 0.86f
private const val NAME_DP = 13f
private const val TURN_DP = 11f
private const val BAND_SP = 11f
private const val MARKER_SHARE = 0.053f
private const val MARKER_ACTIVE_SHARE = 0.063f
private const val MARKER_LABEL_SP = 7.5f
private val SteelLight = Color(0xFFC9D2D8)
private val SteelMid = Color(0xFF7D8A93)
private val SteelDark = Color(0xFF3E4950)
private val FrameShadow = Color(0xB3000000)
private val BronzeLine = Color(0xCC9A7440)
private val BracketTeal = Color(0xFF3FC1B0)
private val TabParchmentTop = Color(0xFFF1E8D2)
private val TabParchmentLow = Color(0xFFDCCDA9)
private val TabEdge = Color(0xFF9A7440)
private const val SECOND_CARD = 0.17f
private const val SECOND_ALPHA = 0.9f
private const val STEPPED_SCRIM = 0.38f
private const val EMPTY_FILL = 0.55f
private const val EMPTY_NUMBER_SP = 18f
private const val ART_VEIL = 0.6f

/** A portrait's face sits above its middle. */
private const val PORTRAIT_BIAS = -0.45f

/** The action that opens a hero's sheet, as TalkBack says it (map piece, hero strip, turn track). */
internal fun showGameState(name: String): String = "Open $name's state"
