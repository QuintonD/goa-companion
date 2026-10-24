# Guards of Atlantis II: strategy knowledge for the computer opponent

The knowledge base for the `AiPlayer` (and the `Coach`) in `:core:game` (ADR-035): what good play
looks like, turned into evaluation terms and decision heuristics that can be computed from a
`GameView` and from forks or determinized copies of a `GameSession`. No code here; the
implementation follows this document and the public contract in
`core/game/src/main/kotlin/dev/quinton/goa/core/game/GameApi.kt`.

## How to read this document

Every claim carries a tag:

| Tag | Meaning |
|-----|---------|
| **[RB p.N]** | Rule fact from the pinned 2nd-printing rulebook (printed page N). Quotes are verbatim. |
| **[CARD]** | Printed card text or values (`D:\.goa-qa\ref\card-texts.txt`, same data as the catalog). Card ids like `brogan-red-1`. |
| **[ADV hero]** | The hero's official tactical advice (source 3, `heroes.json` field `advice`; the dashboard backs carry "tactical tips for playing that hero", RB p.6). |
| **[BGG n]** | BoardGameGeek forum thread id `n` (read through `api.geekdo.com/api/articles?threadid=n`). Who said it is noted: designer, publisher, several posters, one poster. |
| **[DATA]** | A statistic computed from all 632 card records (method given where it is used). |
| **[ANALYSIS]** | Derived exactly from the rules (arithmetic, probabilities). True if the rules are. |
| **[OPINION]** | Strategy judgement or a design proposal. Starting points for tuning by self-play, not facts. |

Rules beat everything here; card text beats the rules ("If a card's text directly contradicts the
rules, the card's text takes precedence", RB p.3). Where this document and the engine disagree on a
rule, the engine (with its page-referenced tests) is right and this document is fixed.

Scope: single-lane maps first (Forgotten Island, the default), Quick and Long. Double-lane notes
are at the end of section 2.

---

## 0. What the AI may know (hidden information)

The AI decides only through `Decision` options and sees only `GameSession.view(seat)`; it searches
on `GameSession.determinize(team, seed)` copies (ADR-035 point 3).

- **Open:** every unit, token and marker on the board; every resolved, discarded and (after the
  reveal) played card; items; levels, coins, Life and Wave counters; hand *counts*. "You can always
  ask another player to read any of their unresolved cards, resolved cards, and discarded cards, as
  well as visible information on their dashboards" [RB p.4].
- **Hidden:** "Cards in the deck. Cards in hand. The part of the card covered by your hero dashboard
  ... Any facedown cards" [RB p.4].

**Finding: an enemy's hand is a small, mostly deducible set.** [ANALYSIS]
A hero's round hand is exactly one card per colour: Gold, Silver, Red, Green, Blue ("You should
now have exactly 5 cards, 1 of each color, in your hand", RB p.15). The tier of each colour follows
from the level and the upgrade rule ("You must first upgrade all your non-basic cards to Tier II,
before upgrading your Tier II cards to Tier III", RB p.15). Each upgrade puts one of the two
next-tier cards of that colour in hand and tucks the other as an item [RB p.15-16].

What is public about an item is only its **icon** (the stat slot it sits under). The designer:
"Gaining items happens after everyone upgraded their cards (which is not public) ... To be clear,
the exact card being tucked under the dashboard as an item is not public either" [BGG 3590819,
designer `nichik`, 2025]; the rulebook hides "The part of the card covered by your hero dashboard"
[RB p.4]. [DATA] Within each of the 192 colour/tier pairs the two cards carry different item icons,
so *if* the colour of an upgrade is known, the icon names the card kept in hand; but in 98 of 290
hero/tier/icon combinations the same icon occurs on two colours, so the icon alone can leave two or
three upgrade histories open. Cards seen since (resolved and discarded cards are open, RB p.4)
eliminate candidates quickly: "By the end of the first round, we'll have seen four of the five cards
each hero has; often, everything but their Silver card" [BGG 3463167, Panetierre, playtester].

**Contract issue (report to the engine owner):** `HeroView.items: List<String>` exposes item *card
ids* of other heroes as "open information". By the designer's ruling that leaks hidden
information. Propose (via `core/game/CONTRACT-CHANGES.md`) that other seats see only the item icon
per item, and that the AI uses only the icon. Under that rule the hidden part of an enemy hand is:
for each upgraded colour whose kept card has not been seen since that upgrade, which of the
(at most two) candidates it holds.

Consequences for the implementation:

1. Two things are hidden on a normal turn: which card each enemy put facedown this turn (at most 5
   options each), and the few unresolved upgrade candidates above. A determinization should sample
   the hand from the consistent upgrade histories (usually 1-4 per hero) and the facedown choice
   from an *opponent model* (section 3.1), not uniformly.
2. Genuinely hidden extras that `determinize` must sample: Takahide's Gold card in hand (one of
   three; `takahide-silver` swaps "place the new card facedown"), Min's facedown Mine tokens (blast
   or dud, `min-blue-*`), Gydion's spellbook ("Only Gydion can look at the cards in the spellbook",
   `gydion-silver`; its content is still mostly deducible from spells cast faceup), any card turned
   facedown ("If a resolved or discarded card is facedown ... Facedown cards are hidden
   information", RB p.10), hand cards swapped with deck cards by text (e.g. `snorri-blue-2b`
   "Swap a resolved card with a card in hand" changes which card is resolved, all open).
3. **Fairness check for the UI:** whatever the AI deduces (enemy hands) must be equally available
   to the human (the hero page, the item strip, the resolved and discarded cards). Otherwise the AI
   "knows more" than the player. Note the designer's intent that exact values stay a little
   uncertain: "Stat heuristics give you a 90% of an answer ... The existence of that uncertainty is
   important for the vision of game" [BGG 2887548, designer]. An AI with perfect card recall plays
   like an expert with a reference sheet; section 4 uses card knowledge as a difficulty knob.
4. **Level-up is simultaneous and secret** ("All players who are leveling up upgrade their cards
   simultaneously", RB p.15; the item is revealed only after every hand choice is made, BGG 3590819):
   the AI must choose its upgrade without seeing this round's enemy upgrades.
5. **Teammates:** strategy talk between teammates is out loud and heard by the enemy, "You are not
   allowed to take notes or show each other cards (though you may read aloud information from your
   cards)", and it stops at the reveal [RB p.4]. A teammate's *facedown* card is hidden information
   too ("Any facedown cards", p.4). [OPINION] AI teammates may plan jointly before cards are played
   (that is legal table talk) but must not read a teammate's facedown card afterwards; if the joint
   plan is used, show a one-line summary in the log ("Wasp: I'll take the minions on the left") so
   the human hears what an opponent at the table would hear. Check that `determinize(viewer: Team)`
   keeps teammates' facedown cards hidden (contract question for the engine).

---

## 1. Core strategic principles

### 1.1 The three ways to win, and what the lane really is

"There are two ways to win ... by Pushing the Lane into the enemy Throne, or by defeating enemy
heroes until they run out of Life counters. But there is also a third way! ... When the last
counter is gone, the game ends immediately, and the team that won the last Push wins the game!"
[RB p.3]. Push steps, in order: flip a Wave counter (if it was the last one, the push winner wins);
then the next Zone towards the loser's Throne becomes the Battle Zone ("If the enemy Throne Zone
itself becomes the new Battle Zone, the team who won the push wins the game") [RB p.8].

**Forgotten Island lane** (map data): Red Throne, `lane-1-1`, `lane-1-2` (start), `lane-1-3`, Blue
Throne. From the start, two pushes in a row by one team reach the enemy Throne.

[ANALYSIS] Let p = probability that we win the next push, k = Battle Zone offset towards the enemy
Throne (start 0), w = Wave counters left. Lane win probability W:

```
W(k, w) = p * (w == 1 || k + 1 == +2 ? 1 : W(k + 1, w - 1))
        + (1 - p) * (w == 1 || k - 1 == -2 ? 0 : W(k - 1, w - 1))
```

- **Quick (3 Wave counters) is exactly "first to win two pushes"**: W(0,3) = 3p² − 2p³. At p = 0.5
  one push moves the lane from 0.50 to 0.75 (W(+1,2) = p(2 − p)); losing one drops it to 0.25.
- **Long (5)**: first team two pushes ahead wins by Throne; otherwise push 5 decides (after an even
  number of pushes without a winner the Battle Zone is back in the middle). W(0,5) at p = 0.6 is
  0.67; W(+1,4) = 0.86.
- **Match point**: whenever w == 1, or the Battle Zone is adjacent to a Throne Zone, the next push
  by one side ends the game. The AI must detect this and switch weights (section 2.5).
- **Comeback built in**: "Note that the defending team has more minion spawn points in their
  Pre-Throne Zone" [RB p.8]. On Forgotten Island the pre-throne Zones hold 6 spawn points for the
  defender and 5 for the attacker (map data: 4/3 Melee, 1 Ranged, 1 Heavy). So p for the leading
  team is lower in the pre-throne Zone than in the middle. The designer: "Being at your pre-throne
  zone is almost never a good thing", "Defenders are always at a disadvantage" [BGG 3168047].

**How games are actually won** [BGG 3033553, designer, 2023, "hundreds of games"]: kills 53.23%,
push 46.77% (double push 22.31%, final push 24.46%); "in 55.81% of those games which were won by
push, at least one of the teams was 1 kill away from winning a kill win victory"; kill wins are more
common in 3v3. "I would say roughly 30% of games go into the last wave" [BGG 3491194, designer]. So
neither race can be ignored, and they interact: "It is a lot easier to win the last push if you can
afford to die, and the other team cannot" [BGG 3033553, designer]. What the
three endings mean, per the designer: a Throne push follows a bad play, Life counters follow a lost
team fight, the last push goes to the team slightly ahead in a long game [BGG 3491194].

Life counters: a defeated hero's team "spends the number of Life counters, equal to that hero's
lowest card Tier" (1 at levels 1-3, 2 at 4-6, 3 at 7-8) [RB p.13, p.23]. Quick: 4 per team (4
players) or 5 (6 players); Long: 6 or 8 [RB p.5, p.23]. [ANALYSIS] In a Quick 4-player game, two
deaths of level-4+ heroes end it; the Life race speeds up sharply once heroes reach level 4. Card
exceptions change the cost: Bain's Bounty marker ("spends 1 additional Life counter when
defeated", `bain-gold`), Ursafar's `ursafar-red-3b` Tear (+1), Gydion's Energy Drain spell (regains
1), Rowenna's Glorious Triumph (triple assist coins).

Alternate win conditions printed on cards (the AI must know them and the opponents' progress):
Cutter `cutter-blue-3b` ("If you have 13 or more coins, you alone win the game", only when the
targeted enemy chooses the coins option), Gydion's Wish ("After you cast the 'Wish' spell three
times your team wins the game"), and Brogan's Ultimate (`brogan-purple-4`, "During minion battle
you count as a heavy minion; if you would be removed, lose the push instead").

### 1.2 Minion Battle arithmetic (the engine of the lane)

"Count the number of minions each team has in the Battle Zone ... the team with fewer minions must
remove a number of their minions (of their choice) equal to the difference ... The Heavy minion must
be removed last!" [RB p.8]. "If you need to remove more minions than your team has in the Battle
Zone, remove all your remaining minions" [RB p.8]. Minions do not come back except when the lane is
pushed ("Respawn one minion of the matching type in each Spawn point in the new Battle Zone") or
by card text [RB p.8, p.19].

[ANALYSIS]
- With a > b ≥ 1 minions at the end of the round, the loser keeps 2b − a. **The lane is pushed at
  the end of the round iff a ≥ 2b** (or b = 0, which has already pushed mid-turn).
- Margins snowball. From 6 v 6, killing 3 enemy minions in a round (6 v 3) pushes at once. Killing
  2 (6 v 4) leaves 6 v 2 after the battle; one more kill next round (6 v 1) pushes again.
- **Each minion a hero defeats moves the battle by one and is worth 2 coins** (Heavy 4) [RB p.13].
  A minion removed in the Minion Battle gives no coins to anyone.
- Counts carry over from round to round until a push resets both sides to full spawn points.
- Displacement does not count: "Displacing a minion miniature (by moving, pushing, or swapping it)
  has no impact on the Minion Battle"; a minion outside the Battle Zone after an action is moved
  back to it [RB p.18]. "A Lane Push can never be triggered by moving, pushing, or swapping a minion.
  It must be either defeated, or removed" [RB p.8].
- **Mid-turn push**: "If you defeat, or remove, the last remaining enemy minion in the Battle Zone,
  the Lane is immediately Pushed" and "the turn continues in the Initiative order as normal" [RB
  p.8]. A repeat can then hit "a freshly respawned minion" [RB p.10].
