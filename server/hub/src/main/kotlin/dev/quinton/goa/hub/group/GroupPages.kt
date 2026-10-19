package dev.quinton.goa.hub.group

import dev.quinton.goa.core.domain.sync.PlayerMergeOutcome
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.group.AddCheck
import dev.quinton.goa.core.presentation.group.MergeOffer
import dev.quinton.goa.core.presentation.group.PlayerLink
import dev.quinton.goa.core.presentation.group.SyncWords
import dev.quinton.goa.hub.feature.AppDownloadFeature
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.group.GroupFeature.INVITE_PATH
import dev.quinton.goa.hub.group.GroupFeature.ME_PATH
import dev.quinton.goa.hub.group.GroupFeature.PATH
import dev.quinton.goa.hub.group.GroupFeature.inviteChoice
import dev.quinton.goa.hub.store.Device
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.ui.ButtonStyle
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.Icons
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.errorState
import dev.quinton.goa.hub.web.ui.hubButton
import dev.quinton.goa.hub.web.ui.icon
import dev.quinton.goa.hub.web.ui.linkButton
import dev.quinton.goa.hub.web.ui.listRow
import dev.quinton.goa.hub.web.ui.notice
import dev.quinton.goa.hub.web.ui.playerAvatar
import dev.quinton.goa.hub.web.ui.postForm
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import dev.quinton.goa.hub.web.ui.textField
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe

/** What "Which player are you?" shows besides the list: the name being added and its answer. */
internal data class WhoAreYouForm(
    val next: String,
    val newName: String = "",
    val nameError: String? = null,
    val addCheck: AddCheck? = null,
    val error: String? = null,
)

private val GroupStyles = listOf("group/group.css")
private val GroupScripts = listOf("group/group.js")

private fun page(title: String, back: String?) =
    HubPage(title, back = back, styles = GroupStyles, scripts = GroupScripts)

/** `/group`: the group, who this browser is, invite, signed-in browsers, phones' last sync, the app. */
internal suspend fun ApplicationCall.respondGroupPage(ctx: HubContext) {
    val data = ctx.groupStore.data()
    val me = GroupFeature.me(ctx, device.id, data)
    val now = ctx.clock.nowMillis()
    val group = ctx.group
    respondPage(page("Group", back = "/settings")) {
        card {
            h2 { +(group?.name ?: "The group") }
            p("muted") {
                +"${count(data.players().size, "player")} · ${count(data.matches.size, "match", "matches")} on the hub"
            }
            linkButton("Invite a friend", INVITE_PATH, ButtonStyle.PRIMARY, GroupIcons.QR)
        }
        sectionHeader("This browser")
        if (me != null) {
            listRow("You: ${me.name}", "Change", href = ME_PATH) { playerAvatar(me) }
        } else {
            listRow("Which player are you?", "Choose yourself so the app can show your page first", href = ME_PATH) {
                span("row-icon") { icon(GroupIcons.PERSON) }
            }
        }
        sectionHeader("Signed-in browsers")
        p("muted") { +"Every browser that scanned the group's invite. Sign out any you don't recognise." }
        ul("plain-list") {
            ctx.devices.list().forEach { d -> li { deviceRow(d, isThis = d.id == device.id, now) } }
        }
        sectionHeader("Phones synced with the hub")
        val phones = ctx.syncState.read().clients
        if (phones.isEmpty()) {
            p("muted") { +"None yet. Android phones with the hub's address sync on their own." }
        } else {
            phones.forEach { phone ->
                listRow(phone.name, trailing = SyncWords.ago(phone.lastSyncAt, now)) {
                    span("row-icon") { icon(Icons.DEVICES) }
                }
            }
        }
        sectionHeader("Android")
        listRow("Get the Android app", "For friends with an Android phone", href = AppDownloadFeature.PATH) {
            span("row-icon") { icon(Icons.DOWNLOAD) }
        }
    }
}

