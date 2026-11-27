package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import org.junit.Test

class TeamStrategyTest {
    private val engine = TestData.engine

    @Test
    fun `every hero has a finite kit`() {
        val geo = engine.data.geometry(TestData.FOREST)
        println("LANES " + geo.lanes.mapValues { it.value.zones })
        for (id in engine.playableHeroes.sorted()) {
            val other = if (id == "arien") "brogan" else "arien"
            val s = position(engine, TestData.FOREST) {
                hero(id, Team.RED, geo.heroSpawns(Team.RED)[0])
                hero(other, Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
                phase = PositionPhase.PlayCards(0)
            }
            val ctx = HContext(s, s.pending, Team.RED, HeuristicProfile.of(9))
            val k = TeamStrategy.kit(ctx, id)
            println("KIT %-12s kill %.2f clear %.2f dura %.2f mob %.2f setup %.2f".format(id, k.kill, k.clear, k.durability, k.mobility, k.setup))
            for (x in listOf(k.kill, k.clear, k.durability, k.mobility, k.setup)) assertThat(x.isFinite()).isTrue()
        }
    }

    private val geo = engine.data.geometry(TestData.FOREST)

    /** Tigerclaw (the roster's sharpest killer) with Mrak (its most durable) against Arien and Brogan. */
    private fun duo(
        round: Int = 3,
        redLevel: Int = 1,
        redLife: Int? = null,
        blueLife: Int? = null,
        zone: String? = null,
        waves: Int? = null,
    ): EngineSession = position(engine, TestData.FOREST) {
        this.round = round
        hero("tigerclaw", Team.RED, geo.heroSpawns(Team.RED)[0]) { level = redLevel }
        hero("mrak", Team.RED, geo.heroSpawns(Team.RED)[1]) { level = redLevel }
        hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
        hero("brogan", Team.BLUE, geo.heroSpawns(Team.BLUE)[1])
        redLife?.let { lifeCounters(Team.RED, it) }
        blueLife?.let { lifeCounters(Team.BLUE, it) }
        zone?.let { battleZone("lane-1", it) }
        waves?.let { wavesLeft("lane-1", it) }
        phase = PositionPhase.PlayCards(0)
    }

    private fun ctx(s: EngineSession, strength: Int = 9, tune: Map<String, Double> = emptyMap()) =
        HContext(s, s.pending, Team.RED, HeuristicProfile.of(strength).copy(tune = tune))

    @Test
    fun `the opening is a scaling game and the most durable hero anchors`() {
        val v = TeamStrategy.view(ctx(duo(round = 1)), Team.RED)
        assertThat(v.plan).isEqualTo(TeamStrategy.Plan.SCALE)
        assertThat(v.jobs).containsExactly("mrak", TeamStrategy.Job.ANCHOR, "tigerclaw", TeamStrategy.Job.FARMER)
    }

    @Test
    fun `one death from their defeat is a kill plan - the killer finishes, the other strips`() {
        val c = ctx(duo(blueLife = 1))
        val v = TeamStrategy.view(c, Team.RED)
        assertThat(v.plan).isEqualTo(TeamStrategy.Plan.KILL)
        assertThat(v.hunt).isTrue()
        assertThat(v.jobs).containsExactly("tigerclaw", TeamStrategy.Job.FINISHER, "mrak", TeamStrategy.Job.STRIPPER)
        // G117: their death is worth more to us; switched off by the knob, the master and below strength 7.
        assertThat(TeamStrategy.deathScale(c, Team.BLUE)).isWithin(1e-9).of(1.15)
        assertThat(TeamStrategy.deathScale(ctx(duo(blueLife = 1), tune = mapOf("plan.race" to 0.0)), Team.BLUE)).isEqualTo(1.0)
        assertThat(TeamStrategy.deathScale(ctx(duo(blueLife = 1), tune = mapOf("plan" to 0.0)), Team.BLUE)).isEqualTo(1.0)
        assertThat(TeamStrategy.deathScale(ctx(duo(blueLife = 1), strength = 6), Team.BLUE)).isEqualTo(1.0)
        // The finisher hunts harder than its own book, the stripper a little.
        val base = HeroPlaybooks.forHero("tigerclaw").style
        assertThat(c.style("tigerclaw").aggression).isGreaterThan(base.aggression)
    }

    @Test
    fun `one push from their throne is a push plan - the anchor holds, the other pushes`() {
        val c = ctx(duo(zone = "lane-1-3", waves = 2))
        val v = TeamStrategy.view(c, Team.RED)
        assertThat(v.plan).isEqualTo(TeamStrategy.Plan.PUSH)
        assertThat(v.jobs).containsExactly("mrak", TeamStrategy.Job.ANCHOR, "tigerclaw", TeamStrategy.Job.PUSHER)
        val base = HeroPlaybooks.forHero("tigerclaw").style
        val s = c.style("tigerclaw")
        assertThat(s.laneWeight).isGreaterThan(base.laneWeight)
        assertThat(s.aggression).isLessThan(base.aggression)
        // The enemy reads its own plan from the same public facts: three pushes from our throne, a weaker push for them.
        val push = TeamStrategy.Plan.PUSH
        assertThat(TeamStrategy.view(c, Team.BLUE).scores.getValue(push)).isLessThan(v.scores.getValue(push) - 0.5)
    }

    @Test
    fun `the last push when we can afford a death and they cannot - our deaths are cheaper`() {
        val c = ctx(duo(redLevel = 4, redLife = 4, blueLife = 1, waves = 1))
        val v = TeamStrategy.view(c, Team.RED)
        assertThat(v.ours).isEqualTo(2)
        assertThat(v.theirs).isEqualTo(1)
        assertThat(v.afford).isTrue()
        assertThat(v.lastWave).isTrue()
        assertThat(TeamStrategy.deathScale(c, Team.RED)).isWithin(1e-9).of(0.85)
        // Without the last wave a death is only a little cheaper.
        assertThat(TeamStrategy.deathScale(ctx(duo(redLevel = 4, redLife = 4, blueLife = 1)), Team.RED))
            .isWithin(1e-9).of(0.925)
    }

    @Test
    fun `we cannot afford a death and they can - play safe and keep a defender`() {
        val c = ctx(duo(redLife = 1))
        val v = TeamStrategy.view(c, Team.RED)
        assertThat(v.danger).isTrue()
        assertThat(TeamStrategy.deathScale(c, Team.RED)).isWithin(1e-9).of(1.15)
        for (id in listOf("tigerclaw", "mrak")) {
            val base = HeroPlaybooks.forHero(id).style
            assertThat(c.style(id).keepDefender).isTrue()
            assertThat(c.style(id).aggression).isLessThan(ctx(duo(), tune = mapOf("plan" to 0.0)).style(id).aggression + 1e-9)
            assertThat(base.aggression).isAtLeast(0.0)
        }
    }

    @Test
    fun `with the plan off or below strength 7 the style is the hero file's`() {
        for ((strength, tune) in listOf(9 to mapOf("plan" to 0.0), 9 to mapOf("plan.style" to 0.0), 6 to emptyMap())) {
            val c = ctx(duo(blueLife = 1), strength, tune)
            for (id in listOf("tigerclaw", "mrak", "arien", "brogan")) {
                assertThat(c.style(id)).isEqualTo(HeroPlaybooks.forHero(id).style)
            }
        }
    }

    @Test
    fun `the plan is the same from a fresh context`() {
        val s = duo(blueLife = 2, zone = "lane-1-1")
        for (team in Team.entries) {
            val a = TeamStrategy.view(ctx(s), team)
            val b = TeamStrategy.view(ctx(s), team)
            assertThat(a.plan).isEqualTo(b.plan)
            assertThat(a.scores).isEqualTo(b.scores)
            assertThat(a.jobs).isEqualTo(b.jobs)
        }
    }

    @Test
    fun `economy - the Tier III step, a due level and the Pity coin`() {
        val s = duo()
        val h = s.state.hero("tigerclaw")!!
        fun econ(level: Int, coins: Int, levelled: Boolean = false): Double {
            h.level = level
            h.coins = coins
            h.levelledThisRound = levelled
            return TeamStrategy.economy(h)
        }
        // Level 4 with 4 coins levels to 5 this round: 0.85 x (3 + 2) on top of HEval's 1.5.
        assertThat(econ(4, 4)).isWithin(1e-9).of(0.85 * 5 - 1.5)
        // An ordinary due level: 0.85 x 3 - 1.5.
        assertThat(econ(2, 2)).isWithin(1e-9).of(0.85 * 3 - 1.5)
        // Not due: the Pity coin; already levelled this round: nothing more.
        assertThat(econ(2, 1)).isEqualTo(1.0)
        assertThat(econ(3, 0, levelled = true)).isEqualTo(0.0)
        // Level 5 holds the Tier III step.
        assertThat(econ(5, 0)).isEqualTo(3.0)
        // Two levels due at once (2 + 3 coins from level 2).
        assertThat(econ(2, 5)).isWithin(1e-9).of(0.85 * 6 - 1.5)
        // The term is off with the knob and below strength 5.
        assertThat(TeamStrategy.evalTerm(ctx(duo(), tune = mapOf("plan.econ" to 0.0)))).isEqualTo(0.0)
        assertThat(TeamStrategy.evalTerm(ctx(duo(), strength = 4))).isEqualTo(0.0)
    }

    /** The first card [heroId] keeps when levelling from 1 with 10 coins, beside [partner]. */
    private fun firstKeep(heroId: String, partner: String, round: Int, blueLife: Int?, tune: Map<String, Double>): String {
        val s = position(engine, TestData.FOREST) {
            this.round = round
            hero(heroId, Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 10 }
            hero(partner, Team.RED, geo.heroSpawns(Team.RED)[1])
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            hero("brogan", Team.BLUE, geo.heroSpawns(Team.BLUE)[1])
            blueLife?.let { lifeCounters(Team.BLUE, it) }
            phase = PositionPhase.EndOfRound
        }
        val ai = HeuristicAi(9, 1L, tune = tune)
        repeat(40) {
            val d = s.pending ?: error("no level up")
            val c = ai.choose(s, d)
            if (d.seat == heroId && d.kind == DecisionKind.KEEP_UPGRADE) return (c as Choice.Card).cardId
            s.answer(d.id, c)
        }
        error("no keep")
    }

    @Test
    fun `the finisher keeps the harder hitter and takes the initiative item`() {
        // Rowenna's Tier II reds: 2b attack 7 (its item Defense), 2a attack 6 (its item Initiative).
        val off = mapOf("plan" to 0.0)
        assertThat(firstKeep("rowenna", "mrak", 3, 1, emptyMap())).isEqualTo("rowenna-red-2b")
        assertThat(firstKeep("rowenna", "mrak", 3, 1, off)).isEqualTo("rowenna-red-2a")
    }

    @Test
    fun `the anchor keeps the higher defense and takes the Defense item`() {
        // Bain's Tier II reds: 2b defense 6 (its item Initiative), 2a defense 5 (its item Defense).
        val off = mapOf("plan" to 0.0)
        assertThat(firstKeep("bain", "tigerclaw", 1, null, emptyMap())).isEqualTo("bain-red-2b")
        assertThat(firstKeep("bain", "tigerclaw", 1, null, off)).isEqualTo("bain-red-2a")
    }

    @Test
    fun `scan level ups with and without the plan`() {
        if (System.getenv("AI_PLAN_SCAN") == null) return
        val geo = engine.data.geometry(TestData.FOREST)
        for (id in engine.playableHeroes.sorted()) for (partner in listOf("mrak", "tigerclaw")) for (kill in listOf(false, true)) {
            if (id == partner) continue
            val foes = listOf("arien", "brogan", "wasp").filter { it != id && it != partner }.take(2)
            val s = position(engine, TestData.FOREST) {
                round = if (kill) 3 else 1
                hero(id, Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 10 }
                hero(partner, Team.RED, geo.heroSpawns(Team.RED)[1])
                hero(foes[0], Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
                hero(foes[1], Team.BLUE, geo.heroSpawns(Team.BLUE)[1])
                if (kill) lifeCounters(Team.BLUE, 1)
                phase = PositionPhase.EndOfRound
            }
            val on = HeuristicAi(9, 1L)
            val off = HeuristicAi(9, 1L, tune = mapOf("plan" to 0.0))
            val line = StringBuilder()
            var guard = 0
            while (guard++ < 40) {
                val d = s.pending ?: break
                if (d.seat != id || (d.kind != DecisionKind.UPGRADE_CARD && d.kind != DecisionKind.KEEP_UPGRADE)) {
                    if (d.seat == id) break
                    s.answer(d.id, on.choose(s, d)); continue
                }
                val a = on.choose(s, d) as Choice.Card
                val b = off.choose(s, d) as Choice.Card
                val ctx = HContext(s, d, Team.RED, HeuristicProfile.of(9))
                val job = TeamStrategy.job(ctx, id)
                fun st(id: String) = ctx.card(id).let { "a${it.attack}d${it.defense}i${it.initiative}r${it.range}${if (it.ranged) "R" else ""}x${it.radius}m${it.movement}${if (it.forcesDiscard || it.discardOrDefeat) "S" else ""}${if (it.block) "B" else ""}:${it.item}" }
                val diff = if (a != b) " (off ${b.cardId})" + if (d.kind == DecisionKind.KEEP_UPGRADE) " {${st(a.cardId)} vs ${st(b.cardId)}}" else "" else ""
                line.append(" ${d.kind.name.take(4)}[$job] ${a.cardId}$diff")
                s.answer(d.id, a)
            }
            println("SCAN $id+$partner ${if (kill) "kill" else "early"}:$line")
        }
    }
}
