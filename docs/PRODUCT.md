# Product

## Goal

The app a GoA2 group leaves open on the table all game: it keeps the pace, settles "who is
best with Tigerclaw", runs the draft, and answers questions about a hero without digging
through cards. Built for one play group.

**Users:** a regular play group (2–10 players, usually 4–6), one phone passed around or propped
up; friends may have the app on their own phones too, kept in step by group sync. **Quality bar:** feels like a first-party premium app — fast, polished,
accessible, no placeholder UI shipped. Terminology follows the rulebook (DOMAIN.md).

**Prior art:** BGG thread 3504878 "Guards of Atlantis Timer" (fan app with draft modes,
timers, stats). Study it for ideas and pitfalls; don't copy code or assets from it.

## Features and acceptance criteria

### 1. Hero catalog
- All 32 heroes from **every product** (Core, Devoted, Defiant, Renowned, Arcane, Wayward),
  2nd printing; ready for the 3rd-printing heroes when they appear.
- Grid/list with official artwork (sigil fallback), name, title, complexity ★–★★★★, roles,
  product. The grid shows three heroes across a phone up to font scale 1.3 and two above it,
  so names never break inside a word; the list is compact (six or more rows per screen); the
  grid/list choice is saved.
- Filter by complexity, role, product, attack style (Ranged / Mixed / Non-ranged attack cards, see
  DOMAIN.md); full-text search over names, aliases, card names and card text. Search lists
  every matching **card** (whole phrase > all words), ranked above heroes matched only
  partially; words may span a card and its hero, and a card's kind narrows a multi-word query
  ("brogan ultimate", "tigerclaw red tier 2"). A result shows the line that matched (a "Choose
  one —" lead-in comes with its options); tapping a card opens it in the card viewer. Results
  start at the top whenever the query, filters or sort change. Back with a search typed clears
  it. When the owned-products collection hides every match, the empty state says so and offers
  "Show all products".
- Hero detail: stat bars (Attack/Defense/Initiative/Movement, base → max), roles, full deck
  laid out by colour and tier (basic → I → II → III → Ultimate) with official card images
  (tap to zoom, swipe between cards), card text and values, item each card grants, Handicap
  card (which basic card it replaces), extras (Gydion's spellbook grouped by tier), Ultimate
  tiles with their text, **FAQ rulings**, and personal stats (games, win
  rate, Elo) from the match log.
- Unverified data is visibly badged. Works fully offline.
- **Done when:** every hero passes catalog validation, search returns in <100 ms, screenshot
  tests cover grid, filters and detail in light/dark at font scale 1.0 and 2.0.

### 2. Match setup and timer
- **Setup:** pick mode (Quick / Long / Double lane) and players → app shows Wave and Life
  counters to lay out (DOMAIN.md table), handicap reminders for odd counts, a "House rule" label
  wherever the double-pilot count gives different numbers than the rulebook's per-player rules,
  the Double lane rules (p.20) when Double lane is chosen, and which team's symbol shows on the
  Tie Breaker coin. After a draft the coin is the draft's (leave it as it is; "Correct the coin"
  fixes a mistake); without a draft the app flips it. The coin is saved to the match and the
  timer starts with it.
- **Rule timers per phase** (owner's table rules): Pre-round strategy 1:30 → 4 × (Turn 0:30 →
  0:30 for each hero as they resolve) → Upgrade 2:00 during the end-of-round Level Up step. Every
  phase is configurable (on/off, duration, cues, auto-advance) and presets are saved/editable.
  A bundled **Speed guards** preset plays fast: 0:30 to strategise before each round and 0:10
  for each hero resolving.
  Defaults in DOMAIN.md.
- Big, glanceable countdown; tap to pause; next/back; round and turn pips; overtime counts up.
  Each turn is "Play a card" (timed), then after the reveal every hero resolving gets their own
  countdown (Action timer, on by default); with the Action timer off, an untimed "Resolve" step
  takes its place, so resolution never shows as overtime. Back right after an accidental Next restores the phase
  with its time; double taps don't skip phases. Side-by-side layout in landscape.
- **Two views of the running timer** (device preference, Settings → Running timer, and a switch
  in the timer's top bar that applies and saves at once; default Step by step):
  - *Step by step*: one step per screen around the big dial; Next and Back walk the round.
  - *Whole round*: every step of the round on one scrolling list under Round start / Turn 1…N /
    End of round headings, each with its length ("0:30", "Untimed") and context (which Action
    hero, which checklist step carries the Upgrade timer). Done steps are ticked, the current one
    is expanded with its live clock, progress bar, warning/overtime colours, rules hint, Level Up
    rule and ladder, and the Action seat chooser; tap its clock to pause. The list keeps the
    current step in view when the step changes, but never while someone is scrolling it. Tapping
    another step asks "Jump to …?"; the jump starts that step fresh and Back right after it
    returns, like an accidental Next. Header, Hurry up, time-outs and the controls stay pinned;
    landscape puts the list beside the current step's clock and controls.
- Table rules (house rules, per preset): on-demand "Hurry up" countdown, optional team
  time-outs, opt-in volume-key control; "turn length tonight" adjustable from the start card.
- Action timer: one countdown per hero each turn; tap "Next hero" as each one finishes. With the
  match's heroes known, tap the hero who resolves now: their full time starts at that tap, the
  title names them, and the last hero left is named automatically.
- **End of round:** tap-through checklist of the six official steps (each with a short
  rulebook summary, p.8 and p.15), then next round.
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
- **"Who won?"** when a game ends (timer summary, or a pending match in the log): winner and
  win condition first, everything else optional below (each winner button names that team's
  pilots, so the table confirms who it credits at a glance); dated from the session start. Saving
  shows each player's rating change, their rank and who is on top, plus **Rematch** (same
  seats / swap heroes / swap sides → Match setup, saved as a pending match when its timer
  starts; draft again → the draft). Pending matches can be resumed into setup and the timer from
  the log; on the Timer tab they show each team's heroes and when they were set up, with
  Discard. The pilot picker can add a
  player inline.
- Elo per player and per hero (seat-based, double-pilot aware, tunable — DOMAIN.md / ADR-011),
  provisional flag for <10 games, rating history chart.
- Meta dashboard: last game night (matches, biggest movers, streaks), how games end (win
  conditions, sides, average length by mode), hero win/pick/ban rates as a compact sortable
  table or cards, best player per hero, rivalries and teammate pairs (Wilson-ranked, minimum
  sample), strongest hero pairs. Profiles add the player's win-condition mix, head-to-head and
  teammate records, and dates under the rating chart.
- Edit/delete any match; all ratings recompute.
- **Share stats page** (ADR-027), for friends who can't run the app (iPhone players whose matches
  an Android phone logs): the share icon on the Meta tab's top bar writes one self-contained HTML
  file and opens the share sheet. It holds the leaderboard (Elo, games, W–L, recent trend,
  provisional), each player's best heroes as their profile lists them, the hero meta (same
  "Not enough games yet" rule), the group stats the Meta tab shows, and the newest 100 matches
  (date, mode, teams with pilots and heroes, winner, win condition, notes), with "Generated …,
  ratings as of the last logged match" in the footer. All numbers come from the app's own
  calculations; nothing new is computed. Text only (no card art), light/dark, printable, opens
  offline in any browser; no scripts or network references.
