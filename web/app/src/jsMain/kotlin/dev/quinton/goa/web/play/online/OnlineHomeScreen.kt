package dev.quinton.goa.web.play.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.OnlineHomePresenter
import dev.quinton.goa.feature.board.play.online.OnlineHomeUi
import dev.quinton.goa.feature.board.play.online.OnlineRooms
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.online.ROOM_CODE_LENGTH
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.players.SwitchRow
import dev.quinton.goa.web.players.TextField
import dev.quinton.goa.web.pwa.DeviceStorage
import dev.quinton.goa.web.sync.QrScanner
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.Segmented
import dev.quinton.goa.web.ui.rememberGuardedScope
import dev.quinton.goa.web.ui.rememberLoad
import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

private const val HOME_FAILED = "Something went wrong here. Go back and open Online again."

/** The Android app's link for an invite: only a phone with the app answers it (`goa-companion://play/<code>`). */
private fun appLink(code: String) = "goa-companion://play/$code"

/**
 * The online home (ADR-056): a name and the recovery code shown once, then the games to go back to, a new room,
 * joining by code or QR, the leaderboard, the account. With [joinCode] (an invite link opened, `#play=<code>`), an
 * account comes first if there is none, and then the room opens in this screen's place.
 */
@Composable
fun OnlineHomeScreen(joinCode: String? = null) {
    val services = LocalServices.current
    val online = services.data.online
    val router = services.router
    val scope = rememberGuardedScope { services.messages.show(HOME_FAILED) }
    val presenter = remember { OnlineHomePresenter(online.gateway, online.identities, scope) }
    LaunchedEffect(presenter) { presenter.start() }
    val ui by presenter.state.collectAsState()
    LaunchedEffect(ui.stage) {
        if (ui.stage == AccountStage.READY && joinCode != null) presenter.join(joinCode)
    }
    LaunchedEffect(ui.openRoom) {
        ui.openRoom?.let { room ->
            presenter.roomOpened()
            router.navigate(Route.OnlineRoom(room.code), replace = joinCode != null)
        }
    }
    Screen(
        "Online",
        // The code shows once and nothing else happens until it's saved: no way off by accident.
        showBack = if (ui.stage == AccountStage.SAVE_CODE) false else null,
    ) {
        Div({ classes("goa-w2-form") }) {
            ui.message?.let { message ->
                P({
                    classes("t-body-medium", "goa-online-message")
                    attr("role", "alert")
                }) { Text(message) }
            }
            if (joinCode != null) JoinNotice(joinCode, ui)
            when (ui.stage) {
                AccountStage.LOADING -> LoadingState()
                AccountStage.PICK_NAME -> PickName(ui, presenter)
                AccountStage.SAVE_CODE -> SaveCode(services, ui, presenter)
                AccountStage.READY -> Ready(ui, presenter, services)
            }
        }
    }
}

/** Which room the invite is for, and where a phone with the Android app can open it. */
@Composable
private fun JoinNotice(code: String, ui: OnlineHomeUi) {
    GoaCard {
        H2({ classes("t-title-medium", "goa-w2-tight0") }) { Text("Room $code") }
        P({ classes("t-body-medium", "c-variant") }) {
            Text(
                when {
                    ui.stage != AccountStage.READY -> "Choose a name for this device and the room opens."
                    ui.message != null -> "It couldn't be opened from here."
                    else -> "Going to the room…"
                },
            )
        }
        if (onAndroid()) {
            A(appLink(code), { classes("goa-button", "outlined", "block") }) { Text("Open in the app") }
        }
    }
}

@Composable
private fun PickName(ui: OnlineHomeUi, presenter: OnlineHomePresenter) {
    GoaCard {
        H2({ classes("t-title-large", "goa-w2-tight0") }) { Text("Play people on other phones") }
        P({ classes("t-body-medium", "c-variant") }) {
            Text(
                "Pick the name the others will see. There is no email and no password: you get a recovery code, and " +
                    "that is how you get back to this account.",
            )
        }
        TextField(
            label = "Your name",
            value = ui.name,
            onValue = presenter::setName,
            maxLength = NAME_MAX,
            words = true,
            id = "online-name",
            onEnter = presenter::createAccount,
        )
        GoaButton("Create account", presenter::createAccount, enabled = !ui.working, block = true)
    }
    GoaCard {
        H3({ classes("t-title-medium", "goa-w2-tight0") }) { Text("Already have an account?") }
        P({ classes("t-body-medium", "c-variant") }) { Text("Enter its recovery code to use it on this device.") }
        TextField(
            label = "Recovery code",
            value = ui.linkCode,
            onValue = presenter::setLinkCode,
            id = "online-link",
            onEnter = presenter::linkDevice,
        )
        GoaButton("Use this code", presenter::linkDevice, ButtonKind.OUTLINED, enabled = !ui.working, block = true)
    }
    StorageNote()
}

