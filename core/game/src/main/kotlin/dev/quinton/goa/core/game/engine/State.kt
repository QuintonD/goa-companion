package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable

/*
 * The engine's internal state (ENGINE.md "State"). Plain mutable data: every rule and every card
 * effect writes it through the primitives in [Game]; the session keeps a checkpoint copy
 * ([GameState.copy]) and re-runs a step from it with the answers given so far. Everything here is
 * serializable, so a game saves at any decision, mid-card and mid-defense included.
 *
 * Deep copies must be cheap (the AI forks a lot): no derived caches are stored here.
 */

/** Where a card is (p.10 "Card States"; deck p.6; items and the Ultimate p.16). */
@Serializable
enum class Zone {
    /** Not in play yet (Tier II+ before levelling up, cards returned on upgrade, extra golds). */
    DECK,
    HAND,

    /** Played and unresolved (p.10): on the board, facedown until the reveal. */
    PLAYED,

    /** On a Turn slot of the dashboard ([CardInst.slot] 1..4). Played and resolved. */
    RESOLVED,
    DISCARD,

    /** Tucked under the dashboard as an item (p.16). */
    ITEM,

    /** Unlocked Ultimate (p.16). */
    ULTIMATE,

    /** Set aside during a level up (p.15), on its way to becoming an item. */
    ASIDE,

    /** Back in the box: an unused Handicap card, or the basic card it replaced (p.6, p.20). */
    BOX,

    /** A hero-specific place named by [CardInst.tag] (e.g. Gydion's spellbook). */
    EXTRA,
}

/** One of a hero's cards and its state (p.10). */
@Serializable
class CardInst(
    val id: String,
    var zone: Zone,
    /** Turn slot 1..4 while [Zone.RESOLVED]. */
    var slot: Int = 0,
    /** p.10 "Facedown": a resolved or discarded card that lost its type, colour and actions. */
    var faceDown: Boolean = false,
    /** p.10 "Played": kept until the card returns to hand (a discarded played card is both). */
    var played: Boolean = false,
    /** Hero-specific sub-place for [Zone.EXTRA], or a note a hero script keeps on the card. */
    var tag: String? = null,
    /** Monotone stamp of the last state change: orders the discard pile and "rightmost" slots. */
    var stamp: Long = 0,
) {
    fun copy(): CardInst = CardInst(id, zone, slot, faceDown, played, tag, stamp)
}

/** A marker on a hero (p.18 "Markers"), e.g. poison or bounty. */
@Serializable
class Marker(
    val kind: String,
    /** The hero whose card gave it. */
    val from: String?,
    /** Not returned at the end of round or on defeat (e.g. Snorri's runes say so). */
    val persistent: Boolean = false,
    val data: MutableMap<String, String> = LinkedHashMap(),
) {
    fun copy(): Marker = Marker(kind, from, persistent, LinkedHashMap(data))
}

@Serializable
class HeroState(
    val id: String,
    val team: Team,
    /** Index in [GameState.heroes]: seat order (pick order per team, Red first). */
    val seat: Int,
    var at: Hex?,
    var level: Int = 1,
    var coins: Int = 0,
    val cards: MutableList<CardInst> = ArrayList(),
    val markers: MutableList<Marker> = ArrayList(),
    /** Hero-specific state for card scripts (enraged, charges, spellbook data…). Never shown. */
    val vars: MutableMap<String, String> = LinkedHashMap(),
    /** Hero-specific public counters, shown in the view ([dev.quinton.goa.core.game.HeroView.counters]). */
    val counters: MutableMap<String, Int> = LinkedHashMap(),
    var levelledThisRound: Boolean = false,
    val handicap: Boolean = false,
) {
    fun card(id: String): CardInst? = cards.firstOrNull { it.id == id }

    fun inZone(zone: Zone): List<CardInst> = cards.filter { it.zone == zone }

    val hand: List<CardInst> get() = inZone(Zone.HAND)

    /** This turn's played and unresolved cards (usually one). */
    val unresolved: List<CardInst> get() = inZone(Zone.PLAYED)

    val ultimateUnlocked: Boolean get() = cards.any { it.zone == Zone.ULTIMATE }

    fun copy(): HeroState = HeroState(
        id, team, seat, at, level, coins,
        cards.mapTo(ArrayList(cards.size)) { it.copy() },
        markers.mapTo(ArrayList(markers.size)) { it.copy() },
        LinkedHashMap(vars), LinkedHashMap(counters), levelledThisRound, handicap,
    )
}

