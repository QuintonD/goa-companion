# Rulings: Trinkets the Scavenger

Format as [RULINGS.md](../RULINGS.md): card — question — reading — reason — source. **[APP]** marks a
reading the card text and FAQ leave open. Code: `heroes/TrinketsHero.kt`; tests: `heroes/TrinketsTest.kt`.

1. **The Turret (FAQ "What is the Turret?").** "It's an obstacle and counts as terrain. It's not a unit,
   minion, or hero, and it doesn't do anything other than as instructed by Trinkets' cards. The Turret's
   presence and counting as terrain are properties, not an active effect, so they don't change if
   Trinkets' cards change zones, between rounds, or if Trinkets dies. You're limited to the single
   provided Turret, so placing a Turret while it's already on the map will reposition it." Ramshackle
   Turret: "(The Turret is not a token and remains in play between rounds.)" So: a piece that is not a
   token (Clear does not remove it, tokens' end-of-round removal does not touch it), of no team, never a
   unit or token for other cards ("a unit or a token"), but an obstacle (Grappling Bolt, Brynn's count)
   and terrain. Placing it needs an empty space (p.18 "Place"), so its own current space is not offered.
   Source: Trinkets FAQ. A hero performing one of her cards (NebKher's Mind Grip) uses and places *the*
   Turret: designer, BGG 3485218 ("No he places *the* turret"; "all Trinket's card texts refer to *the*
   turret, not *a* turret. Nothing in the text prevents Neb from using it, as long as the turret is on the
   board"). (Rules audit: before, the Turret was looked up by the performing hero, so NebKher made a second
   one.) `TrinketsTest` "The Turret - …", "Rapid Redeployment - FAQ - a single Turret …", "The Turret -
   designer - NebKher performing Salvage Parts uses the one Turret".
2. **All Turret cards — "in range / in radius of the Turret".** Measured from the Turret's space with the
   card's own Range or Radius value (items and the Ultimate included), independently of the range from
   Trinkets (FAQ: "measuring the range of both of these independently"). Without the Turret on the board
   such a condition is never met: the attack cards and Self-Destruct have no Primary action then.
   Source: Trinkets FAQ. `TrinketsTest` "Makeshift Minigun - …".
3. **All Turret cards — who acts (FAQ "How do Trinkets cards target?").** Trinkets performs the action
   and targets; the Turret only restricts the targets. So effects on Trinkets apply as usual (Arien's
   Spell Break stops her non-gold skills even if the Turret is far; Min's Smoke Bomb between Trinkets and
   the target blocks her attack whatever the Turret's position — a rule of Min's card, not yet playable).
   Source: Trinkets FAQ. `TrinketsTest` "FAQ - Trinkets is the one acting - …".
4. **Rapid Redeployment / Ramshackle Turret / Salvage Parts.** "Move up to 3 spaces" is card-text movement
   (no Fast Travel, p.11) and may be 0. Placing the Turret is mandatory; with no empty space next to her
   the action stops there (p.10). Salvage Parts' "Remove the Turret; …" bullets are offered only with the
   Turret on the board (RULINGS 38). `TrinketsTest` "Salvage Parts - …".
5. **Makeshift Minigun / Gatling Gun / Supercharged Cannon — the bonus.** "+2 (+3) Attack" when the target
   is in a straight line (p.19) from Trinkets and in a straight line from the Turret, read as the attack
   total is made. `TrinketsTest` "Makeshift Minigun - not in a straight line from the Turret, no bonus".
6. **Steam Discharge / Flame Belcher — the repeats.** "On a different enemy unit": every unit targeted
   before is excluded; each repeat again needs a unit in range and adjacent to the Turret.
   `TrinketsTest` "Steam Discharge - …", "Flame Belcher - …".
7. **Early Prototype / Updated Design / Perfected Design — "If you are in radius of the Turret".** A
   condition: when it fails (or there is no Turret) the text does nothing; the action is still the
   Primary action (as Xargatha's Fresh Converts). The swap is with any unit or token (either team) in
   radius of the Turret that she may target and swap; never the Turret itself (not a unit or token).
   Early Prototype removes the Turret only after a swap was made (p.10: "then" follows a mandatory step).
   Perfected Design's placement is into a space in radius of the Turret that is empty for Trinkets (a space
   that counts as an obstacle for enemy units, Takahide's Spinning Blade, is not offered; rules audit). `TrinketsTest`
   "Early Prototype - …", "Perfected Design - …".
8. **Disruptor Jolt / Pulse / Grid — "Before any enemy hero in radius of the Turret performs a primary
   action".** **[APP]** Before each primary action an enemy hero performs while this effect is in force:
   a repeat is a separate action (p.10 "Treat each instance of repeat as a separate action", as RULINGS
   39), and a primary action performed through another card's text counts too, and so does a **primary
   Defense action** (defending with a card whose Primary action is Defense, or with a Defense/Skill card):
   the hero discards after revealing and discarding its defense card, before that card's text (p.14 step 4).
   *Reason:* the rulebook calls it a primary action — p.14 "since Defense is the Primary action of
   'Vampiric Shield', the card text is applied", quick reference "Defense Action … Primary: Same as above,
   and, in addition, apply the card text", "Defense/Skill … perform either Defense or a Skill as a primary
   action". The other reading (no Defense action, p.14 "A Defense action is different from all other
   actions") was the engine's before the rules audit; that sentence introduces specific differences (not on
   a played card, not counted in the one card per turn) and does not say a Defense action is not a
   primary action. No designer ruling found (BGG searched 2026-10-24; a strategy writer, Cryomancy, BGG
   3611965, reads it as here: "primary defense actions … also count as primary actions for the purpose of
   triggering this blue"). A secondary Defense action is not a primary action. The radius
   is checked from where the hero stands then. The hero is affected unless immune to Trinkets' skill
   effects (p.18). "If they discard a card, deactivate this effect": the effect ends after a discard;
   a hero with no card discards nothing (Jolt, Pulse) or is defeated (Grid), and the effect stays.
   Secondary actions are not primary actions. `TrinketsTest` "Disruptor Jolt - …", "Disruptor Grid - …".
9. **Self-Destruct / Emergency Protocol.** "Up to two enemy heroes in radius of the Turret" (0, 1 or 2,
   chosen by Trinkets) discard, then "Remove the Turret." With no Turret there is nothing to do: no
   Primary action. `TrinketsTest` "Self-Destruct - …", "Emergency Protocol - …".
10. **Deployable Barrier / Bastion — placing.** Up to 2 (3) Barrier tokens on empty spaces in Trinkets'
    radius; at least one of them adjacent to the Turret — the engine asks for that one first (any valid
    set can be placed in that order). Without the Turret no Barrier can be placed. **[APP]** The number of
    Barrier tokens in the box is not in our sources: the supply is not limited (`TokenKind.supply = null`).
    `TrinketsTest` "Deployable Barrier - …".
11. **Deployable Barrier / Bastion — "+1 Defense for each Barrier token they are adjacent to".** A property
    of each Barrier token while it is on the board (p.19: a token "retains all its properties until that
    token is removed"; the text has no Active-effect keyword), for Trinkets and her friendly heroes
    (Trinkets' team); added to the Defense total like a minion modifier (`defenseBonus`, so Arien's
    "ignore minion defense modifiers" does not remove it). Barriers are tokens: removed at the end of the
    round, by Clear, and kept if Trinkets is defeated. `TrinketsTest` "Deployable Barrier - friendly
    heroes …".
12. **Unlimited Firepower (Ultimate) — "Gain +1 Range and +1 Radius".** Added to the Range and Radius icons
    of her cards, like an item (p.16: a card without the icon gains nothing); so "in range of the Turret"
    grows too. `TrinketsTest` "Unlimited Firepower - …".

## Cross-check with goa2-backend (`trinkets_effects.py`, `docs/heroes/trinkets_effect_reference.md`)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Disruptor timing | Checked by `ResolveCardStep` before the primary action of a resolved card | Before every primary action, repeats, primaries performed by other cards and primary Defense actions included (ruling 8) |
| Barrier supply | 3 tokens | Not limited: the count is not in the pinned sources |
| Turret | Unique non-unit, non-token board entity; repositioned when placed again | Same |
