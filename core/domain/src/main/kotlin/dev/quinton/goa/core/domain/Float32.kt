package dev.quinton.goa.core.domain

/**
 * [x] rounded to the nearest 32-bit float, as a Double. Kotlin/JS computes `Float` arithmetic in double
 * precision (a `Float` is a JS number), so float code that must give the same bits on the phone and in
 * the browser (the learned evaluation, ADR-047) does each operation in Double and rounds it with this:
 * one `+`, `-` or `*` of two floats done in Double and rounded once equals the JVM's float operation.
 */
expect fun f32(x: Double): Double
