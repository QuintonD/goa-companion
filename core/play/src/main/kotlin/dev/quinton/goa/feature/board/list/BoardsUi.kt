package dev.quinton.goa.feature.board.list

import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.lineupSources

/** One saved board in the list. */
data class BoardRowUi(
    val id: String,
    val name: String,
    val mapName: String,
    val mapVerified: Boolean,
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
    val updatedAt: Long,
) {
    fun team(team: Team): List<BoardHero> = if (team == Team.RED) red else blue
}

/** The game against the computer saved on this phone, as the Boards entry shows it. */
data class ResumeUi(
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
    /** "You play Arien · Computer: Hard 8". */
    val summary: String,
    val savedAt: Long,
    /** The save can't be read: opening it offers to delete it. */
    val unreadable: Boolean = false,
)

/** The puzzles as the Boards entry shows them: how many of the playable ones are solved. */
data class PuzzleHubUi(val solved: Int, val total: Int)

/**
 * Play against the computer (ADR-035): offered once heroes are playable; a game in progress to
 * resume. [puzzles]: null while no puzzle is playable (the entry says they are coming).
 */
data class PlayHubUi(val available: Boolean = false, val resume: ResumeUi? = null, val puzzles: PuzzleHubUi? = null)

sealed interface BoardsUiState {
    data object Loading : BoardsUiState

    /**
     * [latest]: the newest finished draft or match, set up in one tap. [unreadable]: the boards
     * couldn't be read (the list shows what it can, never crashes). [play]: the entry to games
     * against the computer and puzzles.
     */
    data class Ready(
        val boards: List<BoardRowUi>,
        val now: Long,
        val latest: LineupSource? = null,
        val unreadable: Boolean = false,
        val play: PlayHubUi = PlayHubUi(),
    ) : BoardsUiState
}

/** What the Boards list shows, worked out from the stored boards, games and puzzle progress (phone and web). */
object BoardsRules {
    const val MAX_NAME = 60

    fun copyName(name: String): String = "$name (copy)".take(MAX_NAME)

    /** A board's row: its map's name (or that the map isn't in this build) and its line-up. */
    fun row(board: SavedBoard, maps: List<BoardMap>, heroes: Map<String, BoardHero>): BoardRowUi {
        val map = maps.firstOrNull { it.id == board.state.mapId }
        return BoardRowUi(
            id = board.id,
            name = board.name,
            mapName = map?.name ?: "Map not in this build",
            mapVerified = map?.verified ?: true,
            red = board.lineup.red.map(heroes::hero),
            blue = board.lineup.blue.map(heroes::hero),
            updatedAt = board.updatedAt,
        )
    }

    /** The newest finished draft or match with heroes: "Set up" on it is one tap. */
    fun latest(drafts: List<DraftRecord>, matches: List<Match>, heroes: Map<String, BoardHero>): LineupSource? =
        lineupSources(drafts, matches, heroes, limit = 1).firstOrNull()

    /** How many of the playable puzzles ([ids]) are solved; null while none is playable. */
    fun puzzleHub(ids: List<String>, progress: Map<String, PuzzleProgress>): PuzzleHubUi? =
        if (ids.isEmpty()) null else PuzzleHubUi(solved = ids.count { progress[it]?.solved == true }, total = ids.size)

    /** The saved game as its entry shows it: line-up, who you play, the computer's levels. */
    fun resumeOf(stored: StoredGame, heroes: Map<String, BoardHero>): ResumeUi? = when (stored) {
        StoredGame.None -> null

        StoredGame.Unreadable -> ResumeUi(
            emptyList(),
            emptyList(),
            "This saved game can't be read.",
            0,
            unreadable = true,
        )

        is StoredGame.Saved -> {
            val seats = stored.game.game.setup.seats
            val mine = seats.filter { it.controller == Controller.Human }.map { heroes.hero(it.heroId).name }
            val levels = seats.mapNotNull { it.controller as? Controller.Ai }.map(PlayText::strength).distinct()
            ResumeUi(
                red = seats.filter { it.team == Team.RED }.map { heroes.hero(it.heroId) },
                blue = seats.filter { it.team == Team.BLUE }.map { heroes.hero(it.heroId) },
                summary = listOfNotNull(
                    "You play ${mine.joinToString(" and ")}".takeIf { mine.isNotEmpty() },
                    levels.takeIf { it.isNotEmpty() }?.let { "Computer: " + it.joinToString(", ") },
                ).joinToString(" · "),
                savedAt = stored.game.game.savedAt,
            )
        }
    }
}
