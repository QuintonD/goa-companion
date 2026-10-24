package dev.quinton.goa.core.game.effects

import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Marker
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/*
 * The hooks card effects plug into (EFFECTS.md "Hooks"). A [Behavior] is a stateless object
 * registered under a key (a card id, a token kind, a marker kind); the engine finds every behaviour
 * in force ([Src]) and asks it at fixed points of the rules. All state lives in the game state:
 * [ActiveEffect.data], [PieceState.data], [dev.quinton.goa.core.game.engine.HeroState.vars].
 *
 * Every method has a neutral default, so a behaviour overrides only what its text needs.
 * Queries must be pure (no state change, no decisions); triggers and replacements may change the
 * state and ask decisions through [Game].
 */

/** Where a behaviour in force comes from. */
enum class SrcKind {
    /** An Active effect of a card (This turn / This round / Next turn / End of turn …). */
    EFFECT,

    /** An unlocked Ultimate: "always in effect (as long as your hero is on the board)" (p.16). */
    ULTIMATE,

    /** A marker on a hero. */
    MARKER,

    /** A token or other piece on the board. */
    PIECE,

    /** Always on for its owner, whatever the card state (e.g. a hero rule printed on the dashboard). */
    HERO,
}

/**
 * A behaviour in force: [owner] is the hero whose card, Ultimate, marker or piece it is (the giver
 * for a marker); [holder] the hero carrying a marker.
 */
class Src(
    val kind: SrcKind,
    val key: String,
    val owner: String?,
    val cardId: String? = null,
    val effect: ActiveEffect? = null,
    val marker: Marker? = null,
    val holder: String? = null,
    val piece: PieceState? = null,
)

/** The stats a card or hero has (p.9 icons). */
typealias StatKind = Stat

/** "What is the value of [stat] on [cardId] for [unitId] right now?" ([Behavior.statDelta]). */
class StatQuery(
    val unitId: String,
    val stat: StatKind,
    /** The card the value is read from; null = the unit itself (e.g. a radius of an Ultimate). */
    val cardId: String?,
    /** The action being performed with it, when there is one. */
    val action: ActionKind? = null,
)

/** "May [heroId] perform [kind] (primary or not) on [cardId]?" */
class ActionQuery(
    val heroId: String,
    val cardId: String?,
    val kind: ActionKind,
    val primary: Boolean,
    /** A basic action: any action on a basic (Gold or Silver) card (p.9). */
    val basic: Boolean,
    /** The colour of [cardId] as it is now (null when facedown or none). */
    val cardColor: dev.quinton.goa.core.model.catalog.CardColor?,
)

/** Who is acting on whom, for immunity and targeting (p.10 "Picking Targets", p.18 "Immune"). */
class AffectQuery(
    /** The unit or token affected. */
    val targetId: String,
    /** The hero performing the action (null: a rule, e.g. Minion Battle). */
    val actorId: String?,
    val kind: ActionKind,
    val primary: Boolean,
    val basic: Boolean,
    /** The card whose action (or Active effect) it is. */
    val cardId: String?,
    /** The attack is ranged (attacks only). */
    val ranged: Boolean = false,
    /** True when checked for an Active effect of that action rather than the action itself. */
    val fromEffect: Boolean = false,
)

/** How a unit or token is being displaced (p.18–19: move, push, place, swap). */
enum class Displacement { MOVE, PUSH, PLACE, SWAP }

class DisplaceQuery(val unitId: String, val how: Displacement, val byHeroId: String?)

/** "Does [hex] count as an obstacle for [moverId] while [actorId] performs an action?" */
class SpaceQuery(val hex: Hex, val moverId: String?, val actorId: String?)

/** Nebkher-style partitions: may [actorId] interact with [hex] (or the object there)? */
class InteractQuery(val actorId: String, val hex: Hex)

/** How a movement may pass obstacles ([dev.quinton.goa.core.domain.board.BoardRules.MovePath]). */
enum class PassMode { NORMAL, IGNORE_OBSTACLES }

class MoveQuery(
    val unitId: String,
    /** True for a Movement action (p.11), false for movement in card text. */
    val movementAction: Boolean,
    val actorId: String?,
)

/** A participant of a Minion Battle (p.8) on [lane]: a minion, or something that counts as one. */
class BattleParticipant(
    val id: String,
    val team: Team,
    val type: MinionType,
    /** Removing it means something else (e.g. Brogan: "lose the push instead"). */
    val virtual: Boolean = false,
)

