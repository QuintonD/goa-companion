package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team

/** One choice in the Rematch sheet. [kind] null = redraft with the same players (opens the Draft tab). */
data class RematchOption(val kind: RematchKind?, val title: String, val body: String, val unavailable: String? = null)

/** Where a rematch or a pending match continues (wired to other tabs in `Entries.kt`). */
sealed interface MatchNav {
    /** Match setup for this pending match, then the timer. */
    data class OpenSetup(val matchId: String) : MatchNav

    /**
     * Match setup for a rematch of [sourceId]. Nothing is saved yet: the rematch becomes a match
     * when its timer starts, so backing out leaves no pending match behind.
     */
    data class OpenRematch(val sourceId: String, val kind: RematchKind) : MatchNav

    /** The draft, to draft again with the same group. */
    data object OpenDraft : MatchNav
}

fun rematchOptions(match: Match): List<RematchOption> = listOf(
    RematchOption(
        RematchKind.SAME_SEATS,
        "Same seats",
        "Same players, heroes and teams.",
        Rematch.unavailable(match, RematchKind.SAME_SEATS),
    ),
    RematchOption(
        RematchKind.SWAP_HEROES,
        "Swap heroes",
        "Players stay on their team; Red's heroes go to Blue and Blue's to Red.",
        Rematch.unavailable(match, RematchKind.SWAP_HEROES),
    ),
    RematchOption(
        RematchKind.SWAP_TEAMS,
        "Swap sides",
        "Everyone keeps their hero; Red plays Blue and Blue plays Red.",
        Rematch.unavailable(match, RematchKind.SWAP_TEAMS),
    ),
    RematchOption(null, "Draft again", "The draft opens with the same players and teams; pick new heroes."),
)

/** What a rematch does, the same on the phone and in the web app (ADR-043). */
object Rematches {
    /** Where a rematch of [source] continues (Match setup); null when it can't be built. */
    fun start(source: Match, kind: RematchKind): MatchNav? =
        MatchNav.OpenRematch(source.id, kind).takeIf { Rematch.unavailable(source, kind) == null }

    /** Fills the draft setup with [source]'s players and teams (the caller opens the draft). */
    suspend fun redraft(source: Match, draftSetup: DraftSetupPrefill) {
        val red = source.team(Team.RED)
        val blue = source.team(Team.BLUE)
        val doublePilot = source.participants.groupingBy {
            it.playerId
        }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
        draftSetup.prefill(
            red = red.map { it.playerId }.distinct(),
            blue = blue.map { it.playerId }.distinct(),
            doublePilot = doublePilot,
            handicap = red.size != blue.size,
        )
    }
}
