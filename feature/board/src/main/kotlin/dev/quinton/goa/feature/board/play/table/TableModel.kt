package dev.quinton.goa.feature.board.play.table

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero

/*
 * The game table's UI model (ADR-035): what one viewer may see of a game (GameView, p.4) and the
 * pending question, turned into what the table draws. Pure and independent of any ViewModel, so the
 * play screen and the puzzle screens build it the same way from a GameSession.
 */

/** The catalog lookups the table needs: heroes and every card (deck, Handicap, extras) by id. */
@Immutable
class TableCatalog(val heroes: Map<String, BoardHero>, val cards: Map<String, Card>) {
    fun hero(id: String): BoardHero = heroes.hero(id)

    fun card(id: String?): Card? = id?.let(cards::get)

    fun cardName(id: String?): String = card(id)?.name ?: "a card"

    companion object {
        fun of(catalog: Catalog): TableCatalog =
            TableCatalog(catalog.boardHeroes(), catalog.heroes.flatMap { it.allCards }.associateBy { it.id })
    }
}

/** A card as the viewer sees it: [card] null when it is facedown to them. */
@Immutable
data class TableCard(val cardId: String?, val card: Card?, val facedown: Boolean, val active: Boolean, val slot: Int?)

/** One hero at the table, with its dashboard (open information, p.4). */
@Immutable
data class TableHero(
    val id: String,
    val name: String,
    val portrait: String?,
    val team: Team,
    /** Answered by the person holding the phone. */
    val human: Boolean,
    /** "You", "Computer · Normal". */
    val controller: String,
    val at: Hex?,
    val level: Int,
    val coins: Int,
    val handCount: Int,
    /** One item icon per item (p.16), for every hero. */
    val items: List<Stat>,
    val markers: List<MarkerView>,
    /** Cards whose Active effect is in force. */
    val activeEffects: List<Card>,
    val played: List<TableCard>,
    val resolved: List<TableCard>,
    val discard: List<TableCard>,
    /** The tucked item cards, for the viewer's own hero only. */
    val ownItems: List<Card>,
    val ultimate: Boolean,
    val counters: Map<String, Int>,
    val acting: Boolean,
)

/** One hero in the initiative order after the reveal (p.7). */
@Immutable
data class InitiativeUi(val hero: TableHero, val card: Card?, val initiative: Int?)

/** One answer, as a button or a list row: [label] what it does, [detail] why or what follows. */
@Immutable
data class OptionUi(val choice: Choice, val label: String, val detail: String? = null)

/** A card to choose (play, discard, upgrade, keep): [detail] e.g. the item it would become. */
@Immutable
data class CardOptionUi(val choice: Choice, val cardId: String, val card: Card?, val detail: String? = null)

/** A hand card in a defense: its Defense total if the engine knows it up front, and whether it holds. */
@Immutable
data class DefenseCardUi(val choice: Choice, val card: Card?, val cardId: String, val total: Int?) {
    fun holds(attack: Int?): Boolean? = if (total == null || attack == null) null else total >= attack
}

/** DEFEND (p.14): the attack and what each card in hand would do against it. */
@Immutable
data class DefenseUi(
    val attacker: TableHero?,
    val attackTotal: Int?,
    val ranged: Boolean?,
    val minionModifier: Int?,
    val cards: List<DefenseCardUi>,
    val decline: OptionUi?,
)

/**
 * The pending question as the table shows it. Board choices ([spaces], [units]) are drawn on the
 * map and also listed ([listed]) so TalkBack and a precise tap can reach every one; everything else
 * is a button ([buttons]) or a card ([cards]).
 */
