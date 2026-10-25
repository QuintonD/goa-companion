package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.EXTRAS_SPELLBOOK
import dev.quinton.goa.feature.board.play.table.LogLine
import dev.quinton.goa.feature.board.play.table.MomentKind
import dev.quinton.goa.feature.board.play.table.SlotState
import dev.quinton.goa.feature.board.play.table.TableMoment
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.TrackSlot
import dev.quinton.goa.feature.board.play.table.TurnTrack
import dev.quinton.goa.feature.board.play.table.actionWord
import dev.quinton.goa.feature.board.play.table.cardCaption
import dev.quinton.goa.feature.board.play.table.cardDown
import dev.quinton.goa.feature.board.play.table.extrasGroups
import dev.quinton.goa.feature.board.play.table.extrasTitle
import dev.quinton.goa.feature.board.play.table.shownCounters
import dev.quinton.goa.feature.board.play.table.slotCaption
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.play.table.spokenChip
import dev.quinton.goa.feature.board.play.table.spokenSlot
import dev.quinton.goa.feature.board.play.table.ultimateCard
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.play.cards.ItemBadge
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** Turns in a round (p.7). */
private const val TURNS = 4

/**
 * The round at a glance: each team's Life counters, the Wave counters left (per Lane), the round and
 * its four turn pips, and the side of the Tie Breaker coin showing. One VoiceOver line.
 */
@Composable
fun TableHud(table: TableUi, compact: Boolean = false) {
    val view = table.view
    val waves = table.map.lanes.map { lane -> BoardText.lane(table.map, lane.id) to (view.wavesLeft[lane.id] ?: 0) }
    val spoken = buildList {
        Team.entries.forEach {
            add("${BoardText.team(it)} ${BoardText.count(view.lifeCounters[it] ?: 0, "Life counter")}")
        }
        waves.forEach { (lane, left) ->
            add(listOfNotNull(lane, "${BoardText.count(left, "Wave counter")} left").joinToString(" "))
        }
        add("Round ${view.round}, turn ${view.turn} of $TURNS")
        add("Tie Breaker coin shows ${BoardText.team(view.tieBreaker)}")
    }.joinToString(". ")
    Div({
        classes("goa-table-hud")
        if (compact) classes("compact")
        attr("role", "group")
        attr("aria-label", spoken)
    }) {
        Team.entries.forEach { team ->
            Span({ classes("goa-hud-item", teamCss(team)) }) {
                TeamMark(team)
                Icon("heart", IconSize.SMALL)
                Span({ classes("t-numeric") }) { Text("${view.lifeCounters[team] ?: 0}") }
            }
        }
        waves.forEach { (lane, left) ->
            Span({ classes("goa-hud-item", "waves") }) {
                Icon("waves", IconSize.SMALL)
                Span({ classes("t-numeric") }) { Text(listOfNotNull(lane, "$left").joinToString(" ")) }
            }
        }
        Span({ classes("goa-hud-item") }) {
            Span({ classes("t-numeric") }) { Text("R${view.round}") }
            Span({ classes("goa-pips") }) {
                for (t in 1..TURNS) {
                    Span({
                        classes("goa-pip")
                        when {
                            t < view.turn -> classes("done")
                            t == view.turn -> classes("now")
                        }
                    })
                }
            }
        }
        Span({ classes("goa-coin") }) { TeamMark(view.tieBreaker) }
    }
}

/**
 * Every hero's status, one chip each: portrait ringed in team colour, level, coins, cards in hand.
 * Before the reveal in team order; after it in initiative order, numbered. The acting hero's chip
 * is outlined in bronze and marked "Now". A tap opens the hero ([onHero]). [compact]: portraits only.
 */
