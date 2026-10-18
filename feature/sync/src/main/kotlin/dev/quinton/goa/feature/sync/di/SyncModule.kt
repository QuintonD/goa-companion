package dev.quinton.goa.feature.sync.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.domain.sync.protocol.SyncTransport
import dev.quinton.goa.feature.sync.PhoneName
import dev.quinton.goa.feature.sync.importfile.SyncFileReader
import dev.quinton.goa.feature.sync.nearby.NearbySyncTransport
import dev.quinton.goa.feature.sync.share.SyncFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A transport for one sync session; [shutdown] stops every radio activity it started. */
interface SessionTransport : SyncTransport {
    fun shutdown()
}

/** Creates the transport for a session (Nearby in the app, in-memory in tests). */
fun interface SyncTransportFactory {
    fun create(): SessionTransport
}

@Module
@InstallIn(SingletonComponent::class)
internal object SyncModule {
    @Provides
    fun transports(@ApplicationContext context: Context): SyncTransportFactory = SyncTransportFactory {
        val nearby = NearbySyncTransport(context)
        object : SessionTransport, SyncTransport by nearby {
            override fun shutdown() = nearby.shutdown()
        }
    }

    @Provides
    fun phoneName(@ApplicationContext context: Context): PhoneName = PhoneName {
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()?.trim()?.takeIf(String::isNotEmpty)
            ?: Build.MODEL?.trim()?.takeIf(String::isNotEmpty)
            ?: "Phone"
    }

    @Provides
    fun fileReader(@ApplicationContext context: Context): SyncFileReader = SyncFileReader { uri ->
        withContext(Dispatchers.IO) { SyncFiles.read(context, uri.toUri()) }
    }
}
