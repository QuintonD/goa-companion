# Domain

Rules below were checked on 2026-10-17 against the 2nd-printing rulebook text (as
reproduced on RulesPal) and BGG designer posts. `[RB]` = rulebook, `[BGG]` = forum,
`[APP]` = our own design choice, not a game rule. Use official terminology in UI copy.

## Sources (catalog import — ADR-009)
All heroes from all products, **2nd printing** (current). Pin each source to a commit SHA
in `tools/catalog-import/sources.lock.json`; fetch from `raw.githubusercontent.com` (the
GitHub API allows only 60 unauthenticated requests per hour). None of these repos has a
licence; content belongs to Wolff Designa. Fine for this private build, never redistribute.

| # | Source | Use for |
|---|--------|---------|
| 1 | **Stats-of-Atlantis** — github.com/QuasiStellar/Stats-of-Atlantis: `src/new_heroes.json` (2nd printing, 32 heroes, 610 cards), `src/spells.json` (Gydion), `src/states.ts` (title, ★, pack, roles, [base,max] stats), `src/lib/images/cards/<hero>/*.webp` (card artwork) | **Primary** card data, hero metadata, artwork/portraits |
| 2 | **dmrediger/goa2** — `public/hero-cards/<pack>/<hero>/<hero>-<n>.jpeg` (729×1026; 0 = hero card, 1–19 = deck, 20 = back) | Full card images; visual check of values |
| 3 | **yvo-niedrich/goa2-faq** — `build/data/heroes.json`, `cards/<hero>.json`, `faqs/*.json` (2nd printing; fix `â€™` mojibake) | Cross-check text; lore, tips, **per-hero FAQ rulings** |
| 4 | **colefin8/guards-of-atlantis-ii-data** — `server/<Hero>.csv` (30 heroes; no Ignatia, Whisper) | Cross-check numbers |
| 5 | Official: 2nd-printing rulebook PDF on Tabletopia (image-only); wolffdesigna.com | Rules, terminology |
| 6 | BGG via `api.geekdo.com` JSON (HTML and XML API are blocked): IDs, "(2nd Printing)" hero spotlight threads | Strategy notes, roster check |

`verified: true` = numeric fields agree between sources 1 and 4 (or 1 and the card image)
**and** text agrees between 1 and 3. Disagreements go to `REPORT.md`; resolve them in
`tools/catalog-import/overrides.json`, never by editing generated files.

### Roster — 32 heroes (titles from source 1; other spellings are aliases)
| Product (BGG id, year) | Heroes (★ complexity) |
|---|---|
| Core (267609, 2022) | Arien the Tidemaster ★, Brogan the Destroyer ★, Tigerclaw the Cutpurse ★, Wasp the Warmaiden ★, Sabina the Commander ★, Xargatha the Changed ★, Dodger the Warlock ★ |
| Devoted (356292, 2022) | Misa the Samurai ★★, Whisper the Outcast ★★, Ursafar the Savage ★★, Silverarrow the Pathfinder ★★, Tali the Spiritcaller ★★★ |
| Defiant (356290, 2022) | Garrus the Gladiator ★★, Bain the Bounty Hunter ★★, Cutter the Sky Pirate ★★★, Trinkets the Scavenger ★★★, NebKher the Harbinger ★★★★ |
| Renowned (356291, 2022) | Min the Dragonmonk ★★, Swift the Sharpshooter ★★★, Wuk the Grovekeeper ★★★, Hanu the Trickster ★★★, Ignatia the Mad ★★★★ |
| Arcane (412138, 2025) | Rowenna the Vanguard ★★, Mrak the Rockshaper ★★★, Snorri the Runescribe ★★★★, Razzle the Ringmaster ★★★★, Gydion the Archwizard ★★★★ |
| Wayward (412139, 2025) | Brynn the Seeker ★★★, Mortimer the Awakener ★★★, Widget the Dragonbonded (with Pyro) ★★★, Takahide the Warlord ★★★★, Emmitt the Traveler ★★★★ |

Official roles: Tactician, Disabler, Durable, Melee, Pusher, Damager, Sniper, Tokens, Farming.
**Exceptions:** Gydion has a 22-card spellbook; Takahide has 21 cards instead of 19.
**Printings:** 1st (2022, 22 heroes; every hero later changed by the Update Pack), 2nd (2025,
current; small watermark bottom-right on cards), **3rd announced** (Gamefound project 11755,
2027: two new packs ≈ 10 heroes, reworks rumoured for Sabina, Cutter, Tigerclaw, Wasp) —
expect a data refresh. Maps (source: PedroVIOliv/goa2-backend): Forgotten Island, Magma
and Ice, Across the River, Narrow Passages — verify names against the rulebook.


