package dev.quinton.goa.core.domain.setup

import dev.quinton.goa.core.model.match.GameMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Rules facts for match setup, loaded from `assets/catalog/match-setup.json`. Every number the
 * app shows at setup comes from that file (docs/DOMAIN.md, "Match setup and win conditions"),
 * so a rules correction is a one-line data change.
 */

@Serializable
data class MatchRules(
    val schemaVersion: Int,
    val sources: List<String> = emptyList(),
    val minPlayers: Int,
    val maxPlayers: Int,
    val modes: List<ModeRule>,
    val playerCounts: List<PlayerCountRule>,
    /** The six official end-of-round steps, in order. */
    val endOfRound: List<EndOfRoundStep>,
    /** How the Tie Breaker coin is used during play, one point per line (shown verbatim at setup). */
    val tieBreaker: List<String>,
    /** Note shown for extended player counts (2, odd, 8–10: rulebook pp.3, 20). */
    val extendedCountNote: String,
    /** Rulebook first-game advice. */
    val firstGameAdvice: String,
) {
    fun mode(mode: GameMode): ModeRule = modes.first { it.mode == mode }

    fun playerCount(players: Int): PlayerCountRule? = playerCounts.firstOrNull { it.players == players }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): MatchRules = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class ModeRule(
    val mode: GameMode,
    val lanes: Int,
    val waveCountersPerLane: Int,
    /** Smallest number of heroes this mode may be played with; null = no lower limit. */
    val minHeroes: Int? = null,
    /** From this many heroes this mode is the only one allowed. */
    val requiredFromHeroes: Int? = null,
    /** Life counters by heroes in play: the app's lookup (house rule for double piloting, DOMAIN.md). */
    val lifeCounters: List<LifeBracket>,
    /** The rulebook's own Life counters by player count; used to label where the house lookup differs. */
    val lifeCountersByPlayers: List<PlayerLifeBracket> = emptyList(),
    /** The rulebook lists this mode's counters from this many players (Double lane: 6). */
    val listedFromPlayers: Int? = null,
    /** The rulebook requires this mode from this many players (Double lane: 8). */
    val requiredFromPlayers: Int? = null,
    /** Rules to know when this mode is chosen (Double lane, p.20); shown at setup. */
    val rulesNote: RulesNote? = null,
) {
    val totalWaveCounters: Int get() = lanes * waveCountersPerLane

    /** Life counters per team for [heroes] on the board, or null when the rules don't list it. */
    fun lifeCountersFor(heroes: Int): Int? = lifeCounters.firstOrNull { heroes in it.minHeroes..it.maxHeroes }?.counters

    /** The rulebook's Life counters per team for [players], or null when it lists none. */
    fun rulebookLifeCountersFor(players: Int): Int? =
        lifeCountersByPlayers.firstOrNull { players in it.minPlayers..it.maxPlayers }?.counters

    /** True when the rulebook lists this mode for [players]. */
    fun listedFor(players: Int): Boolean = listedFromPlayers?.let { players >= it } ?: true

    /** True when the rulebook requires this mode for [players]. */
    fun requiredFor(players: Int): Boolean = requiredFromPlayers?.let { players >= it } ?: false
}

@Serializable
data class LifeBracket(val minHeroes: Int, val maxHeroes: Int, val counters: Int)

/** A titled list of short rule points, shown as bullets. */
@Serializable
data class RulesNote(val title: String, val points: List<String>)

@Serializable
data class PlayerLifeBracket(val minPlayers: Int, val maxPlayers: Int, val counters: Int)

@Serializable
data class PlayerCountRule(
    val players: Int,
    val recommended: Boolean = false,
    /** Extended count: recommended only when everyone has played at least 5 times. */
    val extended: Boolean = false,
    val layouts: List<SeatLayout>,
)

@Serializable
enum class LayoutKind {
    /** One hero per player, even teams. */
    @SerialName("standard")
    STANDARD,

    /** A special case spelled out by the rulebook (2 or 3 players). */
    @SerialName("rulebook")
    RULEBOOK,

    /** Odd count, uneven teams; the larger team plays Handicap cards. */
    @SerialName("handicap")
    HANDICAP,

    /** Odd count evened out by one player controlling two heroes (house option). */
    @SerialName("double_pilot")
    DOUBLE_PILOT,
}

/** How the heroes are split for a player count. Team colours are chosen at the table. */
@Serializable
data class SeatLayout(
    val id: String,
    val kind: LayoutKind,
    val largerTeamHeroes: Int,
    val smallerTeamHeroes: Int,
    /** Heroes that play with their Handicap card. */
    val handicapHeroes: Int = 0,
    /** Players who control two heroes. */
    val doublePilots: Int = 0,
    val guidance: String? = null,
) {
    val heroes: Int get() = largerTeamHeroes + smallerTeamHeroes
    val even: Boolean get() = largerTeamHeroes == smallerTeamHeroes
}

@Serializable
data class EndOfRoundStep(
    val id: String,
    val title: String,
    val detail: String? = null,
)
