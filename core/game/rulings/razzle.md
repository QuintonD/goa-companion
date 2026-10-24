# Rulings: Razzle the Ringmaster

Format as [RULINGS.md](../RULINGS.md): card, question, reading, reason, source. **[APP]** where the
card text, the hero's FAQ and the rulebook are silent. Code: `heroes/RazzleHero.kt`; tests:
`heroes/RazzleTest.kt`. The FAQ is quoted from `catalog/heroes/razzle.json` (goa2-faq).

1. **What the other figures are.** Each extra figure is a piece of kind `razzle_double` that *is*
   Razzle (`heroBody`): a unit and a hero of her team, defended with her cards, never a token (not
   removed at the end of round, p.19, and not by a Clear action), never a minion. "If defeated, remove
   all of you": defeating any figure defeats Razzle once — all figures leave the board, coins and Life
   counters are paid once (p.13). Tests "If defeated, remove all of you", Stunt Doubles.
2. **[APP] Which one of her acts.** Before Razzle chooses an action (after any respawn) she chooses which
   figure performs it; the actions offered are that figure's (Fast Travel from its Zone, Clear next to
   it, a Primary action only if it can start from there — FAQ "first instruction"). The engine keeps
   the acting figure on the hero's space (`HeroState.at`): the two spaces are exchanged as bookkeeping,
   not as a swap or a move. Engine: `Trigger.ChoosingAction` (EFFECTS.md "Razzle"). **Disagreement:**
   goa2-backend asks after the action is chosen.
3. **FAQ — "the same hero, except when actions are performed".** "During any action, regardless of
   whether it is performed by your enemy, ally or yourself, all the Razzles other than the active one is
   considered 'another hero'": every figure is a separate target, so a repeat "on a different enemy
   hero" (Xargatha's Cleave) may hit a second figure, and multi-target discards make Razzle discard once
   per figure. Test "FAQ - during an enemy action …".
4. **FAQ — her own actions.** By the same FAQ, during Razzle's action her other figures are other heroes
   of her team, i.e. friendly heroes (p.17 "another unit on your team"): Alleyoop / Group Performance /
   Team Spirit may swap with one of them, Magic Trick may push one. goa2-backend reads it the same way
   (its FRIENDLY filter keeps her other pieces). Test "Alleyoop - FAQ …".
5. **Stunt Doubles — spawning.** p.19 "Spawn": figures come only from the supply, never from the board,
   so "up to 3 more" is limited by the figures not in play (four in all). Spaces: in radius of the acting
   figure, empty, or a Spawn point holding a token (p.19 removes the token first). One at a time; Razzle
   may stop ("up to").
6. **Removing figures (Phantom Strike, Hit and Gone, Into Thin Air, Crowd Control).** p.19 "Remove":
   no coins, no defeat, no Life counters. **[APP]** Removing the acting figure while others remain:
   Razzle goes on as one of the others (the figures are identical, so which one is immaterial). Into
   Thin Air removing all of her: she is off the board, not defeated, and respawns at her next card as
   usual (the card's reminder, p.7). Crowd Control's skill with no other figure in play has nothing to
   do and is not offered.
7. **Crowd Control — defense.** "+2 Defense for each other one of you in radius": counted from the
   figure that was attacked (presence, p.18), with the card's Radius (items included).
8. **Rummage / Ransack.** "Another one of you in radius" is counted from the acting figure. Ransack:
   one optional retrieve per other figure in radius, from the shared discard pile.
9. **Tightrope / High Wire / Wire Dancers.** The Movement action first ("After you move", even a move
   of 0). Tightrope: "you may move another one of you 1 space" — optional, exactly 1 space (p.11).
   High Wire / Wire Dancers: "move another one of you up to N" — a figure is chosen when there is one,
   and "up to" allows 0.
10. **Theatrics / Spectacle.** "A minion": friendly or enemy, in range of the acting figure.
11. **Spectacle / Twin Strike — "by another one of you".** The repeat is performed by another figure,
    which becomes the acting one; only figures from which the action can start are offered; "a
    different minion / unit" excludes everything targeted so far (RULINGS 37); a repeated action can't
    repeat and Dodger's Enfeeblement forbids it (p.10). Twin Strike: "a basic attack action" is an
    Attack action on a basic card (p.9), i.e. her gold or Handicap; the repeated Stunt Doubles may spawn
    again while figures remain in the supply.
12. **Magic Trick / Aaaand it's gone!** Any adjacent unit that can be pushed; "for every space the
    target moved" is the actual push distance (p.18, as far as possible); this figure then moves that
    many spaces straight in the opposite direction, one space at a time (card-text movement); a blocked
    space stops the action (p.10).
13. **Known limits.** Effects of other heroes that make Razzle act outside her own card resolution
    (e.g. Whisper's "performs a movement action on the card they defended with") use her current acting
    figure, not necessarily the one that defended. The acting-figure bookkeeping exchanges the two
    figures' start-of-turn spaces too (`TurnRecord.startPositions`), so "where that unit was at the
    start of this turn" and "remained in the same space" follow the figures; Emmitt's per-id record of
    displacements this turn does not (a figure displaced earlier this turn and then chosen to act may
    count as not displaced, and the other way round).
