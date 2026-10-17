package dev.quinton.goa.feature.timer.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** In-app threshold cues (only while the timer screen is visible). */
interface CuePlayer {
    /** [secondsLeft] = the cue threshold crossed; 0 = time's up. */
    fun cue(secondsLeft: Int, haptics: Boolean, sound: Boolean)

    /** A light tick for taps on the big pause target. */
    fun tap(haptics: Boolean)
}

/**
 * Generated tones and vibration patterns; warnings are short, zero is long. Tones play on the
 * alarm stream and vibration uses alarm usage, so cues still work on a phone set to silent or
 * vibrate, or with touch feedback off. The in-app Sounds/Haptics switches still gate them.
 */
@Singleton
class AndroidCuePlayer @Inject constructor(@ApplicationContext private val context: Context) : CuePlayer {
    private val tones: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME) }.getOrNull()
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun cue(secondsLeft: Int, haptics: Boolean, sound: Boolean) {
        val final = secondsLeft == 0
        if (sound) {
            tones?.startTone(
                if (final) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                if (final) FINAL_TONE_MS else WARNING_TONE_MS,
            )
        }
        if (haptics) vibrate(if (final) FINAL_PATTERN else WARNING_PATTERN, alarm = true)
    }

    override fun tap(haptics: Boolean) {
        if (haptics) vibrate(TAP_PATTERN, alarm = false)
    }

    private fun vibrate(pattern: LongArray, alarm: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        when {
            !alarm -> v.vibrate(effect)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))

            else -> {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }

    private companion object {
        const val TONE_VOLUME = 90
        const val WARNING_TONE_MS = 150
        const val FINAL_TONE_MS = 600
        val WARNING_PATTERN = longArrayOf(0, 60)
        val FINAL_PATTERN = longArrayOf(0, 250, 120, 250, 120, 450)
        val TAP_PATTERN = longArrayOf(0, 20)
    }
}
