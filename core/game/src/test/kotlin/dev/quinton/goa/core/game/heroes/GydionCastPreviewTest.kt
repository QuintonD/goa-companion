package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.PrimaryPreview
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.UnavailableCard
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.gydion.Spellbook
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BANISHMENT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BURNING_HANDS
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CLOUD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CREATE_UNDEAD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DIMENSION_DOOR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DISINTEGRATE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DOMINATE_PERSON
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.ENERGY_DRAIN
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.EXPEDITIOUS_RETREAT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIND_FAMILIAR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIREBALL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.INVULNERABILITY
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MAGIC_MISSILE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MIDAS_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POLYMORPH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POWER_WORD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.REMOVED
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHIELD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHOCKING_GRASP
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SPELLBOOK
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUGGESTION
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUNBURST
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.VAMPIRIC_TOUCH
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * What a Gydion card will cast, told on the action question before the player commits
 * (`DecisionContext.primaryPreview`, ADR-040 extended after the hands-on pass): in the hands-on game,
 * "Skill, primary action" cast the card's only castable spell — and picked its only target — with no
 * question, and Suggestion was cast to no effect. The preview must agree with what the cast then does,
 * ask nothing new (saves replay by question order), and tell nothing hidden (p.4).
 * Quiet strip of Forgotten Island as in [GydionTest]: rows r = -2 and r = -5, q = -1..4.
 */
class GydionCastPreviewTest {
    private val g0 = Hex(1, -2)

    /** The spells each casting card names, from its text (`catalog/heroes/gydion.json`). */
    private val named = mapOf(
        "gydion-gold" to listOf(SHOCKING_GRASP, MAGIC_MISSILE, EXPEDITIOUS_RETREAT),
        "gydion-handicap" to listOf(EXPEDITIOUS_RETREAT),
        "gydion-red-1" to listOf(BURNING_HANDS),
        "gydion-green-1" to listOf(SHIELD),
        "gydion-blue-1" to listOf(SUGGESTION),
        "gydion-red-2a" to listOf(BURNING_HANDS, FIREBALL),
        "gydion-red-2b" to listOf(VAMPIRIC_TOUCH, CREATE_UNDEAD),
        "gydion-green-2a" to listOf(SHIELD, BANISHMENT),
        "gydion-green-2b" to listOf(FIND_FAMILIAR, DIMENSION_DOOR),
        "gydion-blue-2a" to listOf(SUGGESTION, DOMINATE_PERSON),
        "gydion-blue-2b" to listOf(MIDAS_TOUCH, DISINTEGRATE),
        "gydion-red-3a" to listOf(BURNING_HANDS, FIREBALL, SUNBURST),
        "gydion-red-3b" to listOf(VAMPIRIC_TOUCH, CREATE_UNDEAD, ENERGY_DRAIN),
        "gydion-green-3a" to listOf(SHIELD, BANISHMENT, INVULNERABILITY),
        "gydion-green-3b" to listOf(FIND_FAMILIAR, DIMENSION_DOOR, CLOUD_KILL),
        "gydion-blue-3a" to listOf(SUGGESTION, DOMINATE_PERSON, POWER_WORD_KILL),
        "gydion-blue-3b" to listOf(MIDAS_TOUCH, DISINTEGRATE, POLYMORPH),
    )

    private fun PositionBuilder.HeroBuilder.book(removed: Collection<String>) {
        extra(*(Spellbook.ALL - removed.toSet()).toTypedArray(), tag = SPELLBOOK)
        if (removed.isNotEmpty()) extra(*removed.toTypedArray(), tag = REMOVED)
    }

    /** Where the others stand: next to Gydion, at range 3, out of reach; with or without minions. */
    private enum class Layout { CLOSE, FAR, NOBODY, MINIONS }

    private fun game(card: String, layout: Layout, removed: Collection<String> = emptyList()): Driver = pos {
        clearMinions()
        hero("gydion", Team.RED, g0) {
            played(card)
            book(removed)
        }
        when (layout) {
            Layout.CLOSE -> {
                hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1", "arien-gold") } // adjacent
                hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away
            }

            // Arien 3 away, alone.
            Layout.FAR -> hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") }

            // Arien out of reach.
            Layout.NOBODY -> hero("arien", Team.BLUE, Hex(4, -9)) { hand("arien-blue-1") }

            Layout.MINIONS -> {
                hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") }
                minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // adjacent to Gydion and Arien
                minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // 3 away, alone
                minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            }
        }
        phase = PositionPhase.Resolving(next = "gydion")
    }

