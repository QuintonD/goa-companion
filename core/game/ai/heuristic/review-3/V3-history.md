# V3 — History audit: every path the computer player took, from the first search to wave 3

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

Auditor V3, 2026-11-24. Read-only. Nothing was built or run except `python3 -I` over logs and numbers.

**Evidence tags.** `[doc X §Y:L]` = read in the repo at branch `t3/strength-10-search` unless another branch is named
(L = line in that file on that branch). `[scratch …]` = an agent's notes or run log, read directly. `[recomputed]` =
my own arithmetic (scripts in `scratchpad/v3work/py/`). `UNVERIFIED` = not checked; kept out of conclusions.
Docs used: `core/game/ai/AI.md` (1774 lines), `STRATEGY.md`, `docs/DECISIONS.md` (ADR-035, 039, 047, 049–052,
060–064, 085), `core/game/ai/heuristic/{RESULTS,DESIGN,GENERAL,README,REVIEW-2-*}.md`, `HEVAL.md` on
`t3/evaluate-strongest-computer-player` (B) and `t3/implement-shared-brief-task` (B2), each wave-3 branch's
`RESULTS.md` diff, the integrator's `r3-*.txt` briefs, the C2 / C2-resume / E2 scratch logs.

---

## 0. Findings in one page

1. **Nothing measured on held-out line-ups has reached the 65% target.** The best held-out number for any
   new strength 10 is W2T 56.8% (109/192, Wilson 49.7–63.6) on 600–695 [doc RESULTS.md "Wave 3 — Track C":454-458]:
   its upper bound is below 65%. The candidate that ships on the most integrated branch (W3 + NET6 + `6.cap`=8,
   `AiPlayers.kt` on `t3/strength-10-search`) has **no completed held-out measurement**; its running checkpoint stands
   at 17–12 (58.6%, Wilson 40.7–74.5) [scratch C2-resume runs/s10-600.txt; recomputed].
2. **Gains measured inside the hybrid family shrink by half or more against old s10.** Chaining the dev head-to-heads
   (W1 59.4% → W2 55.5% → W2T 56.6%) predicts W2T ≈ 70% against s10; held out it is 56.8%. W2T beats h10 95.8% and
   h10 scores 24.0% against s10, which predicts 88% for W2T vs s10 [recomputed]. Intransitivity is large, so the
   current chain (W3 59.4%, NET6 56.9%) cannot be read as "≈ 80% vs s10".
3. **Dev and early-stopped SPRT numbers are systematically optimistic**: x10 65.6% → 52.1% held out; W2T 71.9%
   (64 games, SPRT stop) → 56.8%; v0.14's three-net mean 71.9% (64, SPRT stop) → 58.6% on the next 128; G4-off +6.3 →
   −2.1. The same 71.9%-at-64-to-96-games pattern now headlines W3 vs s10 (dev 2200–2247, 96 games).
4. **The pivot did not deliver "traditional heuristics" at strength 10.** The book alone is 24.0% vs s10 (192, held
   out). Strength comes from a determinized search with the old learned ensemble (now six nets) at the leaf; the
   hand-written leaf loses to the net leaf 38.9% head to head (144, SPRT H0). The owner's ruling that HEval must ship
   was reversed within the same day. Net: the project returned to "search + learned evaluation" with a better
   playout policy and a post-reveal tree.
5. **The record predicted the HEval-leaf failure before the pivot**: v0.9 measured the net over the hand evaluation
   at strength 10 by 67.2–67.7% [doc AI.md "Learned evaluation":280-287]. Wave 3 re-learned it at the cost of two tracks.
6. **What did pay, every time it was tried: a better playout/decision policy and a longer horizon *after the reveal*.**
   What never paid: longer horizons with the same rules-of-thumb policy (≥ 7 tries), hand weight re-fits after the
   first, per-hero tuning (3 eras), joint team play, better guesses of the enemy card, the kill extension (twice).
