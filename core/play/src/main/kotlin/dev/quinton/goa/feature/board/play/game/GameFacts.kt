package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.match.Team

/*
 * What the game screen works out from the session, shared by the phone's GameViewModel and the web
 * app's game (whose session lives in a Web Worker, ADR-043): the person's view, whose move it is,
 * the reveal, what happened lately and the game-over summary. Pure functions of the engine's data.
 */

/** The heroes the person at the phone controls (every human seat: one human team per game). */
val GameSetup.humans: Set<String> get() = seats.filter { it.controller == Controller.Human }.map { it.heroId }.toSet()

/** The person's team: the team of the first human seat (null for a game with none). */
val GameSetup.humanTeam: Team? get() = seats.firstOrNull { it.controller == Controller.Human }?.team

/**
 * What the person may see: the view of their first hero, with each other hero they control as
 * that hero sees itself (its hand and its played card). They play all of them.
 */
fun GameSession.mergedView(humans: Set<String>): GameView {
    val first = humans.firstOrNull() ?: return view(null)
    val base = view(first)
    if (humans.size == 1) return base
    val own = humans.drop(1).associateWith { seat -> view(seat).heroes.firstOrNull { it.heroId == seat } }
    return base.copy(heroes = base.heroes.map { hero -> own[hero.heroId] ?: hero })
}

/** Where the current turn's events begin (after a restore or take back, "since your last answer"). */
fun turnStart(events: List<GameEvent>): Int =
    events.indexOfLast { it is GameEvent.TurnStarted || it is GameEvent.RoundStarted }.coerceAtLeast(0)

/** The cards of the latest reveal, until the turn ends; [printed] gives a card's printed initiative. */
fun latestReveal(events: List<GameEvent>, printed: (String) -> Int?): List<RevealedCard> {
    val last = events.indexOfLast { it is GameEvent.CardsRevealed }
    if (last < 0) return emptyList()
    if (events.drop(last + 1).any { it is GameEvent.TurnStarted || it is GameEvent.RoundStarted }) return emptyList()
    return revealOf(events[last] as GameEvent.CardsRevealed, printed)
}

/** The engine lists the cards in the order they will act (initiative, ties by the coin, p.7). */
fun revealOf(event: GameEvent.CardsRevealed, printed: (String) -> Int?): List<RevealedCard> = event.played
    .map { (hero, card) -> RevealedCard(hero, card, event.initiatives[hero] ?: card?.let(printed)) }

/** Whose move it is: the person's, the computer's (thinking once it takes a while), the wait for the reveal, over. */
fun turnStatus(decision: Decision?, view: GameView, humans: Set<String>, thinking: Boolean): TurnStatus = when {
    decision == null || view.phase == GamePhase.GAME_OVER -> TurnStatus.Over

    decision.seat in humans -> TurnStatus.YourTurn

    decision.kind == DecisionKind.PLAY_CARD &&
        humans.all { h -> view.heroes.any { it.heroId == h && it.played != null } } ->
        TurnStatus.Waiting(view.heroes.filter { it.heroId !in humans && it.played == null }.map { it.heroId })

    else -> TurnStatus.Computer(decision.seat, thinking)
}

/**
 * The panel's lines: when the question is the person's, what happened since their last answer
 * ([answeredAt] = the event count then), else the latest few.
 */
fun recentLines(status: TurnStatus, events: List<GameEvent>, answeredAt: Int, heroIds: Set<String>): RecentLines =
    if (status == TurnStatus.YourTurn) {
        recentSince(events, answeredAt, heroIds)
    } else {
        RecentLines(events.filter(::panelWorthy).takeLast(GamePacing.RECENT).map { it.text })
    }

/** The game-over summary from the log, or null while the game goes on. */
fun gameOverUi(
    view: GameView,
    events: List<GameEvent>,
    setup: GameSetup,
    inProgress: GameInProgress,
    turningPoints: List<CoachNote>?,
): GameOverUi? {
    val over = events.filterIsInstance<GameEvent.GameOver>().lastOrNull()
    val winner = view.winner ?: over?.winner ?: return null
    val reason = view.winReason ?: over?.reason ?: return null
    val heroIds = setup.seats.map { it.heroId }.toSet()
    val defeats = events.filterIsInstance<GameEvent.Defeated>()
    val results = setup.seats.map { seat ->
        val hero = view.heroes.firstOrNull { it.heroId == seat.heroId }
        HeroResult(
            heroId = seat.heroId,
            team = seat.team,
            controller = seat.controller,
            level = hero?.level ?: 1,
            heroDefeats = defeats.count { it.byHeroId == seat.heroId && it.unitId in heroIds },
            minionDefeats = defeats.count { it.byHeroId == seat.heroId && it.unitId !in heroIds },
            defeated = defeats.count { it.unitId == seat.heroId },
            coins = events.filterIsInstance<GameEvent.CoinsGained>().filter {
                it.heroId == seat.heroId
            }.sumOf { it.amount },
        )
    }
    return GameOverUi(
        winner = winner,
        reason = reason,
        youWon = winner == setup.humanTeam,
        rounds = view.round,
        heroes = results,
        takeBacks = inProgress.takeBacks,
        nudges = inProgress.nudges,
        rethinks = inProgress.rethinks,
        turningPoints = turningPoints,
        coach = inProgress.coach,
    )
}

/** How the game against the computer is paced (PRODUCT §9 "Pacing"), the phone's and the web's. */
object GamePacing {
    /**
     * The most an answer waits for the coach; then it goes through unnudged. The look ahead has
     * usually finished while the person was thinking (it starts with the question), so this only
     * covers an answer given within moments; 300 ms still reads as the tap's own response.
     */
    const val NUDGE_WAIT_MS = 300L

    const val TURNING_POINTS_TIMEOUT_MS = 20_000L

    /** "Thinking…" after this long. */
    const val THINKING_MS = 700L

    /** The reveal stays this many paces; a hidden card play (p.7) a fraction of one. */
    const val REVEAL_FACTOR = 2L
    const val HIDDEN_FACTOR = 4L

    const val RECENT = 4

    /** How long a computer action stays on screen before the next one. */
    fun after(pace: AiPace, revealed: Boolean, kind: DecisionKind): Long = when {
        revealed -> pace.millis * REVEAL_FACTOR
        kind == DecisionKind.PLAY_CARD -> pace.millis / HIDDEN_FACTOR
        else -> pace.millis
    }
}