- End-of-round card effects run before the battle and change the count: Xargatha
  `xargatha-green-2b` Constrict ("End of round: Defeat an enemy melee minion adjacent to you",
  reminder "Before the end of round minion battle"), Wuk `wuk-blue-2b`/`3b` (enemy minions that "do
  not count towards the minion total"), Brogan Bolster/Shield/Fortify (a defeated minion "is not
  removed"), Tali's Totems, Dodger's and Gydion's minion respawns, Brogan's Ultimate.

The AI's round-end projection must run exactly this arithmetic on the current counts plus the
kills it expects in the remaining turns (section 2.3, feature L3).

**The designer's macro model: Push Potential, Minion Advantage, Openings** [BGG 2899258,
"Ruling the Waves", designer, 2022; the minion rules it relies on are the same as RB p.8]:

- "The Push Potential is how many enemy minions your team can remove until the round ends. Since the
  majority of heroes in Guards II have only two actions capable of defeating minions (there are
  exceptions), we can assume that both teams start the round with roughly the same push potential of
  two minions per hero." Low-mobility melee heroes realise less of it, high-mobility ranged heroes
  more.
- Tracking the enemy's: "in most cases, they have to use their Red and Gold card to take minions ...
  Your opponent spent a Red card without taking a minion? That's a minus one to their push
  potential. Both their Red and Gold are played? Their push potential is now zero!"
- "Assuming that both teams will realize their current push potential, how many minions will remain
  for each team by the end of round? ... Having one minion advantage makes winning the push very
  likely. Two minion advantage almost guarantees it." That projection also says where the next
  Battle Zone will be: go there. "The easiest way to lose the game is to miscalculate the push
  direction and get stranded on the wrong side of the board."
- **Openings**: with a Minion Advantage, attacks can be spent on heroes; opponents must choose between
  fighting you and losing the push, or playing defensively. With an opening "it might be in your best
  interest to avoid taking more minions, letting the wave push by itself (the slower, the better)".
- **Push timing and the Heavy**: taking the centre Heavy for 4 coins "means that you start the next
  wave with the defending team having a Minion Advantage of two (They have one extra minion in that
  battle zone, and they remove one of yours during minion battle)" [BGG 2899258]. [ANALYSIS] On
  Forgotten Island a mid-round push from the middle lands in the defender's pre-throne Zone at 6 v 5,
  and that same round's Minion Battle leaves 6 v 4. A push through the end-of-round Minion Battle
  instead gives the attacker a full round before the next battle. So: prefer pushes that happen in
  the Minion Battle, or mid-round pushes only when you can defeat another defender before the round
  ends ("only do this if you can stabilize the wave", BGG 2899258).
- "Talk to your teammates" about which minions each will take: taking an ally's planned minion wastes
  Push Potential [BGG 2899258].

### 1.3 Heavy minion immunity and ranged minion threat zones

- "As long as there are any friendly non-heavy minions in the Battle Zone, the heavy minion is
  Immune and can't be affected by any actions (including actions of friendly heroes)" [RB p.13].
  So the Heavy is untouchable until it is alone; then it is worth 4 coins and **killing it pushes
  the lane immediately** (it is the last minion). In the battle it is removed last.
- Minion defense modifiers [RB p.14]: "+1 for every adjacent friendly melee or heavy minion. -1 for
  every adjacent enemy melee or heavy minion. -1 for every enemy ranged minion in radius of 2 spaces
  around you." A **friendly ranged minion gives nothing**. Minions never attack heroes [RB p.3].
- [ANALYSIS] The enemy ranged minion projects a radius-2 "threat zone": every enemy hero inside
  defends at −1. Killing that ranged minion early removes the zone and is worth 2 coins; standing in
  it is the most common way to lose a hero that "should" have defended.
- [DATA] Typical numbers, so ±1 or ±2 decides most attacks. Mean printed values over all heroes
  (Handicap cards excluded): Gold initiative 11.1, Defense 2.0, Attack 3.2; Silver initiative 8.0
  (1-13), Defense 2.9; Red Tier I/II/III initiative 8.0/8.3/9.0, Defense 5.4/5.5/6.1, Attack
  4.2/4.6/5.0; Green initiative 4.0/3.5/3.0, Defense 2.5/2.9/3.5; Blue initiative 9.3/9.9/10.3,
  Defense 4.3/4.8/5.3.

### 1.4 Coins, levels and the Pity coin

- Defeating a hero pays its level in coins; each friendly hero of the killer gets assist coins equal
  to the defeated hero's lowest Tier; melee/ranged minions 2, heavy 4 [RB p.13].
- Level costs 1, 2, 3, 4, 5, 6, 7 (cumulative 1, 3, 6, 10, 15, 21, 28); "If you have enough coins
  for multiple level ups, you must purchase as many levels as you can afford"; levelling is not
  optional [RB p.15]. Pity coin: "All players who have NOT leveled up this round collect 1 coin. You
  do not get to level up this round, even if you now have enough coins" [RB p.8].
- [ANALYSIS] Early levels are cheap: one minion (2 coins) is level 2 with a coin to spare; two
  minions in a round reach level 3. Level 4 needs 6 coins in total and doubles the hero's death cost
  (lowest Tier II = 2 Life counters, 4 coins + 2 assists to each enemy) [RB p.13]. Level 7 makes it
  3. A level-up is permanent strength (a better card and an item) while a death is a temporary
  setback plus a Life counter.
- [ANALYSIS] Coins matter only at the end of the round. A coin that completes a level this round is
  worth more than one that does not; a hero 1 coin short gets it from the Pity coin only in the
  *next* round's level-up.
- [OPINION] After reaching level 4 (and 7) a hero should play more safely: same risk, twice (three
  times) the cost. Conversely, enemy heroes at level 4+ and 7+ are worth focusing: a level-4+ kill is
  worth 6-8 coins to the killing team, more than 2-3 minions [BGG 3033553, community].
- The designer: "The actual true bump in power level happens at level 5 where you get to TIII
  upgrades"; Ultimates are "deliberately hard to get to" ("the likelihood of getting to an ultimate
  is something around 2-4%") [BGG 3472633]. [OPINION] So the Ultimate weight in 2.2 matters little.
  "Comebacks happen all the time" [BGG 3080063, designer]; higher-level heroes are worth more coins
  and Life counters [RB p.3]. The Pity coin is the backstop that guarantees progress in a standoff
  [BGG 3168047].

### 1.5 Cards are actions and health

"Everything your hero can do — skills, attacks and even your hero's 'health' — is represented by
an action on a card" [RB p.3]. You play one card per turn for four turns from a five-card hand,
and "Cards used to defend are always discarded from your hand" [RB p.10]; "A Defense action does
not count towards your 1 card per turn limit. You can defend each time you are attacked, as long as
you have cards in your hand" [RB p.14]; "If the attacked hero has no cards in hand ... that hero is
defeated" [RB p.13]. "You must play a card, if able ... If you have no cards left in your hand, you
must announce that you are passing" [RB p.7]. All cards return at the end of the round [RB p.8].

[ANALYSIS] Define, before playing on turn t (1-4), **spare = hand size − (5 − t)**. Without
discards, spare = 1 on every turn: the fifth card is the round's built-in defense. Each defense or
forced discard lowers spare by one; at spare = 0 the hero still has one card per remaining turn but
nothing to defend with after playing on turn 4; below 0 it will pass turns. So:

- The **cost of a defense** is the spare card (insurance) while spare ≥ 1, and a future action once
  spare ≤ 0. It shrinks as the round goes on: on turn 4, after cards are played, the remaining hand
  card has no future action value at all, so defending with it costs nothing beyond its insurance
  for the rest of that turn.
- **"... discards a card, or is defeated"** effects (many Skill cards: `mrak-red-1`,
  `arien-red-1`, `garrus-green-3b`, `silverarrow-blue-3b`, `swift-green-3a`, ...) cannot be
  defended with values: they cost a card, and they defeat a hero with an empty hand. Treat them as
  attacks that always succeed against empty hands. "Discards a card, if able" effects only cost a
  card.
- **Focus fire** [ANALYSIS]: the first attack on a hero strips its best defense card; a second
  attack in the same turn needs to beat only the next best one. Roles: "Damagers are heroes adept at
  forcing their opponents to discard cards. This weakens enemy heroes, making it easier for your
  allies to take them down" [RB p.21]. The designer: "Hero kills require heroes teaming up and
  focusing on a single target ... If one player ... is trying to get a kill, while the other is
  farming minions, you won't get a kill and you will fall behind on minions"; "Sometimes all it takes
  is to land a single discard skill early, and let your ally finish them off, while you farm" [BGG
  3033553]. Community consensus: one-on-one attacks into a full hand mostly fail; coordinate two
  heroes on one target in the same turn [BGG 3499405, 3474863].
- **One-shots and card kills** [BGG 2894296, essay by anwei, a credited playtester (RB p.22),
  2022]: a hero dies either to one attack above every defense it has left, or to a *card kill*:
  "Guards doesn't have HP; your health is your cards ... If you can attack or pseudo-attack (force a
  discard) on a hero twice in the same round — including on turn 4 — they're probably dead,
  regardless of any attack/defend values." And: "you should plan rounds more than you plan turns."
- **How often a defense exists** [BGG 2521254, designer]: "most heroes will be able to block a basic
  attack with 3-4 of their 5 cards and a non-basic attack with 1-2. At the same time, you can dodge a
  non-basic attack with 2-3 of your cards." So a Red (non-basic) attack threatens most heroes; a
  basic (Gold) attack mostly finishes heroes who have already thrown cards.
- **Late-round aggression** [ANALYSIS]: on turns 3-4 heroes have the fewest cards; a hero that
  defended earlier often has nothing left after playing its turn-4 card. Track enemy hand counts
  (open). Several cards punish empty hands outright (`rowenna-blue-2b` Accept Surrender, Gydion's
  Power Word Kill). The designer: "it is almost never a good idea to play your attacks early in the
  round" [BGG 2432019, 2020].
- **Colour roles** (essay by Panetierre, a credited playtester (RB p.22), BGG 3092335, 2023;
  consistent with [DATA] above). Paraphrased: Gold is the fastest card with the weakest attack and
  lowest defense (initiate, finish an empty hand, take a minion, dodge one space; a turn-4 Gold often
  manages an escape). Silver has no movement action; "any time a hero is called on to 'discard a
  card', we can expect that hero to discard Silver if they can". Red has the highest defense, the
  longest move and the big attack, at low initiative. Blue: "most often Blues are held to be able to
  defend or used to move into the action. It's reasonably common for us to hold on to Blue until turn
  four, and then if we're safe we can play it on turn four to reposition for the next round." Green
  is the slowest: it moves you after the Gold and Red attacks have resolved and sets up next turn.
  Vulnerable hand states: holding only Gold + Silver or Green + Silver; Red and Blue both played (no
  good defense left); Gold and Red played (no attacks left).
- **Which card is the spare** [OPINION, from the above]: "Avoid using your Red and Gold cards to Fast
  Travel" [RB p.11]. [DATA] Red cards carry the highest Defense values (mean 5.4-6.1) and most of the
  attacks; Blue is next (4.3-5.3); Gold (2.0), Green (2.5-3.5) and Silver (2.9) are poor defenders.
  Default round plan: Blue is the spare (defend with it, or play it on turn 4 to reposition), Red
  attacks late in the round, Gold, Green and Silver are spent on actions first. Takahide is the
  exception (section 5).
- **A card in hand is a threat** [ADV Brogan]: "An attack card in hand can be as much of a threat as
  an attack card played." Opponents who can deduce your hand must respect it every turn.
  [ADV Arien]: the Silver "can force opponents to play around it while it's in your hand".

### 1.6 Initiative: acting first, acting last, reading revealed cards

- Order: highest initiative first; "Ties are broken by the Tie Breaker coin" (cross-team: the team
  showing acts first, then that player flips the coin; same team: the team decides, no flip) [RB
  p.7]. After each resolution the next actor is the highest unresolved initiative.
- [ANALYSIS] **Exposure by initiative.** A hero stands at p0 until it acts and at p1 afterwards.
  Enemies acting before it can only hit p0; enemies acting after it hit p1. A high-initiative mover
  is exposed at its destination to almost everyone this turn; a low-initiative mover is exposed at
  its start but its destination is safe until next turn. Hence: escape with high initiative, approach
  with low initiative. [ADV Cutter]: the Silver "allows you to move long distances at low
  initiative, enabling aggressive plays."
- [ANALYSIS] **Strike before they leave.** To hit a hero that will move away, your initiative must
  beat its likely card. The rulebook models exactly this: "Knowing that the only Range 2 attack
  Dodger has is on her Red card, Brogan plays his gold 'Onslaught' card to act before the enemy hero
  in the Initiative order" [RB p.12]. [ADV Garrus]: "Attacking second with your gold card can be an
  easy way to deal extra damage" (his Gold gains +1 Attack per card in his discard).
