package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.MasterySummary
import dev.quinton.goa.core.domain.trivia.TriviaChallenge
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A quiz to open: [config] is a [TriviaConfig] as JSON ([TriviaText.encode]); [factKeys], when not
 * empty, limits the round to those facts ("Practise the missed ones"); [challenge] = the fixed
 * Challenge (ADR-032), [config] then ignored. The phone's `TriviaQuizRoute` carries the same fields.
 */
data class TriviaQuizRequest(
    val config: String,
    val factKeys: List<String> = emptyList(),
    val challenge: Boolean = false,
)

/** A hero offered in the quiz's hero picker; [verified] false = the picker marks it. */
data class HeroOption(
    val heroId: String,
    val name: String,
    val title: String?,
    val portrait: String?,
    val verified: Boolean = true,
)

/** One hero's row in the progress list: every printed number on its deck cards. */
data class HeroProgressUi(
    val heroId: String,
    val name: String,
    val portrait: String?,
    val progress: MasterySummary,
    /** Numbers a quiz on this hero alone would ask with the current Numbers and Cards. */
    val quizNumbers: Int,
    /** False when the hero's data isn't verified yet: the row marks it. */
    val verified: Boolean = true,
)

sealed interface TriviaHomeUiState {
    data object Loading : TriviaHomeUiState

    /** No hero data bundled (or it couldn't be read). */
    data object Unavailable : TriviaHomeUiState

    data class Ready(
        /** Every number on the owned heroes' cards. */
        val overall: MasterySummary,
        val setup: TriviaConfig,
        /** Owned heroes (and any chosen hero outside them), by name. */
        val heroOptions: List<HeroOption>,
        /** True when Settings limits the heroes to owned products. */
        val ownedOnly: Boolean,
        /** Heroes in the owned products (every hero when Settings doesn't limit them). */
        val ownedHeroCount: Int,
        /** The numbers the quiz as set up would ask. */
        val selection: MasterySummary,
        val heroes: List<HeroProgressUi>,
        /** Stored progress on any hero (what Reset forgets). */
        val stored: MasterySummary,
        /** Who the next round is played as (ADR-032). */
        val playingAs: PlayingAsState = PlayingAsState.GUEST_ONLY,
    ) : TriviaHomeUiState {
        val canStart: Boolean get() = selection.total > 0
        val initiativeSelected: Boolean get() = TriviaStat.INITIATIVE in setup.stats
        val selectedHeroNames: List<String>
            get() = setup.heroIds.mapNotNull { id -> heroOptions.firstOrNull { it.heroId == id }?.name }
    }
}

sealed interface TriviaHomeAction {
    data class ToggleStat(val stat: TriviaStat) : TriviaHomeAction

    data class ToggleGroup(val group: CardGroup) : TriviaHomeAction

    data class SetAnswerStyle(val style: AnswerStyle) : TriviaHomeAction

    data class SetLength(val length: Int) : TriviaHomeAction

    data class SetDuels(val enabled: Boolean) : TriviaHomeAction

    /** Picks or unpicks one hero; unpicking the last one goes back to all heroes. */
    data class ToggleHero(val heroId: String) : TriviaHomeAction

    data object AllHeroes : TriviaHomeAction
}

/** What tapping Challenge does. */
sealed interface TriviaChallengeStart {
    data class Open(val request: TriviaQuizRequest) : TriviaChallengeStart

    /** Playing as Guest: pick who's playing first (a Challenge needs a name on the board). */
    data object PickPlayer : TriviaChallengeStart

    /** Nothing loaded yet. */
    data object NotReady : TriviaChallengeStart
}

/**
 * Every hero's facts with each fact's card group, built once per catalog: the home's state is
 * rebuilt on every progress or setup change and filters these instead of re-reading the cards.
 */
private class CatalogFacts(val catalog: Catalog) {
    private val byHero: Map<String, List<Pair<TriviaFact, CardGroup?>>> = catalog.heroes.associate { hero ->
        val groups = hero.cards.associate { it.id to CardGroup.of(it) }
        hero.id to TriviaFacts.of(hero).map { it to groups[it.cardId] }
    }

