package dev.quinton.goa.core.presentation.draft

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
import dev.quinton.goa.core.domain.draft.TieBreakerCoin
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
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.players.PlayerLook
import kotlin.random.Random
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

/**
 * A player to draft, with their rating; [start] is what an unrated player counts as when balancing
 * (the group's starting rating, `EloConfig.initialRating`, as the replay starts a new player).
 */
data class PlayerOption(val player: Player, val rating: Rating?, val start: Double = Rating.START) {
    /** The rating used to balance teams and suggest captains. */
    val balanceValue: Double get() = rating?.value ?: start
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
 * The draft setup form; everything else in [SetupUiState] is derived. Persisted on every change
 * (the phone's DataStore, the hub's per-browser slot) so it survives leaving the tab, Back from the
 * board and process death; after a draft starts it becomes the next game's default ([forNextGame],
 * ADR-017). Every edit is a pure function here, so the phone and the web edit it the same way.
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

    // --- Players ---

    fun togglePlayer(id: String): SetupForm {
        val next = if (id in selected) selected - id else selected + id
        return copy(selected = next, lastGame = false).withDefaultTeams()
    }

    /** [ids] become the selection (in this order), as ticking each of them would. */
    fun withPlayers(ids: List<String>): SetupForm {
        val wanted = ids.distinct()
        if (wanted.toSet() == selected.toSet()) return this
        val kept = selected.filter { it in wanted }
        return copy(selected = kept + wanted.filter { it !in kept }, lastGame = false).withDefaultTeams()
    }

    /** A player added at the table joins the selection; re-adding someone already ticked changes nothing. */
    fun withAddedPlayer(id: String): SetupForm = if (id in
        selected
    ) {
        this
    } else {
        copy(selected = selected + id).withDefaultTeams()
    }

    // --- Teams ---

    /** Switches the team mode; the caller then balances or suggests captains ([withTeamMode] does both). */
    fun withTeamModeOnly(mode: TeamMode): SetupForm = copy(teamMode = mode, teamA = null)

    /** Switches the team mode: By Elo balances straight away, Player Draft suggests captains. */
    fun withTeamMode(mode: TeamMode, players: List<PlayerOption>, seed: Long): SetupForm {
        val switched = withTeamModeOnly(mode)
        return when (mode) {
            TeamMode.BALANCED -> switched.balanced(balanceRatings(players), seed)
            TeamMode.PLAYER_DRAFT -> switched.withSuggestedCaptains(players)
            TeamMode.MANUAL -> switched
        }
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

    /** The split with the smallest Elo gap that keeps locked players on their team ([TeamBalancer]). */
    fun balanced(ratings: Map<String, Double>, seed: Long): SetupForm {
        val split = TeamBalancer.balance(selected, ratings, locked, seed) ?: return this
        return copy(
            red = split.red,
            blue = split.blue,
            balanceDifference = split.difference,
            teamMode = TeamMode.BALANCED,
        )
    }

    fun withOddMode(mode: OddSeatMode): SetupForm = copy(oddMode = mode)

    fun withDoublePilot(id: String): SetupForm = copy(doublePilot = id)

    /** A player can captain only one team: choosing them for one side clears them from the other. */
    fun withCaptain(team: Team, id: String): SetupForm = when (team) {
        Team.RED -> copy(redCaptain = id, blueCaptain = blueCaptain.takeIf { it != id })
        Team.BLUE -> copy(blueCaptain = id, redCaptain = redCaptain.takeIf { it != id })
    }

    /** "The two most experienced players" as captains [RB p.22] ([PlayerDraft.suggestCaptains]). */
    fun withSuggestedCaptains(players: List<PlayerOption>): SetupForm {
        val games = players.associate { it.player.id to (it.rating?.games ?: 0) }
        val values = players.associate { it.player.id to it.balanceValue }
        val captains = PlayerDraft.suggestCaptains(selected, games, values)
        return copy(redCaptain = captains.getOrNull(0), blueCaptain = captains.getOrNull(1))
    }

    // --- Heroes ---

    fun withMethod(id: String): SetupForm = copy(methodId = id)

    fun withPoolSource(source: PoolSource): SetupForm = copy(poolSource = source)

    fun toggleCustomHero(id: String): SetupForm =
        copy(customSelection = if (id in customSelection) customSelection - id else customSelection + id)

    fun withCustomSelection(ids: Set<String>): SetupForm = copy(customSelection = ids)

    fun withMaxComplexity(max: Int?): SetupForm = copy(maxComplexity = max)

    fun withStepTimer(enabled: Boolean): SetupForm = copy(stepTimer = enabled)

    fun withStepSeconds(seconds: Int): SetupForm = copy(stepSeconds = seconds, stepTimer = true)

    // --- Coin ---

    /** Flips the Tie Breaker coin: the team showing is Team A [RB p.5 step 3, p.22]. */
    fun flipped(seed: Long): SetupForm = copy(teamA = TieBreakerCoin.flip(Random(seed)), flipCount = flipCount + 1)

    /** The table flipped the physical coin. */
    fun withTeamA(team: Team): SetupForm = copy(teamA = team)

    // --- Stages ---

    /** The next stage, or this form if [blocker] says the current stage isn't complete. */
    fun next(blocker: String?): SetupForm {
        if (blocker != null) return this
        val nextStage = SetupStage.entries.getOrNull(stage.ordinal + 1) ?: return this
        return copy(stage = nextStage)
    }

    /** The previous stage; null on the first one (Back leaves the setup). */
    fun back(): SetupForm? = if (stage ==
        SetupStage.PLAYERS
    ) {
        null
    } else {
        copy(stage = SetupStage.entries[stage.ordinal - 1])
    }

    /** Only earlier stages can be jumped to (later ones need this one complete). */
    fun goTo(target: SetupStage): SetupForm = if (target.ordinal < stage.ordinal) copy(stage = target) else this

    /**
     * "Draft again" with the same group (Rematch): these players and teams, keeping the method,
     * pool and timer choices, so the draft opens ready at the Players step.
     */
    fun redraft(group: DraftAgain): SetupForm = cleared().copy(
        selected = group.red + group.blue,
        teamMode = TeamMode.MANUAL,
        red = group.red,
        blue = group.blue,
        oddMode = if (group.handicap) OddSeatMode.HANDICAP else OddSeatMode.DOUBLE_PILOT,
        doublePilot = group.doublePilot,
        lastGame = true,
    )

    companion object {
        /** Ratings for balancing: an unrated player counts as a new player ([PlayerOption.start]). */
        fun balanceRatings(players: List<PlayerOption>): Map<String, Double> =
            players.associate { it.player.id to it.balanceValue }
    }
}

