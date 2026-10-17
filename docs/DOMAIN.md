# Domain

Rules below were audited on 2026-10-17 against the **official Wolff Designa rulebook PDF**
(RulesPal used as a cross-check: it mirrors the PDF except the 6-player Pick and Ban row) and
BGG designer posts. Page refs `p.N` are the PDF's printed pages. `[RB]` = rulebook, `[BGG]` = forum,
`[APP]` = our own design choice, not a game rule. Use official terminology in UI copy.

## Sources (catalog import — ADR-009)
All heroes from all products, **2nd printing** (current). Pin each source to a commit SHA
in `tools/catalog-import/sources.lock.json`; fetch from `raw.githubusercontent.com` (the
GitHub API allows only 60 unauthenticated requests per hour; the importer makes one cached
tree listing per pinned SHA). None of these repos has a licence; content belongs to Wolff
Designa. Fine for this private build, never redistribute. Facts below verified at the pinned
SHAs on 2026-10-17.

| # | Source | Use for |
|---|--------|---------|
| 1 | **Stats-of-Atlantis** — github.com/QuasiStellar/Stats-of-Atlantis: `src/new_heroes.json` (2nd printing, 32 heroes, 610 cards incl. Handicap cards and Takahide's 2 extra golds), `src/spells.json` (Gydion's 22 spells), `src/states.ts` (title, ★, pack, all 2–6 roles incl. Healer, [base,max] stats in A/D/I/M order), `src/lib/images/cards/<hero>/<Slot>.webp` (card illustration without frame, e.g. `RedIIA`) | **Primary** card data and hero metadata; artwork used to identify scans. Known errors at the pinned SHA: Hanu Movement, two stray commas (fixed in overrides) |
| 2 | **dmrediger/goa2** — `public/hero-cards/<pack>/<hero>/<hero>-<n>.jpeg` (729×1026; 0 = hero card, highest = card back; deck order varies by hero, so scans are matched by artwork), `…/gydion/gydions-spellbook/<level-N\|ult>/spellbook-<n>.jpeg` | Full card images (bundled); visual check of every disputed value |
| 3 | **yvo-niedrich/goa2-faq** — `build/data/heroes.json` (lore, advice, stats, roles), `build/data/cards/<hero>.json` (+ `gydion_spellbook.json`), `build/data/faqs/<hero>.json` + `common.json`, `public/hero/<hero>.png` (portraits). No mojibake at the pinned SHA (repair kept). | Cross-check text; lore, **FAQ rulings**, portraits. Wording sometimes paraphrased or misspelt; card names differ for 4 cards |
| 4 | **colefin8/guards-of-atlantis-ii-data** — `server/<Hero>.csv` (no Ignatia, Whisper; `Min.csv` holds another hero's cards; Range vs Radius item labels unreliable; some values shifted) | Cross-check numbers |
| 5 | **Official rulebook PDF**: "latest version of the rules" linked from wolffdesigna.com/products/guards-of-atlantis-ii-1 (Google Drive id `1qceAxzEcdkMYP-00F61wNiUiiyj6iJAc`, 24 pages, text layer). The rules authority. | Rules, terminology |
| 6 | BGG via `api.geekdo.com` JSON (HTML and XML API are blocked): IDs, "(2nd Printing)" hero spotlight threads | Strategy notes, roster check |

`verified: true` = numeric fields agree between sources 1 and 4 (or 1 and the card image)
**and** text agrees between 1 and 3. Disagreements go to `REPORT.md`; resolve them in
`tools/catalog-import/overrides.json`, never by editing generated files. A disagreement counts
as agreement once the card image has decided it (patched or `confirm`ed with evidence); heroes
without usable source-4 data are verified via `imageChecks` (Whisper, Ignatia, Min).

### Roster — 32 heroes (titles per BGG/Wolff; source 1 spells "Wuk the Grove Keeper", "Emmitt the Traveller", "Widget and Pyro": store those as aliases)
| Product (BGG id, year) | Heroes (★ complexity) |
|---|---|
| Core (267609, 2022) | Arien the Tidemaster ★, Brogan the Destroyer ★, Tigerclaw the Cutpurse ★, Wasp the Warmaiden ★, Sabina the Commander ★, Xargatha the Changed ★, Dodger the Warlock ★ |
| Devoted (356292, 2022) | Misa the Samurai ★★, Whisper the Outcast ★★, Ursafar the Savage ★★, Silverarrow the Pathfinder ★★, Tali the Spiritcaller ★★★ |
| Defiant (356290, 2022) | Garrus the Gladiator ★★, Bain the Bounty Hunter ★★, Cutter the Sky Pirate ★★★, Trinkets the Scavenger ★★★, NebKher the Harbinger ★★★★ |
| Renowned (356291, 2022) | Min the Dragonmonk ★★, Swift the Sharpshooter ★★★, Wuk the Grovekeeper ★★★, Hanu the Trickster ★★★, Ignatia the Mad ★★★★ |
| Arcane (412138, 2025) | Rowenna the Vanguard ★★, Mrak the Rockshaper ★★★, Snorri the Runescribe ★★★★, Razzle the Ringmaster ★★★★, Gydion the Archwizard ★★★★ |
| Wayward (412139, 2025) | Brynn the Seeker ★★★, Mortimer the Awakener ★★★, Widget the Dragonbonded (with Pyro) ★★★, Takahide the Warlord ★★★★, Emmitt the Traveler ★★★★ |

Official roles (10, p.21): Damager, Disabler, Durable, Farming, **Healer**, Melee, Pusher, Sniper,
Tactician, Tokens. A hero has 2–6 role icons.
**Exceptions:** Gydion has a 22-card spellbook; Takahide has 21 cards instead of 19.
**Printings:** 1st (2022, 22 heroes; every hero later changed by the Update Pack), 2nd (2025,
current; small watermark bottom-right on cards), **3rd announced** (Gamefound project 11755,
2027: two new packs ≈ 10 heroes, reworks rumoured for Sabina, Cutter, Tigerclaw, Wasp) —
expect a data refresh. **Maps:** the rulebook names none. The core board is double-sided:
a single-lane map and a double-lane map. Names like Forgotten Island / Magma and Ice / Across the
River / Narrow Passages / Vexing Cliffs come only from goa2-backend (unverified); `Match.map` is
free text.


## Game structure (drives the timer)
- **Team Red** (Atlanteans) vs **Team Blue** (Titans), one hero per player [RB]. Each hero
  is one physical set, so a hero is picked at most once per match.
- Players: 4 or 6 recommended; up to 10 and odd counts are allowed for experienced groups.
  2 players = 2 heroes each; 3 players = most experienced controls 2 heroes, one with a
  Handicap card; with 5/7/9 the larger team's heroes swap a basic card for their Handicap
  card [RB] (p.20). The official **3-player** setup is a double pilot **plus** a Handicap card
  on one of that player's heroes. Extended counts (odd, 8–10) are for groups where everyone
  has played at least 5 times (p.3). Handicap cards may also be used at even counts to
  calibrate skill (any seat may carry the Handicap flag).
- **Double piloting** [APP — owner's group]: with an odd count the group often evens the
  teams by having one player control two heroes. Match setup offers for 5/7/9: *Handicap*
  (uneven seats, official) or *Double pilot* (even seats, one player owns two, house rule);
  3 players default to the official double pilot + Handicap.
- A **round = exactly 4 turns** [RB]. Each turn:
  1. **Play one card** — simultaneous, face-down. You must play a card if able (even with your
     hero off the board); with no cards you announce a pass. Strategy talk is allowed from the
     end of one turn until the reveal: out loud only, no notes, no showing cards (reading your
     own cards aloud is fine) (p.4). *Main timed phase.*
  2. **Reveal** — all at once; table talk stops.
  3. **Resolve** — sequential: highest initiative acts first; after each resolution the next
     actor is the highest *unresolved* initiative (re-checked every time). Cross-team ties:
     the team shown on the **Tie Breaker coin** acts first, then that player flips the coin.
     Same-team ties: the team decides (without discussing why), coin not flipped. A hero off
     the board may respawn before acting. *Optional per-action timer.*
  4. **Turn ends** — "this turn" effects end.
- **End of round** (in order) [RB p.8]: 1 Minion Battle · 2 Remove Tokens, Return Markers ·
  3 Push the Lane (if applicable; flip a Wave counter) · 4 Retrieve Cards · 5 Level Up
  (mandatory; buy every level you can afford; simultaneous) · 6 Collect a Pity Coin (1 coin
  if you didn't level up; it can't be spent on a level this round). The app shows these as a
  tap-through checklist (Upgrade timer on step 5). A lane can also be pushed mid-turn (last
  enemy minion in the Battle Zone defeated or removed), and **a match can end mid-turn**, so
  "End match" must be reachable from every phase.
- **Tie Breaker coin:** flipped during setup and placed by the board; the app tracks *which
  team's symbol shows*, not who holds it. It also breaks ties for simultaneous effects and
  minion placement (not flipped in those cases).
- Hand: 5 cards per round (one per colour) for 4 turns; Defense is played by discarding from
  hand and doesn't use your turn card [RB].

### Match setup and win conditions [RB]
| Mode | Wave counters | Life counters per team |
|------|---------------|------------------------|
| Quick (single lane) | 3 | 4 (2–5 players), 5 (6 players) |
| Long (single lane) | 5 | 6 (2–5 players), 8 (6 players) |
| Double lane (long game; **required at 8+**, allowed at 6–7) | 2 × 7 (one set per lane) | 6 (6–8 players), 7 (9–10 players) |

2 players use the 4-player setup; 3 and 5 players use 4-player Life counters (p.20). The
rulebook gives **no single-lane counts for 7+ players** (the app shows "not specified").
[APP] Counts are looked up by the number of **heroes in play** (seats). This reproduces every
official case (2 or 3 players = 4 heroes; 5 players with Handicap = 5 heroes → 4-player
counts) and gives the owner's double-pilot house rule (5 players as 3v3 = 6 heroes) the
6-player counts. Rulebook first-game advice: play a Quick game if anyone is new.

Win conditions (`WinCondition` enum): **THRONE** — a push makes the enemy Throne Zone the
Battle Zone; **LAST_PUSH** — the last Wave counter flips, the team that won that push wins
(double lane: more total Zones between own Throne and both Battle Zones); **LIFE_COUNTERS** —
a team spends its last Life counter (a defeated hero costs counters = its lowest card tier:
1 at levels 1–3, 2 at 4–6, 3 at 7–8); **OTHER** (concession, house rule). If both teams would
win at once from a push, the push is undone and play continues. The rulebook has no draw
outcome (and is silent on a tied double-lane Zone count), so the app has no draws.

### Timer phases [APP — owner's table rules, 2026-10-17]
The rulebook sets no timers. The group plays with **rule timers per phase**, all editable per
preset (enable/disable, duration, cues, auto-advance):

| Phase | Scope | Default | Notes |
|-------|-------|---------|-------|
| **Pre-round strategy** | once per round, before turn 1 | **1:30** | Team talk before cards are played |
| **Turn** (play a card) | each of the 4 turns | **0:30** | Simultaneous card selection |
| Action (optional, off) | per hero during resolution | 0:30 | Tap "next" as each hero resolves |
| **Upgrade** | end of round, Level Up step | **2:00** | Spend coins / choose upgrades |

The end of round is still shown as the six-step checklist (see above); the Upgrade timer runs
on step 5 (Level Up). Users can add custom phases with the same three scopes
(`ROUND_START`, `TURN`, `ROUND_END`). Overtime counts up; nothing is auto-enforced.

## Cards and heroes [RB]
- **Deck:** 18 cards = Gold + Silver (basic, never upgraded) + 3 × Tier I + 6 × Tier II +
  6 × Tier III (Red/Green/Blue, two options per colour per tier) + 1 Tier IV **Ultimate**
  (Purple), plus a separate **Handicap** card (H) — 19 in total. Exceptions listed above.
- **Card fields:** name, initiative, colour letter (D/S/R/G/B/P), tier, primary action
  (Attack, Skill, Defense, Movement, Defense/Skill), secondary actions (Movement, Defense;
  Hold on any card; Fast Travel replaces Movement; Clear replaces Attack), values
  (Attack, Defense, Movement, Range, Radius), Ranged flag, Block ("!") on Defense, card text,
  item icon on Tier II+ (the stat the card gives when tucked as an item: +1).
- **Levels 1–8**, costs 1–7 coins per step; max tier II at levels 2–4, III at 5–7, Ultimate
  at 8. Each level: upgrade lowest-tier colour; of two next-tier cards one goes to hand, the
  other becomes an item. All colours reach II before any reaches III.
- **Hero Profile card** (p.21): Attack, Initiative, Defense, Movement bars from 1 ("Very
  poor") to 8 ("Exceptional"); brighter sections are conditional (level-up choices, card
  text) and are stored as [base, max]. Complexity: ★ Challenging, ★★ Complex, ★★★ Very
  Complex, ★★★★ Wizard. 2–6 role icons. Descriptive, not used in play.
- **Coins** (pp.13–16): defeating a hero pays its level in coins; teammates get assist coins
  equal to its lowest tier; melee and ranged minions 2, heavy minions 4. Reaching level 8
  costs 28 coins in total.
- **Starting hand:** Gold, Silver and the three Tier I cards; the Handicap card goes back to
  the box unless used (p.6).

## Core model (`core:model`, code is authoritative)
Catalog types are `@Serializable` and shared by the importer and the app
(`core/model/.../catalog/Catalog.kt`). Shared user-data types:
```
Hero(id, name, title, product, aliases, complexity?, stats: HeroStats([base,max] per stat),
     roles: List<Role>, portrait?, printing, cards, handicap?, extras, faq: List<Ruling>,
     lore?, verified, sources)
Card(id, heroId, name, color: GOLD|SILVER|RED|GREEN|BLUE|PURPLE|OTHER, tier: 1..4|null,
     initiative?, primary?, secondary, values: CardValues(attack, defense, movement, range,
     radius), ranged, block, item: Stat?, text (inline markup, see CardText), image?)
Player(id, name, color: ARGB, avatarId, createdAt, archived)   // name unique, see below
Match(id, createdAt, playedAt, status: PENDING|COMPLETED|ABANDONED, mode: QUICK|LONG|DOUBLE_LANE,
      map?, winner?, winCondition?, durationMin?, notes, draftId?, participants)
Participant(playerId, heroId?, team: RED|BLUE, handicap)   // one per hero seat
DraftRecord(id, createdAt, methodId, methodKind, pool, teamA, steps: (team, PICK|BAN, heroId), matchId?)
```
Engine-specific types (timer presets, draft methods/sessions, Elo config) live with their
engines in `core:domain`.
- **Card text markup** (`CardText`): `{icon}` inline game icon, `~(…)` reminder text, `
`
  paragraph break; everything else is printed text verbatim.
- IDs: catalog = permanent kebab-case slugs (`"arien"`, `"arien-red-1"`); user data = UUIDs.
- **Player names are unique** [APP], compared trimmed and case-insensitively, archived
  players included (`PlayerNames`), so one person never has two ids and two split ratings.
  `PlayerRepository.ensurePlayer` (draft setup's quick add) reuses — and unarchives — the
  existing player; the player editor refuses a taken name.
- `Team` is always `RED`/`BLUE` in code. "Team A" exists only inside a draft = the team
  showing on the Tie Breaker coin.
- Unknown values are `null`, never guessed.

## Hero JSON (`assets/catalog/heroes/<id>.json`)
```json
{
  "id": "example-hero",
  "name": "Example Hero",
  "title": "the Example",
  "product": "base",
  "aliases": [],
  "complexity": 2,
  "stats": { "attack": [3, 6], "defense": [4, 7], "initiative": [4, 6], "movement": [2, 4] },
  "roles": ["tactician", "disabler"],
  "portrait": "official/heroes/example-hero/portrait.webp",
  "printing": 2,
  "cards": [
    {
      "id": "example-hero-red-1",
      "heroId": "example-hero",
      "name": "Card Name",
      "color": "red",
      "tier": 1,
      "initiative": 8,
      "primary": "attack",
      "secondary": ["movement", "defense"],
      "values": { "attack": 4, "defense": 2, "movement": 2, "range": null, "radius": null },
      "ranged": false,
      "block": false,
      "item": null,
      "text": "Exact card text as printed.",
      "image": "official/cards/example-hero-red-1.webp"
    }
  ],
  "handicap": null,
  "extras": [],
  "faq": [{ "question": "…", "answer": "…", "source": "goa2-faq" }],
  "verified": false,
  "sources": [{ "name": "…", "url": "…", "retrieved": "2026-10-17" }]
}
```
Validation (unit tests): unknown keys rejected; IDs unique and permanent; asset paths exist;
32 heroes present, each with the standard 18-card composition + handicap unless listed in
`deckExceptions` (Gydion, Takahide); `verified: true` per the rule in *Sources*. Values above
are placeholders, not real data.

## Elo [APP — ADR-011]
Deterministic replay of COMPLETED matches ordered by `(playedAt, createdAt, id)`. Two rating
spaces, players and heroes, updated from the same match. Every constant lives in `EloConfig`
(editable under Settings → Ratings; changing it recomputes everything).

**Seats, not players.** A match is a list of hero *seats* (`Participant`). A player who
**double-pilots** (controls two heroes, e.g. 5 players as 3v3) owns two seats.
- Player space: a seat's strength `s = R_pilot + heroInfluence · (R_hero − 1200)`.
- Hero space: a seat's strength `s = R_hero + pilotInfluence · (R_pilot − 1200)`.
  (A strong pilot winning with a hero earns the hero less credit, and vice versa.)
- Team strength `T = mean(s over the team's seats) + seatAdvantage · (seats − opponentSeats)`.
  This handles uneven seat counts (e.g. 3 heroes vs 2 with Handicap cards).
- `E_A = 1 / (1 + 10^((T_B − T_A) / 400))`; `S_A` = 1 win, 0 loss (no draws in GoA2).

**Updates.**
- Player: each *distinct* player on team A gets `Δ = K · m · w · (S_A − E_A)`, where
  `w = 1 + doublePilotWeight · (seatsPiloted − 1)` (a double pilot carried more of the result).
- Hero: each hero seat gets `Δ = K · m · (S − E)`.
- `K = 40` while provisional (< 10 rated games in that space), then `K = 20`. `m` = mode
  multiplier (Quick / Long / Double lane).
- Defaults: `heroInfluence = 0.5`, `pilotInfluence = 0.5`, `seatAdvantage = 0` (the Handicap
  card is the rulebook's balance), `doublePilotWeight = 0.5`, mode multipliers 1.0.
- Skipped: matches that aren't COMPLETED or have no winner, and matches with the same player
  on both teams (the editor also blocks them). Seats with an unknown hero are left out of the
  hero space (not in that team's mean; their pilot still counts at hero rating 1200 in the
  player space); a match where a team has no known hero, or a hero appears twice, is skipped
  for the hero space only. `seatAdvantage` always uses the real seat counts.
- The start rating (1200) is editable in `EloConfig`; the 400 scale is fixed.
- Stats tracked alongside: games/wins per player, per hero, per player × hero, double-pilot
  games, and the larger-team win rate (to tune `seatAdvantage`).

## Meta stats [APP]
- Pick rate = matches featuring hero ÷ all matches; ban rate = drafts banning hero ÷ drafts
  with at least one ban (House draft and Pick and Ban both count); win rate = wins ÷ matches
  featuring hero.
- Rank by **Wilson score lower bound (95%)**; hide below a minimum sample (default 5).
- "Best with hero": highest hero-specific win rate per player with ≥ 3 games.

## Team balancing [APP]
Enumerate all splits into ⌈n/2⌉ / ⌊n/2⌋ (≤ 126 for n ≤ 10), minimise `|mean(A) − mean(B)|`,
tie-break by seed, respect locked players. Offer the official **Player Draft** too [RB p.22]:
the two most experienced players are Captains; first a hero is chosen for *every* player with
any hero selection method; then Captains draft players one at a time, Team A's captain first;
everyone plays their pre-selected hero.

## Draft
Official hero selection methods [RB p.22]; `Team A` = the team showing on the Tie Breaker
coin at the time of selection (the coin is flipped in setup step 3, before heroes are chosen):
- **All Random** — each player gets a random hero.
- **All Pick** — pick from all available heroes, starting with Team A and alternating teams.
- **Single Draft** — deal 3 heroes to each player; players take turns picking from their own
  3, starting with any player on Team A and alternating teams.
- **Random Draft** — reveal (players + 2) heroes; pick starting with Team A, alternating.
- **Pick and Ban** (competitive/tournament):
  | Players | Sequence |
  |---------|----------|
  | 4 | Ban A,B → Pick A,B → Ban B,A → Pick B,A |
  | 6 | 4-player + Ban A,B → Pick **B,A** (official PDF; RulesPal shows A,B) |
  | 8 | 6-player + Ban B,A → Pick A,B |
  | 10 | 8-player + Ban B,A → Pick B,A |
  [APP] odd counts: use the next even sequence and skip the smaller team's surplus pick.
- **House draft** [APP — owner's group, the default method]: deal a **pool of 15** heroes at
  random from the owned/filtered roster (re-roll or swap allowed before starting). Then
  Team A **bans 1 and picks 1**, Team B **bans 1 and picks 2**, then teams alternate
  **2 picks** each, and the last pick is single when one seat remains. 2v2:
  `Ban A · Pick A · Ban B · Pick B · Pick B · Pick A`. 3v3: `… · Pick A · Pick A · Pick B`.
  Implemented as a data-defined custom method; pool size, bans and pick pattern are editable,
  and users can save their own methods.
- **Seats:** drafting fills hero *seats*. A team with a double pilot drafts as many heroes as
  it has seats, not players.
- **Complexity filter** [RB]: remove heroes above the least experienced player's complexity.
- **First game** [RB p.5]: 4 players = Red Wasp + Xargatha vs Blue Arien + Brogan; 6 players
  add Tigerclaw (Red) and Sabina (Blue). Offered as a "Learning game" preset.
- Team picks are assigned to players on that team before the match starts [APP].
- **Rearranging seats** after the draft [APP]: only for methods where a *team* picks (House
  draft, Pick and Ban, All Pick, Random Draft, All Random): heroes swap between two seats of
  the same team; swapping with an open seat moves the hero (once rearranged, the session keeps
  a full seat → hero map with `null` for open seats, so a hero never appears twice). Refused
  (typed errors) for **Single Draft** — each player picks from their own 3 [RB], so the hero
  stays with that seat — and for a **Player Draft** hand-off.
- **Player Draft hand-off** [RB p.22: everyone plays their pre-selected hero]: the finished
  board is flagged `fromPlayerDraft`, so re-roll ("Deal again"), pool swaps and seat swaps are
  refused (`DraftError.PlayerDraftFixed`) and hidden. Undo on that board takes back the
  captains' last pick (back to the Player Draft) [APP].
- **Hidden steps** [APP]: a hero chosen in the current run of hidden steps still looks
  available to the next team. Choosing it is refused with the neutral
  `DraftError.Unavailable` ("Choose a different hero"), which does not say who chose it or
  whether it was banned or picked. Once the hidden run ends, the normal banned/picked errors
  apply. (Duplicate blind bans are *not* accepted: every hero appears at most once per draft.)
- **Dealt hands + bans** [APP]: a method that deals hands (`deal_per_seat`) may not contain
  bans — a ban draws from the whole pool and could empty a player's hand. The planner
  (`DraftPlanner.problem`) and the method editor reject the combination.
- Engine: `DraftEngine.apply(session, command) -> Result<DraftSession>`; typed errors for
  taken/banned/not-in-pool/wrong-step; a command may name its step, and a draft that moved on
  refuses it (`StaleStep`, e.g. a confirm racing a step change); undo = drop last result.
  Property tests: every method × every player count 2–10 completes with unique heroes when the
  pool is large enough; random seat-swap sequences keep the assigned heroes equal to the picks.
