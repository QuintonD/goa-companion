package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText

/** Words of the game table and the play screens. Rulebook terms; page references where a rule speaks. */
object TableText {
    fun level(level: AiLevel): String = when (level) {
        AiLevel.EASY -> "Easy"
        AiLevel.NORMAL -> "Normal"
        AiLevel.HARD -> "Hard"
        AiLevel.EXPERT -> "Expert"
    }

    /** One line per level (PRODUCT §9). */
    fun levelDescription(level: AiLevel): String = when (level) {
        AiLevel.EASY -> "Makes visible mistakes and plays fast"
        AiLevel.NORMAL -> "Sensible, with a short look-ahead"
        AiLevel.HARD -> "Plans the turn against what you might have played"
        AiLevel.EXPERT -> "Searches longest"
    }

    fun controller(controller: Controller, human: Boolean): String = when {
        human -> "You"
        controller is Controller.Ai -> "Computer · ${level(controller.level)}"
        else -> "Player"
    }

    fun length(length: GameLength): String = when (length) {
        GameLength.QUICK -> "Quick"
        GameLength.LONG -> "Long"
    }

    fun phase(phase: GamePhase): String = when (phase) {
        GamePhase.PLAY_CARDS -> "Play cards"
        GamePhase.RESOLVE -> "Resolve"
        GamePhase.END_OF_ROUND -> "End of round"
        GamePhase.GAME_OVER -> "Game over"
    }

    fun stat(stat: Stat): String = when (stat) {
        Stat.ATTACK -> "Attack"
        Stat.DEFENSE -> "Defense"
        Stat.INITIATIVE -> "Initiative"
        Stat.MOVEMENT -> "Movement"
        Stat.RANGE -> "Range"
        Stat.RADIUS -> "Radius"
    }

    /** "+1 Attack, +1 Defense" (one item icon each, p.16). */
    fun items(items: List<Stat>): String = items.joinToString(", ") { "+1 ${stat(it)}" }

    /** The Primary action button: "Primary: Attack". */
    fun primary(card: Card?): String = when (card?.primary) {
        PrimaryAction.ATTACK -> "Primary: Attack"
        PrimaryAction.SKILL -> "Primary: Skill"
        PrimaryAction.DEFENSE -> "Primary: Defense"
        PrimaryAction.MOVEMENT -> "Primary: Movement"
        PrimaryAction.DEFENSE_SKILL -> "Primary: Skill"
        null -> "Primary action"
    }

    /** The printed values that matter for the Primary action: "Attack 4 · Range 2". */
    fun primaryDetail(card: Card): String? {
        val v = card.values
        return listOfNotNull(
            v.attack?.let { "Attack $it" },
            v.movement?.let { "Movement $it" }.takeIf { card.primary == PrimaryAction.MOVEMENT },
            v.range?.let { "Range $it" },
            v.radius?.let { "Radius $it" },
        ).joinToString(" · ").ifEmpty { "Its text applies (p.7)" }
    }

    /** What the panel's overline says the question is about. */
    fun kind(kind: DecisionKind): String = when (kind) {
        DecisionKind.PLAY_CARD -> "Play a card · p.7"
        DecisionKind.ACT_FIRST -> "Who acts first · p.7"
        DecisionKind.RESPAWN -> "Respawn · p.7"
        DecisionKind.CHOOSE_ACTION -> "Choose an action · p.9"
        DecisionKind.CHOOSE_SPACE -> "Choose a space"
        DecisionKind.CHOOSE_UNIT -> "Choose a unit"
        DecisionKind.CHOOSE_TOKEN -> "Choose a token"
        DecisionKind.CHOOSE_CARD -> "Choose a card"
        DecisionKind.DEFEND -> "Defend · p.14"
        DecisionKind.CHOOSE_OPTION -> "Choose one"
        DecisionKind.CONFIRM -> "Your choice"
        DecisionKind.CHOOSE_NUMBER -> "Choose a number"
        DecisionKind.REMOVE_MINION -> "Minion Battle · p.8"
        DecisionKind.MINION_RETURN -> "Minion back to the Battle Zone · p.18"
        DecisionKind.UPGRADE_CARD -> "Level up · p.15"
        DecisionKind.KEEP_UPGRADE -> "Level up · p.15"
    }

    /** The headline of a win, in the rulebook's words for the three ways to win (and card text). */
    fun winReason(reason: WinReason?): String = when (reason) {
        WinReason.THRONE -> "The lane was pushed into the enemy Throne Zone (p.8)"
        WinReason.LAST_PUSH -> "Last push: the last Wave counter was flipped (p.8)"
        WinReason.LIFE_COUNTERS -> "The other team spent its last Life counter (p.13)"
        WinReason.CARD_TEXT -> "A card's text won the game"
        null -> "The game is over"
    }

    fun winner(team: Team?, humanTeam: Team?): String = when {
        team == null -> "Game over"
        humanTeam == null -> "${BoardText.team(team)} wins"
        team == humanTeam -> "${BoardText.team(team)} wins: you won!"
        else -> "${BoardText.team(team)} wins"
    }
}
