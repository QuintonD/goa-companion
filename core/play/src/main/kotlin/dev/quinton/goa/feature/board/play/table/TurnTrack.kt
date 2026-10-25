package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.model.match.Team

/*
 * The turn at a glance after the reveal (ADR-041): every played card in the order the heroes act,
 * as the cards lie faceup in front of their players at the table until each one is resolved and
 * moved to its Turn slot (p.7). Built only from what the person may see: the engine's GameView
 * (p.4: played cards hidden until the reveal, facedown cards stay facedown) and the events of the
 * turn, which are the log's own content.
 */

/** Where a hero stands in the turn (p.7 "Check the Initiative" … "Resolve Your Card"). */
enum class SlotState {
    /** The card is resolved: on the hero's Turn slot. */
    DONE,

    /** The hero acting now. */
    NOW,

    /** The next to act, as the initiative stands now (it is checked again after every card, p.7). */
    NEXT,

    /** Still to act, later. */
    LATER,

    /** No card this turn: the hero passed (p.7, no cards in hand). */
    PASSED,

    /** The card was revealed but left the table without being resolved (discarded by an effect, p.10). */
    SKIPPED,
}

/**
 * One hero's place in the turn. [cards] are the played card(s) as the person may see them (Emmitt's
 * second card after the first; a card resolved facedown keeps [CardView.cardId] null); empty for a
 * pass. [initiative] is the initiative as it counts (items and effects, p.7). [place] is 1-based
 * among the heroes with a card. [tied]: another hero still to act has the same initiative.
 * [action] is the action the hero chose (done, or acting and already chosen). [offBoard]: the hero
 * is not on the board (defeated); [defeated]: it was defeated during this turn.
 */
data class TrackSlot(
    val heroId: String,
    val team: Team,
    val state: SlotState,
    val cards: List<CardView>,
    val initiative: Int?,
    val place: Int?,
    val tied: Boolean = false,
    val action: ActionType? = null,
    val offBoard: Boolean = false,
    val defeated: Boolean = false,
)

/** The turn's cards in acting order: done first, then the acting hero, the next, the later ones, passes. */
data class TurnTrack(val round: Int, val turn: Int, val slots: List<TrackSlot>) {
    val acting: TrackSlot? get() = slots.firstOrNull { it.state == SlotState.NOW }
    val next: TrackSlot? get() = slots.firstOrNull { it.state == SlotState.NEXT }
    val done: List<TrackSlot> get() = slots.filter { it.state == SlotState.DONE }

    /** The heroes still to act, in order (the acting one first). */
    val upcoming: List<TrackSlot>
        get() = slots.filter { it.state == SlotState.NOW || it.state == SlotState.NEXT || it.state == SlotState.LATER }
}

/**
 * The events of the latest two turns, from the start of the turn before this one (with the end of a
 * round between them): what the turn track and the table's moments read. [events] is the whole log.
 */
fun turnLog(events: List<GameEvent>): List<GameEvent> {
    var seen = 0
    for (i in events.indices.reversed()) {
        if (events[i] is GameEvent.TurnStarted && ++seen == 2) return events.subList(i, events.size).toList()
    }
    return events.toList()
}

/** The events since the current turn started (the last TurnStarted), or all of [log] when it has none. */
fun currentTurn(log: List<GameEvent>): List<GameEvent> {
    val start = log.indexOfLast { it is GameEvent.TurnStarted }
    return if (start < 0) log else log.subList(start + 1, log.size)
}

/** The events of the turn before the current one (and the end of round after it), or empty. */
fun previousTurn(log: List<GameEvent>): List<GameEvent> {
    val last = log.indexOfLast { it is GameEvent.TurnStarted }
    if (last < 0) return emptyList()
    val before = log.subList(0, last).indexOfLast { it is GameEvent.TurnStarted }
    return if (before < 0) log.subList(0, last) else log.subList(before + 1, last)
}

/**
 * The turn track from [view] and the current turn's events ([turnEvents], from [turnLog]); null
 * before the reveal and outside the resolve phase. [printed] gives a card's printed initiative, the
 * fallback when neither the view nor the reveal says how it counted.
 */
fun turnTrack(view: GameView, turnEvents: List<GameEvent>, printed: (String) -> Int? = { null }): TurnTrack? {
    if (view.phase != GamePhase.RESOLVE) return null
    val turn = currentTurn(turnEvents)
    val revealed = turn.lastOrNull { it is GameEvent.CardsRevealed } as? GameEvent.CardsRevealed
    if (revealed == null && view.initiativeOrder.isEmpty() && view.actedThisTurn.isEmpty()) return null
    val facts = TurnFacts(view, turn, revealed, printed)
    val slots = facts.doneSlots() + facts.upcomingSlots() + facts.otherSlots()
    var place = 0
    val numbered = slots.map { slot ->
        if (slot.state == SlotState.PASSED || slot.state == SlotState.SKIPPED) slot else slot.copy(place = ++place)
    }
    return TurnTrack(view.round, view.turn, numbered)
}