/** The code once, plainly, and nothing else until "I've saved it" (docs/ONLINE.md "The client"). */
@Composable
private fun SaveCode(services: AppServices, ui: OnlineHomeUi, presenter: OnlineHomePresenter) {
    val code = ui.recoveryCode.orEmpty()
    GoaCard {
        H2({ classes("t-title-large", "goa-w2-tight0") }) { Text("Your recovery code") }
        P({ classes("t-body-medium") }) { Text(OnlineWords.SAVE_RECOVERY_CODE) }
        Code({
            classes("goa-online-code", "t-numeric")
            attr("aria-label", "Recovery code: ${code.toList().joinToString(" ")}")
        }) { Text(code) }
        Div({ classes("goa-w2-stack") }) {
            GoaButton("Copy the code", { copy(services, code) }, ButtonKind.OUTLINED, icon = "copy", block = true)
            GoaButton("I've saved it", presenter::recoveryCodeSaved, block = true)
        }
    }
    StorageNote()
}

private fun copy(services: AppServices, text: String) {
    services.data.scope.launch {
        try {
            (window.navigator.asDynamic().clipboard.writeText(text) as Promise<Any?>).await()
            services.messages.show("Copied. Keep it somewhere safe: not only in this browser.")
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable) {
            services.messages.show("This browser can't copy here. Write the code down.")
        }
    }
}

/** On an iPhone not added to the Home Screen the browser may clear this site's data: say what that means. */
@Composable
private fun StorageNote() {
    if (DeviceStorage.standalone) return
    P({ classes("t-body-small", "c-variant", "goa-w2-note") }) {
        Text(
            "Safari can clear a website's data after a week without a visit, unless it's on the Home Screen (Share → " +
                "Add to Home Screen). If that happens, your recovery code is how you get back to your account.",
        )
    }
}

private const val NAME_MAX = 24

@Composable
private fun Ready(ui: OnlineHomeUi, presenter: OnlineHomePresenter, services: AppServices) {
    val maps = rememberLoad(Unit) { services.data.play.boardMaps.maps.first().associate { it.id to it.name } }
    val names = (maps.state as? Load.Ready)?.value.orEmpty()
    val mapName = { id: String -> names[id] ?: id }
    if (ui.games.isNotEmpty()) {
        SectionHeader("My games")
        GoaCard(flush = true) {
            RowList {
                ui.games.forEach { game ->
                    ListRow(
                        mapName(game.mapId),
                        subtitle = game.seats.joinToString(", ") { it.name },
                        leadingIcon = "board",
                        route = Route.OnlineGame(game.gameId),
                    )
                }
            }
        }
    }
    NewRoom(ui, presenter, mapName)
    JoinByCode(ui, presenter)
    SectionHeader("Leaderboard")
    GoaCard(flush = true) {
        RowList {
            ListRow(
                "Leaderboard",
                subtitle = "Players and heroes, from rated games",
                leadingIcon = "trophy",
                route = Route.OnlineLeaderboard,
            )
        }
    }
    Account(ui, presenter)
    StorageNote()
}

@Composable
private fun NewRoom(ui: OnlineHomeUi, presenter: OnlineHomePresenter, mapName: (String) -> String) {
    val form = ui.room
    SectionHeader("New room")
    GoaCard {
        Div({ classes("goa-online-form") }) {
            FormLabel("Map")
            Segmented("Map", OnlineRooms.options.map { it.mapId }, form.mapId, mapName, presenter::setMap)
            FormLabel("Length")
            Segmented("Length", form.option.lengths, form.length, GameLength::label, presenter::setLength)
            FormLabel("Team size")
            Segmented("Team size", form.option.teamSizes, form.teamSize, { "${it}v$it" }, presenter::setTeamSize)
            SwitchRow(
                "Ranked: the result counts on the leaderboard",
                form.ranked,
                presenter::setRanked,
                "online-ranked",
            )
            GoaButton("Create room", presenter::createRoom, enabled = !ui.working, block = true)
        }
    }
}

private val GameLength.label: String get() = PlayText.length(this)

@Composable
private fun FormLabel(text: String) {
    P({ classes("t-label-large", "goa-w2-tight0") }) { Text(text) }
}

