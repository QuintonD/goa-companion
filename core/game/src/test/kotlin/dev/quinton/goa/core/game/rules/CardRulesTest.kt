package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.StatQuery
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.inRadiusOf
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.moveSelf
import dev.quinton.goa.core.game.effects.pushAway
import dev.quinton.goa.core.game.effects.script
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.discard
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.statDelta
import dev.quinton.goa.core.game.engine.swapCards
import dev.quinton.goa.core.game.engine.turnFaceDown
import dev.quinton.goa.core.game.heroes.Heroes
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The card rules of p.10 (states, mandatory steps, modal actions, repeat) and p.17–19 (Active effects,
 * immunity, tokens), checked with test-only scripts on Dodger's card ids (Dodger has no module yet),
 * so the engine primitives are tested on their own.
 */
class CardRulesTest {
    private val inc = "inc" // a "+1 Attack this effect" key

    private val registry = Registry().also { r ->
        Heroes.all.forEach(r::module)
        // "You may move 1 space. Push an enemy unit adjacent to you 1 space. Gain 1 coin."
        r.card(
            "dodger-green-1",
            script {
                moveSelf(1, optional = true)
                val t = unitsWithin(1).firstOrNull { isEnemy(it) && canTarget(it) } ?: fizzle("nothing to push")
                pushAway(t, 1)
                g.gainCoins(heroId, 1)
            },
        )
        // "Target a unit adjacent to you. Before the attack: push the target 2 spaces. May repeat once."
        r.card(
            "dodger-gold",
            script {
                val t = adjacentAttackTarget() ?: return@script
                pushAway(t, 2)
                attack(t, ranged = false) // a non-ranged attack: the target must still be adjacent (p.13)
                repeat()
            },
        )
        // "This round: you have +1 Attack." (an effect checked over several turns)
        r.card(
            "dodger-silver",
            script(
                behavior = object : Behavior {
                    override fun statDelta(g: dev.quinton.goa.core.game.engine.Game, src: Src, q: StatQuery): Int =
                        if (q.unitId == src.owner && q.stat == Stat.ATTACK) 1 else 0
                },
            ) { activeEffect(Timing.THIS_ROUND) },
        )
        // "Next turn: enemy heroes within 3 spaces have -2 Initiative."
        r.behavior(
            inc,
            object : Behavior {
                override fun statDelta(g: dev.quinton.goa.core.game.engine.Game, src: Src, q: StatQuery): Int =
                    if (q.stat == Stat.INITIATIVE && g.isHero(q.unitId) && g.isEnemy(src.owner!!, q.unitId) &&
                        g.inRadiusOf(src, q.unitId, 3)
                    ) {
                        -2
                    } else {
                        0
                    }
            },
        )
        r.card("dodger-red-1", script { activeEffect(Timing.NEXT_TURN, key = inc) })
        r.token(TokenKind("token_test", "Test token", supply = 2))
    }

    private val engine =
        GameEngine(TestData.catalog, TestData.maps, TestData.matchRules, registry, allowIncomplete = true)

    private fun at(block: PositionBuilder.() -> Unit) = Driver(position(engine, TestData.FOREST, block))

    @Test
    fun `p10 mandatory steps - a mandatory step that can't be done stops the action, later steps are skipped`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-green-1") }
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.decline() // optional move skipped
        assertThat(d.texts()).contains("Dodger's Dark Ritual stops: nothing to push.")
        assertThat(d.hero("dodger").coins).isEqualTo(0) // the coin step is never reached
        assertThat(d.zoneOf("dodger", "dodger-green-1")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `p10 optional steps - the action goes on after a step that could be skipped`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-green-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.decline()
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
        assertThat(d.hero("dodger").coins).isEqualTo(1)
    }

