package dev.quinton.goa.feature.board.setup

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.inOrderOf
import dev.quinton.goa.feature.board.lineup
import dev.quinton.goa.feature.board.size

/** Where a line-up offered by "Use a draft or match" comes from. */
enum class LineupSourceKind(val label: String) {
    DRAFT("Finished draft"),
    PENDING("Pending match"),
    LOGGED("Logged match"),
}

/** A line-up to fill both teams with in one tap. */
@Immutable
data class LineupSource(
    val key: String,
    val kind: LineupSourceKind,
    val at: Long,
    val lineup: BoardLineup,
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
)

/**
 * Finished drafts, pending and logged matches with heroes, newest first, at most [limit] of each.
 * A draft whose match is in the log shows the same heroes: the match alone is offered, in the
 * draft's pick order (the opening places heroes in pick order, p.20). Abandoned matches are left out.
 */
fun lineupSources(
    drafts: List<DraftRecord>,
    matches: List<Match>,
    heroes: Map<String, BoardHero>,
    limit: Int = 10,
): List<LineupSource> {
    fun source(key: String, kind: LineupSourceKind, at: Long, lineup: BoardLineup) =
        LineupSource(key, kind, at, lineup, lineup.red.map(heroes::hero), lineup.blue.map(heroes::hero))

    val inLog = matches.mapTo(HashSet()) { it.id }
    val draftOf = drafts.filter { it.matchId != null }.associateBy { it.matchId }
    val draftSources = drafts.filterNot { it.matchId != null && it.matchId in inLog }
        .sortedByDescending { it.createdAt }
        .take(limit)
        .map { draft -> source("draft:${draft.id}", LineupSourceKind.DRAFT, draft.createdAt, draft.lineup()) }
    val matchSources = matches.filter { it.status != MatchStatus.ABANDONED }
        .sortedByDescending { it.playedAt }
        .take(limit)
        .map { match ->
            val kind = if (match.status == MatchStatus.PENDING) LineupSourceKind.PENDING else LineupSourceKind.LOGGED
            source("match:${match.id}", kind, match.playedAt, match.lineup().inOrderOf(draftOf[match.id]?.lineup()))
        }
    return (matchSources + draftSources).filter { it.lineup.size > 0 }.sortedByDescending { it.at }
}
