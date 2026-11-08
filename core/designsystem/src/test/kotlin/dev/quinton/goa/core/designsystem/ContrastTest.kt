package dev.quinton.goa.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.designsystem.component.ControlSteel
import dev.quinton.goa.core.designsystem.component.DisabledFace
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.SteelIconBronze
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

    /** The rules reader's page in each theme: the passage, its reminder words and the printed heads on it. */
    @Test fun readerPage() = themes().forEach { (theme, _, x) ->
        check("$theme onPage", x.onPage, x.page, TEXT)
        check("$theme onPageVariant", x.onPageVariant, x.page, TEXT)
        check("$theme pageInk (heads)", x.pageInk, x.page, TEXT)
    }

    @Test fun bodyText() = themes().forEach { (theme, scheme, _) ->
        listOf(scheme.background, scheme.surface, scheme.surfaceVariant).forEach { bg ->
            check("$theme onSurface", scheme.onSurface, bg, TEXT)
            check("$theme onSurfaceVariant", scheme.onSurfaceVariant, bg, TEXT)
        }
    }

    /**
     * Secondary text is read in dim light at arm's length: onSurfaceVariant is ≥ 7:1 (WCAG AAA) on the canvas, the
     * surfaces, the plate and its well, and a steel chip.
     */
    @Test fun secondaryTextIsAaa() {
        themes().forEach { (theme, scheme, x) ->
            listOf(
                "background" to scheme.background,
                "surface" to scheme.surface,
                "surfaceVariant" to scheme.surfaceVariant,
                "plate" to x.plate,
                "well" to x.plateWell,
            ).forEach { (name, bg) -> check("$theme onSurfaceVariant on $name", scheme.onSurfaceVariant, bg, AAA) }
        }
        check("dark onSurfaceVariant on a steel chip", DarkColors.onSurfaceVariant, ControlSteel.Dark.face, AAA)
    }

    /**
     * Team words (a team's name, an actor heading) are as legible as body text: ≥ 6:1 on the plate, ≥ 4.5:1 on every
     * other ground of the theme, a picked choice's tint included. Team marks (the gear, the star, an accent bar, a
     * ring) and bronze marks are ≥ 3:1 on every ground, cream included.
     */
    @Test fun teamWordsAndMarks() = themes().forEach { (theme, scheme, x) ->
        val picked = x.selectionFill.compositeOver(x.plate)
        val grounds = listOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "plate" to x.plate,
            "well" to x.plateWell,
            "picked" to picked,
        )
        listOf("red" to x.teamRedText, "blue" to x.teamBlueText).forEach { (team, words) ->
            check("$theme $team team words on plate", words, x.plate, TEAM_WORDS)
            grounds.forEach { (name, bg) -> check("$theme $team team words on $name", words, bg, TEXT) }
        }
        listOf("red" to x.teamRed, "blue" to x.teamBlue, "bronze" to x.bronzeInk).forEach { (mark, color) ->
            grounds.forEach { (name, bg) -> check("$theme $mark mark on $name", color, bg, GRAPHIC) }
        }
    }

    /** The light theme's paper plate and its well, and a picked choice's bronze tint over either theme's plate. */
    @Test fun textOnPlates() = themes().forEach { (theme, scheme, x) ->
        val picked = x.selectionFill.compositeOver(x.plate)
        listOf("plate" to x.plate, "well" to x.plateWell, "picked" to picked).forEach { (name, bg) ->
            check("$theme onSurface on $name", scheme.onSurface, bg, TEXT)
            check("$theme onSurfaceVariant on $name", scheme.onSurfaceVariant, bg, TEXT)
            check("$theme primary on $name", scheme.primary, bg, TEXT)
            check("$theme heading on $name", x.heading, bg, TEXT)
            check("$theme secondary on $name", scheme.secondary, bg, TEXT)
        }
        check("$theme selection edge", x.selection, x.plate, GRAPHIC)
    }

    /**
     * A disabled button: its words muted but legible (text contrast) on its filled face, its edge visible on the
     * canvas around it.
     */
    @Test fun disabledButtons() {
        listOf(
            Triple("dark", DisabledFace.Dark, DarkColors.background),
            Triple("light", DisabledFace.Light, LightColors.background),
        ).forEach { (theme, face, canvas) ->
            check("$theme disabled words", face.ink, face.fill, TEXT)
            check("$theme disabled edge", face.edge, canvas, DISABLED_EDGE)
        }
    }

    /** Glyphs that carry meaning: unfilled complexity stars, a bronze glyph on a steel face. */
    @Test fun meaningfulGlyphs() {
        themes().forEach { (theme, scheme, x) ->
            listOf(scheme.background, x.plate, scheme.surface).forEach { bg ->
                check("$theme glyphMuted", x.glyphMuted, bg, GRAPHIC)
            }
        }
        listOf(Color(0xFF34424B), Color(0xFF222C33)).forEach { steel ->
            check("bronze glyph on steel", SteelIconBronze, steel, GRAPHIC)
        }
    }

    /** Bronze that carries meaning (a star, a check, a symbol): words-strong on every ground of its theme. */
    @Test fun bronzeInk() = themes().forEach { (theme, scheme, x) ->
        listOf(scheme.background, x.plate, x.plateWell, scheme.surface).forEach { bg ->
            check("$theme bronzeInk", x.bronzeInk, bg, TEXT)
        }
    }

    /**
     * The game's controls: the steel edge of a chip, a segmented bar and a switch's off track, and the switch's bronze
     * on track, ≥ 3:1 on the grounds they sit on; words on a segmented bar and on its picked segment.
     */
    @Test fun controls() {
        listOf(
            Triple("dark", ControlSteel.Dark, DarkExtended to DarkColors),
            Triple("light", ControlSteel.Light, LightExtended to LightColors),
        ).forEach { (theme, steel, pair) ->
            val (x, scheme) = pair
            listOf(scheme.background, x.plate).forEach { bg ->
                check("$theme steel edge (lit)", steel.lit, bg, GRAPHIC)
                check("$theme steel edge (shade)", steel.shade, bg, GRAPHIC)
                check("$theme switch on track", x.selection, bg, GRAPHIC)
            }
            check("$theme words on a segmented bar", scheme.onSurface, steel.recess, TEXT)
            check("$theme words on a picked segment", scheme.onSurface, x.selectionFill.compositeOver(steel.face), TEXT)
        }
    }

    /**
     * Muted states (a done turn, a past step, an empty slot) step back by losing decoration, not legibility: their
     * words ≥ 4.5:1 (5:1 on the plate and well, where they mostly sit) and their edges ≥ 3:1 on every ground of the
     * theme, a picked tint and, in the dark theme, a steel chip included.
     */
    @Test fun mutedStates() {
        themes().forEach { (theme, scheme, x) ->
            listOf(
                "background" to scheme.background,
                "surface" to scheme.surface,
                "surfaceVariant" to scheme.surfaceVariant,
                "plate" to x.plate,
                "well" to x.plateWell,
                "picked" to x.selectionFill.compositeOver(x.plate),
            ).forEach { (name, bg) ->
                check("$theme textMuted on $name", x.textMuted, bg, TEXT)
                check("$theme outlineMuted on $name", x.outlineMuted, bg, GRAPHIC)
            }
            check("$theme textMuted on plate", x.textMuted, x.plate, MUTED_ON_PLATE)
            check("$theme textMuted on well", x.textMuted, x.plateWell, MUTED_ON_PLATE)
            // The table's well is the plate darkened (dark) or the paper a shade deeper (light).
            val tableWell = if (theme == "dark") Color.Black.copy(alpha = 0.26f) else Color(0x1F6A5634)
            check("$theme textMuted on the table's well", x.textMuted, tableWell.compositeOver(x.plate), TEXT)
            check("$theme outlineMuted on the table's well", x.outlineMuted, tableWell.compositeOver(x.plate), GRAPHIC)
        }
        check("dark textMuted on a steel chip", DarkExtended.textMuted, ControlSteel.Dark.face, TEXT)
    }

    @Test fun everyOfficialGraphicTheAppNamesIsBundled() {
        OfficialArt.all.forEach { path -> assertWithMessage(path).that(bundledAsset(path).isFile).isTrue() }
    }

    private companion object {
        const val TEXT = 4.5
        const val TEAM_WORDS = 6.0
        const val AAA = 7.0
        const val GRAPHIC = 3.0
        const val DISABLED_EDGE = 1.6
        const val MUTED_ON_PLATE = 5.0
    }
}
