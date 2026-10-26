package dev.quinton.goa.web.nav

/**
 * The bottom bar's destinations, in the Android app's order (core:navigation `TopLevelTab`,
 * app `GoaApp.Tabs`). [icon] names the `icons/nav_<icon>.svg` / `nav_<icon>_filled.svg` pair.
 */
enum class Tab(val label: String, val icon: String, val root: Route) {
    HEROES("Heroes", "heroes", Route.Catalog),
    TIMER("Timer", "timer", Route.Timer),
    DRAFT("Draft", "draft", Route.Draft),
    PLAYERS("Players", "players", Route.Players),
}

/**
 * Every screen of the web app, addressed by the URL hash (`#/heroes/arien`) so a deep link survives
 * a reload and "Add to Home Screen" (GitHub Pages has no server rewrites). Mirrors core:navigation's
 * keys; a stream adds its screens here, in [Routes.parse] and in `Screens.kt`.
 *
 * [tab] is the bottom-bar tab the screen belongs to; null for screens reachable from several tabs
 * (Settings, Rules): they stay on whichever tab opened them. [parent] is where Back goes when there
 * is no history to return to (a deep link opened cold).
 */
sealed interface Route {
    val path: String
    val tab: Tab?
    val parent: Route?

    /** False for full-screen table tools (no tab bar to mis-tap beside the map or a pick). */
    val showsTabBar: Boolean get() = true

    // Heroes tab
    data object Catalog : Route {
        override val path = "heroes"
        override val tab get() = Tab.HEROES
        override val parent: Route? = null
    }

    data class HeroDetail(val heroId: String) : Route {
        override val path get() = "heroes/${Routes.encode(heroId)}"
        override val tab get() = Tab.HEROES
        override val parent get() = Catalog
    }

    data class CardViewer(val heroId: String, val cardId: String) : Route {
        override val path get() = "heroes/${Routes.encode(heroId)}/cards/${Routes.encode(cardId)}"
        override val tab get() = Tab.HEROES
        override val parent get() = HeroDetail(heroId)
    }

    data class Trivia(val heroId: String? = null) : Route {
        override val path get() = if (heroId == null) "trivia" else "trivia?hero=${Routes.encode(heroId)}"
        override val tab get() = Tab.HEROES
        override val parent get() = Catalog
    }

    /**
     * A trivia round (ADR-028) or the Challenge (ADR-032): the shared `TriviaQuizRequest` in the URL,
     * so a reload or a home-screen relaunch reopens the same quiz. Full screen: no tab bar beside the
     * answer buttons.
     */
    data class TriviaQuiz(
        val config: String,
        val factKeys: List<String> = emptyList(),
        val challenge: Boolean = false,
    ) :
        Route {
        override val path get() = buildString {
            append("trivia/quiz?config=").append(Routes.encode(config))
            if (factKeys.isNotEmpty()) append("&facts=").append(Routes.encode(factKeys.joinToString(",")))
            if (challenge) append("&challenge=1")
        }
        override val tab get() = Tab.HEROES
        override val parent get() = Trivia()
        override val showsTabBar get() = false
    }

    /** The trivia leaderboard (ADR-032): Challenge and Practice boards from the group's rounds. */
    data object TriviaLeaderboard : Route {
        override val path = "trivia/leaderboard"
        override val tab get() = Tab.HEROES
        override val parent get() = Trivia()
    }

    data object Boards : Route {
        override val path = "boards"
        override val tab get() = Tab.HEROES
        override val parent get() = Catalog
    }

    data class Board(val boardId: String) : Route {
        override val path get() = "boards/${Routes.encode(boardId)}"
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
        override val showsTabBar get() = false
    }

    /**
     * Choosing a new board's line-up and map; [red]/[blue] pre-fill the teams (hero ids in pick order),
     * e.g. from a draft or a match (Android's NewBoardRoute).
     */
    data class NewBoard(
        val red: List<String> = emptyList(),
        val blue: List<String> = emptyList(),
        val mapId: String? = null,
    ) : Route {
        override val path get() = "boards/new" +
            Routes.query("red" to red.ids(), "blue" to blue.ids(), "map" to mapId)
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
    }

