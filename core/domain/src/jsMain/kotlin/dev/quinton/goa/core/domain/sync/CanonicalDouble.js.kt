package dev.quinton.goa.core.domain.sync

import kotlin.math.abs

private const val PLAIN_MIN = 1e-3
private const val PLAIN_MAX = 1e7

/**
 * Java's layout around JavaScript's digits: both pick the shortest digits that read back as
 * [value] (ECMA-262 Number::toExponential, JDK 19+ Double.toString), so only the layout differs.
 */
actual fun jvmDoubleString(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    if (value == 0.0) return if (1.0 / value < 0) "-0.0" else "0.0"
    val sign = if (value < 0) "-" else ""
    // "d.ddde+x": the shortest round-trip digits and the decimal exponent. Java never writes fewer
    // than two digits: when one would do, it takes the two-digit decimal nearest the value (4.9E-324).
    var exponential = abs(value).asDynamic().toExponential() as String
    if ('.' !in exponential) exponential = abs(value).asDynamic().toExponential(1) as String
    val exponent = exponential.substringAfter('e').toInt()
    val digits = exponential.substringBefore('e').replace(".", "").trimEnd('0').ifEmpty { "0" }
    val magnitude = abs(value)
    return sign + if (magnitude >= PLAIN_MIN && magnitude < PLAIN_MAX) {
        if (exponent >= 0) {
            val whole = digits.take(exponent + 1).padEnd(exponent + 1, '0')
            val fraction = digits.drop(exponent + 1).ifEmpty { "0" }
            "$whole.$fraction"
        } else {
            "0." + "0".repeat(-exponent - 1) + digits
        }
    } else {
        "${digits.first()}.${digits.drop(1).ifEmpty { "0" }}E$exponent"
    }
}
