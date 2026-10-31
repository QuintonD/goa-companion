package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachReason
import dev.quinton.goa.core.game.CoachReasonKind
import dev.quinton.goa.core.game.CoachSuggestions
import dev.quinton.goa.core.game.SuggestedOption
import dev.quinton.goa.core.game.SuggestionConfidence
import kotlin.math.sqrt

/** One option's chance per sample and how it stands against the best, for the sheet and the review. */
internal class Ranked(val option: OptionValue, val chances: DoubleArray) {
    val chance: Double = chances.filter { !it.isNaN() }.average().let { if (it.isNaN()) 0.0 else it }
}

/** The options by the team's winning chance, each sample's chance through the fit ([WinChance]). */
internal fun rank(a: Analysis, heroesPerSide: Int): List<Ranked> = a.options
    .filter { it.n > 0 }
    .map { o ->
        Ranked(
            o,
            DoubleArray(o.values.size) { i ->
                if (o.values[i].isNaN()) Double.NaN else WinChance.of(o.values[i], heroesPerSide)
            },
        )
    }
    .sortedWith(compareBy({ -it.chance }, { -it.option.mean }))

/**
 * How firmly the samples rank [other] below [best]. The samples share their guesses of the hidden
 * cards (common random numbers), so the paired differences say more than the two means.
 */
internal fun confidence(best: Ranked, other: Ranked): SuggestionConfidence {
    val diffs = best.chances.indices
        .filter { !best.chances[it].isNaN() && !other.chances[it].isNaN() }
        .map { best.chances[it] - other.chances[it] }
    if (diffs.size < 2) return SuggestionConfidence.CLOSE
    val mean = diffs.average()
    val se = sqrt(diffs.sumOf { (it - mean) * (it - mean) } / (diffs.size - 1) / diffs.size)
    val ahead = diffs.count { it > 0 }.toDouble() / diffs.size
    return when {
        mean >= Confidence.CLEAR_GAP && mean - 2 * se > 0 && ahead >= Confidence.CLEAR_SHARE ->
            SuggestionConfidence.CLEAR

        mean >= Confidence.LIKELY_GAP && mean - se > 0 -> SuggestionConfidence.LIKELY

        else -> SuggestionConfidence.CLOSE
    }
}

internal object Confidence {
    const val CLEAR_GAP = 0.03
    const val CLEAR_SHARE = 0.75
    const val LIKELY_GAP = 0.01
}

/** The sheet's content for one analysis; [whys] keeps what each sentence claims, for the survey. */
internal class SuggestionBuilder(
    private val f: CoachFacts,
    private val searcher: Searcher,
    private val seed: Long,
    private val runner: SampleRunner,
    private val deadline: Long,
    private val cancelled: () -> Boolean,
) {
    val whys = LinkedHashMap<Choice, List<Why>>()

    fun build(): CoachSuggestions? {
        val ranked = rank(f.a, f.heroesPerSide)
        if (ranked.isEmpty()) return null
        val top = ranked.take(MAX_OPTIONS)
        val best = top.first()
        val second = ranked.getOrNull(1)
        val reasons = ReasonFinder(f)
        val lines = LineBuilder(f)
        val labels = distinctLabels(top.map { it.option.choice })
        val close = second != null && confidence(best, second) == SuggestionConfidence.CLOSE
        val options = top.mapIndexed { i, r ->
            val c = r.option.choice
            val compared = if (i == 0) second?.option?.choice else best.option.choice
            val found = reasons.forOption(c, compared)
            whys[c] = found
            SuggestedOption(
                choice = c,
                label = labels[i],
                chance = WinChance.percent(r.chance),
                // From the numbers shown: "5 behind" beside two equal chances read as a mistake.
                behind = (WinChance.percent(best.chance) - WinChance.percent(r.chance)).coerceAtLeast(0),
                confidence = if (i == 0) {
                    second?.let { confidence(best, it) } ?: SuggestionConfidence.CLEAR
                } else {
                    confidence(best, r)
                },
                line = lines.line(c),
                reasons = found.filterNot { it.reason.speaksAgainst() }.map { it.reason }
                    // "Enough, so the other stays" is said of the first; under another it argues against the first.
                    .filter { i == 0 || it.kind != CoachReasonKind.DEFENSE_CHEAPER }
                    .distinctBy { it.text }
                    .take(MAX_REASONS),
                against = (
                    found.filter { it.reason.speaksAgainst() }.map { it.reason } +
                        listOfNotNull(if (i == 0) null else downside(c, best.option.choice))
                    ).distinctBy { it.text },
            )
        }
        return CoachSuggestions(
            decisionId = f.d.id,
            seat = f.seat,
            options = options,
            close = close,
            samples = f.a.samples,
            threats = ThreatFinder(f, threatCopies()).threats(),
            plan = RoundPlanner(f, searcher, seed, runner, deadline, cancelled)
                .plan(top.mapNotNull { (it.option.choice as? Choice.Card)?.cardId }, close),
            advice = advice(),
        )
    }

    /** Why the sampled option trails, in the nudge's own words when it has them. */
    private fun downside(c: Choice, best: Choice): CoachReason? {
        val said = Explainer(f.es, f.d, f.a, c, best).explainWithKind() ?: return null
        // These speak of what the better option does: under this one they must say so.
        val ofTheBest = said.kind == CoachReasonKind.KILL || said.kind == CoachReasonKind.MINION ||
            said.kind == CoachReasonKind.PUSH_WON
        return if (ofTheBest) said.copy(text = "Instead: ${said.text}") else said
    }

    /** Three spaces can read the same: what else tells them apart, the distance from the hero first. */
    private fun distinctLabels(choices: List<Choice>): List<String> {
        val plain = choices.map(f::label)
        val at = f.myAt
        val withDistance = choices.mapIndexed { i, c ->
            val hex = (c as? Choice.Space)?.hex
            if (hex != null && at != null && plain.count { it == plain[i] } > 1) {
                "${plain[i]} · ${HexGrid.distance(at, hex)} from you"
            } else {
                plain[i]
            }
        }
        return withDistance.mapIndexed { i, label ->
            if (withDistance.count { it == label } > 1) {
                "$label " +
                    "(${withDistance.take(i + 1).count { it == label }})"
            } else {
                label
            }
        }
    }

    /** What the option costs, whatever else it does: told under "Against it", never under "Why". */
    private fun CoachReason.speaksAgainst(): Boolean = kind == CoachReasonKind.DEFENSE_FALLS ||
        kind == CoachReasonKind.LIFE_COST || (kind == CoachReasonKind.BLOCK && "isn't met" in text)

    private fun threatCopies() = (0 until THREAT_COPIES).map { k ->
        searcher.determinize(f.es, f.d, Searcher.mix(seed, THREAT_SEED + k))
    }

    private fun advice(): List<String> =
        f.data.catalog.hero(f.seat)?.advice.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.removePrefix("- ").trim() }

    private companion object {
        const val MAX_OPTIONS = 3
        const val MAX_REASONS = 3
        const val THREAT_COPIES = 8
        const val THREAT_SEED = 8_101L
    }
}