    /**
     * Opens the board of a line-up in one tap ("Try on the board", "Open on the board"): creates it at
     * the opening on [mapId] (null = the default map for that many heroes), or reopens an untouched
     * board of the same line-up and map, then stands in for that board (Android's BoardRoute with no id).
     * [matchId]: the line-up is that logged match's (in its draft's pick order when it came from one).
     */
    data class OpenBoard(
        val red: List<String> = emptyList(),
        val blue: List<String> = emptyList(),
        val mapId: String? = null,
        val name: String? = null,
        val matchId: String? = null,
    ) : Route {
        override val path get() = "boards/open" + Routes.query(
            "red" to red.ids(),
            "blue" to blue.ids(),
            "map" to mapId,
            "name" to name,
            "match" to matchId,
        )
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
        override val showsTabBar get() = false
    }

    /** The game against the computer in progress (this browser's): the table, then the game-over summary. */
    data object Play : Route {
        override val path = "play"
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
        override val showsTabBar get() = false
    }

    /** Setting up a game against the computer; [red]/[blue] pre-fill the teams (e.g. after a game). */
    data class NewGame(val red: List<String> = emptyList(), val blue: List<String> = emptyList()) : Route {
        override val path get() = "play/new" +
            Routes.query("red" to red.ids(), "blue" to blue.ids())
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
    }

    data object Puzzles : Route {
        override val path = "puzzles"
        override val tab get() = Tab.HEROES
        override val parent get() = Boards
    }

    data class Puzzle(val puzzleId: String) : Route {
        override val path get() = "puzzles/${Routes.encode(puzzleId)}"
        override val tab get() = Tab.HEROES
        override val parent get() = Puzzles
        override val showsTabBar get() = false
    }

    // Shared: opened from several tabs
    data object Rules : Route {
        override val path = "rules"
        override val tab: Tab? = null
        override val parent get() = Catalog
    }

    data class RulesTopic(val topicId: String) : Route {
        override val path get() = "rules/${Routes.encode(topicId)}"
        override val tab: Tab? = null
        override val parent get() = Rules
    }

    /**
     * The level & defeat ladder (W1's rules reference; the running timer opens it on Level Up).
     * [heroIds] are the heroes in play: their cards that change Life counters or defeat rewards are
     * quoted. Empty = all heroes. As Android's `LevelLadderRoute`.
     */
    data class LevelLadder(val heroIds: List<String> = emptyList()) : Route {
        override val path get() = "levels" +
            Routes.query("heroes" to heroIds.takeIf { it.isNotEmpty() }?.joinToString(","))
        override val tab: Tab? = null
        override val parent get() = Rules
    }

    data object Settings : Route {
        override val path = "settings"
        override val tab: Tab? = null
        override val parent get() = Catalog
    }

    data object About : Route {
        override val path = "settings/about"
        override val tab: Tab? = null
        override val parent get() = Settings
    }

    // Timer tab
    data object Timer : Route {
        override val path = "timer"
        override val tab get() = Tab.TIMER
        override val parent: Route? = null
    }

    /**
     * Match setup (mode, counters, Tie Breaker coin) for a pending match ([matchId]) or a fresh one.
     * [rematchOf] + [rematchKind] (a `RematchKind` name) set up a rematch instead, saved as a match
     * only when its timer starts.
     */
    data class MatchSetup(
        val matchId: String? = null,
        val rematchOf: String? = null,
        val rematchKind: String? = null,
    ) : Route {
        override val path
            get() = "timer/setup" + Routes.query("match" to matchId, "rematch" to rematchOf, "kind" to rematchKind)
        override val tab get() = Tab.TIMER
        override val parent get() = Timer
    }

    data object TimerPresets : Route {
        override val path = "timer/presets"
        override val tab get() = Tab.TIMER
        override val parent get() = Timer
    }

