package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What can be asked for on one map (docs/ONLINE.md "A room"). Equal teams only. */
data class RoomOption(val mapId: String, val teamSizes: List<Int>, val lengths: List<GameLength>)

object OnlineRooms {
    /** Double lane has no Quick or Long: its counters are p.20's. */
    val options: List<RoomOption> = listOf(
        RoomOption("forgotten-island", listOf(1, 2, 3), listOf(GameLength.QUICK, GameLength.LONG)),
        RoomOption("across-the-river", listOf(4, 5), listOf(GameLength.QUICK)),
    )

    fun option(mapId: String): RoomOption? = options.firstOrNull { it.mapId == mapId }
}

/** The room being set up: every value allowed for its map. */
data class RoomForm(
    val mapId: String = OnlineRooms.options.first().mapId,
    val length: GameLength = GameLength.QUICK,
    val teamSize: Int = OnlineRooms.options.first().teamSizes.first(),
    val ranked: Boolean = false,
) {
    val option: RoomOption get() = OnlineRooms.option(mapId) ?: OnlineRooms.options.first()
}

/** Where the person is with their account. */
enum class AccountStage {
    LOADING,

    /** No account on this device: pick a name, or link one with a recovery code. */
    PICK_NAME,

    /** The recovery code is on screen; nothing else happens until "I've saved it". */
    SAVE_CODE,
    READY,
}

data class OnlineHomeUi(
    val stage: AccountStage = AccountStage.LOADING,
    /** The name field, then the account's name. */
    val name: String = "",
    val linkCode: String = "",
    /** Shown once: only at [AccountStage.SAVE_CODE]. */
    val recoveryCode: String? = null,
    val room: RoomForm = RoomForm(),
    val joinText: String = "",
    /** Games to go back to, the ones that need you first ([MyGameStatuses.sorted]). */
    val games: List<MyGame> = emptyList(),
    /** The signed-in account, for each game's "Your turn" ([MyGameStatuses.of]). */
    val you: String? = null,
    /** A call is on its way: its button waits. */
    val working: Boolean = false,
    /** What went wrong, in words. */
    val message: String? = null,
    /** The room to open, once: the screen goes there and calls [OnlineHomePresenter.roomOpened]. */
    val openRoom: RoomView? = null,
)

/**
 * The online home: the account, a new room, joining by code, the games to go back to (ADR-056). [scope]
 * is the screen's and runs on one thread. Pure: the platforms provide the gateway and the store.
 */
