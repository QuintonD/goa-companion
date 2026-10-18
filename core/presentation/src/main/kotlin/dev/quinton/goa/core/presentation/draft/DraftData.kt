package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.draft.DraftMethodKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ids, seeds and time for drafts — injectable so tests are deterministic. */
interface DraftEnvironment {
    fun newId(): String

    fun newSeed(): Long

    fun nowMillis(): Long
}

/**
 * The in-progress draft: a hero board, or the official Player Draft. Kept by the device that runs
 * it (the phone's DataStore, the hub's per-browser slot); never synced.
 */
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

    /**
     * An editable custom copy of [methodId] ("House draft (copy)"), to be saved by the caller;
     * null if there is no such method.
     */
    fun duplicate(methodId: String, newId: String): DraftMethod? = find(methodId)?.let { source ->
        source.copy(
            id = "custom-$newId",
            name = "${source.name} (copy)",
            kind = DraftMethodKind.CUSTOM,
            origin = MethodOrigin.CUSTOM,
            notes = listOf("Based on ${source.name}."),
        )
    }

    companion object {
        val EMPTY = MethodLibrary(emptyList(), emptyList(), "")

        /** The bundled file plus the user's methods, with old summaries repaired as they are read. */
        fun of(bundled: DraftMethodsFile, custom: List<DraftMethod>): MethodLibrary = MethodLibrary(
            builtIn = bundled.methods,
            custom = custom.map { it.copy(summary = DraftMethod.repairSummary(it.summary)) },
            defaultId = bundled.defaultMethodId,
        )

        /** `catalog/draft-methods.json`, or an empty file if it can't be read (the app still opens). */
        fun decodeBundled(text: String?): DraftMethodsFile =
            text?.let { runCatching { DraftJson.decodeMethods(it) }.getOrNull() }
                ?: DraftMethodsFile(0, "", emptyList())
    }
}

/** A custom method list with [method] added, or replacing the one with its id (in place). */
fun List<DraftMethod>.withSaved(method: DraftMethod): List<DraftMethod> =
    if (any { it.id == method.id }) map { if (it.id == method.id) method else it } else this + method
