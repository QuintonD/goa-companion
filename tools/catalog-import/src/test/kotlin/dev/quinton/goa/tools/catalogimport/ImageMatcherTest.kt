package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageMatcherTest {
    private fun f(vararg v: Float) = Feature(v)

    @Test
    fun `assignment picks globally closest pairs one to one`() {
        val arts = mapOf("Gold" to f(0f, 0f), "Silver" to f(10f, 10f), "RedIA" to f(20f, 0f))
        val scans = mapOf("s1" to f(19f, 1f), "s2" to f(1f, 0f), "s3" to f(10f, 9f), "back" to f(50f, 50f))
        val matches = ImageMatcher.assign(arts, scans).associate { it.art to it.scan }
        assertThat(matches).containsExactly("Gold", "s2", "Silver", "s3", "RedIA", "s1")
    }

    @Test
    fun `confidence is high for a clear match`() {
        val m = ImageMatcher.assign(mapOf("A" to f(0f)), mapOf("x" to f(1f), "y" to f(10f))).single()
        assertThat(m.confidence).isWithin(1e-9).of(10.0)
    }

    @Test
    fun `twins are swapped when the handicap scan carries the basic badge`() {
        val emblem = f(0f, 10f, 0f, 10f)
        val letterH = f(10f, 0f, 10f, 0f)
        // Scan assigned to the basic card shows an H, the one assigned to the handicap shows the emblem.
        assertThat(
            ImageMatcher.shouldSwapTwins(
                basicScanBadge = letterH,
                handicapScanBadge = emblem,
                otherBasicBadge = emblem,
            ),
        ).isTrue()
        assertThat(
            ImageMatcher.shouldSwapTwins(
                basicScanBadge = emblem,
                handicapScanBadge = letterH,
                otherBasicBadge = emblem,
            ),
        ).isFalse()
    }

    @Test
    fun `standardized features ignore brightness`() {
        val a = f(1f, 2f, 3f).standardized()
        val b = f(101f, 102f, 103f).standardized()
        assertThat(a.distance(b)).isWithin(1e-6).of(0.0)
    }

    @Test
    fun `same art is judged relative to the spread of all arts`() {
        val all = listOf(f(0f), f(100f), f(200f), f(1f))
        assertThat(ImageMatcher.sameArt(all[0], all[3], all)).isTrue()
        assertThat(ImageMatcher.sameArt(all[0], all[1], all)).isFalse()
    }
}
