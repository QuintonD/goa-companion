package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.PortableMath
import kotlin.test.Test
import kotlin.test.assertEquals

/** PortableMath gives the JVM's bits in JavaScript too (vectors: java.lang.StrictMath.exp). */
class PortableMathVectorsTest {
    @Test
    fun expGivesTheSameBitsEverywhere() {
        var transcript = ""
        var x = -40.0
        while (x <= 40.0) {
            transcript += "${PortableMath.exp(x).toRawBits()} ${PortableMath.pow10(x / 4).toRawBits()}\n"
            x += 0.37
        }
        assertTranscript(
            "e7cbb5ddcd855da246f1756f57b58ea4ef0e581dfee595b8bcaed1d56989fa38",
            transcript,
            "portable math",
        )
        assertEquals(1.0, PortableMath.exp(0.0))
        assertEquals(10.0, PortableMath.pow10(1.0), 1e-14)
    }
}
