package dev.quinton.goa.feature.sync.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing

/** Above this font scale compact rows stack (DESIGN.md, Large text). */
private const val LARGE_FONT_SCALE = 1.3f

class HubActions(
    val onSave: (String) -> Unit = {},
    val onClearAddressError: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

/**
 * The group's hub on the Group screen (ADR-030 §5): add its address, or see how the last
 * automatic sync went, sync now, retry, or remove it. Sits under a "Hub" section header.
 */
@Composable
fun HubCard(hub: HubUi, actions: HubActions, modifier: Modifier = Modifier) {
    GoaCard(modifier.fillMaxWidth()) {
        if (hub.url == null) AddHub(hub, actions) else HubStatus(hub, actions)
    }
}

@Composable
private fun AddHub(hub: HubUi, actions: HubActions) {
    var address by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Cloud,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(24.dp)),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            HubText.EXPLAINER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.size(Spacing.md))
    OutlinedTextField(
        value = address,
        onValueChange = {
            address = it.trim()
            actions.onClearAddressError()
        },
        label = { Text("Hub address") },
        placeholder = { Text("goa.your-tailnet.ts.net") },
        singleLine = true,
        isError = hub.addressError != null,
        supportingText = hub.addressError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (address.isNotBlank()) actions.onSave(address) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedButton(
        onClick = { actions.onSave(address) },
        enabled = address.isNotBlank(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Save hub") }
}

@Composable
private fun HubStatus(hub: HubUi, actions: HubActions) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val (icon, tint) = when {
        hub.syncing -> Icons.Outlined.CloudSync to MaterialTheme.colorScheme.secondary
        hub.hasProblem -> Icons.Outlined.CloudOff to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.CloudDone to MaterialTheme.colorScheme.secondary
    }
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            // Long addresses wrap after a dot, never mid-label.
            Text(hub.host.orEmpty().replace(".", ".\u200B"), style = MaterialTheme.typography.titleMedium)
            Text(
                hub.status.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Large text: the address needs the full width, so the icon goes above it.
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        StatusIcon(icon, tint)
        Spacer(Modifier.size(Spacing.sm))
        title(Modifier.fillMaxWidth())
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(icon, tint)
            Spacer(Modifier.width(Spacing.md))
            title(Modifier.weight(1f))
        }
    }
    hub.error?.let { error ->
        Spacer(Modifier.size(Spacing.md))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.ErrorOutline,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp).size(scaledIconSize(20.dp)),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                val detail = listOfNotNull(
                    hub.errorAgo?.replaceFirstChar(Char::uppercase),
                    HubText.RETRYING.takeIf { hub.retrying },
                ).joinToString(". ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.size(Spacing.md))
    OutlinedButton(
        onClick = actions.onSyncNow,
        enabled = !hub.syncing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (hub.syncing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Sync, null)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(if (hub.hasProblem) "Retry" else "Sync with hub")
    }
    Spacer(Modifier.size(Spacing.sm))
    Text(
        HubText.AUTOMATIC,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { confirmRemove = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Remove hub")
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove the hub?",
            message = "This phone stops syncing with ${hub.host} on its own. Nothing is deleted, and Nearby " +
                "sync and sync files still work. You can add the hub again any time.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemove = false
                actions.onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(scaledIconSize(32.dp)))
}
