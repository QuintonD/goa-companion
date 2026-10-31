package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachReasonKind
import dev.quinton.goa.core.game.CoachSuggestions
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.lowestTier
import dev.quinton.goa.core.game.engine.minionModifier
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import java.io.File
import org.junit.Test

/**
 * The suggestions (ADR-051) over whole games from all 32 heroes: every sentence checked against the
 * events it came from and the printed values, no hidden card named, every reason with its page. A game by
 * default; `AI_COACH_SURVEY=<games>` for the survey of AI.md "The coach", `AI_COACH_SURVEY_OUT=<file>`
 * writes every output for reading.
 */
class CoachSuggestTest {
    private val unhurried = 3_600_000L

    private fun coach() = AiCoach(TestData.engine, 0x0C0AC4L, unhurried)

    @Test
    fun `every sentence of a survey is true`() {
        val games = System.getenv("AI_COACH_SURVEY")?.toIntOrNull() ?: 1
        val out = System.getenv("AI_COACH_SURVEY_OUT")?.let { File(it).printWriter() }
        val survey = Survey(out)
        val lineups = AiHarness.mixedLineups(games, seed = 4_242L, size = 3)
        val pace = if (games == 1) 6 else 3
        for ((g, lineup) in lineups.withIndex()) {
            val (red, blue) = lineup
            val weak = if (g % 2 == 0) 4 else 6
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.RED))
                as EngineSession
            val players = mapOf<Team, AiPlayer>(
                Team.RED to AiPlayers.deterministic(weak, 100L + g),
                Team.BLUE to AiPlayers.deterministic(8, 200L + g),
            )
            val coach = coach()
            var n = 0
            while (n < 8000) {
                val d = s.pending ?: break
                // Every kind that is rare, a share of the rest: a game has thousands of questions.
                if (d.options.size > 1 && (d.kind !in COMMON || n % pace == 0)) survey.look(coach, s, d)
                s.answer(d.id, players.getValue(d.team).choose(s, d))
                n++
            }
        }
        out?.close()
        survey.report()
        assertThat(survey.asked).isGreaterThan(20)
        assertThat(survey.errors).isEmpty()
        if (games >= 12) {
            // Every hero gets reasons about what it actually does.
            assertThat(survey.byHero.keys).hasSize(TestData.engine.playableHeroes.size)
        }
    }

    private class Survey(val out: java.io.PrintWriter?) {
        var asked = 0
        var spoken = 0
        var ms = 0.0
        var maxMs = 0.0
        val tookMs = ArrayList<Double>()
        val kinds = sortedMapOf<String, Int>()
        val reasons = sortedMapOf<String, Int>()
        val byHero = sortedMapOf<String, MutableMap<String, Int>>()
        val errors = ArrayList<String>()
        var lines = 0
        var plans = 0
        var planOrders = 0
        var threats = 0
        var close = 0
        var planAsked = 0

        fun look(coach: AiCoach, s: EngineSession, d: Decision) {
            val t0 = System.nanoTime()
            val built = coach.built(coach.prepare(s.fork(), d), s.fork(), d) { false } ?: return
            val took = (System.nanoTime() - t0) / 1e6
            ms += took
            tookMs += took
            maxMs = maxOf(maxMs, took)
            asked++
            kinds[d.kind.name] = (kinds[d.kind.name] ?: 0) + 1
            val sug = built.suggestions
            if (sug.options.any { it.reasons.isNotEmpty() }) spoken++
            if (sug.options.any { it.line.isNotEmpty() }) lines++
            if (d.kind == DecisionKind.PLAY_CARD) planAsked++
            sug.plan?.let {
                plans++
                if (it.order.isNotEmpty()) planOrders++
            }
            threats += sug.threats.size
            if (sug.close) close++
            for (o in sug.options) {
                for (r in o.reasons + o.against) {
                    reasons[r.kind.name] = (reasons[r.kind.name] ?: 0) + 1
                    val hero = byHero.getOrPut(d.seat) { sortedMapOf() }
                    hero[r.kind.name] = (hero[r.kind.name] ?: 0) + 1
                }
            }
            check(built, s, d)
            out?.let { dump(it, sug, s, d) }
        }

        private fun fail(d: Decision, what: String) {
            errors += "${d.seat} ${d.kind} #${d.id}: $what"
        }

        private fun check(built: AiCoach.Built, s: EngineSession, d: Decision) {
            val sug = built.suggestions
            val f = built.facts
            for (o in sug.options) {
                assertOption(d, o.choice in d.options, "offered ${o.choice}")
                for (r in o.reasons + o.against) {
                    if (!Regex("p\\.\\d+").containsMatchIn(r.detail)) fail(d, "no page: ${r.text} | ${r.detail}")
                    numbers(f, d, r.kind, r.text)
                }
                for (w in built.whys[o.choice].orEmpty()) claim(f, d, o.choice, w)
            }
            hiddenCards(f, s, d, sug)
            for (i in 1 until sug.options.size) {
                val o = sug.options[i]
                if (o.chance > sug.options[0].chance) fail(d, "the ranking contradicts its chances")
                if (o.behind != sug.options[0].chance - o.chance) {
                    fail(
                        d,
                        "behind ${o.behind} against the chances " +
                            "shown",
                    )
                }
            }
        }

        private fun assertOption(d: Decision, ok: Boolean, what: String) {
            if (!ok) fail(d, what)
        }

        /** The numbers in a sentence against the context and the printed tables. */
        private fun numbers(f: CoachFacts, d: Decision, kind: CoachReasonKind, text: String) {
            when (kind) {
                CoachReasonKind.DEFENSE_HOLDS, CoachReasonKind.DEFENSE_FALLS -> {
                    val m = Regex("Defense (\\d+) against Attack (\\d+)").find(text) ?: return
                    val (def, att) = m.destructured
                    if (att.toInt() != d.context.attackTotal) fail(d, "attack total in: $text")
                    if (def.toInt() !in d.context.defensePreview.values) fail(d, "defense total in: $text")
                    val holds = def.toInt() >= att.toInt()
                    if (holds != (kind == CoachReasonKind.DEFENSE_HOLDS) && "can still " +
                        "stop" !in text
                    ) {
                        fail(d, "verdict in: $text")
                    }
                }

                CoachReasonKind.LIFE_COST -> {
                    val tier = f.g.lowestTier(f.seat)
                    val life = f.view.lifeCounters[f.team] ?: 0
                    val says = if (life <= tier) "last Life counter" else "$tier Life counter"
                    if (says !in text) fail(d, "Life cost in: $text (tier $tier, life $life)")
                    if ("gains ${f.me.level} coin" !in text) fail(d, "coins in: $text")
                }

                CoachReasonKind.UPGRADE -> Regex(
                    "\\+1 " +
                        "(Attack|Defense|Initiative|Movement|Range|Radius)",
                ).findAll(text).forEach { m ->
                    val stat = Stat.valueOf(m.groupValues[1].uppercase())
                    val named = d.options.filterIsInstance<Choice.Card>().flatMap { c ->
                        listOf(c.cardId) + d.context.upgrades[c.cardId].orEmpty()
                    }
                    if (named.none { f.data.card(it).item == stat }) fail(d, "item in: $text")
                }

                CoachReasonKind.MINION_BATTLE -> {
                    val m = Regex("your (\\d+) against their (\\d+)").find(text) ?: return fail(d, "counts in: $text")
                    val lane = f.state.battleZones.keys.single()
                    val (ours, theirs) = f.battleCount(lane)!!
                    if (m.groupValues[1].toInt() != ours || m.groupValues[2].toInt() != theirs) {
                        fail(
                            d,
                            "counts in: " +
                                "$text",
                        )
                    }
                }

                CoachReasonKind.MINION_MODIFIER -> {
                    val hex = (d.options.firstOrNull { it is Choice.Space } as? Choice.Space)?.hex ?: return
                    f.g.minionModifier(hex, f.team, null)
                }

                else -> Unit
            }
        }

        private fun leaves(f: CoachFacts, c: Choice) = f.leaves(c)

        /** A claim about the playouts, counted again here from their events. */
        private fun claim(f: CoachFacts, d: Decision, c: Choice, w: Why) {
            val claim = w.claim ?: return
            val ls = leaves(f, c)
            if (ls.isEmpty()) return fail(d, "a reason for an option with no playouts: ${w.reason.text}")
            fun share(test: (List<GameEvent>) -> Boolean) = ls.count { test(f.events(it)) }.toDouble() / ls.size
            val seat = f.seat
            val counted = when (claim) {
                PlayoutClaim.DEFEATS_HERO -> share { ev ->
                    ev.any { it is GameEvent.Defeated && it.byHeroId == seat && f.enemy(it.unitId) }
                }

                PlayoutClaim.SEAT_SAFE -> share { ev -> ev.none { it is GameEvent.Defeated && it.unitId == seat } }

                PlayoutClaim.PUSH_OURS -> share { ev -> ev.any { it is GameEvent.LanePushed && it.winner == f.team } }

                PlayoutClaim.GAME_OVER -> share { ev ->
                    ev.any { it is GameEvent.GameOver && it.winner.name == w.subject }
                }

                PlayoutClaim.MINIONS -> share { ev ->
                    ev.any { it is GameEvent.Defeated && it.byHeroId == seat && !f.isHero(it.unitId) }
                }

                else -> null
            }
            if (counted != null && counted + 1e-9 < w.share) {
                fail(
                    d,
                    "$claim claims ${w.share}, counted $counted: " +
                        "${w.reason.text}",
                )
            }
            if (claim == PlayoutClaim.DEFEATS_HERO) {
                val victim = w.subject!!
                if (f.data.heroName(victim) !in w.reason.text) fail(d, "names the wrong hero: ${w.reason.text}")
                if (ls.none { l ->
                        f.events(l).any {
                            it is GameEvent.Defeated && it.unitId == victim &&
                                it.byHeroId == seat
                        }
                    }
                ) {
                    fail(d, "$victim never falls: ${w.reason.text}")
                }
            }
            if (claim == PlayoutClaim.MECHANIC && w.share < 0.6) fail(d, "a mechanic in few playouts: ${w.reason.text}")
        }

        /** No sentence names a card of another hero that is not open, unless it says "could". */
        private fun hiddenCards(f: CoachFacts, s: EngineSession, d: Decision, sug: CoachSuggestions) {
            val mine = f.data.catalogHero(d.seat).allCards.map { it.name }.toSet()
            for (other in s.setup.seats.filter { it.heroId != d.seat }) {
                for (card in f.data.catalogHero(other.heroId).allCards) {
                    if (card.name in mine || f.openCard(other.heroId, card.id)) continue
                    val pattern = Regex("\\b${Regex.escape(card.name)}\\b")
                    val texts = sug.options.flatMap { o -> o.line + (o.reasons + o.against).map { it.text } } +
                        sug.threats.map { it.text } + listOfNotNull(sug.plan?.text)
                    for (t in texts) {
                        // A name of the seat's own may hold another hero's shorter one ("Control Gravity").
                        val rest = mine.sortedByDescending { it.length }.fold(t) { acc, own -> acc.replace(own, "") }
                        if (pattern.containsMatchIn(rest) && "could" !in rest) fail(d, "names ${card.id}, hidden: $t")
                    }
                }
            }
        }

        private fun dump(w: java.io.PrintWriter, sug: CoachSuggestions, s: EngineSession, d: Decision) {
            val st = s.state
            w.println(
                "=== R${st.round}T${st.turn} ${d.seat} ${d.kind} #${d.id}: ${d.prompt} (${d.options.size} " +
                    "options, ${sug.samples} samples)",
            )
            for ((i, o) in sug.options.withIndex()) {
                w.println("  ${i + 1}. ${o.label}: ${o.chance}% (-${o.behind}) ${o.confidence}")
                o.reasons.forEach { w.println("     WHY[${it.kind}] ${it.text} | ${it.detail}") }
                o.against.forEach { w.println("     AGAINST[${it.kind}] ${it.text} | ${it.detail}") }
                o.line.forEach { w.println("     LINE $it") }
            }
            if (sug.close) w.println("  CLOSE")
            sug.threats.forEach { w.println("  THREAT ${it.text}") }
            sug.plan?.let { w.println("  PLAN ${it.text} | ${it.detail}") }
        }

        fun report() {
            println(
                "SUGGEST survey: $asked decisions, reasons on $spoken, lines on $lines, close $close, threats " +
                    "$threats, " +
                    "plans $plans of $planAsked card plays ($planOrders with an order), mean " +
                    "${"%.0f".format(ms / asked.coerceAtLeast(1))} ms, " +
                    "p95 ${"%.0f".format(tookMs.sorted().getOrElse((tookMs.size * 0.95).toInt()) { 0.0 })} ms, max " +
                    "${"%.0f".format(maxMs)} ms",
            )
            println("SUGGEST kinds: $kinds")
            println("SUGGEST reasons: $reasons")
            byHero.forEach { (h, r) -> println("SUGGEST hero $h: $r") }
            errors.take(40).forEach { println("SUGGEST ERROR $it") }
            println("SUGGEST ${errors.size} errors")
        }
    }

    /** Brogan's Onslaught (Attack 4) hits Wasp, who holds Blue (Defense 5) and Gold (Defense 2). */
    private fun attacked() = pos(engine = TestData.engine) {
        clearMinions()
        hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-gold", "wasp-red-1", exactly = true) }
        hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        turn = 2
        phase = PositionPhase.Resolving(next = "brogan")
    }.also { it.action("brogan", dev.quinton.goa.core.game.ActionType.PRIMARY) }

    @Test
    fun `at a defense the card that holds comes first, with the printed numbers`() {
        val d = attacked()
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        val sug = coach().suggest(null, d.s, q)!!
        println("SUGGEST defend: $sug")
        val top = sug.options.first()
        assertThat(top.choice).isEqualTo(Choice.Card("wasp-blue-1"))
        assertThat(top.reasons.map { it.kind }).contains(CoachReasonKind.DEFENSE_HOLDS)
        assertThat(top.reasons.first().text).contains("Defense 5 against Attack 4")
    }

    @Test
    fun `suggesting after a look ahead gives the same as from nothing, and changes nothing`() {
        val d = attacked()
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        val before = d.s.view("wasp")
        val fromNothing = coach().suggest(null, d.s.fork(), q)
        val c = coach()
        val ahead = c.suggest(c.prepare(d.s.fork(), q), d.s.fork(), q)
        assertThat(ahead).isEqualTo(fromNothing)
        assertThat(d.s.view("wasp")).isEqualTo(before)
    }

    @Test
    fun `the win chance is only as fine as the fit`() {
        assertThat(WinChance.percent(0.63)).isEqualTo(65)
        assertThat(WinChance.percent(0.999)).isEqualTo(95)
        assertThat(WinChance.percent(1.0)).isEqualTo(100)
        assertThat(WinChance.of(40.0, 3)).isWithin(0.02).of(0.74)
        assertThat(WinChance.of(0.0, 3)).isEqualTo(0.5)
        assertThat(WinChance.of(Evaluator.WIN - 5, 3)).isEqualTo(1.0)
    }

    private companion object {
        val COMMON = setOf(DecisionKind.CHOOSE_SPACE, DecisionKind.CHOOSE_ACTION, DecisionKind.PLAY_CARD)
    }
}
