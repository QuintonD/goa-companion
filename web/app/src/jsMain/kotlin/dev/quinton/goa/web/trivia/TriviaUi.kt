package dev.quinton.goa.web.trivia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.data.trivia.TriviaCardUi
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.domain.trivia.MasterySummary
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * A scope for a screen's shared model (the phone's `viewModelScope`): lives while the screen is
 * shown, cancelled when it goes. A failure in one of its jobs (a storage error) is reported through
 * [onError] instead of taking the app down.
 */
@Composable
internal fun rememberScreenScope(onError: (Throwable) -> Unit = {}): CoroutineScope {
    val scope = remember {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error ->
                console.error("Trivia", error)
                onError(error)
            },
        )
    }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    return scope
}

/**
 * The group's players, "Who are you?" and trivia rounds, read lazily: every read becomes a flow
 * that fails when collected (never when created), so the shared models degrade as they do on the
 * phone (Guest only, "couldn't be saved") when this browser can't reach the group's data.
 */
internal class TriviaGroupAccess(players: PlayerRepository, groups: GroupRepository, rounds: TriviaRoundRepository) {
    val players: PlayerRepository = object : PlayerRepository by players {
        override fun observePlayers(includeArchived: Boolean): Flow<List<Player>> =
            flow { emitAll(players.observePlayers(includeArchived)) }

        override fun observePlayer(id: String): Flow<Player?> = flow { emitAll(players.observePlayer(id)) }
    }

    val groups: GroupRepository = object : GroupRepository by groups {
        override val state get() = flow { emitAll(groups.state) }
    }

    val rounds: TriviaRoundRepository = object : TriviaRoundRepository by rounds {
        override fun observeRounds() = flow { emitAll(rounds.observeRounds()) }
    }
}

@Composable
internal fun rememberTriviaGroup(): TriviaGroupAccess {
    val group = LocalServices.current.data.group
    return remember { TriviaGroupAccess(group.players, group.group, group.triviaRounds) }
}

/** The tone a stored player colour is drawn with in the current theme (Android's `playerColor`). */
@Composable
private fun playerTone(argb: Long): String {
    val dark = LocalServices.current.theme.isDark
    val swatch = PlayerPalette.find(argb)
    val tone = when {
        swatch == null -> argb
        dark -> swatch.argb
        else -> swatch.lightArgb
    }
    return "#" + (tone and RGB_MASK).toString(HEX).padStart(RGB_DIGITS, '0')
}

/** A player's crest in their colour on a tinted disc, or their initial without a crest. */
@Composable
internal fun PlayerAvatar(player: Player, small: Boolean = false) {
    val tone = playerTone(player.color)
    Span({
        classes("goa-trivia-avatar")
        if (small) classes("small")
        attr("aria-hidden", "true")
        style { property("--player", tone) }
    }) {
        val crest = player.avatarId.takeIf { it in 0 until PlayerPalette.CREST_COUNT }
        if (crest != null) {
            Icon("crest_" + crest.toString().padStart(2, '0'))
        } else {
            Span({ classes("goa-trivia-initial") }) {
                Text(player.name.trim().take(1).uppercase().ifEmpty { "?" })
            }
        }
    }
}

/** A neutral silhouette for Guest: no colour, no crest, nobody in particular. */
@Composable
internal fun GuestAvatar(small: Boolean = false) {
    Span({
        classes("goa-trivia-avatar", "guest")
        if (small) classes("small")
        attr("aria-hidden", "true")
    }) { Icon("person", IconSize.SMALL) }
}

/** Whoever is playing: their crest, or the Guest silhouette. */
@Composable
internal fun PlayingAvatar(player: Player?, small: Boolean = false) {
    if (player == null) GuestAvatar(small) else PlayerAvatar(player, small)
}

/** Marks this device's own player, as on the Players tab. */
@Composable
internal fun YouTag() {
    Span({ classes("goa-trivia-you", "t-label-medium") }) { Text("You") }
}

/** Data not checked against the printed cards yet (AGENTS.md): never shown without this mark. */
@Composable
internal fun UnverifiedBadge() {
    Span({
        classes("goa-trivia-unverified", "t-label-medium")
        attr("title", "Unverified data, may contain errors")
    }) { Text("Unverified") }
}

/** A hero's portrait in a small medallion (decorative: the name is always beside it). */
@Composable
internal fun HeroMedallion(portrait: String?, size: MedallionSize = MedallionSize.MEDIUM) {
    Span({ classes("goa-trivia-medallion", size.css) }) {
        if (portrait != null) {
            Img(portrait, alt = "") {
                attr("loading", "lazy")
                attr("decoding", "async")
            }
        }
    }
}

internal enum class MedallionSize(val css: String) { SMALL("small"), MEDIUM("medium"), LARGE("large") }

/**
 * Learned, learning and new numbers as one segmented bar. Decorative on its own: callers pair it
 * with the counts in words ([MasteryLegend] or a "12/86" line).
 */
@Composable
internal fun MasteryBar(summary: MasterySummary, thin: Boolean = false) {
    val total = summary.total.coerceAtLeast(1).toDouble()
    Div({
        classes("goa-trivia-bar")
        if (thin) classes("thin")
        attr("aria-hidden", "true")
    }) {
        if (summary.mastered > 0) {
            Div({
                classes("learned")
                style { property("flex-grow", (summary.mastered / total).toString()) }
            })
        }
        if (summary.learning > 0) {
            Div({
                classes("learning")
                style { property("flex-grow", (summary.learning / total).toString()) }
            })
        }
        if (summary.unseen > 0) Div({ style { property("flex-grow", (summary.unseen / total).toString()) } })
    }
}

/** "● Learned 41  ● Learning 12  ● New 271": the bar's colours in words. */
@Composable
internal fun MasteryLegend(summary: MasterySummary) {
    Div({ classes("goa-trivia-legend") }) {
        listOf(
            Triple("learned", "Learned", summary.mastered),
            Triple("learning", "Learning", summary.learning),
            Triple("new", "New", summary.unseen),
        ).forEach { (css, label, count) ->
            Span({ classes("goa-trivia-legend-item", "t-label-large") }) {
                Span({
                    classes("goa-trivia-legend-dot", css)
                    attr("aria-hidden", "true")
                })
                Text("$label ${TriviaText.number(count)}")
            }
        }
    }
}

/** A card's colour as a small dot with a hairline ring (so pale colours stay visible). */
@Composable
internal fun CardColorDot(color: CardColor) {
    Span({
        classes("goa-trivia-dot", color.name.lowercase())
        attr("aria-hidden", "true")
    })
}

/** "● Red · Tier II · Attack card" under a card name. */
@Composable
internal fun CardKindLine(card: TriviaCardUi, showPrimary: Boolean = true) {
    Div({ classes("goa-trivia-kind", "t-label-large") }) {
        CardColorDot(card.color)
        Span {
            Text(listOfNotNull(card.label.ifEmpty { null }, card.primary?.takeIf { showPrimary }).joinToString(" · "))
        }
    }
}

/** The hero a card belongs to: small portrait and name, and Unverified when it is. */
@Composable
internal fun HeroLine(card: TriviaCardUi, small: Boolean = false) {
    Div({ classes("goa-trivia-heroline") }) {
        HeroMedallion(card.portrait, if (small) MedallionSize.SMALL else MedallionSize.MEDIUM)
        Span({ classes("t-title-small", "c-variant") }) { Text(card.heroName) }
        if (!card.heroVerified) UnverifiedBadge()
    }
}

private const val RGB_MASK = 0xFFFFFFL
private const val HEX = 16
private const val RGB_DIGITS = 6
