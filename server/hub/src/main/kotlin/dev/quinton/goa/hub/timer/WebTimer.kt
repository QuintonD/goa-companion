package dev.quinton.goa.hub.timer

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.MatchDuration
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.presentation.timer.TimerCommand
import dev.quinton.goa.core.presentation.timer.TimerCommands
import dev.quinton.goa.core.presentation.timer.TimerPresets
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.push.PushPayload
import dev.quinton.goa.hub.store.DeviceSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * This browser's running timer (ADR-030 §9): the phone's [TimerSnapshot], on the hub's monotonic
 * timeline with its wall-clock anchor, plus the phase end that last alerted (so each phase end
 * alerts at most once, as the phone's controller dedupes by step key).
 */
@Serializable
data class WebTimerState(val snapshot: TimerSnapshot, val alertedKey: String? = null)

/** Tonight's turn length for one preset (the phone keeps it in memory until changed or saved). */
@Serializable
data class WebTonight(val presetId: String, val seconds: Int)

/**
 * This browser's presets, as a phone keeps them in its timer DataStore: the saved list (null = the
 * bundled presets, upgraded on read like the phone's), the selected preset, tonight's turn length,
 * and how many heroes sit at the table for a timer started without a match.
 */
@Serializable
data class WebTimerPresets(
    val presets: List<TimerPreset>? = null,
    val selectedPresetId: String? = null,
    val tonight: WebTonight? = null,
    val seats: Int? = null,
)

object TimerSlots {
    val Timer = DeviceSlot("timer", WebTimerState.serializer())
    val Presets = DeviceSlot("timer-presets", WebTimerPresets.serializer())
}

/** The bundled rules and presets (`catalog/match-setup.json`, `catalog/timer-presets.json`), parsed once. */
internal class TimerData(val rules: MatchRules, val presets: TimerPresetCatalog, val ladder: LevelLadder?) {
    companion object {
        private val cache = ConcurrentHashMap<HubCatalog, TimerData>()

        fun of(catalog: HubCatalog): TimerData = cache.getOrPut(catalog) {
            TimerData(
                MatchRules.parse(catalog.readText("catalog/match-setup.json")),
                TimerJson.parseCatalog(catalog.readText("catalog/timer-presets.json")),
                runCatching { RulesReference.parse(catalog.readText(RulesReference.PATH)).ladder }.getOrNull(),
            )
        }
    }
}

/** A device's presets as the pages show them. */
internal data class PresetsView(
    val list: List<TimerPreset>,
    val selectedId: String,
    val tonight: WebTonight?,
    val seats: Int,
) {
    val selected: TimerPreset get() = list.first { it.id == selectedId }

    /** Tonight's turn length for the selected preset, if one is set. */
    val tonightSeconds: Int? get() = tonight?.takeIf { it.presetId == selectedId }?.seconds
}

/** What a command did: the session after it (null = none) and an alert the page should give now. */
internal data class TimerOutcome(val state: WebTimerState?, val alert: String? = null)

/**
 * The web timer's controller, the hub's counterpart of the phone's TimerController: every command
 * re-syncs the stored session first (rebased onto this process's clock, auto-advanced past every
 * phase end that passed, each new step starting exactly when the previous one hit zero), then runs
 * the same [TimerEngine] through [TimerCommands], writes it back, and re-arms the Web Push for the
 * browser's next phase ends. Reads ([read]) re-sync in memory only; nothing changes on a GET.
 */
internal class WebTimer(private val ctx: HubContext) {
    private val clock get() = ctx.clock
    val engine = TimerEngine(ctx.clock, ctx.clock)
    val data: TimerData get() = TimerData.of(ctx.catalog)

    // ---- presets -------------------------------------------------------------------------------

    fun presets(deviceId: String): PresetsView {
        val stored = ctx.deviceStore.read(deviceId, TimerSlots.Presets) ?: WebTimerPresets()
        val list = TimerPresets.effective(stored.presets, data.presets)
        return PresetsView(
            list = list,
            selectedId = TimerPresets.selectedId(list, stored.selectedPresetId, data.presets),
            tonight = stored.tonight,
            seats = (stored.seats ?: DEFAULT_SEATS).coerceIn(MIN_SEATS, MAX_SEATS),
        )
    }

    /** Read-modify-write of the stored presets; [transform] gets the effective list (never empty). */
    suspend fun updatePresets(deviceId: String, transform: (WebTimerPresets, List<TimerPreset>) -> WebTimerPresets) {
        ctx.deviceStore.update(deviceId, TimerSlots.Presets) { old ->
            val current = old ?: WebTimerPresets()
            transform(current, TimerPresets.effective(current.presets, data.presets))
        }
    }

    // ---- the session ---------------------------------------------------------------------------

    /** The stored session re-synced to now, in memory (rebased, due phase ends passed). */
    fun read(deviceId: String): TimerSnapshot? =
        ctx.deviceStore.read(deviceId, TimerSlots.Timer)?.snapshot?.let { engine.advanceDue(rebase(it)) }

    suspend fun start(
        deviceId: String,
        preset: TimerPreset,
        actionSlots: Int,
        matchId: String? = null,
        mode: GameMode? = null,
        tieBreaker: Team? = null,
    ): TimerSnapshot = lock(deviceId).withLock {
        ctx.deviceStore.read(deviceId, TimerSlots.Timer)?.let {
            recordDuration(rebase(it.snapshot), onlyIfMissing = true)
        }
        val tonight = presets(deviceId).tonight?.takeIf { it.presetId == preset.id }?.seconds
        val snapshot = engine.start(
            sessionId = UUID.randomUUID().toString(),
            // Every start path (Timer home, Match setup) gets tonight's turn length.
            preset = TimerPresets.withTonight(preset, tonight),
            endOfRound = data.rules.endOfRound,
            actionSlots = actionSlots,
            matchId = matchId,
            bootCount = BOOT,
            mode = mode,
            tieBreaker = tieBreaker,
        )
        val state = WebTimerState(snapshot)
        ctx.deviceStore.write(deviceId, TimerSlots.Timer, state)
        pushes(deviceId, state)
        snapshot
    }

    /**
     * Runs [command] after re-syncing. [command] null = only re-sync (the page came back into view):
     * a phase end passed while nothing was counting on screen alerts once, in the page, unless a
     * Web Push already did. [TimerCommand.AdvanceDue] comes from a page that was counting and has
     * just chimed itself, so it never alerts again.
     */
    suspend fun perform(deviceId: String, command: TimerCommand?): TimerOutcome = lock(deviceId).withLock {
        val stored = ctx.deviceStore.read(deviceId, TimerSlots.Timer) ?: return@withLock TimerOutcome(null)
        val before = rebase(stored.snapshot)
        val synced = engine.advanceDue(before)
        var alertedKey = stored.alertedKey
        var alert: String? = null
        if (TimerCommands.advanced(before, synced)) {
            val key = TimerCommands.stepKey(before, engine.readout(before).phaseEndElapsed)
            if (key != alertedKey) {
                alertedKey = key
                val pushed = ctx.push.subscriptions(deviceId).isNotEmpty()
                if (command == null && !pushed) alert = "Time's up: ${TimerText.endedTitle(before.step, before.round)}"
            }
        }
        val after = command?.let { TimerCommands.apply(engine, synced, it) } ?: synced
        if (after.status == TimerStatus.ENDED && before.status != TimerStatus.ENDED) {
            recordDuration(after, onlyIfMissing = false)
        }
        val state = WebTimerState(after, alertedKey)
        ctx.deviceStore.write(deviceId, TimerSlots.Timer, state)
        pushes(deviceId, state)
        TimerOutcome(state, alert)
    }

    /**
     * The id for the new match "Log result" opens from an ended session without a linked match;
     * reserved once and kept in the session (the phone's `loggingMatchId`, ADR-023).
     */
    suspend fun reserveLoggingMatchId(deviceId: String): String? = lock(deviceId).withLock {
        val updated = ctx.deviceStore.update(deviceId, TimerSlots.Timer) { current ->
            when {
                current == null || current.snapshot.status != TimerStatus.ENDED -> current
                current.snapshot.loggingMatchId != null -> current
                else -> current.copy(snapshot = current.snapshot.copy(loggingMatchId = UUID.randomUUID().toString()))
            }
        }
        updated?.snapshot?.takeIf { it.status == TimerStatus.ENDED }?.loggingMatchId
    }

    /** Clears the session (after the summary); an ended session's duration is kept first. */
    suspend fun clear(deviceId: String) = lock(deviceId).withLock {
        ctx.deviceStore.read(deviceId, TimerSlots.Timer)?.let {
            recordDuration(rebase(it.snapshot), onlyIfMissing = true)
        }
        ctx.deviceStore.delete(deviceId, TimerSlots.Timer)
        pushes(deviceId, null)
    }

    /** True when an ended session's result was logged under its reserved id: the session is done. */
    fun loggedAlready(s: TimerSnapshot): Boolean {
        val id = s.loggingMatchId ?: return false
        return s.status == TimerStatus.ENDED && ctx.groupStore.read().matches.any { it.id == id }
    }

    /** Wall time (epoch ms) of an instant on the engine's timeline. */
    fun wallOf(elapsed: Long): Long = elapsed + (clock.nowMillis() - clock.elapsedMillis())

    /** Re-arms the push schedule for the device's current session (e.g. after it subscribed). */
    suspend fun rearm(deviceId: String) = lock(deviceId).withLock {
        pushes(deviceId, ctx.deviceStore.read(deviceId, TimerSlots.Timer))
    }

    // ---- internals -----------------------------------------------------------------------------

    private fun rebase(s: TimerSnapshot): TimerSnapshot = s.rebased(clock.elapsedMillis(), clock.nowMillis(), BOOT)

    /** Writes the ended session's duration (and mode) into its linked match, as the phone does. */
    private suspend fun recordDuration(s: TimerSnapshot, onlyIfMissing: Boolean) {
        if (s.status != TimerStatus.ENDED) return
        val id = s.matchId ?: return
        // The stored record as is (not the merge-resolved view), read and written in one step, so
        // only these two fields change.
        val minutes = MatchDuration.minutes(engine.readout(s).matchElapsedMillis)
        ctx.groupStore.editMatch(id) { match ->
            match.takeUnless { onlyIfMissing && it.durationMin != null }
                ?.copy(durationMin = minutes, mode = s.mode ?: match.mode)
        }
    }

    /**
     * Schedules a Web Push for each coming phase end of a running session (the current one, and with
     * auto-advance every timed step after it up to the next untimed one), cancels the rest. Only for a
     * browser that subscribed; text as the phone's "phase over" alert.
     */
    private suspend fun pushes(deviceId: String, state: WebTimerState?) {
        val wanted = if (ctx.push.subscriptions(deviceId).isEmpty()) emptyList() else planPushes(state?.snapshot)
        val existing = ctx.push.scheduled(deviceId).filter { it.key == PUSH_KEY || it.key.startsWith("$PUSH_KEY-") }
        existing.filter { old -> wanted.none { it.key == old.key } }.forEach { ctx.push.cancel(deviceId, it.key) }
        wanted.forEach { push ->
            val same = existing.firstOrNull { it.key == push.key }
            if (same == null || same.at != push.at || same.payload != push.payload) {
                ctx.push.schedule(deviceId, push.at, push.payload, push.key)
            }
        }
    }

    private data class PlannedPush(val key: String, val at: Long, val payload: PushPayload)

    /** The coming phase ends, worked out by the engine itself on a clock set to each end. */
    private fun planPushes(s: TimerSnapshot?): List<PlannedPush> {
        if (s == null || s.status != TimerStatus.RUNNING) return emptyList()
        val offset = clock.nowMillis() - clock.elapsedMillis()
        val at = SteppedClock()
        val sim = TimerEngine(at, at)
        val out = mutableListOf<PlannedPush>()
        var current: TimerSnapshot? = s
        while (current != null && out.size < MAX_PUSHES) {
            val end = sim.readout(current).phaseEndElapsed ?: break
            at.now = end
            val next = sim.advanceDue(current)
            val title = "Time's up: ${TimerText.endedTitle(current.step, current.round)}"
            val body = TimerText.alertBody(next)
            val key = if (out.isEmpty()) PUSH_KEY else "$PUSH_KEY-${out.size + 1}"
            out += PlannedPush(key, end + offset, PushPayload(title, body, url = TimerRoutes.RUN, tag = PUSH_KEY))
            // Without auto-advance the step runs into overtime: one phase end only.
            current = next.takeIf { it !== current && s.preset.autoAdvance }
        }
        return out.filter { it.at > clock.nowMillis() }
    }

    /** A clock the push planner moves to each phase end. */
    private class SteppedClock(var now: Long = 0) : ElapsedClock, WallClock {
        override fun elapsedMillis(): Long = now

        override fun nowMillis(): Long = now
    }

    companion object {
        const val DEFAULT_SEATS = 4
        const val MIN_SEATS = 2
        const val MAX_SEATS = 10
        const val PUSH_KEY = "timer"
        private const val MAX_PUSHES = 8

        /**
         * This hub process's "boot": a stored session from an earlier process is rebased through its
         * wall-clock anchor, as the phone rebases after a reboot (the monotonic clock restarted).
         */
        private val BOOT = Random.nextInt()

        private val locks = ConcurrentHashMap<String, Mutex>()

        /** One command at a time per browser, so the session and its push schedule stay in step. */
        private fun lock(deviceId: String) = locks.computeIfAbsent(deviceId) { Mutex() }
    }
}
