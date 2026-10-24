package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.SavedPayload
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import kotlin.math.abs

/**
 * The learning coach (ADR-035, PRODUCT §9). [nudge] runs Hard-level playouts on copies determinized
 * from the *human's* team's view (it never uses hidden enemy cards) and speaks only when the human's
 * choice is clearly and consistently worse than the best option *and* a concrete reason can be named;
 * otherwise it stays silent. [turningPoints] replays a saved game and explains the 3–6 turns where the
 * seat's winning chances swung most.
 */
class AiCoach(private val factory: GameFactory? = null, private val seed: Long = 0x0C0AC4L) : Coach {
    private val profile = AiPlayers.profile(dev.quinton.goa.core.game.AiLevel.HARD).copy(
        playSamples = 6,
        otherSamples = 4,
        otherHorizon = Horizon.TURN,
        candidates = 8,
        budgetMs = 1500,
    )
    private var cached: Pair<EngineData, Searcher>? = null

    private fun searcher(data: EngineData): Searcher {
        cached?.let { if (it.first === data) return it.second }
        return Searcher(Evaluator(data, profile.knobs), profile, keepLeaves = true).also { cached = data to it }
    }

    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? {
        if (decision.options.size < 2 || choice !in decision.options) return null
        // Nothing to learn from a forced pass, or from the order of tied teammates.
        if (decision.kind == DecisionKind.ACT_FIRST || decision.kind == DecisionKind.MINION_RETURN) return null
        val es = session as? EngineSession ?: return null
        val s = searcher(es.engineData)
        val deadline = System.nanoTime() + profile.budgetMs * 1_000_000L
        val a = s.analyze(es, decision, Searcher.mix(seed, decision.id), cancelled, deadline, mustInclude = choice)
            ?: return null
        val mine = a.options.firstOrNull { it.choice == choice } ?: return null
        val best = a.options.maxByOrNull { it.mean } ?: return null
        if (best.choice == choice || mine.n == 0) return null
        val gap = best.mean - mine.mean
        // Consistently worse in the samples, not just on average.
        val pairs = (0 until a.samples).filter { !best.values[it].isNaN() && !mine.values[it].isNaN() }
        if (pairs.isEmpty()) return null
        val worse = pairs.count { best.values[it] - mine.values[it] >= 1.0 }.toDouble() / pairs.size
        if (gap < NUDGE_GAP || worse < NUDGE_CONSISTENCY) return null
        val reason = Explainer(es, decision, a, choice, best.choice).explain() ?: return null
        val state = es.state
        return CoachNote(reason.first, reason.second, decision.id, state.round, state.turn)
    }

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> {
        val engine = factory as? GameEngine ?: return emptyList()
        val payload = EngineSession.JSON.decodeFromString(SavedPayload.serializer(), saved.payload)
        val session = engine.fromState(saved.setup, payload.initial)
        val team = saved.setup.seats.firstOrNull { it.heroId == seat }?.team ?: return emptyList()
        val ev = Evaluator(engine.data)
        val points = ArrayList<Snapshot>()
        fun snap() {
            val st = session.state
            points += Snapshot(st.round, st.turn, winChance(ev.evaluate(st, team)), session.events.size, session.pending?.id)
        }
        snap()
        var lastTurn = Searcher.turnIndex(session.state)
        for (rec in payload.answers) {
            if (cancelled()) break
            val r = session.answer(rec.decisionId, rec.choice)
            if (r !is dev.quinton.goa.core.game.AnswerResult.Accepted) break
            val t = Searcher.turnIndex(session.state)
            if (t != lastTurn || session.pending == null) {
                snap()
                lastTurn = t
            }
        }
        if (points.size < 2) return emptyList()
        val events = session.events
        val swings = (1 until points.size).map { i -> Swing(points[i - 1], points[i]) }
        // Turns where something happened (a defeat, a push, a level, a minion, a forced card) explain best.
        fun weight(sw: Swing): Double {
            val evs = events.subList(sw.from.events, sw.to.events)
            val big = evs.any { it is GameEvent.LanePushed || it is GameEvent.GameOver || it is GameEvent.LifeSpent }
            val some = evs.any { it is GameEvent.Defeated || it is GameEvent.Defended || it is GameEvent.LevelledUp }
            return abs(sw.delta) * if (big) 1.5 else if (some) 1.0 else 0.4
        }
        val ranked = swings.sortedByDescending(::weight)
        val chosen = ranked.filter { abs(it.delta) >= MIN_SWING }.take(MAX_POINTS)
            .let { if (it.size >= MIN_POINTS) it else ranked.take(MIN_POINTS) }
            .sortedBy { it.from.events }
        return chosen.map { sw -> TurningPoint(engine.data, saved, team, events.subList(sw.from.events, sw.to.events), sw).note() }
    }

