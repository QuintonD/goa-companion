package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat
import org.junit.Assert.assertThrows
import org.junit.Test

class CardMappingTest {
    private fun card(
        color: String,
        level: Int? = null,
        variant: Int? = null,
        handicapped: Boolean = false,
        extra: Boolean = false,
        primary: String? = "SKILL",
        value: Int? = null,
        sign: String? = null,
        modifier: String? = null,
        modifierValue: Int? = null,
        item: String? = null,
        secondaryMovement: Int? = null,
        secondaryDefense: Int? = null,
        secondaryAttack: Int? = null,
        school: String? = null,
    ) = SoaCard(
        name = "Test Card",
        description = "Do a thing.",
        color = color,
        level = level,
        variant = variant?.let { SoaVariant(it, 2) },
        handicapped = handicapped,
        extra = extra,
        initiative = 7,
        primaryAction = primary,
        primaryValue = value,
        primaryValueSign = sign,
        modifier = modifier,
        modifierValue = modifierValue,
        item = item,
        secondaryMovement = secondaryMovement,
        secondaryDefense = secondaryDefense,
        secondaryAttack = secondaryAttack,
        school = school,
    )

    @Test
    fun `card ids follow the permanent scheme`() {
        assertThat(CardMapping.cardId("arien", card("GOLD"))).isEqualTo("arien-gold")
        assertThat(CardMapping.cardId("arien", card("SILVER"))).isEqualTo("arien-silver")
        assertThat(CardMapping.cardId("arien", card("GOLD", handicapped = true))).isEqualTo("arien-handicap")
        assertThat(CardMapping.cardId("takahide", card("SILVER", handicapped = true))).isEqualTo("takahide-handicap")
        assertThat(CardMapping.cardId("arien", card("PURPLE", level = 4))).isEqualTo("arien-purple-4")
        assertThat(CardMapping.cardId("arien", card("RED", level = 1))).isEqualTo("arien-red-1")
        assertThat(CardMapping.cardId("arien", card("GREEN", level = 2, variant = 1))).isEqualTo("arien-green-2a")
        assertThat(CardMapping.cardId("arien", card("BLUE", level = 3, variant = 2))).isEqualTo("arien-blue-3b")
        assertThat(
            CardMapping.cardId("takahide", card("GOLD", extra = true), extraIndex = 2),
        ).isEqualTo("takahide-gold-extra-2")
    }

    @Test
    fun `levelled cards need a variant`() {
        assertThrows(IllegalStateException::class.java) { CardMapping.cardId("x", card("RED", level = 2)) }
    }

    @Test
    fun `spellbook ids are slugs of the spell name`() {
        assertThat(
            CardMapping.spellId(
                "gydion",
                card("BLUE", level = 2, school = "TRANSMUTATION").copy(name = "Midas' Touch"),
            ),
        )
            .isEqualTo("gydion-spell-midas-touch")
        assertThat(CardMapping.slug("Power Word Kill")).isEqualTo("power-word-kill")
    }

    @Test
    fun `colour and tier follow the deck rules`() {
        assertThat(CardMapping.color(card("GOLD", handicapped = true))).isEqualTo(CardColor.OTHER)
        assertThat(CardMapping.color(card("RED", level = 1, school = "EVOCATION"))).isEqualTo(CardColor.OTHER)
        assertThat(CardMapping.color(card("PURPLE"))).isEqualTo(CardColor.PURPLE)
        assertThat(CardMapping.tier(card("GOLD"))).isNull()
        assertThat(CardMapping.tier(card("GOLD", extra = true))).isNull()
        assertThat(CardMapping.tier(card("SILVER", handicapped = true))).isNull()
        assertThat(CardMapping.tier(card("PURPLE", level = 4))).isEqualTo(4)
        assertThat(CardMapping.tier(card("GREEN", level = 3, variant = 1))).isEqualTo(3)
        assertThat(CardMapping.tier(card("RED", level = 2, school = "NECROMANCY"))).isEqualTo(2)
        assertThat(CardMapping.tier(card("GOLD", school = "CANTRIP"))).isNull()
    }

    @Test
    fun `primary value lands on the matching stat and secondaries fill the rest`() {
        val attack = CardMapping.values(
            card("RED", primary = "ATTACK", value = 6, secondaryMovement = 4, secondaryDefense = 6),
        )
        assertThat(attack.attack).isEqualTo(6)
        assertThat(attack.movement).isEqualTo(4)
        assertThat(attack.defense).isEqualTo(6)

        val defense = CardMapping.values(card("BLUE", primary = "DEFENSE", value = 5, secondaryMovement = 3))
        assertThat(defense.defense).isEqualTo(5)
        assertThat(defense.movement).isEqualTo(3)
        assertThat(defense.attack).isNull()

        val movement = CardMapping.values(card("BLUE", primary = "MOVEMENT", value = 3, secondaryDefense = 6))
        assertThat(movement.movement).isEqualTo(3)
        assertThat(movement.defense).isEqualTo(6)

        val defenseSkill = CardMapping.values(card("SILVER", primary = "DEFENSE_SKILL", value = 1))
        assertThat(defenseSkill.defense).isEqualTo(1)
    }