/**
 * The group of a finished match, for "Draft again" (Rematch, DOMAIN.md): each team's distinct
 * players, who double-piloted (if anyone), and whether the teams had uneven seats (Handicap cards).
 */
data class DraftAgain(val red: List<String>, val blue: List<String>, val doublePilot: String?, val handicap: Boolean) {
    companion object {
        fun of(match: Match): DraftAgain {
            val red = match.team(Team.RED)
            val blue = match.team(Team.BLUE)
            val doublePilot = match.participants.groupingBy {
                it.playerId
            }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
            return DraftAgain(
                red = red.map { it.playerId }.distinct(),
                blue = blue.map { it.playerId }.distinct(),
                doublePilot = doublePilot,
                handicap = red.size != blue.size,
            )
        }
    }
}

/** What the setup screen is built from (the phone's repositories, or the hub's group data). */
data class SetupInputs(
    val players: List<Player>,
    val ratings: Map<String, Rating>,
    val catalog: Catalog,
    val settings: AppSettings,
    val methods: MethodLibrary,
    /** The group's starting rating (`EloConfig.initialRating`): what an unrated player counts as. */
    val initialRating: Double = Rating.START,
)

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

    /** The double pilot actually seated (the smaller team's first player unless one was chosen). */
    val seatedDoublePilot: String?
        get() = seats.groupBy { it.pilotId }.entries.firstOrNull { it.value.size > 1 }?.key

    companion object {
        const val MAX_PLAYERS = 10
    }
}