    private fun Driver.preview(): PrimaryPreview =
        expect(DecisionKind.CHOOSE_ACTION, "gydion").context.primaryPreview ?: error("No preview\n${log()}")

    private fun Driver.tag(spell: String): String? = hero("gydion").card(spell)!!.tag

    private fun name(id: String) = TestData.catalog.heroes
        .flatMap { it.allCards }.first { it.id == id }.name

    private fun restore(s: EngineSession): EngineSession = ANY_HERO_ENGINE.restore(s.save()) as EngineSession

    /** What the board, coins and hands look like, to tell "nothing happened". */
    private fun Driver.snapshot(): List<Any?> = listOf(
        s.state.heroes.map { Triple(it.id, it.at, it.coins) },
        s.state.heroes.map { h -> h.cards.filter { it.zone == Zone.HAND }.map { it.id } },
        s.state.minions.map { it.id to it.at },
        s.state.pieces.map { it.id to it.at },
        s.state.lifeCounters.toMap(),
        s.state.effects.map { it.cardId },
    )

    /**
     * The preview against what answering Primary does: its spells are the card's, split into the
     * castable (the Primary option exists iff any) and the rest with why; two or more castable → the
     * cast question offers exactly them; one → it is cast at once (no new question), its only target
     * is the one the action takes, and "will do nothing" means nothing but the spell's removal changed.
     */
    private fun checkAgainstCast(card: String, layout: Layout, removed: Collection<String>): PrimaryPreview {
        val d = game(card, layout, removed)
        val p = d.preview()
        val label = "$card $layout removed=$removed: $p"
        assertWithMessage(label).that((p.cardIds + p.unavailableCards.map { it.cardId }).sorted())
            .containsExactlyElementsIn(named.getValue(card).sorted())
        assertWithMessage(label).that(ActionType.PRIMARY in d.actions()).isEqualTo(p.cardIds.isNotEmpty())
        if (p.cardIds.isEmpty()) {
            assertWithMessage(label).that(p.onlyTargetId).isNull()
            assertWithMessage(label).that(p.noEffect).isNull()
            return p
        }
        val asked = d.d.id
        val before = d.snapshot()
        d.action("gydion", ActionType.PRIMARY)
        if (p.cardIds.size > 1) {
            d.expect(DecisionKind.CHOOSE_CARD, "gydion")
            assertThat(d.d.prompt).isEqualTo(Spellbook.CAST_PROMPT)
            assertWithMessage(label).that(d.cards()).containsExactlyElementsIn(p.cardIds)
            assertWithMessage(label).that(p.onlyTargetId).isNull()
            assertWithMessage(label).that(p.noEffect).isNull()
            return p
        }
        val spell = p.cardIds.single()
        assertWithMessage(label).that(d.texts()).contains("Gydion casts ${name(spell)}.")
        assertWithMessage(label).that(d.tag(spell)).isEqualTo(REMOVED)
        val next = d.pending
        // The cast itself asked nothing: the next question (if any) is the spell's own, never the cast's.
        if (next != null && next.seat == "gydion") assertThat(next.prompt).isNotEqualTo(Spellbook.CAST_PROMPT)
        if (next != null) assertThat(next.id).isGreaterThan(asked)
        if (p.noEffect != null) {
            assertWithMessage(label).that(d.snapshot()).isEqualTo(before)
            assertWithMessage(label).that(next?.seat == "gydion" && next.sourceCardId == spell).isFalse()
        }
        p.onlyTargetId?.let { t ->
            val taken = takes(d, t)
            assertWithMessage("$label\n${d.log()}").that(taken).isTrue()
        }
        return p
    }

    /**
     * Plays the action on (declining what may be declined) to its end: the only target is taken with
     * no question, so no question of Gydion's ever offers it, and it is affected — it defends, or the
     * log names it, or (a target the log doesn't name: Dominate Person's hero) it was never a choice.
     */
    private fun takes(d: Driver, t: String): Boolean {
        repeat(MAX_STEPS) {
            val q = d.pending ?: return true
            if (q.kind == DecisionKind.DEFEND && q.seat == t) return true
            if (q.kind == DecisionKind.CHOOSE_ACTION || q.kind == DecisionKind.PLAY_CARD) return true
            val offered = q.options.any { it == Choice.Unit(t) || it == Choice.Token(t) }
            if (q.seat == "gydion" && q.kind == DecisionKind.CHOOSE_UNIT && offered) return false
            d.answer(q.options.firstOrNull { it is Choice.Decline } ?: q.options.first())
        }
        return true
    }