@Composable
private fun JoinByCode(ui: OnlineHomeUi, presenter: OnlineHomePresenter) {
    var scanning by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    SectionHeader("Join a room")
    GoaCard {
        TextField(
            label = "Room code or invite link",
            value = ui.joinText,
            onValue = {
                problem = null
                presenter.setJoinText(it)
            },
            error = problem,
            placeholder = "$ROOM_CODE_LENGTH letters and numbers",
            id = "online-join",
            onEnter = { presenter.join() },
        )
        Div({ classes("goa-w2-stack") }) {
            GoaButton("Join", { presenter.join() }, enabled = ui.joinText.isNotBlank() && !ui.working, block = true)
            GoaButton("Scan invite QR code", { scanning = true }, ButtonKind.OUTLINED, icon = "g_scan", block = true)
        }
    }
    if (scanning) {
        BottomSheet("Scan invite QR code", onDismiss = { scanning = false }) {
            QrScanner(
                onFound = { text ->
                    scanning = false
                    presenter.join(text)
                },
                onProblem = { message ->
                    scanning = false
                    problem = message
                },
            )
        }
    }
}

private enum class AccountSheet { RENAME, LINK }

@Composable
private fun Account(ui: OnlineHomeUi, presenter: OnlineHomePresenter) {
    var sheet by remember { mutableStateOf<AccountSheet?>(null) }
    var deleting by remember { mutableStateOf(false) }
    SectionHeader("Your account")
    GoaCard(flush = true) {
        RowList {
            ListRow(
                ui.name.ifEmpty { "Your name" },
                subtitle = "What the others see. Tap to change it",
                leadingIcon = "person",
                onClick = { sheet = AccountSheet.RENAME },
            )
            ListRow(
                "Link another device",
                subtitle = "Your recovery code brings this account to another phone",
                leadingIcon = "g_qr",
                onClick = { sheet = AccountSheet.LINK },
            )
            ListRow(
                "Delete account",
                subtitle = "Takes your name off the leaderboard",
                leadingIcon = "g_logout",
                onClick = { deleting = true },
            )
        }
    }
    when (sheet) {
        AccountSheet.RENAME -> RenameSheet(ui.name, ui.working, { sheet = null }, presenter::rename)
        AccountSheet.LINK -> LinkSheet(ui, presenter) { sheet = null }
        null -> Unit
    }
    if (deleting) {
        AlertDialog("Delete your account?", onDismiss = { deleting = false }, buttons = {
            GoaButton("Cancel", { deleting = false }, ButtonKind.TEXT)
            GoaButton("Delete", {
                deleting = false
                presenter.deleteAccount()
            }, ButtonKind.DANGER)
        }) {
            P {
                Text(
                    "Your name comes off the leaderboard; the games you played stay in the log as “Former player”. " +
                        "This can't be undone, and your recovery code stops working.",
                )
            }
        }
    }
}

@Composable
private fun RenameSheet(name: String, working: Boolean, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var wanted by remember { mutableStateOf(name) }
    val save = {
        onRename(wanted)
        onDismiss()
    }
    BottomSheet("Your name", onDismiss = onDismiss) {
        TextField(
            label = "Your name",
            value = wanted,
            onValue = { wanted = it },
            maxLength = NAME_MAX,
            words = true,
            id = "online-rename",
            onEnter = save,
        )
        GoaButton("Save", save, enabled = wanted.isNotBlank() && !working, block = true)
    }
}

/** Where the recovery code goes: the other device, or here to bring another account to this one. */
@Composable
private fun LinkSheet(ui: OnlineHomeUi, presenter: OnlineHomePresenter, onDismiss: () -> Unit) {
    BottomSheet("Link another device", onDismiss = onDismiss) {
        P({ classes("t-body-medium") }) {
            Text(
                "On the other phone, open Play → Online and enter your recovery code under “Already have an " +
                    "account?”. A code works once: that phone then shows you a new one to save.",
            )
        }
        P({ classes("t-body-medium", "c-variant") }) {
            Text(
                "To bring another account to this device instead, enter its code here. This device then uses it.",
            )
        }
        TextField(
            label = "Recovery code",
            value = ui.linkCode,
            onValue = presenter::setLinkCode,
            id = "online-link-here",
        )
        GoaButton("Use this code here", {
            presenter.linkDevice()
            onDismiss()
        }, ButtonKind.OUTLINED, enabled = ui.linkCode.isNotBlank() && !ui.working, block = true)
    }
}
