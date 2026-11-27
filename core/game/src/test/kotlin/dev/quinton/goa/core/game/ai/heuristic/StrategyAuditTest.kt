package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in behaviour audit of the computer player (strategy round, Track M). Plays the same mirrored games as
 * [HeuristicTournamentTest] and counts what the players DID, read from the engine's events and state, so a
 * change to the book can be measured by behaviour as well as by win rate. RESULTS.md "Strategy round - Track M"
 * has the same definitions in a table and the baseline.
 *
 * `AI_AUDIT=h9:h9+team=0,h9:a10` (pairs, the tournament's side syntax: `hN[+knob=v…]` the book, `sN` the old
 * search, `xN`/`yN` the search prototypes, `h10/action.move/s10` category swaps, plus `aN` = the shipped player
 * at strength N, [AiPlayers.deterministic]: the book below 10, the hybrid search at 10 and 11).
 * `AI_AUDIT_GAMES` line-ups (each mirrored, so twice the games; default 16), `AI_H_FIRST` the first line-up
 * (default 2900, Track M's range), `AI_FORMAT` 2v2 / 3v3, `AI_THREADS` (default 2). The line-ups, seeds and
 * coins are [dev.quinton.goa.core.game.ai.Tournament.runMixed]'s, so an audit and a tournament on the same
 * range play the same games. `AI_AUDIT_TRAIL=k` prints game k's event log with the audit's notes (hand checks);
 * `AI_AUDIT_OUT=file` appends the summary lines.
 *
 * Output per side: totals and per-game means of every counter, a few rates, the ending table, the team
 * compositions and the level-up choices, then one `AUDIT_SUMMARY` line per side (`key=per-game-mean`, same keys
 * in the same order every run) to diff before against after.
 *
 * The counters (a "side" is one contestant; it plays Red in one game of a pair and Blue in the other):
 *
 * Economy
 * - `minions`: enemy minions defeated by the side's heroes (a [GameEvent.Defeated] of a minion with `byHeroId`);
 *   `minions.lost`: the side's minions defeated by enemy heroes (Minion Battle removals are not defeats).
 * - `coins.minion` / `coins.kill` / `coins.assist` / `coins.pity` / `coins.other`: coins gained, by the reason the
 *   engine writes in [GameEvent.CoinsGained] ("defeated a … minion", "defeated <hero>", "assist", "pity coin";
 *   anything else, e.g. coins taken by a card, is `other`).
 * - `lvl.rK`: the side's mean hero level at the end of round K (after its level-ups; read at the next
 *   [GameEvent.RoundStarted]); only rounds that ended are counted.
 * - `lvl5.share`: hero-games that reached level 5; `lvl5.round`: the mean round in which they did.
 *
 * Waves ([GameEvent.LanePushed]; `mid` = during the turns, `eor` = the end-of-round Push step)
 * - `push.won.mid` / `push.won.eor` / `push.lost.mid` / `push.lost.eor`: pushes the side won or suffered.
 * - `push.lost.double`: pushes suffered when the same side had already suffered one in this round or the round
 *   before. `push.lost.again`: pushes suffered when the lane's previous push also went against the side (the next
 *   wave lost too, however many rounds later: a double push in the owner's sense).
 * - `push.lost.in` / `.walk` / `.far` / `.dead`: for each push suffered, the side's heroes, read at the first
 *   decision after the push (the new wave has spawned; nobody moves in between except by the rest of the pushing
 *   action): standing in the new Battle Zone; outside it but able to reach it with one Movement action of the best
 *   Movement card in hand ([reach], the engine's own paths); farther; off the board. `push.lost.stranded`: the
 *   `far` heroes standing on the enemy's side of the new Battle Zone (the lane's zone order). `push.won.*` the same
 *   for pushes won (following the wave forward).
 * - `act.far`: actions ([DecisionKind.CHOOSE_ACTION]) offering Movement while the hero stands outside the Battle
 *   Zone and this card's Movement cannot reach it; `ft.offered.far` / `ft.blocked.far`: of those, Fast Travel was
 *   offered / was not; `ft.taken`: Fast Travel chosen (anywhere).
 * - `win.<reason>` / `loss.<reason>`: how the side's games ended ([dev.quinton.goa.core.game.WinReason]);
 *   `end.mid` / `end.eor`: the side's wins by a push made during the turns / at the end of the round.
 *
 * Farming against poking (an attack is a [GameEvent.Attacked]; a Razzle double counts as Razzle)
 * - `atk.minion`: attacks on minions. `atk.hero`: attacks on heroes, split into `atk.hero.kill` (this attack
 *   defeated the hero: the next hero defeat is that hero's, by this attacker, before any other attack),
 *   `atk.hero.followed` (not a kill, but the side defeated that hero later in the same turn or the next one) and
 *   `atk.hero.poke` (neither). `atk.hero.poke.minion`: pokes made by a hero whose action began with an enemy minion
 *   in reach (below).
 * - "Minion in reach" at a hero's action (CHOOSE_ACTION): the card it resolves is an attack card and an enemy
 *   minion that is not an immune heavy stands within the card's reach (printed range or 1, a printed pre-attack
 *   move, range items; [HContext.attackReach], hex distance, walls not walked).
 * - `act.minion`: actions that began with a minion in reach; of those `.took` (the hero defeated a minion this
 *   turn), `.untaken` (it did not), `.hero` (it attacked a hero instead), `.hold` (chose Hold), `.move` (chose
 *   Movement or Fast Travel). `act.hold`: every Hold chosen.
 *
 * Team play
 * - `kills`: enemy heroes the side defeated. `kills.stripFinish`: kills where a different hero of the side had
 *   attacked the victim earlier in the same round (the strip, then the finish); `kills.oneShot`: kills with no
 *   earlier attack on the victim by the side that round.
 * - `team.sameTarget`: turns in which two or more of the side's heroes attacked the same enemy hero.
 * - `assists`: assist payments (one per teammate per kill, [GameEvent.CoinsGained] "assist").
 * - `team.minionCollision`: actions with an attack card and no minion left in reach, where a teammate had
 *   defeated a minion within that reach earlier in the turn (the teammate took "my" minion).
 * - `deaths`: the side's heroes defeated; `deaths.emptyHand`: of those, with no card to defend with (the engine's
 *   note); `deaths.emptyHand.covered`: of those, a teammate stood within 3 spaces of the victim at the start of the
 *   turn (a teammate was there to screen or trade; a proxy, not a proof).
 *
 * Win condition: the role mix of each team ([HeroStyle.role] of its heroes, sorted) with games and wins, and per
 * hero the colours it upgraded and the item stats it tucked ([DecisionKind.UPGRADE_CARD], the "gains an item"
 * event).
 */
