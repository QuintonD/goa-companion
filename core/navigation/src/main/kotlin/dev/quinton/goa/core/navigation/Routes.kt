package dev.quinton.goa.core.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every navigable screen. Keys live here (not in features) so any feature can open another
 * feature's screen without a module dependency; each feature registers entries for its own keys
 * via `EntryProviderScope<NavKey>.<feature>Entries(navigator)`, wired in `app`.
 */

enum class TopLevelTab { CATALOG, TIMER, DRAFT, PLAYERS }

/**
 * Intent extra naming the [TopLevelTab] to show when the app opens (e.g. "TIMER" from the timer
 * notification). The app shell reads it in `MainActivity`.
 */
const val EXTRA_OPEN_TAB = "dev.quinton.goa.extra.OPEN_TAB"

interface Navigator {
    fun navigate(key: NavKey)

    /**
     * Pushes [key] on [tab]'s stack and switches to that tab. If a screen of the same type is
     * already on that stack (e.g. an earlier Match setup), the stack is first cut back to below it,
     * so a stale copy and whatever was opened on top of it can't resurface.
     */
    fun navigateToTab(tab: TopLevelTab, key: NavKey? = null)

    /**
     * Shows the running timer: the Timer tab's stack becomes exactly its root (no stale presets
     * or Match setup underneath), whichever tab the caller was on.
     */
    fun openTimer() = navigateToTab(TopLevelTab.TIMER)

    fun back()

    /**
     * A short app-wide message (snackbar above the bottom bar) that survives the screen that
     * sent it closing, e.g. "Pending match saved". [onAction] runs when [actionLabel] is tapped.
     */
    fun showMessage(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {}
}

// Catalog
@Serializable data object CatalogRoute : NavKey

@Serializable data class HeroDetailRoute(val heroId: String) : NavKey

@Serializable data class CardViewerRoute(val heroId: String, val cardId: String) : NavKey

// Match setup & timer
@Serializable data object TimerRoute : NavKey

/**
 * Match setup (mode, counters, tie breaker) for a pending match, or a fresh one. [rematchOf] +
 * [rematchKind] (a `RematchKind` name) set up a rematch of that match instead; it is saved as a
 * match only when its timer starts.
 */
@Serializable data class MatchSetupRoute(
    val matchId: String? = null,
    val rematchOf: String? = null,
    val rematchKind: String? = null,
) : NavKey

@Serializable data object TimerPresetsRoute : NavKey

@Serializable data class TimerPresetEditRoute(val presetId: String? = null) : NavKey

// Draft
@Serializable data object DraftRoute : NavKey

@Serializable data object DraftBoardRoute : NavKey

@Serializable data object DraftMethodsRoute : NavKey

@Serializable data class DraftMethodEditRoute(val methodId: String? = null) : NavKey

// Players, matches, meta
@Serializable data object PlayersRoute : NavKey

@Serializable data class PlayerDetailRoute(val playerId: String) : NavKey

@Serializable data class PlayerEditRoute(val playerId: String? = null) : NavKey

@Serializable data object MatchLogRoute : NavKey

/**
 * Match editor. [matchId] null = new match; [prefillDurationMin] / [prefillMode] (a `GameMode`
 * serial name, e.g. "quick") pre-fill a NEW match, e.g. from a finished timer session.
 *
 * [result] opens the focused "Who won?" layout (winner and win condition first, a pending match
 * defaults to Completed), used when a game has just ended: from the timer summary or a pending
 * match. [prefillPlayedAt] (epoch ms, e.g. the timer session's start) replaces the date of a new
 * or still pending match. [newMatchId] is the id a NEW match is saved under (the timer reserves
 * one so it can tell when the result it opened was saved).
 */
@Serializable data class MatchEditRoute(
    val matchId: String? = null,
    val prefillDurationMin: Int? = null,
    val prefillMode: String? = null,
    val result: Boolean = false,
    val prefillPlayedAt: Long? = null,
    val newMatchId: String? = null,
) : NavKey

@Serializable data object MetaRoute : NavKey

/** Export / import of all user data (owner: players). */
@Serializable data object BackupRoute : NavKey

// Rules reference
@Serializable data object RulesRoute : NavKey

@Serializable data class RulesTopicRoute(val topicId: String) : NavKey

/**
 * The level & defeat ladder, shown as a bottom sheet ([NavSheet]). [heroIds] are the heroes in the
 * current match: their cards that change Life counters or defeat rewards are quoted. Empty = all heroes.
 * [showRulesLink] offers "All rules" (off when opened from the Rules screen itself).
 */
@Serializable data class LevelLadderRoute(
    val heroIds: List<String> = emptyList(),
    val showRulesLink: Boolean = true,
) : NavKey

/**
 * Entry metadata for screens shown as a modal bottom sheet over the previous screen; the app's
 * scene strategy reads it. Usage: `entry<Key>(metadata = NavSheet.metadata) { … }`.
 */
object NavSheet {
    const val KEY = "goa.bottomSheet"
    val metadata: Map<String, Any> = mapOf(KEY to true)

