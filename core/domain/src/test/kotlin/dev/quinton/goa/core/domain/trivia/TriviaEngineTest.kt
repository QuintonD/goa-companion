package dev.quinton.goa.core.domain.trivia

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.random.Random
import org.junit.Test

class TriviaEngineTest {
    private val catalog = BundledCatalog.catalog
    private val ranges = BundledCatalog.ranges
    private val now = 1_700_000_000_000L

    private fun fact(card: String, stat: TriviaStat, value: Int, plus: Boolean = false, hero: String = "h") =
        TriviaFact(hero, card, stat, value, plus)

    private fun start(
        pool: List<TriviaFact>,
        config: TriviaConfig = TriviaConfig(duels = false),
        progress: Map<String, FactProgress> = emptyMap(),
        seed: Int = 1,
        ranges: Map<TriviaStat, List<Int>> = this.ranges,
    ) = TriviaEngine.start(pool, ranges, progress, config, Random(seed))

    private fun rightAnswer(question: TriviaQuestion): TriviaAnswer = when (question) {
        is TriviaQuestion.CardNumber -> TriviaAnswer.Number(question.fact.value)
        is TriviaQuestion.HigherInitiative -> TriviaAnswer.Duel(TriviaEngine.duelAnswer(question))
    }

    private fun wrongAnswer(question: TriviaQuestion): TriviaAnswer = when (question) {
        is TriviaQuestion.CardNumber -> TriviaAnswer.Number(question.choices.first { it != question.fact.value })

        is TriviaQuestion.HigherInitiative ->
            TriviaAnswer.Duel(DuelPick.entries.first { it != TriviaEngine.duelAnswer(question) })
    }

    private fun TriviaSession.reply(right: Boolean) =
        TriviaEngine.answer(this, if (right) rightAnswer(current!!) else wrongAnswer(current!!), now)

    private fun TriviaSession.step(right: Boolean, random: Random) = TriviaEngine.next(reply(right).session, random)

    private val wasp = TriviaFacts.of(BundledCatalog.hero("wasp"))

    // --- Answer choices ---

    @Test
    fun `choices are four neighbouring range values around the answer, ascending and distinct`() {
        val all = TriviaFacts.of(catalog, catalog.heroes.map { it.id })
        all.forEachIndexed { i, fact ->
            val question = start(listOf(fact), seed = i).current as TriviaQuestion.CardNumber
            val range = ranges.getValue(fact.stat)
            val choices = question.choices
            assertWithMessage(fact.key).that(choices).contains(fact.value)
            assertWithMessage(fact.key).that(choices).isInStrictOrder()
            assertWithMessage(fact.key).that(choices).hasSize(minOf(TriviaEngine.CHOICE_COUNT, range.size))
            assertWithMessage(fact.key).that(range).containsAtLeastElementsIn(choices)
            // Neighbouring: a run of consecutive range entries.
            val from = range.indexOf(choices.first())
            assertWithMessage(fact.key).that(range.subList(from, from + choices.size)).isEqualTo(choices)
        }
    }

    @Test
    fun `the answer can sit at any of the four places, not only in the middle`() {
        val fact = fact("c", TriviaStat.INITIATIVE, 7) // 1..13: room on both sides
        val places = (0 until 400).map { seed ->
            val q = start(listOf(fact), seed = seed).current as TriviaQuestion.CardNumber
            q.choices.indexOf(7)
        }.groupingBy { it }.eachCount()
        assertThat(places.keys).containsExactly(0, 1, 2, 3)
        places.values.forEach { assertThat(it).isGreaterThan(60) } // ~100 each
    }

    @Test
    fun `choices at the edge of the range or in a short range`() {
        val low = start(listOf(fact("c", TriviaStat.INITIATIVE, 1))).current as TriviaQuestion.CardNumber
        assertThat(low.choices).containsExactly(1, 2, 3, 4).inOrder()
        val high = start(listOf(fact("c", TriviaStat.INITIATIVE, 13))).current as TriviaQuestion.CardNumber
        assertThat(high.choices).containsExactly(10, 11, 12, 13).inOrder()
        val short = mapOf(TriviaStat.RANGE to listOf(1, 2))
        val q = start(listOf(fact("c", TriviaStat.RANGE, 2)), ranges = short).current as TriviaQuestion.CardNumber
        assertThat(q.choices).containsExactly(1, 2).inOrder()
        // A value missing from the range (never in practice) is still offered.
        val odd = start(listOf(fact("c", TriviaStat.RANGE, 9)), ranges = short).current as TriviaQuestion.CardNumber
        assertThat(odd.choices).containsExactly(1, 2, 9).inOrder()
    }

