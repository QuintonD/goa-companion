package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch6.RandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Emmitt the Traveler: every card, clause by clause (text from `catalog/heroes/emmitt.json`). */
class EmmittTest {
    private fun glitches(d: dev.quinton.goa.core.game.Driver) =
        d.s.state.pieces.filter { it.kind == EmmittHero.GLITCH && it.at != null }

    // ---- Reverse Time (gold) / Tear in Time (Handicap) -----------------------------------------

    @Test
    fun `Reverse Time - Next turn, heroes with lower initiative act first, only that turn`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                played("emmitt-gold")
                hand("emmitt-red-1", "emmitt-green-1")
            }
            hero("arien", Team.BLUE, Hex(-1, -5)) { hand("arien-blue-1", "arien-red-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        assertThat(d.hero("emmitt").coins).isEqualTo(2)
        // Turn 2: Emmitt 7, Arien 9 — the lower acts first.
        d.play("emmitt", "emmitt-red-1")
        d.play("arien", "arien-blue-1")
        assertThat(d.s.view(null).initiativeOrder).containsExactly("emmitt", "arien").inOrder()
        d.action("emmitt", ActionType.HOLD)
        d.action("arien", ActionType.HOLD)
        // Turn 3: back to normal, Arien 8 before Emmitt 6.
        d.play("emmitt", "emmitt-green-1")
        d.play("arien", "arien-red-1")
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Reverse Time - the effect starts after the attack, also when the hero defends`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                played("emmitt-gold")
                hand("emmitt-red-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        // The attack connected (Arien defended): the effect is active for next turn.
        assertThat(d.s.state.effects.map { it.cardId }).contains("emmitt-gold")
    }

    @Test
    fun `Tear in Time - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                handicap = true
                played("emmitt-handicap")
            }
            hero("arien", Team.BLUE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Tear in Time - after the attack on a hero, next turn lower initiative acts first`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                handicap = true
                played("emmitt-handicap")
                hand("emmitt-red-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1", "arien-red-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-1") // Defense 5 against Attack 3
        d.play("emmitt", "emmitt-red-1") // 7
        d.play("arien", "arien-red-1") // 8
        d.expect(DecisionKind.CHOOSE_ACTION, "emmitt")
    }

    // ---- Unstable Timeline (silver) ------------------------------------------------------------

    @Test
    fun `Unstable Timeline - place 2 Glitch tokens two spaces apart, an enemy hero picks one, Emmitt swaps`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-silver") } // radius 4
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "emmitt")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(1..4)
        d.space(1, -5)
        // "at least two spaces between each token": 3 or more apart.
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -5))).isAtLeast(3)
        assertThat(d.spaces()).doesNotContain(Hex(2, -5))
        d.space(4, -2)
        // Arien, the only enemy hero in play, picks the token.
        d.expect(DecisionKind.CHOOSE_TOKEN, "arien")
        assertThat(glitches(d)).hasSize(2)
        d.token(glitches(d).single { it.at == Hex(4, -2) }.id)
        assertThat(d.at("emmitt")).isEqualTo(Hex(4, -2))
        assertThat(d.texts()).contains("Emmitt and Glitch swap places.")
        // "End of turn: Remove all Glitch tokens." (the turn ended: only Emmitt had a card)
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(glitches(d)).isEmpty()
    }

    @Test
    fun `Unstable Timeline - used as a defense, 3 tokens are placed and the defense still counts`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // Attack 6
            hero("emmitt", Team.RED, Hex(1, -2)) { hand("emmitt-silver") } // Defense 6
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("emmitt-silver")
        repeat(3) {
            d.expect(DecisionKind.CHOOSE_SPACE, "emmitt")
            d.space(d.spaces().first())
        }
        d.expect(DecisionKind.CHOOSE_TOKEN, "arien")
        assertThat(glitches(d)).hasSize(3)
        val t = glitches(d).first()
        val to = t.at
        d.token(t.id)
        assertThat(d.at("emmitt")).isEqualTo(to)
        assertThat(
            d.texts().any {
                it.startsWith("Emmitt defends with Unstable Timeline") && "Defended" in it
            },
        ).isTrue()
    }

    // ---- Temporal Punch / Slam / Judgment (red) ------------------------------------------------

    @Test
    fun `Temporal Punch - the defender uses the Initiative of their card instead of its Defense`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-1") } // Attack 9
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // Defense 5, Initiative 9
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
        assertThat(d.texts().any { "Initiative 9" in it && "Defended" in it }).isTrue()
    }

    @Test
    fun `Temporal Slam - minion defense modifiers are applied as normal`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-2a") } // Attack 11
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-2b") } // Initiative 10
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // +1 for Arien
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-2b") // Initiative 10 + 1 (adjacent friendly melee minion)
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Temporal Judgment - Initiative 10 against Attack 12 is not enough`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-3a") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-2b") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-2b")
        assertThat(d.at("arien")).isNull()
    }

    // ---- Flashback / Déjà Vu (red alternates) --------------------------------------------------

    @Test
    fun `Flashback - after the attack, may place 3 Glitch tokens, up to 1 enemy hero swaps with one of their choice`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) // in radius
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // the target
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "emmitt")
        d.yes()
        repeat(3) {
            for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(1, -2))).isIn(1..3)
            d.space(d.spaces().first())
        }
        val placed = glitches(d).map { it.at!! }
        for (a in placed) for (b in placed) if (a != b) assertThat(HexGrid.distance(a, b)).isAtLeast(3)
        // Up to 1 enemy hero: Emmitt may choose Arien or nobody.
        d.expect(DecisionKind.CHOOSE_UNIT, "emmitt")
        assertThat(d.d.options).contains(Choice.Decline())
        d.unit("arien")
        d.expect(DecisionKind.CHOOSE_TOKEN, "arien")
        val t = glitches(d).last()
        val to = t.at
        d.token(t.id)
        assertThat(d.at("arien")).isEqualTo(to)
    }

    @Test
    fun `Deja Vu - 2 tokens, and the enemy swap may be declined`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-3b") }
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(4, -7)) // no Lane push when a Blue minion falls
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.yes()
        repeat(2) { d.space(d.spaces().first()) }
        assertThat(glitches(d)).hasSize(2)
        d.decline()
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(glitches(d)).isEmpty() // End of turn
    }

    // ---- Time Walk / Fast Forward (green) -------------------------------------------------------

    @Test
    fun `Time Walk - move an enemy hero in range who remained in place 2 spaces in a straight line`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-green-1") } // init 6, range 3
            hero("brogan", Team.BLUE, Hex(3, -3)) { played("brogan-blue-1") } // init 8: moves first
            hero("arien", Team.BLUE, Hex(3, -2)) // stays
            phase = PositionPhase.Resolving()
        }
        d.action("brogan", ActionType.MOVEMENT)
        d.space(3, -4) // still in range, but no longer "remained"
        d.action("emmitt", ActionType.PRIMARY)
        // Arien is the only valid hero: his destination is asked straight away.
        d.expect(DecisionKind.CHOOSE_SPACE, "emmitt")
        assertThat(d.d.context.subjectId).isEqualTo("arien")
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(h, Hex(3, -2))).isEqualTo(2)
            assertThat(HexGrid.inStraightLine(h, Hex(3, -2))).isTrue()
        }
        d.space(3, 0)
        assertThat(d.at("arien")).isEqualTo(Hex(3, 0))
    }

    @Test
    fun `Time Walk - a hero moved away and back this turn did not remain in the same space`() {
        val d = pos {
            clearMinions()
            hero("wasp", Team.RED, Hex(1, -5)) { played("wasp-blue-1") } // Lift Up, init 10
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-green-1") }
            hero("arien", Team.BLUE, Hex(3, -5)) // 3 from Emmitt
            phase = PositionPhase.Resolving()
        }
        d.action("wasp", ActionType.PRIMARY)
        if (d.d.kind == DecisionKind.CHOOSE_SPACE) d.space(3, -6)
        d.yes() // repeat on the same target
        d.space(3, -5) // back where he started
        assertThat(d.at("arien")).isEqualTo(Hex(3, -5))
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Fast Forward - range 4`() {
        fun build(card: String) = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played(card) }
            hero("arien", Team.BLUE, Hex(5, -5)) // 4 away
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        assertThat(build("emmitt-green-1").actions()).doesNotContain(ActionType.PRIMARY)
        val d = build("emmitt-green-2a")
        d.action("emmitt", ActionType.PRIMARY)
        assertThat(d.d.context.subjectId).isEqualTo("arien")
    }

    // ---- Time Snare / Time Trap / Time Bomb (blue) ----------------------------------------------

    @Test
    fun `Time Snare - an enemy hero in range who already resolved a card this turn discards, if able`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-1") } // init 8, range 2
            hero("arien", Team.BLUE, Hex(3, -2)) {
                played("arien-blue-2b") // init 10: resolves first
                hand("arien-gold", "arien-silver")
            }
            hero("brogan", Team.BLUE, Hex(0, -2)) {
                played("brogan-green-1") // init 6: not resolved yet
                hand("brogan-gold", "brogan-silver")
            }
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.HOLD)
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Time Snare - nobody resolved yet, the action is not offered`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-1") }
            hero("brogan", Team.BLUE, Hex(0, -2)) { played("brogan-green-1") }
            phase = PositionPhase.Resolving()
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Time Trap - range 3, and Time Bomb - discards a card, or is defeated`() {
        val trap = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-2a") }
            hero("arien", Team.BLUE, Hex(4, -2)) {
                played("arien-blue-2b")
                hand("arien-gold", "arien-silver")
            }
            phase = PositionPhase.Resolving()
        }
        trap.action("arien", ActionType.HOLD)
        trap.action("emmitt", ActionType.PRIMARY)
        trap.expect(DecisionKind.CHOOSE_CARD, "arien")
        val bomb = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-3a") }
            hero("arien", Team.BLUE, Hex(4, -2)) {
                played("arien-blue-2b")
                hand()
            }
            phase = PositionPhase.Resolving()
        }
        bomb.action("arien", ActionType.HOLD)
        bomb.action("emmitt", ActionType.PRIMARY)
        assertThat(bomb.at("arien")).isNull()
        assertThat(bomb.hero("emmitt").coins).isEqualTo(1)
    }

    // ---- Time Loop (blue alternate) -------------------------------------------------------------

    @Test
    fun `Time Loop - swap with an enemy hero in range who has already resolved a card this turn`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-2b") } // init 9, range 4
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-blue-2b") } // init 10
            hero("brogan", Team.BLUE, Hex(0, -2)) { played("brogan-green-1") } // init 6
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.HOLD)
        d.action("emmitt", ActionType.PRIMARY)
        // Brogan has not resolved yet: Arien is the only choice.
        assertThat(d.at("emmitt")).isEqualTo(Hex(4, -5))
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
    }

    // ---- Time Capsule (green alternate) ---------------------------------------------------------

    @Test
    fun `Time Capsule - you and friendly heroes in radius may retrieve all cards discarded this turn`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // init 8, attacks Wasp
            hero("wasp", Team.RED, Hex(3, -2)) {
                hand("wasp-red-1", "wasp-gold")
                discard("wasp-blue-1") // discarded in an earlier turn
            }
            hero("emmitt", Team.RED, Hex(-1, -2)) { played("emmitt-green-2b") } // init 5, radius 4
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("wasp-red-1")
        d.action("emmitt", ActionType.PRIMARY)
        // Emmitt discarded nothing; Wasp decides for herself.
        d.expect(DecisionKind.CONFIRM, "wasp")
        d.yes()
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Time Capsule - nothing discarded this turn, the action is not offered`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(-1, -2)) {
                played("emmitt-green-2b")
                discard("emmitt-red-1")
            }
            hero("arien", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Back to the Future / Future Proof (green III) ------------------------------------------

    @Test
    fun `Back to the Future - place a unit in range into the space where it was at the start of this turn`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") } // init 8
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-green-3a") } // init 5, range 4
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.MOVEMENT)
        d.space(3, -4)
        d.action("emmitt", ActionType.PRIMARY)
        // The other bullet can't start (nobody remained in place): no question; Arien is the only unit.
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Back to the Future - choose the second bullet, move an enemy hero who remained in place`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") }
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-green-3a") }
            hero("brogan", Team.BLUE, Hex(0, -2)) // remained
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.MOVEMENT)
        d.space(3, -4)
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "emmitt")
        d.option(1)
        assertThat(d.d.context.subjectId).isEqualTo("brogan")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -2))).isEqualTo(2)
    }

    @Test
    fun `Future Proof - This turn, friendly heroes in radius are immune to enemy actions`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(-1, -2)) { played("emmitt-green-3b") } // radius 4
            hero("wasp", Team.RED, Hex(3, -2)) // 4 away
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY) // only the immunity bullet can start: no question
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY) // Wasp can't be targeted
    }

    @Test
    fun `Future Proof - Emmitt himself is not protected`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                played("emmitt-green-3b")
                hand("emmitt-red-1")
            }
            hero("wasp", Team.RED, Hex(2, -3)) // adjacent to Arien, in radius: immune
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        // Emmitt is the only target (no question): "friendly" never includes you.
        d.expect(DecisionKind.DEFEND, "emmitt")
    }

    @Test
    fun `Future Proof - friends outside the radius are not protected`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(-1, -2)) { played("emmitt-green-3b") }
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-red-1") } // 5 away
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Future Proof - the retrieve bullet, when cards were discarded this turn`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(0, -2)) { played("arien-red-1") } // init 8, attacks Emmitt
            hero("emmitt", Team.RED, Hex(-1, -2)) {
                played("emmitt-green-3b")
                hand("emmitt-red-1")
            }
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("emmitt-red-1") // Defense 7 against 6
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "emmitt")
        d.option(0)
        d.expect(DecisionKind.CONFIRM, "emmitt")
        d.yes()
        assertThat(d.zoneOf("emmitt", "emmitt-red-1")).isEqualTo(Zone.HAND)
    }

    // ---- Time Warp (blue III alternate) ----------------------------------------------------------

    @Test
    fun `Time Warp - an enemy hero swaps their unresolved card with one of their resolved cards of their choice`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-3b") } // init 9, range 4
            hero("arien", Team.BLUE, Hex(3, -2)) {
                played("arien-green-1") // init 4
                resolved(1, "arien-red-1")
                resolved(2, "arien-blue-1")
            }
            phase = PositionPhase.Resolving()
        }
        d.action("emmitt", ActionType.PRIMARY)
        // Nobody resolved this turn: only the card bullet; Arien is the only enemy hero; he chooses.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        assertThat(d.cards()).containsExactly("arien-red-1", "arien-blue-1")
        d.card("arien-red-1")
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.PLAYED)
        assertThat(d.hero("arien").card("arien-green-1")!!.zone).isEqualTo(Zone.RESOLVED)
        assertThat(d.hero("arien").card("arien-green-1")!!.slot).isEqualTo(1)
        assertThat(d.s.state.turnRecord.playedAttack).contains("arien")
        // Arien now resolves Dangerous Current.
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Time Warp - the swap bullet, with an enemy hero who already resolved`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-blue-2b") } // init 10
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-3b") }
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.HOLD)
        d.action("emmitt", ActionType.PRIMARY)
        assertThat(d.at("emmitt")).isEqualTo(Hex(4, -5))
    }

    // ---- Alternative Timelines (Ultimate) ------------------------------------------------------

    @Test
    fun `Alternative Timelines - may play two cards, after the reveal retrieve one of the unresolved cards`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                ultimate()
                hand("emmitt-gold", "emmitt-red-1", "emmitt-green-1")
            }
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold") }
            phase = PositionPhase.PlayCards(0)
        }
        d.play("emmitt", "emmitt-gold")
        d.expect(DecisionKind.PLAY_CARD, "emmitt")
        assertThat(d.d.options).contains(Choice.Decline("Play only one card"))
        d.card("emmitt-red-1")
        assertThat(d.s.view("emmitt").heroes.single { it.heroId == "emmitt" }.alsoPlayed).hasSize(1)
        d.play("arien", "arien-gold")
        d.expect(DecisionKind.CHOOSE_CARD, "emmitt")
        assertThat(d.cards()).containsExactly("emmitt-gold", "emmitt-red-1")
        d.card("emmitt-gold")
        assertThat(d.zoneOf("emmitt", "emmitt-gold")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("emmitt", "emmitt-red-1")).isEqualTo(Zone.PLAYED)
    }

    @Test
    fun `Alternative Timelines - playing one card asks nothing after the reveal`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                ultimate()
                hand("emmitt-gold", "emmitt-red-1")
            }
            hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold") }
            phase = PositionPhase.PlayCards(0)
        }
        d.play("emmitt", "emmitt-gold")
        d.decline()
        d.play("arien", "arien-gold")
        assertThat(d.d.kind).isEqualTo(DecisionKind.CHOOSE_ACTION)
    }

    // ---- immunity, items, Active effect duration -----------------------------------------------

    @Test
    fun `Temporal Punch - the defender adds their Initiative items (p16)`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-red-1") } // Attack 9
            hero("arien", Team.BLUE, Hex(2, -2)) {
                hand("arien-red-1") // Initiative 8
                items("arien-blue-2a") // +1 Initiative
            }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-red-1")
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
        val defended = d.s.events.filterIsInstance<dev.quinton.goa.core.game.GameEvent.Defended>().single()
        assertThat(defended.total).isEqualTo(9)
    }

    @Test
    fun `Temporal Punch - a hero immune to attack actions can't be targeted, Time Snare still can (p18)`() {
        fun build(card: String, init: String) = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played(card) }
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played(init)
                discard("arien-blue-2a")
                hand("arien-gold", "arien-silver")
            }
            // Expert Duelist, used against another attacker: immune to Emmitt's attack actions this turn.
            effect("arien", "arien-blue-2a", Timing.THIS_TURN, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        assertThat(build("emmitt-red-1", "arien-green-1").actions()).doesNotContain(ActionType.PRIMARY)
        // Time Snare is a skill: the attack immunity does not protect Arien (Initiative 10, resolves first).
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) { played("emmitt-blue-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played("arien-blue-2b")
                discard("arien-blue-2a")
                hand("arien-gold", "arien-silver")
            }
            effect("arien", "arien-blue-2a", Timing.THIS_TURN, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving()
        }
        d.action("arien", ActionType.HOLD)
        d.action("emmitt", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Reverse Time - cancelled when Emmitt is defeated before next turn (p17)`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                played("emmitt-gold") // init 11
                hand()
            }
            hero("wasp", Team.RED, Hex(-1, -5)) { hand("wasp-red-1", "wasp-gold") }
            hero("arien", Team.BLUE, Hex(1, -1)) {
                played("arien-red-1") // init 8, next to Emmitt
                hand("arien-blue-1")
            }
            hero("brogan", Team.BLUE, Hex(4, -5)) { hand("brogan-green-1") }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        d.defend("arien-blue-1") // Defense 5 against Attack 4
        assertThat(d.s.state.effects.map { it.cardId }).contains("emmitt-gold")
        d.action("arien", ActionType.PRIMARY) // Emmitt, with no card in hand, is defeated
        assertThat(d.at("emmitt")).isNull()
        assertThat(d.s.state.effects.map { it.cardId }).doesNotContain("emmitt-gold")
        // Turn 2: normal order, Wasp 8 before Brogan 6.
        d.expect(DecisionKind.PLAY_CARD, "emmitt")
        d.answer(Choice.Pass)
        d.play("wasp", "wasp-red-1")
        d.expect(DecisionKind.PLAY_CARD, "arien")
        d.answer(Choice.Pass)
        d.play("brogan", "brogan-green-1")
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
    }

    @Test
    fun `Future Proof - the immunity ends with the turn (p19 This turn)`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(-1, -2)) {
                played("emmitt-green-3b")
                hand()
            }
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-green-1", "wasp-red-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played("arien-red-1")
                hand("arien-gold")
            }
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        d.action("arien", ActionType.HOLD)
        d.answer(Choice.Pass) // Emmitt has no card
        d.play("wasp", "wasp-green-1")
        d.play("arien", "arien-gold")
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Unstable Timeline - a Radius item widens where the tokens may go (p16)`() {
        val d = pos {
            clearMinions()
            hero("emmitt", Team.RED, Hex(1, -2)) {
                played("emmitt-silver") // radius 4 + 1 (Time Warp as an item)
                items("emmitt-blue-3b")
            }
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "emmitt")
        }
        d.action("emmitt", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(it, Hex(1, -2)) }).isEqualTo(5)
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Emmitt keep every invariant`() {
        val stats = RandomGames.play("emmitt", games = 250, seed = 62, maxPlayed = { if (it == "emmitt") 2 else 1 })
        println("EMMITT random games: $stats")
    }
}
