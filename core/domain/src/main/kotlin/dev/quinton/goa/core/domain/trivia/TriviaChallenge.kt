package dev.quinton.goa.core.domain.trivia

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlin.random.Random

/**
 * The Challenge (ADR-032): one fixed quiz, so scores compare across players and phones. 20
 * questions drawn evenly from every number on every hero's deck cards in the catalog (whatever the
 * owned products), all six numbers, all tiers, four choices, initiative duels on, no number asked
 * twice and no retries ([TriviaDraw.UNIFORM]). Progress is passed in only so a round played as this
 * phone's "You" can still move it; it never shapes the draw.
 */
object TriviaChallenge {
    const val QUESTIONS = 20

    val CONFIG = TriviaConfig(
        heroIds = emptyList(),
        stats = TriviaStat.entries.toSet(),
        groups = CardGroup.entries.toSet(),
        answerStyle = AnswerStyle.CHOICES,
        length = QUESTIONS,
        duels = true,
    )

    /** Every fact of every hero in [catalog]. */
    fun pool(catalog: Catalog): List<TriviaFact> = TriviaFacts.of(catalog, catalog.heroes.map { it.id })

    fun start(
        catalog: Catalog,
        progress: Map<String, FactProgress> = emptyMap(),
        random: Random = Random.Default,
    ): TriviaSession = TriviaEngine.start(
        pool(catalog),
        TriviaFacts.valueRange(catalog),
        progress,
        CONFIG,
        random,
        TriviaDraw.UNIFORM,
    )

    /** Only a Challenge with every question answered goes on the board. */
    fun isFinished(session: TriviaSession): Boolean =
        session.draw == TriviaDraw.UNIFORM && session.results.size == QUESTIONS
}

/** Turning a finished round into the [TriviaRound] the leaderboard is built from. */
object TriviaRounds {
    /**
     * The record of [session] played as [playerId], or null when it isn't one: nothing answered, or
     * a Challenge ended before its last question ([TriviaChallenge.isFinished]).
     */
    @Suppress("LongParameterList")
    fun of(
        session: TriviaSession,
        id: String,
        playerId: String,
        challenge: Boolean,
        playedAt: Long,
        answerMillis: Long,
        catalogVersion: Int,
    ): TriviaRound? {
        if (session.results.isEmpty()) return null
        if (challenge && !TriviaChallenge.isFinished(session)) return null
        return TriviaRound(
            id = id,
            playerId = playerId,
            kind = if (challenge) TriviaRoundKind.CHALLENGE else TriviaRoundKind.PRACTICE,
            playedAt = playedAt,
            length = session.config.length,
            questions = session.results.size,
            correct = session.correctCount,
            bestStreak = session.bestStreak,
            answerMillis = answerMillis.coerceAtLeast(0),
            recall = session.config.answerStyle == AnswerStyle.RECALL,
            heroCount = session.pool.mapTo(HashSet()) { it.heroId }.size,
            catalogVersion = catalogVersion,
            updatedAt = playedAt,
        )
    }
}
