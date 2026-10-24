package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** End of Round (p.8) and Leveling Up (pp.15–16). */
class EndOfRoundTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    @Test
    fun `p8 - tokens are removed, markers returned, unless the text says otherwise`() {
        val d = pos {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                marker("marker_poison", "tigerclaw")
                marker("rune", "snorri", persistent = true)
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            token("token_rock", Hex(2, -5))
            token("token_tree", Hex(3, -5), removeAtEndOfRound = false)
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.s.state.pieces.map { it.kind }).containsExactly("token_tree")
        assertThat(d.hero("wasp").markers.map { it.kind }).containsExactly("rune")
    }

    @Test
    fun `p8 retrieve cards - resolved and discarded cards return to hand, all Active effects end`() {
        val d = pos {
            turn = 4
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                resolved(1, "wasp-gold")
                resolved(2, "wasp-silver")
                resolved(3, "wasp-red-1", faceDown = true)
                discard("wasp-green-1")
                resolved(4, "wasp-blue-1")
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            effect("wasp", "wasp-blue-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 4)
            phase = PositionPhase.EndOfRound
        }
        val wasp = d.hero("wasp")
        assertThat(
            wasp.hand.map {
                it.id
            },
        ).containsExactly("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-1")
        assertThat(wasp.cards.none { it.faceDown }).isTrue()
        assertThat(d.s.state.effects).isEmpty()
        assertThat(d.s.state.round).isEqualTo(2)
    }

    @Test
    fun `p17 End of round effects come before the Minion Battle`() {
        // Xargatha's Constrict: "End of round: Defeat an enemy melee minion adjacent to you. (Before the end
        // of round minion battle.)"
        val d = pos {
            clearMinions()
            turn = 4
            hero("xargatha", Team.RED, Hex(1, -5)) { resolved(2, "xargatha-green-2b") }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            effect("xargatha", "xargatha-green-2b", Timing.END_OF_ROUND, ActionKind.SKILL, madeTurn = 2)
            phase = PositionPhase.EndOfRound
        }
        // Constrict defeats the adjacent Blue minion (2 coins), then the battle is 1 against 1: no effect.
        assertThat(d.hero("xargatha").coins).isEqualTo(1) // 2 coins, then the level up to 2 costs 1
        assertThat(d.hero("xargatha").level).isEqualTo(2)
        assertThat(d.texts()).contains("Minion Battle: 1 against 1, no effect.")
    }

    @Test
    fun `p15 level up - mandatory, lowest Tier card upgraded, one of the next two to hand, the other an item`() {
        val d = pos {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 1 }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.UPGRADE_CARD, "wasp")
        assertThat(d.cards()).containsExactly("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        d.card("wasp-green-1")
        d.expect(DecisionKind.KEEP_UPGRADE, "wasp")
        assertThat(d.cards()).containsExactly("wasp-green-2a", "wasp-green-2b")
        d.card("wasp-green-2b")
        val wasp = d.hero("wasp")
        assertThat(wasp.level).isEqualTo(2)
        assertThat(wasp.coins).isEqualTo(0)
        assertThat(wasp.card("wasp-green-2b")!!.zone).isEqualTo(Zone.HAND)
        assertThat(wasp.card("wasp-green-2a")!!.zone).isEqualTo(Zone.ITEM)
        assertThat(wasp.card("wasp-green-1")!!.zone).isEqualTo(Zone.DECK)
        assertThat(d.s.view("arien").heroes.first { it.heroId == "wasp" }.items).containsExactly("wasp-green-2a")
        // Wasp levelled up: no Pity coin; Arien did not: 1 coin.
        assertThat(d.hero("arien").coins).isEqualTo(1)
    }

    @Test
    fun `p15 - buy as many levels as you can afford, all colours to Tier II before any Tier III`() {
        val d = pos {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 7 } // 1 + 2 + 3 = 6 → level 4, 1 left
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        repeat(3) {
            // With one Tier I card left there is nothing to choose: it is upgraded.
            if (d.d.kind == DecisionKind.UPGRADE_CARD) {
                for (c in d.cards()) assertThat(TestData.engine.data.card(c).tier).isEqualTo(1)
                d.card(d.cards().first())
            }
            d.expect(DecisionKind.KEEP_UPGRADE, "wasp")
            for (c in d.cards()) assertThat(TestData.engine.data.card(c).tier).isEqualTo(2)
            d.card(d.cards().first())
        }
        assertThat(d.hero("wasp").level).isEqualTo(4)
        assertThat(d.hero("wasp").coins).isEqualTo(1)
        assertThat(d.hero("wasp").cards.count { it.zone == Zone.ITEM }).isEqualTo(3)
        // The Pity coin comes after the Level Up step and is not spent this round (p.8).
        assertThat(d.hero("wasp").coins).isEqualTo(1)
    }

    @Test
    fun `p16 - after all Tier III the next level unlocks the Ultimate, no more levels, coins still count`() {
        val d = pos {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) {
                level = 7
                coins = 7 + 9
                items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b", "wasp-red-3b", "wasp-green-3b", "wasp-blue-3b")
                hand("wasp-gold", "wasp-silver", "wasp-red-3a", "wasp-green-3a", "wasp-blue-3a")
            }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        val wasp = d.hero("wasp")
        assertThat(wasp.level).isEqualTo(8)
        assertThat(wasp.card("wasp-purple-4")!!.zone).isEqualTo(Zone.ULTIMATE)
        assertThat(wasp.coins).isEqualTo(9)
        assertThat(d.texts()).contains("Wasp reaches level 8 and unlocks High Voltage.")
    }

    @Test
    fun `p8 pity coin - a hero who did not level up gains 1 coin and cannot spend it on a level this round`() {
        val d = pos {
            hero("wasp", Team.RED, geo.heroSpawns(Team.RED)[0]) { coins = 0 }
            hero("arien", Team.BLUE, geo.heroSpawns(Team.BLUE)[0])
            phase = PositionPhase.EndOfRound
        }
        assertThat(d.hero("wasp").coins).isEqualTo(1)
        assertThat(d.hero("wasp").level).isEqualTo(1)
        d.expect(DecisionKind.PLAY_CARD, "wasp") // next round
    }
}
