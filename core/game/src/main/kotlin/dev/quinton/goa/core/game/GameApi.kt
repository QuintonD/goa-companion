package dev.quinton.goa.core.game

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The public contract of the game engine (ADR-035). Everything the app (play screen, puzzles, AI
 * driver, persistence) needs goes through these types; engine internals stay behind them.
 *
 * CONTRACT RULES for everyone working in parallel:
 * - Add fields (with defaults), kinds and subclasses freely. Never rename or remove one without
 *   recording it in core/game/CONTRACT-CHANGES.md: other branches build against this file.
 * - Everything here is plain data (kotlinx.serialization), so a game can be saved mid-card.
 * - The engine is deterministic: the same start and the same answers give the same game. There is
 *   no randomness in the rules after setup (the Tie Breaker coin is placed at setup, p.5).
 * - Ids: a hero unit's id is its catalog hero id (a hero is picked at most once per game); other
 *   pieces get engine ids that stay stable for the whole game.
 */

// ---------------------------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------------------------

/** Quick / Long single-lane game (p.5 step 4–5, p.23); Double lane uses the p.20 counters. */
@Serializable
enum class GameLength {
    @SerialName("quick")
    QUICK,

    @SerialName("long")
    LONG,
}

/** How strong a computer-controlled hero plays. Easy makes visible mistakes; Expert searches hardest. */
@Serializable
enum class AiLevel {
    @SerialName("easy")
    EASY,

    @SerialName("normal")
    NORMAL,

    @SerialName("hard")
    HARD,

    @SerialName("expert")
    EXPERT,
}

/** Who answers a seat's decisions. */
@Serializable
sealed interface Controller {
    @Serializable
    @SerialName("human")
    data object Human : Controller

    @Serializable
    @SerialName("ai")
    data class Ai(val level: AiLevel) : Controller
}

/** One hero at the table. Seats are listed in pick order per team: the opening places them in it. */
@Serializable
data class SeatSetup(
    val heroId: String,
    val team: Team,
    val controller: Controller,
    /** p.20 Handicap card: replaces a basic card (usually Gold) of this hero. */
    val handicap: Boolean = false,
)

@Serializable
data class GameSetup(
    /** A bundled map id (`assets/catalog/maps/`), e.g. `forgotten-island`. */
    val mapId: String,
    val seats: List<SeatSetup>,
    val length: GameLength = GameLength.QUICK,
    /** The team whose symbol shows on the Tie Breaker coin after the setup flip (p.5 step 3). */
    val tieBreaker: Team,
)

// ---------------------------------------------------------------------------------------------
// Decisions: the engine asks, a human or the AI answers with one of the listed options.
// ---------------------------------------------------------------------------------------------

/**
 * A question the engine is waiting on. Exactly one is pending at a time (simultaneous steps such
 * as playing cards or levelling up are asked seat by seat; answers stay hidden until the reveal).
 * [options] is always complete and finite: the AI and the puzzle solver enumerate it, the UI
 * offers exactly these. Answering anything not in [options] is rejected.
 */
@Serializable
data class Decision(
    /** Increases by one for every decision of the game; answers name it so a stale tap is ignored. */
    val id: Long,
    /** The deciding hero (a team-level decision names the team's first hero in seat order). */
    val seat: String,
    val team: Team,
    val kind: DecisionKind,
    /** What is asked, in plain English, e.g. "Choose a space to move to (up to 3 spaces)". */
    val prompt: String,
    /** The card whose text (or whose action) asks this, if any. */
    val sourceCardId: String? = null,
    val options: List<Choice>,
    /** Extra facts the UI shows next to the options (attack total, defense previews, highlights). */
    val context: DecisionContext = DecisionContext(),
)

@Serializable
enum class DecisionKind {
    /** Turn step 1 (p.7): play one card facedown, or [Choice.Pass] with no cards in hand. */
    @SerialName("play_card")
    PLAY_CARD,