    internal class Snapshot(val round: Int, val turn: Int, val chance: Double, val events: Int, val decisionId: Long?)

    internal class Swing(val from: Snapshot, val to: Snapshot) {
        val delta: Double get() = to.chance - from.chance
    }

    companion object {
        /** Coin-equivalents below the best option before the coach speaks, and the share of samples. */
        const val NUDGE_GAP: Double = 5.0
        const val NUDGE_CONSISTENCY: Double = 0.75
        const val MIN_SWING: Double = 0.08
        const val MIN_POINTS: Int = 3
        const val MAX_POINTS: Int = 6

        fun winChance(score: Double): Double = Evaluator.sigmoid(score / 15.0)
    }
}

/** Names the concrete reason a choice is worse, from the playouts of both options. Null = not sure. */
internal class Explainer(
    private val es: EngineSession,
    private val d: Decision,
    private val a: Analysis,
    private val mine: Choice,
    private val best: Choice,
) {
    private val data = es.engineData
    private val seat = d.seat
    private val team = d.team
    private val name = data.heroName(seat)

    private fun leaves(c: Choice) = a.leaves.filter { it.choice == c }

    private fun events(l: Leaf): List<GameEvent> = l.end.events.drop(l.startEvents)

    private fun share(c: Choice, test: (Leaf) -> Boolean): Double {
        val ls = leaves(c)
        return if (ls.isEmpty()) 0.0 else ls.count(test).toDouble() / ls.size
    }

    private fun cardName(id: String?): String? = id?.let { data.cardOrNull(it)?.name }

    @Suppress("ReturnCount")
    fun explain(): Pair<String, String>? {
        defendReason()?.let { return it }
        dangerReason()?.let { return it }
        emptyHandReason()?.let { return it }
        laneReason()?.let { return it }
        killReason()?.let { return it }
        fastTravelReason()?.let { return it }
        minionReason()?.let { return it }
        return null
    }

    private fun defendReason(): Pair<String, String>? {
        if (d.kind != DecisionKind.DEFEND) return null
        val total = d.context.attackTotal ?: return null
        val attacker = d.context.attackerId?.let { data.heroName(it) } ?: "The attacker"
        if (mine is Choice.Decline && best is Choice.Card) {
            val v = d.context.defensePreview[best.cardId]
            val card = cardName(best.cardId) ?: return null
            val numbers = v?.let { " (Defense $it against Attack $total)" } ?: ""
            return "Your $card can still stop this attack$numbers." to
                "A Defense total equal to or higher than the Attack total defends successfully; without it $name is defeated (p.14)."
        }
        if (mine is Choice.Card && best is Choice.Card) {
            val mineV = d.context.defensePreview[mine.cardId]
            if (mineV != null && mineV < total) {
                val card = cardName(best.cardId) ?: return null
                return "${cardName(mine.cardId)} isn't enough against Attack $total, but $card is." to
                    "Defense ${mineV} is below $attacker's Attack $total, so the card is discarded and $name is still defeated (p.14)."
            }
            val bv = d.context.defensePreview[best.cardId]
            if (bv != null && bv >= total) {
                return "${cardName(best.cardId)} also stops this attack and keeps ${cardName(mine.cardId)} for later." to
                    "Cards used to defend are discarded (p.10); keep the card you still want to play this round."
            }
        }
        return null
    }

    /** The chosen option gets the hero defeated far more often than the best one. */
    private fun dangerReason(): Pair<String, String>? {
        val dead = { l: Leaf -> events(l).any { it is GameEvent.Defeated && it.unitId == seat } }
        val mineRate = share(mine, dead)
        val bestRate = share(best, dead)
        if (mineRate < 0.5 || mineRate - bestRate < 0.4) return null
        // Who did it, and with what (named only when the card is face up now).
        val killers = leaves(mine).mapNotNull { l ->
            val ev = events(l)
            val i = ev.indexOfFirst { it is GameEvent.Defeated && it.unitId == seat }
            if (i < 0) return@mapNotNull null
            val by = (ev[i] as GameEvent.Defeated).byHeroId ?: return@mapNotNull null
            val card = ev.subList(0, i).lastOrNull { it is GameEvent.ActionChosen && it.heroId == by }
                ?.let { (it as GameEvent.ActionChosen).cardId }
            by to card
        }
        val (by, card) = killers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        val enemy = data.heroName(by)
        val revealedCard = card?.takeIf { cardIsOpen(by, it) }?.let { cardName(it) }
        val where = mine is Choice.Space || d.kind == DecisionKind.CHOOSE_ACTION || d.kind == DecisionKind.RESPAWN
        val text = when {
            revealedCard != null && where -> "$enemy's $revealedCard can still reach that space."
            revealedCard != null -> "$enemy's $revealedCard can still defeat $name after this."
            where -> "$enemy can still reach $name there."
            else -> "This leaves $name open to $enemy."
        }
        return text to (modifierDetail() ?: defenseDetail())
    }

    /** Is [cardId] of [heroId] face up for the deciding seat now (revealed or resolved)? */
    private fun cardIsOpen(heroId: String, cardId: String): Boolean {
        val view = es.view(seat)
        val h = view.heroes.firstOrNull { it.heroId == heroId } ?: return false
        return h.played?.cardId == cardId || h.alsoPlayed.any { it.cardId == cardId } ||
            h.resolved.any { it.cardId == cardId } || h.discard.any { it.cardId == cardId }
    }

    private fun defenseDetail(): String =
        "An attack whose total is higher than your best Defense card defeats you; minion modifiers count (p.14)."

    /** For a move: the minion defense modifier at the chosen space versus the better one (p.14). */
    private fun modifierDetail(): String? {
        val hex = (mine as? Choice.Space)?.hex ?: return null
        val state = es.state
        val enemyRanged = state.minions.any {
            it.team != team && it.type == MinionType.RANGED && it.at?.let { m -> HexGrid.distance(m, hex) in 1..2 } == true
        }
        val enemyMelee = state.minions.count {
            it.team != team && it.type != MinionType.RANGED && it.at?.let { m -> HexGrid.distance(m, hex) == 1 } == true
        }
        return when {
            enemyRanged -> "The enemy ranged minion gives you −1 Defense within 2 spaces of it (p.14)."
            enemyMelee > 0 -> "Each adjacent enemy melee minion gives you −1 Defense there (p.14)."
            else -> null
        }
    }

    private fun handAfter(l: Leaf): Int = l.end.state.hero(seat)?.hand?.size ?: 0

    private fun emptyHandReason(): Pair<String, String>? {
        if (d.kind != DecisionKind.PLAY_CARD && d.kind != DecisionKind.DEFEND && d.kind != DecisionKind.CHOOSE_CARD) return null
        val mineEmpty = share(mine) { handAfter(it) == 0 && !it.end.state.revealed.not() }
        val bestEmpty = share(best) { handAfter(it) == 0 }
        if (mineEmpty < 0.75 || bestEmpty > 0.25) return null
        return "This leaves $name no card to defend with." to
            "If the attacked hero has no cards in hand, that hero is defeated (p.13)."
    }

    private fun pushes(l: Leaf, t: Team) = events(l).any { it is GameEvent.LanePushed && it.winner == t }

    private fun laneReason(): Pair<String, String>? {
        val oursBest = share(best) { pushes(it, team) }
        val oursMine = share(mine) { pushes(it, team) }
        if (oursBest - oursMine >= 0.6) {
            return "There's a lane push for your team here." to
                "Defeating or removing the last enemy minion in the Battle Zone pushes the Lane at once (p.8)."
        }
        val theirsMine = share(mine) { pushes(it, team.opponent) }
        val theirsBest = share(best) { pushes(it, team.opponent) }
        if (theirsMine - theirsBest >= 0.6) {
            return "This gives the other team the lane push." to
                "The team with minions left in the Battle Zone wins the push; a lost push moves the fight towards your Throne (p.8)."
        }
        return null
    }

    private fun kills(l: Leaf): Int = events(l).count { e ->
        e is GameEvent.Defeated && l.end.state.hero(e.unitId)?.team == team.opponent
    }

    private fun killReason(): Pair<String, String>? {
        val bestKill = share(best) { kills(it) > 0 }
        val mineKill = share(mine) { kills(it) > 0 }
        if (bestKill - mineKill < 0.6) return null
        val victim = leaves(best).flatMap { l -> events(l).filterIsInstance<GameEvent.Defeated>() }
            .mapNotNull { e -> es.state.hero(e.unitId)?.takeIf { it.team != team }?.id }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        return "You can defeat ${data.heroName(victim)} here." to
            "A hero who cannot defend is defeated: the attacker gains coins equal to its level and its team spends Life counters (p.13)."
    }

    private fun fastTravelReason(): Pair<String, String>? {
        val act = mine as? Choice.Action ?: return null
        if (act.action != ActionType.FAST_TRAVEL) return null
        val card = d.sourceCardId?.let { data.cardOrNull(it) } ?: return null
        if (card.color != CardColor.RED && card.color != CardColor.GOLD) return null
        val colour = if (card.color == CardColor.RED) "Red" else "Gold"
        return "Fast travelling spends your $colour card's attack." to
            "Fast Travel replaces the whole Movement action; the rulebook's tip is to avoid using Red and Gold cards for it (p.11)."
    }

    private fun minionKills(l: Leaf): Int = events(l).count { e ->
        e is GameEvent.Defeated && l.end.state.hero(e.unitId) == null && e.byHeroId != null &&
            l.end.state.hero(e.byHeroId!!)?.team == team
    }

    private fun minionReason(): Pair<String, String>? {
        val more = leaves(best).map { minionKills(it) }.average() - leaves(mine).map { minionKills(it) }.average()
        if (more.isNaN() || more < 0.7) return null
        return "A minion is there for the taking." to
            "Minions can't defend: each one you defeat is 2 coins (Heavy 4) and a step towards the push (p.13, p.8)."
    }

    @Suppress("unused")
    private fun hexText(h: Hex): String = "${h.q},${h.r}"
}

