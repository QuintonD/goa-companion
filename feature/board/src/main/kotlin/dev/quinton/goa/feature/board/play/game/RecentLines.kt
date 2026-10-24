package dev.quinton.goa.feature.board.play.game

import dev.quinton.goa.core.game.GameEvent

/** What happened since the person last chose: a few log lines, the ones that matter most marked. */
data class RecentLines(val lines: List<String>, val strong: Set<Int> = emptySet())

/**
 * The events since the person's last answer ([from] = the event count then), for the panel when
 * the next question is theirs. Between two of their answers the computer may play a whole turn and
 * the end of round, so this keeps what a player must not miss — a hero defeated, a Life counter
 * spent, a push, a level up, the Minion Battle — and fills up with the latest lines, oldest first.
 * Round and turn headings (the title shows them) and "plays a card facedown" are left out; the
 * pity coins of one round are one line.
 */
fun recentSince(events: List<GameEvent>, from: Int, heroIds: Set<String>, max: Int = MAX_RECENT): RecentLines {
    val since = merged(
        events.drop(from.coerceIn(0, events.size)).filterNot {
            it is GameEvent.RoundStarted || it is GameEvent.TurnStarted ||
                (it is GameEvent.Note && it.text.endsWith(FACEDOWN))
        },
    )
    if (since.isEmpty()) {
        // Nothing new (a resumed game, a take back): the last lines before, so the panel isn't bare.
        val before = events.take(from.coerceIn(0, events.size)).filterNot {
            it is GameEvent.RoundStarted || it is GameEvent.TurnStarted ||
                (it is GameEvent.Note && it.text.endsWith(FACEDOWN))
        }
        return RecentLines(before.takeLast(EARLIER).map { it.text })
    }
    val important = since.indices.filter { important(since[it], heroIds) }.takeLast(max - 1)
    val rest = since.indices.filter { it !in important }.takeLast(max - important.size)
    val kept = (important + rest).sorted()
    return RecentLines(
        lines = kept.map { since[it].text },
        strong = kept.withIndex().filter { (_, i) -> i in important }.map { it.index }.toSet(),
    )
}

/** A line a player must not miss: a hero defeated, a Life counter spent, a push, a level up, the Minion Battle. */
internal fun important(event: GameEvent, heroIds: Set<String>): Boolean = when (event) {
    is GameEvent.Defeated -> event.unitId in heroIds
    is GameEvent.LifeSpent, is GameEvent.LanePushed, is GameEvent.LevelledUp, is GameEvent.GameOver -> true
    is GameEvent.Note -> event.text.startsWith(MINION_BATTLE)
    else -> false
}

/** The pity coins of a round, one line: "Wasp, Arien and Brogan gain 1 coin each (pity coin)." */
internal fun merged(events: List<GameEvent>): List<GameEvent> {
    val out = ArrayList<GameEvent>()
    var i = 0
    while (i < events.size) {
        val run = events.drop(i).takeWhile { it is GameEvent.CoinsGained && it.text.endsWith(PITY) }
            .filterIsInstance<GameEvent.CoinsGained>()
        if (run.size > 1 && run.all { it.amount == run.first().amount }) {
            val names = run.map { it.text.substringBefore(" gains ") }
            val who = names.dropLast(1).joinToString(", ") + " and " + names.last()
            val amount = run.first().amount
            out += GameEvent.Note("$who gain $amount coin${if (amount == 1) "" else "s"} each (pity coin).")
            i += run.size
        } else {
            out += events[i]
            i++
        }
    }
    return out
}

internal const val FACEDOWN = "plays a card facedown."
private const val PITY = "(pity coin)."
private const val MINION_BATTLE = "Minion Battle"
private const val EARLIER = 2
const val MAX_RECENT = 4
