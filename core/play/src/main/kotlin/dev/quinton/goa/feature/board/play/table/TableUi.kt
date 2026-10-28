package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.PieceView
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.PlayText

/**
 * Everything the game table draws (the game screen's and the puzzles'). [view] is what the person
 * at the phone may see (p.4): their own heroes' hands, everyone's open information (a puzzle shows
 * every hand: its hands are open). [decision] is the pending question; [asking] = it is theirs to
 * answer (else the computer is choosing, or the game is waiting for others). [selected] is the
 * option they tapped but have not confirmed yet, or a puzzle replay's move. [hint] is the option a
 * puzzle's last nudge points at (drawn apart from the selection). [turnLog] is the events of the
 * latest two turns ([turnLog]): the turn track and the table's moments read them (ADR-041).
 */
data class TableUi(
    val map: BoardMap,
    val view: GameView,
    val heroes: Map<String, BoardHero>,
    val cards: Map<String, Card>,
    /** The heroes the person at the phone controls. */
    val mine: Set<String>,
    val decision: Decision? = null,
    val asking: Boolean = false,
    val selected: Choice? = null,
    val bottomTeam: Team = Team.RED,
    val hint: Choice? = null,
    val turnLog: List<GameEvent> = emptyList(),
) {
    /** After the reveal: every played card in acting order, with who is done, acting and next (ADR-041). */
    val track: TurnTrack? by lazy { turnTrack(view, turnLog) { card(it)?.initiative } }

    /** The transition the table is at (the reveal, a turn or round over), in words. */
    val moment: TableMoment? by lazy { tableMoment(view, turnLog, track, { hero(it).name }, mine) }

    val geometry: BoardGeometry get() = BoardGeometry.of(map)

    fun hero(id: String): BoardHero = heroes.hero(id)

    fun heroView(id: String): HeroView? = view.heroes.firstOrNull { it.heroId == id }

    /**
     * Who plays [hero], in words: "You" for the person's heroes; a puzzle's other side (seated as
     * human in the engine, its replies come from the solver) is "The other side", not "You".
     */
    fun controllerLabel(hero: HeroView): String = when {
        hero.heroId in mine -> "You"
        hero.controller == Controller.Human -> "The other side"
        else -> PlayText.controller(hero.controller)
    }

    fun card(id: String?): Card? = id?.let(cards::get)

    /** The Battle Zone of each Lane, in map order. */
    val battleZones: List<String>
        get() = map.lanes.map { lane -> view.battleZones[lane.id] ?: lane.startingBattleZone }

    /** The pieces as the board draws them (heroes off the board are left out). */
    val pieces: List<BoardPiece>
        get() = buildList {
            view.minions.forEach { add(MinionPiece(it.id, it.at, it.team, it.type, it.lane)) }
            view.pieces.forEach { p ->
                val figureOf = figureOf(p)
                // A hero's other figures (Razzle's) are drawn as that hero, not as a token.
                if (figureOf != null && p.team != null) {
                    add(HeroPiece(p.id, p.at, figureOf, p.team!!))
                } else {
                    add(TokenPiece(p.id, p.at, p.label, p.team))
                }
            }
            view.heroes.forEach { h -> h.at?.let { add(HeroPiece(h.heroId, it, h.heroId, h.team)) } }
        }

    /** Where every unit and token stands. */
    val occupancy: Map<Hex, String>
        get() = buildMap {
            view.minions.forEach { put(it.at, it.id) }
            view.pieces.forEach { put(it.at, it.id) }
            view.heroes.forEach { h -> h.at?.let { put(it, h.heroId) } }
        }

    fun at(pieceId: String): Hex? = view.heroes.firstOrNull { it.heroId == pieceId }?.at
        ?: view.minions.firstOrNull { it.id == pieceId }?.at
        ?: view.pieces.firstOrNull { it.id == pieceId }?.at

    /** Spaces the person can tap to answer, with the choice each one is. */
    val spaceOptions: Map<Hex, Choice.Space>
        get() = if (!asking) {
            emptyMap()
        } else {
            decision?.options.orEmpty().filterIsInstance<Choice.Space>().associateBy {
                it.hex
            }
        }

    /** Units and tokens the person can tap to answer, by piece id. */
    val pieceOptions: Map<String, Choice>
        get() = if (!asking) {
            emptyMap()
        } else {
            decision?.options.orEmpty().mapNotNull { choice ->
                when (choice) {
                    is Choice.Unit -> choice.unitId to choice
                    is Choice.Token -> choice.tokenId to choice
                    else -> null
                }
            }.toMap()
        }

    /** Cards offered as answers (to play, defend with, upgrade, keep…). */
    val cardOptions: List<Choice.Card>
        get() = decision?.options.orEmpty().filterIsInstance<Choice.Card>()

    /** Every other answer: buttons (actions, "Choose one" options, yes / decline, numbers, pass). */
    val buttonOptions: List<Choice>
        get() = decision?.options.orEmpty().filter {
            it !is Choice.Space && it !is Choice.Unit && it !is Choice.Token && it !is Choice.Card
        }

    /** The path the engine will use to reach [hex], when the decision gives one (keys "q,r"). */
    fun pathTo(hex: Hex): List<Hex> = decision?.context?.paths?.get("${hex.q},${hex.r}").orEmpty()

    /** Where [hint] is on the board, when it is a space, a unit or a token. */
    val hintAt: Hex?
        get() = when (val h = hint) {
            is Choice.Space -> h.hex
            is Choice.Unit -> at(h.unitId)
            is Choice.Token -> at(h.tokenId)
            else -> null
        }

    /** The unit the question is about (the one moving, the attack target…). */
    val subject: String? get() = decision?.context?.subjectId

    /** The deciding hero. */
    val decider: HeroView? get() = decision?.seat?.let(::heroView)

    /** The card whose text (or action) asks the question. */
    val sourceCard: Card? get() = card(decision?.sourceCardId)

    /** After the reveal: who acts in which order, the acting hero first among the unresolved. */
    val initiativeOrder: List<String> get() = view.initiativeOrder

    val over: Boolean get() = view.phase == GamePhase.GAME_OVER

    /** Name of a unit or token, as the log and TalkBack say it. */
    fun pieceName(id: String): String {
        view.heroes.firstOrNull { it.heroId == id }?.let { return hero(id).name }
        view.minions.firstOrNull {
            it.id == id
        }?.let { return "${BoardText.team(it.team)} ${BoardText.minion(it.type)}" }
        return view.pieces.firstOrNull { it.id == id }?.let(::pieceLabel) ?: "Unknown piece"
    }

    private fun pieceLabel(piece: PieceView): String = figureOf(piece)?.let { "${hero(it).name}'s other figure" }
        ?: listOfNotNull("Token ${piece.label}", piece.team?.let(BoardText::team)).joinToString(", ")

    /** What a choice is, in words: for buttons, TalkBack and the Confirm button. */
    fun label(choice: Choice): String = when (choice) {
        is Choice.Space -> distinctSpaceLabel(choice.hex)
        is Choice.Unit -> distinctPieceName(choice.unitId)
        is Choice.Token -> distinctPieceName(choice.tokenId)
        is Choice.Card -> card(choice.cardId)?.name ?: "Unknown card"
        is Choice.Action -> PlayText.action(choice.action, sourceCard)
        else -> PlayText.button(choice)
    }

    /**
     * A piece's name, with where it stands when another option has the same name ("Red Melee
     * minion · Lane Zone 2 · 1 space from Mortimer"): three chips saying "Red Melee minion" told
     * the player nothing at the Minion Battle.
     */
    private fun distinctPieceName(id: String): String {
        val name = pieceName(id)
        val twins = pieceOptions.keys.count { it != id && pieceName(it) == name }
        val at = at(id)
        return if (twins > 0 && at != null) "$name · ${spaceLabel(at)}" else name
    }

    /**
     * A space's words, with what stands next to it when another offered space reads the same
     * ("Lane Zone 2 · 1 space from Brogan · next to Wasp"): three spaces around Brogan all read
     * "Lane Zone 2 · 1 space from Brogan", and a screen reader or the puzzle's hint told the player
     * nothing about which one.
     */
    private fun distinctSpaceLabel(hex: Hex): String {
        val label = spaceLabel(hex)
        if (spaceOptions.keys.none { it != hex && spaceLabel(it) == label }) return label
        val from = subject ?: decision?.seat
        val standing = occupancy
        // Heroes first: "next to Wasp, Blue Heavy minion".
        val beside = HexGrid.neighbors(hex).mapNotNull { standing[it] }.filter { it != from }
            .sortedBy { id -> view.heroes.none { it.heroId == id } }
            .map(::pieceName).distinct()
        return if (beside.isEmpty()) label else "$label · next to ${beside.joinToString(", ")}"
    }

    /** "Lane Zone 2 · 3 spaces from Arien". */
    fun spaceLabel(hex: Hex): String {
        val zone = BoardText.zone(map, geometry.zoneOf(hex))
        val from = (subject ?: decision?.seat)?.let { id -> at(id)?.let { id to it } }
        return if (from != null && from.second != hex) {
            "$zone · ${BoardText.spaces(HexGrid.distance(from.second, hex))} from ${pieceName(from.first)}"
        } else {
            zone
        }
    }

    /** The cards a hero shows face up on its dashboard (p.4 open information), newest last. */
    fun openCards(hero: HeroView): List<CardView> = hero.resolved + hero.discard

    /** True when [decision] is about cards in a hand (play, defend) rather than the board. */
    val handDecision: Boolean
        get() = decision?.kind == DecisionKind.PLAY_CARD || decision?.kind == DecisionKind.DEFEND
}

/**
 * The hero a piece is another figure of (a unit the engine treats as that hero: Razzle's doubles,
 * kind `<hero>_double`), else null.
 */
fun figureOf(piece: PieceView): String? =
    piece.ownerHeroId?.takeIf { piece.isUnit && piece.kind == "${it}_double" }

/** A tap on the game map: a piece (by id) or a space. */
sealed interface TableTap {
    data class Piece(val pieceId: String) : TableTap

    data class Space(val hex: Hex) : TableTap
}