    @Test
    fun `FAQ - a Primary action whose first instruction can't be done is not offered`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `p17 before the attack - applies even if the attack then fails to connect`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -2)) // pushed 2 before the attack
        assertThat(d.texts()).contains("Dodger's attack on Arien fails to connect.")
        assertThat(d.s.events.filterIsInstance<dev.quinton.goa.core.game.GameEvent.Attacked>()).isEmpty()
    }

    @Test
    fun `p10 repeat - a repeated instance cannot repeat again`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-gold") }
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-green-1") }
            minion(Team.BLUE, dev.quinton.goa.core.model.board.MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, dev.quinton.goa.core.model.board.MinionType.MELEE, Hex(-1, -1))
            minion(Team.RED, dev.quinton.goa.core.model.board.MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.unit(d.units().first { it != "arien" }) // a minion: pushed 2, then attack fails or hits
        d.expect(DecisionKind.CONFIRM)
        d.yes() // repeat once
        if (d.d.kind == DecisionKind.CHOOSE_UNIT) d.unit(d.units().first())
        if (d.d.kind == DecisionKind.DEFEND) d.defend(null)
        // No second repeat question: the repeated action cannot repeat.
        assertThat(d.d.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `p19 This Round effects last through the turns of the round, p17 cancelled when the card changes state`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                played("dodger-silver")
                hand("dodger-gold", exactly = false)
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        val g = dev.quinton.goa.core.game.engine.Game(engine.data, d.s.state, emptyList())
        assertThat(g.statDelta(StatQuery("dodger", Stat.ATTACK, "dodger-gold"))).isEqualTo(1)
        d.action("arien", ActionType.HOLD)
        // Turn 2: still in force.
        val g2 = dev.quinton.goa.core.game.engine.Game(engine.data, d.s.state, emptyList())
        assertThat(g2.statDelta(StatQuery("dodger", Stat.ATTACK, "dodger-gold"))).isEqualTo(1)
        // Discarding the resolved card changes its state: the effect ends.
        val g3 = dev.quinton.goa.core.game.engine.Game(engine.data, d.s.state.copy(), emptyList())
        g3.discard("dodger", "dodger-silver")
        assertThat(g3.statDelta(StatQuery("dodger", Stat.ATTACK, "dodger-gold"))).isEqualTo(0)
    }

    @Test
    fun `p18 Next Turn effects apply only during the next turn, the radius moves with the owner (p17)`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        val now = dev.quinton.goa.core.game.engine.Game(engine.data, d.s.state, emptyList())
        assertThat(now.statDelta(StatQuery("arien", Stat.INITIATIVE, "arien-gold"))).isEqualTo(0) // not yet
        d.action("arien", ActionType.HOLD)
        val next = dev.quinton.goa.core.game.engine.Game(engine.data, d.s.state, emptyList())
        assertThat(next.statDelta(StatQuery("arien", Stat.INITIATIVE, "arien-gold"))).isEqualTo(-2)
        // Dodger far away: out of radius.
        next.state.hero("dodger")!!.at = Hex(-5, -4)
        next.touch()
        assertThat(next.statDelta(StatQuery("arien", Stat.INITIATIVE, "arien-gold"))).isEqualTo(0)
    }

    @Test
    fun `FAQ - a Next Turn effect played on turn 4 does not carry over into the next round`() {
        val d = at {
            clearMinions()
            turn = 4
            hero("dodger", Team.RED, Hex(1, -2)) { played("dodger-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-green-1") }
            phase = PositionPhase.Resolving(next = "dodger")
        }
        d.action("dodger", ActionType.PRIMARY)
        d.action("arien", ActionType.HOLD)
        assertThat(d.s.state.round).isEqualTo(2)
        assertThat(d.s.state.effects).isEmpty()
    }

    @Test
    fun `p17 - Active effects are cancelled when their owner is defeated`() {
        val d = at {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                resolved(1, "dodger-silver")
                hand("dodger-blue-1")
            }
            hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-gold") }
            effect("dodger", "dodger-silver", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.s.state.effects).isEmpty()
    }

    @Test
    fun `p10 card states - swapped cards swap every state, facedown cards lose their Active effect`() {
        val s = position(engine, TestData.FOREST) {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2)) {
                resolved(2, "dodger-silver")
                discard("dodger-gold")
            }
            hero("arien", Team.BLUE, Hex(4, -5))
            effect("dodger", "dodger-silver", Timing.THIS_ROUND, ActionKind.SKILL)
        }
        val g = dev.quinton.goa.core.game.engine.Game(engine.data, s.state.copy(), emptyList())
        g.swapCards("dodger", "dodger-silver", "dodger-gold", keepEffects = true)
        val h = g.state.hero("dodger")!!
        assertThat(h.card("dodger-gold")!!.zone).isEqualTo(Zone.RESOLVED)
        assertThat(h.card("dodger-gold")!!.slot).isEqualTo(2)
        assertThat(h.card("dodger-silver")!!.zone).isEqualTo(Zone.DISCARD)
        g.turnFaceDown("dodger", "dodger-silver")
        assertThat(g.state.effects).isEmpty()
        assertThat(
            s.view("arien").heroes.first {
                it.heroId == "dodger"
            }.discard.single().cardId,
        ).isEqualTo("dodger-gold")
    }

    @Test
    fun `p18 place - tokens are limited, the supply is recycled from the board`() {
        val s = position(engine, TestData.FOREST) {
            clearMinions()
            hero("dodger", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(4, -5))
        }
        val g = dev.quinton.goa.core.game.engine.Game(engine.data, s.state.copy(), emptyList())
        val a = g.placeToken("token_test", "dodger", Hex(0, -2))!!
        g.placeToken("token_test", "dodger", Hex(-1, -2))!!
        assertThat(g.placeToken("token_test", "dodger", Hex(-1, -1))).isNull() // supply empty, no token chosen
        g.placeToken("token_test", "dodger", Hex(-1, -1), takeFrom = a)
        assertThat(g.state.pieces.map { it.at }).containsExactly(Hex(-1, -2), Hex(-1, -1))
    }
}
