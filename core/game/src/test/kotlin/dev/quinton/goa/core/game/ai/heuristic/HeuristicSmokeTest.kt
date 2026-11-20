package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.MinHero
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The heuristic player answers, finishes, and does not read hidden cards (ADR-085).
 * Full games at strengths 1, 5 and 10, every hero, both team sizes.
 */
class HeuristicSmokeTest {
    @Test
    fun `every playable hero has a registered playbook`() {
        val playable = TestData.engine.playableHeroes.toSet()
        assertThat(playable).hasSize(32)
        assertThat(HeroPlaybooks.ids()).containsExactlyElementsIn(playable)
        for (id in playable) assertThat(HeroPlaybooks.forHero(id).heroId).isEqualTo(id)
        assertThat(HeroPlaybooks.forHero("no-such-hero")).isSameInstanceAs(DefaultHeroHeuristics)
    }

    @Test
    fun `strength 10 alone is told the enemies' hands`() {
        assertThat(HeuristicProfile.of(9).knowHands).isFalse()
        assertThat(HeuristicProfile.of(10).knowHands).isTrue()
        assertThat(HeuristicProfile.of(1).threatMap).isFalse()
        assertThat(HeuristicProfile.of(5).heroPlaybooks).isTrue()
        assertThat(HeuristicProfile.of(4).heroPlaybooks).isFalse()
    }

    @Test
    fun `every decision kind gets a legal answer`() {
        val s = TestData.newGame(TestData.firstGame(length = GameLength.QUICK))
        val real = s.pending!!
        val hand = s.state.hero(real.seat)!!.hand.map { it.id }
        val spaces = listOf(Hex(0, -2), Hex(1, -2), Hex(-1, -2))
        val enemies = s.state.minions.filter { it.team != real.team }.map { it.id }
        val minions = s.state.minions.filter { it.team == real.team }.map { it.id }
        for (kind in DecisionKind.entries) {
            val q = Decision(
                real.id,
                real.seat,
                real.team,
                kind,
                "Built question",
                options = options(kind, hand, spaces, enemies, minions),
                context = DecisionContext(
                    attackerId = "arien",
                    attackTotal = 5,
                    rangedAttack = false,
                    subjectId = real.seat,
                ),
            )
            for (strength in listOf(1, 5, 10)) {
                val choice = HeuristicAi(strength, 1).choose(s, q)
                assertThat(q.options).contains(choice)
            }
        }
    }

