package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.model.match.Team

/**
 * What not defending comes to, as the engine plays it out (sol #7, sol-b #5): the defending hero is defeated
 * ([defeated]), its team spends [lifeSpent] Life counters ([lifeLeft] left), heroes collect [coins] (the defeater its
 * level, its friends the assist, p.13) and, when that was the last Life counter, [winner] wins. Only what the engine's
 * events say; [decisionId] is the DEFEND it answers.
 */
data class DefeatPreview(
    val decisionId: Long,
    val defeated: String,
    val byHero: String?,
    val team: Team?,
    val lifeSpent: Int,
    val lifeLeft: Int?,
    /** Hero id → coins, in the order the engine pays them. */
    val coins: List<Pair<String, Int>>,
    val winner: Team?,
)

/**
 * Answers [decision] (a DEFEND) with its "don't defend" option on a fork of [session] and reads the defeat's events.
 * Null when it is no DEFEND, has no such option, or the engine doesn't defeat the hero (card text may say otherwise).
 * The fork is thrown away: nothing changes in [session]. Events after the defeat's (the attack card going on) are left
 * out, and only open information is read (a defeat, Life counters, coins: p.4).
 */
fun defeatPreview(session: GameSession, decision: Decision): DefeatPreview? {
    if (decision.kind != DecisionKind.DEFEND) return null
    val decline = decision.options.firstOrNull { it is Choice.Decline } ?: return null
    val result = runCatching { session.fork().answer(decision.id, decline) }.getOrNull()
    val events = (result as? AnswerResult.Accepted)?.events ?: return null
    return defeatPreviewOf(decision, events)
}

/** [defeatPreview] from the events answering "don't defend" produced. */
fun defeatPreviewOf(decision: Decision, events: List<GameEvent>): DefeatPreview? {
    val hero = decision.seat
    val start = events.indexOfFirst { it is GameEvent.Defeated && it.unitId == hero }
    if (start < 0) return null
    val defeat = events[start] as GameEvent.Defeated
    // The defeat's own events (p.13): coins, the Life counters spent, the game's end if that was the last, and notes
    // (its card resolved without an action). The first other event is the attack card going on: not the defeat's.
    val own = events.drop(start + 1).takeWhile {
        it is GameEvent.CoinsGained || it is GameEvent.LifeSpent || it is GameEvent.GameOver ||
            it is GameEvent.Note || it is GameEvent.LevelledUp || it is GameEvent.CardsChanged
    }
    val spent = own.filterIsInstance<GameEvent.LifeSpent>().firstOrNull()
    val over = own.filterIsInstance<GameEvent.GameOver>().firstOrNull()
    return DefeatPreview(
        decisionId = decision.id,
        defeated = hero,
        byHero = defeat.byHeroId,
        team = spent?.team,
        lifeSpent = spent?.amount ?: 0,
        lifeLeft = spent?.left,
        coins = own.filterIsInstance<GameEvent.CoinsGained>().map { it.heroId to it.amount },
        winner = over?.winner,
    )
}
