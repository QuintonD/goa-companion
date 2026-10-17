package dev.quinton.goa.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every navigable screen. Keys live here (not in features) so any feature can open another
 * feature's screen without a module dependency; each feature registers entries for its own keys
 * via `EntryProviderScope<NavKey>.<feature>Entries(navigator)`, wired in `app`.
 */

enum class TopLevelTab { CATALOG, TIMER, DRAFT, PLAYERS }

interface Navigator {
    fun navigate(key: NavKey)

    /** Opens [key] as the root of [tab]'s stack and switches to that tab. */
    fun navigateToTab(tab: TopLevelTab, key: NavKey? = null)

    fun back()
}

// Catalog
@Serializable data object CatalogRoute : NavKey

@Serializable data class HeroDetailRoute(val heroId: String) : NavKey

@Serializable data class CardViewerRoute(val heroId: String, val cardId: String) : NavKey

// Match setup & timer
@Serializable data object TimerRoute : NavKey

/** Match setup (mode, counters, tie breaker) for a pending match, or a fresh one. */
@Serializable data class MatchSetupRoute(val matchId: String? = null) : NavKey

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
 */
@Serializable data class MatchEditRoute(
    val matchId: String? = null,
    val prefillDurationMin: Int? = null,
    val prefillMode: String? = null,
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
}

// Settings
@Serializable data object SettingsRoute : NavKey

@Serializable data object AboutRoute : NavKey

@Serializable data object RatingSettingsRoute : NavKey
