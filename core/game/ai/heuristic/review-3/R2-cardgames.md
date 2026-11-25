# R2: how the strongest AIs for imperfect-information card and board games were built

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

> **Erratum (integrator, checked in code):** this report says the old s10 solves the face-down card play by regret
> matching. It does not. `LevelProfile.rm` defaults to 0.0 (`ai/Search.kt`, the field under "Mixed card plays by regret
> matching") and no strength profile sets it, so `RegretMatching` is inactive in old s10. Old s10 plays a softmax over
> near-best cards with temperature 0.15 coins inside a 0.6-coin gap (`AiPlayers.kt`, strength 10 profile), which is
> almost always the best card. Old s10 and the hybrid are therefore both near-pure best responders; the comparison
> "old s10 = equilibrium, hybrid = best response" below does not hold. The rest of the report is unaffected.

Auditor R2, 2026-11-24. Scope: research only (no repo edits, no builds). Every number below comes from a primary
source that I downloaded and read (arXiv / proceedings / journal PDF, official competition slides, or the
project's code at a file:line). Where I could not open the primary source I say **UNVERIFIED** and keep the
claim out of the conclusions. PDFs and their text dumps are in
`/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/r2dl/{pdf,txt}/`.

## 0. What GoA's strength 10 actually is, as a game-theory object (this frames every lesson below)

- Per CONTEXT.md: each turn every hero picks a card face down **simultaneously**; after the reveal, resolution is
  deterministic and, for s10 (which may know enemy hands), a **perfect-information sequential game**. So the
  only real hidden information s10 faces is the opponent's simultaneous choice (plus, for weaker strengths, hands).
- The hybrid (the s10 candidate) is, structurally, a **best response to the book**:
  - the enemy's face-down card is drawn from "a softmax of the book's card scores, 15% uniform"
    (`t3/strength-10-search` HybridAi.kt:704; base 64955c18 BookPolicy.kt:110-113);
  - every candidate is played out against the same draw, rollouts are the book's **deterministic** best answers
    ("no softmax, no G17 mixing", BookPolicy.kt:24-26 on both branches);
  - the decision is the **argmax** of mean − risk·sd (+prior) (base HybridAi.kt:139-157; `t3/strength-10-search`
    HybridAi.kt:296-330). No mixed strategy. `RegretMatching` is used only by the old search
    (AiPlayers.kt:290, Search.kt:119; grep finds no use in `ai/hybrid/` on base or the three latest C/C2 branches).
- The old s10 (the baseline) already solved the face-down play as a **restricted-Nash matrix game by regret
  matching** (RegretMatching.kt:3-9, ADR-061). So, in the vocabulary of the literature below, old s10 is
  "SearchBot-shaped" with weak rollouts/leaf, and the hybrid is "BRBot-shaped" with strong rollouts.

That framing matters: most of the classic PIMC/ISMCTS literature is about hidden *state*; GoA s10's residual
uncertainty is a *simultaneous move*, which has its own literature (SM-MCTS, regret matching, Diplomacy).

---

## 1. Determinized search (PIMC), its failure modes, ISMCTS, and where PIMC still wins

### 1.1 PIMC's theoretical defects and why it works anyway — Long, Sturtevant, Buro, Furtak, AAAI 2010
Source: AAAI 2010 PDF (cdn.aaai.org/ojs/7562), full text read.
- **Defects (Frank & Basin 1998, as restated by Long et al.):** *strategy fusion* — PIMC assumes it can choose a
  different strategy in each sampled world although it cannot tell them apart; *non-locality* — a node's value
  depends on other parts of the tree because the opponent steers play using private information. (Frank & Basin
  itself not opened — definitions taken from Long et al. and Cowling et al.; the 1998 paper is UNVERIFIED by me.)
- **Measured predictors** on synthetic trees: *leaf correlation* (PIMC is worst when correlation is low),
  *bias*, *disambiguation factor* (how fast hidden information is revealed; "as disambiguation approaches 1 the
  performance of PIMC improves drastically, since the game is approaching a perfect information game").
- **Real games:** Skat and Hearts measured at correlation 0.8–~1.0, disambiguation ≈0.6 (10,000 Skat games per
  type, 3,000 Hearts games); in the synthetic model this places PIMC at ~0.1 points/game loss vs equilibrium
  (payoffs in −1..1). **Kuhn poker** (disambiguation 0, correlation/bias 0.5): PIMC as player 2 earns −0.166
  against a best response where 0.056 was available (Table 1) — "PIMC is not a good approach in practice for a
  game like poker".
- **GoA lesson:** after the reveal, s10's game is perfect information (disambiguation = 1 for the turn), so
  strategy fusion/non-locality are *not* GoA's main risk. The poker-like component is the simultaneous card
  pick: no information is revealed before both sides commit — the Kuhn-poker corner of the space, where
  determinizing and best-responding is exploitable. Fixing PIMC's defects (αμ, EPIMC, ISMCTS) is the wrong lever
  for GoA; handling the simultaneous move is the right one (§1.6).

### 1.2 ISMCTS — Cowling, Powley, Whitehouse, IEEE TCIAIG 4(2) 2012
Source: White Rose author copy, full text read.
- Searches one tree over information sets instead of one tree per determinization. Results: LOTR:Confrontation —
  ISMCTS significantly beats determinized UCT; **Dou Di Zhu — "on a par"** (branching factor of the information-set
  tree an order of magnitude larger, so "ISMCTS offers no immediate benefit"); Phantom(4,4,4) — MO-ISMCTS best.
  Against humans (LOTR:C) MO-ISMCTS won 14/32 as Light and 16/32 as Dark ("evenly matched with intermediate to
  expert human players"). Humans noted its **weakest aspect is card play in combat: "it has a tendency to waste
  its more powerful cards in situations where less powerful cards would suffice"** — the same card-economy
  failure GoA's playbooks fight.
- **Simultaneous moves under determinization (directly GoA-relevant):** "Under a simple determinization approach,
  the first player is overly pessimistic (assuming the opponent can observe the chosen move and select the best
  response to it) while the second player is overly optimistic (assuming the first player's move is fixed ... and
  thus determinizing it randomly)." They therefore use EXP3 at simultaneous nodes, citing Teytaud & Flory's
  result that EXP3 beat UCB in the simultaneous card game *Urban Rivals* (Teytaud & Flory not opened —
  secondary, UNVERIFIED by me).
- Budget: 10,000 iterations ≈ 1 s/decision was their "acceptable" benchmark.

### 1.3 Ensemble determinization in Magic: The Gathering — Cowling, Ward, Powley, TCIAIG 2012
Source: White Rose author copy, full text read.
- Determinized MCTS with enhancements "was able to compete with, and outperform, a strong expert rule-based player".
- **Rollout finding (important for GoA):** "Applying a fully deterministic rollout strategy, as we did when using
  our expert rules player to handle the rollouts, provided a clearly inferior performance to utilising the reduced
  rules player which uses very limited domain knowledge, but incorporates some randomness ... This was true in all
  of our experiments and despite the fact that the expert rules player is an intrinsically stronger player."
  Uniform-random rollouts were "very weak". Related: Silver & Tesauro, *Monte-Carlo Simulation Balancing* (ICML
  2009) — abstract read via mlanthology only (full PDF links were dead): balanced simulation policies beat
  policies trained for strength inside Monte-Carlo search on 5×5 Go. (The full-text "stronger simulation policy
  makes search weaker" claim, and Gelly & Silver 2007, are UNVERIFIED by me.)
- **GoA lesson:** GoA's hybrid rollouts are exactly the "fully deterministic expert" kind (BookPolicy.kt:24-26),
  chosen for common-random-numbers pairing. That is a reasonable variance-reduction choice, but the one card-game
  study that tested it found deterministic expert rollouts *clearly inferior* to a weaker stochastic one. This is
  a cheap, untested hypothesis for GoA (§6, item 4).

### 1.4 Bridge: GIB, αμ, NukkAI
- **GIB** (Ginsberg, JAIR 14, 2001; arXiv 1106.0669, read): PIMC over double-dummy solves; card play "at the
  expert level"; bidding its weakest part (rule database). At the 1998 world-championship invitational par contest GIB
  joined "a field of 34 of the best card players in the world" and finished 12th (it led at halfway); "tests before the event indicated little or no improvement if gib were
  allotted more time" (500 samples, ~10 min/deal). → **PIMC saturates**: more samples stop helping.
- **αμ** (Cazenave & Ventos 2019, arXiv 1911.07960, read): fixes strategy fusion/non-locality by searching M Max
  moves jointly over all worlds; on 3NT, M=1 (=PIMC) scored 60.2% vs 63.0% for M=2 (20 worlds); 62.4% → 63.2%
  with 40 worlds; M=3 costs 1.2 s/move with transposition table and cuts vs 0.096 s for PIMC. Gains: a few points.
- **EPIMC** (Arjonilla, Saffidine, Cazenave 2024, arXiv 2408.02380, read): postponing the perfect-information leaf
  helps "particularly in games where strategy fusion significantly impacts gameplay" (private observations: Dark
  Chess, Dark Hex), and "increasing the depth does not lead to any improvement" in games whose observations are
  mostly public. GoA's s10 post-reveal play is the public kind.
- **NukkAI / NooK (2022):** only the Imperial College press release was a primary-ish source I could open: 800
  deals in 80 sets of 10, declarer play only (no bidding), method credited to probabilistic inductive logic
  programming ("white box"). The often-quoted "won 67 of 80 sets (83%)" comes from secondary press only →
  **UNVERIFIED**; no paper found.
- **BEN** (open-source bridge engine, github.com/lorserker/ben README, read): neural networks + double-dummy
  solver. README states no strength numbers → no measured claim.

### 1.5 Skat: Kermit and the Alberta line (Buro, Sturtevant, Rebstock, Solinas)
- **Kermit** (Buro, Long, Furtak, Sturtevant, IJCAI 2009, read): PIMC card play + **inference** (P(world|move)
  from human data over high-level features) + a **static evaluator learned from 22 million human games** for
  bidding. Tournaments: 800 hands × 2 seats = 1,600 card-play games, 4,800 full games, 60 CPU s per program per
  game; inference gives a significant gain; full-game Kermit 1188 points/36 games vs XSkat 629. Concluded
  "roughly at the level of human experts" (server games vs world champions had small samples, 50–812 games).
- **Policy-based inference** (Rebstock et al. 2019, arXiv 1905.10911, read): replace hand-built inference by a
  **human-trained policy net used as the opponent model to weight sampled worlds**. PI beats the previous best
  inference (CLI) by +2.32 / +0.64 / +1.57 tournament points per game (suit / grand / null), 5,000 matches each.
  **Striking:** a *cheating* Kermit that puts all probability on the true world plays *worse* than CLI in suit
  and grand (14.75 vs 18.00; 29.97 vs 38.46) — knowing the hidden state does not by itself make PIMC stronger.
- **Learned policies from human data** (Rebstock, Solinas, Buro 2019, arXiv 1905.10907, read): new
  state-of-the-art **bidding** from imitation nets; imitation **card play** "slightly weaker than the current best
  search-based method, [but] orders of magnitude faster".
- **Sampling via a learned card-location net** (Solinas, Rebstock, Buro, AAAI 2019, arXiv 1903.09604, read):
  "provides a substantial increase in cardplay strength" of PIMC.
- **GoA lessons:** (a) the trick-taking winners combine search with *learned* components exactly where the search
  is blind: the opponent model for hidden choices and the static evaluation; (b) the opponent model's accuracy is
  what moves the needle, and perfect knowledge of state does not (Kermit-cheat) — relevant to s10's
  "know enemy hands" privilege, which by itself buys little.

### 1.6 Simultaneous-move games (the literature that matches GoA's turn structure)
- **Bošanský, Lisý, Lanctot, Čermák, Winands, *Algorithms for computing strategies in two-player simultaneous move
  games*, AIJ 2016** (Maastricht preprint, read; six games incl. Goofspiel, Oshi-Zumo, Tron). Online head-to-head
  conclusions: SM-MCTS with **regret matching** "is often very good" and was among the best in random games and
  Tron with an evaluation function; UCT selection sometimes wins "especially in games that require less randomized
  strategies"; Exp3 was weakest overall; **a good evaluation function helps all sampling algorithms**; OOS (sound
  MCCFR) suffers from variance online despite the fastest offline convergence; "proximity to the Nash equilibrium
  is not always a good indicator of game playing performance".
- **Lisý, Kovařík, Lanctot, Bošanský, NIPS 2013** (arXiv 1310.8613, read): SM-MCTS with any Hannan-consistent
  selector (regret matching, Exp3) converges to approximate Nash; UCB does not, even in a one-stage game.
- **Diplomacy SearchBot** (Gray, Lerer, Bakhtin, Brown, ICLR 2021, arXiv 2010.02923, read) — *the closest
  published analogue of GoA's hybrid*: each turn is a simultaneous move; candidate actions = the blueprint's top
  M·k actions (M≈5); each joint action is scored by **rolling out the blueprint 2–3 movement phases and reading
  the blueprint's value net**; an **equilibrium over the candidate matrix is computed by sampled regret matching**
  (256–4,096 iterations, 2–20 min/turn on 1 V100 + 8 CPU cores). Results: 26.6% ± 3.2% vs 14.3% average in
  116 human games, top 2% (rank 17 of 901) on webdiplomacy; rollouts beyond 3–4 phases gave no gain.
  **Head-to-head with its own best-response twin:** *BRBot* = same candidates, same rollouts, but "plays the action
  that yields the greatest reward assuming other powers play the blueprint policy". BRBot scores **higher** than
  SearchBot against the blueprint (69.3% vs 52.7%, Table 3) but **loses head-to-head**: 1 SearchBot vs 6 BRBot
  17.2% ± 1.3% (above the 14.3% par), 1 BRBot vs 6 SearchBot 11.1% ± 1.1% (below par). Expert humans failed to
  exploit SearchBot (5.7% score vs 22.5% vs the blueprint).
- **GoA lesson (the single most transferable finding in this report):** a search that best-responds to its own
  book looks best when measured *against the book or book-like opponents*, and gives part of that back against
  an opponent that is not the book. GoA's hybrid is BRBot-shaped (§0). The CONTEXT.md pattern — W2T 71.9% vs s10
  on dev (64 games) but 56.8% on 600–695 (192 games) — is *consistent with* (not proof of) this; that
  attribution is a hypothesis for the measurement auditors to test, not a finding of mine.

---

## 2. Learning-based successes where search lost (or was not needed)

| Game | System (source, read) | Method | Measured result | Compute |
|---|---|---|---|---|
| Dou Dizhu (3p, 2v1 teams) | **DouZero** (Zha et al., ICML 2021, arXiv 2106.06135) | Deep Monte-Carlo (MC returns, Q(s,a) with action encoding), self-play, **no search** | vs DeltaDou (MCTS + Bayesian inference, "human-level"): WP 0.586, ADP +0.258 over 10,000 decks played both sides; Botzone 1st of 344 bots | 1 server, 48 cores, 4×1080Ti, 45 actors; beats SL in 2 days, DeltaDou in 10; trained 30 days. DeltaDou took >2 months to train |
| Dou Dizhu | **PerfectDou** (Yang et al., NeurIPS 2022, arXiv 2203.16406) | PPO + GAE; **perfect-information distillation** (critic sees all cards, policy only its own) | vs public DouZero: WP 0.543, ADP +0.143 (10k decks) | claims ~10× fewer samples than DouZero (abstract) |
| Riichi Mahjong (4p) | **Suphx** (Li et al., arXiv 2003.13590) | SL on human logs → RL; global reward prediction; **oracle guiding** (train with hidden tiles visible, drop them gradually; plain distillation from the oracle "does not work well"); run-time policy adaptation (pMCPA) | 5,000+ Tenhou expert-room games, 10 dan record, 8.74 dan stable rank; pMCPA vs non-adapted 66% ("hundreds of initial rounds" only) | 44 GPUs × 2 days per RL agent; offline eval of one agent 20 K80 × 2 days |
| Big 2 (4p) | Charlesworth 2018 (arXiv 1808.10442) | PPO self-play, no search | 664 games vs 7 humans: humans won 22.5% (25% = parity), mean −0.96 ± 0.38 | ~3M games, single PC (4 cores + GPU), ~2 days |
| Stratego | **DeepNash** (Perolat et al., Science 2022, arXiv 2206.15378) | R-NaD (regularised Nash dynamics), model-free, **no search**; fine-tuning + test-time removal of low-probability actions | ≥97% vs all prior bots; 42/50 (84%) ranked Gravon games vs humans, 3rd on 2022 and all-time ladders | 7.21M learner steps, batch 768 trajectories (hardware not stated in text I read) |
| Hanabi (coop) | **SPARTA** (Lerer et al., AAAI 2020, arXiv 1912.02318) | search *on top of a blueprint* (see §4) | 2p: SAD 24.08 → 24.53 (single-agent search) → 24.61/25 (multi-agent) | ~2 core-hours per game (single-agent), 90 core-hours (multi-agent) |
| Legends of Code and Magic (2p CCG) | Kowalski & Miernik summary (arXiv 2305.11814) | 2019–2020 won by **Coac: minimax-like search of depth ≤3 + heuristic eval** (CEC'19 94.22%, COG'19 89.88%, CEC'20 86.07%); COG'20 Chad (MCTS + opponent-hand prediction) 79.99%; COG'21 DrainPower (heuristics) 78.72%; **COG'22 ByteRL (end-to-end RL + optimistic smooth fictitious play, arXiv 2303.04096) 84.41%** | competition win rates (round robin) | — |
| Hearthstone | Competition slides (hearthstoneai.github.io), read | 2019 premade track: **BotHeimbrodt, pruned BFS + heuristics 57.66%** > AlvaroAgent MCTS 55.22%; user-deck track: MCGS 79.57%. 2020: **all top-3 in both tracks "Dynamic Lookahead"** (72.34% premade winner). ByteRL Hearthstone (arXiv 2303.05197): RL + OSFP, beat a former top-10 China-ladder player in all Bo5 matches (small sample) | — | — |
| Gwent | — | No public primary source on a strong Gwent AI found → nothing claimed. | | |

**What made them succeed.** (1) Huge numbers of self-play games with cheap simulators and simple Monte-Carlo
targets (DouZero, Big 2, DeepNash) — learning replaced search where the action space was combinatorial
(Dou Dizhu: "a massive set of possible actions" from card combinations, DouZero §1) or the information sets were enormous (Stratego).
(2) Using hidden information during *training only* (Suphx oracle, PerfectDou critic). (3) In the competitive
CCG settings without big compute, **shallow search over a hand-tuned evaluation beat MCTS** (Hearthstone
2019–2020, LOCM 2019–2021); RL only took over when a well-funded team trained end-to-end (ByteRL 2022).
**What failed.** Plain DQN in Dou Dizhu ("very unstable", DouZero §4); ISMCTS in Dou Dizhu (no gain);
distilling an all-seeing oracle directly (Suphx).
**GoA lesson.** GoA sits in the CCG/LOCM band: deterministic resolution, small hidden information, no big lab
compute. There, the evidence says heuristic search with a good evaluation is competitive and the step change
comes from *learning* the policy/value on top. Model-free RL from scratch needs millions of games: GoA's book
self-play throughput is not known to me (UNVERIFIED), so the cost of a DouZero/DeepNash route cannot be
estimated here.

---

## 3. General imperfect-information methods that reached superhuman play

| System | Key mechanism | Result | Compute | Usable at phone/JVM budget? |
|---|---|---|---|---|
| **DeepStack** (Moravčík et al., Science 2017, arXiv 1701.01724) | continual re-solving with depth-limited lookahead; **deep counterfactual value nets** at the leaves (turn net from 10M random solved turn games; flop net trained on targets produced *by the depth-limited solver using the turn net*) | 44,852 hands vs 11 pros: 486 mbb/g (AIVAT), 492 raw | re-solves ~10^7 decision points in <5 s on one GTX 1080 | Leaf-net idea yes; the range-based CFR machinery is poker-specific |
| **Libratus** (Brown & Sandholm, Science 2018) | blueprint (abstraction + MCCFR), **nested safe subgame solving**, self-improver that patches the blueprint where opponents probe | 120,000 hands, +147 mbb/hand vs 4 pros; blueprint alone lost to Baby Tartanian8 by 8 ± 15 | 100 CPUs in play (per Pluribus paper) | No |
| **Modicum / depth-limited solving** (Brown, Sandholm, Amos, NeurIPS 2018, arXiv 1805.08195) | at the depth limit the opponent may choose among **k continuation strategies** (blueprint, biased-to-fold, -call, -raise) | vs Baby Tartanian8: blueprint −57 ± 13, *naive* depth-limited (single blueprint continuation) −10 ± 8, **multi-continuation +6 ± 5** mbb/g | 700 core-hours offline, plays on a 4-core CPU, 16 GB | **Yes in spirit** (§6 item 4) |
| **Pluribus** (Brown & Sandholm, Science 2019) | blueprint (MCCFR, 12,400 core-hours, 8 days on 64 cores) + real-time depth-limited search with **k = 4 continuation strategies**; balanced strategy over all hands | 6-player: +48 ± 25 mbb/g (5H+1AI, 10,000 hands), +32 ± 15 (1H+5AI) | plays on 2 CPUs, <128 GB; 1–33 s per subgame, ~20 s per hand | Search budget similar to GoA's 10 s; memory not |
| **ReBeL** (Brown et al., NeurIPS 2020, arXiv 2007.13544) | AlphaZero-like self-play over **public belief states**; value net trained on subgame values computed by CFR during self-play | HUNL: +45 ± 5 vs Slumbot, +165 ± 69 vs Dong Kim (7,500 hands); **a value net trained on random beliefs "fails to learn anything valuable"** | data generation up to 128 machines × 8 GPUs (90 DGX-1 for HUNL); play <5 s/decision | Play yes, training not at GoA scale |
| **Student of Games** (Schmid et al., Science Advances 2023, arXiv 2112.03178) | GT-CFR growing-tree search + counterfactual value-and-policy net; trains on **the leaf queries the search actually made** + self-play trajectories | Scotland Yard: beats PimBot (determinized MCTS + heuristics) 55% even at 10M PimBot simulations, and **PimBot does not improve from 1M to 10M simulations**; HUNL +7 ± 3 vs Slumbot | TPU scale comparable to AlphaZero (3,500 TPUv4 actors) | No |
| **DORA** (Bakhtin et al., NeurIPS 2021, arXiv 2110.02924) | for *simultaneous-move* games: **1-ply regret-matching equilibrium over policy-proposed candidates, value net at the next state, deep Nash value iteration** (value target = equilibrium value of the 1-step matrix game; policy target = the equilibrium) + double-oracle action discovery | 2-player Diplomacy (FvA) vs top humans: 78% ± 4% (variance-reduced), 26 games; but a self-play-only 7-player agent is **incompatible with human play** (multiple equilibria) | 192 V100 GPUs for the final FvA model | Training no at that scale; the *inference* recipe (1-ply RM over candidates + value net) is phone-sized |

**Transferable mechanisms for GoA** (in order of fit): DORA/SearchBot's 1-ply equilibrium at a simultaneous
move with a learned value at the next state (that *is* GoA's turn structure); Pluribus/Modicum's multiple
continuation strategies at the leaf; DeepStack/SoG/DORA's practice of training the leaf net on values the search
itself produces at the states the search visits. The belief-state CFR machinery (DeepStack ranges, ReBeL PBS,
SoG GT-CFR) exists to handle large private information — s10 has almost none, so it buys little for its cost.

---

## 4. "Search over a heuristic/blueprint policy with rollouts" — where it worked, its ceilings, what broke through

### 4.1 Where it worked
- **Tesauro & Galperin, NIPS 1996** (proceedings PDF, read): rollouts of a base backgammon player to choose the
  move = "a single step of policy iteration". Linear base players −0.52/−0.65/−0.32 ppg vs TD-Gammon 2.1 became
  −0.01/−0.02/+0.04 with rollouts (5–10 s/move on 32 SP1 nodes). Truncated rollouts with a net's evaluation at
  the cut: equity-loss reduction ×3.5–6.6 (Table 3); "while the quality of Monte-Carlo decisions is not as good
  using truncated rollouts (presumably because the neural net's estimates are biased), the degradation ... is
  fairly small", and the stronger the base player, the larger the error-reduction ratio.
- **Bertsekas, *Multiagent rollout algorithms and RL*, 2019 (arXiv 1910.00120, read):** rollout's "cost improvement
  property" — the rollout policy is never worse than the base policy (exact Q-factors). **Multi-agent caveat**
  (Example 3.1): if each agent runs rollout assuming the *other* agents play the base policy, the joint result can
  be **worse than the base policy** (cost 2 vs 1 in his two-agent example); improvement is restored by
  agent-by-agent rollout where agent ℓ knows the rollout choices of agents 1..ℓ−1.
  → For GoA (2–3 AI heroes per team choosing face-down cards at once), whether each AI hero's search sees its
  AI teammates' *search* choices or assumes they play the book is a code question I did not resolve; the
  determinizer hides played cards only of *human* allies (HybridAi.kt:197-199, 211), which suggests AI allies'
  already-chosen cards are visible, but the order and whether a teammate re-decides is for the code auditors.
- **SPARTA (Hanabi), arXiv 1912.02318, read** — the cleanest published "rollouts of a fixed blueprint" result:
  single-agent search (every other player plays the blueprint; for each legal action, Monte-Carlo rollouts to the
  end from the belief; pick the best mean). Guaranteed not to lose to the blueprint up to an error shrinking with
  rollouts. Results (2p, Table 1): SmartBot 22.99 → 24.21 → 24.46 (multi-agent); SAD 24.08 → 24.53 → 24.61.
  Two cheap engineering details worth copying: **UCB-style pruning** (after ≥100 rollouts per action, stop rolling
  out actions not within 2 standard deviations of the best) and a **deviation threshold** (play the blueprint
  action unless search beats it by > 0.05); together they cut rollouts per timestep ~10×.
- **Diplomacy SearchBot** (§1.6): blueprint candidates + blueprint rollouts + value net + equilibrium.
- **AlphaGo** (Nature 2016, DeepMind PDF, read): leaf = (1−λ)·value net + λ·rollout outcome; λ = 0.5 beat
  value-only (λ=0) and rollout-only (λ=1) variants, winning ≥95% against them ("the two position-evaluation
  mechanisms are complementary"). (Elo table: 2890 mixed vs 2177 value-only vs 2416 rollout-only, single machine,
  5 s/move.)
- **M:TG** (§1.3): determinized MCTS with rule-based rollouts beat an expert rule-based player.

### 4.2 Known ceilings
1. **Bounded by the base policy's own blind spots** — rollout = one policy-iteration step (Tesauro; Bertsekas). The
   search can only find improvements whose payoff shows within the horizon *as played by the base policy*; if the
   base policy mis-plays the follow-up, the right first move looks bad. SPARTA's table shows the search result
   tracks blueprint quality: the best blueprint (SAD 24.08) ends highest (24.61), the weakest (SmartBot 22.99) ends
   lowest (24.46).
2. **Saturation with more samples/rollouts:** GIB ("little or no improvement" with more time), PimBot (no gain from
   1M to 10M simulations vs SoG), SearchBot (no gain beyond 3–4 rollout phases). More compute on the same
   recipe stops paying; GoA's many "wider / deeper / longer horizon" rejections (CONTEXT.md: depth-3 tree 43%,
   round horizon 32.8%, wider, hz3) fit this pattern (as reported; not re-verified by me).
3. **Best response to the model, not to the opponent** (BRBot, §1.6): gains measured against the blueprint
   overstate gains against anything else.
4. **Biased leaves** when the rollout is truncated (Tesauro), and the leaf evaluator is queried on states unlike
   its training data (§5).
5. **Multi-agent mis-coordination** (Bertsekas Example 3.1).

### 4.3 What broke through: train the policy (and value) on the search's output
- **Expert Iteration** (Anthony, Tian, Barber, NeurIPS 2017, arXiv 1705.08439, read): search = expert, net =
  apprentice trained to imitate the search; the apprentice then guides the next search. Hex: policy-net-guided MCTS
  won 97% vs vanilla MCTS (doubling vanilla iterations gave only 56%); final ExIt agent (10,000 iterations) beat
  MoHex 1.0 75.3% / 59.3% (vs 100k-iteration MoHex) / 55.6% (vs 4 s/move MoHex). Better targets: the search's
  visit distribution ("tree-policy target") rather than only the chosen move.
- **AlphaZero** (arXiv 1712.01815): same loop at scale; **KataGo** (arXiv 1902.10565, read): 50× cheaper than
  AlphaZero-class runs (<30 GPUs, 19 days) via generic tricks — playout-cap randomization (full search on 25% of
  moves for policy targets, fast search otherwise for more value games), auxiliary targets.
- **In imperfect-information/card settings specifically:**
  - SPARTA **CloneBot**: imitation of single-agent-search-on-SmartBot raised the *search-free* blueprint 22.99 →
    23.32 and search on top of it 24.21 → 24.37 (Table 1). One distillation step, measured.
  - SearchBot-clone (Diplomacy): imitation of SearchBot used as a cheap proxy; *more exploitable than SearchBot*
    (best responder ≥42% vs it) but less than the blueprint (≥54%).
  - **DORA**: the full loop with equilibrium search as the improvement operator → superhuman 2-player Diplomacy.
  - **ReBeL / SoG / DeepStack**: value nets trained on search-produced values.
  - Learned Belief Search (Hu et al., arXiv 2106.09086, read): learns the belief model to make the search cheaper
    — 55–91% of exact search's benefit at 4.6–35.8× less compute.
- **GoA lesson:** the hybrid is at the "SPARTA single-agent search on a hand-written blueprint" stage. The
  published next step that worked every time it was tried is to distil the search's decisions (and values) back
  into the blueprint, then search on the improved blueprint. GoA can do this offline on JVM CPUs because the
  apprentice can be a small net or even a re-weighting of the book's own 27 HEval terms / playbook adjustments
  (cheap at play time); the search runs only offline to produce targets.

---

## 5. Evaluation-function learning in card/board games

- **Outcome targets overfit on correlated positions.** AlphaGo: a value net trained on whole KGS games "memorized
  the game outcomes": MSE 0.19 train vs 0.37 test; fixed by **one position per self-play game** (30M distinct
  positions, MSE 0.226 / 0.234). KataGo: outcome targets are "highly data-limited, with only one noisy binary result
  per entire game", motivating cheap-search games for value data. *GoA check:* if the leaf nets' training sets take
  many positions per game, the train/validation split must be by game, and the effective sample size is the number
  of games, not positions (for the code/data auditors; I did not inspect GoA's split).
- **Search-value targets (bootstrapping).** DORA trains V(s) toward the 1-step equilibrium value r + γΣσ(a′)V(f(s,a′))
  ("1-step bootstrap instead of the end-of-game value", which also lets data be off-policy); DeepStack's flop net
  is trained on values computed by the depth-limited solver using the turn net; SoG trains on solved leaf queries.
  Tesauro: truncated-rollout decisions are slightly worse "presumably because the neural net's estimates are
  biased", but far cheaper. Kermit/Skat: learned static evaluators from 22M human games were the bidding win.
- **Distribution shift between training positions and search leaves.** ReBeL's explicit control: a value net trained
  on *random* belief states "fails to learn anything valuable"; trained on self-play-visited states it works. SoG
  and DORA close the gap by training on exactly the states their search queries. AlphaGo's fix was also a
  distribution fix (self-play positions from the RL policy). → If a GoA leaf net was trained on positions sampled from
  *played games*, it is being queried on a different distribution: *end-of-turn states reached by book playouts from
  hypothetical candidates*. The E2 track ("net trained on search values", CONTEXT.md) is the right direction;
  whether it trained on the hybrid's actual leaf states is for the E2 auditor.
- **Ensembles.** I found no primary card-game *play* result that isolates the gain from a value ensemble.
  Indirect evidence only: the Hearthstone data-mining challenge (state → win-probability, 3.25M training states)
  was won by an ensemble of CNNs (LOCM summary, arXiv 2305.11814), and AlphaGo's mix of two different evaluators
  beat either alone. So "NET6 six-net average" is plausible variance reduction, but not a literature-backed
  strength lever → claim kept out of the ranking.
- **Auxiliary targets** (KataGo ownership/score; Suphx global reward prediction) improve value learning; GoA already
  has auxiliary heads (Trainer.kt:37, ADR-060).

---

## 6. Ranked: the 5 most promising techniques for GoA strength 10

Ranking criterion: strength of published evidence in a setting structurally like GoA s10 (simultaneous card
choice, then perfect-information resolution; team play; ~10 s per card play on JVM/phone; plentiful offline CPU).
"Expected gain" is deliberately qualitative — no source lets me translate another game's gain into GoA win rate.

**1. Expert iteration on the book: distil the hybrid's decisions (and values) back into the blueprint, then search
on the improved blueprint.**
Evidence: SPARTA CloneBot (+0.33 points search-free, +0.16 with search, one step); ExIt (97% vs vanilla MCTS;
beats MoHex); DORA (superhuman FvA); search result tracks blueprint quality (SPARTA Table 1); rollout = one policy
iteration step, so iterating is the obvious continuation (Tesauro, Bertsekas).
Why it fits GoA: the hybrid already *is* the expert, and its ceiling (§4.2.1–2) is the book's blind spots, which
wider/deeper search has repeatedly failed to fix (CONTEXT.md rejections). Improving the book improves candidates,
rollouts and the opponent model at once.
Cost: offline only — run the hybrid (or a slower, wider variant) over many positions, log the visit/mean-value
vector per candidate, fit the book's tunable weights (HEval terms, playbook adjustments) or a small policy net
by ranking loss. Play-time cost: none or one small forward pass. Risk: overfitting to the book-vs-book
distribution — evaluate only on sealed seeds vs old s10.

**2. Restricted-Nash (regret matching) at the face-down card choice, instead of argmax best response to the book.**
Evidence: SearchBot vs BRBot (best response wins vs the blueprint, loses head-to-head); SM-MCTS literature (RM
strong online; UCB/argmax does not converge in simultaneous games); ISMCTS's note that determinizing the other
side's simultaneous move makes the searcher "overly optimistic"; Pluribus/Libratus balance; DORA uses exactly
this as its search.
Why it fits GoA: GoA's card pick is a simultaneous move; the hybrid currently plays a pure argmax against a
book-softmax opponent model (§0), while old s10 already had RM with a "free" restricted-Nash knob
(RegretMatching.kt). Humans adapt within a session; a pure, predictable s10 is exploitable.
Cost: low at play time — needs a candidate × enemy-card payoff matrix. The hybrid already plays each candidate
against each sample's enemy draw, so grouping samples by enemy card gives the matrix almost for free (the old
code does this grouping: RegretMatching.kt:3-6). 1,000 RM iterations on a ≤5×5 matrix are negligible.
Caveat (from the same source): against a *fixed* opponent that really plays like the model, best response
scores higher (BRBot 69.3% vs SearchBot 52.7% against the blueprint). The gain exists only to the extent the real
opponent (old s10, humans) differs from the book model — which is exactly the 65%-vs-old-s10 question. Measure
it with the "free" knob swept from 0 (best response) up, held-out vs old s10.

**3. Train the leaf evaluator on the states the search actually queries, with search-derived targets.**
Evidence: ReBeL (random-state value net learns nothing useful), SoG (trains on queried leaves), DORA (1-step
equilibrium bootstrap), DeepStack (flop net trained on solver values), AlphaGo (one position per game; correlated
positions overfit), Tesauro (truncated-rollout bias is the price of a biased leaf).
Why it fits GoA: the leaf is queried at end-of-turn states after book playouts from hypothetical candidates; GoA's
trainer keeps training targets separate from real outcomes and early-stops on log-loss against the real results
(test Trainer.kt:13-17, 54-60); which targets and which positions each shipped net used is for the E/E2 auditors,
plus E2's search-value experiment. Leaf choice moved results the most among GoA's measured knobs (NET6 vs old net 56.1%;
HEval leaf vs net leaf 38.9%, per CONTEXT.md — not re-verified by me).
Cost: offline CPU to log leaf states and re-label them (e.g. with a deeper hybrid search or 1-step bootstrapped
values); play-time cost unchanged. Split by game, not by position.

**4. Make the rollout/continuation model robust: several continuation policies (Pluribus/Modicum) and stochastic
rather than deterministic book rollouts (M:TG).**
Evidence: Modicum — single-blueprint continuation −10 ± 8 vs multi-continuation +6 ± 5 mbb/g against Baby
Tartanian8; Pluribus k = 4 biased blueprints; M:TG — deterministic expert rollouts "clearly inferior" to weaker
stochastic ones in every experiment; simulation balancing (abstract).
Why it fits GoA: every hybrid playout uses the one deterministic book for both sides (BookPolicy.kt:24-26), so the
enemy can never "shift strategy" beyond the leaf and the hybrid's choice is tuned to one continuation.
Cost: k× more playouts for the multi-continuation version (k=2–4 book variants, e.g. aggressive/defensive tunes),
which collides with the 10 s budget (3v3 W3 already 14.7–23 s per CONTEXT.md); SPARTA-style UCB pruning
(≥N rollouts then drop candidates > 2 sd behind) and a deviation threshold (keep the book's card unless search
beats it by a margin) cut rollouts ~10× in Hanabi and could pay for it. The stochastic-rollout variant costs
nothing but loses common-random-numbers pairing — test it, don't assume.

**5. A better opponent model for the face-down card (learned inference) — and agent-by-agent coordination within
the team.**
Evidence: Skat — policy-based inference +2.32 TP/G over the previous best, and a cheating player (exact world) is
*worse*: the model of what the opponent does matters more than knowing the state; SearchBot does best against the
agent it models most accurately; Bertsekas Example 3.1 — independent per-agent rollout can be worse than the base
policy; agent-by-agent rollout restores improvement.
Why it fits GoA: the enemy-card distribution is a book softmax with 15% uniform (HybridAi.kt:704 on
`t3/strength-10-search`); its temperature/mix were never fit to anything I could find. Fitting it (by maximum
likelihood) to logged face-down plays of the opponents the AI must beat — old s10 and, better, human games from the
app — is cheap. Coordination: confirm in code that a team's second AI hero conditions on the first's search choice.
Cost: low (offline fit of a few parameters; no play-time cost). Risk: fitting to old s10's plays overfits the
benchmark; keep a human-game hold-out and sealed seeds.

**Not recommended for GoA's s10, with reasons:** ISMCTS/αμ/EPIMC (fix strategy fusion, which s10's
public post-reveal play hardly has; ISMCTS gave nothing in Dou Dizhu); belief-state CFR (DeepStack/ReBeL/SoG — built
for large private information, GPU/TPU-scale training); model-free RL from scratch (DouZero/DeepNash/ByteRL —
needs millions of games; GoA self-play throughput unknown, UNVERIFIED); more samples/depth on the current recipe
(saturation in GIB, PimBot, SearchBot and in GoA's own rejections).

---

## Sources opened (primary unless stated)
Full text read (pdftotext): Long et al. AAAI 2010 (cdn.aaai.org/ojs/7562); Cowling, Powley, Whitehouse 2012
ISMCTS (eprints.whiterose.ac.uk/75048); Cowling, Ward, Powley 2012 M:TG (eprints.whiterose.ac.uk/75050); Ginsberg
GIB JAIR 2001 (arXiv 1106.0669); Cazenave & Ventos αμ (arXiv 1911.07960); Arjonilla et al. EPIMC (arXiv
2408.02380); Buro et al. IJCAI 2009 Kermit (cs.du.edu/~sturtevant/papers/skat.pdf); Rebstock et al. PI (arXiv
1905.10911); Rebstock et al. human policies (arXiv 1905.10907); Solinas et al. (arXiv 1903.09604); Bošanský et
al. AIJ 2016 (dke.maastrichtuniversity.nl/m.winands/documents/sm-journal.pdf); Lisý et al. NIPS 2013 (arXiv
1310.8613); Gray et al. SearchBot (arXiv 2010.02923); Bakhtin et al. DORA (arXiv 2110.02924); DouZero (arXiv
2106.06135); PerfectDou (arXiv 2203.16406); Suphx (arXiv 2003.13590); Big 2 (arXiv 1808.10442); DeepNash (arXiv
2206.15378); SPARTA (arXiv 1912.02318); LBS (arXiv 2106.09086); LOCM summary (arXiv 2305.11814); ByteRL LOCM
(arXiv 2303.04096, abstract) and Hearthstone (arXiv 2303.05197); Hearthstone AI competition slides 2018/2019/2020
(hearthstoneai.github.io/files/slides/, 2019 finals checked as rendered slides); Świechowski et al. Hearthstone
MCTS+SL (arXiv 1808.04794); DeepStack (arXiv 1701.01724); Libratus (Science 2018, par.nsf.gov/servlets/purl/10077416);
Brown & Sandholm safe subgame solving (arXiv 1705.02955, abstract); Modicum (arXiv 1805.08195); Pluribus (Science
2019, par.nsf.gov/servlets/purl/10119653); ReBeL (arXiv 2007.13544); Student of Games (arXiv 2112.03178); ExIt
(arXiv 1705.08439); AlphaZero (arXiv 1712.01815, abstract); KataGo (arXiv 1902.10565); AlphaGo (Nature 2016,
storage.googleapis.com/deepmind-media/alphago/AlphaGoNaturePaper.pdf); Tesauro & Galperin NIPS 1996
(proceedings.nips.cc); Bertsekas multiagent rollout (arXiv 1910.00120); BEN README (github.com/lorserker/ben).
Abstract only: Silver & Tesauro ICML 2009 simulation balancing (mlanthology.org). Press release only: NukkAI
(imperial.ac.uk/news/235238).
**UNVERIFIED / not opened:** Frank & Basin 1998 (definitions via Long et al.); Teytaud & Flory Urban Rivals (via
ISMCTS paper); Gelly & Silver 2007; NooK "67/80 sets"; any Gwent AI; DeepNash hardware; GoA self-play throughput;
all GoA win rates quoted from CONTEXT.md (not recomputed by R2).
Code read: HybridAi.kt (base 64955c18 lines 33-45, 88-160, 190-216; `t3/strength-10-search` 296-330, 704),
BookPolicy.kt:20-40, 105-140, RegretMatching.kt:1-40, Trainer.kt (test) grep, AiPlayers.kt:290.
