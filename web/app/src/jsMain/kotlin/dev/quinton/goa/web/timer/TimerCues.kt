package dev.quinton.goa.web.timer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * The timer's cues in the browser (the phone's `CuePlayer`): short generated tones through Web Audio,
 * a short beep for a warning threshold and a long double beep at zero, and — because iPhones have no
 * vibration for web pages — a flash of the screen where the phone would vibrate.
 *
 * Safari only plays sound after a touch: the audio is unlocked on the first tap anywhere in the app
 * (every tap that starts a timer is one). The ring/silent switch mutes web audio on an iPhone; the
 * flash still shows.
 */
class TimerCues {
    private var context: dynamic = null

    /** Counts flashes; the running timer animates when it changes. */
    var flashes: Int by mutableStateOf(0)
        private set

    /** Last flash was the final ("time's up") one. */
    var finalFlash: Boolean by mutableStateOf(false)
        private set

    init {
        val unlock: (Event) -> Unit = { unlock() }
        UNLOCK_EVENTS.forEach { document.addEventListener(it, unlock, js("({ capture: true, passive: true })")) }
    }

    /** Creates (or wakes) the audio context inside a user gesture, so later cues can sound. */
    fun unlock() {
        val ctx = context ?: create() ?: return
        if (ctx.state != "running") runCatching { ctx.resume() }
        if (!unlockedOnce) {
            unlockedOnce = true
            runCatching {
                // A silent sample: iOS unlocks output only after something played inside the gesture.
                val buffer = ctx.createBuffer(1, 1, SAMPLE_RATE)
                val source = ctx.createBufferSource()
                source.buffer = buffer
                source.connect(ctx.destination)
                source.start(0)
            }
        }
    }

    private var unlockedOnce = false

    private fun create(): dynamic {
        if (window.asDynamic().AudioContext == undefined &&
            window.asDynamic().webkitAudioContext == undefined
        ) {
            return null
        }
        context = runCatching { js("new (window.AudioContext || window.webkitAudioContext)()") }.getOrNull()
        return context
    }

    /** [secondsLeft] = the threshold crossed; 0 = time's up. [flash] stands in for the phone's haptics. */
    fun cue(secondsLeft: Int, sound: Boolean, flash: Boolean) {
        val final = secondsLeft == 0
        if (sound) tones(final)
        if (flash) {
            finalFlash = final
            flashes++
        }
    }

    private fun tones(final: Boolean) {
        val ctx = context ?: return
        if (ctx.state != "running") runCatching { ctx.resume() }
        runCatching {
            val start = (ctx.currentTime as Number).toDouble()
            if (final) {
                beep(ctx, start, FINAL_FIRST_HZ, FINAL_BEEP_S)
                beep(ctx, start + FINAL_GAP_S, FINAL_SECOND_HZ, FINAL_LAST_BEEP_S)
            } else {
                beep(ctx, start, WARNING_HZ, WARNING_S)
            }
        }
    }

    private fun beep(ctx: dynamic, at: Double, hz: Double, seconds: Double) {
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.value = hz
        // A soft attack and release: no click at either end.
        gain.gain.setValueAtTime(0.0001, at)
        gain.gain.exponentialRampToValueAtTime(VOLUME, at + RAMP_S)
        gain.gain.setValueAtTime(VOLUME, at + seconds - RAMP_S)
        gain.gain.exponentialRampToValueAtTime(0.0001, at + seconds)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(at)
        osc.stop(at + seconds + RAMP_S)
    }

    private companion object {
        val UNLOCK_EVENTS = listOf("pointerdown", "touchend", "keydown")
        const val SAMPLE_RATE = 22_050
        const val VOLUME = 0.6
        const val RAMP_S = 0.015
        const val WARNING_HZ = 880.0
        const val WARNING_S = 0.15
        const val FINAL_FIRST_HZ = 880.0
        const val FINAL_SECOND_HZ = 1_175.0
        const val FINAL_BEEP_S = 0.25
        const val FINAL_GAP_S = 0.3
        const val FINAL_LAST_BEEP_S = 0.3
    }
}