@Composable
fun HeroStrip(table: TableUi, onHero: (String) -> Unit, compact: Boolean = false) {
    val order = table.initiativeOrder
    val heroes = if (order.isNotEmpty()) {
        order.mapNotNull(table::heroView) + table.view.heroes.filter { it.heroId !in order }
    } else {
        table.view.heroes.sortedBy { it.team.ordinal }
    }
    Div({
        classes("goa-hero-strip")
        if (compact) classes("compact")
    }) {
        heroes.forEach { hero ->
            val place = order.indexOf(hero.heroId).takeIf { it >= 0 }?.plus(1)
            HeroChip(table, hero, place, compact) { onHero(hero.heroId) }
        }
    }
}

@Composable
private fun HeroChip(table: TableUi, hero: HeroView, place: Int?, compact: Boolean, onClick: () -> Unit) {
    val info = table.hero(hero.heroId)
    val acting = table.view.acting == hero.heroId
    val played = table.card(hero.played?.cardId)?.takeIf { hero.played?.facedown != true }
    Button({
        classes("goa-hero-chip")
        if (acting) classes("acting")
        if (compact) classes("compact")
        attr("type", "button")
        attr("aria-label", spokenChip(table, hero, place))
        onClick { onClick() }
    }) {
        Span({ classes("goa-chip-portrait") }) {
            HeroPortrait(info, hero.team, if (compact) 38 else 34, dim = hero.at == null)
            place?.let { Span({ classes("goa-order-badge") }) { Text("$it") } }
            if (cardDown(table, hero)) Span({ classes("goa-card-down") })
        }
        if (!compact) {
            Span({ classes("goa-chip-text") }) {
                Span({ classes("goa-chip-name") }) {
                    TeamMark(hero.team)
                    Span({ classes("name") }) { Text(info.name) }
                    if (acting) Span({ classes("now") }) { Text(" · Now") }
                }
                Span({ classes("goa-chip-stats", "t-numeric") }) {
                    Span { Text("Lv ${hero.level}") }
                    Span {
                        Icon("coin", IconSize.SMALL)
                        Text("${hero.coins}")
                    }
                    Span {
                        Icon("cards", IconSize.SMALL)
                        Text("${hero.handCount}")
                    }
                    (hero.initiative ?: played?.initiative)?.let {
                        Span {
                            Icon("g_initiative", IconSize.SMALL)
                            Text("$it")
                        }
                    }
                }
            }
        }
    }
}

/**
 * The turn at a glance after the reveal (ADR-041): every played card in acting order, each official
 * card with its initiative, its hero's portrait and name with the team's shape, and DONE / NOW /
 * NEXT / PASS on it (words, not colour alone). A tap reads the card in [focus] (swiping through the
 * turn's cards); the portrait opens the hero ([onHero]). [compact]: smaller cards, no names.
 */
@Composable
fun TurnTrackRow(
    table: TableUi,
    track: TurnTrack,
    onHero: (String) -> Unit,
    focus: CardFocusState,
    compact: Boolean = false,
) {
    val viewable = track.slots.flatMap { slot ->
        slot.cards.mapNotNull { view ->
            val card = view.cardId?.takeIf { !view.facedown }?.let(table::card) ?: return@mapNotNull null
            FocusCard(card, key = "track/${slot.heroId}/${card.id}", caption = slotCaption(table, slot, card))
        }
    }
    Div({
        classes("goa-turn-track")
        if (compact) classes("compact")
        attr("role", "list")
        attr("aria-label", "Round ${track.round}, turn ${track.turn}: the cards in the order the heroes act")
    }) {
        track.slots.forEach { slot ->
            val first = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.cardId?.let(table::card)
            val index = viewable.indexOfFirst { it.key == "track/${slot.heroId}/${first?.id}" }
            TrackSlotView(table, slot, compact, first, onOpen = {
                if (index >=
                    0
                ) {
                    focus.open(viewable, index, title = "Round ${track.round} · Turn ${track.turn}")
                } else {
                    onHero(slot.heroId)
                }
            }, onHero = { onHero(slot.heroId) })
        }
    }
}

