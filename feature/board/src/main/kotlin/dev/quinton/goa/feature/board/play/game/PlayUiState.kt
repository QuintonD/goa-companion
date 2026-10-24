package dev.quinton.goa.feature.board.play.game

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.table.InitiativeUi
import dev.quinton.goa.feature.board.play.table.TableHero
import dev.quinton.goa.feature.board.play.table.TableState

/** A game against the computer on screen. */
sealed interface PlayUiState {
    data object Loading : PlayUiState

    /**
     * The saved game can't be played: gone, unreadable, saved by other rules ([otherRules]) or the
     * engine refused it. Offered for deletion, never a crash.
     */
    @Immutable
    data class Unplayable(val gameId: String, val message: String, val otherRules: Boolean = false) : PlayUiState

    @Immutable
    data class Playing(
        val table: TableState,
        val options: PlayOptions,
        /** The computer answering now, and the last thing that happened. */
        val activity: Activity?,
        /** A moment that waits for "Continue": the reveal, the end of a round. */
        val interlude: Interlude?,
        /** The coach's nudge on the choice the player is about to make. */
        val coach: CoachPrompt?,
        val canTakeBack: Boolean,
        val takeBacks: Int,
        /** Every event so far, oldest first (the log). */
        val log: List<String>,
        /** New events for TalkBack. */
        val announcement: Announcement?,
        /** Set once the game has ended. */
        val over: GameOverUi?,
        /** The player's choice is being checked or sent (buttons wait). */
        val busy: Boolean = false,
    ) : PlayUiState
}

/** Something to read out once ([id] changes when there is something new). */
@Immutable
data class Announcement(val id: Long, val text: String)

/**
 * The computer at work. [hero] answers [prompt] now (null between answers); [thinking] once it has
 * taken a while; [waitingForOthers] during card play, when the others play facedown (p.7);
 * [last] the latest log lines, shown for a moment before the next action.
 */
@Immutable
data class Activity(
    val hero: TableHero?,
    val prompt: String?,
    val thinking: Boolean,
    val waitingForOthers: Boolean,
    val last: List<String>,
)

/** A moment of the game that waits for the player. */
@Immutable
sealed interface Interlude {
    /** The reveal (p.7): every played card with its initiative, in the order the heroes will act. */
    data class Reveal(val order: List<InitiativeUi>, val lines: List<String>) : Interlude

    /** The end of a round (p.8 and pp.15–16): what happened, step by step, and each level up. */
    data class RoundEnd(val round: Int, val lines: List<String>, val levelUps: List<TableHero>) : Interlude
}

/** The coach suggests rethinking [choice] (asked of decision [decisionId]); never chooses for the player. */
@Immutable
data class CoachPrompt(val decisionId: Long, val choice: Choice, val choiceLabel: String, val note: CoachNote)

/** The end: who won and how, a short summary and the turning points. */
@Immutable
data class GameOverUi(
    val winner: Team?,
    val reason: WinReason?,
    /** The engine's sentence for the win. */
    val story: String?,
    val humanTeam: Team?,
    val rounds: Int,
    val heroes: List<HeroSummary>,
    val pushes: Map<Team, Int>,
    val takeBacks: Int,
    val coachOn: Boolean,
    /** Null while the coach is still looking (or off). */
    val turningPoints: List<CoachNote>?,
)

/** One hero's game in numbers (from the log). */
@Immutable
data class HeroSummary(
    val hero: TableHero,
    val defeats: Int,
    val defeated: Int,
    val minions: Int,
    val coins: Int,
)

/** The game in numbers, counted from the log. */
internal fun summarize(events: List<GameEvent>, heroes: List<TableHero>): Pair<List<HeroSummary>, Map<Team, Int>> {
    val ids = heroes.map { it.id }.toSet()
    val defeats = events.filterIsInstance<GameEvent.Defeated>()
    val coins = events.filterIsInstance<GameEvent.CoinsGained>()
    val summaries = heroes.map { hero ->
        HeroSummary(
            hero = hero,
            defeats = defeats.count { it.byHeroId == hero.id && it.unitId in ids },
            defeated = defeats.count { it.unitId == hero.id },
            minions = defeats.count { it.byHeroId == hero.id && it.unitId !in ids },
            coins = coins.filter { it.heroId == hero.id }.sumOf { it.amount },
        )
    }
    val pushes = events.filterIsInstance<GameEvent.LanePushed>().groupingBy { it.winner }.eachCount()
    return summaries to pushes
}
