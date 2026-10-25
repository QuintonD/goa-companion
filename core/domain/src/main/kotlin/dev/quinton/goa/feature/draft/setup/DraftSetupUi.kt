package dev.quinton.goa.feature.draft.setup

import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.HeroPool
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.domain.teams.TeamBalancer
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.MethodLibrary
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.plural
import dev.quinton.goa.feature.draft.ui.tokens
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class SetupStage(val title: String, val shortTitle: String = title) {
    PLAYERS("Players"),
    TEAMS("Teams"),
    HEROES("Heroes"),
    COIN("Tie Breaker", "Coin"),
}

@Serializable
enum class TeamMode(val label: String) {
    MANUAL("Manual"),
    BALANCED("By Elo"),
    PLAYER_DRAFT("Player Draft"),
}

data class PlayerOption(val player: Player, val rating: Rating?) {
    /** "Elo 1234 · 5 games · provisional", or "Unrated". */
    val ratingLabel: String
        get() {
            val r = rating ?: return "Unrated"
            if (r.games == 0) return "Unrated"
            val provisional = if (r.provisional) " · provisional" else ""
            return "Elo ${r.value.roundToInt()} · ${plural(r.games, "game")}$provisional"
        }
}

data class MethodOption(
    val method: DraftMethod,
    val preview: List<StepToken>,
    /** Why the method can't run for the current layout, or null. */
    val unavailable: String?,
    /** Heroes the pool must hold for this layout. */
    val heroesNeeded: Int,
)

data class ResumeInfo(val title: String, val detail: String, val complete: Boolean = false)

/**
 * The setup form; everything else in [SetupUiState] is derived. Persisted on every change
 * (`DraftRepository.saveSetupForm` / the web app's table store) so it survives leaving the tab, Back
 * from the board and a reload; after a draft starts it becomes the next game's default ([forNextGame]).
 */
