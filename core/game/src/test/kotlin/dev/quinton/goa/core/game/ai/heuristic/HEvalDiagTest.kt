package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.puzzles.AnswerKey
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PreparedPuzzle
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import org.junit.Test

/**
 * Opt-in diagnosis (HEVAL.md "Wave 3 — Track B2"): `AI_HEVAL_DIAG=p36:DEFEND` prints, for each keyed
 * question of that kind in that puzzle, every option's [HTerm] features after the book's one-ply finish
 * (as [Lookahead.value] scores it) and its value with and without `lastRace`, from the deciding team's side.
 */
class HEvalDiagTest {
    @Test
    fun `features after each option`() {
        val spec = System.getenv("AI_HEVAL_DIAG") ?: return
        val (id, kindName) = spec.split(":")
        val kind = DecisionKind.valueOf(kindName)
        val puzzle = Puzzles.all.first { it.info.id == id }
        val pp = PreparedPuzzle(puzzle, TestData.engine)
        val key = pp.key ?: error("no key")
        val profile = HeuristicProfile.of(10)
        val race = HEvalWeights.HAND.with(HTerm.LAST_RACE, 0.5)
        fun visit(n: Node, path: List<Int>) {
            val d = n.decision ?: return
            when (val e = key.entries[AnswerKey.pathOf(path)] ?: return) {
                is AnswerKey.Wins -> {
                    if (d.kind == kind && d.options.size > 1) {
                        println("DIAG $id ${AnswerKey.pathOf(path)} ${d.kind} seat ${d.seat}, winning ${e.options}")
                        for ((i, c) in d.options.withIndex()) {
                            val fork = n.session.forkForAnswer()
                            if (fork.answer(d.id, c) !is AnswerResult.Accepted) continue
                            Lookahead.finish(fork, d.seat, profile, HeroPlaybooks::forHero, d.team)
                            val ctx = HContext(fork, fork.pending, d.team, profile, finishing = true)
                            val f = HEval.features(ctx, d.team)
                            val hand = HEval.value(ctx, d.team, null, HEvalWeights.HAND)
                            val withRace = HEval.value(ctx, d.team, null, race)
                            println(
                                "DIAG   option $i value hand ${"%.2f".format(hand)} " +
                                    "with lastRace ${"%.2f".format(withRace)}",
                            )
                            val st = fork.state
                            println(
                                "DIAG   option $i $c: step ${st.step} turn ${st.turn} pending ${fork.pending?.kind} " +
                                    "minions ${st.minions.map { "${it.team}:${it.type}@${it.at}" }} " +
                                    "heroes ${st.heroes.map { "${it.id}@${it.at} hand ${it.hand.size}" }}",
                            )
                            val terms = f?.let { v ->
                                HTerm.entries.joinToString(" ") { "${it.label}=${"%.2f".format(v[it.ordinal])}" }
                            }
                            println("DIAG     " + (terms ?: "finished, winner ${st.winner}"))
                        }
                    }
                    for (i in e.options) visit(pp.solver.child(n, d.options[i]) ?: return, path + i)
                }

                is AnswerKey.Reply -> visit(pp.solver.child(n, d.options[e.option]) ?: return, path + e.option)
            }
        }
        visit(pp.rootNode, emptyList())
    }
}
