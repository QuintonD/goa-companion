package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Every [DecisionKind] gets a legal answer at every strength, from real games and from built questions. */
class DecisionKindsTest {
    @Test
    fun `AI games at every level answer every decision they meet`() {
        val seen = sortedSetOf<DecisionKind>()
        // Two strengths per game, so every profile plays (the four bands and the steps between).
        for ((i, strength) in listOf(1, 3, 5, 7, 9).withIndex()) {
            val (red, blue) = AiHarness.LINEUPS[i % 3]
            val level = AiLevel.of(strength)
            val setup = AiHarness.setup(red, blue, level, level, if (i % 2 == 0) Team.RED else Team.BLUE)
            val s = TestData.engine.newGame(setup)
            val players = mapOf(
                Team.RED to AiPlayers.deterministic(strength, 11L + i),
                Team.BLUE to AiPlayers.deterministic(strength + 1, 21L + i),
            )
            var n = 0
            while (n < 6000) {
                val d = s.pending ?: break
                seen += d.kind
                val c = players.getValue(d.team).choose(s, d)
                assertThat(d.options).contains(c)
                s.answer(d.id, c)
                n++
            }
            assertThat(s.pending).isNull()
        }
        println("DECISION KINDS met in AI games: $seen")
        assertThat(seen).containsAtLeast(
            DecisionKind.PLAY_CARD,
            DecisionKind.CHOOSE_ACTION,
            DecisionKind.CHOOSE_SPACE,
            DecisionKind.UPGRADE_CARD,
            DecisionKind.KEEP_UPGRADE,
            DecisionKind.REMOVE_MINION,
        )
    }

    @Test
    fun `a Clear action asks for tokens and the AI answers`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) {
                played("wasp-red-1")
                controller = dev.quinton.goa.core.game.Controller.Ai(AiLevel.HARD)
            }
            hero("brogan", Team.BLUE, Hex(4, -6))
            token("token_rock", Hex(1, -2), owner = "brogan", team = Team.BLUE)
            token("token_rock", Hex(-1, -2), owner = "brogan", team = Team.BLUE)
            minion(Team.RED, MinionType.MELEE, Hex(-3, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -3))
            phase = PositionPhase.Resolving(next = "wasp")
        }
        assertThat(d.actions()).contains(ActionType.CLEAR)
        d.action("wasp", ActionType.CLEAR)
        val q = d.expect(DecisionKind.CHOOSE_TOKEN, "wasp")
        for (strength in AiStrength.MIN..AiStrength.MAX) {
            assertThat(q.options).contains(AiPlayers.deterministic(strength, 3).choose(d.s, q))
        }
    }

    @Test
    fun `built questions of every kind get a legal answer`() {
        val s = TestData.newGame(TestData.firstGame(length = GameLength.QUICK))
        val real = s.pending!!
        val minions = s.state.minions.filter { it.team == real.team }.map { it.id }
        val enemies = s.state.minions.filter { it.team != real.team }.map { it.id }
        val hand = s.state.hero(real.seat)!!.hand.map { it.id }
        val spaces = listOf(Hex(0, -2), Hex(1, -2), Hex(-1, -2))
        fun options(kind: DecisionKind): List<Choice> = when (kind) {
            DecisionKind.PLAY_CARD -> hand.map { Choice.Card(it) }

            DecisionKind.ACT_FIRST -> listOf(Choice.Unit("wasp"), Choice.Unit("xargatha"))

            DecisionKind.RESPAWN -> spaces.map { Choice.Space(it) } + Choice.Decline("Don't respawn")

            DecisionKind.CHOOSE_ACTION -> listOf(ActionType.MOVEMENT, ActionType.HOLD).map { Choice.Action(it) }

            DecisionKind.CHOOSE_SPACE, DecisionKind.MINION_RETURN -> spaces.map { Choice.Space(it) } + Choice.Decline()

            DecisionKind.CHOOSE_UNIT -> enemies.take(3).map { Choice.Unit(it) }

            DecisionKind.CHOOSE_TOKEN -> listOf(Choice.Token("t1"), Choice.Decline("Stop clearing"))

            DecisionKind.CHOOSE_CARD, DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> hand.take(2).map {
                Choice.Card(it)
            }

            DecisionKind.DEFEND -> hand.map { Choice.Card(it) } + Choice.Decline("Don't defend")

            DecisionKind.CHOOSE_OPTION -> listOf(Choice.Option(0, "First"), Choice.Option(1, "Second"))

            DecisionKind.CONFIRM -> listOf(Choice.Yes, Choice.Decline())

            DecisionKind.CHOOSE_NUMBER -> (0..3).map { Choice.Number(it) }

            DecisionKind.REMOVE_MINION -> minions.take(3).map { Choice.Unit(it) }
        }
        val policy = StaticPolicy(Evaluator(TestData.engine.data))
        for (kind in DecisionKind.entries) {
            val q = Decision(
                real.id,
                real.seat,
                real.team,
                kind,
                "Built question",
                options = options(kind),
                context = DecisionContext(
                    attackerId = "arien",
                    attackTotal = 5,
                    rangedAttack = false,
                    subjectId = real.seat,
                ),
            )
            val scores = policy.scores(s.state, q)
            assertThat(scores.size).isEqualTo(q.options.size)
            for (strength in AiStrength.MIN..AiStrength.MAX) {
                assertThat(q.options).contains(AiPlayers.deterministic(strength, 1).choose(s, q))
            }
        }
    }
}