    @Test
    fun `recall offers the stat's whole range`() {
        val config = TriviaConfig(answerStyle = AnswerStyle.RECALL, duels = false)
        TriviaStat.entries.forEach { stat ->
            val f = TriviaFacts.of(catalog, catalog.heroes.map { it.id }, stats = setOf(stat)).first()
            val q = start(listOf(f), config).current as TriviaQuestion.CardNumber
            assertWithMessage("$stat").that(q.choices).isEqualTo(ranges.getValue(stat))
        }
        assertThat((start(listOf(wasp.first()), config).current as TriviaQuestion.CardNumber).choices)
            .containsExactlyElementsIn(1..13).inOrder()
    }

    // --- Round flow ---

    @Test
    fun `a fixed-length round ends after its answers`() {
        TriviaConfig.LENGTHS.filter { it != TriviaConfig.ENDLESS }.forEach { length ->
            val random = Random(length)
            var session = start(wasp, TriviaConfig(length = length))
            assertThat(session.length).isEqualTo(length)
            repeat(length) {
                assertThat(session.finished).isFalse()
                session = session.step(right = it % 3 != 0, random = random)
            }
            assertThat(session.finished).isTrue()
            assertThat(session.results).hasSize(length)
            assertThat(session.correctCount).isEqualTo((0 until length).count { it % 3 != 0 })
            // Finished is final.
            assertThat(TriviaEngine.next(session, random)).isSameInstanceAs(session)
            assertThat(TriviaEngine.answer(session, TriviaAnswer.Number(1), now).updates).isEmpty()
        }
    }

    @Test
    fun `an endless round runs until stopped`() {
        val random = Random(3)
        var session = start(wasp, TriviaConfig(length = TriviaConfig.ENDLESS))
        assertThat(session.length).isNull()
        repeat(200) { session = session.step(right = true, random = random) }
        assertThat(session.finished).isFalse()
        assertThat(session.streak).isEqualTo(200)
        val stopped = TriviaEngine.stop(session)
        assertThat(stopped.finished).isTrue()
        assertThat(stopped.results).hasSize(200)
        // Stopping mid-question: the unanswered question isn't counted.
        assertThat(TriviaEngine.stop(start(wasp)).results).isEmpty()
    }

    @Test
    fun `an empty pool is a finished round`() {
        val session = start(emptyList(), TriviaConfig())
        assertThat(session.finished).isTrue()
        assertThat(session.pool).isEmpty()
    }

    @Test
    fun `answering twice is ignored and next before an answer is a no-op`() {
        val session = start(wasp)
        assertThat(TriviaEngine.next(session, Random(1))).isSameInstanceAs(session)
        val first = session.reply(right = true)
        assertThat(first.correct).isTrue()
        assertThat(first.session.answered).isTrue()
        val again = TriviaEngine.answer(first.session, wrongAnswer(session.current!!), now)
        assertThat(again.session).isSameInstanceAs(first.session)
        assertThat(again.updates).isEmpty()
        assertThat(again.correct).isFalse()
        assertThat(again.session.results).hasSize(1)
    }

    @Test
    fun `a duplicate fact in the pool is asked as one`() {
        val f = wasp.first()
        assertThat(start(listOf(f, f.copy())).pool).containsExactly(f)
    }

    // --- Retries ---

    @Test
    fun `a missed number comes back after RETRY_GAP other questions, then is used up`() {
        repeat(50) { seed ->
            val random = Random(seed)
            var session = start(wasp, TriviaConfig(length = TriviaConfig.ENDLESS, duels = false), seed = seed)
            val missed = (session.current as TriviaQuestion.CardNumber).fact
            session = session.step(right = false, random = random)
            assertThat(session.retries.map { it.fact }).containsExactly(missed)
            repeat(TriviaEngine.RETRY_GAP) {
                val cards = session.current!!.facts.map { it.cardId }
                assertWithMessage("seed $seed").that(cards).doesNotContain(missed.cardId)
                session = session.step(right = true, random = random)
            }
            val retry = session.current as TriviaQuestion.CardNumber
            assertWithMessage("seed $seed").that(retry.fact).isEqualTo(missed)
            assertThat(session.retries).isEmpty()
            session = session.step(right = true, random = random)
            repeat(20) {
                assertThat(session.retries).isEmpty()
                session = session.step(right = true, random = random)
            }
        }
    }

