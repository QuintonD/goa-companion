package dev.quinton.goa.core.game.heroes.gydion

import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.Displacement
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.effects.TokenKind
import dev.quinton.goa.core.game.effects.activeEffect
import dev.quinton.goa.core.game.effects.canMoveOther
import dev.quinton.goa.core.game.effects.discardIfAble
import dev.quinton.goa.core.game.effects.distanceTo
import dev.quinton.goa.core.game.effects.isEnemy
import dev.quinton.goa.core.game.effects.isFriendly
import dev.quinton.goa.core.game.effects.isHero
import dev.quinton.goa.core.game.effects.isMinion
import dev.quinton.goa.core.game.effects.mayRetrieve
import dev.quinton.goa.core.game.effects.moveOther
import dev.quinton.goa.core.game.effects.objectsWithin
import dev.quinton.goa.core.game.effects.script
import dev.quinton.goa.core.game.effects.tokensWithin
import dev.quinton.goa.core.game.effects.unitsWithin
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActionScope
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.adjacentAttackTarget
import dev.quinton.goa.core.game.engine.attack
import dev.quinton.goa.core.game.engine.attackTarget
import dev.quinton.goa.core.game.engine.canDisplace
import dev.quinton.goa.core.game.engine.canPlace
import dev.quinton.goa.core.game.engine.canRespawnMinion
import dev.quinton.goa.core.game.engine.canSwap
import dev.quinton.goa.core.game.engine.defeatHero
import dev.quinton.goa.core.game.engine.defeatMinion
import dev.quinton.goa.core.game.engine.gainCoins
import dev.quinton.goa.core.game.engine.handSize
import dev.quinton.goa.core.game.engine.inRange
import dev.quinton.goa.core.game.engine.isAdjacent
import dev.quinton.goa.core.game.engine.isBasic
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isEmptySpawnPoint
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.place
import dev.quinton.goa.core.game.engine.placeToken
import dev.quinton.goa.core.game.engine.rangedAttackTarget
import dev.quinton.goa.core.game.engine.regainLife
import dev.quinton.goa.core.game.engine.removeMinion
import dev.quinton.goa.core.game.engine.removePiece
import dev.quinton.goa.core.game.engine.respawnMinion
import dev.quinton.goa.core.game.engine.swap
import dev.quinton.goa.core.game.engine.unitsAdjacentTo
import dev.quinton.goa.core.game.engine.within
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BANISHMENT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BURNING_HANDS
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CLOUD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CREATE_UNDEAD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DIMENSION_DOOR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DISINTEGRATE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DOMINATE_PERSON
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.ENERGY_DRAIN
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.EXPEDITIOUS_RETREAT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIND_FAMILIAR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIREBALL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.INVULNERABILITY
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MAGIC_MISSILE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MIDAS_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POLYMORPH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POWER_WORD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.REMOVED
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHIELD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHOCKING_GRASP
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUGGESTION
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUNBURST
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.VAMPIRIC_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.WISH
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType

/**
 * The 22 spell cards of Gydion's spellbook (hero JSON `extras`). Each spell's action is performed by
 * the hero casting it ("you" = the caster, [ActionScope.heroId]); values are the spell card's icons with
 * the caster's items (p.16). Verbatim text above each registration.
 */
internal object GydionSpells {
    const val FAMILIAR: String = "token_familiar"

