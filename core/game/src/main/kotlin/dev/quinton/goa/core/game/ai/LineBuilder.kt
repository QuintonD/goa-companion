package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.model.catalog.PrimaryAction

/**
 * The expected line of an option in words (ADR-051): what recurs in a majority of its playouts, and
 * only what recurs together in the same majority. A playout's hidden cards are guesses: another hero's
 * card is named only when it is open now, or the same in every playout that has the step, and then with
 * "could"; an Attack total only for an open card.
 */
internal class LineBuilder(private val f: CoachFacts) {
    private class Step(val key: String, val at: Int, val text: () -> String, val hiddenCard: String? = null)

    private val upgrading = f.d.kind == DecisionKind.UPGRADE_CARD || f.d.kind == DecisionKind.KEEP_UPGRADE

    fun line(c: Choice): List<String> {
        val leaves = f.leaves(c)
        if (leaves.size < MIN_SAMPLES) return emptyList()
        val perLeaf = leaves.map { l -> steps(l).associateBy { it.key } }
        val need = leaves.size / 2 + 1
        val keys = perLeaf.flatMap { it.keys }.distinct().sortedWith(
            compareBy<String> { k -> perLeaf.mapNotNull { it[k]?.at }.average() }.thenBy { it },
        )
        var support = perLeaf.indices.toList()
        val chosen = ArrayList<String>()
        for (k in keys) {
            val left = support.filter { k in perLeaf[it] }
            if (left.size >= need) {
                support = left
                chosen += k
            }
            if (chosen.size == MAX_STEPS) break
        }
        return chosen.map { k -> sentence(support.map { perLeaf[it].getValue(k) }) }
    }

    private fun sentence(steps: List<Step>): String {
        val first = steps.first()
        val card = first.hiddenCard
        // A guessed card: named only when every playout that has the step guessed the same one.
        if (card != null && steps.all { it.hiddenCard == card }) {
            return "${first.text()} with ${f.cardName(card)}".capitalized()
        }
        return first.text().capitalized()
    }

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    private fun steps(l: Leaf): List<Step> {
        val out = ArrayList<Step>()
        val seenKeys = HashMap<String, Int>()
        val played = HashMap<String, String>()
        fun add(key: String, at: Int, hidden: String? = null, text: () -> String) {
            val n = seenKeys.getOrElse(key) { 0 } + 1
            seenKeys[key] = n
            out += Step(if (n == 1) key else "$key#$n", at, text, hidden)
        }
        for ((i, e) in f.events(l).withIndex()) {
            when (e) {
                is GameEvent.ActionChosen -> {
                    played[e.heroId] = e.cardId
                    if (e.heroId == f.seat) ownAction(e)?.let { (k, t) -> add(k, i) { t } }
                }

                is GameEvent.Attacked -> attack(e, played[e.attackerId], l, i, ::add)

                is GameEvent.Defended -> defend(e, i, ::add)

                is GameEvent.Defeated -> defeat(e, l, i, ::add)

                is GameEvent.LanePushed -> add("push:${e.winner}", i) {
                    if (e.winner == f.team) "your team pushes the lane" else "the other team pushes the lane"
                }

                is GameEvent.LifeSpent -> add("life:${e.team}:${e.amount}", i) {
                    val who = if (e.team == f.team) "your team" else "the other team"
                    "$who spends ${e.amount} Life counter${if (e.amount == 1) "" else "s"}"
                }

                is GameEvent.GameOver -> add("over:${e.winner}", i) {
                    if (e.winner == f.team) "your team wins the game" else "the other team wins the game"
                }

                // Already asked about a level: no news in the line.
                is GameEvent.LevelledUp -> if (e.heroId == f.seat && !upgrading) {
                    add("level:${e.level}", i) { "you reach level ${e.level}" }
                }

                is GameEvent.CoinsGained -> if (e.heroId == f.seat) {
                    add("coins:${e.amount}", i) { "you gain ${e.amount} coin${if (e.amount == 1) "" else "s"}" }
                }

                is GameEvent.Swapped -> add("swap:${listOf(e.a, e.b).sorted()}", i) {
                    "${f.unitName(e.a, l.end.state)} and ${f.unitName(e.b, l.end.state)} swap places"
                }

                is GameEvent.Placed -> l.end.state.piece(e.pieceId)?.takeIf { it.owner == f.seat }?.let { p ->
                    if (!p.faceDown) add("place:${p.kind}", i) { "you place ${p.label}" }
                }

                else -> Unit
            }
        }
        return out
    }