    /** Same-team initiative tie (p.7): which of the tied heroes acts first. Options are [Choice.Unit]. */
    @SerialName("act_first")
    ACT_FIRST,

    /** Hero respawn before acting (p.7): a space, or [Choice.Decline] to stay off the board. */
    @SerialName("respawn")
    RESPAWN,

    /** Perform one action (p.7, p.9): options are [Choice.Action]. */
    @SerialName("choose_action")
    CHOOSE_ACTION,

    /** A space: a move destination, a place/spawn space, a push direction's end space, etc. */
    @SerialName("choose_space")
    CHOOSE_SPACE,

    /** A unit (hero or minion): an attack target, a unit to move, push, swap… */
    @SerialName("choose_unit")
    CHOOSE_UNIT,

    /** A token on the board. */
    @SerialName("choose_token")
    CHOOSE_TOKEN,

    /** A card (discard, retrieve, swap, name…) — options are [Choice.Card]. */
    @SerialName("choose_card")
    CHOOSE_CARD,

    /** Attacked (p.14): discard a card from hand to defend, or [Choice.Decline] to be defeated. */
    @SerialName("defend")
    DEFEND,

    /** A bullet point of a "Choose one —" card (p.10), or any other labelled option. */
    @SerialName("choose_option")
    CHOOSE_OPTION,

    /** An optional step ("you may", "if able", "up to"): [Choice.Yes] / [Choice.Decline]. */
    @SerialName("confirm")
    CONFIRM,

    /** A number (a distance "up to N", a count). Options are [Choice.Number]. */
    @SerialName("choose_number")
    CHOOSE_NUMBER,

    /** Minion Battle (p.8): the losing team picks which minion to remove next (Heavy last). */
    @SerialName("remove_minion")
    REMOVE_MINION,

    /** A minion back into its Battle Zone (p.18) when several shortest paths/spaces exist. */
    @SerialName("minion_return")
    MINION_RETURN,

    /** Level Up (p.15): which lowest-tier card to upgrade. Options are [Choice.Card]. */
    @SerialName("upgrade_card")
    UPGRADE_CARD,

    /** Level Up (p.15): which of the two next-tier cards goes to hand (the other becomes an item). */
    @SerialName("keep_upgrade")
    KEEP_UPGRADE,
}

/** One answer to a [Decision]. */
@Serializable
sealed interface Choice {
    @Serializable
    @SerialName("space")
    data class Space(val hex: Hex) : Choice

    @Serializable
    @SerialName("unit")
    data class Unit(val unitId: String) : Choice

    @Serializable
    @SerialName("token")
    data class Token(val tokenId: String) : Choice

    @Serializable
    @SerialName("card")
    data class Card(val cardId: String) : Choice

    @Serializable
    @SerialName("action")
    data class Action(val action: ActionType) : Choice

    /** A labelled option; [index] is its position in the card's list (bullet points top to bottom). */
    @Serializable
    @SerialName("option")
    data class Option(val index: Int, val label: String) : Choice

    @Serializable
    @SerialName("number")
    data class Number(val value: Int) : Choice

    @Serializable
    @SerialName("yes")
    data object Yes : Choice

    /** Don't (optional step), don't defend, don't respawn, stop moving… [label] says which. */
    @Serializable
    @SerialName("decline")
    data class Decline(val label: String = "Skip") : Choice

    /** No card to play (p.7: "you must announce that you are passing"). */
    @Serializable
    @SerialName("pass")
    data object Pass : Choice
}

/** The actions a played card offers (p.7, p.9, pp.11–14). Defense is only ever a response (p.14). */
@Serializable
enum class ActionType {
    /** The card's primary action: its text applies (p.7). */
    @SerialName("primary")
    PRIMARY,

    @SerialName("movement")
    MOVEMENT,

    /** Replaces the whole Movement action (p.11). */
    @SerialName("fast_travel")
    FAST_TRAVEL,

    /** A secondary Attack action (printed on a few cards, e.g. Mrak). */
    @SerialName("attack")
    ATTACK,

