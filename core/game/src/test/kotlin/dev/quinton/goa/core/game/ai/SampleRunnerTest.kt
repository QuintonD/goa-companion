package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.junit.Test

/** Samples run on several threads give the same answers as one after the other (AI.md "Budgets"). */
class SampleRunnerTest {
    @Test
    fun `a threaded runner answers like the sequential one`() {
        ThreadRunner(3).use { threads ->
            val format = AiHarness.Format.THREE
            val (red, blue) = AiHarness.mixedLineups(1, seed = 4242L, size = format.size)[0]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, Team.RED)) as EngineSession
            val profile = AiPlayers.profile(STRENGTH)
            val one = mapOf(
                Team.RED to SearchAi(AiLevel.of(STRENGTH), profile, 7L, timeCap = false),
                Team.BLUE to SearchAi(AiLevel.of(STRENGTH), profile, 8L, timeCap = false),
            )
            val many = mapOf(
                Team.RED to SearchAi(AiLevel.of(STRENGTH), profile, 7L, timeCap = false, runner = threads),
                Team.BLUE to SearchAi(AiLevel.of(STRENGTH), profile, 8L, timeCap = false, runner = threads),
            )
            var compared = 0
            while (compared < DECISIONS) {
                val d = s.pending ?: break
                val a = one.getValue(d.team).choose(s, d)
                if (d.options.size > 1) {
                    assertThat(many.getValue(d.team).choose(s, d)).isEqualTo(a)
                    compared++
                }
                assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            assertThat(compared).isEqualTo(DECISIONS)
        }
    }

    private companion object {
        const val STRENGTH = 8
        const val DECISIONS = 40
    }
}

/** A [SampleRunner] on a fixed pool of daemon threads, as the Android app's (test code may use threads). */
class ThreadRunner(override val width: Int) :
    SampleRunner,
    AutoCloseable {
    private val pool: ExecutorService = Executors.newFixedThreadPool(width) { r -> Thread(r).apply { isDaemon = true } }

    override fun runAll(tasks: List<() -> Unit>) {
        if (tasks.size <= 1) {
            tasks.forEach { it() }
            return
        }
        pool.invokeAll(tasks.map { t -> Callable { t() } }).forEach { it.get() }
    }

    override fun close() = pool.shutdown()
}
