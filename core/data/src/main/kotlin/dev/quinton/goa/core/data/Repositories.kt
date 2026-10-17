package dev.quinton.goa.core.data

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import dev.quinton.goa.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/*
 * Cross-feature contracts. Each interface has exactly one owning implementation (bound with
 * Hilt in the owner's package) and fakes in core:testing. Features depend only on these.
 */

/** Read-only, in-memory catalog loaded from bundled JSON (owner: catalog). */
interface CatalogRepository {
    /** Emits once the catalog is loaded; never re-emits unless the data version changes. */
    val catalog: Flow<Catalog>
}

/** Players (owner: players). */
interface PlayerRepository {
    fun observePlayers(includeArchived: Boolean = false): Flow<List<Player>>

    fun observePlayer(id: String): Flow<Player?>

    suspend fun upsert(player: Player)

    suspend fun archive(id: String, archived: Boolean)
}

/** The match log — source of truth for every rating and stat (owner: players). */
interface MatchRepository {
    fun observeMatches(): Flow<List<Match>>

    fun observeMatch(id: String): Flow<Match?>

    suspend fun upsert(match: Match)

    suspend fun delete(id: String)
}

/** Finished drafts, for ban stats and draft history (owner: players/database; used by draft). */
interface DraftRecordRepository {
    fun observeDrafts(): Flow<List<DraftRecord>>

    suspend fun save(record: DraftRecord)
}

/** Derived ratings and meta stats, recomputed from the match log (owner: players). */
interface RatingsRepository {
    fun observePlayerRatings(): Flow<Map<String, Rating>>

    fun observeHeroRatings(): Flow<Map<String, Rating>>

    fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>>

    fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>>

    fun observeHeroUsage(): Flow<Map<String, HeroUsage>>
}

/** App-wide settings in DataStore (owner: settings). */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