private fun FlowContent.deviceRow(device: Device, isThis: Boolean, now: Long) {
    div("row device-row") {
        if (isThis) attributes["aria-current"] = "true"
        span("row-icon") { icon(Icons.DEVICES) }
        div("row-text") {
            span("row-title") { +device.name }
            span("row-subtitle") {
                if (isThis) {
                    span("this-device") { +"This browser" }
                    +" · "
                }
                +"last used ${SyncWords.ago(device.lastSeenAt, now)}"
            }
        }
        a("$PATH/devices/${device.id}/sign-out", classes = "btn btn-text") {
            attributes["aria-label"] = if (isThis) "Sign out this browser" else "Sign out ${device.name}"
            +"Sign out"
        }
    }
}

/** `/group/devices/{id}/sign-out`: say what signing out does, then do it (no script needed). */
internal suspend fun ApplicationCall.respondSignOutConfirm(ctx: HubContext, id: String) {
    val target = ctx.devices.get(id)
    if (target == null) {
        respondPage(page("Sign out", back = PATH), HttpStatusCode.NotFound) {
            emptyState("Already signed out", "That browser isn't signed in any more.", "Back to Group", PATH)
        }
        return
    }
    val self = target.id == device.id
    respondPage(page(if (self) "Sign out this browser?" else "Sign out ${target.name}?", back = PATH)) {
        card {
            p {
                +if (self) {
                    "This browser leaves the group: its settings, its timer and who you are here are forgotten. " +
                        "To come back, scan the group's invite again."
                } else {
                    "${target.name} leaves the group: it has to scan the invite again to come back. Its own " +
                        "settings and timer are forgotten. The group's matches and players stay."
                }
            }
            postForm("$PATH/devices/${target.id}/sign-out", classes = "actions") {
                hubButton("Sign out", ButtonStyle.DANGER, icon = Icons.LOGOUT)
                linkButton("Cancel", PATH, ButtonStyle.TEXT)
            }
        }
    }
}

/** `/group/me`: "You're <name>?" first when a personal invite asks, then the list and "Add me". */
internal suspend fun ApplicationCall.respondWhoAreYou(ctx: HubContext, form: WhoAreYouForm) {
    val data = ctx.groupStore.data()
    val meId = GroupFeature.me(ctx, device.id, data)?.id
    val invited = GroupFeature.invited(ctx, device.id, data)
    val players = PlayerLink.choices(data.players())
    val status = if (form.nameError != null ||
        form.error != null
    ) {
        HttpStatusCode.UnprocessableEntity
    } else {
        HttpStatusCode.OK
    }
    respondPage(page("Which player are you?", back = if (form.next == PATH) PATH else form.next), status) {
        form.error?.let { errorState(it) }
        invited?.let { invitedCard(it, form.next) }
        form.addCheck?.let { addCheckCard(it, form.next) }
        p("muted") {
            +"This browser opens your player page first and marks you as “You”. Only this browser knows."
        }
        if (players.isNotEmpty()) {
            sectionHeader("Pick yourself")
            postForm("$ME_PATH/choose", classes = "pick-list") {
                attributes["aria-label"] = "Pick yourself"
                hiddenNext(form.next)
                players.forEach { player -> pickButton(player, selected = player.id == meId) }
            }
        }
        sectionHeader(if (players.isEmpty()) "Add yourself" else "Not in the list?")
        postForm("$ME_PATH/add", classes = "stack") {
            id = "add-me"
            hiddenNext(form.next)
            textField("name", "Your name", value = form.newName, error = form.nameError) {
                maxLength = PlayerLink.MAX_NAME.toString()
                attributes["autocomplete"] = "nickname"
                attributes["autocapitalize"] = "words"
                attributes["enterkeyhint"] = "done"
            }
            hubButton("Add me to the group", icon = Icons.ADD)
        }
    }
}

private fun FlowContent.pickButton(player: Player, selected: Boolean) {
    button(type = ButtonType.submit, classes = if (selected) "row pick selected" else "row pick") {
        name = "player"
        value = player.id
        attributes["aria-pressed"] = selected.toString()
        span("row-leading") { playerAvatar(player, "sm") }
        span("row-text") { span("row-title") { +player.name } }
        if (selected) {
            span("pick-mark") {
                icon(Icons.CHECK)
                +"You"
            }
        }
    }
}

