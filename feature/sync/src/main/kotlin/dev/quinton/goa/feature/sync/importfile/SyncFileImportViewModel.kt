package dev.quinton.goa.feature.sync.importfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncMerge
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Reads a picked or received file's bytes (Android: the content resolver). */
fun interface SyncFileReader {
    suspend fun read(uri: String): ByteArray
}

sealed interface ImportStage {
    data object Reading : ImportStage

    /** The file can't be used here; [problem] null = unreadable. */
    data class Failed(val message: String, val problem: SyncFileProblem?) : ImportStage

    /** What merging the file would change on this phone. */
    data class Ready(val groupName: String, val players: Int, val matches: Int, val changes: SyncChanges) : ImportStage

    data object Importing : ImportStage

    data class Done(val changes: SyncChanges) : ImportStage
}

data class SyncFileImportUiState(val stage: ImportStage = ImportStage.Reading)

/**
 * "Import sync file" (ADR-025): opens a file shared by another phone of the group, shows what it
 * would change, and merges it in exactly like a Nearby sync (later edit wins, tombstones apply).
 * Files from another group, or that this phone's key can't open, get a clear error.
 */
@HiltViewModel(assistedFactory = SyncFileImportViewModel.Factory::class)
class SyncFileImportViewModel @AssistedInject constructor(
    @Assisted private val uri: String,
    private val reader: SyncFileReader,
    private val groups: GroupRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(uri: String): SyncFileImportViewModel
    }

    private val state = MutableStateFlow(SyncFileImportUiState())
    val uiState: StateFlow<SyncFileImportUiState> = state.asStateFlow()
    private var document: SyncDocument? = null

    init {
        viewModelScope.launch { open() }
    }

    @Suppress("SwallowedException") // an unreadable file becomes the Failed stage
    private suspend fun open() {
        val group = groups.state.first().group
        val stage = try {
            val doc = SyncCodec.decodeFile(reader.read(uri), group)
            document = doc
            val local = sync.export()
            ImportStage.Ready(
                groupName = group?.name.orEmpty(),
                // As the app shows them: merged-away duplicates and archived players aren't counted.
                players = PlayerMerges.shownCount(doc.players, doc.playerMerges),
                matches = doc.matches.size,
                changes = SyncMerge.changes(local, SyncMerge.merge(local, doc)),
            )
        } catch (e: SyncFileException) {
            ImportStage.Failed(e.message.orEmpty(), e.problem)
        } catch (e: IOException) {
            ImportStage.Failed("This file can't be read. Save it again and pick it from the file list.", null)
        }
        state.value = SyncFileImportUiState(stage)
    }

    fun confirm() {
        val doc = document ?: return
        if (state.value.stage !is ImportStage.Ready) return
        state.value = SyncFileImportUiState(ImportStage.Importing)
        viewModelScope.launch {
            state.value = SyncFileImportUiState(ImportStage.Done(sync.apply(doc).changes))
        }
    }
}