@Serializable
data class SetupForm(
    val stage: SetupStage = SetupStage.PLAYERS,
    val selected: List<String> = emptyList(),
    val teamMode: TeamMode = TeamMode.MANUAL,
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    val locked: Map<String, Team> = emptyMap(),
    val balanceDifference: Double? = null,
    val oddMode: OddSeatMode = OddSeatMode.DOUBLE_PILOT,
    val doublePilot: String? = null,
    val redCaptain: String? = null,
    val blueCaptain: String? = null,
    val methodId: String? = null,
    val poolSource: PoolSource = PoolSource.OWNED,
    val customSelection: Set<String> = emptySet(),
    val maxComplexity: Int? = null,
    val stepTimer: Boolean = false,
    val stepSeconds: Int = 30,
    val teamA: Team? = null,
    val flipCount: Int = 0,
    /** Players and teams were carried over from the last game (shown with a Clear action). */
    val lastGame: Boolean = false,
    @Transient val starting: Boolean = false,
) {
    /** The next game's default: same players, teams and method; a fresh coin flip. */
    fun forNextGame(): SetupForm =
        copy(stage = SetupStage.PLAYERS, teamA = null, flipCount = 0, starting = false, lastGame = true)

    /** Players, teams and captains cleared; method, pool and timer choices kept. */
    fun cleared(): SetupForm = SetupForm(
        methodId = methodId,
        poolSource = poolSource,
        customSelection = customSelection,
        maxComplexity = maxComplexity,
        stepTimer = stepTimer,
        stepSeconds = stepSeconds,
    )

    /**
     * "Draft again" with the same group (a rematch): these players and teams, keeping the method, pool
     * and timer choices, so the draft opens ready at the Players step.
     */
    fun prefilled(red: List<String>, blue: List<String>, doublePilot: String?, handicap: Boolean): SetupForm =
        cleared().copy(
            selected = red + blue,
            teamMode = TeamMode.MANUAL,
            red = red,
            blue = blue,
            oddMode = if (handicap) OddSeatMode.HANDICAP else OddSeatMode.DOUBLE_PILOT,
            doublePilot = doublePilot,
            lastGame = true,
        )

    /** Drops players who no longer exist (deleted or archived since the form was saved). */
    fun keepOnly(known: Set<String>): SetupForm {
        val kept = selected.filter { it in known }
        if (kept == selected) return this
        return copy(selected = kept, stage = SetupStage.PLAYERS, teamA = null).withDefaultTeams()
    }

    /** Keeps teams in step with [selected]: leavers drop out, newcomers join the smaller team. */
    fun withDefaultTeams(): SetupForm {
        val keepRed = red.filter { it in selected }
        val keepBlue = blue.filter { it in selected }
        val newcomers = selected.filter { it !in keepRed && it !in keepBlue }
        val r = keepRed.toMutableList()
        val b = keepBlue.toMutableList()
        for (id in newcomers) if (r.size <= b.size) r += id else b += id
        return copy(
            red = r,
            blue = b,
            locked = locked.filterKeys { it in selected },
            balanceDifference = null,
            redCaptain = redCaptain?.takeIf { it in selected },
            blueCaptain = blueCaptain?.takeIf { it in selected },
            doublePilot = doublePilot?.takeIf { it in selected },
        )
    }

    /** Ticks or unticks a player (they join or leave a team). */
    fun togglePlayer(id: String): SetupForm {
        val next = if (id in selected) selected - id else selected + id
        return copy(selected = next, lastGame = false).withDefaultTeams()
    }

    /** A player added in a hurry is ticked (re-typing someone already ticked doesn't count them twice). */
    fun withPlayer(id: String): SetupForm = if (id in
        selected
    ) {
        this
    } else {
        copy(selected = selected + id).withDefaultTeams()
    }

    /** Moves a player to the other team (tap to move). */
    fun movePlayer(id: String): SetupForm = when (id) {
        in red -> copy(red = red - id, blue = blue + id, balanceDifference = null, locked = locked - id)
        in blue -> copy(blue = blue - id, red = red + id, balanceDifference = null, locked = locked - id)
        else -> this
    }

    fun toggleLock(id: String): SetupForm {
        val team = if (id in red) Team.RED else Team.BLUE
        return copy(locked = if (id in locked) locked - id else locked + (id to team))
    }

    /** One player can't captain both teams: choosing them for one clears them from the other. */
    fun withCaptain(team: Team, id: String): SetupForm = when (team) {
        Team.RED -> copy(redCaptain = id, blueCaptain = blueCaptain.takeIf { it != id })
        Team.BLUE -> copy(blueCaptain = id, redCaptain = redCaptain.takeIf { it != id })
    }

    fun toggleCustomHero(id: String): SetupForm =
        copy(customSelection = if (id in customSelection) customSelection - id else customSelection + id)

    /** The next stage (call only when [SetupUiState.blocker] is null). */
    fun nextStage(): SetupForm = SetupStage.entries.getOrNull(stage.ordinal + 1)?.let { copy(stage = it) } ?: this

    /** The previous stage; null on the first. */
    fun previousStage(): SetupForm? =
        if (stage == SetupStage.PLAYERS) null else copy(stage = SetupStage.entries[stage.ordinal - 1])

    /** Back to an earlier stage from the stage indicator (later stages can't be skipped to). */
    fun goTo(target: SetupStage): SetupForm = if (target.ordinal < stage.ordinal) copy(stage = target) else this
}