class MinionBattleQuery(val lane: String, val participants: MutableList<BattleParticipant>)

/** "Choose one / up to N times" limits (p.10 "Modal Actions"). */
class ChoiceQuery(val heroId: String, val cardId: String?, val printed: Int, val upTo: Boolean, val basic: Boolean)

/** Life counters a defeated hero's team spends (p.13), before extra costs. */
class LifeQuery(val heroId: String, val byHeroId: String?, val base: Int)

/** Things that happen; behaviours react in [Behavior.on]. */
sealed interface Trigger {
    data class TurnStarted(val round: Int, val turn: Int) : Trigger

    /** p.18 "Next turn, after playing cards": after all cards are revealed. */
    data object CardsRevealed : Trigger

    data class BeforeAction(
        val heroId: String,
        val cardId: String?,
        val kind: ActionKind,
        val primary: Boolean,
        val basic: Boolean,
    ) : Trigger

    data class AfterAction(
        val heroId: String,
        val cardId: String?,
        val kind: ActionKind,
        val primary: Boolean,
        val basic: Boolean,
        val repeat: Boolean,
        val targets: List<String>,
    ) : Trigger

    /** A card was placed on its Turn slot (p.7 "Resolve Your Card"). */
    data class CardResolved(val heroId: String, val cardId: String) : Trigger

    data class MinionDefeated(
        val minionId: String,
        val team: Team,
        val type: MinionType,
        val at: Hex?,
        val byHeroId: String?,
    ) : Trigger

    data class HeroDefeated(val heroId: String, val byHeroId: String?) : Trigger

    /** A unit or token moved (movement, push, card text) along [path] (start excluded). */
    data class Moved(val id: String, val path: List<Hex>, val byHeroId: String?, val push: Boolean) : Trigger

    data class Pushed(val id: String, val byHeroId: String?, val spaces: Int, val stoppedByObstacle: Boolean) : Trigger

    data class Placed(val id: String, val from: Hex?, val to: Hex, val byHeroId: String?) : Trigger

    data class Swapped(val a: String, val b: String, val byHeroId: String?) : Trigger

    data class MarkerGiven(val heroId: String, val kind: String, val byHeroId: String?) : Trigger

    data class CardDiscarded(val heroId: String, val cardId: String, val fromResolved: Boolean) : Trigger

    data class AttackDone(val attackerId: String, val targetId: String, val defeated: Boolean) : Trigger

    data object TurnEnding : Trigger

    data object RoundEnding : Trigger
}

/** What a minion's defeat turns into (p.13), decided by [Behavior.replace]. */
enum class DefeatOutcome {
    /** Defeated and removed as usual. */
    NORMAL,

    /** "The minion is not removed" but the defeat stands (coins are still gained: Brogan's Shield). */
    NOT_REMOVED,

    /** No defeat at all ("remove the Totem instead": no coins, Tali FAQ). */
    PREVENTED,
}

sealed interface Replacement {
    /** A minion is about to be defeated by [byHeroId]. */
    data class MinionDefeat(val minionId: String, val byHeroId: String?) : Replacement

    /** A Minion Battle participant of [owner] would be removed. */
    data class BattleRemoval(val participantId: String, val lane: String) : Replacement

    /** [heroId] would discard [cardId] from hand (Mrak: "you may discard this card instead"). */
    data class Discard(val heroId: String, val cardId: String) : Replacement
}

/** Replacement answer for [Replacement.Discard]: the card to discard instead (null = no change). */
class ReplacementResult(
    val outcome: DefeatOutcome = DefeatOutcome.NORMAL,
    val handled: Boolean = false,
    val cardId: String? = null,
)

/**
 * The hook interface. Override only what the card text needs; defaults change nothing.
 * The engine checks immunity of the affected unit to the source's action for Active effects only
 * when the behaviour calls [Game.affectedBy]; query results are combined as documented per method.
 */
@Suppress("TooManyFunctions")
interface Behavior {
    // ---- values ------------------------------------------------------------------------------

    /** Added to a value (attack, defense, initiative, movement, range, radius). Summed over sources. */
    fun statDelta(g: Game, src: Src, q: StatQuery): Int = 0

