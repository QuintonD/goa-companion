package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LifeCounterIcon
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.WaveCounterIcon
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/** Turns in a round (p.7). */
private const val TURNS = 4

/**
 * The round at a glance, a slate plate as in mock-up 01: each team's mark and official Life counter
 * with the number beside it, the Wave counters left (per Lane), the round and its four diamond pips,
 * the side of the Tie Breaker coin showing at the end. One TalkBack node. [spread] (a phone upright)
 * makes it as wide as it is given, the coin at the far end; with little width or large text it wraps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableHud(table: TableUi, modifier: Modifier = Modifier, compact: Boolean = false, spread: Boolean = false) {
    val view = table.view
    val waves = table.map.lanes.map { lane -> BoardText.lane(table.map, lane.id) to (view.wavesLeft[lane.id] ?: 0) }
    val spoken = buildList {
        Team.entries.forEach { team ->
            add("${BoardText.team(team)} ${BoardText.count(view.lifeCounters[team] ?: 0, "Life counter")}")
        }
        waves.forEach { (lane, left) ->
            add(listOfNotNull(lane, "${BoardText.count(left, "Wave counter")} left").joinToString(" "))
        }
        add("Round ${view.round}, turn ${view.turn} of $TURNS")
        add("Tie Breaker coin shows ${BoardText.team(view.tieBreaker)}")
    }.joinToString(". ")
    // The plate is read at a glance: past 1.6× the numbers are large enough, and the coin keeps its row.
    val fontScale = LocalDensity.current.fontScale
    val big = 16.sp * (minOf(fontScale, HUD_MAX_SCALE) / fontScale)
    val number = GoaTextStyles.numeric.copy(fontSize = big, lineHeight = big * 1.25f, fontWeight = FontWeight.SemiBold)
    PlateSurface(
        shape = RoundedCornerShape(7.dp),
        brackets = true,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        FlowRow(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            // Compact (landscape, beside the heroes' portraits), or two Lanes' counters: closer together.
            horizontalArrangement = Arrangement.spacedBy(
                if (compact || waves.size > 1 || fontScale > 1.3f) Spacing.sm else Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Team.entries.forEach { team ->
                val left = view.lifeCounters[team] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(team, markSize = scaledIconSize(11.dp, 1.15f))
                    Spacer(Modifier.width(2.dp))
                    LifeCounterIcon(team, spent = left == 0, size = scaledIconSize(25.dp, 1.15f))
                    Spacer(Modifier.width(3.dp))
                    Text("$left", style = number)
                }
            }
            waves.forEach { (lane, left) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaveCounterIcon(spent = left == 0, size = scaledIconSize(if (lane != null) 22.dp else 25.dp, 1.15f))
                    Spacer(Modifier.width(2.dp))
                    // Two Lanes: "L1 3" fits both on the row; TalkBack hears "Lane 1".
                    lane?.let {
                        Text(
                            it.replace("Lane ", "L"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("$left", style = number)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "R${view.round}",
                    style = GoaTextStyles.plateTitle.copy(fontSize = big, lineHeight = big * 1.25f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(5.dp))
                TurnPips(view.turn)
            }
            if (spread) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TieBreakerMark(view.tieBreaker, if (fontScale > 1.3f) 22.dp else 28.dp)
                }
            } else {
                TieBreakerMark(view.tieBreaker, if (compact) 24.dp else 28.dp)
            }
        }
    }
}

/**
 * Four bronze diamonds: done turns solid in a dimmer bronze, the current one solid in bright bronze with a ring and
 * larger, later ones hollow.
 */
@Composable
private fun TurnPips(turn: Int) {
    val bronze = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (t in 1..TURNS) {
            val current = t == turn
            Canvas(Modifier.size(if (current) 16.dp else 12.dp)) {
                val c = size.minDimension / 2f
                when {
                    t < turn -> drawPath(diamond(c, c * 0.92f), bronze.copy(alpha = PIP_DONE_ALPHA))

                    current -> {
                        drawPath(diamond(c, c * 0.56f), PipBright)
                        drawPath(diamond(c, c * 0.96f), PipBright, style = Stroke(width = 1.5.dp.toPx()))
                    }

                    else -> drawPath(diamond(c, c * 0.8f), bronze, style = Stroke(width = 1.2.dp.toPx()))
                }
            }
        }
    }
}