private fun FlowContent.invitedCard(invited: Player, next: String) {
    card("invited-card") {
        attributes["aria-labelledby"] = "invited-title"
        div("invited-head") {
            playerAvatar(invited, "lg")
            h2 {
                id = "invited-title"
                +"You're ${invited.name}?"
            }
        }
        p("muted") {
            +"Your invite was made for ${invited.name}. If that's you, this browser becomes ${invited.name}: "
            +"your matches and rating are already there."
        }
        postForm("$ME_PATH/invited", classes = "actions end") {
            hiddenNext(next)
            button(type = ButtonType.submit, classes = ButtonStyle.OUTLINED.css) {
                name = "answer"
                value = "no"
                +"Not me"
            }
            button(type = ButtonType.submit, classes = ButtonStyle.PRIMARY.css) {
                name = "answer"
                value = "yes"
                +"Yes, I'm ${invited.name}"
            }
        }
    }
}

private fun FlowContent.addCheckCard(check: AddCheck, next: String) {
    card("check-card") {
        attributes["role"] = "alertdialog"
        attributes["aria-labelledby"] = "check-title"
        h2 {
            id = "check-title"
            +"Is one of these you?"
        }
        p { +PlayerLink.addCheckText(check) }
        postForm("$ME_PATH/choose", classes = "pick-list") {
            hiddenNext(next)
            check.candidates.forEach { player -> pickButton(player, selected = false) }
        }
        div("actions end") {
            linkButton("Cancel", GroupFeature.meUrl(next), ButtonStyle.TEXT)
            if (!check.nameTaken) {
                postForm("$ME_PATH/add") {
                    hiddenNext(next)
                    input(type = InputType.hidden, name = "name") { value = check.name }
                    input(type = InputType.hidden, name = "confirmed") { value = "true" }
                    hubButton("No, add ${check.name}", ButtonStyle.TONAL)
                }
            }
        }
    }
}

/** `/group/me/merge`: "Merge your old player into <name>?" (ADR-031 §1). */
internal suspend fun ApplicationCall.respondMergeOffer(ctx: HubContext, next: String) {
    val pending = ctx.deviceStore.read(device.id, GroupSlots.PendingMerge)
    val data = ctx.groupStore.data()
    val old = pending?.let { data.players().firstOrNull { p -> p.id == it.old } }
    val into = pending?.let { data.players().firstOrNull { p -> p.id == it.into } }
    if (old == null || into == null) {
        ctx.deviceStore.delete(device.id, GroupSlots.PendingMerge)
        return respondPage(page("Merge", back = next)) {
            emptyState("Nothing to merge", "That merge isn't on offer any more.", "Continue", next)
        }
    }
    val offer = MergeOffer(old, data.matches.count { old.id in it.playerIds }, into)
    respondPage(page("Merge your old player?", back = next)) {
        card {
            h2 { +"Merge your old player into ${into.name}?" }
            p { +PlayerLink.mergeOfferText(offer, device = "browser") }
            div("actions end") {
                postForm("$ME_PATH/merge/keep") {
                    hiddenNext(next)
                    hubButton("Keep both", ButtonStyle.TEXT)
                }
                postForm("$ME_PATH/merge") {
                    hiddenNext(next)
                    hubButton("Merge", ButtonStyle.PRIMARY)
                }
            }
        }
    }
}

/** The old player couldn't be merged: the matches both played in. */
internal suspend fun ApplicationCall.respondMergeRefused(
    ctx: HubContext,
    outcome: PlayerMergeOutcome.PlayedTogether,
    next: String,
) {
    val data = ctx.groupStore.data()
    val people = data.players(includeArchived = true).associateBy { it.id }
    val me = GroupFeature.me(ctx, device.id, data)?.name
    respondPage(page("Can't merge", back = next)) {
        card {
            h2 { +"Can't merge" }
            p { +PlayerLink.refusalText(me, device = "browser") }
            ul { PlayerLink.refusalLines(outcome.matches, people, ctx.clock.zone).forEach { li { +it } } }
            div("actions end") { linkButton("OK", next, ButtonStyle.PRIMARY) }
        }
    }
}

