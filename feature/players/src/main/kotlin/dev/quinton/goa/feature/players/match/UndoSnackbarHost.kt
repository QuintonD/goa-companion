package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Match

/** Shows "Match deleted · Undo" whenever [undo] is set. Overlays the bottom of its parent box. */
@Composable
fun UndoSnackbarHost(
    hostState: SnackbarHostState,
    undo: Match?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(undo?.id) {
        if (undo == null) return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = "Match deleted. Ratings updated.",
            actionLabel = "Undo",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(hostState, Modifier.padding(Spacing.lg))
    }
}
