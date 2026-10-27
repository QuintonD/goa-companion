package dev.quinton.goa.feature.board.play.game

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.table.LogLine
import dev.quinton.goa.feature.board.play.table.ResolutionFlow
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi

/** Whose move it is, as the panel shows it. */
sealed interface TurnStatus {
    /** The pending question is the person's. */
    data object YourTurn : TurnStatus

    /** A computer seat is answering; [thinking] once it has taken a while. */
    data class Computer(val heroId: String, val thinking: Boolean = false) : TurnStatus

    /** Simultaneous card play (p.7): the person's card is down, the computer's heroes still choose theirs. */
    data class Waiting(val heroIds: List<String>) : TurnStatus

    data object Over : TurnStatus
}

/** A card shown at the reveal (p.7), with its initiative. [cardId] null = no card (a pass). */
@Immutable
data class RevealedCard(val heroId: String, val cardId: String?, val initiative: Int?)

/** How one hero did, for the game-over summary (from the event log). */
@Immutable
data class HeroResult(
    val heroId: String,
    val team: Team,
    val controller: Controller,
    val level: Int,
    /** Enemy heroes this hero defeated, and minions. */
    val heroDefeats: Int,
    val minionDefeats: Int,
    /** Times this hero was defeated. */
    val defeated: Int,
    val coins: Int,
)

@Immutable
data class GameOverUi(
    val winner: Team,
    val reason: WinReason,
    /** True when the person's team won. */
    val youWon: Boolean,
    val rounds: Int,
    val heroes: List<HeroResult>,
    val takeBacks: Int,
    val nudges: Int,
    val rethinks: Int,
    /** Coach on: the moments that decided it; null while the coach is still looking. */
    val turningPoints: List<CoachNote>? = null,
    val coach: Boolean,
)

/** A nudge waiting on the person: Choose anyway answers [choice], Rethink goes back to the question. */
@Immutable
data class PendingNudge(val note: CoachNote, val choice: Choice, val choiceLabel: String)

/** What TalkBack reads after something happens; [id] makes each one new. */
@Immutable
data class PlayAnnouncement(val text: String, val id: Long)

@Immutable
data class PlayUi(
    val table: TableUi,
    val status: TurnStatus,
    /**
     * The latest events, oldest first. When the question is the person's: what happened since
     * their last answer ([recentSince]), the lines not to miss at [recentStrong]. The hidden panel's
     * peek reads the last one; the panel itself shows [flow].
     */
    val recent: List<String>,
    val log: List<LogLine>,
    val coach: Boolean,
    val pace: AiPace,
    val takeBacks: Int,
    val canTakeBack: Boolean,
    /** An answer is being sent (or the coach is looking at it): answer buttons wait. */
    val busy: Boolean = false,
    val nudge: PendingNudge? = null,
    /** The cards of the latest reveal, in initiative order, until the turn ends. */
    val reveal: List<RevealedCard> = emptyList(),
    val over: GameOverUi? = null,
    val announcement: PlayAnnouncement? = null,
    /** A short message for the snackbar ("Nothing to take back yet"); [messageId] tells repeats apart. */
    val message: String? = null,
    val messageId: Long = 0,
    val recentStrong: Set<Int> = emptySet(),
    /** The game-state review, while its sheet is open (ADR-037). */
    val review: ReviewUi? = null,
    /** What happened since the person last chose, one entry per card resolved (ADR-046): the panel shows it. */
    val flow: ResolutionFlow = ResolutionFlow(),
)

/** Why there is no game to show. */
enum class NoGame {
    /** Nothing is saved: start one from New game. */
    NONE,

    /** The saved game can't be read (corrupt, or from another build): offered for deletion. */
    UNREADABLE,

    /** The engine can't rebuild it (saved by an older rules version): offered for deletion. */
    INCOMPATIBLE,

    /** Its map is not in this build. */
    MAP_MISSING,
}

sealed interface GameUiState {
    data object Loading : GameUiState

    data class Unavailable(val why: NoGame) : GameUiState

    data class Playing(val play: PlayUi) : GameUiState
}

sealed interface GameAction {
    /** A tap on the board: selects the option it stands for, if any. */
    data class Tap(val tap: TableTap) : GameAction

    data class Select(val choice: Choice) : GameAction

    data class Answer(val choice: Choice) : GameAction

    /** After a nudge: answer as chosen. */
    data object ChooseAnyway : GameAction

    /** After a nudge: back to the question. */
    data object Rethink : GameAction

    data object TakeBack : GameAction

    data class SetCoach(val on: Boolean) : GameAction

    data class SetPace(val pace: AiPace) : GameAction

    data object Flip : GameAction

    /** Same setup, a new Tie Breaker flip. */
    data object Rematch : GameAction

    data class MessageShown(val id: Long) : GameAction

    /** The game-state review: opened, closed, a board rewound. */
    data class Review(val action: ReviewAction) : GameAction
}