    /**
     * Inside a sheet entry: set `value = false` while the sheet must not be dismissed (a sync
     * moving data); swipe, scrim tap and Back then do nothing and the sheet offers its own Cancel.
     * Null outside a sheet.
     */
    val LocalDismissible = staticCompositionLocalOf<MutableState<Boolean>?> { null }
}

// Card-number trivia (ADR-028, owner: trivia)

/** Trivia home: progress per hero and the quiz setup. [heroId] preselects that hero (from its page). */
@Serializable data class TriviaRoute(val heroId: String? = null) : NavKey

/**
 * A quiz round. [config] is a `TriviaConfig` as JSON (core:domain); [factKeys], when not empty,
 * limits the round to those facts ("Practice the ones I missed"). [challenge]: the fixed Challenge
 * quiz (ADR-032); [config] is then ignored.
 */
@Serializable data class TriviaQuizRoute(
    val config: String,
    val factKeys: List<String> = emptyList(),
    val challenge: Boolean = false,
) : NavKey

/** The trivia leaderboard (ADR-032): Challenge and Practice boards from the synced rounds. */
@Serializable data object TriviaLeaderboardRoute : NavKey

// Board sandbox (ADR-033, owner: board)

/** Saved boards and "New board". */
@Serializable data object BoardsRoute : NavKey

/**
 * Choosing a new board's line-up and map. [red]/[blue] pre-fill the teams (hero ids in pick order),
 * e.g. from a draft or a match.
 */
@Serializable data class NewBoardRoute(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    val mapId: String? = null,
) : NavKey

/**
 * A board. [boardId] opens a saved board; null creates a new board with the opening position for the
 * line-up [red]/[blue] on [mapId] (null = the default map for that many heroes) and opens it (or
 * reopens an untouched board of the same line-up and map). [name] names the new board (null = named
 * after the line-up). [matchId]: the line-up is that logged match's; when it came from a draft, the
 * heroes go in the draft's pick order.
 */
@Serializable data class BoardRoute(
    val boardId: String? = null,
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    val mapId: String? = null,
    val name: String? = null,
    val matchId: String? = null,
) : NavKey

// Play against the computer (ADR-035, owner: board)

/**
 * Setting up a game against the computer: line-up, who you control, difficulty, length, map,
 * Coach. [red]/[blue] pre-fill the teams (hero ids in pick order), e.g. after a game ("New game").
 */
@Serializable data class NewGameRoute(
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
) : NavKey

/** The game in progress (the one saved on this phone): the table, then the game-over summary. */
@Serializable data object GameRoute : NavKey

// Puzzles (ADR-035, PRODUCT §10, owner: board)

/** The puzzle list: numbered puzzles with progress, Continue at the top. */
@Serializable data object PuzzlesRoute : NavKey

/** One puzzle ([puzzleId] = `PuzzleInfo.id`, e.g. `p17`): the table, nudges, then the explanation. */
@Serializable data class PuzzleRoute(val puzzleId: String) : NavKey

// Group sync (ADR-025, owner: sync)

/** The group: start or join one; once joined, invite, sync, share or import a sync file, leave. */
@Serializable data object GroupSyncRoute : NavKey

/**
 * The sync session, shown as a bottom sheet ([NavSheet]). [role]: "host", "join" or null (choose).
 * [firstSync]: right after joining a group; "Which player are you?" follows. [fromGroup]: opened
 * from the Group sync screen (its file fallback is right below the sheet).
 */
@Serializable data class SyncSessionRoute(
    val role: String? = null,
    val firstSync: Boolean = false,
    val fromGroup: Boolean = false,
) : NavKey

/** "Which player are you?": links this phone to a player (pick one, or add yourself). */
@Serializable data object WhoAreYouRoute : NavKey

/** Opens a sync file: [uri] is a content URI from the file picker, the share sheet or a file manager. */
@Serializable data class SyncFileImportRoute(val uri: String) : NavKey

// Settings
@Serializable data object SettingsRoute : NavKey

@Serializable data object AboutRoute : NavKey

@Serializable data object RatingSettingsRoute : NavKey
