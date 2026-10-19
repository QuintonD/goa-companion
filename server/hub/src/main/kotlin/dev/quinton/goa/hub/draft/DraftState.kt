package dev.quinton.goa.hub.draft

import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.draft.BoardLocal
import dev.quinton.goa.core.presentation.draft.BoardUiState
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.presentation.draft.DraftSetupPresenter
import dev.quinton.goa.core.presentation.draft.HeroInfo
import dev.quinton.goa.core.presentation.draft.MethodEditUiState
import dev.quinton.goa.core.presentation.draft.MethodLibrary
import dev.quinton.goa.core.presentation.draft.SetupForm
import dev.quinton.goa.core.presentation.draft.SetupInputs
import dev.quinton.goa.core.presentation.draft.SetupUiState
import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.GroupData
import dev.quinton.goa.hub.store.SharedSlots
import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * The board's per-browser state beside the draft itself: [local] is the phone's screen state
 * (pending confirm, acknowledged "pass the phone" gate, selected seat, last message, redo), kept on
 * the hub because every tap is a POST/redirect/GET. [stepDraft]/[stepIndex]/[stepStartedAt] anchor
 * the optional step clock to the step that is on the clock.
 */
@Serializable
data class DraftBoardWeb(
    val local: BoardLocal = BoardLocal(),
    val stepDraft: String? = null,
    val stepIndex: Int? = null,
    val stepStartedAt: Long? = null,
)

/** A message for the setup page (a draft that couldn't start, a saved match), shown until the next action. */
@Serializable
data class DraftNotes(val message: String? = null, val savedMatchId: String? = null)

/** The method being edited ([key]: its id, or "new"), so each tap of the editor keeps the rest. */
@Serializable
data class MethodEditWeb(val key: String, val state: MethodEditUiState)

/**
 * What a phone keeps to itself for the draft (ADR-030 §3), per browser: the setup form (the next
 * game's default, ADR-017), custom methods (not synced, as on the phone), the in-progress draft and
 * the board's screen state. Owner: the Draft area.
 */
object DraftSlots {
    val Setup = DeviceSlot("draft-setup", SetupForm.serializer())
    val Methods = DeviceSlot("draft-methods", ListSerializer(DraftMethod.serializer()))
    val Active = DeviceSlot("draft-active", DraftProgress.serializer())
    val Board = DeviceSlot("draft-board", DraftBoardWeb.serializer())
    val Notes = DeviceSlot("draft-notes", DraftNotes.serializer())
    val MethodEdit = DeviceSlot("draft-method-edit", MethodEditWeb.serializer())
}

/** Ids from UUIDs, seeds from a secure source, time from the hub's clock. */
class HubDraftEnvironment(private val clock: HubClock) : DraftEnvironment {
    private val random = SecureRandom()

    override fun newId(): String = UUID.randomUUID().toString()

    override fun newSeed(): Long = random.nextLong()

    override fun nowMillis(): Long = clock.nowMillis()
}

/**
 * Builds the draft's shared presentation inputs from the hub's data, as the phone's repositories
 * do: players from the group (merged ones hidden), ratings by replaying the group's match log
 * (ADR-003, Unknown-hero rule as `ReplayRatingsRepository` applies it), the catalog, this
 * browser's settings and methods.
 */
class DraftData(private val ctx: HubContext) {
    private val bundled: DraftMethodsFile by lazy {
        MethodLibrary.decodeBundled(runCatching { ctx.catalog.readText(METHODS_PATH) }.getOrNull())
    }

    /** Every hero's display data, from the catalog (never anything else). */
    val heroes: Map<String, HeroInfo> by lazy { ctx.catalog.catalog.heroes.associate { it.id to HeroInfo.of(it) } }

    fun library(deviceId: String): MethodLibrary =
        MethodLibrary.of(bundled, ctx.deviceStore.read(deviceId, DraftSlots.Methods).orEmpty())

    /** Player ratings from the group's match log: the one snapshot every page reads (`GroupRatings`). */
    fun ratings(): Map<String, Rating> = ctx.ratings.current().ratings.players

    fun inputs(deviceId: String, data: GroupData = ctx.groupStore.data()): SetupInputs = SetupInputs(
        players = data.players(),
        ratings = ratings(),
        catalog = ctx.catalog.catalog,
        settings = ctx.deviceStore.read(deviceId, SharedSlots.Settings) ?: AppSettings(),
        methods = library(deviceId),
        initialRating = data.eloConfig.initialRating,
    )

    /** The saved form without players who no longer exist (as the phone does when it reads it). */
    fun form(deviceId: String, data: GroupData = ctx.groupStore.data()): SetupForm {
        val known = data.players().mapTo(HashSet()) { it.id }
        return (ctx.deviceStore.read(deviceId, DraftSlots.Setup) ?: SetupForm()).keepOnly(known)
    }

    fun setup(deviceId: String, form: SetupForm? = null): SetupUiState {
        val data = ctx.groupStore.data()
        val current = form ?: form(deviceId, data)
        val active = ctx.deviceStore.read(deviceId, DraftSlots.Active)
        val notes = ctx.deviceStore.read(deviceId, DraftSlots.Notes)
        return DraftSetupPresenter.derive(inputs(deviceId, data), current, active, notes?.message)
            .copy(lastGameNote = DraftSetupPresenter.lastGameNote(current, data.matches))
    }

    fun board(deviceId: String): BoardUiState {
        val data = ctx.groupStore.data()
        val players = data.document.players.associate { it.id to (data.player(it.id) ?: it) }
        return BoardUiState(
            loading = false,
            progress = ctx.deviceStore.read(deviceId, DraftSlots.Active),
            heroes = heroes,
            players = players,
            local = (ctx.deviceStore.read(deviceId, DraftSlots.Board) ?: DraftBoardWeb()).local,
        )
    }

    fun player(id: String?): Player? = id?.let { ctx.groupStore.data().player(it) }

    companion object {
        const val METHODS_PATH = "catalog/draft-methods.json"
    }
}
