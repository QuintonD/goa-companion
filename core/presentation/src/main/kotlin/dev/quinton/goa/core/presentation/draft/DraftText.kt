package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.Rating
import kotlin.math.roundToInt

/*
 * The draft's words, shared by the Android screens and the hub's pages (ADR-030 §1), so a team,
 * a step or a rating reads the same on every screen.
 */

fun teamGlyph(team: Team): String = if (team == Team.RED) "◆" else "●"

fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"

/** "◆ Red" — team identity is never colour-only. */
fun teamLabel(team: Team): String = "${teamGlyph(team)} ${teamName(team)}"

fun actionName(action: DraftAction): String = if (action == DraftAction.BAN) "Ban" else "Pick"

/** "1 game", "2 games"; [many] for irregular plurals ("heroes"). */
fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"

/** Official role names (rulebook p.21 Hero Profile card). */
val Role.label: String
    get() = when (this) {
        Role.TACTICIAN -> "Tactician"
        Role.DISABLER -> "Disabler"
        Role.DURABLE -> "Durable"
        Role.MELEE -> "Melee"
        Role.PUSHER -> "Pusher"
        Role.DAMAGER -> "Damager"
        Role.SNIPER -> "Sniper"
        Role.TOKENS -> "Tokens"
        Role.FARMING -> "Farming"
        Role.HEALER -> "Healer"
    }

/** "Rulebook", "House" or "Custom": who defined a method. */
fun originLabel(origin: MethodOrigin): String = when (origin) {
    MethodOrigin.RULEBOOK -> "Rulebook"
    MethodOrigin.HOUSE -> "House"
    MethodOrigin.CUSTOM -> "Custom"
}

/** How a method's pool is drawn (methods list, method editor). */
fun poolText(pool: PoolRule): String = when (pool) {
    PoolRule.All -> "Pool: every eligible hero"

    is PoolRule.Random -> "Pool: ${pool.size} at random"

    is PoolRule.SeatsPlus -> when (pool.extra) {
        0 -> "Pool: one per hero seat"
        else -> "Pool: heroes in play + ${pool.extra} revealed"
    }

    is PoolRule.DealPerSeat -> "Pool: ${pool.perSeat} dealt to each seat"
}

/** " · needs 15" after [poolText] when the layout can run and needs heroes; else empty. */
fun heroesText(preview: SequencePreview): String =
    if (preview.error == null && preview.heroesNeeded > 0) " · needs ${preview.heroesNeeded}" else ""

/** "Elo 1234 · 5 games · provisional", or "Unrated". */
fun ratingLabel(rating: Rating?): String {
    val r = rating ?: return "Unrated"
    if (r.games == 0) return "Unrated"
    val provisional = if (r.provisional) " · provisional" else ""
    return "Elo ${r.value.roundToInt()} · ${plural(r.games, "game")}$provisional"
}