/** `/group/invite`: "Who's the invite for?", then the QR code, the link, Copy and Share. */
internal suspend fun ApplicationCall.respondInvite(ctx: HubContext) {
    val view = GroupFeature.inviteView(ctx, this, request.queryParameters.inviteChoice())
    if (view == null) {
        return respondPage(page("Invite a friend", back = PATH)) {
            emptyState("No group yet", "This hub hasn't joined a group.")
        }
    }
    val groupName = ctx.group?.name.orEmpty()
    if (view.choosing) {
        return respondPage(page("Who's the invite for?", back = PATH)) {
            p("muted") {
                +"Already entered your friend as a player? Pick them: after signing in, their phone or browser "
                +"asks “You're …?” and becomes that player, matches and rating included, instead of adding "
                +"someone new."
            }
            div("pick-list") {
                a("$INVITE_PATH?for=anyone", classes = "row pick") {
                    span("row-leading") { span("avatar avatar-sm anyone") { icon(Icons.GROUP) } }
                    span("row-text") {
                        span("row-title") { +"Anyone" }
                        span("row-subtitle") { +"They pick themselves, or add themselves, after joining" }
                    }
                }
                view.players.forEach { player ->
                    a("$INVITE_PATH?player=${GroupFeature.encode(player.id)}", classes = "row pick") {
                        span("row-leading") { playerAvatar(player, "sm") }
                        span("row-text") { span("row-title") { +player.name } }
                    }
                }
            }
        }
    }
    val player = view.player
    val invite = view.invite.orEmpty()
    respondPage(page(if (player != null) "Invite ${player.name}" else "Invite to “$groupName”", back = PATH)) {
        if (player != null) {
            div("invited-for") {
                playerAvatar(player)
                p("muted") { +"A personal invite to “$groupName”: ${player.name}'s phone becomes ${player.name}." }
            }
        }
        div("qr-box") {
            unsafe {
                +InviteQr.svg(
                    invite,
                    if (player != null) "Invite QR code for ${player.name}" else "Invite QR code for $groupName",
                )
            }
        }
        p {
            +"On your friend's phone: point the camera at this code. An iPhone opens the group's web app in "
            +"Safari; on Android, the app's Settings → Group sync → Scan invite QR code (or the link opens the "
            +"app if it's installed)."
            if (player != null) +" After signing in it asks “You're ${player.name}?”."
        }
        notice(
            "This code is the group's key: anyone with it can join. Show it only at the table, to people in " +
                "the group. Only browsers already signed in to the group can see this page.",
        )
        div("field") {
            label {
                htmlFor = "invite-link"
                +"Invite link"
            }
            input(type = InputType.text, classes = "invite-link") {
                id = "invite-link"
                readonly = true
                value = invite
                attributes["autocomplete"] = "off"
                attributes["spellcheck"] = "false"
            }
        }
        div("actions") {
            scriptButton("Copy link", ButtonStyle.TONAL, GroupIcons.COPY, mapOf("data-copy" to "invite-link"))
            scriptButton(
                "Share…",
                ButtonStyle.TONAL,
                Icons.SHARE,
                mapOf("data-share-text" to view.shareText.orEmpty(), "data-share-title" to "Join “$groupName”"),
            )
            p("copy-status") {
                attributes["role"] = "status"
                attributes["aria-live"] = "polite"
            }
        }
        div("actions") {
            linkButton("Done", PATH, ButtonStyle.PRIMARY)
            linkButton(
                if (player !=
                    null
                ) {
                    "For someone else"
                } else {
                    "For a player you entered"
                },
                INVITE_PATH,
                ButtonStyle.TEXT,
            )
        }
    }
}

/**
 * A script-driven button, hidden until its script shows it. (Attributes go on before the content:
 * kotlinx.html streams the tag, so `hubButton`'s trailing block can't add them.)
 */
internal fun FlowContent.scriptButton(label: String, style: ButtonStyle, icon: String?, data: Map<String, String>) {
    button(type = ButtonType.button, classes = style.css) {
        data.forEach { (key, value) -> attributes[key] = value }
        attributes["hidden"] = ""
        icon?.let { icon(it) }
        span { +label }
    }
}

private fun kotlinx.html.FORM.hiddenNext(next: String) {
    input(type = InputType.hidden, name = "next") { value = next }
}

private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** Icons this area adds (`static/material/`, classes in `group/group.css`). */
internal object GroupIcons {
    const val QR = "qr_code_2"
    const val COPY = "content_copy"
    const val PERSON = "person"
}
