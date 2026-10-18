package dev.quinton.goa.feature.trivia

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.model.player.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Who the next trivia round is played as (ADR-032), with this phone's "You" and the players to
 * pick from. [player] null = Guest (not on the leaderboard).
 */
@Immutable
data class PlayingAsState(
    val player: Player?,
    /** This phone's player ("Which player are you?", Group sync), resolved through merges. */
    val me: Player?,
    /** Active players to pick from, this phone's "You" first, then by name. */
    val players: List<Player>,
) {
    val isGuest: Boolean get() = player == null

    /**
     * Learning progress is this phone's (ADR-028): a round moves it only when played as this
     * phone's "You", or by anyone (Guest included) while no "You" is set.
     */
    val movesProgress: Boolean get() = me == null || player?.id == me.id

    companion object {
        val GUEST_ONLY = PlayingAsState(player = null, me = null, players = emptyList())

        /**
         * The resolution rule (PRODUCT §7): the last pick on this phone ([stored], a player id
         * already resolved through merges, or [TriviaProgressRepository.GUEST]) if it is an
         * active player or Guest; else this phone's "You" if active; else Guest.
         */
        fun resolve(stored: String?, me: Player?, active: List<Player>): PlayingAsState {
            val byId = active.associateBy { it.id }
            val activeMe = me?.id?.let(byId::get)
            val player = when {
                stored == TriviaProgressRepository.GUEST -> null
                stored != null && stored in byId -> byId.getValue(stored)
                else -> activeMe
            }
            val ordered = listOfNotNull(activeMe) + active.filter { it.id != activeMe?.id }.sortedBy { it.name.lowercase() }
            return PlayingAsState(player = player, me = me, players = ordered)
        }
    }
}

/**
 * [PlayingAsState] right now, read once (a quiz is played as whoever was picked when it opened).
 * Plain reads in turn, so a store that answers at once (as the fakes do) needs no dispatch.
 */
internal suspend fun currentPlayingAs(
    players: PlayerRepository,
    groups: GroupRepository,
    progress: TriviaProgressRepository,
): PlayingAsState {
    val stored = progress.playingAs.first()
    val me = groups.state.first().mePlayerId?.let { players.observePlayer(it).first() }
    val pick = if (stored == null || stored == TriviaProgressRepository.GUEST) {
        stored
    } else {
        players.observePlayer(stored).first()?.id ?: stored
    }
    return PlayingAsState.resolve(pick, me, players.observePlayers().first())
}

/** [PlayingAsState] as it changes: the stored pick, "You" and the player list. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun playingAs(
    players: PlayerRepository,
    groups: GroupRepository,
    progress: TriviaProgressRepository,
): Flow<PlayingAsState> {
    /** A player id read through merges (a merged-away duplicate reads as the player it joined). */
    fun resolved(id: String?): Flow<Player?> = if (id == null) flowOf(null) else players.observePlayer(id)
    val me = groups.state.map { it.mePlayerId }.distinctUntilChanged().flatMapLatest(::resolved)
    val stored = progress.playingAs.distinctUntilChanged().flatMapLatest { id ->
        if (id == null || id == TriviaProgressRepository.GUEST) flowOf(id) else resolved(id).map { it?.id ?: id }
    }
    return combine(stored, me, players.observePlayers()) { pick, you, active -> PlayingAsState.resolve(pick, you, active) }
        .distinctUntilChanged()
}