    /** A preset's editor; null [presetId] = a new preset. */
    data class TimerPresetEdit(val presetId: String? = null) : Route {
        override val path get() = "timer/presets/" + (presetId?.let(Routes::encode) ?: NEW)
        override val tab get() = Tab.TIMER
        override val parent get() = TimerPresets
    }

    // Draft tab
    data object Draft : Route {
        override val path = "draft"
        override val tab get() = Tab.DRAFT
        override val parent: Route? = null
    }

    /** The draft in progress (bans and picks, pass-and-play): a full-screen table tool. */
    data object DraftBoard : Route {
        override val path = "draft/board"
        override val tab get() = Tab.DRAFT
        override val parent get() = Draft
        override val showsTabBar get() = false
    }

    data object DraftMethods : Route {
        override val path = "draft/methods"
        override val tab get() = Tab.DRAFT
        override val parent get() = Draft
    }

    /** A draft method's editor; null [methodId] = a new method (a built-in opens as a copy). */
    data class DraftMethodEdit(val methodId: String? = null) : Route {
        override val path get() = "draft/methods/" + (methodId?.let(Routes::encode) ?: NEW)
        override val tab get() = Tab.DRAFT
        override val parent get() = DraftMethods
    }

    // Players tab
    data object Players : Route {
        override val path = "players"
        override val tab get() = Tab.PLAYERS
        override val parent: Route? = null
    }

    data class PlayerDetail(val playerId: String) : Route {
        override val path get() = "players/${Routes.encode(playerId)}"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Players
    }

    data object Matches : Route {
        override val path = "matches"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Players
    }

    /**
     * The match editor (W2's screen; W3 opens it from the timer). [matchId] null = a new match;
     * [prefillDurationMin] / [prefillMode] (a `GameMode` serial name) pre-fill a new one from a
     * finished timer session. [result] opens the focused "Who won?" layout. [prefillPlayedAt] (epoch
     * ms, the session's start) dates a new or pending match. [newMatchId] is the id a new match is
     * saved under (the timer reserves one so it can tell when the result was saved). As Android's
     * `MatchEditRoute`.
     */
    data class MatchEdit(
        val matchId: String? = null,
        val prefillDurationMin: Int? = null,
        val prefillMode: String? = null,
        val result: Boolean = false,
        val prefillPlayedAt: Long? = null,
        val newMatchId: String? = null,
    ) : Route {
        override val path
            get() = "matches/" + (matchId?.let(Routes::encode) ?: NEW) + Routes.query(
                "result" to if (result) "1" else null,
                "duration" to prefillDurationMin?.toString(),
                "mode" to prefillMode,
                "playedAt" to prefillPlayedAt?.toString(),
                "newId" to newMatchId,
            )

        // Opened from the Timer tab (Log result) and the match log: stays on the tab that opened it.
        override val tab: Tab? = null
        override val parent get() = Matches
    }

    /** A player profile's editor (W2's screen; the draft opens it for a new player). */
    data class PlayerEdit(val playerId: String? = null) : Route {
        override val path get() = playerId?.let { "players/${Routes.encode(it)}/edit" } ?: "players/$NEW"
        override val tab: Tab? = null
        override val parent get() = playerId?.let { PlayerDetail(it) } ?: Players
    }

    data object Meta : Route {
        override val path = "meta"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Players
    }

    data object Group : Route {
        override val path = "group"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Players
    }

    /** How ratings are calculated (Settings → Ratings on the phone). */
    data object RatingSettings : Route {
        override val path = "ratings"
        override val tab: Tab? = null
        override val parent get() = Meta
    }

    /** "Which player are you?" */
    data object WhoAreYou : Route {
        override val path = "group/me"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Group
    }

    /**
     * An invite link (`#join=goa-sync:1?…`, the QR code's link, `GroupInvite.link`): the Group screen
     * takes the invite and asks first; the key never stays in the address bar or the history.
     */
    data class Join(val invite: String) : Route {
        override val path get() = "join=$invite"
        override val tab get() = Tab.PLAYERS
        override val parent get() = Group
    }