    @Test
    fun `secondary attack is kept as a value and a secondary action`() {
        val c =
            card("RED", level = 1, primary = "SKILL", secondaryMovement = 3, secondaryDefense = 7, secondaryAttack = 6)
        assertThat(CardMapping.values(c).attack).isEqualTo(6)
        assertThat(CardMapping.secondary(c))
            .containsExactly(SecondaryAction.MOVEMENT, SecondaryAction.DEFENSE, SecondaryAction.ATTACK).inOrder()
    }

    @Test
    fun `range and area modifiers become range and radius`() {
        assertThat(CardMapping.values(card("RED", modifier = "RANGE", modifierValue = 2)).range).isEqualTo(2)
        assertThat(CardMapping.values(card("RED", modifier = "AREA", modifierValue = 3)).radius).isEqualTo(3)
        assertThat(CardMapping.values(card("RED", modifier = "AREA", modifierValue = 3)).range).isNull()
    }

    @Test
    fun `block, ranged and item flags`() {
        val unknown = mutableSetOf<String>()
        val block = CardMapping.toCard(
            "tigerclaw",
            "tigerclaw-blue-1",
            card("BLUE", 1, primary = "DEFENSE", sign = "EXCLAMATION"),
            unknown,
        )
        assertThat(block.block).isTrue()
        assertThat(block.primary).isEqualTo(PrimaryAction.DEFENSE)
        val ranged = CardMapping.toCard(
            "x",
            "x-red-1",
            card("RED", 1, primary = "ATTACK", value = 3, modifier = "RANGE", modifierValue = 2),
            unknown,
        )
        assertThat(ranged.ranged).isTrue()
        val rangedSkill = CardMapping.toCard(
            "x",
            "x-green-1",
            card("GREEN", 1, modifier = "RANGE", modifierValue = 2),
            unknown,
        )
        assertThat(rangedSkill.ranged).isFalse()
        assertThat(CardMapping.item("AREA")).isEqualTo(Stat.RADIUS)
        assertThat(CardMapping.item("RANGE")).isEqualTo(Stat.RANGE)
        assertThat(CardMapping.item(null)).isNull()
    }

    @Test
    fun `art slugs match the source artwork file names`() {
        assertThat(CardMapping.artSlug(card("GOLD"))).isEqualTo("Gold")
        assertThat(CardMapping.artSlug(card("SILVER", handicapped = true))).isEqualTo("Handicap")
        assertThat(CardMapping.artSlug(card("PURPLE", level = 4))).isEqualTo("Ultimate")
        assertThat(CardMapping.artSlug(card("BLUE", level = 1))).isEqualTo("BlueIA")
        assertThat(CardMapping.artSlug(card("RED", level = 2, variant = 2))).isEqualTo("RedIIB")
        assertThat(CardMapping.artSlug(card("GREEN", level = 3, variant = 1))).isEqualTo("GreenIIIA")
        assertThat(CardMapping.artSlug(card("GOLD", extra = true), extraIndex = 1)).isEqualTo("Extra1")
        assertThat(CardMapping.artSlug(card("RED", level = 1, school = "EVOCATION"))).isNull()
    }

    @Test
    fun `hero cards get slots and numbered extras`() {
        val mapped = CardMapping.heroCards(
            "takahide",
            listOf(
                card("GOLD"),
                card("GOLD", extra = true),
                card("GOLD", extra = true),
                card("SILVER", handicapped = true),
            ),
            mutableSetOf(),
        )
        assertThat(mapped.map { it.card.id })
            .containsExactly(
                "takahide-gold",
                "takahide-gold-extra-1",
                "takahide-gold-extra-2",
                "takahide-handicap",
            ).inOrder()
        assertThat(mapped.map { it.slot }).containsExactly(Slot.DECK, Slot.DECK, Slot.DECK, Slot.HANDICAP).inOrder()
    }

    @Test
    fun `roles map from source traits including healer`() {
        assertThat(CardMapping.role("HEALER").name).isEqualTo("HEALER")
        assertThat(CardMapping.role("TACTICIAN").name).isEqualTo("TACTICIAN")
    }
}
