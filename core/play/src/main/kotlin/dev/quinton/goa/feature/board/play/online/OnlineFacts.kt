package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.feature.board.play.game.TurnStatus
import kotlin.random.Random

/*
 * What the online driver works out from a frame, as pure functions (docs/ONLINE.md). The person's seats
 * are always `GameStart.you`: `setup.humans` is every seat when all are human.
 */

/** Who is being asked, and nothing else: no ticket, no options (docs/ONLINE.md "Hidden information"). */
val Decision.isStub: Boolean get() = id == 0L || options.isEmpty()

/** People answer these at the same time (p.7), so an answer stays in until the others are in too. */
val DecisionKind.simultaneous: Boolean
    get() = this == DecisionKind.PLAY_CARD || this == DecisionKind.UPGRADE_CARD || this == DecisionKind.KEEP_UPGRADE

/** Whether the person can answer [decision] now: a real question of theirs, not answered yet. */
fun canAnswer(decision: Decision?, you: Set<String>, answered: Long?): Boolean =
    decision != null && !decision.isStub && decision.seat in you && decision.id != answered

/**
 * Whose move it is. A real question of the person's is theirs, even while its answer is on its way
 * ([answered] is its ticket: the screen's `busy` covers that moment), unless it is simultaneous: then
 * their part is done. After that [locked] says they wait; otherwise someone else is being asked.
 */
fun onlineTurnStatus(
    decision: Decision?,
    view: GameView,
    you: Set<String>,
    answered: Long?,
    locked: Collection<String>,
    name: (String) -> String,
): TurnStatus = when {
    decision == null || view.phase == GamePhase.GAME_OVER -> TurnStatus.Over

    canAnswer(decision, you, answered) -> TurnStatus.YourTurn

    !decision.isStub && decision.seat in you && !decision.kind.simultaneous -> TurnStatus.YourTurn

    (!decision.isStub && decision.seat in you) || you.any { it in locked } ->
        TurnStatus.Waiting(stillChoosing(decision, view, you, locked))

    else -> TurnStatus.OtherPlayer(decision.seat, name(decision.seat))
}

private fun stillChoosing(
    decision: Decision,
    view: GameView,
    you: Set<String>,
    locked: Collection<String>,
): List<String> = if (decision.kind == DecisionKind.PLAY_CARD) {
    view.heroes.filter { it.heroId !in you && it.heroId !in locked && it.played == null }.map { it.heroId }
} else {
    // Only some heroes level up, and who is not told: the one being asked is all that is known.
    listOf(decision.seat).filter { it !in you && it !in locked }
}

/** The latest view, with each of the person's other seats as that seat sees itself (their last frame). */
fun mergeOwnSeats(latest: GameView, frames: Map<String?, GameView>, you: Set<String>): GameView {
    val own = you.mapNotNull { seat -> frames[seat]?.heroes?.firstOrNull { it.heroId == seat } }
        .associateBy { it.heroId }
    return latest.copy(heroes = latest.heroes.map { own[it.heroId] ?: it })
}

/** Whole seconds until [deadlineAt] on the server's clock, rounded up. */
fun secondsLeft(deadlineAt: Long, serverNow: Long): Int =
    ((deadlineAt - serverNow + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(0).toInt()

/**
 * How long to wait before reconnecting: 1 s doubling up to 30 s, each up to a quarter shorter, so that
 * everyone dropped by one restart does not come back in the same instant.
 */
fun reconnectDelay(attempt: Int, random: Random): Long {
    val base = (BACKOFF_MIN_MS shl attempt.coerceIn(0, BACKOFF_MAX_DOUBLINGS)).coerceAtMost(BACKOFF_MAX_MS)
    return base - random.nextLong(base / BACKOFF_JITTER_PART + 1)
}

private const val BACKOFF_MIN_MS = 1_000L
private const val BACKOFF_MAX_MS = 30_000L
private const val BACKOFF_MAX_DOUBLINGS = 5
private const val BACKOFF_JITTER_PART = 4
private const val MILLIS_PER_SECOND = 1_000L
