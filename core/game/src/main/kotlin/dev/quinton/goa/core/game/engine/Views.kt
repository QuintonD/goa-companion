package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.game.MinionView
import dev.quinton.goa.core.game.PieceView

/**
 * What one seat sees (p.4 "Hidden Information"): "You can always ask another player to read any of
 * their unresolved cards, resolved cards, and discarded cards, as well as visible information on their
 * dashboards. Everything else is hidden information, including: Cards in the deck. Cards in hand. …
 * Any facedown cards." Played cards stay facedown until the reveal (p.7). Teammates' hands are hidden
 * too. A spectator (null) sees no hand.
 */
object Views {
    fun build(data: EngineData, setup: GameSetup, state: GameState, viewer: String?): GameView {
        val g = Game(data, state, emptyList(), RunMode.PROBE)
        val heroes = state.heroes.map { h -> heroView(g, setup, state, h, viewer) }
        val minions = state.minions.mapNotNull { m ->
            m.at?.let { MinionView(m.id, m.team, m.type, it, m.lane, immune = g.heavyImmune(m.id)) }
        }
        val pieces = state.pieces.mapNotNull { p ->
            val at = p.at ?: return@mapNotNull null
            val hidden = p.faceDown && (viewer == null || g.teamOf(viewer) != p.team)
            PieceView(
                id = p.id,
                kind = if (hidden) "facedown_token" else p.kind,
                label = if (hidden) "Facedown token" else p.label,
                at = at,
                ownerHeroId = p.owner,
                team = p.team,
                isUnit = p.unit || p.heroBody != null,
            )
        }
        val phase = when (state.step) {
            Step.TURN_START, Step.PLAY -> GamePhase.PLAY_CARDS
            Step.REVEAL, Step.RESOLVE, Step.END_OF_TURN -> GamePhase.RESOLVE
            Step.GAME_OVER -> GamePhase.GAME_OVER
            else -> GamePhase.END_OF_ROUND
        }
        return GameView(
            mapId = state.mapId,
            viewer = viewer,
            round = state.round,
            turn = state.turn,
            phase = phase,
            heroes = heroes,
            minions = minions,
            pieces = pieces,
            battleZones = LinkedHashMap(state.battleZones),
            wavesLeft = LinkedHashMap(state.wavesLeft),
            lifeCounters = LinkedHashMap(state.lifeCounters),
            tieBreaker = state.tieBreaker,
            initiativeOrder = if (state.revealed) g.initiativeOrder() else emptyList(),
            acting = state.acting,
            winner = state.winner,
            winReason = state.winReason,
        )
    }

    private fun heroView(g: Game, setup: GameSetup, state: GameState, h: HeroState, viewer: String?): HeroView {
        val own = viewer == h.id
        val active = g.activeCards(h.id).toSet()
        fun view(c: CardInst): CardView = CardView(
            cardId = if (c.faceDown && !own) null else c.id,
            facedown = c.faceDown,
            active = c.id in active,
            slot = if (c.zone == Zone.RESOLVED) c.slot else null,
        )
        val played = h.cards.firstOrNull { it.zone == Zone.PLAYED }?.let { c ->
            if (own || state.revealed) view(c) else CardView(null, facedown = true)
        }
        return HeroView(
            heroId = h.id,
            team = h.team,
            controller = setup.seats.firstOrNull { it.heroId == h.id }?.controller ?: Controller.Human,
            at = h.at,
            level = h.level,
            coins = h.coins,
            hand = if (own) h.cards.filter { it.zone == Zone.HAND }.map { it.id } else null,
            handCount = h.cards.count { it.zone == Zone.HAND },
            played = played,
            resolved = h.cards.filter { it.zone == Zone.RESOLVED }.sortedBy { it.slot }.map(::view),
            discard = h.cards.filter { it.zone == Zone.DISCARD }.sortedBy { it.stamp }.map(::view),
            items = h.cards.filter { it.zone == Zone.ITEM }.map { it.id },
            ultimate = h.cards.any { it.zone == Zone.ULTIMATE },
            markers = h.markers.map { MarkerView(it.kind, it.from) },
            activeEffects = active.toList(),
            counters = LinkedHashMap(h.counters),
        )
    }
}