class StrategyAuditTest {
    @Test
    fun `audit`() {
        val spec = System.getenv("AI_AUDIT") ?: return
        val lineups = System.getenv("AI_AUDIT_GAMES")?.toIntOrNull() ?: 16
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2900
        HeuristicTournamentTest.checkUnsealed(first, lineups, System.getenv("AI_UNSEAL"))
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val trail = System.getenv("AI_AUDIT_TRAIL")?.toIntOrNull()
        lines = System.getenv("AI_AUDIT_LINES") != null
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            println("AUDIT $pair $format line-ups $first+$lineups (${2 * lineups} games), threads $threads")
            val t0 = System.nanoTime()
            val (a, b) = run(aName, bName, lineups, first, format, threads, trail)
            val secs = (System.nanoTime() - t0) / 1e9
            println("AUDIT $aName ${a.wins}-${b.wins} $bName of ${a.games} games in ${"%.0f".format(secs)} s")
            for (s in listOf(a, b)) println(s.report())
            val lines = listOf(a, b).map { "AUDIT_SUMMARY $pair $format $first+$lineups ${it.summary()}" }
            lines.forEach(::println)
            System.getenv("AI_AUDIT_OUT")?.let { java.io.File(it).appendText(lines.joinToString("\n", postfix = "\n")) }
        }
    }

    /** One short game at strength 4: the audit's counts agree with the engine's log and state. */
    @Test
    fun `the audit's counts agree with the game log`() {
        val (a, b) = run("h4", "h4", 1, 2900, AiHarness.Format.TWO, 1, null)
        assertThat(a.games).isEqualTo(2)
        for (s in listOf(a, b)) {
            val c = s.c
            // Every coin falls in exactly one reason; minion coins are 2 or 4 per minion.
            assertThat(c.v("coins.minion")).isAtLeast(2.0 * c.v("minions"))
            assertThat(c.v("coins.minion")).isAtMost(4.0 * c.v("minions"))
            // A hero attack is a kill, followed or a poke; an action with a minion in reach took one or not.
            assertThat(c.v("atk.hero.kill") + c.v("atk.hero.followed") + c.v("atk.hero.poke")).isEqualTo(c.v("atk.hero"))
            assertThat(c.v("act.minion.took") + c.v("act.minion.untaken")).isEqualTo(c.v("act.minion"))
            assertThat(c.v("kills.stripFinish")).isAtMost(c.v("kills"))
            assertThat(c.v("ft.offered.far") + c.v("ft.blocked.far")).isEqualTo(c.v("act.far"))
        }
        // Kills of one side are deaths of the other; pushes won by one are suffered by the other.
        assertThat(a.c.v("kills")).isEqualTo(b.c.v("deaths"))
        assertThat(b.c.v("kills")).isEqualTo(a.c.v("deaths"))
        assertThat(a.c.v("push.won.mid") + a.c.v("push.won.eor")).isEqualTo(b.c.v("push.lost.mid") + b.c.v("push.lost.eor"))
        assertThat(a.c.v("assists")).isEqualTo(a.c.v("kills")) // 2v2: one teammate per kill
    }

    // ---- running ------------------------------------------------------------------------------------------------

    /** `AI_AUDIT_LINES=1`: one line per game (line-up, side a's colour, the result, the pushes in order). */
    private var lines = false

    private fun run(
        aName: String,
        bName: String,
        lineups: Int,
        first: Int,
        format: AiHarness.Format,
        threads: Int,
        trail: Int?,
    ): Pair<SideStats, SideStats> {
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        class Job(val lineup: Pair<List<String>, List<String>>, val coin: Team, val seed: Long, val aRed: Boolean)
        val jobs = (first until first + lineups).flatMap { i ->
            val coin = if (i % 2 == 0) Team.RED else Team.BLUE
            listOf(true, false).map { Job(all[i], coin, 50_000L + i, it) }
        }
        val pool = Executors.newFixedThreadPool(threads)
        val results = try {
            pool.invokeAll(
                jobs.mapIndexed { k, j ->
                    Callable {
                        val setup = AiHarness.setup(
                            j.lineup.first,
                            j.lineup.second,
                            AiLevel.NORMAL,
                            AiLevel.NORMAL,
                            j.coin,
                            format.length,
                            format.mapId,
                        )
                        val red = player(if (j.aRed) aName else bName, j.seed * 2)
                        val blue = player(if (j.aRed) bName else aName, j.seed * 2 + 1)
                        val audit = GameAudit(
                            TestData.engine.newGame(setup) as EngineSession,
                            if (j.aRed) Team.RED else Team.BLUE,
                            trail == k,
                        )
                        audit.play(mapOf(Team.RED to red, Team.BLUE to blue))
                        audit
                    }
                },
            ).map { it.get() }
        } finally {
            pool.shutdown()
        }
        val a = SideStats(aName)
        val b = SideStats(bName)
        for ((k, g) in results.withIndex()) {
            g.trail?.let { t -> t.forEach(::println) }
            if (lines) {
                val st = g.s.state
                val j = jobs[k]
                val result = st.winner?.let { if (it == g.aTeam) "$aName won" else "$bName won" } ?: "unfinished"
                println(
                    "AUDIT_GAME $k ${j.lineup.first.joinToString("+")} vs ${j.lineup.second.joinToString("+")}: " +
                        "$aName ${if (j.aRed) "Red" else "Blue"}, $result by ${st.winReason} in round ${st.round}; " +
                        "pushes ${g.pushLog.joinToString(" ").ifEmpty { "none" }} (a = $aName)",
                )
            }
            g.into(a, g.aTeam)
            g.into(b, g.aTeam.opponent)
        }
        return a to b
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        if (spec.count { it == '/' } == 2) {
            val (base, cats, delegate) = spec.split("/")
            return HybridAi(player(base, seed), player(delegate, seed), cats.split(".").toSet())
        }
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        val threads = tune["thr"]?.toInt() ?: 1
        val runner = if (threads > 1) ThreadRunner(threads) else SampleRunner.SEQUENTIAL
        return when (spec[0]) {
            'h', 'H' -> HeuristicAi(n, seed, tune = tune - "thr")
            'a', 'A' -> AiPlayers.forStrength(n, seed, runner)
            's', 'S' -> AiPlayers.searchDeterministic(n, seed)
            'x', 'X' -> HybridPlayers.hybrid(seed, tune - "thr", runner)
            'y', 'Y' -> HybridPlayers.searchWithBook(seed, tune - "thr", runner)
            else -> error("AI_AUDIT wants hN, aN, sN, xN or yN, not $spec")
        }
    }

    // ---- the counters -------------------------------------------------------------------------------------------

    /** Named counters, in first-use order (so the summary keys keep their order). */
    class Counters {
        val m = LinkedHashMap<String, Double>()

        fun add(key: String, v: Double = 1.0) {
            m[key] = (m[key] ?: 0.0) + v
        }

        fun v(key: String): Double = m[key] ?: 0.0

        fun addAll(o: Counters) = o.m.forEach { (k, v) -> add(k, v) }
    }

    /** One side's totals over its games. */
    class SideStats(val label: String) {
        var games = 0
        var wins = 0
        var rounds = 0
        val c = Counters()

        /** Role mix → (games, wins). */
        val comps = sortedMapOf<String, IntArray>()

        /** Hero → colour upgraded → times; hero → item stat → times. */
        val upgrades = sortedMapOf<String, java.util.TreeMap<String, Int>>()
        val items = sortedMapOf<String, java.util.TreeMap<String, Int>>()

        private fun mean(k: String): Double = c.v(k) / games.coerceAtLeast(1)

        private fun ratio(a: String, b: String): String =
            if (c.v(b) == 0.0) "n/a" else "%.1f%%".format(100 * c.v(a) / c.v(b))

        /** The level means and the level-5 numbers as keys of their own (derived from the sums). */
        private fun derived(): LinkedHashMap<String, Double> {
            val out = LinkedHashMap<String, Double>()
            for (r in 1..MAX_ROUNDS) {
                val n = c.v("lvl.r$r.n")
                if (n > 0) out["lvl.r$r"] = c.v("lvl.r$r.sum") / n
            }
            val heroGames = c.v("heroGames")
            out["lvl5.share"] = if (heroGames > 0) c.v("lvl5.heroes") / heroGames else 0.0
            out["lvl5.round"] = if (c.v("lvl5.heroes") > 0) c.v("lvl5.roundSum") / c.v("lvl5.heroes") else 0.0
            return out
        }

        private fun counterKeys(): List<String> = KEYS + c.m.keys.filter {
            it !in KEYS && !it.startsWith("lvl") && it != "heroGames"
        }.sorted()

        fun report(): String = buildString {
            appendLine("AUDIT --- $label: $wins wins of $games, ${"%.1f".format(rounds.toDouble() / games)} rounds/game")
            for (k in counterKeys()) {
                appendLine("AUDIT   %-26s total %7.0f   per game %7.2f".format(k, c.v(k), mean(k)))
            }
            for ((k, v) in derived()) appendLine("AUDIT   %-26s %7.2f".format(k, v))
            appendLine(
                "AUDIT   rates: poke ${ratio("atk.hero.poke", "atk.hero")} of hero attacks " +
                    "(${ratio("atk.hero.poke.minion", "atk.hero.poke")} with a minion in reach); minion left untaken " +
                    "${ratio("act.minion.untaken", "act.minion")}; strip-finish ${ratio("kills.stripFinish", "kills")} " +
                    "of kills; double ${ratio("push.lost.double", "pushesLost")} (next wave lost too " +
                    "${ratio("push.lost.again", "pushesLost")}) of pushes suffered; " +
                    "FT blocked ${ratio("ft.blocked.far", "act.far")} of far actions",
            )
            val minionCoins = c.v("coins.minion")
            val allCoins = listOf("minion", "kill", "assist", "pity", "other").sumOf { c.v("coins.$it") }
            appendLine("AUDIT   coins from minions ${"%.1f".format(100 * minionCoins / allCoins.coerceAtLeast(1.0))}%")
            appendLine(
                "AUDIT   compositions: " + comps.entries.joinToString("; ") { (k, v) -> "$k ${v[1]}/${v[0]}" },
            )
            for (hero in (upgrades.keys + items.keys).toSortedSet()) {
                appendLine(
                    "AUDIT   levels $hero: upgraded ${upgrades[hero].orEmpty().entries.joinToString(" ") { "${it.key} ${it.value}" }}" +
                        "; items ${items[hero].orEmpty().entries.joinToString(" ") { "${it.key} ${it.value}" }}",
                )
            }
            val itemTotals = java.util.TreeMap<String, Int>()
            items.values.forEach { m -> m.forEach { (k, v) -> itemTotals[k] = (itemTotals[k] ?: 0) + v } }
            append("AUDIT   items in all: ${itemTotals.entries.joinToString(" ") { "${it.key} ${it.value}" }}")
        }

        /** `label games=N win=… key=per-game-mean …`: the same keys in the same order every run. */
        fun summary(): String = buildString {
            append("$label games=$games win=${"%.3f".format(wins.toDouble() / games)}")
            append(" rounds=${"%.2f".format(rounds.toDouble() / games)}")
            for (k in counterKeys()) append(" $k=${"%.3f".format(mean(k))}")
            for ((k, v) in derived()) append(" $k=${"%.3f".format(v)}")
        }

        companion object {
            /** Every run prints these, zero or not (the summary lines stay aligned). */
            val KEYS = listOf(
                "minions", "minions.lost", "coins.minion", "coins.kill", "coins.assist", "coins.pity", "coins.other",
                "push.won.mid", "push.won.eor", "push.lost.mid", "push.lost.eor", "pushesLost", "push.lost.double",
                "push.lost.again",
                "push.lost.in", "push.lost.walk", "push.lost.far", "push.lost.dead", "push.lost.stranded",
                "push.won.in", "push.won.walk", "push.won.far", "push.won.dead",
                "act.far", "ft.offered.far", "ft.blocked.far", "ft.taken",
                "atk.minion", "atk.hero", "atk.hero.kill", "atk.hero.followed", "atk.hero.poke", "atk.hero.poke.minion",
                "act.minion", "act.minion.took", "act.minion.untaken", "act.minion.hero", "act.minion.hold",
                "act.minion.move", "act.hold",
                "kills", "kills.stripFinish", "kills.oneShot", "team.sameTarget", "assists", "team.minionCollision",
                "deaths", "deaths.emptyHand", "deaths.emptyHand.covered",
                "win.THRONE", "win.LAST_PUSH", "win.LIFE_COUNTERS", "loss.THRONE", "loss.LAST_PUSH",
                "loss.LIFE_COUNTERS", "end.mid", "end.eor",
            )
            const val MAX_ROUNDS = 12
        }
    }

    // ---- one game -----------------------------------------------------------------------------------------------

    /**
     * Plays one game and reads it: [before] sees every decision with the live state (reach, Fast Travel, the
     * pending push reads), [event] every event of the log in order. Per-team counters; [into] adds a team's
     * to a side.
     */
    class GameAudit(val s: EngineSession, val aTeam: Team, trailOn: Boolean) {
        val trail: MutableList<String>? = if (trailOn) ArrayList() else null
        private val data = s.engineData
        private val teamOf = s.state.heroes.associate { it.id to it.team }
        private val heroes = teamOf.keys
        private val byTeam = mapOf(Team.RED to Counters(), Team.BLUE to Counters())
        private val profile = HeuristicProfile.of(9)

        private var round = 1
        private var gturn = 0
        private var turn = 0
        private var endOfRound = false
        private val levels = s.state.heroes.associate { it.id to it.level }.toMutableMap()
        private val level5 = HashMap<String, Int>()
        private val itemsBy = HashMap<String, MutableList<String>>()
        private val upgradesBy = HashMap<String, MutableList<String>>()

        private class Attack(val idx: Int, val gturn: Int, val round: Int, val attacker: String, val target: String) {
            var kill = false
            var minionInReach = false
        }

        private class Defeat(val idx: Int, val gturn: Int, val round: Int, val victim: String, val killer: String?) {
            var emptyHand = false
            var covered = false
        }

        private class Push(val round: Int, val winner: Team, val mid: Boolean)

        private class Action(val hero: String, val gturn: Int, val minionInReach: Boolean, val choice: ActionType?)

        private val attacks = ArrayList<Attack>()
        private val defeats = ArrayList<Defeat>()
        private val pushes = ArrayList<Push>()
        private val actions = ArrayList<Action>()

        /** Pushes waiting to be read at the next decision: lane's new zone, winner. */
        private val pendingPushes = ArrayList<Pair<String, Team>>()

        /** Lane → the winner of its last push; the game's pushes as `a3e` (side a won in round 3, end of round). */
        private val lastPushWinner = HashMap<String, Team>()
        val pushLog = ArrayList<String>()

        /** Minion → its team (read before every answer; a defeated minion is gone from the state). */
        private val minionTeam = HashMap<String, Team>()

        /** Hero → whether its current action began with a minion in reach. */
        private val actionReach = HashMap<String, Boolean>()

        /** Minion → its last known space (read before every answer). */
        private val minionAt = HashMap<String, Hex>()

        /** This turn: minions defeated (by hero → spaces). */
        private val minionsTakenThisTurn = ArrayList<Pair<String, Hex?>>()

        /** (hero, global turn) → minions it defeated. */
        private val minionKills = HashMap<Pair<String, Int>, Int>()

        /** Global turn → hero positions at its first decision. */
        private val turnStart = HashMap<Int, Map<String, Hex?>>()

        private var pendingEmpty: String? = null
        private var lastAttack: Attack? = null
        private var idx = 0
        private var lastPushMid = false

        private fun c(team: Team): Counters = byTeam.getValue(team)

        private fun note(text: String) {
            trail?.add("AUDIT r$round.t$turn $text")
        }

        fun play(players: Map<Team, AiPlayer>, maxDecisions: Int = 8000) {
            s.events.forEach(::event)
            var n = 0
            while (n < maxDecisions) {
                val d = s.pending ?: break
                before(d)
                val choice = players.getValue(d.team).choose(s, d)
                after(d, choice)
                val r = s.answer(d.id, choice)
                check(r is AnswerResult.Accepted) { "Rejected $choice for $d: $r" }
                r.events.forEach(::event)
                n++
            }
            finish()
        }

        private fun ctx(d: Decision) = HContext(s, d, d.team, profile)

        private fun before(d: Decision) {
            val st = s.state
            for (m in st.minions) {
                m.at?.let { minionAt[m.id] = it }
                minionTeam[m.id] = m.team
            }
            turnStart.getOrPut(gturn) { st.heroes.associate { it.id to it.at } }
            if (pendingPushes.isNotEmpty()) readPushes(d)
            if (d.kind == DecisionKind.CHOOSE_ACTION) readAction(d)
        }

        private fun readPushes(d: Decision) {
            val g = ctx(d).game
            for ((zone, winner) in pendingPushes) {
                val lane = g.geo.lanes.values.firstOrNull { zone in it.zones } ?: continue
                val zi = lane.zones.indexOf(zone)
                for (h in s.state.heroes) {
                    val side = if (h.team == winner) "won" else "lost"
                    val at = h.at
                    val where = when {
                        at == null -> "dead"
                        g.zoneOf(at) == zone -> "in"
                        canWalkTo(g, h.id, zone) -> "walk"
                        else -> "far"
                    }
                    c(h.team).add("push.$side.$where")
                    var stranded = false
                    if (side == "lost" && where == "far" && at != null) {
                        val hi = zoneIndex(g, lane.zones, at)
                        // Zones run from Red's Throne to Blue's (a Red push moves the index up).
                        stranded = if (h.team == Team.RED) hi > zi else hi < zi
                        if (stranded) c(h.team).add("push.lost.stranded")
                    }
                    note("after the push: ${h.team} ($side) ${h.id} $where${if (stranded) " stranded" else ""}")
                }
            }
            pendingPushes.clear()
        }

        private fun zoneIndex(g: dev.quinton.goa.core.game.engine.Game, zones: List<String>, at: Hex): Int {
            val own = g.zoneOf(at)
            val i = zones.indexOf(own)
            if (i >= 0) return i
            return zones.indices.minBy { g.geo.distanceToZone(at, zones[it]) ?: Int.MAX_VALUE }
        }

        /** One Movement action of the best Movement card in hand reaches [zone] (the engine's paths). */
        private fun canWalkTo(g: dev.quinton.goa.core.game.engine.Game, heroId: String, zone: String): Boolean {
            val h = s.state.hero(heroId) ?: return false
            val best = h.hand.maxOfOrNull { g.cardValue(heroId, it.id, Stat.MOVEMENT) ?: 0 } ?: 0
            return walk(g, heroId, best, zone)
        }

        private fun walk(g: dev.quinton.goa.core.game.engine.Game, heroId: String, n: Int, zone: String): Boolean {
            if (n <= 0) return false
            return g.reach(MoveSpec(heroId, 0, n, movementAction = true)).keys.any { g.zoneOf(it) == zone }
        }

        private fun readAction(d: Decision) {
            val h = s.state.hero(d.seat) ?: return
            val at = h.at ?: return
            val cardId = d.sourceCardId ?: h.unresolved.firstOrNull()?.id ?: return
            val ctx = ctx(d)
            val card = ctx.card(cardId)
            val cs = c(h.team)
            var inReach = false
            if (card.attackCard) {
                val reach = ctx.attackReach(h.id, card)
                inReach = ctx.minionTargets(h.team).any { m -> HexGrid.distance(at, m.at!!) <= reach }
                if (!inReach) {
                    val taken = minionsTakenThisTurn.any { (by, hex) ->
                        by != h.id && teamOf[by] == h.team && hex != null && HexGrid.distance(at, hex) <= reach
                    }
                    if (taken) {
                        cs.add("team.minionCollision")
                        note("minion collision: ${h.id} had a minion taken by a teammate")
                    }
                }
            }
            actionReach[h.id] = inReach
            val options = d.options.mapNotNull { (it as? Choice.Action)?.action }.toSet()
            if (ActionType.MOVEMENT in options) {
                val zones = s.state.battleZones.values.toSet()
                val g = ctx.game
                val inZone = g.zoneOf(at) in zones
                val n = g.cardValue(h.id, cardId, Stat.MOVEMENT) ?: 0
                if (!inZone && zones.none { walk(g, h.id, n, it) }) {
                    cs.add("act.far")
                    if (ActionType.FAST_TRAVEL in options) cs.add("ft.offered.far") else cs.add("ft.blocked.far")
                    note("far: ${h.id} at ${g.zoneOf(at)}, FT ${if (ActionType.FAST_TRAVEL in options) "offered" else "blocked"}")
                }
            }
        }

        private fun after(d: Decision, choice: Choice) {
            when (d.kind) {
                DecisionKind.CHOOSE_ACTION -> {
                    val a = (choice as? Choice.Action)?.action
                    val hero = d.seat
                    val team = teamOf[hero] ?: return
                    val reach = actionReach[hero] ?: false
                    actions += Action(hero, gturn, reach, a)
                    if (a == ActionType.FAST_TRAVEL) c(team).add("ft.taken")
                    if (a == ActionType.HOLD) c(team).add("act.hold")
                    if (reach) note("action with a minion in reach: $hero chooses $a")
                }

                DecisionKind.UPGRADE_CARD -> {
                    val id = (choice as? Choice.Card)?.cardId ?: return
                    val card = data.card(id)
                    upgradesBy.getOrPut(d.seat) { ArrayList() } += card.color.name.lowercase()
                }

                else -> Unit
            }
        }

        private fun heroOf(unitId: String): String? = when {
            unitId in heroes -> unitId
            else -> s.state.piece(unitId)?.heroBody
        }

        private fun event(e: GameEvent) {
            trail?.add("r$round.t$turn ${e.text}")
            when (e) {
                is GameEvent.RoundStarted -> {
                    if (e.round > round) {
                        // The previous round ended: the levels after its level-ups.
                        for ((id, lvl) in levels) {
                            c(teamOf.getValue(id)).add("lvl.r$round.sum", lvl.toDouble())
                            c(teamOf.getValue(id)).add("lvl.r$round.n")
                        }
                    }
                    round = e.round
                    endOfRound = false
                }

                is GameEvent.TurnStarted -> {
                    gturn++
                    turn = e.turn
                    minionsTakenThisTurn.clear()
                }

                is GameEvent.CoinsGained -> {
                    val t = teamOf[e.heroId] ?: return
                    val why = when {
                        e.text.contains("(defeated a ") -> "minion"
                        e.text.contains("(assist)") -> "assist"
                        e.text.contains("(pity coin)") -> "pity"
                        e.text.contains("(defeated ") -> "kill"
                        else -> "other"
                    }
                    c(t).add("coins.$why", e.amount.toDouble())
                    if (why == "assist") c(t).add("assists")
                }

                is GameEvent.Attacked -> {
                    val attacker = heroOf(e.attackerId) ?: return
                    val team = teamOf[attacker] ?: return
                    val target = heroOf(e.targetId)
                    if (target == null) {
                        c(team).add("atk.minion")
                        lastAttack = null
                        return
                    }
                    val a = Attack(idx, gturn, round, attacker, target)
                    a.minionInReach = actionReach[attacker] ?: false
                    attacks += a
                    lastAttack = a
                }

                is GameEvent.Note -> {
                    if (e.text.startsWith("End of round")) endOfRound = true
                    if (e.text.endsWith(" has no card to defend with.")) {
                        pendingEmpty = heroes.firstOrNull { e.text.startsWith(data.heroName(it) + " ") }
                    }
                }

                is GameEvent.Defeated -> {
                    val victim = e.unitId
                    if (victim in heroes) {
                        val d = Defeat(idx, gturn, round, victim, e.byHeroId)
                        d.emptyHand = pendingEmpty == victim
                        val la = lastAttack
                        if (la != null && la.target == victim && la.attacker == e.byHeroId) la.kill = true
                        if (d.emptyHand) {
                            val at = turnStart[gturn]?.get(victim)
                            d.covered = at != null && turnStart[gturn].orEmpty().any { (id, hex) ->
                                id != victim && teamOf[id] == teamOf[victim] && hex != null &&
                                    HexGrid.distance(at, hex) <= COVER
                            }
                        }
                        defeats += d
                        pendingEmpty = null
                        lastAttack = null
                    } else {
                        val by = e.byHeroId?.let(::heroOf) ?: return
                        val team = teamOf[by] ?: return
                        c(team).add("minions")
                        minionTeam[victim]?.let { if (it != team) c(it).add("minions.lost") }
                        minionsTakenThisTurn += by to minionAt[victim]
                        val key = by to gturn
                        minionKills[key] = (minionKills[key] ?: 0) + 1
                    }
                }

                is GameEvent.LanePushed -> {
                    val loser = e.winner.opponent
                    val mid = !endOfRound
                    val doubled = pushes.any { it.winner == e.winner && it.round >= round - 1 }
                    val again = lastPushWinner[e.lane] == e.winner
                    lastPushWinner[e.lane] = e.winner
                    if (again) c(loser).add("push.lost.again")
                    pushLog += "${if (e.winner == aTeam) "a" else "b"}$round${if (mid) "m" else "e"}"
                    pushes += Push(round, e.winner, mid)
                    lastPushMid = mid
                    val when_ = if (mid) "mid" else "eor"
                    c(e.winner).add("push.won.$when_")
                    c(loser).add("push.lost.$when_")
                    c(loser).add("pushesLost")
                    if (doubled) c(loser).add("push.lost.double")
                    pendingPushes += e.newBattleZone to e.winner
                    note("push won by ${e.winner} ($when_)${if (doubled) ", a double push" else ""}")
                }

                is GameEvent.LevelledUp -> {
                    levels[e.heroId] = e.level
                    if (e.level >= 5 && e.heroId !in level5) level5[e.heroId] = round
                }

                is GameEvent.CardsChanged -> {
                    val m = ITEM.find(e.text) ?: return
                    itemsBy.getOrPut(e.heroId) { ArrayList() } += m.groupValues[1]
                }

                is GameEvent.GameOver -> {
                    c(e.winner).add("win.${e.reason.name}")
                    c(e.winner.opponent).add("loss.${e.reason.name}")
                    if (e.reason != dev.quinton.goa.core.game.WinReason.LIFE_COUNTERS) {
                        c(e.winner).add(if (lastPushMid) "end.mid" else "end.eor")
                    }
                }

                else -> Unit
            }
            idx++
        }

        private fun finish() {
            for (a in attacks) {
                val team = teamOf.getValue(a.attacker)
                val cs = c(team)
                cs.add("atk.hero")
                val followed = !a.kill && defeats.any {
                    it.victim == a.target && it.killer != null && teamOf[it.killer] == team &&
                        it.idx > a.idx && it.gturn <= a.gturn + 1
                }
                when {
                    a.kill -> cs.add("atk.hero.kill")
                    followed -> cs.add("atk.hero.followed")
                    else -> {
                        cs.add("atk.hero.poke")
                        if (a.minionInReach) cs.add("atk.hero.poke.minion")
                    }
                }
            }
            for (d in defeats) {
                c(teamOf.getValue(d.victim)).add("deaths")
                if (d.emptyHand) c(teamOf.getValue(d.victim)).add("deaths.emptyHand")
                if (d.covered) c(teamOf.getValue(d.victim)).add("deaths.emptyHand.covered")
                val killer = d.killer ?: continue
                val team = teamOf[killer] ?: continue
                if (team == teamOf[d.victim]) continue
                val cs = c(team)
                cs.add("kills")
                val earlier = attacks.filter {
                    it.target == d.victim && teamOf[it.attacker] == team && it.round == d.round && it.idx < d.idx &&
                        !it.kill
                }
                if (earlier.any { it.attacker != killer }) cs.add("kills.stripFinish")
                if (earlier.isEmpty()) cs.add("kills.oneShot")
            }
            for ((_, group) in attacks.groupBy { Triple(it.gturn, teamOf[it.attacker], it.target) }) {
                if (group.map { it.attacker }.distinct().size >= 2) c(teamOf.getValue(group[0].attacker)).add("team.sameTarget")
            }
            for (a in actions) {
                if (!a.minionInReach) continue
                val cs = c(teamOf.getValue(a.hero))
                cs.add("act.minion")
                val took = (minionKills[a.hero to a.gturn] ?: 0) > 0
                cs.add(if (took) "act.minion.took" else "act.minion.untaken")
                when (a.choice) {
                    ActionType.HOLD -> cs.add("act.minion.hold")
                    ActionType.MOVEMENT, ActionType.FAST_TRAVEL -> cs.add("act.minion.move")
                    else -> Unit
                }
                if (attacks.any { it.attacker == a.hero && it.gturn == a.gturn }) cs.add("act.minion.hero")
            }
            for ((id, team) in teamOf) {
                c(team).add("heroGames")
                level5[id]?.let {
                    c(team).add("lvl5.heroes")
                    c(team).add("lvl5.roundSum", it.toDouble())
                }
            }
        }

        /** Adds [team]'s counters of this game to [side]. */
        fun into(side: SideStats, team: Team) {
            side.games++
            side.rounds += s.state.round
            if (s.state.winner == team) side.wins++
            side.c.addAll(c(team))
            val ids = teamOf.filterValues { it == team }.keys
            val comp = ids.map { HeroPlaybooks.forHero(it).style.role.name.lowercase() }.sorted().joinToString("+")
            val row = side.comps.getOrPut(comp) { IntArray(2) }
            row[0]++
            if (s.state.winner == team) row[1]++
            for (id in ids) {
                for (col in upgradesBy[id].orEmpty()) {
                    val m = side.upgrades.getOrPut(id) { java.util.TreeMap() }
                    m[col] = (m[col] ?: 0) + 1
                }
                for (st in itemsBy[id].orEmpty()) {
                    val m = side.items.getOrPut(id) { java.util.TreeMap() }
                    m[st] = (m[st] ?: 0) + 1
                }
            }
        }

        companion object {
            /** "Spaces of a teammate" for [Defeat.covered]. */
            const val COVER = 3
            val ITEM = Regex("gains an item: \\+1 ([A-Za-z]+)\\.")
        }
    }
}
