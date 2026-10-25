package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.Match
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// RematchOption, MatchNav and rematchOptions() are shared with the web app (core:group-presentation).

/**
 * Starts a rematch: [start] checks it can be built and says where it continues (Match setup,
 * which saves it when the timer starts); [redraft] fills the draft setup with the same players
 * and teams (the caller opens the draft).
 */
@Singleton
class RematchLauncher @Inject constructor(private val draftSetup: DraftSetupPrefill) {
    fun start(source: Match, kind: RematchKind): MatchNav? = Rematches.start(source, kind)

    suspend fun redraft(source: Match) = Rematches.redraft(source, draftSetup)
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
