package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.web.LocalServices
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The device's choice of how pieces and spaces look on the painted board, as on the phone. */
val LocalBoardStyle = compositionLocalOf { BoardStyle.MINIATURES }

/** Provides [LocalBoardStyle] from the settings, so a map follows a change in Settings at once. */
@Composable
fun ProvideBoardStyle(content: @Composable () -> Unit) {
    val repository = LocalServices.current.data.content.settings
    val style by remember(repository) {
        repository.settings.map { it.boardStyle }.distinctUntilChanged()
    }.collectAsState(BoardStyle.MINIATURES)
    CompositionLocalProvider(LocalBoardStyle provides style, content = content)
}