private fun diamond(center: Float, radius: Float) = Path().apply {
    moveTo(center, center - radius)
    lineTo(center + radius, center)
    lineTo(center, center + radius)
    lineTo(center - radius, center)
    close()
}

/**
 * The Tie Breaker coin as printed (ADR-042, `OfficialArt.tieBreaker`: Red's gear, Blue's star, p.5),
 * with the app's team shape on a badge at its foot, as on the board's pieces (team never by colour
 * alone). While the face loads, or if it fails: the shape on a bronze disc.
 */
@Composable
internal fun TieBreakerMark(team: Team, size: Dp = 22.dp) {
    val bronze = MaterialTheme.colorScheme.primary
    val coin = scaledIconSize(size, 1.4f)
    val badge = (coin * COIN_BADGE).coerceAtMost(MAX_COIN_BADGE)
    Box(Modifier.size(coin)) {
        OfficialIcon(OfficialArt.tieBreaker(team), Modifier.matchParentSize()) {
            Box(Modifier.matchParentSize().border(2.dp, bronze, CircleShape))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = badge * 0.2f, y = badge * 0.2f)
                .size(badge)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, bronze, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TeamMark(team, markSize = badge * 0.62f)
        }
    }
}

private const val HUD_MAX_SCALE = 1.6f
private const val PIP_DONE_ALPHA = 0.62f
private val PipBright = Color(0xFFE4C47E)
private const val COIN_BADGE = 0.48f
private val MAX_COIN_BADGE = 24.dp

/**
 * Every hero's status, one chip each: portrait ringed in team colour, level, coins, cards in hand.
 * Before the reveal in team order; after it in initiative order, numbered, with the initiative of
 * the card played (the initiative order strip). The acting hero's chip is outlined in bronze and
 * marked "Now". Tap a chip for the hero's game state (ADR-044). The chips share out the width when they
 * fit, and scroll when they don't (every name is in full).
 *
 * [compact] (a phone held sideways with the panel shown, where the map has little height): each
 * hero is just its ringed portrait with the order badge, the acting one in a bronze ring, so the
 * strip fits beside the round at a glance; a tap still opens everything about the hero, and TalkBack
 * hears the whole chip.
 */
@Composable
fun HeroStrip(table: TableUi, onHero: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val order = table.initiativeOrder
    val heroes = if (order.isNotEmpty()) {
        order.mapNotNull(table::heroView) + table.view.heroes.filter { it.heroId !in order }
    } else {
        table.view.heroes.sortedBy { it.team.ordinal }
    }
    if (compact) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            heroes.forEach { hero ->
                val place = order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
                CompactHeroChip(table, hero, place, onClick = { onHero(hero.heroId) })
            }
        }
    } else {
        SharedWidthRow(modifier) {
            heroes.forEach { hero ->
                val place = order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
                HeroChip(table, hero, place, onClick = { onHero(hero.heroId) })
            }
        }
    }
}

/** A row that scrolls when its items don't fit, and shares the spare width among them when they do. */
@Composable
private fun SharedWidthRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxWidth
        Layout(content, Modifier.horizontalScroll(rememberScrollState())) { items, _ ->
            val gap = Spacing.xs.roundToPx()
            val natural = items.map { it.maxIntrinsicWidth(Constraints.Infinity) }
            val free = viewport - natural.sum() - gap * (items.size - 1)
            val spare = free.coerceAtLeast(0) / items.size.coerceAtLeast(1)
            val height = items.maxOfOrNull { it.maxIntrinsicHeight(Constraints.Infinity) } ?: 0
            val placed = items.mapIndexed { i, item ->
                item.measure(Constraints.fixedWidth(natural[i] + spare).copy(minHeight = height, maxHeight = height))
            }
            layout(placed.sumOf { it.width } + gap * (placed.size - 1).coerceAtLeast(0), height) {
                var x = 0
                placed.forEach {
                    it.place(x, 0)
                    x += it.width + gap
                }
            }
        }
    }
}

