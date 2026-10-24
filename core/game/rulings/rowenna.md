# Rowenna the Vanguard — rulings

Readings of Rowenna's cards (`catalog/heroes/rowenna.json`) beyond the plain text, in the format of
[RULINGS.md](../RULINGS.md). Code: `heroes/RowennaHero.kt`; tests: `heroes/RowennaTest.kt`.

1. **[APP] Code of Chivalry / Earn the Spurs, "both you and the target may retrieve a discarded
   card".** Rowenna chooses first, then the target (each picks its own card, p.10 "Retrieve": "You
   can never retrieve another hero's cards"; the target makes its own choice because the text says it
   may). Both happen before the attack, so the target may defend with the card it took back (FAQ:
   "she heals them first"). The order is not printed; discards are open information, so it changes
   little. Tests "Code of Chivalry - …".
2. **[APP] Throw the Gauntlet, "that hero may move 1 space".** The enemy hero decides and moves
   itself: it is the mover and, for that move, the acting hero (`Game.asActor`). So an effect against
   being moved by enemy heroes (Brogan's Bulwark) does not stop it. Rowenna gains 2 coins only if it
   really moves. The hero is chosen first ("an enemy hero in range", targetable), then a space in range
   next to it. Tests "Throw the Gauntlet - …".
3. **Token of Gratitude family, "A friendly hero in radius gains 1 coin".** Never Rowenna (common FAQ
   "Can my hero target themself"); the step is mandatory but it is the last one, except for Paragon of
   Grace: with no friendly hero in radius after the attack the action stops before "May repeat once".
   On the repeat "on a different target" every unit targeted so far is excluded (RULINGS 37), the
   friend who gained the first coin included, so a second coin goes to another friend or to no one.
   Test "Paragon of Grace - …". (Players may read the repeat as excluding only the attack target; the
   engine keeps ruling 37's literal reading for all heroes.)
4. **Feat of Bravery / Paragon of Valor:** "may retrieve" is optional for the friend, who chooses the
   card; only friends with a discarded card are offered (the others could not retrieve). Paragon of
   Valor repeats only "if they do" retrieve.
5. **Close Quarters family:** "if you are adjacent to an enemy hero" is checked after the movement and
   is a presence check (immune heroes count, p.18). "that enemy hero" is one Rowenna is adjacent to (she
   picks one when several). A bullet with nothing to place is not offered (RULINGS 38). Grand Melee's
   "Choose up to two times" allows the same bullet twice (p.10 "Modal Actions": twice / up to).
6. **[APP] Fabled Lance on Earn the Spurs.** "All your attack actions … target a unit in range": the
   Lance changes how far an attack reaches, not what it may target, so the Handicap still targets a
   hero (its "Before the attack" text needs a hero target). The printed Range 2 is raised by Range
   items (p.16), and the attack is ranged (RULINGS 19 for blocks). Tests "Fabled Lance - …".
7. **Stand Guard family:** "who has a card in the discard" can only be a hero; "adjacent to an enemy
   hero" is presence. The swap is the first instruction; Defend the Innocent's retrieve comes only after
   a swap.
8. **Opening Shots / Opening Volley:** the condition reads statuses (both discards), so the enemy hero
   is targeted and must be targetable. With the condition false there is no valid target: no Primary
   action (FAQ "first instruction"). Opening Volley's repeat is asked only when the condition holds
   again (a hero who just discarded no longer qualifies).
9. **Accept Surrender / Glorious Triumph:** the defeat follows p.13 (coins equal to the level, assist
   coins equal to the defeated hero's lowest card Tier for every other hero of Rowenna's team, on the
   board or not). Triple assist coins = three times that, i.e. twice more on top of the normal assist.
