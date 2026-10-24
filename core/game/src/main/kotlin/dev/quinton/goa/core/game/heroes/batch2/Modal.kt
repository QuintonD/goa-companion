package dev.quinton.goa.core.game.heroes.batch2

import dev.quinton.goa.core.game.engine.ActionScope

/**
 * "Choose one —" / "Choose one, or both —" with two bullets (p.10 "Modal Actions": "choose a bullet
 * point and apply its text, ignoring the rest … you may be able to choose multiple options … Treat this
 * as a single action. Unless the card text says otherwise you can choose the bullet points in any
 * order"). Returns the bullets to apply, in order. A bullet whose first instruction cannot be done is not
 * offered (RULINGS 38); a single possible choice is taken without a question; empty when none can start.
 * Used by Whisper, Ursafar and Bain (hero batch 2).
 */
internal fun ActionScope.chooseBullets(
    labels: List<String>,
    possible: List<Boolean>,
    both: Boolean,
    chooser: String = heroId,
): List<Int> {
    val ok = labels.indices.filter { possible[it] }
    if (ok.isEmpty()) return emptyList()
    val choices = ArrayList<Pair<String, List<Int>>>()
    for (i in ok) choices += labels[i] to listOf(i)
    if (both && ok.size == 2) {
        val (x, y) = ok
        choices += "Both: ${labels[x]}; then ${labels[y]}" to listOf(x, y)
        choices += "Both: ${labels[y]}; then ${labels[x]}" to listOf(y, x)
    }
    if (choices.size == 1) return choices.single().second
    val pick =
        chooseOption(if (both) "Choose one, or both" else "Choose one", choices.map { it.first }, chooser = chooser)
    return choices[pick ?: 0].second
}