@Composable
private fun TrackSlotView(
    table: TableUi,
    slot: TrackSlot,
    compact: Boolean,
    card: Card?,
    onOpen: () -> Unit,
    onHero: () -> Unit,
) {
    val info = table.hero(slot.heroId)
    Div({
        classes("goa-track-slot", slot.state.name.lowercase())
        attr("role", "listitem")
    }) {
        Button({
            classes("goa-track-card")
            attr("type", "button")
            attr("aria-label", spokenSlot(table, slot) + ". Look at the card")
            onClick { onOpen() }
        }) {
            val width = if (compact) 34 else 46
            if (slot.cards.isEmpty()) {
                Span({ classes("goa-track-pass") }) { Text("—") }
            } else {
                CardImage(card, width)
            }
            Span({ classes("goa-track-band") }) {
                Text(
                    when (slot.state) {
                        SlotState.DONE -> "✓ DONE"
                        SlotState.NOW -> "NOW"
                        SlotState.NEXT -> "NEXT"
                        SlotState.LATER -> slot.place?.let { PlayText.ordinal(it).uppercase() } ?: ""
                        SlotState.PASSED -> "PASS"
                        SlotState.SKIPPED -> "SKIP"
                    },
                )
            }
            slot.initiative?.let {
                Span({ classes("goa-track-init", "t-numeric") }) { Text(if (slot.tied) "=$it" else "$it") }
            }
        }
        Button({
            classes("goa-track-hero")
            attr("type", "button")
            attr("aria-label", "Read about ${info.name}")
            onClick { onHero() }
        }) {
            HeroPortrait(info, slot.team, if (compact) 18 else 20, dim = slot.offBoard)
            if (!compact) {
                Span({ classes("goa-track-name") }) { Text(info.name) }
            }
        }
        slot.action?.takeIf { !compact }?.let { action ->
            Span({ classes("goa-track-action", "t-body-small") }) { Text(actionWord(action, card)) }
        }
    }
}

/**
 * A moment of the turn the table states in words (ADR-041): "All cards revealed · Wasp acts first",
 * "Turn 1 is over" with that turn's cards (tap one to read it), "End of round 1".
 */
@Composable
fun MomentCard(moment: TableMoment, table: TableUi, focus: CardFocusState) {
    Div({
        classes("goa-moment", moment.kind.name.lowercase())
        attr("role", "status")
    }) {
        P({ classes("t-title-small") }) { Text(moment.title) }
        P({ classes("t-body-medium") }) { Text(moment.text) }
        if (moment.kind == MomentKind.TURN_OVER && moment.cards.isNotEmpty()) {
            val cards = moment.cards.flatMap { slot ->
                slot.cards.mapNotNull { v -> table.card(v.cardId.takeIf { !v.facedown }) }
            }
            Div({ classes("goa-moment-cards") }) {
                moment.cards.forEach { slot ->
                    val card = slot.cards.firstOrNull()?.takeIf { !it.facedown }?.let { table.card(it.cardId) }
                    Button({
                        classes("goa-thumb")
                        attr("type", "button")
                        attr(
                            "aria-label",
                            "${table.hero(slot.heroId).name}: ${card?.let(::spokenCard) ?: "a facedown card"}",
                        )
                        onClick { if (card != null) focus.show(cards, cards.indexOf(card), moment.title) }
                    }) { CardImage(card, 36) }
                }
            }
        }
    }
}

/**
 * What just happened: the latest lines, newest last and in full contrast; lines at [strong] (a defeat,
 * a push, a level up…) bold.
 */
@Composable
fun RecentEvents(lines: List<String>, strong: Set<Int> = emptySet()) {
    if (lines.isEmpty()) return
    val skip = (lines.size - RECENT).coerceAtLeast(0)
    Ul({ classes("goa-recent") }) {
        lines.drop(skip).forEachIndexed { i, line ->
            Li({
                if ((i + skip) in strong) classes("strong")
                if (i == minOf(lines.size, RECENT) - 1) classes("latest")
            }) { Text(line) }
        }
    }
}

