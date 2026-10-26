package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.CrestNames
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.argbCss
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/** The form, kept while this screen's history entry exists. */
private class PlayerForm {
    var loaded by mutableStateOf(false)
    var original by mutableStateOf<Player?>(null)
    var notFound by mutableStateOf(false)
    var name by mutableStateOf("")
    var color by mutableStateOf(PlayerPalette.all.first().argb)
    var avatarId by mutableStateOf(0)
    var nameError by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
}

/** New player or edit one (PlayerEditScreen): name, colour, crest. One player per name, archived included. */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun PlayerEditScreen(playerId: String?) {
    val services = LocalServices.current
    val players = services.data.group.players
    val scope = rememberCoroutineScope()
    val form = rememberRetained("player-form") { PlayerForm() }
    LaunchedEffect(playerId) {
        if (form.loaded) return@LaunchedEffect
        val all = players.observePlayers(includeArchived = true).first()
        val existing = playerId?.let { id -> all.firstOrNull { it.id == id } }
        when {
            existing != null -> {
                form.original = existing
                form.name = existing.name
                form.color = existing.color
                form.avatarId = existing.avatarId
            }

            playerId != null -> form.notFound = true

            else -> {
                val (color, crest) = PlayerPalette.forNew(all)
                form.color = color
                form.avatarId = crest
            }
        }
        form.loaded = true
    }
    val save: () -> Unit = save@{
        if (form.saving || !form.loaded || form.notFound) return@save
        val name = form.name.trim()
        if (name.isEmpty()) {
            form.nameError = "Enter a name"
            return@save
        }
        form.saving = true
        scope.launch {
            // Same rule as quick add in draft setup: one player per name, archived included.
            val clash = players.findByName(name)?.let { it.id != playerId } == true
            if (clash) {
                form.saving = false
                form.nameError = "“$name” is already in your group"
                return@launch
            }
            val player = form.original?.copy(name = name, color = form.color, avatarId = form.avatarId) ?: Player(
                id = Uuid.random().toString(),
                name = name,
                color = form.color,
                avatarId = form.avatarId,
                createdAt = Date.now().toLong(),
            )
            players.upsert(player)
            form.saving = false
            services.router.back()
        }
    }
    Screen(
        if (playerId == null) "New player" else "Edit player",
        actions = {
            if (form.loaded && !form.notFound) GoaButton("Save", save, ButtonKind.TEXT, enabled = !form.saving)
        },
    ) {
        when {
            !form.loaded -> LoadingState()

            form.notFound -> EmptyState("person", "Player not found", "This player no longer exists.")

            else -> Div({ classes("goa-w2-form") }) {
                Div({ classes("goa-w2-preview") }) {
                    PlayerAvatar(Player("preview", form.name.ifBlank { "?" }, form.color, form.avatarId, 0), 72)
                }
                TextField(
                    label = "Name",
                    value = form.name,
                    onValue = {
                        form.name = it.take(MAX_NAME_LENGTH)
                        form.nameError = null
                    },
                    error = form.nameError,
                    supporting = "${form.name.length} / $MAX_NAME_LENGTH",
                    maxLength = MAX_NAME_LENGTH,
                    words = true,
                    id = "player-name",
                    onEnter = save,
                )
                SectionHeader("Colour")
                Div({
                    classes("goa-w2-swatches")
                    attr("role", "radiogroup")
                    attr("aria-label", "Colour")
                }) {
                    PlayerPalette.all.forEach { swatch ->
                        val selected = swatch.argb == form.color
                        Button({
                            type(ButtonType.Button)
                            classes("goa-w2-swatch", "goa-pc")
                            attr("role", "radio")
                            attr("aria-checked", selected.toString())
                            attr("aria-label", swatch.name)
                            style {
                                property("--pc-d", argbCss(swatch.argb))
                                property("--pc-l", argbCss(swatch.lightArgb))
                            }
                            onClick { form.color = swatch.argb }
                        }) { if (selected) Icon("check") }
                    }
                }
                SectionHeader("Crest")
                Div({
                    classes("goa-w2-crests")
                    attr("role", "radiogroup")
                    attr("aria-label", "Crest")
                }) {
                    CrestNames.forEachIndexed { id, crestName ->
                        val selected = id == form.avatarId
                        Button({
                            type(ButtonType.Button)
                            classes("goa-w2-crest")
                            attr("role", "radio")
                            attr("aria-checked", selected.toString())
                            attr("aria-label", crestName)
                            onClick { form.avatarId = id }
                        }) {
                            PlayerAvatar(Player("c", "?", form.color, id, 0), 48)
                        }
                    }
                }
                P({ classes("t-body-small", "c-variant") }) {
                    Text("Colour and crest identify the player on the leaderboard and in match records.")
                }
                GoaButton("Save", save, enabled = !form.saving, block = true)
            }
        }
    }
}

/** The phone's limit on a player's name (`PlayerEditViewModel.MAX_NAME_LENGTH`). */
const val MAX_NAME_LENGTH = 24
