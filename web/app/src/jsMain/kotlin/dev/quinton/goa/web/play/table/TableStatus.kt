package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.DashboardSlot
import dev.quinton.goa.feature.board.play.table.EXTRAS_SPELLBOOK
import dev.quinton.goa.feature.board.play.table.HeroTurn
import dev.quinton.goa.feature.board.play.table.HeroTurnState
import dev.quinton.goa.feature.board.play.table.LogLine
import dev.quinton.goa.feature.board.play.table.MomentKind
import dev.quinton.goa.feature.board.play.table.SlotState
import dev.quinton.goa.feature.board.play.table.TableMoment
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.TrackSlot
import dev.quinton.goa.feature.board.play.table.TurnTrack
import dev.quinton.goa.feature.board.play.table.cardCaption
import dev.quinton.goa.feature.board.play.table.cardDown
import dev.quinton.goa.feature.board.play.table.dashboardSlots
import dev.quinton.goa.feature.board.play.table.extrasGroups
import dev.quinton.goa.feature.board.play.table.extrasTitle
import dev.quinton.goa.feature.board.play.table.heroTurn
import dev.quinton.goa.feature.board.play.table.heroWhere
import dev.quinton.goa.feature.board.play.table.itemGroups
import dev.quinton.goa.feature.board.play.table.shownCounters
import dev.quinton.goa.feature.board.play.table.slotBand
import dev.quinton.goa.feature.board.play.table.slotCaption
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.feature.board.play.table.spokenChip
import dev.quinton.goa.feature.board.play.table.spokenSlot
import dev.quinton.goa.feature.board.play.table.turnLines
import dev.quinton.goa.feature.board.play.table.turnWord
import dev.quinton.goa.feature.board.play.table.ultimateCard
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.play.ui.TieBreakerCoin
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** Turns in a round (p.7). */
private const val TURNS = 4
private const val TRACK_CARD = 64
private const val SLOT_CARD = 72
private const val SHEET_CARD = 92
private const val DASHBOARD_HERO = "tigerclaw"
private const val NAME_CQW = 2.55
private const val PLATE_CQW = 29.0
private const val CINZEL_EM = 0.6

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
        if (waves.size > 1) classes("two-lanes")
        attr("role", "group")
        attr("aria-label", spoken)
    }) {
        Team.entries.forEach { team ->
            val left = view.lifeCounters[team] ?: 0
            Span({ classes("goa-hud-item", teamCss(team)) }) {
                TeamMark(team)
                LookImg("official/ui/life-${teamCss(team)}${if (left == 0) "-spent" else ""}.webp", "goa-hud-counter")
                Span({ classes("t-numeric") }) { Text("$left") }
            }
        }
        waves.forEach { (lane, left) ->
            Span({ classes("goa-hud-item", "waves") }) {
                LookImg("official/ui/wave${if (left == 0) "-spent" else ""}.webp", "goa-hud-counter")
                // Two Lanes: "L1 3" fits both on the row; VoiceOver hears "Lane 1".
                lane?.let { Span({ classes("goa-hud-lane") }) { Text(it.replace("Lane ", "L")) } }
                Span({ classes("t-numeric") }) { Text("$left") }
            }
        }
        Span({ classes("goa-hud-item", "round") }) {
            Span({ classes("goa-hud-round") }) { Text("R${view.round}") }
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
        TieBreakerCoin(view.tieBreaker)
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
            HeroPortrait(info, hero.team, if (compact) 38 else 32, dim = hero.at == null)
            Span({ classes("goa-chip-badge") }) { TeamMark(hero.team) }
            if (!compact) Span({ classes("goa-chip-hand", "t-numeric") }) { Text("${hero.handCount}") }
            place?.let { Span({ classes("goa-order-badge") }) { Text("$it") } }
            if (cardDown(table, hero)) Span({ classes("goa-card-down") })
        }
        if (!compact) {
            Span({ classes("goa-chip-text") }) {
                Span({ classes("goa-chip-name") }) {
                    Span({ classes("name") }) { Text(info.name) }
                    if (acting) Span({ classes("now") }) { Text(" · Now") }
                }
                Span({ classes("goa-chip-stats", "t-numeric") }) {
                    Span { Text("Lv${hero.level}") }
                    Span {
                        LookImg("official/ui/coin.webp", "goa-chip-coin")
                        Text("${hero.coins}")
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
            val width = if (compact) 34 else TRACK_CARD
            if (slot.cards.isEmpty()) {
                Span({ classes("goa-track-pass") }) { Text("—") }
            } else {
                // A second card (Emmitt's) peeks behind the first, as on the phone.
                slot.cards.getOrNull(1)?.let { second ->
                    Span({
                        classes("goa-track-second")
                        attr("aria-hidden", "true")
                    }) { CardImage(second.cardId?.takeIf { !second.facedown }?.let(table::card), width, backOf = slot.heroId) }
                }
                CardImage(card, width, backOf = slot.heroId)
            }
            // The phone's band: DONE (with a tick), NOW, NEXT, PASS, OUT; none for the later ones.
            slotBand(slot.state)?.let { band ->
                Span({ classes("goa-track-band") }) {
                    Text(if (slot.state == SlotState.DONE) "✓ $band" else band)
                }
            }
            slot.initiative?.let {
                Span({ classes("goa-track-init", "t-numeric") }) { Text(if (slot.tied) "=$it" else "$it") }
            }
            // The action chosen, as the game's icon on the art (the slot's label says it in words).
            slot.action?.let { action ->
                Span({ classes("goa-track-action") }) { Icon(actionIconName(action, card), IconSize.SMALL) }
            }
        }
        Button({
            classes("goa-track-hero")
            attr("type", "button")
            attr("aria-label", "Read about ${info.name}")
            onClick { onHero() }
        }) {
            HeroPortrait(info, slot.team, if (compact) 18 else 28, dim = slot.offBoard)
            if (!compact) {
                TeamMark(slot.team)
                Span({ classes("goa-track-name") }) { Text(info.name) }
            }
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
        Div({ classes("goa-text-box", "gold") }) {
            Span({ classes("cap") }) { Text(moment.title) }
            P { Text(moment.text) }
        }
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
fun CardRow(
    cards: List<CardView>,
    table: TableUi,
    focus: CardFocusState,
    title: String,
    slots: Boolean = false,
    cardWidth: Int = 64,
) {
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
                }) { CardImage(card, cardWidth) }
                if (caption.isNotEmpty()) Span({ classes("t-body-small", "goa-quiet") }) { Text(caption) }
            }
        }
    }
}

