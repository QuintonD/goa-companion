package dev.quinton.goa.core.data.backup

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Versioned, human-readable backup of all user data (PRODUCT.md §3). Ratings are not included:
 * they are rebuilt from the match log after import (ADR-003).
 *
 * Versioning: bump [CURRENT_VERSION] on any incompatible change and teach [BackupCodec] to read
 * older versions. Unknown keys are ignored, so additive fields don't need a bump.
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val appVersion: String? = null,
    val players: List<Player> = emptyList(),
    val matches: List<Match> = emptyList(),
    val drafts: List<DraftRecord> = emptyList(),
    val eloConfig: EloConfig? = null,
    /** Data from [BackupContributor]s (timer presets, custom draft methods, …) keyed by [BackupContributor.key]. */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        const val FORMAT = "goa-companion-backup"
        const val CURRENT_VERSION = 1
        const val MIME_TYPE = "application/json"
    }
}

/** How an import treats data already on the phone. */
enum class ImportMode {
    /** Wipe players, matches, drafts and settings covered by the backup, then load it. */
    REPLACE,

    /** Keep local data; add backup items and overwrite local items with the same id. Local Elo config is kept. */
    MERGE,
}

/** What a backup file contains, shown before the user confirms an import. */
data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val players: Int,
    val matches: Int,
    val drafts: Int,
    val hasEloConfig: Boolean,
    val extras: Set<String>,
)

fun BackupDocument.summary() = BackupSummary(
    version = version,
    exportedAt = exportedAt,
    players = players.size,
    matches = matches.size,
    drafts = drafts.size,
    hasEloConfig = eloConfig != null,
    extras = extras.keys,
)

/** The file isn't a backup this version of the app can read. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    /** Parses and validates; throws [InvalidBackupException] with a user-readable message. */
    fun decode(text: String): BackupDocument {
        val document = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw InvalidBackupException("This file isn't a Guards of Atlantis backup.", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This file isn't a Guards of Atlantis backup.", e)
        }
        if (document.format != BackupDocument.FORMAT) {
            throw InvalidBackupException("This file isn't a Guards of Atlantis backup.")
        }
        if (document.version > BackupDocument.CURRENT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of the app. Update the app first.")
        }
        if (document.version < 1) throw InvalidBackupException("Unknown backup version ${document.version}.")
        val ids = document.players.map { it.id } + document.matches.map { it.id } + document.drafts.map { it.id }
        if (document.players.distinctBy { it.id }.size != document.players.size ||
            document.matches.distinctBy { it.id }.size != document.matches.size ||
            document.drafts.distinctBy { it.id }.size != document.drafts.size ||
            ids.any { it.isBlank() }
        ) {
            throw InvalidBackupException("The backup contains duplicate or empty ids.")
        }
        return document
    }
}