data class SetupUiState(
    val loading: Boolean = true,
    val form: SetupForm = SetupForm(),
    val players: List<PlayerOption> = emptyList(),
    val seats: List<Seat> = emptyList(),
    val methods: List<MethodOption> = emptyList(),
    val heroes: List<HeroInfo> = emptyList(),
    val candidateCount: Int = 0,
    val ownedProductsLabel: String = "All products",
    val resume: ResumeInfo? = null,
    val message: String? = null,
    /** A House-draft style pool that will be dealt smaller than the method asks for. */
    val poolNote: String? = null,
    /**
     * "Same players and teams as last game" when the carried-over group really played the last
     * completed match; null before any game was played or when the group differs.
     */
    val lastGameNote: String? = null,
) {
    val stage: SetupStage get() = form.stage
    val selectedMethod: MethodOption? get() = methods.firstOrNull { it.method.id == form.methodId }
    val playerCount: Int get() = form.selected.size

    fun player(id: String): PlayerOption? = players.firstOrNull { it.player.id == id }

    /** Null when the current stage is complete, else what's missing (shown under the button). */
    val blocker: String?
        get() = when (form.stage) {
            SetupStage.PLAYERS -> when {
                playerCount < 2 -> "Choose at least 2 players"
                playerCount > MAX_PLAYERS -> "Up to $MAX_PLAYERS players"
                else -> null
            }

            SetupStage.TEAMS -> when (form.teamMode) {
                TeamMode.PLAYER_DRAFT -> when {
                    playerCount < PlayerDraft.MIN_PLAYERS -> "Player Draft needs ${PlayerDraft.MIN_PLAYERS}+ players"
                    form.redCaptain == null || form.blueCaptain == null -> "Choose both captains"
                    else -> null
                }

                else -> if (seats.isEmpty()) "Teams may differ by at most one player" else null
            }

            SetupStage.HEROES -> when {
                selectedMethod == null -> "Choose a method"

                selectedMethod?.unavailable != null -> selectedMethod?.unavailable

                (selectedMethod?.heroesNeeded ?: 0) > candidateCount ->
                    "Needs ${selectedMethod?.heroesNeeded} heroes, only $candidateCount match the filters"

                else -> null
            }

            SetupStage.COIN -> if (form.teamA == null) "Flip the Tie Breaker coin" else null
        }

    /** The bottom button: "Next: Teams" … "Start draft". */
    val primaryLabel: String
        get() = when {
            stage != SetupStage.COIN -> "Next: ${SetupStage.entries[stage.ordinal + 1].title}"
            form.teamMode == TeamMode.PLAYER_DRAFT -> "Start Player Draft"
            else -> "Start draft"
        }

    /** The solo player of a 3-player table (the official setup gives them two heroes). */
    val soloPlayerName: String
        get() {
            val solo = (if (form.red.size == 1) form.red else form.blue).firstOrNull()
            return solo?.let { player(it)?.player?.name } ?: "The solo player"
        }

    /** The double pilot's options (the smaller team) and the current choice. */
    val doublePilotChoices: List<String> get() = if (form.red.size < form.blue.size) form.red else form.blue

    val currentDoublePilot: String? get() = seats.groupBy { it.pilotId }.entries.firstOrNull { it.value.size > 1 }?.key

    companion object {
        const val MAX_PLAYERS = 10
    }
}

/** What the draft setup is derived from (the repositories' current values). */
data class DraftSetupInputs(
    val players: List<Player>,
    val ratings: Map<String, Rating>,
    val catalog: Catalog,
    val settings: AppSettings,
    val methods: MethodLibrary,
)

/**
 * The draft setup's derivations and its start, pure and shared by the phone's
 * `DraftSetupViewModel` and the web app (ADR-043 "Table, W3").
 */
object DraftSetupPresenter {
    fun derive(inputs: DraftSetupInputs, form: SetupForm, active: DraftProgress?, message: String?): SetupUiState {
        val options = inputs.players.map { PlayerOption(it, inputs.ratings[it.id]) }
        val seats = seats(form)
        val candidates = candidates(inputs, form)
        val methods = inputs.methods.all.map { methodOption(it, form, seats) }
        val methodId = form.methodId?.takeIf { id -> methods.any { it.method.id == id } }
            ?: inputs.methods.default?.id
        val selected = methods.firstOrNull { it.method.id == methodId }
        return SetupUiState(
            loading = false,
            form = form.copy(methodId = methodId),
            players = options,
            seats = seats,
            methods = methods,
            heroes = inputs.catalog.heroes.map { HeroInfo.of(it) },
            candidateCount = candidates.size,
            ownedProductsLabel = ownedLabel(inputs),
            resume = active?.let { resumeInfo(it) },
            message = message,
            poolNote = selected?.let { poolNote(it, candidates.size) },
        )
    }

