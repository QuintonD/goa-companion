package dev.quinton.goa.feature.draft.ui

import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.domain.draft.SlotStep
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Role as HeroRole
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team

/*
 * The draft's words and display data, shared by the phone's draft screens and the web app's
 * (ADR-043 "Table, W3"): change a label here and both say it.
 */

fun teamGlyph(team: Team): String = if (team == Team.RED) "◆" else "●"

fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"

/** "◆ Red" — team identity is never colour-only. */
fun teamLabel(team: Team): String = "${teamGlyph(team)} ${teamName(team)}"

fun actionName(action: DraftAction): String = if (action == DraftAction.BAN) "Ban" else "Pick"

/** "1 game", "2 games"; [many] for irregular plurals ("heroes"). */
fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"

/** Official role names (rulebook p.21 Hero Profile card). */
val HeroRole.label: String
    get() = when (this) {
        HeroRole.TACTICIAN -> "Tactician"
        HeroRole.DISABLER -> "Disabler"
        HeroRole.DURABLE -> "Durable"
        HeroRole.MELEE -> "Melee"
        HeroRole.PUSHER -> "Pusher"
        HeroRole.DAMAGER -> "Damager"
        HeroRole.SNIPER -> "Sniper"
        HeroRole.TOKENS -> "Tokens"
        HeroRole.FARMING -> "Farming"
        HeroRole.HEALER -> "Healer"
    }

/** One of a hero's starting cards, for the draft's hero peek. */
data class CardInfo(val name: String, val color: CardColor, val tier: Int?, val image: String? = null)

/**
 * The five starting cards (RB p.6, Hero Setup), selected from the catalog by [StartingCards]:
 * Gold, Silver, then Tier I by colour. Basic cards whose own text says they start in the deck
 * (Takahide's extra golds) are left out, since card text overrides the rulebook (RB p.3).
 */
fun startingCards(
    hero: Hero,
): List<CardInfo> = StartingCards.of(hero).map { CardInfo(it.name, it.color, it.tier, it.image) }

/** "Gold · Basic", "Red · Tier I" (a starting card, for assistive tech). */
fun cardKind(card: CardInfo): String = when {
    card.tier == null && (card.color == CardColor.GOLD || card.color == CardColor.SILVER) ->
        "${colorName(card.color)} · Basic"

    card.tier != null -> "${colorName(card.color)} · Tier ${"I".repeat(card.tier.coerceIn(1, 3))}"

    else -> colorName(card.color)
}

private fun colorName(color: CardColor): String = when (color) {
    CardColor.GOLD -> "Gold"
    CardColor.SILVER -> "Silver"
    CardColor.RED -> "Red"
    CardColor.GREEN -> "Green"
    CardColor.BLUE -> "Blue"
    CardColor.PURPLE -> "Purple"
    CardColor.OTHER -> "Other"
}

/** Display data for one hero; unknown ids render as "Unknown hero" (ARCHITECTURE.md). */
data class HeroInfo(
    val id: String,
    val name: String,
    val title: String = "",
    val portrait: String? = null,
    /** The deck symbol (official, ADR-042); null for unknown heroes. */
    val symbol: String? = null,
    val complexity: Int? = null,
    val known: Boolean = true,
    val roles: List<HeroRole> = emptyList(),
    val stats: HeroStats = HeroStats(),
    val startingCards: List<CardInfo> = emptyList(),
) {
    companion object {
        fun of(hero: Hero) = HeroInfo(
            id = hero.id,
            name = hero.name,
            title = hero.title,
            portrait = hero.portrait,
            symbol = hero.symbol,
            complexity = hero.complexity,
            roles = hero.roles,
            stats = hero.stats,
            startingCards = startingCards(hero),
        )

        fun unknown(id: String) = HeroInfo(id, "Unknown hero", known = false)
    }
}

fun Map<String, HeroInfo>.info(id: String): HeroInfo = this[id] ?: HeroInfo.unknown(id)

/** "No hero" when a seat has none. */
fun heroLabel(heroes: Map<String, HeroInfo>, id: String?) = id?.let { heroes.info(it).name } ?: "No hero"

/** Visual state of a hero tile on the board. */
sealed interface TileState {
    data object Available : TileState

    /** Not choosable now (e.g. dealt to another seat) but still in play. */
    data object Inactive : TileState

    data class Banned(val team: Team) : TileState

    data class Picked(val team: Team) : TileState
}

/** What assistive tech says about a tile's state. */
fun TileState.description(): String = when (this) {
    TileState.Available -> "available"
    TileState.Inactive -> "not available to this seat"
    is TileState.Banned -> "banned by ${teamName(team)}"
    is TileState.Picked -> "picked by ${teamName(team)}"
}

/** One step in a sequence preview, already resolved to a display team label. */
data class StepToken(val label: String, val action: DraftAction, val team: Team?, val hidden: Boolean = false)

/** Tokens for slot steps; [teamA] maps A/B to Red/Blue once the coin is known. */
fun List<SlotStep>.tokens(teamA: Team? = null): List<StepToken> = map { step ->
    val team = teamA?.let { if (step.team == TeamSlot.A) it else it.opponent }
    val who = team?.let { teamGlyph(it) } ?: step.team.name
    StepToken("${actionName(step.action)} $who", step.action, team, step.hidden)
}

/** "Sequence: Ban Red, Pick Blue, …" for assistive tech. */
fun List<StepToken>.spoken(): String =
    "Sequence: " + joinToString(", ") { it.label.replace("◆", "Red").replace("●", "Blue") }