- Export/import full data as a versioned JSON file (backup, moving phones): players, matches,
  drafts, tombstones and player merges, rating constants, app settings, timer presets and custom
  draft methods. Import "Merge" follows the group-sync rules (the later edit of a record wins).
  Android Auto Backup enabled for the database and settings.
- Duplicate players (one friend added on two phones): "Merge into…" on the player page, with a
  confirmation naming both and their match counts; refused, with the matches listed, when the two
  played in the same match.
- **Done when:** recompute of 1,000 matches <1 s on device; import→export is lossless.

### 4. Draft
- Choose players and teams (manual, balanced by Elo, or official Player Draft), complexity
  filter, hero pool (all, owned products, or custom).
- Methods: the group's **House draft** (default: pool of 15, A ban+pick, B ban+2 picks,
  alternate pairs, single last pick) plus the official All Random, All Pick, Single Draft,
  Random Draft and Pick and Ban (exact sequences in DOMAIN.md); custom methods can be saved. Animated Tie Breaker coin flip decides Team A.
- Pass-and-play UI with clear "Team Red — Ban" prompts, hidden picks where the method needs
  them, optional per-step timer, undo at every step (with "Undid …" feedback and Redo), then
  assign picks to players. The step banner stays on screen while the pool scrolls; the pool is
  dense (about five tiles across) with name search, role filters and "Hide taken", and taken
  heroes shrink to chips. Long-press a hero for a peek (★, roles, stats, starting cards, link to
  the hero page); each team shows the roles its revealed picks cover. Single Draft shows the
  current player's own heroes first.
- The setup form (players, teams, method, pool, coin) survives leaving the tab, Back from the
  board and process death; after a draft starts, the next game defaults to the same players,
  teams and method (one "Clear" forgets them).
- Finished draft → "Start match" creates a pending match and opens setup/timer.
- **Done when:** every method completes legally for 2–10 players (property tests), undo works
  at every step, and the result flows into match log and timer.

### 5. Rules reference
Fast rule lookup at the table, starting with the arithmetic players get wrong every game (BGG
research, 2026-10-17). A reference, not a card cheat sheet: hidden information stays hidden (p.4).
- **Levels & defeats** (bottom sheet): pick the defeated hero's level → Life counters their
  team spends, coins for the defeat, assist coins; the full table for levels 1–8 (lowest card
  Tier, Coins (Defeat), Assist Coins, Life counters, next level's cost, max card Tier) from the
  rulebook's p.13 / p.15 / p.23 tables; the defeat and level-up rules quoted verbatim (level up
  is mandatory and simultaneous; "If you are defeated, use your correct Tier and Level"; minion
  coins; Pity coin); and the cards of the match's heroes whose text changes Life counters or
  assist coins (e.g. Bain "Dead or Alive", Ursafar "Tear", Gydion "Energy Drain", Rowenna
  "Glorious Triumph"), quoted from the catalog, never computed.