    /** A hash no screen answers to (an old bookmark): says so and offers the way home. */
    data class NotFound(val hash: String) : Route {
        override val path get() = hash
        override val tab: Tab? = null
        override val parent get() = Catalog
    }
}

/** The path segment that means "a new one" (`#/timer/presets/new`); never an id. */
private const val NEW = "new"

/** Parses and formats the `#/…` hash. Pure, so it is unit-tested without a browser. */
object Routes {
    /** The route for [hash] (`#/heroes/arien`, `/heroes/arien` or `heroes/arien`); empty = Heroes. */
    fun parse(hash: String): Route {
        val raw = hash.removePrefix("#").removePrefix("/").trimEnd('/')
        // An invite link: everything after "join=" is the invite, kept exactly as written.
        return if (raw.startsWith(JOIN)) Route.Join(raw.removePrefix(JOIN)) else parsePath(raw)
    }

    private fun parsePath(raw: String): Route {
        if (raw.isEmpty()) return Route.Catalog
        val path = raw.substringBefore('?')
        val query = parseQuery(raw.substringAfter('?', ""))
        val parts = path.split('/').map(::decode)
        if (parts.any { it.isEmpty() }) return Route.NotFound(raw)
        return when (parts.first()) {
            "heroes" -> heroes(parts, raw)
            "trivia", "levels" -> queried(parts, query)
            "boards" -> boards(parts, query)
            "play" -> play(parts, query)
            "puzzles" -> oneOrTwo(parts, Route.Puzzles) { Route.Puzzle(it) }
            "rules" -> oneOrTwo(parts, Route.Rules) { Route.RulesTopic(it) }
            "settings" -> settings(parts)
            "timer" -> timer(parts, query)
            "draft" -> draft(parts)
            "players" -> players(parts)
            "matches" -> matches(parts, query)
            "meta" -> Route.Meta.takeIf { parts.size == 1 }
            "ratings" -> Route.RatingSettings.takeIf { parts.size == 1 }
            "group" -> group(parts)
            else -> null
        } ?: Route.NotFound(raw)
    }

    /** The hash for [route], e.g. `#/heroes/arien`. */
    fun hash(route: Route): String = "#/${route.path}"

    private fun heroes(parts: List<String>, raw: String): Route? = when {
        parts.size == 1 -> Route.Catalog
        parts.size == 2 -> Route.HeroDetail(parts[1])
        parts.size == 4 && parts[2] == "cards" -> Route.CardViewer(parts[1], parts[3])
        else -> Route.NotFound(raw)
    }

    private fun boards(parts: List<String>, query: Map<String, String>): Route? = when {
        parts.size == 1 -> Route.Boards

        parts.size == 2 && parts[1] == "new" -> Route.NewBoard(
            ids(query["red"]),
            ids(query["blue"]),
            query["map"].orNull(),
        )

        parts.size == 2 && parts[1] == "open" -> Route.OpenBoard(
            ids(query["red"]),
            ids(query["blue"]),
            query["map"].orNull(),
            query["name"].orNull(),
            query["match"].orNull(),
        )

        parts.size == 2 -> Route.Board(parts[1])

        else -> null
    }

    private fun play(parts: List<String>, query: Map<String, String>): Route? = when {
        parts.size == 1 -> Route.Play
        parts.size == 2 && parts[1] == "new" -> Route.NewGame(ids(query["red"]), ids(query["blue"]))
        else -> null
    }

    /** Hero ids in a query value, comma-separated ("arien,brogan"). */
    private fun ids(value: String?): List<String> = value.orEmpty().split(',').filter { it.isNotEmpty() }

    private fun String?.orNull(): String? = this?.takeIf { it.isNotEmpty() }

    /**
     * The screens whose address carries a query: trivia (`?hero=`, a quiz's request) and the level
     * ladder (`?heroes=`); the boards' and the game's are [boards] and [play].
     */
    private fun queried(parts: List<String>, query: Map<String, String>): Route? = when (parts.first()) {
        "trivia" -> trivia(parts, query)
        else -> Route.LevelLadder(query.list("heroes")).takeIf { parts.size == 1 }
    }

