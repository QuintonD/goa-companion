package dev.quinton.goa.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.quinton.goa.core.navigation.NavSheet

/**
 * Shows the top entry as a modal bottom sheet over the screen below it when the entry carries
 * [NavSheet.metadata] (e.g. the level ladder over the running timer). Dismissing the sheet
 * (swipe down, scrim tap, back) pops the entry.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[NavSheet.KEY] != true) return null
        val below = entries.dropLast(1)
        return BottomSheetScene(
            key = last.contentKey,
            previousEntries = below,
            overlaidEntries = below,
            entry = last,
            onBack = onBack,
        )
    }
}

private class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable () -> Unit = {
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean = other is BottomSheetScene<*> && other.key == key && other.entry == entry

    override fun hashCode(): Int = key.hashCode() * 31 + entry.hashCode()
}
