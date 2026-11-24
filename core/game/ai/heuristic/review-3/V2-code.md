# V2 — Code audit of strength 10 as it would ship (t3/strength-10-search, 959e4013)

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

Auditor scope: `HybridAi` / `HybridConfig` / `BookPolicy` / the book paths they call (`HeuristicAi.answerOn`, `CardSim`,
`HContext`, `HEval`), `Determinizer`, `NetEvaluator`, and the app/web/test wiring. Read-only; no builds, no runs.
Worktree: `/home/quinton/.t3/worktrees/goa-companion/t3-f53f8cfc-a7aabd8166620e65` (all paths below relative to it;
`G` = `core/game/src/main/kotlin/dev/quinton/goa/core/game`).

Evidence tags: **[code]** read at file:line; **[log]** recomputed by me from raw run output; **[doc]** a claim in
RESULTS.md / notes that I did not recompute. UNVERIFIED marks anything I could not check.

## 0. What ships (the effective configuration)

`AiPlayers.forStrength(10)` → `HybridAi(seed, SEARCH_10 + leaf=NET6)` (`G/ai/AiPlayers.kt:92-115`) [code]. With the
defaults of `HybridConfig` (`G/ai/hybrid/HybridConfig.kt:75-172`) the knobs resolve to:

| Knob | Value | Meaning |
|---|---|---|
| `pol` | 1 | FULL book (h10 with CardSim looks, `temp`=0, `mix`=0) for both teams in playouts (`BookPolicy.kt:32-35`) |
| `scope` | 3 | every decision with ≥2 options except ACT_FIRST is searched (`HybridAi.kt:139-143`) |
| `K` / `Ko` | 24 / 4 (default) | determinizations at card plays / other questions, merged by position (`dd` on by default) |
| `top/topS/topU` | 4 / 8 / 6 | non-card candidates: the root book's answer + best N by the book's *static* score (`HybridAi.kt:558-562`) |
| `hz` / `hzo` | TURN | playouts to the first decision of the next turn (turn 4: through the whole round end) |
| `rep` | 4 | while an enemy still has to choose its card: column 0 = book reply, columns 1–3 = drawn replies |
| `repM`/`repT`, `model`/`tau` | 1 / 2.0 | both opponent models are a softmax (τ 2 coins) of the book's **static** card scores + 15% uniform |
| `td` / `tw` / `te` / `tc` | 2 / 3 / 1 / 0 | post-reveal minimax 2 branching choices deep, 3 options, enemy minimises; **no tree under card plays** |
| `tt` | 1 | transposition memo for the real turn |
| `branch`, `restSpaces` | 1, 6 | book knobs, root and playouts (cheap F1) |
| `6.cap` | 8 | 3v3 only: at most 8 columns at a card play |
| `prior`, `risk`, `mm`, `kill`, `lw*`, `lavg` | off | |

## 1. Hidden information (ADR-062)

### 1.1 What the shipped player itself reads — no violation found

- The root's real session is read only through `determinizeWith` (`HybridAi.kt:565-591` → `engine/Session.kt:231-244`),
  `Searcher.seenCards` (public events only: CardsRevealed, Defended, ActionChosen, LevelledUp — `G/ai/Search.kt:895-923`),
  `es.state.heroes.size`, `wavesLeft`, `turnIndex` (public), and the root book, which determinizes again
  (`HeuristicAi.kt:76-81`, `:264-274`). [code]
- Card play is one engine step per seat (`engine/Flow.kt:62-97`: `playStep` asks one seat, `stepSeat++`), so the
  replayed `segment` in `determinizeWith` never carries another seat's card choice. [code]
- `Determinizer.sample` with `knowHands`: enemy pools and histories are kept, the facedown card is dealt again from the
  pool with the model's weights after all pool cards are put back in hand and stamps cleared
  (`engine/Determinizer.kt:50-67`, `:281-308`); facedown tokens shuffled per owner (`:319-334`). Gydion's spellbook sits
  in `Zone.EXTRA`, which the determinizer does not touch, but ADR-040 records that its contents follow from public
  information, so that is not a leak. [code + doc]