@Composable
private fun HeroChip(table: TableUi, hero: HeroView, place: Int?, onClick: () -> Unit) {
    val info = table.hero(hero.heroId)
    // A small phone: four chips still fit side by side.
    val tight = LocalConfiguration.current.screenWidthDp < TIGHT_CHIPS
    val acting = table.view.acting == hero.heroId
    val played = table.card(hero.played?.cardId)?.takeIf { hero.played?.facedown != true }
    val spoken = spokenChip(table, hero, place)
    PlateSurface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        brackets = true,
        border = if (acting) BorderStroke(2.dp, BronzeEdge) else null,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = showGameState(info.name)) {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(
                start = if (tight) 5.dp else 6.dp,
                end = if (tight) 2.dp else 4.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipPortrait(table, hero, place, if (tight) 26.dp else 32.dp)
            Spacer(Modifier.width(if (tight) 4.dp else 5.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = if (tight) 12.sp else 13.sp,
                            letterSpacing = 0.sp,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (acting) {
                        Text(
                            " · Now",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (tight) 2.dp else 3.dp),
                ) {
                    Text("Lv${hero.level}", style = chipNumber())
                    CoinIcon(size = scaledIconSize(12.dp, 1.3f))
                    Text("${hero.coins}", style = chipNumber())
                    (hero.initiative ?: played?.initiative)?.let {
                        Stat(dev.quinton.goa.core.designsystem.cardtext.GameIcons.initiative.vector, "$it")
                    }
                    // Before the reveal: whose card is down already (p.7: played facedown, unseen).
                    if (cardDown(table, hero)) TableCard(null, width = 9.dp, backOf = hero.heroId)
                }
            }
        }
    }
}

@Composable
private fun chipNumber(): TextStyle =
    GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize)

/**
 * The portrait in its team ring, the mark on a dark badge at its foot; the cards in hand on a small card
 * at its head, so four chips fit a phone; the order among the heroes, after the reveal, at the other side.
 */
@Composable
private fun ChipPortrait(table: TableUi, hero: HeroView, place: Int?, size: Dp) {
    val info = table.hero(hero.heroId)
    Box {
        HeroPortrait(
            hero.heroId,
            info.portrait,
            null,
            Modifier
                .size(scaledIconSize(size, 1.3f))
                .border(2.dp, teamColor(hero.team), CircleShape)
                .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
            shape = CircleShape,
            framed = false,
        )
        TeamBadge(hero.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(CHIP_BADGE), size = CHIP_BADGE)
        ActiveMarkerBadge(table, hero, Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 3.dp))
        HandCount(hero.handCount, Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-3).dp))
        if (place != null) OrderBadge(place, Modifier.align(Alignment.TopEnd))
    }
}

/** The rune active this turn, open to everyone at a glance (TalkBack hears it with the chip). */
@Composable
private fun ActiveMarkerBadge(table: TableUi, hero: HeroView, modifier: Modifier) {
    activeMarker(table, hero)?.let {
        OfficialIcon(
            markerArt(it.kind),
            modifier
                .size(CHIP_MARKER)
                .background(BadgeCard, CircleShape)
                .border(1.dp, BronzeEdge, CircleShape)
                .clearAndSetSemantics {},
        )
    }
}

