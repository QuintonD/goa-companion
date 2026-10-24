# Rulings: Snorri the Runescribe

Readings of Snorri's cards (`heroes/SnorriHero.kt`, tests in `heroes/SnorriTest.kt`). Sources as in
[RULINGS.md](../RULINGS.md): card text, the 2nd-printing rulebook, the hero's FAQ (`snorri.json`
`faq`), goa2-faq `common.json`. **[APP]** marks a reading where text and FAQ are silent.

1. **Inscribe the Runes — placing the runes.** Q: who places which rune below which slot? Reading:
   Snorri chooses, slot 1 to 4 (four, three, two choices; the last rune is forced). The runes are
   persistent markers (`marker_rune_axe|bird|anvil|horn`, `Marker.data["slot"]`) given by Snorri to
   himself; playing the card again moves them (one marker of a kind per giver). Reason: p.10 "You make
   all the choices in your card's text"; card text "not removed at the end of round, nor if you are
   defeated". Tests: `Inscribe the Runes - …` (three).
2. **"A rune is active"** = the rune whose marker is below the Turn slot numbered like the current
   turn (`state.turn`), whatever happened to the cards on those slots. Before the silver is first
   played no rune is active. Source: card text. Test: every rune-dependent test sets the turn.
3. **[APP] Rune Mastery — when the second rune is chosen.** "Each time you perform an action, choose
   one inactive rune": the choice is asked once per action (a repeat is a separate action, p.10 "Treat
   each instance of repeat as a separate action"; a Defense action is an action) and only when that
   action's text reads the runes — for a Hold, Fast Travel or a card with no rune text the choice has
   no effect and is not asked. Reason: the outcome is the same; asking a pointless question every
   action would only slow play. The probe that decides whether a Primary action is possible (RULINGS
   4) counts every inactive rune as a candidate instead of asking. Tests: `Rune Sigils - Ultimate …`,
   `Oath of Perseverance - FAQ …`, `Runebomb - FAQ …`.
4. **"If a rune is active:" with two active runes (Rune Mastery)** applies the line of every active
   rune (Runetrap: [APP] the same enemy hero discards both colours; Oath of Endurance / Fortitude: the
   Block succeeds if any active rune's condition holds). "Choose one active rune" (Runebomb, Oath of
   Perseverance) asks which, and only when two are active. Source: FAQ "How does 'choose' work here?"
   ("The language is different from Runetrap and Oath of Fortitude to accommodate Snorri's Ultimate
   … the player has to choose one of them. If the Ultimate is not active, there is no choice.").
5. **Rune Sigils / Rune Shards {rune_horn} "Repeat once on a different hero in range".** The repeat
   is optional (p.10 "you may perform the entire action again"); the repeated attack targets a hero in
   range other than every unit targeted so far (not only adjacent), and the rest of the text applies
   again ({rune_axe} +3, {rune_anvil} coins). It is offered only when such a hero exists. Test:
   `Rune Sigils - horn …`, `Rune Shards - horn …`.
6. **Rune Sigils {rune_anvil} "If you target a hero, gain 3 coins"** is gained when the hero is
   targeted, whatever the attack's outcome; Rune Shards' "Gain 2 coins" always. Source: card text
   (no "after the attack"). Test: `Rune Sigils - anvil …`, `Rune Shards - …`.
7. **Rune Sigils {rune_bird} "You may target a minion in range instead"**: the targets are the
   adjacent units plus the enemy minions in range (Range 2 with items). Test: `Rune Sigils - bird …`.
8. **Runic Hammer / Battleaxe "Before the attack: If the {rune_horn} rune is active, you may move 1
   space. Target a unit adjacent to you."** The move comes first (printed order), so a unit 2 spaces
   away can be reached; without the horn rune and no adjacent target the Primary action is not
   offered. Test: `Runic Hammer - …`.
9. **Runic Battleaxe {rune_axe} "May repeat once on an enemy minion"**: optional; the repeat may
   target enemy minions only. [APP] It is offered when an enemy minion is within 2 spaces (1, or 2 with
   a horn move in the repeat). Test: `Runic Battleaxe - axe …`.
10. **Runecaster / Runeblaster "at maximum range"** = exactly the card's Range value (items
    included, p.16). {rune_axe} "An enemy hero who was adjacent to the target": the enemy heroes
    adjacent to the target when it is chosen (before the attack), still on the board after it;
    Snorri picks one if several; mandatory. Tests: `Runecaster - …`, `Runeblaster - …`.
11. **After-the-attack rune lines are skipped when the attack does not connect** (RULINGS 18).
12. **Oaths — "This Turn: You are immune to enemy actions"** starts whatever the Block result; if
    the Block fails, Snorri is defeated and his Active effects are cancelled (p.13), so the immunity
    only matters after a successful Block. "Enemy actions" = actions performed by an enemy hero.
    Basic attack = an attack with a basic card (p.9); non-ranged/ranged = the attack's
    `ranged` flag (RULINGS 19). No active rune with a matching line: the Block fails (p.14). Tests:
    `Oath of … - …` (six).
13. **[APP] Passages "You may ignore obstacles" (This turn)** applies to every move Snorri makes
    himself this turn (his Movement action included), not when another hero moves or pushes him
    (same reading as RULINGS 45). The text applies before the movement (p.11). Tests: `Safe Passage`,
    `Hidden Passage`, `Deep Passage`.
14. **Runetrap / Runebomb**: Snorri chooses an enemy hero in radius (mandatory; immune heroes can't
    be chosen); it discards a card of the colour, if able. With no rune that has a line active, the
    text does nothing and the Primary action is not offered. Tests: `Runetrap - …`, `Runebomb - …`.
15. **Ancestral Boon / Grace "a friendly hero in radius may"**: Snorri chooses the friendly hero; that
    hero decides each "may" and picks the cards. {rune_axe}: a resolved card and a hand card swap
    states (p.10 "Swap a Card"; their Active effects are cancelled). {rune_anvil}: all discarded cards
    return to hand at once. **[APP] {rune_bird} "Swap one of their items with an item on their card of
    the same tier and color"**: the item card and the hero's other card of that colour and Tier swap
    states, wherever that card is (hand, deck, discard, Turn slot): the other card becomes the item.
    Reason: the two cards of a colour and Tier are the level-up pair (p.15); "an item on their card" is
    the item icon printed on that card. Tests: `Ancestral Boon - …`, `Ancestral Grace - …`.
16. **[APP] Whose runes a card reads.** The runes are those of the hero who owns the card (Snorri),
    also when another hero performs it (NebKher's Mind Grip); Rune Mastery applies only to Snorri's own
    actions. Reason: the runes are markers below Snorri's Turn slots and the text says "a rune is
    active", not "your rune"; the Mind Grip FAQ has NebKher use another hero's tokens "as those heroes
    would". NebKher copying Inscribe the Runes gives no markers ("skip giving markers").

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend (`snorri_effects.py`) | Engine |
|-------|-------------------------------------|--------|
| Rune Mastery prompt | Asked before every action and every defense | Asked only when the action's text reads runes (ruling 3) |
| Runetrap / Runebomb hero | Choosing the enemy hero is optional | Mandatory when one is in radius (no "up to"/"may") |
| Repeats and Rune Mastery | The repeat reuses the first instance's active runes | A repeat is a separate action (p.10), so Rune Mastery's second rune is chosen again |
