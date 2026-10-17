package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match

/**
 * DOMAIN.md's Unknown-hero rule: a seat whose hero this phone's catalog doesn't know keeps its
 * pilot but counts as an "Unknown hero" seat (`heroId = null`), and draft bans, picks and pool
 * entries naming such a hero are left out, so it never appears in ratings or ban stats.
 *
 * Applied when *reading* (ratings, meta stats), never when storing: a phone with an older catalog
 * must keep the newer hero's id, or its cleared copy could win a sync tie and erase the hero on
 * every phone (ADR-025). An empty [known] set (catalog unavailable) changes nothing.
 */
object UnknownHeroes {
    fun ids(matches: List<Match>, drafts: List<DraftRecord>, known: Set<String>): Set<String> {
        if (known.isEmpty()) return emptySet()
        val used = matches.flatMap { m -> m.participants.mapNotNull { it.heroId } } +
            drafts.flatMap { d -> d.steps.map { it.heroId } + d.pool }
        return used.filterNot { it in known }.toSortedSet()
    }

    fun clear(match: Match, known: Set<String>): Match {
        if (known.isEmpty() || match.participants.all { it.heroId == null || it.heroId in known }) return match
        val seats = match.participants.map { p -> if (p.heroId in known) p else p.copy(heroId = null) }
        return match.copy(participants = seats)
    }

    fun clear(draft: DraftRecord, known: Set<String>): DraftRecord {
        if (known.isEmpty() || (draft.pool.all { it in known } && draft.steps.all { it.heroId in known })) return draft
        return draft.copy(pool = draft.pool.filter { it in known }, steps = draft.steps.filter { it.heroId in known })
    }

    fun clearMatches(matches: List<Match>, known: Set<String>): List<Match> =
        if (known.isEmpty()) matches else matches.map { clear(it, known) }

    fun clearDrafts(drafts: List<DraftRecord>, known: Set<String>): List<DraftRecord> =
        if (known.isEmpty()) drafts else drafts.map { clear(it, known) }
}
