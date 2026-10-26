package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.TeamMark
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
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/** Turns in a round (p.7). */
private const val TURNS = 4

/**
 * The round at a glance: each team's Life counters, the Wave counters left (per Lane), the round
 * and its four turn pips, and the side of the Tie Breaker coin showing. One TalkBack node.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableHud(table: TableUi, modifier: Modifier = Modifier, compact: Boolean = false) {
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        FlowRow(
            Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
            // Compact (landscape, beside the heroes' portraits): the same, closer together.
            horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Team.entries.forEach { team ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(team, markSize = scaledIconSize(10.dp))
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.Favorite,
                        null,
                        tint = teamColor(team),
                        modifier = Modifier.size(scaledIconSize(14.dp, 1.4f)),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("${view.lifeCounters[team] ?: 0}", style = GoaTextStyles.numeric, fontWeight = FontWeight.Bold)
                }
            }
            waves.forEach { (lane, left) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Waves,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(scaledIconSize(16.dp, 1.4f)),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        listOfNotNull(lane, "$left").joinToString(" "),
                        style = GoaTextStyles.numeric,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("R${view.round}", style = GoaTextStyles.numeric, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                TurnPips(view.turn)
            }
            TieBreakerMark(view.tieBreaker)
        }
    }
}

/** Four pips: done turns filled, the current one ringed and larger, later ones hollow. */
@Composable
private fun TurnPips(turn: Int) {
    val done = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (t in 1..TURNS) {
            val current = t == turn
            Canvas(Modifier.size(if (current) 11.dp else 8.dp)) {
                val r = size.minDimension / 2f
                when {
                    t < turn -> drawCircle(done, r)

                    current -> {
                        drawCircle(done, r * 0.55f)
                        drawCircle(done, r * 0.92f, style = Stroke(width = r * 0.3f))
                    }

                    else -> drawCircle(rest, r * 0.8f, style = Stroke(width = r * 0.3f))
                }
            }
        }
    }
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

private const val COIN_BADGE = 0.48f
private val MAX_COIN_BADGE = 24.dp

/**
 * Every hero's status, one chip each: portrait ringed in team colour, level, coins, cards in hand.
 * Before the reveal in team order; after it in initiative order, numbered, with the initiative of
 * the card played (the initiative order strip). The acting hero's chip is outlined in bronze and
 * marked "Now". Tap a chip for the hero's cards and markers.
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
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        heroes.forEach { hero ->
            val place = order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
            if (compact) {
                CompactHeroChip(table, hero, place, onClick = { onHero(hero.heroId) })
            } else {
                HeroChip(table, hero, place, onClick = { onHero(hero.heroId) })
            }
        }
    }
}

@Composable
private fun HeroChip(table: TableUi, hero: HeroView, place: Int?, onClick: () -> Unit) {
    val info = table.hero(hero.heroId)
    val color = teamColor(hero.team)
    val acting = table.view.acting == hero.heroId
    val played = table.card(hero.played?.cardId)?.takeIf { hero.played?.facedown != true }
    val spoken = spokenChip(table, hero, place)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(
            if (acting) 2.dp else 1.dp,
            if (acting) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Read about ${info.name}") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = Spacing.sm, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HeroPortrait(
                    hero.heroId,
                    info.portrait,
                    null,
                    Modifier
                        .size(scaledIconSize(34.dp, 1.3f))
                        .border(2.dp, color, CircleShape)
                        .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
                    shape = CircleShape,
                    framed = false,
                )
                if (place != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$place", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(hero.team, markSize = 8.dp)
                    Spacer(Modifier.width(3.dp))
                    // The name may shorten; "Now" never does ("Trinkets · N…" at large text).
                    Text(
                        info.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 108.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Stat(null, "Lv ${hero.level}")
                    Stat(Icons.Filled.Paid, "${hero.coins}")
                    Stat(Icons.Outlined.Style, "${hero.handCount}")
                    (hero.initiative ?: played?.initiative)?.let {
                        Stat(dev.quinton.goa.core.designsystem.cardtext.GameIcons.initiative.vector, "$it")
                    }
                    // Before the reveal: whose card is down already (p.7: played facedown, unseen).
                    if (cardDown(table, hero)) TableCard(null, width = 10.dp)
                }
            }
        }
    }
}

/** A hero as its portrait only: team ring, order badge, a bronze ring while it acts. */
@Composable
private fun CompactHeroChip(table: TableUi, hero: HeroView, place: Int?, onClick: () -> Unit) {
    val info = table.hero(hero.heroId)
    val acting = table.view.acting == hero.heroId
    val spoken = spokenChip(table, hero, place)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(
            if (acting) 2.dp else 1.dp,
            if (acting) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .size(COMPACT_CHIP)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Read about ${info.name}") {
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
            if (place != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$place", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(2.dp)) { TeamMark(hero.team, markSize = 9.dp) }
            if (cardDown(table, hero)) TableCard(null, Modifier.align(Alignment.TopEnd), width = 11.dp)
        }
    }
}

