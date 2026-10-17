package dev.quinton.goa.feature.timer.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.feature.timer.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sounds the background "phase over" alert itself: the bundled chime once and a vibration, both
 * with alarm usage (audible on a phone set to silent or vibrate, and in Do Not Disturb's alarm
 * lane), following Settings → Sounds and Haptics. The notification stays the silent visual part:
 * the system may auto-group it with the countdown and mark it silent, which made the old
 * channel-sounded alert mute (ADR-018 addendum).
 */
interface PhaseAlertSounder {
    fun sound(style: AlertStyle)

    /** Suspends until the chime started by [sound] has finished (the alarm receiver waits for it). */
    suspend fun awaitDone()
}

@Singleton
class AndroidPhaseAlertSounder @Inject constructor(@ApplicationContext private val context: Context) :
    PhaseAlertSounder {
    private val alarm: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Volatile private var playing: CompletableDeferred<Unit>? = null

    override fun sound(style: AlertStyle) {
        if (style.haptics) vibrate()
        if (style.sound) play()
    }

    override suspend fun awaitDone() {
        val done = playing ?: return
        withTimeoutOrNull(MAX_PLAY_MS) { done.await() }
    }

    private fun play() {
        val audio = context.getSystemService(AudioManager::class.java)
        val player = runCatching {
            MediaPlayer.create(context, R.raw.phase_chime, alarm, audio?.generateAudioSessionId() ?: 0)
        }.getOrNull() ?: return
        val focus = audio?.let { requestFocus(it) }
        val done = CompletableDeferred<Unit>()
        playing = done
        fun finish() {
            if (done.isCompleted) return
            runCatching { player.release() }
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus)
            done.complete(Unit)
        }
        player.setOnCompletionListener { finish() }
        player.setOnErrorListener { _, _, _ ->
            finish()
            true
        }
        runCatching { player.start() }.onFailure { finish() }
    }

    /** Ducks music for the 1.3 s chime; playback doesn't depend on getting focus. */
    private fun requestFocus(audio: AudioManager): AudioFocusRequest? = runCatching {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarm)
            .build()
            .also { audio.requestAudioFocus(it) }
    }.getOrNull()

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarm)
        }
    }

    companion object {
        val VIBRATION = longArrayOf(0, 400, 200, 400, 200, 700)

        /** The chime is 1.3 s; never hold the alarm broadcast longer than this. */
        private const val MAX_PLAY_MS = 4_000L
    }
}