/** How many cards a hero holds, on a little card. */
@Composable
private fun HandCount(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 12.dp, minHeight = 15.dp)
            .background(BadgeCard, RoundedCornerShape(2.dp))
            .border(1.dp, BronzeEdge, RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = GoaTextStyles.numeric.copy(fontSize = 10.sp, lineHeight = 13.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OrderBadge(place: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(16.dp)
            .background(BadgeCard, CircleShape)
            .border(1.dp, BronzeEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$place", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** A hero as its portrait only: team ring, order badge, a bronze ring while it acts. */
@Composable
private fun CompactHeroChip(table: TableUi, hero: HeroView, place: Int?, onClick: () -> Unit) {
    val info = table.hero(hero.heroId)
    val acting = table.view.acting == hero.heroId
    val spoken = spokenChip(table, hero, place)
    PlateSurface(
        onClick = onClick,
        shape = CircleShape,
        border = if (acting) BorderStroke(2.dp, BronzeEdge) else null,
        modifier = Modifier
            .size(COMPACT_CHIP)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = showGameState(info.name)) {
                    onClick()
                    true
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            HeroPortrait(
                hero.heroId,
                info.portrait,
                null,
                Modifier
                    .size(COMPACT_PORTRAIT)
                    .border(2.dp, teamColor(hero.team), CircleShape)
                    .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
                shape = CircleShape,
                framed = false,
            )
            if (place != null) OrderBadge(place, Modifier.align(Alignment.TopStart))
            // Inside the chip's circle, which clips what overhangs it.
            TeamBadge(hero.team, Modifier.align(Alignment.BottomEnd).padding(5.dp), size = 15.dp)
            ActiveMarkerBadge(table, hero, Modifier.align(Alignment.BottomStart).padding(5.dp))
            if (cardDown(table, hero)) {
                TableCard(null, Modifier.align(Alignment.TopEnd), width = 11.dp, backOf = hero.heroId)
            }
        }
    }
}

private val COMPACT_CHIP = 48.dp
private val COMPACT_PORTRAIT = 38.dp
private val CHIP_BADGE = 15.dp
private val CHIP_MARKER = 17.dp
private const val TIGHT_CHIPS = 380
private val BronzeEdge = Color(0xFFC9A35B)
private val BadgeCard = Color(0xFF10181E)

@Composable
private fun Stat(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(12.dp, 1.3f)),
            )
            Spacer(Modifier.width(1.dp))
        }
        Text(text, style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize))
    }
}

/**
 * One hero's game state (ADR-044, open information, p.4) on the real dashboard (mock-up 05): who plays
 * them and where they stand, then this round's cards lying in the frame's Turn slots with their states
 * ([HeroDashboard]; a card facedown to the person stays a back), what this turn's state means and the
 * initiative as it counts; the items as icons (the tucked cards for the hero's own player), the Ultimate
 * and the hand (the person's own, any hero's in a puzzle, otherwise card backs), the discard, Active
 * effects, markers and tokens, the spellbook (counts, the Spellbook sheet one tap away). Tap a card to read it.
 */
@Composable
internal fun HeroDetail(
    table: TableUi,
    heroId: String,
    focus: CardFocusState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSpellbook: ((String) -> Unit)? = null,
) {
    val hero = table.heroView(heroId) ?: return
    val info = table.hero(heroId)
    val gap = Arrangement.spacedBy(Spacing.md)
    val side = Modifier.padding(horizontal = Spacing.screen)
    val theRest: @Composable ColumnScope.() -> Unit = {
        ItemsSection(table, hero, focus)
        UltimateAndHand(table, hero, focus)
        if (hero.discard.isNotEmpty()) {
            Section("Discard · ${hero.discard.size}")
            CardRow(hero.discard, table, focus, "${info.name} · Discarded")
        }
        if (hero.activeEffects.isNotEmpty()) {
            Section("Active effects")
            CardRow(
                hero.activeEffects.map { CardView(it, active = true) },
                table,
                focus,
                "${info.name} · Active effects",
            )
        }
        Counters(hero)
        MarkersSection(table, hero)
        // Gydion's spellbook (ADR-040): SpellbookSection.kt.
        ExtrasSection(
            table,
            hero,
            focus,
            onOpenSheet = onSpellbook?.takeIf { info.spells.isNotEmpty() }?.let { open -> { open(heroId) } },
        )
        Text(
            "Open information (p.4)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(modifier.fillMaxSize()) {
        HeroSheetHeader(table, hero, onDone)
        // A wide sheet (landscape) puts the dashboard beside everything else.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= WIDE_SHEET) {
                Row(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = gap) {
                    Column(Modifier.weight(1f), verticalArrangement = gap) {
                        HeroDashboard(table, hero, focus, Modifier.fillMaxWidth())
                        Column(side, verticalArrangement = gap) {
                            SlotMarkersSection(table, hero)
                            ThisTurnSection(table, hero)
                        }
                    }
                    Column(
                        Modifier.weight(1f).padding(end = Spacing.screen),
                        verticalArrangement = gap,
                        content = theRest,
                    )
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = gap) {
                    HeroDashboard(table, hero, focus, Modifier.fillMaxWidth())
                    Column(side.padding(bottom = Spacing.md), verticalArrangement = gap) {
                        SlotMarkersSection(table, hero)
                        ThisTurnSection(table, hero)
                        theRest()
                    }
                }
            }
        }
    }
}

