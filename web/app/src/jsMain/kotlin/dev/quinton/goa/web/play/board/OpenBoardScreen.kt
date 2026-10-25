package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.feature.board.board.BoardSandbox
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.lineup
import dev.quinton.goa.feature.board.newPieceId
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.data.WebServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlin.js.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Opens the board of a line-up in one tap ("Set up", "Try on the board", "Open on the board"): a new
 * board at the opening, or the untouched board of the same line-up and map when there is one, then
 * stands in for it in the history (the phone's BoardRoute without an id). The same line-up asked for
 * twice at once (a double tap) opens one board.
 */
@Composable
fun OpenBoardScreen(route: Route.OpenBoard) {
    val services = LocalServices.current
    var failed by remember(route) { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
        val id = try {
            openBoard(services.data, route)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            failed = "The board couldn't be set up: ${e.message}"
            return@LaunchedEffect
        }
        if (id ==
            null
        ) {
            failed = "No map is bundled with this build."
        } else {
            services.router.navigate(Route.Board(id), replace = true)
        }
    }
    Screen("Board") {
        val message = failed
        if (message == null) {
            LoadingState("Setting up the board")
        } else {
            EmptyState("board", "Board not available", message) {
                GoaButton("Back to Boards", {
                    services.router.navigate(Route.Boards, replace = true)
                }, ButtonKind.TONAL)
            }
        }
    }
}

/** Line-ups being opened right now: a second request for one waits for the first's board. */
private val opening = mutableMapOf<Route.OpenBoard, CompletableDeferred<String?>>()

/** The id of [route]'s board: the untouched twin, or a new board written at the opening; null with no map. */
internal suspend fun openBoard(data: WebServices, route: Route.OpenBoard): String? {
    opening[route]?.let { return it.await() }
    val result = CompletableDeferred<String?>()
    opening[route] = result
    try {
        val id = create(data, route)
        result.complete(id)
        return id
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        // Whatever failed (a fetch, storage), a second request waiting on this one fails the same way.
        result.completeExceptionally(e)
        throw e
    } finally {
        opening.remove(route)
    }
}

private suspend fun create(data: WebServices, route: Route.OpenBoard): String? {
    val heroes = data.content.catalog.catalog.first().boardHeroes()
    val maps = data.play.boardMaps.maps.first()
    // From a logged match that came from a draft: the draft's pick order (the opening uses it).
    val drafted = route.matchId?.let { matchId -> drafts(data).first().firstOrNull { it.matchId == matchId }?.lineup() }
    val now = Date.now().toLong()
    val fresh: SavedBoard = BoardSandbox.newBoard(
        red = route.red,
        blue = route.blue,
        drafted = drafted,
        mapId = route.mapId,
        name = route.name,
        maps = maps,
        heroes = heroes,
        id = newPieceId(),
        now = now,
        newPieceId = ::newPieceId,
    ) ?: return null
    val twin = BoardSandbox.twinOf(data.play.boards.boards.catch { emit(emptyList()) }.first(), fresh)
    if (twin != null) return twin.id
    data.play.boards.upsert(fresh)
    return fresh.id
}
