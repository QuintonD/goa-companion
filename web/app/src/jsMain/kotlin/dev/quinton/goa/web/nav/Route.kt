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

    data object Play : Route {
        override val path = "play"
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

    // Draft tab
    data object Draft : Route {
        override val path = "draft"
        override val tab get() = Tab.DRAFT
        override val parent: Route? = null
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

    /** New player ([playerId] null) or edit one. */
    data class PlayerEdit(val playerId: String? = null) : Route {
        override val path get() = if (playerId == null) "players/new" else "players/${Routes.encode(playerId)}/edit"
        override val tab get() = Tab.PLAYERS
        override val parent get() = if (playerId == null) Players else PlayerDetail(playerId)
    }

    /** Log a match ([matchId] null), edit one, or [result]: the focused "Who won?" of a pending one. */
    data class MatchEdit(val matchId: String? = null, val result: Boolean = false) : Route {
        override val path get() = when {
            matchId == null -> "matches/new"
            result -> "matches/${Routes.encode(matchId)}?result=1"
            else -> "matches/${Routes.encode(matchId)}"
        }
        override val tab get() = Tab.PLAYERS
        override val parent get() = Matches
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
            "trivia" -> if (parts.size == 1) Route.Trivia(query["hero"]?.takeIf { it.isNotEmpty() }) else null
            "boards" -> oneOrTwo(parts, Route.Boards) { Route.Board(it) }
            "play" -> Route.Play.takeIf { parts.size == 1 }
            "puzzles" -> oneOrTwo(parts, Route.Puzzles) { Route.Puzzle(it) }
            "rules" -> oneOrTwo(parts, Route.Rules) { Route.RulesTopic(it) }
            "settings" -> settings(parts)
            "timer" -> Route.Timer.takeIf { parts.size == 1 }
            "draft" -> Route.Draft.takeIf { parts.size == 1 }
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

    private fun players(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Players
        parts.size == 2 && parts[1] == "new" -> Route.PlayerEdit()
        parts.size == 2 -> Route.PlayerDetail(parts[1])
        parts.size == 3 && parts[2] == "edit" -> Route.PlayerEdit(parts[1])
        else -> null
    }

    private fun group(parts: List<String>): Route? = when {
        parts.size == 1 -> Route.Group
        parts.size == 2 && parts[1] == "me" -> Route.WhoAreYou
        else -> null
    }

    private fun matches(parts: List<String>, query: Map<String, String>): Route? = when {
        parts.size == 1 -> Route.Matches
        parts.size == 2 && parts[1] == "new" -> Route.MatchEdit()
        parts.size == 2 -> Route.MatchEdit(parts[1], result = query["result"] == "1")
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