    fun all(hero: Hero): List<TriviaFact> = byHero[hero.id].orEmpty().map { it.first }

    /** The same facts as [TriviaFacts.of] with these stats and groups, in the same order. */
    fun of(hero: Hero, stats: Set<TriviaStat>, groups: Set<CardGroup>): List<TriviaFact> =
        byHero[hero.id].orEmpty().filter { (fact, group) -> fact.stat in stats && group in groups }.map { it.first }
}

/**
 * The trivia home (ADR-028, ADR-032): who is playing, overall progress, the quiz setup and progress
 * per hero; starting a quiz or the Challenge. The phone's `TriviaHomeViewModel` and the web app's
 * screen both run this, in their own [scope]. [heroId] opens the setup on that one hero (a hero
 * page's "Learn the numbers").
 */
@Suppress("LongParameterList", "TooManyFunctions")
class TriviaHomeModel(
    private val scope: CoroutineScope,
    heroId: String?,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
    private val playerRepository: PlayerRepository,
    groupRepository: GroupRepository,
    /** Builds the state off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: EditClock = EditClock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    /** The setup being edited; null until the last one has been read. */
    private val setup = MutableStateFlow<TriviaConfig?>(null)

    init {
        scope.launch {
            val last = runCatching { progressRepository.lastConfig.first() }.getOrNull() ?: TriviaConfig()
            setup.value = if (heroId != null) last.copy(heroIds = listOf(heroId)) else last
        }
    }

    /** The facts, rebuilt only when the catalog itself changes (it is loaded once per process). */
    private val facts = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }
        .distinctUntilChanged { a, b -> a === b }
        .map(::CatalogFacts)

    private val playing = playingAs(playerRepository, groupRepository, progressRepository)
        .catch { emit(PlayingAsState.GUEST_ONLY) }

    val uiState: StateFlow<TriviaHomeUiState> = combine(
        facts,
        settingsRepository.settings,
        progressRepository.progress,
        setup,
        playing,
    ) { facts, settings, progress, config, playingAs ->
        if (config == null) return@combine TriviaHomeUiState.Loading
        val catalog = facts.catalog
        if (catalog.heroes.isEmpty()) return@combine TriviaHomeUiState.Unavailable
        val owned = ownedHeroes(catalog, settings.ownedProducts)
        val chosen = config.heroIds.mapNotNull(catalog::hero)
        val heroScope = chosen.ifEmpty { owned }
        val options = (owned + chosen).distinctBy { it.id }.sortedBy { it.name }
            .map { HeroOption(it.id, it.name, it.title, it.portrait, it.verified) }
        TriviaHomeUiState.Ready(
            overall = TriviaMastery.summary(owned.flatMap(facts::all), progress),
            setup = config.copy(heroIds = chosen.map { it.id }),
            heroOptions = options,
            ownedOnly = settings.ownedProducts.isNotEmpty(),
            ownedHeroCount = owned.size,
            selection = TriviaMastery.summary(
                heroScope.flatMap { facts.of(it, config.stats, config.groups) },
                progress,
            ),
            heroes = owned.sortedBy { it.name }.map { hero ->
                HeroProgressUi(
                    heroId = hero.id,
                    name = hero.name,
                    portrait = hero.portrait,
                    progress = TriviaMastery.summary(facts.all(hero), progress),
                    quizNumbers = facts.of(hero, config.stats, config.groups).size,
                    verified = hero.verified,
                )
            },
            stored = TriviaMastery.summary(catalog.heroes.flatMap(facts::all), progress),
            playingAs = playingAs,
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TriviaHomeUiState.Loading)

    fun onAction(action: TriviaHomeAction) {
        setup.update { current -> current?.let { reduce(it, action) } }
    }

    /** Saves the setup and returns the quiz to open, or null when it would ask nothing. */
    fun start(): TriviaQuizRequest? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        if (!ready.canStart) return null
        save(ready.setup)
        return TriviaQuizRequest(TriviaText.encode(ready.setup))
    }

    /** A quiz on [heroId] alone with the current Numbers, Cards and answer settings. */
    fun startHero(heroId: String): TriviaQuizRequest? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        val row = ready.heroes.firstOrNull { it.heroId == heroId } ?: return null
        if (row.quizNumbers == 0) return null
        save(ready.setup)
        return TriviaQuizRequest(TriviaText.encode(ready.setup.copy(heroIds = listOf(heroId))))
    }

    /** Forgets every learned and learning number. */
    fun resetProgress() {
        scope.launch { progressRepository.reset(null) }
    }

    /**
     * The Challenge (ADR-032): the fixed quiz over every hero in the catalog, whatever the setup and
     * the owned products. Needs a named player; Guest is asked to pick one first.
     */
    fun startChallenge(): TriviaChallengeStart {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return TriviaChallengeStart.NotReady
        return if (ready.playingAs.isGuest) {
            TriviaChallengeStart.PickPlayer
        } else {
            TriviaChallengeStart.Open(challengeRequest())
        }
    }

    /** Plays the next rounds as [playerIdOrGuest] (a player id or [TriviaProgressRepository.GUEST]). */
    fun playAs(playerIdOrGuest: String, then: (() -> Unit)? = null) {
        scope.launch {
            progressRepository.setPlayingAs(playerIdOrGuest)
            then?.invoke()
        }
    }

    /** Picks [playerId] and then opens the Challenge, once the pick is stored (the quiz reads it). */
    fun playAsAndChallenge(playerId: String, open: (TriviaQuizRequest) -> Unit) = playAs(playerId) {
        open(challengeRequest())
    }

    /**
     * Adds a player from the picker (a known name, archived included, picks that player instead of
     * creating a second one) and plays as them; [open] gets the Challenge when it was asked for.
     */
    fun addPlayer(name: String, open: ((TriviaQuizRequest) -> Unit)? = null) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        scope.launch {
            val all = playerRepository.observePlayers(includeArchived = true).first()
            val (color, avatar) = PlayerPalette.forNew(all)
            val player = playerRepository.ensurePlayer(
                Player(id = newId(), name = trimmed, color = color, avatarId = avatar, createdAt = clock.now()),
            )
            progressRepository.setPlayingAs(player.id)
            open?.invoke(challengeRequest())
        }
    }

    private fun challengeRequest() = TriviaQuizRequest(TriviaText.encode(TriviaChallenge.CONFIG), challenge = true)

    private fun save(config: TriviaConfig) {
        scope.launch { progressRepository.saveConfig(config) }
    }

    private fun ownedHeroes(catalog: Catalog, ownedProducts: Set<String>): List<Hero> =
        if (ownedProducts.isEmpty()) catalog.heroes else catalog.heroes.filter { it.product in ownedProducts }

    companion object {
        /** As in the player editor. */
        const val MAX_NAME_LENGTH = 24

        private const val STOP_TIMEOUT_MS = 5_000L

        fun reduce(config: TriviaConfig, action: TriviaHomeAction): TriviaConfig = when (action) {
            // The last Number or Cards chip can't be turned off: the quiz always asks something.
            is TriviaHomeAction.ToggleStat -> config.copy(stats = toggle(config.stats, action.stat))

            is TriviaHomeAction.ToggleGroup -> config.copy(groups = toggle(config.groups, action.group))

            is TriviaHomeAction.SetAnswerStyle -> config.copy(answerStyle = action.style)

            is TriviaHomeAction.SetLength -> config.copy(length = action.length)

            is TriviaHomeAction.SetDuels -> config.copy(duels = action.enabled)

            is TriviaHomeAction.ToggleHero -> config.copy(
                heroIds = if (action.heroId in config.heroIds) {
                    config.heroIds - action.heroId
                } else {
                    config.heroIds + action.heroId
                },
            )

            TriviaHomeAction.AllHeroes -> config.copy(heroIds = emptyList())
        }

        private fun <T> toggle(set: Set<T>, item: T): Set<T> = when {
            item !in set -> set + item
            set.size > 1 -> set - item
            else -> set
        }
    }
}