    /** Replaces the whole Attack action (p.13): remove any number of adjacent tokens. */
    @SerialName("clear")
    CLEAR,

    /** Do nothing (p.12). Always available. */
    @SerialName("hold")
    HOLD,
}

@Serializable
data class DecisionContext(
    /** DEFEND: the attacker, the announced Attack total and whether the attack is ranged. */
    val attackerId: String? = null,
    val attackTotal: Int? = null,
    val rangedAttack: Boolean? = null,
    /**
     * DEFEND: the Defense total each hand card would give (card value + items + minion defense
     * modifiers + text bonuses the engine can compute up front), or null when the card text
     * decides only during resolution (Block conditions, choices).
     */
    val defensePreview: Map<String, Int?> = emptyMap(),
    /** The minion defense modifier counted for this attack (p.14), already inside [defensePreview]. */
    val minionModifier: Int? = null,
    /** Spaces the UI should show as context (e.g. a card's range) besides the options. */
    val highlight: List<Hex> = emptyList(),
    /** The unit the question is about (the unit being moved/pushed, the attack target…). */
    val subjectId: String? = null,
    /** CHOOSE_SPACE for a move: the path the engine will use to each option, when it matters. */
    val paths: Map<String, List<Hex>> = emptyMap(),
)

// ---------------------------------------------------------------------------------------------
// What a seat can see (p.4 Hidden Information). The UI renders only this.
// ---------------------------------------------------------------------------------------------

@Serializable
enum class GamePhase {
    @SerialName("play_cards")
    PLAY_CARDS,

    @SerialName("resolve")
    RESOLVE,

    @SerialName("end_of_round")
    END_OF_ROUND,

    @SerialName("game_over")
    GAME_OVER,
}

@Serializable
enum class WinReason {
    /** The push made the enemy Throne Zone the Battle Zone (p.8 step 2). */
    @SerialName("throne")
    THRONE,

    /** The last Wave counter flipped (p.8 step 1). */
    @SerialName("last_push")
    LAST_PUSH,

    /** The other team spent its last Life counter (p.13). */
    @SerialName("life_counters")
    LIFE_COUNTERS,

    /** A card says so ("you alone win the game": Cutter; "your team wins the game": Gydion's Wish). */
    @SerialName("card_text")
    CARD_TEXT,
}

/** A card as a viewer may see it: [cardId] null when it is facedown/hidden to this viewer. */
@Serializable
data class CardView(
    val cardId: String?,
    val facedown: Boolean = false,
    /** A resolved or discarded card with an Active effect shown on its side (p.7). */
    val active: Boolean = false,
    /** The Turn slot 1..4 of a resolved card (p.7), else null. */
    val slot: Int? = null,
)

@Serializable
data class HeroView(
    val heroId: String,
    val team: Team,
    val controller: Controller,
    /** Null while off the board (defeated, not yet respawned). */
    val at: Hex?,
    val level: Int,
    val coins: Int,
    /** The viewer's own hand in full; another hero's hand only as a count ([handCount]). */
    val hand: List<String>?,
    val handCount: Int,
    /** This turn's played card: hidden until the reveal unless it is the viewer's. */
    val played: CardView?,
    /** More played cards this turn, when an effect allows it (Emmitt's Ultimate); same hiding as [played]. */
    val alsoPlayed: List<CardView> = emptyList(),
    /** Resolved cards on the dashboard's Turn slots, turn 1..4 (open information). */
    val resolved: List<CardView>,
    val discard: List<CardView>,
    /** Tucked item cards (open information, p.16). */
    val items: List<String>,
    val ultimate: Boolean,
    /** Markers on this hero (poison, bounty, runes…) by marker kind, with the giving hero. */
    val markers: List<MarkerView> = emptyList(),
    /** Card ids whose Active effect is in force (This turn / This round / Next turn…). */
    val activeEffects: List<String> = emptyList(),
    /** Hero-specific public counters a card or the UI shows (e.g. charges), by name. */
    val counters: Map<String, Int> = emptyMap(),
)

