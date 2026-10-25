package dev.quinton.goa.core.domain

import kotlin.math.abs
import kotlin.math.floor

/**
 * Elementary functions that give the same bits on every platform (ADR-043). `kotlin.math.exp` and
 * `pow` call the platform's library: the JVM's intrinsics, Android's bionic, V8 and Safari's own
 * libm differ in the last bit now and then, so the same match log could replay to ratings that
 * differ in the last digit on two phones or in the browser (the JVM and Node did, in
 * `DomainParityTest`). These use only `+ - * /`, correctly rounded everywhere: [exp] is fdlibm's
 * `e_exp.c`, bit for bit `java.lang.StrictMath.exp` (`PortableMathTest`).
 */
object PortableMath {
    private val HALF = doubleArrayOf(0.5, -0.5)
    private val HUGE = bits(0x7e37e43c8800759cuL) // 1.0e300
    private val TWO_M1000 = bits(0x0170000000000000uL) // 2^-1000
    private val O_THRESHOLD = bits(0x40862E42FEFA39EFuL)
    private val U_THRESHOLD = bits(0xc0874910D52D3051uL)
    private val LN2_HI = doubleArrayOf(bits(0x3fe62e42fee00000uL), bits(0xbfe62e42fee00000uL))
    private val LN2_LO = doubleArrayOf(bits(0x3dea39ef35793c76uL), bits(0xbdea39ef35793c76uL))
    private val INV_LN2 = bits(0x3ff71547652b82feuL)
    private val P1 = bits(0x3FC555555555553EuL)
    private val P2 = bits(0xBF66C16C16BEBD93uL)
    private val P3 = bits(0x3F11566AAF25DE2CuL)
    private val P4 = bits(0xBEBBBD41C5D26BF1uL)
    private val P5 = bits(0x3E66376972BEA4D0uL)

    private fun bits(value: ULong) = Double.fromBits(value.toLong())

    /** ln 10, the Double nearest it. */
    private const val LN10 = 2.302585092994046

    /** e^[x] (fdlibm `__ieee754_exp`). */
    @Suppress("CyclomaticComplexMethod", "ReturnCount", "MagicNumber")
    fun exp(x: Double): Double {
        val bits = x.toRawBits()
        var hx = (bits ushr 32).toInt()
        val xsb = (hx ushr 31) and 1
        hx = hx and 0x7fffffff
        if (hx >= 0x40862E42) {
            if (hx >= 0x7ff00000) {
                return if (((hx and 0xfffff) or bits.toInt()) != 0) {
                    x + x
                } else if (xsb == 0) {
                    x
                } else {
                    0.0
                }
            }
            if (x > O_THRESHOLD) return HUGE * HUGE
            if (x < U_THRESHOLD) return TWO_M1000 * TWO_M1000
        }
        var r = x
        var hi = 0.0
        var lo = 0.0
        var k = 0
        if (hx > 0x3fd62e42) {
            if (hx < 0x3FF0A2B2) {
                hi = r - LN2_HI[xsb]
                lo = LN2_LO[xsb]
                k = 1 - xsb - xsb
            } else {
                k = (INV_LN2 * r + HALF[xsb]).toInt()
                val t = k.toDouble()
                hi = r - t * LN2_HI[0]
                lo = t * LN2_LO[0]
            }
            r = hi - lo
        } else if (hx < 0x3e300000) {
            if (HUGE + r > 1.0) return 1.0 + r
        }
        val t = r * r
        val c = r - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))))
        if (k == 0) return 1.0 - ((r * c) / (c - 2.0) - r)
        val y = 1.0 - ((lo - (r * c) / (2.0 - c)) - hi)
        return if (k >= -1021) {
            Double.fromBits(y.toRawBits() + (k.toLong() shl 52))
        } else {
            Double.fromBits(y.toRawBits() + ((k + 1000).toLong() shl 52)) * TWO_M1000
        }
    }

    /** 10^0 .. 10^22, each exact in a Double. */
    private val EXACT_POWERS = DoubleArray(MAX_EXACT_POWER + 1).also { p ->
        p[0] = 1.0
        for (i in 1..MAX_EXACT_POWER) p[i] = p[i - 1] * 10.0
    }

    private const val MAX_EXACT_POWER = 22

    /**
     * 10^[x], the same bits everywhere and within a few ulps: 10^k × e^((x − k) ln 10) with k the
     * nearest integer, so the exact power 10^k carries the size and `exp` only a factor below √10
     * (e^(x ln 10) alone would lose about |x| × 2.3 ulps to the rounding of x ln 10).
     */
    fun pow10(x: Double): Double {
        val k = floor(x + 0.5)
        if (x.isNaN() || abs(k) > MAX_EXACT_POWER) return exp(x * LN10)
        val f = exp((x - k) * LN10)
        val power = EXACT_POWERS[abs(k).toInt()]
        return if (k >= 0) f * power else f / power
    }
}
