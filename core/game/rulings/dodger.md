# Rulings: Dodger the Warlock

Format as in [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** = the
card text and FAQ are silent and the engine takes the reading closest to the text. Code:
`heroes/DodgerHero.kt`; tests: `heroes/DodgerTest.kt` (and `rules/CombatTest` "p13 example").

1. **"Empty spawn point in the battle zone".** A minion spawn point printed on the board, in a current
   Battle Zone (either one on the double-lane map), in an empty space — nothing on it at all (FAQ "What
   is an empty minion spawn point in the battle zone?"; p.17 "An empty Spawn point is a Spawn point in
   an empty space"). Spawn points of both teams count. "In radius" = 1..Radius from Dodger. Tests:
   "Dread Razor - FAQ", "Dark Ritual - FAQ", "Vampiric Shield - FAQ".
2. **Dread Razor / Rusty Razor, Littlefinger / Finger / Middlefinger of Death.** A bullet whose first
   instruction cannot be done is not offered (RULINGS 38); both bullets are the card's ranged attack
   (RULINGS 19). "A hero in range who has one or more cards in the discard" checks that hero's discard
   pile when targeting and again as the attack is made.
3. **Middlefinger of Death — "Choose one, or both, on different targets".** Both bullets may be chosen
   in either order (p.10 "Modal Actions": "Unless the card text says otherwise you can choose the bullet
   points in any order"); the second target must differ from the first. "Both" is offered only when two
   different targets exist for the two bullets. If the second bullet has no target when it is reached,
   that step stops the action (p.10). A hero who discarded to defend the first attack now has a card in
   the discard, but it is the first target and is excluded.
4. **Burning Skull / Blazing Skull.** "Up to 1 (2) minion(s) adjacent to you": any minions, friendly or
   enemy, that can be targeted (heavy minion immunity applies); each moves 1 space to a space not
   adjacent to Dodger; chosen and moved one at a time. Minions moved out of the Battle Zone go back
   after the action (p.18).
5. **Dark / Darker / Darkest Ritual.** With fewer than two such spawn points the first instruction
   cannot be done and the Primary action is not offered (FAQ common "Can I play an action if only some
   of its instructions can be performed?"), Darkest Ritual included: its second sentence ("If you have
   your Ultimate, gain an Attack item") is reached only after the first. (With the Ultimate, Tide of
   Darkness makes every empty space count, so this almost never matters.) **[APP]** The item is a spare
   item from the box (reminder text); each play gains one more (the text has no limit). It is a
   permanent Attack item like a Tier card item (p.16) and is shown in the hero's item icons.
6. **Necromancy / Necromastery.** FAQ "What minions can Dodger respawn with her Necromancy?": a friendly
   minion of any type, onto any empty friendly minion spawn point (not a space next to one); it must be
   a minion that was in play in this wave and has been defeated or removed. **[APP]** "Was in play this
   wave" is counted per Lane and type as p.19 Respawn says: the Battle Zone has more friendly spawn
   points of that type than friendly minions of that type on the board (minions displaced out of the
   Battle Zone during the action still count). The respawned minion belongs to that Lane (p.20). With
   several missing types Dodger chooses which. Tests: "Necromancy - FAQ" (any type, full wave),
   "Necromastery".
7. **Shield of Decay / Vampiric Shield / Aegis of Doom.** Primary Defense cards, so their text applies
   when discarded to defend (p.14); the bonus is "card text" Defense. Rulebook p.14 example: Defense 4
   + 2 = 6 against Mad Dash's 6 defends. Test: "p14 example". The p.13 example position
   (`CombatTest`) continued with "This time Brogan chooses to attack Dodger": there the immune heavy
   minion north-west of Brogan also stands next to Dodger, so her total is 7.
8. **Weakness / Enfeeblement.** "Enemy heroes in radius" is read from Dodger's current space each time a
   value is read (p.17: the radius moves with her); heroes immune to her skill actions are not affected
   (p.18). "Cannot repeat actions" stops every "repeat" text (p.10) of those heroes while they are in
   radius (Arien's Violent Torrent, Tigerclaw's Cloak and Daggers…).
9. **Tide of Darkness (Ultimate).** "While you are performing an action, all spaces count as if they were
   in the battle zone and had a friendly minion spawn point": read by Dodger's own card texts (they run
   only during her actions — her Defense text included, p.14 calls it a Defense action), so every empty
   space counts as an empty (friendly) spawn point in the Battle Zone for Dread Razor, Death Trap, the
   Rituals, Necromancy and the Shields. **[APP]** It does not change which minions can be respawned
   (ruling 6: the wave is the real one), and other heroes' cards never see it. A minion respawned outside
   its Battle Zone goes back after the action (p.18). Tests: "Tide of Darkness".

## Disagreements with goa2-backend (cross-check only)

None found. goa2-backend picks the spawn point first and takes the minion from that Lane's minions that
are off the board ("lane-bound limbo supply"), which gives the same choices as ruling 6; its Tide of
Darkness likewise makes every space a friendly spawn point in the Battle Zone for Dodger's own actions,
her Defense text included, and "empty" is read from the point of view of Dodger performing the action
(another hero's "counts as obstacles" effect that applies to her action applies to her count too).