- **After the reveal** everything played is open, the order is known (`GameView.initiativeOrder`),
  and each enemy's options for this turn are exactly the actions of one known card. The AI can then
  enumerate precisely who can reach whom. Card choice is the only real guess (section 3.1).
- The Tie Breaker coin is open state: the AI knows who wins a cross-team tie now and that the coin
  flips after it (Ignatia's cards read the coin, section 5).
- [DATA] Colour slots order initiative roughly Gold (6-13) > Blue (8-11) > Red (7-12) > Green
  (1-6); Silver varies most (1-13). The rulebook: "Even if your hero has the lowest possible
  Initiative stat, a Gold card (with a few exceptions) will still have a higher Initiative value than
  another hero's Red card" [RB p.21]. Initiative items add +1 to every card [RB p.16]; the designer:
  "Brogan with 3 initiative items will be faster than a Tigerclaw with none" [BGG 2432019].
- [BGG 2908021, 3092335, community, OPINION] Initiative items have a cost too: a hero who always acts
  first resolves before everyone and is answered by all of them. One poster: "very high ini or very
  low ini are very good ... Middle ini is awful."
- Range is checked once, when the target is chosen: "if defender moves after the target is selected,
  the attack still happens" [BGG 3439410, designer]. "If a card has 'ranged' subtype, it's ranged.
  That's it" (even when adjacent; matters for Block cards) [BGG 3478009, designer].

### 1.7 Movement, Fast Travel and blocking it

- One action per card: "choose one and only one of the available actions" [RB p.7]. Moving and
  attacking on the same card is possible only where the card text moves you ("Before the attack:
  Move ..."), e.g. `brogan-red-1` Mad Dash, `tigerclaw-gold` Blink Strike, `garrus-red-*`,
  `cutter-red-*`. A threat map must use the attack's own text, not the card's Movement value.
- Fast Travel: "you may move to any space in the same Zone, or an adjacent Zone, as long as ...
  There are no enemy heroes or minions in the Zone you are Fast Traveling from ... to" [RB p.11]; it
  replaces the whole Movement action and ignores the card text; "You can only perform one Fast
  Travel action, regardless of the movement value on the card!" [RB p.11].
- "**Blocking the enemy from Fast Traveling is one of the key strategies.**" [RB p.11 tip]
  [ANALYSIS] Any one enemy hero or minion in a Zone blocks Fast Travel into and out of it. Nobody
  can Fast Travel into the Battle Zone while the enemy has minions there (the rulebook's Arien
  example, p.11). The useful hops are Throne → pre-throne Zone → Zone next to the Battle Zone. A hero
  standing in the Zone between the enemy Throne and the Battle Zone forces respawned enemies to walk.
  Cards that deny Fast Travel and movement: `arien-blue-2b` Slippery Ground, `garrus-silver`
  Chilling Howl (whole round), `silverarrow-blue-*` Grasping Roots and its upgrades.
- Body-blocking spaces [ADV Tigerclaw]: "Enemies can deny your basic attack by blocking a crucial
  space. Don't let them!" (Blink Strike needs a straight line through an enemy unit and an empty
  landing space). Do not block your own team [ADV Mortimer]: "be cautious not to obstruct friendly
  heroes' movement."
- After a push the action moves one Zone: heroes of the winning team are now behind the new Battle
  Zone, and Fast Travel into it is blocked by the respawned enemy minions. [ADV Ursafar]: "your
  worst nightmare is finding yourself on the wrong side of the board after a lane push." [ADV Wuk]:
  "move along when the wave is about to push." [ADV Brogan]: "Don't get stranded away from the
  action."

### 1.8 Farm or fight

[ANALYSIS] Values in coin-equivalents (see 2.2):

- **Minion kill**: certain (minions cannot defend, RB p.13), 2 coins, +1 battle margin. Roughly 5.
- **Attack on a hero who can defend**: it costs the defender one card (about 2-4 depending on its
  spare, much more if it has to throw its attack card or ends up with an empty hand) and costs you the
  minion you could have killed.
- **Hero kill**: level coins + assists for each teammate + Life counters + tempo (the hero is off the
  board until its next action, respawns at its Throne, and its unresolved card "is resolved,
  without performing an action", RB p.13). Roughly 10-20, more at match point.

Rule of thumb [OPINION]: attack a hero when the probability of defeating it is high (your total
beats every card it can still hold, after minion modifiers and items), when the discard you force is
decisive (its last card, its key attack, the card a teammate's follow-up needs to beat), or when a
teammate acts after you on the same target (focus fire). Otherwise kill minions: they win pushes and
levels. Pushing is usually worth more than an even trade of cards.

**Forks** [BGG 3511053, community]: "positioning yourself somewhere where if the opponents try to come
close you can slam them. The ideal version of this is effectively a 'fork' ... if one of the enemy
team attacks your minion you smack them ... and if they instead don't and run away/use their action
for something else you can smack a minion yourself." [ANALYSIS] In the evaluation this is H8: a space
from which the cards still in hand threaten both an enemy hero and an enemy minion. The threat alone
lowers the enemy's Push Potential. "Neither of us make progress" plays (disrupting the enemy instead
of advancing) can be right [same thread]. The forum's critique of a simple bot adds: a good player
sometimes sees that "your best decision is to NOT kill a hero or minion even though you could"
[BGG 3559857], which is exactly the Opening rule above (let the wave push itself).

### 1.9 Positioning in the Battle Zone

[ANALYSIS] Heroes far from the Battle Zone contribute nothing to the lane (minions live there and
return there). The safe side of the fight is next to your own melee/heavy minions and outside every
enemy ranged minion's radius 2; the dangerous side is next to enemy melee minions. Defense totals
there swing by 2-4. Heroes in the Battle Zone also block enemy Fast Travel into it. [ADV Sabina]:
"You become considerably stronger when you are near friendly minions. Stay in formation!"
[ADV Xargatha]: "Surround yourself with enemy minions to boost your powers, but be careful not to
overextend." [ADV Silverarrow]: "Staying too close to enemy units makes you vulnerable, so maintain
your distance. However, don't be tempted to avoid the fight entirely."

Numbers from Panetierre's position essays (playtester, BGG 2897477, 2898177, 2898634, 2022), useful
as feature scales:
- "We can think of a path of 3 spaces on the board as being worth an action, or a path of 5 spaces
  as being worth two actions." Moving through obstacles is "often doubling the effective movement
  value"; the Movement item is "a general 50% efficiency gain" on the slowest heroes, "closer to 30%"
  on the fastest. (H5 can count distance in actions this way.)
- "Making the kill usually costs a team two actions ... And it usually takes three actions for that
  hero to return to a useful position" (the respawn tempo cost in 2.2).
- "When minions spawn in a battle zone, the net defense modifier of each space ranges from -3 to +2."
- Without a specific goal, "central spaces on the board will on average minimize the distance to a new
  goal"; the centre is where "the first, third, and final minion waves spawn" (on a 5-Zone lane the
  Battle Zone returns to the middle after every even number of pushes).

### 1.10 Respawn

"If your hero is not on the board, you may choose to respawn on any empty Spawn point in your Throne
Zone before performing an action. If no empty Spawn points are available, respawn in the nearest
empty space, adjacent to a Spawn point in your Throne Zone. You may also choose not to respawn and
resolve your card without performing an action" [RB p.7]. "You must play a card even if your hero
is not on the board" [RB p.7]. A defeated hero keeps its hand [ANALYSIS: nothing in p.13's defeat
list removes hand cards].

- [OPINION] Respawn nearly always, on the spawn point closest to the Battle Zone that no enemy still
  to act this turn can attack, then use the card's Movement or Fast Travel (Fast Travel from the
  Throne Zone is legal only if no enemy is in it or in the destination Zone).
- [OPINION] While off the board, play the card you value least: its only use this turn is respawn
  plus a move. Decline to respawn only when an enemy still to act this turn can surely defeat the
  hero at every spawn space (spawn camping): staying off the board costs one action but no Life
  counters.
- The designer describes a deliberate late defeat: "You don't have to discard your red to block the
  attack. You can use it late, after you already fired your attacks, to get defeated and re-spawn,
  obtaining a minion advantage that can win you the game" [BGG 2432019, 2020]. [ANALYSIS] A defeat
  late in the round costs a Life counter and coins but saves the hand and puts the hero at its Throne
  for the next round, which can be where the next push will be fought (1.2). Only worth it when the
  team can afford the Life counters (1.1: "if you can afford to die").

### 1.11 Upgrades: which card to hand, which to item

"When you gain a new level, you choose a card in your hand with the lowest card Tier to upgrade
... Take one of those cards into your hand and set the other one aside ... it becomes your new item"
[RB p.15-16]. "Each item increases the value of the matching icon on each of your Action cards by
+1. If an Action card has no matching icon, the item has no effect on that particular Action card"
[RB p.16]. [DATA] Item icons across all Tier II/III cards: Initiative 96, Defense 96, Attack 96,
Movement 32, Range 32, Radius 32.

- [ANALYSIS] An item's worth = number of cards it affects × how often that stat decides outcomes.
  Initiative and Defense affect almost every card (Defense: all but block-only cards); Attack usually
  only the Gold and Red cards ("most heroes have only two cards with Attack icons", RB p.16); Range
  only ranged cards; Radius only radius cards; Movement every card with a Movement icon.
