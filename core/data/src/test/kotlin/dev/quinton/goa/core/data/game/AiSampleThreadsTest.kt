package dev.quinton.goa.core.data.game

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicIntegerArray
import org.junit.Test

/** The app's sample runner (AI.md "Budgets"): every task runs once, and it returns when all are done. */
class AiSampleThreadsTest {
    @Test
    fun `runs every task once and waits for them`() {
        val runs = AtomicIntegerArray(10)
        AiSampleThreads.runAll(List(10) { i -> { runs.incrementAndGet(i) } })
        assertThat((0 until 10).map { runs.get(it) }).containsExactlyElementsIn(List(10) { 1 })
        assertThat(AiSampleThreads.width).isIn(1..3)
    }

    @Test
    fun `a task's failure reaches the caller`() {
        val failure = runCatching {
            AiSampleThreads.runAll(listOf({ Unit }, { error("boom") }))
        }.exceptionOrNull()
        assertThat(failure).isNotNull()
    }
}
