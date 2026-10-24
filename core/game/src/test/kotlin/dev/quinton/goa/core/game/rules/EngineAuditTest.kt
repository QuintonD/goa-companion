package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.script
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.defeatHero
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.retrieve
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.game.heroes.ArienHero
import dev.quinton.goa.core.game.heroes.BroganHero
import dev.quinton.goa.core.game.heroes.WaspHero
import dev.quinton.goa.core.game.heroes.XargathaHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Regressions of the engine-core rules audit (pp.5–20): simultaneous timed effects (p.17), the
 * double-lane Zone count (p.20, BGG 3499394), the push removing the Lane's displaced minions (p.8,
 * p.18), minions going back after an action that ended in its hero's defeat (p.18), and hidden
 * cards in the log (p.4). Test-only scripts sit on Dodger's card ids in a registry of their own.
 */
class EngineAuditTest {
    private val registry = Registry().also { r ->
        listOf(ArienHero, BroganHero, WaspHero, XargathaHero).forEach(r::module)
        // "End of turn: gain 1 coin."
        r.behavior(
            COIN,
            object : Behavior {
                override fun applyTimed(g: Game, src: Src) = g.gainCoins(src.owner!!, 1, "end of turn")
            },
        )
        // "End of turn: defeat an enemy hero on the board."
        r.behavior(
            DEFEAT,
            object : Behavior {
                override fun applyTimed(g: Game, src: Src) {
                    val owner = src.owner!!
                    val victim = g.state.heroes.first { it.at != null && g.isEnemy(owner, it.id) }
                    g.defeatHero(victim.id, owner)
                }
            },
        )
        // "Move a unit adjacent to you 1 space, then you are defeated." (an action that ends in a defeat)
        r.card(
            "dodger-gold",
            script {
                val m = g.state.minions.first { g.distance(pos, it.at!!) == 1 }.id
                moveOther(m, 1)
                g.defeatHero(heroId, null)
            },
        )
        // "Retrieve your facedown resolved card." / "Swap your facedown resolved card with a card in hand."
        r.card("dodger-silver", script { g.retrieve(heroId, "dodger-red-1") })
        r.card("dodger-green-1", script { g.swapCards(heroId, "dodger-red-1", "dodger-blue-1") })
    }

    private val engine =
        GameEngine(TestData.catalog, TestData.maps, TestData.matchRules, registry, allowIncomplete = true)

    private fun at(map: String = TestData.FOREST, block: PositionBuilder.() -> Unit) =
        Driver(position(engine, map, block))

    private val geo = engine.data.geometry(TestData.FOREST)

    @Test
    fun `p17 - simultaneous End of turn effects of one team are ordered by that team, not by seat`() {
        val d = at {
            clearMinions()
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            hero("xargatha", Team.RED, geo.heroSpawns(Team.RED)[1])
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            effect("wasp", "wasp-red-1", Timing.END_OF_TURN, ActionKind.ATTACK, key = COIN)
            effect("xargatha", "xargatha-red-1", Timing.END_OF_TURN, ActionKind.ATTACK, key = COIN)
            phase = PositionPhase.Resolving()
        }
        // p.7 "they have to decide which one of them will act first": the team (its first seat) is asked.
        val q = d.expect(DecisionKind.CHOOSE_OPTION, "wasp")
        assertThat(q.options.map { (it as Choice.Option).label })
            .containsExactly("Wasp's Shock", "Xargatha's Threatening Slash").inOrder()
        d.option(1)
        val coins = d.s.events.filterIsInstance<GameEvent.CoinsGained>().map { it.heroId }
        assertThat(coins).containsExactly("xargatha", "wasp").inOrder()
    }

    @Test
    fun `p13 p17 - an End of turn effect of a hero defeated by an earlier one no longer applies`() {
        val d = at {
            clearMinions()
            tieBreaker = Team.RED
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            effect("wasp", "wasp-red-1", Timing.END_OF_TURN, ActionKind.ATTACK, key = DEFEAT)
            effect("arien", "arien-red-1", Timing.END_OF_TURN, ActionKind.ATTACK, key = COIN)
            phase = PositionPhase.Resolving()
        }
        d.expect(DecisionKind.PLAY_CARD) // the next turn: nothing else was asked
        // The coin's team first: Wasp defeats Arien, whose effects are cancelled (p.13) before his applies.
        assertThat(d.hero("arien").at).isNull()
        assertThat(d.hero("arien").coins).isEqualTo(0)
        assertThat(d.texts()).doesNotContain("Arien gains 1 coin (end of turn).")
    }