    @Test
    fun `a retry missed again is queued again`() {
        val random = Random(9)
        var session = start(wasp, TriviaConfig(length = TriviaConfig.ENDLESS, duels = false))
        val missed = (session.current as TriviaQuestion.CardNumber).fact
        session = session.step(right = false, random = random)
        repeat(TriviaEngine.RETRY_GAP) { session = session.step(right = true, random = random) }
        assertThat((session.current as TriviaQuestion.CardNumber).fact).isEqualTo(missed)
        session = session.step(right = false, random = random)
        assertThat(session.retries.map { it.fact }).containsExactly(missed)
    }

    @Test
    fun `a missed duel brings both cards back as number questions`() {
        val pool = listOf(
            fact("a", TriviaStat.INITIATIVE, 5),
            fact("b", TriviaStat.INITIATIVE, 6),
            fact("c", TriviaStat.ATTACK, 3),
            fact("d", TriviaStat.ATTACK, 4),
            fact("e", TriviaStat.ATTACK, 5),
            fact("f", TriviaStat.ATTACK, 6),
        )
        val seed = firstDuelSeed(pool)
        var session = start(pool, TriviaConfig(length = 20), seed = seed)
        val duel = session.current as TriviaQuestion.HigherInitiative
        val outcome = session.reply(right = false)
        assertThat(outcome.updates).isEmpty()
        assertThat(outcome.session.retries.map { it.fact }).containsExactly(duel.first, duel.second)
        // No more duels, so the retries can only come back as number questions.
        val noDuels = outcome.session.copy(config = outcome.session.config.copy(duels = false))
        session = TriviaEngine.next(noDuels, Random(1))
        val asked = mutableListOf<TriviaQuestion>()
        repeat(8) {
            asked += session.current!!
            session = session.step(right = true, random = Random(it))
        }
        val numbers = asked.filterIsInstance<TriviaQuestion.CardNumber>().map { it.fact }
        assertThat(numbers).containsAtLeast(duel.first, duel.second)
        assertThat(session.retries).isEmpty()
    }

    // --- Duels ---

    @Test
    fun `duel answers, ties included`() {
        val five = fact("a", TriviaStat.INITIATIVE, 5)
        val eight = fact("b", TriviaStat.INITIATIVE, 8)
        val alsoFive = fact("c", TriviaStat.INITIATIVE, 5)
        val higherFirst = TriviaQuestion.HigherInitiative(eight, five)
        val higherSecond = TriviaQuestion.HigherInitiative(five, eight)
        val tie = TriviaQuestion.HigherInitiative(five, alsoFive)
        assertThat(TriviaEngine.duelAnswer(higherFirst)).isEqualTo(DuelPick.FIRST)
        assertThat(TriviaEngine.duelAnswer(higherSecond)).isEqualTo(DuelPick.SECOND)
        assertThat(TriviaEngine.duelAnswer(tie)).isEqualTo(DuelPick.TIE)
        assertThat(TriviaEngine.isCorrect(higherFirst, TriviaAnswer.Duel(DuelPick.FIRST))).isTrue()
        assertThat(TriviaEngine.isCorrect(higherFirst, TriviaAnswer.Duel(DuelPick.SECOND))).isFalse()
        assertThat(TriviaEngine.isCorrect(higherFirst, TriviaAnswer.Duel(DuelPick.TIE))).isFalse()
        assertThat(TriviaEngine.isCorrect(tie, TriviaAnswer.Duel(DuelPick.TIE))).isTrue()
        assertThat(TriviaEngine.isCorrect(tie, TriviaAnswer.Duel(DuelPick.FIRST))).isFalse()
        // The wrong kind of answer is wrong, never a crash.
        assertThat(TriviaEngine.isCorrect(tie, TriviaAnswer.Number(5))).isFalse()
        val number = TriviaQuestion.CardNumber(five, listOf(5))
        assertThat(TriviaEngine.isCorrect(number, TriviaAnswer.Duel(DuelPick.TIE))).isFalse()
        assertThat(tie.facts).containsExactly(five, alsoFive).inOrder()
    }

    /** A seed whose first question is a duel. */
    private fun firstDuelSeed(pool: List<TriviaFact>): Int =
        (0 until 1000).first { start(pool, TriviaConfig(), seed = it).current is TriviaQuestion.HigherInitiative }

