package dev.quinton.goa.feature.timer.system

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.feature.timer.session.TimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-syncs the timer's alarm and notification every time the app process starts (alarms are
 * dropped by a force stop). Declared in this feature's manifest so `app` needs no wiring;
 * it posts to the main looper so Hilt's application component exists by the time it runs.
 */
class TimerStartupProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TimerStartupEntryPoint {
        fun controller(): TimerController
    }

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return true
        Handler(Looper.getMainLooper()).post { resync(app) }
        return true
    }

    private fun resync(app: Context) {
        val controller = runCatching {
            EntryPointAccessors.fromApplication(app, TimerStartupEntryPoint::class.java).controller()
        }.getOrNull() ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { controller.resync() }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0
}