/** Who the hero is, who plays them, their level and coins, where they stand; closing the sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroSheetHeader(table: TableUi, hero: HeroView, onDone: () -> Unit) {
    val info = table.hero(hero.heroId)
    val small = MaterialTheme.typography.bodySmall
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            HeroPortrait(
                hero.heroId,
                info.portrait,
                null,
                Modifier
                    .size(scaledIconSize(44.dp, 1.3f))
                    .border(2.dp, teamColor(hero.team), CircleShape)
                    .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
                shape = CircleShape,
                framed = false,
            )
            TeamBadge(hero.team, Modifier.align(Alignment.BottomEnd).badgeOverhang(CHIP_BADGE), size = CHIP_BADGE)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    info.name,
                    style = GoaTextStyles.sectionHead.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = GoaTheme.colors.heading,
                    modifier = Modifier.semantics { heading() },
                )
                TeamMark(hero.team, markSize = scaledIconSize(11.dp, 1.3f))
                Text(
                    listOf(BoardText.team(hero.team), table.controllerLabel(hero)).joinToString(" · "),
                    style = small,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Level ${hero.level} ·", style = small)
                CoinIcon(size = scaledIconSize(13.dp, 1.3f))
                Text("${BoardText.count(hero.coins, "coin")} ·", style = small)
                Text(
                    heroWhere(table, hero),
                    style = small,
                    color = if (hero.at == null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
        }
        IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
    }
}

/** The hero's own numbers a card or the Ultimate adds; the Ultimate unlocked when it has no card to show. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Counters(hero: HeroView) {
    val counters = shownCounters(hero)
    if (counters.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        counters.forEach { (name, value) -> Fact(PlayText.counter(name), "$value") }
    }
}

/**
 * The unlocked Ultimate (a faceup card on the dashboard, p.16) beside the hand: the person's own cards, any
 * hero's in a puzzle (its hands are open information), otherwise as many card backs (hidden, p.4).
 */