## Game structure (drives the timer)
- **Team Red** (Atlanteans) vs **Team Blue** (Titans), one hero per player [RB]. Each hero
  is one physical set, so a hero is picked at most once per match.
- Players: 4 or 6 recommended; up to 10 and odd counts are allowed for experienced groups.
  2 players = 2 heroes each; 3 players = most experienced controls 2 heroes, one with a
  Handicap card; with 5/7/9 the larger team's heroes swap a basic card for their Handicap
  card [RB].
- **Double piloting** [APP — owner's group]: with an odd count the group often evens the
  teams by having one player control two heroes. Match setup offers both options for odd
  counts: *Handicap* (uneven seats) or *Double pilot* (even seats, one player owns two).
- A **round = exactly 4 turns** [RB]. Each turn:
  1. **Play one card** — simultaneous, face-down (strategy talk allowed *before* reveal,
     out loud, no notes). *Main timed phase.*
  2. **Reveal** — all at once; table talk stops.
  3. **Resolve** — sequential: highest initiative acts first; after each resolution the next
     actor is the highest *unresolved* initiative (re-checked every time). Cross-team ties:
     the team shown on the **Tie Breaker coin** acts first, then that player flips the coin.
     Same-team ties: the team decides, coin not flipped. *Optional per-action timer.*
  4. **Turn ends** — "this turn" effects end.
- **End of round** (in order) [RB]: 1 Minion Battle · 2 Remove tokens / return markers ·
  3 Push the Lane (if a side has no minions; flip a Wave counter) · 4 Retrieve cards ·
  5 Level Up (mandatory, simultaneous) · 6 Pity Coin (1 coin if you didn't level).
  *Not timed — the app shows it as a tap-through checklist.*
- Hand: 5 cards per round (one per colour) for 4 turns; Defense is played by discarding from
  hand and doesn't use your turn card [RB].

### Match setup and win conditions [RB]
| Mode | Wave counters | Life counters per team |
|------|---------------|------------------------|
| Quick | 3 | 4 (3–5 players), 5 (6 players) |
| Long | 5 | 6 (3–5 players), 8 (6 players) |
| Double lane (required 8+, optional 6–8) | 2 × 7 | 6 (6–8 players), 7 (9–10 players) |

Win conditions (`WinCondition` enum): **THRONE** — a push makes the enemy Throne Zone the
Battle Zone; **LAST_PUSH** — the last Wave counter flips, the team that won that push wins
(double lane: more total Zones between own Throne and both Battle Zones); **LIFE_COUNTERS** —
a team spends its last Life counter (a defeated hero costs counters = its lowest card tier);
**OTHER** (concession, house rule). There are no draws.

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
- **Hero card / Profile:** Attack, Defense, Initiative, Movement as 1–8 bars with a base and
  max (items raise them), complexity ★–★★★★ and two roles. Descriptive, not used in play.

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
Player(id, name, color: ARGB, avatarId, createdAt, archived)
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
- Skipped: matches that aren't COMPLETED or have no winner; seats with an unknown hero are
  left out of the hero space only (their pilot still counts at hero rating 1200).
- Stats tracked alongside: games/wins per player, per hero, per player × hero, double-pilot
  games, and the larger-team win rate (to tune `seatAdvantage`).

## Meta stats [APP]
- Pick rate = matches featuring hero ÷ all matches; ban rate = drafts banning hero ÷ Pick
  and Ban drafts; win rate = wins ÷ matches featuring hero.
- Rank by **Wilson score lower bound (95%)**; hide below a minimum sample (default 5).
- "Best with hero": highest hero-specific win rate per player with ≥ 3 games.

## Team balancing [APP]
Enumerate all splits into ⌈n/2⌉ / ⌊n/2⌋ (≤ 126 for n ≤ 10), minimise `|mean(A) − mean(B)|`,
tie-break by seed, respect locked players. Offer the official **Player Draft** too (captains
pick players alternately).

## Draft
Official hero selection methods [RB]; `Team A` = team on the Tie Breaker coin:
- **All Random** — each player gets a random hero.
- **All Pick** — players choose freely. [APP] order: alternate teams, Team A first.
- **Single Draft** — deal 3 heroes to each player; each picks one.
- **Random Draft** — reveal (players + 2) heroes; players pick from them. [APP] order:
  alternate teams, Team A first.
- **Pick and Ban** (competitive/tournament):
  | Players | Sequence |
  |---------|----------|
  | 4 | Ban A,B → Pick A,B → Ban B,A → Pick B,A |
  | 6 | 4-player + Ban A,B → Pick A,B |
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
- Team picks are assigned to players on that team before the match starts [APP].
- Engine: `DraftEngine.apply(session, heroId) -> Result<DraftSession>`; typed errors for
  taken/banned/not-in-pool/wrong-step; undo = drop last result. Property test: every method ×
  every player count 2–10 completes with unique heroes when the pool is large enough.
