package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.domain.draft.SlotStep
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team

/** One of a hero's starting cards, for the draft's hero peek. */
data class CardInfo(val name: String, val color: CardColor, val tier: Int?)

/**
 * The five starting cards (RB p.6, Hero Setup), selected from the catalog by [StartingCards]:
 * Gold, Silver, then Tier I by colour. Basic cards whose own text says they start in the deck
 * (Takahide's extra golds) are left out, since card text overrides the rulebook (RB p.3).
 */
fun startingCards(hero: Hero): List<CardInfo> = StartingCards.of(hero).map { CardInfo(it.name, it.color, it.tier) }

/** "Gold · Basic", "Red · Tier I". */
fun cardKind(card: CardInfo): String = when {
    card.tier == null && (card.color == CardColor.GOLD || card.color == CardColor.SILVER) ->
        "${colorName(card.color)} · Basic"

    card.tier != null -> "${colorName(card.color)} · Tier ${"I".repeat(card.tier.coerceIn(1, 3))}"

    else -> colorName(card.color)
}

private fun colorName(color: CardColor): String = color.name.lowercase().replaceFirstChar { it.uppercase() }

/** Display data for one hero; unknown ids render as "Unknown hero" (ARCHITECTURE.md). */
data class HeroInfo(
    val id: String,
    val name: String,
    val title: String = "",
    val portrait: String? = null,
    val complexity: Int? = null,
    val known: Boolean = true,
    val roles: List<Role> = emptyList(),
    val stats: HeroStats = HeroStats(),
    val startingCards: List<CardInfo> = emptyList(),
) {
    companion object {
        fun of(hero: Hero) = HeroInfo(
            id = hero.id,
            name = hero.name,
            title = hero.title,
            portrait = hero.portrait,
            complexity = hero.complexity,
            roles = hero.roles,
            stats = hero.stats,
            startingCards = startingCards(hero),
        )

        fun unknown(id: String) = HeroInfo(id, "Unknown hero", known = false)
    }
}

fun Map<String, HeroInfo>.info(id: String): HeroInfo = this[id] ?: HeroInfo.unknown(id)

/** "No hero" for an empty choice, else the hero's name. */
fun heroLabel(heroes: Map<String, HeroInfo>, id: String?): String = id?.let { heroes.info(it).name } ?: "No hero"

/** Visual state of a hero tile on the board. */
sealed interface TileState {
    data object Available : TileState

    /** Not choosable now (e.g. dealt to another seat) but still in play. */
    data object Inactive : TileState

    data class Banned(val team: Team) : TileState

    data class Picked(val team: Team) : TileState
}

/** One step in a sequence preview, already resolved to a display team label. */
data class StepToken(val label: String, val action: DraftAction, val team: Team?, val hidden: Boolean = false)

/** Tokens for slot steps; [teamA] maps A/B to Red/Blue once the coin is known. */
fun List<SlotStep>.tokens(teamA: Team? = null): List<StepToken> = map { step ->
    val team = teamA?.let { if (step.team == TeamSlot.A) it else it.opponent }
    val who = team?.let { teamGlyph(it) } ?: step.team.name
    StepToken("${actionName(step.action)} $who", step.action, team, step.hidden)
}