- Measurement hooks: `leafOverride` (internal, `HybridAi.kt:103`), `scored` (internal, `:481`), `trace` (public, `:478`)
  are null unless a test sets them; `AiPlayers`, `PlayEngineModule`, `EngineHost`, `Takeover` never set them (grep of
  app/web/server/core-data). `playModel` / `playModelOn` / `leafOn` belong to the *old* `SearchAi`'s `LevelProfile`
  and are set only by tests and by `HybridPlayers.searchWithBook` (the `y10` prototype, test-only). There is no
  `onSearched` hook on this branch. [code]
- `NoPeekingTest` (`core/game/src/test/.../ai/NoPeekingTest.kt:41-47`) runs `AiPlayers.deterministic(10, seed)`, i.e.
  **the shipped `SEARCH_10` + NET6**, in all three hidden-information cases (enemy facedown card ×2, tokens), plus the
  `x10`/`y10` prototypes. So the hybrid path is covered. [code]

### 1.2 Gaps in that coverage (latent risks, not observed leaks)

1. **Only 2v2, round 1, no human seat.** The `6.*` knobs, the 3v3 column cap, and `hidePlayedOf` (human teammate)
   are never exercised under NoPeekingTest. [code]
2. **Facedown tokens placed in the current step.** `determinizeWith` samples the step-start checkpoint, then replays
   the real `segment` (`Session.kt:238-243`). Tokens created during the step (Min's Mines: the owner answers
   "Which space holds a blast?" inside its own RESOLVE step, `heroes/MinHero.kt:283-318`) are re-created with their
   *real* kinds in every sample; the shuffle only happened on the checkpoint. A RESOLVE step is one hero's whole card
   (`Flow.kt:218-257`). I found no enemy-team decision that follows mine placement inside Min's step, so this is a
   design hole, not a live leak; the token test in NoPeekingTest builds its tokens before the step and cannot catch it.
3. **Order dependence inside the determinizer.** `redealHand` runs hero by hero (`Determinizer.kt:60-63`); the weight
   function for enemy A is evaluated on a state where enemy B (later in seat order) still has its **real** card in
   `PLAYED`. With `model=1` the weights are `General.score + adjust` (`BookPolicy.kt:144-165`). I traced the obvious
   readers (teammate pools are `hand + unresolved` unions; `defenseless` is size-based) and found no term that
   separates B's played card from B's hand before the reveal, and NoPeekingTest's first case (two enemies played)
   passes by its design. Still, nothing structurally prevents a future static term from reading it. Fix: put every
   hidden hero's pool back in hand before any weight is computed.
4. **Human teammate's hand.** The determinizer keeps the viewer team's hands (`Determinizer.kt:53`); for a human
   teammate only the played card is redealt (`hidePlayedOf`). `HContext.hand` documents "a teammate's hand is real for a
   computer team (they may say what they hold)" (`HContext.kt:201-210`), but a human never said it. p.4 forbids showing
   cards to teammates. Rules nit, covered in spirit by the owner's ADR-062 allowance.

### 1.3 The real defect: the opponent *model* inside playouts sees our facedown card (severity: strength)

This is not an ADR-062 violation by the player. It is a **model that is better informed than any real opponent**:

- In a playout, every enemy answer is `BookPolicy.answer` → `HeuristicAi.answerOn(f, …)` on the playout fork **with no
  determinization from the enemy's view** (`BookPolicy.kt:48-65`, `HeuristicAi.kt:83-96` "no second determinization").
  When an enemy's PLAY_CARD comes after ours, our candidate card is already in `Zone.PLAYED` and missing from our
  `hand`.
- With `knowHands`, the book reads our hand *contents* directly: `HContext.defensesOf` returns `h.hand`
  (`HContext.kt:832-838`), used by `killChance` (`:820-828`) → `heroStrikeWorth` (`:377-395`) → `strikeFrom` (`:410-432`)
  → the static card score (`PlayRules.kt:120`, `:192`). Also `HEval.hand` (`HEval.kt:108`), `BatchASupport.chanceHolds`
  (`heroes/BatchASupport.kt:195-197`), Bain's colour reads (`heroes/BainHeuristics.kt:214,239`). The non-`knowHands`
  branch explicitly guards this ("The card they are playing now has left the hand", `HContext.kt:836`), but only
  once revealed. The `knowHands` branch has no guard.