/** What the turn track reads from the view and the turn's events. */
private class TurnFacts(
    private val view: GameView,
    turn: List<GameEvent>,
    private val revealed: GameEvent.CardsRevealed?,
    private val printed: (String) -> Int?,
) {
    private val heroes = view.heroes.associateBy { it.heroId }
    private val afterReveal = revealed?.let { turn.subList(turn.indexOf(it) + 1, turn.size) } ?: turn
    private val actions = afterReveal.filterIsInstance<GameEvent.ActionChosen>().associate { it.heroId to it.action }
    private val defeated = afterReveal.filterIsInstance<GameEvent.Defeated>().map { it.unitId }.toSet()
    private val done = view.actedThisTurn.filter { it in heroes }.distinct()
    private val acting = view.acting?.takeIf { it in heroes && it !in done && heroes[it]?.played != null }
    private val upcoming = listOfNotNull(acting) +
        view.initiativeOrder.filter { it in heroes && it !in done && it != acting }

    private fun revealedCard(heroId: String): String? = revealed?.played?.get(heroId)

    fun doneSlots(): List<TrackSlot> = done.map { id ->
        val hero = heroes.getValue(id)
        val cards = hero.resolved.filter { it.slot == view.turn }
            .ifEmpty { listOfNotNull(revealedCard(id)?.let { CardView(it) }) }
        TrackSlot(
            heroId = id,
            team = hero.team,
            state = SlotState.DONE,
            cards = cards,
            initiative = revealed?.initiatives?.get(id) ?: cards.firstOrNull()?.cardId?.let(printed),
            place = null,
            action = actions[id],
            offBoard = hero.at == null,
            defeated = id in defeated,
        )
    }

    fun upcomingSlots(): List<TrackSlot> {
        val initiatives = upcoming.associateWith { id ->
            val hero = heroes.getValue(id)
            hero.initiative ?: revealed?.initiatives?.get(id) ?: (hero.played?.cardId ?: revealedCard(id))?.let(printed)
        }
        val nextIndex = if (acting != null) 1 else 0
        return upcoming.mapIndexed { i, id ->
            val hero = heroes.getValue(id)
            val initiative = initiatives[id]
            TrackSlot(
                heroId = id,
                team = hero.team,
                state = when {
                    id == acting -> SlotState.NOW
                    i == nextIndex -> SlotState.NEXT
                    else -> SlotState.LATER
                },
                cards = listOfNotNull(hero.played) + hero.alsoPlayed,
                initiative = initiative,
                place = null,
                // The acting hero's tie is settled; the others' still decides who goes next (p.7).
                tied = id != acting && initiative != null &&
                    upcoming.count { it != acting && initiatives[it] == initiative } > 1,
                action = if (id == acting) actions[id] else null,
                offBoard = hero.at == null,
                defeated = id in defeated,
            )
        }
    }

    /** Heroes with no card left to resolve and none resolved: a pass, or a card that left the table. */
    fun otherSlots(): List<TrackSlot> = (revealed?.played?.keys.orEmpty() + view.heroes.map { it.heroId })
        .distinct()
        .filter { it !in done && it !in upcoming }
        .mapNotNull { id ->
            val hero = heroes[id] ?: return@mapNotNull null
            val card = revealedCard(id)
            TrackSlot(
                heroId = id,
                team = hero.team,
                state = if (card == null) SlotState.PASSED else SlotState.SKIPPED,
                cards = listOfNotNull(card?.let { CardView(it) }),
                initiative = null,
                place = null,
                offBoard = hero.at == null,
                defeated = id in defeated,
            )
        }
}

/** What a moment of the turn is (ADR-041): the transitions a player should not miss. */
enum class MomentKind {
    /** Every card was just revealed: who acts first (p.7). */
    REVEALED,

    /** A turn ended; the next one starts with everyone playing a card (p.7). */
    TURN_OVER,

    /** The fourth turn ended: Minion Battle, push, retrieve, level up, Pity coin (p.8). */
    ROUND_ENDING,

    /** A round ended and the next began. */
    ROUND_OVER,
}

/**
 * A moment of the turn the table states in words: [title] ("All cards revealed"), [text] ("Wasp acts
 * first"). [cards] are the cards of the turn just over (TURN_OVER), each on its hero's Turn slot.
 */
data class TableMoment(
    val kind: MomentKind,
    val title: String,
    val text: String,
    val cards: List<TrackSlot> = emptyList(),
)

/**
 * The moment the table is at, if any: right after the reveal until the first hero chooses an action;
 * at the end of a round while its steps run; at the start of a turn or round while cards are played.
 * [name] gives a hero's name, [mine] the person's heroes ("you act first").
 */