    private fun duelsIn(pool: List<TriviaFact>, config: TriviaConfig, runs: Int = 40, questions: Int = 30): Int {
        var duels = 0
        repeat(runs) { seed ->
            val random = Random(seed)
            var session = start(pool, config.copy(length = TriviaConfig.ENDLESS), seed = seed)
            repeat(questions) {
                if (session.current is TriviaQuestion.HigherInitiative) duels++
                session = session.step(right = random.nextInt(10) != 0, random = random)
            }
        }
        return duels
    }

    @Test
    fun `duels appear when allowed and possible`() {
        val duels = duelsIn(wasp, TriviaConfig())
        // ~20% of 1200 questions.
        assertThat(duels).isIn(150..400)
    }

    @Test
    fun `no duels without Initiative, with one initiative card, or when turned off`() {
        val noInitiative = wasp.filter { it.stat != TriviaStat.INITIATIVE }
        assertThat(duelsIn(noInitiative, TriviaConfig())).isEqualTo(0)
        val oneCard = wasp.filter { it.cardId == "wasp-red-1" || it.stat != TriviaStat.INITIATIVE }
        assertThat(duelsIn(oneCard, TriviaConfig())).isEqualTo(0)
        assertThat(duelsIn(wasp, TriviaConfig(duels = false))).isEqualTo(0)
    }

    @Test
    fun `a duel never changes mastery`() {
        val seed = firstDuelSeed(wasp)
        val progress = mapOf(wasp.first().key to FactProgress(wasp.first().value, box = 2, seen = 2, correct = 2))
        val session = start(wasp, TriviaConfig(), progress, seed = seed)
        listOf(true, false).forEach { right ->
            val outcome = session.reply(right)
            assertThat(outcome.correct).isEqualTo(right)
            assertThat(outcome.updates).isEmpty()
            assertThat(outcome.session.progress).isEqualTo(progress)
        }
    }

    // --- Mastery ---

    @Test
    fun `right answers climb a box up to the top, a wrong one drops to zero`() {
        val f = fact("c", TriviaStat.ATTACK, 4)
        var p: FactProgress? = null
        repeat(TriviaMastery.MAX_BOX + 2) { i ->
            p = TriviaMastery.record(f, p, correct = true, now = now + i)
            assertThat(p!!.box).isEqualTo(minOf(i + 1, TriviaMastery.MAX_BOX))
            assertThat(TriviaMastery.isMastered(f, p)).isEqualTo(i + 1 >= TriviaMastery.MASTERED_BOX)
        }
        assertThat(p!!.seen).isEqualTo(TriviaMastery.MAX_BOX + 2)
        assertThat(p!!.correct).isEqualTo(TriviaMastery.MAX_BOX + 2)
        assertThat(p!!.lastSeenAt).isEqualTo(now + TriviaMastery.MAX_BOX + 1)
        p = TriviaMastery.record(f, p, correct = false, now = now)
        assertThat(p!!.box).isEqualTo(0)
        assertThat(p!!.seen).isEqualTo(TriviaMastery.MAX_BOX + 3)
        assertThat(p!!.correct).isEqualTo(TriviaMastery.MAX_BOX + 2)
        assertThat(TriviaMastery.isMastered(f, p)).isFalse()
        assertThat(TriviaMastery.MASTERED_BOX).isEqualTo(3)
    }

    @Test
    fun `progress learned for another printed value or plus flag counts as new`() {
        val f = fact("c", TriviaStat.ATTACK, 4, plus = true)
        val learned = FactProgress(value = 4, plus = true, box = 4, seen = 9, correct = 8)
        assertThat(TriviaMastery.box(f, learned)).isEqualTo(4)
        val corrected = FactProgress(value = 5, plus = true, box = 4, seen = 9, correct = 8)
        val noPlus = learned.copy(plus = false)
        listOf(corrected, noPlus).forEach { old ->
            assertThat(TriviaMastery.current(f, old)).isNull()
            assertThat(TriviaMastery.box(f, old)).isEqualTo(0)
            assertThat(TriviaMastery.isMastered(f, old)).isFalse()
            val next = TriviaMastery.record(f, old, correct = true, now = now)
            assertThat(next)
                .isEqualTo(FactProgress(value = 4, plus = true, box = 1, seen = 1, correct = 1, lastSeenAt = now))
        }
        assertThat(TriviaEngine.weight(f, corrected)).isEqualTo(TriviaEngine.weight(f, null))
    }

