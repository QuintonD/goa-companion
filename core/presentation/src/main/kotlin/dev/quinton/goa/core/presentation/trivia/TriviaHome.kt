package dev.quinton.goa.core.presentation.trivia

import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.MasterySummary
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero

/** A hero offered in the quiz's hero picker; [verified] false = the picker marks it. */
data class HeroOption(
    val heroId: String,
    val name: String,
    val title: String?,
    val portrait: String?,
    val verified: Boolean = true,
) {
    /** The picker's search: name or title contains the query (case-insensitive); blank = all. */
    fun matches(query: String): Boolean = query.isBlank() ||
        name.contains(query.trim(), ignoreCase = true) ||
        title?.contains(query.trim(), ignoreCase = true) == true
}

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
) {
    val canQuiz: Boolean get() = quizNumbers > 0
}

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
    ) : TriviaHomeUiState {
        val canStart: Boolean get() = selection.total > 0
        val initiativeSelected: Boolean get() = TriviaStat.INITIATIVE in setup.stats
        val selectedHeroNames: List<String>
            get() = setup.heroIds.mapNotNull { id -> heroOptions.firstOrNull { it.heroId == id }?.name }

        /** "7 of 2,150 numbers learned". */
        val overallTitle: String
            get() = "${TriviaText.number(overall.mastered)} of ${TriviaText.count(overall.total, "number")} learned"

        val overallSubtitle: String
            get() = if (ownedOnly) {
                "Every number on the Action cards of the heroes you own."
            } else {
                "Every number on every hero's Action cards."
            }

        /** "All heroes you own", "Arien, Brogan", "Arien, Brogan +3". */
        val heroScopeSummary: String
            get() {
                val names = selectedHeroNames
                return when {
                    names.isEmpty() -> if (ownedOnly) "All heroes you own" else "All heroes"
                    names.size <= 2 -> names.joinToString(", ")
                    else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
                }
            }

        /** How many heroes the quiz covers: the owned ones for "All heroes", else the chosen ones. */
        val heroScopeCount: Int
            get() = selectedHeroNames.let { if (it.isEmpty()) ownedHeroCount else it.size }

        /** "12 numbers in this quiz · 3 learned". */
        val startLine: String
            get() = "${TriviaText.count(selection.total, "number")} in this quiz · " +
                "${TriviaText.number(selection.mastered)} learned"

        /** Why Start is off, or a warning (see [startNote]). */
        val startNote: String? get() = startNote(this)

        /** The setup to start, or null when it would ask nothing. */
        fun startConfig(): TriviaConfig? = setup.takeIf { canStart }

        /** A quiz on [heroId] alone with the current Numbers, Cards and answer settings; null if it asks nothing. */
        fun heroConfig(heroId: String): TriviaConfig? {
            val row = heroes.firstOrNull { it.heroId == heroId } ?: return null
            if (!row.canQuiz) return null
            return setup.copy(heroIds = listOf(heroId))
        }

        /** The Reset progress confirmation. */
        val resetMessage: String
            get() = buildString {
                append("This forgets ")
                append(TriviaText.count(stored.mastered, "learned number"))
                if (stored.learning >
                    0
                ) {
                    append(" and ${TriviaText.count(stored.learning, "number")} you're still learning")
                }
                append(". Every card number starts again as new. This can't be undone.")
            }

        val canReset: Boolean get() = stored.mastered + stored.learning > 0

        /** The answer style's hint under its chips. */
        val answerStyleHint: String
            get() = if (setup.answerStyle == AnswerStyle.CHOICES) {
                "Pick the number from four close ones."
            } else {
                "Pick from every number the stat has on any card: no hints."
            }

        /** Under "Initiative duels". */
        val duelsHint: String
            get() = if (initiativeSelected) {
                "Now and then: which of two cards has the higher initiative?"
            } else {
                "Needs Initiative under Numbers."
            }

        /** The picker's "All heroes" row subtitle. */
        val allHeroesSubtitle: String
            get() = if (ownedOnly) {
                "The ${TriviaText.count(ownedHeroCount, "hero", "heroes")} you own (Settings)"
            } else {
                "Every hero, $ownedHeroCount in all"
            }
    }

    companion object {
        /**
         * The trivia home: overall progress over the owned heroes ([ownedProducts] empty = every
         * hero), the setup being edited ([config]), and progress per hero.
         */
        fun of(
            facts: TriviaCatalogFacts,
            ownedProducts: Set<String>,
            progress: Map<String, FactProgress>,
            config: TriviaConfig,
        ): TriviaHomeUiState {
            val catalog = facts.catalog
            if (catalog.heroes.isEmpty()) return Unavailable
            val owned = ownedHeroes(catalog, ownedProducts)
            val chosen = config.heroIds.mapNotNull(catalog::hero)
            val scope = chosen.ifEmpty { owned }
            val options = (owned + chosen).distinctBy { it.id }.sortedBy { it.name }
                .map { HeroOption(it.id, it.name, it.title, it.portrait, it.verified) }
            return Ready(
                overall = TriviaMastery.summary(owned.flatMap(facts::all), progress),
                setup = config.copy(heroIds = chosen.map { it.id }),
                heroOptions = options,
                ownedOnly = ownedProducts.isNotEmpty(),
                ownedHeroCount = owned.size,
                selection = TriviaMastery.summary(
                    scope.flatMap { facts.of(it, config.stats, config.groups) },
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
            )
        }

        /** The heroes of the owned products; every hero when none are set. */
        fun ownedHeroes(catalog: Catalog, ownedProducts: Set<String>): List<Hero> =
            if (ownedProducts.isEmpty()) catalog.heroes else catalog.heroes.filter { it.product in ownedProducts }
    }
}