    /** Replaces the printed value (p.9; e.g. Hanu's "printed Initiative ... to 11"). Last source wins. */
    fun printedValue(g: Game, src: Src, q: StatQuery): Int? = null

    /** Items count this many times (Min: "Double your item bonuses"). Highest wins. */
    fun itemMultiplier(g: Game, src: Src, heroId: String): Int = 1

    /** Added to a defender's Defense total like a minion modifier (e.g. Barrier tokens). Summed. */
    fun defenseBonus(g: Game, src: Src, defenderId: String, attackerId: String): Int = 0

    // ---- permissions (any source saying "forbid" wins) ------------------------------------------

    fun forbidsAction(g: Game, src: Src, q: ActionQuery): Boolean = false

    /** Most spaces [MoveQuery.unitId] may move with a Movement action, checked at its start (p.11). Lowest wins. */
    fun movementCap(g: Game, src: Src, q: MoveQuery): Int? = null

    fun forbidsFastTravel(g: Game, src: Src, heroId: String): Boolean = false

    fun forbidsDisplacement(g: Game, src: Src, q: DisplaceQuery): Boolean = false

    fun forbidsRepeat(g: Game, src: Src, heroId: String): Boolean = false

    /** p.18 "Immune": the target cannot be targeted or affected by this action. */
    fun immune(g: Game, src: Src, q: AffectQuery): Boolean = false

    /** The target may not be targeted, without being immune (e.g. Min's Smoke bomb blocks a line). */
    fun forbidsTarget(g: Game, src: Src, q: AffectQuery): Boolean = false

    /** [SpaceQuery.hex] counts as an obstacle (Static Barrier, Spinning Blade…). */
    fun countsAsObstacle(g: Game, src: Src, q: SpaceQuery): Boolean = false

    /** The unit or object at [hex] counts as terrain (Stone Gaze). */
    fun countsAsTerrain(g: Game, src: Src, hex: Hex): Boolean = false

    fun forbidsInteraction(g: Game, src: Src, q: InteractQuery): Boolean = false

    /** How the moving unit may pass obstacles (p.18 "Ignoring Obstacles"); [PassMode.IGNORE_OBSTACLES] wins. */
    fun passMode(g: Game, src: Src, q: MoveQuery): PassMode? = null

    /** Heroes with lower initiative act first (Emmitt). */
    fun reversesInitiative(g: Game, src: Src): Boolean = false

    /** Extra cards [heroId] may play this turn (Emmitt's Ultimate). Summed. */
    fun extraCardsToPlay(g: Game, src: Src, heroId: String): Int = 0

    /** Adjust a Minion Battle (p.8): add participants that count as minions, drop ones that don't count. */
    fun minionBattle(g: Game, src: Src, q: MinionBattleQuery) {}

    /** A piece counts as a minion of a team and type for "minion" checks (Nebkher's illusions). */
    fun pieceCountsAsMinion(g: Game, src: Src, piece: PieceState, actorId: String?): Pair<Team, MinionType>? = null

    /** A different limit for "choose one / up to N times" (Misa, Whisper, Mortimer Ultimates). Highest wins. */
    fun choiceLimit(g: Game, src: Src, q: ChoiceQuery): Int? = null

    /** The hero who answers [heroId]'s decisions now (Hanu's Ultimate); first source wins. */
    fun controls(g: Game, src: Src, heroId: String): String? = null

    /** The token kind actually placed when [byHeroId] places [kind] (NebKher: "place Illusion tokens instead"). */
    fun tokenKind(g: Game, src: Src, kind: String, byHeroId: String?): String? = null

    /** Extra Life counters spent for a defeat (Bounty). Summed. */
    fun extraLifeCost(g: Game, src: Src, q: LifeQuery): Int = 0

    /** Cards outside the hand [heroId] may defend with "as if it was in your hand" (Mrak). */
    fun extraDefenseCards(g: Game, src: Src, heroId: String): List<String> = emptyList()

    // ---- triggers and replacements ------------------------------------------------------------

    fun on(g: Game, src: Src, t: Trigger) {}

    /** "End of turn:" / "End of round:" / "Next turn, after playing cards:" text, applied once (p.17–18). */
    fun applyTimed(g: Game, src: Src) {}

    fun replace(g: Game, src: Src, r: Replacement): ReplacementResult = ReplacementResult()
}

/** A behaviour with no hooks (a card whose text has no Active effect). */
object NoBehavior : Behavior
