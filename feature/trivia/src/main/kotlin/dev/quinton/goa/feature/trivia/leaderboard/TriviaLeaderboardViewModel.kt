package dev.quinton.goa.feature.trivia.leaderboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.TriviaRoundRepository
import dev.quinton.goa.core.designsystem.component.formatDuration
import dev.quinton.goa.core.designsystem.component.spokenDuration
import dev.quinton.goa.core.domain.trivia.ChallengeRow
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.PracticeRow
import dev.quinton.goa.core.domain.trivia.TriviaLeaderboard
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import dev.quinton.goa.feature.trivia.TriviaText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LeaderboardTab { CHALLENGE, PRACTICE }

/** One ranked row: "1 · [crest] Sam (You) … 18/20 / 1:42 · 3 Oct". */
@Immutable
data class LeaderboardRowUi(
    val player: Player,
    val place: Int,
    val tied: Boolean,
    val isMe: Boolean,
    /** The figure the row is ranked by: "18/20", "245". */
    val main: String,
    /** Under the main figure: "right" on the Practice board. */
    val mainCaption: String?,
    val detail: String,
    /** The whole row as one sentence for TalkBack. */
    val spoken: String,
)

/** A recorded round in a player's sheet. */
@Immutable
data class RoundUi(
    val id: String,
    /** "Challenge", "Best Challenge" or "Practice". */
    val kind: String,
    /** The player's best Challenge in the period: the one on the Challenge board. */
    val best: Boolean = false,
    /** "18/20". */
    val score: String,
    /** "1:42 · best streak 9 · 3 Oct". */
    val detail: String,
    /** How the confirm dialog names it: "Sam's Challenge on 3 Oct (18/20)". */
    val label: String,
    val spoken: String,
)

@Immutable
data class PlayerRoundsUi(val player: Player, val isMe: Boolean, val rounds: List<RoundUi>)

sealed interface TriviaLeaderboardUiState {
    data object Loading : TriviaLeaderboardUiState

    @Immutable
    data class Ready(
        val tab: LeaderboardTab,
        val period: LeaderboardPeriod,
        val challenge: List<LeaderboardRowUi>,
        val practice: List<LeaderboardRowUi>,
        /** The Challenge board has rows over all time (an empty period then points to All time). */
        val challengeEver: Boolean = challenge.isNotEmpty(),
        /** The Practice board has rows over all time. */
        val practiceEver: Boolean = practice.isNotEmpty(),
        /** The player whose rounds are open in the sheet, if any. */
        val selected: PlayerRoundsUi? = null,
        /** A short message to show once (a failed removal). */
        val message: String? = null,
    ) : TriviaLeaderboardUiState {
        val rows: List<LeaderboardRowUi> get() = if (tab == LeaderboardTab.CHALLENGE) challenge else practice

        /** This tab has scores, just none in the chosen period. */
        val olderScores: Boolean
            get() = period != LeaderboardPeriod.ALL_TIME &&
                (if (tab == LeaderboardTab.CHALLENGE) challengeEver else practiceEver)
    }
}