    /** The hero seats the teams make (none for a Player Draft, whose teams come later). */
    fun seats(form: SetupForm): List<Seat> = if (form.teamMode == TeamMode.PLAYER_DRAFT) {
        emptyList()
    } else {
        SeatLayouts.build(form.red, form.blue, form.oddMode, form.doublePilot).getOrNull().orEmpty()
    }

    /**
     * The carried-over group is only "as last game" if the most recent completed match had
     * exactly these players; "and teams" only if they sat on the same sides (either colour).
     */
    fun lastGameNote(form: SetupForm, log: List<Match>): String? {
        if (!form.lastGame || form.selected.isEmpty()) return null
        val last = log.filter { it.status == MatchStatus.COMPLETED }
            .maxWithOrNull(compareBy<Match>({ it.playedAt }, { it.createdAt })) ?: return null
        if (last.playerIds != form.selected.toSet()) return null
        val red = last.team(Team.RED).mapTo(mutableSetOf()) { it.playerId }
        val blue = last.team(Team.BLUE).mapTo(mutableSetOf()) { it.playerId }
        val sides = setOf(form.red.toSet(), form.blue.toSet())
        return if (sides == setOf(red, blue)) "Same players and teams as last game" else "Same players as last game"
    }

    /**
     * A random pool larger than the eligible roster is quietly dealt smaller (House draft); say
     * so. Nothing to say when the method can't run at all (the blocker covers that).
     */
    fun poolNote(option: MethodOption, eligible: Int): String? {
        val size = (option.method.pool as? PoolRule.Random)?.size ?: return null
        if (eligible >= size || eligible < option.heroesNeeded) return null
        return "Only ${plural(eligible, "hero", "heroes")} match your filters, " +
            "so the pool is $eligible instead of $size."
    }

    fun candidates(inputs: DraftSetupInputs, form: SetupForm): List<String> = HeroPool.candidates(
        heroes = inputs.catalog.heroes,
        source = form.poolSource,
        ownedProducts = inputs.settings.ownedProducts,
        customSelection = form.customSelection,
        maxComplexity = form.maxComplexity,
    )

    private fun ownedLabel(inputs: DraftSetupInputs): String {
        val owned = inputs.settings.ownedProducts
        val total = inputs.catalog.products.size
        return if (owned.isEmpty() || owned.size >= total) "All products" else "${owned.size} of $total products"
    }

    fun methodOption(method: DraftMethod, form: SetupForm, seats: List<Seat>): MethodOption {
        if (form.teamMode == TeamMode.PLAYER_DRAFT) {
            val supported = HeroSelections.supports(method)
            val n = form.selected.size
            val needed = when (val p = method.pool) {
                PoolRule.All, is PoolRule.Random -> n
                is PoolRule.SeatsPlus -> n + p.extra
                is PoolRule.DealPerSeat -> n * p.perSeat
            }
            return MethodOption(
                method = method,
                preview = emptyList(),
                unavailable = if (supported) null else "Not available with Player Draft (needs teams)",
                heroesNeeded = needed,
            )
        }
        val teamA = form.teamA ?: Team.RED
        val countA = seats.count { it.team == teamA }
        val countB = seats.size - countA
        val plan = DraftPlanner.plan(method, countA, countB)
        val steps = plan.getOrNull()
        val needed = when (val p = method.pool) {
            is PoolRule.SeatsPlus -> seats.size + p.extra
            is PoolRule.DealPerSeat -> seats.size * p.perSeat
            else -> steps?.size ?: 0
        }
        val unavailable = when (val e = plan.exceptionOrNull()) {
            null -> null
            is DraftError.NotSupported -> e.reason
            else -> "Doesn't fit ${countA}v$countB seats"
        }
        return MethodOption(
            method,
            steps?.tokens(form.teamA).orEmpty(),
            unavailable,
            if (method.sequence is SequenceRule.Lineup) 0 else needed,
        )
    }

    fun resumeInfo(progress: DraftProgress): ResumeInfo = when (progress) {
        is DraftProgress.Board -> {
            val s = progress.session
            if (s.isComplete) {
                ResumeInfo(s.method.name, "Assign seats and save the match", complete = true)
            } else {
                ResumeInfo(s.method.name, "Step ${s.history.size + 1} of ${s.plan.size}")
            }
        }

        is DraftProgress.Players -> ResumeInfo("Player Draft · ${progress.session.method.name}", "In progress")
    }

