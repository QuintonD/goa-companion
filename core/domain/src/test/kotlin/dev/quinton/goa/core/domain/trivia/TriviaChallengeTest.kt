package dev.quinton.goa.core.domain.trivia

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

/** The Challenge (ADR-032): a fixed quiz over the whole catalog, drawn evenly, no number twice. */
class TriviaChallengeTest {
    private val catalog = BundledCatalog.catalog
    private val now = 1_700_000_000_000L

    private fun rightAnswer(question: TriviaQuestion): TriviaAnswer = when (question) {
        is TriviaQuestion.CardNumber -> TriviaAnswer.Number(question.fact.value)
        is TriviaQuestion.HigherInitiative -> TriviaAnswer.Duel(TriviaEngine.duelAnswer(question))
    }

    private fun wrongAnswer(question: TriviaQuestion): TriviaAnswer = when (question) {
        is TriviaQuestion.CardNumber -> TriviaAnswer.Number(question.choices.first { it != question.fact.value })

        is TriviaQuestion.HigherInitiative ->
            TriviaAnswer.Duel(DuelPick.entries.first { it != TriviaEngine.duelAnswer(question) })
    }

    /** Plays a whole Challenge, answering right when [right] says so. */
    private fun play(seed: Int, progress: Map<String, FactProgress> = emptyMap(), right: (Int) -> Boolean = { true }):
        TriviaSession {
        val random = Random(seed)
        var session = TriviaChallenge.start(catalog, progress, random)
        var i = 0
        while (!session.finished) {
            val q = session.current!!
            session = TriviaEngine.answer(session, if (right(i)) rightAnswer(q) else wrongAnswer(q), now).session
            session = TriviaEngine.next(session, random)
            i++
        }
        return session
    }

    @Test
    fun `the setup is fixed and covers every hero in the catalog, owned or not`() {
        val config = TriviaChallenge.CONFIG
        assertThat(config.length).isEqualTo(20)
        assertThat(config.stats).containsExactlyElementsIn(TriviaStat.entries)
        assertThat(config.groups).containsExactlyElementsIn(CardGroup.entries)
        assertThat(config.answerStyle).isEqualTo(AnswerStyle.CHOICES)
        assertThat(config.duels).isTrue()
        val pool = TriviaChallenge.pool(catalog)
        assertThat(pool.map { it.heroId }.toSet()).containsExactlyElementsIn(catalog.heroes.map { it.id })
        assertThat(pool).hasSize(catalog.heroes.sumOf { TriviaFacts.of(it).size })
    }

    @Test
    fun `a challenge asks 20 questions, never the same number twice, with four choices and some duels`() {
        var duels = 0
        repeat(200) { seed ->
            val session = play(seed, right = { it % 3 != 0 })
            assertThat(session.results).hasSize(20)
            val facts = session.results.flatMap { r -> r.question.facts.map { it.key } }
            assertWithMessage("seed $seed").that(facts).containsNoDuplicates()
            // Misses never come back in a Challenge.
            assertThat(session.retries).isEmpty()
            session.results.forEach { r ->
                when (val q = r.question) {
                    is TriviaQuestion.CardNumber -> assertThat(q.choices.size).isAtMost(TriviaEngine.CHOICE_COUNT)
                    is TriviaQuestion.HigherInitiative -> duels++
                }
            }
            // Never the same card twice in a row.
            session.results.zipWithNext().forEach { (a, b) ->
                val cardsA = a.question.facts.map { it.cardId }.toSet()
                assertThat(b.question.facts.map { it.cardId }).containsNoneIn(cardsA)
            }
            assertThat(TriviaChallenge.isFinished(session)).isTrue()
        }
        // About one question in five is a duel (DUEL_SHARE).
        val share = duels / (200.0 * 20)
        assertThat(abs(share - TriviaEngine.DUEL_SHARE)).isLessThan(0.05)
    }

    @Test
    fun `the draw is even whatever the progress, and seeded rounds repeat`() {
        // Mastering half the heroes would make a practice round avoid them; the Challenge doesn't care.
        val pool = TriviaChallenge.pool(catalog)
        val heroes = catalog.heroes.map { it.id }.sorted()
        val mastered = heroes.take(heroes.size / 2).toSet()
        val progress = pool.filter { it.heroId in mastered }
            .associate { it.key to FactProgress(it.value, it.plus, box = TriviaMastery.MAX_BOX) }
        val runs = 400
        val asked = (0 until runs).flatMap { seed ->
            play(seed, progress).results.mapNotNull { (it.question as? TriviaQuestion.CardNumber)?.fact }
        }
        val share = asked.count { it.heroId in mastered }.toDouble() / asked.size
        val expected = pool.count { it.heroId in mastered }.toDouble() / pool.size
        assertThat(abs(share - expected)).isLessThan(0.03)
        // Each stat turns up about as often as it is printed.
        TriviaStat.entries.forEach { stat ->
            val seen = asked.count { it.stat == stat }.toDouble() / asked.size
            val printed = pool.count { it.stat == stat }.toDouble() / pool.size
            assertWithMessage(stat.name).that(abs(seen - printed)).isLessThan(0.03)
        }
        // Reproducible from a seed (tests and screenshots rely on it).
        assertThat(play(5).results.map { it.question }).isEqualTo(play(5).results.map { it.question })
    }

    @Test
    fun `right answers in a challenge still move learning progress like any round`() {
        val session = play(3)
        val numbers = session.results.mapNotNull { (it.question as? TriviaQuestion.CardNumber)?.fact }
        numbers.forEach { assertThat(session.progress[it.key]?.box).isEqualTo(1) }
    }

    @Test
    fun `a finished challenge becomes a round, an unfinished one does not`() {
        val session = play(9, right = { it != 4 && it != 10 })
        val round = TriviaRounds.of(session, "r1", "ann", challenge = true, playedAt = 5, answerMillis = 61_000, 7)!!
        assertThat(round.kind).isEqualTo(TriviaRoundKind.CHALLENGE)
        assertThat(round.length).isEqualTo(20)
        assertThat(round.questions).isEqualTo(20)
        assertThat(round.correct).isEqualTo(18)
        assertThat(round.bestStreak).isEqualTo(9)
        assertThat(round.answerMillis).isEqualTo(61_000)
        assertThat(round.heroCount).isEqualTo(catalog.heroes.size)
        assertThat(round.catalogVersion).isEqualTo(7)
        assertThat(TriviaRoundValidation.problem(round)).isNull()

        val random = Random(1)
        var early = TriviaChallenge.start(catalog, random = random)
        early = TriviaEngine.answer(early, rightAnswer(early.current!!), now).session
        early = TriviaEngine.stop(early)
        assertThat(TriviaChallenge.isFinished(early)).isFalse()
        assertThat(TriviaRounds.of(early, "r2", "ann", challenge = true, playedAt = 5, answerMillis = 1, 7)).isNull()
        // The same session counted as practice is a round (ended early is fine for practice).
        val practice = TriviaRounds.of(early, "r3", "ann", challenge = false, playedAt = 5, answerMillis = 1, 7)!!
        assertThat(practice.kind).isEqualTo(TriviaRoundKind.PRACTICE)
        assertThat(practice.questions).isEqualTo(1)
        // Nothing answered = nothing to record.
        val none = TriviaEngine.stop(TriviaChallenge.start(catalog, random = Random(2)))
        assertThat(TriviaRounds.of(none, "r4", "ann", challenge = false, playedAt = 5, answerMillis = 0, 7)).isNull()
    }
}