private const val RECENT = 4

/** The person's hand, small (one row per hero they control); tap a card to see it large. */
@Composable
fun YourHand(table: TableUi, focus: CardFocusState) {
    table.mine.forEach { heroId ->
        val hand = table.heroView(heroId)?.hand ?: return@forEach
        if (hand.isEmpty()) return@forEach
        val title = if (table.mine.size > 1) "${table.hero(heroId).name}'s hand" else "Your hand"
        Overline(title)
        CardRow(hand.map { CardView(it) }, table, focus, title)
    }
}

/**
 * Small cards in a row; a facedown one shows its back. Tap a face-up one to read it in [focus],
 * swiping through the row's other face-up cards ([title] heads the viewer). [slots]: captions say the
 * Turn slot.
 */
@Composable
fun CardRow(cards: List<CardView>, table: TableUi, focus: CardFocusState, title: String, slots: Boolean = false) {
    val open = cards.indices.filter { !cards[it].facedown && table.card(cards[it].cardId) != null }
    val viewable = open.map { i ->
        FocusCard(table.card(cards[i].cardId)!!, caption = cardCaption(cards[i], slots).ifEmpty { null })
    }
    Div({ classes("goa-card-row") }) {
        cards.forEachIndexed { i, view ->
            val card = table.card(view.cardId).takeIf { !view.facedown }
            val caption = cardCaption(view, slots)
            Div({ classes("goa-card-cell") }) {
                Button({
                    classes("goa-thumb")
                    if (view.active) classes("active")
                    attr("type", "button")
                    attr(
                        "aria-label",
                        listOfNotNull(
                            caption.ifEmpty {
                                null
                            },
                            card?.let(::spokenCard) ?: "Facedown card",
                        ).joinToString(", "),
                    )
                    if (card == null) attr("disabled", "")
                    onClick { open.indexOf(i).takeIf { it >= 0 }?.let { focus.open(viewable, it, title = title) } }
                }) { CardImage(card, 64) }
                if (caption.isNotEmpty()) Span({ classes("t-body-small", "goa-quiet") }) { Text(caption) }
            }
        }
    }
}

/**
 * One hero in full (open information, p.4), for a sheet: level, coins, cards in hand, items, markers,
 * Active effects, the card played this turn, the resolved Turn slots, the discard, the Ultimate, the
 * spellbook (ADR-040), and its hand when it is the person's (any hero's in a puzzle).
 */
