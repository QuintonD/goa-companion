package dev.quinton.goa.core.domain

import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

/** PortableMath against the JVM's fdlibm (StrictMath), bit for bit; its common twin pins vectors for JS. */
class PortableMathTest {
    @Test
    fun expIsStrictMathExpBitForBit() {
        val rng = Random(43)
        val specials = listOf(
            0.0, -0.0, 1.0, -1.0, 0.5, 0.34657359027997264, 1.0397207708399179, 709.782712893384, 709.79,
            -745.1332191019411, -745.14, -708.4, -1022.0 * 0.6931471805599453, 1e-300, -1e-300, 3.7252902984e-9,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE,
        )
        val randoms = List(400_000) {
            when (it % 4) {
                0 -> rng.nextDouble(-750.0, 750.0)
                1 -> rng.nextDouble(-2.0, 2.0)
                2 -> rng.nextDouble(-1e-6, 1e-6)
                else -> Double.fromBits(rng.nextLong())
            }
        }
        (specials + randoms).forEach { x ->
            assertWithMessage("exp($x)").that(PortableMath.exp(x).toRawBits()).isEqualTo(StrictMath.exp(x).toRawBits())
        }
    }

    @Test
    fun pow10IsAccurateForRatings() {
        val rng = Random(44)
        repeat(200_000) {
            val x = if (it % 2 == 0) rng.nextDouble(-3.0, 3.0) else rng.nextDouble(-22.0, 22.0)
            val expected = StrictMath.pow(10.0, x)
            assertWithMessage("10^$x").that(abs(PortableMath.pow10(x) - expected)).isAtMost(3 * Math.ulp(expected))
        }
    }
}
