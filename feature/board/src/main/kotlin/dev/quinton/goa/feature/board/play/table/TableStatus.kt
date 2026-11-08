package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.CoinIcon
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LifeCounterIcon
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.WaveCounterIcon
import dev.quinton.goa.core.designsystem.component.bottomEdgeFade
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.TeamBadge
import dev.quinton.goa.feature.board.ui.badgeOverhang
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell

/**
 * The round at a glance, a slate plate as in mock-up 01, each group named in small caps over it so nothing has to
 * be remembered: LIVES (each team's mark and official Life counter with the number), WAVES (the Wave counters
 * left, per Lane), TURN (four diamond pips: done solid and dimmer, current bright and ringed, later hollow; the
 * round is in the title), TIE BREAKER (the coin's side showing). Numbers in large figures for arm's length. One
 * TalkBack node. [spread] (a phone upright) makes it as wide as it is given, the coin at the far end; with little
 * width or large text it wraps. [below] (the turn strip) shares the plate under a hairline: one score bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableHud(
    table: TableUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    spread: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    val view = table.view
    val waves = laneWaves(table)
    val spoken = hudSpoken(table)
    // The plate is read at a glance: past 1.6× the numbers are large enough, and the coin keeps its row.
    val fontScale = LocalDensity.current.fontScale
    val cap = minOf(fontScale, HUD_MAX_SCALE) / fontScale
    val big = (if (compact) 17.sp else 20.sp) * cap
    val icon = 22.dp
    PlateSurface(
        shape = RoundedCornerShape(7.dp),
        brackets = true,
        modifier = modifier,
    ) {
        Column {
            HudCounters(view, waves, spoken, cap, big, icon, compact, spread, fontScale)
            if (below != null) {
                PlateRowDivider(Modifier.padding(horizontal = 6.dp))
                below()
            }
        }
    }
}

/** The counters of [TableHud], one TalkBack node ([spoken]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HudCounters(
    view: GameView,
    waves: List<Pair<String?, Int>>,
    spoken: String,
    cap: Float,
    big: TextUnit,
    icon: Dp,
    compact: Boolean,
    spread: Boolean,
    fontScale: Float,
) {
    val number = GoaTextStyles.numeric.copy(fontSize = big, lineHeight = big * 1.15f, fontWeight = FontWeight.Bold)
    FlowRow(
        Modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        // Compact (landscape, beside the heroes' portraits), or two Lanes' counters: closer together.
        horizontalArrangement = Arrangement.spacedBy(
            if (compact || waves.size > 1 || fontScale > 1.3f) Spacing.md else Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        HudGroup("Lives", cap) {
            Team.entries.forEachIndexed { i, team ->
                val left = view.lifeCounters[team] ?: 0
                if (i > 0) Spacer(Modifier.width(6.dp))
                TeamMark(team, markSize = scaledIconSize(11.dp, 1.15f))
                Spacer(Modifier.width(2.dp))
                LifeCounterIcon(team, spent = left == 0, size = scaledIconSize(icon, 1.15f))
                Spacer(Modifier.width(3.dp))
                Text("$left", style = number)
            }
        }
        HudGroup("Waves", cap) {
            waves.forEachIndexed { i, (lane, left) ->
                if (i > 0) Spacer(Modifier.width(6.dp))
                WaveCounterIcon(
                    spent = left == 0,
                    size = scaledIconSize(
                        if (lane !=
                            null
                        ) {
                            icon - 2.dp
                        } else {
                            icon
                        },
                        1.15f,
                    ),
                )
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
        HudGroup("Turn ${view.turn}", cap) {
            Box(Modifier.heightIn(min = with(LocalDensity.current) { big.toDp() } * 1.15f), Alignment.Center) {
                TurnPips(view.turn)
            }
        }
        val coin: @Composable () -> Unit = {
            // Compact (the side panel) or large text: the coin alone keeps the HUD on one row (TalkBack names it).
            HudGroup(if (compact || fontScale > 1.3f) null else "Tie Breaker", cap, end = spread) {
                TieBreakerMark(view.tieBreaker, if (compact || fontScale > 1.3f) 22.dp else 26.dp)
            }
        }
        if (spread) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) { coin() }
        } else {
            coin()
        }
    }
}

/** A HUD group: its name in small caps over what it counts (the name grows no more than the numbers do). */
@Composable
private fun HudGroup(name: String?, cap: Float, end: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        name?.let {
            Text(
                it.uppercase(),
                style = GoaTextStyles.overline.copy(
                    fontSize = 11.sp * cap,
                    lineHeight = 12.sp * cap,
                    letterSpacing = 0.6.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Four bronze diamonds: done turns solid in a dimmer bronze, the current one solid in bright bronze with a ring and
 * larger, later ones hollow.
 */
@Composable
internal fun TurnPips(turn: Int, scale: Float = 1f) {
    // On the light theme's paper the pale bronze washed out (round 3): bronze ink there (ADR-077).
    val dark = GoaTheme.isDark
    val bronze = if (dark) MaterialTheme.colorScheme.primary else GoaTheme.colors.bronzeInk
    val bright = if (dark) PipBright else GoaTheme.colors.bronzeInk
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        for (t in 1..TURNS_IN_ROUND) {
            val current = t == turn
            Canvas(Modifier.size((if (current) 19.dp else 14.dp) * scale)) {
                val c = size.minDimension / 2f
                when {
                    t < turn -> drawPath(diamond(c, c * 0.92f), bronze.copy(alpha = PIP_DONE_ALPHA))

                    current -> {
                        drawPath(diamond(c, c * 0.56f), bright)
                        drawPath(diamond(c, c * 0.96f), bright, style = Stroke(width = 1.5.dp.toPx()))
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
        val strip = rememberScrollState()
        Row(
            modifier.horizontalEdgeFade(strip).horizontalScroll(strip),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            heroes.forEach { hero ->
                val place = order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
                CompactHeroChip(table, hero, place, onClick = { onHero(hero.heroId) })
            }
        }
    } else {
        // The turn strip's own cells (UI hill climb, round 4): the strip keeps one form in every state.
        HeroCells(table, onHero, modifier)
    }
}

/** A row that scrolls when its items don't fit, and shares the spare width among them when they do. */
@Composable
internal fun SharedWidthRow(modifier: Modifier = Modifier, gap: Dp = Spacing.xs, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxWidth
        val strip = rememberScrollState()
        Layout(content, Modifier.horizontalEdgeFade(strip).horizontalScroll(strip)) { items, _ ->
            val gap = gap.roundToPx()
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

/** The rune active this turn, open to everyone at a glance (TalkBack hears it with the chip). */
@Composable
internal fun ActiveMarkerBadge(table: TableUi, hero: HeroView, modifier: Modifier) {
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
private val BronzeEdge = Color(0xFFC9A35B)
private val BadgeCard = Color(0xFF10181E)

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
        RuleRow("Open information", page = "p.4", live = false)
    }
    Column(modifier.fillMaxSize()) {
        HeroSheetHeader(table, hero, onDone)
        // A wide sheet (landscape) puts the dashboard beside everything else.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= WIDE_SHEET) {
                val scroll = rememberScrollState()
                Row(Modifier.bottomEdgeFade(scroll).verticalScroll(scroll), horizontalArrangement = gap) {
                    Column(Modifier.weight(1f), verticalArrangement = gap) {
                        HeroDashboard(table, hero, focus, Modifier.fillMaxWidth())
                        Column(side, verticalArrangement = gap) {
                            SlotMarkersSection(table, hero)
                            ThisTurnSection(table, hero)
                        }
                    }
                    Column(
                        Modifier.weight(1f).padding(end = Spacing.screen, bottom = SHEET_END),
                        verticalArrangement = gap,
                        content = theRest,
                    )
                }
            } else {
                // The sheet fades out at its foot while more lies below: read as a page that goes on, not one cut.
                val scroll = rememberScrollState()
                Column(Modifier.bottomEdgeFade(scroll).verticalScroll(scroll), verticalArrangement = gap) {
                    HeroDashboard(table, hero, focus, Modifier.fillMaxWidth())
                    Column(side.padding(bottom = SHEET_END), verticalArrangement = gap) {
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
            // The dashboard's dials as plain numbers: Level and Coins in chips, read at a glance.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp),
            ) {
                DialChip("Level", hero.level)
                DialChip("Coins", hero.coins) { CoinIcon(size = scaledIconSize(15.dp, 1.3f)) }
            }
            Text(
                heroWhere(table, hero),
                style = small,
                color = if (hero.at == null) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }
        IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
    }
}

/**
 * A dial of the dashboard: its name in small caps and its value in large figures ("LEVEL 2") on a well, without an
 * edge of its own: the sheet is the frame (DESIGN.md "Framing levels").
 */
@Composable
private fun DialChip(label: String, value: Int, icon: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .background(PlateWell, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            it()
            Spacer(Modifier.width(4.dp))
        }
        Text(label.uppercase(), style = GoaTextStyles.overline, color = GoaTheme.colors.heading)
        Spacer(Modifier.width(6.dp))
        Text(
            "$value",
            style = GoaTextStyles.score.copy(fontSize = 17.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.Bold,
        )
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
                HeroSection("Ultimate", page = "p.16")
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
                    CardRow(
                        backs,
                        table,
                        focus,
                        "$name · In hand",
                        width = SHEET_CARD,
                        backOf = hero.heroId,
                        wrap = true,
                    )
                }

                hero.heroId in table.mine -> {
                    Section("Your hand · ${hand.size}")
                    CardRow(
                        hand.map { CardView(it) },
                        table,
                        focus,
                        "$name · Your hand",
                        width = SHEET_CARD,
                        wrap = true,
                    )
                }

                hand.isNotEmpty() -> {
                    // Only a puzzle shows another hero's hand: its hands are open information.
                    Section("In hand · open in this puzzle")
                    CardRow(hand.map { CardView(it) }, table, focus, "$name · In hand", width = SHEET_CARD, wrap = true)
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

/**
 * The deciding hero's hand, small, under the question; tap a card to see it large. Only while one of the person's
 * heroes decides, and only that hero's: a phone passed round never shows a hand beside another seat's question. With
 * several heroes the heading names whose it is ("Your hand (Arien)"); hidden while the computer or another seat
 * decides, and when the question itself shows the hand (play, defend).
 */
@Composable
fun YourHand(table: TableUi, focus: CardFocusState) {
    val heroId = table.decision?.seat?.takeIf { it in table.mine && table.asking } ?: return
    if (table.handDecision) return
    val hand = table.heroView(heroId)?.hand ?: return
    if (hand.isEmpty()) return
    val title = yourHandTitle(table, heroId)
    Text(
        title,
        style = GoaTextStyles.sectionHead,
        color = GoaTheme.colors.heading,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    CardRow(hand.map { CardView(it) }, table, focus, title)
}

/** "Your hand", or with several heroes of the person's "Your hand (Arien)": always whose, never another seat's. */
internal fun yourHandTitle(table: TableUi, heroId: String): String =
    if (table.mine.size > 1) "Your hand (${table.hero(heroId).name})" else "Your hand"

/**
 * The deciding hero's hand as a tab pinned at the foot of the panel (GameTable's `panelFooter`), so the panel ends
 * on a whole line rather than a heading cut at the screen's edge: a drag handle, the card back, "Your hand · 5" and a
 * chevron. A tap (or a drag up) shows the cards under it, small, a tap on one reads it large; a tap (or a drag down)
 * folds them away. Shown when [YourHand] would show the hand ([open] is kept by the screen).
 */
@Composable
fun HandTab(table: TableUi, focus: CardFocusState, open: Boolean, onOpenChange: (Boolean) -> Unit) {
    val heroId = table.decision?.seat?.takeIf { it in table.mine && table.asking } ?: return
    // A question answered with cards (play, defend, discard, level up) shows them itself.
    if (table.handDecision || table.cardOptions.isNotEmpty()) return
    val hand = table.heroView(heroId)?.hand ?: return
    if (hand.isEmpty()) return
    // Whose hand, always: the phone is passed round the table.
    val title = "Your hand (${table.hero(heroId).name})"
    val toggle by rememberUpdatedState(onOpenChange)
    val shown by rememberUpdatedState(open)
    val threshold = with(LocalDensity.current) { HAND_DRAG.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { dragged += it }
    Column(Modifier.fillMaxWidth()) {
        PlateRowDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .draggable(
                    drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragged = 0f },
                    onDragStopped = {
                        if (-dragged > threshold && !shown) toggle(true)
                        if (dragged > threshold && shown) toggle(false)
                    },
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (open) "Fold the hand away" else "Show the hand",
                ) { onOpenChange(!open) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title, ${BoardText.count(hand.size, "card")}"
                    stateDescription = if (open) "Shown" else "Folded away"
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape),
            )
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TableCard(null, width = 14.dp, backOf = heroId)
                Spacer(Modifier.width(Spacing.sm))
                // The card back is the hero's deck symbol, so the words need not name whose (TalkBack does).
                Text(
                    "Your hand · ${hand.size}",
                    style = GoaTextStyles.sectionHead,
                    color = GoaTheme.colors.heading,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }
        if (open) {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = Spacing.sm)) {
                CardRow(hand.map { CardView(it) }, table, focus, title)
            }
        }
    }
}

private val HAND_DRAG = 24.dp

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
    /** A grid as wide as it is given instead of a row that scrolls (the hero sheet's hand). */
    wrap: Boolean = false,
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
        wrap = wrap,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbRow(
    cards: List<CardView>,
    table: TableUi,
    captions: List<String>,
    origin: (Card) -> Modifier,
    onOpen: (index: Int, card: Card) -> Unit,
    width: Dp = THUMB_CARD,
    backOf: String? = null,
    wrap: Boolean = false,
) {
    if (wrap) {
        // The hero sheet's hand: a grid as wide as the sheet, every card whole (a row cut the fourth at the edge).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = Spacing.sm
            val columns = ((maxWidth + gap) / (GRID_CARD_MIN + gap)).toInt().coerceAtLeast(1)
            val each = minOf(width, (maxWidth - gap * (columns - 1)) / columns)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                maxItemsInEachRow = columns,
            ) {
                ThumbItems(cards, table, captions, origin, onOpen, each, backOf)
            }
        }
        return
    }
    val scroll = rememberScrollState()
    Row(
        Modifier.horizontalEdgeFade(scroll).horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ThumbItems(cards, table, captions, origin, onOpen, width, backOf)
        // Room after the last card, so the edge's fade never lies over it once scrolled to the end.
        Spacer(Modifier.width(Spacing.md))
    }
}

/** The cards of a [ThumbRow], each with its caption and, hand-sized, its name and numbers. */
@Composable
private fun ThumbItems(
    cards: List<CardView>,
    table: TableUi,
    captions: List<String>,
    origin: (Card) -> Modifier,
    onOpen: (index: Int, card: Card) -> Unit,
    width: Dp,
    backOf: String?,
) {
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
            // A hand-sized card says its name and numbers in a strip under it (the scan's print is too small).
            if (card != null && !card.isSpell && width >= NUMBERED_THUMB) {
                CardCaption(card, width)
                CardNumbers(card, width)
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

private const val OFF_BOARD_ALPHA = 0.45f

/** From this width the hero sheet shows the dashboard beside the rest. */
private val WIDE_SHEET = 600.dp
private val THUMB_CARD = 64.dp

/** From this width a thumbnail carries its name and numbers under it ([CardNumbers]): the hand, not small rows. */
private val NUMBERED_THUMB = 80.dp

/** The Ultimate and the hand on the hero sheet (mock-up 05). */
private val SHEET_CARD = 92.dp

/** Room under the sheet's last line, clear of the sheet's foot and the gesture bar. */
private val SHEET_END = 40.dp

/** The narrowest card of the hero sheet's hand grid: four to a row on a phone, names and numbers still read. */
private val GRID_CARD_MIN = 84.dp