/**
 * One hero in full (open information, p.4), for a sheet, on the real dashboard as on the phone (mock-up
 * 05): who plays them, level, coins, where they stand; this round's cards lying in the frame's Turn slots
 * with their states (a card facedown to the person stays a back), what this turn's state means; the items
 * as icons, the Ultimate and the hand (the person's own, any hero's in a puzzle, otherwise card backs),
 * the discard, Active effects, markers, the spellbook (ADR-040).
 */
@Composable
fun HeroDetail(table: TableUi, heroId: String, focus: CardFocusState) {
    val hero = table.heroView(heroId) ?: return
    val info = table.hero(heroId)
    Div({ classes("goa-hero-detail") }) {
        Div({ classes("goa-hero-head") }) {
            Span({ classes("goa-chip-portrait") }) {
                HeroPortrait(info, hero.team, 44, dim = hero.at == null)
                Span({ classes("goa-chip-badge") }) { TeamMark(hero.team) }
            }
            Div {
                P({ classes("t-label-large", "goa-quiet") }) {
                    TeamMark(hero.team)
                    Text(" ${BoardText.team(hero.team)} · ${table.controllerLabel(hero)}")
                }
                P({ classes("t-body-small", "goa-hero-where") }) {
                    Text("Level ${hero.level} · ")
                    LookImg("official/ui/coin.webp", "goa-chip-coin")
                    Text(" ${BoardText.count(hero.coins, "coin")} · ")
                    Span({ if (hero.at == null) classes("off") }) { Text(heroWhere(table, hero)) }
                }
            }
        }
        HeroDashboard(table, hero, focus)
        heroTurn(table, hero)?.let { turn -> ThisTurn(table, hero, turn) }
        ItemsSection(table, hero, focus)
        val counters = shownCounters(hero)
        if (counters.isNotEmpty()) {
            Div({ classes("goa-facts") }) {
                counters.forEach { (name, value) -> Fact(PlayText.counter(name), "$value") }
            }
        }
        UltimateAndHand(table, hero, focus)
        if (hero.discard.isNotEmpty()) {
            Overline("Discard · ${hero.discard.size}")
            CardRow(hero.discard, table, focus, "${info.name} · Discarded")
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
        if (hero.markers.isNotEmpty()) {
            Overline("Markers")
            hero.markers.forEach { marker ->
                val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
                P({ classes("t-body-medium") }) { Text(marker.kind.replaceFirstChar { it.uppercase() } + from) }
            }
        }
        ExtrasSection(table, hero, focus)
    }
}

/**
 * The hero's real dashboard (rulebook p.16): the frame over the hero's art (the rulebook's for Tigerclaw,
 * the portrait cropped and darkened toward the edges for the others), "Hero the Title" on its big plate,
 * "Turn n" on the small ones, the round's cards in its slots (Emmitt's second one peeking behind the
 * first). Positions come from the frame (goa-look-table.css), as fractions of its 821 × 416.
 */
@Composable
private fun HeroDashboard(table: TableUi, hero: HeroView, focus: CardFocusState) {
    val info = table.hero(hero.heroId)
    val slots = dashboardSlots(table, hero)
    val title = "${info.name} · Round ${table.view.round}"
    val viewable = slots.flatMap { slot ->
        slot.cards.mapNotNull { view ->
            val card = view.cardId?.takeIf { !view.facedown }?.let(table::card) ?: return@mapNotNull null
            FocusCard(card, key = "$title/${card.id}", caption = "Turn ${slot.turn}")
        }
    }
    Div({ classes("goa-dashboard") }) {
        val art = if (hero.heroId == DASHBOARD_HERO) "official/ui/dashboard-$DASHBOARD_HERO.webp" else null
        when {
            art != null -> LookImg(art, "goa-dashboard-art")

            info.portrait != null -> {
                LookImg(info.portrait!!, "goa-dashboard-portrait")
                Span({ classes("goa-dashboard-shade") })
            }
        }
        LookImg("official/ui/dashboard-frame.webp", "goa-dashboard-frame")
        val name = listOf(info.name, info.title).filter { it.isNotBlank() }.joinToString(" ")
        Span({
            classes("goa-dashboard-name")
            attr("aria-hidden", "true")
            // The big plate is about a third of the frame: a long title gets smaller rather than cut.
            style { property("font-size", "${minOf(NAME_CQW, PLATE_CQW / (name.length * CINZEL_EM))}cqw") }
        }) { Text(name) }
        slots.forEach { slot ->
            Span({
                classes("goa-dashboard-turn", "t${slot.turn}")
                attr("aria-hidden", "true")
            }) { Text("Turn ${slot.turn}") }
            DashboardSlotView(hero.heroId, table, slot, focus, title, viewable)
        }
    }
}

@Composable
private fun DashboardSlotView(
    heroId: String,
    table: TableUi,
    slot: DashboardSlot,
    focus: CardFocusState,
    title: String,
    viewable: List<FocusCard>,
) {
    val faces = slot.cards.map { view -> view.cardId?.takeIf { !view.facedown }?.let(table::card) }
    val first = faces.firstNotNullOfOrNull { it }
    val spoken = buildList {
        add("Turn ${slot.turn}")
        if (slot.cards.isEmpty() && slot.word == null) add("empty slot")
        slot.word?.let { add(it.lowercase()) }
        faces.forEach { add(it?.let(::spokenCard) ?: "facedown card") }
    }.joinToString(", ")
    Button({
        classes("goa-dashboard-slot", "t${slot.turn}")
        if (slot.now) classes("now")
        if (slot.next) classes("next")
        if (slot.stepped) classes("stepped")
        if (slot.cards.isEmpty()) classes("empty")
        attr("type", "button")
        attr("aria-label", spoken)
        if (first == null) attr("disabled", "")
        onClick {
            first?.let { card ->
                val at = viewable.indexOfFirst { it.key == "$title/${card.id}" }.coerceAtLeast(0)
                focus.open(viewable, at, title = title)
            }
        }
    }) {
        faces.getOrNull(1)?.let { second -> Span({ classes("second") }) { CardImage(second, SLOT_CARD, "fluid", backOf = heroId) } }
        if (slot.cards.size > 1 && faces[1] == null) Span({ classes("second") }) { CardImage(null, SLOT_CARD, "fluid", backOf = heroId) }
        if (slot.cards.isNotEmpty()) CardImage(faces[0], SLOT_CARD, "fluid", backOf = heroId)
        slot.word?.let { Span({ classes("band") }) { Text(it) } }
    }
}

/** What this turn's state means, in lines: the card itself lies on the dashboard. */
@Composable
private fun ThisTurn(table: TableUi, hero: HeroView, turn: HeroTurn) {
    Overline("This turn · Round ${table.view.round}, turn ${table.view.turn}")
    Span({
        classes("goa-turn-state")
        if (turn.state == HeroTurnState.NOW) classes("now")
        if (turn.state == HeroTurnState.NEXT) classes("next")
    }) { Text(turnWord(turn.state)) }
    turnLines(table, hero, turn).forEach { line ->
        P({ classes(if (line.startsWith("Initiative")) "t-title-medium" else "t-body-medium") }) { Text(line) }
    }
}

/** Items (p.16): the official icon of each stat with what they add; the tucked cards for their own player (p.4). */
@Composable
private fun ItemsSection(table: TableUi, hero: HeroView, focus: CardFocusState) {
    if (hero.itemStats.isEmpty() && hero.items.isEmpty()) return
    Overline("Items · p.16")
    Div({ classes("goa-items") }) {
        itemGroups(hero.itemStats).forEach { (stat, count) ->
            Span({
                classes("goa-item")
                attr("role", "img")
                attr("aria-label", "+1 ${PlayText.stat(stat)}" + if (count > 1) ", $count items" else ", 1 item")
            }) {
                LookImg("official/icons/item-${stat.name.lowercase()}.webp", "goa-item-icon")
                Span({ classes("t-title-small") }) { Text("+$count") }
            }
        }
    }
    if (hero.items.isNotEmpty()) {
        Quiet("Tucked under the dashboard: only you see which cards (p.4).")
        CardRow(hero.items.map { CardView(it) }, table, focus, "${table.hero(hero.heroId).name} · Items")
    }
}

/**
 * The unlocked Ultimate (a faceup card on the dashboard, p.16) beside the hand: the person's own cards, any
 * hero's in a puzzle (its hands are open information), otherwise as many card backs (hidden, p.4).
 */
@Composable
private fun UltimateAndHand(table: TableUi, hero: HeroView, focus: CardFocusState) {
    val name = table.hero(hero.heroId).name
    Div({ classes("goa-ultimate-hand") }) {
        ultimateCard(table, hero)?.let { card ->
            Div {
                Overline("Ultimate · p.16")
                CardRow(listOf(CardView(card.id)), table, focus, "$name · Ultimate", cardWidth = SHEET_CARD)
            }
        } ?: run { if (hero.ultimate) Fact("Ultimate", "Unlocked") }
        Div({ classes("goa-ultimate-hand-rest") }) {
            val hand = hero.hand
            when {
                hand == null -> {
                    Overline("In hand · ${hero.handCount}")
                    val backs = List(hero.handCount) { CardView(null) }
                    CardRow(backs, table, focus, "$name · In hand", cardWidth = SHEET_CARD)
                }

                hero.heroId in table.mine -> {
                    Overline("Your hand · ${hand.size}")
                    CardRow(hand.map { CardView(it) }, table, focus, "$name · Your hand", cardWidth = SHEET_CARD)
                }

                hand.isNotEmpty() -> {
                    Overline("In hand · open in this puzzle")
                    CardRow(hand.map { CardView(it) }, table, focus, "$name · In hand", cardWidth = SHEET_CARD)
                }
            }
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