@Serializable
class MinionState(
    val id: String,
    val team: Team,
    val type: MinionType,
    var at: Hex?,
    /** p.20: bound to the Lane whose Battle Zone it was spawned in. */
    val lane: String,
) {
    fun copy(): MinionState = MinionState(id, team, type, at, lane)
}

/**
 * Any other object on the board (p.18 "Obstacle": "any objects represented by miniatures"): tokens,
 * Pyro, the Turret, Razzle's doubles, Mortimer's zombies… Its rules come from the behaviour
 * registered for [kind] (EFFECTS.md "Pieces and tokens").
 */
@Serializable
class PieceState(
    val id: String,
    /** Registry key, e.g. `token_rock`, `token_smoke_bomb`, `pyro`. */
    val kind: String,
    val label: String,
    var at: Hex?,
    val owner: String? = null,
    val team: Team? = null,
    /** p.19 "Tokens": "All tokens are obstacles"; the Clear action removes tokens. */
    val token: Boolean = true,
    /** A unit (can be attacked, blocks, counts for "units"), e.g. Razzle's doubles. */
    val unit: Boolean = false,
    /** p.19: tokens are removed at the end of round "unless the card text says otherwise". */
    var removeAtEndOfRound: Boolean = true,
    /** Units may move through it (e.g. Min's mines: "units may move through them"). */
    var passable: Boolean = false,
    /** Facedown token (Min's mines): its kind is hidden information. */
    var faceDown: Boolean = false,
    /** This piece is a body of that hero ("each of you is the same hero", Razzle): it is that hero. */
    val heroBody: String? = null,
    val data: MutableMap<String, String> = LinkedHashMap(),
) {
    fun copy(): PieceState = PieceState(
        id, kind, label, at, owner, team, token, unit, removeAtEndOfRound, passable, faceDown, heroBody,
        LinkedHashMap(data),
    )
}

/** When an Active effect applies (p.17–19). */
@Serializable
enum class Timing {
    /** "This turn:" — now until the end of this turn (p.19). */
    THIS_TURN,

    /** "This round:" — now until the end of the round (p.19). */
    THIS_ROUND,

    /** "Next turn:" — the whole of the next turn (p.18); never carries into the next round (FAQ). */
    NEXT_TURN,

    /** "Next turn, after playing cards:" — applied once next turn after the reveal (p.18). */
    NEXT_TURN_AFTER_PLAYING,

    /** "End of turn:" — applied once at the end of this turn (p.17). */
    END_OF_TURN,

    /** "End of round:" — applied once at the end of this round (p.17). */
    END_OF_ROUND,

    /** In force until a script cancels it or its card changes state (e.g. "you are enraged" kept on a card). */
    UNTIL_CANCELLED,
}

/**
 * An Active effect (p.17) on [cardId] of [owner]. Cancelled when it no longer applies, when the
 * owner is defeated, and when the card changes state (end of round included).
 */
@Serializable
class ActiveEffect(
    val id: Int,
    val cardId: String,
    val owner: String,
    val timing: Timing,
    val round: Int,
    val turn: Int,
    /** The action type of the action that created it, for "immune to … actions (and their active effects)" (p.18). */
    val action: ActionKind,
    /** A behaviour key other than [cardId] (several effects on one card); null = the card's own behaviour. */
    val key: String? = null,
    val data: MutableMap<String, String> = LinkedHashMap(),
    /** Applied once already (End of turn / round, After playing cards), or deactivated by its text. */
    var spent: Boolean = false,
) {
    fun copy(): ActiveEffect = ActiveEffect(id, cardId, owner, timing, round, turn, action, key, LinkedHashMap(data), spent)
}

/** Action types (p.9, pp.11–14); [DEFENSE] is the response to an attack. */
@Serializable
enum class ActionKind { ATTACK, SKILL, MOVEMENT, DEFENSE, FAST_TRAVEL, CLEAR, HOLD, NONE }

/** The program counter of the game flow (ENGINE.md "Flow"). */
@Serializable
enum class Step {
    TURN_START,

    /** Asking [GameState.stepSeat] for this turn's card (p.7 "Play One Card"). */
    PLAY,
    REVEAL,

    /** One hero's card resolution per run. */
    RESOLVE,
    END_OF_TURN,

