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

### Timer phases [APP, informed by BGG practice]
Rulebook sets no timers. Defaults (user-editable): **Planning 45 s** per turn (community
range 30–60 s, plus an optional 2-min team discussion), **Action 30 s** per player in
resolution (optional, range 10–40 s; designer endorses "a 30-second sand timer once someone
takes too long"), **End of round** = untimed checklist.

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

## Core model (`core:model`)
```
Product(id, name, kind: BASE|HERO_PACK|PROMO|UPGRADE, released?)
Hero(id, name, title, aliases: List<String>, productId, complexity: 1..4,
     stats: Map<Stat, Range>  // attack, defense, initiative, movement: base..max, 1..8
     roles: List<Role>, portrait?: AssetPath, cards: List<Card>, handicap?: Card,
     extras: List<Card>  // e.g. Gydion's spells
     faq: List<Ruling>, printing: Int, verified: Boolean, sources: List<Source>)
Card(id, heroId, name, color: GOLD|SILVER|RED|GREEN|BLUE|PURPLE, tier: 1..4|null (basic),
     initiative: Int, primary: ATTACK|SKILL|DEFENSE|MOVEMENT|DEFENSE_SKILL,
     secondary: Set<SecondaryAction>, values: CardValues(attack?, defense?, movement?,
     range?, radius?), ranged: Boolean, block: Boolean, item: Stat?, text: String,
     image?: AssetPath)
Player(id, name, colorHex, avatarId, createdAt, archived)
Match(id, createdAt, playedAt, status: PENDING|COMPLETED|ABANDONED, mode: QUICK|LONG|DOUBLE_LANE,
      map?, winner: Team?, winCondition?, durationMin?, notes, draftId?, participants)
Participant(playerId, heroId, team: RED|BLUE, handicap: Boolean)
DraftMethod(id, name, kind: ALL_RANDOM|ALL_PICK|SINGLE_DRAFT|RANDOM_DRAFT|PICK_AND_BAN, steps…)
DraftSession(id, methodId, pool, teams, teamA: Team, history: List<StepResult>, assignments)
TimerPreset(id, name, phases: List<PhaseSpec(kind: PLANNING|DISCUSSION|ACTION|CUSTOM, seconds?, cues)>)
```
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

## Elo [APP]
Deterministic replay of COMPLETED matches ordered by `(playedAt, createdAt)`.
- Start **1200** (players and heroes). Team rating = mean of members' ratings (handles 2v3).
- `E_A = 1 / (1 + 10^((R_B − R_A) / 400))`; `S_A` = 1 win, 0 loss. Each member:
  `Δ = K · (S_A − E_A)`; team B mirrored.
- `K = 40` while provisional (< 10 rated games), then `K = 20`; constants in one `EloConfig`.
- Hero Elo: same algorithm in a separate rating space.
- Matches with no winner, or with an unknown hero (for the hero space), are skipped.

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
- **Complexity filter** [RB]: remove heroes above the least experienced player's complexity.
- Team picks are assigned to players on that team before the match starts [APP].
- Engine: `DraftEngine.apply(session, heroId) -> Result<DraftSession>`; typed errors for
  taken/banned/not-in-pool/wrong-step; undo = drop last result. Property test: every method ×
  every player count 2–10 completes with unique heroes when the pool is large enough.