/** One swing of the game, explained from the events in it. */
internal class TurningPoint(
    private val data: EngineData,
    private val saved: SavedGame,
    private val team: Team,
    private val events: List<GameEvent>,
    private val swing: AiCoach.Swing,
) {
    private fun teamOf(heroId: String?): Team? = saved.setup.seats.firstOrNull { it.heroId == heroId }?.team

    private fun plural(n: Int, what: String) = "$n $what${if (n == 1) "" else "s"}"

    fun note(): CoachNote {
        val good = swing.delta > 0
        val round = swing.from.round
        val turn = swing.from.turn
        val parts = ArrayList<String>()
        var detail = ""
        val over = events.filterIsInstance<GameEvent.GameOver>().firstOrNull()
        val defeats = events.filterIsInstance<GameEvent.Defeated>().filter { teamOf(it.unitId) != null }
        val pushes = events.filterIsInstance<GameEvent.LanePushed>()
        val levels = events.filterIsInstance<GameEvent.LevelledUp>()
        for (dft in defeats) {
            val by = dft.byHeroId?.let { " by ${data.heroName(it)}" } ?: ""
            parts += "${data.heroName(dft.unitId)} was defeated$by"
        }
        if (defeats.isNotEmpty()) {
            detail = "A defeated hero's team spends Life counters equal to its lowest card Tier and the attacker gains coins (p.13)."
        }
        for (p in pushes) {
            parts += "${if (p.winner == team) "your team" else "the other team"} pushed the lane"
            detail = "The Lane is pushed when a team loses its last minion in the Battle Zone; the fight moves one Zone (p.8)."
        }
        if (over != null) {
            parts += if (over.winner == team) "your team won" else "the other team won"
            detail = when (over.reason) {
                dev.quinton.goa.core.game.WinReason.THRONE -> "Pushing the Lane into the enemy Throne Zone wins at once (p.8)."
                dev.quinton.goa.core.game.WinReason.LAST_PUSH -> "The team that wins the push of the last Wave counter wins (p.8)."
                dev.quinton.goa.core.game.WinReason.LIFE_COUNTERS -> "Spending the last Life counter loses the game (p.13)."
                dev.quinton.goa.core.game.WinReason.CARD_TEXT -> "A card's text won the game."
            }
        }
        if (parts.isEmpty() && levels.isNotEmpty()) {
            parts += levels.joinToString(", ") { "${data.heroName(it.heroId)} reached level ${it.level}" }
            detail = "Levels bring a stronger card and an item; the lowest Tier also sets the Life cost of a defeat (p.15, p.13)."
        }
        if (parts.isEmpty()) {
            val minionKills = events.filterIsInstance<GameEvent.Defeated>().filter { teamOf(it.unitId) == null && it.byHeroId != null }
            val ours = minionKills.count { teamOf(it.byHeroId) == team }
            val theirs = minionKills.size - ours
            val defends = events.filterIsInstance<GameEvent.Defended>()
            when {
                ours + theirs > 0 -> {
                    parts += "your team defeated ${plural(ours, "minion")} to their $theirs"
                    detail = "Each minion is 2 coins (Heavy 4) and a step towards the push: the team with more minions " +
                        "in the Battle Zone wins the Minion Battle at the end of the round (p.13, p.8)."
                }
                defends.isNotEmpty() -> {
                    parts += defends.joinToString("; ") { e ->
                        "${data.heroName(e.heroId)} had to defend with ${e.cardId?.let { data.cardOrNull(it)?.name } ?: "a card"}"
                    }
                    detail = "Cards used to defend are discarded, so they can't be played later in the round (p.14, p.10)."
                }
                else -> {
                    parts += "the heroes moved into position"
                    detail = "Where heroes end a turn decides who can attack whom next; minions next to a hero change its Defense (p.14)."
                }
            }
        }
        val sentence = parts.joinToString("; ").replaceFirstChar { it.uppercase() }
        val swingText = if (good) "a good moment for your team" else "the game turned against your team"
        return CoachNote(
            text = "Round $round, turn $turn: $sentence — $swingText.",
            detail = detail,
            decisionId = swing.from.decisionId,
            round = round,
            turn = turn,
        )
    }
}
