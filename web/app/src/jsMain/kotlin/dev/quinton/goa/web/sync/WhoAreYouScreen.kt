package dev.quinton.goa.web.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.feature.sync.me.WhoAreYouText
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.players.TextField
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlin.js.Date
import kotlin.js.Promise
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * "Which player are you?" (WhoAreYouScreen, ADR-025): links this browser to a player, picked from
 * the group's list or created here, which is how a friend adds themself to the group. Stored in this
 * browser only.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun WhoAreYouScreen() {
    val services = LocalServices.current
    val group = services.data.group
    val scope = rememberCoroutineScope()
    val people by remember { group.players.observePlayers() }.collectAsState(emptyList())
    val me by remember { group.group.state }.collectAsState(null as GroupState?)
    var newName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val players = people.sortedBy { it.name.lowercase() }
    Screen("Which player are you?") {
        P({ classes("t-body-medium", "c-variant") }) {
            Text("This browser opens your player page first and marks you as “You”. Only this browser knows.")
        }
        if (players.isNotEmpty()) {
            SectionHeader("Pick yourself")
            GoaCard(flush = true) {
                RowList {
                    players.forEach { player ->
                        val chosen = player.id == me?.mePlayerId
                        ListRow(
                            player.name,
                            subtitle = if (chosen) "You" else null,
                            leading = { PlayerAvatar(player, 40) },
                            trailing = if (chosen) "✓" else null,
                            onClick = {
                                scope.launch {
                                    group.group.setMe(player.id)
                                    services.router.back()
                                }
                            },
                        )
                    }
                }
            }
        }
        SectionHeader(if (players.isEmpty()) "Add yourself" else "Not in the list?")
        val addMe: () -> Unit = {
            val name = newName.trim()
            if (name.isEmpty()) {
                nameError = WhoAreYouText.ENTER_NAME
            } else {
                scope.launch {
                    val existing = group.players.findByName(name)
                    if (existing != null) {
                        nameError = WhoAreYouText.taken(existing.name)
                        return@launch
                    }
                    val all = group.players.observePlayers(includeArchived = true).first()
                    val (color, crest) = PlayerPalette.forNew(all)
                    val player = Player(Uuid.random().toString(), name, color, crest, createdAt = Date.now().toLong())
                    group.players.upsert(player)
                    group.group.setMe(player.id)
                    services.router.back()
                }
            }
        }
        TextField(
            label = "Your name",
            value = newName,
            onValue = {
                newName = it.take(WhoAreYouText.MAX_NAME)
                nameError = null
            },
            error = nameError,
            maxLength = WhoAreYouText.MAX_NAME,
            words = true,
            id = "me-name",
            onEnter = addMe,
        )
        // As on the phone: nothing to add until a name is typed; Return adds it.
        GoaButton("Add me to the group", addMe, block = true, enabled = newName.isNotBlank())
    }
}

/** An invite link (`#join=…`) opened: hand the invite to the Group screen and drop the key from the address. */
@Composable
fun JoinLinkScreen(invite: String) {
    val services = LocalServices.current
    LaunchedEffect(invite) {
        PendingInvite.text = invite
        services.router.navigate(Route.Group, replace = true)
    }
    Screen("Group sync") { Icon("sync") }
}

/** Shares [text] (the share sheet), else copies it and says so. */
fun shareText(services: AppServices, text: String) {
    services.data.scope.launch {
        val navigator = window.navigator.asDynamic()
        if (navigator.share != null) {
            val data: dynamic = js("({})")
            data.text = text
            try {
                (navigator.share(data) as Promise<Any?>).await()
                return@launch
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                if (e.asDynamic().name == "AbortError") return@launch
            }
        }
        try {
            (navigator.clipboard.writeText(text) as Promise<Any?>).await()
            services.messages.show("Invite copied. Paste it in a message to the group.")
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            services.messages.show("This browser can't share or copy here.")
        }
    }
}
