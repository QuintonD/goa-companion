package dev.quinton.goa.web.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThumbnailsTest {
    @Test
    fun cardScansAndDeckSymbolsHaveSmallCopiesUnderThumbs() {
        assertEquals("official/thumbs/cards/arien-gold.webp", Thumbnails.of("official/cards/arien-gold.webp"))
        assertEquals("official/thumbs/heroes/arien/symbol.webp", Thumbnails.of("official/heroes/arien/symbol.webp"))
        // The hero art is shown large (grid tiles, the hero page): no copy.
        assertNull(Thumbnails.of("official/heroes/arien/portrait.webp"))
        assertNull(Thumbnails.of("official/board/tie-breaker-red.webp"))
        assertTrue(Thumbnails.isThumbnail("official/thumbs/cards/arien-gold.webp"))
        assertFalse(Thumbnails.isThumbnail("official/cards/arien-gold.webp"))
    }

    @Test
    fun theSrcsetOffersTheCopyAndTheFullImageWithTheirWidths() {
        assertEquals(
            "official/thumbs/cards/arien-gold.webp 384w, official/cards/arien-gold.webp 546w",
            Thumbnails.cardSrcset("official/cards/arien-gold.webp", spell = false),
        )
        assertEquals(
            "official/thumbs/cards/gydion-spell-banishment.webp 384w, official/cards/gydion-spell-banishment.webp 737w",
            Thumbnails.cardSrcset("official/cards/gydion-spell-banishment.webp", spell = true),
        )
        assertEquals(
            "official/thumbs/heroes/arien/symbol.webp 96w, official/heroes/arien/symbol.webp 250w",
            Thumbnails.symbolSrcset("official/heroes/arien/symbol.webp"),
        )
    }
}
