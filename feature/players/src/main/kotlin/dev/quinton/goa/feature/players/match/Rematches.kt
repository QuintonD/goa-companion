package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/**
 * Starts a rematch: [start] checks it can be built and says where it continues (Match setup,
 * which saves it when the timer starts); [redraft] fills the draft setup with the same players
 * and teams (the caller opens the draft).
 */
@Singleton
class RematchLauncher @Inject constructor(private val draftSetup: DraftSetupPrefill) {
    fun start(source: Match, kind: RematchKind): MatchNav? =
        MatchNav.OpenRematch(source.id, kind).takeIf { Rematch.unavailable(source, kind) == null }

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