/** A change to the quiz setup on the trivia home. */
sealed interface TriviaHomeAction {
    data class ToggleStat(val stat: TriviaStat) : TriviaHomeAction

    data class ToggleGroup(val group: CardGroup) : TriviaHomeAction

    data class SetAnswerStyle(val style: AnswerStyle) : TriviaHomeAction

    data class SetLength(val length: Int) : TriviaHomeAction

    data class SetDuels(val enabled: Boolean) : TriviaHomeAction

    /** Picks or unpicks one hero; unpicking the last one goes back to all heroes. */
    data class ToggleHero(val heroId: String) : TriviaHomeAction

    data object AllHeroes : TriviaHomeAction

    companion object {
        fun reduce(config: TriviaConfig, action: TriviaHomeAction): TriviaConfig = when (action) {
            // The last Number or Cards chip can't be turned off: the quiz always asks something.
            is ToggleStat -> config.copy(stats = toggle(config.stats, action.stat))

            is ToggleGroup -> config.copy(groups = toggle(config.groups, action.group))

            is SetAnswerStyle -> config.copy(answerStyle = action.style)

            is SetLength -> config.copy(length = action.length)

            is SetDuels -> config.copy(duels = action.enabled)

            is ToggleHero -> config.copy(
                heroIds = if (action.heroId in config.heroIds) {
                    config.heroIds - action.heroId
                } else {
                    config.heroIds + action.heroId
                },
            )

            AllHeroes -> config.copy(heroIds = emptyList())
        }

        private fun <T> toggle(set: Set<T>, item: T): Set<T> = when {
            item !in set -> set + item
            set.size > 1 -> set - item
            else -> set
        }
    }
}

/**
 * Every hero's facts with each fact's card group, built once per catalog: the home's state is
 * rebuilt on every progress or setup change and filters these instead of re-reading the cards.
 */
class TriviaCatalogFacts(val catalog: Catalog) {
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
 * Why Start is off ("No Ultimate card of the heroes you own prints Initiative."), or a warning for
 * a pool smaller than the round (questions repeat). The claim comes from the counted facts of the
 * quiz's own scope (chosen heroes, owned heroes or every hero) and names that scope, so it is true
 * as written.
 */
fun startNote(state: TriviaHomeUiState.Ready): String? {
    val setup = state.setup
    val total = state.selection.total
    return when {
        total == 0 -> {
            val groups = or(CardGroup.entries.filter { it in setup.groups }.map(TriviaText::group))
            val stats = or(TriviaStat.entries.filter { it in setup.stats }.map(TriviaText::stat))
            val scope = when {
                setup.heroIds.size == 1 -> " of the chosen hero"
                setup.heroIds.isNotEmpty() -> " of the chosen heroes"
                state.ownedOnly -> " of the heroes you own"
                else -> ""
            }
            "No $groups card$scope prints $stats. Add a number or more cards."
        }

        setup.length != TriviaConfig.ENDLESS && total < setup.length ->
            "Only ${TriviaText.count(total, "number")} for ${setup.length} questions, so some come up more than once."

        else -> null
    }
}

/** "Tier I, Tier II or Ultimate". */
private fun or(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " or " + items.last()
}

/** "● Learned 41" … — the mastery bar's parts in words, in order. */
fun MasterySummary.legend(): List<Pair<String, Int>> =
    listOf("Learned" to mastered, "Learning" to learning, "New" to unseen)

/** "Arien's Mad Dash", and ", unverified data" when the hero's data isn't verified. */
fun spokenOwned(card: TriviaCardUi): String = "${card.heroName}'s ${card.name}${spokenUnverified(card.heroVerified)}"

/** ", unverified data" after a card or hero a screen reader names, when its data isn't verified. */
fun spokenUnverified(verified: Boolean): String = if (verified) "" else ", $UNVERIFIED_SPOKEN"

const val UNVERIFIED_SPOKEN = "unverified data"

/** "Mad Dash, Arien's Red · Tier II Attack card": how a screen reader names a card. */
fun spokenCard(card: TriviaCardUi): String =
    "${card.name}, ${card.heroName}'s ${
        listOfNotNull(card.label.replace(" · ", " ").ifEmpty { null }, card.primary ?: "card").joinToString(" ")
    }${spokenUnverified(card.heroVerified)}"