- Column 0's enemy reply (FULL card look: `CardSim.value` copies the state with our card in PLAYED, `CardSim.kt:61-76`;
  `CardLook.known` answers our defense from our real remaining hand, `:238-244`) and the drawn replies of columns 1–3
  (`drawReply` → `playWeights` on `f.state`, `HybridAi.kt:705-727`, `BookPolicy.kt:121-158`) are therefore computed
  **knowing which card we put down**. A real h10/s10 enemy cannot: `HeuristicAi.answer` determinizes and redeals our
  played card uniformly (`HeuristicAi.kt:264-274`, no `playWeight`).
- **Who is affected.** Seats are Red first, then Blue (`engine/Setup.kt:43-47`, `AiHarness.setup`
  `test/.../AiHarness.kt:101-115`), and replies exist only while an enemy still has to play (`enemiesToPlay`,
  `HybridAi.kt:156-157`). So every **Red** s10 card play is scored against a clairvoyant enemy reply; Blue's never
  (Red's cards are already down and are dealt from the model instead). In the app this follows the colour the
  computer is given.
- **Failure scenario.** Red s10 holds one card that can defend. Candidate "play it facedown": in the playout the enemy
  book's `defensesOf(ourHero)` is empty → `killChance = 1.0` → `heroStrikeWorth = killWorth` → the enemy's attack card
  scores far higher and is chosen in column 0, and gets most of the softmax mass in columns 1–3 → that candidate is
  scored as if we will be killed. A real opponent sees five equally likely cards. The search systematically avoids
  plays whose weakness only shows if the card is known, and over-values "keep a defense in hand". Direction and size
  in games: UNVERIFIED (no run isolates it). Fix: answer enemy card plays in playouts on a copy where our unrevealed
  card goes back into our hand (as `CardSim.cardModel` already does for the other direction, `CardSim.kt:375-382`).

## 2. Search correctness

### 2.1 Candidates and pruning
- Card plays: every legal card plus the root book's answer first (`HybridAi.kt:544-548`); `prune` is off. Non-card:
  the root book's answer + top-N by **static** score on the first sample (`:558-562`). A square/target the static
  score ranks below 8/6/4 is never searched, even if the book's own F1 RestLook would like it (the book's answer is
  always in). [code]
- The root book's full card look runs at every card play (`candidates` → `book.choose`, `:544`) only to order the
  candidates and keep ties. Its G17 mixing is still on (`rootTune` does not zero `mix`, `HybridAi.kt:89-93`), so the
  tie-keeper is seeded-random. The cost is wasted, and ties are rare. [code]

### 2.2 Samples, columns, weights
- `dd` merges identical determinized positions with a count weight (`:487-522`); key = `StateHash` of the step-start
  state (a full serialization walk, `puzzles/StateHash.kt:17-29`) + answers since + decision counter. Collision risk is
  negligible at 128 bits. [code]
- Columns: one per distinct sample, ×`rep` for samples where an enemy still has to choose; column weight = sample
  weight / r (`:187-202`). The value is the **weighted mean of raw leaf values** over columns (`:298-315`); `risk`=0,
  `prior`=0. [code]
- **Red seats (enemies not yet played)**: with known hands and no facedown enemy card, the 24 draws collapse to ~1
  position, so K=24 is wasted and the opponent model is **4 joint replies** (1 deterministic book reply at 25%, 3 draws
  at 75%). [log] In the C2 3v3 shadow run (`.../t3-f53f8cfc-.../da53a9b0-.../scratchpad/runs/sh3v3a.txt`), 506 of 684
  variant rows had `cols=4`. The same search with only the seed changed (`v1`, `seed=1`) chose a different card in
  **18 of 103 (17.5%) cols=4 card plays**, against **1 of 34 (2.9%)** card plays with more columns. That is
  Monte-Carlo noise from three drawn replies flipping card plays, at zero reported cost. [log, recomputed with awk
  from the SHADOW rows]
