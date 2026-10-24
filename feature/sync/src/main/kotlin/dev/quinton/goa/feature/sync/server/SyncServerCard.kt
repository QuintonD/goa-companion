package dev.quinton.goa.feature.sync.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing

/** Above this font scale compact rows stack (DESIGN.md, Large text). */
private const val LARGE_FONT_SCALE = 1.3f

class SyncServerActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onSaveAddress: (String) -> Unit = {},
    val onUseDefault: () -> Unit = {},
    val onClearAddressError: () -> Unit = {},
)

/**
 * The group's sync server on the Group screen (ADR-036): how the last automatic sync went, the
 * error in words, "Sync with the server", the on/off switch, and the address under Advanced.
 * Sits under a "Sync server" section header.
 */
@Composable
fun SyncServerCard(
    server: SyncServerUi,
    actions: SyncServerActions,
    modifier: Modifier = Modifier,
    advancedInitially: Boolean = false,
) {
    var advanced by rememberSaveable { mutableStateOf(advancedInitially || server.addressError != null) }
    GoaCard(modifier.fillMaxWidth()) {
        Header(server)
        if (server.url != null) {
            Spacer(Modifier.size(Spacing.sm))
            EnabledRow(server.enabled, actions.onEnabled)
        }
        if (server.active) {
            // "Sync now" at the top of the Group screen syncs through the server; no second button here.
            server.error?.let { ErrorBlock(server, it) }
            Spacer(Modifier.size(Spacing.md))
            Text(
                SyncServerText.AUTOMATIC,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Advanced")
            Spacer(Modifier.width(Spacing.xs))
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (advanced) Advanced(server, actions)
    }
}

@Composable
private fun Header(server: SyncServerUi) {
    val (icon, tint) = when {
        server.url == null || !server.enabled -> Icons.Outlined.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
        server.syncing -> Icons.Outlined.CloudSync to MaterialTheme.colorScheme.secondary
        server.hasProblem -> Icons.Outlined.CloudOff to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.CloudDone to MaterialTheme.colorScheme.secondary
    }
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            // Long addresses wrap after a dot, never mid-label.
            Text(
                server.host?.replace(".", ".​") ?: SyncServerText.TITLE_NONE,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when {
                    server.url == null -> SyncServerText.NONE
                    !server.enabled -> SyncServerText.OFF
                    else -> server.status.orEmpty()
                },
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
}

@Composable
private fun EnabledRow(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sync through the server", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ErrorBlock(server: SyncServerUi, error: String) {
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
                server.errorAgo?.replaceFirstChar(Char::uppercase),
                SyncServerText.RETRYING.takeIf { server.retrying },
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

@Composable
private fun Advanced(server: SyncServerUi, actions: SyncServerActions) {
    var address by rememberSaveable(server.url) { mutableStateOf(server.host.orEmpty()) }
    OutlinedTextField(
        value = address,
        onValueChange = {
            address = it.trim()
            actions.onClearAddressError()
        },
        label = { Text("Server address") },
        placeholder = { Text(server.defaultHost ?: "oldboi.example.ts.net:8443") },
        singleLine = true,
        isError = server.addressError != null,
        supportingText = { Text(server.addressError ?: SyncServerText.ADVANCED_HINT) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (address.isNotBlank()) actions.onSaveAddress(address) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedButton(
        onClick = { actions.onSaveAddress(address) },
        enabled = address.isNotBlank() && address != server.host,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Save address") }
    if (server.custom && server.defaultHost != null) {
        TextButton(onClick = actions.onUseDefault, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Use the app's server (${server.defaultHost})")
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(scaledIconSize(32.dp)))
}
