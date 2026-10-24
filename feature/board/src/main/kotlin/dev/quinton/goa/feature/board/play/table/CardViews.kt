package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardText

/** A printed card's proportions (63 × 88 mm). */
internal const val CARD_ASPECT = 0.716f

/** "Rogue Wave · Red Tier II · Initiative 8". */
internal fun cardSummary(card: Card): String = buildList {
    add(card.name)
    add(cardKind(card))
    card.initiative?.let { add("Initiative $it") }
}.joinToString(" · ")

internal fun cardKind(card: Card): String {
    val color = when (card.color) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        CardColor.RED -> "Red"
        CardColor.GREEN -> "Green"
        CardColor.BLUE -> "Blue"
        CardColor.PURPLE -> "Ultimate"
        CardColor.OTHER -> "Card"
    }
    val tier = card.tier?.takeIf { card.color != CardColor.PURPLE }?.let { " Tier ${"I".repeat(it.coerceIn(1, 3))}" }
    return color + tier.orEmpty()
}

/**
 * A card's official image (cropped and resized only, DESIGN.md *Asset policy*), [width] wide, in a
 * frame with the card's colour; its name stands in while the image loads or when there is none. A
 * facedown or hidden card ([card] null) is the card back: a bronze-edged blank.
 */
@Composable
internal fun CardImage(card: Card?, width: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val shape = RoundedCornerShape(width * 0.07f)
    val edge = when {
        selected -> MaterialTheme.colorScheme.secondary
        card != null -> cardColor(card.color)
        else -> GoaTheme.colors.engravedBorder
    }
    var loaded by remember(card?.image) { mutableStateOf(false) }
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT)
            .clip(shape)
            .background(if (card == null) GoaTheme.colors.medallion else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 3.dp else 1.5.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (card == null) {
            Text(
                "Facedown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            if (!loaded) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp),
                )
            }
            card.image?.let { path ->
                AsyncImage(
                    model = assetUrl(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { loaded = true },
                )
            }
        }
    }
}

/**
 * A row of cards to read or choose from. [selectedId] is outlined in teal and called out; a tap
 * calls [onClick] with the card's id. Each card is one TalkBack node naming the card and the action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardRow(
    cards: List<Pair<String, Card?>>,
    width: Dp,
    onClick: (String) -> Unit,
    actionLabel: String,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    caption: (String) -> String? = { null },
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        cards.forEach { (id, card) ->
            val chosen = id == selectedId
            val note = caption(id)
            Column(
                Modifier
                    .width(width)
                    .clip(RoundedCornerShape(8.dp))
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(card?.let(::cardSummary) ?: "Facedown card", note)
                            .joinToString(". ")
                        role = Role.Button
                        if (chosen) selected = true
                        onClick(label = actionLabel) {
                            onClick(id)
                            true
                        }
                    }
                    .then(Modifier.padding(0.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(onClick = { onClick(id) }, shape = RoundedCornerShape(8.dp), color = Color0) {
                    CardImage(card, width, selected = chosen)
                }
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent

/** A card in full: its image as large as fits and its text with the game icons (the card reader). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardReader(card: Card, modifier: Modifier = Modifier, note: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            cardKind(card).uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(card.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CardImage(card, 240.dp, Modifier.widthIn(max = 240.dp).clearAndSetSemantics {})
        }
        CardValuesRow(card)
        if (card.text.isNotBlank()) CardTextView(card.text, style = MaterialTheme.typography.bodyLarge)
        card.item?.let {
            Text(
                "As an item: +1 ${TableText.stat(it)} (p.16)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Initiative, Attack, Defense, Movement, Range, Radius as printed, each with its icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardValuesRow(card: Card, modifier: Modifier = Modifier) {
    val v = card.values
    val values = listOfNotNull(
        card.initiative?.let { Stat.INITIATIVE to it },
        v.attack?.let { Stat.ATTACK to it },
        v.defense?.let { Stat.DEFENSE to it },
        v.movement?.let { Stat.MOVEMENT to it },
        v.range?.let { Stat.RANGE to it },
        v.radius?.let { Stat.RADIUS to it },
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        values.forEach { (stat, value) ->
            val plus = if (stat in v.plus) "+" else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${TableText.stat(stat)} $value$plus"
                },
            ) {
                Icon(
                    GameIcons.forStat(stat).vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(scaledIconSize(18.dp)),
                )
                Spacer(Modifier.width(2.dp))
                Text("$value$plus", style = GoaTextStyles.numeric)
            }
        }
    }
}

/** Item icons in a row: one per item (p.16). */
@Composable
internal fun ItemIcons(items: List<Stat>, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    if (items.isEmpty()) return
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        items.forEach { stat ->
            Icon(
                GameIcons.forStat(stat).vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(scaledIconSize(size, maxScale = 1.4f)),
            )
        }
    }
}

