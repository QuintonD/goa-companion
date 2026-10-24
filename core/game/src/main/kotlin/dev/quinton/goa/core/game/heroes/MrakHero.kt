package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry

/**
 * Mrak the Rockshaper: not implemented yet. The hero agent writes every card here (EFFECTS.md
 * "Authoring a hero") and sets [complete] once each card has a clause-by-clause test.
 */
object MrakHero : HeroModule {
    override val heroId: String = "mrak"
    override val complete: Boolean = false

    override fun register(r: Registry) = Unit
}