@Composable
fun HeroDetail(table: TableUi, heroId: String, focus: CardFocusState) {
    val hero = table.heroView(heroId) ?: return
    val info = table.hero(heroId)
    Div({ classes("goa-hero-detail") }) {
        Div({ classes("goa-hero-head") }) {
            HeroPortrait(info, hero.team, 56)
            Div {
                H2({ classes("t-title-large") }) { Text(info.name) }
                P({ classes("t-label-large", "goa-quiet") }) {
                    TeamMark(hero.team)
                    Text(" ${BoardText.team(hero.team)} · ${table.controllerLabel(hero)}")
                }
            }
        }
        Div({ classes("goa-facts") }) {
            Fact("Level", "${hero.level}")
            Fact("Coins", "${hero.coins}")
            Fact("In hand", "${hero.handCount}")
            if (hero.ultimate) Fact("Ultimate", "Active")
            if (hero.at == null) Fact("Board", "Off the board")
            shownCounters(hero).forEach { (name, value) -> Fact(PlayText.counter(name), "$value") }
        }
        if (hero.items.isNotEmpty()) {
            Overline("Items · p.16")
            hero.items.mapNotNull(table::card).forEach { card ->
                P({ classes("t-body-medium") }) {
                    card.item?.let { ItemBadge(it) }
                    Text(card.item?.let { "+1 ${PlayText.stat(it)} · ${card.name}" } ?: card.name)
                }
            }
        } else if (hero.itemStats.isNotEmpty()) {
            Overline("Items · p.16")
            P({ classes("t-body-medium") }) {
                hero.itemStats.forEach { ItemBadge(it) }
                Text(hero.itemStats.joinToString(", ") { "+1 ${PlayText.stat(it)}" })
            }
        }
        if (hero.markers.isNotEmpty()) {
            Overline("Markers")
            hero.markers.forEach { marker ->
                val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
                P({ classes("t-body-medium") }) { Text(marker.kind.replaceFirstChar { it.uppercase() } + from) }
            }
        }
        if (hero.activeEffects.isNotEmpty()) {
            Overline("Active effects")
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
            Overline("Played this turn")
            CardRow(listOf(played) + hero.alsoPlayed, table, focus, "${info.name} · Played this turn")
        }
        if (hero.resolved.isNotEmpty()) {
            Overline("Resolved · Turn slots")
            CardRow(hero.resolved, table, focus, "${info.name} · Resolved", slots = true)
        }
        if (hero.discard.isNotEmpty()) {
            Overline("Discarded")
            CardRow(hero.discard, table, focus, "${info.name} · Discarded")
        }
        ultimateCard(table, hero)?.let { card ->
            Overline("Ultimate · p.16")
            CardRow(listOf(CardView(card.id)), table, focus, "${info.name} · Ultimate")
        }
        ExtrasSection(table, hero, focus)
        val hand = hero.hand
        if (hand != null && heroId in table.mine) {
            Overline("Your hand")
            CardRow(hand.map { CardView(it) }, table, focus, "${info.name} · Your hand")
        } else if (hand != null && hand.isNotEmpty()) {
            Overline("In hand · open in this puzzle")
            CardRow(hand.map { CardView(it) }, table, focus, "${info.name} · In hand")
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Div({ classes("goa-fact") }) {
        Span({ classes("t-overline", "goa-table-overline") }) { Text(label) }
        Span({ classes("t-title-medium") }) { Text(value) }
    }
}

/**
 * A hero's extra cards (Gydion's spellbook, ADR-040), nothing for a hero without any: each group (in
 * the spellbook, named to Gydion's player only; cast; not yet added) as landscape thumbnails; tap one
 * to read it, swiping through its group.
 */
@Composable
fun ExtrasSection(table: TableUi, hero: HeroView, focus: CardFocusState) {
    if (hero.extras.isEmpty()) return
    val name = table.hero(hero.heroId).name
    Overline(extrasTitle(table, hero))
    extrasGroups(table, hero).forEach { group ->
        val title = "$name · ${group.title}"
        Div({ classes("goa-extras-group") }) {
            P({ classes("t-title-small") }) { Text("${group.title} · ${group.count}") }
            group.note?.let { Quiet(it) }
            val cards = group.cards
            when {
                cards == null -> Unit

                cards.isEmpty() && group.place != EXTRAS_SPELLBOOK -> Quiet("None yet")

                else -> Div({ classes("goa-extras-grid") }) {
                    cards.forEachIndexed { i, card ->
                        Button({
                            classes("goa-thumb")
                            attr("type", "button")
                            attr("aria-label", spokenCard(card))
                            onClick { focus.show(cards, i, title) }
                        }) { CardImage(card, 66) }
                    }
                }
            }
        }
    }
}

/** The event log: every event of the game in the engine's words, rounds and turns as headings. */
@Composable
fun EventLog(lines: List<LogLine>) {
    Div({
        classes("goa-event-log")
        attr("role", "log")
    }) {
        lines.forEach { line ->
            if (line.heading) {
                P({
                    classes("t-overline", "goa-table-overline")
                    attr("role", "heading")
                    attr("aria-level", "3")
                }) { Text(line.text) }
            } else {
                P({ classes("t-body-medium") }) { Text(line.text) }
            }
        }
    }
}