    @Test
    fun `every casting card in four layouts and three spellbooks - the preview matches what the cast does`() {
        val books = listOf(
            emptyList(),
            listOf(EXPEDITIOUS_RETREAT, BURNING_HANDS, SHIELD, SUGGESTION, VAMPIRIC_TOUCH),
            listOf(SHOCKING_GRASP, MAGIC_MISSILE, FIREBALL, BANISHMENT, FIND_FAMILIAR, DOMINATE_PERSON, MIDAS_TOUCH),
        )
        val previews = named.keys.flatMap { card ->
            Layout.entries.flatMap { layout -> books.map { removed -> checkAgainstCast(card, layout, removed) } }
        }
        // The layouts reach every kind of preview: several spells, one, none; a target; nothing to do.
        assertThat(previews.count { it.cardIds.size > 1 }).isGreaterThan(0)
        assertThat(previews.count { it.cardIds.size == 1 }).isGreaterThan(0)
        assertThat(previews.count { it.cardIds.isEmpty() }).isGreaterThan(0)
        assertThat(previews.count { it.onlyTargetId != null }).isGreaterThan(0)
        assertThat(previews.count { it.noEffect != null }).isGreaterThan(0)
    }

    // ---- The hands-on findings ------------------------------------------------------------------

    @Test
    fun `Cantrip with only Expeditious Retreat castable - the preview names it, then it is cast with no question`() {
        val d = game("gydion-gold", Layout.NOBODY)
        val p = d.preview()
        assertThat(p.cardIds).containsExactly(EXPEDITIOUS_RETREAT)
        assertThat(p.unavailableCards.map { it.cardId }).containsExactly(SHOCKING_GRASP, MAGIC_MISSILE).inOrder()
        assertThat(p.unavailableCards.first { it.cardId == SHOCKING_GRASP }.text)
            .isEqualTo("Its Attack can't start: no target adjacent to you")
        assertThat(p.noEffect).isNull()
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.texts()).contains("Gydion casts Expeditious Retreat.")
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion") // the spell's own movement
    }

    @Test
    fun `Lesser Evocation with only Fireball castable - its only target is named before the cast`() {
        // Burning Hands has no adjacent unit; Fireball's one target is Arien, 3 away and alone.
        val d = game("gydion-red-2a", Layout.FAR)
        val p = d.preview()
        assertThat(p.cardIds).containsExactly(FIREBALL)
        assertThat(p.onlyTargetId).isEqualTo("arien")
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `two castable spells - both named, no target or outcome guessed, the cast question follows`() {
        val d = game("gydion-gold", Layout.CLOSE)
        val p = d.preview()
        assertThat(p.cardIds).containsExactly(SHOCKING_GRASP, MAGIC_MISSILE, EXPEDITIOUS_RETREAT).inOrder()
        assertThat(p.onlyTargetId).isNull()
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(SHOCKING_GRASP, MAGIC_MISSILE, EXPEDITIOUS_RETREAT)
    }

    @Test
    fun `Elementary Enchantment with no enemy hero in radius - Suggestion will do nothing and is removed`() {
        val d = game("gydion-blue-1", Layout.NOBODY)
        val p = d.preview()
        assertThat(p.cardIds).containsExactly(SUGGESTION)
        assertThat(p.noEffect)
            .isEqualTo("No enemy hero in radius 3: Suggestion will do nothing and is removed from the spellbook.")
        assertThat(p.noEffectSource).isEqualTo("Prepare Spells")
        // Rules unchanged (rulings/gydion.md 5): "If able" can always be cast.
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.tag(SUGGESTION)).isEqualTo(REMOVED)
        assertThat(d.texts()).contains("Suggestion does nothing: no enemy hero in radius 3.")
    }

    @Test
    fun `Elementary Enchantment with an enemy hero able to move - no warning`() {
        val d = game("gydion-blue-1", Layout.FAR)
        val p = d.preview()
        assertThat(p.cardIds).containsExactly(SUGGESTION)
        assertThat(p.noEffect).isNull()
        assertThat(p.onlyTargetId).isEqualTo("arien")
    }

    @Test
    fun `Midas Touch with fewer than two other spells removed gains nothing, said - with five, no warning`() {
        val none = game("gydion-blue-2b", Layout.NOBODY, removed = listOf(MAGIC_MISSILE))
        assertThat(none.preview().cardIds).containsExactly(MIDAS_TOUCH)
        assertThat(none.preview().noEffect).isEqualTo(
            "Fewer than two other spell cards are removed from the spellbook: Midas' Touch will do nothing " +
                "and is removed from the spellbook.",
        )
        val five = listOf(MAGIC_MISSILE, SHOCKING_GRASP, EXPEDITIOUS_RETREAT, BURNING_HANDS, FIREBALL)
        val some = game("gydion-blue-2b", Layout.NOBODY, removed = five)
        assertThat(some.preview().noEffect).isNull()
        val coins = some.hero("gydion").coins
        some.action("gydion", ActionType.PRIMARY)
        assertThat(some.hero("gydion").coins).isEqualTo(coins + 2)
    }

    @Test
    fun `no spell of the card can be cast - each spell is named with its own reason`() {
        // Cantrip with Expeditious Retreat removed and nobody in reach: nothing to cast.
        val d = game("gydion-gold", Layout.NOBODY, removed = listOf(EXPEDITIOUS_RETREAT))
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        val p = d.preview()
        assertThat(p.cardIds).isEmpty()
        assertThat(p.unavailableCards).containsExactly(
            UnavailableCard(SHOCKING_GRASP, "Its Attack can't start: no target adjacent to you", "p.10, FAQ"),
            UnavailableCard(MAGIC_MISSILE, "Its Attack can't start: no target in range", "p.10, FAQ"),
            UnavailableCard(EXPEDITIOUS_RETREAT, "Not in the spellbook: removed faceup", "Prepare Spells"),
        ).inOrder()
    }

    // ---- Saves and hidden information --------------------------------------------------------------

    @Test
    fun `the preview asks nothing - a save made at the action question replays to the same questions`() {
        val d = game("gydion-red-2a", Layout.FAR)
        val restored = restore(d.s)
        assertThat(restored.pending).isEqualTo(d.s.pending)
        d.action("gydion", ActionType.PRIMARY)
        restored.answer(restored.pending!!.id, Choice.Action(ActionType.PRIMARY))
        assertThat(restored.pending).isEqualTo(d.s.pending)
        assertThat(restore(d.s).pending).isEqualTo(d.s.pending)
    }

    @Test
    fun `no peeking - an enemy hand decides Cloud Kill and Energy Drain, so the preview tells nothing of it`() {
        // Greater Conjuration with Find Familiar and Dimension Door removed: Cloud Kill only, on Arien.
        fun cloud(vararg hand: String) = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-3b")
                book(listOf(FIND_FAMILIAR, DIMENSION_DOOR))
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand(*hand) }
            phase = PositionPhase.Resolving(next = "gydion")
        }.preview()
        val basic = cloud("arien-gold") // a basic card to discard
        val none = cloud("arien-blue-1") // no basic card: the discard does nothing
        assertThat(basic).isEqualTo(none)
        assertThat(none.cardIds).containsExactly(CLOUD_KILL)
        assertThat(none.noEffect).isNull()

        fun drain(vararg hand: String) = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3b")
                book(listOf(VAMPIRIC_TOUCH, CREATE_UNDEAD))
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand(*hand) }
            phase = PositionPhase.Resolving(next = "gydion")
        }.preview()
        assertThat(drain("arien-blue-1")).isEqualTo(drain("arien-gold"))
        assertThat(drain("arien-gold").noEffect).isNull()
    }

    @Test
    fun `no peeking - only Gydion's own action question carries his spells`() {
        val d = game("gydion-gold", Layout.CLOSE)
        assertThat(d.d.seat).isEqualTo("gydion")
        assertThat(d.preview().cardIds).isNotEmpty()
        // The others' questions in the same game carry none.
        d.action("gydion", ActionType.PRIMARY)
        d.card(MAGIC_MISSILE)
        // Magic Missile's only target (Arien is adjacent) is Brogan.
        val defend = d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(defend.context.primaryPreview).isNull()
    }

    private companion object {
        const val MAX_STEPS = 12
    }
}
