package dev.quinton.goa.feature.timer.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.quinton.goa.core.designsystem.theme.Spacing

/** True on API 33+ while POST_NOTIFICATIONS hasn't been granted. */
fun needsNotificationPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
    PackageManager.PERMISSION_GRANTED

/** This app's notification settings page (to turn alerts back on after a denial). */
fun notificationSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/**
 * Wraps a timer start so POST_NOTIFICATIONS is requested first (API 33+). [start] runs whatever
 * the answer: without the permission the timer still works in-app and the timer screen shows
 * the alerts-off banner. Used by every start path (Timer tab and Match setup).
 */
@Composable
fun rememberStartWithNotificationPermission(start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentStart by rememberUpdatedState(start)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentStart() }
    return remember(context, launcher) {
        {
            if (needsNotificationPermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentStart()
            }
        }
    }
}

/** Shown on the running timer when notifications are denied or blocked. Dismissible. */
@Composable
fun AlertsOffBanner(onOpenSettings: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f).padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Lock-screen alerts are off", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The timer runs, but nothing sounds or shows on the lock screen when a phase ends. " +
                        "Allow notifications to get them.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Turn on") }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
        }
    }
}
