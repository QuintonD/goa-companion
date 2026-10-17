package dev.quinton.goa.core.designsystem.icon

import androidx.annotation.DrawableRes
import dev.quinton.goa.core.designsystem.R

/*
 * Original vector artwork generated from design/ by design/tools/svg2vd.py.
 * Masters: design/sigils/<id>.svg, design/avatars/crest_NN.svg, design/icons/<name>.svg.
 */

/** Hero id → sigil drawable. Unknown ids (new heroes, imported backups) fall back to a monogram. */
object HeroSigils {
    private val byId: Map<String, Int> = mapOf(
        "arien" to R.drawable.sigil_arien,
        "brogan" to R.drawable.sigil_brogan,
        "tigerclaw" to R.drawable.sigil_tigerclaw,
        "wasp" to R.drawable.sigil_wasp,
        "sabina" to R.drawable.sigil_sabina,
        "xargatha" to R.drawable.sigil_xargatha,
        "dodger" to R.drawable.sigil_dodger,
        "misa" to R.drawable.sigil_misa,
        "whisper" to R.drawable.sigil_whisper,
        "ursafar" to R.drawable.sigil_ursafar,
        "silverarrow" to R.drawable.sigil_silverarrow,
        "tali" to R.drawable.sigil_tali,
        "garrus" to R.drawable.sigil_garrus,
        "bain" to R.drawable.sigil_bain,
        "cutter" to R.drawable.sigil_cutter,
        "trinkets" to R.drawable.sigil_trinkets,
        "nebkher" to R.drawable.sigil_nebkher,
        "min" to R.drawable.sigil_min,
        "swift" to R.drawable.sigil_swift,
        "wuk" to R.drawable.sigil_wuk,
        "hanu" to R.drawable.sigil_hanu,
        "ignatia" to R.drawable.sigil_ignatia,
        "rowenna" to R.drawable.sigil_rowenna,
        "mrak" to R.drawable.sigil_mrak,
        "snorri" to R.drawable.sigil_snorri,
        "razzle" to R.drawable.sigil_razzle,
        "gydion" to R.drawable.sigil_gydion,
        "brynn" to R.drawable.sigil_brynn,
        "mortimer" to R.drawable.sigil_mortimer,
        "widget" to R.drawable.sigil_widget,
        "takahide" to R.drawable.sigil_takahide,
        "emmitt" to R.drawable.sigil_emmitt,
    )

    /** Every hero id that has a hand-drawn sigil. */
    val knownIds: Set<String> get() = byId.keys

    /** Tolerant lookup: case, `-`, `_` and spaces are ignored ("Neb-Kher" → nebkher). */
    @DrawableRes
    fun drawableFor(heroId: String): Int? = byId[normalize(heroId)]

    internal fun normalize(heroId: String): String = heroId.lowercase().filter { it.isLetterOrDigit() }
}

/** The 24 original player crests; `Player.avatarId` indexes into [all]. */
object PlayerCrests {
    val all: List<Int> = listOf(
        R.drawable.crest_00, R.drawable.crest_01, R.drawable.crest_02, R.drawable.crest_03,
        R.drawable.crest_04, R.drawable.crest_05, R.drawable.crest_06, R.drawable.crest_07,
        R.drawable.crest_08, R.drawable.crest_09, R.drawable.crest_10, R.drawable.crest_11,
        R.drawable.crest_12, R.drawable.crest_13, R.drawable.crest_14, R.drawable.crest_15,
        R.drawable.crest_16, R.drawable.crest_17, R.drawable.crest_18, R.drawable.crest_19,
        R.drawable.crest_20, R.drawable.crest_21, R.drawable.crest_22, R.drawable.crest_23,
    )

    /** Heraldic names, for the avatar picker's content descriptions. */
    val names: List<String> = listOf(
        "Double chevron", "Lozenge", "Three bezants", "Four-pointed star", "Key", "Crescent",
        "Sun", "Waves", "Mount", "Cross pattée", "Saltire", "Tower",
        "Crown", "Anchor", "Leaf", "Hourglass", "Thunderbolt", "Quatrefoil",
        "Three rings", "Arrowhead", "Hexagon", "Pall", "Chequy", "Frost star",
    )

    val count: Int get() = all.size

    @DrawableRes
    fun drawableFor(avatarId: Int): Int? = all.getOrNull(avatarId)
}

/** UI icons not covered by Material Symbols. Selected variants are solid. */
object GoaIcons {
    /** Full-colour shield-and-trident mark (draw untinted). */
    @DrawableRes val BrandMark = R.drawable.ic_brand_mark

    @DrawableRes val NavHeroes = R.drawable.ic_nav_heroes

    @DrawableRes val NavHeroesSelected = R.drawable.ic_nav_heroes_filled

    @DrawableRes val NavTimer = R.drawable.ic_nav_timer

    @DrawableRes val NavTimerSelected = R.drawable.ic_nav_timer_filled

    @DrawableRes val NavDraft = R.drawable.ic_nav_draft

    @DrawableRes val NavDraftSelected = R.drawable.ic_nav_draft_filled

    @DrawableRes val NavPlayers = R.drawable.ic_nav_players

    @DrawableRes val NavPlayersSelected = R.drawable.ic_nav_players_filled
}
