package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.effects.CardScript
import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.gydion.GydionSpells
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
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHIELD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHOCKING_GRASP
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUGGESTION
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUNBURST
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.VAMPIRIC_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.WISH

/**
 * Gydion the Archwizard. Every deck card casts spells from the spellbook ([Spellbook]); the 22 spell
 * cards are in [GydionSpells]. Card text quoted verbatim above each registration
 * (`catalog/heroes/gydion.json`); `GydionTest` follows it clause by clause. Readings in
 * `core/game/rulings/gydion.md`.
 */
object GydionHero : HeroModule {
    override val heroId: String = "gydion"
    override val complete: Boolean = true

    override fun register(r: Registry) {
        // Cantrip: "Choose and cast one spell in the spellbook — • "Shocking Grasp" (To repel a foe!)
        // • "Magic Missile" (To blast at range!) • "Expeditious Retreat" (To dart a great distance!)"
        r.card("gydion-gold", Cast(SHOCKING_GRASP, MAGIC_MISSILE, EXPEDITIOUS_RETREAT))
        // Cheap Trick (Handicap): "Cast the "Expeditious Retreat" spell in the spellbook. (To dart a great distance!)"
        r.card("gydion-handicap", Cast(EXPEDITIOUS_RETREAT))
        // Prepare Spells: "Add all spell cards to the spellbook. Only Gydion can look at the cards in the
        // spellbook. As each spell is cast, remove it from the spellbook faceup. The action on the spell
        // card is performed by the hero casting the spell."
        r.card("gydion-silver", PrepareSpells)
        // Elementary Evocation: "Cast the "Burning Hands" spell in the spellbook."
        r.card("gydion-red-1", Cast(BURNING_HANDS))
        // Elementary Abjuration: "Cast the "Shield" spell in the spellbook."
        r.card("gydion-green-1", Cast(SHIELD))
        // Elementary Enchantment: "Cast the "Suggestion" spell in the spellbook."
        r.card("gydion-blue-1", Cast(SUGGESTION))
        // Lesser Evocation: "Choose and cast one spell in the spellbook — • "Burning Hands" • "Fireball""
        r.card("gydion-red-2a", Cast(BURNING_HANDS, FIREBALL))
        // Lesser Necromancy: "Choose and cast one … — • "Vampiric Touch" • "Create Undead""
        r.card("gydion-red-2b", Cast(VAMPIRIC_TOUCH, CREATE_UNDEAD))
        // Lesser Abjuration: "Choose and cast one … — • "Shield" • "Banishment""
        r.card("gydion-green-2a", Cast(SHIELD, BANISHMENT))
        // Lesser Conjuration: "Choose and cast one … — • "Find Familiar" • "Dimension Door""
        r.card("gydion-green-2b", Cast(FIND_FAMILIAR, DIMENSION_DOOR))
        // Lesser Enchantment: "Choose and cast one … — • "Suggestion" • "Dominate Person""
        r.card("gydion-blue-2a", Cast(SUGGESTION, DOMINATE_PERSON))
        // Lesser Transmutation: "Choose and cast one … — • "Midas Touch" • "Disintegrate""
        r.card("gydion-blue-2b", Cast(MIDAS_TOUCH, DISINTEGRATE))
        // Greater Evocation: "Choose and cast one … — • "Burning Hands" • "Fireball" • "Sunburst""
        r.card("gydion-red-3a", Cast(BURNING_HANDS, FIREBALL, SUNBURST))
        // Greater Necromancy: "Choose and cast one … — • "Vampiric Touch" • "Create Undead" • "Energy Drain""
        r.card("gydion-red-3b", Cast(VAMPIRIC_TOUCH, CREATE_UNDEAD, ENERGY_DRAIN))
        // Greater Abjuration: "Choose and cast one … — • "Shield" • "Banishment" • "Invulnerability""
        r.card("gydion-green-3a", Cast(SHIELD, BANISHMENT, INVULNERABILITY))
        // Greater Conjuration: "Choose and cast one … — • "Find Familiar" • "Dimension Door" • "Cloud Kill""
        r.card("gydion-green-3b", Cast(FIND_FAMILIAR, DIMENSION_DOOR, CLOUD_KILL))
        // Greater Enchantment: "Choose and cast one … — • "Suggestion" • "Dominate Person" • "Power Word Kill""
        r.card("gydion-blue-3a", Cast(SUGGESTION, DOMINATE_PERSON, POWER_WORD_KILL))
        // Greater Transmutation: "Choose and cast one … — • "Midas Touch" • "Disintegrate" • "Polymorph""
        r.card("gydion-blue-3b", Cast(MIDAS_TOUCH, DISINTEGRATE, POLYMORPH))
        // The Archwizard (Ultimate): "Whenever you would add cards to the spellbook, you may cast the
        // "Wish" spell in the spellbook instead." (Applied in Prepare Spells, the only card that adds.)
        r.card(Spellbook.ULTIMATE, Archwizard)

        GydionSpells.register(r)
    }

    /** "Cast the X spell" (one) / "Choose and cast one spell in the spellbook —" (several bullets). */
    private class Cast(vararg spells: String) : CardScript {
        private val spells: List<String> = spells.toList()

        override fun primary(a: ActionScope) = Spellbook.castOneOf(a, spells)
    }

    private object PrepareSpells : CardScript {
        override fun primary(a: ActionScope) {
            // The spell cards are Gydion's whoever performs this (NebKher's Mind Grip, BGG 3487018).
            val owner = Spellbook.owner(a.g, WISH) ?: a.fizzle("no spellbook")
            // The Archwizard: "you" is Gydion; only while Wish is in the spellbook and can be cast.
            val ultimate = owner.id == a.heroId && owner.card(Spellbook.ULTIMATE)?.zone == Zone.ULTIMATE
            if (ultimate && Spellbook.castable(a.g, a.heroId, WISH) &&
                a.confirm("The Archwizard: cast Wish instead of adding the spell cards?", decline = "Add the spells")
            ) {
                Spellbook.cast(a.g, a.heroId, WISH)
                return
            }
            Spellbook.addAll(a.g, owner)
        }
    }

    /** The Ultimate has no action of its own. */
    private object Archwizard : CardScript {
        override val hasPrimary: Boolean get() = false
    }
}
