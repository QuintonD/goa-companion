package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** The state of something a screen loads (catalog JSON, stored data). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>

    data class Failed(val error: Throwable) : Load<Nothing>
}

/** [Load] plus a way to try again after a failure. */
class Loaded<T>(val state: Load<T>, val retry: () -> Unit)

/** Runs [load] when [key] changes (or on retry) and reports its [Load] state. */
@Composable
fun <T> rememberLoad(key: Any?, load: suspend () -> T): Loaded<T> {
    var attempt by remember(key) { mutableStateOf(0) }
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, attempt) {
        state = Load.Loading
        state = try {
            Load.Ready(load())
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            Load.Failed(e) // shown with Try again; JS failures arrive as plain Errors
        }
    }
    return Loaded(state) { attempt++ }
}

/**
 * Shows [loaded]: a spinner while loading, an error with Try again on failure ([failedTitle]),
 * else [content] with the value.
 */
@Composable
fun <T> LoadContent(loaded: Loaded<T>, failedTitle: String, content: @Composable (T) -> Unit) {
    when (val state = loaded.state) {
        Load.Loading -> LoadingState()

        is Load.Failed -> ErrorState(
            failedTitle,
            "Check the connection and try again. Once the app has been opened online, this works offline too.",
            onRetry = loaded.retry,
        )

        is Load.Ready -> content(state.value)
    }
}
