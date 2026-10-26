package dev.quinton.goa.feature.draft.data

import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.feature.draft.setup.SetupForm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/*
 * The draft's stored state, shared by the phone (DataStore) and the web app (IndexedDB): the same
 * JSON on both (`DraftJson.lenient`), ADR-043 "Table, W3".
 */

/** The in-progress draft: a hero board, or the official Player Draft. */
@Serializable
sealed interface DraftProgress {
    val id: String

    @Serializable
    @SerialName("board")
    data class Board(
        val session: DraftSession,
        /** The Player Draft this board was handed off from, so its last captain pick can be undone. */
        val playerDraft: PlayerDraftSession? = null,
    ) : DraftProgress {
        override val id: String get() = session.id
    }

    @Serializable
    @SerialName("players")
    data class Players(val session: PlayerDraftSession) : DraftProgress {
        override val id: String get() = session.id
    }
}

/** Bundled + custom methods. Built-ins come first, in file order. */
data class MethodLibrary(val builtIn: List<DraftMethod>, val custom: List<DraftMethod>, val defaultId: String) {
    val all: List<DraftMethod> get() = builtIn + custom

    fun find(id: String?): DraftMethod? = all.firstOrNull { it.id == id }

    val default: DraftMethod? get() = find(defaultId) ?: all.firstOrNull()

    companion object {
        val EMPTY = MethodLibrary(emptyList(), emptyList(), "")
    }
}

/**
 * The stored formats of the draft (the in-progress draft, the setup form, the group's own methods):
 * one codec for the phone's DataStore and the web app's IndexedDB. Lenient: a newer build's fields are
 * ignored, an unreadable value reads as absent rather than crashing.
 */
object DraftStorage {
    private val json = DraftJson.lenient
    private val methodList = ListSerializer(DraftMethod.serializer())

    fun encodeProgress(progress: DraftProgress): String = json.encodeToString(DraftProgress.serializer(), progress)

    fun decodeProgress(text: String?): DraftProgress? =
        text?.let { runCatching { json.decodeFromString(DraftProgress.serializer(), it) }.getOrNull() }

    fun encodeSetupForm(form: SetupForm): String = json.encodeToString(SetupForm.serializer(), form)

    fun decodeSetupForm(text: String?): SetupForm? =
        text?.let { runCatching { json.decodeFromString(SetupForm.serializer(), it) }.getOrNull() }

    fun encodeCustomMethods(methods: List<DraftMethod>): String = json.encodeToString(methodList, methods)

    /** The group's own methods, with summaries saved by older builds repaired ([DraftMethod.repairSummary]). */
    fun decodeCustomMethods(text: String?): List<DraftMethod> =
        text?.let { runCatching { json.decodeFromString(methodList, it) }.getOrNull() }.orEmpty()
            .map { it.copy(summary = DraftMethod.repairSummary(it.summary)) }

    /** [methods] with [method] saved: replaced in place, or added at the end. */
    fun upsert(methods: List<DraftMethod>, method: DraftMethod): List<DraftMethod> =
        if (methods.any { it.id == method.id }) {
            methods.map {
                if (it.id ==
                    method.id
                ) {
                    method
                } else {
                    it
                }
            }
        } else {
            methods + method
        }

    /** The bundled `catalog/draft-methods.json`; an unreadable file gives no methods rather than a crash. */
    fun bundled(text: String?): DraftMethodsFile =
        text?.let { runCatching { DraftJson.decodeMethods(it) }.getOrNull() } ?: DraftMethodsFile(0, "", emptyList())
}