@Serializable
data class MarkerView(val kind: String, val fromHeroId: String?)

@Serializable
data class MinionView(
    val id: String,
    val team: Team,
    val type: MinionType,
    val at: Hex,
    /** The Lane the minion is bound to (p.20). */
    val lane: String,
    /** p.13 Heavy Minion Immunity (or any other immunity) in force right now. */
    val immune: Boolean = false,
)

/** A token or other non-hero piece on the board (tokens, Pyro, zombies…), labelled for drawing. */
@Serializable
data class PieceView(
    val id: String,
    /** Catalog icon name when there is one (`token_rock`, `token_tree`…), else a short kind. */
    val kind: String,
    val label: String,
    val at: Hex,
    val ownerHeroId: String? = null,
    val team: Team? = null,
    /** Units (minions/heroes) block and can be attacked; tokens are obstacles only (p.19). */
    val isUnit: Boolean = false,
)

@Serializable
data class GameView(
    val mapId: String,
    /** The viewing seat (null = spectator: every hand hidden). */
    val viewer: String?,
    val round: Int,
    /** 1..4 (p.7). */
    val turn: Int,
    val phase: GamePhase,
    val heroes: List<HeroView>,
    val minions: List<MinionView>,
    val pieces: List<PieceView>,
    /** Current Battle Zone per Lane id. */
    val battleZones: Map<String, String>,
    /** Wave counters left per Lane id. */
    val wavesLeft: Map<String, Int>,
    val lifeCounters: Map<Team, Int>,
    val tieBreaker: Team,
    /** After the reveal: hero ids in the order they will act, recomputed after every resolution. */
    val initiativeOrder: List<String> = emptyList(),
    /** The hero acting now, if any. */
    val acting: String? = null,
    val winner: Team? = null,
    val winReason: WinReason? = null,
)

// ---------------------------------------------------------------------------------------------
// Events: what happened, for the log, animation, TalkBack and the coach.
// ---------------------------------------------------------------------------------------------

/** Something that happened. [text] is a full English sentence ready for the log and TalkBack. */
@Serializable
sealed interface GameEvent {
    val text: String

    @Serializable
    @SerialName("round_started")
    data class RoundStarted(val round: Int, override val text: String) : GameEvent

    @Serializable
    @SerialName("turn_started")
    data class TurnStarted(val round: Int, val turn: Int, override val text: String) : GameEvent

    @Serializable
    @SerialName("cards_revealed")
    data class CardsRevealed(val played: Map<String, String?>, override val text: String) : GameEvent

    @Serializable
    @SerialName("action")
    data class ActionChosen(val heroId: String, val cardId: String, val action: ActionType, override val text: String) :
        GameEvent

    @Serializable
    @SerialName("moved")
    data class Moved(val unitId: String, val path: List<Hex>, override val text: String) : GameEvent

    @Serializable
    @SerialName("placed")
    data class Placed(val pieceId: String, val from: Hex?, val to: Hex, override val text: String) : GameEvent

    @Serializable
    @SerialName("swapped")
    data class Swapped(val a: String, val b: String, override val text: String) : GameEvent

    @Serializable
    @SerialName("pushed")
    data class Pushed(val unitId: String, val from: Hex, val to: Hex, override val text: String) : GameEvent

    @Serializable
    @SerialName("attacked")
    data class Attacked(val attackerId: String, val targetId: String, val total: Int, override val text: String) :
        GameEvent

    @Serializable
    @SerialName("defended")
    data class Defended(
        val heroId: String,
        val cardId: String?,
        val total: Int?,
        val success: Boolean,
        override val text: String,
    ) : GameEvent

    @Serializable
    @SerialName("defeated")
    data class Defeated(val unitId: String, val byHeroId: String?, override val text: String) : GameEvent

    @Serializable
    @SerialName("removed")
    data class Removed(val pieceId: String, override val text: String) : GameEvent

    @Serializable
    @SerialName("coins")
    data class CoinsGained(val heroId: String, val amount: Int, override val text: String) : GameEvent

