# Product

## Goal

The app a GoA2 group leaves open on the table all game: it keeps the pace, settles "who is
best with Tigerclaw", runs the draft, and answers questions about a hero without digging
through cards. Private build for one play group (ADR-006).

**Users:** a regular play group (2–10 players, usually 4–6), one phone passed around or
propped up. **Quality bar:** feels like a first-party premium app — fast, polished,
accessible, no placeholder UI shipped. Terminology follows the rulebook (DOMAIN.md).

**Prior art:** BGG thread 3504878 "Guards of Atlantis Timer" (fan app with draft modes,
timers, stats). Study it for ideas and pitfalls; don't copy code or assets from it.

## Features and acceptance criteria

### 1. Hero catalog
- All 32 heroes from **every product** (Core, Devoted, Defiant, Renowned, Arcane, Wayward),
  2nd printing; ready for the 3rd-printing heroes when they appear.
- Grid/list with official artwork (sigil fallback), name, title, complexity ★–★★★★, roles,
  product.
- Filter by complexity, role, product, ranged; full-text search over names, aliases, card
  names and card text.
- Hero detail: stat bars (Attack/Defense/Initiative/Movement, base → max), roles, full deck
  laid out by colour and tier (basic → I → II → III → Ultimate) with official card images
  (tap to zoom, swipe between cards), card text and values, item each card grants, Handicap
  card, extras (e.g. Gydion's spellbook), **FAQ rulings**, and personal stats (games, win
  rate, Elo) from the match log.
- Unverified data is visibly badged. Works fully offline.
- **Done when:** every hero passes catalog validation, search returns in <100 ms, screenshot
  tests cover grid, filters and detail in light/dark at font scale 1.0 and 2.0.

### 2. Match setup and timer
- **Setup:** pick mode (Quick / Long / Double lane) and players → app shows Wave and Life
  counters to lay out (DOMAIN.md table), handicap reminders for odd counts, and which team's symbol
  shows on the Tie Breaker coin.
- **Rule timers per phase** (owner's table rules): Pre-round strategy 1:30 → 4 × Turn 0:30 →
  Upgrade 2:00 during the end-of-round Level Up step; optional per-hero Action timer. Every
  phase is configurable (on/off, duration, cues, auto-advance) and presets are saved/editable.
  Defaults in DOMAIN.md.
- Big, glanceable countdown; tap to pause; next/back; round and turn pips; overtime counts up.
  Each turn is "Play a card" (timed) then an untimed "Resolve" step after the reveal, so
  resolution never shows as overtime. Back right after an accidental Next restores the phase
  with its time; double taps don't skip phases. Side-by-side layout in landscape.
- Table rules (house rules, per preset): on-demand "Hurry up" countdown, optional team
  time-outs, opt-in volume-key control; "turn length tonight" adjustable from the start card.
- Action timer: tap "next" as each player resolves; shows whose turn it is if heroes known.
- **End of round:** tap-through checklist of the six official steps, then next round.
- Haptic + audio cues at configurable thresholds (e.g. 10 s, 5 s, 0).
- Correct when backgrounded, locked or after process death: countdown notification with
  controls (promoted Live Update on Android 16+), alarm at phase end. Screen stays awake on
  the timer screen.
- **Done when:** drift <250 ms over 30 min, phase-end alert fires with screen locked and in
  Doze, state survives process death and reboot, all controls work with TalkBack.

### 3. Players, matches and meta
- Player profiles (name, colour, avatar from original avatar set). No accounts.
- Log a match: date, mode, teams (Red/Blue), hero seats with their pilot (a player may
  **double-pilot** two heroes), handicap flags, winner, win
  condition (Throne / Last Push / Life counters / Other), duration, notes. Draft → pending
  match automatically; timer session can fill in duration.
- Elo per player and per hero (seat-based, double-pilot aware, tunable — DOMAIN.md / ADR-011),
  provisional flag for <10 games, rating history chart.
- Meta dashboard: hero win/pick/ban rates, best player per hero, team-composition stats (later).
- Edit/delete any match; all ratings recompute.
- Export/import full data as a versioned JSON file (backup, moving phones). Android Auto
  Backup enabled for the database and settings.
- **Done when:** recompute of 1,000 matches <1 s on device; import→export is lossless.

### 4. Draft
- Choose players and teams (manual, balanced by Elo, or official Player Draft), complexity
  filter, hero pool (all, owned products, or custom).
- Methods: the group's **House draft** (default: pool of 15, A ban+pick, B ban+2 picks,
  alternate pairs, single last pick) plus the official All Random, All Pick, Single Draft,
  Random Draft and Pick and Ban (exact sequences in DOMAIN.md); custom methods can be saved. Animated Tie Breaker coin flip decides Team A.
- Pass-and-play UI with clear "Team Red — Ban" prompts, hidden picks where the method needs
  them, optional per-step timer, undo at every step, then assign picks to players.
- Finished draft → "Start match" creates a pending match and opens setup/timer.
- **Done when:** every method completes legally for 2–10 players (property tests), undo works
  at every step, and the result flows into match log and timer.

### Settings
Theme (dark default / light / system), owned products (default: all), timer defaults, cue
sounds and haptics, Elo constants (advanced), export/import, About/credits.

## Non-goals (v1)
- Online play, accounts, cloud sync, ads, analytics SDKs, any network access from the app.
- Play Store or any public distribution — sideloaded only.
- Full rules engine or board-state simulation (we don't track minions, coins or positions).
- iOS; tablet-first layouts (phone-first; tablets must not break).

## Open questions (owner to decide)
- Which printing the group plays (data targets the 2nd printing / upgrade kit; a 3rd printing
  is crowdfunding for 2027 — the model supports multiple printings).
- Later: optional Life/Wave counter tracker during a match? Glicko-2 alongside Elo?
- Later: quick-reference rules section built from the rulebook.