- **Blue seats**: no replies. Red's cards are dealt from the static softmax (`model=1`), up to 24 distinct samples
  (2v2 has no cap). The book's own FULL card choice is never the model there. So the opponent model differs by seat
  colour: the same position is modelled differently depending on whether the enemy acts before or after in seat order.

### 2.3 Enemy replies (`rep=4`)
- Drawn only on the root's turn (`turnsEnded == 0`, `HybridAi.kt:686`). The draw RNG is seeded per (salt, column), so
  candidates in a column share uniforms (partial common random numbers). The weights depend on our candidate through
  §1.3, so the same uniform can pick different enemy cards for different candidates. [code]

### 2.4 Aggregation: logit-scale mean mixed with ±1000 terminals (severity: strength)
- The leaf is `NetEvaluator.evaluate` = Red's (mean over six nets of) logit × `coinScale`, sign-flipped for Blue
  (`ai/LearnedEval.kt:222-236`). `coinScale` = 39.146 (current, 2, G3) / 36.559 (3, G1, G2) (`LearnedWeights*.kt:12`). A
  finished game scores ±(`Evaluator.WIN` 1000 − round) (`ai/Evaluator.kt:128`, `LearnedEval.kt:232-234`). [code]
- The root then takes an **arithmetic mean over columns** of these values. 1000 coins ≈ logit 25.5, while a clearly
  winning non-terminal leaf is about ±40–120 (logit 1–3). One terminal column out of four therefore swamps the rest.
  **Failure scenario** (turn 4 before a last push, Red seat, 4 equal-weight columns): card A has one drawn reply that
  loses the game now (−1000) and three columns at +80 (P≈0.88 by the net's own calibration), so mean −190. Card B has
  four columns at −117 (P≈0.05), so mean −117. The search plays **B**, although by expected win probability A ≈ 0.66
  and B ≈ 0.05. The mirror case makes it chase a 1-in-4 "junk reply wins" column: the 15% uniform share of the reply
  model draws such replies. The project already has the fix at hand: `WinChance` maps scores to probabilities and
  treats ±900+ as 0/1 (`ai/WinChance.kt:9-34`), and the regret oracle uses it for exactly this reason
  (REVIEW-2-losses.md §1 "so a lopsided or finished position counts little"). The search does not use it. Averaging
  six nets' logits instead of probabilities is a smaller version of the same thing. Magnitude in games: UNVERIFIED.
  This matters most in the last-push turns where s10 wins most of its games (§5).

### 2.5 The post-reveal minimax (`td=2`, `tw=3`, `te=1`)
- Correct alpha-beta: nodes where `d.team == t.team` maximise (**teammates are max players**), enemies minimise;
  bounds are updated and cut correctly; the book's own answer is first among the options (`HybridAi.kt:404-475`). Forced
  answers and non-branching kinds are played by the book. Card plays never branch (`:431`). Depth counts branching
  choices of any seat, ours or theirs. [code]
- The values come from the root team's leaf, and the root does not pass alpha/beta across candidates (each candidate
  needs its exact value for the column mean). That is right. [code]
