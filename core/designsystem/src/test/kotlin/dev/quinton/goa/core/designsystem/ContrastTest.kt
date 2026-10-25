package dev.quinton.goa.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.theme.DarkColors
import dev.quinton.goa.core.designsystem.theme.DarkExtended
import dev.quinton.goa.core.designsystem.theme.GoaExtendedColors
import dev.quinton.goa.core.designsystem.theme.LightColors
import dev.quinton.goa.core.designsystem.theme.LightExtended
import dev.quinton.goa.core.testing.bundledAsset
import org.junit.Test

/**
 * The text/background pairs docs/DESIGN.md promises (WCAG 2 contrast ratio): text ≥ 4.5:1, icons
 * and other graphics ≥ 3:1. A token change that breaks one fails here, not on a phone at the table.
 */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        assertWithMessage("$name: ${"%.2f".format(ratio(fg, bg))}").that(ratio(fg, bg)).isAtLeast(min)
    }

    private fun themes(): List<Triple<String, androidx.compose.material3.ColorScheme, GoaExtendedColors>> = listOf(
        Triple("dark", DarkColors, DarkExtended),
        Triple("light", LightColors, LightExtended),
    )

    @Test fun bannerTextAndIcons() = themes().forEach { (theme, _, x) ->
        listOf(x.bannerTop, x.bannerBottom).forEach { stop ->
            check("$theme onBanner", x.onBanner, stop, TEXT)
            check("$theme onBannerVariant", x.onBannerVariant, stop, TEXT)
        }
    }

    @Test fun copperHeadings() = themes().forEach { (theme, scheme, x) ->
        check("$theme heading on background", x.heading, scheme.background, TEXT)
        check("$theme heading on surface", x.heading, scheme.surface, TEXT)
        check("$theme heading on surfaceVariant", x.heading, scheme.surfaceVariant, TEXT)
    }

    @Test fun parchment() = themes().forEach { (theme, _, x) ->
        check("$theme onParchment", x.onParchment, x.parchment, TEXT)
        check("$theme onParchmentVariant", x.onParchmentVariant, x.parchment, TEXT)
        check("$theme parchmentInk (icons)", x.parchmentInk, x.parchment, TEXT)
    }

    @Test fun bodyText() = themes().forEach { (theme, scheme, _) ->
        listOf(scheme.background, scheme.surface, scheme.surfaceVariant).forEach { bg ->
            check("$theme onSurface", scheme.onSurface, bg, TEXT)
            check("$theme onSurfaceVariant", scheme.onSurfaceVariant, bg, TEXT)
        }
    }

    @Test fun everyOfficialGraphicTheAppNamesIsBundled() {
        OfficialArt.all.forEach { path -> assertWithMessage(path).that(bundledAsset(path).isFile).isTrue() }
    }

    private companion object {
        const val TEXT = 4.5
    }
}