    private fun ownAction(e: GameEvent.ActionChosen): Pair<String, String>? {
        val card = f.data.cardOrNull(e.cardId) ?: return null
        val what = when (e.action) {
            ActionType.PRIMARY -> when (card.primary) {
                PrimaryAction.ATTACK -> null

                // The attack itself is the next step, with its target.
                PrimaryAction.MOVEMENT -> "you move with ${card.name}"

                else -> "you use ${card.name}"
            }

            ActionType.MOVEMENT -> "you move with ${card.name}"

            ActionType.FAST_TRAVEL -> "you fast travel with ${card.name}"

            ActionType.ATTACK -> null

            ActionType.CLEAR -> "you clear with ${card.name}"

            ActionType.HOLD -> "you hold ${card.name}"
        } ?: return null
        return "own:${e.action}:${e.cardId}" to what
    }

    private fun attack(
        e: GameEvent.Attacked,
        card: String?,
        l: Leaf,
        at: Int,
        add: (String, Int, String?, () -> String) -> Unit,
    ) {
        val target = when {
            f.isHero(e.targetId) -> f.who(e.targetId)
            l.end.state.piece(e.targetId) != null || f.state.piece(e.targetId) != null -> "a token or figure"
            else -> minionWords(e.targetId, l)
        }
        val key = "attack:${e.attackerId}:$target"
        val open = card != null && (e.attackerId == f.seat || f.openCard(e.attackerId, card))
        if (open) {
            val cardName = f.cardName(card)
            // A card with no Attack value attacks by another card's text (a spell): the number isn't its own.
            val printed = f.data.cardOrNull(card)?.values?.attack != null
            val total = if (f.isHero(e.targetId) && printed) " (Attack ${e.total})" else ""
            add("$key:$card", at, null) {
                if (e.attackerId == f.seat) {
                    "you attack $target with $cardName$total"
                } else {
                    "${f.who(e.attackerId)} attacks $target with $cardName$total"
                }
            }
        } else {
            add(key, at, card) { "${f.who(e.attackerId)} could attack $target" }
        }
    }

    private fun defend(e: GameEvent.Defended, at: Int, add: (String, Int, String?, () -> String) -> Unit) {
        val own = e.heroId == f.seat
        val card = e.cardId
        if (own && card != null) {
            val total = e.total?.let { " (Defense $it)" }.orEmpty()
            add("def:own:$card:${e.success}", at, null) {
                "you defend with ${f.cardName(card)}$total${if (e.success) "" else ", and it falls"}"
            }
        } else {
            add("def:${e.heroId}:${e.success}", at, null) {
                val who = f.who(e.heroId)
                if (e.success) "$who could hold the attack" else "$who's defense falls"
            }
        }
    }

    private fun defeat(e: GameEvent.Defeated, l: Leaf, at: Int, add: (String, Int, String?, () -> String) -> Unit) {
        if (f.isHero(e.unitId)) {
            add("dead:${e.unitId}", at, null) {
                if (e.unitId == f.seat) "you are defeated" else "${f.who(e.unitId)} is defeated"
            }
        } else if (f.state.minion(e.unitId) != null || l.end.state.minion(e.unitId) != null) {
            val what = minionWords(e.unitId, l)
            add("deadminion:$what", at, null) { "$what is defeated" }
        }
    }

    private fun minionWords(id: String, l: Leaf): String {
        val m = f.state.minion(id) ?: l.end.state.minion(id) ?: return "a minion"
        val colour = if (m.team == f.team) "your" else "an enemy"
        return "$colour ${m.type.name.lowercase()} minion"
    }

    private companion object {
        const val MIN_SAMPLES = 3
        const val MAX_STEPS = 6
    }
}