- One tap from the running timer (top bar; in the menu at large font sizes) and from the Level
  Up end-of-round step; also from the Rules screen.
- **Quick reference:** 11 searchable topics (turn sequence & tied initiative, hero respawn,
  attack/defense/defeats, minions & Heavy minion, the 6 end-of-round steps, levelling up & items,
  Active effects, pushing the Lane, table talk & hidden information, ways to win, double-lane
  map), each made of verbatim rulebook excerpts with printed page numbers. Anything in our own
  words is labelled "Summary".
- Reached from the Heroes tab top bar (Rules) and the timer's menu.
- **Done when:** every excerpt passes the importer's drift check against the pinned rulebook
  (`--rules-only`), the ladder's numbers pass their table and rule tests, and the ladder and a
  topic page have light/dark (and font scale 2) screenshots.

### 6. Group sync (ADR-025)
Each friend has the app on their own phone (the owner shares the APK); the group's
phones keep the same matches, players, drafts, deletions and rating constants by syncing **face to
face at the table**, phone to phone over Bluetooth and Wi-Fi Direct (Google Nearby Connections),
and **through the group's sync server** whenever a phone has internet (ADR-036). No accounts.
- **Group:** "Start a group" (name) creates the group and its key; "Invite a friend" shows a QR
  code (and "Share as text", which sends the invite with what to do with it); a friend's phone
  joins by scanning it (Google's code scanner, no camera permission) or pasting the invite (or the
  whole message it came in; the dialog says both ways, and Join stays in view above the keyboard).
  Leave group keeps all data.
- **Join flow:** install → Group sync → scan → the first sync starts right away → "Which player
  are you?" (pick yourself, or add yourself: this is how a friend adds themself as a player).
  Your player shows "You" in the Players list, and the Players tab's top bar opens your page.