@Composable
private fun UltimateAndHand(table: TableUi, hero: HeroView, focus: CardFocusState) {
    val name = table.hero(hero.heroId).name
    val ultimate = ultimateCard(table, hero)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (ultimate != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Section("Ultimate · p.16")
                CardRow(listOf(CardView(ultimate.id)), table, focus, "$name · Ultimate", width = SHEET_CARD)
            }
        } else if (hero.ultimate) {
            Fact("Ultimate", "Unlocked")
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val hand = hero.hand
            when {
                hand == null -> {
                    Section("In hand · ${hero.handCount}")
                    val backs = List(hero.handCount) { CardView(null) }
                    CardRow(backs, table, focus, "$name · In hand", width = SHEET_CARD, backOf = hero.heroId)
                }

                hero.heroId in table.mine -> {
                    Section("Your hand · ${hand.size}")
                    CardRow(hand.map { CardView(it) }, table, focus, "$name · Your hand", width = SHEET_CARD)
                }

                hand.isNotEmpty() -> {
                    // Only a puzzle shows another hero's hand: its hands are open information.
                    Section("In hand · open in this puzzle")
                    CardRow(hand.map { CardView(it) }, table, focus, "$name · In hand", width = SHEET_CARD)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Section(title: String) = HeroSection(title)

/** The person's hand, small (one row per hero they control); tap a card to see it large. */
@Composable
fun YourHand(table: TableUi, focus: CardFocusState) {
    table.mine.forEach { heroId ->
        val hand = table.heroView(heroId)?.hand ?: return@forEach
        if (hand.isEmpty()) return@forEach
        val title = if (table.mine.size > 1) "${table.hero(heroId).name}'s hand" else "Your hand"
        Text(
            title,
            style = GoaTextStyles.sectionHead,
            color = GoaTheme.colors.heading,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        CardRow(hand.map { CardView(it) }, table, focus, title)
    }
}

/**
 * Small cards in a row; a facedown one shows its back. Tap a face-up one to see it large in the
 * card focus viewer, swiping through the row's other face-up cards ([title] heads the viewer).
 */
@Composable
internal fun CardRow(
    cards: List<CardView>,
    table: TableUi,
    focus: CardFocusState,
    title: String,
    slots: Boolean = false,
    /** Captions under the cards instead of the Turn slot / Active ones. */
    captions: List<String>? = null,
    width: Dp = THUMB_CARD,
    /** Whose deck's back a facedown card shows. */
    backOf: String? = null,
) {
    val captions = captions ?: cards.map { cardCaption(it, slots) }
    val open = cards.indices.filter { !cards[it].facedown && table.card(cards[it].cardId) != null }
    val viewable = open.map { i ->
        val card = table.card(cards[i].cardId)!!
        FocusCard(card, key = "$title/${card.id}", caption = captions[i].ifEmpty { null })
    }
    ThumbRow(
        cards,
        table,
        captions,
        origin = { card -> Modifier.cardFocusOrigin(focus, "$title/${card.id}") },
        onOpen = { i, _ -> open.indexOf(i).takeIf { it >= 0 }?.let { focus.open(viewable, it, title = title) } },
        width = width,
        backOf = backOf,
    )
}

/** Small cards in a row; a tap on a face-up one goes to [onCard] (the caller decides how it is read). */
@Composable
internal fun CardRow(
    cards: List<CardView>,
    table: TableUi,
    onCard: (Card) -> Unit,
    slots: Boolean = false,
    backOf: String? = null,
) {
    ThumbRow(
        cards,
        table,
        cards.map {
            cardCaption(it, slots)
        },
        origin = { Modifier },
        onOpen = { _, card -> onCard(card) },
        backOf = backOf,
    )
}

@Composable
private fun ThumbRow(
    cards: List<CardView>,
    table: TableUi,
    captions: List<String>,
    origin: (Card) -> Modifier,
    onOpen: (index: Int, card: Card) -> Unit,
    width: Dp = THUMB_CARD,
    backOf: String? = null,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cards.forEachIndexed { i, view ->
            val card = table.card(view.cardId).takeIf { !view.facedown }
            val spoken = listOfNotNull(
                captions.getOrNull(i)?.takeIf { it.isNotEmpty() },
                card?.let(::spokenCard) ?: "Facedown card",
            ).joinToString(", ")
            val show = { if (card != null) onOpen(i, card) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    if (card != null) {
                        onClick(label = "Look at the card") {
                            show()
                            true
                        }
                    }
                },
            ) {
                Surface(
                    onClick = show,
                    enabled = card != null,
                    shape = RoundedCornerShape(width * 0.08f),
                    color = Color.Transparent,
                    border = if (view.active) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                    modifier = if (card != null) origin(card) else Modifier,
                ) {
                    TableCard(card, width = width, backOf = backOf)
                }
                val caption = captions.getOrNull(i).orEmpty()
                if (caption.isNotEmpty()) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val OFF_BOARD_ALPHA = 0.45f

/** From this width the hero sheet shows the dashboard beside the rest. */
private val WIDE_SHEET = 600.dp
private val THUMB_CARD = 64.dp

/** The Ultimate and the hand on the hero sheet (mock-up 05). */
private val SHEET_CARD = 92.dp
