package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Draft methods are DATA (ADR-012): the bundled `assets/catalog/draft-methods.json` plus methods
 * the user saves. One engine runs them all, so a rules correction is a data edit, never code.
 */

/** "Team A" = the team shown on the Tie Breaker coin; "Team B" = the other team. */
@Serializable
enum class TeamSlot {
    @SerialName("A")
    A,

    @SerialName("B")
    B,
    ;

    val other: TeamSlot get() = if (this == A) B else A
}

/** One action in a method's sequence: a team bans or picks one hero. */
@Serializable
data class StepSpec(
    val team: TeamSlot,
    val action: DraftAction,
    /** Blind step: the choice stays concealed until the hidden run ends (pass-the-phone). */
    val hidden: Boolean = false,
    /** Per-step timer override in seconds; null = the method/session default. */
    val seconds: Int? = null,
)

/** Where the hero pool comes from, applied to the eligible candidates (owned, filtered roster). */
@Serializable
sealed interface PoolRule {
    /** Every eligible hero. */
    @Serializable
    @SerialName("all")
    data object All : PoolRule

    /** [size] heroes drawn at random (House draft: 15). Re-roll and swaps allowed before starting. */
    @Serializable
    @SerialName("random")
    data class Random(val size: Int) : PoolRule

    /** Reveal (hero seats + [extra]) heroes at random (Random Draft: the rulebook says players + 2). */
    @Serializable
    @SerialName("seats_plus")
    data class SeatsPlus(val extra: Int) : PoolRule

    /** Deal [perSeat] random heroes to every seat; a seat picks only from its own hand (Single Draft). */
    @Serializable
    @SerialName("deal_per_seat")
    data class DealPerSeat(val perSeat: Int) : PoolRule
}

/** The order of bans and picks. Picks for a team whose seats are all filled are skipped. */
@Serializable
sealed interface SequenceRule {
    /** No choices: every seat gets a random hero from the pool (All Random). */
    @Serializable
    @SerialName("random_assign")
    data object RandomAssign : SequenceRule

    /** [opening] once, then [repeat] cycled until every seat is filled. */
    @Serializable
    @SerialName("pattern")
    data class Pattern(
        val opening: List<StepSpec> = emptyList(),
        val repeat: List<StepSpec>,
    ) : SequenceRule

    /**
     * A fixed sequence per (even) player count, e.g. Pick and Ban. A layout uses the smallest
     * table covering twice its larger team; the smaller team's surplus picks are skipped.
     */
    @Serializable
    @SerialName("by_count")
    data class ByCount(val tables: List<CountSequence>) : SequenceRule

    /**
     * Fixed heroes per Red/Blue seat (the rulebook's first game). The lineup whose size matches
     * an even layout is used; a hero that is not eligible leaves its seat open.
     */
    @Serializable
    @SerialName("lineup")
    data class Lineup(val lineups: List<TeamLineup>) : SequenceRule {
        fun forSeats(red: Int, blue: Int): TeamLineup? =
            lineups.firstOrNull { it.red.size == red && it.blue.size == blue }
    }
}

@Serializable
data class CountSequence(val players: Int, val steps: List<StepSpec>)

@Serializable
data class TeamLineup(val red: List<String>, val blue: List<String>)

/** Who defined a method; drives labels in the UI and whether it can be edited in place. */
@Serializable
enum class MethodOrigin {
    @SerialName("rulebook")
    RULEBOOK,

    @SerialName("house")
    HOUSE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
data class DraftMethod(
    val id: String,
    val name: String,
    val kind: DraftMethodKind,
    val origin: MethodOrigin = MethodOrigin.CUSTOM,
    val summary: String = "",
    val pool: PoolRule,
    val sequence: SequenceRule,
    /** Default per-step timer in seconds; null = no timer unless the session turns one on. */
    val stepSeconds: Int? = null,
    /** Provenance notes, e.g. which parts are rulebook [RB] and which are app choices [APP]. */
    val notes: List<String> = emptyList(),
) {
    val isBuiltIn: Boolean get() = origin != MethodOrigin.CUSTOM

    companion object {
        private val LegacyBanCount = Regex("""\b(\d+) ban\(s\)""")

        /**
         * Repairs a custom method's summary saved by builds before the editor pluralised its ban
         * count ("2 ban(s)." → "2 bans."). Applied wherever stored or imported methods are read.
         */
        fun repairSummary(summary: String): String = LegacyBanCount.replace(summary) { match ->
            val count = match.groupValues[1].toInt()
            if (count == 1) "1 ban" else "$count bans"
        }
    }
}

/** Contents of `catalog/draft-methods.json`. */
@Serializable
data class DraftMethodsFile(
    val version: Int,
    val defaultMethodId: String,
    val methods: List<DraftMethod>,
)

/** JSON codecs: strict for bundled data (typos fail tests), lenient for persisted user data. */
object DraftJson {
    val strict: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    val lenient: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun decodeMethods(text: String): DraftMethodsFile = strict.decodeFromString(DraftMethodsFile.serializer(), text)
}

/** Convenience constructors, mostly for tests and the method editor. */
fun ban(team: TeamSlot, hidden: Boolean = false) = StepSpec(team, DraftAction.BAN, hidden)

fun pick(team: TeamSlot, hidden: Boolean = false) = StepSpec(team, DraftAction.PICK, hidden)