private val COMPACT_CHIP = 48.dp
private val COMPACT_PORTRAIT = 38.dp

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
 * One hero in full (open information, p.4): level, coins, cards in hand, items with the stat each
 * raises, markers, Active effects, the card played this turn, the resolved Turn slots and the
 * discard. Its own hand when it is the person's hero (any hero's in a puzzle). Tap a card to read it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroDetail(table: TableUi, heroId: String, focus: CardFocusState, modifier: Modifier = Modifier) {
    val hero = table.heroView(heroId) ?: return
    val info = table.hero(heroId)
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroPortrait(
                heroId,
                info.portrait,
                null,
                Modifier.size(56.dp).border(2.dp, teamColor(hero.team), CircleShape),
                shape = CircleShape,
                framed = false,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics {
                        heading()
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(hero.team, markSize = 10.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${BoardText.team(hero.team)} · ${table.controllerLabel(hero)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Fact("Level", "${hero.level}")
            Fact("Coins", "${hero.coins}")
            Fact("In hand", "${hero.handCount}")
            if (hero.ultimate) Fact("Ultimate", "Active")
            if (hero.at == null) Fact("Board", "Off the board")
            shownCounters(hero).forEach { (name, value) -> Fact(PlayText.counter(name), "$value") }
        }
        if (hero.items.isNotEmpty()) {
            Section("Items · p.16")
            hero.items.mapNotNull(table::card).forEach { card ->
                val stat = card.item
                if (stat != null) ItemLine(stat, "+1 ${PlayText.stat(stat)} · ${card.name}") else Text(card.name)
            }
        }
        if (hero.markers.isNotEmpty()) {
            Section("Markers")
            hero.markers.forEach { marker ->
                val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
                Text(
                    marker.kind.replaceFirstChar {
                        it.uppercase()
                    } + from,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (hero.activeEffects.isNotEmpty()) {
            Section("Active effects")
            CardRow(
                hero.activeEffects.map {
                    CardView(it, active = true)
                },
                table,
                focus,
                "${info.name} · Active effects",
            )
        }
        hero.played?.let { played ->
            Section("Played this turn")
            CardRow(listOf(played) + hero.alsoPlayed, table, focus, "${info.name} · Played this turn")
        }
        if (hero.resolved.isNotEmpty()) {
            Section("Resolved · Turn slots")
            CardRow(hero.resolved, table, focus, "${info.name} · Resolved", slots = true)
        }
        if (hero.discard.isNotEmpty()) {
            Section("Discarded")
            CardRow(hero.discard, table, focus, "${info.name} · Discarded")
        }
        // The unlocked Ultimate is a faceup card on the dashboard (p.16): show it, not only "Active".
        ultimateCard(table, hero)?.let { card ->
            Section("Ultimate · p.16")
            CardRow(listOf(CardView(card.id)), table, focus, "${info.name} · Ultimate")
        }
        // Gydion's spellbook (ADR-040): SpellbookSection.kt.
        ExtrasSection(table, hero, focus)
        val hand = hero.hand
        if (hand != null && heroId in table.mine) {
            Section("Your hand")
            CardRow(hand.map { CardView(it) }, table, focus, "${info.name} · Your hand")
        } else if (hand != null && hand.isNotEmpty()) {
            // Only a puzzle shows another hero's hand: its hands are open information.
            Section("In hand · open in this puzzle")
            CardRow(hand.map { CardView(it) }, table, focus, "${info.name} · In hand")
        }
        Spacer(Modifier.size(Spacing.md))
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
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/** The person's hand, small (one row per hero they control); tap a card to see it large. */
@Composable
fun YourHand(table: TableUi, focus: CardFocusState) {
    table.mine.forEach { heroId ->
        val hand = table.heroView(heroId)?.hand ?: return@forEach
        if (hand.isEmpty()) return@forEach
        val title = if (table.mine.size > 1) "${table.hero(heroId).name}'s hand" else "Your hand"
        Text(
            title.uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
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
) {
    val captions = cards.map { cardCaption(it, slots) }
    val open = cards.indices.filter { !cards[it].facedown && table.card(cards[it].cardId) != null }
    val viewable = open.map { i ->
        val card = table.card(cards[i].cardId)!!
        FocusCard(card, key = "$title/${card.id}", caption = captions[i].ifEmpty { null })
    }
    ThumbRow(
        cards,
        table,
        slots,
        origin = { card -> Modifier.cardFocusOrigin(focus, "$title/${card.id}") },
        onOpen = { i, _ -> open.indexOf(i).takeIf { it >= 0 }?.let { focus.open(viewable, it, title = title) } },
    )
}

/** Small cards in a row; a tap on a face-up one goes to [onCard] (the caller decides how it is read). */
@Composable
internal fun CardRow(cards: List<CardView>, table: TableUi, onCard: (Card) -> Unit, slots: Boolean = false) {
    ThumbRow(cards, table, slots, origin = { Modifier }, onOpen = { _, card -> onCard(card) })
}

@Composable
private fun ThumbRow(
    cards: List<CardView>,
    table: TableUi,
    slots: Boolean,
    origin: (Card) -> Modifier,
    onOpen: (index: Int, card: Card) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cards.forEachIndexed { i, view ->
            val card = table.card(view.cardId).takeIf { !view.facedown }
            val spoken = listOfNotNull(
                view.slot?.takeIf { slots }?.let { "Turn $it" },
                card?.let(::spokenCard) ?: "Facedown card",
                "Active".takeIf { view.active },
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
                    shape = RoundedCornerShape(6.dp),
                    border = if (view.active) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                    modifier = if (card != null) origin(card) else Modifier,
                ) {
                    TableCard(card, width = 64.dp)
                }
                val caption = cardCaption(view, slots)
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
