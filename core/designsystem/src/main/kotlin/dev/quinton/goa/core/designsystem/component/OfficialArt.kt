package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/*
 * The official graphics the catalog import brings in (ADR-042, `official/SOURCES.md`), shown
 * as-is inside our own frames, and the parchment the cards print their text on.
 */

/** Asset paths of the official graphics beyond card scans and portraits. */
object OfficialArt {
    /** The item icon printed at the foot of a card (the stat it gives as an item, p.16). */
    fun itemIcon(stat: Stat): String = "official/icons/item-${stat.name.lowercase()}.webp"

    /** A team's hero Spawn point: Red's gear, Blue's star (rulebook p.6). */
    fun heroSpawn(team: Team): String = "official/board/spawn-hero-${team.slug}.webp"

    /** A team's minion Spawn point symbol for [type] (rulebook p.6). */
    fun minionSpawn(type: MinionType, team: Team): String =
        "official/board/spawn-${type.name.lowercase()}-${team.slug}.webp"

    /** The Tie Breaker coin's face for [team] (rulebook p.5): gear for Red, star for Blue. */
    fun tieBreaker(team: Team): String = "official/board/tie-breaker-${team.slug}.webp"

    /** Every path above, for the bundled-asset check. */
    val all: List<String>
        get() = Stat.entries.map(::itemIcon) +
            Team.entries.flatMap { team ->
                listOf(heroSpawn(team), tieBreaker(team)) + MinionType.entries.map { minionSpawn(it, team) }
            }

    private val Team.slug: String get() = if (this == Team.RED) "red" else "blue"
}

/** Width ÷ height of a deck card scan (729 × 1026). */
const val CARD_ASPECT = 729f / 1026f

/**
 * A hero's deck symbol (the emblem on their card backs and dashboard) in its own colours, or the
 * app's sigil while it loads and when there is none. Decorative unless [contentDescription] is set.
 */
@Composable
fun HeroSymbol(heroId: String, symbol: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    var loaded by remember(symbol) { mutableStateOf(false) }
    Box(
        modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (symbol == null || !loaded) HeroSigil(heroId, Modifier.fillMaxSize().padding(2.dp))
        if (symbol != null) {
            AsyncImage(
                model = assetUrl(symbol),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
    }
}

/**
 * An official graphic at its own aspect ratio (item icons, Spawn point symbols, the coin);
 * [fallback] shows while it loads and if it fails.
 */
@Composable
fun OfficialIcon(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallback: (@Composable BoxScope.() -> Unit)? = null,
) {
    var loaded by remember(path) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded && fallback != null) fallback()
        AsyncImage(
            model = assetUrl(path),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
            onSuccess = { loaded = true },
            onError = { loaded = false },
        )
    }
}

/**
 * An official graphic as an [ImageBitmap] for drawing on a canvas (the board's Spawn points);
 * null until it has loaded, and if it fails.
 */
@Composable
fun rememberOfficialBitmap(path: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, path) {
        val request = ImageRequest.Builder(context).data(assetUrl(path)).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        value = (result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
    }
    return bitmap
}

/**
 * An official card scan as it lies on the table: rounded corners like the printed card, a dark
 * hairline edge and a soft shadow, so cards of any colour sit on either theme. [fallback] draws
 * instead when there is no scan or it fails to load (a designed card, never a broken tile).
 * [contentDescription] is the whole card for TalkBack (its values and text, not just its name);
 * with [onClick] the card is one button named by [clickLabel].
 */
@Composable
fun CardScan(
    image: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    aspect: Float = CARD_ASPECT,
    onClick: (() -> Unit)? = null,
    clickLabel: String = "View card",
    elevation: Dp = 3.dp,
    fallback: @Composable BoxScope.() -> Unit,
) {
    var failed by remember(image) { mutableStateOf(false) }
    val shape = RoundedCornerShape(CARD_CORNER)
    val click = if (onClick != null) {
        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .aspectRatio(aspect)
            .shadow(elevation, shape)
            .clip(shape)
            .background(GoaTheme.colors.medallion)
            .border(1.dp, GoaTheme.colors.bannerShadow.copy(alpha = 0.55f), shape)
            .then(click)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = clickLabel) {
                        onClick()
                        true
                    }
                }
            },
    ) {
        if (image == null || failed) {
            fallback()
        } else {
            AsyncImage(
                model = assetUrl(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    }
}

private val CARD_CORNER = 6.dp

/**
 * The cards' parchment text box: text the game prints (card text, rulebook excerpts) sits on it,
 * dark on light in both themes as on the card. Content colour is `onParchment`.
 */
@Composable
fun ParchmentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = GoaTheme.colors
    val shape = MaterialTheme.shapes.small
    CompositionLocalProvider(LocalContentColor provides colors.onParchment) {
        Column(
            modifier
                .clip(shape)
                .background(colors.parchment)
                .border(1.dp, colors.parchmentEdge, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
            content = content,
        )
    }
}

/**
 * Printed card text on parchment, set in the printed face (Crimson Pro): inline game icons in
 * the parchment's ink, reminders in its secondary tone.
 */
@Composable
fun PrintedCardText(text: String, modifier: Modifier = Modifier, style: TextStyle = GoaTextStyles.printed) {
    val colors = GoaTheme.colors
    ParchmentPanel(modifier) {
        CardTextView(
            text,
            style = style,
            color = colors.onParchment,
            iconTint = colors.parchmentInk,
            reminderColor = colors.onParchmentVariant,
        )
    }
}
