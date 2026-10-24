# Ursafar the Savage — rulings

Sources as in [RULINGS.md](../RULINGS.md): card text (`catalog/heroes/ursafar.json`), the pinned rulebook
(printed pages), the hero's FAQ and goa2-faq `common.json`. **[APP]** marks a reading the text and FAQ
leave open. Code: `heroes/UrsafarHero.kt`; tests: `heroes/UrsafarTest.kt`.

1. **"This round: You are enraged"** is an Active effect of the card that says it (p.17 "This Round"):
   Ursafar is enraged while at least one such effect is in force. Like every Active effect it ends at the
   end of the round (Retrieve), when Ursafar is defeated, and when that card changes state (p.17), and
   starting it again on the same card does not duplicate it (p.10). Green cards never enrage. Tests
   "Enraged - …".
2. **[APP] A card's own "This round: You are enraged" comes after the rest of its text**, its movement
   included (Prowling Brute family, Cold Ire family), so a card never satisfies its own "If enraged".
   *Reason:* p.10 "applied in the exact order it is written" — the "This round" clause is printed last; on
   the Movement cards, applying it "before movement" (p.11 default) would make their own "If enraged"
   always true, which the text would then not need. goa2-backend reads it the same way.
3. **Claws That Catch / Jaws That Bite:** "Before the attack: If enraged, you may move 1 space to a space
   adjacent to an enemy hero" — optional, a card-text move; the end space must be adjacent to an enemy hero
   (presence, p.18); then any adjacent unit (a hero for Jaws) is the target. Like RULINGS 4, the possible
   move makes the Primary action available even with no target yet.
4. **Angry Roar:** "one of your active cards with an active effect" = a card of Ursafar's with an Active
   effect in force or pending (turned sideways, p.7); with Unbound Fury every faceup resolved card counts
   too (its "all your resolved cards count as active" would otherwise change nothing — only Angry Roar
   reads "active cards"). Only cards whose Primary action can be performed are offered (FAQ "Can I play an
   action if only some of its instructions can be performed?"); enraged with none, Angry Roar's Primary
   action cannot be chosen (its first step is mandatory). The card performed stays where it is and its
   text applies in full (its own "This round" effect is started again, p.10).
5. **Prey Drive family:** "the target was not removed" = the target is still on the board after the
   attack (a hero who defended; a minion Brogan's Shield kept). "Remove" gives no coins (hero FAQ). The
   minions are chosen together, each targetable when chosen (heavy minion immunity; Whisper's FAQ on
   choosing minions together), so the attack target itself may be among them if it is still there.
6. **[APP] Tear:** "If enraged, gain 2 coins; if you defeated a hero, that hero spends 1 additional Life
   counter" — both clauses are under "If enraged" (one sentence). The extra counter is spent by that hero's
   team after the defeat's own Life counters (and can end the game, p.13).
7. **Sniff Out family:** Ursafar picks an enemy hero in range (p.10 example). Not enraged: nothing.
   Enraged with no targetable enemy hero in range: the step can't be done, for Sniff Out and Eyes on
   the Prey too ("if able" covers only the discard: goa2-faq, Energy Drain; RULINGS 51), so the
   Primary action is not offered (rules audit 2026-10-24; before, these two did nothing instead).
8. **Instinctive Reaction / Evolutionary Response:** "Perform the primary action on one of your discarded
   cards": a faceup discarded card whose Primary action can be performed; it stays discarded (an Active
   effect it starts lives on the discarded card). "Choose one, or both": chosen up front, either order
   (p.10), so retrieving the card just performed is possible. A bullet that cannot start is not offered
   (RULINGS 38).
9. **Prowling Brute family:** the movement comes first; then, if he was enraged, an optional swap with an
   adjacent unit or token (targets: immunity applies; swap limits such as Bulwark apply); Rampaging Beast /
   Unstoppable Force: "if you do, move up to 1 (2) additional spaces" is a card-text move after the swap
   (Movement-action limits do not apply to it).
10. **Cold Ire / Eyes of Flame:** +1/+2 to this card's Movement action only (hero FAQ: "It does not apply to
    any other cards"). Tests "Cold Ire - …".
11. **Unbound Fury:** always enraged while Ursafar is on the board (p.16: an Ultimate is in effect "as long
    as your hero is on the board"); see 4 for "resolved cards count as active".

## Disagreements with goa2-backend (cross-check only)

| Topic | goa2-backend | Engine |
|-------|--------------|--------|
| Sniff Out family: choosing the hero | Optional | Mandatory when there is one (p.10 example; "if able" is about the discard) |
| Unbound Fury "count as active" | A flag on every card (also seen by other heroes' filters and views) | Only Angry Roar reads "active cards"; the view still turns sideways only cards with an effect |
