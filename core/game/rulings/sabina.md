# Rulings: Sabina the Commander

Format as in [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** = the
card text and FAQ are silent and the engine takes the reading closest to the text. Code:
`heroes/SabinaHero.kt`; tests: `heroes/SabinaTest.kt`.

1. **Point Blank Shot — Range 1.** A ranged attack that reaches only adjacent units unless items or
   effects widen it (goa2-faq common "Why is there a range 1 attack?"). It is ranged (RULINGS 19), so
   "Block a ranged attack" blocks it. "If the target is adjacent to you" is read after the attack; a
   defeated target is gone and is not pushed.
2. **Bang (Handicap).** "Push the target 1 space" after the attack whenever the target is still on the
   board (it defended).
3. **Quickdraw / Gunslinger / Dead Shot.** The bonus counts a hero who played an attack card this turn,
   whether or not it resolved, which action it performed, and even if the card is gone (FAQ "Does
   Sabina gain the attack bonus against Heroes who have played but not resolved an attack card?";
   RULINGS 36). Minions never played a card. Test: "Quickdraw - FAQ".
4. **Shootout / Bullet Hell.** "If the target was adjacent to you" = adjacent when it was attacked.
   No After the attack text when the attack did not connect (RULINGS 18). The minions removed must be
   targetable (heavy minion immunity applies); removing gives no coins (reminder text) but may push the
   Lane (p.8), so Bullet Hell removes them one at a time.
5. **Troop Movement / Marching Orders / Path to Victory.** "To a space in radius": the end space is
   1..Radius from Sabina. A repeat may move the same minion or another.
6. **Close Support / Covering Fire.** "Adjacent to your friendly minion" is the minion's presence
   (p.18); the hero must be targetable. With no such hero the first instruction cannot be done and the
   Primary action is not offered (FAQ common "Can I play an action if only some of its instructions
   can be performed?").
7. **Listen Up / Roger Roger / Ready and Waiting.** Any two minions in radius, friendly or enemy; both
   are targets (heavy minion immunity applies except on Ready and Waiting), and "cannot be swapped"
   effects apply (RULINGS 17).
8. **Steady Advance / Unwavering Resolve.** "Two or more friendly minions in radius" is presence, so
   immune minions count (goa2-faq common "Would an immune hero trigger effects, that require
   adjacency?": "cards such as Sabina's Steady Advance … simply check the presence of units"). The move
   happens only after a card was retrieved ("if you do").
9. **Big Sodding Gun (Ultimate).** "Your basic attack" = the Attack and Range icons of Sabina's basic
   Attack card (Point Blank Shot, or Bang; Bang has no Range icon, so +2 Range does nothing there). Red
   cards are not basic. **[APP]** "If you push an enemy hero" applies to every push Sabina makes, and a
   push of 0 spaces (blocked at once) still counts ("this can result in pushing it 0 spaces", p.18); a
   push that an effect forbids (Bulwark) is not a push. The hero chooses its card; with none it is
   defeated, credited to Sabina.

## Disagreements with goa2-backend (cross-check only)

None found on these readings (Big Sodding Gun: same stat bonus on basic Attack cards and a trigger
after every push).