    @Serializable
    @SerialName("cards")
    data class CardsChanged(val heroId: String, override val text: String) : GameEvent

    @Serializable
    @SerialName("lane_pushed")
    data class LanePushed(val lane: String, val winner: Team, val newBattleZone: String, override val text: String) :
        GameEvent

    @Serializable
    @SerialName("life")
    data class LifeSpent(val team: Team, val amount: Int, val left: Int, override val text: String) : GameEvent

    @Serializable
    @SerialName("level_up")
    data class LevelledUp(val heroId: String, val level: Int, override val text: String) : GameEvent

    @Serializable
    @SerialName("game_over")
    data class GameOver(val winner: Team, val reason: WinReason, override val text: String) : GameEvent

    /** Anything else worth a log line (an Active effect starting or ending, a minion battle…). */
    @Serializable
    @SerialName("note")
    data class Note(override val text: String) : GameEvent
}

// ---------------------------------------------------------------------------------------------
// The session: one game in progress.
// ---------------------------------------------------------------------------------------------

/** Result of [GameSession.answer]. */
sealed interface AnswerResult {
    /** Accepted; [events] happened as a result (until the next decision or the end of the game). */
    data class Accepted(val events: List<GameEvent>) : AnswerResult

    /** Not one of the pending options, or a stale decision id; nothing changed. */
    data class Rejected(val reason: String) : AnswerResult
}

/**
 * One game. Not thread-safe: the app drives it from one coroutine (off the main thread for the AI).
 * Created by [GameEngine].
 */
interface GameSession {
    val setup: GameSetup

    /** The pending decision; null only once the game is over. */
    val pending: Decision?

    /** What [seat] may see right now (null = spectator). */
    fun view(seat: String?): GameView

    /** Every event so far, oldest first. */
    val events: List<GameEvent>

    fun answer(decisionId: Long, choice: Choice): AnswerResult

    /** Plain data to persist and later [GameEngine.restore]; includes the event log. */
    fun save(): SavedGame

    /** An independent copy (AI search, puzzle solver, "what if"). Answers to it never touch this one. */
    fun fork(): GameSession

    /**
     * A fork in which everything [viewer]'s team cannot see (other teams' hands, facedown played
     * cards) is replaced by a sample consistent with what it can see, drawn with [seed]. The AI
     * searches on these, so it never reads a human's hidden cards.
     */
    fun determinize(viewer: Team, seed: Long): GameSession

    /** Undo the last answer of a [Controller.Human] seat and everything after it, if any. */
    fun undoToLastHumanDecision(): Boolean
}

/** A saved game: setup plus whatever the engine needs to rebuild the exact same session. */
@Serializable
data class SavedGame(
    val engineVersion: Int,
    val setup: GameSetup,
    /** Engine-private state (checkpoint and answers), opaque to the app. */
    val payload: String,
    val savedAt: Long = 0,
)

// ---------------------------------------------------------------------------------------------
// Puzzles (ADR-035): a position, a goal, nudges and an explanation. Defined in code, solver-checked.
// ---------------------------------------------------------------------------------------------

/** What the player must achieve. The solver proves it against the best replies of the other side. */
@Serializable
sealed interface PuzzleGoal {
    /** One sentence for the goal banner, e.g. "Defeat Brogan this turn". */
    val text: String

    @Serializable
    @SerialName("win")
    data class WinGame(override val text: String) : PuzzleGoal

    @Serializable
    @SerialName("defeat")
    data class DefeatHero(val heroId: String, val by: Deadline, override val text: String) : PuzzleGoal

    @Serializable
    @SerialName("push")
    data class PushLane(val by: Deadline, override val text: String) : PuzzleGoal

    /** None of the player's heroes is defeated, and the lane is not pushed against them, until [by]. */
    @Serializable
    @SerialName("survive")
    data class Survive(val by: Deadline, override val text: String) : PuzzleGoal

    @Serializable
    @SerialName("coins")
    data class GainCoins(val atLeast: Int, val by: Deadline, override val text: String) : PuzzleGoal
}

