package dev.quinton.goa.core.data.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.quinton.goa.core.datastore.draft.DraftStore
import dev.quinton.goa.core.datastore.settings.SettingsDataSource
import dev.quinton.goa.core.datastore.timer.TimerStateStore
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.model.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Backup contributors for everything outside the match log (PRODUCT.md §3: "full data").
 * They work on the stores' own JSON documents (core:datastore), so the timer and draft features
 * keep owning their schemas and migrations: a backup carries exactly what the store holds.
 */

private val lenient = Json { ignoreUnknownKeys = true }

/** App settings: theme, owned products, sounds, haptics (and any field added to [AppSettings] later). */
internal class SettingsBackup @Inject constructor(private val source: SettingsDataSource) : BackupContributor {
    override val key = KEY

    override suspend fun export(): JsonElement = lenient.encodeToJsonElement(
        AppSettings.serializer(),
        source.settings.first(),
    )

    override suspend fun import(data: JsonElement, mode: ImportMode, useBackupSettings: Boolean) {
        if (!useBackupSettings) return
        val settings =
            runCatching { lenient.decodeFromJsonElement(AppSettings.serializer(), data) }.getOrNull() ?: return
        source.update { settings }
    }

    companion object {
        const val KEY = "settings"
    }
}

/**
 * Timer presets (the user's list; absent = bundled defaults) and the selected preset, i.e. the
 * timer defaults. `{"presets": [...] | null, "selectedPresetId": "..." | null}`.
 */
internal class TimerPresetsBackup @Inject constructor(private val store: TimerStateStore) : BackupContributor {
    override val key = KEY

    override suspend fun export(): JsonElement? {
        val presets = store.presetsJson.first()?.let(::parseArrayOrNull)
        val selected = store.selectedPresetId.first()
        if (presets == null && selected == null) return null
        return buildJsonObject {
            put(PRESETS, presets ?: JsonNull)
            put(SELECTED, selected?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    override suspend fun import(data: JsonElement, mode: ImportMode, useBackupSettings: Boolean) {
        val obj = data as? JsonObject ?: return
        val incoming = obj[PRESETS] as? JsonArray
        val selected = (obj[SELECTED] as? JsonPrimitive)?.contentOrNull
        val local = store.presetsJson.first()?.let(::parseArrayOrNull)
        val presets = when {
            mode == ImportMode.REPLACE -> incoming
            incoming == null -> local
            local == null -> incoming
            else -> JsonLists.mergeById(local, incoming)
        }
        store.writePresets(presets?.toString())
        if (useBackupSettings || mode == ImportMode.REPLACE) store.writeSelectedPresetId(selected)
    }

    companion object {
        const val KEY = "timer-presets"
        private const val PRESETS = "presets"
        private const val SELECTED = "selectedPresetId"
    }
}

/** The user's custom draft methods (the bundled ones ship with the app). */
internal class DraftMethodsBackup @Inject constructor(private val store: DraftStore) : BackupContributor {
    override val key = KEY

    override suspend fun export(): JsonElement? =
        store.customMethods.first()?.let(::parseArrayOrNull)?.takeIf { it.isNotEmpty() }?.withRepairedSummaries()

    override suspend fun import(data: JsonElement, mode: ImportMode, useBackupSettings: Boolean) {
        val incoming = (data as? JsonArray)?.withRepairedSummaries() ?: return
        val local = store.customMethods.first()?.let(::parseArrayOrNull)
        val methods = if (mode == ImportMode.REPLACE ||
            local == null
        ) {
            incoming
        } else {
            JsonLists.mergeById(local, incoming)
        }
        store.setCustomMethods(methods.toString())
    }

    companion object {
        const val KEY = "draft-methods"
        private const val SUMMARY = "summary"

        /** Old builds saved "2 ban(s)." in custom method summaries ([DraftMethod.repairSummary]). */
        private fun JsonArray.withRepairedSummaries(): JsonArray = JsonArray(
            map { element ->
                val method = element as? JsonObject
                val summary = (method?.get(SUMMARY) as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (method == null || summary == null) {
                    element
                } else {
                    JsonObject(method + (SUMMARY to JsonPrimitive(DraftMethod.repairSummary(summary))))
                }
            },
        )
    }
}

private fun parseArrayOrNull(text: String): JsonArray? = runCatching {
    Json.parseToJsonElement(text) as? JsonArray
}.getOrNull()

/** Merging lists of JSON objects that carry a string `"id"`. */
internal object JsonLists {
    /**
     * [local] in order with items of the same id taken from [incoming], then [incoming]'s new
     * items in their order. Elements without an id are kept as they are.
     */
    fun mergeById(local: JsonArray, incoming: JsonArray): JsonArray {
        val incomingById = incoming.mapNotNull { e -> idOf(e)?.let { it to e } }.toMap()
        val localIds = local.mapNotNull(::idOf).toSet()
        val merged = local.map { e -> idOf(e)?.let(incomingById::get) ?: e } +
            incoming.filter { e -> idOf(e).let { it == null || it !in localIds } }
        return JsonArray(merged)
    }

    private fun idOf(element: JsonElement): String? =
        ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull
}

@Module
@InstallIn(SingletonComponent::class)
internal interface BackupContributorsModule {
    @Binds @IntoSet
    fun settings(impl: SettingsBackup): BackupContributor

    @Binds @IntoSet
    fun timerPresets(impl: TimerPresetsBackup): BackupContributor

    @Binds @IntoSet
    fun draftMethods(impl: DraftMethodsBackup): BackupContributor

    @Binds fun appInfo(impl: AndroidAppInfo): AppInfo
}
