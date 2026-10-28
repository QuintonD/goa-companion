package dev.quinton.goa.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class Float32Test {
    /** The JVM's float operations, on every platform (Kotlin/JS would keep these in double precision). */
    @Test
    fun roundsToTheNearestFloat() {
        assertEquals(0.10000000149011612, f32(0.1))
        assertEquals(16777216.0, f32(16777217.0)) // 2^24 + 1 ties to even
        assertEquals(1.0, f32(1.0))
        assertEquals(-0.0, f32(-0.0))
    }

    /** A float sum done in Double and rounded once is the float sum (the learned net's inner loop). */
    @Test
    fun aFloatSumRoundedOnceIsTheFloatSum() {
        val a = f32(1.0e8)
        val b = f32(3.25)
        assertEquals(100000000.0, f32(a + b))
        assertEquals(f32(0.3), f32(f32(0.1) + f32(0.2)))
    }
}