@Serializable
enum class Deadline {
    @SerialName("turn")
    END_OF_TURN,

    @SerialName("round")
    END_OF_ROUND,
}

/** Puzzle metadata the list and the result screen show. The position itself is built in code. */
@Serializable
data class PuzzleInfo(
    /** Permanent id, `p01`…`p50`; progress is stored by it. */
    val id: String,
    val number: Int,
    val title: String,
    /** 1 (hard) .. 3 (hardest). Every puzzle is meant to be difficult. */
    val difficulty: Int,
    /** What it teaches, e.g. "Initiative", "Minion defense modifiers", "Pushing the lane". */
    val theme: String,
    val heroes: List<String>,
    val goal: PuzzleGoal,
    /** Gentle → strong, at least three; the last one names the first move. */
    val nudges: List<String>,
    /** Shown at the end, solved or not: why the solution works, with page references. */
    val explanation: String,
    /** Rulebook pages the puzzle leans on, e.g. "p.14 Minion Defense Modifiers". */
    val rules: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Entry points implemented by the engine, the AI and the puzzle runtime.
// ---------------------------------------------------------------------------------------------

/**
 * Creates and restores games. Implemented in this module (`GameEngine`, built from the loaded
 * catalog and maps). Only heroes in [playableHeroes] may be seated: a hero is playable once every
 * card of it (deck, Handicap, Ultimate, extras) has a tested implementation.
 */
interface GameFactory {
    val playableHeroes: Set<String>

    /** Maps a game can be played on (verified single-lane first). */
    val playableMaps: List<String>

    fun newGame(setup: GameSetup): GameSession

    fun restore(saved: SavedGame): GameSession
}

/** Answers decisions for a [Controller.Ai] seat. Blocking; checks [cancelled] while it searches. */
interface AiPlayer {
    fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean = { false }): Choice
}

/** A gentle nudge for a human about to answer, or a turning point found after the game. */
@Serializable
data class CoachNote(
    val text: String,
    /** Why, in one or two sentences, with a page reference when a rule is involved. */
    val detail: String = "",
    val decisionId: Long? = null,
    val round: Int? = null,
    val turn: Int? = null,
)

/** Learning help in games against the AI (ADR-035); switchable, never answers for the player. */
interface Coach {
    /** A nudge when [choice] looks clearly worse than another option, else null. */
    fun nudge(
        session: GameSession,
        decision: Decision,
        choice: Choice,
        cancelled: () -> Boolean = { false },
    ): CoachNote?

    /** After the game: the few moments that decided it, oldest first. */
    fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean = { false }): List<CoachNote>
}

@Serializable
enum class PuzzleStatus {
    @SerialName("playing")
    PLAYING,

    /** The goal is met. */
    @SerialName("solved")
    SOLVED,

    /** The last answer lost the forced line: the goal can no longer be reached against best play. */
    @SerialName("missed")
    MISSED,
}

/**
 * A puzzle being played. Decisions of the player's seats wait in [GameSession.pending] for the
 * player; every other decision is answered at once by the solver's best reply. After each player
 * answer the runtime checks that the goal is still reachable (alternative solutions count).
 */
interface PuzzleSession {
    val info: PuzzleInfo
    val game: GameSession

    /** The heroes the player controls. */
    val playerSeats: Set<String>
    val status: PuzzleStatus

    fun answer(decisionId: Long, choice: Choice): AnswerResult

    /** Back to the starting position (attempts are counted by the app). */
    fun restart(): PuzzleSession

    /** Undo the player's last answer (after a miss, to try again from just before it). */
    fun undo(): Boolean

    /** The solver's choice for the pending player decision (the "show me" nudge / solution replay). */
    fun bestChoice(cancelled: () -> Boolean = { false }): Choice?
}

/** The bundled puzzles in order. */
interface PuzzleBook {
    val puzzles: List<PuzzleInfo>

    fun start(puzzleId: String): PuzzleSession
}