    @Test
    fun `summary counts mastered, learning and unseen facts`() {
        val facts = (1..6).map { fact("c$it", TriviaStat.DEFENSE, it) }
        val progress = mapOf(
            facts[0].key to FactProgress(1, box = 3),
            facts[1].key to FactProgress(2, box = 5),
            facts[2].key to FactProgress(3, box = 0, seen = 1),
            facts[3].key to FactProgress(4, box = 2),
            facts[4].key to FactProgress(99, box = 5), // learned for an older printed value
            "other/attack" to FactProgress(1, box = 5), // not in this set
        )
        val summary = TriviaMastery.summary(facts, progress)
        assertThat(summary).isEqualTo(MasterySummary(total = 6, mastered = 2, learning = 2))
        assertThat(summary.unseen).isEqualTo(2)
        assertThat(TriviaMastery.summary(emptyList(), progress)).isEqualTo(MasterySummary(0, 0, 0))
    }

    @Test
    fun `an answer records mastery in the session and in the updates`() {
        val session = start(wasp)
        val f = (session.current as TriviaQuestion.CardNumber).fact
        val right = session.reply(right = true)
        val expected = FactProgress(f.value, f.plus, box = 1, seen = 1, correct = 1, lastSeenAt = now)
        assertThat(right.updates).containsExactly(f.key, expected)
        assertThat(right.session.progress[f.key]).isEqualTo(right.updates[f.key])
        val wrong = session.reply(right = false)
        assertThat(wrong.updates.getValue(f.key).box).isEqualTo(0)
        assertThat(wrong.session.missedFacts).containsExactly(f)
    }

    // --- Picking ---

    @Test
    fun `the same card is not asked twice in a row while the pool has another`() {
        listOf(wasp, wasp.filter { it.cardId in setOf("wasp-gold", "wasp-silver") }).forEach { pool ->
            repeat(30) { seed ->
                val random = Random(seed)
                var session = start(pool, TriviaConfig(length = TriviaConfig.ENDLESS), seed = seed)
                val poolCards = pool.map { it.cardId }.toSet()
                var previous = emptySet<String>()
                repeat(40) {
                    val cards = session.current!!.facts.map { it.cardId }.toSet()
                    // Only after a duel on both cards of a two-card pool is there no other card to ask.
                    if ((poolCards - previous).isNotEmpty()) {
                        assertWithMessage("seed $seed").that(cards.intersect(previous)).isEmpty()
                    }
                    previous = cards
                    session = session.step(right = random.nextInt(4) != 0, random = random)
                }
            }
        }
    }

    @Test
    fun `missed and unseen facts are picked far more often than mastered ones`() {
        val mastered = fact("a", TriviaStat.ATTACK, 3)
        val missed = fact("b", TriviaStat.ATTACK, 4)
        val unseen = fact("c", TriviaStat.ATTACK, 5)
        val learning = fact("d", TriviaStat.ATTACK, 6)
        val progress = mapOf(
            mastered.key to FactProgress(3, box = TriviaMastery.MAX_BOX, seen = 5, correct = 5),
            missed.key to FactProgress(4, box = 0, seen = 3, correct = 1),
            learning.key to FactProgress(6, box = 1, seen = 1, correct = 1),
        )
        val pool = listOf(mastered, missed, unseen, learning)
        val counts = (0 until 4000)
            .map { seed -> start(pool, progress = progress, seed = seed).current!!.facts.single() }
            .groupingBy { it }.eachCount()
        val m = counts[mastered] ?: 0
        val x = counts.getValue(missed)
        val u = counts.getValue(unseen)
        val l = counts.getValue(learning)
        assertThat(x).isGreaterThan(l)
        assertThat(l).isGreaterThan(u)
        assertThat(u).isGreaterThan(m * 5)
        assertThat(m).isGreaterThan(0) // mastered still comes up now and then
        assertThat(TriviaEngine.weight(missed, progress[missed.key])).isGreaterThan(TriviaEngine.weight(unseen, null))
    }

    // --- Property-style sweep over the real catalog ---