/**
 * Everything the draft setup computes beyond `core:domain`, for the phone's ViewModel and the hub
 * (ADR-030 §1): the derived screen state, the notes, and starting the draft.
 */
object DraftSetupPresenter {
    fun derive(inputs: SetupInputs, form: SetupForm, active: DraftProgress?, message: String? = null): SetupUiState {
        val options = inputs.players.map { PlayerOption(it, inputs.ratings[it.id], inputs.initialRating) }
        val seats = seatsFor(form)
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

    /** Hero seats for the form's teams (none for a Player Draft: teams come later). */
    fun seatsFor(form: SetupForm): List<Seat> = if (form.teamMode == TeamMode.PLAYER_DRAFT) {
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

    /** Eligible hero ids for the form's pool source and complexity filter. */
    fun candidates(inputs: SetupInputs, form: SetupForm): List<String> = HeroPool.candidates(
        heroes = inputs.catalog.heroes,
        source = form.poolSource,
        ownedProducts = inputs.settings.ownedProducts,
        customSelection = form.customSelection,
        maxComplexity = form.maxComplexity,
    )

    fun ownedLabel(inputs: SetupInputs): String {
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

    /**
     * Starts the draft the form describes: the official Player Draft, or a hero board for the
     * form's seats. Null when the form can't start (a stage incomplete, no coin); a failure carries
     * the engine's [DraftError] (see [errorMessage]). The caller saves the progress and then keeps
     * `form.forNextGame()` as the next default.
     */
    fun start(inputs: SetupInputs, state: SetupUiState, env: DraftEnvironment): Result<DraftProgress>? {
        val f = state.form
        val option = state.selectedMethod ?: return null
        val teamA = f.teamA ?: return null
        if (state.blocker != null) return null
        val candidates = candidates(inputs, f)
        return if (f.teamMode == TeamMode.PLAYER_DRAFT) {
            PlayerDraftSession.start(
                id = env.newId(),
                createdAt = env.nowMillis(),
                method = option.method,
                players = f.selected,
                redCaptain = f.redCaptain.orEmpty(),
                blueCaptain = f.blueCaptain.orEmpty(),
                teamA = teamA,
                candidates = candidates,
                seed = env.newSeed(),
            ).map { DraftProgress.Players(it) }
        } else {
            DraftEngine.start(
                DraftSetup(
                    id = env.newId(),
                    createdAt = env.nowMillis(),
                    method = option.method,
                    seats = state.seats,
                    teamA = teamA,
                    candidates = candidates,
                    seed = env.newSeed(),
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
}

/**
 * A player added from the draft setup ("Quick add a player"): a colour from [colors] and a crest
 * that no existing player (archived included) has together ([PlayerLook.newLook]). The caller passes
 * the palette without the team-like colours (DESIGN.md) and saves the player through `ensurePlayer`,
 * so a known name selects that player instead of creating a second one.
 */
object QuickAddPlayer {
    const val AVATAR_COUNT = 24

    /** A new player called [name], trimmed and cut to the player editor's limit ([PlayerLook.nameInput]). */
    fun create(id: String, name: String, existing: List<Player>, now: Long, colors: List<Long>): Player? {
        val trimmed = PlayerLook.nameInput(name.trim()).trim()
        if (trimmed.isEmpty() || colors.isEmpty()) return null
        val look = PlayerLook.newLook(existing, colors, AVATAR_COUNT)
        return Player(id = id, name = trimmed, color = look.color, avatarId = look.crest, createdAt = now)
    }
}
