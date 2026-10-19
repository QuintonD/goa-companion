package dev.quinton.goa.core.presentation.timer

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import java.io.File

/** Fake clock pair: elapsed and wall time advance together. */
class FakeClocks(var elapsed: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : ElapsedClock, WallClock {
    override fun elapsedMillis(): Long = elapsed

    override fun nowMillis(): Long = wall

    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }
}

/** The shipped rules and presets, so tests exercise the real data. */
object TimerTestData {
    private fun asset(name: String): String {
        val candidates = listOf("../../app/src/main/assets/catalog/$name", "app/src/main/assets/catalog/$name")
        return candidates.map(::File).first { it.exists() }.readText()
    }

    val rules: MatchRules by lazy { MatchRules.parse(asset("match-setup.json")) }

    val presets: TimerPresetCatalog by lazy { TimerJson.parseCatalog(asset("timer-presets.json")) }
}