/** The small portrait in the team's ring, with the team's shape beside it in lists. */
@Composable
internal fun HeroFace(hero: TableHero, size: Dp, modifier: Modifier = Modifier) {
    val color = teamColor(hero.team)
    HeroPortrait(
        heroId = hero.id,
        portrait = hero.portrait,
        contentDescription = null,
        shape = CircleShape,
        framed = false,
        modifier = modifier
            .size(size)
            .border(
                if (hero.acting) 3.dp else 2.dp,
                if (hero.acting) MaterialTheme.colorScheme.primary else color,
                CircleShape,
            ),
    )
}

/** What TalkBack reads for a hero's dashboard. */
internal fun heroSummary(hero: TableHero): String = buildList {
    add("${hero.name}, ${BoardText.team(hero.team)}, ${hero.controller}")
    add("level ${hero.level}")
    add(BoardText.count(hero.coins, "coin"))
    add(BoardText.count(hero.handCount, "card") + " in hand")
    if (hero.items.isNotEmpty()) add("items ${TableText.items(hero.items)}")
    if (hero.markers.isNotEmpty()) add("markers ${hero.markers.joinToString(", ") { it.kind }}")
    if (hero.activeEffects.isNotEmpty()) add("active ${hero.activeEffects.joinToString(", ") { it.name }}")
    if (hero.at == null) add("off the board")
    if (hero.acting) add("acting now")
}.joinToString(", ")

/**
 * A hero's dashboard in full (open information, p.4): level, coins, cards in hand, items, markers
 * and Active effects, then this turn's played card(s), the resolved cards by Turn slot, the discard
 * and, for your own hero, the tucked item cards. Tap a card to read it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroDetails(hero: TableHero, onRead: (Card) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroFace(hero, 56.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(hero.team, markSize = scaledIconSize(10.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        hero.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    "${BoardText.team(hero.team)} · ${hero.controller}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Fact("Level", hero.level.toString())
            Fact("Coins", hero.coins.toString())
            Fact("In hand", hero.handCount.toString())
            if (hero.ultimate) Fact("Ultimate", "unlocked")
            hero.counters.forEach { (name, value) -> Fact(name, value.toString()) }
        }
        if (hero.items.isNotEmpty()) {
            Section("Items · p.16") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcons(hero.items, size = 18.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(TableText.items(hero.items), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (hero.markers.isNotEmpty()) {
            Section("Markers") {
                Text(
                    hero.markers.joinToString(", ") { m -> m.kind.replace('_', ' ') },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (hero.activeEffects.isNotEmpty()) {
            Section("Active effects · p.17") {
                CardRow(hero.activeEffects.map { it.id to it }, 72.dp, onClick = { id ->
                    hero.activeEffects.firstOrNull { it.id == id }?.let(onRead)
                }, actionLabel = "Read")
            }
        }
        CardSection("Played this turn", hero.played, onRead, caption = { null })
        CardSection("Resolved · Turn slots", hero.resolved, onRead) { card -> card.slot?.let { "Turn $it" } }
        CardSection("Discard", hero.discard, onRead) { null }
        if (hero.ownItems.isNotEmpty()) {
            Section("Your item cards") {
                CardRow(hero.ownItems.map { it.id to it }, 72.dp, onClick = { id ->
                    hero.ownItems.firstOrNull { it.id == id }?.let(onRead)
                }, actionLabel = "Read")
            }
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    cards: List<TableCard>,
    onRead: (Card) -> Unit,
    caption: (TableCard) -> String?,
) {
    if (cards.isEmpty()) return
    Section(title) {
        CardRow(
            cards.mapIndexed { i, c -> (c.cardId ?: "hidden-$i") to c.card },
            72.dp,
            onClick = { id -> cards.firstOrNull { it.cardId == id }?.card?.let(onRead) },
            actionLabel = "Read",
            caption = { id ->
                val c = cards.firstOrNull { (it.cardId ?: "") == id }
                listOfNotNull(
                    c?.let(caption),
                    "Active".takeIf {
                        c?.active == true
                    },
                ).joinToString(" · ").ifEmpty { null }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title.uppercase(),
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tinted container for a note in the panel (the computer's move, the coach). */
@Composable
internal fun NoteSurface(
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(Spacing.md)) { content() }
    }
}

/** Fills the height it is given (for sheets). */
internal val SheetFill = Modifier.fillMaxHeight(0.9f)