    /** Teams split by Elo (locked players stay put); null when no split is possible. */
    fun balanced(form: SetupForm, players: List<PlayerOption>, seed: Long): SetupForm? {
        val ratings = players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
        val split = TeamBalancer.balance(form.selected, ratings, form.locked, seed) ?: return null
        return form.copy(
            red = split.red,
            blue = split.blue,
            balanceDifference = split.difference,
            teamMode = TeamMode.BALANCED,
        )
    }

    /** The two most experienced players as captains (games played, then Elo). */
    fun withSuggestedCaptains(form: SetupForm, players: List<PlayerOption>): SetupForm {
        val games = players.associate { it.player.id to (it.rating?.games ?: 0) }
        val values = players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
        val captains = PlayerDraft.suggestCaptains(form.selected, games, values)
        return form.copy(redCaptain = captains.getOrNull(0), blueCaptain = captains.getOrNull(1))
    }

    /**
     * The player the quick add creates: [name] trimmed, a palette colour that doesn't read as a team
     * and a crest, both cycling with the number of players so far ([count], archived included).
     */
    fun quickAddPlayer(name: String, count: Int, id: String, now: Long): Player = Player(
        id = id,
        name = name.trim(),
        color = PlayerPalette.quickAdd[count % PlayerPalette.quickAdd.size].dark,
        avatarId = count % PlayerPalette.AVATAR_COUNT,
        createdAt = now,
    )

    /**
     * Starts the draft [state] describes: a hero board, or the official Player Draft. [candidates] are
     * the eligible heroes ([DraftSetupPresenter.candidates]). Null when the form isn't ready.
     */
    fun start(
        state: SetupUiState,
        candidates: List<String>,
        id: String,
        createdAt: Long,
        seed: Long,
    ): Result<DraftProgress>? {
        val f = state.form
        val option = state.selectedMethod ?: return null
        val teamA = f.teamA ?: return null
        if (state.blocker != null) return null
        return if (f.teamMode == TeamMode.PLAYER_DRAFT) {
            PlayerDraftSession.start(
                id = id,
                createdAt = createdAt,
                method = option.method,
                players = f.selected,
                redCaptain = f.redCaptain.orEmpty(),
                blueCaptain = f.blueCaptain.orEmpty(),
                teamA = teamA,
                candidates = candidates,
                seed = seed,
            ).map { DraftProgress.Players(it) }
        } else {
            DraftEngine.start(
                DraftSetup(
                    id = id,
                    createdAt = createdAt,
                    method = option.method,
                    seats = state.seats,
                    teamA = teamA,
                    candidates = candidates,
                    seed = seed,
                    stepSeconds = if (f.stepTimer) f.stepSeconds else null,
                ),
            ).map { DraftProgress.Board(it) }
        }
    }

    fun errorMessage(e: Throwable): String = when (e) {
        is DraftError.PoolTooSmall -> "Needs ${e.required} heroes, only ${e.available} available. Widen the pool."
        is DraftError -> e.message ?: "Can't start this draft"
        else -> "Can't start this draft"
    }

    /** Draft method origin badge. */
    fun originLabel(method: DraftMethod): String = when (method.origin) {
        dev.quinton.goa.core.domain.draft.MethodOrigin.RULEBOOK -> "Rulebook"
        dev.quinton.goa.core.domain.draft.MethodOrigin.HOUSE -> "House"
        dev.quinton.goa.core.domain.draft.MethodOrigin.CUSTOM -> "Custom"
    }

    /** "Official Player Draft" explanation on the Teams step ([players] = how many). */
    fun playerDraftExplanation(players: Int): String =
        "The two most experienced players are Captains. First every player gets a hero with the method you " +
            "choose next. Then the Captains draft players one at a time, Team A's captain first. Everyone plays " +
            "their pre-selected hero." + if (players % 2 == 1) {
                " With an odd count, the larger team's heroes use their Handicap cards."
            } else {
                ""
            }
}
