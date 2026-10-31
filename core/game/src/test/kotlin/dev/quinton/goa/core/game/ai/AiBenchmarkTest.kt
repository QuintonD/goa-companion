package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.TestData
import java.util.concurrent.atomic.AtomicLong
import org.junit.Test

/**
 * The phone benchmark's core (AI.md "Budgets"): numbers from two devices compare only if they answered
 * the same card plays. `AI_BENCHMARK=1` runs it in full on this machine, three threads and one, and
 * prints the desktop's line for AI.md.
 */
class AiBenchmarkTest {
    private val engine = TestData.engine

    @Test
    fun `every device measures the same card plays`() {
        fun fingerprint() = AiBenchmark.positions(engine).map { s ->
            val d = s.pending!!
            listOf(d.seat, d.kind, d.options, s.state.round, s.state.turn, s.events.size).hashCode()
        }
        val first = fingerprint()
        assertThat(fingerprint()).isEqualTo(first)
        assertThat(first.size).isGreaterThan(MIN_POSITIONS)
    }

    @Test
    fun `counts whole card plays on any runner`() {
        ThreadRunner(THREADS).use { threads ->
            val r = AiBenchmark.run(engine, threads, millis = SHORT_MS)!!
            assertThat(r.threads).isEqualTo(THREADS)
            assertThat(r.cardPlays).isGreaterThan(0)
            assertThat(r.answersPerSecond).isGreaterThan(0)
            assertThat(r.medianMs).isAtMost(r.p95Ms)
            assertThat(r.p95Ms).isAtMost(r.maxMs)
            assertThat(r.line("test")).contains("${r.answersPerSecond} answers/s")
        }
    }

    @Test
    fun `a uniform answer gives the same work with any runner`() {
        // The work is what makes answers per second comparable: three threads must not lose counts.
        val positions = AiBenchmark.positions(engine).take(WORK_POSITIONS)
        fun work(runner: SampleRunner): List<Long> {
            val p = SearchAi(
                dev.quinton.goa.core.game.AiLevel.of(10),
                AiPlayers.profile(10),
                1L,
                timeCap = false,
                runner = runner,
            )
            return positions.map { s ->
                val w0 = p.work
                p.choose(s, s.pending!!)
                p.work - w0
            }
        }
        ThreadRunner(THREADS).use { threads ->
            assertThat(work(threads)).isEqualTo(work(SampleRunner.SEQUENTIAL))
        }
    }

    @Test
    fun `stops at once when cancelled`() {
        val polls = AtomicLong()
        val result = AiBenchmark.run(engine, SampleRunner.SEQUENTIAL, cancelled = { polls.incrementAndGet() > 200 })
        assertThat(result).isNull()
    }

    @Test
    fun `the full measure on this machine`() {
        if (System.getenv("AI_BENCHMARK") == null) return
        ThreadRunner(THREADS).use { threads ->
            println("BENCHMARK ${AiBenchmark.run(engine, threads)!!.line("desktop JVM, Ryzen 7 3800X")}")
        }
        println("BENCHMARK ${AiBenchmark.run(engine, SampleRunner.SEQUENTIAL)!!.line("desktop JVM, Ryzen 7 3800X")}")
    }

    private companion object {
        const val THREADS = 3
        const val SHORT_MS = 6_000L
        const val MIN_POSITIONS = 200
        const val WORK_POSITIONS = 6
    }
}
