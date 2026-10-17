package dev.quinton.goa.core.domain.timer

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.setup.MatchRules
import java.io.File

/** Fake clock pair: elapsed and wall time advance together unless told otherwise. */
class FakeClocks(var elapsed: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : ElapsedClock, WallClock {
    override fun elapsedMillis(): Long = elapsed

    override fun nowMillis(): Long = wall

    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }

    fun advanceSeconds(seconds: Double) = advance((seconds * 1000).toLong())
}

/** Reads a bundled data file from the app module so tests check the shipped data. */
fun bundledAsset(name: String): String {
    val candidates = listOf("../../app/src/main/assets/catalog/$name", "app/src/main/assets/catalog/$name")
    val file = candidates.map(::File).firstOrNull { it.exists() } ?: error("Missing bundled asset $name")
    return file.readText()
}

val bundledRules: MatchRules by lazy { MatchRules.parse(bundledAsset("match-setup.json")) }

val bundledPresets: TimerPresetCatalog by lazy { TimerJson.parseCatalog(bundledAsset("timer-presets.json")) }

val endOfRound: List<EndOfRoundStep> get() = bundledRules.endOfRound

fun phase(
    id: String,
    kind: PhaseKind,
    scope: PhaseScope,
    seconds: Int?,
    enabled: Boolean = true,
    endStepId: String? = null,
    cues: List<Int> = PhaseSpec.DEFAULT_CUES,
) = PhaseSpec(id, id.replaceFirstChar { it.uppercase() }, kind, scope, seconds, enabled, cues, endStepId)

/** The owner's table rules: Strategy 1:30, Turn 0:30 ×4, Upgrade 2:00 on Level Up, Action off. */
fun tableRules(autoAdvance: Boolean = false, actionEnabled: Boolean = false, resolve: Boolean = false) = TimerPreset(
    id = "table",
    name = "Table rules",
    autoAdvance = autoAdvance,
    phases = listOfNotNull(
        phase("strategy", PhaseKind.STRATEGY, PhaseScope.ROUND_START, 90),
        phase("turn", PhaseKind.TURN, PhaseScope.TURN, 30),
        if (resolve) phase("resolve", PhaseKind.RESOLVE, PhaseScope.TURN, null, cues = emptyList()) else null,
        phase("action", PhaseKind.ACTION, PhaseScope.TURN, 30, enabled = actionEnabled, cues = listOf(5, 0)),
        phase("upgrade", PhaseKind.UPGRADE, PhaseScope.ROUND_END, 120, endStepId = "level_up"),
    ),
)