    /** End of round (p.8): "End of round:" effects first, then the six steps. */
    EOR_EFFECTS,
    MINION_BATTLE,
    CLEANUP,
    PUSH,
    RETRIEVE,

    /** Level up of [GameState.stepSeat] (p.15). */
    LEVEL_UP,
    PITY,
    GAME_OVER,
}

/** Facts remembered for one turn (cards that ask "since the last turn", "this turn"). */
@Serializable
class TurnRecord(
    /** Where each unit stood at the start of the turn (null = off the board). */
    val startPositions: MutableMap<String, Hex?> = LinkedHashMap(),
    /** Heroes who played an attack card this turn (FAQ: remembered even if it is swapped away). */
    val playedAttack: MutableSet<String> = LinkedHashSet(),
    /** Cards discarded this turn, per hero. */
    val discarded: MutableMap<String, MutableList<String>> = LinkedHashMap(),
    /** Heroes who resolved a card this turn, in order. */
    val resolved: MutableList<String> = ArrayList(),
    /** Free per-turn notes for scripts ("once per turn" abilities). */
    val notes: MutableSet<String> = LinkedHashSet(),
) {
    fun copy(): TurnRecord = TurnRecord(
        LinkedHashMap(startPositions),
        LinkedHashSet(playedAttack),
        discarded.mapValuesTo(LinkedHashMap()) { ArrayList(it.value) },
        ArrayList(resolved),
        LinkedHashSet(notes),
    )
}

@Serializable
class GameState(
    val mapId: String,
    val length: GameLength,
    var round: Int = 1,
    var turn: Int = 1,
    var step: Step = Step.TURN_START,
    var stepSeat: Int = 0,
    var tieBreaker: Team,
    val heroes: MutableList<HeroState> = ArrayList(),
    val minions: MutableList<MinionState> = ArrayList(),
    val pieces: MutableList<PieceState> = ArrayList(),
    /** Current Battle Zone per Lane id. */
    val battleZones: MutableMap<String, String> = LinkedHashMap(),
    /** Unflipped Wave counters per Lane id. */
    val wavesLeft: MutableMap<String, Int> = LinkedHashMap(),
    val lifeCounters: MutableMap<Team, Int> = LinkedHashMap(),
    val effects: MutableList<ActiveEffect> = ArrayList(),
    /** True once this turn's cards are revealed (p.7). */
    var revealed: Boolean = false,
    /** The hero resolving a card right now. */
    var acting: String? = null,
    var winner: Team? = null,
    var winReason: WinReason? = null,
    /** Decisions answered and committed so far (the next decision's id). */
    var decisions: Long = 0,
    var nextId: Int = 1,
    var stamp: Long = 0,
    var turnRecord: TurnRecord = TurnRecord(),
    /** "If any hero was defeated this round" (e.g. Brogan's War Drummer). */
    var heroDefeatedThisRound: Boolean = false,
    /** A Lane whose push is due at the end-of-round Push step (Minion Battle removed a team's last minion). */
    val pushDue: MutableMap<String, Team> = LinkedHashMap(),
    /** Lanes whose last Wave counter has been flipped (double lane, p.20). */
    val lastWaveFlipped: MutableSet<String> = LinkedHashSet(),
    /** Game-level notes for scripts. */
    val vars: MutableMap<String, String> = LinkedHashMap(),
) {
    fun hero(id: String): HeroState? = heroes.firstOrNull { it.id == id }

    fun minion(id: String): MinionState? = minions.firstOrNull { it.id == id }

    fun piece(id: String): PieceState? = pieces.firstOrNull { it.id == id }

    fun newId(prefix: String): String = "$prefix${nextId++}"

    fun copy(): GameState = GameState(
        mapId, length, round, turn, step, stepSeat, tieBreaker,
        heroes.mapTo(ArrayList(heroes.size)) { it.copy() },
        minions.mapTo(ArrayList(minions.size)) { it.copy() },
        pieces.mapTo(ArrayList(pieces.size)) { it.copy() },
        LinkedHashMap(battleZones), LinkedHashMap(wavesLeft), LinkedHashMap(lifeCounters),
        effects.mapTo(ArrayList(effects.size)) { it.copy() },
        revealed, acting, winner, winReason, decisions, nextId, stamp, turnRecord.copy(), heroDefeatedThisRound,
        LinkedHashMap(pushDue), LinkedHashSet(lastWaveFlipped), LinkedHashMap(vars),
    )
}
