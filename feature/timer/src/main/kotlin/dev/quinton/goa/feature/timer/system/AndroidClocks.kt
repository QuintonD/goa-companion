package dev.quinton.goa.feature.timer.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import javax.inject.Inject

/** `elapsedRealtime` keeps counting in deep sleep and Doze; it resets only on reboot. */
class SystemElapsedClock @Inject constructor() : ElapsedClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}

class SystemWallClock @Inject constructor() : WallClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Identifies the current boot so a persisted session can be rebased after a reboot. */
fun interface BootCountProvider {
    fun bootCount(): Int?
}

class SettingsBootCountProvider @Inject constructor(@ApplicationContext private val context: Context) :
    BootCountProvider {
    override fun bootCount(): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()
}
