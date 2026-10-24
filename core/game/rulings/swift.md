# Swift the Sharpshooter — rulings

Readings of Swift's cards (`catalog/heroes/swift.json`) beyond the plain text, in the format of
[RULINGS.md](../RULINGS.md). Code: `heroes/SwiftHero.kt`; tests: `heroes/SwiftTest.kt`.

1. **[APP] Reload! / Backup and Bullet Time: "your rightmost resolved card".** When Bullet Time
   performs an already resolved Reload! (or Backup) again, Reload! itself is the rightmost resolved
   card; performing itself would never end. The engine skips the card that is performing: the
   rightmost resolved card *other than this one* (the card before it). Reason: Bullet Time's own
   clause "you cannot target the same enemy hero twice in the same turn this way" only has a use
   when the repeated Reload! performs that earlier card again (Swift's basic cards target no enemy
   hero by themselves). goa2-backend says the designer ruled the same, from the first printing's
   "card in the previous turn slot" wording (not verified: no source given). Test "Bullet Time - Reload
   again repeats the card before it".
2. **"Perform the primary action of your rightmost resolved card"** needs a faceup card (p.10: a
   facedown card "loses its type, color and actions"), a Primary action that is not Defense, and its
   first instruction possible (FAQ "Can I play an action if only some of its instructions can be
   performed?"). Otherwise Reload!'s first bullet is not offered (RULINGS 38) and Backup cannot be
   performed. Backup's "you cannot target minions" is part of that check (an attack card whose only
   targets are minions cannot be performed through Backup). Tests "Reload - a facedown …", "Backup - …".
3. **Shotgun / Super-Shotgun, "Before the attack: An enemy hero adjacent to the target discards a
   card, if able / , or is defeated".** The enemy hero is a target that must be found for both cards;
   "if able" makes only Shotgun's discard optional. With no targetable enemy hero next to the target
   the action stops before the attack (p.10). Sources: goa2-faq (Gydion, Energy Drain, the same
   sentence shape): "you need to be able to target a hero first. 'If able' after a comma makes the
   preceding step non-mandatory (p. 17), so it only applies to the 'discard' step"; p.10's example of
   this sentence ("You choose one enemy hero in radius to target, then that hero chooses a card to
   discard, if they have one"); and the card texts themselves — Gydion's Burning Hands says "Before
   the attack: **Up to 1** enemy hero adjacent to the target discards a card, if able", which would
   not need "Up to 1" if "if able" already made the hero optional. **Other reading (the hero batch's
   first version, changed by the rules audit 2026-10-24):** p.17 "If a sentence ends with this
   keyword, the clause preceding the keyword is non-mandatory" read as the whole sentence, so Shotgun
   attacks with no enemy hero next to the target. The FAQ is ranked above the rulebook text for this
   project and matches the printed "Up to 1" wording elsewhere, so it is kept. goa2-backend makes the
   step optional for both cards. RULINGS 53. Tests "Shotgun - …", "Super-Shotgun - …".
4. **[APP] Bullet Time, "you cannot target the same enemy hero twice in the same turn this way".** The
   extra primary action may not target any enemy hero Swift's actions already targeted this turn
   (the resolved card's own action, and actions it performed, e.g. Reload! performing a red card).
   goa2-backend excludes only the last combat target. Test "Bullet Time - Reload again …".
5. **Bullet Time triggers after any basic card is resolved,** whatever action was performed on it
   (p.7 "Resolve Your Card"), not when it was resolved without an action because Swift was defeated
   (she is off the board: the Ultimate is in force only "as long as your hero is on the board", p.16).
   Declining is allowed ("you may").
6. **Snipe "at maximum range"** is exactly the card's Range value with items (p.16: items change the
   Range icon). Test "Snipe - maximum range counts Range items".
7. **Suppress / Pin Down / Killing Ground "not adjacent to terrain":** no neighbouring space is
   terrain (p.19: water spaces included; heroes that count as terrain count, e.g. Stone Gaze). Choosing
   the hero is the first instruction: with none, the action cannot be performed (FAQ above).
8. **Mark for Death / Hunting Season:** the minion move is optional ("up to", p.10), so the Next turn
   effect always starts. It counts every enemy minion defeated in radius by anyone, measured from
   Swift's space at the moment of the defeat (p.17), the first one (two) times only. Minion Battle
   removals are not defeats. Engine limit: a minion "defeated but not removed" by Brogan's Shield does
   not fire the defeat trigger, so it gives no coin here.
9. **Steam Jump family:** "Push … up to N spaces" is optional (p.10 "up to"); the distance is chosen
   once for both units of Drop Trooper (p.18 "Push"). The placement is the first instruction
   (mandatory).
10. **Delayed Jump / Mobile Scout:** the End of turn placement is mandatory; with no empty space in
    radius off Swift's straight lines (or when she cannot be placed) the text stops and there is no
    Fast Travel. The Fast Travel follows the normal Fast Travel rules (p.11) and may be declined.