- [OPINION] Default priorities: Defense items for fragile heroes [ADV Dodger: "Your main weakness is
  your low defense. Keep this in mind when leveling up"], Attack items for heroes whose attacks come
  from many cards or a basic attack [ADV Tali: "Your basic attack benefits from attack items more
  than most"], Initiative for heroes whose plan depends on acting first. Choose the *hand* card by
  role: the official advice repeatedly says the level-up branch defines the role (Arien, Misa, Sabina,
  Trinkets, Mrak, Cutter: section 5).
- [OPINION] Which colour to upgrade first: the colour whose next-tier pair gives the best (hand card +
  item) value now; Red first is a sound default (bigger attack and defense in hand), but heroes whose
  engine is a Blue or Green card should start there.
- Forum heuristics [BGG 2908021, OPINION of individual players]: Panetierre (playtester): "I usually
  pick two stats I want 2+ items in, and one stat I'll take 0 items in"; "If you have the fastest gold card in your game it is useful to
  take enough ini items to maintain having top gold card. If you have the slowest green card it may be
  useful to avoid ini items." GoatRogan (playtester): "if too many people on your team are going faster
  than the enemy ... [the enemy] get to see where you're going/what you're planning before they have
  to act ... high ini builds are best when paired with at least one teammate who can go relatively
  slow." Another poster (RelentIess): buy Attack only up to a meaningful threshold (a one-shot, or
  forcing a Red/Blue discard). Saroule14: "about half the time I take an upgrade because I feel I need
  the item, and the other half because I really want a specific card"; "Range and Ini are
  particularly important if I,m [sic] going for minions, attack is more important if I want to get hero
  kills or just want to zone out enemies. Defense is more important if the enemy wants to kill me."
- Community rule of thumb for comparing cards [BGG 3463167, Panetierre]: a higher tier, an item and
  a helping minion each count like "a few extra dots" on the matching stat bar; "even the lowest
  attack hero can threaten the best defense hero who's getting -3 defense from minions."

---

## 2. Position evaluation

### 2.1 Shape

[OPINION] One function `eval(view, team) -> score` in **coin-equivalents** (1 = one coin now), with
`P(win) ≈ σ(score / T)` (T ≈ 15) for search leaves and the Coach. Terminal states are ±∞ (±1000).
Every feature below is computable from `GameView`, the catalog and map geometry
(`core:domain/board`: distance, zones, movement reach, Fast Travel). Hidden enemy hand cards come from
the deduction in section 0 or from the determinized copy being evaluated.

score = Lane + Life + Σ heroes(ours) − Σ heroes(theirs) + Tempo, each term below. Weights are
starting points for tuning by self-play (section 4.5).

### 2.2 Exchange rates (starting weights) [OPINION]

| Quantity | Weight | Reasoning |
|----------|--------|-----------|
| 1 coin (not completing a level) | 1 | unit |
| 1 coin that completes a level at this round's end | 1.5 | permanent upgrade arrives now |
| 1 level (beyond its coin cost) | +3 | a better card and an item |
| 1 minion margin in the Battle Zone | 3 (×2 at match point) | 2 coins it may yield + its share of the next push (the larger effect comes through W via L4) |
| Lane win probability W | 60 per 1.0 | a push in a Quick game moves W by ~0.25 → 15 |
| 1 Life counter | 5 at full, rising to 15 as the team's counters approach the enemy's death cost | scarce counters end the game; kills decide 53% of games (BGG 3033553) |
| 1 spare card | 3 (turn 1), 2 (turn 2), 1.5 (turn 3), 0.5 (turn 4 after playing) | insurance shrinks as the round ends |
| hero off the board | −4 per turn it needs to be back in the Battle Zone | lost actions, no FT into the BZ; "usually ... three actions for that hero to return" (BGG 2898177) |
| Ultimate unlocked | +8 | permanent ability |

### 2.3 Features

**Lane (per lane)**

- **L1 Battle Zone position** k and Wave counters w → W(k, w, p) from 1.1 (precompute the table per
  map; Forgotten Island needs only the recursion).
- **L2 Push Potential** (the designer's term, 1.2): per team, the enemy minions it can still remove
  this round. Exact version: for each hero, the minion-killing actions among its remaining cards
  (attacks; "Defeat a minion adjacent to you" Golds; removal skills) that can reach a minion given
  its position, movement and the turns left. Cheap version: 2 per hero at round start, −1 for each
  Red or Gold already played without taking a minion ("Both their Red and Gold are played? Their push
  potential is now zero!", BGG 2899258), capped by heroes more than about two actions (5-6 spaces)
  from the Battle Zone.
- **L3 projected Minion Battle and Minion Advantage**: current counts − each side's Push Potential,
  then the 1.2 arithmetic (including end-of-round card effects). The projected difference is the
  Minion Advantage ("one minion advantage makes winning the push very likely. Two minion advantage
  almost guarantees it"); if the projection pushes, evaluate W after the push. On turn 4 after all
  actions this is exact.
- **L4 next-push probability p** for W = σ(a·advantage + b·presence), presence = our heroes within
  one action of the next Battle Zone − theirs. Starting a = 1.2 (so +1 → p ≈ 0.77, +2 → 0.92,
  matching the designer's "very likely" / "almost guarantees"), b = 0.3.
- **L5 Heavy status**: enemy Heavy alone (immune false) = a push is one kill away → add the push
  value × P(we land that kill this turn), minus the mid-round-push penalty from 1.2 when the push
  would land in the enemy pre-throne Zone with no follow-up kill this round.
- **L6 minion counts by type**: enemy ranged minion alive (threat zone on our heroes), our melee near
  our heroes (defense support). Each counted through H4.

**Life**

- **F1** Life counters each side. **F2** "deaths to lose": counters / (lowest Tier of the hero most
  likely to die), per team. When deaths-to-lose ≤ 1, every exposure term (H4) on that team is
  multiplied by 2-3.
- **F3** Bounty markers and other death-cost modifiers in play.

**Per hero (computed for all heroes; ours added, theirs subtracted)**

- **H1 XP**: coins + cumulative level cost (1, 3, 6, 10, 15, 21, 28) + 3 per level + item count
  (items are already part of the level; count them separately only where a hero gains extra items,
  e.g. `min-green-3b`, `dodger-green-3a`).
- **H2 coins to next level** and whether the round's remaining income completes it.
- **H3 hand**: hand size, spare (1.5), best Defense value in hand (card + Defense items), block cards
  available and whether their conditions would hold against the likely attackers.
- **H4 exposure** (the most important per-hero term): for every enemy E and every card c E may
  still play this turn (before the reveal: E's deduced hand; after: its played card) and next turn,
  compute whether c can target this hero from E's position (legal pre-attack moves from the card
  text, range + Range items, straight-line conditions, plus moves by E's teammates acting earlier),
  and the attack total (value + Attack items + text bonuses). Compare with this hero's best defense
  at its position: card Defense + Defense items + text + minion modifiers at that space (1.3). Then
  `P(defeat) ≈` max over threats of [attack > best defense] weighted by how likely E plays c
  (section 3.1), with "discards a card, or is defeated" effects counted as lethal at hand size 0 and
  as a forced discard otherwise. Exposure cost = P(defeat) × (Life counters lost × Life weight +
  level coins + assist coins × enemy heroes + 4 tempo) + P(forced discard) × spare weight.
- **H5 distance to the Battle Zone** in turns (movement reach of its likely cards, Fast Travel where
  legal): −1 per turn beyond the first.
- **H6 Fast Travel**: +1 if it can Fast Travel towards the Battle Zone next turn; for enemies, +1 for
  us per enemy hero currently blocked by our pieces.
- **H7 on board**: off-board heroes take the respawn tempo cost.
- **H8 attack opportunities**: enemy units it can target next turn with a card it still holds,
  valued at minion 5 / hero kill value × P(kill). Small weight (0.3) to avoid double counting with
  L2/H4.
- **H9 hero-specific state** (section 5): tokens placed and still useful (Pyro, Turret, trees,
  zombies, runes), counters (`HeroView.counters`), markers.

**Tempo**

- **T1** Tie Breaker coin side when ties are likely (small, ±0.5).
- **T2** whose cards are left this round: total remaining hand Defense and attack cards per team.

### 2.4 Using it inside a turn

[OPINION] Evaluate at **end of turn** (after all actions) in search; during a turn, heroes still to
act have known cards (after the reveal), so a 1-ply playout of the remaining actors with the Normal
policy (section 4) is cheaper and more accurate than a static eval with half a turn pending. At the
end of turn 4, apply the Minion Battle, push, retrieve, level-up (with the AI's own upgrade policy
for both sides) and the Pity coin before evaluating: these steps are deterministic apart from the
removal and upgrade choices.

### 2.5 Phase weights

[OPINION]

| Phase | Detect | Shift |
|-------|--------|-------|
| Early | round ≤ 2 and all heroes ≤ level 3 | coins ×1.3 (levels are cheap and snowball); Life counter weight 5; exposure lower (deaths cost 1) |
| Mid | default | table 2.2 |
| Level jump | a hero at level 4+ or 7+ | its exposure ×2 / ×3 automatically through Life cost; enemy at 4+ becomes the focus target |
| Match point | w == 1 on a lane, or the Battle Zone adjacent to a Throne Zone, or deaths-to-lose ≤ 1 | Lane and Life dominate: minion margin ×2, W weight 100, coins ×0.3 unless they complete a level that changes the next fight, exposure ×2 |
| Turn within round | turn 1 → 4 | spare weight falls (2.2); minion margin weight rises towards the Minion Battle |

### 2.6 Double lane (later)

Each lane has its own Battle Zone, minions and Wave counters; "After the last Wave counter on either
Lane is flipped, each team counts how many Zones on each Lane are between their Throne and that
lane's Battle Zone. The team with more total Zones between their Throne and both Battle Zones wins"
[RB p.20]. On a tied count the designer rules that play continues: teams "have to be the first(or the
only) team to push the wave in either lane, breaking the tie", "…or a life-counter victory happens
first" [BGG 3499394]. (The app's DOMAIN.md says the rulebook is silent on that tie; this ruling fills
it, for the engine owner to adopt or not.) Replace W with a joint estimate over both lanes and add a
lane-assignment term (heroes split between lanes; Fast Travel distances between them).

---

## 3. Decision heuristics per DecisionKind

### 3.1 PLAY_CARD (simultaneous, initiative guessing)

Options: each hand card, or `Choice.Pass` with an empty hand [RB p.7].

**Opponent model** (needed by every level from Normal up) [OPINION]: for each enemy E, a
distribution over its remaining cards. Level-1 reasoning: score each of E's cards with E's own
1-ply greedy value (as if E assumed our likely plays were our previous-turn style), and take a softmax
with temperature τ (τ ≈ 3 coin-eq). Mix in 15% uniform to stay robust against surprises. Refine with
public cues: E's position (no target in reach → its attack cards are unlikely), E's spare (a hero at
spare 0 rarely plays its last high-Defense card early), turn number (the 5th card stays in hand),
what E did in earlier rounds (style). Same model for human and AI enemies.

**Heuristics** for our own card:

1. **Win now**: a card that pushes at match point, defeats the last Life counter or completes an
   alternate win condition is played if any determinization lets it succeed with high probability.
2. **Safety first**: if H4 says we die this turn to a likely enemy card, prefer cards whose
   initiative lets us leave before that attacker acts (escape with high initiative), or whose primary
   Defense/immunity/Block text answers it, or keep the right defense card in hand.
3. **Keep a spare**: do not drop below spare = 1 before turn 4 unless the play is decisive (kill,
   push, match point). Default round plan (1.5): Gold, Green and Silver are spent on actions first,
   Red attacks late ("it is almost never a good idea to play your attacks early in the round", BGG
   2432019, designer), Blue is the spare and is played on turn 4 if it was not needed (RB p.11 tip:
   don't Fast Travel with Red or Gold).
4. **Plan the round, then pick the turn's card** ("plan rounds more than you plan turns", BGG
   2894296): the card for this turn is the first step of the best 4-turn sequence found (Expert
   searches it; Hard and Normal use the default plan above, adjusted by the position). The forum's
   critique of a random-card bot spells out the requirement [BGG 3559857, Naughty zoot]: "Picking a
   card each turn would need to be part of a larger full round plan of action, IE: I plan to fast
   travel on blue then move on green then kill a minion on gold, then move on red"; "read the wave
   flow ... forecast where it should plan on being positioned at the end of each round"; "if you play
   an attack card you want at least two targets available in case one moves away"; "Evaluate which
   heroes can attack it or use a skill against it. Then evaluate if it can avoid or prevent the
   action. Then evaluate what it would give up in value by avoiding the effect."
5. **Initiative race**: to hit a target that will move, our initiative must exceed its likely
   escape card's (the p.12 Brogan example); to land next to enemies safely, act low (Cutter advice);
   against an enemy who must commit first (high initiative), a low-initiative card lets us answer
   its final position.
6. **Off the board**: play the least valuable card (respawn + move only).
7. **Tie Breaker**: if a tie with an enemy is likely, the side showing acts first and then flips the
   coin [RB p.7]; avoid relying on a tie we lose.
8. **Team** [OPINION]: plan the team's turn jointly before cards are played (focus fire on one
   enemy in initiative order; one hero on minions, one on the hero threat), within the table-talk
   rule in section 0.

### 3.2 ACT_FIRST (same-team tie)

The team decides without discussing reasons [RB p.7, p.4]. Choose the order with the better 1-ply
team value (e.g. the hero that strips a defense card goes before the one that finishes). With a human
teammate, the AI states only a preference ("I would really like you to go first" is the rulebook's
legal example, p.4).

### 3.3 RESPAWN

See 1.10. Score each empty spawn point by: exposure to enemies still to act this turn (known cards),
distance to the Battle Zone, Fast Travel options next turn, blocking a Zone the enemy wants to Fast
Travel through. Decline only against certain spawn-camping death.

### 3.4 CHOOSE_ACTION

After the reveal, enumerate PRIMARY / MOVEMENT / FAST_TRAVEL / ATTACK / CLEAR / HOLD and evaluate each
with a playout of the remaining actors (their cards are known). Rules that shape the options: the
card text applies only on the primary action [RB p.7, p.9]; mandatory steps stop the action if they
cannot be completed [RB p.10]; Fast Travel ignores the card text [RB p.11]; Hold "do nothing" is
always legal and right when every action worsens the position [RB p.12]; Clear removes adjacent
tokens instead of attacking [RB p.13] (good against Pyro, which "counts as a token", `widget-silver`,
and against zombies, trees, rocks, ice and mines that matter).

### 3.5 CHOOSE_SPACE (movement destinations, placements, pushes)

Score each option (the engine supplies paths in `DecisionContext.paths`) by:

- **Safety** (H4 at that space): threats from enemies still to act this turn (exact cards after the
  reveal) plus next turn's likely threats, against our best defense there including minion modifiers.
  This term is the single biggest difference between Easy and Normal (section 4).
- **Opportunity**: targets reachable next turn with cards in hand (minions first in the Battle Zone,
  the enemy ranged minion, low-defense heroes); spaces next to an enemy hero with an empty discard
  pile or hand for heroes whose cards need it.
- **Lane**: inside the Battle Zone (to farm and to block Fast Travel); next to friendly melee/heavy
  minions; outside enemy ranged radius 2.
- **Fast Travel denial**: standing in the Zone the enemy needs to Fast Travel through.
- **Body-blocking**: occupying the landing space or line an enemy attack needs (straight-line attacks,
  "Move 2 spaces in a straight line" pre-attack moves).
- **Don't block teammates** (paths of friendly straight-line attacks, spawn points).
- For pushes and placements of enemy heroes: into *our* ranged minion's radius 2 and next to our
  melee minions (both lower their Defense), away from their next targets, against obstacles where a
  card punishes it (`wasp-blue-2b` "if a pushed hero is stopped by an obstacle, that hero discards a
  card"), or across a Zone border where a card punishes that (`cutter-gold`).

### 3.6 CHOOSE_UNIT (attack targets and other unit choices)

- **Hero target**: P(defeat) = P(our total > every Defense the target can still produce), where the
  target's candidate cards are its deduced hand; include Defense items, minion modifiers at its space
  (`DecisionContext` shows ours when we defend; compute the same for it), text bonuses and Block
  conditions (e.g. `tigerclaw-blue-*` "Block a ranged attack" / "Block a non-ranged attack" carry
  no Defense value at all). Value = P(defeat) × kill value + (1 − P(defeat)) × value of the card it
  will throw (it throws its least useful sufficient card).
- **Minion target**: 2 coins + 1 margin; prefer (a) the last enemy minion or the lone Heavy (push +
  coins; mid-turn push, RB p.8), (b) the enemy ranged minion if its radius covers our heroes, (c)
  enemy melee/heavy minions adjacent to our heroes (each lowers our Defense by 1 there) or adjacent
  to enemy heroes we want to attack next (each raises their Defense by 1), (d) minions our teammates
  cannot reach.
- Heavy minions are not legal targets while immune; the engine's `MinionView.immune` says so.
- Non-attack unit choices (move/push/swap a unit): use 3.5's space scoring for the resulting
  position.

### 3.7 DEFEND (when, with which card, when not)

The engine gives `attackTotal`, `rangedAttack`, `minionModifier` and `defensePreview` per hand card
(null when the text decides during resolution).

1. Candidates = cards whose preview ≥ attack total ("If your Defense total is equal or higher than the
   Attack Total, you successfully defend", RB p.14), plus Block cards whose condition holds, plus
   null-preview cards the AI can resolve on a fork.
2. **No candidate → Decline.** A failed defense still discards the card and the hero is defeated
   anyway ("If you cannot defend successfully, or choose not to defend, you are defeated", RB p.14),
   and a defeated hero keeps its hand for the rest of the round. Exception: cards whose Defense *text*
   pays off even on failure (rare; check on a fork).
3. **Several candidates → throw the least valuable**: the planned spare first; otherwise the card
   with the lowest remaining action value this round (cards needed for the rest of the round plan
   are kept); prefer cards whose primary Defense text adds value (immunity this turn, e.g.
   `arien-blue-2a` Expert Duelist; retrieval; counter-discards like `tigerclaw-blue-2b` Parry,
   "The attacker discards a card, if able"); Defense/Skill cards apply their text in both uses
   [RB p.14].
4. **When defending is wrong** [OPINION, rare]: decline a defense you could make only if being
   defeated costs less than the card. That needs all of: our deaths-to-lose comfortably > 1, the hero
   is level 1-3 (1 Life counter, 1-3 coins), the attacker's team gains little, and the card is
   decisive later this round (e.g. it wins the last push next turn and nothing else can). Compute it;
   never hard-code "always defend". The designer's own example of a good late defeat (keep the Red,
   respawn for the next push) is in 1.10 [BGG 2432019]. Choosing not to defend is legal: "If the
   attacked hero has no cards in hand, is unable to defend, or chooses not to, that hero is
   defeated" [RB p.13].
5. **On turn 4 after cards are played**, the leftover hand card has no action value: defend freely.

### 3.8 REMOVE_MINION (losing a Minion Battle)

The team chooses which minions go; Heavy last [RB p.8].

- [ANALYSIS] Remove the minions the enemy could **kill next round anyway**: a removal gives no coins,
  a kill gives 2. So remove the most exposed minions first (adjacent to or in reach of enemy heroes),
  keep the protected ones.
- Keep melee minions adjacent to our heroes (+1 Defense), keep our ranged minion if its radius 2
  covers enemy hero positions (−1 to their Defense).
- Keep minions on spawn points when enemy Dodger or Whisper profit from empty spawn points
  (`dodger-*`, `whisper-*`: "empty spawn point in the battle zone").

### 3.9 MINION_RETURN

The minion's team chooses among shortest paths [RB p.18]: end next to our heroes, away from enemy
heroes' reach, off the enemy's attack lines.

### 3.10 UPGRADE_CARD and KEEP_UPGRADE

See 1.11. [OPINION] Implementation: a static rating per catalog card (stats by colour/tier
normalised + tags: attack with movement, ranged, discard-or-defeat effects, immunity, retrieval,
coin gain, minion removal) plus an item rating (Defense and Initiative ≈ number of affected cards ×
0.8, Attack ≈ number of attack cards × 1.0, Range/Radius/Movement per affected card × 0.6), plus the
hero's preferred branch from section 5. Expert can refine with a short playout (the next round with
each choice). Log the choice so self-play can learn per-hero branch win rates. The choice is made
without seeing this round's enemy upgrades (simultaneous and secret, section 0 point 4); items already
on enemy dashboards from earlier rounds are fair input (e.g. keep or give up the fastest Gold, 1.11).

### 3.11 CHOOSE_CARD (forced discards, retrieves, swaps, names)

- Forced "discard a card": throw the least valuable card (same order as 3.7.3).
- Retrieve: take back the card with the highest value for the rest of the round (often the best
  Defense card if spare is 0, else the strongest action).
- "Name a color" effects (NebKher `nebkher-blue-*`) and guessing games (Bain `bain-blue-*` "Guess that
  card's color"): use the deduced hand. For Bain's guess the enemy *chooses* which card to show, so
  it will show the card it minds losing least; guess that colour more often, with mixing at Hard+.

### 3.12 CHOOSE_OPTION, CONFIRM, CHOOSE_NUMBER, CHOOSE_TOKEN

Evaluate each option with a fork and a short playout; default for "you may" steps is to take them
only when the evaluation does not drop. For "up to N" distances, prefer the exact space found by 3.5.

---

## 4. Difficulty design

### 4.1 Principles [OPINION]

- Levels differ in **what they notice**, not only in search depth. The ladder follows the order in
  which human players learn the game: rules → minion modifiers and card economy → initiative and
  hand reading → round planning, lane timing and team play.
- Mistakes must look human: drawn from plausible options (top-k by the AI's own score), concentrated
  in the categories a player at that level gets wrong, never absurd (no walking away from the fight
  for no reason, no throwing a defense card that cannot succeed at Normal+).
- One code path with switches, so every level is the same engine with features turned on.

### 4.2 The four levels

| | Easy | Normal | Hard | Expert |
|-|------|--------|------|--------|
| Card knowledge (what it assumes about unseen enemy cards) | Only cards it has seen this game; otherwise colour-slot averages (1.3 [DATA]) | Colour-slot averages adjusted by the hero's stat profile (the "stat heuristics" the designer intends, BGG 2887548) | Exact catalog values for candidate cards | Exact catalog values plus upgrade-history tracking (section 0) |
| Card choice | Rule-based: plays the highest-initiative card that has a legal useful action 50% of the time, else a random reasonable card | 1-ply: each own card vs the single most likely enemy play (opponent model), full eval | Determinization: N = 24-64 samples of enemy facedown cards from the opponent model; each own card simulated through the whole turn; maximise mean − 0.3·stdev | Hard + round planning: beam search over the order of its remaining cards to turn 4 and the round end (Minion Battle, level-up); regret-matching over the per-turn simultaneous game so its own choice is mixed (unexploitable) |
| Minion modifiers | Ignores them 40% of the time when moving | Always | Always | Always |
| Exposure (H4) | Only the nearest enemy, printed values | All enemies' deduced hands, items | Exact per sampled play, incl. teammates' moves acting earlier | Two turns ahead |
| Cards as health | Defends with the first sufficient card; often spends its spare early | Keeps a spare; throws the least valuable card | + discard economy in the turn plan | + plans which card is the round's spare |
| Initiative | Ignores it | Avoids obvious "they act first" deaths | Initiative races, act-low/act-high | + manipulation (Hanu, Emmitt, Tie Breaker) |
| Fast Travel | Uses any card, never blocks | Avoids Red/Gold FT (RB p.11), uses FT well | + blocks enemy FT when cheap | + spawn-camp denial, post-push repositioning |
| Lane | Farms when adjacent; no Minion Battle projection | Projected Minion Battle, match-point switch | + push timing within the round | + last-push timing across rounds, comeback-zone awareness |
| Team | Independent | Independent, avoids blocking allies | Focus fire in initiative order | Joint plan for the team |
| Upgrades | Random among the pair, 50% the higher Attack | Static rating | Rating + hero branch notes | + playout refinement |
| Noise | Softmax τ = 6, 10% per decision "beginner slip" from the top-3 | τ = 2 | τ = 0.5 | none (mixing is strategic) |
| Time budget per card choice (S25 Ultra) | < 50 ms | < 300 ms | < 1.5 s | < 5 s, cancellable |

Notes:
- **Easy's slips** (per decision, budgeted so one game has a handful): standing next to an enemy melee
  minion or in a ranged minion's radius; Fast Travelling with its Red card; attacking a hero who can
  easily defend instead of the minion beside it; forgetting the Heavy is immune (it still chooses
  only legal options, so this shows as wasting a turn walking to it). It never declines a defense it
  can make and never misses a push that is one adjacent minion away (those would feel broken, not
  human).
- **Normal** is the honest greedy player: no deliberate mistakes, just short sight (no initiative
  races, no round plan).
- **Hard** is the "plans the turn against what you might have played" level promised in PRODUCT §9.
- **Expert** adds what strong groups do: round plans, the last push, focus fire, mixing.

### 4.2a Lessons from community bots

- The designer, on solo play: "(In my opinion) It's not possible to make a solo mode for Guards which
  would be even remotely comparable with the actual experience. Several people tried and failed"
  [BGG 2842314, 2022]. Expect the AI to feel weaker than humans at team play long before it is weaker
  at tactics; budget effort for round planning and team coordination.
- "Bots of Atlantis" (card-table bots, BGG 3607708): testers liked that "Every single hero feels
  different", but in 3v3 the bots played "like a computer player in a video game who keeps running into
  walls, but ends up winning anyway somehow"; one bot (Wasp) "often got stuck in places where she would
  barely move one space closer to the action"; the author is "still unhappy with the fast travel
  rules" and notes "opening moves are a lil' bit predictable". [OPINION] Lessons: movement must be
  goal-directed (H5 towards the projected next Battle Zone), Fast Travel needs real evaluation, and
  openings need variety (seeded mixing at Hard+, personalities at Easy/Normal).
- "Queller Bot" thread (BGG 3559857): a random-card bot is "a simulation of a very inexperienced
  player"; the canonical failure is spending the Red on turn 1 next to a threat ("you need to be aware
  of how much danger you're in and hold onto defensive cards accordingly, or the other team will pick
  you off"). One suggestion there, a bot that peeks at revealed human cards and "reconsider[s] its card
  choice", is a hidden cheat and is ruled out by ADR-035 point 3 (the AI never reads hidden cards).
  The other, "plays cards at random but always has a perfect knowledge of the card values. So it will
  not be wise but it will always be precise", is roughly our Easy minus its deliberate slips.

### 4.3 Natural-looking mistakes [OPINION]

- Sample from the top-k (k = 3) by the level's own score with temperature τ; never pick an option more
  than X coin-eq below the best (Easy X = 12, Normal X = 5).
- Use mistake *categories* rather than uniform noise: positional (modifiers, ranged radius), economy
  (wrong card to defend, spare spent), timing (initiative), lane (farming at match point instead of
  defending the minions). Easy draws from all four, Normal from none deliberately.
- Per-game mistake budget so it doesn't blunder twice in the same way in a row.
- Optional per-seat "personality" from the seed (aggressive: hero targets +20%; farmer: minions +20%)
  so AI seats differ.

### 4.4 Target strength [OPINION]

- Easy: loses to a careful player in their first games who knows the rules; still pushes when gifted.
- Normal: a casual player with a few games.
- Hard: an experienced group player.
- Expert: as strong as the time budget allows.
- Gap between neighbours: about 200 Elo (≈ 75% expected win rate), using the app's own Elo (ADR-011)
  over tournament results.

### 4.5 Testing that each level beats the one below

[OPINION] (PRODUCT §9: "each level beats the one below it over a seeded tournament")

- **Harness**: seeded self-play, Quick on Forgotten Island, 2v2. For every line-up drawn from the
  playable heroes, play it twice with the levels swapping teams and the Tie Breaker side, so hero and
  side strength cancel. 200+ games per pair of neighbouring levels.
- **Pass**: level k wins ≥ 62% against k − 1 with the 95% confidence interval's lower bound above 55%;
  Expert vs Hard may run with fewer games on CI (nightly).
- **Blunder suite**: positions built in code where one answer is clearly right (kill the lone Heavy at
  the last Wave counter; defend with the one sufficient card; don't Fast Travel the Red card when it is
  the only attack; step out of a ranged minion's radius; don't spend the Red on turn 1 next to an
  enemy that can card-kill you, BGG 3559857; don't take the centre Heavy mid-round when the push hands
  the defenders a two-minion advantage, BGG 2899258). Normal+ must pass all; Easy must pass the
  "never" list in 4.2.
- **Hero balance in line-ups**: tier lists on BGG disagree widely; the designer shared a community
  list "based on 300+ games" with the caveat "I don't believe it actually represents an accurate power
  level of each hero ... each player and player group tends to gel better with some heroes and not so
  much with others" [BGG 3481942]. The mirrored line-ups above cancel hero strength; don't compare
  levels across different line-ups.
- **Puzzles as tests**: the 50 puzzles (ADR-035) double as tactics tests: Expert should solve most,
  Hard many, Normal the easiest; a solved-rate drop is a regression.
- **Behaviour metrics per level** over the tournament: cards discarded per round, heroes lost, pushes
  won, Fast Travel with Red/Gold, turns spent in enemy ranged radius. Easy's numbers should be worse
  in exactly its designed categories.
- **Determinism**: same seed → same game (contract rule); keep AI randomness behind the seed.
- **No peeking**: a test that swaps the human's hidden cards (same public view) must give the AI the
  same choice.

---

## 5. Hero-specific notes for the AI

What each hero's cards need beyond the generic heuristics. Card ids are catalog ids. "Advice" quotes
are [ADV hero]. Forum notes, where found, are tagged [BGG n].

General rule for all of them: the engine resolves the text; the AI must *evaluate through the
engine* (forks, `DecisionContext` previews) rather than re-implement card logic. What follows is what
to look for, what to precompute, and which generic default is wrong for that hero.

Forum notes come from BGG "Hero Spotlight" and "Build & Go" threads. Many heroes changed in the 2nd
printing ("Mixing 1st and 2nd printing heroes is not recommended", BGG 3481942, designer), so notes
from 1st-printing threads are marked "(1st printing)" and kept only where they still match the
current card text. Spotlight authors are community players unless marked designer.

**Arien** (★; no tokens)
- Forum: "Sticking to Arien's primary red makes him a formidable melee brawler" [BGG 3487765]. The
  designer on his melee red (2020, 1st printing, same mechanism as today's line discard): "it is
  secretly an area denial which you trigger by attacking a minion and forcing the opponent to move
  away to avoid discard" [BGG 2432847]. Cancelling skills with
  the Silver "is cruicial for stopping a lot of the characters", e.g. Ignatia's Equilibrium and Wuk's
  tree-planting Silver [BGG 3487765].
- Gold `arien-gold` moves *another* unit adjacent to the target 1 space before the attack: rescue an
  ally, or move a minion to change Defense modifiers. Advice: "Use your gold card to move allies out
  of harm's way, or to set up future attacks."
- Silver `arien-silver` (init 13): "Enemy heroes in radius cannot perform skill actions, except on
  gold cards" this turn. Opponent model: enemies within radius 3 of an Arien holding it should
  discount their Skill cards ("force opponents to play around it while it's in your hand").
- Red I/IIa/IIIa: before the attack, an enemy hero in the 2/3/5 spaces in a straight line *behind*
  the target "discards a card, or is defeated": line geometry; a minion can be the target that hits the
  hero behind it.
- Blue: `arien-blue-1/2a/3a` ignore *all* minion modifiers (good among enemy minions, bad next to
  friendly ones; the preview shows it); `2a`/`3a` make him immune to every other enemy hero this
  turn/round (counters focus fire); `2b`/`3b` block Fast Travel and long moves. Advice: the red and
  blue branch "define your battlefield role the most".

**Bain** (★★; Bounty marker)
- Forum: setting up straight lines for his Red and Silver is the practical constraint [BGG 3500265].
- Gold `bain-gold`: after the attack, give an enemy hero in radius 4 the Bounty marker ("spends 1
  additional Life counter when defeated"). Raise that hero's kill value by one Life counter; markers
  return at the end of the round [RB p.8], so re-mark each round.
- Green I/IIa/IIIa are Block cards that work only "If a hero in play has a Bounty marker"; `1` also
  moves the marker to Bain. Keeping a bounty in play is part of Bain's defense.
- Blue `1/2a/3a` guessing game: see 3.11. Silver "Get over here!" pulls a unit or token in a straight
  line with no obstacles: drag an enemy hero into his team's reach. Ultimate: a newly bountied hero
  discards. Advice: "The psychological effect of the bounty marker should not be underestimated."

**Brogan** (★; durable, initiative stat 1)
- Forum: against Brogan, "If he is forking you and an ally unit and you can use your movement to block
  for both of you, do so. A wasted red from Brogan will allow your team a chance" [BGG 3479796]; as
  Brogan, a blocked charge on turn 3 can be followed by the Gold on turn 4 to take a minion or
  reposition [BGG 3503843].
- Red Mad Dash/Bullrush/Furious Charge: "Move 2 [2-3, 2-4] spaces in a straight line to a space
  adjacent to an enemy unit, then target that unit" (if he cannot make the move he cannot attack).
  Threat map = straight lines only; a blocked line is safety.
- Green Shield/Bolster/Fortify: this round a friendly minion in radius that is defeated is not
  removed if Brogan discards a Silver (Fortify: any basic) card; the enemy still gets the coins
  (RB p.12 example). Minion Battle projection must allow for it while he holds the card.
- Blue `2b`/`3b` punish an adjacent enemy hero "who has played an attack card this turn" (known after
  the reveal). Silver Bulwark: retrieve a card and make him and friendly units in radius 4 immune to
  enemy move/push/swap/place this turn. Ultimate: he counts as a heavy minion in the battle.
- Advice: "If opponents are focused on you, they aren't attacking your allies"; "Utilize fast travel
  and attacks with bonus movement to offset your low mobility." [OPINION] Brogan can afford to stand
  in exposed spaces (Defense stat 8): lower his exposure weight, raise his zoning value.

**Brynn** (★★★; obstacle counting)
- Forum: her Silver can create the 3-obstacle condition and then move that hero in the same play;
  "the easiest way to count this is if they have 3 or less empty spaces adjacent" [BGG 3456595].
- Nearly every card checks "a hero ... adjacent to 3 or more obstacles" ("You, other heroes, minions,
  tokens, and terrain are obstacles", `brynn-gold`). Precompute obstacle counts around every enemy
  hero; pushing enemies into crowds and against terrain powers her. Ultimate: every enemy counts.
- Advice: "When the condition isn't met, your cards still have an effect. The non-conditional part is
  less powerful but much more reliable." [OPINION] The AI should value the conditional branch only
  when the condition holds *at her initiative*, after earlier actors move.

**Cutter** (★★★; Zone borders, coins win)
- Forum (1st printing): a Green on turn 3 sets up a turn-4 Gold [BGG 2893527].
- Gold Walk the Plank: push an adjacent enemy hero up to 4; "if that hero is pushed into another zone,
  that hero discards a card, or is defeated", or defeat an adjacent minion. Precompute push lines that
  cross a Zone border. For enemies of Cutter: standing a few spaces from a Zone border in a straight line
  from him is a risk.
- Silver Grappling Bolt (init 5, range 5): pull himself to an obstacle in a straight line; advice:
  long moves at low initiative. Green Brace for Impact/Ramming Speed/Crashland: straight dash ignoring
  obstacles to an enemy hero, who discards.
- Blue `2b`/`3b`: the *enemy* chooses between being placed by Cutter and Cutter gaining 2/3 coins;
  `3b` adds "If you have 13 or more coins, you alone win the game". Coins pile up within a round
  (levels are bought only at its end) and after level 8 ("you cannot gain any additional levels, but
  can still gain coins", RB p.16). An AI facing this choice must check Cutter's coins + 3 ≥ 13 and
  then always choose placement.
- Ultimate: after a primary action, perform the primary of the card in the previous turn slot: card
  order matters (round plan).

**Dodger** (★; empty spawn points)
- Forum: take a minion to open its spawn point, "then follow through on turn 4 with your own gold to
  finish them off while they have no cards remaining" [BGG 3499269]; her range-3 Red is often saved
  "until turn 4 to finish you off" [BGG 3474317].
- Empty minion spawn points in the Battle Zone power her: Gold range when adjacent to one, Silver
  Death Trap (an enemy hero adjacent to one discards), Blue `1/2a/3a` +2/+2/+4 Defense with 2+ in
  radius 2, Green coins with 2+ in radius 3. Feature: empty spawn points near Dodger (every minion kill
  creates one). Enemies of Dodger: keep minions on spawn points (3.8), don't stand next to empty ones.
- Red: ranged option "Target a hero in range who has one or more cards in the discard" (heroes who
  defended are in range). Blue `2b`/`3b`: enemy heroes in radius 4 get −4/−6 Attack this turn: the
  threat map must include it while she holds the card. Green `2b`/`3b` respawn friendly minions.
- Advice: "Play your silver card on turns when you expect enemy heroes to attack minions" and "Your
  main weakness is your low defense" (Defense items).

**Emmitt** (★★★★; initiative as defense, reversed order)
- Forum: his skills are planned for both "forward and reverse time" (one build takes Initiative items
  to use them mainly in reverse time) [BGG 3606834]; his Silver is "one of your highest defenses" and
  repositions him, but "an enemy hero has control of where you go" [BGG 3459593].
- Red Temporal Punch/Slam/Judgment (Attack 9/11/12): "when defending, the enemy hero must use the
  Initiative value of their card and items instead of the Defense value" (minion modifiers still
  apply). DEFEND against it with high-initiative cards (Gold ~11, Blue ~10), not the usual Red; the
  engine preview must reflect this.
- Gold Reverse Time: "Next turn: Heroes with lower initiative act before heroes with higher
  initiative" (an open Active effect). The opponent model and the initiative heuristics (1.6) invert
  for that turn.
- Green: moves an enemy hero "who remained in the same space since the last turn" (standing still is
  punished); Blue: targets heroes "who [have] already resolved a card this turn" (Emmitt wants to act
  late). Silver (init 1, Defense/Skill): Glitch tokens, an enemy chooses which one he swaps with.
  Ultimate: plays two cards a turn and retrieves one unresolved.
- Advice: "The effect of your gold card impacts everyone, so be cautious not to endanger your allies."

**Garrus** (★★; discards fuel him)
- Forum: his primary Red "is made for those that want to play Garrus like a hunter seeking to finish
  up kills ... better against low defense heroes and turn 4 kills on injured heroes" [BGG 3526542].
  The designer, on Garrus against Tigerclaw: "All Tigerclaw's movement cards are movement 3+ up. Hence Garrus's Silver has strong effect on him"; "Hit him with your Pilum when he
  wants to Silver, Attack or to steal your hard-earned coins" [BGG 2886354].
- Gold Angry Strike: +1 Attack per card in his discard (advice: attack second). Red Trace/Chase/Hunt
  Down: move 1/2/3 before attacking a hero if he has a discarded card.
- Silver Chilling Howl (init 13): discard one of his resolved cards (fuels the Gold) and "This round:
  Enemy heroes in radius cannot fast travel, or move more than 2 spaces with a movement action":
  the strongest Fast Travel denial in the game. Advice: "very effective against heroes with high
  movement values."
- Blue moves enemy units away (Menace/Threaten/Terrify) or friendly units closer (Form Up/Testudo).
  Ultimate: each time a resolved card is discarded, perform its primary action (Silver combo).

**Gydion** (★★★★; spellbook)
- Forum: almost no strategy coverage; one poster notes he needs an important skill in round 1, usually
  in his own side Zone, where he can be caught [BGG 3493559].
- His deck casts spells from a spellbook; a spell must be *in* the spellbook to be cast and is removed
  faceup when cast; Silver Prepare Spells refills it ("Add all spell cards to the spellbook"). The AI
  as Gydion plans the Silver as a reload. Enemies can deduce the spellbook (all spells minus those cast
  since the last refill); the engine/determinizer treats its content as hidden (only Gydion looks).
- Threat map: Shield ("immune to basic attacks") and Invulnerability ("immune to non-basic attacks")
  this round remove whole attack classes; Power Word Kill defeats an enemy hero in radius 3 with no
  cards in hand; Energy Drain regains a Life counter; Sunburst and Dimension Door grow with spells
  removed.
- Ultimate: Wish; three casts win the game for his team (alternate win; enemies must race it).
- He has no Attack cards in his deck (his attacks are spells, which carry Attack icons). Whether
  Attack and Range items apply to spell values is a rules question for the engine to settle; the
  item rating (3.10) must follow the engine's answer.

**Hanu** (★★★; initiative manipulation, swaps)
- Silver Hurry up! (init 12, range 4): "Set the printed Initiative value of an unresolved card of a
  hero in range to 11". Force an enemy to act before it wanted to, or let an ally act earlier. The
  AI must re-evaluate `initiativeOrder` after it. Ultimate: Hanu "choose[s] the next action, and how
  it is performed, for a hero you target with the 'Hurry Up!'" (he controls an enemy's turn).
- Gold Fight and Flight (init 13): if the target survives, move 3 in a straight line if able (escape;
  advice: "as long as you have an escape route"). Red: ranged option on a hero adjacent to his
  friendly hero (setup by allies). Blue: swap with an enemy hero who is immune this turn and swaps back
  at end of turn.
- Advice: "It's often worth strategizing with your team, even if it reveals your plans."

**Ignatia** (★★★★; Tie Breaker coin)
- Forum (1st printing): predict ties, or stand where both texts have a target [BGG 2887471].
- Most cards have two texts keyed to the Tie Breaker coin's symbol ("Apply the text matching the
  symbol on the Tie Breaker coin"). The AI must predict the coin at her action: it flips after every
  cross-team tie resolved before her [RB p.7]. Silver Equilibrium (init 1): this round she picks
  either text. Ultimate: flip the coin to perform the action again.
- Magma tokens; Blue options remove/defeat an enemy minion adjacent to an enemy hero.
- Advice: "Always aim to have a target, regardless of which side the tie-breaker coin lands on."
  [OPINION] For PLAY_CARD, score her cards as the probability-weighted mix of both texts.

**Min** (★★; Smoke bomb, Mines)
- Silver: the Smoke bomb token blocks enemy targeting along straight lines through it. Green
  Poof/Vanish/Ruse are Block cards that work by swapping with the Smoke bomb in range: her defense
  depends on the bomb's position. Advice: "End movement in straight lines from enemy heroes to utilize
  your smoke bomb."
- Blue Mines are placed facedown, blast or dud: hidden information the determinizer samples; enemies
  moving through lose cards. Grenades: end of turn, adjacent enemy heroes discard (or are defeated at
  III).
- Gold applies the "After the attack" text of her resolved or discarded Red card (advice: works even
  if the Red was used for movement or defense). Green `2b`/`3b` double item bonuses this round.

**Misa** (★★; placement, one attack card)
- Advice: "You only have one attack card — make it count." Gold: place herself at maximum range 4,
  or defeat an adjacent minion. Silver Swoop In: place adjacent to two or more enemy units and retrieve
  a card (mobility and healing).
- Red: after the attack, +2/+3/+5 Defense this turn (she is safe to dive on the turn she attacks,
  against attackers after her). Blue: straight-line dashes ignoring obstacles that place or damage
  units moved through. Ultimate: choose two options on every "choose one".
- Advice: her blue and green choices set her role; "If your team is focused on winning the waves
  rather than fighting heroes, you probably don't need the 'Slash and Dash.'"

**Mortimer** (★★★; Zombie tokens)
- Forum: summon zombies "as soon as possible" (but not at the cost of the first round's minions) and
  "keep most of your zombies clustered near each other in the center, with 1 zombie closer to the side
  of the map you think the wave will push towards"; pack them tightly only for a big Gold, the alt
  Green, or a wall [BGG 3453397]. Tokens stay when the hero who placed them is defeated (RB p.19).
- Silver Awaken!: up to 4 Zombie tokens adjacent or "into spawn points in radius"; "Zombie tokens are
  not removed at the end of round". Zombies are obstacles: zone and block enemies (advice), but they
  block allies too. Tokens on spawn points are removed when a minion spawns there [RB p.8].
- Gold: removes zombies adjacent to the target for +1 Attack each (up to 3). Red: enemy heroes adjacent
  to zombies discard. Green `2b`/`3b`: replace an enemy minion adjacent to 2+ zombies with a zombie
  (a removal: lowers their minion count, can push, no coins). Blue `2b`/`3b` farm coins.
- Enemies of Mortimer should value Clear actions on zombies that trap them (3.4).

**Mrak** (★★★; Rock tokens, terrain)
- Forum: "at his best when the action comes to him staying in the middle zone"; "Heroes with the
  ability to ignore obstacles, place themselves at range, or swap with things will find it easier to
  avoid" him [BGG 3449043].
- Red Seismic Slam/Assault/Epicenter (radius 3-4): "An enemy hero in radius adjacent to terrain, or to
  a Rock token, discards a card, or is defeated". Enemies of Mrak: being next to terrain (map edges,
  rocks) is the exposure feature, not attack range. Silver Stone Grip places 3 rocks next to an enemy
  hero; Gold Fissure lays a line of 3 rocks.
- Red `2b`/`3b` Stone Carapace/Rock Solid: "If you would discard a card from your hand, you may
  discard this card instead; you may discard this card to perform its defense action, as if it was in
  your hand": his played card is an extra defense this round (H3 must count it).
- Initiative stat 1. Ultimate: rocks placed next to enemy heroes make them discard. Advice: area denial;
  the alternative red is durable, the alternative green fixes mobility.

**NebKher** (★★★★; Illusion tokens, other heroes' cards)
- Forum: his Gold copying enemies' previous actions pushes enemies to attack late (1st printing,
  [BGG 2896563]); the designer: "to play Nebkher competently you need to know everyone else's cards
  pretty well" [BGG 3459990]. The AI's catalog knowledge suits him.
- Gold Mind Grip (init 9, range 5): "Perform an action on the card in the previous turn slot of an
  enemy hero in range": his options are the enemies' previous-turn resolved cards (open). Silver
  (init 3) rearranges an enemy's resolved cards ("useful for your gold", advice) and moves him via
  Illusions.
- Blue `1/2a/3a`: "Name a color. Next turn, after playing cards: An enemy hero in radius discards a
  card of that color, if able." Use the deduced hand, minus the card the enemy will most likely *play*
  next turn (the effect applies after cards are played): name the colour of its likely spare.
- Blue `2b`/`3b` split the board for the turn. Green `2b`/`3b`: Illusions "count as both tokens and
  friendly melee minions" while he acts (check Defense modifiers through the engine's preview).
  Ultimate: every "laugh" makes all enemy heroes in radius 5 discard or be defeated.

**Razzle** (★★★★; multiple pieces)
- Forum: "Razzle will be at their most vulnerable early in a round"; "it is very important that you
  remain entirely unpredictable as Razzle"; "Teams with lots of discards and double discard abilities
  in particular are always gonna be strong against her" [BGG 3659764, 3613420].
- Gold Stunt Doubles: after the attack, "Spawn up to 3 more of you in radius; each of you is the same
  hero, except when actions are performed. If defeated, remove all of you." Enemies may target any
  copy; one defeat removes all. Advice: "Your survival often relies on keeping your clones within the
  radius of your silver card" (Silver Crowd Control as defense: +2 Defense per other copy in radius 3;
  as a skill: removes the other copies).
- The AI as Razzle chooses which copy acts (CHOOSE_UNIT) and keeps copies clustered; as an enemy, it
  attacks the most isolated copy. Advice: only one of her is placed at the start and on respawn.
- Red `2b`/`3b` retrieve cards per copy in radius. Ultimate: another copy repeats her basic attack.

**Rowenna** (★★; coins and retrieval for allies)
- Forum: her Gold lets both heroes retrieve first, so it is "at its best when used to initiate,
  hopefully drawing a blue or red card from an opponent's hand"; "she relies on silver and if you can
  predict when this will be and get out of range, she is forced to hold" [BGG 3447239].
- Gold Code of Chivalry: before attacking a hero, "both you and the target may retrieve a discarded
  card". Advice: best "when they have a low-defense card in their discard".
- Silver Throw the Gauntlet: place herself next to an enemy hero in range 4; if it steps away she gains
  2 coins. Red cards give coins or retrievals to a friendly hero in radius: stay near allies
  ("Talk to your teammates to maximize the impact of your red cards").
- Blue `2b`/`3b`: "Defeat an enemy hero adjacent to you with no cards in hand" (turn-3/4 hunter; track
  hand counts). Blue `1/2a/3a` swap with a friendly unit in trouble. Ultimate: all her attacks become
  ranged at range 2.

**Sabina** (★; minions)
- Forum: the designer: "She is in the top 3 of lane pushers in the game and the top 1 when it comes to
  second push wins" [BGG 2889623, 1st-printing spotlight]; a 2nd-printing poster: her alt red "is
  literally the only card in the game that can remove two minions at tier II" [BGG 3481942]. "Read the
  wave carefully, and don't get caught out of position when it pushes into a new zone" [BGG 3461122].
- Advice: "You become considerably stronger when you are near friendly minions. Stay in formation!"
  Silver Back to Back swaps her with a friendly minion in radius 2 (escape or approach). Blue swaps two
  minions: swapping an enemy melee minion beside an ally with a friendly one changes that ally's Defense
  by 2.
- Red Quickdraw/Gunslinger/Dead Shot: +3/+3/+4 Attack "if the target played an attack card this turn"
  (decided after the reveal). Red `2b`/`3b` also remove enemy minions adjacent to her (counts drop, no
  coins).
- Gold Point Blank Shot pushes an adjacent target 1 (advice: "a great defensive counter to non-ranged
  attacks", it acts at 12). Advice: "Your choice of red card affects your preferred win condition the
  most."

**Silverarrow** (★★; fragile sniper)
- Forum: 2nd-printing players rate her mobility highly ("Unbelievably mobile", BGG 3481942). (The
  1st-printing guide's "her cards don't work if an enemy is adjacent" no longer matches the card text.)
- Defense stat 1: highest exposure weight; keep distance (advice). Red options target "a unit in range,
  which is not adjacent to any other unit" (isolation) or at maximum range: precompute exact-range
  rings.
- Gold Shoot and Scoot: after the attack, may Fast Travel to an adjacent Zone (escape if no enemies
  in either Zone). Silver Trailblazer: Fast Travel, and this round she and friendly heroes in radius
  ignore obstacles on movement actions. Blue `1/2a/3a` deny Fast Travel and long moves in radius;
  `2b`/`3b` end-of-turn discards.

**Snorri** (★★★★; Rune markers per turn slot)
- Silver Inscribe the Runes places 4 Rune markers under his turn slots; "A rune is active as long as
  it is below the turn slot matching the current turn"; they stay through rounds and defeats. So the
  active rune on every turn is open and fixed: the AI plans which card to play on which turn by rune
  (e.g. axe turn: Gold +3 Attack). Advice: "Readjusting rune placement costs you an action."
- Green Oaths are Block cards whose block depends on the active rune (horn: basic attacks; axe:
  non-ranged; bird: ranged; anvil: non-basic) and grant immunity this turn: H3's block check is per
  turn. Ultimate: a second active rune per action.

**Swift** (★★★; turn-slot order)
- Forum (1st printing, same idea as today's Reload): "sequencing red into gold is your only way of
  getting two attacks in a round you are predictable and a clever enemy will move in to threaten you on
  the turn you wish to repeat your red" [BGG 2889490]. As Swift, vary the order; against Swift, threaten
  her before the Gold turn.
- Gold Reload! (init 6): "Perform the primary action of your rightmost resolved card", or defeat an
  adjacent minion. The round plan should put a strong card right before the Gold. Silver Bounce
  repeats when already resolved (combo with Reload).
- Red Snipe/Prepared Shot/Killshot need a target at maximum (or in-) range "in a straight line":
  exact geometry. Green Suppress/Pin Down/Killing Ground hit an enemy hero "not adjacent to terrain":
  against Swift, standing next to terrain is the safe square (the opposite of Mrak). Blue `2b`/`3b`
  place Swift at end of turn (out of reach of the next turn's planned attacks).
- Advice: threaten a hero and a minion at once; the rifle pushes, the shotgun fights heroes; "it's
  better to use your gold card to defeat an adjacent minion" sometimes.

**Takahide** (★★★★; three Gold cards, ally card swaps)
- Forum: "Taka benefits a lot from being within range of his teammates, which is typically 3 or 4
  spaces" [BGG 3451226]: keep him near allies (H5 should measure distance to teammates for him).
- Three Golds (Float like a Butterfly: Movement, Defense 8; Sting like a Bee: ranged at maximum range;
  Strike like a Tiger: Attack 7), only one in hand until his Ultimate; Gold and Silver swap it with the
  deck, facedown if resolved or discarded. Which Gold he holds can be hidden: the determinizer samples
  it. Advice: "try to have the exact gold card that you need in your hand at the exact time".
- His Red cards are support cards with Defense 1-2 (high initiative 11-12): after the attack, a
  friendly hero in radius may swap their *unresolved* card with a card in hand (an ally changes its
  card after the reveal). [DATA] The "keep Red as the spare" default (1.5) is wrong for him; Float like
  a Butterfly (Defense 8) is his defender.
- Green/Blue need allies with cards in the discard (advice: work together). Handicap card Shameful
  Display reads "(You are defeated.)": never defend with it.

**Tali** (★★★; Ice and Totem tokens)
- Ice tokens: "Enemy heroes have -1 Initiative for every Ice token they are adjacent to" (initiative
  prediction must include it). Totems: a friendly minion adjacent that would be defeated survives and
  the Totem is removed instead (Minion Battle projection, kill planning).
- Gold Ice Blast: if the target discards to defend, another enemy hero in radius discards a card of the
  *same colour* (deduced hands tell which colours both still hold). Silver Commune with Spirits: "A
  friendly hero in play names a color"; table talk forbids suggesting it [RB p.4], so the naming
  teammate decides alone.
- Red Winter Dagger/Spear/Scepter: +3 Attack "if this card is not in the discard" (normal plays get
  it). Advice: "Know when you can afford to discard a card"; "Your basic attack benefits from attack
  items more than most."

**Tigerclaw** (★; Block-only defense)
- Forum: the designer (2026): "Your gold dodges or ties with any card. ... Your blues either block or
  dodge any attack" [BGG 3688691]; and "With no options to heal and his tricky escape cards,
  Tigerclaw really hates discard" [BGG 2886354]. Ranged heroes and movement caps are his counters.
- His Blue cards have **no Defense value**: they only Block ranged (Dodge/Sidestep/Evade) or non-ranged
  (Parry/Riposte) attacks. He holds one Blue at a time, and the deduced hand shows which, so the right
  answer is to attack him with the *other* attack type. Defense stat 1-4 otherwise.
- Gold Blink Strike (init 13): "Move 2 spaces in a straight line through an enemy unit; target that
  unit" (needs the landing space): advice "Enemies can deny your basic attack by blocking a crucial
  space." Silver: next to terrain, place himself in radius 2 and be immune to attack actions next turn.
- Green steals coins or gives a Poison marker (−1/−2 Initiative, Attack, Defense until the end of the
  round). Red Backstab: +2 if a friendly unit is adjacent to the target. Advice: "The best defense is
  not to get hit at all"; "Ranged attacks and discard effects are the main threats to you."

**Trinkets** (★★★; the Turret)
- Forum: find the position to hold, then play the Silver to put the Turret down "after everyone else's
  cards have already resolved"; "don't remove your turret with silver without your gold available to
  replace it in the next turn or two" [BGG 3611965].
- The Turret is placed by the Gold or Silver, "counts as an obstacle", "is not a token and remains in
  play between rounds" (`trinkets-handicap` reminder): Clear does not remove it. Red attacks need a
  target "in range of both you and the Turret" (+2/+3 Attack when in a straight line from both): two
  origins in the threat map.
- Blue Disruptor: before an enemy hero in radius of the Turret performs a primary action it discards
  (enemies may prefer secondary actions or Hold). Barrier tokens: +1 Defense to friends adjacent.
  Green `2b`/`3b`: discards around the Turret, then it is removed.
- Advice: "Plan ahead when placing your turret to avoid spending actions on moving it each round"; "Use
  the turret to tactically block key spaces."

**Ursafar** (★★; Enraged)
- Forum: the designer's sequencing: "Use red to move 3 spaces to a minion, then gold to defeat it, then
  green to move, then silver to repeat gold and attack a second target (ideally position to threaten a
  hero)"; "~300 recorded games and the win rate above 50%" [BGG 3513251]. Community: his Gold is "nearly
  unavoidable, ensuring we can enrage with it early or finish with it on turn 4" [BGG 3653998].
- "This round: You are enraged" is on most Gold, Silver, Red and Blue cards; Green cards and many
  bonuses work only "If enraged". Round plan: enrage first, then the Green. Advice: "the best way to
  fuel your rage is by attacking a minion with your red or gold cards. Use your blues as a backup".
- Red: after the attack, if enraged and the target was not removed, remove enemy minions in radius
  (counts drop); `3b` Tear: +1 Life counter on a hero kill and coins. Very low movement on Blue (1):
  "You excel when enemies come to you; don't waste precious movement on pursuits."
- Ultimate: always enraged and all resolved cards count as active (Silver performs an active card's
  primary).

**Wasp** (★; lock-down)
- Forum: "easy to build improperly leaving you as a Jack of all Trades and Master of None" [BGG
  3502775]; her block green makes her immune to ranged attacks while in hand, "but remember that this
  only works against ranged, so think about who is your biggest threat" [BGG 3466028].
- Silver Static Barrier (init 13): this turn, spaces in radius 2 are obstacles for enemies outside and
  spaces outside are obstacles for enemies inside: a cage or a wall. Movement reach of every enemy must
  be recomputed under it.
- Gold Magnetic Dagger: after the attack, enemy units in radius 3 "cannot be swapped or placed by
  themselves or by enemy heroes" this turn (stops placement escapes: Misa, Arien, Hanu, Whisper).
- Green are Block cards against ranged attacks only (no Defense value): attack her in melee. Blue `2b`/
  `3b` push two adjacent enemies 3-4 spaces, with a discard if stopped by an obstacle. Advice: "you
  cannot afford to absorb repeated damage or high-value attacks, so choose your targets wisely".

**Whisper** (★★; empty spawn points, enemy discards)
- Forum: "Whisper can be an incredible initiator or a very effective finisher, but rarely can he do
  both in one round" [BGG 3589436].
- Green places him into an empty minion spawn point in the Battle Zone (a teleport into the fight);
  Red pre-attack movement scales with empty spawn points in radius.
- "If an enemy hero in radius has a card in the discard" powers the Silver (immunity or retrieval) and
  Blood Fury/Frenzy repeats (up to five times at III): once enemies have defended, he snowballs. Gold:
  a hero with an empty discard is hit and then moved full distance in a straight line by the card it
  defended with.
- Blue `2b`/`3b` make the *enemy* choose: discard (III: or be defeated) or let her defeat minions (III:
  up to 3). The AI facing it compares the card against the minion swing (push risk at 1.2). The
  opponent may pick an option that has no effect (reminder text "Any option can be chosen, even if it
  would have no effect", `whisper-blue-2b`).

**Widget and Pyro** (★★★; Pyro counts as a token)
- Forum: "you need to be playing other things before your Gold for it to truly matter" (it re-performs
  a *resolved* Skill card, not a discarded one, on a different target); Pyro on a spawn point is
  removed when a minion spawns there [BGG 3454879].
- Silver places Pyro; "Pyro counts as a token, but is not removed at the end of round", so a Clear
  action removes him (advice: "keep him out of trouble"). Enemies of Widget should Clear Pyro when it
  costs little.
- Green Breath cards hit an enemy hero "in range of Pyro and in a straight line from Pyro": Pyro's
  position is the threat origin. Blue swaps Pyro with Widget or an ally (teleport). Green `2b`/`3b`
  remove/defeat a minion adjacent to Pyro and remove Pyro.
- Gold Fight as One: after the attack, perform the primary action of a resolved skill card on another
  target: play a skill early in the round, the Gold later. Advice: "Winning with this duo comes from
  properly positioning Pyro and timing your gold attack."

**Wuk** (★★★; Tree tokens)
- Forum (1st printing): trees next to enemy minions and on chokepoints [BGG 2894009].
- Silver places up to 3 Tree tokens that "are not removed at the end of round": obstacles, space
  blockers, and ammunition (Gold removes an adjacent tree to make a ranged attack; Red targets units
  adjacent to a tree; Blue removes a tree to retrieve a card).
- Red `2b`/`3b` Trample/Angry Stampede: a straight move ignoring obstacles; enemy heroes moved through
  "discard a card, or [are] defeated" and minions moved through are defeated. Blue `2b`/`3b`: enemy
  minions adjacent to Wuk "do not count towards the minion total during minion battle" (projection).
- Initiative stat 1, Defense 8. Advice: "Plan ahead. Where will you need those trees next turn?";
  "move along when the wave is about to push."

**Xargatha** (★; enemy units around her)
- Forum: signature play: stand next to your ranged minion and the enemy's centre minion, "play silver
  to draw the enemy next to the twins and in range of your ranged minion, hopefully giving them a net
  -2 defense from minion modifiers. Then finish the center minion and the enemy hero with gold" [BGG
  3515164]. The designer: if the Silver's target cannot end adjacent to her, it does not move at all
  [BGG 3459245].
- Red Threatening Slash/Deadly Swipe/Lethal Spin: +1/+2/+3 Attack "for each other enemy unit adjacent
  to you" — while each adjacent enemy melee minion lowers her Defense by 1: her position score must
  trade the two (advice: "be careful not to overextend").
- Silver Siren's Call (init 3): pull an enemy unit up to 3 spaces to her. Blue Stone Gaze/Petrifying
  Stare/Turn into Statues: "Next turn: Enemy heroes in radius count as both heroes and terrain, and
  cannot perform movement actions" (no Fast Travel either: "If you are prevented from moving or from
  taking a Movement action, you could not use a Movement action to Fast Travel", goa2-faq common
  ruling; RB p.11). The opponent model must drop movement options for them.
- Green `2b`/`3b`: end of round, defeat an adjacent enemy melee (or ranged) minion before the Minion
  Battle. Gold Cleave repeats on a different enemy hero. Ultimate: +1 Movement and Initiative per
  adjacent enemy unit.

---

## 6. Sources

- Official rulebook, 2nd printing (source 5, pinned by SHA-256 `2c19b11d…`), text layer at
  `D:\.goa-qa\rulebook-text\rulebook.txt`; pages cited as printed page numbers.
- Card texts and values: `D:\.goa-qa\ref\card-texts.txt` (632 records, same data as the catalog);
  statistics in this document computed from it (scripts kept outside the repo).
- Hero advice: source 3, yvo-niedrich/goa2-faq @ `b841b76c` `build/data/heroes.json` (`advice`), and
  `build/data/faqs/common.json` rulings.
- Forgotten Island map: `app/src/main/assets/catalog/maps/forgotten-island.json` (Zones and spawn
  points).
- Community engine goa2-backend (`D:\.goa-qa\ref\goa2-backend`, read only): contains no AI or
  heuristics code or notes (searched for AI, bot, heuristic, strategy, evaluation).
- BoardGameGeek forums (BGG thing 267609), read 2026-10-24 through
  `api.geekdo.com/api/forums/threads` and `api.geekdo.com/api/articles?threadid=<id>` (all article pages
  read; threads 2887548, 3481942, 3513251 have 3 pages, 3078548, 2432019, 3762487 have 2). All 81
  Strategy-forum threads were listed and the relevant ones read in full; the General, Rules and
  Variants forums were scanned (about 150 threads in all; 2994088 failed to download). "Designer" means the BGG
  account `nichik`, tagged as the game's designer on BGG; posters named in the rulebook's playtester
  credits (RB p.22) are called playtesters. Every quote in this document was checked against the
  downloaded thread text. Threads cited:
  - Macro strategy: 2899258 "Ruling the Waves" (designer, 2022); 3033553 win-condition statistics
    (designer, 2023); 3491194 wave counters and lives; 3168047 stalling and defenders; 3080063
    comebacks; 3472633 Ultimates and levels (designer); 2432019, 2521254 (designer, 2020); 2887548
    "Reference sheets, and why you don't need them" (designer posts); 3590819 level-up secrecy
    (designer, 2025); 3499394 double-lane tie (designer); 3439410 range check, 3478009 ranged subtype
    (designer).
  - Essays: 2894296 "Fundamentals — Sequencing and Card Kills" (anwei); 3092335 "Fundamentals — The
    Roles of Card Colors" (Panetierre); 2897477, 2898177, 2898634 position structures (Panetierre);
    3463167 "How to predict card values (2nd printing)" (Panetierre); 2908021 "Level up strats?";
    3499405 "Attack card strategy"; 3474863; 3511053 (forks).
  - AI and bots: 2842314 solo mode (designer); 3607708 "Bots of Atlantis"; 3559857 "Queller Bot".
  - Heroes (2nd printing unless noted): 3487765 Arien; 3493559 (Gydion note); 3479796, 3503843 Brogan; 3459245,
    3515164 Xargatha; 3466028, 3502775 Wasp; 2889623 Sabina (1st, designer), 3461122 Sabina; 2886354
    Tigerclaw (1st, designer posts), 3688691 Tigerclaw (designer, 2026); 3474317, 3499269 Dodger;
    3589436 Whisper; 3653998 Ursafar, 3513251 (designer posts on Ursafar); 3526542 Garrus;
    3500265 Bain; 3611965 Trinkets; 3453397 Mortimer; 3459593, 3606834 Emmitt; 3659764, 3613420
    Razzle; 3451226 Takahide; 3454879 Widget; 3456595 Brynn; 3447239 Rowenna; 3449043 Mrak; 3459990
    complexity (designer, NebKher); 3481942 2nd-printing tier lists; 1st printing: 2432847 Arien
    (designer), 2893527 Cutter, 2896563 NebKher, 2894009 Wuk, 2889490 Swift, 2887471 Ignatia,
    2891788 Silverarrow.
- Not found on the forum (gaps, so the rules-derived analysis above stands alone): respawn-location
  choice, ranged-minion tactics, Fast Travel blocking as a deliberate tactic, Pity-coin play, Snorri
  and Gydion strategy.
