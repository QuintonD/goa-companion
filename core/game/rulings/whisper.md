# Whisper the Outcast — rulings

Sources as in [RULINGS.md](../RULINGS.md): card text (`catalog/heroes/whisper.json`), the pinned rulebook
(printed pages), the hero's FAQ and goa2-faq `common.json`. **[APP]** marks a reading the text and FAQ
leave open. Code: `heroes/WhisperHero.kt`; tests: `heroes/WhisperTest.kt`.

1. **Swift Justice / Vile Accusation, first bullet.** "Target a hero in range with an empty discard" —
   the hero's discard pile is empty when targeted (and when the attack is checked again, RULINGS 18).
   "After the attack: If able, that hero performs a movement action on the card they defended with, moving
   full distance in a straight line": only when it defended and was not defeated; the full distance is the
   defense card's Movement value (Movement items included, p.16); the hero chooses the direction; with no
   Movement icon, or no straight line of exactly that length, nothing happens. *Source:* hero FAQ "When
   Whisper plays Gold (Swift Justice) against an enemy Hero, how do they move?" Tests "Swift Justice - FAQ …".
2. **[APP] That movement is the defender's own Movement action**: it acts (effects "while an enemy hero
   is performing an action" see it), effects that forbid movement actions stop it, and a limit below the
   full distance ("cannot move more than 1 space with a movement action") makes it impossible ("full
   distance", "If able"). Its "before/after an action" hooks fire. *Reason:* the text says it "performs a
   movement action"; "moving full distance" admits no shorter move.
3. **Bullets** (Swift Justice, Death Seeker): RULINGS 38 — a bullet that cannot start is not offered; both
   attacks of Swift Justice are ranged (RULINGS 19).
4. **Grim Reaper:** "When performing basic actions, you may choose one, or both" widens "Choose one" on his
   basic cards (gold, silver, Handicap) to one or both, chosen up front in either order (p.10 "Modal
   Actions"; one action). The two bullets of Swift Justice may target the same unit if it qualifies for
   both (the text does not say "different").
5. **Death Seeker:** "If an enemy hero in radius has a card in the discard" checks a hero's status, which
   counts as targeting (common FAQ "Would an immune hero trigger effects, that require adjacency?"), so an
   immune hero does not count. "This turn: You are immune to enemy actions": every action of an enemy hero
   and its Active effects (p.18), not his own team's, not the Minion Battle.
6. **Crimson Trail family:** the empty minion spawn points in radius in a Battle Zone (any Lane on the
   double-lane map, p.20; empty = no obstacle, so his own space and occupied points never count) are
   counted once, before the move; the move is optional, "up to" that many spaces (capped at 1/2/3), a
   card-text move. Like RULINGS 4, the possible move makes the Primary action available.
7. **Shadow Step / Shadow Walk:** an empty minion spawn point (heavy included, not hero spawn points) in
   range and in a Battle Zone. **[APP] Creeping Shadow** "or into a space in range adjacent to such a spawn
   point": the space must be in range and empty; the spawn point next to it must be an empty minion spawn
   point in a Battle Zone, but need not itself be in range ("in range" is restated for the space). goa2-backend
   reads it the same way; no designer ruling found (BGG searched 2026-10-24). The space Whisper is placed
   into must be empty for him (p.18 "Place"): a space that counts as an obstacle for enemy units (Takahide's
   Spinning Blade) is not offered (rules audit; before, choosing it crashed). Test "Shadow Step - a spawn
   point that counts as an obstacle for Whisper …".
8. **Seeds of Fear family:** "occupying a spawn point" — any spawn point, minion or hero (the text does not
   say "minion"); Seeds of Fear's move of exactly 1 is mandatory (a unit that cannot be moved 1 space is
   not a target), Sprouts / Blooming "up to 2 (3)" may be 0.
9. **Lesser Evil / Greater Good:** Whisper picks an enemy hero in range (FAQ: required, else the card
   cannot be played); that hero picks a bullet, both always offered ("Any option can be chosen, even if it
   would have no effect"). The minions are enemy minions adjacent to Whisper (friendly units can never be
   defeated, p.13); Greater Good's up to 3 are chosen together, so the heavy minion cannot be chosen with
   the last non-heavy one (FAQ). Coins are gained as usual (p.13).
10. **Blood Fury / Blood Frenzy:** after a connected attack, the condition is checked once; then "may
    repeat once" / "repeat up to five times on different targets": each repeat is optional and excludes
    every unit targeted so far (p.10).
11. **Cruel Twist / Sealed Fate:** the swap is mandatory, with any unit adjacent (friend or enemy) or an
    enemy hero in range with a non-empty discard; then a card-text move of up to 2 (3).

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Swift Justice second bullet | Flagged ranged | Ranged too (RULINGS 19: the card is ranged) — same |
| Grim Reaper | First option chosen normally, then the other offered as optional | Both chosen up front, either order (p.10); same results |
| Blood Frenzy condition | Checked once (its design note says each time) | Once, after the first attack |
