package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import kotlin.math.roundToInt
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The 24 crests' heraldic names (core:designsystem `PlayerCrests.names`), in [Player.avatarId] order:
 * what a screen reader says for a crest choice.
 */
val CrestNames: List<String> = listOf(
    "Double chevron", "Lozenge", "Three bezants", "Four-pointed star", "Key", "Crescent",
    "Sun", "Waves", "Mount", "Cross pattée", "Saltire", "Tower",
    "Crown", "Anchor", "Leaf", "Hourglass", "Thunderbolt", "Quatrefoil",
    "Three rings", "Arrowhead", "Hexagon", "Pall", "Chequy", "Frost star",
).also { check(it.size == PlayerPalette.CREST_COUNT) }

/** `#RRGGBB` for a stored ARGB colour. */
fun argbCss(argb: Long): String = "#" + (argb and RGB_MASK).toString(HEX).padStart(RGB_DIGITS, '0')

/**
 * Gives an element the player colour [argb] as CSS `--pc`: the palette swatch's dark tone in the dark
 * theme, its light tone in the light one (goa.css "Player avatars" picks), the stored value itself
 * for a colour outside the palette, the muted colour for none.
 */
fun AttrsScope<*>.playerColor(argb: Long?) {
    classes("goa-pc")
    if (argb == null) return
    val swatch = PlayerPalette.find(argb)
    style {
        property("--pc-d", argbCss(swatch?.argb ?: argb))
        property("--pc-l", argbCss(swatch?.lightArgb ?: argb))
    }
}

/**
 * A player's crest in their colour on a tinted disc, or their initial without a crest; Android's
 * `PlayerAvatar`. One component for every screen (players, trivia, timer, draft). Decorative: the
 * name is always next to it.
 */
@Composable
fun PlayerAvatar(player: Player?, size: Int = AVATAR_SIZE) {
    Span({
        classes("goa-avatar")
        playerColor(player?.color)
        attr("aria-hidden", "true")
        avatarSize(size)
    }) {
        val crest = player?.avatarId?.takeIf { it in 0 until PlayerPalette.CREST_COUNT }
        if (crest != null) {
            Icon("crest_" + crest.toString().padStart(2, '0'))
        } else {
            Span({ classes("goa-avatar-initial") }) {
                Text(player?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?")
            }
        }
    }
}

/** A neutral silhouette for Guest (trivia): no colour, no crest, nobody in particular. */
@Composable
fun GuestAvatar(size: Int = AVATAR_SIZE) {
    Span({
        classes("goa-avatar", "guest")
        attr("aria-hidden", "true")
        avatarSize(size)
    }) { Icon("person") }
}

private fun AttrsScope<*>.avatarSize(size: Int) {
    style {
        property("width", "${size}px")
        property("height", "${size}px")
        property("--crest", "${(size * CREST_SCALE).roundToInt()}px")
        property("font-size", "${(size * INITIAL_SCALE).roundToInt()}px")
    }
}

/** The usual list-row avatar (Android's 40 dp). */
const val AVATAR_SIZE = 40
private const val CREST_SCALE = 0.56
private const val INITIAL_SCALE = 0.42
private const val RGB_MASK = 0xFFFFFFL
private const val HEX = 16
private const val RGB_DIGITS = 6