/**
 * The trivia leaderboard (ADR-032): Challenge and Practice boards derived from the recorded rounds
 * (synced with the group), All time or Last 30 days; a player's recent rounds, each removable (a
 * tombstone, so it goes from every phone).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TriviaLeaderboardViewModel internal constructor(
    private val rounds: TriviaRoundRepository,
    players: PlayerRepository,
    groups: GroupRepository,
    private val clock: EditClock,
    private val saved: SavedStateHandle,
    /** Builds the boards off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(
        rounds: TriviaRoundRepository,
        players: PlayerRepository,
        groups: GroupRepository,
        clock: EditClock,
        saved: SavedStateHandle,
    ) : this(rounds, players, groups, clock, saved, Dispatchers.Default)

    private val tab = saved.getStateFlow(KEY_TAB, LeaderboardTab.CHALLENGE.name)
    private val period = saved.getStateFlow(KEY_PERIOD, LeaderboardPeriod.ALL_TIME.name)
    private val selected = saved.getStateFlow<String?>(KEY_PLAYER, null)

    /** "Now" for Last 30 days: read again whenever the screen comes back ([onResume]). */
    private val now = MutableStateFlow(clock.now())
    private val message = MutableStateFlow<String?>(null)

    private val me = groups.state.map { it.mePlayerId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else players.observePlayer(id).map { it?.id }
    }

    private data class Choice(
        val tab: LeaderboardTab,
        val period: LeaderboardPeriod,
        val selected: String?,
        val now: Long,
        val message: String?,
    )

    private val choice = combine(tab, period, selected, now, message) { t, p, s, n, m ->
        Choice(LeaderboardTab.valueOf(t), LeaderboardPeriod.valueOf(p), s, n, m)
    }

    val uiState: StateFlow<TriviaLeaderboardUiState> = combine(
        rounds.observeRounds(),
        players.observePlayers(includeArchived = true),
        me,
        choice,
    ) { all, people, meId, c -> build(all, people, meId, c) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TriviaLeaderboardUiState.Loading)

    /** The screen is back in view: "Last 30 days" counts from now, not from the last data change. */
    fun onResume() {
        now.value = clock.now()
    }

    fun messageShown() {
        message.value = null
    }

    fun selectTab(value: LeaderboardTab) {
        saved[KEY_TAB] = value.name
    }

    fun selectPeriod(value: LeaderboardPeriod) {
        saved[KEY_PERIOD] = value.name
    }

    fun openPlayer(playerId: String) {
        saved[KEY_PLAYER] = playerId
    }

    fun closePlayer() {
        saved[KEY_PLAYER] = null
    }

    /** "Remove from leaderboard": deleted with a tombstone, so every phone drops it at its next sync. */
    @Suppress("SwallowedException") // a failed write becomes the message the user sees
    fun removeRound(id: String) {
        viewModelScope.launch {
            try {
                rounds.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                message.value = "Couldn't remove the round. Try again."
            }
        }
    }

    private fun build(
        all: List<TriviaRound>,
        people: List<Player>,
        meId: String?,
        c: Choice,
    ): TriviaLeaderboardUiState {
        val now = c.now
        val board = TriviaLeaderboard.build(all, people, meId, now, c.period)
        val ever = if (c.period == LeaderboardPeriod.ALL_TIME) {
            board
        } else {
            TriviaLeaderboard.build(all, people, meId, now, LeaderboardPeriod.ALL_TIME)
        }
        val byId = people.associateBy { it.id }
        val dates = Dates(now)
        val open = c.selected?.let(byId::get)?.let { player ->
            // Every round that counts for them in the period, their best Challenge first.
            val bestId = board.challenge.firstOrNull { it.playerId == player.id }?.best?.id
            val list = TriviaLeaderboard.playerRounds(all, player.id, now, c.period)
            val ordered = list.filter { it.id == bestId } + list.filterNot { it.id == bestId }
            PlayerRoundsUi(
                player = player,
                isMe = player.id == meId,
                rounds = ordered.map { roundUi(it, player, dates, best = it.id == bestId) },
            )
        }
        return TriviaLeaderboardUiState.Ready(
            tab = c.tab,
            period = c.period,
            challenge = board.challenge.mapNotNull { row -> byId[row.playerId]?.let { challengeRow(row, it, dates) } },
            practice = board.practice.mapNotNull { row -> byId[row.playerId]?.let { practiceRow(row, it) } },
            challengeEver = ever.challenge.isNotEmpty(),
            practiceEver = ever.practice.isNotEmpty(),
            selected = open,
            message = c.message,
        )
    }

    private class Dates(now: Long) {
        private val zone = ZoneId.systemDefault()
        private val year = Instant.ofEpochMilli(now).atZone(zone).year
        private val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        private val long = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        private val spoken = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

        fun short(at: Long): String = Instant.ofEpochMilli(at).atZone(zone).let {
            if (it.year == year) it.format(short) else it.format(long)
        }

        fun spoken(at: Long): String = Instant.ofEpochMilli(at).atZone(zone).format(spoken)
    }

    private fun placeWords(place: Int, tied: Boolean) = TriviaText.place(place, tied).lowercase() + " place"

    private fun challengeRow(row: ChallengeRow, player: Player, dates: Dates): LeaderboardRowUi {
        val best = row.best
        val detail = listOfNotNull(
            TriviaText.answerTime(best.answerMillis),
            dates.short(best.playedAt),
            TriviaText.count(row.challenges, "Challenge").takeIf { row.challenges > 1 },
        ).joinToString(" · ")
        val spoken = listOfNotNull(
            placeWords(row.place, row.tied),
            player.name,
            "you".takeIf { row.isMe },
            "best ${best.correct} of ${best.questions} right in ${TriviaText.spokenAnswerTime(best.answerMillis)}",
            dates.spoken(best.playedAt),
            TriviaText.count(row.challenges, "Challenge").takeIf { row.challenges > 1 },
        ).joinToString(", ")
        return LeaderboardRowUi(
            player = player,
            place = row.place,
            tied = row.tied,
            isMe = row.isMe,
            main = "${best.correct}/${best.questions}",
            mainCaption = null,
            detail = detail,
            spoken = spoken,
        )
    }

    private fun practiceRow(row: PracticeRow, player: Player): LeaderboardRowUi {
        val accuracy = "${(row.accuracy * PERCENT).roundToInt()}%"
        val detail = "$accuracy · best streak ${row.bestStreak} · ${TriviaText.count(row.rounds, "round")}"
        val spoken = listOfNotNull(
            placeWords(row.place, row.tied),
            player.name,
            "you".takeIf { row.isMe },
            "${TriviaText.number(row.correct)} right of ${TriviaText.number(row.questions)}, $accuracy",
            "best streak ${row.bestStreak}",
            TriviaText.count(row.rounds, "round"),
        ).joinToString(", ")
        return LeaderboardRowUi(
            player = player,
            place = row.place,
            tied = row.tied,
            isMe = row.isMe,
            main = TriviaText.number(row.correct),
            mainCaption = "right",
            detail = detail,
            spoken = spoken,
        )
    }

    private fun roundUi(round: TriviaRound, player: Player, dates: Dates, best: Boolean): RoundUi {
        val challenge = round.kind == TriviaRoundKind.CHALLENGE
        val kind = when {
            best -> "Best Challenge"
            challenge -> "Challenge"
            else -> "Practice"
        }
        val score = "${round.correct}/${round.questions}"
        val date = dates.short(round.playedAt)
        // A Challenge's time as the board ranks it (tenths); a practice round's to the second.
        val time = if (challenge) TriviaText.answerTime(round.answerMillis) else formatDuration(round.answerMillis)
        val spokenTime =
            if (challenge) TriviaText.spokenAnswerTime(round.answerMillis) else spokenDuration(round.answerMillis)
        val detail = listOf(time, "best streak ${round.bestStreak}", date).joinToString(" · ")
        return RoundUi(
            id = round.id,
            kind = kind,
            best = best,
            score = score,
            detail = detail,
            label = if (round.kind == TriviaRoundKind.CHALLENGE) {
                "${player.name}'s Challenge on $date ($score)"
            } else {
                "${player.name}'s practice round on $date ($score)"
            },
            spoken = "$kind, ${round.correct} of ${round.questions} right in $spokenTime, " +
                "best streak ${round.bestStreak}, ${dates.spoken(round.playedAt)}",
        )
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_PERIOD = "period"
        const val KEY_PLAYER = "player"
        const val STOP_TIMEOUT_MS = 5_000L
        const val PERCENT = 100
    }
}
