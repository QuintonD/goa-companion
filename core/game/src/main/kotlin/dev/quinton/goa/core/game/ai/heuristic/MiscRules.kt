package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.model.board.MinionType

/**
 * The smaller questions (GENERAL §6): ACT_FIRST (G23–G26), CHOOSE_TOKEN (G69–G71), CHOOSE_OPTION
 * (G85–G88), CONFIRM (G89–G91), CHOOSE_NUMBER (G92–G94), REMOVE_MINION (G95–G99).
 *
 * Options, confirms, numbers and tokens are opaque to these scores: from strength 3 the one-ply look
 * plays each through the engine and [HEval] ranks them (G84, G85, G89, G92). G88 holds by construction:
 * a team's question is always scored from that team's side, so an enemy choosing is scored as the
 * option least bad for them. Not implemented: G25 (straight-line geometry), G86 and G91 (which bullet or
 * repeat serves the intent is card text), G94 and G99 (public effects that punish a cross or reward an
 * empty spawn are hero-specific; hero playbooks own them).
 */
internal object MiscRules {
    /**
     * ACT_FIRST, a same-team tie (RB p.7): G23 the hero who strips or moves the target before the one
     * who hits; G24 the hero an enemy still to act can defeat and whose card leaves; G26 otherwise attack
     * before movement.
     */
    fun actFirst(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val h = ctx.state.hero(o.unitId) ?: return 0.0
        val card = h.unresolved.firstOrNull()?.let { ctx.card(it.id) } ?: return 0.0
        var v = 0.0
        if (card.forcesDiscard) v += STRIP_FIRST // G23
        if (card.attackCard) v += 0.9 // G26
        if (card.hasMovement && !card.attackCard) v -= 0.35
        val at = h.at
        if (at != null && card.hasMovement && ctx.profile.threatMap) {
            // G24: their kill value, at the chance they would be defeated staying.
            val exposure = ctx.exposure(h.id, at)
            if (exposure > ctx.killWorth(h.id) * 0.4) v += exposure * ESCAPE_SHARE
        }
        return v
    }

    /** G69 an enemy piece is worth removing; G70 ours is not; G71 Stop when nothing left is worth it. */
    fun token(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Token) {
            if (o !is Choice.Decline) return 0.0
            val anyEnemy = d.options.any { it is Choice.Token && enemyPiece(ctx, it.tokenId) }
            return if (anyEnemy) -0.15 else STOP
        }
        val p = ctx.state.piece(o.tokenId) ?: return 0.0
        if (!enemyPiece(ctx, o.tokenId)) return -OWN_PIECE
        val at = p.at
        return if (at != null && ctx.inBattleZone(at)) ENEMY_NEAR else ENEMY_FAR
    }

    private fun enemyPiece(ctx: HContext, id: String): Boolean {
        val p = ctx.state.piece(id) ?: return false
        val owner = p.team ?: p.owner?.let { ctx.state.hero(it)?.team }
        return owner != null && owner != ctx.team
    }

    /** G89: an optional step is taken when it is not free damage; the look measures it from strength 3. */
    fun confirm(ctx: HContext, o: Choice): Double = if (o is Choice.Yes) 0.35 else 0.0

    /** G90: Yes that spends the last card that answers a threat still coming. */
    fun confirmCommitment(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Yes || !ctx.profile.threatMap) return 0.0
        val h = ctx.own() ?: return 0.0
        val at = h.at ?: return 0.0
        val defenders = ctx.ownHand().count { it.canDefend || it.block }
        return if (defenders <= 1 && unansweredKill(ctx, h.id, at)) -ctx.killWorth(ctx.seat) else 0.0
    }

    /** G92: more is usually more; the look picks the distance that lands best (G93 may be 0). */
    fun number(ctx: HContext, d: Decision, o: Choice): Double = if (o is Choice.Number) o.value * 0.2 else 0.0

    /** G85: every bullet goes through the look; none is preferred blind. */
    fun option(ctx: HContext, d: Decision, o: Choice): Double = 0.0

    fun optionCommitment(ctx: HContext, d: Decision, o: Choice): Double = 0.0

    /**
     * REMOVE_MINION, the Minion Battle loser (RB p.8): G95 the heavy goes last (the engine withholds it);
     * G96 remove the minion an enemy would kill for coins anyway; G97 keep the one next to our hero;
     * G98 keep a ranged minion whose radius covers an enemy hero.
     */
    fun remove(ctx: HContext, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val m = ctx.state.minion(o.unitId) ?: return 0.0
        val at = m.at ?: return 0.0
        val enemyDist = ctx.state.heroes.filter { it.team != m.team && it.at != null }
            .minOfOrNull { ctx.distance(it.at!!, at) } ?: 9
        var v = -enemyDist.toDouble() // G96
        val guards = ctx.state.heroes.any { hero ->
            hero.team == m.team && hero.at?.let { ctx.distance(it, at) == 1 } == true
        }
        if (guards && m.type != MinionType.RANGED) v -= 2.2 // G97
        val zonesEnemy = ctx.state.heroes.any { hero ->
            hero.team != m.team && hero.at?.let { ctx.distance(it, at) in 1..2 } == true
        }
        if (m.type == MinionType.RANGED && zonesEnemy) v -= 3.0 // G98
        return v
    }

    private const val STRIP_FIRST = 2.0
    private const val ESCAPE_SHARE = 0.3
    private const val STOP = 0.5
    private const val OWN_PIECE = 1.2
    private const val ENEMY_NEAR = 1.2
    private const val ENEMY_FAR = 0.8
}
