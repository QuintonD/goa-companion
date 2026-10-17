package dev.quinton.goa.tools.catalogimport

import kotlin.math.sqrt

/** A small fixed-size pixel signature of an image region. */
class Feature(val values: FloatArray) {
    fun distance(other: Feature): Double {
        require(values.size == other.values.size) { "Feature size mismatch" }
        var sum = 0.0
        for (i in values.indices) {
            val d = (values[i] - other.values[i]).toDouble()
            sum += d * d
        }
        return sqrt(sum)
    }

    /** Zero mean, unit variance: compares shapes, not brightness or frame colour. */
    fun standardized(): Feature {
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).takeIf { it > 1e-6 } ?: 1.0
        return Feature(FloatArray(values.size) { ((values[it] - mean) / sd).toFloat() })
    }
}

/**
 * Identifies which card scan (source 2) shows which card by comparing the artwork region of each
 * scan with the card artwork published by source 1 (same illustration, no text overlays).
 */
object ImageMatcher {
    data class Match(
        val art: String,
        val scan: String,
        val distance: Double,
        val confidence: Double,
        val method: String,
    )

    /** Region of a card that is mostly illustration (right of the value banner, above the text box). */
    val ART_BOX = floatArrayOf(0.30f, 0.13f, 0.95f, 0.55f)

    /** Top-right badge: tier numeral, `H` for Handicap cards, an emblem on basic cards. */
    val BADGE_BOX = floatArrayOf(0.80f, 0.02f, 0.97f, 0.12f)

    /**
     * Greedy global assignment: repeatedly takes the closest unassigned (art, scan) pair.
     * Confidence = distance to the art's second-best scan ÷ distance to the chosen one.
     */
    fun assign(arts: Map<String, Feature>, scans: Map<String, Feature>): List<Match> {
        val pairs = arts.flatMap { (a, fa) ->
            scans.map { (s, fs) -> Triple(a, s, fa.distance(fs)) }
        }.sortedBy { it.third }
        val usedArts = mutableSetOf<String>()
        val usedScans = mutableSetOf<String>()
        val out = mutableListOf<Match>()
        for ((a, s, d) in pairs) {
            if (a in usedArts || s in usedScans) continue
            usedArts += a
            usedScans += s
            val second = pairs.filter { it.first == a && it.second != s }.minOfOrNull { it.third } ?: Double.MAX_VALUE
            out += Match(a, s, d, if (d <= 1e-9) Double.MAX_VALUE else second / d, "art")
        }
        return out
    }

    /**
     * A Handicap card often reuses its basic twin's illustration, so the art cannot tell them
     * apart. The basic card shows the same emblem badge as the other basic card (gold ↔ silver),
     * while the Handicap card shows an `H`. Returns true when the two scans must be swapped.
     */
    fun shouldSwapTwins(basicScanBadge: Feature, handicapScanBadge: Feature, otherBasicBadge: Feature): Boolean {
        val ref = otherBasicBadge.standardized()
        return handicapScanBadge.standardized().distance(ref) < basicScanBadge.standardized().distance(ref)
    }

    /** True when two artworks are near-identical relative to the typical distance between arts. */
    fun sameArt(a: Feature, b: Feature, allArts: Collection<Feature>): Boolean {
        val list = allArts.toList()
        val distances = list.indices.flatMap { i ->
            (i + 1 until list.size).map { j -> list[i].distance(list[j]) }
        }.sorted()
        if (distances.isEmpty()) return false
        val median = distances[distances.size / 2]
        return a.distance(b) < 0.3 * median
    }
}