- The tree assumes a fully informed adversary that minimises our leaf. Post-reveal that matches what s10 knows. It is
  pessimistic against humans who do not know our hand (RESULTS: depth 3 lost, 43.0%, "turns more noise into
  pessimism"). [code + doc]
- With several samples before the reveal (e.g. non-card questions at TURN_START), the tree is solved separately per
  sample and then averaged: strategy fusion (PIMC). Low impact for s10 because hands are known. [code]

### 2.6 Memo (`tt`)
- Key = (posKey, turn ends left, MemoView(team, `seenByUs.hashCode()*31 + seenByThem.hashCode()`, horizon ordinal))
  (`HybridAi.kt:785-795`). Values are leaf values of pure book playouts from that position. Tree minimax values are
  never stored, only frontier playout values (`:287-291`). Only column 0 reads or writes (`:228`). The memo grows only
  after `runAll`, in task order (`:277-292`). With `temp`=`gap`=`slip`=`blind`=0 at h10 (`HeuristicProfile.kt:176-191`)
  and `mix`=0 in the policy, a playout is a function of its position, so reuse is exact; `HybridSearchTest` checks
  that memo on and off give the same answers (`test/.../hybrid/HybridSearchTest.kt:45-77`). Sound. [code]
- Nits: `seen` enters only as an `Int` hash, so two different public logs with equal hashes would share entries
  (improbable). After the reveal the `seen` maps change, so post-reveal questions can never reuse the card-play
  searches' entries. `MEMO_CAP` is 400k entries before a reset (`:812`). [code]

### 2.7 Determinism
- RNG = `mix(seed, d.id·1_000_003 + seat.hashCode)` (`:161`); a task writes only its own cell; `leafCache`/`killEval` are
  set before `runAll`; the book and evaluator are per-context and stateless (`MapInfo` caches are synchronized,
  `Evaluator.kt:189-202`). `HybridSearchTest` checks 1 vs 3 threads on the shipped config. No problem found. `active`
  (the 2v2/3v3 config) is a field written per `choose`, so one instance must not serve two concurrent decisions; no
  current caller does. [code]

### 2.8 Column cap (`6.cap=8`, 3v3)
- When it binds: replies per sample shrink to `max(1, cap / samples)`, and if more samples than the cap remain, the
  **heaviest 8 are kept** (stable sort, so ties keep draw order) (`HybridAi.kt:188-198`). Candidates are untouched, so
  there is no bias toward the first candidates. The deals are truncated to the model's mode: the tail of the opponent
  model (its 15% uniform share and its unlikely-but-dangerous cards) is dropped rather than subsampled. With 3 samples
  it keeps 2 replies each, raising the clairvoyant book reply of §1.3 from 25% to 50% of the weight.
- Measured on positions: cap 8 changed 1 of 35 card plays with more than 4 columns, below the seed noise floor (§2.2).
  Card-play max went 17.2 s → 9.0 s in game 3001 (3 threads). [log] Low strength impact; it is the right lever for
  time.

## 3. The simultaneous card choice, precisely

At a card play, s10 plays a **pure argmax** over its cards of E_columns[leaf after one book playout to the end of the
turn]:
- enemies who already played: their card dealt from a softmax (τ 2, static book scores, 15% uniform), up to 24 draws;
- enemies still to play: 1/4 the book's deterministic FULL reply, 3/4 draws from the same static softmax. Both see our
  card (§1.3);
- teammates still to play: the FULL book, which reacts to our candidate (computer teammates' facedown cards are
  visible to each other: `Determinizer.kt:53`, `CardSim.kt:84-110`). The teammate then runs its own search when its
  turn comes, so the first seat modelled it as the book and it actually searches. There is no joint (card1, card2)
  enumeration; ADR-061's `joint=2` exists only in the old `SearchAi`;
- no tree under card plays (`tc`=0), so after the reveal both sides follow the book to the leaf.

There is **no mixing and no equilibrium reasoning**. The book's G17 coin between near-equal cards is disabled in
playouts (`BookPolicy.kt:32`) and only orders the candidates at the root. Regret matching was dropped because "every
opponent here is deterministic" (RESULTS.md, Track C). Consequences:
- Against the test opponents: old s10 is a different algorithm, so the hybrid is not "exploiting its own book"
  there. But **x10 vs h10 95.8%** (Track C log `.../t3-09da4030-.../ac020ecd-.../runs/final.txt`: 92–4 of 96) and
  every x-vs-x keep decision pit the search against opponents whose post-reveal play *is* the playout policy, and whose
  card play is partly the column-0 reply. Those numbers measure exploitation of a known policy, not strength against
  a human. [log]
- Against people: answers depend only on (APP_SEED + strength, decision id, seat, session) (`AiPlayers.kt:41-42`).
  The same line therefore gets the same reply every game, and the card play is a deterministic best response a player
  can learn. Product risk; not measured.

## 4. The leaf

- NET6 = mean of six nets' logits × coinScale, Red minus Blue, from the asking team's side (`LearnedEval.kt:222-236`).
  Trained on **turn boundaries** = "the first decision of a new turn" of real games (`test/.../learned/Learned.kt:266-300`),
  which is exactly where a TURN playout stops (`HybridAi.kt:640-667`: stop when `turnIndex` changes, at a pending
  decision). The kind of position matches. [code]
- Turn 4: `state.turn` stays 4 through EOR effects, the minion battle, the push and the level-ups; round++ and turn=1
  happen in `pityStep` (`Flow.kt:363-368`, `:536-540`). So turn-4 playouts include the round end and turn 1–3 playouts
  do not. Within one decision every candidate uses the same horizon and ends at the same kind of point, so they are
  comparable. The exceptions are a playout that ends the game (§2.4) and the 600-answer cap (`maxSteps`), which scores
  a mid-turn state the nets were not trained on (rare; UNVERIFIED frequency).
- Objective mismatch: the rollout policy's inner looks (CardSim card looks, RestLook on 6 squares) maximise **HEval**,
  while the outer search maximises **NET6**. The playouts follow what HEval likes and are then judged by the net.

## 5. Book playouts as the rollout policy: inherited blind spots

- **The minion race and the last push.** [log] W2T vs s10 held out, `final.txt`: on 648–695, s10 won 31 of its 44 games
  by LAST_PUSH; on 600–647, 23 of 39. W3 vs s10 dev (C2 notes): s10 won by last push 20 of 27. REVIEW-2-losses.md §4: the
  book takes fewer minions (1.09 vs 1.36 a round), and `TurnPlan.derive` never derives HOLD_LINE. On this branch the
  fix exists but is **off**: `if (ctx.profile.on("hold", false)) holdLine(…)` (`heuristic/TurnPlan.kt:29`), and
  SEARCH_10 does not set `hold`. In every playout our own team therefore never holds the line. The search can reach a
  hold square only if it is among the static top 8 squares or the tree's top 3, and only two choices deep. On turns 1–3
  the push is never played out, so the leaf alone judges it. The two attempts to look further were rejected: `lw=3`
  52.1% at 45 s max, and the round horizon 32.8%, "the book's own errors over four turns".
- **Empty-hand deaths.** A turn-3 card play is scored at the end of turn 3. The two-turn horizon was rejected (`hz3=2`
  48.4% on W2). The tree cut empty-hand defeats [doc: 88 vs 130] but the rollout still ends where the book's look
  ended.
- Summary: the search corrects the book within one turn and two choices; whatever the book never proposes or never
  plays (holding, slow minion arithmetic across turns), the search cannot see.

## 6. Engineering risks for shipping

1. **The memo never works in the app, and the app pays for it.** Android creates a new player for every decision
   (`feature/board/.../GameViewModel.kt:620-621` → `PlayEngineModule.kt:69`), and so does the web
   (`web/engine/.../EngineHost.kt:192`, `:246-247`). `resetMemo` starts empty each time. Every playout step still
   computes `posKey` (state copy plus full serialization hash, `HybridAi.kt:672-680`, `StateHash.kt:18-29`), and the
   filled memo is thrown away. The tournaments keep one instance per team per game (`test/.../Tournament.kt:354-358`),
   where 66,538 of 485,928 playouts (13.7%) were memo hits [log, C2 f1a summary]. **The app is slower than every
   measured time**, by those playouts plus the hashing. Answers are identical (§2.6). Fix: cache the player per
   (game, seat) or set `tt=0` in the app.
2. **Worst-case time is unbounded and higher than any budget.** There is no wall clock. 2v2 has no cap, and Blue card
   plays reach about 24 columns × the cards in hand. Measured: W3 2v2 card play max 21.98 s and 20.86 s (one thread per
   answer, loaded machine); W3 3v3 23.3 s with 3 threads, 14.7 s with `6.cap=12`; the shadow with cap 8 gave 9.0 s
   [log/doc, C2 summaries]. The web worker runs `SampleRunner.SEQUENTIAL` (`forApp` default, `EngineHost.aiFor`), so it
   gets no thread speed-up. JS timing: UNVERIFIED.
3. **The speed meter measures the wrong player.** "Measures this phone's speed at strength 10" runs the **old**
   `SearchAi` (`G/ai/AiBenchmark.kt:102`, `PlayEngineModule.kt:43-90`). [code]
4. **No JVM↔JS parity for the shipped s10.** `GameParityTest` pins the book (EASY/NORMAL) and the *old*
   `searchDeterministic(8/10)` (`core/game/src/commonTest/.../GameParityTest.kt:119-121`, `:161-164`), and
   `EngineHostTest` uses strength 3. RESULTS.md (Track E) says "GameParityTest's strength-10 hash changes when the
   switch flips". That is **false**: nothing there runs `HybridAi`. Whether phone and browser play the same s10 is
   untested, and nothing needs re-pinning for NET6. [code]
5. **Bundle size.** NET6 adds `LearnedWeightsG1..3.kt`, 209,089 bytes of Base64 source each (≈627 KB), plus 3 more
   forward passes per leaf. Size in `engine-worker.js`: UNVERIFIED (doc: "about 0.6 MB, not measured").
6. **Cancellation granularity.** `cancelled()` is polled only at task start and at tree nodes (`HybridAi.kt:218`,
   `:232`, `:411`). Playouts never poll, and a cancelled tree node still plays a playout instead of returning, so a
   cancel waits for every in-flight task's remaining playouts (turn 4 runs through the round end). Android comment
   "stops it within a playout" (`PlayEngineModule.kt:71`) understates this. The web ends the worker.
7. **Silent fallbacks hide failures from measurement.** An exception in `search` makes the move the book's
   (`HybridAi.kt:130-136`); one in a playout answer becomes `Fallback.choose` (first non-decline option,
   `BookPolicy.kt:57-64`, `AiPlayers.kt:380-395`). There is no counter in `stats`, so a broken path plays as the book
   and corrupts playout values without showing up in any log.
8. **WIP state.** HEAD 959e4013's message says "uncompiled gates, parity not re-pinned". For the parity part see
   item 4.
9. **Memory.** The memo holds up to 400k entries per instance before a reset (tournaments only; tens of MB by a
   rough per-entry estimate, UNVERIFIED). The app never accumulates it.

## 7. Ranking

| # | Finding | Severity | Evidence |
|---|---|---|---|
| 1 | Logit-scale mean over columns with ±1000 terminals: picks slow losses over risky wins in decisive turns, chases junk-reply wins | changes strength | code §2.4 |
| 2 | Playout opponent model sees our facedown card (knowHands `h.hand` reads); Red seats only | changes strength | code §1.3 |
| 3 | Red-seat reply model = 4 columns; seed-only change flips 17.5% of those card plays; K=24 wasted there; the model differs by seat colour | changes strength (noise) | log §2.2 |
| 4 | Book blind spots inherited: `hold` off in playouts, no push before turn 4; s10 still wins mainly by last push (31/44, 23/39, 20/27) | changes strength | code + log §5 |
| 5 | Rollout looks optimise HEval, outer search NET6 | changes strength (unquantified) | code §4 |
| 6 | x-vs-x and x-vs-h10 results measure exploitation of the playout policy | misleads measurement | §3 |
| 7 | App/web create a player per decision: memo dead, hashing kept; app slower than measured | misleads measurement (time) | code §6.1 |
| 8 | Speed meter benchmarks old SearchAi | misleads measurement | code §6.3 |
| 9 | No JS parity for shipped s10; RESULTS claim about GameParityTest wrong | misleads / ship risk | code §6.4 |
| 10 | Silent fallbacks, no counter | misleads measurement | code §6.7 |
| 11 | Unbounded worst-case time (2v2 no cap; web single thread); coarse cancellation | ship risk | log + code §6.2, §6.6 |
| 12 | `6.cap` drops the model's tail and doubles the book reply's share | small | code + log §2.8 |
| 13 | NoPeekingTest gaps (3v3, human teammate, tokens placed this step); determinizer order dependence | latent | code §1.2 |
| 14 | Deterministic pure strategy: a person can learn lines | product | code §3 |
| 15 | Root book card look run only for ties (wasted); human teammate's hand read | cosmetic | code §2.1, §1.2 |
