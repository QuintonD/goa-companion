package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.CoachThreat
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.AttackInfo
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.canTarget
import dev.quinton.goa.core.game.engine.defensePreviewAgainst
import dev.quinton.goa.core.game.engine.minionModifier

/**
 * What can hit the hero this turn and next (ADR-051), read from the evaluation's own threat list
 * ([Ctx.HeroInfo.threats]) on determinized copies: a card is named when it is open now; otherwise only that
 * "a card of Attack N could", from the guesses. What the hero has to answer with comes from the same previews
 * as the Defend question (p.14), Blocks included; an enemy it is immune to is no threat this turn (p.18). Past
 * the turns of a round nothing is told: every card comes back before the next turn (p.8).
 */
internal class ThreatFinder(private val f: CoachFacts, private val copies: List<EngineSession>) {
    private class Seen(val enemy: String, val now: Boolean, val card: String, val attack: Int, val ranged: Boolean)

    fun threats(): List<CoachThreat> {
        val at = f.myAt ?: return emptyList()
        if (copies.isEmpty() || f.state.step.ordinal >= Step.EOR_EFFECTS.ordinal) return emptyList()
        val seen = copies.flatMap { det -> threatsIn(det) }
        val mod = f.g.minionModifier(at, f.team, null)
        val out = ArrayList<CoachThreat>()
        for ((key, list) in seen.groupBy { it.enemy to it.now }.entries.sortedBy { it.key.first }) {
            val (enemy, now) = key
            // Open card first; otherwise the strongest guess, without naming it.
            val open = list.filter { f.openCard(enemy, it.card) }.maxByOrNull { it.attack }
            val top = open ?: list.maxBy { it.attack }
            out += threat(enemy, now, open?.card, top, mod)
        }
        // Next turn adds nothing when the same enemy can already do as much this turn.
        val told = out.filter { next ->
            next.thisTurn || out.none { it.thisTurn && it.heroId == next.heroId && it.attack >= next.attack }
        }
        return told.sortedWith(compareBy({ !it.thisTurn }, { -it.attack }, { it.heroId })).take(MAX)
    }

    private fun threatsIn(det: EngineSession): List<Seen> {
        val ctx = f.ev.context(det.state)
        val g = Game(f.data, det.state, emptyList(), RunMode.PROBE)
        val at = f.myAt ?: return emptyList()
        val out = ArrayList<Seen>()
        for ((enemy, threats) in ctx.info(f.seat).threats) {
            val from = enemy.at ?: continue
            val distance = HexGrid.distance(from, at)
            for (t in threats) {
                if (!t.p.attackCard || distance > t.reach) continue
                // Immunity is read now: next turn's may have ended (p.18).
                if (t.now && !g.canTarget(f.seat, enemy.id, ActionKind.ATTACK, t.p.id, ranged = t.p.ranged)) continue
                out += Seen(enemy.id, t.now, t.p.id, t.attackTotal, t.p.ranged)
            }
        }
        return out
    }

    /** The hero's answers to this attack on its own cards: the same on every copy, read on the first. */
    private fun answers(enemy: String, top: Seen): Map<String, Pair<Int?, Boolean?>> {
        val g = Game(f.data, copies.first().state, emptyList(), RunMode.PROBE)
        val info = AttackInfo(enemy, top.card, f.seat, top.ranged, basic = false, primary = true, total = top.attack)
        return g.defensePreviewAgainst(f.seat, info)
    }

    private fun threat(enemy: String, now: Boolean, card: String?, top: Seen, mod: Int): CoachThreat {
        val name = f.data.heroName(enemy)
        val attack = top.attack
        val whenWord = if (now) "this turn" else "next turn"
        val first = if (card != null) {
            // Next turn it is a card that comes back or stays in hand: still only what could be.
            val can = if (now) "can" else "could"
            "$name's ${f.cardName(card)} (Attack $attack) $can reach you $whenWord"
        } else {
            "$name: a card of Attack $attack could reach you $whenWord"
        }
        val profile = card?.let { f.data.cardOrNull(it) }?.let(CardProfile::of)
        val all = answers(enemy, top)
        val blocker = all.entries.filter { it.value.second == true }.minOfOrNull { it.key }
        val maybe = all.entries.filter { it.value.second == null && f.data.card(it.key).block }.minOfOrNull { it.key }
        val best = all.entries.mapNotNull { (id, v) -> v.first?.let { id to it } }.maxByOrNull { it.second }
        val countsMinions = best != null && mod != 0 && !CardProfile.of(f.data.card(best.first)).ignoresMinionModifiers
        val modified = if (countsMinions) ", counting ${if (mod > 0) "+$mod" else "−${-mod}"} from minions here" else ""
        val total = best?.second?.let { if (it < 0) "−${-it}" else "$it" }
        val answer = when {
            // Its own text decides what it takes, so a number here would be a guess (card text beats the rules, p.3).
            profile?.textSetsAttack == true -> "its text changes what it takes"

            blocker != null -> "your ${f.cardName(blocker)} blocks it"

            best == null && maybe != null ->
                "your only answer is the Block on ${f.cardName(maybe)}, if its condition holds"

            best == null -> "you hold no card that defends"

            best.second >= attack -> "it takes Defense $attack, and your best is $total$modified"

            else -> "it takes Defense $attack, and your best is only $total$modified"
        }
        return CoachThreat(enemy, now, card, attack, best?.second, mod, "$first: $answer.")
    }

    private companion object {
        const val MAX = 4
    }
}
