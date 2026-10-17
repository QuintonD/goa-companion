package dev.quinton.goa.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.model.settings.ThemeMode

private val GoaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GoaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalGoaColors provides if (dark) DarkExtended else LightExtended,
        LocalGoaIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = GoaTypography,
            shapes = GoaShapes,
            content = content,
        )
    }
}

/** Access point for tokens outside the Material scheme: `GoaTheme.colors.teamRed`. */
object GoaTheme {
    val colors: GoaExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGoaColors.current

    /** True when the dark scheme is active (resolved from [ThemeMode]). */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGoaIsDark.current
}

internal val LocalGoaIsDark = staticCompositionLocalOf { true }

/** 4 dp spacing grid (docs/DESIGN.md). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp
}
