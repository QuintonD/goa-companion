package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.CoachThreat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.minionModifier
import dev.quinton.goa.core.model.catalog.Stat

/**
 * What can hit the hero this turn and next (ADR-051), read from the evaluation's own threat list
 * ([Ctx.HeroInfo.threats]) on determinized copies: a card is named when it is open now; otherwise only that
 * "a card of Attack N could", from the guesses. The Defense it would take and what the hero has are its
 * own cards, items and the minion modifier at its space (p.14).
 */
internal class ThreatFinder(private val f: CoachFacts, private val copies: List<EngineSession>) {
    private class Seen(val enemy: String, val now: Boolean, val card: String, val attack: Int)

    fun threats(): List<CoachThreat> {
        val at = f.myAt ?: return emptyList()
        if (copies.isEmpty()) return emptyList()
        val seen = copies.flatMap { det -> threatsIn(det) }
        val mod = f.g.minionModifier(at, f.team, null)
        val best = bestDefense(mod)
        val out = ArrayList<CoachThreat>()
        for ((key, list) in seen.groupBy { it.enemy to it.now }.entries.sortedBy { it.key.first }) {
            val (enemy, now) = key
            // Open card first; otherwise the strongest guess, without naming it.
            val open = list.filter { f.openCard(enemy, it.card) }.maxByOrNull { it.attack }
            val top = open ?: list.maxBy { it.attack }
            out += threat(enemy, now, open?.card, top.attack, best, mod)
        }
        // Next turn adds nothing when the same enemy can already do as much this turn.
        val told = out.filter { next ->
            next.thisTurn || out.none { it.thisTurn && it.heroId == next.heroId && it.attack >= next.attack }
        }
        return told.sortedWith(compareBy({ !it.thisTurn }, { -it.attack }, { it.heroId })).take(MAX)
    }

    private fun threatsIn(det: EngineSession): List<Seen> {
        val ctx = f.ev.context(det.state)
        val at = f.myAt ?: return emptyList()
        val out = ArrayList<Seen>()
        for ((enemy, threats) in ctx.info(f.seat).threats) {
            val from = enemy.at ?: continue
            val distance = HexGrid.distance(from, at)
            for (t in threats) {
                if (t.p.attackCard && distance <= t.reach) out += Seen(enemy.id, t.now, t.p.id, t.attackTotal)
            }
        }
        return out
    }

    private fun bestDefense(mod: Int): Int? {
        val items = f.me.itemStats.count { it == Stat.DEFENSE }
        return f.myHand.mapNotNull { c -> CardProfile.of(c).takeIf { it.canDefend }?.defense }.maxOrNull()
            ?.plus(items + mod)
    }

    @Suppress("LongParameterList")
    private fun threat(enemy: String, now: Boolean, card: String?, attack: Int, best: Int?, mod: Int): CoachThreat {
        val name = f.data.heroName(enemy)
        val whenWord = if (now) "this turn" else "next turn"
        val first = if (card != null) {
            // Next turn it is a card that comes back or stays in hand: still only what could be.
            val can = if (now) "can" else "could"
            "$name's ${f.cardName(card)} (Attack $attack) $can reach you $whenWord"
        } else {
            "$name: a card of Attack $attack could reach you $whenWord"
        }
        val modified = if (mod == 0) "" else ", counting ${if (mod > 0) "+$mod" else "−${-mod}"} from minions here"
        val total = best?.let { if (it < 0) "−${-it}" else "$it" }
        val answer = when {
            best == null -> "you hold no card that defends"
            best >= attack -> "it takes Defense $attack, and your best is $total$modified"
            else -> "it takes Defense $attack, and your best is only $total$modified"
        }
        return CoachThreat(enemy, now, card, attack, best, mod, "$first: $answer.")
    }

    private companion object {
        const val MAX = 4
    }
}