7. **The owner's real complaint (a human finds s10 weak) is not what is measured.** No human game was ever logged
   ("Not measured: … a human" [doc AI.md "v0.14 together":1537-1541]). Readability — the one AI-vs-AI proxy for a
   human exploiting the bot (oracle names s10's card 88.9%, wins 60.9%) — was never measured for the new search,
   which is pure argmax [code HybridAi.kt:294-341 on t3/strength-10-search].
8. **The wave-2/3 instruments are anchored to the yardstick**: the regret probe, the probe bench and the category
   swaps all use old s10 (search + net) as the oracle; Track E found the bench leans to the net and did not predict
   game results. They can steer toward s10's taste, not past it.

---

## 1. Timeline

Rates are mirrored games unless noted; [n] = games; CI = 95% Wilson unless "pairs". "dev" = tuning/development
line-ups; "HO" = held-out at the time; "HO\*" = 300–395 / 600–695 after the wave-3 audit declared them no longer clean.

| # | Era (date) | Set out to | Measured (n, interval, range) | Concluded | Source |
|---|---|---|---|---|---|
| 1 | First AI, four levels (2026-10-24) | Determinized playouts (one ply, horizon end of turn), static policy, hand eval; Easy/Normal/Hard/Expert | Normal–Easy 93%, Hard–Normal 76%, Expert–Hard 60% (line-ups 96–191; n not stated per pair); set-up heroes gain least (±25 pts per hero) | Ladder exists; top step thin | [doc AI.md "Measured strength":725-728]; [doc DECISIONS ADR-035:682] |
| 2 | v0.7 slider + TUNED (10-25) | 1–10 slider; fit eval terms to outcomes | Logistic FitTest (12 096 positions): exposure ≈ −0.04×, opportunity 2.35×; TUNED (exposure ×0.3, opp ×2) 64.1% (57–71) Hard, 59.9% (52.8–66.6) Expert [192]; ladder every step > 50%, 10 vs 9 55.7% (50.7–60.6) [384] | "Top end is flat": 24 samples 52.0% [448], next-turn horizon 37.5% [128], τ 0 43.8%, wider one-ply 44.5% [128] | [doc AI.md "Evaluation":182-197, "What changed in v0.7":563-589, "Measured strength":693-719]; [doc ADR-039:937] |
| 3 | v0.9 part A: 3v3 (10-27) | Owner beat s10 "easily" in 3v3; test four suspects | Push Potential 0.35 + defenseless floor vs v0.8 s10: 55.5% (50.5–60.3) [384 HO]; double exposure 41.1%; focus fire 48.4%; racing 51.0% [96]; `Hunt` exploiter never beats s10 | Starvation real only in 4v4; caution tuned away by self-play; kills over two turns are the gap | [doc AI.md "Team sizes":735-850]; [doc ADR-047 A:2008] |
| 4 | v0.9 part B: learned net (10-27/28) | NNUE-style value net at playout ends, λ-returns from hand eval | s10+net vs s10+hand: 3v3 67.7% (60.8–73.9), 2v2 67.2%, 4v4 65.1% [192 each, test family]; result-only target: best log-loss, 37.5% in play; net in one-ply looks 45.3%; merged vs v0.8 s10 64.6% 3v3 / 69.3% 2v2 / 60.9% 4v4 [192] | "A sharp, noisy evaluation is exploited by its own search"; net trades heroes for lane (1.98 vs 1.29 heroes lost/game) | [doc AI.md "Learned evaluation":256-299, "Two parts together":852-918]; [doc ADR-047 B/together] |
| 5 | v0.10 net with hero/card/piece tables (10-31) | Per-hero residuals, card tables, piece inputs | vs v0.9 s10: 3v3 60.5% (55.6–65.2) [400], 2v2 59.4% [192], 4v4 56.8% (49.7–63.6) [192]; retrain without tables 48.4% [384]; upgrade tables 50.6% [1000] | Tables pay; log-loss "hardly separates what play separates" (p0 0.5842 / b1 0.5808 vs 48.4% / 56.0%) | [doc AI.md "v0.10":353-480]; [doc ADR-049:2318] |
| 6 | v0.10 depth gate (10-31) | Prove depth pays before a learned move policy | Next-turn horizon variants 22.7–38.2% [20–96, several runs cut at 2 h]; double the search 42.2% (35.4–49.3) [192]; half the work 46.9% [96] | "Deep search is out"; "the next gain is in what judges a position … a reading, not a proof"; ISMCTS / team tree not tried | [doc AI.md "Depth and speed":920-968]; [doc ADR-050:2358] |
| 7 | v0.14 A: ensembles (11-04) | Make the eval harder to exploit | Mean of 3 seeds at leaves vs v0.10 s10: 71.9% [64, SPRT stop] then 58.6% [128] → 63.0% (56.0–69.5) [192]; also in one-ply looks 57.5% (52.0–62.8) [320, time-fair]; aux heads: mean-of-3 50.0% [384]; lcb 55.7% | Averaging wins; aux heads add nothing after averaging | [doc AI.md "The evaluation the search exploits less":976-1099]; [doc ADR-060:2532] |
| 8 | v0.14 B: team play (11-04) | Owner: coordinate, take two-hero kills | Joint play 49.5% [192, stopped]; kill extension 46.0% [176, H0]; regret matching 49.5% [192, stopped]; oracle (knows the card) 60.9% (54.8–66.7) [256], hit 88.9%; vs RM 55.5% [256]; two-hero kills taken 10–15% | None ships; readability is "the lever"; RM "did not mix enough" (reason not checked) | [doc AI.md "Team play":1115-1267]; [doc ADR-061:2569] |
| 9 | v0.14 C: hand knowledge + card model (11-03/04) | Owner: s10 knows enemy hands; better guess of the played card | knowHands 51.3% (46.3–56.3) [384, undecided], fewer heroes lost; learned card model offline log-loss 1.119 vs 1.272 (old model worse than uniform 1.214), in play 51.0% (47.0–55.0) [592], s7 47.8% H0 | Hands kept (owner's request); "the next gains are not in the guess of the enemy's card" | [doc AI.md "v0.14: hand knowledge":1269-1362]; [doc ADR-062:2609] |
| 10 | v0.14 D: bench, per-hero scales, exploiters (11-04) | Per-hero tuning by NTBEA; learned exploiters | 7 heroes' rows on fresh line-ups 35.9–52.2% (none held); exploiters vs s10 46.9–55.2% [96]; `@life` 51.6% [192, H0] | "Per-hero knowledge that pays is the net's job, not a hand-tuned multiplier" | [doc AI.md "Stream D":1364-1490]; [doc ADR-063:2654] |
| 11 | v0.14 together (11-04) | Integrate | New s10 vs 0.13.0 s10, test family: 3v3 62.5% (54.4–70.0) [144, H1]; 2v2 56.3% (49.2–63.1) [192]; 4v4 57.3% (50.2–64.1) [192]; oracle vs new 41.7% [96] | Ships (this is "old s10", the floor) | [doc AI.md "v0.14 together":1492-1541]; [doc ADR-064:2687] |
| 12 | **Pivot** ADR-085 (11-20) | Owner: "Abandon the current approach in favour of more traditional approaches" — rule book + 32 hero playbooks, no playouts, < 20 ms | Foundation h10 vs s8 14.6%; general v2 h10 vs s8 46.9% HO, vs s10 17.2% HO [192] | Book must be measured against the old player | [doc ADR-085:2996-3022]; [doc heuristic/DESIGN.md:1-56] |
| 13 | Integration with 32 playbooks (11-21) | Merge playbooks; fix shared flaws | h10 vs s8 54.2% (49.2–59.1) [384 HO]; vs s10 15.6% (11.2–21.4) [192 HO]; playbooks vs bare book 52.1% [192]; per-hero ablation mean 50.6%; h10 vs h8 50.5%; lane ×1.4 the one weight that moved (54.9% [768]) | "Layers from 9 up add nothing"; empty-hand deaths and 3v3 weak | [doc RESULTS.md §1-§5:9-141]; [doc ADR-085 integration] |
| 14 | Wave 2 reviews (11-21) | Find why the book loses (REVIEW-2-losses) and what search on the book buys (REVIEW-2-search) | Category swaps vs s8 [128 each]: own moves by s10 +11.7, card plays +8.6, G4 off +6.3, actions −0.8; x10 (book playouts, scope 3, net leaf) vs s10 65.6% [64 dev] → 52.1% (45.0–59.0) [192 HO]; HEval leaf 12–22 pts under net leaf [64] | Errors sit where the look's horizon ends; search over the book is the strongest player; leaf is the second lever | [doc REVIEW-2-losses.md §5-§6:191-290]; [doc REVIEW-2-search.md:71-141] |
| 15 | Wave 3 A: policy (11-22) | F1 rest-of-turn look, F2 sampled reveal, F3 escape, bench | F1: h10 vs s8 54.7% → 70.3% [192 dev]; HO\* 70.3% / 60.4% [192 each]; vs s10 24.0% (18.5–30.5) [192 HO\*]; F2 65.1% (rejected); ladder h10:h9 59.4% HO\*, h9:h8 52.6% (45.6–59.5) HO\* | "F1 is the step"; the book alone is not s10's answer | [doc RESULTS.md "Wave 3 — Track A":150-310] |
| 16 | Wave 3 B / B2: HEval as a leaf (11-22/23) | Hand-written leaf as good as the net (owner ruling #1) | Fitted sets as leaf vs s10 43.8 / 40.6 / 35.9% [64 each]; B2 `lastRace` 57.5% (51.2–63.6) [240, H1] inside an HEval-leaf x10; HEval+lastRace leaf vs net leaf 38.9% (31.3–47.0) [144, H0] | Better outcome-prediction ≠ better leaf; bar not met | [doc RESULTS.md "Track B" on t3/evaluate-strongest-computer-player]; [doc HEVAL.md]; [doc RESULTS.md "Track B2" on t3/implement-shared-brief-task] |
| 17 | Wave 3 C: hybrid search (11-22/23) | Make x10 the strongest and ship it | W1 56.3% [128]; W2 (rep) 55.5% [128]; W2 vs s10 54.2% (47.1–61.1) [192 HO\*]; tree td2/tw3 56.6% (50.5–62.6) [256]; depth 3 43.0% H0; round horizon 32.8% [64]; W2T vs s10 71.9% [64 dev, SPRT(0.6)] / 56.8% (49.7–63.6) [192 HO\*] / 3v3 61.5% (51.5–70.6) [96 HO\*]; vs h10 95.8% [96] | Strength 10 = W2T; "short of 65%" | [doc RESULTS.md "Wave 3 — Track C":352-509]; [doc ADR-085 addendum] |
| 18 | Wave 3 D: playbooks (11-22) | Fix 5 heroes; re-ablate 32 at h10; in-search ablation | 32 heroes at h10: 50.0% (48.8–51.3) [6144]; Mortimer 44.8% (39.9–49.8) [384] switched off; in x10 (HEval leaf, scope 2) with playbooks 57.8% (49.2–66.0) [128], "not a loss" H0 | Playbooks add nothing to the book; probably something inside the search | [doc RESULTS.md "Track D" on t3/resume-scratchpad-task-1] |
| 19 | Wave 3 E: NET6 leaf (11-22/23) | Retrain nets on book/hybrid positions | Retrained trios alone 47.7% [256], 44.8% [192, H0]; six-net average vs leaf=2 in W2 56.1% (51.7–60.3) [512, H1]; 3v3 59.4% (49.4–68.7) [96]; W2+NET6 vs s10 55.5% (46.8–63.8) [128 HO\*] | Better fit (log-loss 0.561 vs 0.582) not a better leaf; averaging across families wins | [doc RESULTS.md "Track E":511-599] |
| 20 | Wave 3 C2: W3 (11-23) | Cheap F1 book in playouts; 3v3 under 10 s; last push | W3 vs W2T 59.4% (51.6–66.7) [160, H1]; W3 vs s10 71.9% (62.2–79.9) [96 dev]; W3+NET6 vs W3 56.9% (51.4–62.2) [320, H1]; lw=3 52.1% [96] not kept; 3v3 card play max 23.3 s (cap=12: 14.7 s); HO\* W3+NET6 vs s10 running 17–12 | Not in any committed doc; WIP commit "uncompiled gates, parity not re-pinned" | [scratch t3-0453f4ee…/notes.md:1-7]; [scratch t3-f53f8cfc…/runs/s10-600.txt]; `git log t3/strength-10-search` 959e4013 |
| 21 | Wave 3 E2: search-value targets (11-23/24) | Train the leaf on the search's backed-up values | ~970 games of search values recorded (sv1-1 563 games 6 624 s; sv1-2 407 games 6 391 s); training queued, **never run** | Nothing measured | [scratch ~/.goa-qa/trackE2/logs/*.log]; commit ee34875a "training not run" |

**Rate of progress, old line vs new.** Each old iteration added roughly +70 to +100 Elo against its predecessor on
held-out/test families: v0.8 TUNED 59.9% (+70), v0.9 64.6% 3v3 (+104), v0.10 60.5% (+74), v0.14 62.5% (+89)
[recomputed from the rows above]. The pivot's best held-out 2v2 result over old s10 is +48 Elo (W2T 56.8%)
[recomputed]. So the five days since 11-20 bought about half of one old iteration, held out, at much higher compute
(see §3).

---

## 2. Recurring patterns

### 2.1 Ideas tried more than once

| Idea | Tries (era: result) | Reading |
|---|---|---|
| Longer horizon with the same rules-of-thumb policy | v0.7 next turn 39–44% [36] and 37.5% [128]; v0.10 next turn 23.8–38.2%; v0.10 "tied answer to next turn" 48.3% [1000]; REVIEW-2-search hz=3 60.9 vs 64.1% [64]; Track B round horizon 29.2% [48]; Track C hz=3 32.8% [64], hz3=2 48.4% [128]; C2 lw=3 52.1% [96] | **Seven-plus failures, one cause stated each time** (later turns played by a weak policy). The one horizon extension that paid changed the *policy* too: F1 (+15.6) and the post-reveal tree (+6.6) [doc RESULTS.md Track A:195; Track C:432] |
| Better playout / decision policy | v0.7 everyone's one-ply looks (10 vs 9 55.7%); v0.14 nets inside one-ply looks 57.5%; x10 pol=1 vs pol=0 78.1% vs 64.1% vs h10 [64]; W3 cheap F1 book 59.4% [160] | **Paid every time** it was measured. Never made the primary research line until wave 3 |
| Kill extension through the next turn | v0.14 46.0% [176, H0]; Track C B+kill=1 50.8% [128] | Repeated dead end |
| Joint team card play / two-hero kills | v0.14 joint 49.5% [192]; with threat card 53.6% [384]; with kill ext 50.0% [256]; book layers "twoTurnPlans" 50.0% [128] [doc REVIEW-2-design §3.3] | Repeated; the owner's request is still unmet (10–15% of two-hero chances taken, never re-counted for the new search) |
| Per-hero hand knowledge | v0.14 NTBEA per-hero scales: 0 of 7 held; integration ablation mean 50.6%; wave-3 D 50.0% over 6144 games at h10 | Three eras say the same; ADR-085's premise ("one playbook per hero") was built anyway (32 playbooks) |
| Re-fitting hand weights to outcomes | v0.7 fit → TUNED (**paid**, 59.9–64.1%); v0.7 refit 0.15 / 3.5 not better; v0.14 NTBEA flat; Track B fitted sets even as h10 (49.2% [128]) and worse/equal as leaf (35.9–43.8% [64]) | The first fit paid; every refit after it was flat. The audit's blanket "re-fits all came out flat" [scratch r3-audit.txt] forgets v0.7 |
| More samples | v0.7 24 samples 52.0% [448]; v0.10 double 42.2% [192] (decided below 0.55); REVIEW-2-search K16 60.9 vs 64.1% (vs h10) and 56.3 vs 65.6% (vs s10) [64]; Track C W1 (K 24 + wider) 56.3% [128] **kept** | Contradictory and never root-caused (see §2.4) |
| Better guess of the enemy's card | v0.7 opponent-model τ 0.8/3 51.6/50.0% [128]; v0.14 learned model 51.0% [592]; REVIEW-2-search uniform = model (64.1% both) [64]; Track A `predict` 70.3 vs 70.8%; F2 sampled reveals 65.1% vs 70.3%; Track C W2 `rep=4` (opponent-model replies) 55.5% [128] **kept** | Treated as worthless — while `rep` (variety from the same model) is kept in SEARCH_10 on an interval that includes 50% |
| Hand evaluation as the leaf | v0.9: net beats hand 67.2–67.7% [192]; REVIEW-2-search: HEval 12–22 pts under net; Track B: fitted leaves 35.9–43.8% [64]; B2: 38.9% [144, H0] | Answered in v0.9; re-asked twice in wave 3 under the owner's ruling |

### 2.2 Development optimism and early-stopped SPRT

| Claim (dev or SPRT-stopped) | Then (fresh or held out) | Source |
|---|---|---|
| x10 best vs s10 65.6% [64 dev] | 52.1% (45.0–59.0) [192 HO] | [doc REVIEW-2-search.md:82, 98] |
| W2T vs s10 71.9% (59.9–81.4) [64 dev, SPRT(0.6) H1] | 56.8% (49.7–63.6) [192 HO\*] | [doc RESULTS.md Track C:448-458] |
| v0.14 three-net mean 71.9% [64, SPRT stop] | 58.6% (49.9–66.8) on the next 128 | [doc AI.md:1030-1032] |
| G4 off +6.3 (59.4% vs 53.1%) [128] | −2.1 on the next 192; 55.3 vs 54.1% over 320 | [doc RESULTS.md Track A:205-206] |
| Ablation "losers" < 47% at h10 (Arien 45.8, Ignatia 45.3, Snorri 45.8, Ursafar 46.9) [192] | 56.8, 54.2, 55.2, 56.2 on fresh sets | [doc RESULTS.md Track D, t3/resume-scratchpad-task-1] |
| W3 vs s10 71.9% (62.2–79.9) [96 dev] | running HO\* 17–12 = 58.6% (40.7–74.5) [29] | [scratch C2 notes.md:3]; [scratch s10-600.txt; recomputed] |

An SPRT that stops at the first boundary crossing reports a biased-high point estimate; the project quotes those
points in summary tables (RESULTS.md Track C summary row "s10, 2v2, dev 1600–1631, 64, 71.9%" [doc :493];
CONTEXT.md's headline list). **Intransitivity** compounds it [recomputed, `py/elo.py`]:

| Chain | Predicted vs s10 | Measured vs s10 |
|---|---|---|
| W1 59.4% (dev) → W2 vs W1 55.5% | 64.6% | W2 54.2% [192 HO\*] |
| … → W2T vs W2 56.6% | 70.4% | W2T 56.8% [192 HO\*] |
| W2T vs h10 95.8% and h10 (F1) vs s10 24.0% | 87.8% | 56.8% |
| v0.14: 63.0% then 57.5% | 69.7% | 62.5% (test family, 3v3) |
| … → W3 vs W2T 59.4% → NET6 56.9% | 82.1% | not measured (running 58.6% [29]) |

Within-family head-to-heads overstate gains against s10 by a factor of about 2–3 in Elo. Any claim that W3+NET6 is near
or above 65% rests on this chain and is unsupported until the held-out run completes.

### 2.3 Conclusions that contradict each other across eras

1. **"Deeper search does not pay"** (ADR-050: "Deep search is out … the next gain is expected in what judges a
   position" [doc DECISIONS:2365-2382]) vs wave 3, where the two largest gains were search: F1 (rest of the turn,
   +15.6 points for the book) and the depth-2 tree after the reveal (56.6% [256]), and scope 3 (every question
   searched) took x10 vs s10 from 25.0% to 43.8% [64] [doc REVIEW-2-search.md:77-80]. ADR-050's gate tested only
   one form of depth (later turns played by a small static search) and generalised it. ADR-050 itself said it was "a
   reading, not a proof"; later docs (AI.md "Known weaknesses" "the top end was flat" [:1732-1738]) state it as fact.
2. **"Log-loss does not predict play"** holds in every era (v0.9 result-only target 0.552 best log-loss, 37.5% play
   [AI.md:273-277]; v0.10 p0 vs b1 [:477-480]; v0.14 averaging barely moves log-loss [:985-989]; Track B fitted sets
   0.587 vs 0.593 yet no better leaf; Track E 0.561 vs 0.582 yet 44.8–47.7% in play). This is the most robust lesson in
   the record. But the wave-3 audit rule "POSITIONS BEFORE GAMES" [scratch r3-audit.txt] replaced one proxy with
   another that also failed: Track E "the bench did not predict the games below (g had the lowest probe regret and
   lost)" [doc RESULTS.md:553-554]; F1 inside x10 was "undecided on positions" [:264-266] yet +9.4 points in games
   (W3 vs W2T). No proxy for play has been validated.
3. **"Weights fitted lose"** vs v0.7, where the fitted direction became TUNED and won 59.9–64.1% [AI.md:569-577].
   Correct statement: the first fit paid, refits after it did not.
4. **"Regret matching measured nothing"** (Track C: "measured nothing against s10 and every opponent here is
   deterministic; not retried" [doc RESULTS.md:386-387]) vs ADR-061, which measured it **even** on wins
   (49.5%, undecided), lost fewer heroes (1.56 vs 1.79), and named the unverified reason it failed to mix ("each column
   is one noisy sample") [AI.md:1212-1215]. "Every opponent is deterministic" is a reason tournaments *cannot*
   measure the value of mixing, not evidence it has none.
5. **"The guess of the enemy card buys nothing"** (ADR-062 consequence [DECISIONS:2647-2649]) vs the oracle: knowing
   the card is worth 60.9% [256]; the learned model's top-1 is 44.6% against the oracle's 88.9%. The data show that a
   *modestly* better guess bought nothing, not that the guess cannot matter. Wave 3 then kept `rep=4` (variety from the
   same model) on 128 games — the opposite reading.
6. **Value of hand knowledge.** v0.14: knowing hands is even, 51.3% [384] [AI.md:1298-1309]. REVIEW-2-design: "The
   old s10's hand knowledge is a large part of why it beats h10" [doc REVIEW-2-design.md §3.3:330]. No measurement
   supports the second; h10 : h10+knowHands=0 was 53.1% [128].
7. **More samples.** v0.10: double the search *loses* significantly (42.2%, 35.4–49.3 [192]) [AI.md:955]. Track C kept
   W1 = more samples + wider candidates (56.3% [128]). REVIEW-2-search: K16 worse than K8 (both 64 games). A search
   that gets measurably *worse* with more samples of the same distribution is an anomaly that was explained ("a
   search that averages harder finds what the evaluation overrates", "Reading (not proven)" [AI.md:963-966]) but never
   tested (e.g., by checking the same position's choice stability, or a bug hunt).
8. **HEval vs net leaf, policy.** Owner ruling #1 (2026-11-22): "The learned net is NOT an acceptable leaf"; same day:
   "Do whatever shows the most promise" [scratch r3-owner-ruling.txt]. Track B was briefed to "make HEval as good a
   leaf as the net" [scratch r3-B.txt]; v0.9 had already measured the net 67% over the hand evaluation.
9. **Mixing.** GENERAL.md §8 lists G17 mixing as the guard against "learn the card script and play the answer"
   [doc GENERAL.md:585]; REVIEW-2-design §3.2 then made strength 10 "Argmax, no G17 mixing" on 52.2% head to head
   [doc :313]; HybridAi chooses the best weighted mean, deterministically [code HybridAi.kt:294-341]. The guard the
   book's own research names against the owner's failure mode is off at the one strength the owner plays.
10. **DESIGN contract vs what ships.** DESIGN.md: "no playouts … do not call Searcher, Evaluator … LearnedNet or
    anything that plays out games", "< 20 ms" [doc DESIGN.md:14-27, 50-53]. Strength 10 on 11-24 is determinized
    playouts with six learned nets, 1.6 s mean / 21.9 s max per 2v2 card play [scratch C2 notes.md:3].

### 2.4 Results never re-checked

- **W1 and W2 knobs** (K 24, topS 8, topU 6, `rep=4`) were kept on 128 games with intervals including 50%; the
  audit flagged it [doc RESULTS.md:389-391] and the brief asked to "re-check them by SPRT if you build on them"
  [scratch r3-audit.txt]. No re-check is recorded; W2T, W3 and SEARCH_10 are built on them.
- **The playbooks inside the search** were measured with the HEval leaf at scope 2 (57.8% [128]); the shipping
  search uses scope 3 and the net leaf. Track D wrote "repeat the ablation with leaf=2" [doc RESULTS.md Track D]; not done.
- **B2's `lastRace`** won 57.5% [240] inside `x10+pol=1+scope=3+leaf=0` (K 8, no tree); it is the default only when
  the leaf is HEval, which does not ship. Never measured in W2T/W3.
- **`6.cap`=8** is in SEARCH_10 [code AiPlayers.kt:92-106] with only a 4-game 3v3 shadow test (137 card plays: answers
  equal to uncapped 99.3%; a different seed changes 13.9% of answers, regret 0.208) [recomputed from
  scratch …/runs/sh3v3a.txt]. The brief's SPRT for the 3v3 setting [scratch r3-resume-C2.txt item 5a] was not run.
- **The oracle exploiter** was never run against x10/W2T/W3, so readability of the new player is unknown.
- **The two-hero kill count** (`AI_KILLS`) was never run on the new search; the owner's coordination request is
  unanswered for what ships.
- **v0.10 per-hero "uplift" and the bench at strength 10**: "about 90 hours", never run [AI.md:1379-1381, 1538].
- **The "double the search loses" anomaly** (§2.3.7), never investigated.
- **The phone factor (3–4×)** under every budget since v0.9 "has never been measured" [ADR-050:2366; AI.md:1534-1535].
- **ADR-061's RM failure reason** ("one sample per column") "not checked" [AI.md:1214-1215].

### 2.5 Promising leads dropped for cost or time, not evidence

| Lead | Evidence at drop | Why dropped | Source |
|---|---|---|---|
| ISMCTS / a tree over the team's choices | Never tried | "kept … for when cheaper, deeper playouts had shown …" | [ADR-050 Alternatives:2376-2377]; [ADR-061 Alternatives] |
| `kill=2`, `jointThreat`, `rm=1` | Not played | "Compute went to the runs above" | [AI.md:1267] |
| Tree under card plays (`tc=2`) | 46.9% [32] | "too slow for the 10 s card-play limit" | [RESULTS.md:436] |
| A third net generation (v0.10) | b1 vs a1 46.9% but fewer heroes lost | "the time went to the gates" | [AI.md:475] |
| Aux heads ("fewer heroes lost is the lead to follow") | every aux net lost fewer heroes | averaging took the wins | [AI.md:1070-1075] |
| Learned card model as our own playout policy; trained on s10 games | not run | "did not fit beside the tournaments" | [AI.md:1350-1356]; [ADR-062 Consequences] |
| E2 search-value targets (Track E's own recommendation) | data recorded (~970 games, ~3.6 h) | session ended; training "not run" | [scratch ~/.goa-qa/trackE2/logs]; commit ee34875a |
| Playouts vs leaf split for playbooks | — | "frozen" for machine time | [RESULTS.md Track D] |
| 3v3 SPRT of the 6-seat cap | shadow only | session close-out | [scratch r3-resume-C2.txt]; commit 959e4013 |
| Per-hero bench at s10 | — | 90 h | [AI.md:1379-1381] |

The most important of these is the oldest: a tree over own/team choices was deferred in v0.10 pending a gate that
measured something else, and the post-reveal tree that wave 3 finally built is the first new gain of the hybrid line.

### 2.6 Dead ends repeated (with cost)

The book was built in wave 1 with a **one-action horizon** although AI.md had measured "every choice played to the
end of the turn" as the biggest single searching step (7 over 6, 66.7%, +120 Elo) [AI.md:709, 520-521]. REVIEW-2-design
§0.3 says so ("The old search's single biggest step was exactly this horizon") [doc :43-50], and F1 then recovered
+15.6 points. Equally, REVIEW-2-search's `y10` (s10 with the static book in its playouts) lost to s10 32.8% [64]: the
*static* book is a worse policy than the old search's own playouts, so the book only pays in its look-ahead form.
Other repeats: kill extension (×2), round horizons (×7+), joint play (×3), per-hero tuning (×3), HEval-as-leaf (×3).

### 2.7 Instruments anchored to the yardstick

The regret probe's oracle is `AiPlayers.profile(10)` with the net, more samples, `rm`, kill extension
[doc REVIEW-2-losses.md §1:22-28]; the category swaps replace h10's answers with s10's [§5:193-212]; the position
bench's probe set is scored by the same oracle [doc RESULTS.md:333-338]. So the wave-2 diagnosis ranked fixes by how
far they move the book *toward s10*. That is a sound way to reach s10 and a structurally weak way to exceed it by 15
points. The exact ground truth available (puzzle solver: 271 + 499 questions) separates the ladder but barely moves
between leaves (225–228 of 271) [doc RESULTS.md:553].

---

## 3. The pivot to "traditional heuristics" (2026-11-20)

**What was asked.** "Abandon the current approach in favour of more traditional approaches. Start with a robust
series of heuristics" [doc ADR-085:2998-3000]. The reasons given were explainability and editability, and "the search
… is strong and slow" — not a measured weakness of the search [ibid.].

**What it achieved, in strength.**

| Player | vs old s8 | vs old s10 | Source |
|---|---|---|---|
| Foundation book | 14.6% | — | [ADR-085] |
| General v2 | 46.9% HO [192] | 17.2% HO [192] | [ADR-085] |
| Book + 32 playbooks (11-21) | 54.2% (49.2–59.1) HO [384] | 15.6% (11.2–21.4) HO [192] | [RESULTS.md §5] |
| Book + F1 (h10 today) | 70.3% / 60.4% HO\* [192 each] | **24.0% (18.5–30.5)** HO\* [192] | [RESULTS.md Track A:298-301] |
| Search over the book (W2T, net leaf) | — | **56.8% (49.7–63.6)** HO\* [192] | [RESULTS.md Track C:458] |
| W3 + NET6 (WIP SEARCH_10) | — | dev only (W3 71.9% [96]); HO\* running 17–12 | [scratch] |

As a book, the pivot reached about old s8 plus 5–15 points, at 3–16 ms an answer instead of s8's 20 ms or s10's
~400 ms [RESULTS.md §5:122, Track A:298-301]. Against old s10 the book is roughly 200 Elo short (24.0% ≈ −200 Elo
[recomputed]). Every point the project now holds over old s10 comes from putting **search** back on top of the book,
with **the old learned nets** at the leaf (NET6 = the three v0.14 nets plus three fine-tuned from them
[RESULTS.md:582-586]).

**Was it the right call on the evidence?** On the evidence in AI.md at 11-20, no — and that evidence was borne out:
- The record said the gains were in the evaluation and that the net beat the hand evaluation by ~67% at strength 10
  (v0.9). A hand-written, explainable evaluation was always likely to be a weaker leaf; Tracks B and B2 confirmed it
  (38.9% head to head). The owner's goal of a traditional, explainable strength 10 is not met by what is strongest.
- The record also contained the lever the pivot later found: the playout policy. v0.10 planned "a learned move policy
  to make deep playouts affordable" and cancelled it when the depth gate failed [ADR-050:2363-2368]; v0.14 found nets
  in the one-ply looks paid (57.5%). An evidence-led next step from v0.14 was "improve the playout policy / search the
  post-reveal turn", which is what wave 3 ended up doing, via a detour through a stand-alone book.

On the owner's terms it was legitimate: the owner asked for it, and the owner's later goal change (11-21: "strongest
possible regardless of the device", search over heuristics allowed [doc REVIEW-2-losses.md:3-4; REVIEW-2-design.md:6-8])
re-opened search. The pivot's value should be judged as "a new playout policy and candidate generator", not as
"heuristics instead of search".

**What it cost.**
- Time and compute: 2 AI commits on 11-20, 95 on 11-21, 87 on 11-22, 23 on 11-23 [git log on ai/, recomputed by
  date], with four to six agents sharing one 8-core, 7 GB machine, many runs queued for hours (Track D's final
  re-run "waited more than 2 hours for the machine's build lock" [RESULTS.md Track D]).
- The strength ladder. Old: every adjacent step > 50% with lower bounds above 50% [AI.md:702-719]. Book: h10 vs h8
  50.5% at integration; after wave 3 h9 vs h8 52.6% (45.6–59.5) HO\* [RESULTS.md:303]; and strength 10 vs 9 is now a
  cliff (W2T vs h10 95.8%). STRATEGY §4.5's own pass bar (each step ≥ 62%, lower bound > 55%) [doc STRATEGY.md:904-910]
  is failed by 9/8 and 10/8.
- Speed at strength 10: W3 2v2 card play 1.6 s mean / 5.5 s p95 / 22 s max against s10's 0.8 / 1.7 / 8.9 s on the
  same shared machine [scratch C2 notes.md:3]; 3v3 max 23.3 s (cap 12: 14.7 s) [notes.md:6]; no wall clock by design.
  At the assumed 3–4× phone factor (never measured) a slow 3v3 card play would be about a minute. Against old s10 the
  comparison is not time-equal; the yardstick s10 is the uncapped deterministic player.
- The 32 hero playbooks: measured 50.0% at h10 over 6 144 games (Track D). Inside search a possible +8 [128, CI incl 50%].
- Two docs now drift: AI.md (most integrated branch) still says the leaf is "the old learned ensemble today" while
  `SEARCH_10_LEAF = HybridLeaf.NET6` [AI.md:13-14 vs AiPlayers.kt:76], and still quotes the book at 15.6% vs s10
  [AI.md:24-28] where Track A measured 24.0%. W3 and the C2 numbers exist only in scratch notes.

**What it added (real).**
- The F1 rest-of-turn look (+15.6 for the book; W3 cheap form +9.4 inside the search).
- Search over every question (scope 3) with book candidates; the post-reveal minimax tree (first measured gain from
  depth in the whole history); sample merging and a memo (4.8× faster, same answers) [RESULTS.md:366-368].
- Fast, explainable strengths 1–9 that are roughly as strong as the old ones at 8.
- The position bench (exact puzzle truth) and pentanomial SPRT discipline (from v0.14, enforced in wave 3).

---

## 4. What limits strength today (per the record)

1. **The last push (lane arithmetic).** s10 wins mostly by the last push: 31 of 44 of its wins on 648–695 against
   W2T [RESULTS.md:460-462]; 20 of 27 against W3 on dev 2200–2247 [scratch C2 notes.md:3]. The book took 1.09 minions a
   round against s10's 1.36 (16 games) [REVIEW-2-losses §4:149, 174-179]. Track B's residuals: last-wave lanes are 22%
   of leaves but 55% of HEval's disagreement with the net; the net prices one more minion there at ~52 coins, HEval at
   21 [HEVAL.md (B) "Residuals":129-131]. The one targeted try at the search level, lw=3, was 52.1% [96] [notes.md:2].
   *Caveat:* the minion-rate diagnosis is from 16 games of the book, not of the search.
2. **The leaf.** Net leaf vs HEval leaf 61.1% / 38.9% [144, H0 for HEval] (B2). NET6 gave +6.9 points over the
   checked-in trio inside W3 [320, H1]. The leaf is trained on old-search self-play; Track E showed the distribution
   shift (log-loss 0.582 → 0.561 on book positions) but fitting the shift alone lost [RESULTS.md:534-540, 560-566].
   The E2 lead (search-value targets) is unmeasured.
3. **The rollout policy.** Every gain in the hybrid line was a policy gain: pol=1 vs pol=0 (78.1 vs 64.1% vs h10
   [64]), scope 1→3 (25.0→43.8% vs s10 [64]), F1 in playouts (59.4% [160]). Every longer horizon lost because the
   policy errs over more turns (round horizon 32.8% [64], "the book's own errors over four turns outweigh what the
   leaf no longer has to judge" [RESULTS.md:415-416]). The policy is deterministic (temperature 0) inside playouts
   [REVIEW-2-search.md:17-19]; enemy replies get variety only at card plays (`rep`).
4. **3v3 cost.** W3 3v3 card play 2.9 s mean / 10.2 s p95 / 23.3 s max with three threads [notes.md:6]; the cap that
   brings the max under 10 s is in SEARCH_10 untested for strength (§2.4). 3v3 strength vs s10: W2T 61.5% (51.5–70.6)
   [96 HO\*]; W3 / NET6 3v3 vs s10 never measured. 3v3 is the size the owner plays [ADR-047 Context].
5. **Empty-hand defeats** were the book's main leak (37 of 78 defeats [REVIEW-2-losses §4:157]); F1 halved them (79 →
   41 [RESULTS.md:181]) and the tree cut them further (88 vs 130) [RESULTS.md:438-439]. Against s10, W3 now loses fewer
   heroes (0.74 vs 1.48 a game, dev) [notes.md:3]. On the record this is no longer the binding limit for the search;
   it remains one for the book (h10 vs s10: 1.20 vs 0.95 defeats a game [RESULTS.md:129]).
6. **Readability / mixing (unmeasured, likely binding against a human).** Old s10 with τ 0.15 was named 88.9% and lost
   60.9% to the oracle [AI.md:1205-1207]; the new search is argmax (§2.3.9). The owner's complaint is a human's.

---

## 5. Assertions stated as fact that rest on small samples or dev line-ups only

Wilson half-width at 55%: 64 games ±11.8, 96 ±9.8, 128 ±8.5, 192 ±7.0, 256 ±6.1 points [recomputed].

| Assertion (where) | Basis | Problem |
|---|---|---|
| W1 knobs are a gain; in SEARCH_10 (RESULTS Track C table "Kept: yes") | 56.3% (47.6–64.5) [128 dev] | CI includes 50%; acknowledged, never re-checked |
| `rep=4` (enemy reply variety) is a gain; in SEARCH_10 | 55.5% (46.8–63.8) [128 dev] | Same |
| W2T vs s10 71.9% (Track C summary table, CONTEXT headline) | 64 dev, SPRT(0.6) stop | Early-stop bias; HO\* 56.8% |
| W3 vs s10 71.9% (C2 notes; resume brief; CONTEXT) | 96 dev (2200–2247) | Dev only; not in a committed doc |
| W3+NET6 is "the current best candidate" (resume brief) | W3 vs W2T 160 H1; NET6 vs W3 320 H1, all dev | No held-out; intransitivity (§2.2) |
| NET6 helps in 3v3: 59.4% | 96, (49.4–68.7) | CI includes 50% |
| W2+NET6 vs s10 55.5% | 128 HO\*, (46.8–63.8) | CI includes 50% |
| W2T 3v3 vs s10 61.5% (ADR-085 addendum) | 96 HO\* (51.5–70.6) | Small; never repeated with W3 |
| Playbooks are "+8 points" inside the search (Track D) | 57.8% (49.2–66.0) [128 dev], HEval leaf, scope 2 | CI includes 50%; not the shipping config |
| Mortimer "loses" at h10, switched off | 44.8% (39.9–49.8) [384] | The minimum of 32 heroes tested; upper bound 49.8 with no multiple-comparison correction |
| lw=3, te=0, hz3=2, "wider still", kill=1 rejected | 52.1% [96], 50.0% [96], 48.4% / 52.3% / 50.8% [128] | Rejections on intervals including 50%; lw=3 and te=0 postdate the audit rule "No keep/reject decision on a game result whose 95% interval includes 50%" [r3-audit.txt] |
| "F2 as built loses 5–6 points" (Track A reading 3) | 65.1% vs 70.3% on the same 192 games | Difference of two rates each ±6.6; no paired test reported |
| F3 "small but the right way round", kept (Track A) | +0.5 points, 41 → 36 empty-hand deaths [192] | No evidence either way |
| `knowLook` / `branch=2` make 10 > 9 (DESIGN.md strength table) | 55.7% / 55.2% [192]; combined 56.6% [256 dev]; held out h10:h9 59.4% [192 HO\*] | Each part alone has CI incl 50%; "knowLook adds nothing measurable" yet kept "because principled" [RESULTS.md:226-228] |
| h9 > h8 (ladder) | 59.4%, 58.6% dev; 52.6% (45.6–59.5) HO\* | Not shown held out |
| Category-swap ceilings "+11.7 / +8.6 / +6.3 / −0.8" drive wave 3 (REVIEW-2-losses §5) | 128 games each, one line-up set | ±8.5 each; G4's +6.3 did not hold; "the action choice alone is worth nothing" rests on −0.8 ± 8.5 |
| Minion race 1.09 vs 1.36; 7 of 13 losses by last push; 37 of 78 defeats empty-handed (REVIEW-2-losses §4) | 16 games per pair | The diagnosis of wave 3 is from 64 diagnostic games in all |
| "More samples, longer horizons and the opponent model bought nothing" (REVIEW-2-search Reading 4) | 64-game rows | ±11.8; then contradicted by keeping W1 |
| "The net leaf beats HEval by 12–22 points" (REVIEW-2-search Reading 3) | 64-game rows vs s10 | Direction later confirmed (B2 38.9% [144]); magnitude unproven |
| "The old s10's hand knowledge is a large part of why it beats h10" (REVIEW-2-design §3.3) | none | Contradicted by ADR-062 (51.3% [384]) |
| "Deep search is out" (ADR-050) | 3v3 tune family; several runs cut at 20–44 games by a 2 h limit | Generalised beyond the variant tested; later contradicted (§2.3.1) |
| v0.14 2v2 / 4v4 gains (ADR-064) | 56.3% (49.2–63.1), 57.3% (50.2–64.1) [192] | 2v2 CI includes 50% |
| "Knowing hands … kills more and is killed less" (ADR-062) | 1.71 vs 1.94 heroes lost, 384 games, win rate even | Not tested for significance |
| v0.10 depth/hand per-hero suspects | ±22–25 points per hero | Stated by the doc as "names suspects, no more" — fine, but later docs cite the set-up heroes' gains as facts |
| Per-hero scorecards in every era | 10–40 games a hero a side | Noise (the docs say so); still used to pick heroes for work |

**Dev-only claims currently in shipping code:** every knob of SEARCH_10 beyond what W2T's held-out run covered
(`branch=1`, `restSpaces=6`, NET6 leaf, `6.cap=8`); Mortimer off at 10; B2's `race` set as the HEval-leaf default.

---

## 6. Method notes

- `py/elo.py`: Elo = −400·log10(1/p − 1), Wilson score interval, chain sums. `py/shadow.py`: per-variant counts over the
  `SHADOW` lines of `…t3-f53f8cfc…/runs/sh3v3a.txt` (137 3v3 card plays per variant, line-ups 3000–3003; v0 = W3L3 reference,
  v1 = seed 1, v2–v4 = `6.cap` 12 / 8 / 6).
- The running checkpoint `runs/s10-600.txt` had 29 finished games when read (17 won by W3L3); it is a live run and
  will change. Its `.summary` file was empty.
- Not verified: whether the integrator has other C2 numbers not in the scratch notes; the exact line-up overlap
  between dev ranges used by different tracks (C2/B2/E2 briefs say 2000–2459 and 3000–3471 were used by C2/B2).