    fun register(r: Registry) {
        // [APP] rulings/gydion.md: the number of Familiar tokens in the box is not in our sources.
        r.token(TokenKind(FAMILIAR, "Familiar", supply = null))

        // Magic Missile (gold, Attack 1, Range 3, ranged): "Target a unit in range and not adjacent to you."
        r.card(MAGIC_MISSILE, script { magicMissile() })
        // Shocking Grasp (gold, Attack 3): "Target a unit adjacent to you. After the attack: Move the
        // target up to 1 space."
        r.card(SHOCKING_GRASP, script { shockingGrasp() })
        // Expeditious Retreat (gold, Movement 5): "Move only in a straight line."
        r.card(EXPEDITIOUS_RETREAT, script { movementAction(straightLine = true) })
        // Burning Hands (red I, Attack 5): "Target a unit adjacent to you. Before the attack: Up to 1
        // enemy hero adjacent to the target discards a card, if able."
        r.card(BURNING_HANDS, script { burningHands() })
        // Fireball (red II, Attack 5, Range 3, ranged): "Target a unit in range, not adjacent to you or to
        // your friendly unit."
        r.card(FIREBALL, script { fireball() })
        // Sunburst (red III, Attack 0+, Range 0+, ranged): "Target a unit at maximum range. +1 Attack and
        // +1 Range for each other spell card removed from the spellbook."
        r.card(SUNBURST, script { sunburst() })
        // Vampiric Touch (red II, Attack 5): "Target a unit adjacent to you. After the attack: You may
        // retrieve a discarded card."
        r.card(VAMPIRIC_TOUCH, script { vampiricTouch() })
        // Create Undead (red II, Range 3): "Respawn a friendly minion in an empty friendly spawn point in
        // range in the battle zone."
        r.card(CREATE_UNDEAD, script { createUndead() })
        // Energy Drain (red III, Range 3): "An enemy hero in range discards a non-basic card, if able. Your
        // team regains 1 spent Life counter."
        r.card(ENERGY_DRAIN, script { energyDrain() })
        // Shield (green I): "This round: You are immune to basic attacks. (Cancelled if the spell is
        // returned to the spellbook.)"
        r.card(SHIELD, script(behavior = AttackImmunity(basic = true)) { activeEffect(Timing.THIS_ROUND) })
        // Banishment (green II, Radius 3): "Place a unit or a token adjacent to you into a space in radius."
        r.card(BANISHMENT, script { banishment() })
        // Invulnerability (green III): "This Round: You are immune to non-basic attacks."
        r.card(INVULNERABILITY, script(behavior = AttackImmunity(basic = false)) { activeEffect(Timing.THIS_ROUND) })
        // Dimension Door (green II, Radius 0+): "Place yourself into a space at maximum radius. +1 Radius
        // for each other spell card removed from the spellbook."
        r.card(DIMENSION_DOOR, script { dimensionDoor() })
        // Find Familiar (green II, Radius 3): "Place a Familiar token in radius. You may remove up to three
        // other spell cards from the spellbook faceup."
        r.card(FIND_FAMILIAR, script { findFamiliar() })
        // Cloud Kill (green III, Radius 3): "An enemy hero in radius discards a basic card, if able."
        r.card(CLOUD_KILL, script { discardSpell(radius, "a basic card") { g.isBasic(it) } })
        // Suggestion (blue I, Radius 3): "If able, an enemy hero in radius moves 3 spaces in a straight line."
        r.card(SUGGESTION, script { suggestion() })
        // Dominate Person (blue II, Radius 3): "Target an enemy hero in radius. Defeat an enemy minion in
        // radius adjacent to the target."
        r.card(DOMINATE_PERSON, script { dominatePerson() })
        // Power Word Kill (blue III, Radius 3): "Defeat an enemy hero in radius with no cards in hand."
        r.card(POWER_WORD_KILL, script { powerWordKill() })
        // Midas' Touch (blue II): "Gain 1 coin for every two other spell cards removed from the spellbook."
        r.card(MIDAS_TOUCH, script { midasTouch() })
        // Disintegrate (blue II): "Remove a token or an enemy non-heavy minion adjacent to you."
        r.card(DISINTEGRATE, script { disintegrate() })
        // Polymorph (blue III, Radius 3): "Swap an enemy hero in radius with a token in radius or with an
        // enemy minion in radius."
        r.card(POLYMORPH, script { polymorph() })
        // Wish (purple): "Cast any spell in the Spellbook. After you cast the 'Wish' spell three times your
        // team wins the game." (The count and the win are in Spellbook.cast.)
        r.card(WISH, script { Spellbook.castOneOf(this, Spellbook.ALL - WISH) })
    }

