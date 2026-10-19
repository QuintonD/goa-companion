package dev.quinton.goa.hub.trivia

import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaJson
import dev.quinton.goa.core.presentation.trivia.TriviaRound
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.DeviceStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * This browser's card-trivia record (ADR-028), the same two documents as the phone's own
 * `trivia_progress` DataStore file: mastery per fact key and the last quiz setup, in the phone's
 * JSON ([TriviaJson], lenient: a bad entry loses only itself). Personal practice: never synced.
 */
@Serializable
data class TriviaDocuments(
    /** Fact key → progress, as `trivia_progress_json`. */
    val progress: JsonObject? = null,
    /** The last quiz setup, as `trivia_last_config_json`. */
    val config: JsonObject? = null,
)

/** The round on screen, so a hub restart restarts it from question 1 (as the phone's saved route does). */
@Serializable
data class TriviaRoundSpec(val config: JsonObject, val factKeys: List<String> = emptyList()) {
    fun decodeConfig(): TriviaConfig = TriviaJson.decodeConfig(config) ?: TriviaConfig()

    companion object {
        fun of(
            config: TriviaConfig,
            factKeys: List<String>,
        ) = TriviaRoundSpec(TriviaJson.configElement(config), factKeys)
    }
}

object TriviaSlots {
    /** Writer: Card trivia. */
    val Trivia = DeviceSlot("trivia", TriviaDocuments.serializer())

    /** Writer: Card trivia. */
    val Round = DeviceSlot("trivia-round", TriviaRoundSpec.serializer())
}

/** Reads and writes [TriviaSlots] with the phone's semantics. */
internal class TriviaRecord(private val store: DeviceStore, private val deviceId: String) {
    fun progress(): Map<String, FactProgress> = TriviaJson.decodeProgress(
        store.read(deviceId, TriviaSlots.Trivia)?.progress,
    )

    fun lastConfig(): TriviaConfig? = TriviaJson.decodeConfig(store.read(deviceId, TriviaSlots.Trivia)?.config)

    /** Merges [updates] into the stored progress (one transaction, like `TriviaProgressRepository.record`). */
    suspend fun record(updates: Map<String, FactProgress>) {
        if (updates.isEmpty()) return
        store.update(deviceId, TriviaSlots.Trivia) { docs ->
            val current = docs ?: TriviaDocuments()
            current.copy(progress = TriviaJson.progressElement(TriviaJson.decodeProgress(current.progress) + updates))
        }
    }

    suspend fun saveConfig(config: TriviaConfig) {
        store.update(deviceId, TriviaSlots.Trivia) { docs ->
            (docs ?: TriviaDocuments()).copy(config = TriviaJson.configElement(config))
        }
    }

    /** Forgets every learned and learning number (the setup stays). */
    suspend fun reset() {
        store.update(deviceId, TriviaSlots.Trivia) { docs -> docs?.copy(progress = null) }
    }
}

/**
 * Rounds in progress, one per browser, in memory (a round is not saved, ADR-028: every answer's
 * progress is stored as it is given). Each round has its own lock: answers from two tabs of one
 * browser are applied one at a time.
 */
internal object TriviaRounds {
    class Held(val round: TriviaRound, val spec: TriviaRoundSpec) {
        val lock = Mutex()
    }

    private val rounds = ConcurrentHashMap<String, Held>()

    fun get(deviceId: String): Held? = rounds[deviceId]

    fun put(deviceId: String, held: Held) {
        if (rounds.size >= MAX_ROUNDS &&
            !rounds.containsKey(deviceId)
        ) {
            rounds.keys.firstOrNull()?.let { rounds.remove(it) }
        }
        rounds[deviceId] = held
    }

    fun remove(deviceId: String) {
        rounds.remove(deviceId)
    }

    /** Far more than a group's browsers; bounds memory if something goes wrong. */
    private const val MAX_ROUNDS = 1_000
}
