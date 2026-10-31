package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.game.puzzles.rounds.PlanProver
import dev.quinton.goa.core.game.puzzles.rounds.RoundCertificate
import org.junit.Assert.fail
import org.junit.Test

/**
 * Opt-in: `ROUND_PROVE=all` or `r01,r02`. Proves the full rounds again and rewrites their certificates
 * (needed when a rules change, or a new field of the engine's state, invalidates them: their keys hash
 * the state). A puzzle that can no longer be proven fails here: fix it or cut it.
 */
class FullRoundProveTest {
    @Test
    fun `prove the full rounds again`() {
        val spec = System.getenv("ROUND_PROVE") ?: return
        val wanted = spec.split(",").toSet()
        val failed = ArrayList<String>()
        for (p in Puzzles.fullRounds.filter { "all" in wanted || it.info.id in wanted }) {
            val prep = RoundCertificate.prepared(p)
            val prover = PlanProver(prep.solver, MAX_RUNS, System.nanoTime() + SECONDS * NANOS)
            val t0 = System.nanoTime()
            val value = prover.solve(prep.rootNode)
            val ms = (System.nanoTime() - t0) / MILLIS
            val proof = if (value == Value.WIN) prover.verify(prep.rootNode) else null
            println("${p.info.id}: $value, ${prover.engineRuns} runs, $ms ms, ${proof?.positions} positions")
            if (proof == null) {
                failed += p.info.id
                continue
            }
            val f = RoundCertificate.file(p.info.id)
            f.parentFile.mkdirs()
            f.writeText(RoundCertificate.of(proof).format())
        }
        if (failed.isNotEmpty()) fail("Not proven: $failed")
    }

    private companion object {
        const val MAX_RUNS = 200_000_000L
        const val SECONDS = 1_800L
        const val NANOS = 1_000_000_000L
        const val MILLIS = 1_000_000L
    }
}
