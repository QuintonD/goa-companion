# Rulings: Gydion the Archwizard

Readings of the engine for Gydion's cards and his 22 spell cards (format and sources as
[RULINGS.md](../RULINGS.md); card text beats the rulebook, p.3). Code: `heroes/GydionHero.kt`,
`heroes/gydion/Spellbook.kt`, `heroes/gydion/GydionSpells.kt`; tests: `heroes/GydionTest.kt`.
"Designer" = Arty (BGG user 202424), whose answers on BoardGameGeek are the only rulings on Gydion
besides the goa2-faq; the threads were read through `api.geekdo.com` on 2026-10-24.

## The spellbook

1. **Where the spells start.** *Prepare Spells: "Add all spell cards to the spellbook."* Question: are
   the spells in the spellbook at the start of the game? Reading: no. The 22 spell cards start outside
   it (in `Zone.EXTRA`, tag `box`), so no spell can be cast until Prepare Spells has been performed.
   Reason: nothing puts them there; "you cannot access the spellbook (cast a spell), because cast is
   undefined until silver is played". Source: designer, BGG 3491113 ("If it doesn't say that spells
   start in the spellbook, they don't"). Test: `Prepare Spells - the spellbook starts empty…`.
2. **"Add all spell cards".** Every spell card is added, those of Tiers Gydion has not reached and Wish
   included; removed ones come back. Source: designer, BGG 3491113 ("Silver adds **all** spells to your
   spell book. You simply cannot cast them until you get your higher tiered cards"). Test: `Prepare
   Spells - Add all spell cards…`.
3. **[APP] "Only Gydion can look at the cards in the spellbook."** The engine keeps no hidden
   information here: the spellbook always holds every spell card except the ones removed faceup (public),
   so its contents follow from open information. Its counts are public dashboard counters
   (`HeroView.counters`: `spellbook`, `spellsRemoved`, `wishCasts`). The views still follow the card's
   words (ADR-040): `HeroView.extras` names the spells in the spellbook to Gydion's own seat only (not to
   a teammate, p.4 "Hidden Information"); the removed spells (faceup) and the ones never added (the
   spell cards are public components, all of them listed in the box at the start) to every seat. Test:
   `ExtrasViewTest`.
4. **Casting.** *"As each spell is cast, remove it from the spellbook faceup. The action on the spell
   card is performed by the hero casting the spell."* The spell is removed first, then the caster
   performs the spell card's action as an action of its own type (Attack, Skill or Movement): its own
   Before/After action triggers, immunity to that action type (p.18), action restrictions (e.g. Spell
   Break forbids a coloured Skill spell, Stone Gaze a Movement spell), and the gold spells (Magic
   Missile, Shocking Grasp, Expeditious Retreat) are basic (p.9: basic cards are the gold and silver
   cards). Values are the spell card's icons with the caster's items and modifiers (p.16). The card that
   cast it keeps its own action around it (its After-action triggers come after the spell's).
   `Spellbook.cast`.
5. **[APP] A spell is cast only when its action can start.** Question: may Gydion cast a spell whose
   action cannot do anything (Magic Missile with no target), just to remove it? Reading: no. Casting is
   performing the spell's action, and "if you cannot perform the first instruction … you may not perform
   the action at all" (FAQ, `faq-common-fizzle`). So a "Choose and cast one" bullet whose spell cannot
   start is not offered (RULINGS 38), and a card whose only spell cannot start has no Primary action.
   A spell whose first step is optional ("If able", Suggestion) can always be cast. `Spellbook.castable`;
   tests `Cantrip - Choose and cast one…`, `Elementary Evocation - with no unit adjacent…`, `Energy Drain
   - FAQ…`. Disagreement: goa2-backend spends the spell, then offers the normal action menu.
6. **[APP] No Hold, Clear or Fast Travel instead of a spell.** "The action on the spell card" is its
   printed action; Hold, Clear and Fast Travel are actions chosen for the played card (p.7, p.9), which
   is the Gydion card, not the spell. Disagreement: goa2-backend offers Hold/Clear on attack spells and
   Fast Travel on Expeditious Retreat.
7. **"each other spell card removed from the spellbook"** (Sunburst, Dimension Door, Midas' Touch)
   counts the spells removed faceup right now (cast, or removed by Find Familiar), not the ones never
   added, never the spell itself. Removing is not a choice: "The spell cards are removed from the
   spellbook either when a spell is cast, or when a spell effect tell you you can do it" (Arty on the
   game's Discord, quoted on BGG 3456872). `Spellbook.otherRemoved`.
8. **A spell returned to the spellbook ends its Active effect** (Shield's reminder "Cancelled if the spell
   is returned to the spellbook"; p.17 a card changing state ends its effect), whoever cast it. Test:
   `Shield - Cancelled if the spell is returned…`.
9. **NebKher's Mind Grip on a Gydion card** casts from Gydion's spellbook; NebKher is the caster ("you"
   in the spell text) and the spell is removed from Gydion's spellbook. Source: designer, BGG 3487018
   ("Correct"; he sees the spell only once chosen — no hidden information in the engine, ruling 3).
   The scripts use the caster (`ActionScope.heroId`) and find the spellbook by the spell card's owner.
   Test: `NebkherTest` "Mind Grip - a copied Gydion card casts from Gydion's spellbook".

## Deck cards

10. **Cantrip / Cheap Trick / the Tier cards** list the spells they may cast; only those are offered
    (Cheap Trick: Expeditious Retreat only, even if Shocking Grasp has a target). Tests per card.
11. **The Archwizard (Ultimate)** *"Whenever you would add cards to the spellbook, you may cast the Wish
    spell in the spellbook instead."* Only Prepare Spells adds cards. Gydion is asked (Yes / Add the
    spells) when Wish is in the spellbook and can be cast; NebKher performing Prepare Spells is not
    "you". Wish is removed when cast like any spell, so the next Prepare Spells adds it back.
    Tests `The Archwizard - …`.

## Spells

12. **Wish** *"Cast any spell in the Spellbook. After you cast the Wish spell three times your team wins
    the game."* Any spell in the spellbook that can be cast (ruling 5). The casts are counted for the
    whole game (`wishCasts`); [APP] the third cast wins once its action has been performed (the text sets
    no moment; the inner spell cannot make the other team win first). `Spellbook.cast`; test `Wish - …`.
13. **Magic Missile:** in range (3 + Range items) and not adjacent. **Fireball:** in range, not adjacent to
    Gydion and not adjacent to any friendly unit of his (a presence check: immune units count, p.18).
14. **Sunburst** *"Target a unit at maximum range"*: exactly at Range 0 + Range items + the other removed
    spells, not closer; the same number is added to Attack. Source: designer, BGG 3589662 ("Yes (items can
    increase range/attack)"; "I must find an enemy 7 spaces away? — Yes.").
15. **Shocking Grasp** *"After the attack: Move the target up to 1 space."* Only after an attack that
    connected (RULINGS 18), and only while the target is still on the board and targetable; Gydion chooses
    the space and may leave it ("up to").
16. **Burning Hands** *"Before the attack: Up to 1 enemy hero adjacent to the target discards a card, if
    able."* Gydion may choose nobody; the discard happens even if the attack then fails to connect (p.17).
17. **Vampiric Touch:** "You may retrieve a discarded card" — the caster's own discard (p.10 "You can
    never retrieve another hero's cards"), after an attack that connected.
18. **Create Undead** *"Respawn a friendly minion in an empty friendly spawn point in range in the battle
    zone."* Any type of friendly minion on any empty friendly minion spawn point (Dodger's FAQ on the same
    text: "It need not be a matching minion type"); a type is offered when p.19 allows its respawn (more
    spawn points of that type and colour in that Battle Zone than such minions there). [APP] p.19's count
    stands for the FAQ's "originally in play in this wave".
19. **Energy Drain** FAQ: "you need to be able to target a hero first"; "if able" covers only the discard
    of a non-basic card. "regains 1 spent Life counter": only a spent one, so never above the team's
    starting count. Tests `Energy Drain - …` (three).
20. **Shield / Invulnerability:** the caster is immune to attack actions on basic (gold and silver, the
    gold spells included) / non-basic cards, and to their Active effects (p.18), this round, until the
    spell is returned to the spellbook or the caster is defeated.
21. **Banishment:** a unit or token adjacent to the caster, into any empty space within the radius (not
    its own space). **Dimension Door:** an empty space exactly at Radius 0 + Radius items + the other
    removed spells.
22. **Find Familiar** *"You may remove up to three other spell cards from the spellbook faceup."* Any spells
    in the spellbook, not only ones Gydion could cast (designer, BGG 3475239: "The only requirement it has
    is 'spell card in the spellbook'"), chosen one at a time, stop at any point. [APP] The Familiar token
    supply is not in our sources: treated as unlimited (`TokenKind(supply = null)`); it is a plain token
    (an obstacle, removed at the end of the round, p.19).
23. **Cloud Kill / Energy Drain / Power Word Kill / Dominate Person / Polymorph:** the enemy hero is a
    mandatory target (immune heroes cannot be chosen). Power Word Kill's "with no cards in hand" checks a
    status, so it targets (goa2-faq `faq-common-xNApYXXu`). Dominate Person: "Target an enemy hero" is its
    own step; a target with no enemy minion in radius adjacent to it stops the spell there (p.10), and the
    defeat is the caster's (coins). Polymorph is one instruction: only heroes with a token or enemy minion
    in radius they can swap with are offered.
24. **Suggestion** *"If able, an enemy hero in radius moves 3 spaces in a straight line."* Mandatory
    whenever some enemy hero in radius can move exactly 3 spaces in a straight line (as RULINGS 47); the
    caster chooses the hero and the line (p.10 "You make all the choices"). With nobody able nothing moves
    and the spell is still cast. Disagreement: goa2-backend lets the caster pick any enemy hero in radius
    and fizzles when that one cannot move.
25. **Midas' Touch:** 1 coin per two other removed spells, rounded down.

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`docs/superpowers/plans/2026-07-13-gydion-*.md`) | Engine |
|-------|------------------------------------------------------------------|--------|
| Spells cast whose action cannot start | Spent, then the normal action menu (Hold possible) | Not castable (ruling 5) |
| Hold / Clear / Fast Travel on a spell | Offered | Not offered (ruling 6) |
| Suggestion with an enemy that cannot move | Any enemy hero may be picked; the spell fizzles | Only enemies able to move are offered (ruling 24) |
| Spellbook identities | Secret to everyone but Gydion | Derivable from public information (ruling 3) |
| Spells beyond the first six, Wish, the Ultimate | Not implemented | Implemented |