    // ---- attacks -------------------------------------------------------------------------------

    private fun ActionScope.magicMissile() {
        val t = rangedAttackTarget("Target a unit in range and not adjacent to you") { !isAdjacent(it) } ?: return
        attack(t, valid = { inRange(it) && !isAdjacent(it) })
    }

    private fun ActionScope.shockingGrasp() {
        val t = adjacentAttackTarget() ?: return
        val res = attack(t)
        if (!res.connected) return
        // "Move the target up to 1 space": a defeated target is gone; you choose the space.
        if (g.posOf(t) == null || !canTarget(t)) return
        moveOther(t, 0, 1, optional = true)
    }

    private fun ActionScope.burningHands() {
        val t = adjacentAttackTarget() ?: return
        // "Before the attack: Up to 1 enemy hero adjacent to the target discards a card, if able."
        val at = g.posOf(t)!!
        val victims = g.unitsAdjacentTo(at).filter { isHero(it) && isEnemy(it) && canTarget(it) }
        val v = chooseUnit("Up to 1 enemy hero adjacent to the target discards a card", victims, optional = true)
        if (v != null) discardIfAble(v)
        attack(t)
    }

    private fun ActionScope.fireball() {
        fun valid(id: String): Boolean {
            val p = g.posOf(id) ?: return false
            // "not adjacent to you or to your friendly unit": presence, immune units count (p.18).
            return inRange(id) && !isAdjacent(id) && g.unitsAdjacentTo(p).none { isFriendly(it) }
        }
        val t = attackTarget("Target a unit in range, not adjacent to you or to your friendly unit") { valid(it) }
            ?: return
        attack(t, valid = { valid(it) })
    }

    private fun ActionScope.sunburst() {
        val n = Spellbook.otherRemoved(g, cardId)
        // Designer (BGG 3589662): exactly at the maximum range; items add to the 0 printed Range and Attack.
        val reach = range + n
        val t = attackTarget("Target a unit at maximum range ($reach)") { distanceTo(it) == reach } ?: return
        attack(t, valid = { distanceTo(it) == reach }, bonus = { n })
    }

    private fun ActionScope.vampiricTouch() {
        val t = adjacentAttackTarget() ?: return
        if (!attack(t).connected) return
        mayRetrieve()
    }

    // ---- skills --------------------------------------------------------------------------------

    private fun ActionScope.createUndead() {
        val team = hero.team
        val me = pos
        // An empty friendly (minion) spawn point in range in a Battle Zone; any type of friendly minion
        // that may be respawned there (p.19 "Respawn"; Dodger FAQ on the same text: any type).
        val options = LinkedHashMap<Hex, List<MinionType>>()
        for (h in g.within(me, range)) {
            if (!g.isMinionSpawnPoint(h) || g.geo.spaces[h]?.spawn?.team != team) continue
            if (!g.isEmptySpawnPoint(h) || !g.isInBattleZone(h)) continue
            val lane = laneOf(h) ?: continue
            val types = MinionType.entries.filter { g.canRespawnMinion(team, it, lane) }
            if (types.isNotEmpty()) options[h] = types
        }
        if (options.isEmpty()) fizzle("no friendly minion can respawn in range")
        val h = chooseSpace("Respawn a friendly minion in an empty friendly spawn point in range", options.keys)!!
        val types = options.getValue(h)
        val type = if (types.size == 1) {
            types.single()
        } else {
            types[chooseOption("Which friendly minion respawns?", types.map { "${minionLabel(it)} minion" })!!]
        }
        g.respawnMinion(team, type, laneOf(h)!!, h)
    }

    private fun ActionScope.laneOf(h: Hex): String? {
        val zone = g.zoneOf(h) ?: return null
        return g.state.battleZones.entries.firstOrNull { it.value == zone }?.key
    }