@Immutable
data class DecisionUi(
    val decision: Decision,
    /** A human answers it (the person holding the phone). */
    val mine: Boolean,
    val seat: TableHero?,
    /** The card whose text or action asks this. */
    val source: Card?,
    val spaces: Map<Hex, OptionUi>,
    val units: Map<String, OptionUi>,
    val buttons: List<OptionUi>,
    val cards: List<CardOptionUi>,
    val defense: DefenseUi?,
    val highlight: Set<Hex>,
    /** The unit the question is about (moved, pushed, attacked). */
    val subject: String?,
    val paths: Map<Hex, List<Hex>>,
) {
    val id: Long get() = decision.id
    val kind: DecisionKind get() = decision.kind
    val prompt: String get() = decision.prompt

    /** Board options in reading order, for the options list. */
    val listed: List<OptionUi> get() = spaces.values.toList() + units.values.toList()

    fun option(choice: Choice): OptionUi? =
        (spaces.values + units.values + buttons).firstOrNull { it.choice == choice }
}

/** The whole table for one viewer. */
@Immutable
data class TableState(
    val map: BoardMap,
    val geometry: BoardGeometry,
    val view: GameView,
    val heroes: List<TableHero>,
    /** Every piece on the board, drawn like the sandbox's: heroes, minions, tokens. */
    val pieces: List<BoardPiece>,
    /** Minions immune right now (p.13 Heavy Minion Immunity). */
    val immune: Set<String>,
    /** The hero whose hand is shown (the human deciding now, or the first human hero). */
    val viewer: TableHero?,
    val hand: List<Card>,
    val decision: DecisionUi?,
    val initiative: List<InitiativeUi>,
    /** The team the person holding the phone plays (null = watching). */
    val humanTeam: Team?,
    val bottomTeam: Team,
) {
    fun hero(id: String?): TableHero? = heroes.firstOrNull { it.id == id }

    /** Lane id → current Battle Zone id. */
    val battleZones: Map<String, String> get() = view.battleZones
}

/** Builds [TableState]s. */
object TableModel {
    /**
     * The table for [view] (built for its viewer) with [decision] pending (any seat's). [humanSeats]:
     * the heroes the person holding the phone answers for; [levels] names each computer seat's level.
     */
    fun build(
        map: BoardMap,
        view: GameView,
        decision: Decision?,
        catalog: TableCatalog,
        humanSeats: Set<String>,
    ): TableState {
        val geometry = BoardGeometry.of(map)
        val heroes = view.heroes.map { tableHero(it, view, catalog, humanSeats) }
        val byId = heroes.associateBy { it.id }
        val pieces = buildList<BoardPiece> {
            view.heroes.forEach { h -> h.at?.let { add(HeroPiece(h.heroId, it, h.heroId, h.team)) } }
            view.minions.forEach { m -> add(MinionPiece(m.id, m.at, m.team, m.type, m.lane)) }
            view.pieces.forEach { p -> add(TokenPiece(p.id, p.at, p.label, p.team)) }
        }
        val viewer = byId[view.viewer]
        val humanTeam = heroes.firstOrNull { it.human }?.team
        val hand = view.heroes.firstOrNull { it.heroId == view.viewer }?.hand.orEmpty().mapNotNull(catalog::card)
        val initiative = view.initiativeOrder.mapNotNull { id ->
            val hero = byId[id] ?: return@mapNotNull null
            val card = hero.played.firstOrNull()?.card
            InitiativeUi(hero, card, card?.initiative)
        }
        return TableState(
            map = map,
            geometry = geometry,
            view = view,
            heroes = heroes,
            pieces = pieces,
            immune = view.minions.filter { it.immune }.map { it.id }.toSet(),
            viewer = viewer,
            hand = hand,
            decision = decision?.let { decisionUi(it, map, geometry, view, pieces, byId, catalog, humanSeats) },
            initiative = initiative,
            humanTeam = humanTeam,
            bottomTeam = humanTeam ?: Team.RED,
        )
    }

