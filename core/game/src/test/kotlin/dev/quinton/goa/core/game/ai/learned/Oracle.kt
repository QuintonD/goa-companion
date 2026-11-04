package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.SearchAi
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step

/**
 * The oracle exploiter (ADR-061), tests only and a cheat on purpose: before the reveal it runs a copy of
 * the enemy's own player on the real game to predict the enemy's card plays of the turn (the copy has
 * other dice: it knows how the enemy thinks, not its seed), and its search best-responds to them
 * ([dev.quinton.goa.core.game.ai.LevelProfile.predictPlays]). How much it gains over a fair player
 * measures how exploitable a predictable card play is. A variant `oracle/<base>` against `<enemy>`.
 */
internal object Oracle {
    private const val REPLICA_SALT = 0x0EAC1EL

    /** Predicted enemy card plays checked at the reveal, and how many were the card really played. */
    val predicted = java.util.concurrent.atomic.AtomicInteger()
    val right = java.util.concurrent.atomic.AtomicInteger()

    fun side(base: String, enemy: String): Tournament.Side = Tournament.Side("oracle/$base") { seed ->
        Player(base, Learned.side(enemy).factory(seed xor REPLICA_SALT) as SearchAi, seed)
    }

    private class Player(base: String, private val replica: SearchAi, seed: Long) : AiPlayer, AiHarness.Watcher {
        /** The game at the first card play of each turn (round × 8 + turn), and the predictions made from it. */
        private val snapshots = HashMap<Int, EngineSession>()
        private val predictions = HashMap<Int, Map<String, String>>()
        private val inner: SearchAi = run {
            val strength = base.substringBefore("+").removePrefix("s").toInt()
            val p = Learned.profile(base).copy(predictPlays = ::predict)
            SearchAi(AiLevel.of(strength), p, seed, timeCap = false)
        }

        /** Turns whose predictions were checked against the reveal. */
        private val checked = HashSet<Int>()

        override fun watch(s: EngineSession, d: Decision) {
            val st = s.state
            val key = st.round * 8 + st.turn
            val made = predictions[key]
            if (st.revealed && made != null && checked.add(key)) {
                val shown = s.events.filterIsInstance<GameEvent.CardsRevealed>().lastOrNull()?.played.orEmpty()
                for ((hero, card) in made) {
                    predicted.incrementAndGet()
                    if (shown[hero] == card) right.incrementAndGet()
                }
            }
            if (d.kind == DecisionKind.PLAY_CARD && st.step == Step.PLAY && key !in snapshots) {
                snapshots.clear()
                snapshots[key] = s.forkSession()
            }
        }

        /** The enemy's card plays of this turn as its player's copy would choose them, from the turn's start. */
        private fun predict(es: EngineSession, d: Decision): Map<String, String> {
            val key = es.state.round * 8 + es.state.turn
            predictions[key]?.let { return it }
            val snap = snapshots[key] ?: return emptyMap()
            val f = snap.forkSession()
            val out = LinkedHashMap<String, String>()
            while (true) {
                val p = f.pending ?: break
                if (p.kind != DecisionKind.PLAY_CARD || f.state.step != Step.PLAY) break
                // Our own facedown cards don't change what the enemy sees (p.4), so any will do here.
                val c = if (p.team == d.team) p.options.first() else replica.mode(f, p)
                if (p.team != d.team && c is Choice.Card) out.putIfAbsent(p.seat, c.cardId)
                f.answer(p.id, c)
            }
            predictions.clear()
            predictions[key] = out
            return out
        }

        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
            inner.choose(session, decision, cancelled)
    }
}
