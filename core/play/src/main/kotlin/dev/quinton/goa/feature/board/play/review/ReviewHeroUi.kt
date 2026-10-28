package dev.quinton.goa.feature.board.play.review

import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.figureOf
import dev.quinton.goa.feature.board.play.table.shownCounters

/**
 * The words of one hero's card on the Game state review's Heroes tab, shared by the phone and the web
 * app (ADR-043): its facts, what it has in play this turn (or why nothing), its markers and tokens,
 * and whose hand shows. The cards themselves (in play, resolved, discarded, active effects, the
 * Ultimate, extras, the hand) come from the [HeroView] as they are.
 */
data class ReviewHeroUi(
    /** "Level" 3, "Coins" 2, "In hand" 4, "Ultimate" Unlocked, then the shown counters. */
    val facts: List<Pair<String, String>>,
    /** Under the card in play: facedown before the reveal, else its initiative; null without one. */
    val inPlayNote: String?,
    /** No card in play: why ("No card played yet.", "Resolved: on Turn slot 2."…); null with one. */
    val noCardInPlay: String?,
    /** "Poison marker · from Bain". */
    val markers: List<String>,
    /** "On the board: Totem × 2", or null. */
    val tokens: String?,
    /** The hand's heading when it shows ("Your hand", "Arien's hand", the puzzle's open hand), else null. */
    val handTitle: String?,
) {
    val hasMarkersOrTokens: Boolean get() = markers.isNotEmpty() || tokens != null
}

fun reviewHero(table: TableUi, hero: HeroView, puzzle: Boolean): ReviewHeroUi = ReviewHeroUi(
    facts = buildList {
        add("Level" to "${hero.level}")
        add("Coins" to "${hero.coins}")
        add("In hand" to "${hero.handCount}")
        if (hero.ultimate) add("Ultimate" to "Unlocked")
        shownCounters(hero).forEach { (name, value) -> add(PlayText.counter(name) to "$value") }
    },
    inPlayNote = inPlayNote(table, hero),
    noCardInPlay = noCardInPlay(table, hero),
    markers = hero.markers.map { marker ->
        val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
        marker.kind.replaceFirstChar { it.uppercase() } + " marker" + from
    },
    tokens = tokensLine(table, hero),
    handTitle = handTitle(table, hero, puzzle),
)

private fun inPlayNote(table: TableUi, hero: HeroView): String? {
    val played = hero.played ?: return null
    val choosing = table.view.phase == GamePhase.PLAY_CARDS
    return when {
        choosing && played.cardId == null -> "Played facedown: revealed with the others (p.7)."
        choosing -> "Your card, facedown until the reveal."
        else -> hero.initiative?.let { "Initiative $it" }
    }
}

private fun noCardInPlay(table: TableUi, hero: HeroView): String? {
    val view = table.view
    return when {
        hero.played != null -> null
        view.phase == GamePhase.PLAY_CARDS -> "No card played yet."
        hero.heroId in view.actedThisTurn -> "Resolved: on Turn slot ${view.turn}."
        view.phase == GamePhase.RESOLVE -> "No card this turn."
        else -> "No card in play."
    }
}

private fun tokensLine(table: TableUi, hero: HeroView): String? {
    val pieces = table.view.pieces.filter { it.ownerHeroId == hero.heroId && figureOf(it) == null }
    if (pieces.isEmpty()) return null
    return "On the board: " + pieces.groupingBy { it.label }.eachCount().entries.joinToString(", ") { (label, n) ->
        if (n == 1) label else "$label × $n"
    }
}

private fun handTitle(table: TableUi, hero: HeroView, puzzle: Boolean): String? = when {
    hero.hand == null -> null
    hero.heroId in table.mine -> if (table.mine.size > 1) "${table.hero(hero.heroId).name}'s hand" else "Your hand"
    puzzle -> "In hand · open in this puzzle"
    else -> null
}