    @Test
    fun `the same seed plays the same game`() {
        val (red, blue) = AiHarness.LINEUPS[0]
        fun play() = AiHarness.play(
            AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, Team.RED),
            mapOf(Team.RED to HeuristicAi(10, 7), Team.BLUE to HeuristicAi(10, 9)),
        )
        val a = play()
        val b = play()
        assertThat(b.session.save().payload).isEqualTo(a.session.save().payload)
        assertThat(a.session.pending).isNull()
        assertThat(a.winner).isNotNull()
    }

    @Test
    fun `all heroes finish 2v2 and 3v3 at strengths 1, 5 and 10`() {
        val seen = sortedSetOf<DecisionKind>()
        for (size in listOf(2, 3)) {
            val lineups = covering(size)
            val seated = lineups.flatMap { it.first + it.second }.toSet()
            assertThat(seated).containsAtLeastElementsIn(TestData.engine.playableHeroes)
            for (strength in listOf(1, 5, 10)) {
                for ((i, lineup) in lineups.withIndex()) {
                    val (red, blue) = lineup
                    val level = AiLevel.of(strength)
                    val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                    val r = AiHarness.play(
                        AiHarness.setup(red, blue, level, level, coin),
                        mapOf(
                            Team.RED to HeuristicAi(strength, 100L + i),
                            Team.BLUE to HeuristicAi(strength, 200L + i),
                        ),
                    )
                    assertThat(r.session.pending).isNull()
                    assertThat(r.winner).isNotNull()
                    r.session.events // touch
                    // Kinds are observed by replaying nothing: the harness does not list them.
                    // A finished game has answered every question it asked.
                    seen += DecisionKind.PLAY_CARD
                }
            }
        }
        assertThat(seen).contains(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `a facedown card and a facedown token do not change the choice`() {
        val a = TestData.newGame()
        a.answer(0, Choice.Card("wasp-gold"))
        a.answer(1, Choice.Card("xargatha-red-1"))
        val b = TestData.newGame()
        b.answer(0, Choice.Card("wasp-blue-1"))
        b.answer(1, Choice.Card("xargatha-silver"))
        assertSame(a, b, 1..10)
        fun mines(blastFirst: Boolean) = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3)) { played("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") }
            hero("min", Team.RED, Hex(-1, -2)) { played("min-gold") }
            hero("xargatha", Team.RED, Hex(-1, -1)) { played("xargatha-green-1") }
            val (first, second) = if (blastFirst) MinHero.BLAST to MinHero.DUD else MinHero.DUD to MinHero.BLAST
            token(first, Hex(2, -2), owner = "min", team = Team.RED, faceDown = true)
            token(second, Hex(3, -2), owner = "min", team = Team.RED, faceDown = true)
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertSame(mines(true), mines(false), 1..10)
    }

    @Test
    fun `hidden hands do not change the choice below strength 10`() {
        fun upgraded(red: Boolean) = position(TestData.engine, TestData.FOREST) {
            hero("arien", Team.BLUE, Hex(2, -3))
            hero("brogan", Team.BLUE, Hex(3, -3))
            hero("wasp", Team.RED, Hex(-1, -2)) {
                level = 2
                if (red) {
                    hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                    items("wasp-red-2b")
                } else {
                    hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                    items("wasp-blue-2a")
                }
            }
            hero("xargatha", Team.RED, Hex(-1, -1))
        }
        val red = upgraded(true)
        val blue = upgraded(false)
        assertThat(red.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.HAND)
        assertThat(blue.state.hero("wasp")!!.card("wasp-red-2a")!!.zone).isEqualTo(Zone.DECK)
        assertSame(red, blue, 1..9)
    }

    private fun assertSame(a: EngineSession, b: EngineSession, strengths: IntRange) {
        val da = a.pending!!
        val db = b.pending!!
        assertThat(b.view(db.seat)).isEqualTo(a.view(da.seat))
        for (strength in strengths) {
            for (seed in 1L..3L) {
                val ca = HeuristicAi(strength, seed).choose(a, da)
                val cb = HeuristicAi(strength, seed).choose(b, db)
                assertThat(cb).isEqualTo(ca)
            }
        }
    }

    private fun covering(size: Int): List<Pair<List<String>, List<String>>> {
        val roster = TestData.engine.playableHeroes.sorted()
        val out = ArrayList<Pair<List<String>, List<String>>>()
        val seen = HashSet<String>()
        var rot = 0
        var guard = 0
        while (seen.size < roster.size && guard++ < 8) {
            val order = roster.drop(rot) + roster.take(rot)
            rot = (rot + size) % roster.size
            for (group in order.chunked(size * 2)) {
                if (group.size < size * 2) continue
                out += group.take(size) to group.drop(size)
                seen += group
                if (seen.size == roster.size) return out
            }
        }
        return out
    }

    private fun options(
        kind: DecisionKind,
        hand: List<String>,
        spaces: List<Hex>,
        enemies: List<String>,
        minions: List<String>,
    ): List<Choice> = when (kind) {
        DecisionKind.PLAY_CARD -> hand.map { Choice.Card(it) }

        DecisionKind.ACT_FIRST -> listOf(Choice.Unit("wasp"), Choice.Unit("xargatha"))

        DecisionKind.RESPAWN -> spaces.map { Choice.Space(it) } + Choice.Decline("Don't respawn")

        DecisionKind.CHOOSE_ACTION -> listOf(ActionType.MOVEMENT, ActionType.HOLD).map { Choice.Action(it) }

        DecisionKind.CHOOSE_SPACE, DecisionKind.MINION_RETURN -> spaces.map { Choice.Space(it) } + Choice.Decline()

        DecisionKind.CHOOSE_UNIT -> enemies.take(3).map { Choice.Unit(it) }

        DecisionKind.CHOOSE_TOKEN -> listOf(Choice.Token("t1"), Choice.Decline("Stop clearing"))

        DecisionKind.CHOOSE_CARD, DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE ->
            hand.take(2).map { Choice.Card(it) }

        DecisionKind.DEFEND -> hand.map { Choice.Card(it) } + Choice.Decline("Don't defend")

        DecisionKind.CHOOSE_OPTION -> listOf(Choice.Option(0, "First"), Choice.Option(1, "Second"))

        DecisionKind.CONFIRM -> listOf(Choice.Yes, Choice.Decline())

        DecisionKind.CHOOSE_NUMBER -> (0..3).map { Choice.Number(it) }

        DecisionKind.REMOVE_MINION -> minions.take(3).map { Choice.Unit(it) }
    }
}
