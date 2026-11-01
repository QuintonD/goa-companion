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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.assetUrl
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
 * The hero's real dashboard (rulebook p.16, mock-up 05): the frame over the hero's art, "Hero the Title" on
 * its big plate, "Turn n" on its small ones and the round's cards lying in its slots, each slot one
 * TalkBack node. Tap a card to read it. It scales with the sheet, as printed: its words don't grow with
 * the font scale (TalkBack and the lines under it say them).
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
    BoxWithConstraints(modifier.aspectRatio(FRAME_ASPECT).clipToBounds().background(PlateGround)) {
        val w = maxWidth
        val h = maxHeight
        val scale = w.value / FRAME_REFERENCE
        val ink = GoaTheme.colors.onParchment
        DashboardArt(hero.heroId, info.portrait, Modifier.fillMaxSize())
        OfficialIcon(LookArt.DASHBOARD_FRAME, Modifier.fillMaxSize().clearAndSetSemantics {})
        // The frame's blank upright plate at its right edge holds nothing here, and reads as a scrollbar.
        Box(
            Modifier
                .offset(x = w * BLANK_PLATE_X, y = h * BLANK_PLATE_Y)
                .size(w * BLANK_PLATE_WIDTH, h * BLANK_PLATE_HEIGHT)
                .background(PlateGround, RoundedCornerShape(2.dp)),
        )
        val name = listOf(info.name, info.title).filter { it.isNotBlank() }.joinToString(" ")
        // The big plate is about a third of the frame: a long title gets smaller rather than cut.
        val nameSize = dpToSp(minOf(NAME_SP * scale, (w * BIG_PLATE_TEXT).value / (name.length * CINZEL_EM)))
        Text(
            name,
            style = GoaTextStyles.plateTitle.copy(fontSize = nameSize, lineHeight = nameSize * 1.2f, color = ink),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * BIG_PLATE_Y - (nameSize.value * 0.6f).dp)
                .clearAndSetSemantics {},
        )
        val slotHeight = h * (SLOTS_BOTTOM - SLOTS_TOP)
        val cardWidth = slotHeight * CARD_ASPECT
        slots.forEachIndexed { i, slot ->
            val centre = w * SLOT_CENTRES[i]
            val turnSize = dpToSp(TURN_SP * scale)
            Text(
                "Turn ${slot.turn}",
                style = GoaTextStyles.plateTitle.copy(fontSize = turnSize, lineHeight = turnSize * 1.2f, color = ink),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(cardWidth)
                    .offset(x = centre - cardWidth / 2, y = h * TURN_PLATE_Y - (turnSize.value * 0.6f).dp)
                    .clearAndSetSemantics {},
            )
            val extra = if (slot.cards.size > 1) cardWidth * SECOND_CARD else 0.dp
            SlotOnFrame(
                hero.heroId,
                table,
                slot,
                cardWidth,
                scale,
                focus,
                title,
                viewable,
                Modifier.offset(x = centre - (cardWidth + extra) / 2, y = h * SLOTS_TOP),
            )
            slot.marker?.let { marker ->
                val size = h * if (marker.active) MARKER_ACTIVE else MARKER_SIZE
                SlotMarkerToken(
                    marker,
                    scale,
                    Modifier.offset(x = centre - size / 2, y = h * MARKER_Y - size / 2),
                    size,
                )
            }
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
                // The art under an empty slot can look almost like a card.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = EMPTY_SCRIM)))
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
    HeroSection("Items · p.16")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemGroups(hero.itemStats).forEach { (stat, count) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 32.dp).clearAndSetSemantics {
                    contentDescription = "+1 ${PlayText.stat(stat)}" + if (count > 1) ", $count items" else ", 1 item"
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
    if (hero.items.isNotEmpty()) {
        val name = table.hero(hero.heroId).name
        // A puzzle shows the other side's too, and there "only you" isn't so.
        Quiet(
            if (hero.heroId in table.mine) {
                "Tucked under the dashboard: only you see which cards (p.4)."
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
internal fun HeroSection(title: String) {
    Text(
        title,
        style = GoaTextStyles.sectionHead,
        color = GoaTheme.colors.heading,
        modifier = Modifier.semantics { heading() },
    )
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

// Positions as fractions of the 821 × 416 frame (official/ui/dashboard-frame).
private const val FRAME_ASPECT = 821f / 416f
private const val BIG_PLATE_Y = 0.19f
private const val TURN_PLATE_Y = 0.272f
private const val SLOTS_TOP = 0.335f
private const val SLOTS_BOTTOM = 0.83f
private const val MARKER_Y = 0.905f
private const val MARKER_SIZE = 0.105f
private const val MARKER_ACTIVE = 0.125f
private const val MARKER_LABEL_SP = 7.5f
private val SLOT_CENTRES = listOf(0.170f, 0.389f, 0.607f, 0.829f)

/** The frame width the words below are sized for, and how much of it the big plate's text may take. */
private const val FRAME_REFERENCE = 411f
private const val BIG_PLATE_TEXT = 0.29f
private const val NAME_SP = 10.5f
private const val CINZEL_EM = 0.6f
private const val TURN_SP = 9.5f
private const val BAND_SP = 10f
private const val SECOND_CARD = 0.17f
private const val SECOND_ALPHA = 0.9f
private const val STEPPED_SCRIM = 0.38f
private const val EMPTY_SCRIM = 0.4f
private const val BLANK_PLATE_X = 0.936f
private const val BLANK_PLATE_Y = 0.445f
private const val BLANK_PLATE_WIDTH = 0.029f
private const val BLANK_PLATE_HEIGHT = 0.215f

/** A portrait's face sits above its middle. */
private const val PORTRAIT_BIAS = -0.45f

/** The action that opens a hero's sheet, as TalkBack says it (map piece, hero strip, turn track). */
internal fun showGameState(name: String): String = "Show $name's game state"
