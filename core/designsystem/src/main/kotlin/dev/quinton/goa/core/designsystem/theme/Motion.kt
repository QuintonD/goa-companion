package dev.quinton.goa.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Motion tokens (docs/DESIGN.md): 200–300 ms, Material emphasised easing. */
object Motion {
    const val SHORT_MS = 200
    const val MEDIUM_MS = 250
    const val LONG_MS = 300

    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> = tween(durationMs, easing = Emphasized)
}