    private fun minionLabel(t: MinionType): String = when (t) {
        MinionType.MELEE -> "Melee"
        MinionType.RANGED -> "Ranged"
        MinionType.HEAVY -> "Heavy"
    }

    private fun ActionScope.energyDrain() {
        // FAQ: the enemy hero must be targetable for the team to regain the Life counter.
        discardSpell(range, "a non-basic card") { !g.isBasic(it) }
        val team = hero.team
        val start = g.data.lifeCounters(g.geo.map.lanes.size, g.state.length, g.state.heroes.size)
        // "regains 1 spent Life counter": only a spent one, never above the starting count.
        if ((g.state.lifeCounters[team] ?: 0) < start) g.regainLife(team, 1, start)
    }

    /** "An enemy hero in range/radius discards [what], if able": the hero is mandatory, the discard is not. */
    private fun ActionScope.discardSpell(distance: Int, what: String, filter: (String) -> Boolean) {
        val victims = unitsWithin(distance).filter { isHero(it) && isEnemy(it) && canTarget(it) }
        if (victims.isEmpty()) fizzle("no enemy hero to target")
        val v = chooseUnit("An enemy hero discards $what, if able", victims)!!
        discardIfAble(v, what) { filter(it.id) }
    }

    private fun ActionScope.banishment() {
        val me = pos
        fun spaces(id: String): List<Hex> =
            g.within(me, radius).filter { g.geo.isSpace(it) && it != g.posOf(id) && g.isEmpty(it, id) }
        val objects = objectsWithin(1).filter { canTarget(it) && g.canPlace(it, heroId) && spaces(it).isNotEmpty() }
        if (objects.isEmpty()) fizzle("nothing adjacent can be placed")
        val t = target(chooseObject("Place a unit or a token adjacent to you", objects)!!)
        val h = chooseSpace("Place ${g.name(t)} into a space in radius", spaces(t))!!
        g.place(t, h, heroId)
    }

    private fun ActionScope.dimensionDoor() {
        val reach = radius + Spellbook.otherRemoved(g, cardId)
        val me = pos
        val spaces = g.within(me, reach).filter {
            g.distance(me, it) == reach && g.geo.isSpace(it) &&
                g.isEmpty(it, heroId)
        }
        if (spaces.isEmpty() || !g.canPlace(heroId, heroId)) fizzle("no empty space at maximum radius")
        val h = chooseSpace("Place yourself into a space at maximum radius ($reach)", spaces)!!
        g.place(heroId, h, heroId)
    }

    private fun ActionScope.findFamiliar() {
        val spaces = g.within(pos, radius).filter { g.geo.isSpace(it) && g.isEmpty(it) }
        if (spaces.isEmpty()) fizzle("no empty space in radius")
        val h = chooseSpace("Place a Familiar token in radius", spaces)!!
        g.placeToken(FAMILIAR, heroId, h)
        // "You may remove up to three other spell cards from the spellbook faceup": any spell in the
        // spellbook (designer, BGG 3475239), chosen one at a time.
        val owner = Spellbook.owner(g, cardId) ?: return
        repeat(3) { i ->
            val left = Spellbook.contents(owner).filter { it != cardId }
            if (left.isEmpty()) return
            val c =
                chooseCard(
                    "You may remove another spell card from the spellbook (${i + 1} of up to 3)",
                    left,
                    optional = true,
                )
                    ?: return
            Spellbook.setTag(g, owner, c, REMOVED)
            g.note("${g.cardName(c)} is removed from the spellbook faceup.")
        }
    }

