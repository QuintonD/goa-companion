package dev.quinton.goa.core.game.heroes

import dev.quinton.goa.core.game.effects.HeroModule
import dev.quinton.goa.core.game.effects.Registry

/**
 * Every hero module, one file each in this package (EFFECTS.md). Hero agents edit only their own
 * file; this list already names all 32 heroes.
 */
object Heroes {
    val all: List<HeroModule> = listOf(
        ArienHero,
        BainHero,
        BroganHero,
        BrynnHero,
        CutterHero,
        DodgerHero,
        EmmittHero,
        GarrusHero,
        GydionHero,
        HanuHero,
        IgnatiaHero,
        MinHero,
        MisaHero,
        MortimerHero,
        MrakHero,
        NebkherHero,
        RazzleHero,
        RowennaHero,
        SabinaHero,
        SilverarrowHero,
        SnorriHero,
        SwiftHero,
        TakahideHero,
        TaliHero,
        TigerclawHero,
        TrinketsHero,
        UrsafarHero,
        WaspHero,
        WhisperHero,
        WidgetHero,
        WukHero,
        XargathaHero,
    )

    /** A registry with every module registered (built once per engine). */
    fun registry(): Registry = Registry().also { r -> all.forEach(r::module) }
}
