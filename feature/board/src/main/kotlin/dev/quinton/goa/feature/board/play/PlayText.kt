package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText

/** Words of the game against the computer (PRODUCT §9). Rulebook terms throughout. */
object PlayText {
    fun level(level: AiLevel): String = when (level) {
        AiLevel.EASY -> "Easy"
        AiLevel.NORMAL -> "Normal"
        AiLevel.HARD -> "Hard"
        AiLevel.EXPERT -> "Expert"
    }

    /** One line per band (PRODUCT §9), with the two ends of the slider said apart (ADR-039). */
    fun strengthNote(strength: Int): String = when (AiStrength.coerce(strength)) {
        AiStrength.MIN -> "A gentle first game: slow to spot danger and quick to slip. Good for learning the rules."
        AiStrength.MAX -> "As strong as it gets on a phone. Expect to be punished."
        else -> levelNote(AiLevel.of(strength))
    }

    fun levelNote(level: AiLevel): String = when (level) {
        AiLevel.EASY -> "Makes visible mistakes and plays fast. Good for learning the turn."
        AiLevel.NORMAL -> "Plays sensibly and looks a short way ahead."
        AiLevel.HARD -> "Plans the turn against what you might have played."
        AiLevel.EXPERT -> "Searches the longest and remembers the cards you have shown. Expect to be punished."
    }

    /** The slider's value, "6 · Hard". */
    fun strengthValue(strength: Int): String = "$strength · ${level(AiLevel.of(strength))}"

    /** What TalkBack reads for the slider: "Strength 6 of 10, Hard". */
    fun strengthSpoken(strength: Int): String =
        "Strength $strength of ${AiStrength.MAX}, ${level(AiLevel.of(strength))}"

    /** A computer seat's band and strength, "Hard 6". */
    fun strength(computer: Controller.Ai): String = "${level(AiLevel.of(computer.strength))} ${computer.strength}"

    fun controller(controller: Controller): String = when (controller) {
        Controller.Human -> "You"
        is Controller.Ai -> "Computer · ${strength(controller)}"
    }

    fun pace(pace: AiPace): String = when (pace) {
        AiPace.SLOW -> "Slow"
        AiPace.NORMAL -> "Normal"
        AiPace.FAST -> "Fast"
    }

    fun length(length: GameLength): String = when (length) {
        GameLength.QUICK -> "Quick"
        GameLength.LONG -> "Long"
    }

    fun lengthNote(length: GameLength): String = when (length) {
        GameLength.QUICK -> "Fewer Wave and Life counters (p.5)."
        GameLength.LONG -> "More Wave and Life counters (p.23)."
    }

    /** How the game was won, in the rulebook's words (p.8, p.13). */
    fun winReason(reason: WinReason, winner: Team): String {
        val team = BoardText.team(winner)
        return when (reason) {
            WinReason.THRONE -> "$team pushed the lane into the enemy Throne Zone (p.8)."
            WinReason.LAST_PUSH -> "$team won the push that flipped the last Wave counter (p.8)."
            WinReason.LIFE_COUNTERS -> "The other team spent its last Life counter (p.13)."
            WinReason.CARD_TEXT -> "A card's text declared the win."
        }
    }

    fun winTitle(reason: WinReason): String = when (reason) {
        WinReason.THRONE -> "Throne Zone taken"
        WinReason.LAST_PUSH -> "Last push"
        WinReason.LIFE_COUNTERS -> "Out of Life counters"
        WinReason.CARD_TEXT -> "Won by card text"
    }

    fun stat(stat: Stat): String = when (stat) {
        Stat.ATTACK -> "Attack"
        Stat.DEFENSE -> "Defense"
        Stat.INITIATIVE -> "Initiative"
        Stat.MOVEMENT -> "Movement"
        Stat.RANGE -> "Range"
        Stat.RADIUS -> "Radius"
    }

    fun primary(primary: PrimaryAction?): String = when (primary) {
        PrimaryAction.ATTACK -> "Attack"
        PrimaryAction.SKILL -> "Skill"
        PrimaryAction.DEFENSE -> "Defense"
        PrimaryAction.MOVEMENT -> "Movement"
        PrimaryAction.DEFENSE_SKILL -> "Defense and Skill"
        null -> "Primary action"
    }

    /** An action button: "Attack 4 (primary)", "Movement 3", "Fast Travel", "Hold". */
    fun action(action: ActionType, card: Card?): String = when (action) {
        ActionType.PRIMARY -> {
            val name = primary(card?.primary)
            val value = when (card?.primary) {
                PrimaryAction.ATTACK -> card.values.attack
                PrimaryAction.MOVEMENT -> card.values.movement
                else -> null
            }
            listOfNotNull(name, value?.toString()).joinToString(" ") + " · primary"
        }

        ActionType.MOVEMENT -> listOfNotNull("Movement", card?.values?.movement?.toString()).joinToString(" ")

        ActionType.FAST_TRAVEL -> "Fast Travel"

        ActionType.ATTACK -> listOfNotNull("Attack", card?.values?.attack?.toString()).joinToString(" ")

        ActionType.CLEAR -> "Clear"

        ActionType.HOLD -> "Hold"
    }

    /** A plain button label for the choices the board and card pickers don't show. */
    fun button(choice: Choice): String = when (choice) {
        is Choice.Option -> choice.label
        is Choice.Number -> choice.value.toString()
        Choice.Yes -> "Yes"
        is Choice.Decline -> choice.label
        Choice.Pass -> "Pass"
        is Choice.Action -> action(choice.action, null)
        is Choice.Space -> choice.hex.toString()
        is Choice.Unit -> choice.unitId
        is Choice.Token -> choice.tokenId
        is Choice.Card -> choice.cardId
    }

    /** A hero's counter as words: "spellsRemoved" → "Spells removed" (it read "SPELLSREMOVED"). */
    fun counter(name: String): String = name
        .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2].lowercase()}" }
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

    fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }
}
