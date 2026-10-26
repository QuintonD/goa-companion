package dev.quinton.goa.web.data

/**
 * The small copies of official images the site carries for lists (ADR-043 "Addendum (R3 first load)",
 * made by `:tools:catalog-import:webThumbnails`): a card scan 384 pixels wide at
 * `official/thumbs/cards/<id>.webp`, a deck symbol 96 pixels at `official/thumbs/heroes/<id>/symbol.webp`.
 * An `<img>` offers both through `srcset`, so the browser takes the small copy when it is sharp enough
 * for the size on screen and the full image otherwise. The copies are an online speed-up only: "Download
 * everything" stores the full images, and an image whose copy can't load falls back to the full one.
 */
object Thumbnails {
    const val CARD_WIDTH = 384
    const val SYMBOL_WIDTH = 96
    const val ROOT = "official/thumbs/"

    // The full images' widths: upright card scans, Gydion's landscape spells, deck symbols.
    private const val CARD_FULL_WIDTH = 546
    private const val SPELL_FULL_WIDTH = 737
    private const val SYMBOL_FULL_WIDTH = 250

    /** The small copy of [path], or null when the site has none (portraits, board art, icons). */
    fun of(path: String): String? = when {
        path.startsWith("official/cards/") -> ROOT + path.removePrefix("official/")
        path.startsWith("official/heroes/") && path.endsWith("/symbol.webp") -> ROOT + path.removePrefix("official/")
        else -> null
    }

    /** True for a path under [ROOT]. */
    fun isThumbnail(path: String): Boolean = path.startsWith(ROOT)

    /** `srcset` for a card scan at [image] (a landscape spell when [spell]), or null without a copy. */
    fun cardSrcset(image: String, spell: Boolean): String? =
        of(image)?.let { "$it ${CARD_WIDTH}w, $image ${if (spell) SPELL_FULL_WIDTH else CARD_FULL_WIDTH}w" }

    /** `srcset` for a deck symbol at [symbol], or null without a copy. */
    fun symbolSrcset(symbol: String): String? =
        of(symbol)?.let { "$it ${SYMBOL_WIDTH}w, $symbol ${SYMBOL_FULL_WIDTH}w" }
}