    private fun ActionScope.suggestion() {
        // "If able": mandatory whenever an enemy hero in radius can move exactly 3 spaces in a straight
        // line (as RULINGS 47); you choose the hero and the line.
        val able = unitsWithin(radius).filter { id ->
            isHero(id) && isEnemy(id) && canTarget(id) && g.canDisplace(id, Displacement.MOVE, heroId) &&
                canMoveOther(id, 3, 3, straightLine = true)
        }
        if (able.isEmpty()) {
            val any = unitsWithin(radius).any { isHero(it) && isEnemy(it) }
            nothing(
                if (any) {
                    "no enemy hero in radius $radius can move 3 spaces in a straight line"
                } else {
                    "no enemy hero in radius $radius"
                },
            )
            return
        }
        val t = chooseUnit("An enemy hero in radius moves 3 spaces in a straight line", able)!!
        moveOther(t, 3, 3, straightLine = true)
    }

    private fun ActionScope.dominatePerson() {
        val heroes = unitsWithin(radius).filter { isHero(it) && isEnemy(it) && canTarget(it) }
        if (heroes.isEmpty()) fizzle("no enemy hero in radius")
        val t = target(chooseUnit("Target an enemy hero in radius", heroes)!!)
        val tAt = g.posOf(t)!!
        val minions = unitsWithin(radius).filter { id ->
            isMinion(id) && isEnemy(id) && canTarget(id) && g.distance(g.posOf(id)!!, tAt) == 1
        }
        if (minions.isEmpty()) fizzle("no enemy minion in radius adjacent to the target")
        val m = target(chooseUnit("Defeat an enemy minion in radius adjacent to the target", minions)!!)
        g.defeatMinion(m, heroId)
    }

    private fun ActionScope.powerWordKill() {
        // Checking a hero's hand is checking its status: it targets (goa2-faq common).
        val victims = unitsWithin(radius).filter { id ->
            isHero(id) && isEnemy(id) && canTarget(id) && g.handSize(g.heroBodyOf(id)!!) == 0
        }
        if (victims.isEmpty()) fizzle("no enemy hero in radius with no cards in hand")
        val v = target(chooseUnit("Defeat an enemy hero in radius with no cards in hand", victims)!!)
        g.defeatHero(g.heroBodyOf(v)!!, heroId)
    }

    private fun ActionScope.midasTouch() {
        val coins = Spellbook.otherRemoved(g, cardId) / 2
        if (coins == 0) {
            nothing("fewer than two other spell cards are removed from the spellbook")
            return
        }
        g.gainCoins(heroId, coins, "Midas' Touch")
    }

    private fun ActionScope.disintegrate() {
        val options = objectsWithin(1).filter { id ->
            canTarget(id) &&
                (g.isToken(id) || (isMinion(id) && isEnemy(id) && g.state.minion(id)?.type != MinionType.HEAVY))
        }
        if (options.isEmpty()) fizzle("no token or enemy non-heavy minion adjacent")
        val t = target(chooseObject("Remove a token or an enemy non-heavy minion adjacent to you", options)!!)
        if (g.isToken(t)) g.removePiece(t) else g.removeMinion(t)
    }

    private fun ActionScope.polymorph() {
        fun partners(h: String): List<String> =
            (tokensWithin(radius) + unitsWithin(radius).filter { isMinion(it) && isEnemy(it) })
                .filter { canTarget(it) && g.canSwap(h, it, heroId) }
        val heroes = unitsWithin(radius).filter {
            isHero(it) && isEnemy(it) && canTarget(it) &&
                partners(it).isNotEmpty()
        }
        if (heroes.isEmpty()) fizzle("nothing to swap")
        val h = target(chooseUnit("Swap an enemy hero in radius", heroes)!!)
        val o = target(chooseObject("… with a token in radius or with an enemy minion in radius", partners(h))!!)
        g.swap(h, o, heroId)
    }

    // ---- Shield / Invulnerability -----------------------------------------------------------------

    /** "You are immune to basic attacks" ([basic]) / "to non-basic attacks": attacks and their effects (p.18). */
    private class AttackImmunity(private val basic: Boolean) : Behavior {
        override fun immune(g: Game, src: Src, q: AffectQuery): Boolean {
            val owner = src.owner ?: return false
            return g.heroBodyOf(q.targetId) == owner && q.kind == ActionKind.ATTACK && q.basic == basic
        }
    }
}
