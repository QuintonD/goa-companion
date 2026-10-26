package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * A hero's game state on the hero sheet (ADR-044): where the hero is in this turn, the cards of this
 * round on their Turn slots, the discard, the items (icons for everyone, the tucked cards for the
 * hero's own player), where the hero stands. Built only from the engine's view (p.4 open information:
 * a facedown card stays a card back, an enemy's hand and tucked cards are never named) and the turn
 * track (ADR-041), so it can't show more than the table does.
 */

/** Where a hero is in this turn, as the sheet says it. */
enum class HeroTurnState {
    /** Before the reveal: a card is down, facedown (p.7). */
    PLAYED,

    /** Before the reveal: no card down yet. */
    CHOOSING,

    /** Before the reveal: no cards in hand, so the hero passes (p.7). */
    NO_CARDS,

    DONE,
    NOW,
    NEXT,
    LATER,
    PASSED,
    SKIPPED,
}

/**
 * A hero's card this turn: [cards] as the person may see them ([CardView.cardId] null = facedown to
 * them), [initiative] as it counts after the reveal (items and effects, p.7), [printed] the printed
 * initiative of the first faceup card, [place] the order among the heroes with a card, [action] the
 * action chosen.
 */
@Immutable
data class HeroTurn(
    val state: HeroTurnState,
    val cards: List<CardView>,
    val initiative: Int? = null,
    val printed: Int? = null,
    val place: Int? = null,
    val tied: Boolean = false,
    val action: ActionType? = null,
)

/** [hero]'s card this turn, or null outside the play and resolve phases. */
fun heroTurn(table: TableUi, hero: HeroView): HeroTurn? = when (table.view.phase) {
    GamePhase.PLAY_CARDS -> {
        val played = hero.played
        when {
            played != null -> HeroTurn(HeroTurnState.PLAYED, listOf(played) + hero.alsoPlayed)
            hero.handCount == 0 -> HeroTurn(HeroTurnState.NO_CARDS, emptyList())
            else -> HeroTurn(HeroTurnState.CHOOSING, emptyList())
        }
    }

    GamePhase.RESOLVE -> table.track?.slots?.firstOrNull { it.heroId == hero.heroId }?.let { slot ->
        val first = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.cardId?.let(table::card)
        HeroTurn(
            state = when (slot.state) {
                SlotState.DONE -> HeroTurnState.DONE
                SlotState.NOW -> HeroTurnState.NOW
                SlotState.NEXT -> HeroTurnState.NEXT
                SlotState.LATER -> HeroTurnState.LATER
                SlotState.PASSED -> HeroTurnState.PASSED
                SlotState.SKIPPED -> HeroTurnState.SKIPPED
            },
            cards = slot.cards,
            initiative = slot.initiative,
            printed = first?.initiative,
            place = slot.place,
            tied = slot.tied,
            action = slot.action,
        )
    }

    GamePhase.END_OF_ROUND, GamePhase.GAME_OVER -> null
}

/** The state as the word on the card's band (as on the turn track). */
internal fun turnWord(state: HeroTurnState): String = when (state) {
    HeroTurnState.PLAYED -> "FACEDOWN"
    HeroTurnState.CHOOSING -> "CHOOSING"
    HeroTurnState.NO_CARDS -> "PASS"
    HeroTurnState.DONE -> "DONE"
    HeroTurnState.NOW -> "NOW"
    HeroTurnState.NEXT -> "NEXT"
    HeroTurnState.LATER -> "LATER"
    HeroTurnState.PASSED -> "PASS"
    HeroTurnState.SKIPPED -> "OUT"
}

/** The lines beside the card: what the state means, the action, the initiative, the order. */
internal fun turnLines(table: TableUi, hero: HeroView, turn: HeroTurn): List<String> {
    val first = turn.cards.firstOrNull()
    val card = first?.takeIf { !it.facedown }?.cardId?.let(table::card)
    val own = hero.heroId in table.mine
    return buildList {
        add(
            when (turn.state) {
                HeroTurnState.PLAYED -> when {
                    own -> "Played facedown: the others see it at the reveal (p.7)."
                    card != null -> "Played facedown; open in this puzzle."
                    else -> "Played facedown: revealed when everyone has played (p.7)."
                }

                HeroTurnState.CHOOSING -> if (own) {
                    "No card played yet: choose one from your hand."
                } else {
                    "No card played yet."
                }

                HeroTurnState.NO_CARDS -> "No cards in hand: passes this turn (p.7)."

                HeroTurnState.DONE -> "Resolved: on the Turn ${table.view.turn} slot."

                HeroTurnState.NOW -> "Resolving this card now."

                HeroTurnState.NEXT -> "Acts next."

                HeroTurnState.LATER -> "Acts later."

                HeroTurnState.PASSED -> "Passed: no card this turn (p.7)."

                HeroTurnState.SKIPPED -> "The card left the table unresolved (p.10)."
            },
        )
        turn.action?.let { add("Chose ${actionWord(it, card)}.") }
        turn.initiative?.let { counted ->
            val printed = turn.printed?.takeIf { it != counted }?.let { " · printed $it" }.orEmpty()
            add("Initiative $counted$printed")
        }
        val upcoming = turn.state == HeroTurnState.NEXT || turn.state == HeroTurnState.LATER
        turn.place?.takeIf { upcoming }?.let { add("${PlayText.ordinal(it)} to act this turn") }
        if (turn.tied) add("Tied on initiative: the coin or the team decides (p.7).")
    }
}

/** Where the hero is: its Zone, or off the board with when it may come back. */
internal fun heroWhere(table: TableUi, hero: HeroView): String {
    val at = hero.at ?: return "Off the board: may respawn in their Throne Zone before acting on their next card (p.7)."
    return "On the board · ${BoardText.zone(table.map, table.geometry.zoneOf(at))}"
}

/** The item icons grouped by stat, in the engine's (stat) order: "+1 Defense ×2". */
internal fun itemGroups(stats: List<Stat>): List<Pair<Stat, Int>> =
    stats.groupingBy { it }.eachCount().entries.sortedBy { it.key.ordinal }.map { it.key to it.value }

internal fun itemText(stat: Stat, count: Int): String =
    "+1 ${PlayText.stat(stat)}" + if (count > 1) " ×$count" else ""

// ---- Sections ---------------------------------------------------------------------------------

/** This turn: the card large (a card back when it is facedown to the person), its state and initiative. */
@Composable
internal fun ThisTurnSection(table: TableUi, hero: HeroView, focus: CardFocusState) {
    val turn = heroTurn(table, hero) ?: return
    val name = table.hero(hero.heroId).name
    val title = "$name · Round ${table.view.round}, turn ${table.view.turn}"
    HeroSection("This turn · Round ${table.view.round}, turn ${table.view.turn}")
    val lines = turnLines(table, hero, turn)
    val open = turn.cards.mapNotNull { view -> view.cardId?.takeIf { !view.facedown }?.let(table::card) }
    val viewable = open.map { FocusCard(it, key = "$title/${it.id}", caption = lines.first()) }
    val spoken = buildList {
        add(turnWord(turn.state).lowercase().replaceFirstChar { it.uppercase() })
        turn.cards.forEach { view ->
            add(view.cardId?.takeIf { !view.facedown }?.let(table::card)?.name ?: "a facedown card")
        }
        addAll(lines)
    }.joinToString(", ")
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spoken
            if (viewable.isNotEmpty()) {
                onClick(label = "Look at the card") {
                    focus.open(viewable, 0, title = title)
                    true
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (turn.cards.isEmpty()) {
                EmptySlot(EMPTY_TURN_CARD)
            } else {
                turn.cards.forEachIndexed { i, view ->
                    val card = view.cardId?.takeIf { !view.facedown }?.let(table::card)
                    val width = if (i == 0) THIS_TURN_CARD else THIS_TURN_SECOND
                    Surface(
                        onClick = { focus.open(viewable, open.indexOf(card).coerceAtLeast(0), title = title) },
                        enabled = card != null,
                        shape = RoundedCornerShape(8.dp),
                        modifier = if (card != null) Modifier.cardFocusOrigin(focus, "$title/${card.id}") else Modifier,
                    ) {
                        TableCard(card, width = width)
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StateTag(turn.state)
            lines.forEachIndexed { i, line ->
                Text(
                    line,
                    style = if (line.startsWith(
                            "Initiative",
                        )
                    ) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (line.startsWith("Initiative")) FontWeight.SemiBold else null,
                    color = if (i == 0 || line.startsWith("Initiative")) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The state in a word, as the turn track's band (never colour alone). */
@Composable
private fun StateTag(state: HeroTurnState) {
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (state) {
        HeroTurnState.NOW -> scheme.primary to scheme.onPrimary
        HeroTurnState.NEXT -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceVariant to scheme.onSurface
    }
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
 * This round's four Turn slots as on the dashboard (p.7): the card resolved on each (a card resolved
 * facedown stays a back), an empty outline for a turn without one. Tap a card to read it.
 */
@Composable
internal fun RoundSlotsSection(table: TableUi, hero: HeroView, focus: CardFocusState) {
    val name = table.hero(hero.heroId).name
    HeroSection("This round · Turn slots")
    if (hero.resolved.isEmpty()) {
        Quiet("No card resolved yet this round.")
        return
    }
    val title = "$name · Resolved"
    val bySlot = (1..TURNS_IN_ROUND).associateWith { t -> hero.resolved.filter { it.slot == t } }
    val unslotted = hero.resolved.filter { it.slot == null || it.slot !in 1..TURNS_IN_ROUND }
    val shown = bySlot.values.flatten() + unslotted
    val open = shown.mapNotNull { v -> v.cardId?.takeIf { !v.facedown }?.let(table::card) }
    val viewable = shown.mapNotNull { v ->
        val card = v.cardId?.takeIf { !v.facedown }?.let(table::card) ?: return@mapNotNull null
        FocusCard(card, key = "$title/${card.id}", caption = v.slot?.let { "Turn $it" })
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        bySlot.forEach { (turn, cards) ->
            if (cards.isEmpty()) {
                SlotColumn("Turn $turn", "Turn $turn, empty slot", null) { EmptySlot(SLOT_CARD) }
            } else {
                cards.forEach { view ->
                    val card = view.cardId?.takeIf { !view.facedown }?.let(table::card)
                    val spoken = listOfNotNull(
                        "Turn $turn",
                        card?.let(::spokenCard) ?: "facedown card",
                        "Active".takeIf { view.active },
                    ).joinToString(", ")
                    val show = card?.let { c ->
                        { focus.open(viewable, open.indexOf(c).coerceAtLeast(0), title = title) }
                    }
                    SlotColumn(
                        listOfNotNull(
                            "Turn $turn",
                            "Active".takeIf {
                                view.active
                            },
                        ).joinToString(" · "),
                        spoken,
                        show,
                    ) {
                        Surface(
                            onClick = { show?.invoke() },
                            enabled = show != null,
                            shape = RoundedCornerShape(6.dp),
                            border = if (view.active) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                            modifier = if (card !=
                                null
                            ) {
                                Modifier.cardFocusOrigin(focus, "$title/${card.id}")
                            } else {
                                Modifier
                            },
                        ) { TableCard(card, width = SLOT_CARD) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotColumn(caption: String, spoken: String, onOpen: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spoken
            if (onOpen != null) {
                onClick(label = "Look at the card") {
                    onOpen()
                    true
                }
            }
        },
    ) {
        content()
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** No card here: a hollow outline where the card would lie. */
@Composable
private fun EmptySlot(width: Dp) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(CARD_ASPECT)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    )
}

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
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
                Spacer(Modifier.width(Spacing.xs))
                Text(itemText(stat, count), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (hero.items.isNotEmpty()) {
        val name = table.hero(hero.heroId).name
        Quiet("Tucked under the dashboard: only you see which cards (p.4).")
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
    if (hero.markers.isEmpty() && tokens.isEmpty()) return
    HeroSection("Markers and tokens")
    hero.markers.forEach { marker ->
        val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
        Text(
            marker.kind.replaceFirstChar { it.uppercase() } + " marker" + from,
            style = MaterialTheme.typography.bodyMedium,
        )
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
        title.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val TURNS_IN_ROUND = 4
private val THIS_TURN_CARD = 112.dp
private val THIS_TURN_SECOND = 76.dp
private val EMPTY_TURN_CARD = 40.dp
private val SLOT_CARD = 64.dp
private val ITEM_ICON_WIDTH = 40.dp
private val ITEM_ICON_HEIGHT = 30.dp

/** The action that opens a hero's sheet, as TalkBack says it (map piece, hero strip, turn track). */
internal fun showGameState(name: String): String = "Show $name's game state"