    @Test
    fun `p20 BGG 3499394 - the double-lane Zone count is made after both end-of-round pushes, a tie plays on`() {
        val rgeo = engine.data.geometry(TestData.RIVER)
        fun free(zone: String) = rgeo.zoneSpaces.getValue(zone).filter { rgeo.spaces.getValue(it).spawn == null }
        val d = at(TestData.RIVER) {
            clearMinions()
            hero("wasp", Team.RED, rgeo.heroSpawns(Team.RED)[0])
            hero("arien", Team.BLUE, rgeo.heroSpawns(Team.BLUE)[0])
            wavesLeft("lane-1", 1)
            // Lane 1: Red wins the battle (its last Wave counter); Lane 2: Blue wins.
            minion(Team.RED, MinionType.MELEE, free("lane-1-2")[0], lane = "lane-1")
            minion(Team.BLUE, MinionType.MELEE, free("lane-2-3")[0], lane = "lane-2")
            phase = PositionPhase.EndOfRound
        }
        // Counting after Lane 1 alone would give Red 4 Zones to 2; after both pushes it is 3 to 3: no winner.
        assertThat(d.s.state.battleZones).containsExactly("lane-1", "lane-1-3", "lane-2", "lane-2-2")
        assertThat(d.s.state.winner).isNull()
        d.expect(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `p8 p18 - a push removes the Lane's minion displaced out of the Battle Zone during the action`() {
        val target = Hex(3, -9)
        val friend = Hex(4, -9)
        val out = Hex(4, -10)
        val d = at {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -8)) { played("arien-gold") }
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0])
            minion(Team.RED, MinionType.MELEE, target)
            minion(Team.BLUE, MinionType.MELEE, friend)
            phase = PositionPhase.Resolving(next = "arien")
        }
        val displaced = d.s.state.minions.first { it.team == Team.BLUE }.id
        assertThat(engine.data.geometry(TestData.FOREST).zoneOf(out)).isNotEqualTo("lane-1-2")
        // Noble Blade: "Before the attack: You may move another unit that is adjacent to the target 1 space."
        d.action("arien", ActionType.PRIMARY)
        d.yes()
        d.space(out)
        // The last Red minion is defeated: Blue pushes into lane-1-1 and the old Zone's minions are removed,
        // the displaced one included (it does not walk into the new Battle Zone as an extra minion).
        assertThat(d.s.state.battleZones).containsExactly("lane-1", "lane-1-1")
        assertThat(d.s.state.minions.map { it.id }).doesNotContain(displaced)
        val blueSpawns = geo.minionSpawns("lane-1-1").count { it.spawn?.team == Team.BLUE }
        assertThat(d.s.state.minions.count { it.team == Team.BLUE }).isEqualTo(blueSpawns)
    }

    @Test
    fun `p18 - minions go back to the Battle Zone after an action that ended with its hero's defeat`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(3, -8)) { played("dodger-gold") }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0]) { played("arien-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -9))
            minion(Team.RED, MinionType.MELEE, Hex(0, -5))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.space(Hex(4, -10)) // out of the Battle Zone; then Dodger is defeated
        assertThat(d.hero("dodger").at).isNull()
        // Back before Arien acts (its team picks among the shortest paths), not left outside until the
        // next action ends.
        if (d.d.kind == DecisionKind.MINION_RETURN) d.answer(d.d.options.first())
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
        val m = d.s.state.minions.first { it.team == Team.BLUE }
        assertThat(engine.data.geometry(TestData.FOREST).zoneOf(m.at!!)).isEqualTo("lane-1-2")
    }

    @Test
    fun `p4 p10 - retrieving or swapping facedown cards never names them in the log`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                hand("dodger-blue-1", "dodger-green-1")
                played("dodger-silver")
                resolved(1, "dodger-red-1", faceDown = true)
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        assertThat(d.texts()).contains("Dodger retrieves a facedown card.")
        assertThat(d.texts().none { "Littlefinger" in it }).isTrue()

        val s = at {
            clearMinions()
            hero("dodger", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                hand("dodger-blue-1")
                played("dodger-green-1")
                resolved(1, "dodger-red-1", faceDown = true)
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.Resolving(next = "dodger")
        }
        s.action("dodger", ActionType.PRIMARY)
        assertThat(s.texts()).contains("Dodger swaps two hidden cards.")
        assertThat(s.texts().none { "Littlefinger" in it || "Shield of Decay" in it }).isTrue()
    }

    private companion object {
        const val COIN = "audit-eot-coin"
        const val DEFEAT = "audit-eot-defeat"
    }
}
