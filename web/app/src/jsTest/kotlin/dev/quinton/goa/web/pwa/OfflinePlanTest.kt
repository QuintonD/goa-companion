package dev.quinton.goa.web.pwa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflinePlanTest {
    private val manifest = AssetManifest(
        build = "b",
        version = "0.6.0",
        files = listOf(
            AssetFile("index.html", 2_000, "h0"),
            AssetFile("catalog/index.json", 1_500, "h1"),
            AssetFile("official/SOURCES.md", 90_000, "h2"),
            AssetFile("official/cards/arien-gold.webp", 60_000, "h3"),
            AssetFile("official/heroes/arien/portrait.webp", 40_000, "h4"),
        ),
    )

    @Test
    fun theImagesAreEverythingUnderOfficialButItsSourcesFile() {
        assertEquals(
            listOf("official/cards/arien-gold.webp", "official/heroes/arien/portrait.webp"),
            OfflinePlan.images(manifest).map { it.p },
        )
    }

    @Test
    fun anImageCachedWithAnOlderHashIsDownloadedAgain() {
        val images = OfflinePlan.images(manifest)
        val cached = mapOf("official/cards/arien-gold.webp" to "old", "official/heroes/arien/portrait.webp" to "h4")
        assertEquals(listOf("official/cards/arien-gold.webp"), OfflinePlan.missing(images, cached).map { it.p })
        val status = OfflinePlan.status(images, cached)
        assertEquals(OfflineStatus(1, 2, 40_000, 100_000), status)
        assertFalse(status.complete)
        assertTrue(OfflinePlan.status(images, cached + ("official/cards/arien-gold.webp" to "h3")).complete)
    }

    @Test
    fun cacheKeysCarryPathAndHash() {
        assertEquals("official/cards/a b.webp?v=h3", OfflinePlan.imageKey("official/cards/a b.webp", "h3"))
        assertEquals("official/cards/a b.webp" to "h3", OfflinePlan.parseImageKey("official/cards/a%20b.webp?v=h3"))
        assertNull(OfflinePlan.parseImageKey("official/cards/a.webp"))
        assertNull(OfflinePlan.parseImageKey("official/cards/a.webp?v="))
    }

    @Test
    fun sizesReadLikeIosSettings() {
        assertEquals("0 B", OfflinePlan.formatBytes(0))
        assertEquals("999 B", OfflinePlan.formatBytes(999))
        assertEquals("860 KB", OfflinePlan.formatBytes(860_400))
        assertEquals("34.1 MB", OfflinePlan.formatBytes(34_123_456))
        assertEquals("999 KB", OfflinePlan.formatBytes(999_999))
        assertEquals("1.0 MB", OfflinePlan.formatBytes(1_000_000))
        assertEquals("2.5 GB", OfflinePlan.formatBytes(2_500_000_000))
    }

    @Test
    fun progressIsAFractionOfTheBytes() {
        assertEquals(0.25, DownloadProgress(1, 4, 25, 100).fraction)
        assertEquals(1.0, DownloadProgress(0, 0, 0, 0).fraction)
    }
}