class OnlineHomePresenter(
    private val gateway: OnlineGateway,
    private val identities: OnlineIdentityStore,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(OnlineHomeUi())
    val state: StateFlow<OnlineHomeUi> = mutableState.asStateFlow()

    /** A new account, kept from the store until its code is saved: see [recoveryCodeSaved]. */
    private var unsaved: OnlineIdentity? = null

    /** What the last failed games load said, so a later load clears only that, never another action's message. */
    private var gamesMessage: String? = null

    fun start() {
        scope.launch {
            val identity = identities.load()
            if (identity == null) {
                mutableState.update { it.copy(stage = AccountStage.PICK_NAME) }
            } else {
                mutableState.update { it.copy(stage = AccountStage.READY, name = identity.name) }
                loadGames()
            }
        }
    }

    // ---- The account -------------------------------------------------------------------------------

    fun setName(name: String) = mutableState.update { it.copy(name = name, message = null) }

    fun setLinkCode(code: String) = mutableState.update { it.copy(linkCode = code, message = null) }

    fun createAccount() {
        val name = state.value.name.trim()
        if (name.isEmpty()) return say(OnlineWords.NAME_NEEDED)
        calling {
            when (val result = gateway.createAccount(name)) {
                is OnlineResult.Ok -> {
                    // Not saved yet: if the app dies before the code is saved, the person starts again
                    // with nothing lost, rather than holding an account they could never get back.
                    unsaved = identityOf(result.value, name)
                    showCode(result.value, name)
                }

                else -> say(result.words())
            }
        }
    }

    /** Linking burns the old code and issues a new one, so it has to be saved too. */
    fun linkDevice() {
        val code = state.value.linkCode.trim()
        if (code.isEmpty()) return say(OnlineWords.RECOVERY_CODE_NEEDED)
        calling {
            when (val result = gateway.link(code)) {
                is OnlineResult.Ok -> {
                    // The server's `me` needs the token from the store, so this one is saved at once.
                    identities.save(identityOf(result.value, ""))
                    val me = gateway.me() as? OnlineResult.Ok
                    val name = me?.value?.name.orEmpty()
                    identities.save(identityOf(result.value, name))
                    showCode(result.value, name)
                }

                else -> say(result.words())
            }
        }
    }

    private fun identityOf(account: AccountCreated, name: String) =
        OnlineIdentity(account.accountId, account.token, name)

    private fun showCode(account: AccountCreated, name: String) = mutableState.update {
        it.copy(stage = AccountStage.SAVE_CODE, recoveryCode = account.recoveryCode, name = name, linkCode = "")
    }

    /** The explicit "I've saved it": with no email, the code is the only way back to this account. */
    fun recoveryCodeSaved() {
        if (state.value.stage != AccountStage.SAVE_CODE) return
        scope.launch {
            unsaved?.let { identities.save(it) }
            unsaved = null
            mutableState.update { it.copy(stage = AccountStage.READY, recoveryCode = null, message = null) }
            loadGames()
        }
    }

    fun rename(name: String) {
        val wanted = name.trim()
        if (!ready()) return
        if (wanted.isEmpty()) return say(OnlineWords.NAME_NEEDED)
        calling {
            when (val result = gateway.rename(wanted)) {
                is OnlineResult.Ok -> {
                    identities.load()?.let { identities.save(it.copy(name = result.value.name)) }
                    mutableState.update { it.copy(name = result.value.name) }
                }

                else -> say(result.words())
            }
        }
    }

    /** Anonymises the name on the server; the games played stay in the log as "Former player". */
    fun deleteAccount() {
        if (!ready()) return
        calling {
            when (val result = gateway.deleteMe()) {
                is OnlineResult.Ok -> {
                    identities.clear()
                    mutableState.value = OnlineHomeUi(stage = AccountStage.PICK_NAME)
                }

                else -> say(result.words())
            }
        }
    }

    // ---- A room ------------------------------------------------------------------------------------

    /** Changing map keeps what still fits and falls back to what the new map allows. */
    fun setMap(mapId: String) {
        val option = OnlineRooms.option(mapId) ?: return
        mutableState.update { ui ->
            val form = ui.room
            ui.copy(
                room = form.copy(
                    mapId = mapId,
                    teamSize = form.teamSize.takeIf { it in option.teamSizes } ?: option.teamSizes.first(),
                    length = form.length.takeIf { it in option.lengths } ?: option.lengths.first(),
                ),
            )
        }
    }

    fun setLength(length: GameLength) = mutableState.update { ui ->
        if (length in ui.room.option.lengths) ui.copy(room = ui.room.copy(length = length)) else ui
    }

    fun setTeamSize(size: Int) = mutableState.update { ui ->
        if (size in ui.room.option.teamSizes) ui.copy(room = ui.room.copy(teamSize = size)) else ui
    }

    fun setRanked(ranked: Boolean) = mutableState.update { it.copy(room = it.room.copy(ranked = ranked)) }

    fun createRoom() {
        if (!ready()) return
        val form = state.value.room
        calling {
            when (val created = gateway.createRoom(CreateRoom(form.mapId, form.length, form.teamSize, form.ranked))) {
                is OnlineResult.Ok -> enter(created.value.code)
                else -> say(created.words())
            }
        }
    }

    fun setJoinText(text: String) = mutableState.update { it.copy(joinText = text, message = null) }

    /** Joins by the code or link in [text] (the field, by default; an opened invite link can pass its own). */
    fun join(text: String = state.value.joinText) {
        if (!ready()) return
        val code = parseInvite(text) ?: return say(OnlineWords.NOT_A_CODE)
        calling { enter(code) }
    }

    private suspend fun enter(code: String) {
        when (val joined = gateway.join(code)) {
            is OnlineResult.Ok -> mutableState.update { it.copy(openRoom = joined.value, joinText = "") }
            else -> say(joined.words())
        }
    }

    fun roomOpened() = mutableState.update { it.copy(openRoom = null) }

    // ---- My games ----------------------------------------------------------------------------------

    fun refreshGames() {
        if (!ready()) return
        scope.launch { loadGames() }
    }

    private suspend fun loadGames() {
        when (val result = gateway.myGames()) {
            is OnlineResult.Ok -> {
                val you = identities.load()?.accountId
                // The games load runs beside an invite's join: clearing every message here wiped "no room with
                // that code" when the list came back second (seen as a flaky web e2e test, 2026-11-08).
                val stale = gamesMessage
                gamesMessage = null
                mutableState.update {
                    it.copy(
                        games = MyGameStatuses.sorted(result.value.games, you),
                        you = you,
                        message = it.message.takeUnless { message -> message == stale },
                    )
                }
            }

            is OnlineResult.Refused -> {
                // The token stays: a recovery code or a new account replaces it, nothing is thrown away.
                if (result.reason == Refusal.BAD_TOKEN) {
                    mutableState.update { it.copy(stage = AccountStage.PICK_NAME, name = "") }
                }
                gamesMessage = result.words()
                say(gamesMessage)
            }

            // The games already listed stay on screen: they wait for the server.
            is OnlineResult.Failed -> {
                gamesMessage = result.words()
                say(gamesMessage)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------

    private fun ready(): Boolean = state.value.stage == AccountStage.READY

    private fun say(text: String?) = mutableState.update { it.copy(message = text) }

    private fun calling(block: suspend () -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true, message = null) }
        scope.launch {
            try {
                block()
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }
}