    @Test
    fun `random real quizzes never break the invariants`() {
        val heroIds = catalog.heroes.map { it.id }
        val meta = Random(2026)
        repeat(300) { run ->
            val seed = meta.nextInt()
            val random = Random(seed)
            val heroes = heroIds.shuffled(random).take(random.nextInt(1, 4))
            val stats = TriviaStat.entries.filter { random.nextInt(3) != 0 }.toSet()
                .ifEmpty { setOf(TriviaStat.entries.random(random)) }
            val groups = CardGroup.entries.filter { random.nextInt(3) != 0 }.toSet()
                .ifEmpty { setOf(CardGroup.entries.random(random)) }
            val config = TriviaConfig(
                heroIds = heroes,
                stats = stats,
                groups = groups,
                answerStyle = AnswerStyle.entries.random(random),
                length = TriviaConfig.LENGTHS.random(random),
                duels = random.nextBoolean(),
            )
            val pool = TriviaFacts.of(catalog, heroes, stats, groups)
            val progress = pool.filter { random.nextInt(3) == 0 }.associate {
                // Now and then learned for another printed value: counts as new.
                val value = if (random.nextInt(8) == 0) it.value + 1 else it.value
                it.key to FactProgress(value, it.plus, box = random.nextInt(0, 6), seen = 1)
            }
            val label = "run $run seed $seed $config"
            var session = TriviaEngine.start(pool, ranges, progress, config, random)
            if (pool.isEmpty()) {
                assertWithMessage(label).that(session.finished).isTrue()
                return@repeat
            }
            val poolKeys = pool.map { it.key }.toSet()
            val poolCards = pool.map { it.cardId }.toSet()
            val limit = config.length.takeIf { it != TriviaConfig.ENDLESS } ?: 60
            var previous = emptySet<String>()
            var asked = 0
            while (!session.finished && asked < limit) {
                val question = session.current!!
                assertWithMessage(label).that(poolKeys).containsAtLeastElementsIn(question.facts.map { it.key })
                val cards = question.facts.map { it.cardId }.toSet()
                if ((poolCards - previous).isNotEmpty()) {
                    assertWithMessage(label).that(cards.intersect(previous)).isEmpty()
                }
                when (question) {
                    is TriviaQuestion.CardNumber -> {
                        val range = (ranges.getValue(question.fact.stat) + question.fact.value).distinct().sorted()
                        assertWithMessage(label).that(question.choices).contains(question.fact.value)
                        assertWithMessage(label).that(question.choices).isInStrictOrder()
                        assertWithMessage(label).that(range).containsAtLeastElementsIn(question.choices)
                        if (config.answerStyle == AnswerStyle.RECALL) {
                            assertWithMessage(label).that(question.choices).isEqualTo(range)
                        } else {
                            val count = minOf(TriviaEngine.CHOICE_COUNT, range.size)
                            assertWithMessage(label).that(question.choices).hasSize(count)
                        }
                    }

                    is TriviaQuestion.HigherInitiative -> {
                        assertWithMessage(label).that(config.duels).isTrue()
                        assertWithMessage(label).that(TriviaStat.INITIATIVE in stats).isTrue()
                        assertWithMessage(label).that(question.first.cardId).isNotEqualTo(question.second.cardId)
                        val duelStats = question.facts.map { it.stat }.toSet()
                        assertWithMessage(label).that(duelStats).containsExactly(TriviaStat.INITIATIVE)
                    }
                }
                val right = random.nextInt(10) < 7
                val outcome = session.reply(right)
                assertWithMessage(label).that(outcome.correct).isEqualTo(right)
                when (question) {
                    is TriviaQuestion.CardNumber -> {
                        val update = outcome.updates.getValue(question.fact.key)
                        assertWithMessage(label).that(update.value).isEqualTo(question.fact.value)
                        val before = TriviaMastery.box(question.fact, session.progress[question.fact.key])
                        val box = if (right) (before + 1).coerceAtMost(TriviaMastery.MAX_BOX) else 0
                        assertWithMessage(label).that(update.box).isEqualTo(box)
                    }

                    is TriviaQuestion.HigherInitiative -> assertWithMessage(label).that(outcome.updates).isEmpty()
                }
                asked++
                assertWithMessage(label).that(outcome.session.results).hasSize(asked)
                previous = cards
                session = TriviaEngine.next(outcome.session, random)
            }
            if (config.length != TriviaConfig.ENDLESS) {
                assertWithMessage(label).that(session.finished).isTrue()
                assertWithMessage(label).that(session.results).hasSize(config.length)
            } else {
                assertWithMessage(label).that(session.finished).isFalse()
                assertWithMessage(label).that(TriviaEngine.stop(session).finished).isTrue()
            }
        }
    }
}
