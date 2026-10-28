package dev.quinton.goa.core.domain

@Suppress("UnusedParameter") // read by the js() call
actual fun f32(x: Double): Double = js("Math.fround(x)").unsafeCast<Double>()
