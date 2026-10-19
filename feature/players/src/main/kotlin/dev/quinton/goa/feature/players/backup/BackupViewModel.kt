package dev.quinton.goa.feature.players.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.backup.BackupDocument
import dev.quinton.goa.core.data.backup.BackupRepository
import dev.quinton.goa.core.data.backup.BackupSummary
import dev.quinton.goa.core.data.backup.ImportMode
import dev.quinton.goa.core.data.backup.InvalidBackupException
import dev.quinton.goa.core.presentation.players.Dates
import dev.quinton.goa.core.presentation.players.plural
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val players: Int = 0,
    val matches: Int = 0,
    val working: Boolean = false,
    /** A file that was read and validated, waiting for the user to choose replace/merge. */
    val pendingImport: BackupSummary? = null,
    /** One-shot result message for a snackbar. */
    val message: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupRepository,
    players: PlayerRepository,
    matches: MatchRepository,
) : ViewModel() {
    /** Overridable in tests. */
    internal var io: CoroutineDispatcher = Dispatchers.IO

    private val local = MutableStateFlow(BackupUiState())
    private var pendingDocument: BackupDocument? = null

    val uiState: StateFlow<BackupUiState> = combine(
        local,
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
    ) { state, p, m -> state.copy(players = p.size, matches = m.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String = "goa-backup-${Dates.forFileName(now)}.json"

    /** [open] opens the document the user created via the Storage Access Framework. */
    fun export(open: () -> OutputStream?) = run {
        val stream = withContext(io) { open() } ?: throw IOException("Couldn't open the file")
        withContext(io) { backup.exportTo(stream) }
        "Backup saved"
    }

    /** Reads and validates the chosen file, then asks for confirmation (see [BackupUiState.pendingImport]). */
    fun read(open: () -> InputStream?) {
        if (local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val stream = withContext(io) { open() } ?: throw IOException("Couldn't open the file")
                val document = withContext(io) { backup.read(stream) }
                pendingDocument = document
                val summary = withContext(io) { backup.summarize(document) }
                local.update { it.copy(working = false, pendingImport = summary) }
            } catch (e: InvalidBackupException) {
                local.update { it.copy(working = false, message = e.message) }
            } catch (e: IOException) {
                local.update { it.copy(working = false, message = "Couldn't read the file: ${e.message}") }
            }
        }
    }

    /**
     * [useBackupSettings]: in [ImportMode.MERGE], also take the file's rating constants and
     * settings (Replace always does).
     */
    fun confirmImport(mode: ImportMode, useBackupSettings: Boolean = mode == ImportMode.REPLACE) {
        val document = pendingDocument ?: return
        pendingDocument = null
        local.update { it.copy(pendingImport = null) }
        run {
            withContext(io) { backup.import(document, mode, useBackupSettings || mode == ImportMode.REPLACE) }
            val verb = if (mode == ImportMode.REPLACE) "Restored" else "Merged"
            val players = plural(document.players.size, "player")
            val matches = plural(document.matches.size, "match", "matches")
            "$verb $players and $matches. Ratings recalculated."
        }
    }

    fun cancelImport() {
        pendingDocument = null
        local.update { it.copy(pendingImport = null) }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun run(block: suspend () -> String) {
        if (local.value.working) return
        local.update { it.copy(working = true) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: IOException) {
                "Something went wrong: ${e.message}"
            } catch (e: IllegalStateException) {
                "Something went wrong: ${e.message}"
            }
            local.update { it.copy(working = false, message = message) }
        }
    }
}