- **Sync session** (a sheet, from Group sync → Sync now or the Players tab's sync icon): one phone
  taps Host, the others Join (a joiner finds the host on its own; with two hosts in range it asks
  which). The host lists each phone with its progress and taps Sync when everyone is in; result
  summary per phone ("+5 matches, +1 player, 2 updated"). Permissions are asked when a sync starts,
  with an explanation; after a refusal the sheet says so and offers asking again (or system
  settings) and the file fallback.
  Every stage times out; Cancel always works; the sheet can't be swiped away while merged data is
  being written.
- **Merge rules:** the later edit of a match, player or draft wins; a deleted match stays deleted
  everywhere unless someone edits it later; duplicates merged on one phone are merged on all;
  rating constants are the group's (the latest change wins). After a sync, two players with the
  same name get a "Merge into…" hint.
- **Fallback:** "Share sync file" (encrypted with the group key) through Quick Share, a message or
  Drive; "Import sync file" or opening the file merges it the same way, after showing what would
  change. A file from another group, or one this phone's key can't open, gets a clear error.
- **Sync server (ADR-036, [SYNC-SERVER.md](SYNC-SERVER.md)):** a dumb mailbox on the owner's
  machine holding one sealed document per group; it never has the group key. Each phone fetches
  the group's document, opens it, merges it exactly like an imported sync file, and uploads the
  merge (sealed again) only when the server's copy is missing something, so the server never sees
  content and an idle phone only reads. Automatic: when the app comes on screen and every 5 minutes
  while it stays there, 15 s after the last change to synced data, and hourly in the background (with
  a network). Two phones uploading at once: the later one re-reads, merges and tries again (three
  tries). A busy or full server's `Retry-After` is respected: no retry before it.
  - *One Sync now:* with the server switched on, the Group screen's **Sync now** and the Players
    tab's sync icon sync through the server; face to face becomes the secondary **Sync at the table
    (no internet)**. Without a server both behave as before (the Nearby sheet).
  - *Server address:* the app's built-in server (Gradle property `goa.syncServer`, empty = none),
    or a group's own address under Group sync → Sync server → Advanced (https only in release
    builds). An invite carries the address only when it isn't the built-in one and this phone syncs
    through it (switched on); older apps ignore it and join as before.
  - *Switch:* "Sync through the server", on by default whenever there is a server; off = Nearby and
    files only.
  - *Status:* the Group screen's Sync server card shows the server, "Synced 2 min ago" / "Not
    synced yet" / "Syncing…", and an error in words with what to do. Problems that pass on their
    own (no connection, timeout, server error, busy) retry with backoff and say "Trying again on its
    own"; problems that need a person (the server holds the group under another key, another app
    version, data too big, data this phone must fix first, other phones kept uploading) wait, and
    put a dot on the Players tab's sync icon, which then opens the Group screen. A server at its
    group cap says "The sync server is full", is retried no sooner than it asks, and also gets the dot.
  - *Joining:* scanning or pasting an invite with a server switched on syncs with it at once (as
    background work, expedited on Android 12+, so leaving the screen doesn't stop it); the group's
    data arrives without anyone hosting. When the server has nothing yet or can't be reached, the
    first sync is face to face as before.
- **Done when:** merge properties (commutative, associative, idempotent, convergence, identical
  ratings) and the protocol over a fake transport (host + 3 phones, a dropped phone, a wrong key)
  pass on the JVM; **two physical phones
  sync at the table** (emulators prove Nearby only over virtual Bluetooth). Server sync: every
  branch of the phone algorithm against a fake relay on a real socket (empty server, newer server
  copy, both sides new, 412 then success, 412 three times, 401, version gap, 413, 429/5xx/timeout/no
  connection, wrong address, refused local data), the scheduler on WorkManager's test driver, and
  two physical phones syncing through the real server without meeting.

### 7. Card trivia (ADR-028)
Learn the numbers printed on the heroes' Action cards so the table doesn't have to look them up:
Initiative, Attack, Defense, Movement, Range and Radius on every deck card (Basic, Tier I–III,
Ultimate), about 2,150 numbers over 32 heroes. Every question is generated from the catalog.
- **Card trivia home** (Heroes tab top bar, or "Learn <hero>'s card numbers" on a hero's page with
  that hero preselected): "N of M numbers learned" over the owned products, with Learned / Learning /
  New counts; the setup (heroes: all owned or chosen; numbers; card tiers; answer with 4 choices or
  Recall from every possible number; 10 / 20 / endless questions; initiative duels on or off) with
  a live count of the numbers in that quiz; progress per hero (tap = quiz that hero); Reset progress.
  The live count (or why Start is off, naming the quiz's scope) and Start quiz sit in a bar pinned
  under the list, always in reach.
- **Quiz:** the hero and card (name, colour, Tier, primary action) and the number asked; or a duel:
  "Which has the higher initiative?" (first, second, same). After an answer the right one is
  marked (and a wrong pick), never by colour alone, and the official card image shows the real
  numbers, scrolled into view above Next. Printed `+` values read "4+". Score, question count and
  streak at the top; haptics follow Settings. An unverified hero is marked Unverified.
- **Ending a round:** End (top bar) shows the summary at any point, for every round length. Back
  once a question is answered asks "Leave this round?" — "Your answers are saved. The round's
  results won't be." (See results / Leave; dismiss = stay); before the first answer, or on the
  summary, Back leaves directly.
- **Learning:** a number is *learned* after three right answers in a row, spaced out (an answer
  repeated within 4 questions counts but doesn't move it on); a wrong answer resets it and asks it
  again a few questions later; questions favour missed, new and still-learning numbers.
  Progress is saved with every answer, on this phone only (not synced, not in the backup file).
- **Summary:** score, best streak, numbers newly learned, each miss with the right answer (a missed
  duel shows each card with its own hero); "Practise the missed ones" (each missed number about
  twice: 2 × missed, at most the round's length, never endless), "Play again", "Done".
- **Done when:** a test checks every fact against the bundled hero JSON (no null value asked, no
  Handicap or spellbook card), engine invariants hold over seeded random rounds, and the home,
  question, answered, duel and summary screens have light/dark, font scale 2, 384 dp and landscape
  screenshots.

- **Leaderboard** (ADR-032): who knows the cards best, across the group.
  - *Playing as:* the Card trivia home shows who the next round is played as ("Playing as Sam ▾"):
    any active player, or Guest (not on the leaderboard). Default: the last pick on this phone, else
    this phone's "You" (Group sync), else Guest. The picker can add a player. Learning progress stays
    this phone's (ADR-028): a round moves it only when played as this phone's "You", or by anyone
    while no "You" is set; the home says so when another player is picked.
  - *Challenge:* one fixed quiz so scores compare: 20 questions drawn evenly (not weighted by
    progress) from every hero's numbers (all heroes, whatever the owned products), all six numbers,
    all tiers, 4 choices, initiative duels on, no number asked twice. Shown on the home as its own
    "Challenge" action next to Start quiz; needs a named player (Guest is asked to pick one). The
    time from each question appearing to its answer is summed (reading the card scan afterwards
    doesn't count). Only a Challenge with all 20 answered goes on the board; ending one early says
    "Unfinished challenges don't go on the leaderboard" in the summary. A finished Challenge's
    summary shows the player's place ("2nd of 5 · your best 18/20"). A Challenge's summary has
    Leaderboard, Play again and Done right under the score, above the missed cards, so the phone can
    go round the table.
  - *Rounds:* every round played as a player that reaches its summary is recorded (score, questions,
    best streak, answer time, Challenge or Practice), except an unfinished Challenge. Guest rounds
    are not recorded.
  - *Leaderboard screen* (trophy on the Card trivia top bar, and "Leaderboard" on a Challenge
    summary): **Challenge** board = each player's best Challenge (score, then less answer time, then
    earlier); **Practice** board = every recorded round including Challenges: right answers (rank),
    accuracy, best streak, rounds. Period: All time / Last 30 days. Each row: place, the player's
    colour and avatar, name ("You" tag), main figure and detail. Tap a player: their recent rounds,
    each removable ("Remove from leaderboard", confirmed; a tombstone, so it goes everywhere). Empty
    states say how to get on the board.
  - *Shared:* rounds sync with the group like matches (Group sync and sync files) and are in the
    backup file; merged duplicate players count as one. Phones on an older app version can't sync
    until updated (sync document version 2).
- **Leaderboard done when:** ranking and tie-break tests, a Challenge draws evenly with no repeats,
  merge properties hold with rounds and their tombstones, backup round-trips them, a finished round is
  recorded exactly once, and the leaderboard, Challenge summary and Playing-as picker have light/dark,
  font scale 2, 384 dp and landscape screenshots.

### 8. Board sandbox (ADR-033)
Set up a position on the real map and see what can happen: an opening for a line-up, where a
hero can get to, who is in range. A sandbox: you move the pieces and the app measures with the
rulebook's movement and distance rules; it never plays the game (no turns, card resolution, coins or
Life counters).
- **Maps:** bundled from the import pipeline (`assets/catalog/maps/`), drawn by the app (original
  vector style): spaces by Zone, Throne Zones in team colours, Zone borders, printed terrain, hero
  and minion Spawn points by team and type, each Lane's current Battle Zone. The core single-lane
  map first; any map not verified against the official art and rulebook is badged Unverified.
- **Set-up in one tap:** Boards and New board show the latest finished draft or match ("Latest
  pending match · Snorri vs Gydion · **Set up**"); a finished draft ("Try on the board", pinned
  with Set up match and Log result later under the teams on a phone held upright) and a match
  ("Open on the board", in the draft's pick order when it came from a draft) open their board in one
  tap. A line-up whose board is still untouched (on its opening) reopens that board instead of a
  duplicate, and a double tap never opens two. Boards also holds Play against the computer and
  Puzzles (9, 10) above the boards, so with no boards yet it shows those and "New board" rather than
  jumping to New board.
- **New board without scrolling:** both teams in pick order at the top (until the first pick, the
  latest line-up's card stands in their place, in a slot as tall as either, so the grid never moves
  under the next tap), search with an "Adding to Red | Blue" switch beside it, a grid of the heroes
  the group owns (Settings; "Show all heroes" lists the rest) with as many columns as it takes to
  show them all on a 384 dp phone (portraits 42–64 dp, each with its gap a 48 dp tap; names shrink
  rather than cut), and "Map: Forgotten Island ▾" (Unverified noted) beside **Set up** at the
  bottom. Picks alternate like a draft: the next pick goes to the team with fewer heroes (a tie to
  the other team), so tapping Brogan, Tigerclaw, Arien, Wasp gives Brogan + Arien vs Tigerclaw + Wasp
  (5 taps with Set up); tapping Red or Blue picks for that team. "Use a draft or match" (top bar)
  fills both teams from any recent draft or match. **Set up** opens the opening position (p.6):
  minions on their Spawn points in each starting Battle Zone, heroes on their team's hero Spawn
  points in pick order (more than three per team: next to them, p.20). Default map: single lane; the
  double-lane map when chosen or with 8+ heroes (if bundled).
- **Moving pieces:** tap a piece to select it; tap an empty space to move it there; tap another piece
  to select that one; drag the selected piece to move it (a long press lifts any other piece), dropping
  on another piece swaps them; any other drag pans. One piece per space, always. "Add" puts in a hero
  (owned heroes first), a minion (team and type) or a token (short label, an obstacle) — then tap
  where. Remove. Undo/redo every change; "Reset to opening" (added pieces go too). TalkBack hears
  every move ("Brogan moved 2 spaces, to Lane Zone 2, Battle Zone"), and each undo and redo.
- **Measuring** (a hero selected): pick one of its cards (by colour, Basic → Tier III → Ultimate)
  and switch on overlays: Movement (the card's movement value: spaces it can end on, p.11; "Path:
  Normal · Tokens · Ignore all" for cards that move through tokens or ignore obstacles, p.18, and "In
  a straight line", p.19), Fast Travel (p.11: own or adjacent Zone, no enemy hero or minion in
  either), Range and Radius (from the card's values, ± for items; the water around the board counts,
  p.19), and the card's text quoted (Gydion's spell cards come after his deck: his deck cards cast
  spells, so the spells carry the Movement, Range and Radius to measure); "Read the cards" shows the hero's cards large in the card focus viewer (§9), swiping between them, and a second tap measures with the one on show. A minion or token selected: Movement N with a stepper; a minion
  outside its Battle Zone shows where it goes back to (p.18, bound to its Lane, p.20). "Measure": tap
  any space of the board to read its distance in spaces and whether it lies in a straight line. The
  view zooms and pans so the selection and what it reaches stay in sight, hexes big enough to tap.
- **Battle Zone:** "Push the lane" (pick the team that won the push; per Lane on a double-lane map,
  named like the map's labels, "Lane 1 · Battle Zone") applies p.8 steps 2–3; a minion whose Spawn
  point is taken waits in a tray, with the nearest empty spaces of its Battle Zone highlighted; taps
  place it while no board piece is selected. Pushing into a Throne Zone says that team wins.
- **Boards:** every change is saved as it happens; the Boards list (name, map, line-up, last change)
  opens, renames, duplicates and deletes them (delete can be undone, even after leaving Boards). On
  this phone and in the backup file, not synced. A board left open follows its stored copy: a backup
  import replaces it on screen, a deletion closes it; it never writes over either.
- **Layout:** the board fills the screen with the Lane running top to bottom in portrait (your
  team's Throne at the bottom; "Flip" swaps), left to right in landscape. It opens on the Lane
  between the hero spawn areas, both teams' heroes in view, with hexes about 38 dp across (down to
  32 dp if that is what it takes to show the Lane's length; a double-lane map on its whole region,
  so neither Battle Zone is cut off); after a push it brings the new Battle Zone into view, and when
  the selection panel closes the map fills the space it leaves; "Fit" shows the whole map (beside the side panel in
  landscape) and toggles back to the Lane; pinch to zoom, drag to pan, double tap on a space where
  the tap did nothing zooms in. Selection details sit in a bottom panel (on the right in
  landscape, where a bottom panel would leave no map) that never covers the selected piece. **See the
  whole board:** the panel hides (Hide, or drag it down) to a one-line peek bar with the piece's name
  and Zone, Deselect and Show (a small card at the bottom end in landscape); the map then fits the
  whole board unless you had pinched or panned, and taps on the map work as before. Hidden or shown is
  remembered while the board is open, also across rotation. The title bar is compact (48 dp, 40 dp in
  landscape) on the board, the game and a puzzle. Pieces
  show the hero's portrait ringed in team colour, minions by type, tokens by label. TalkBack: a
  "Pieces" list selects any piece; spaces in view read their Zone, what stands there and how far
  they are from the selected piece.
- **Entry points:** Heroes tab top bar (Board), finished draft, a pending or logged match.
- **Done when:** hex distance, movement, Fast Travel, opening and push tests match rulebook cases;
  every bundled map passes validation (spawn counts per p.6, Lanes, Zones, no overlaps); the board,
  new-board and boards screens have light/dark, font scale 2, 384 dp and landscape screenshots (and
  a two-lane board); an end-to-end test sets up a board from a finished draft's "Try on the board"
  and moves a hero to a named space next to it.

### 9. Play against the computer (ADR-035)
Learn by playing: the real game on the board, with the app running every rule and computer
opponents (and teammates) at a strength from 1 to 10. One phone, one human team.
- **New game** (from Boards): a line-up as on New board (alternate picks, "Use a draft or match");
  only heroes whose every card the engine implements and tests can be seated, the rest show as
  coming later. Pick the hero(es) you control (one, or your whole team); every other seat is the
  computer at a **strength** from 1 to 10 on a slider (5 the first time, then the strength of the last game started; ADR-039), shown with its band:
  1–3 **Easy** (makes visible mistakes, plays fast; 1 is a gentle first game), 4–6 **Normal**
  (sensible, few mistakes, from 5 a short look-ahead), 7–8 **Hard** (plans the turn against what you
  might have played), 9–10 **Expert** (searches longest, remembers the cards you have shown; 10 is
  as strong as a phone allows), one strength for all or per seat. A game saved before the slider
  plays its level as Easy 2, Normal 6, Hard 8, Expert 10. Quick or Long, a verified map (Forgotten Island default), Coach on
  or off. The app flips the Tie Breaker coin. 2v2 is the rulebook's standard; 1v1 is offered,
  labelled as not the rulebook's way (p.20: two players each control two heroes).
- **The game** follows the rulebook exactly: play a card facedown, reveal, initiative order (coin
  ties, team ties), respawn, one action per card, attacks and defense with the totals shown
  (minion modifiers, items, card text), minions, pushes mid-turn, the six end-of-round steps,
  mandatory level ups with the upgrade choice, Pity coin, the three ways to win. Every question
  says what is asked and which card asks it; every option is shown on the board or as a button;
  the log narrates every event (TalkBack reads it). The computer never sees hidden cards (p.4):
  it plans on guesses consistent with what is face up.
- **Choosing and reading cards** (card focus, DESIGN "Card focus"): the first tap on a card zooms it
  large enough to read the printed card, with its values, verbatim text and what the question says
  about it (Holds / Falls with the Defense against the Attack, what keeping or upgrading it means, why
  a card isn't an option); a second tap on it, or its button ("Discard Magnetic Dagger"), answers,
  with a press-and-tick animation and a haptic tick; swiping sideways goes through the other cards;
  back (the edge swipe, predictive), an upward swipe, the scrim or × return with nothing chosen. A
  hand card the question doesn't offer is shown dimmed and can't be chosen. The answer goes through
  the Coach exactly as a button's does. The same viewer, without the button, reads any card on the
  table: your hand, a hero's played, resolved and discarded cards, the card that asks. TalkBack: Select,
  Next card, Previous card, Close. Portrait and landscape, font scale 2, light and dark.
- **Game state** (a top-bar action, one tap; also on puzzles): a full-height sheet with **Now** — Life
  and Wave counters, pushes won, minions per team in the Battle Zone, the Tie Breaker coin, who has
  acted this turn, who acts now and next (before the reveal: who has played facedown); every hero by
  team with its card in play (facedown before the reveal, named only to its owner), the resolved Turn
  slots, discards, item icons, level, coins, hand size, on/off the board, markers and its tokens, and
  your own hand — and **Turns** — every turn so far, newest first, each hero's card at the reveal in the
  order they acted (facedown cards stay hidden), the turn's lines not to miss with the rest behind
  "Show all", the end of each round, and **Board**: the start of any earlier turn, read only, as you
  saw it then. Back steps out one level at a time: from a turn's board to Turns, then closes the sheet,
  then leaves the game for Boards. Tap a card to read it in the card focus viewer, over the sheet. Only open information (p.4): built by the engine from its own
  views, never from the log's words. Note: the rulebook forbids taking notes at the table (p.4 Table
  Talk); the history is the log's own content, offered as a learning aid (ADR-037).
- **Gydion's spellbook** (ADR-040; hero sheet, Game state's Now tab, puzzles): the official spell cards,
  landscape as printed, in three groups — in the spellbook (Prepare Spells: "Only Gydion can look at the
  cards in the spellbook": named to Gydion's player only, everyone else sees how many), cast (removed
  faceup: everyone), not yet added (everyone; the game starts with an empty spellbook). Tap a spell to
  read it large. "Choose and cast one spell" offers the spells as landscape cards ("Cast Magic
  Missile"). An unlocked Ultimate shows as its card. **Done when:** an engine test shows two games that
  differ only in the spellbook's spells look the same to every other seat, live and in the review;
  screenshots of the sheet (Gydion's player and an opponent, before Prepare Spells, light/dark, 384 dp
  at font scale 2, landscape), the cast question and a spell in the card focus viewer (portrait,
  landscape, short landscape, font scale 2); every bundled card scan matches the shape it is drawn at.
- **The turn at a glance** (ADR-041): after the reveal the hero strip becomes the played cards
  themselves, in the order the heroes act (p.7): each official card with its initiative as it counts,
  the hero's portrait, name and team shape, and DONE / NOW / NEXT / PASS on the card (words, not colour
  alone), the action a done hero chose, ties ("=11"), Emmitt's second card, a hero off the board; tap a
  card to read it, long-press for the hero. It stays at the top with the panel hidden and in landscape.
  The acting hero carries a "NOW" tag on the map. The panel says the moments: "All cards revealed ·
  Wasp acts first" (ties: who decides, p.7), "Turn 1 is over" with that turn's cards, "End of round"
  with its steps (p.8), "Round 1 is over · Red won the push · Arien reached level 2". Hidden
  information stays hidden (p.4): before the reveal only a card back shows who has played; a card
  resolved facedown stays facedown. **Done when:** unit tests build the track and the moments from the
  engine's view (order, done/now/next, ties in one team and across teams, a pass, a card that left the
  table, Emmitt's second card, a hero defeated mid-turn, a facedown card, turn and round over); screenshots
  right after the reveal, mid-turn, panel hidden, landscape, light, 384 dp at font scale 2, a turn and a
  round over, a track card in the viewer.
- **The card is the content** (ADR-041): a question asked by a card shows that card large beside its
  answers (the action chips carry the game's icon and the card's value; only rules no card prints are
  added: Fast Travel's condition, Hold, Clear, that only the primary applies the text); Defend shows the
  attacker's card and the hand's cards with Holds / Falls under each; two or three cards to choose are
  shown larger (Keep's two next-Tier cards side by side); the computer's move shows its card. The panel
  repeats no log line the table already shows (the reveal, the action chosen, a move); the log sheet has
  them all. With large text the card goes above its answers.
- **See the whole board:** the panel (questions, the computer's move, the coach) hides to a
  one-line peek bar that still says what is asked ("● Arien · Choose an action for Dangerous
  Current", teal when it waits for you; a new question is announced by TalkBack). The map fits the
  whole board (unless you had moved it), only the round at a glance and, after the reveal, the turn
  track stay at the top (the hero strip returns with the panel), and a space or piece tapped on the map is answered with Confirm in the
  peek bar, without opening the panel. Show (or a tap on the peek bar, or dragging it up) brings the
  panel back. In landscape the side panel hides to a small card at the bottom end; while it is shown,
  the heroes are portraits beside the round at a glance (a tap still opens each one) and a puzzle's
  goal is one line, so a phone held sideways keeps most of its map.
- **Pacing:** each computer action is shown for a moment (Slow / Normal / Fast); "thinking…" when
  it takes long. Take back undoes your last choice (counted in the summary).
- **Coach** (on/off, also in-game): before a clearly worse choice, a gentle nudge ("Coach: Wasp's
  Lift Up can still reach you") with Choose anyway / Rethink; never chooses for you. After the
  game: the turning points, by round and turn, with the rule behind each.
- **Saved as you play:** a game in progress resumes from Boards after leaving or process death;
  local only, not synced, not in the backup file. One game at a time: starting another replaces it
  (New game says so). A save that can't be read, or that the engine can no longer rebuild, is
  offered for deletion, never a crash. "End this game" deletes it.
- **Until heroes are playable** the Boards entry and New game say play is coming; heroes not yet
  playable show in New game's grid, veiled as "Later". Puzzles show as coming soon until a
  puzzle's heroes are all playable; then Boards' Puzzles entry says "N of M solved".
- **Done when:** the Game state sheet shows nothing the person's live view did not (engine tests:
  facedown and unrevealed cards, enemy hands and tucked items never appear), with screenshots (light/
  dark, 384 dp at font scale 2, landscape, the rewound board); every rule above has a page-referenced
  test; random full games over every
  playable hero keep the invariants (one obstacle per space, cards conserved, deterministic,
  save/restore and fork identical); each strength beats the one below it over a seeded tournament
  (AI.md "Measured strength");
  the table has screenshots for every decision kind (light/dark, font scale 2, 384 dp, landscape)
  and of the card focus viewer (choosing in portrait, landscape, light, font scale 2; defending with
  the verdict; playing; upgrading; reading the hand); UI tests prove a first tap answers nothing, a
  second answers with that card, back and an upward swipe answer nothing, a swipe moves to the next
  card, a card that isn't an option can't be chosen, and the accessibility actions; an end-to-end test
  plays a game to its end.

### 10. Puzzles (ADR-035)
Fifty difficult puzzles in the style of chess tactics: a position, a goal ("Defeat Brogan this
turn", "Push the lane before the round ends", "Survive this turn"), one way (or a few) to make it.
- **List** (from Boards → Puzzles; only puzzles whose heroes are all playable): a Continue card on
  top with the first unsolved one and "N of M solved" (all solved: a seal instead); difficulty filter
  chips, and theme chips for themes two or more puzzles share; then every puzzle by number with
  title, theme, difficulty (three hexes and the word: hard / harder / hardest), heroes, and Solved /
  Tried N times / Not tried yet, nudges used, "Solution seen". Reset progress in the menu, confirmed.
- **Solving:** the game table with the goal banner floating under the hero strip ("Win the game this
  turn." · "You play Wasp and Xargatha"); in the panel the number, difficulty, title and theme, "What
  you know" (the intro: what the board doesn't show; it folds after the first move), then the
  question, card questions answered with the card focus viewer (§9). Every hero's hand is open (tap a hero). The other side answers at once with its best
  reply; its replies are narrated line by line ("Arien answers") over the board as it was after your
  move, then the board catches up. A move that loses the forced line says so gently, with the reason
  taken from what followed where it can ("Not quite — that lets Arien defend with Aspiring
  Duelist."; otherwise what the goal can no longer reach), and offers Try again from before that
  move, Restart and Show solution. Tries are counted. Any winning line counts, not just the stored one.
  The panel hides as in a game (9): the whole board with the question, the latest reply, the miss,
  "Solved" or the replay's step in the peek bar, Confirm there for a tapped space or piece; the goal
  banner returns with the panel.
- **Nudges** (on by default; switch in the puzzle's menu and in Settings → Learning → Puzzle
  nudges, one preference): when you're stuck (a miss, or 90 s without progress on the question) a
  gentle nudge card appears in the panel (not a dialog; close it with ×); "Another nudge" gives the
  next, stronger one; the last names the first move and outlines the solver's move for the current
  question in bronze on the board ("Outlined in bronze: …"). A correct move clears the card. Off =
  no nudges unless asked from the menu ("Nudge me" / "Another nudge").
- **Explanation at the end** (solved, or after "Show solution", offered after a miss and in the
  menu): solved shows a seal, the title, "First try · no nudges" (or tries and nudges), then why it
  works with its rulebook pages, and Replay the solution, Next puzzle, All puzzles, Play it again.
  The replay drives a fresh copy of the puzzle with the solver's choice: Back / Next through the
  steps, each the position before a move with the move outlined (a card's image beside its name) and
  the log of what happened since; the last step is the goal met. Back to the puzzle leaves it as it was.
- **Game state** as in §9, from the puzzle's top bar: every hand open (the puzzle's convention), the
  puzzle's turn marked "The puzzle starts in this turn", its starting position under Board.
- Progress (solved and when, tries, most nudges in a try, solution seen) is on this phone only
  (`datastore/puzzle_progress`; not synced, not in the backup file).
- **Done when:** a solver proves every puzzle against best replies within its time budget, the
  stored solution and every nudge agree with it, an independent review checked each puzzle's
  rules, and the list, puzzle, miss, nudge and explanation screens have screenshots.

### Settings
Theme (dark default / light / system), owned products (default: all), timer defaults, running
timer view (step by step / whole round), cue sounds and haptics, puzzle nudges (on by default), Elo constants (advanced; shared
with the sync group), Group sync, export/import, About/credits.

## Non-goals (v1)
- Online play, accounts, cloud sync, ads, analytics SDKs: not in v1, no longer ruled out
  (ADR-034). Group sync is phone to phone at the table (ADR-025) and through the group's own
  encrypted sync server (ADR-036); no third-party cloud.
- Play Store release: not in v1 (sideloaded); bundled official assets would need permission
  (ADR-034).
- Online play and pass-and-play between several humans on opposing teams in Play (9); one human
  team per game.
- iOS; tablet-first layouts (phone-first; tablets must not break).

## Open questions (owner to decide)
- Which printing the group plays (data targets the 2nd printing / upgrade kit; a 3rd printing
  is crowdfunding for 2027 — the model supports multiple printings).
- Later: optional Life/Wave counter tracker during a match? Glicko-2 alongside Elo? (Research
  2026-10-17: a standalone tracker duplicates the physical counters and card effects change the
  counts; at most as part of an optional defeat/push event log.)
- Later: general goa2-faq rulings in the rules reference (labelled as community rulings).
