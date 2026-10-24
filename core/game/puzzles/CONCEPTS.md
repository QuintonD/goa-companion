# Puzzle concepts — 60 candidates for the 50-puzzle book (ADR-035)

Design notes for the chess-style puzzles of `PuzzleBook`. Each concept is a position, a goal
(`PuzzleGoal`), the intended line with the opponents' best replies, the tempting wrong tries,
three nudges and the end explanation. Encoding agents build the position in code and let the
solver prove it; a concept that does not survive is fixed or cut. 60 are offered so 10 can go.
How to encode one: [ENCODING.md](ENCODING.md); where each stands: [STATUS.md](STATUS.md). A concept
changed while encoding carries an "Encoded as" line saying what changed and why.

Sources used: the pinned 2nd-printing rulebook (printed pages, `p.N` = PDF page − 1), the card
texts and values in `app/src/main/assets/catalog/heroes/<hero>.json` (ids quoted exactly), the
FAQ rulings (each hero JSON's `faq`, and `faqs/common.json` of the goa2-faq source), the map
`app/src/main/assets/catalog/maps/forgotten-island.json`. Card text beats the rules (p.3).

## Conventions (apply to every concept unless it says otherwise)

- **Map:** Forgotten Island. Coordinates are axial `(q,r)` exactly as in the map JSON; directions
  follow `HexGrid.DIRECTIONS`: E `(+1,0)`, NE `(+1,−1)`, NW `(0,−1)`, W `(−1,0)`, SW `(−1,+1)`,
  SE `(0,+1)`. Zones: `red-throne`, `lane-1-1` (Red's pre-throne), `lane-1-2` (middle, the
  starting Battle Zone), `lane-1-3` (Blue's pre-throne), `blue-throne`, `red-side`, `blue-side`.
  Distances are hex distances (obstacles counted, p.17 "Farther", p.18 "Nearest"); every hex that
  is not a listed space is terrain, the water ring included (p.19 "Terrain").
- **Game:** Quick game with 4 heroes (2 per team): 4 Life counters and 3 Wave counters at the start
  (p.5); each concept states the counters still unspent. "BZ" = Battle Zone. A BZ of `lane-1-2`
  with 3 Wave counters means no push has happened yet; `lane-1-1`/`lane-1-3` means one has.
- **Heroes:** listed as *Hero (team, level, coins) at (q,r)*. Unless stated: 0 coins, no items, no
  markers, no active effects. Cards are given as **played** (this turn's card, unresolved unless
  marked), **resolved** (turn slots), **hand**, **discard**. A hero always owns exactly 5 cards
  (gold, silver, one per colour at its tier); the lists account for all 5. Initiative values
  quoted include items. "Already resolved" heroes take no further action this turn but are still
  units on the board (they can be attacked, they block, they defend).
- **Items:** a hero of level L has L−1 items (6 at level 8), one per upgrade: the other card of the
  colour and tier it kept (p.15–16). Items that matter are listed with their source card; any item not
  listed is the one implied by the hand in this way and changes nothing in the puzzle (no unlisted
  item alters a number the solution or a wrong try depends on).
- **Open information:** every opponent hand is given explicitly. The solver may treat it as open
  (the puzzle states it), which keeps the search exact. The opponent's possible replies are their
  defences (which card or none) and the actions of their own unresolved cards.
- **Player seats:** the heroes the player controls are named. Heroes not controlled by the player
  are either opponents (best reply) or teammates that have already resolved; no puzzle relies on
  an AI teammate's choice.
- **Minions:** every minion on the board is listed (team, type, space). Spawn points not listed as
  occupied are empty. Minion defence modifiers (p.14): +1 per adjacent friendly melee/heavy, −1 per
  adjacent enemy melee/heavy, −1 per enemy ranged minion within 2.
- **Goals:** `GainCoins` counts the coins gained this turn by all of the player's heroes together
  (if the engine counts one hero, the goal text names that hero and the number still holds where
  marked). `Survive` = none of the player's heroes is defeated and the lane is not pushed against
  them until the deadline (contract wording).
- **Geometry:** every distance, adjacency, straight line, push, path, Fast Travel and spawn-point
  claim below was computed with a throwaway script over the map JSON (see the appendix). Numbers in
  brackets like `d3` are hex distances.
- **Risk:** a reading that the rulebook or card text does not spell out is flagged `RISKY` in the
  concept and collected in the index at the end.

## Summary

| # | Title | Diff | Theme | Goal | Heroes (player first) |
|---|-------|------|-------|------|------------------------|
| C01 | Through the Line | 1 | Targets & straight lines | Win | Arien vs Wasp, Xargatha |
| C02 | Worth Two Counters | 1 | Life counters by tier, Block | Win | Garrus vs Brogan, Wasp |
| C03 | Melee First, Then the Heavy | 1 | Heavy Minion Immunity, Last Push | Push | Snorri vs Xargatha |
| C04 | Price on Her Head | 1 | Life counters, initiative | Win | Bain, Brogan vs Dodger, Xargatha |
| C05 | Beat the Drum | 1 | Coins & assist coins | Coins | Tigerclaw, Brogan vs Arien |
| C06 | The Hands of the Clock | 1 | Defend with initiative | Win | Emmitt vs Wuk, Tigerclaw |
| C07 | Ranged at Point Blank | 1 | Block, defend with the right card | Survive | Tigerclaw vs Sabina, Brogan |
| C08 | Walk the Plank | 1 | Push, zones | Win | Cutter vs Wasp, Dodger |
| C09 | Phantom at Five Spaces | 1 | Targets, tokens | Win | NebKher vs Dodger, Wasp |
| C10 | Shotgun Splash | 1 | Before the attack | Win | Swift vs Wasp, Brogan |
| C11 | By the Water's Edge | 1 | Terrain adjacency | Win | Mrak vs Wasp, Dodger |
| C12 | Fast Travel Within the Zone | 1 | Fast Travel | Survive | Xargatha vs Brogan, Swift |
| C13 | Empty the Spawn Point | 1 | Coins, spawn points | Coins | Tigerclaw, Dodger vs Arien |
| C14 | Long Thrust | 1 | Range from card text | Win | Xargatha vs Tigerclaw, Arien |
| C15 | Quickdraw | 1 | Card conditions, defence totals | Win | Sabina vs Arien, Wasp |
| C16 | Get Over Here! | 1 | Moving enemies, obstacles | Win | Bain, Xargatha vs Dodger, Wasp |
| C17 | Into the Wall | 1 | Push, forced discard | Defeat | Wasp, Xargatha vs Arien |
| C18 | Runecaster at Maximum Range | 1 | Maximum range, After the attack | Defeat | Snorri vs Dodger, Wasp |
| C19 | Smoke on the Line | 1 | Tokens, straight lines | Survive | Min vs Swift, Wasp |
| C20 | Clear the Smoke | 1 | Clear action | Defeat | Brogan, Swift vs Wuk |
| C21 | Charge onto the Spawn Point | 2 | Mandatory move, defence text | Defeat | Brogan vs Dodger |
| C22 | Change of Plans | 2 | Initiative (card swap) | Defeat | Takahide, Brogan vs Dodger |
| C23 | Lift Up into the Line | 2 | Moving enemies, straight lines | Defeat | Wasp, Swift vs Dodger |
| C24 | Reverse Time | 2 | Initiative, Next turn effect | Defeat | Wuk, Emmitt vs Tigerclaw |
| C25 | Cold Snap | 2 | Minion defence modifiers | Defeat | Tali, Brogan vs Wasp |
| C26 | Blood Fury to the Throne | 2 | Push the lane, throne | Win | Whisper vs Xargatha, Arien |
| C27 | Rain of Arrows | 2 | Items change maximum range | Win | Silverarrow vs Dodger, Tigerclaw |
| C28 | Angry Stampede | 2 | Straight lines, Last Push | Push | Wuk vs Dodger |
| C29 | Threaten the Guards | 2 | Minion defence modifiers, repeat | Defeat | Garrus, Brogan vs Wasp |
| C30 | Three Obstacles | 2 | Obstacles, initiative combo | Defeat | Tigerclaw, Brynn vs Dodger |
| C31 | Knife of the Living Dead | 2 | Token arithmetic | Defeat | Mortimer vs Wasp |
| C32 | Fight as One | 2 | Resolved skill reuse, Pyro range | Win | Widget vs Dodger, Wasp |
| C33 | Even a Blocked Shot Pushes | 2 | After the attack, Block | Defeat | Sabina, Brogan vs Tigerclaw |
| C34 | Respawn Where It Counts | 2 | Respawn choice, mandatory move | Push | Brogan vs Arien, Tigerclaw |
| C35 | Onslaught's Forced Step | 2 | "if able" mandatory step | Survive | Brogan vs Xargatha, Swift |
| C36 | Who Goes First? | 2 | Tie Breaker coin | Survive | Tigerclaw, Dodger vs Wasp |
| C37 | The Poisoned Clock | 2 | Markers, initiative, Block | Defeat | Wasp vs Arien |
| C38 | Strip the Silver | 2 | Active effects, same-team order | Push | Wasp, Swift vs Brogan, Dodger |
| C39 | Static Barrier | 2 | Active effects, Block | Survive | Wasp vs Brogan, Swift |
| C40 | Carry Away | 2 | Minions outside the BZ | Win | Widget, Swift vs Dodger |
| C41 | The Duelist's Oath | 3 | Defend with the right card, immunity | Survive | Arien vs Wasp, Dodger |
| C42 | The Courteous Trap | 3 | Card choice, tie, retrieve trap | Defeat | Rowenna vs Tigerclaw |
| C43 | Count the Minions | 3 | Minion Battle arithmetic | Push (round) | Tigerclaw, Xargatha vs Arien |
| C44 | A Fistful of Coins | 3 | Opponent's choice (fork) | Win | Cutter, Swift vs Wasp, Dodger |
| C45 | The Lesser Evil | 3 | Opponent's choice (fork) | Win | Whisper, Tigerclaw vs Xargatha, Wasp |
| C46 | Hurry Up! | 3 | Initiative, coin, reply | Defeat | Hanu, Brogan vs Tigerclaw, Xargatha |
| C47 | Swoop In | 3 | Retrieve to survive, two threats | Survive | Misa vs Garrus, Brynn |
| C48 | Necromancy at the Bell | 3 | End of round, minion types | Survive (round) | Dodger vs Xargatha |
| C49 | Prey Drive's Dilemma | 3 | Defender's best reply | Win | Ursafar vs Brogan, Wasp |
| C50 | Shoot, Push, Scoot | 3 | Mid-turn push opens Fast Travel | Survive | Silverarrow vs Xargatha, Brogan |
| C51 | Ice in the Veins | 3 | Initiative from tokens | Defeat | Tali, Brogan vs Tigerclaw |
| C52 | Battle Fury | 3 | Ultimate, card states | Defeat | Garrus vs Tigerclaw |
| C53 | Death Grenade | 3 | End of turn, opponent's reply | Defeat | Min vs Tigerclaw, Wasp |
| C54 | Chaos Incarnate | 3 | Tie Breaker coin as card text | Win | Ignatia vs Tigerclaw, Xargatha |
| C55 | The Push Strips the Guard | 3 | Push respawns, modifiers | Defeat | Swift, Brogan vs Arien |
| C56 | Pile On the Fresh Wave | 3 | Push mid-action, coins | Coins | Hanu, Tigerclaw |
| C57 | Turned to Stone | 3 | Counts as terrain, team order | Defeat | Mrak, Xargatha vs Dodger, Wasp |
| C58 | Unexpected Journey | 3 | Swap, immunity, blocking | Survive | Hanu vs Xargatha, Brogan |
| C59 | Mind Grip | 3 | Copying an enemy card | Win | NebKher vs Dodger, Brogan, Wasp |
| C60 | Hold My Saké | 3 | Card states (discard swap) | Defeat | Takahide, Brogan vs Dodger |

---

## Difficulty 1 — hard

### C01 — Through the Line
- **Theme:** targets and straight lines; "no line of sight". **Difficulty:** 1. **Heroes:** Arien; Wasp, Xargatha (Brogan idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 3, turn 4, cards revealed; Arien acts next (initiative 8). Coin: Red shows (no tie arises). Life counters: Red 1, Blue 3. Wave counters 3. BZ `lane-1-2`.
  - Arien (Blue, L1) at (0,−5). Played `arien-red-1` Dangerous Current (8). Resolved T1 `arien-gold`, T2 `arien-silver`, T3 `arien-blue-1`. Hand `arien-green-1`.
  - Brogan (Blue, L1) at (4,−2). Played `brogan-gold`, already resolved. Hand `brogan-green-1`.
  - Wasp (Red, L1) at (3,−5). Played `wasp-green-1` (3). Resolved T1 `wasp-gold`, T2 `wasp-red-1`, T3 `wasp-blue-1`. Discard `wasp-silver`. **Hand: none.**
  - Xargatha (Red, L1) at (0,−7). Played `xargatha-green-1` (5). Resolved T1 `xargatha-gold`, T2 `xargatha-red-1`, T3 `xargatha-blue-1`. Hand `xargatha-silver`.
  - Minions: Red melee (1,−5), (2,−5), (0,−6); Red heavy (1,0). Blue melee (1,−4), (3,−1); Blue heavy (2,−7).
- **Player controls:** Arien. **Opponents hold:** Wasp nothing; Xargatha `xargatha-silver` (both act after Arien).
- **Solution:** Dangerous Current targeting the Red melee at (1,−5) (E of Arien). Before the attack, the two spaces directly behind the target are (2,−5) (a Red minion) and (3,−5) (Wasp); "up to 1 enemy hero in any of the 2 spaces" → Wasp discards a card or is defeated; she has none → defeated. Red spends its last Life counter (Wasp's lowest tier I) → Blue wins at once. No reply exists (Wasp has no card).
- **Wrong tries:** (1) Dangerous Current on the melee at (0,−6) (NW): the space behind holds Xargatha, but she discards `xargatha-silver` and survives; the minion dies, no win. (2) "The minion at (2,−5) blocks the line": there is no line of sight (p.3, p.9); the card names spaces, not a clear path. (3) Secondary Movement 4 toward Wasp: no attack follows.
- **Nudges:** (1) "Who on Red can no longer defend?" (2) "Dangerous Current reaches further than the unit you hit — look at the spaces behind it." (3) "Attack the melee minion east of Arien; Wasp stands two spaces behind it."
- **Explanation:** Dangerous Current's "Before the attack" text resolves before the target is hit (p.14 order step 1, p.17) and reaches the 2 spaces in a straight line behind the target, whatever stands between — there are no line-of-sight rules (p.3, p.9). Wasp has no card left, so "discards a card, or is defeated" defeats her. A defeated hero's team spends Life counters equal to her lowest card tier (p.13); Red had one left, so the game ends immediately.
- **Rules:** p.3 Line of sight; p.9 Range/Radius; p.13 When a hero is defeated; p.14 Attack/Defense detailed order; p.17 Before the attack, If able.
- **Risk:** none ("directly behind" = continuing the line from Arien through the target, the card's own wording).

### C02 — Worth Two Counters
- **Theme:** Life counters by lowest tier; Block cards only stop their attack type. **Difficulty:** 1. **Heroes:** Garrus; Brogan, Wasp.
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Garrus acts next (Chase, 9). Coin: Red shows. Life: Red 2, Blue 3. Waves 3. BZ `lane-1-2`.
  - Garrus (Blue, L2) at (0,−4). Items: +1 initiative (`garrus-red-2b` tucked). Played `garrus-red-2a` Chase (8+1 = 9). Resolved T1 `garrus-gold`, T2 `garrus-silver`, T3 `garrus-blue-1`. Discard `garrus-green-1`. Hand none.
  - Arien (Blue, L1) at (−1,−1), already resolved.
  - Brogan (Red, L3) at (−1,−4). Played `brogan-blue-1` (8). Resolved T1 `brogan-gold`, T2 `brogan-red-2a`, T3 `brogan-green-2b`. Discard `brogan-silver`. Hand none.
  - Wasp (Red, L4; items +1 defence, +2 attack) at (3,−6). Played `wasp-gold` (12), already resolved. Resolved T1 `wasp-red-2a`, T2 `wasp-blue-2a`, T3 `wasp-silver`. **Hand `wasp-green-2a` Deflect Projectiles** (Block a ranged attack; no Defense value).
  - Minions: Red melee (1,−6); Red heavy (1,0). Blue melee (1,−4); Blue heavy (2,−7).
- **Player controls:** Garrus. **Opponents:** Brogan has no card; Wasp holds only Deflect Projectiles.
- **Solution:** Chase, first bullet: Garrus has a card in his discard, so he moves up to 2 — via (1,−5) to (2,−5) (or to (2,−6)), adjacent to Wasp — and attacks her (Attack 4, non-ranged). Deflect Projectiles blocks only ranged attacks: if she discards it the Block condition fails and she is defeated; not defending also defeats her. Wasp is level 4, lowest tier II → Red spends 2 Life counters, its last → Blue wins.
- **Wrong tries:** (1) Attack the adjacent Brogan (no cards, level 3, 3 coins): he is defeated but his lowest tier is I → 1 counter, Red keeps 1. (2) Assume Wasp can block: Deflect Projectiles names ranged attacks; Chase is non-ranged (p.13, p.14 Block).
- **Nudges:** (1) "Two Life counters left — which Red hero is worth both?" (2) "Read Wasp's last card: what kind of attack does it stop?" (3) "Use Chase's move to reach Wasp at (2,−5) and attack her."
- **Explanation:** A defeated hero's team spends Life counters equal to that hero's lowest card tier, not one per hero (p.13): level 4 means tier II, so Wasp costs Red 2 counters while Brogan (level 3, tier I) costs 1. A Block card defends only when its condition is met; otherwise the defender is defeated (p.14). Chase's movement needs at least one card in your discard (card text), which Garrus has.
- **Rules:** p.13 Attacking a hero, When a hero is defeated, level/tier table; p.14 Block.
- **Risk:** none.
- **Encoded as p02 (2026-10-24):** as written (Brogan's items `brogan-red-2b`, `brogan-green-2a`; Wasp's `wasp-red-2b`, `wasp-blue-2b`, `wasp-green-2b`). The solver finds (2,−5) and (2,−6) as the winning spaces of Chase's move; Chase's second option, or its first without moving, leaves only Brogan to hit.

### C03 — Melee First, Then the Heavy
- **Theme:** Heavy Minion Immunity; Last Push. **Difficulty:** 1. **Heroes:** Snorri; Xargatha (Garrus, Tigerclaw idle).
- **Goal:** `PushLane(turn)` — "Push the lane this turn." (With one Wave counter left the push also wins.)
- **Position:** round 4, turn 4, revealed; Snorri acts next (8). Coin: Blue shows. Life: Red 3, Blue 2. **Wave counters: 1.** BZ `lane-1-2`.
  - Snorri (Blue, L5; items +2 defence, +1 attack, +1 movement) at (1,−1). Rune markers (from `snorri-silver`, resolved T1): T1 anvil, T2 bird, T3 horn, **T4 axe**. Played `snorri-red-3a` Runic Battleaxe (8; Attack 6+1 = 7). Resolved T1 `snorri-silver`, T2 `snorri-gold`, T3 `snorri-green-2a`. Hand `snorri-blue-2a`.
  - Garrus (Blue) at (3,−6), already resolved.
  - Xargatha (Red, L1) at (2,−2). Played `xargatha-green-1` (5). Discard `xargatha-silver`. Hand none.
  - Tigerclaw (Red) at (−1,−4), already resolved.
  - Minions: **Red melee (0,−1); Red heavy (1,0)** — Red's only minions. Blue melee (2,−1), (1,−4); Blue heavy (2,−7).
- **Player controls:** Snorri. **Opponents:** Xargatha holds nothing and acts after Snorri.
- **Solution:** Runic Battleaxe on the Red melee at (0,−1) → defeated (2 coins). After the attack the axe rune is active: "May repeat once on an enemy minion" → the Red heavy at (1,0), no longer Immune because no Red non-heavy minion remains in the BZ → defeated (4 coins). Red has no minion left → the Lane is pushed at once; the last Wave counter flips → Blue wins.
- **Wrong tries:** (1) Target the heavy first: Immune while the melee lives — not a legal target (p.13). (2) Defeat Xargatha first (no cards): 1 Life counter (Red keeps 2), then the repeat may only hit the melee; the heavy survives, no push.
- **Nudges:** (1) "One Wave counter left — what ends the game right now?" (2) "The heavy can't be touched while another Red minion still stands in the Battle Zone." (3) "Hit the melee minion first, then repeat on the heavy."
- **Explanation:** As long as a friendly non-heavy minion is in the BZ the heavy minion is Immune and cannot be targeted (p.13). Defeating the melee lifts that, and the axe rune's repeat (a new instance of the attack, p.10) can then take the heavy. When the last enemy minion in the BZ is defeated the Lane is pushed immediately (p.8), and flipping the last Wave counter ends the game for the team that won the push (p.3, p.8).
- **Rules:** p.8 Push the Lane, Triggering a Push; p.10 Repeat; p.13 Heavy Minion Immunity.
- **Risk:** none.
- **Encoded as p03 (2026-10-24):** as written; Garrus and Tigerclaw (both playable now) sit idle. Rune markers carry their Turn slot (`marker_rune_axe` under slot 4). After the axe repeat the heavy minion is the only target, so the line is Primary, the melee minion, Yes.

### C04 — Price on Her Head
- **Theme:** Life counters (Bounty marker); initiative order between teammates. **Difficulty:** 1. **Heroes:** Bain, Brogan; Dodger, Xargatha.
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Bain acts next (11). Coin: Blue. Life: Red 2, Blue 3. Waves 3. BZ `lane-1-2`.
  - Bain (Blue, L1) at (0,−4). Played `bain-gold` Dead or Alive (11). Resolved T1 `bain-red-1`, T2 `bain-blue-1`, T3 `bain-silver`. Hand `bain-green-1`.
  - Brogan (Blue, L1) at (1,−2). Played `brogan-red-1` Mad Dash (7). Resolved T1 `brogan-gold`, T2 `brogan-blue-1`, T3 `brogan-silver`. Hand `brogan-green-1`.
  - Dodger (Red, L1) at (3,−3). Played `dodger-green-1` (3). Resolved T1 `dodger-gold`, T2 `dodger-red-1`, T3 `dodger-silver`. Discard `dodger-blue-1`. Hand none.
  - Xargatha (Red, L1) at (1,−5). Played `xargatha-silver` (3). Resolved T1 `xargatha-gold`, T2 `xargatha-red-1`, T3 `xargatha-green-1`. Hand `xargatha-blue-1` (Defense 5).
  - Minions: Red ranged (0,−3), Red melee (4,−4), Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
- **Player controls:** Bain and Brogan. **Opponents:** Dodger nothing; Xargatha `xargatha-blue-1`; both act after Brogan.
- **Solution:** Bain attacks the adjacent Red ranged minion at (0,−3) (any adjacent target will do) → defeated. After the attack he gives the Bounty marker to Dodger (d4, inside radius 4). Brogan's Mad Dash: 2 spaces E via (2,−2) to (3,−2) (or NE via (2,−3) to (3,−4)), adjacent to Dodger → Attack 6; she has no card → defeated. Lowest tier I + 1 for the Bounty = 2 Life counters → Red is out → Blue wins.
- **Wrong tries:** (1) Bounty on Xargatha: Brogan cannot reach her (his NW line runs into terrain at (1,−3)); defeating Dodger then costs only 1 counter. (2) No bounty: same, 1 counter. (3) Bain without an adjacent target cannot perform the attack, so no "After the attack" bounty (p.10, faq-common-fizzle).
- **Nudges:** (1) "Red has two counters; a level-1 hero only pays one…" (2) "Bain can make one hero cost more — but only one Brogan can actually reach." (3) "Attack the ranged minion with Bain and put the Bounty on Dodger."
- **Explanation:** Bain acts at 11, before Brogan's 7, so the marker is in place when Dodger falls (p.7 initiative order). "A hero with the Bounty marker spends 1 additional Life counter when defeated" (card text), on top of the lowest-tier counters (p.13). The bounty is given after Bain's own attack resolves, so he needs some adjacent target.
- **Rules:** p.7 Check the initiative; p.10 Mandatory steps; p.13 When a hero is defeated.
- **Risk:** none.
- **Encoded as p04 (2026-10-24):** as written. Xargatha is seated before Dodger. As the concept says, any adjacent target works for Bain (the solver accepts the ranged minion or Xargatha); the Bounty must go to Dodger, and Brogan's dash may end at (3,−2) or (3,−4).

### C05 — Beat the Drum
- **Theme:** coins and assist coins; "friendly" never includes you. **Difficulty:** 1. **Heroes:** Tigerclaw, Brogan; Arien (Wasp idle).
- **Goal:** `GainCoins(6, turn)` — "Gain at least 6 coins for your team this turn."
- **Position:** round 2, turn 4, revealed; Tigerclaw acts next (9). Coin: Red. Life: Red 4, Blue 4. Waves 3. **No hero has been defeated this round.** BZ `lane-1-2`.
  - Tigerclaw (Blue, L1, 0 coins) at (3,−6). Played `tigerclaw-red-1` Hit and Run (9). Resolved T1 `tigerclaw-gold`, T2 `tigerclaw-blue-1`, T3 `tigerclaw-green-1`. Hand `tigerclaw-silver`.
  - Brogan (Blue, L2, 1 coin; item +1 attack) at (−2,−3) (`lane-1-1`). Played `brogan-green-2b` War Drummer (5, Range 4). Resolved T1 `brogan-gold`, T2 `brogan-red-1`, T3 `brogan-blue-1`. Hand `brogan-silver`.
  - Arien (Red, L2; item +1 defence) at (4,−7). Played `arien-green-1` (4). Resolved T1 `arien-gold`, T2 `arien-silver`, T3 `arien-blue-1`. Discard `arien-red-2a`. Hand none.
  - Wasp (Red) at (0,0), already resolved.
  - Minions: Red melee (0,−6), (2,−3), (4,−6); Red heavy (1,0). Blue melee (2,−1), (1,−4); Blue heavy (2,−7).
- **Player controls:** Tigerclaw and Brogan. **Opponent:** Arien has no card; he acts after both.
- **Solution:** Hit and Run on Arien (adjacent) → defeated: Tigerclaw +2 (Arien's level), Brogan +1 assist (Arien's lowest tier I). After the attack Tigerclaw moves 1 to (2,−6) or (2,−5), which is d4 from Brogan (from (3,−6) he is d5). War Drummer: a friendly hero in range gains 1 coin, 3 if any hero was defeated this round → Tigerclaw +3. Total 6.
- **Wrong tries:** (1) Hit the Red melee at (4,−6): 2 + 1 (no defeat this round) = 3. (2) Defeat Arien but stay at (3,−6): War Drummer has no friendly hero in range — its first step fails and the card does nothing (p.10) → 3. (3) "Brogan can drum for himself": a friendly hero never includes you (p.17).
- **Nudges:** (1) "War Drummer pays much more after a defeat this round." (2) "Count the range from Brogan to where Tigerclaw ends his action." (3) "Defeat Arien with Hit and Run, then step one space toward Brogan."
- **Explanation:** Defeating a hero pays its level to the attacker and assist coins equal to its lowest tier to each teammate (p.13). War Drummer gives 3 instead of 1 once any hero was defeated this round, and it acts at initiative 5, after Hit and Run at 9 (p.7). Its target must be another hero in range when it resolves, so Tigerclaw's optional after-attack step matters.
- **Rules:** p.7 initiative; p.10 Mandatory and optional steps; p.13 coins table; p.17 Friendly.
- **Risk:** `GainCoins` counts the team total (convention); Tigerclaw alone gains 5.
- **Encoded as p05 (2026-10-24):** as written (Brogan's item `brogan-green-2a`, Arien's `arien-red-2b`). Winning steps after the attack: (2,−6) or (2,−5).

### C06 — The Hands of the Clock
- **Theme:** defending with initiative (Temporal Punch); choosing the target. **Difficulty:** 1. **Heroes:** Emmitt; Wuk, Tigerclaw (Sabina idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 3, turn 4, revealed; Emmitt acts next (7). Coin: Red. Life: Red 1, Blue 3. Waves 3. BZ `lane-1-2`.
  - Emmitt (Blue, L1) at (0,−1). Played `emmitt-red-1` Temporal Punch (7, Attack 9). Resolved T1 `emmitt-gold`, T2 `emmitt-blue-1`, T3 `emmitt-green-1`. Hand `emmitt-silver`.
  - Sabina (Blue) at (3,−5), already resolved.
  - Wuk (Red, L1) at (0,0). Played `wuk-green-1` (6). Resolved T1 `wuk-gold`, T2 `wuk-silver`, T3 `wuk-blue-1`. **Hand `wuk-red-1`** (Initiative 7, Defense 7).
  - Tigerclaw (Red, L1) at (−1,−1). Played `tigerclaw-green-1` (2). Resolved T1 `tigerclaw-red-1`, T2 `tigerclaw-blue-1`, T3 `tigerclaw-silver`. **Hand `tigerclaw-gold`** (Initiative 13, Defense 1).
  - Minions: Red heavy (1,0) (adjacent to Wuk), Red melee (0,−6). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Emmitt. **Opponents:** Wuk `wuk-red-1`; Tigerclaw `tigerclaw-gold`.
- **Solution:** Temporal Punch on Wuk. When defending he must use the Initiative of his card and items: `wuk-red-1` has 7, +1 for the adjacent friendly heavy = 8 < 9 → defeated (or he declines and is defeated). Red's last Life counter → Blue wins.
- **Wrong tries:** Temporal Punch on Tigerclaw "because his card has Defense 1": he defends with `tigerclaw-gold`'s Initiative 13 ≥ 9 and survives (his best reply). With a normal attack the reverse would be true.
- **Nudges:** (1) "Read what Temporal Punch makes the defender count." (2) "Fast heroes have high initiative — slow ones don't." (3) "Punch Wuk."
- **Explanation:** Temporal Punch replaces the Defense value of the defender's card and items with its Initiative value; minion defence modifiers still apply (card reminder). Wuk's whole deck is slow (initiative stat 1), Tigerclaw's gold card is the fastest in his deck, so the "weak" defender is the safe one this time. The defender always chooses the best card (p.14).
- **Rules:** p.14 Defense action, Defense total, Minion defense modifiers; p.13 When a hero is defeated.
- **Risk:** none.
- **Encoded as p06 (2026-10-24):** as written; Tigerclaw is seated before Wuk, so the tempting target is listed first.

### C07 — Ranged at Point Blank
- **Theme:** Block: which attack is ranged; defend with the right card. **Difficulty:** 1. **Heroes:** Tigerclaw; Sabina, Brogan.
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 2, turn 3, revealed; Sabina acts next (12). Coin: Blue. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Tigerclaw (Red, L3; items +1 defence from `tigerclaw-red-2b`, +1 attack from `tigerclaw-blue-2a`) at (1,−5). Played `tigerclaw-green-1` (2). Resolved T1 `tigerclaw-gold`, T2 `tigerclaw-silver`. Hand `tigerclaw-red-2a` Combat Reflexes (Defense 3), `tigerclaw-blue-2b` Parry (Block a non-ranged attack; the attacker discards).
  - Wasp (Red) at (−1,0), already resolved this turn.
  - Sabina (Blue, L1) at (0,−5). Played `sabina-gold` Point Blank Shot (12; Attack 2, **Ranged**, Range 1). Resolved T1 `sabina-red-1`, T2 `sabina-blue-1`. Hand `sabina-silver`, `sabina-green-1`.
  - Brogan (Blue, L1) at (4,−6). Played `brogan-red-1` Mad Dash (7). Resolved T1 `brogan-gold`, T2 `brogan-blue-1`. Hand `brogan-silver`, `brogan-green-1`.
  - Minions: Red melee (0,−6), (2,−3); Red heavy (1,0). Blue melee (3,−1), (2,−1); Blue heavy (2,−7).
- **Player controls:** Tigerclaw (his decisions are defences). **Opponents:** Sabina and Brogan, both attacking him if able.
- **Solution:** Sabina shoots Tigerclaw (Attack 2). Defend with Combat Reflexes: 3 +1 item = 4 ≥ 2. After the attack she pushes him 1 E to (2,−5). Brogan's Mad Dash W via (3,−6) to (2,−6) is adjacent to him (it would be from (1,−5) too) → Attack 6, non-ranged → defend with Parry: blocked, Brogan discards a card.
- **Wrong tries:** (1) Parry against Point Blank Shot "because she is adjacent": it is a ranged attack (Range 1) → the Block fails → defeated (common FAQ "range 1 attack"). (2) Combat Reflexes against Brogan: 4 < 6. (3) Not defending: defeated.
- **Nudges:** (1) "Range 1 is still range." (2) "Each of your cards answers exactly one of the two attacks." (3) "Defend Sabina's shot with Combat Reflexes and keep Parry for Brogan."
- **Explanation:** An attack is ranged when its primary action says "Ranged", whatever its range; Point Blank Shot has Range 1, so it can only reach adjacent targets but is still ranged (p.9, faq-common-J6uK9mGB). Parry blocks only non-ranged attacks; Block cards succeed only when their condition is met (p.14). Each attack is defended with a separate card from hand (p.14), so the order of use matters.
- **Rules:** p.9 Ranged/Non-ranged; p.13 Attack action; p.14 Defense action, Block.
- **Risk:** none.
- **Encoded as p07 (changed, 2026-10-24):** as written the first option at every decision won (Combat Reflexes, the first card in catalog order, is right against the first attack, then Parry against Brogan). Tigerclaw now holds **`tigerclaw-red-2b` Backstab (Defense 5) and `tigerclaw-blue-2a` Sidestep (Block a ranged attack)**, with the items `tigerclaw-red-2a` (+1 Initiative) and `tigerclaw-blue-2b` (+1 Defense). Same lesson: Sidestep blocks the point-blank shot, Backstab 5 + 1 = 6 holds Brogan's 6; Backstab first leaves Sidestep against a non-ranged attack. Sidestep's optional step is free (every choice survives). Wasp's idle hand: `wasp-green-1`, `wasp-blue-1`.

### C08 — Walk the Plank
- **Theme:** pushing across a zone border; zones you might not notice. **Difficulty:** 1. **Heroes:** Cutter; Wasp, Dodger (Swift idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Cutter acts next (12). Coin: Red. Life: Red 1, Blue 3. Waves 3. BZ `lane-1-2`.
  - Cutter (Blue, L1) at (−1,−4). Played `cutter-gold` Walk the Plank (12). Resolved T1 `cutter-red-1`, T2 `cutter-green-1`, T3 `cutter-blue-1`. Hand `cutter-silver`.
  - Swift (Blue) at (3,−6), already resolved.
  - Wasp (Red, L1) at (−1,−5) (`lane-1-2`). Played `wasp-green-1` (3); discard `wasp-silver`; hand none.
  - Dodger (Red, L1) at (−2,−3) (`lane-1-1`). Played `dodger-green-1` (3); discard `dodger-silver`; hand none.
  - Minions: Red melee (0,−4) (adjacent to Cutter); Red heavy (1,0). Blue melee (1,−4); Blue heavy (2,−7).
- **Player controls:** Cutter. **Opponents:** neither Red hero has a card.
- **Solution:** First bullet on Wasp, push 2, 3 or 4: directly away from Cutter is NW — (−1,−6) in `lane-1-2`, then (−1,−7) in `red-side`; (−1,−8) is terrain, so she stops at (−1,−7). She was pushed into another zone → discards a card or is defeated → defeated → Red's last counter → Blue wins.
- **Wrong tries:** (1) Push Wasp only 1: she stays in `lane-1-2`. (2) Push Dodger: she starts in `lane-1-1` and every push SW ((−3,−2), (−4,−1), (−5,0), (−6,1)) keeps her in `lane-1-1` — she is "in another zone" from Cutter but is not pushed into one. (3) Second bullet on the melee: 2 coins, no win.
- **Nudges:** (1) "Walk the Plank only bites when the push crosses a border." (2) "The narrow strip north-west of the middle is its own zone." (3) "Push Wasp at least 2 spaces."
- **Explanation:** A push moves the target in a straight line directly away from the pusher, as far as possible up to the distance chosen (p.18 Push); obstacles and terrain stop it. `red-side` is a separate Zone even though it is a thin strip, so Wasp ends in another Zone and the card's second clause applies; with no card to discard she is defeated (p.17 If able).
- **Rules:** p.18 Push; p.19 Zone, Terrain; p.13 When a hero is defeated.
- **Risk:** "pushed into another zone" read as "ends the push in a different Zone than it started" (unambiguous here).
- **Encoded as p08 (2026-10-24):** as in the concept (Swift idle, resolved). The solver confirms Wasp as the only winning hero and 2, 3 or 4 as the winning distances; pushing Dodger, pushing Wasp 1 and the minion bullet are refuted.

### C09 — Phantom at Five Spaces
- **Theme:** targeting conditions; moving a token before the attack. **Difficulty:** 1. **Heroes:** NebKher; Dodger, Wasp (Garrus idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; NebKher acts next (7). Coin: Blue. Life: Red 1, Blue 2. Waves 3. BZ `lane-1-2`.
  - NebKher (Blue, L2; item +1 defence from `nebkher-red-2b`) at (3,−2). Played `nebkher-red-2a` Phantasmal Warrior (7, Attack 3, Ranged, Range 5). Resolved T1 `nebkher-green-1`, T2 `nebkher-gold`, T3 `nebkher-blue-1`. Hand `nebkher-silver`.
  - Garrus (Blue) at (−1,−1), already resolved.
  - Dodger (Red, L1) at (0,−4) (d5). Played `dodger-green-1` (3); discard `dodger-blue-1`; hand none.
  - Wasp (Red, L1) at (5,−3) (d2). Played `wasp-green-1` (3); hand `wasp-blue-1` (Defense 5).
  - Tokens: Illusion at (2,−5) (d4 from NebKher) and at (4,−3) (adjacent to NebKher and Wasp).
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** NebKher. **Opponents:** Dodger nothing; Wasp `wasp-blue-1`.
- **Solution:** First bullet: before the attack move the Illusion at (2,−5) one space to (1,−5) (or (1,−4)), adjacent to Dodger, who is in range 5; target Dodger → no card → defeated → Blue wins.
- **Wrong tries:** (1) Use the Illusion already next to Wasp: Wasp defends with Defense 5 ≥ 3. (2) Second bullet "unit adjacent to you": only the Blue minion is adjacent — friendly.
- **Nudges:** (1) "The far Red hero can't defend at all." (2) "Your attack needs an Illusion next to its target — can one get there?" (3) "Slide the Illusion at (2,−5) next to Dodger and shoot her."
- **Explanation:** Phantasmal Warrior's "Before the attack" moves an Illusion token (in range) up to 1 space to a space adjacent to an enemy hero in range, then targets that hero; the token moves like a unit (p.11 Movement in the card text) into an empty space. Range 5 reaches Dodger exactly (p.9). With no card in hand she cannot defend (p.13).
- **Rules:** p.9 Range; p.11 Movement in the card text; p.13 Attacking a hero; p.19 Tokens.
- **Risk:** none.
- **Encoded as p09 (2026-10-24):** as written. Phantasmal Warrior's bullet is not asked (no enemy unit is adjacent to NebKher, so only the first can be done); the Illusion beside Wasp is listed first. Both (1,−5) and (1,−4) win.

### C10 — Shotgun Splash
- **Theme:** "Before the attack" hits a hero next to the real target. **Difficulty:** 1. **Heroes:** Swift; Wasp, Brogan (Arien idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Swift acts next (9). Coin: Red. Life: Red 2, Blue 2. Waves 3. BZ `lane-1-2`.
  - Swift (Blue, L5; items +3 defence from `swift-red-2a`, `swift-blue-2b`, `swift-red-3a`, +1 attack from `swift-green-2a`) at (2,−2). Played `swift-red-3b` Super-Shotgun (9, Attack 5+1 = 6, Ranged, Range 2). Resolved T1 `swift-gold`, T2 `swift-silver`, T3 `swift-green-2b`. Hand `swift-blue-2a`.
  - Arien (Blue) at (0,−5), already resolved.
  - Wasp (Red, L4) at (−1,−2) (d3, out of range). Played `wasp-green-2a` (3); discard 1; hand none.
  - Brogan (Red, L3) at (3,−3) (adjacent to Swift). Played `brogan-green-2a` (5); discard 1; hand none.
  - Minions: Red melee (0,−2) (d2 from Swift, adjacent to Wasp); Red heavy (1,0). Blue melee (2,−1); Blue heavy (2,−7).
- **Player controls:** Swift. **Opponents:** no cards.
- **Solution:** Super-Shotgun targeting the Red melee at (0,−2). Before the attack an enemy hero adjacent to the target — Wasp — discards a card or is defeated → defeated: level 4, tier II → Red spends 2 counters, its last → Blue wins (the minion's fate no longer matters).
- **Wrong tries:** (1) Shoot Brogan (adjacent, no cards): he falls but tier I → 1 counter, Red keeps 1 (and with no enemy hero next to Brogan the "Before the attack" step cannot even be completed, see risk). (2) Shoot Wasp: d3 > Range 2.
- **Nudges:** (1) "Which Red hero is worth both counters?" (2) "Your shot hurts someone standing next to its target before it lands." (3) "Shoot the melee minion beside Wasp."
- **Explanation:** "Before the attack" text is applied at the very beginning, before the target defends or is removed (p.14, p.17), so the blast reaches the hero next to the minion. Wasp's lowest tier is II, so her defeat spends 2 Life counters (p.13) — the game ends at once.
- **Rules:** p.13 When a hero is defeated; p.14 Attack/Defense detailed order; p.17 Before the attack.
- **Risk:** `RISKY` (only for the wrong try): with no enemy hero adjacent to the target, the mandatory "Before the attack" step cannot be completed; read literally (p.10) the action stops. The intended line does not depend on it.
- **Encoded as p10 (2026-10-24):** as in the concept. Under the engine (rulings/swift.md 3) Super-Shotgun's "Before the attack" step is mandatory, so shooting Brogan stops before the attack (no enemy hero next to him); the explanation says so. The melee at (0,−2) is the only winning target.

### C11 — By the Water's Edge
- **Theme:** terrain adjacency (the water ring counts); Life counters by tier. **Difficulty:** 1. **Heroes:** Mrak; Wasp, Dodger (Tigerclaw idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Mrak acts next (7). Coin: Blue. Life: Red 2, Blue 3. Waves 3. BZ `lane-1-2`.
  - Mrak (Blue, L1) at (3,−6). Played `mrak-red-1` Seismic Slam (7; Skill, Radius 3; secondary Attack 6, non-ranged). Resolved T1 `mrak-gold`, T2 `mrak-silver`, T3 `mrak-blue-1`. Hand `mrak-green-1`.
  - Tigerclaw (Blue) at (0,−2), already resolved.
  - Wasp (Red, L4) at (4,−9) (d3). Played `wasp-green-2a` (3); discard 1; hand none. Her neighbour (5,−10) is off the grid (water).
  - Dodger (Red, L1) at (2,−6) (adjacent to Mrak; no terrain, token or rock next to her). Played `dodger-green-1` (3); discard 1; hand none.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Mrak. **Opponents:** no cards.
- **Solution:** Seismic Slam (primary): "An enemy hero in radius adjacent to terrain, or to a Rock token, discards a card, or is defeated" → Wasp at d3, adjacent to the water space (5,−10) → defeated → tier II → Red spends its last 2 counters → Blue wins.
- **Wrong tries:** (1) Secondary Attack on the adjacent Dodger (no cards): defeated, but tier I → Red keeps 1. (2) Seismic Slam on Dodger: she touches no terrain or Rock — not a legal choice. (3) "Wasp isn't next to any rock": the water around the board is terrain.
- **Nudges:** (1) "Red needs to lose two counters at once." (2) "Look at what surrounds Wasp on the map's edge." (3) "Seismic Slam Wasp — the water beside her is terrain."
- **Explanation:** "Any board space without a full grid outline counts as terrain … including the water spaces surrounding the board" (p.19), so a hero on the edge is adjacent to terrain. Seismic Slam's discard-or-be-defeated defeats a hero with no cards (p.17 If able). Wasp's lowest tier is II → 2 Life counters (p.13).
- **Rules:** p.13 When a hero is defeated; p.17 If able; p.19 Terrain.
- **Risk:** none.
- **Encoded as p11 (changed, 2026-10-24):** as written Seismic Slam had one legal target (Wasp) and the first option won. **Dodger now stands at (4,−6)**, still adjacent to Mrak and now also next to the terrain at (5,−6), so the Slam has an obvious target by the rocks (one counter) and the winning one by the water. Dodger is seated before Wasp.

### C12 — Fast Travel Within the Zone
- **Theme:** Fast Travel (same zone; blocked zones); "use your gold to Fast Travel". **Difficulty:** 1. **Heroes:** Xargatha; Brogan, Swift (Dodger idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 4, turn 4, revealed; Xargatha acts next (11). Coin: Red. Life: Red 2, Blue 3. Waves 3. BZ `lane-1-2`.
  - Xargatha (Red, L1) at (5,−1) in `blue-side`. Played `xargatha-gold` Cleave (11; secondary Movement 1). Resolved T1–T3 `xargatha-red-1`, `xargatha-green-1`, `xargatha-blue-1`; discard `xargatha-silver`; hand none.
  - Dodger (Red) at (−1,−2), already resolved.
  - Brogan (Blue, L1) at (3,−1) (`lane-1-2`). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Swift (Blue, L2; item +1 initiative from `swift-red-2b`) at (7,−1) in `blue-throne`. Played `swift-red-2a` Prepared Shot (8+1 = 9; Ranged, Range 4, straight line, not adjacent). Hand 1 card.
  - Minions: Blue melee (1,−4), (2,−1); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Xargatha. **Opponents:** Swift then Brogan, attacking her if they can.
- **Solution:** Use Cleave's Movement action as Fast Travel: `blue-side` holds no enemy hero or minion, so she may go to any empty space of her own Zone — e.g. (−2,4), (−1,3), (−1,4), (0,3), (0,4), (1,3), (1,4), (2,3), (3,1) or (6,0). None of them is on a straight line within 4 of Swift (or is adjacent to her), and Brogan has no 2-space straight run ending next to any of them.
- **Wrong tries:** (1) Hold: Swift's line W to (5,−1) at d2 → defeated. (2) Walk 1 space: (4,−1) is on Swift's line (d3); (4,0), (5,0), (6,−1) are reached by Brogan's dash E through (4,−1),(5,−1). (3) Fast Travel to `blue-throne` (Swift is there) or `lane-1-2` (enemy minions): forbidden. (4) Fast Travel to (2,2): Brogan dashes SE via (3,0),(3,1).
- **Nudges:** (1) "Walking one space won't get you clear." (2) "Your card's Movement action can be replaced by something much longer." (3) "Fast Travel inside blue-side, to its far end."
- **Explanation:** Fast Travel replaces a whole Movement action, whatever its value, and may land on any empty space of the same Zone or an adjacent Zone, provided neither the Zone you leave nor the one you enter holds an enemy hero or minion (p.11; the Arien example allows the own Zone). Swift in `blue-throne` and the minions in `lane-1-2` close those Zones, but `blue-side` itself is long and empty.
- **Rules:** p.11 Fast Travel; p.13 Ranged attacks.
- **Risk:** none.
- **Encoded as p12 (2026-10-24):** as in the concept (hands: Brogan `brogan-silver`, Swift `swift-blue-1`, Dodger `dodger-blue-1`). The solver's winning Fast Travel spaces are exactly the ten listed; Hold, a Movement action and Fast Travel to (2,2) are refuted.

### C13 — Empty the Spawn Point
- **Theme:** coins; "empty spawn point" conditions; initiative between teammates. **Difficulty:** 1. **Heroes:** Tigerclaw, Dodger; Arien (Wasp idle).
- **Goal:** `GainCoins(4, turn)` — "Gain at least 4 coins for your team this turn."
- **Position:** round 2, turn 4, revealed; Tigerclaw acts next (9). Coin: Blue. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Tigerclaw (Red, L1) at (2,−5). Played `tigerclaw-red-1` Hit and Run (9). Resolved T1 `tigerclaw-gold`, T2 `tigerclaw-blue-1`, T3 `tigerclaw-green-1`. Hand `tigerclaw-silver`.
  - Dodger (Red, L2; item +1 attack from `dodger-green-2b`) at (0,−5). Played `dodger-green-2a` Darker Ritual (3; Radius 3: "If there are 2 or more empty spawn points in radius in the battle zone, gain 2 coins"). Resolved T1 `dodger-gold`, T2 `dodger-red-1`, T3 `dodger-blue-1`. Hand `dodger-silver`.
  - Arien (Blue, L2) at (3,−5), already resolved this turn; hand none.
  - Wasp (Blue) at (4,−2), already resolved.
  - Minions: Blue melee (1,−4) **on a Blue melee spawn point**, Blue melee (2,−6) (no spawn point), Blue heavy (2,−7) (its spawn point). Red melee (0,−6), (1,−6) (on their spawn points), Red heavy (1,0).
  - Spawn points within 3 of Dodger: (2,−7), (0,−6), (1,−6), (1,−4) occupied; (0,−3) empty → only 1 empty.
- **Player controls:** Tigerclaw and Dodger. **Opponents:** none left to act.
- **Solution:** Hit and Run on the Blue melee at (1,−4) → defeated (+2). Do not use the optional after-attack step into (1,−4). Now (0,−3) and (1,−4) are empty → Darker Ritual +2. Total 4.
- **Wrong tries:** (1) Defeat Arien (no cards): +2, Dodger +1 assist, Ritual fails → 3. (2) Defeat the melee at (2,−6): +2, still one empty spawn point → 2. (3) Defeat the right minion and then step onto its spawn point: Ritual fails → 2.
- **Nudges:** (1) "Dodger's Ritual needs two empty spawn points near her; count them." (2) "A space is only empty if nothing is standing on it." (3) "Defeat the Blue minion standing on the spawn point at (1,−4)."
- **Explanation:** An empty spawn point is one in a space with no obstacle (p.17), and Dodger's card counts only those in the current BZ (Dodger FAQ). Minions are worth 2 coins, a level-2 hero 2 coins plus 1 assist coin (p.13). Tigerclaw's 9 resolves before Dodger's 3, so the spawn point is empty when the Ritual checks.
- **Rules:** p.7 initiative; p.13 coins; p.17 Empty.
- **Risk:** none.
- **Encoded as p13 (2026-10-24):** as written (Arien: level 2, `arien-red-2a` in the discard, item `arien-red-2b`). After the attack, staying put or stepping to (3,−6) or (1,−5) all win; only (1,−4) loses.

### C14 — Long Thrust
- **Theme:** range set by card text (enemy units adjacent to you). **Difficulty:** 1. **Heroes:** Xargatha; Tigerclaw, Arien (Wasp idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 3, turn 4, revealed; Xargatha acts next (9). Coin: Red. Life: Red 3, Blue 1. Waves 3. BZ `lane-1-2`.
  - Xargatha (Red, L2; item +1 initiative from `xargatha-red-2a`) at (1,−2). Played `xargatha-red-2b` Long Thrust (8+1 = 9; Attack 3, Ranged, Range 1, "+1 Range for each enemy unit adjacent to you"). Resolved T1 `xargatha-gold`, T2 `xargatha-silver`, T3 `xargatha-blue-1`. Hand `xargatha-green-1`.
  - Wasp (Red) at (3,−6), already resolved.
  - Tigerclaw (Blue, L1) at (1,−5) (d3). Played `tigerclaw-green-1` (2); discard 1; hand none.
  - Arien (Blue, L1) at (1,−1) (adjacent). Played `arien-green-1` (4). Hand `arien-blue-1` Aspiring Duelist (Defense 5, ignores minion modifiers).
  - Minions: Blue melee (2,−3) (adjacent to Xargatha), (3,−1); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Xargatha. **Opponents:** Arien (defence with Aspiring Duelist), Tigerclaw nothing.
- **Solution:** Two enemy units are adjacent (the Blue melee and Arien) → Range 1 + 2 = 3 → target Tigerclaw at d3 → no card → defeated → Blue's last counter → Red wins.
- **Wrong tries:** (1) Attack Arien: Aspiring Duelist 5 ≥ 3. (2) "Range 1 means adjacent only": the card text adds range (the "+" on the icon, p.9).
- **Nudges:** (1) "Your range isn't the number printed." (2) "Count the enemy units touching Xargatha." (3) "Shoot Tigerclaw — three spaces is in range."
- **Explanation:** The Range value on a card can be modified by its text (the "+" sign, p.9, p.13); Long Thrust grows by one for each enemy unit adjacent to Xargatha — minions and heroes alike (p.19 Unit). A hero with no cards in hand cannot defend (p.13).
- **Rules:** p.9 Range; p.13 Attacking a hero; p.19 Unit.
- **Risk:** none.
- **Encoded as p14 (changed, 2026-10-24):** Tigerclaw is not playable yet, and his part is only "a Blue hero with no cards three spaces away", so **Brogan** (Blue, L1, played `brogan-green-1` (6), resolved gold/red-1/blue-1, discard silver, hand none) stands at (1,−5) instead; Wasp (Red) stays idle. Same lesson, same numbers; the solver finds Brogan as the only winning target.

### C15 — Quickdraw
- **Theme:** card conditions ("played an attack card this turn"); defence totals with modifiers. **Difficulty:** 1. **Heroes:** Sabina; Arien, Wasp (Tigerclaw idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Sabina acts next (8). Coin: Blue. Life: Red 3, Blue 1. Waves 3. BZ `lane-1-2`.
  - Sabina (Red, L1) at (1,−5). Played `sabina-red-1` Quickdraw (8; Attack 2, Ranged, Range 2, "+3 Attack if the target played an attack card this turn"). Resolved T1 `sabina-gold`, T2 `sabina-silver`, T3 `sabina-blue-1`. Hand `sabina-green-1`.
  - Tigerclaw (Red) at (4,−2), already resolved.
  - Arien (Blue, L1) at (2,−5). **Played `arien-gold` (an attack card), already resolved** (he moved). Resolved T1 `arien-red-1`, T2 `arien-green-1`, T3 `arien-blue-1`. Hand `arien-silver` (Defense 3).
  - Wasp (Blue, L1) at (0,−4). Played `wasp-blue-1` (a skill card), already resolved. Resolved T1 `wasp-red-1`, T2 `wasp-silver`, T3 `wasp-green-1`. Hand `wasp-gold` (Defense 2).
  - Minions: Blue melee (3,−6) (adjacent to Arien), Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Sabina. **Opponents:** Arien's and Wasp's single cards (defence only).
- **Solution:** Quickdraw on Arien: 2 + 3 = 5 against 3 + 1 (adjacent friendly melee) = 4 → defeated → Blue's last counter → Red wins.
- **Wrong tries:** Quickdraw on Wasp "because her card is weaker": she played a skill, no bonus → 2 against 2 → she defends (ties go to the defender).
- **Nudges:** (1) "Your shot is much stronger against some targets." (2) "Who played an attack card this turn — even one already used?" (3) "Shoot Arien."
- **Explanation:** Quickdraw's bonus checks what the target *played* this turn, whatever action they took and even if the card is resolved (Sabina FAQ). Defence totals add minion modifiers (p.14), and equal totals defend (p.14).
- **Rules:** p.14 Defense total, Minion defense modifiers; FAQ Sabina.
- **Risk:** none.
- **Encoded as p15 (changed, 2026-10-24):** a position can't record that a hero *resolved* an attack card this turn (`PositionBuilder` marks "played an attack card" only for an unresolved played card). Arien has instead **played `arien-red-1` Dangerous Current (8), not yet resolved**, tied with Sabina at 8 with the **coin showing Red** (Sabina acts first, p.7), resolved gold, green-1, blue-1, hand `arien-silver`. The Sabina FAQ covers both cases. Wasp is seated before Arien.

### C16 — Get Over Here!
- **Theme:** moving an enemy hero; "no obstacles between" for a straight-line effect. **Difficulty:** 1. **Heroes:** Bain, Xargatha; Dodger, Wasp.
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, turn 4, revealed; Bain acts next (13). Coin: Blue. Life: Red 1, Blue 2. Waves 3. BZ `lane-1-2`.
  - Bain (Blue, L1) at (0,−4). Played `bain-silver` Get over here! (13; Range 4: "Target a unit or a token in range and in a straight line, with no obstacles between you and the target. Move the target towards you in a straight line, until you are adjacent"). Resolved T1–T3 `bain-gold`, `bain-red-1`, `bain-blue-1`. Hand `bain-green-1`.
  - Xargatha (Blue, L1) at (−1,−4). Played `xargatha-red-1` Threatening Slash (7). Resolved T1–T3; hand 1 card.
  - Dodger (Red, L1) at (0,−7) (d3, NW line, (0,−5) and (0,−6) empty). Played `dodger-green-1` (3); discard 1; hand none.
  - Wasp (Red, L1) at (3,−4) (d3, E line, but (2,−4) between is terrain). Played `wasp-green-1` (3); hand `wasp-blue-1`.
  - Minions: Red melee (1,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Bain and Xargatha. **Opponents:** Dodger nothing; Wasp `wasp-blue-1`.
- **Solution:** Get over here! on Dodger: she is pulled along the line to (0,−5), adjacent to Bain — and adjacent to Xargatha. Xargatha's Threatening Slash (7, before Dodger's 3) → no card → defeated → Red's last counter → Blue wins.
- **Wrong tries:** (1) Pull Wasp: terrain at (2,−4) lies between — not a legal target. (2) Xargatha moves toward Dodger instead: Threatening Slash has no move before its attack.
- **Nudges:** (1) "Xargatha can only hit what is next to her." (2) "Bain can bring a Red hero to her — check which line is clear." (3) "Pull Dodger down the NW line."
- **Explanation:** The pull is movement in the card text, so it follows movement rules and needs a clear path (p.11); the card also demands no obstacle between Bain and the target, and terrain is an obstacle (p.18, p.19). Bain at 13 acts long before Xargatha at 7 (p.7).
- **Rules:** p.7 initiative; p.11 Movement in the card text; p.18 Obstacle; p.19 Terrain, Straight line.
- **Risk:** none.

### C17 — Into the Wall
- **Theme:** pushes stopped by obstacles force a discard; stripping the last card. **Difficulty:** 1. **Heroes:** Wasp, Xargatha; Arien (Brogan idle).
- **Goal:** `DefeatHero(arien, turn)` — "Defeat Arien this turn."
- **Position:** round 3, turn 4, revealed; Wasp acts next (10). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Wasp (Red, L2; item +1 defence from `wasp-blue-2a`) at (4,−3). Played `wasp-blue-2b` Kinetic Repulse (10: "Push up to 2 enemy units adjacent to you 3 spaces; if a pushed hero is stopped by an obstacle, that hero discards a card, if able"). Resolved T1 `wasp-gold`, T2 `wasp-red-1`, T3 `wasp-green-1`. Hand `wasp-silver`.
  - Xargatha (Red, L1) at (1,−2). Played `xargatha-red-1` Threatening Slash (7). Resolved T1–T3; hand 1 card.
  - Arien (Blue, L2) at (3,−3) (adjacent to Wasp, d2 from Xargatha). Played `arien-green-1` (4). Resolved T1 `arien-gold`, T2 `arien-silver`, T3 `arien-red-1`. Hand `arien-blue-2a` Expert Duelist (Defense 6).
  - Brogan (Blue) at (0,0), already resolved.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7). Space (2,−3) (a Red melee spawn point) is empty; (1,−3) is terrain.
- **Player controls:** Wasp and Xargatha. **Opponent:** Arien (his discard choice is forced: one card).
- **Solution:** Kinetic Repulse on Arien: directly away from Wasp is W; he moves to (2,−3) and is stopped by the terrain at (1,−3) after 1 space → discards Expert Duelist. He is now adjacent to Xargatha with no cards; Threatening Slash (7, before his 4) defeats him.
- **Wrong tries:** (1) Without the push Xargatha cannot reach him (d2, no move on her card). (2) Leave the push and let Xargatha attack later: he keeps Expert Duelist (Defense 6 vs 5).
- **Nudges:** (1) "A push that runs into a wall costs the victim a card." (2) "Where does Arien end up if Wasp pushes him?" (3) "Kinetic Repulse on Arien."
- **Explanation:** A push moves the target directly away as far as it can, possibly less than the full distance (p.18); Kinetic Repulse then makes a hero stopped by an obstacle discard (Wasp FAQ: "cannot be pushed the full distance … forced to discard"). Terrain is an obstacle (p.19). The push also lands him next to Xargatha, who acts before him (p.7).
- **Rules:** p.7 initiative; p.18 Push; p.19 Terrain; FAQ Wasp (Kinetic Repulse).
- **Risk:** none.
- **Encoded as p17 (changed, 2026-10-24):** as written the puzzle is a click-through under the engine: Wasp's only real action is Kinetic Repulse, Arien the only adjacent enemy, Xargatha's only target Arien, so answering the first option at every decision wins. It now **starts at the play-a-card step** (turn 4, `PlayCards(fromSeat = 1)`): Arien is seated first and has already played `arien-green-1` (his kept card `arien-blue-2a` is known); **Wasp holds `wasp-red-1` Shock and `wasp-blue-2b` Kinetic Repulse** (resolved gold, silver, green-1), **Xargatha holds `xargatha-red-1` Threatening Slash and `xargatha-silver` Siren's Call** (resolved gold, green-1, blue-1); Brogan has no cards (passes). Solution: Kinetic Repulse + Threatening Slash, then the concept's line. New wrong tries: **Shock** (the obvious attack: Arien defends with Expert Duelist and loses it, but stays two spaces from Xargatha), **Repulse + Siren's Call** (Siren's Call at 3 comes after Arien's 4 and is no attack), Repulse then a Movement action. Same lesson (the push into the wall both strips the card and brings him into reach), now with a real choice; the solver proves only this pair of cards wins.

### C18 — Runecaster at Maximum Range
- **Theme:** "maximum range" targeting; After the attack on a neighbour. **Difficulty:** 1. **Heroes:** Snorri; Dodger, Wasp (Garrus idle).
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 2, turn 3, revealed; Snorri acts next (8). Coin: Blue. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Snorri (Blue, L2; item +1 initiative from `snorri-red-2a`) at (0,−4). Runes (from `snorri-silver`, resolved T1): T1 horn, T2 anvil, **T3 axe**, T4 bird. Played `snorri-red-2b` Runecaster (7+1 = 8; Attack 4, Ranged, Range 3, "Target a unit at maximum range. After the attack: … axe: An enemy hero who was adjacent to the target discards a card, or is defeated"). Resolved T1 `snorri-silver`, T2 `snorri-gold`. Hand `snorri-green-1`, `snorri-blue-1`.
  - Garrus (Blue) at (−1,−1), already resolved.
  - Dodger (Red, L1) at (2,−5) (d2). Played `dodger-green-1` (3); discard `dodger-silver`, `dodger-blue-1`; hand none.
  - Wasp (Red, L1) at (0,−7) (d3; not adjacent to Dodger). Played `wasp-green-1` (3); hand `wasp-blue-1`, `wasp-silver`.
  - Minions: Red melee (3,−5) (d3, adjacent to Dodger); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Snorri. **Opponents:** Wasp's two cards (defence only), Dodger nothing.
- **Solution:** Runecaster on the Red melee at (3,−5) — exactly 3 away → defeated. After the attack, the axe rune is the one under the turn-3 slot: Dodger was adjacent to the target → discards or is defeated → defeated.
- **Wrong tries:** (1) Shoot Dodger: d2 is not maximum range. (2) Shoot Wasp (d3): she defends; nobody stands next to her, so the axe text has no one to hit.
- **Nudges:** (1) "Your card can only hit things exactly three spaces away." (2) "Which rune is active this turn, and what does it do after the shot?" (3) "Shoot the minion beside Dodger."
- **Explanation:** "Maximum range" means exactly the Range value (p.9, and Snorri's bird rune is the exception that would lift it). "A rune is active as long as it is below the turn slot matching the current turn" (card text); on turn 3 that is the axe, whose After-the-attack text hits a hero who *was* adjacent to the target, so it still works after the minion is removed (p.17 After the attack).
- **Rules:** p.9 Range; p.14 order; p.17 After the attack.
- **Risk:** none.

### C19 — Smoke on the Line
- **Theme:** tokens that block straight-line targeting; "place" needs an empty space; protecting the last minion. **Difficulty:** 1. **Heroes:** Min; Swift, Wasp (Brogan idle).
- **Goal:** `Survive(turn)` — "Don't let the lane be pushed this turn."
- **Position:** round 5, turn 4, revealed; Min acts next (12). Coin: Blue. Life 2/2. **Wave counters 1.** BZ `lane-1-2`.
  - Min (Blue, L1) at (0,−2). Played `min-silver` Smoke Bomb (12; Radius 3: place the Smoke bomb in radius; "enemy heroes cannot target you or another unit if the Smoke bomb token is on a straight line between that enemy hero and their target"). Resolved T1–T3 `min-gold`, `min-red-1`, `min-blue-1`. Hand `min-green-1`.
  - Brogan (Blue) at (−2,1), already resolved.
  - Swift (Red, L1) at (5,−5). Played `swift-red-1` Snipe (8; Range 4, "at maximum range, and in a straight line").
  - Wasp (Red, L2; item +1 initiative from `wasp-red-2a`) at (1,−7). Played `wasp-red-2b` Charged Boomerang (9+1 = 10; Ranged, Range 3, "Target a unit in range and not in a straight line").
  - Minions: **Blue melee (1,−5) — Blue's only minion** (d4 W of Swift; d2 SE of Wasp). Red melee (2,−5) (on Swift's line), (0,−6); Red heavy (1,0).
- **Player controls:** Min. **Opponents:** Wasp (10) then Swift (8), each trying to defeat the Blue minion (a mid-turn push on the last Wave counter wins Red the game).
- **Solution:** Place the Smoke bomb at (3,−5): the only empty space strictly between Swift and the minion that is within Min's radius 3 ((2,−5) is occupied, (4,−5) is d4). Swift can no longer target the minion. Wasp's Boomerang cannot target it either: it stands in a straight line from her. Neither can Clear the token (it is adjacent to neither).
- **Wrong tries:** (1) Smoke at (4,−5): out of radius. (2) At (2,−5): occupied — a piece can only be placed in an empty space (p.18 Place). (3) Anywhere off the line, or trying to stop Wasp with smoke: her card needs a target *not* in a straight line, which the minion already isn't.
- **Nudges:** (1) "Who can actually shoot your last minion?" (2) "The smoke only matters on the line between shooter and target." (3) "Put the Smoke bomb at (3,−5)."
- **Explanation:** The Smoke bomb blocks targeting along a straight line through its space (card text); a placement needs an empty space within the card's radius (p.18 Place, p.9). Wasp's Charged Boomerang can only target units not in a straight line from her (card text, p.19 Straight line), so the minion is already safe from her. If Blue's last minion fell, the Lane would push at once and the last Wave counter would end the game (p.8).
- **Rules:** p.8 Triggering a Push; p.9 Radius; p.18 Place; p.19 Straight line, Tokens.
- **Risk:** none.
- **Encoded as p19 (2026-10-24):** as in the concept (Swift hand `swift-blue-1`, Wasp hand `wasp-blue-1`). (3,−5) is the only winning space of 29. Wrong try (1) of the concept is not testable ((4,−5) is not offered: out of radius); the tested tries are smoke at (0,−5) (the first option) and Hold.

### C20 — Clear the Smoke
- **Theme:** the Clear action (replaces an Attack); tokens on the line. **Difficulty:** 1. **Heroes:** Brogan, Swift; Wuk (Min idle).
- **Goal:** `DefeatHero(wuk, turn)` — "Defeat Wuk this turn."
- **Position:** round 3, turn 4, revealed; Brogan acts next (11). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Brogan (Red, L1) at (3,−6). Played `brogan-gold` Onslaught (11). Resolved T1–T3 `brogan-red-1`, `brogan-green-1`, `brogan-blue-1`. Hand `brogan-silver`.
  - Swift (Red, L1) at (5,−5). Played `swift-red-1` Snipe (8; Range 4, maximum range, straight line). Hand 1 card.
  - Wuk (Blue, L1) at (1,−5) (d4 W of Swift). Played `wuk-green-1` (6); discard 1; hand none.
  - Min (Blue, L1) at (0,−2): her `min-silver` (12) is already resolved — **Smoke bomb token at (3,−5)**, between Swift and Wuk, adjacent to Brogan.
  - Minions: Blue melee (2,−6) (adjacent to Brogan); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Brogan and Swift. **Opponent:** Wuk acts at 6, after Swift.
- **Solution:** Brogan performs **Clear** instead of Onslaught's attack: remove the Smoke bomb adjacent to him. Swift's Snipe now targets Wuk (d4, straight line) → no card → defeated.
- **Wrong tries:** (1) Onslaught on the Blue melee (2 coins): Brogan moves into (2,−6); the smoke stays and Swift cannot target Wuk. (2) Snipe a different target: nothing else at d4 matters.
- **Nudges:** (1) "Swift's shot is blocked — by what?" (2) "Every card with an Attack action can do something besides attacking." (3) "Clear the Smoke bomb with Brogan."
- **Explanation:** You may perform one Clear secondary action instead of your entire Attack action, removing any number of tokens adjacent to you (p.13; p.19 Tokens). With the Smoke bomb gone nothing stops Swift targeting along the line. Brogan's 11 acts before Swift's 8 (p.7).
- **Rules:** p.7 initiative; p.13 Clear action; p.19 Tokens.
- **Risk:** none.
- **Encoded as p20 (2026-10-24):** as in the concept (Swift hand `swift-blue-1`, Wuk resolved gold/silver/red-1, discard blue-1; Min resolved all four). Clear asks which adjacent token to remove (with "Stop clearing"); the stored line is Clear, the Smoke bomb, Snipe.

---

## Difficulty 2 — harder

### C21 — Charge onto the Spawn Point
- **Theme:** mandatory straight-line move before an attack; a defender's card text depends on the board. **Difficulty:** 2. **Heroes:** Brogan; Dodger (Arien, Xargatha idle).
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 2, turn 4, revealed; Brogan acts next (7). Coin: Blue. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Brogan (Blue, L1) at (1,−2). Played `brogan-red-1` Mad Dash (7; Attack 6: "Before the attack: Move 2 spaces in a straight line to a space adjacent to an enemy unit, then target that unit"). Resolved T1 `brogan-gold`, T2 `brogan-blue-1`, T3 `brogan-silver`. Hand `brogan-green-1`.
  - Arien (Blue) at (−1,1), already resolved. Xargatha (Red) at (5,−4), already resolved.
  - Dodger (Red, L1) at (3,−3). Played `dodger-green-1` (3). Resolved T1 `dodger-gold`, T2 `dodger-red-1`, T3 `dodger-silver`. **Hand `dodger-blue-1` Shield of Decay** (Defense 3, primary: "+2 Defense if there are 2 or more empty spawn points in radius [2] in the battle zone").
  - Minions: Red melee (4,−4) (adjacent to Dodger), Red ranged (0,−3), Red heavy (1,0), Red melee (0,−6). Blue melee (2,−1), (3,−1), (5,−8); Blue heavy (2,−7). (Blue's ranged minion is gone.)
  - Spawn points within 2 of Dodger: (2,−3) Red melee and (3,−4) Blue ranged are **empty**; (2,−1), (3,−1), (1,0) are occupied.
- **Player controls:** Brogan. **Opponent:** Dodger defends with Shield of Decay (or not).
- **Solution:** Mad Dash NE: (2,−3) then (3,−4) — landing **on the empty Blue ranged spawn point** next to Dodger. Now only (2,−3) is an empty spawn point within 2 of her: no +2. Defence 3 + 1 (adjacent Red melee) = 4 < 6 → defeated.
- **Wrong tries:** (1) Dash E via (2,−2) to (3,−2): two empty spawn points remain → 3 + 2 + 1 = 6 ≥ 6, she defends. (2) Dash W to (−1,−2): adjacent only to the Red ranged minion — kills it (2 coins). (3) Dash SW to (−1,0): adjacent to no enemy unit, the move is illegal and the attack never happens (Brogan FAQ "fizzle"). (4) Secondary Movement 3: no attack.
- **Nudges:** (1) "Count the empty spawn points around Dodger." (2) "Brogan's own body can fill one of them." (3) "Mad Dash north-east onto the spawn point at (3,−4)."
- **Explanation:** Mad Dash's move is mandatory and exact (p.11 Movement in the card text; Brogan FAQ), so only two landing squares next to Dodger exist. Shield of Decay is a primary Defense, so its text applies when she defends (p.14); "empty" means no obstacle in the space, and a hero is an obstacle (p.17, p.18) — standing on a spawn point switches the bonus off. Equal totals defend (p.14), so the +2 decides it.
- **Rules:** p.11 Movement in the card text; p.14 Defense action, Defense total, Minion modifiers; p.17 Empty; p.18 Obstacle.
- **Risk:** none (Dodger FAQ: minion spawn points of either colour in the current BZ count).

### C22 — Change of Plans
- **Theme:** initiative is re-checked after every resolution; a card swap moves a teammate up the order. **Difficulty:** 2. **Heroes:** Takahide, Brogan; Dodger (Wasp idle).
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 2, turn 3, revealed; Takahide acts next (11). Coin: Red. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Takahide (Blue, L1) at (2,−5). Played `takahide-red-1` Set an Example (11; Attack 2, Radius 3: "After the attack: A friendly hero in radius may swap their unresolved card with a card in their hand"). Resolved T1 `takahide-gold`, T2 `takahide-green-1`. Hand `takahide-silver`, `takahide-blue-1`.
  - Brogan (Blue, L1) at (0,−4) (d2 from Takahide). Played `brogan-green-1` Shield (6). Resolved T1 `brogan-silver`, T2 `brogan-blue-1`. **Hand `brogan-gold` Onslaught (11), `brogan-red-1` Mad Dash (7).**
  - Dodger (Red, L1) at (−1,−4) (adjacent to Brogan, d3 from Takahide). Played `dodger-red-1` Littlefinger of Death (9). Resolved T1 `dodger-gold`, T2 `dodger-green-1`. Discard `dodger-silver`, `dodger-blue-1`. Hand none.
  - Wasp (Red) at (4,−2), already resolved.
  - Minions: Red melee (3,−5) (adjacent to Takahide); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Takahide and Brogan. **Opponent:** Dodger (9) — she will run (Movement 4) or attack once she acts.
- **Solution:** Set an Example on the Red melee (defeated). After the attack Brogan swaps his unresolved Shield for **Onslaught** from his hand. Onslaught (11) is now the highest unresolved card, above Dodger's 9 → Brogan acts at once: attacks the adjacent Dodger → no cards → defeated (then moves into her space).
- **Wrong tries:** (1) Swap in Mad Dash (7): Dodger acts first at 9 and leaves; besides, Mad Dash cannot hit an adjacent hero without first moving 2. (2) No swap: Shield at 6, Dodger escapes. (3) Takahide attacks Dodger: not adjacent (d3).
- **Nudges:** (1) "Dodger will move at 9 — can anyone on Blue act before that?" (2) "Set an Example lets a friend change the card he played." (3) "After the attack, swap Brogan's Shield for Onslaught."
- **Explanation:** After each card resolves, the next actor is whoever holds the highest initiative on an unresolved card (p.7), checked again every time. Swapping a played card swaps all its states (p.10), so Onslaught becomes Brogan's played, unresolved card at 11 and jumps ahead of Dodger.
- **Rules:** p.7 Resolve your card; p.10 Swap a card; p.13 Attacking a hero.
- **Risk:** none.

### C23 — Lift Up into the Line
- **Theme:** moving an enemy hero without changing its distance; exact maximum range. **Difficulty:** 2. **Heroes:** Wasp, Swift; Dodger (Brogan idle).
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 3, turn 4, revealed; Wasp acts next (10). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Wasp (Red, L1) at (2,−6). Played `wasp-blue-1` Lift Up (10; Radius 2: "Move a unit, or a token, in radius 1 space, without moving it away from you or closer to you. May repeat once on the same target."). Resolved T1–T3 `wasp-gold`, `wasp-red-1`, `wasp-silver`. Hand `wasp-green-1`.
  - Swift (Red, L1) at (4,−4). Played `swift-red-1` Snipe (8; Range 4, maximum range, straight line). Hand 1 card.
  - Dodger (Blue, L1) at (4,−6) (d2 from Wasp; d2 from Swift). Played `dodger-green-1` (3); discard 1; hand none.
  - Brogan (Blue) at (−1,0), already resolved.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1), (1,−4); Blue heavy (2,−7). (4,−7) and (4,−8) are empty.
- **Player controls:** Wasp and Swift. **Opponent:** Dodger (no card, acts last).
- **Solution:** Lift Up on Dodger: (4,−6) → (4,−7) (still d2 from Wasp), repeat once on the same target → (4,−8) (d2). Swift's Snipe: (4,−4) to (4,−8) is 4 spaces NW in a straight line → Dodger cannot defend → defeated.
- **Wrong tries:** (1) One step only: (4,−7) is d3 from Swift — not maximum range. (2) Lift Up on Swift: she is d4 from Wasp, out of radius 2. (3) Any other ring step: from (4,−6) the only same-distance step is (4,−7).
- **Nudges:** (1) "Snipe only hits targets exactly four spaces away in a line." (2) "Lift Up slides a unit around Wasp — and can do it twice." (3) "Move Dodger two steps north along Wasp's ring."
- **Explanation:** Lift Up moves the unit 1 space without changing its distance to Wasp, and its repeat must use the same target (card text, Wasp FAQ). Snipe needs a target at maximum range and in a straight line (p.9, p.19). Wasp at 10 acts before Swift at 8, who acts before Dodger at 3 (p.7).
- **Rules:** p.7 initiative; p.9 Range; p.10 Repeat; p.11 Movement in the card text; p.19 Straight line.
- **Risk:** none.
- **Encoded as p23 (changed, 2026-10-24):** the concept's "the only same-distance step is (4,−7)" is wrong: (3,−5) is also 2 from Wasp (a dead end: its only same-distance step leads back). With the concept's coordinates the engine lists the right step first, so the first option at every decision won; the position is turned half a turn ((q,r) → (3−q, −7−r)) with the teams swapped (Wasp and Swift Blue at (1,−1) and (−1,−3), Dodger Red at (−1,−1), Brogan Red idle at (4,−7)), so the dead-end step (0,−2) comes first and the winning line is (−1,0) then (−1,1). Both wrong tries (the dead end; one step only) are refuted.

### C24 — Reverse Time
- **Theme:** an initiative-reversing "Next turn" effect; choosing your card when the enemy's card is forced. **Difficulty:** 2. **Heroes:** Wuk, Emmitt; Tigerclaw, Dodger.
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 3, **turn 4, play-a-card step** (cards not yet chosen). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`. **Active: Emmitt's `emmitt-gold` Reverse Time, resolved in turn 3 — "Next turn: Heroes with lower initiative act before heroes with higher initiative; this effect ignores immunity" (ties resolve as normal).**
  - Wuk (Blue, L1) at (0,−4). Resolved T1 `wuk-gold`, T2 `wuk-silver`, T3 `wuk-green-1`. **Hand `wuk-red-1` Nature's Protector (7; Ranged, Range 2: "Target a hero adjacent to you" or "Target a unit in range adjacent to a Tree token"), `wuk-blue-1` Gifts of Nature (8).**
  - Emmitt (Blue, L1) at (−1,−2). Resolved T1 `emmitt-red-1`, T2 `emmitt-blue-1`, T3 `emmitt-gold`. Hand `emmitt-silver` (1), `emmitt-green-1` (6).
  - Tigerclaw (Red, L1) at (2,−5) (d2 from Wuk; adjacent to the Tree). Resolved T1–T3; discard 1. **Hand: `tigerclaw-gold` Blink Strike (13) only → forced.**
  - Dodger (Red, L1) at (4,−2). Resolved T1–T3; discard 1. **Hand: `dodger-green-1` Dark Ritual (3) only → forced.**
  - Tokens: Tree (Wuk's) at (2,−6).
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Wuk and Emmitt (both choose a card, then act). **Opponents:** forced cards; Dodger acts first of all, Tigerclaw last.
- **Solution:** Wuk plays Nature's Protector (7); Emmitt plays either card. Order is now low-to-high: Emmitt's silver (1) if chosen, Dodger (3), Emmitt's Time Walk (6) if chosen, Wuk (7), Tigerclaw (13) last. Wuk targets Tigerclaw — a unit in range 2 adjacent to the Tree → he has no card → defeated before his Blink Strike.
- **Wrong tries:** (1) Wuk plays Gifts of Nature: no attack. (2) "Tigerclaw at 13 acts first and escapes": not this turn — the order is reversed. (3) Emmitt's Time Walk on Tigerclaw before Wuk acts: it moves him 2 spaces in a straight line, possibly away from the Tree — don't.
- **Nudges:** (1) "Who acts first this turn — really?" (2) "Tigerclaw's 13 is the slowest card on the board this turn." (3) "Play Nature's Protector with Wuk."
- **Explanation:** A "Next turn" effect is in force for that entire turn (p.18); Reverse Time flips the initiative order, ties still going by the usual rules (card text, p.7). Tigerclaw has a single card left, so his play is forced and he will have no card to defend with (p.7 "You must play a card, if able"; p.13). Nature's Protector's second bullet needs only a unit in range next to a Tree token (card text).
- **Rules:** p.7 Play one card, Check the initiative; p.13 Attacking a hero; p.18 Next Turn.
- **Risk:** none (the starting phase "play a card" must be supported by the puzzle builder).

### C25 — Cold Snap
- **Theme:** minion defence modifiers; moving every enemy unit at once; blocked moves. **Difficulty:** 2. **Heroes:** Tali, Brogan; Wasp (Dodger idle).
- **Goal:** `DefeatHero(wasp, turn)` — "Defeat Wasp this turn."
- **Position:** round 3, turn 4, revealed; Tali acts next (9). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Tali (Blue, L1) at (3,−5). Played `tali-blue-1` Cold Snap (9; Radius 2: "Choose a direction. Move each enemy unit in radius 1 space in that direction, if able."). Resolved T1–T3 `tali-gold`, `tali-red-1`, `tali-green-1`. Hand `tali-silver`.
  - Brogan (Blue, L1) at (2,−6). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Wasp (Red, L1) at (5,−7). Played `wasp-green-1` (3). **Hand `wasp-blue-1`** (Defense 5).
  - Dodger (Red) at (0,−2), already resolved.
  - Minions: **Red melee (4,−6)** (adjacent to Wasp and Tali); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7). The only Red units within 2 of Tali are Wasp and that melee.
- **Player controls:** Tali and Brogan. **Opponent:** Wasp defends with Defense 5.
- **Solution:** Cold Snap **SE**: Wasp's SE space (5,−6) is terrain, so she stays; the melee moves to (4,−5). Wasp has no adjacent friendly minion now: 5. Brogan's Mad Dash E via (3,−6) to (4,−6) — free now — lands next to her: 6 > 5 → defeated.
- **Wrong tries:** (1) No Cold Snap: Brogan has no 2-space run to her ((4,−6) is occupied) and she would have 5 + 1 = 6. (2) E: Wasp slides to (6,−7) (5 Defense) but Brogan can no longer reach her. (3) NW or W: both move together and stay adjacent (6), Brogan reaches but 6 ≥ 6. (4) NE or SW: whatever the order of moving, she keeps the minion or ends out of Brogan's reach.
- **Nudges:** (1) "Wasp is guarded — by what?" (2) "Cold Snap moves all of them the same way — unless one of them can't move." (3) "Cold Snap south-east."
- **Explanation:** An adjacent friendly melee minion gives +1 Defense (p.14) and equal totals defend. Cold Snap moves each enemy unit 1 space "if able" — a unit that would enter terrain simply stays (p.17 If able, p.19 Terrain), which splits Wasp from her guard and opens Brogan's straight run (p.11).
- **Rules:** p.11 Movement in the card text; p.14 Minion defense modifiers; p.17 If able; p.19 Terrain.
- **Risk:** `RISKY` (low): the order in which Cold Snap moves several units is not specified. The intended direction (SE) gives the same result in every order; the alternative where order matters (NE) fails in every order too.
- **Encoded as p25 (2026-10-24):** as in the concept. South-east is the only winning direction (engine order of the Cold Snap moves: units blocked by another moving enemy still move, rulings/tali.md 4); east, north-east, north-west, west and a Movement action are refuted.

### C26 — Blood Fury to the Throne
- **Theme:** pushing the lane mid-turn into the enemy Throne; heavy immunity; a repeat that depends on the enemy's discard. **Difficulty:** 2. **Heroes:** Whisper; Xargatha, Arien (Tigerclaw idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Whisper acts next (9). Coin: Red. Life: Red 2, Blue 3. **Wave counters 2. BZ `lane-1-3`** (Red pushed once).
  - Whisper (Red, L2; item +1 defence from `whisper-red-2a`) at (8,−7). Played `whisper-red-2b` Blood Fury (9; Attack 6, Radius 5: "Target a unit adjacent to you. After the attack: If an enemy hero in radius has a card in the discard, may repeat once on a different target"). Resolved T1–T3; hand `whisper-silver`.
  - Tigerclaw (Red) at (5,−4), already resolved.
  - Xargatha (Blue, L1) at (7,−7) (adjacent to Whisper). Played `xargatha-green-1` (5); discard `xargatha-silver`; hand none.
  - Arien (Blue, L1) at (6,−5) (d2). Played `arien-green-1` (4); discard `arien-silver`; hand none.
  - Minions (BZ `lane-1-3`): **Blue melee (8,−6) and Blue heavy (9,−8)** — Blue's only minions, both adjacent to Whisper. Red melee (7,−10); Red ranged (6,−7).
- **Player controls:** Whisper. **Opponents:** defence only (none possible).
- **Solution:** Blood Fury on the Blue melee → defeated. After the attack an enemy hero in radius 5 has a card in the discard (Arien, also Xargatha) → repeat on the Blue heavy, no longer Immune → defeated → Blue has no minion in the BZ → push: a Wave counter flips (2 → 1, not the last), and the next Zone toward Blue's Throne is `blue-throne` itself → Red wins.
- **Wrong tries:** (1) Heavy first: Immune. (2) Xargatha first (no cards, adjacent): she falls (1 counter), the repeat takes the melee, the heavy stays — no push, no win.
- **Nudges:** (1) "Red is one Zone from Blue's Throne." (2) "The heavy can't be hit while its friend stands — so order your two hits." (3) "Blood Fury the Blue melee, then repeat on the heavy."
- **Explanation:** Defeating the last enemy minion in the BZ pushes the Lane at once (p.8); if the enemy Throne Zone becomes the new BZ, the team that won the push wins (p.8 step 2). The heavy minion is Immune while a friendly non-heavy minion remains (p.13). Blood Fury's repeat needs an enemy hero in radius with a discarded card (card text).
- **Rules:** p.8 Push the Lane, Triggering a Push; p.10 Repeat; p.13 Heavy Minion Immunity.
- **Risk:** none.

### C27 — Rain of Arrows
- **Theme:** items change "maximum range"; mandatory repeat on a second hero. **Difficulty:** 2. **Heroes:** Silverarrow; Dodger, Tigerclaw, Xargatha (Wuk idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 6, turn 4, revealed; Silverarrow acts next (10). Coin: Blue. Life: Red 2, Blue 2. Waves 3. BZ `lane-1-2`. *(Three Red heroes: this concept uses a 3v3 table with 5 Life counters per team at the start, p.5.)*
  - Silverarrow (Blue, L6; items **+1 range** from `silverarrow-blue-3b`, +1 attack from `silverarrow-green-2b`, +2 defence from `silverarrow-red-2a` and `silverarrow-blue-2a`, +1 radius from `silverarrow-red-3a`) at (1,−5). Played `silverarrow-red-3b` Rain of Arrows (10; Attack 3+1, Ranged, Range 3+1 = **4**: "Target a unit at maximum range. If you target a hero, repeat once on a different hero; if you do, may repeat once on a minion"). Resolved T1–T3 `silverarrow-gold`, `silverarrow-silver`, `silverarrow-green-2a`. Hand `silverarrow-blue-3a`.
  - Wuk (Blue) at (−1,−3), already resolved (third Blue hero: Arien at (−1,1), resolved).
  - Dodger (Red, L1) at (1,−1) (d4). Tigerclaw (Red, L1) at (5,−7) (d4). Xargatha (Red, L1) at (4,−5) (d3). Each: played a green card (≤ 5), discard 1, hand none.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Silverarrow. **Opponents:** no cards.
- **Solution:** Target Dodger at d4 (maximum range with the item) → defeated; repeat on a different hero at maximum range → Tigerclaw (d4) → defeated → Red spends its last 2 counters → Blue wins.
- **Wrong tries:** Target Xargatha (d3, "the printed range"): not at maximum range — illegal; reading range 3, the only targets would be Xargatha and nothing else at 3, so the repeat would fail.
- **Nudges:** (1) "What is your real Range this turn?" (2) "Maximum range moves out when you carry a Range item." (3) "Shoot Dodger, then Tigerclaw — both four spaces away."
- **Explanation:** Each item adds +1 to the matching icon on your cards; a Range item raises the Range icon of ranged cards (p.16), so "maximum range" is 4 here. Rain of Arrows' repeat on a different hero is part of the same action (p.10 Repeat). Two tier-I heroes defeated cost Red two counters (p.13).
- **Rules:** p.9 Range; p.10 Repeat; p.13 When a hero is defeated; p.16 Item bonus.
- **Risk:** none.

### C28 — Angry Stampede
- **Theme:** straight-line movement through units; Last Push; the right line among several. **Difficulty:** 2. **Heroes:** Wuk; Dodger (Xargatha, Garrus idle).
- **Goal:** `PushLane(turn)` — "Push the lane this turn." (With one Wave counter left the push also wins.)
- **Position:** round 5, turn 4, revealed; Wuk acts next (9). Coin: Blue. Life: Red 2, Blue 2. **Wave counters 1.** BZ `lane-1-2`.
  - Wuk (Blue, L5; items +1 initiative from `wuk-red-3a`, +1 defence from `wuk-red-2b`, +2 attack from `wuk-green-2a`, `wuk-blue-2a`) at (0,−2). Played `wuk-red-3b` Angry Stampede (8+1 = 9; Movement 4: "If you move in a straight line: You may ignore obstacles; each enemy hero you moved through discards a card, or is defeated; defeat up to two minions you moved through"). Resolved T1–T3; hand `wuk-silver`.
  - Garrus (Blue) at (−1,−4), already resolved. Xargatha (Red) at (3,−6), already resolved.
  - Dodger (Red, L1) at (−1,−1) (adjacent, SW). Played `dodger-green-1` (3); discard 1; hand none.
  - Minions: **Red melee (1,−2) and Red ranged (3,−2)** — Red's only minions (the heavy fell earlier). Blue melee (1,−4); Blue heavy (2,−7). (2,−2) and (4,−2) are empty.
- **Player controls:** Wuk. **Opponents:** none.
- **Solution:** Move 4 spaces E in a straight line: through (1,−2) (Red melee), (2,−2), (3,−2) (Red ranged), ending on (4,−2). Defeat both minions → Red has none left → push → the last Wave counter flips → Blue wins.
- **Wrong tries:** (1) SW through Dodger: she is defeated (1 counter, Red keeps 1), no minion touched. (2) E only 2 or 3 spaces: ends on (2,−2)/through one minion, or on the ranged minion's space (illegal end). (3) Moving not in a straight line: the card text does nothing.
- **Nudges:** (1) "One Wave counter left, and Red has exactly two minions." (2) "Find a single straight line through both of them." (3) "Stampede four spaces east."
- **Explanation:** Moving "through" units is possible only while ignoring obstacles, and the move must end on an empty space (p.18 Ignoring Obstacles). Angry Stampede defeats up to two minions you passed — both at once, so the heavy-immunity trap does not arise here (Wuk FAQ). The last enemy minion defeated pushes the Lane and the final Wave counter ends the game (p.8, p.3).
- **Rules:** p.3 Last Push; p.8 Triggering a Push; p.18 Ignoring Obstacles; p.19 Straight line.
- **Risk:** none.

### C29 — Threaten the Guards
- **Theme:** minion defence modifiers; repeat on a different target; "farther away" moves. **Difficulty:** 2. **Heroes:** Garrus, Brogan; Wasp.
- **Goal:** `DefeatHero(wasp, turn)` — "Defeat Wasp this turn."
- **Position:** round 3, turn 4, revealed; Garrus acts next (10). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Garrus (Blue, L2; item +1 attack from `garrus-blue-2b`) at (3,−7). Played `garrus-blue-2a` Threaten (10; Range 2: "Move an enemy unit in range 1 space to a space farther away from you. May repeat once."). Resolved T1–T3 `garrus-gold`, `garrus-red-1`, `garrus-silver`. Hand `garrus-green-1`.
  - Brogan (Blue, L1) at (0,−4). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Wasp (Red, L1) at (2,−5). Played `wasp-green-1` (3). **Hand `wasp-blue-1`** (Defense 5).
  - Minions: **Red melee (3,−5) and (3,−6)**, both adjacent to Wasp and within 2 of Garrus; Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
  - Wasp now defends at 5 + 2 = 7 against Brogan's 6.
- **Player controls:** Garrus and Brogan. **Opponent:** Wasp (defence).
- **Solution:** Threaten the melee at (3,−5) to (4,−5) (or (3,−4)), repeat on the melee at (3,−6) to (4,−6) — both still in the BZ, neither adjacent to Wasp. Brogan's Mad Dash NE via (1,−5) to (2,−6) → 6 > 5 → defeated.
- **Wrong tries:** (1) Move one guard only: 6 ≥ 6. (2) Move Wasp: her only "farther" space is (1,−4), which leaves her guardless but out of every Mad Dash line. (3) Move the second melee into (3,−5) after the first has left: still adjacent.
- **Nudges:** (1) "Wasp's two guards are worth +2." (2) "Threaten can repeat — on a different minion." (3) "Push both melee minions one space away from Garrus."
- **Explanation:** Each adjacent friendly melee minion adds +1 Defense (p.14). A repeat performs the whole action again with the same restrictions but a new choice of target unless the card says otherwise (p.10). "Farther" is measured by spaces from Garrus (p.17), and moved minions must stay in the BZ or they walk straight back (p.18).
- **Rules:** p.10 Repeat; p.14 Minion defense modifiers; p.17 Farther; p.18 Minions Outside of the Battle Zone.
- **Risk:** `RISKY` (low): the repeat choosing a different unit relies on p.10's general rule (cards that require the same target say so, e.g. Lift Up).

### C30 — Three Obstacles
- **Theme:** "adjacent to 3 or more obstacles" (units, terrain, water); a teammate creates the condition. **Difficulty:** 2. **Heroes:** Tigerclaw, Brynn; Dodger.
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 3, turn 4, revealed; Tigerclaw acts next (13), then Brynn (12). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Tigerclaw (Blue, L1) at (2,−6). Played `tigerclaw-gold` Blink Strike (13: "Before the attack: Move 2 spaces in a straight line through an enemy unit; target that unit"). Hand 1 card.
  - Brynn (Blue, L1) at (2,−8). Played `brynn-gold` Familiar Ground (12; Attack 2, Ranged, Range 3: "Target a unit adjacent to you" or "Target a hero in range who is adjacent to 3 or more obstacles"). Hand 1 card.
  - Dodger (Red, L1) at (4,−9) (d2 from Brynn). Played `dodger-green-1` (3); discard 1; hand none. Her neighbours: (5,−10) water (terrain), (3,−9) Red melee, and four empty spaces → **2 obstacles**.
  - Red's other hero: Wasp at (0,0), already resolved.
  - Minions: Red melee (3,−9), (3,−7) (adjacent to Tigerclaw, not to Dodger), (1,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (5,−8).
- **Player controls:** Tigerclaw and Brynn. **Opponent:** Dodger (no card).
- **Solution:** Blink Strike NE through the Red melee at (3,−7), landing on (4,−8) — adjacent to Dodger — and defeat that minion. Dodger now touches the water, the melee at (3,−9) and Tigerclaw: 3 obstacles. Familiar Ground's second bullet targets her (d2) → defeated.
- **Wrong tries:** (1) Blink through the melee at (1,−6) (landing (0,−6)): coins, but Dodger still has 2 obstacles. (2) Brynn first-bullet on the adjacent melee: no use. (3) Killing the melee at (3,−9) would *remove* one of her obstacles.
- **Nudges:** (1) "Brynn can shoot far only at a hero hemmed in by three obstacles." (2) "Water counts, and so does your own hero." (3) "Blink over the minion at (3,−7) to land next to Dodger."
- **Explanation:** Units, terrain and the water around the board are all obstacles (p.18, p.19; Brynn's reminder text "You, other heroes, minions, tokens, and terrain"). Blink Strike's jump must be exactly 2 spaces through an enemy unit (Tigerclaw FAQ), and Tigerclaw's 13 resolves before Brynn's 12 (p.7).
- **Rules:** p.7 initiative; p.11 Movement in the card text; p.18 Obstacle; p.19 Terrain.
- **Risk:** none.

### C31 — Knife of the Living Dead
- **Theme:** token arithmetic with a limited number of choices. **Difficulty:** 2. **Heroes:** Mortimer; Wasp (Sabina, Brogan idle).
- **Goal:** `DefeatHero(wasp, turn)` — "Defeat Wasp this turn."
- **Position:** round 3, turn 4, revealed; Mortimer acts next (11). Coin: Blue. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Mortimer (Blue, L2; item +1 attack from `mortimer-green-2a`) at (1,−5). Played `mortimer-gold` Knife of the Living Dead (11; Attack 4+1 = 5, Radius 4: "Target a unit adjacent to you. Before the attack: Choose up to three times — • Move a Zombie token in radius 1 space. • Remove a Zombie token adjacent to the target; if you do, gain +1 Attack."). Resolved T1–T3 `mortimer-silver`, `mortimer-red-1`, `mortimer-blue-1`. Hand `mortimer-green-2b`.
  - Sabina (Blue) at (−1,−3), already resolved.
  - Wasp (Red, L1) at (2,−5) (adjacent). Played `wasp-green-1` (3). **Hand `wasp-blue-1`** (Defense 5).
  - Brogan (Red) at (4,−2), already resolved.
  - Tokens (Zombies, from `mortimer-silver`): Z1 at (2,−6) (adjacent to Wasp), Z2 at (4,−5) (d2 from Wasp; its step to (3,−5) is free).
  - Minions: Red melee (3,−6) (adjacent to Wasp); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
  - Wasp defends at 5 + 1 = 6; Mortimer needs 7.
- **Player controls:** Mortimer. **Opponent:** Wasp (defence).
- **Solution:** Choice 1: remove Z1 (+1). Choice 2: move Z2 from (4,−5) to (3,−5), now adjacent to Wasp. Choice 3: remove Z2 (+1). Attack 7 > 6 → defeated.
- **Wrong tries:** (1) Remove Z1 only: 6 ≥ 6. (2) Move Z1 or move Z2 twice: wasted choices. (3) Target the Red melee: no.
- **Nudges:** (1) "You need two more Attack; each removed Zombie gives one." (2) "A Zombie must be next to the target when you remove it — you may move one there first." (3) "Remove Z1, move Z2 next to Wasp, remove it."
- **Explanation:** "Before the attack" choices happen before the defence is calculated (p.14 order), and "Choose up to three times" lets you mix bullets in any order (p.10 Modal actions). Each removal needs the token adjacent to the target at that moment. Wasp's adjacent friendly melee gives her +1 (p.14), and equal totals defend.
- **Rules:** p.10 Modal actions; p.14 order, Defense total, Minion defense modifiers; p.19 Tokens.
- **Risk:** none.
- **Encoded as p31 (2026-10-24):** as in the concept. The solver also accepts the other orders of the
  same three choices (remove Z1 first, or move Z2 and remove it before Z1); the wrong tries tested are
  "remove Z1 and stop", "move Z2 twice" and "move Z2 to (4,−6)".

### C32 — Fight as One
- **Theme:** reusing a resolved skill; range measured from a token (Pyro). **Difficulty:** 2. **Heroes:** Widget; Dodger, Wasp (Garrus idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Widget acts next (11). Coin: Red. Life: Red 1, Blue 2. Waves 3. BZ `lane-1-2`.
  - Widget (Blue, L5; items +1 initiative from `widget-red-2a`, +1 attack from `widget-green-2a`, +1 defence from `widget-blue-2a`, +1 movement from `widget-green-3b`) at (1,−5). Played `widget-gold` Fight as One (11+1 = 12; Attack 4: "Target a unit adjacent to you. After the attack: You may perform the primary action on one of your resolved skill cards, targeting a different unit."). Resolved T1 `widget-silver` (placed Pyro), **T2 `widget-green-3a` Scorching Breath** (Range 3: "An enemy hero in range of Pyro and in a straight line from Pyro discards a card, or is defeated"), T3 `widget-red-2b`. Hand `widget-blue-2b`.
  - Garrus (Blue) at (−1,−2), already resolved.
  - Pyro (counts as a token) at (3,−5).
  - Dodger (Red, L1) at (3,−8): d3 NW of Pyro, straight line. Played `dodger-green-1` (3); discard 1; hand none.
  - Wasp (Red, L1) at (1,−7): d2 NW of **Widget**, but d4 and not in a line from Pyro. Played `wasp-green-1` (3); discard 1; hand none.
  - Minions: Red melee (0,−5) (adjacent to Widget); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Widget. **Opponents:** none.
- **Solution:** Fight as One on the Red melee (defeated). After the attack perform Scorching Breath's primary action, targeting a different unit: Dodger is within 3 of Pyro on Pyro's NW line → no card → defeated → Red's last counter → Blue wins.
- **Wrong tries:** (1) Breath aimed at Wasp "who is in line with Widget": range and line are counted from Pyro. (2) Reuse `widget-red-2b` (an attack, not a skill): not allowed. (3) No first target: without an adjacent unit there is no attack and no After-the-attack.
- **Nudges:** (1) "Your gold attack unlocks a second action." (2) "That second action is a card you already used — and it measures from your dragon." (3) "Attack the minion, then cast Scorching Breath through Pyro at Dodger."
- **Explanation:** Fight as One performs the primary action of a resolved skill card again (card text); the action is the card's own, so Scorching Breath still measures "in range of Pyro and in a straight line from Pyro" (card text, p.19 Straight line). A hero with no cards is defeated by "discards a card, or is defeated" (p.17 If able).
- **Rules:** p.10 Making choices; p.17 If able; p.19 Straight line.
- **Risk:** none.
- **Encoded as p32 (changed, 2026-10-24):** Wasp moved to **(3,−7)** and keeps **`wasp-blue-1`** in
  hand: she is now in a straight line from both Widget (NE, d2) and Pyro (NW, d2), so Scorching Breath
  offers two targets and the closer one (Wasp) just discards. Why: as written Wasp was never a legal
  Breath target (the engine doesn't offer her), so the concept's tempting wrong try could not be played;
  now it can, and it fails. Wrong tries tested: Breath on Wasp, Dragon Bond after the attack, a Movement action.

### C33 — Even a Blocked Shot Pushes
- **Theme:** After-the-attack text applies even when the attack is blocked; a push sets up a teammate. **Difficulty:** 2. **Heroes:** Sabina, Brogan; Tigerclaw.
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 4, turn 4, revealed; Sabina acts next (12). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Sabina (Red, L1) at (3,−5). Played `sabina-gold` Point Blank Shot (12; Attack 2, Ranged, Range 1: "After the attack: If the target is adjacent to you, push it 1 space"). Resolved T1–T3 `sabina-red-1`, `sabina-green-1`, `sabina-blue-1`. Hand `sabina-silver`.
  - Brogan (Red, L1) at (0,−7). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Tigerclaw (Blue, L1) at (2,−5) (adjacent to Sabina). Played `tigerclaw-gold`, already resolved. Resolved T1–T3. **Hand `tigerclaw-blue-1` Dodge** (Block a ranged attack).
  - Blue's other hero (Arien) at (−1,1), already resolved.
  - Minions: Red melee (2,−3); Red heavy (1,0). Blue melee (3,−1), (5,−8); Blue heavy (2,−7). (1,−5), (0,−6), (0,−5) are empty.
- **Player controls:** Sabina and Brogan. **Opponent:** Tigerclaw (Dodge or no defence).
- **Solution:** Point Blank Shot at Tigerclaw. Best reply: Dodge blocks it (he is left with no cards). After the attack he is still adjacent → pushed 1 directly away, to (1,−5). Brogan's Mad Dash SE via (0,−6) to (0,−5), adjacent → Attack 6, non-ranged → no card → defeated. (If he does not block, the shot defeats him.)
- **Wrong tries:** (1) "Dodge blocks Sabina, so shoot something else": then Brogan has no 2-space run to (2,−5) and Tigerclaw keeps his Dodge (which can't stop Mad Dash anyway — but Brogan can't reach). (2) Brogan acts first: impossible, 7 < 12.
- **Nudges:** (1) "Brogan can't reach Tigerclaw where he stands." (2) "A blocked attack still finishes its After-the-attack text." (3) "Shoot Tigerclaw with Point Blank Shot anyway."
- **Explanation:** "After the attack" text applies after the target defends or is removed (p.17, p.14 order step 7), so a blocked shot still pushes. The push moves him one space directly away (p.18), straight into the end of Brogan's straight line. Dodge only stops ranged attacks (card text, p.14 Block).
- **Rules:** p.11 Movement in the card text; p.14 order, Block; p.17 After the attack; p.18 Push.
- **Risk:** none.
- **Encoded as p33 (changed, 2026-10-24):** as written every decision had a single option, so "first
  option everywhere" solved it. Added: a **Blue melee at (4,−6)**, adjacent to Sabina (the "shoot
  something else" target), and the idle **Arien at (−3,−7)** holding `arien-red-1` (Defense 6), so
  Brogan's Mad Dash has a second run, west to (−2,−7), which ends next to Arien and comes first in the
  canonical order. Tigerclaw's resolved cards: `red-1`, `green-1`, `silver`, `gold` (this turn).

### C34 — Respawn Where It Counts
- **Theme:** hero respawn choice; mandatory 2-space move; pushing the lane mid-turn. **Difficulty:** 2. **Heroes:** Brogan; Arien, Tigerclaw (Wasp idle).
- **Goal:** `PushLane(turn)` — "Push the lane this turn."
- **Position:** round 3, turn 4, revealed; Brogan acts next (7). Coin: Blue. Life: Red 2, Blue 3. **Wave counters 2. BZ `lane-1-1`** (Blue pushed once, toward Red).
  - Brogan (Red, L1) **off the board** (defeated earlier this round). Played `brogan-red-1` Mad Dash (7) (a hero must play a card even off the board). Resolved T1 `brogan-gold`, T2 `brogan-blue-1`, T3 `brogan-green-1` (resolved without an action when he was defeated); hand `brogan-silver`.
  - Wasp (Red) at (−6,−1), already resolved.
  - Arien (Blue) at (−2,−3), Tigerclaw (Blue) at (−3,0): both already resolved.
  - Red hero spawn points: (−8,−1), (−6,−3), (−5,−5) — all empty.
  - Minions (BZ `lane-1-1`): **Blue melee (−4,−3) — Blue's only minion.** Red melee (−5,−1), (−8,1); Red ranged (−6,2).
- **Player controls:** Brogan. **Opponents:** none left to act.
- **Solution:** Respawn on (−5,−5), then Mad Dash SE: (−5,−4), (−5,−3) — adjacent to the Blue melee → attack → defeated → Blue has no minion in the BZ → push: a Wave counter flips (2 → 1), the BZ moves to `lane-1-2`.
- **Wrong tries:** (1) Respawn on (−6,−3), the closest point (d2): from 2 away a 2-space straight move cannot end next to the minion (E runs into it; the other lines end too far). (2) Respawn on (−8,−1): its lines end at d≥2. (3) Don't respawn: the card resolves with no action (p.7).
- **Nudges:** (1) "You choose where to come back." (2) "Mad Dash needs exactly two spaces in a line — closest isn't best." (3) "Respawn on the far spawn point (−5,−5)."
- **Explanation:** A hero off the board may respawn on any empty Spawn point of its Throne Zone before acting (p.7). Mad Dash's move is mandatory and exactly 2 spaces (p.11), so the start square decides everything. Defeating the last enemy minion in the BZ pushes the Lane immediately (p.8).
- **Rules:** p.7 Hero Respawn; p.8 Push the Lane, Triggering a Push; p.11 Movement in the card text.
- **Risk:** none.
- **Encoded as p34 (changed, 2026-10-24):** two changes, neither touching the idea. (1) The idle Tigerclaw (Blue, already resolved) is replaced by **Xargatha**, also idle. (2) The whole position is **rotated to Blue's side** of the map, `(q, r) → (3 − q, −7 − r)` (Forgotten Island is symmetric under this half turn): Brogan is Blue and respawns in the Blue Throne Zone; Wasp is his idle teammate at (9,−6); Arien (5,−4) and Xargatha (6,−7) are Red; Red's only minion, a melee, stands at (7,−4); Blue melee (8,−6), (11,−8), Blue ranged (9,−9); BZ `lane-1-3`, 2 Wave counters, Life Red 3 / Blue 2, coin Red. The answer is the far spawn point **(8,−2)**; (9,−4) is the closest, (11,−6) the northern one. Why rotate: on Red's side the right spawn point (−5,−5) is the first space in the engine's canonical order (row, then column), so "first option everywhere" solved it; on Blue's side the answer is the last of the three.

### C35 — Onslaught's Forced Step
- **Theme:** "if able" makes a step optional only when impossible — Onslaught's move is mandatory; a survival square you can only enter by killing. **Difficulty:** 2. **Heroes:** Brogan; Xargatha, Swift (Wasp idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 4, turn 4, revealed; Brogan acts next (11). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Brogan (Red, L1) at (−2,0). Played `brogan-gold` Onslaught (11: "Target a unit adjacent to you. After the attack: Move into the space it occupied, if able."; secondary Movement 1). Resolved T1–T3; discard 1; **hand none**.
  - Wasp (Red) at (4,−2), already resolved.
  - Xargatha (Blue, L1) at (−2,1) (adjacent). Played `xargatha-red-1` Threatening Slash (7; adjacent only). Hand `xargatha-blue-1` (Defense 5).
  - Swift (Blue, L1) at (1,−4). Played `swift-red-1` Snipe (8; maximum range 4, straight line).
  - Minions: **Blue melee M1 (−1,0), M2 (−1,−1)** (both adjacent to Brogan); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
  - Brogan's empty neighbours: (−3,0) (on Swift's SW line at d4) and (−3,1) (adjacent to Xargatha); (−2,−1) is terrain.
- **Player controls:** Brogan. **Opponents:** Swift (8) and Xargatha (7) will attack him wherever they can.
- **Solution:** Onslaught on **M2** at (−1,−1) → defeated → Brogan must move into (−1,−1): d2 from Xargatha and not on any of Swift's lines → safe.
- **Wrong tries:** (1) Hold: Xargatha is adjacent. (2) Movement 1 to (−3,0) (Snipe line) or (−3,1) (next to Xargatha). (3) Onslaught on M1: he is forced into (−1,0), adjacent to Xargatha. (4) Onslaught on Xargatha: she defends, so he does not move ("if able" — the space is still occupied) and stays next to her.
- **Nudges:** (1) "Every square you can walk to is covered." (2) "Onslaught's follow-up step is not optional — so choose where it takes you." (3) "Defeat the minion at (−1,−1)."
- **Explanation:** "If able" makes a step optional only when it cannot be done; otherwise the movement is mandatory (p.17 If able; Brogan FAQ "Brogan must move into the space"). Snipe needs exactly 4 in a straight line (p.19), Threatening Slash an adjacent target (p.13).
- **Rules:** p.10 Mandatory steps; p.13 Non-ranged attacks; p.17 If able; p.19 Straight line.
- **Risk:** none.
- **Encoded as p35 (2026-10-24):** as in the concept (Wasp idle; Swift hand `swift-blue-1`). M2 at (−1,−1) is the only winning target; M1, Xargatha, a Movement action and Hold are refuted.

### C36 — Who Goes First?
- **Theme:** same-team and cross-team ties; the coin flips after the first player of the favoured team. **Difficulty:** 2. **Heroes:** Tigerclaw, Dodger; Wasp (Arien idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 3, turn 4, revealed; **three cards at initiative 9**. Coin: **Red shows.** Life 3/3. Waves 3. BZ `lane-1-2`.
  - Tigerclaw (Red, L2; item +1 defence from `tigerclaw-red-2b`) at (1,−5). Played `tigerclaw-red-2a` Combat Reflexes (9; secondary Movement 4). Discard 1; **hand none**.
  - Dodger (Red, L2; item +1 defence from `dodger-red-2b`) at (0,−2). Played `dodger-red-2a` Finger of Death (9). Hand `dodger-blue-1`.
  - Wasp (Blue, L2; item +1 defence from `wasp-red-2b`) at (2,−5) (adjacent to Tigerclaw, d3 from Dodger). Played `wasp-red-2a` Electrocute (9; Attack 5, adjacent: "After the attack: An enemy hero in radius [3] and not adjacent to you discards a card, if able"). Hand `wasp-blue-1` (Defense 5+1).
  - Arien (Blue) at (4,−2), already resolved.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Tigerclaw and Dodger (the Red team chooses which tied Red hero goes first). **Opponent:** Wasp.
- **Solution:** Red shows, so a Red hero acts first — choose **Tigerclaw**. He uses Movement 4 (or attacks Wasp and steps away) to end out of her reach. He flips the coin to Blue. Wasp (Blue showing) acts next: with nobody adjacent she has no attack (at worst a minion target and an after-attack discard for Dodger, who keeps or loses one card but is not defeated). Dodger acts last. Nobody is defeated.
- **Wrong tries:** Dodger first: she resolves, flips the coin to Blue, and the remaining tie (Wasp vs Tigerclaw) goes to Wasp, who defeats the cardless Tigerclaw. Dodger cannot save him: Wasp is neither adjacent to her nor has a card in her discard, so Finger of Death cannot even target Wasp.
- **Nudges:** (1) "Only one Red hero gets to act before Wasp." (2) "The coin turns over after the first Red hero, not after both." (3) "Send Tigerclaw first and move him away."
- **Explanation:** In a cross-team tie the team shown on the coin acts first and picks which of its tied heroes goes; that player then flips the coin (p.7), so the next tie falls to the other team. Teammates choose their order without discussing reasons (p.4, p.7).
- **Rules:** p.4 Table talk; p.7 Tied Initiative.
- **Risk:** none.
- **Encoded as p36 (2026-10-24):** as in the concept; Dodger is seated before Tigerclaw, so the team's
  "who acts first?" lists her first. Tigerclaw's discard is `tigerclaw-blue-1`. The solver accepts any
  Tigerclaw action that ends away from Wasp (Movement, or Combat Reflexes with a step); Wasp's best reply
  is then to move. Wrong tries tested: Dodger first; Tigerclaw first but Hold.

### C37 — The Poisoned Clock
- **Theme:** markers that change initiative and defence; Block cards only stop one attack type; choosing your card when the enemy's is forced. **Difficulty:** 2. **Heroes:** Wasp; Arien (Tigerclaw, Brogan idle).
- **Goal:** `DefeatHero(arien, turn)` — "Defeat Arien this turn."
- **Position:** round 3, **turn 4, play-a-card step**. Coin: **Blue shows.** Life 3/3. Waves 3. BZ `lane-1-2`.
  - Wasp (Red, L1) at (2,−5). Resolved T1 `wasp-gold`, T2 `wasp-silver`, T3 `wasp-blue-1`. **Hand `wasp-red-1` Shock (8, Attack 5), `wasp-green-1` Stop Projectiles (3; Block a ranged attack).**
  - Tigerclaw (Red, L2) at (−1,−1). Resolved T1–T3 including `tigerclaw-green-2b` Poisoned Dagger (gave Arien the Poison marker). Hand: one card (forced, irrelevant), discard 0.
  - Arien (Blue, L1) at (1,−5) (adjacent to Wasp). **Poison marker** (−1 Initiative, −1 Attack, −1 Defense). Resolved T1–T3; discard 1. **Hand: `arien-red-1` Dangerous Current (8) only → forced**; after playing he has no card.
  - Brogan (Blue) at (4,−2): one card in hand, forced, far away.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Wasp (choose the card, then act). **Opponent:** Arien's forced Dangerous Current, effectively initiative 7.
- **Solution:** Play Shock (8). Poisoned Arien is at 7, so there is no tie: Wasp acts first and attacks him (adjacent) → no card → defeated.
- **Wrong tries:** (1) "8 against 8, Blue shows, Arien goes first — so play Stop Projectiles and block": the poison makes it 7, and Dangerous Current is non-ranged anyway, so Stop Projectiles could not block it. (2) Playing Stop Projectiles: Arien acts at 7, Wasp survives with Shock in hand but never attacks.
- **Nudges:** (1) "Look at Arien's markers before counting initiative." (2) "Is it really a tie?" (3) "Play Shock."
- **Explanation:** A Poison marker lowers the hero's Initiative, Attack and Defense values (card text; Tigerclaw FAQ: values can drop and are resolved normally), so Arien's 8 is a 7 and the coin never comes into it (p.7). A hero with one card left must play it (p.7), leaving him nothing to defend with (p.13).
- **Rules:** p.7 Play one card, Check the initiative, Tied Initiative; p.13 Attacking a hero; p.18 Markers.
- **Risk:** none (the "play a card" starting step must be supported by the puzzle builder).
- **Encoding note (2026-10-24):** blocked on Tigerclaw: the Poison marker's rules will be a behaviour of `TigerclawHero` (not written yet), and he is seated. The builder supports the play step (`PlayCards`); seat Wasp before Arien (or give Arien his forced card as already played), else the answer key refuses a card choice made after an opponent's facedown one (ENCODING.md "Hidden information").
- **Encoded as p37 (changed, 2026-10-24):** Wasp's hand is **`wasp-silver` Static Barrier (13) and
  `wasp-red-1` Shock (8)**, resolved `gold`, `green-1`, `blue-1`. Why: hand cards are offered in catalog
  order, so with Shock and Stop Projectiles the first option was the answer; Static Barrier is the
  tempting "act first for sure" card and comes first. The Block lesson goes; the poison lesson stays.
  Tigerclaw (resolved `red-1`, `green-2b`, `blue-1`; discard `gold`, `silver`; item `green-2a`) and
  Brogan have no cards and pass, so no teammate's choice matters. Seats: Wasp, Tigerclaw, Arien, Brogan.

### C38 — Strip the Silver
- **Theme:** an enemy Active effect (Bolster) protecting the last minion; same-team order. **Difficulty:** 2. **Heroes:** Wasp, Swift; Brogan, Dodger.
- **Goal:** `PushLane(turn)` — "Push the lane this turn."
- **Position:** round 2, turn 3, revealed; Wasp (8) and Swift (8) tie, same team. Coin: Red. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Wasp (Blue, L1) at (0,−5). Played `wasp-red-1` Shock (8; Attack 5, Radius 2: "Target a unit adjacent to you. After the attack: An enemy hero in radius and not adjacent to you discards a card, if able."). Resolved T1–T2; hand 2 cards.
  - Swift (Blue, L1) at (3,−2). Played `swift-red-1` Snipe (8; maximum range 4, straight line). Resolved T1–T2; hand 2 cards.
  - Brogan (Red, L2) at (2,−5). **Active: `brogan-green-2a` Bolster** (resolved T1; "This round: When any friendly minion in radius [2] is defeated you may discard a silver card. If you do, the minion is not removed."). Item +1 defence (`brogan-green-2b` tucked). Resolved T2 `brogan-gold`. Played `brogan-red-1` Mad Dash (7, acts after both Blue heroes). Discard `brogan-blue-1`. **Hand `brogan-silver` only.**
  - Dodger (Red, L1) at (0,−6) (adjacent to Wasp). Played `dodger-green-1` (3). Hand 1 card (`dodger-blue-1`).
  - Minions: **Red melee (3,−6) — Red's only minion** (adjacent to Brogan; d4 NW of Swift; d3 from Wasp). Blue melee (1,−4), (3,−1); Blue heavy (2,−7).
- **Player controls:** Wasp and Swift (and their order). **Opponents:** Brogan's silver (Bolster), Dodger's defence.
- **Solution:** Wasp first: Shock on Dodger (adjacent). After the attack, Brogan — d2 and not adjacent to Wasp — discards a card: his only card is the silver. Then Swift's Snipe on the Red melee: Brogan has no silver to save it → removed → Red has no minion → the Lane is pushed.
- **Wrong tries:** (1) Swift first: Brogan discards the silver, the minion stays; Wasp cannot reach it (d3). (2) Shock targeting Brogan: not adjacent.
- **Nudges:** (1) "Something keeps saving Red's last minion — what does it cost?" (2) "Shock's after-effect can reach a hero you are not touching." (3) "Let Wasp go first and make Brogan drop his silver."
- **Explanation:** Bolster is a "This round" Active effect that stays while the card is resolved (p.17); its cost is a silver card from hand. Shock's After-the-attack makes a non-adjacent enemy hero in radius discard (card text) — Brogan's only card. Same-team ties are ordered by the team (p.7). With the minion defeated and removed, the Lane is pushed at once (p.8).
- **Rules:** p.7 Tied Initiative; p.8 Triggering a Push; p.17 Active Effects, After the attack.
- **Risk:** none.

### C39 — Static Barrier
- **Theme:** an Active effect that blocks movement but not attacks; Block. **Difficulty:** 2. **Heroes:** Wasp; Brogan, Swift (Tigerclaw idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 3, turn 4, revealed; Wasp acts next (13). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Wasp (Red, L1) at (1,−5). Played `wasp-silver` Static Barrier (13; Radius 2: "This turn: While an enemy hero outside of radius is performing an action, spaces in radius count as obstacles. While an enemy hero in radius is performing an action, spaces outside of radius count as obstacles."). Resolved T1–T3 `wasp-gold`, `wasp-red-1`, `wasp-blue-1`. **Hand `wasp-green-1` Stop Projectiles** (Block a ranged attack).
  - Tigerclaw (Red) at (−1,−2), already resolved.
  - Brogan (Blue, L1) at (4,−7) (d3). Played `brogan-red-1` Mad Dash (7): his run SW via (3,−6) to (2,−5) would end next to Wasp.
  - Swift (Blue, L1) at (5,−5) (d4 E, straight line). Played `swift-red-1` Snipe (8).
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Wasp. **Opponents:** Swift (8), Brogan (7).
- **Solution:** Perform Static Barrier. Brogan (outside radius 2) treats every space within 2 of Wasp as an obstacle: (3,−6) and (2,−5) are inside, and every space adjacent to Wasp is inside — no Mad Dash can end next to her. Swift's Snipe still targets her (attacks are not blocked) → Stop Projectiles blocks it.
- **Wrong tries:** Hold (or the Barrier's lack of a Movement secondary): Brogan dashes in (Attack 6, non-ranged) and Stop Projectiles cannot stop it → defeated; one card cannot answer two attacks.
- **Nudges:** (1) "You have one card — it can only stop one attack, and only one kind." (2) "Keep Brogan from ever reaching you." (3) "Perform Static Barrier."
- **Explanation:** Static Barrier turns the spaces in radius into obstacles for enemies acting from outside, so they cannot move into them, but they can still attack a unit inside (Wasp FAQ). Stop Projectiles blocks ranged attacks only (card text, p.14 Block).
- **Rules:** p.11 Movement; p.14 Block; p.17 Active Effects; p.18 Obstacle; FAQ Wasp (Static Barrier).
- **Risk:** none.

### C40 — Carry Away
- **Theme:** minions outside the Battle Zone go back — moving a minion never pushes the lane. **Difficulty:** 2. **Heroes:** Widget, Swift; Dodger (Wasp idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Widget acts next (10). Coin: Blue. Life: Red 1, Blue 2. **Wave counters 1.** BZ `lane-1-2`.
  - Widget (Blue, L5; item +1 defence from `widget-blue-3a`) at (0,−5). Played `widget-blue-3b` Carry Away (10: "Move both Pyro, and an enemy unit adjacent to Pyro, either 2 or 3 spaces each, in the same direction, ignoring obstacles."). Resolved T1–T3; hand 1 card.
  - Swift (Blue, L1) at (1,−7). Played `swift-red-1` Snipe (8; maximum range 4, straight line).
  - Pyro (counts as a token) at (2,−6).
  - Dodger (Red, L1) at (3,−7) (adjacent to Pyro). Played `dodger-green-1` (3); discard 1; hand none.
  - Wasp (Red) at (4,−2), already resolved.
  - Minions: **Red melee (1,−6) — Red's only minion** (adjacent to Pyro). Blue melee (1,−4); Blue heavy (3,−1).
- **Player controls:** Widget and Swift. **Opponent:** none.
- **Solution:** Carry Away E 2: Pyro to (4,−6), Dodger to (5,−7). Swift's Snipe: (1,−7) to (5,−7) is 4 spaces E in a straight line → defeated → Red's last counter → Blue wins.
- **Wrong tries:** (1) Carry the last Red minion W 3 (to (−2,−6), `red-side`) or SW 3 (to (−2,−3), `lane-1-1`) "out of the Battle Zone": it walks straight back by the shortest path; moving a minion never pushes the lane. (2) Any other Dodger move: no spot is at exactly 4 in a line from Swift (NE 2 lands at d4 off-line; the rest are nearer or off-board/occupied).
- **Nudges:** (1) "Dragging the last minion away does not count as removing it." (2) "Where would Swift love to see Dodger?" (3) "Carry Dodger two spaces east."
- **Explanation:** "A Lane Push can never be triggered by moving, pushing, or swapping a minion. It must be either defeated, or removed" (p.8); a minion left outside the BZ after an action returns by the shortest path (p.18). Carry Away moves both pieces the same distance and direction, through obstacles, onto empty spaces (card text, p.18 Ignoring Obstacles).
- **Rules:** p.8 Triggering a Push; p.18 Ignoring Obstacles, Minions Outside of the Battle Zone; p.19 Straight line.
- **Risk:** none.

---

## Difficulty 3 — hardest

### C41 — The Duelist's Oath
- **Theme:** defend with the right card; conditional immunity; an attack that needs a discard. **Difficulty:** 3. **Heroes:** Arien; Wasp, Dodger (Sabina idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 2, turn 3, revealed; Wasp acts next (12). Coin: Red. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Arien (Blue, L3; items +1 initiative from `arien-red-2a`, +1 attack from `arien-blue-2b`) at (1,−5). Played `arien-green-1` (4+1 = 5). Resolved T1 `arien-gold`, T2 `arien-silver`. **Hand `arien-red-2b` Rogue Wave (Defense 4), `arien-blue-2a` Expert Duelist (Defense 6, primary: "Ignore all minion defense modifiers. This turn: You are immune to attack actions of all enemy heroes, except this attacker.").** Discard none.
  - Sabina (Blue) at (−1,0), already resolved.
  - Wasp (Red, L1) at (2,−6) (adjacent). Played `wasp-gold` Magnetic Dagger (12, Attack 3). Hand `wasp-silver`, `wasp-green-1`.
  - Dodger (Red, L5; items +2 attack from `dodger-green-2b`, `dodger-blue-2b`, +1 defence from `dodger-red-2b`, +1 radius from `dodger-red-3b`) at (3,−5) (d2). Played `dodger-red-3a` Middlefinger of Death (10; Attack 5+2 = 7, Ranged, Range 3: "Choose one, or both, on different targets — • Target a unit adjacent to you. • Target a hero in range who has one or more cards in the discard."). Hand 2 cards.
  - Minions: Red melee (1,−6) (adjacent to Arien: −1), (0,−6); Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
- **Player controls:** Arien (defence choices). **Opponents:** Wasp (12) and Dodger (10), attacking him if they can.
- **Solution:** Wasp attacks (3). Defend with **Expert Duelist**: 6 (modifiers ignored) ≥ 3, and for the rest of the turn Arien is immune to attack actions of every enemy hero except Wasp. Dodger cannot target him. Arien survives with Rogue Wave still in hand.
- **Opponent replies:** if Wasp does not attack, Arien's discard stays empty, so Middlefinger's ranged bullet cannot target him and he is not adjacent to Dodger — safe.
- **Wrong tries:** Rogue Wave against Wasp ("keep the immunity card for the big attack"): 4 − 1 = 3 ≥ 3 holds, but now Arien has a card in his discard, so Dodger may shoot him: 7 against Expert Duelist's 6 → defeated. Not defending → defeated.
- **Nudges:** (1) "The second attack can only find you if the first one changes something." (2) "Read the condition on Dodger's ranged shot — and what Expert Duelist does after you defend." (3) "Defend Wasp's dagger with Expert Duelist."
- **Explanation:** A primary Defense card applies its text when you defend with it (p.14); Expert Duelist ignores minion modifiers and grants immunity to attack actions of all other enemy heroes this turn — conditional immunity (p.18 Immune; common FAQ). Discarding a card to defend puts it in your discard (p.10), which is exactly what Middlefinger's ranged bullet looks for.
- **Rules:** p.10 Discard a card; p.14 Defense action, Defense/Skill; p.18 Immune.
- **Risk:** none.
- **Encoded as p41 (2026-10-24):** as in the concept. Dodger's cards: resolved `gold`, `silver`, hand
  `green-2a`, `blue-2a`; Wasp: resolved `red-1`, `blue-1`, hand `silver`, `green-1`. The defence choice
  offers Rogue Wave first. Arien ends the turn with Liquid Leap's Hold (any Movement also wins; its
  primary is not offered). Wrong tries tested: Rogue Wave, no defense.

### C42 — The Courteous Trap
- **Theme:** choosing your last-but-one card; tie broken by the coin; an attack that heals the target. **Difficulty:** 3. **Heroes:** Rowenna; Tigerclaw (Swift, Xargatha idle).
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 3, **turn 4, play-a-card step**. Coin: **Blue shows.** Life 3/3. Waves 3. BZ `lane-1-2`.
  - Rowenna (Blue, L2; item **+1 attack** from `rowenna-blue-2a`) at (3,−2). Resolved T1 `rowenna-red-1`, T2 `rowenna-silver`, T3 `rowenna-green-1`. **Hand `rowenna-gold` Code of Chivalry (11, Attack 4+1: "Before the attack: If you target a hero, both you and the target may retrieve a discarded card"), `rowenna-blue-2b` Accept Surrender (10: "Defeat an enemy hero adjacent to you with no cards in hand").**
  - Tigerclaw (Red, L2; item **+1 defence** from `tigerclaw-red-2b` — no initiative item) at (3,−3) (adjacent). Resolved T1 `tigerclaw-gold`, T2 `tigerclaw-green-1`, T3 `tigerclaw-silver`. **Discard `tigerclaw-red-2a` Combat Reflexes** (Defense 3). **Hand `tigerclaw-blue-1` Dodge (10) only → forced;** after playing he has no card.
  - Swift (Blue) at (−1,1) and Xargatha (Red) at (5,−6): one card each (forced), far away, irrelevant.
  - Minions: Red melee (2,−3) (adjacent to Tigerclaw: +1), (0,−6); Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
- **Player controls:** Rowenna (choose a card, then act). **Opponent:** Tigerclaw (forced Dodge; may retrieve when targeted by the gold).
- **Solution:** Play **Accept Surrender (10)**. Tigerclaw's Dodge is also 10: a cross-team tie, Blue shows → Rowenna acts first. Tigerclaw is adjacent with no cards in hand → defeated (no attack, no defence possible). She flips the coin afterwards.
- **Wrong tries:** (1) Code of Chivalry (11) "to act first for sure": targeting a hero lets him retrieve Combat Reflexes and defend with 3 + 1 item + 1 adjacent melee = 5 against 5 → he survives, then moves away at 10. (2) Assume the tie goes to Red, or that his Dodge reaches 11: his item is Defense, not Initiative — check the dashboard.
- **Nudges:** (1) "One of your cards needs no attack roll at all." (2) "Who wins a 10–10 tie this turn?" (3) "Play Accept Surrender."
- **Explanation:** A hero with one card must play it (p.7), so Tigerclaw arrives at turn 4's resolve with an empty hand. Accept Surrender is a skill: no defence is possible (p.12). The tie at 10 is broken by the coin — Blue's symbol shows, so Blue acts first and then flips it (p.7). The gold would have handed him back a defence card before the attack (card text; Rowenna FAQ).
- **Rules:** p.7 Play one card, Tied Initiative; p.12 Skill action; p.14 Defense total; p.16 Item bonus.
- **Risk:** none (requires the "play a card" starting step).

### C43 — Count the Minions
- **Theme:** Minion Battle arithmetic; an End-of-round effect; why killing the hero is wrong. **Difficulty:** 3. **Heroes:** Tigerclaw, Xargatha; Arien (Wasp idle).
- **Goal:** `PushLane(round)` — "Push the lane by the end of this round."
- **Position:** round 3, turn 4, revealed; Tigerclaw acts next (13). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Tigerclaw (Red, L1) at (1,−5). Played `tigerclaw-gold` Blink Strike (13). Resolved T1–T3 `tigerclaw-red-1`, `tigerclaw-blue-1`, `tigerclaw-green-1`. Hand `tigerclaw-silver`.
  - Xargatha (Red, L2; item +1 attack from `xargatha-green-2a`) at (−1,−4). Played `xargatha-green-2b` Constrict (4: "End of round: Defeat an enemy melee minion adjacent to you." — before the Minion Battle). Resolved T1–T3; hand `xargatha-silver`.
  - Arien (Blue, L1) at (1,−6). Played `arien-red-1` Dangerous Current (8). Resolved T1–T3; discard 1; hand none.
  - Wasp (Blue) at (4,−2), already resolved.
  - Minions: **Red: melee (0,−6), melee (2,−3), heavy (1,0) = 3. Blue: melee B1 (2,−5), melee B2 (0,−4) (adjacent to Xargatha), heavy (2,−7) = 3.**
  - Tigerclaw's Blink Strike options: E through B1 to (3,−5); SW through B2 to (−1,−3); NW through Arien to (1,−7).
- **Player controls:** Tigerclaw and Xargatha. **Opponent:** Arien (8) — will defeat a Red minion (the melee at (0,−6) is adjacent to him) if that helps Blue.
- **Solution:** Blink Strike **E through B1** (defeated). Arien's best reply: defeat the Red melee at (0,−6) → Red 2. Xargatha performs Constrict. End of round: Constrict defeats B2 → Blue has only the heavy. Minion Battle 2 vs 1 → Blue removes 1 → its heavy (last) → Blue has none → the Lane is pushed. (If Arien does nothing: 3 vs 1 → push anyway.)
- **Wrong tries:** (1) Blink through Arien (no cards) — tempting: Arien never acts, but Blue keeps 3 minions: Constrict → 2, battle 3 vs 2 → Blue removes 1 → 1 left, no push. (2) Blink through B2: Constrict has no adjacent melee left; 2 vs 2 after Arien's kill → nothing. (3) Hold: 2 vs 2 → nothing.
- **Nudges:** (1) "At the end of the round, count minions — not heroes." (2) "Xargatha's Constrict needs its own victim; don't take it from her." (3) "Blink Strike east through the minion at (2,−5)."
- **Explanation:** In the Minion Battle the team with fewer minions removes the difference, heavy last; losing the last minion pushes the Lane (p.8). Constrict defeats a melee minion before the battle (card reminder text). Defeating Arien removes one Red loss but no Blue minion — and the battle only counts minions.
- **Rules:** p.8 Minion Battle, Push the Lane; p.13 Attacking a minion; p.17 End of Round.
- **Risk:** none.
- **Encoded as p43 (2026-10-24):** as in the concept (`PushLane(round)`). Only the east Blink wins; the
  blink through Arien is first in the canonical order. The solver's best reply for Arien is to move away
  (the push still comes, 3 vs 1). Wrong tries tested: through Arien, through B2, Hold.

### C44 — A Fistful of Coins
- **Theme:** a fork through the opponent's own choice; picking the right hero to force. **Difficulty:** 3. **Heroes:** Cutter, Swift; Wasp, Dodger.
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Cutter acts next (12). Coin: Blue. Life: Red 2, Blue 2. Waves 3. BZ `lane-1-2`.
  - Cutter (Blue, L7, **10 coins**; items +3 defence from `cutter-red-2b`, `cutter-green-2b`, `cutter-green-3b`, +1 attack from `cutter-blue-2b`, +1 movement from `cutter-red-3a`, +1 initiative from `cutter-blue-3a`) at (1,−2). Played `cutter-blue-3b` A Fistful of Coins (11+1 = 12; Radius 3: "An enemy hero in radius chooses one — • You place that hero in a space in radius. • You gain 3 coins. If you have 13 or more coins, you alone win the game."). Resolved T1 `cutter-gold`, T2 `cutter-red-3b`, T3 `cutter-green-3a`. Hand `cutter-silver`.
  - Swift (Blue, L1) at (5,−5). Played `swift-red-1` Snipe (8; maximum range 4, straight line). Hand 1 card.
  - Wasp (Red, **L4**) at (0,−2) (d1). Played `wasp-green-2a` (3); discard 1; hand none.
  - Dodger (Red, **L1**) at (3,−3) (d2). Played `dodger-green-1` (3); discard 1; hand none.
  - Minions: Red melee (2,−3); Red heavy (1,0). Blue melee (2,−1), (1,−4); Blue heavy (2,−7).
  - Snipe squares (d4 and in line from Swift) that are empty and within Cutter's radius 3: **(1,−1)** and **(1,−5)**.
- **Player controls:** Cutter and Swift. **Opponent:** the chosen hero picks the option.
- **Solution:** Fistful on **Wasp**. If she lets Cutter place her: put her on (1,−1) or (1,−5); Swift (8) snipes her → no card → defeated → tier II → Red's last 2 counters → Blue wins. If she gives the coins: Cutter reaches 13 → Cutter's side wins at once.
- **Wrong tries:** (1) Fistful on Dodger: she chooses to be placed, dies to Snipe, but she is tier I — Red keeps 1 counter. (2) Placing Wasp anywhere off Swift's lines (or adjacent to Swift): Snipe needs exactly 4 in a straight line.
- **Nudges:** (1) "Give the opponent two choices that both lose." (2) "Count what each Red hero is worth in Life counters." (3) "Fistful of Coins on Wasp, and place her at (1,−5)."
- **Explanation:** When a card makes the enemy choose, the enemy picks (p.10 Making choices) — so build a choice with no good answer. Placing a hero needs an empty space in the card's radius (p.18 Place). Snipe requires maximum range in a straight line (p.9, p.19). Wasp at level 4 costs two counters (p.13); the coin threshold is the card's own win condition.
- **Rules:** p.9 Range; p.10 Making choices; p.13 When a hero is defeated; p.18 Place; p.19 Straight line.
- **Risk:** "you alone win the game" treated as a team win (`WinGame`). Cutter can hold 10 coins at level 7 because levels are bought only at the end of a round (p.8, p.15).

### C45 — The Lesser Evil
- **Theme:** opponent's choice as a fork (Last Push or Life counters). **Difficulty:** 3. **Heroes:** Whisper, Tigerclaw; Xargatha, Wasp.
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, **turn 3**, revealed; Whisper acts next (11). Coin: Blue. Life: Red 1, Blue 2. **Wave counters 1.** BZ `lane-1-2`.
  - Whisper (Blue, L2; item +1 initiative from `whisper-blue-2a`) at (2,−2). Played `whisper-blue-2b` Lesser Evil (10+1 = 11; Range 3: "An enemy hero in range chooses one — • That hero discards a card, if able. • You may defeat a minion adjacent to you."). Resolved T1–T2; hand 2 cards.
  - Tigerclaw (Blue, L1) at (−1,−2). Played `tigerclaw-red-1` Hit and Run (9; Attack 3). Hand 2 cards.
  - Xargatha (Red, L2) at (0,−2) (d2 from Whisper, adjacent to Tigerclaw). Played `xargatha-green-1` (5). Discard 1. **Hand `xargatha-blue-2a` (Defense 6) only.**
  - Wasp (Red, L1) at (4,−4) (d2). Played `wasp-green-1` (3). Hand 2 cards.
  - Minions: **Red melee (3,−3) — Red's only minion** (adjacent to Whisper, not to Tigerclaw). Blue melee (2,−1), (1,−4); Blue heavy (2,−7).
- **Player controls:** Whisper and Tigerclaw. **Opponents:** the chosen hero's choice; Xargatha/Wasp act after Tigerclaw.
- **Solution:** Lesser Evil on **Xargatha**. If she discards: her last card goes; Tigerclaw's Hit and Run (9) defeats her (no card) → Red's last Life counter → Blue wins. If she picks the minion: Whisper defeats Red's last minion → push → the last Wave counter flips → Blue wins.
- **Wrong tries:** (1) Lesser Evil on Wasp: she simply discards one of two cards. (2) Tigerclaw alone: 3 against Xargatha's 6. (3) Whisper's Movement action: nothing.
- **Nudges:** (1) "Red is one Life counter and one minion away from losing." (2) "Make a Red hero choose between those two." (3) "Lesser Evil on Xargatha."
- **Explanation:** The enemy hero picks the bullet (p.10), and "any option can be chosen, even if it would have no effect" (card reminder) — which is why the target must still hold a card, or "discard" would be a free pass. Losing the last minion pushes at once and the last Wave counter ends the game (p.8, p.3); a cardless hero cannot defend (p.13).
- **Rules:** p.3 Last Push; p.8 Triggering a Push; p.10 Making choices; p.13 Attacking a hero.
- **Risk:** none (Whisper FAQ confirms the hero picks regardless of effect).
- **Encoded as p45 (2026-10-24):** as in the concept; Wasp is seated before Xargatha, so Lesser Evil
  offers Wasp first. Cards: Whisper resolved `gold`, `red-1`, hand `silver`, `green-1`, item `blue-2a`;
  Tigerclaw resolved `gold`, `silver`, hand `green-1`, `blue-1`; Xargatha resolved `gold`, `red-1`,
  discard `silver`, item `blue-2b`; Wasp resolved `gold`, `silver`, hand `red-1`, `blue-1`. Both of
  Xargatha's bullets are refuted by the solver. Wrong tries tested: Lesser Evil on Wasp, Movement.

### C46 — Hurry Up!
- **Theme:** changing a teammate's initiative; a tie on the coin; the opponent's blocking reply. **Difficulty:** 3. **Heroes:** Hanu, Brogan; Tigerclaw, Xargatha.
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 3, turn 4, revealed; Hanu acts next (12). Coin: **Red shows.** Life 3/3. Waves 3. BZ `lane-1-2`.
  - Hanu (Blue, L1) at (−1,−1). Played `hanu-silver` Hurry up! (12; Range 4: "Set the printed Initiative value of an unresolved card of a hero in range to 11, until it is resolved, or otherwise changes state."). Hand 1 card.
  - Brogan (Blue, L1, no initiative items) at (1,−2) (d2). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Tigerclaw (Red, L1) at (3,−3) (d4). Played `tigerclaw-blue-1` Dodge (10; Movement 3). Discard 1; **hand none.**
  - Xargatha (Red, L1) at (4,−2). Played `xargatha-gold` Cleave (11; Movement 1). Hand 1 card.
  - Minions: Red melee (4,−4) (adjacent to Tigerclaw), Red ranged (0,−3), Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
  - Brogan's Mad Dash routes to Tigerclaw: E via (2,−2) to (3,−2); NE via (2,−3) to (3,−4).
- **Player controls:** Hanu and Brogan. **Opponents:** Xargatha (11) and Tigerclaw (10).
- **Solution:** Hurry Up on **Brogan's Mad Dash** → 11. Now Xargatha (11) and Brogan (11) tie across teams; Red shows → Xargatha first. Her best reply is to step onto one of Brogan's landing squares — she can reach (3,−2) but neither (2,−3) nor (3,−4). She flips the coin. Brogan (11) acts before Tigerclaw (10): Mad Dash by the free route → Tigerclaw has no card → defeated.
- **Wrong tries:** (1) Hurry Up on Tigerclaw's Dodge: 11, tied with his own teammate (Red decides), still before Brogan — he runs. (2) Hurry Up on Xargatha's card: already 11. (3) Forgetting the reply: if Brogan had only one route, Xargatha would block it.
- **Nudges:** (1) "Tigerclaw escapes at 10 unless Brogan goes sooner." (2) "Hurry Up can speed up a friend — and ties are decided by the coin." (3) "Set Brogan's Mad Dash to 11."
- **Explanation:** Initiative is checked again after every resolution (p.7), so changing a card's printed value moves its owner in the queue. A cross-team tie goes to the team on the coin, and that player flips it afterwards (p.7). Xargatha's one-space move is her only way to interfere; two disjoint routes beat it.
- **Rules:** p.7 Check the initiative, Tied Initiative; p.11 Movement in the card text.
- **Risk:** none (no initiative items are involved, so "printed value" vs items does not arise).
- **Encoded as p46 (2026-10-24):** as in the concept; Tigerclaw and Xargatha are seated first, so Hurry
  up! offers Tigerclaw before Brogan. The solver's reply for Xargatha is Cleave on the Blue minion (a
  step can't block both runs); either run then wins. Wrong tries tested: Hurry up! on Tigerclaw, Hanu
  Holds, Brogan's dash to (3,−4) hitting the Red minion instead.

### C47 — Swoop In
- **Theme:** retrieving a card to survive; two different threats with different conditions. **Difficulty:** 3. **Heroes:** Misa; Garrus, Brynn (Wasp idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 4, turn 4, revealed; Misa acts next (6). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Misa (Red, L1) at (1,−5). Played `misa-silver` Swoop In (6; Radius 3: "Place yourself into a space in radius adjacent to two or more enemy units; if you do, you may retrieve a discarded card."). Resolved T1 `misa-gold`, T2 `misa-green-1`, T3 `misa-blue-1`. **Discard `misa-red-1`. Hand none.**
  - Wasp (Red) at (4,−2), already resolved.
  - Garrus (Blue, L5; item +1 initiative from `garrus-green-3a`, no other Initiative item) at (3,−6). Played `garrus-green-3b` Heavy Pilum (4+1 = 5; Range 2: "An enemy hero in range discards a card, or is defeated. You may move up to 2 spaces.").
  - Brynn (Blue, L5) at (−1,−4). Played `brynn-green-3a` Deadfall Trap (2; Radius 4: "Choose one — • An enemy hero adjacent to you discards a card, or is defeated. • An enemy hero in radius who is adjacent to 3 or more obstacles discards a card, or is defeated.").
  - Minions: Blue melee (2,−6), (0,−4), (2,−3); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Misa. **Opponents:** Garrus (5) then Brynn (2); each defeats her if she has no card when hit, each takes one card otherwise.
- **Solution:** Swoop In to **(0,−5)** (adjacent to the Blue melee (0,−4) and to Brynn) or **(−1,−3)** (adjacent to Brynn and the melee) and retrieve `misa-red-1`. Both squares are more than 2 from Garrus; only Brynn can reach her, and Misa discards the retrieved card. She survives.
- **Wrong tries:** (1) Hold: Garrus is in range (d2) and she has no card → defeated. (2) Swoop to (2,−5), (1,−6) or (3,−7): within 2 of Garrus **and** adjacent to 3 obstacles (units count) → two discard-or-be-defeated effects, one card → defeated. (3) Forgetting to retrieve.
- **Nudges:** (1) "With one card you can survive exactly one of their effects." (2) "Brynn's second option counts every unit next to you as an obstacle." (3) "Swoop to (0,−5) and take back your red card."
- **Explanation:** Swoop In is a placement, so it needs only an empty space in radius adjacent to two enemy units (p.18 Place), and its retrieve gives Misa one card (p.10 Retrieve). "Discards a card, or is defeated" only defeats a hero who cannot discard (p.17 If able). Units, terrain and tokens are all obstacles (p.18) — swooping next to enemies feeds Brynn's 3-obstacle condition.
- **Rules:** p.10 Retrieve a card; p.17 If able; p.18 Obstacle, Place.
- **Risk:** none.
- **Encoded as p47 (2026-10-24):** as in the concept. Level-5 cards: Garrus resolved `gold`, `silver`,
  `red-2b`, hand `blue-2a`, items `red-2a`, `green-2b`, `green-3a` (his only Initiative item), `blue-2b`;
  Brynn resolved `gold`, `silver`, `red-2a`, hand `blue-2a`, items `red-2b`, `green-2b`, `green-3b`,
  `blue-2b` (no Initiative item, so she stays at 2). The solver accepts (0,−5) and (−1,−3); (3,−7) is
  first in the canonical order. Wrong tries tested: (3,−7), (2,−5), no retrieve, Hold.

### C48 — Necromancy at the Bell
- **Theme:** end-of-round order (End of round effects, Minion Battle); minion types; respawn rules. **Difficulty:** 3. **Heroes:** Dodger; Xargatha (Tigerclaw, Arien idle).
- **Goal:** `Survive(round)` — "Don't let the lane be pushed this round."
- **Position:** round 3, turn 4, revealed; Dodger acts next (3) — everyone else has resolved. Coin: Blue. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Dodger (Red, L2; item +1 defence from `dodger-green-2a`) at (0,−5). Played `dodger-green-2b` Necromancy (3: "Respawn a friendly minion in an empty friendly spawn point adjacent to you in the battle zone."). Resolved T1–T3; hand 1 card.
  - Tigerclaw (Red) at (4,−2), already resolved.
  - Xargatha (Blue, L2) at (2,−6): **resolved `xargatha-green-2b` Constrict this turn** ("End of round: Defeat an enemy melee minion adjacent to you.").
  - Arien (Blue) at (−1,−2), already resolved.
  - Minions: **Red: melee (0,−6) only** (not adjacent to Xargatha). This wave Red has lost 3 melee, its ranged and its heavy. Blue: melee (1,−4), (3,−1); heavy (2,−7) = 3.
  - Empty friendly spawn points adjacent to Dodger: **(1,−6)** only (a Red melee spawn point, adjacent to Xargatha).
- **Player controls:** Dodger. **Opponent:** none acts; Xargatha's Constrict fires at the end of the round.
- **Solution:** Necromancy on (1,−6), respawning **Red's ranged (or heavy) minion**. End of round: Constrict finds no Red *melee* adjacent to Xargatha. Minion Battle 2 vs 3 → Red removes 1 (the heavy last, if it is the heavy) → 1 left → no push.
- **Wrong tries:** (1) Respawn a melee: Constrict defeats it before the battle → 1 vs 3 → Red must remove 2, has 1 → all gone → pushed. (2) Don't respawn: 1 vs 3 → pushed.
- **Nudges:** (1) "Count Red's minions after Xargatha's End-of-round effect." (2) "Constrict only takes melee minions." (3) "Respawn the ranged minion on (1,−6)."
- **Explanation:** End-of-round effects marked "before the minion battle" fire first (card reminder), then the Minion Battle removes the difference and a team that loses its last minion is pushed (p.8). Necromancy may bring back any removed friendly minion onto any friendly spawn point, but only one that was in play this wave (Dodger FAQ; p.19 Respawn: there must be more spawn points of that type than such minions present).
- **Rules:** p.8 Minion Battle, Push the Lane; p.17 End of Round; p.19 Respawn; FAQ Dodger (Necromancy).
- **Risk:** `RISKY` (low): respawning a ranged/heavy minion onto a *melee* spawn point rests on the Dodger FAQ ("it need not be a matching minion type").

### C49 — Prey Drive's Dilemma
- **Theme:** the defender's best reply — sometimes being defeated is the better defence; make both answers lose. **Difficulty:** 3. **Heroes:** Ursafar; Brogan, Wasp (Garrus idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 5, turn 4, revealed; Ursafar acts next (8). Coin: Red. Life: **Red 2**, Blue 3. **Wave counters 1.** BZ `lane-1-2`.
  - Ursafar (Blue, L1) at (1,−5). **Enraged** (T1 `ursafar-gold` resolved: "This round: You are enraged"). Played `ursafar-red-1` Prey Drive (8; Attack 5, Radius 3: "Target a unit adjacent to you. After the attack: If enraged, and the target was not removed, remove up to 1 enemy minion in radius."). Resolved T1 `ursafar-gold`, T2 `ursafar-blue-1`, T3 `ursafar-green-1`. Hand `ursafar-silver`.
  - Garrus (Blue) at (−1,−1), already resolved.
  - Brogan (Red, **L3**, tier I) at (0,−4) (adjacent). Played `brogan-green-2b` (5). Hand `brogan-red-2a` (Defense 8).
  - Wasp (Red, **L4**, tier II; item +1 defence) at (2,−6) (adjacent). Played `wasp-green-2a` (3). Hand `wasp-blue-2a` (Defense 5+1; −1 from the adjacent Blue heavy = 5).
  - Minions: **Red melee (3,−5) — Red's only minion** (d2 from Ursafar, not adjacent). Blue melee (1,−4), (3,−1); Blue heavy (2,−7).
- **Player controls:** Ursafar. **Opponent:** the attacked hero's defence choice.
- **Solution:** Prey Drive on **Wasp**. If she defends successfully (5 vs 5), she is not removed → Ursafar removes the Red melee → Red has no minion → push → last Wave counter → Blue wins. If she fails or declines, she is defeated → tier II → Red's last 2 counters → Blue wins.
- **Wrong tries:** Prey Drive on Brogan: his best reply is **not** to defend — he is defeated (1 counter, Red keeps 1), and since the target was removed, no minion is removed. Blue misses.
- **Nudges:** (1) "Prey Drive's bonus only works if the target survives." (2) "Would the target rather die? Pick one for whom dying also loses." (3) "Attack Wasp."
- **Explanation:** A defender may always choose not to defend (p.13), and the solver will when that blocks the bonus. Removing the last enemy minion pushes the Lane at once (p.8) — removal counts as well as defeat. Life counters follow the lowest tier (p.13): Wasp's defeat ends the game, Brogan's does not.
- **Rules:** p.3 Last Push; p.8 Triggering a Push; p.13 Attacking a hero, When a hero is defeated; p.17 Active Effects.
- **Risk:** none.

### C50 — Shoot, Push, Scoot
- **Theme:** a mid-turn push empties the old Battle Zone and opens Fast Travel; maximum range. **Difficulty:** 3. **Heroes:** Silverarrow; Xargatha, Brogan (Arien idle).
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 4, turn 4, revealed; Silverarrow acts next (12). Coin: Blue. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Silverarrow (Blue, L1) at (−1,−3). Played `silverarrow-gold` Shoot and Scoot (12; Attack 1, Ranged, Range 2: "Target a unit at maximum range. After the attack: If able, you may fast travel to an adjacent zone."; Movement 1). Resolved T1–T3; discard 1; **hand none.**
  - Arien (Blue) at (4,−6), already resolved.
  - Xargatha (Red, L1) at (−3,−2) (`lane-1-1`, d2). Played `xargatha-red-1` Threatening Slash (7). Hand 1 card.
  - Brogan (Red, L1) at (−4,0) (`lane-1-1`). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Minions: **Red melee (1,−4) — Red's only minion** (d2). Blue melee (0,−3), (0,−4), (−1,−4), (−1,−2) (around Silverarrow); Blue heavy (2,−7).
  - Silverarrow's free neighbours are (−2,−3) and (−2,−2) (`lane-1-1`), both next to Xargatha; Brogan's Mad Dash reaches a square next to (−1,−3).
- **Player controls:** Silverarrow. **Opponents:** Xargatha and Brogan (7) — either defeats her if it can reach.
- **Solution:** Shoot the Red melee at (1,−4) (exactly 2 away) → defeated → Red's last minion → the Lane is pushed: Blue wins the push, the BZ moves to `lane-1-1`, and **all minions in `lane-1-2` are removed** (Blue's too). After the attack, Fast Travel: `lane-1-2` now holds no enemy hero or minion, and `lane-1-3` has none → move to any empty space of `lane-1-3`, far beyond both attackers.
- **Wrong tries:** (1) Shoot Xargatha (also exactly 2 away): she defends; no push, the minion keeps `lane-1-2` closed to Fast Travel → she is caught. (2) Walk 1: both squares are next to Xargatha. (3) Hold: Brogan's dash.
- **Nudges:** (1) "You can't Fast Travel out of a Zone with an enemy minion in it — unless it stops being there." (2) "What happens to every minion in this Zone if the lane moves?" (3) "Shoot Red's last minion, then Fast Travel to lane-1-3."
- **Explanation:** Defeating the last enemy minion pushes the Lane in the middle of the turn and removes every minion from the old BZ (p.8). "After the attack" applies after the target is removed (p.17), so the Fast Travel check sees the emptied Zone: no enemy heroes or minions in the Zone you leave or enter (p.11). The turn then continues in initiative order (p.8).
- **Rules:** p.8 Push the Lane, Triggering a Push; p.9 Range; p.11 Fast Travel; p.17 After the attack.
- **Risk:** none.

### C51 — Ice in the Veins
- **Theme:** initiative changed mid-turn by tokens; moving an enemy into them. **Difficulty:** 3. **Heroes:** Tali, Brogan; Tigerclaw (Dodger idle).
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 4, turn 4, revealed; Tali acts next (10). Coin: **Red shows.** Life 3/3. Waves 3. BZ `lane-1-2`.
  - Tali (Blue, L5; items +3 attack from `tali-green-2b`, `tali-blue-2b`, `tali-green-3b`, +1 defence from `tali-red-2b`) at (0,−4). Played `tali-blue-2a` Snowstorm (10; Radius 3: "Choose a direction. Move each enemy unit in radius 1 space in that direction, if able."). Resolved T1 `tali-green-3a` Pack Ice (placed the Ice tokens: "Enemy heroes have −1 Initiative for every Ice token they are adjacent to"), T2 `tali-gold`, T3 `tali-red-2a`. Hand `tali-silver`.
  - Brogan (Blue, L1) at (2,−7). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Tigerclaw (Red, L1) at (2,−5) (d2 from Tali; touching no Ice). Played `tigerclaw-red-1` Hit and Run (9). Discard 1; **hand none.**
  - Dodger (Red) at (4,−2), already resolved.
  - Tokens: **Ice at (4,−6), (4,−7), (3,−7).**
  - Minions (none of Red's within 3 of Tali): Red melee (4,−4); Red heavy (1,0). Blue melee (3,−1), (1,−4).
- **Player controls:** Tali and Brogan. **Opponent:** Tigerclaw (9 now) — he would run before Brogan's 7.
- **Solution:** Snowstorm **NE**: Tigerclaw moves to (3,−6), adjacent to all three Ice tokens → his card counts 9 − 3 = **6**, below Brogan's 7. Brogan acts next: Mad Dash SE via (2,−6) to (2,−5) (Tigerclaw's old square), adjacent → no card → defeated.
- **Wrong tries:** (1) E to (3,−5) or NW to (2,−6): one Ice → 8, he still goes first. (2) W/SW: no Ice → 9. (3) Only two Ice → 7 = 7, a cross-team tie; Red shows, Tigerclaw first. (4) No Snowstorm: he runs at 9.
- **Nudges:** (1) "Tigerclaw is faster than Brogan — for now." (2) "Each Ice token he touches slows him by one." (3) "Snowstorm north-east, into the ice."
- **Explanation:** Tali's Ice tokens keep their property while on the board (p.19 Tokens): enemy heroes lose 1 Initiative per adjacent Ice token. Initiative is compared again before each resolution (p.7), so the move takes effect immediately; ties would go to the team on the coin (p.7).
- **Rules:** p.7 Check the initiative, Tied Initiative; p.11 Movement in the card text; p.19 Tokens.
- **Risk:** `RISKY`: that the Ice penalty is a lasting token property (not an Active effect that ends with the turn Pack Ice was played) and that a mid-turn initiative change re-orders the remaining heroes. Both follow from p.19 and p.7 but are not spelled out for this card.

### C52 — Battle Fury
- **Theme:** an Ultimate that turns a discard into an action; card states; acting first. **Difficulty:** 3. **Heroes:** Garrus; Tigerclaw (Wasp, Arien idle).
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 2, turn 3, revealed; Garrus acts next (14). Coin: Blue. Life 4/4. Waves 3. BZ `lane-1-2`.
  - Garrus (Blue, **L8**; Ultimate `garrus-purple-4` Battle Fury: "Each time after one of your resolved cards is discarded, you may perform its primary action."; items +3 attack from `garrus-green-2b`, `garrus-blue-2b`, `garrus-blue-3b`, +1 initiative from `garrus-red-2b`, +1 radius from `garrus-red-3b`, +1 movement from `garrus-green-3b`) at (0,−4). Played `garrus-silver` Chilling Howl (13+1 = 14: "You may discard one of your resolved cards. This round: Enemy heroes in radius cannot fast travel, or move more than 2 spaces with a movement action."). **Resolved T1 `garrus-gold` Angry Strike, T2 `garrus-red-3a` Hunt Down** ("Before the attack: If you have one or more cards in the discard, move up to 3 spaces. Target a hero adjacent to you."; Attack 4+3 = 7). Hand `garrus-green-3a`, `garrus-blue-3a`.
  - Tigerclaw (Red, L3; items +1 initiative from `tigerclaw-red-2a`, +1 defence from `tigerclaw-blue-2b`) at (3,−6) (d3). Played `tigerclaw-blue-2a` Sidestep (11+1 = 12; Movement 3). Discard 1. **Hand `tigerclaw-red-2b` Backstab** (Defense 5+1 = 6).
  - Wasp (Red) at (4,−2) and Arien (Blue) at (−1,−1): cards unresolved but lower than 12.
  - Minions: Red melee (0,−6), (2,−3); Red heavy (1,0). Blue melee (1,−4); Blue heavy (2,−7).
- **Player controls:** Garrus. **Opponent:** Tigerclaw (defence now; at 12 he would run).
- **Solution:** Chilling Howl: discard the resolved **Hunt Down** → Battle Fury → perform Hunt Down's primary action: Garrus now has a card in his discard → move up to 3, to (2,−5), (2,−6), (3,−5) or (3,−7) → attack Tigerclaw: 7 against 6 → defeated. (Chilling Howl's This-round effect then applies as normal.)
- **Wrong tries:** (1) Discard Angry Strike: its attack only targets an adjacent unit — nobody. (2) Discard nothing: Tigerclaw acts at 12 and Garrus has no other action this turn. (3) "Hunt Down needs a card already in the discard": it is in the discard itself by then.
- **Nudges:** (1) "Your Ultimate gives you a second action — what triggers it?" (2) "Chilling Howl lets you throw away a card you already used." (3) "Discard Hunt Down and perform it."
- **Explanation:** Chilling Howl's first sentence discards one of Garrus's resolved cards; Battle Fury then lets him perform that card's primary action (Ultimate text). Items raise the Attack icon of every card (p.16). Acting at 14, Garrus resolves before Tigerclaw's 12 (p.7).
- **Rules:** p.7 initiative; p.10 Discard a card, card states; p.16 Ultimate, Item bonus.
- **Risk:** `RISKY`: the timing of a triggered primary action in the middle of another card's text, and that Hunt Down's discard condition counts the card itself once it is in the discard. Both are natural readings; neither is spelled out.

### C53 — Death Grenade
- **Theme:** an End-of-turn effect; the opponent's best reply with a moving card. **Difficulty:** 3. **Heroes:** Min; Tigerclaw, Wasp (Arien idle).
- **Goal:** `DefeatHero(tigerclaw, turn)` — "Defeat Tigerclaw this turn."
- **Position:** round 3, turn 4, revealed; Min acts next (11). Coin: Blue. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Min (Blue, L2; item +1 attack from `min-blue-2a`) at (5,−8). Played `min-blue-2b` Death Grenade (11; Radius 2: "Place a Grenade token into a space in radius. End of turn: Up to 1 enemy hero adjacent to that token discards a card, or is defeated. Remove the Grenade token."). Hand 1 card.
  - Arien (Blue) at (−1,0), already resolved.
  - Tigerclaw (Red, L1) at (4,−6): **already resolved this turn**; discard 1; **hand none.**
  - Wasp (Red, L1) at (4,−4) (d2 from Tigerclaw). Played `wasp-blue-1` Lift Up (10; Radius 2: move a unit or token 1 space keeping its distance, may repeat on the same target). Hand 1 card.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
  - Spaces adjacent to Tigerclaw and within Min's radius 2: (4,−7), (5,−7), (3,−6).
- **Player controls:** Min. **Opponent:** Wasp (10) — she will Lift Up the Grenade or Tigerclaw to break the contact.
- **Solution:** Place the Grenade on **(3,−6)**. It is d3 from Wasp, outside her radius, so she cannot move it. She can move Tigerclaw only along her ring: (4,−6) → (3,−5) (no second step exists). (3,−5) is still adjacent to (3,−6). End of turn: Tigerclaw is adjacent and has no card → defeated.
- **Wrong tries:** (4,−7) or (5,−7): Wasp moves Tigerclaw to (3,−5), out of contact. Wasp's other options (moving herself, Lift Up on a minion) change nothing.
- **Nudges:** (1) "Wasp will try to separate the Grenade and Tigerclaw." (2) "Where can Lift Up move him — and can it reach the Grenade at all?" (3) "Place the Grenade at (3,−6)."
- **Explanation:** End-of-turn text applies after every player has acted (p.17), so later actors get to respond. Lift Up keeps the moved piece at the same distance from Wasp (card text) and needs it within radius 2 (p.9); a Grenade at d3 is out of her reach, and Tigerclaw's only ring step stays next to it.
- **Rules:** p.9 Radius; p.17 End of Turn; p.18 Place; p.19 Tokens.
- **Risk:** none.

### C54 — Chaos Incarnate
- **Theme:** the Tie Breaker coin as card text; an Ultimate repeat that flips it. **Difficulty:** 3. **Heroes:** Ignatia; Tigerclaw, Xargatha (Brogan idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 6, turn 4, revealed; Ignatia acts next (11). **Coin: Blue shows** (the blue face). Life: Red 3, **Blue 1**. Waves 3. BZ `lane-1-2`.
  - Ignatia (Red, **L8**; Ultimate `ignatia-purple-4` Chaos Incarnate: "The first time each turn after you perform a primary action, you may flip the Tie Breaker coin; if you do, you may perform that action again, choosing different targets."; items: no Range or Initiative items) at (2,−2). Played `ignatia-gold` Chaos Bolt (11; Attack 2, Ranged, Range 3: "{blue}: Target a minion adjacent to you. {orange}: Target a hero in range."). Resolved T1–T3; hand `ignatia-silver`.
  - Brogan (Red) at (−1,−1), already resolved.
  - Tigerclaw (Blue, L1) at (0,−3) (d3). Played `tigerclaw-gold`, already resolved. Discard 1; **hand none.**
  - Xargatha (Blue, L1) at (4,−5) (d3). Played `xargatha-green-1` (5). Hand `xargatha-blue-1`.
  - Minions: Blue melee (2,−1) (adjacent to Ignatia), (1,−4); Blue heavy (2,−7). Red melee (0,−6); Red heavy (1,0).
- **Player controls:** Ignatia. **Opponents:** Tigerclaw (no card), Xargatha's defence.
- **Solution:** Chaos Bolt with the blue face: defeat the Blue melee at (2,−1). Chaos Incarnate: flip the coin (now the orange face) and perform Chaos Bolt again with a different target: orange → target a hero in range → Tigerclaw (d3) → no card → defeated → Blue's last counter → Red wins.
- **Wrong tries:** (1) Not flipping: one minion, no win. (2) Flipping and shooting Xargatha: she defends (Defense 5 ≥ 2). (3) "The gold can't reach heroes this turn": only until the coin turns.
- **Nudges:** (1) "Which face is up decides what Chaos Bolt may target." (2) "Your Ultimate turns the coin — and gives a second shot." (3) "Bolt the adjacent minion, flip, then bolt Tigerclaw."
- **Explanation:** Ignatia's cards apply the text matching the symbol on the Tie Breaker coin at the moment the action is performed (card reminder). The Ultimate's flip happens after the first performance and the repeat must choose different targets (Ultimate text; p.10 Repeat). A hero with no cards cannot defend (p.13).
- **Rules:** p.5 Tie Breaker coin; p.7 Tied Initiative (the coin); p.10 Repeat; p.13 Attacking a hero.
- **Risk:** `RISKY`: which coin face is "{blue}" and which is "{orange}" — read as the Blue team's side and the Red team's side (the community engine reads it the same way). The puzzle only needs "the face shown now gives the minion text; the other gives the hero text"; encode it from whichever mapping the engine adopts.

### C55 — The Push Strips the Guard
- **Theme:** a mid-turn push respawns minions next to an enemy hero; defence modifiers change instantly. **Difficulty:** 3. **Heroes:** Swift, Brogan; Arien (Dodger idle).
- **Goal:** `DefeatHero(arien, turn)` — "Defeat Arien this turn."
- **Position:** round 4, turn 4, revealed; Swift acts next (8). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Swift (Red, L1) at (−1,−3). Played `swift-red-1` Snipe (8; maximum range 4, straight line). Hand 1 card.
  - Brogan (Red, L1) at (4,−4). Played `brogan-red-1` Mad Dash (7). Hand 1 card.
  - Arien (Blue, L2; item +1 defence from `arien-red-2b`) at (7,−7) in `lane-1-3` (no minions there yet). Played `arien-green-1` (4). **Hand `arien-red-2a` Raging Stream** (Defense 7+1 = 8).
  - Dodger (Blue) at (0,1), already resolved.
  - Minions: **Blue melee (3,−3) — Blue's only minion** (d4 E of Swift). Red melee (0,−6), (2,−3); Red heavy (1,0).
  - Red spawn points in `lane-1-3`: melee (7,−10), (7,−6), (8,−9); ranged (6,−7); heavy (8,−8). Next to Arien: the heavy (8,−8), the melee (7,−6) and the ranged (6,−7). No Blue spawn point touches him.
- **Player controls:** Swift and Brogan. **Opponent:** Arien defends (8 now).
- **Solution:** Swift snipes the Blue melee → Blue's last minion → push: Red wins it, the BZ moves to `lane-1-3`, all minions in `lane-1-2` are removed and both teams respawn on `lane-1-3`'s spawn points. Arien now has an adjacent Red heavy (−1), an adjacent Red melee (−1) and the Red ranged minion within 2 (−1): 8 − 3 = 5. Brogan's Mad Dash NE via (5,−5) to (6,−6) (free of spawn points) → 6 > 5 → defeated.
- **Wrong tries:** (1) Brogan first or without the push: 8 ≥ 6. (2) Swift shoots Arien: d8, out of reach. (3) Brogan dashes to kill the Blue minion himself: the push happens, but nobody is left to attack Arien.
- **Nudges:** (1) "Arien stands alone — for now." (2) "Where will fresh minions appear if Red pushes?" (3) "Snipe Blue's last minion first."
- **Explanation:** A mid-turn push respawns one minion on every Spawn point of the new BZ (p.8) and the turn goes on in initiative order (p.8). Minion defence modifiers are counted when the defence happens (p.14 order step 2), so the fresh Red minions next to Arien count against him.
- **Rules:** p.8 Push the Lane, Triggering a Push; p.14 order, Minion defense modifiers.
- **Risk:** none.

### C56 — Pile On the Fresh Wave
- **Theme:** pushing the lane mid-action and hitting a freshly respawned minion (the rulebook's own hint, p.10). **Difficulty:** 3. **Heroes:** Hanu, Tigerclaw; (Blue: Arien, Dodger idle).
- **Goal:** `GainCoins(4, turn)` — "Gain at least 4 coins this turn." (Hanu alone gains them.)
- **Position:** round 3, turn 4, revealed; Hanu acts next (10). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Hanu (Red, L5; no Range items) at (4,−4). Played `hanu-red-3b` Pile On (10; Attack 5, Ranged, Range 4: "Choose one, or both, in any order — • Target a unit adjacent to you. • Target a minion in range, adjacent to your friendly hero."). Resolved T1–T3; hand 1 card.
  - Tigerclaw (Red, L1) at (6,−4) in `lane-1-3` (not a spawn point), already resolved.
  - Arien (Blue) at (−1,−5), Dodger (Blue) at (1,−2): already resolved.
  - Minions: **Blue ranged (3,−4) — Blue's only minion** (adjacent to Hanu; not adjacent to Tigerclaw). Red melee (0,−6); Red heavy (1,0).
  - Blue melee spawn point (7,−5) in `lane-1-3`: adjacent to Tigerclaw, d3 from Hanu.
- **Player controls:** Hanu (Tigerclaw has acted). **Opponents:** none.
- **Solution:** First bullet: the adjacent Blue ranged minion → defeated (+2) → Blue's last minion → push: the BZ moves to `lane-1-3` and minions respawn there — a Blue melee appears on (7,−5). Second bullet: a minion in range adjacent to a friendly hero → that melee (d3, next to Tigerclaw) → defeated (+2). Total 4.
- **Wrong tries:** (1) Second bullet first: before the push no Blue minion stands next to a friendly hero (Hanu himself is not "friendly"). (2) Only the first bullet: 2 coins. (3) Expecting the heavy: freshly spawned heavies are Immune while their melee minions stand.
- **Nudges:** (1) "Pile On has two shots and lets you choose the order." (2) "After a push, new minions appear — maybe next to a friend." (3) "Shoot the adjacent ranged minion first."
- **Explanation:** "If you defeat the last enemy minion, the wave will push and repeating your attack could allow you to defeat a freshly respawned minion" (p.10). The push is immediate (p.8), so the second bullet sees the new BZ; "friendly" never includes yourself (p.17). Minions are worth 2 coins (p.13).
- **Rules:** p.8 Triggering a Push; p.10 Modal actions, Repeat note; p.13 Attacking a minion; p.17 Friendly.
- **Risk:** `RISKY` (low): treating Pile On's "Choose one, or both, in any order" like the p.10 repeat note — the second bullet is chosen after the first resolves.

### C57 — Turned to Stone
- **Theme:** "counts as terrain"; ordering a same-team tie; the opponent's sacrifice. **Difficulty:** 3. **Heroes:** Mrak, Xargatha; Dodger, Wasp.
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 3, turn 4, revealed; Mrak and Xargatha both at 7 (same team). Coin: Red. Life: Red 2, Blue 3. Waves 3. BZ `lane-1-2`.
  - Xargatha (Blue, L1) at (2,−5). **Active: `xargatha-blue-1` Stone Gaze, resolved T3** ("Next turn: Enemy heroes in radius [2] count as both heroes and terrain, and cannot perform movement actions."). Played `xargatha-red-1` Threatening Slash (7; secondary Movement 5). Hand 1 card.
  - Mrak (Blue, L1) at (2,−8). Played `mrak-red-1` Seismic Slam (7; Radius 3: "An enemy hero in radius adjacent to terrain, or to a Rock token, discards a card, or is defeated."). Hand 1 card.
  - Wasp (Red, L1) at (3,−6) (adjacent to Xargatha → **counts as terrain**). Played `wasp-green-1` (3). Hand `wasp-blue-1`.
  - Dodger (Red, L1) at (4,−7): adjacent to Wasp; no printed terrain, Rock or token next to her; d3 from Mrak. Played `dodger-green-1` (3). Discard 1; **hand none.**
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (2,−7).
- **Player controls:** Mrak and Xargatha (and their order). **Opponents:** Wasp and Dodger (defences only; they act at 3).
- **Solution:** **Mrak first**: Seismic Slam on Dodger — she is adjacent to Wasp, who counts as terrain → no card → defeated. Xargatha then does as she likes.
- **Wrong tries:** (1) Xargatha first, attacking Wasp: Red's best reply is to let Wasp be defeated (1 counter; Red keeps 1) — now nothing next to Dodger counts as terrain and Seismic Slam cannot target her. (2) Xargatha first, moving away: Wasp leaves the Stone Gaze radius and stops being terrain. (3) Seismic Slam on Wasp: she is next to Dodger (also in radius, also "terrain") but she discards her card and survives.
- **Nudges:** (1) "What does Stone Gaze turn Wasp into?" (2) "Seismic Slam wants a hero next to terrain — and Xargatha must keep that terrain in place." (3) "Let Mrak act first and Slam Dodger."
- **Explanation:** An object that counts as another type takes on that type's rules (p.17 Counts as), so a stone-gazed hero is terrain for Seismic Slam. The Stone Gaze radius is measured from Xargatha's current space (p.17 Active Effects), and same-team ties are ordered by the team (p.7). A defender may always decline to defend (p.13).
- **Rules:** p.7 Tied Initiative; p.13 Attacking a hero; p.17 Active Effects, Counts as; p.18 Next Turn.
- **Risk:** `RISKY` (low): "counts as … terrain" satisfying "adjacent to terrain" — directly supported by p.17 Counts as and the Xargatha FAQ ("count as both heroes and terrain"), but not illustrated anywhere.

### C58 — Unexpected Journey
- **Theme:** swapping places with an enemy; the swapped enemy becomes the obstacle; temporary immunity. **Difficulty:** 3. **Heroes:** Hanu, Wuk; Xargatha, Brogan.
- **Goal:** `Survive(turn)` — "Survive this turn."
- **Position:** round 4, turn 4, revealed; Hanu acts next (10). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Hanu (Blue, L1) at (2,−7). Played `hanu-blue-1` Unexpected Journey (10; Radius 2: "Swap with an enemy hero in radius. This turn: That hero is immune. End of turn: Swap with that hero, regardless of radius and immunity."). Hand 1 card.
  - Wuk (Blue, L1) at (1,−5). Played `wuk-gold`, already resolved. Discard 1; **hand none.**
  - Xargatha (Red, L1) at (2,−5) (adjacent to Wuk; d2 from Hanu). Played `xargatha-red-1` Threatening Slash (7; adjacent only).
  - Brogan (Red, L1) at (2,−8) (adjacent to Hanu). Played `brogan-red-1` Mad Dash (7). His only run toward Wuk is SE through (2,−7) — Hanu's square.
  - Minions: Red melee (0,−6); Red heavy (1,0). Blue melee (3,−1); Blue heavy (5,−8).
- **Player controls:** Hanu (Wuk has acted). **Opponents:** Xargatha and Brogan (7, Red orders them).
- **Solution:** Unexpected Journey with **Xargatha**: she goes to (2,−7), Hanu to (2,−5). Xargatha is now d2 from Wuk and from Hanu — Threatening Slash reaches neither. Brogan's SE run is blocked by Xargatha herself, and no other 2-space run ends next to Wuk or Hanu. At the end of the turn they swap back.
- **Wrong tries:** (1) Hanu walks away (Movement 3): Xargatha stays next to Wuk, and Brogan's lane through (2,−7) opens too. (2) Swap with Brogan: he lands on (2,−7) and dashes SW to (0,−5) next to Wuk — and Xargatha is still adjacent anyway.
- **Nudges:** (1) "Wuk can't defend — remove every way to reach him." (2) "Hanu is currently blocking Brogan; if he leaves, someone must take his place." (3) "Swap with Xargatha."
- **Explanation:** A swap is neither movement nor placement and just exchanges the two pieces (p.19 Swap); the swapped enemy is immune this turn (card text) and still an obstacle (p.18). Threatening Slash only reaches adjacent units (p.13), and Mad Dash needs two empty spaces in a line (p.11). The End-of-turn swap back happens after everyone has acted (p.17).
- **Rules:** p.11 Movement in the card text; p.13 Non-ranged attacks; p.17 End of Turn; p.18 Immune, Obstacle; p.19 Swap.
- **Risk:** none.

### C59 — Mind Grip
- **Theme:** using an enemy's previous card (NebKher); a borrowed movement-attack. **Difficulty:** 3. **Heroes:** NebKher; Dodger, Brogan, Wasp (Sabina idle).
- **Goal:** `WinGame` — "Win the game this turn."
- **Position:** round 4, **turn 3**, revealed; NebKher acts next (9). Coin: Blue. Life: Red 1, Blue 2. Waves 3. BZ `lane-1-2`. *(Three Red heroes: a 3v2 with Handicap, or a 3v3 whose third Blue hero has resolved — either way Red has 1 Life counter left.)*
  - NebKher (Blue, L1; no Attack items) at (1,−2). Played `nebkher-gold` Mind Grip (9; Range 5: "Choose one — • Perform an action on the card in the previous turn slot of an enemy hero in range; … • Defeat a minion adjacent to you."). Resolved T1–T2; hand 2 cards.
  - Sabina (Blue) at (−1,0), already resolved.
  - Brogan (Red, L1) at (−1,−5) (d5): **turn-2 slot `brogan-red-1` Mad Dash** ("Before the attack: Move 2 spaces in a straight line to a space adjacent to an enemy unit, then target that unit"; Attack 6). Played a card below 9 this turn.
  - Wasp (Red, L1) at (4,−6) (d4): turn-2 slot `wasp-blue-1` Lift Up. Played a card below 9.
  - Dodger (Red, L1) at (3,−3) (d2). Played `dodger-green-1` (3). Discard `dodger-silver`, `dodger-blue-1`; **hand none.**
  - Minions: Red melee (4,−4), Red ranged (0,−3), Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
- **Player controls:** NebKher. **Opponents:** none act before him.
- **Solution:** Mind Grip on Brogan's turn-2 card: perform Mad Dash's primary action — NebKher moves 2 E via (2,−2) to (3,−2) (or NE via (2,−3) to (3,−4)), adjacent to Dodger, and attacks with Attack 6 → no card → defeated → Red's last counter → Blue wins.
- **Wrong tries:** (1) Copy Wasp's Lift Up: moves things, no attack. (2) Second bullet: no minion is adjacent to NebKher. (3) Copy a card of a hero out of range 5: not allowed.
- **Nudges:** (1) "NebKher can borrow what an enemy did last turn." (2) "Whose previous card would carry you to Dodger and hit her?" (3) "Mind Grip Brogan's Mad Dash."
- **Explanation:** Mind Grip performs an action of the card in the enemy's previous turn slot as if it were NebKher's (NebKher FAQ: copied attacks keep their kind — a copied non-ranged attack is non-ranged; copied active effects work normally). Mad Dash's text then applies with NebKher as the mover (p.11), and a hero with no cards cannot defend (p.13).
- **Rules:** p.11 Movement in the card text; p.13 Attacking a hero; FAQ NebKher (Mind Grip).
- **Risk:** `RISKY`: which values a copied attack uses — the copied card's printed Attack (6) and whether NebKher's own items apply. The puzzle avoids the question (no items; Dodger has no card, so any Attack value wins).

### C60 — Hold My Saké
- **Theme:** card states — bringing a discarded card back as this turn's card; initiative of the new card. **Difficulty:** 3. **Heroes:** Takahide, Brogan; Dodger (Wasp idle).
- **Goal:** `DefeatHero(dodger, turn)` — "Defeat Dodger this turn."
- **Position:** round 4, turn 3, revealed; Takahide acts next (12). Coin: Red. Life 3/3. Waves 3. BZ `lane-1-2`.
  - Takahide (Blue, L5) at (0,−4). Played `takahide-red-3a` Hold my Saké (12; Attack 3, Radius 4: "Target a unit adjacent to you. After the attack: A friendly hero in radius may swap their unresolved card with a card in their hand, or in their discard."). Resolved T1–T2; hand 2 cards.
  - Brogan (Blue, L1) at (1,−2) (d3). Played `brogan-green-1` Shield (6). Resolved T1 `brogan-silver`, T2 `brogan-blue-1`. **Hand `brogan-gold` Onslaught (11). Discard `brogan-red-1` Mad Dash (7).**
  - Dodger (Red, L1) at (3,−3) (d2 from Brogan, not adjacent). Played `dodger-green-1` (3). Discard 2; **hand none.**
  - Wasp (Red) at (4,−7): card below 7, irrelevant.
  - Minions: Red ranged (0,−3) (adjacent to Takahide), Red melee (4,−4), Red heavy (1,0). Blue melee (2,−1), (3,−1); Blue heavy (2,−7).
- **Player controls:** Takahide and Brogan. **Opponent:** Dodger acts at 3.
- **Solution:** Hold my Saké on the Red ranged minion (defeated). After the attack Brogan swaps his unresolved Shield with **Mad Dash from his discard**: Mad Dash becomes his played, unresolved card (7), Shield goes to the discard. Brogan acts at 7, before Dodger: Mad Dash E via (2,−2) to (3,−2) (or NE to (3,−4)) → she has no card → defeated.
- **Wrong tries:** (1) Swap in Onslaught from hand (11 — "act sooner"): it targets only adjacent units, and Dodger is d2. (2) No swap: Shield does nothing to her.
- **Nudges:** (1) "Brogan's best card for this is already in his discard." (2) "Hold my Saké reaches into the discard too." (3) "Swap Brogan's Shield for Mad Dash from his discard."
- **Explanation:** Swapping two cards swaps all their states (p.10): the discarded Mad Dash becomes played and unresolved, so it resolves at its own initiative in the normal order (p.7). Its exact 2-space move reaches a square next to Dodger (p.11).
- **Rules:** p.7 Resolve your card; p.10 Swap a card; p.11 Movement in the card text.
- **Risk:** none.

---

## Counts

**Difficulty:** 1 → C01–C20 (20) · 2 → C21–C40 (20) · 3 → C41–C60 (20).

**Goal types (`PuzzleGoal`):**
| Goal | Count | Concepts |
|------|-------|----------|
| `WinGame` | 20 | C01, C02, C04, C06, C08, C09, C10, C11, C14, C15, C16, C26, C27, C32, C40, C44, C45, C49, C54, C59 |
| `DefeatHero` | 21 | C17, C18, C20, C21, C22, C23, C24, C25, C29, C30, C31, C33, C37, C42, C46, C51, C52, C53, C55, C57, C60 |
| `Survive` | 11 | C07, C12, C19, C35, C36, C39, C41, C47, C48 (round), C50, C58 |
| `PushLane` | 5 | C03, C28, C34, C38, C43 (round) |
| `GainCoins` | 3 | C05, C13, C56 |

Many `WinGame` concepts are won through a push (Last Push or Throne) or a Life-counter defeat, so they also exercise those goals; relabelling a few to `DefeatHero` would give away the target, which is why they stay `WinGame`. Deadlines: all `turn` except C43 and C48 (`round`).

**Themes (primary):**
| Theme | Concepts |
|-------|----------|
| Initiative, ties, Tie Breaker coin | C04, C22, C24, C36, C37, C42, C46, C51, C52, C54, C57, C60 |
| Which card / which action (primary, secondary, Fast Travel, Hold, Clear) | C12, C20, C35, C39, C42, C52, C59, C60 |
| Targets, ranges, straight lines | C01, C09, C14, C18, C23, C27, C28, C32 |
| Moving / pushing / placing to make attacks legal | C08, C16, C17, C23, C29, C33, C40, C44, C58 |
| Minion defence modifiers | C21, C25, C29, C31, C55 |
| Heavy Minion Immunity | C03, C26, C56 |
| Items | C27, C42, C52 (also bookkeeping in many) |
| Block / defending with the right card (or not at all) | C02, C07, C33, C37, C39, C41, C47, C49 |
| Pushing the lane mid-turn, wave counter, throne | C03, C26, C28, C34, C38, C50, C55, C56 |
| Minion Battle arithmetic, end of round | C43, C48 |
| Last Push | C03, C19, C28, C40, C45, C49 |
| Life counters by lowest tier, Bounty | C02, C04, C10, C11, C44, C49 |
| Coins and assist coins | C05, C13, C56 |
| Active effects (This turn / This round / Next turn / End of turn) | C24, C38, C39, C41, C53, C57 |
| Fast Travel (and when it is blocked) | C12, C50 |
| Respawn choices | C34, C48 |
| "you may" / "if able" vs mandatory | C05, C21, C35, C10 |
| Opponent's choice / defender's best reply | C44, C45, C49, C53, C57 |
| Terrain and obstacles | C11, C17, C30, C57 |

**Heroes featured (29 of 32):** Core — Arien (C01, C41), Brogan (C05, C20, C21, C34, C35 + support in C04, C22, C29, C33, C46, C51, C55, C60), Tigerclaw (C07, C13, C30, C36, C43 + C05), Wasp (C17, C23, C37, C38, C39), Sabina (C15, C33), Xargatha (C12, C14, C16, C43, C57), Dodger (C13, C36, C48). Devoted — Misa (C47), Whisper (C26, C45), Ursafar (C49), Silverarrow (C27, C50), Tali (C25, C51). Defiant — Garrus (C02, C29, C52), Bain (C04, C16), Cutter (C08, C44), NebKher (C09, C59). Renowned — Min (C19, C53), Swift (C10, C23, C38, C44, C55 + C20), Wuk (C24, C28), Hanu (C46, C56, C58), Ignatia (C54). Arcane — Rowenna (C42), Mrak (C11, C57), Snorri (C03, C18). Wayward — Brynn (C30), Mortimer (C31), Widget (C32, C40), Takahide (C22, C60), Emmitt (C06, C24). Not used: Trinkets, Razzle, Gydion (their state — Turret, doubles, spellbook — needs more engine surface than a first puzzle book should lean on).

## Strongest concepts (keep these first)

C44 A Fistful of Coins, C45 The Lesser Evil, C49 Prey Drive's Dilemma (forks built from the opponent's own choice); C50 Shoot, Push, Scoot and C55 The Push Strips the Guard and C56 Pile On the Fresh Wave (the mid-turn push as a tool, the last one straight from the rulebook's own hint on p.10); C43 Count the Minions (the counter-intuitive "don't kill the hero"); C41 The Duelist's Oath and C47 Swoop In (defence-order survival); C21 Charge onto the Spawn Point and C25 Cold Snap (pure geometry with one solution); C58 Unexpected Journey (the swapped enemy becomes the wall); C42 The Courteous Trap; C08 Walk the Plank and C11 By the Water's Edge (clean D1 lessons about zones and terrain).

## RISKY interpretations (index)

| Concept | Reading |
|---------|---------|
| C10 | Only for a wrong try: with no enemy hero next to its target, Super-Shotgun's mandatory "Before the attack" step cannot be done; literal p.10 stops the action. |
| C25 | Order of moving several units with Cold Snap is unspecified; the intended direction is order-independent. |
| C29 | A repeat may choose a different target unless the card restricts it (p.10). |
| C48 | Necromancy may respawn a ranged or heavy minion onto a melee spawn point (Dodger FAQ). |
| C51 | Ice-token initiative penalty is a lasting token property; a mid-turn initiative change re-orders the queue at once. |
| C52 | Battle Fury's triggered action resolves in the middle of Chilling Howl's text; Hunt Down counts itself as "a card in the discard". |
| C54 | Coin-face mapping for Ignatia ({blue} = Blue side, {orange} = Red side). |
| C56 | Pile On's second bullet is chosen after the first resolves, so it can see minions respawned by the push. |
| C57 | A hero that "counts as terrain" satisfies "adjacent to terrain" for another hero. |
| C59 | Values used by a copied attack (printed Attack of the copied card; own items?) — sidestepped by the position. |

Also noted (not risky, but conventions the encoder must share): `GainCoins` as a team total (C05, C13); `WinGame` for Cutter's "you alone win the game" (C44); "pushed into another zone" judged by the end space (C08); puzzles starting at the play-a-card step (C24, C37, C42) need the opponent's forced card (exactly one card in hand) and the player's card choice as the first decision.

## Appendix — how the geometry was checked

A throwaway script (outside the repo, `D:\.goa-qa\puzzle-work\`) loaded `forgotten-island.json` and implemented the `HexGrid` conventions: hex distance, the six directions, straight lines (`straightLineDirection`), movement reach with obstacles (units, tokens, non-space hexes), exact-length straight runs (Mad Dash, Blink Strike), pushes (stop at the first obstacle or terrain), ring moves at constant distance (Lift Up), Fast Travel legality (zone adjacency from the map, enemies in both zones), minion defence modifiers, empty-spawn-point counts and obstacle counts. Every concept's position was built in it and the claims in the text (distances, which squares a run can end on, who is adjacent, which Zone a space is in, which spawn points are empty) were printed and compared. Several concepts were found by search over the whole map (C21, C25, C35, C50, C53, C58 in particular) so that the intended answer is the only one among the obvious alternatives. Encoders should still let the solver confirm each line.
