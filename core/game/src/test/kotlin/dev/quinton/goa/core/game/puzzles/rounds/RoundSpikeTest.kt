package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.ai.learned.Learned
import dev.quinton.goa.core.game.puzzles.GoalTracker
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Progress
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Value
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Opt-in feasibility spike (ADR-052): `ROUND_SPIKE=<positions>:<runs>:<seconds>` takes round starts of
 * 2v2 self-play games and, for both teams, every goal on a ladder (the coins the game really gained and
 * a little more, each enemy hero defeated, the lane pushed, surviving), and tries to prove it with
 * [PlanProver], the enemy's cards fixed to the ones the game really played. The proof's size and the
 * turns its lines end in say whether this is a real four-turn puzzle.
 */
class RoundSpikeTest {
    private companion object {
        const val SEED = 52L
        const val LATE = 3

        /** The first turn the enemy's actions are free: 5 = only its reactions are, all round. */
        val OPEN_FROM = listOf(5, 4, 3)
    }

    @Test
    fun `how far do proofs reach`() {
        val spec = System.getenv("ROUND_SPIKE")?.split(":") ?: return
        val n = spec[0].toInt()
        val runs = spec.getOrNull(1)?.toLong() ?: 3_000_000L
        val seconds = spec.getOrNull(2)?.toLong() ?: 120L
        val games = System.getenv("ROUND_GAMES")?.toInt() ?: 200
        val size = System.getenv("ROUND_SIZE")?.toInt() ?: 2
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 4
        val data = File(System.getenv("ROUND_DATA") ?: "D:/.goa-qa/v09/a2/data/gen2/games-2.jsonl")
        val mined = ArrayList<MinedRound>()
        var read = 0
        data.useLines { lines ->
            for (line in lines) {
                if (read >= games) break
                val rec = Learned.JSON.decodeFromString(Learned.GameRecord.serializer(), line)
                if (rec.size != size || rec.winner == null) continue
                read++
                mined += RoundMine.rounds(rec, 2).filter { m -> m.played.values.all { p -> p.all { it != null } } }
            }
        }
        // Rounds where the game itself reached a goal late (turn 3 on), at each level of openness.
        val rng = kotlin.random.Random(SEED)
        val cases = mined.shuffled(rng).flatMap { m ->
            Team.entries.flatMap { t ->
                m.goalsFor(t).filter {
                    it !is PuzzleGoal.Survive
                }.filter { (m.metIn(it, t) ?: 0) >= LATE }.map { Job(m, t, it) }
            }
        }.distinctBy { it.m.game }.take(n)
        println("SPIKE ${mined.size} rounds of $read games, ${cases.size} cases")
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "spike", 512L shl 20) }
        for (j in cases) {
            for (open in OPEN_FROM) {
                pool.submit { runCatching { run(j, open, runs, seconds) }.onFailure { it.printStackTrace(System.out) } }
            }
        }
        pool.shutdown()
        pool.awaitTermination(2, TimeUnit.HOURS)
    }

    private class Job(val m: MinedRound, val team: Team, val goal: PuzzleGoal)

    private fun run(j: Job, openFrom: Int, runs: Long, seconds: Long): Boolean {
        val m = j.m
        val mine = m.team(j.team)
        val enemies = m.team(j.team.opponent)
        val known = KnownPlays(
            enemies.associateWith { h -> m.played.getValue(h).map { it!! } },
            enemies.associateWith { h -> m.actions[h].orEmpty().filterKeys { it < openFrom } },
        )
        val tracker = GoalTracker(j.goal, j.team, mine.toSet())
        val label = "open$openFrom g${m.game} r${m.round} ${j.team} $mine vs $enemies ${j.goal.text} " +
            "(game: turn ${m.metIn(j.goal, j.team)})"
        val sv = PuzzleSolver(tracker, known = known)
        val root = sv.wrap(m.halted().also { it.quiet = System.getenv("ROUND_QUIET") != "0" }, Progress(), null)
        val prover = PlanProver(sv, runs, System.nanoTime() + seconds * 1_000_000_000L)
        val t = System.nanoTime()
        val v = prover.solve(root)
        val ms = (System.nanoTime() - t) / 1_000_000
        val proof = if (v == Value.WIN) prover.verify(root) else null
        val shown = when {
            v != Value.WIN -> ""

            proof == null -> " PROOF DOES NOT HOLD"

            else ->
                " proof ${proof.positions} positions (${proof.playerPositions} player, " +
                    "${proof.enemyPositions} enemy) ${proof.edges} edges, goal met in turns ${proof.earliest}..${proof.latest}"
        }
        println("SPIKE $label: $v runs ${prover.engineRuns} positions ${prover.positions} $ms ms$shown")
        return v == Value.WIN
    }
}
