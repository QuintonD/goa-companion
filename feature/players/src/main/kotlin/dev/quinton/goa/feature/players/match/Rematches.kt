package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One choice in the Rematch sheet. [kind] null = redraft with the same players (opens the Draft tab). */
data class RematchOption(val kind: RematchKind?, val title: String, val body: String, val unavailable: String? = null)

/** Where a rematch or a pending match continues (wired to other tabs in `Entries.kt`). */
sealed interface MatchNav {
    /** Match setup for this pending match, then the timer. */
    data class OpenSetup(val matchId: String) : MatchNav

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

/**
 * Starts a rematch: [start] saves the pending match (the caller opens Match setup with its id);
 * [redraft] fills the draft setup with the same players and teams (the caller opens the draft).
 */
@Singleton
class RematchLauncher @Inject constructor(
    private val matches: MatchRepository,
    private val draftSetup: DraftSetupPrefill,
) {
    suspend fun start(source: Match, kind: RematchKind, now: Long = System.currentTimeMillis()): Result<String> =
        Rematch.create(source, kind, UUID.randomUUID().toString(), now).map { match ->
            matches.upsert(match)
            match.id
        }

    suspend fun redraft(source: Match) {
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

/** The match just saved from the editor, so the match log can scroll to it. Process-scoped. */
@Singleton
class RecentlySavedMatch @Inject constructor() {
    private val state = MutableStateFlow<String?>(null)
    val matchId: StateFlow<String?> = state.asStateFlow()

    fun put(id: String) {
        state.value = id
    }

    /** Clears [id] once the log has shown it (a newer save is kept). */
    fun consumed(id: String) {
        state.compareAndSet(id, null)
    }
}