fun tableMoment(
    view: GameView,
    log: List<GameEvent>,
    track: TurnTrack?,
    name: (String) -> String,
    mine: Set<String> = emptySet(),
): TableMoment? = when (view.phase) {
    GamePhase.RESOLVE -> track?.let { revealMoment(it, currentTurn(log), view.tieBreaker, name, mine) }

    GamePhase.END_OF_ROUND -> TableMoment(
        MomentKind.ROUND_ENDING,
        "End of round ${view.round}",
        "Minion Battle, the push if a team has no minions left in the Battle Zone, " +
            "everyone retrieves their cards, level up, Pity coin (p.8).",
    )

    GamePhase.PLAY_CARDS -> startMoment(view, log, name)

    GamePhase.GAME_OVER -> null
}

private fun revealMoment(
    track: TurnTrack,
    turn: List<GameEvent>,
    coin: Team,
    name: (String) -> String,
    mine: Set<String>,
): TableMoment? {
    val reveal = turn.indexOfLast { it is GameEvent.CardsRevealed }
    if (reveal < 0 || track.done.isNotEmpty()) return null
    // The moment lasts until the first hero has chosen what to do.
    if (turn.drop(reveal + 1).any { it is GameEvent.ActionChosen || it is GameEvent.Placed }) return null
    val upcoming = track.upcoming
    val first = upcoming.firstOrNull() ?: return TableMoment(
        MomentKind.REVEALED,
        "All cards revealed",
        "No one has a card to resolve this turn.",
    )
    val tiedTop = upcoming.filter { it.initiative != null && it.initiative == first.initiative }
    val names = tiedTop.joinToString(" and ") { name(it.heroId) }
    val crossTeam = tiedTop.map { it.team }.distinct().size > 1
    val who = when {
        tiedTop.size < 2 -> actsFirst(first, name, mine)

        // p.7: across teams the coin's team acts first; that hero flips the coin once resolved.
        crossTeam && first.state == SlotState.NOW ->
            "$names tie on initiative ${first.initiative}. The Tie Breaker coin shows ${teamName(coin)}: " +
                "${name(first.heroId)} acts first, then flips it (p.7)." + yourMove(first, mine)

        crossTeam ->
            "$names tie on initiative ${first.initiative}. The Tie Breaker coin shows ${teamName(coin)}: " +
                "${teamName(coin)} acts first (p.7)."

        first.state == SlotState.NOW -> actsFirst(first, name, mine)

        // p.7: within one team the tied players decide; the coin does not flip.
        else -> "$names tie on initiative ${first.initiative}: their team chooses who acts first (p.7)."
    }
    val passes = track.slots.filter { it.state == SlotState.PASSED }.map { name(it.heroId) }
    val verb = if (passes.size == 1) "passes" else "pass"
    val passed = if (passes.isEmpty()) "" else " ${passes.joinToString(" and ")} $verb."
    return TableMoment(MomentKind.REVEALED, "All cards revealed", who + passed)
}

private fun actsFirst(slot: TrackSlot, name: (String) -> String, mine: Set<String>): String {
    val initiative = slot.initiative?.let { " with initiative $it" }.orEmpty()
    return "${name(slot.heroId)} acts first$initiative." + yourMove(slot, mine)
}

private fun yourMove(slot: TrackSlot, mine: Set<String>): String =
    if (slot.heroId in mine && slot.state == SlotState.NOW) " Your move." else ""

private fun startMoment(view: GameView, log: List<GameEvent>, name: (String) -> String): TableMoment? {
    if (view.round == 1 && view.turn == 1) return null
    val previous = previousTurn(log)
    if (view.turn == 1) {
        val outcomes = buildList {
            previous.filterIsInstance<GameEvent.LanePushed>().forEach { add("${teamName(it.winner)} won the push") }
            previous.filterIsInstance<GameEvent.LevelledUp>().forEach {
                add("${name(it.heroId)} reached level ${it.level}")
            }
        }
        val text = (outcomes + "Round ${view.round}: everyone plays a card facedown (p.7)").joinToString(" · ")
        return TableMoment(MomentKind.ROUND_OVER, "Round ${view.round - 1} is over", "$text.")
    }
    val ended = view.turn - 1
    val order = previous.filterIsInstance<GameEvent.CardsRevealed>().lastOrNull()?.played?.keys.orEmpty()
    val heroes = (order + view.heroes.map { it.heroId }).distinct().mapNotNull { id ->
        view.heroes.firstOrNull { it.heroId == id }
    }
    val cards = heroes.mapNotNull { hero ->
        val resolved = hero.resolved.filter { it.slot == ended }
        if (resolved.isEmpty()) {
            null
        } else {
            TrackSlot(hero.heroId, hero.team, SlotState.DONE, resolved, initiative = null, place = null)
        }
    }
    return TableMoment(
        MomentKind.TURN_OVER,
        "Turn $ended is over",
        "Turn ${view.turn}: everyone plays a card facedown, then the cards are revealed together (p.7).",
        cards,
    )
}

private fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"