    private fun trivia(parts: List<String>, query: Map<String, String>): Route? = when {
        parts.size == 1 -> Route.Trivia(query["hero"]?.takeIf { it.isNotEmpty() })

        parts.size == 2 && parts[1] == "leaderboard" -> Route.TriviaLeaderboard

        parts.size == 2 && parts[1] == "quiz" -> query["config"]?.let { config ->
            Route.TriviaQuiz(
                config = config,
                factKeys = query.list("facts"),
                challenge = query["challenge"] == "1",
            )
        }

        else -> null
    }

    private fun timer(parts: List<String>, query: Map<String, String>): Route? = when {
        parts.size == 1 -> Route.Timer

        parts.size == 2 && parts[1] == "setup" -> Route.MatchSetup(
            query.value("match"),
            query.value("rematch"),
            query.value("kind"),
        )

        parts.size == 2 && parts[1] == "presets" -> Route.TimerPresets

        parts.size == 3 && parts[1] == "presets" -> Route.TimerPresetEdit(parts[2].takeIf { it != NEW })

        else -> null
    }

    private fun draft(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Draft
        parts.size == 2 && parts[1] == "board" -> Route.DraftBoard
        parts.size == 2 && parts[1] == "methods" -> Route.DraftMethods
        parts.size == 3 && parts[1] == "methods" -> Route.DraftMethodEdit(parts[2].takeIf { it != NEW })
        else -> null
    }

    private fun players(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Players
        parts.size == 2 && parts[1] == NEW -> Route.PlayerEdit()
        parts.size == 2 -> Route.PlayerDetail(parts[1])
        parts.size == 3 && parts[2] == "edit" -> Route.PlayerEdit(parts[1])
        else -> null
    }

    private fun matches(parts: List<String>, query: Map<String, String>): Route? = when (parts.size) {
        1 -> Route.Matches

        2 -> Route.MatchEdit(
            matchId = parts[1].takeIf { it != NEW },
            prefillDurationMin = query.value("duration")?.toIntOrNull(),
            prefillMode = query.value("mode"),
            result = query["result"] == "1",
            prefillPlayedAt = query.value("playedAt")?.toLongOrNull(),
            newMatchId = query.value("newId"),
        )

        else -> null
    }

    private fun Map<String, String>.value(key: String): String? = this[key]?.takeIf { it.isNotEmpty() }

    private fun Map<String, String>.list(key: String): List<String> =
        this[key].orEmpty().split(',').filter { it.isNotEmpty() }

    /** `?a=1&b=2` from the non-null pairs (empty when none), values percent-encoded. */
    fun query(vararg pairs: Pair<String, String?>): String {
        val present = pairs.filter { it.second != null }
        if (present.isEmpty()) return ""
        return "?" + present.joinToString("&") { (k, v) -> "$k=${encode(v!!)}" }
    }

    private fun group(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Group
        parts.size == 2 && parts[1] == "me" -> Route.WhoAreYou
        else -> null
    }

    private const val JOIN = "join="

    private fun settings(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Settings
        parts.size == 2 && parts[1] == "about" -> Route.About
        else -> null
    }

    private inline fun oneOrTwo(parts: List<String>, list: Route, item: (String) -> Route): Route? = when (parts.size) {
        1 -> list
        2 -> item(parts[1])
        else -> null
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter { it.isNotEmpty() }
        .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }

    /** Percent-encodes an id for a path segment (ids are lower-case slugs, but stay safe). */
    fun encode(segment: String): String = encodeURIComponent(segment)

    private fun decode(segment: String): String = try {
        decodeURIComponent(segment)
    } catch (_: Throwable) {
        segment // a malformed escape: keep it as typed, the screen will say it's not found
    }
}

private external fun encodeURIComponent(value: String): String

private external fun decodeURIComponent(value: String): String

/** Hero ids for an address (`arien,brogan`), or nothing for none. */
private fun List<String>.ids(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")