    private fun tableHero(h: HeroView, view: GameView, catalog: TableCatalog, humans: Set<String>): TableHero {
        val hero = catalog.hero(h.heroId)
        fun card(c: dev.quinton.goa.core.game.CardView) =
            TableCard(c.cardId, catalog.card(c.cardId), c.facedown, c.active, c.slot)
        return TableHero(
            id = h.heroId,
            name = hero.name,
            portrait = hero.portrait,
            team = h.team,
            human = h.heroId in humans,
            controller = TableText.controller(h.controller, h.heroId in humans),
            at = h.at,
            level = h.level,
            coins = h.coins,
            handCount = h.handCount,
            items = h.itemStats,
            markers = h.markers,
            activeEffects = h.activeEffects.mapNotNull(catalog::card),
            played = listOfNotNull(h.played).map(::card) + h.alsoPlayed.map(::card),
            resolved = h.resolved.map(::card),
            discard = h.discard.map(::card),
            ownItems = h.items.mapNotNull(catalog::card),
            ultimate = h.ultimate,
            counters = h.counters,
            acting = view.acting == h.heroId,
        )
    }

    @Suppress("LongParameterList")
    private fun decisionUi(
        d: Decision,
        map: BoardMap,
        geometry: BoardGeometry,
        view: GameView,
        pieces: List<BoardPiece>,
        heroes: Map<String, TableHero>,
        catalog: TableCatalog,
        humans: Set<String>,
    ): DecisionUi {
        val seat = heroes[d.seat]
        val source = catalog.card(d.sourceCardId)
        val subject = d.context.subjectId
        val from = subject?.let { id -> pieces.firstOrNull { it.id == id }?.at } ?: seat?.at
        val fromName = subject?.let { id -> pieceName(pieces.firstOrNull { it.id == id }, heroes) } ?: seat?.name
        val sorter = OptionSorter(d, map, geometry, view, pieces, heroes, catalog, from, fromName)
        d.options.forEach(sorter::add)
        val spaces = sorter.spaces
        val units = sorter.units
        val buttons = sorter.buttons
        val cards = sorter.cards
        val defense = if (d.kind == DecisionKind.DEFEND) {
            DefenseUi(
                attacker = heroes[d.context.attackerId],
                attackTotal = d.context.attackTotal,
                ranged = d.context.rangedAttack,
                minionModifier = d.context.minionModifier,
                cards = cards.map { DefenseCardUi(it.choice, it.card, it.cardId, d.context.defensePreview[it.cardId]) },
                decline = buttons.firstOrNull { it.choice is Choice.Decline },
            )
        } else {
            null
        }
        return DecisionUi(
            decision = d,
            mine = d.seat in humans,
            seat = seat,
            source = source,
            spaces = spaces,
            units = units,
            buttons = buttons,
            cards = cards,
            defense = defense,
            highlight = d.context.highlight.toSet(),
            subject = subject,
            paths = d.context.paths.mapNotNull { (key, path) -> parseHex(key)?.let { it to path } }.toMap(),
        )
    }

