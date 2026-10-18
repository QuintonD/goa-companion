package dev.quinton.goa.core.presentation.settings

/** Settings → About: the credits the phone's About screen and the hub's `/settings/about` show. */
object AboutCredits {
    const val GAME_CONTENT = "Guards of Atlantis II, its heroes, card text, card images and artwork are by " +
        "Wolff Designa. They appear here for private, non-commercial play."

    const val NOT_AFFILIATED = "Not affiliated with or endorsed by Wolff Designa."

    const val DATA_SOURCES_INTRO =
        "Thanks to these community projects, whose published data helped build and check the catalog:"

    /** Community projects whose published data informed the catalog import (credit only, no code reused). */
    val dataSources: List<Pair<String, String>> = listOf(
        "Stats-of-Atlantis (QuasiStellar)" to "Card data, hero details and artwork",
        "goa2-faq (yvo-niedrich)" to "Card text cross-check and FAQ rulings",
        "goa2 (dmrediger)" to "Full card images",
        "guards-of-atlantis-ii-data (colefin8)" to "Card values cross-check",
    )

    const val ORIGINAL_ARTWORK = "Hero sigils, player crests, icons and the app icon were drawn for this app."
}