    /**
     * Sorts a decision's options into where the table shows them: spaces and units on the board,
     * cards to pick, everything else as buttons.
     */
    @Suppress("LongParameterList")
    private class OptionSorter(
        private val d: Decision,
        private val map: BoardMap,
        private val geometry: BoardGeometry,
        private val view: GameView,
        private val pieces: List<BoardPiece>,
        private val heroes: Map<String, TableHero>,
        private val catalog: TableCatalog,
        private val from: Hex?,
        private val fromName: String?,
    ) {
        val spaces = LinkedHashMap<Hex, OptionUi>()
        val units = LinkedHashMap<String, OptionUi>()
        val buttons = ArrayList<OptionUi>()
        val cards = ArrayList<CardOptionUi>()
        private val occupied = pieces.filter { it.at != null }.associateBy { it.at!! }
        private val source = catalog.card(d.sourceCardId)

        fun add(choice: Choice) {
            when (choice) {
                is Choice.Space -> spaces[choice.hex] = OptionUi(choice, spaceLabel(choice.hex))

                is Choice.Unit -> piece(choice, choice.unitId, catalog.hero(choice.unitId).name)

                is Choice.Token -> piece(choice, choice.tokenId, "Token")

                is Choice.Card -> {
                    val card = catalog.card(choice.cardId)
                    cards += CardOptionUi(choice, choice.cardId, card, cardDetail(d, card))
                }

                is Choice.Action -> buttons += actionOption(choice, source, heroes[d.seat])

                is Choice.Option -> buttons += OptionUi(choice, choice.label)

                is Choice.Number -> buttons += OptionUi(choice, choice.value.toString())

                Choice.Yes -> buttons += OptionUi(choice, "Yes")

                is Choice.Decline -> buttons += OptionUi(choice, choice.label)

                Choice.Pass -> buttons += OptionUi(choice, "Pass", "You have no card to play (p.7)")
            }
        }

        /** A unit or token on the board is tapped there; a hero off it (who acts first) is a button. */
        private fun piece(choice: Choice, id: String, fallback: String) {
            val piece = pieces.firstOrNull { it.id == id }
            val label = pieceName(piece, heroes) ?: fallback
            val at = piece?.at
            if (at != null && d.kind != DecisionKind.ACT_FIRST) {
                units[id] = OptionUi(choice, label, zoneOf(map, geometry, at))
            } else {
                buttons += OptionUi(choice, label)
            }
        }

        /** "Lane Zone 2 · 3 spaces from Wasp · Red Melee minion", for a space option. */
        private fun spaceLabel(hex: Hex): String = buildList {
            add(zoneOf(map, geometry, hex))
            if (from != null && fromName != null && from != hex) {
                add("${BoardText.spaces(HexGrid.distance(from, hex))} from $fromName")
            }
            occupied[hex]?.let { piece -> pieceName(piece, heroes)?.let(::add) }
            geometry.spaces[hex]?.spawn?.let { add("${BoardText.team(it.team)} ${BoardText.spawn(it.kind)}") }
            if (view.battleZones.values.contains(geometry.spaces[hex]?.zone)) add("Battle Zone")
        }.joinToString(" · ")
    }

    private fun zoneOf(map: BoardMap, geometry: BoardGeometry, hex: Hex): String =
        BoardText.zone(map, geometry.spaces[hex]?.zone)

    private fun pieceName(piece: BoardPiece?, heroes: Map<String, TableHero>): String? = piece?.let {
        BoardText.piece(it) { id -> heroes[id]?.name ?: id }
    }

    private fun actionOption(choice: Choice.Action, source: Card?, seat: TableHero?): OptionUi {
        val values = source?.values
        return when (choice.action) {
            ActionType.PRIMARY -> OptionUi(
                choice,
                TableText.primary(source),
                source?.let { TableText.primaryDetail(it) },
            )

            ActionType.MOVEMENT -> {
                val items = seat?.items?.count { it == Stat.MOVEMENT } ?: 0
                val printed = values?.movement?.let { "Movement $it" }
                OptionUi(choice, "Movement", printed?.let { if (items > 0) "$it, +$items from items" else it })
            }

            ActionType.FAST_TRAVEL ->
                OptionUi(
                    choice,
                    "Fast Travel",
                    "Your Zone or an adjacent one, no enemy hero or minion in either (p.11)",
                )

            ActionType.ATTACK -> OptionUi(choice, "Attack", values?.attack?.let { "Attack $it" })

            ActionType.CLEAR -> OptionUi(choice, "Clear", "Remove any number of adjacent tokens (p.13)")

            ActionType.HOLD -> OptionUi(choice, "Hold", "Do nothing (p.12)")
        }
    }

    private fun cardDetail(d: Decision, card: Card?): String? = when (d.kind) {
        DecisionKind.KEEP_UPGRADE -> card?.item?.let { "The other card becomes an item" }
        DecisionKind.UPGRADE_CARD -> card?.let { "Replace it with a card of the next tier" }
        else -> null
    }

    /** `paths` keys are `"q,r"` (core/game CONTRACT-CHANGES.md). */
    private fun parseHex(key: String): Hex? {
        val parts = key.split(',')
        if (parts.size != 2) return null
        val q = parts[0].trim().toIntOrNull() ?: return null
        val r = parts[1].trim().toIntOrNull() ?: return null
        return Hex(q, r)
    }
}

/** True when the controller is a person. */
val Controller.isHuman: Boolean get() = this is Controller.Human
