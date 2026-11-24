# R1 — Strong AIs for simultaneous-move, small-hidden-information, team games, and what transfers to GoA strength 10

> Part of the wave-3 audit (2026-11-24); the synthesis is `../REVIEW-3-audit.md`. Paths under `/tmp/claude-1000/` were the
> agents' scratch folders at the time and may be gone.

Auditor R1, 2026-11-24. Read-only. Nothing in the repo was changed, built or run.

## How to read the evidence tags

- **[P]** = a primary source I downloaded and read in full text: paper PDF converted with `pdftotext`, or source code.
  Local copies are in `.../scratchpad/audit/r1papers/` (`*.pdf`, `*.txt`; code in `r1papers/repos/`, `r1papers/rftg/`).
- **[P-abs]** = only the official abstract or landing page (proceedings page, publisher record, official blog).
  The claim goes no further than that page.
- **[REPO]** = the GoA repo, file:line, read with `git show <branch>:<path>`. `HybridAi.kt` line numbers are from
  branch `t3/strength-10-search` (copy at `r1papers/HybridAi.s10.kt`).
- **UNVERIFIED** = I could not open a primary source. Such claims are kept out of the conclusions.

Every number below is quoted from the source named next to it. None comes from memory.

---

## 0. Where GoA sits, and what s10 does now at the card play

**The structure.** Within one turn of GoA:

- Every hero chooses a card at the same time, face down.
- After the reveal, resolution is deterministic, with sequential choices.
- s10 knows the enemy hands (CONTEXT.md), so its only uncertainty before the reveal is the face-down card.
- Each team is 2–4 heroes, and one AI plays the whole team.

The closest small benchmark in the literature is **Goofspiel with a known card sequence**:

- Both hands are fully known.
- Both players pick a card at the same time.
- The outcome follows deterministically.

[P] Bošanský et al., AIJ 2016, §6.2, describes it this way. The equilibrium needs mixed strategies "throughout the
whole course of the game". For Goofspiel(5) the root state is mixed (Depth 0: Pure 0, Mixed 1).

**What the current s10 (hybrid W3/SEARCH_10) does at the card play** [REPO]:

- **It best-responds with a pure strategy to a fixed opponent model.**
  - The docstring says the face-down enemy card is "dealt again from the book's opponent model"
    (`HybridAi.kt:38`). It also says "The best weighted mean wins" (`HybridAi.kt:44`).
  - The selection takes the argmax of the weighted mean, minus `risk·sd`, plus an optional prior
    (`HybridAi.kt:296-332`; the comparison is `if (score > bestV)` at `:329`).
  - Enemy replies are drawn from "softmax of the book's card scores, 15% uniform" (`HybridAi.kt:704-728`,
    `BookPolicy.kt:118-121`).
  - The optional robustness knob `worstReply` (`HybridConfig.kt:133-136`) mixes the mean with the worst reply. It is
    still a pure argmax.
- **The opponent model is the book (h10).** It is neither the target opponent (old s10, a net-guided determinized
  search) nor a human.
- **Each hero decides its own card.** Teammates who have not chosen yet are answered by the book inside the playout:
  "played out to the horizon by the book for every seat" (`HybridAi.kt:41-42`).

In Diplomacy terms, this is exactly the **BRBot / Sampled Best Response** design: candidate actions from a prior
policy, opponents sampled from that prior, play the argmax. §3 shows how that design compares with equilibrium search.

**What was tried before** [REPO `core/game/ai/AI.md:1144-1212`, branch t3/strength-10-search]:

- **Regret matching (ADR-061).**
  - It was built on the old search, as a restricted Nash response with `rm=0.5`.
  - It scored 49.5% against s10 over 192 games, stopped by hand while undecided.
- **The oracle exploiter.** It cheats by predicting the enemy's card plays with a copy of the enemy's player.
  - It named old s10's card 88.9% of the time and won 60.9% of 256 games against it.
  - Against the regret-matching variant it named the card 85.0% of the time and won 55.5%.
- **The authors' own diagnosis** (`AI.md:1212-1215`): "samples' enemy cards rarely repeat, so each column is one noisy
  sample and one own card tends to win most of them" (they mark it "not checked").
- **The heuristic track never retried it.** `RESULTS.md:386` says: "Regret matching at card plays (ADR-061) measured
  nothing against s10 and every opponent here is deterministic; not retried".
- **The hybrid's exploitability was never measured.** `git grep "oracle/"` finds only `AI.md` (the old search) and
  the test harness. RESULTS.md has no oracle or exploiter run against W2/W3.

---

## 1. Theory and algorithms for simultaneous-move games

### 1.1 Bošanský, Lisý, Lanctot, Čermák, Winands — "Algorithms for computing strategies in two-player simultaneous move games", AIJ 2016 [P]

Source: preprint `sm-journal.pdf` (dke.maastrichtuniversity.nl/m.winands/documents/sm-journal.pdf), DOI
10.1016/j.artint.2016.03.005.

**Methods compared.**

- Exact methods:
  - backward induction (BI);
  - BI with serialized alpha-beta bounds (BIαβ);
  - double oracle with alpha-beta (DOαβ). DOαβ restricts each stage matrix game to a few actions, solves it, and adds
    the best responses until no improvement is found (§4).
- Sampling methods:
  - SM-MCTS with UCT, Exp3 or regret matching (RM) selection;
  - SM-OOS (online outcome sampling, an MCCFR variant).

**Games.** Biased RPS, Goofspiel (4 variants), Oshi-Zumo, pursuit-evasion, Tron, and random games.

**How strength was measured.**

- Offline: exploitability, error = sum of best-response gains.
- Online: head-to-head play at 1 s per move, at least 1000 matches per pair, 95% confidence intervals.
- Parameters were tuned per domain.

**Results (quoted).**

- **Offline:** "DOαβ is clearly the best algorithm"; it is "several orders of magnitude faster than the classical
  backward induction" (§6.4.6, §7).
- **Goofspiel(13), stochastic sequence, win rate (Table 1).** The table gives each row algorithm's win rate against
  each column opponent, in %.

  | Row algorithm \ vs | DOαβ | OOS | UCT | Exp3 | RM |
  |---|---|---|---|---|---|
  | RM | 74.1 | 52.5 | 50.3 | 58.9 | — |
  | UCT | 64.0 | 48.8 | — | 55.6 | 49.7 |
  | DOαβ (heuristic evaluation) | — | 26.6 | 36.0 | 26.1 | 25.9 |

  - In the known-sequence variant, RM wins 68.5% against DOαβ.
  - "RM is the only algorithm that did not lose significantly against any other sampling algorithm in any of the game
    variants".
  - DOαβ uses a hand-tuned evaluation function, yet "does not win significantly against any of the sampling algorithms
    that do not use any domain knowledge".
- **Domains that mostly need pure strategies.** In Tron, Oshi-Zumo and pursuit-evasion, UCT (pure-ish, not
  convergent) is the strongest sampling algorithm. It wins 70.3% against RM in Oshi-Zumo (Table 2), and "the game
  mostly requires pure strategies … UCT is able to quickly disregard other actions" (§6.5.2).
- **A good evaluation function changes the ranking.**
  - In Tron without evaluation functions in the samplers, DOαβ beats RM 65.0% and UCT 53.8% (Table 4, top).
  - With evaluation functions, UCT beats DOαβ 57.3% and RM beats DOαβ 53.7% (Table 4, bottom).
  - "DOαβ with a good evaluation function often wins over the sampling algorithms without a domain specific knowledge.
    This is not the case with a weaker evaluation function" (§6.5, conclusions).
- **Variance matters more than the theory.** OOS converges fastest offline but often plays worst online, because its
  root regret-estimate variance is huge. For Goofspiel(13), the variance of OOS is 26 737–32 939, against 283–552 for
  RM (Table 7).
- **Their closing warning:** "Proximity to the Nash equilibrium is not always a good indicator of game playing
  performance" (§7).

**Lessons for GoA.**

- (a) Whether mixing pays off depends on whether the stage games have pure saddle points. That is measurable directly
  in GoA: count, at real card plays, how often the root payoff matrix has a pure equilibrium, as the paper does per
  depth for Goofspiel and Tron.
- (b) When the stage games need mixing, RM-based SM search is the robust default. When they mostly do not, a cheap
  pure/UCT-like choice is as good or better.
- (c) Backed-up search with a good evaluation beats naive sampling. A weak evaluation reverses this.
- (d) Low-variance value estimates beat theoretically nicer high-variance ones at small budgets.

### 1.2 Lisý, Kovařík, Lanctot, Bošanský — "Convergence of MCTS in Simultaneous Move Games", NIPS 2013 [P] (arXiv 1310.8613)

**The theorem.** If a selection method is ε-Hannan-consistent in a matrix game and satisfies the exploration
conditions, SM-MCTS converges to an approximate Nash equilibrium (NE) (abstract; Thm 4.11).

**Conditions and caveats.**

- The played strategy is the empirical frequencies or the average strategy (§4, Lemma 4.5).
- For UCB they cite Shafiei et al., who show it "does not converge to Nash equilibrium, even in a simple one-stage
  simultaneous move game" (§1).
- Back-propagating the mean (RMM) instead of the sample value converges slightly slower, and "RMM never performed
  better" in their worst-case search (§5.1–5.2).

**Follow-up.** [P] Kovařík & Lisý (arXiv 1804.09045) show that applying Hannan-consistent algorithms directly to
observed payoffs "is not sufficient to guarantee the convergence". Averaging over joint actions fixes it, but
convergence is empirically slower.

**Lesson for GoA.** If GoA wants an unexploitable mix at the card play, use RM or Exp3 and play the average or final
iterate. A UCB/argmax rule offers no such guarantee.

### 1.3 Lanctot, Wittlinger, Winands, Den Teuling — SM-MCTS in Tron (BNAIC 2013 per the hosting file name) [P]

Variants compared: Decoupled UCB1-Tuned, Sequential UCT, Exp3 and RM, played on 4 boards, 500 games per matchup.

- Decoupled UCB1-Tuned won "62.3% of games overall".
- RM won 53.1%.
- The sequential (turn-taking) models won 51.4–54.3% (abstract; Table 2: DUCB1T(max) 62.32 ± 0.56%).

### 1.4 Tak, Lanctot, Winands — "MCTS Variants for Simultaneous Move Games", CIG 2014 [P-abs]

Source: Maastricht CRIS record, pp. 232–239.

- Nine games; variants DUCT, Sequential UCT, Exp3 and RM.
- Which variant wins depends on the game and the opponent.
- "Overall, Decoupled UCT performs best despite its theoretical shortcomings."
- I did not open the full text, so I have no per-game numbers.

### 1.5 Lanctot, Lisý, Winands — Goofspiel, CGW@IJCAI 2013

**UNVERIFIED beyond a search-engine summary of the abstract.** Per that summary, RM and OOS perform best, and all
variants are less exploitable than UCT. Not used in conclusions.

### 1.6 When does a pure best response to a model beat equilibrium play, and vice versa?

**Against the modelled opponent, best response wins.**

- [P] Gray et al. 2021, Table 3: BRBot is a best response to the blueprint over the same candidate actions. It scores
  69.3% ± 1.7% against 6× Blueprint, where SearchBot (the equilibrium) scores 52.7% ± 1.3%.

**Head-to-head, equilibrium wins.**

- Same table, 1v6 games: BRBot scores 11.1% ± 1.1% against SearchBot, and SearchBot scores 17.2% ± 1.3% against BRBot.
  A tie would be 14.3%.
- The paper: "BRBot scores higher than SearchBot against the Blueprint, but SearchBot outperforms BRBot in a
  head-to-head comparison."

**Best responses are brittle.**

- [P-abs] Johanson, Zinkevich, Bowling, NIPS 2007, "Computing Robust Counter-Strategies".
  - Restricted Nash responses are "substantially more robust than best response counter-strategies" while still
    exploiting the modelled tendency.
  - Combining them in an experts algorithm gives a "dramatic improvement in performance over using simple best
    responses".

**Pure, predictable play gets exploited by adaptive humans.**

- [P] PokéChamp (§5): "humans who knew they were playing against PokéChamp were able to determine the excessive
  switching limitation and tailor an adversarial strategy".
- [REPO] GoA's own number: the oracle named old s10's card 88.9% of the time and won 60.9% of 256 games against it.
  AI.md calls this "about +75 Elo at this level".

**Pure play is fine where the stage games have pure equilibria.** [P] AIJ 2016: UCT is best in Oshi-Zumo, Tron and
pursuit-evasion (§1.1).

**Synthesis.**

- Against a fixed deterministic bot (old s10, the 65% target), an accurate best response is the best you can do.
  Equilibrium play gives up that edge.
- Against humans, against other strong searchers, or under a *wrong* opponent model, equilibrium or regularized play
  wins.
- GoA's hybrid best-responds to *the book's* softmax. That model is not old s10 and not a human. So it pays the
  brittleness of a best response without the exploitation gain of an accurate model.

---

## 2. Pokémon battles (simultaneous moves, partial information, near-deterministic resolution)

### 2.1 Foul Play (P. Mariglia): search bot, PokéAgent Challenge Gen 9 OU champion [P: code + competition paper]

**Method, read in code** (`fp/search/main.py`, poke-engine `src/mcts.rs`):

- **Determinization.** `prepare_battles` samples N possible opponent configurations, each with a weight.
- **Search.** Each sample runs MCTS in a separate process (root-parallel).
- **Selection is decoupled UCB1 (DUCT).**
  - Each side keeps its own per-move statistics (`maximize_ucb_for_side` for s1 and s2, `mcts.rs:98-99`).
  - Back-propagation adds `score` to side one and `1 − score` to side two (`mcts.rs:176-194`).
- **No random rollouts.** The "rollout" is a sigmoid of the static evaluation difference (`mcts.rs:196-208`).
- **Final choice:**
  - visit shares are aggregated across samples, weighted by sample chance;
  - moves with at least 75% of the best share are kept;
  - **one is sampled in proportion** (`main.py: select_move_from_mcts_results`). So Foul Play mixes.

**How strength was measured.** [P] PokéAgent Challenge, arXiv 2603.15563, App. E.1.2, written by the author:

- "over 90% GXE in Generation 9 Random Battles (peak rating 2341), over 80% GXE in Generation 9 OU (peak rating 1879)";
- first place in the NeurIPS 2025 Gen 9 OU track, "50–14 finals victory" (§5.1, App. B).

**What made the difference** (the author's account):

- Moving from expectiminimax (depth ≈5 turns) to MCTS (10+ turns on promising lines).
- "damage roll grouping" to cut chance branching.
- Likelihood-weighted set prediction from usage statistics and in-battle inference.

### 2.2 Metamon (Grigsby et al., RLJ 2025, arXiv 2504.04395) [P]

**Method.**

- Reconstructs first-person trajectories from spectator logs.
- Trains transformer policies: imitation, then offline RL, then fine-tuning on self-play data.
- No search.

**How strength was measured.**

- Anonymous Showdown ladder, at least 400 battles per evaluation, Glicko-1/GXE.
- The best agents are "inside the top 10% of active players". Peak global ranks are #46 and #31 in Gen1OU.
- Against Foul Play over 300 battles per generation: a draw in Gens 3–4, a win in Gens 1–2 (§5.4).

**Successor.** [P] The PokéAgent Gen 1 OU champion, PA-Agent, built on Metamon:

- iterative offline RL, cutting human data from 100% to 10% over 6 rounds;
- team selection by tournament;
- GXE 80.35% (App. E.1.1).

### 2.3 PokéChamp (Karten, Nguyen, Jin, arXiv 2503.04094) [P]

**Method.**

- Depth-limited minimax over *pure* strategies: "we focus on pure strategies and compute the best action that is safe
  even against the adversarial play" (§4).
- An LLM samples its own candidate actions, models the opponent, and values the leaves.

**Results.**

- 84% win rate against the Abyssal heuristic bot with GPT-4o (Table 2).
- On the ladder, "For about one third of the games, PokéChamp lost by exceeding the turn time limit". Its 1300–1500
  Elo, "top 30%–10%", is an estimate *after removing timeout losses* (§5).

**Measurement caveat.** The headline human-percentile number is not a measured ladder result. The authors also say
humans who knew the bot exploited its predictable switching.

### 2.4 PokéLLMon (Hu, Huang, Liu, arXiv 2402.01118) [P]

LLM agent with in-context feedback. It won 49% of ladder battles and 56% of invited battles (abstract), and was weak
on long horizons per Metamon §5.4.

### 2.5 Cross-cutting lesson from the challenge [P]

The challenge paper (§5.3) says: "In battling, the top participants all used RL or MCTS rather than LLM reasoning".

**Not covered:** the VGC AI competition. I did not open a primary source.

### 2.6 Transferable to GoA

- **DUCT plus determinization plus an evaluation leaf plus mixing among near-best root moves** is what won a 2025
  head-to-head competition in a simultaneous-move game. The mixing is a few lines of code.
- **Opponent-configuration inference** is the dominant lever in Pokémon. In GoA, s10 already knows the hands, so the
  equivalent lever is predicting the face-down card.
- **Imitation, then offline RL, then self-play fine-tuning** reached top-10% humans with no search at all. A
  phone-friendly policy prior could come from the same pipeline (§3).

---

## 3. Diplomacy (simultaneous orders, many players, alliances)

### 3.1 SearchBot — Gray, Lerer, Bakhtin, Brown, "Human-Level Performance in No-Press Diplomacy via Equilibrium Search", ICLR 2021 [P] (arXiv 2010.02923)

**Method** (§2.2, §3.2):

- A blueprint policy and value network, trained by imitation on 46,148 human games.
- On each turn, a one-ply subgame for **all** players. The subgame actions are the M·k_i highest-probability blueprint
  actions, with M usually 5.
- The payoff of a joint action: roll the blueprint out 2–3 movement phases, then take the value network.
- The equilibrium is found by **sampled RM**: linear weighting, optimism, and the agent "samples from the final
  iteration's policy" (§2.2).
- Cost: 256–4,096 RM iterations; "between 2 minutes and 20 minutes per turn" on one V100 and 8 CPU cores (§3.2).

**Results.**

- Against humans:
  - 26.6% ± 3.2% score on webdiplomacy.net (a tie is 14.3%);
  - "top 2% of human players" (abstract).
- Against expert human exploiters in repeated play, the human's score fell:
  - from 22.5% against the blueprint
  - to 5.7% against SearchBot (Table 4).
- Ablations:
  - "Even when sampling only two actions, SearchBot dramatically outperforms our blueprint" (Fig. 1 caption).
  - "We do not observe improved performance for rolling out farther than 3 or 4 movement phases" (§3.2).
- Exploitability:
  - an RL exploiter of a SearchBot-clone scores at least 42%;
  - the blueprint's exploiter scores at least 54% (§4.3).

### 3.2 BRBot (same paper)

BRBot "searches over only its own actions" against the blueprint (§4.2). Its numbers are in §1.6: higher against the
modelled blueprint, lower head-to-head.

**This is the closest published analogue of GoA's hybrid.**

### 3.3 Sampled Best Response / BRPI — Anthony et al., NeurIPS 2020 (arXiv 2006.04635) [P]

- SBR evaluates candidate actions from a candidate policy against sampled "base profiles". Every candidate meets the
  *same* opponent samples (§3.1).
- That is GoA's paired, common-random-numbers design (`HybridAi.kt:43-44`).
- The paper uses SBR as the **improvement operator of a policy-iteration loop**, not as the final player. The networks
  are retrained on SBR's choices and SBR is iterated, approximating fictitious play (Alg. 2, §3.2).

### 3.4 DORA — Bakhtin et al., "No-Press Diplomacy from Scratch", NeurIPS 2021 (arXiv 2110.02924) [P]

**Method.** Self-play with value iteration, an RM equilibrium at each state, and a double-oracle step that adds new
candidate actions.

**Two-player (France vs Austria) result.** 78% ± 4% score against top humans after variance reduction, 26 games
(Table 1).

**Still exploitable.**

- Exploiter agents win 90.5% against DORA.
- Inference-time double oracle brings that down to 85.8% (Table 5).
- Inference-time double oracle against an equal model: scores 0.89, 0.59 and 0.56 for Model1–Model3 (Table 4).
  "Stronger agents require better sampling methods to find candidate actions that help."

**7-player result.** DORA and other self-play agents land in **mutually incompatible equilibria**:

- DORA scores about 11% 1v6 against SearchBot; SearchBot scores 1.1% against DORA.
- Two DORA seeds score 3–8% 1v6 against each other (§5.2).

### 3.5 piKL — Jacob et al., ICML 2022 (arXiv 2112.07544) [P]

**Method.** Regret minimization with a KL penalty, weight λ, toward an imitation policy.

**Result.** "λ = 10⁻³ outperforms unregularized search methods in both score (by ~5%) and human prediction accuracy (by
~6%). Mild regularization improves average score, rather than harming it."

In a mixed population of bots (Table 2):

| Agent | Score |
|---|---|
| piKL-HedgeBot | 32.9% |
| RMBot | 31.3% |
| BRBot | 16.1% |
| SearchBot | 13.4% |

### 3.6 DiL-piKL / Diplodocus — Bakhtin et al., ICLR 2023 (arXiv 2210.05492) [P]

A 200-game tournament with 62 humans:

- Diplodocus-High ranked 1st by Elo (181, average 27% ± 4%).
- BRBot was 6th (119).
- DORA was 32nd (−20) (Table 2).

**Cicero** (Science 2022) is UNVERIFIED: I did not open it.

### 3.7 Transferable to GoA (GoA is 2-team zero-sum, so minimax theory applies)

- **(1) The current hybrid is BRBot.** The direct upgrade with published evidence is SearchBot's move:
  - keep the same book-generated candidates and book playouts for the rest of the turn, plus the leaf;
  - also give the enemy a top-k candidate set;
  - fill the payoff matrix;
  - solve it with RM;
  - play a sample from the solution.
- **(2) Two candidates per side were already enough** for a dramatic gain over the blueprint. Short rollouts (2–3
  phases) plus a value net beat longer rollouts. GoA's own rejections are consistent with this: round horizon 32.8%,
  depth-3 tree 43% (CONTEXT).
- **(3) Use exploiters to measure robustness.** An RL exploiter, invited experts, or GoA's existing oracle.
- **(4) The incompatible-equilibria problem is a many-player, general-sum issue.** It matters in GoA only if a human
  plays *alongside* AI teammates, where the AI's conventions must be human-compatible (the piKL argument).

---

## 4. Programmed-card and initiative-card board games

### 4.1 Race for the Galaxy, Keldon Jones's AI [P: source code, `bnordli/rftg` `src/ai.c`]

**How the simultaneous role (action) choice works** (`ai_choose_action`, `ai.c:3979-4270`):

1. A **role-predictor network** (`static net role`, `ai.c:41-43`; inputs built in `predict_action_player`,
   `ai.c:2629-2850`) gives each opponent a probability over actions.
2. Joint opponent combinations are enumerated with their probabilities, dropping those below a threshold. All are kept
   in 2-player games (`ai_choose_action_combo`, `ai.c:3823-3880`; the threshold logic is at `ai.c:4105-4115`).
3. For each own action against each combination, the round is simulated with the AI's greedy sub-choices
   (`complete_turn`) and scored by the evaluation network (`eval_game`, `ai.c:3892-3975`).
4. It plays the **argmax of the probability-weighted score**, a pure best response (`ai.c:4185-4200`).
5. After choosing, it predicts its own action. The softmax `exp(20·score/best)` becomes the role network's training
   target (`ai.c:4225-4270`). So the predictor is trained to imitate what the search does: a self-consistent opponent
   model.

**Strength.** UNVERIFIED. The author's site keldon.net refused connections. A secondary source says it trained on
about 30,000 self-play games and was later used commercially by Temple Gate. Not used.

**Lesson.** RftG has the same "best response to a predicted distribution" as GoA's hybrid. The difference is that its
predictor is a **learned model of the AI's own play, trained on the search's choices**. GoA's predictor is a
hand-written softmax over the book's card scores, never fitted to anyone's actual play.

### 4.2 TAG (Tabletop Games framework, QMUL) [P for the framework docs; agent numbers UNVERIFIED]

- TAG includes Colt Express (programmed cards), Sushi Go and 7 Wonders (simultaneous choice), with OSLA, RHEA and MCTS
  agents (arXiv 2009.12065 §6).
- The EXAG 2020 slides show per-game win rates for Random, OSLA, RHEA and MCTS, including Colt Express. They are only
  a bar chart with no extractable values. UNVERIFIED, not used.

**Goodman & Lucas, "Does it matter how well I know what you're thinking?", arXiv 2006.08659** [P]. This is from the same
group, on a simple RTS with simultaneous actions:

- "RHEA is much more sensitive to the accuracy of an opponent model than MCTS. MCTS generally does better even with an
  inaccurate model".
- With an unknown opponent and a low budget, it is "better not to use any explicit model with RHEA, and to model the
  opponent's actions within the tree as part of the MCTS algorithm" (abstract).

**Lesson.** An explicit, wrong opponent model hurts a planner that best-responds to it. Searching the opponent's
choices too is more robust.

### 4.3 RoboRally, Gloomhaven, Colt Express-specific AIs

No primary AI paper found; one search turned up nothing relevant. No claims made.

---

## 5. RTS combat and MOBA-like AI (simultaneous unit actions)

### 5.1 NaïveMCTS — Ontañón, AIIDE 2013; JAIR 2017 (arXiv 1710.04805) [P]

- It treats a joint multi-unit action as a combinatorial multi-armed bandit. "Naïve sampling" keeps one local bandit
  per unit plus a global bandit over the joint actions already tried.
- "as the branching factor grows, naïve sampling outperforms the other sampling strategies" (abstract).

**Lesson.** For a team's joint card choice (h cards per hero, raised to the number of heroes), keep per-hero marginal
statistics instead of enumerating the product.

### 5.2 Portfolio Greedy Search — Churchill & Buro, CIG 2013

The CIG 2013 PDF failed to download, so the summary below is [P] from Churchill, Lin & Synnaeve, AIIDE-17 WS, which
describes it.

**Method.**

- Units are limited to a small *portfolio of scripts*.
- Start every unit on a seed script.
- For each unit in turn, try each script and keep the best by playout.
- "Steps 3-5 can then be repeated any number of times for both players, improving via self play". This is iterated
  best response over unit-script assignments, at linear rather than exponential cost.

**Results.**

- That paper credits Churchill & Buro 2013 with showing PGS "outperforms other search-based methods in medium and
  large-scale combat scenarios".
- Its own finding: "the performance of PGS relies heavily on the accuracy of the underlying model". PGS wins in the
  real StarCraft engine only where the SparCraft simulator matches it.

**Lesson.**

- GoA's book playbooks are a portfolio, and the team's heroes are the units. Alternating, per-hero improvement for both
  sides is a cheap route to a team joint choice. It is a poor man's double oracle (§1.1).
- GoA has an exact forward model (the engine), so the sim-to-real risk that hurt PGS does not apply. The *opponent
  model* inside playouts is where the model error sits.

### 5.3 microRTS competitions [P: Goodfriend, arXiv 2402.08112]

- "Scripted agents have predominantly won the five previous iterations".
- The winners were CoacAI in 2020 and Mayari in 2021; both scripted.
- RAISocketAI was the first deep-RL winner, at CoG 2023. It needed about 70 GPU-days, 7 policy networks chosen by map,
  and "iteratively fine-tuning … and transfer learning to specific maps were critical".
- The follow-up direction: behaviour cloning of the scripted winners, then RL fine-tuning, "as an efficient way to
  bootstrap".

**Lesson.** Strong hand-written scripts plus search held the top spot for years. Learned policies overtook them only
with large compute and by bootstrapping from the scripts. GoA's book is such a script and could seed a cloned policy.

### 5.4 OpenAI Five, arXiv 1912.06680 [P]

- **Training:** self-play against the latest policy 80% of the time and older policies 20%. A "team spirit" reward
  mix, annealed from 0.3 toward 1.
- **Results:** beat world champions OG. "won 99.4% of over 7000 games" in Arena.
- **Measurement caveat (their footnote 9):** abandoned games count as wins, "3140 of the 7215 wins". The 99.4% is
  therefore inflated by forfeits.
- 29 teams beat it, 42 games in total.

### 5.5 AlphaStar [P-abs: DeepMind blog; the Nature paper was not opened]

- "playing to win is insufficient". The league needs "main agents whose goal is to win versus everyone, and also
  exploiter agents".
- It started from supervised learning: "better than 84% of active players".
- Final result: "above 99.8% of active players on Battle.net".

**Lesson.** Robustness came from training against exploiters built to find the main agent's holes, not from playing
the previous version.

---

## 6. Critical reading of GoA's AI path against this literature

1. **The hybrid is a sampled best response to a hand-made model of the enemy's card that is not fitted to any real
   opponent** (§0). Two pieces of evidence predict how such an agent behaves:
   - Gray et al. Table 3: a best response looks good against the modelled policy and loses head-to-head to
     equilibrium search.
   - Goodman & Lucas: best-responding planners are the ones most hurt by an inaccurate opponent model.

   The benchmark opponent, old s10, plays differently from the book's softmax. So the 65% target measures how well the
   book's softmax happens to describe old s10, plus the deeper search. It does not measure robustness.
2. **The earlier regret-matching test was not the published algorithm.** Gray et al., the AIJ RM variants and
   Foul Play's DUCT all evaluate *the same set of opponent actions* against *every* own candidate, with many samples
   per cell, then solve. ADR-061 did not:
   - its columns were single sampled draws ("each column is one noisy sample", `AI.md:1215`);
   - the enemy's mix was fixed at 0.5 toward the book model.

   The 192-game run could not detect less than about +7 points: its 95% interval was 42.5–56.5%. AI.md itself says
   ~600 games are needed for 0.55 (`AI.md:1177`).

   So "regret matching measured nothing" (`RESULTS.md:386`) is a statement about that implementation and that sample
   size. It is not evidence about root equilibrium search.
3. **Whether mixing matters in GoA was never measured directly.** AIJ 2016 shows the answer depends on how often stage
   games have pure saddle points:
   - Goofspiel needs mixing at the root;
   - Oshi-Zumo, Tron and pursuit-evasion mostly do not.

   GoA's card play is structurally Goofspiel-like: known hands, simultaneous card, deterministic resolution. The test
   is cheap:
   - log full own × enemy payoff matrices at real s10 card plays;
   - solve each exactly (an LP or 10⁴ RM iterations);
   - report the share with a pure saddle point, and the value gap between the pure argmax-mean play and the maximin
     mix.
4. **The hybrid's exploitability is unmeasured.** The old s10 was 88.9% predictable and lost 60.9% to the oracle
   [REPO]. The literature treats exploitability as a first-class metric:
   - SearchBot used human experts and an RL exploiter;
   - DORA used exploiter bots;
   - AlphaStar used exploiter agents in the league.

   A deterministic argmax over the same book will likely be at least as readable as old s10. That is a prediction, not
   a measurement.
5. **The deterministic-opponent argument cuts both ways.** "Every opponent here is deterministic" (`RESULTS.md:386`) is
   exactly the regime where best response beats equilibrium (§1.6). The product's opponents are humans, who adapt.
   Strength 10 has to be measured against old s10 **and** against the oracle exploiter. ADR-061 already set this
   two-part rule for the old search (`AI.md:1212`); the hybrid track dropped it.
6. **Team joint choice.**
   - Farina, Celli, Gatti, Sandholm, NeurIPS 2018 [P-abs]: a team that coordinates *ex ante* against an opponent
     reduces to a two-player zero-sum game over the team's joint strategies.
   - An AI that controls a whole team can therefore treat the team as one player.
   - The obstacle is cost: joint enumeration measured about 4× the work for a team's first seat in 3v3 and 8× in 4v4
     (`AI.md:1184-1200`).
   - NaïveMCTS (per-hero marginal bandits) and PGS (per-hero coordinate ascent, alternating sides) are the published
     ways around the product space.

---

## 7. The five most promising techniques for GoA strength 10, ranked

The budget is a card play of about 10 s or less on the phone. CONTEXT reports W3 at 23 s max in 3v3, or 14.7 s with
`6.cap=12`. That claim comes from agent notes and I did not re-verify it; the other auditors own timings. The costs
below are relative to the current W3 card play.

### 1. Root equilibrium search over book candidates (SearchBot-style), replacing the best response to the book softmax

**What to build.**

- At each card play, take the own hero's cards as rows.
- For columns, enumerate, for each enemy hero still to choose, its top-k_e cards by book prior (k_e = 2–4), as
  *actions*, not draws.
- Each cell is the paired book playout to turn end plus the NET6 leaf.
  - For s10 the hands are known, so before the reveal one determinization per cell often suffices.
  - Below s10, average a few determinizations per cell.
- Solve with RM+ (linear averaging) or an exact LP.
- Play a sample from the solution, or SearchBot's final iterate.

**Evidence.**

- SearchBot over BRBot head-to-head: 17.2% vs 11.1% 1v6 [P].
- Top 2% of humans, and nearly unexploitable by experts (5.7%) [P].
- RM never lost significantly among the samplers in Goofspiel, GoA's closest analogue [P AIJ].
- Even two candidates per side gave a large gain [P].

**Cost.**

- Playouts are k_own × Π k_e cells. For a single enemy column set of about 12 (k_e = 3 over 2 enemies → 9; 3v3 → 27),
  this is in the range of today's cap of 12 columns × k_own. In 3v3 it must be pruned with the double-oracle growth of
  technique 3, or with per-enemy decoupling.
- Solving a matrix of 10×27 or smaller for 10³ iterations is about 3·10⁵ multiply-adds. That is under a millisecond on
  a phone CPU. This is my estimate; ADR-061's solve was "not timed".

**Risk.** It may not move the 65%-vs-old-s10 number at all (§1.6). Judge it with the two-part rule.

### 2. A learned card-choice policy prior / opponent model distilled from the search (RftG, SearchBot blueprint, BRPI, AlphaStar SL, Metamon/PA-Agent, RAISocketAI BC)

**What to build.**

- Train a small net on the 248-feature input that already exists (`ai/learned/Features`) to predict the s10 search's
  *own* card choices, plus, where available, old s10's and humans'.
- Use it for (a) the top-k candidates of technique 1, (b) the enemy-card model inside playouts in place of the book
  softmax, and (c) a piKL anchor if mixing is adopted.
- Iterating search → distil → search is BRPI's policy iteration.

**Evidence.**

- RftG predicts opponents' roles with a net trained on its own search's choices [P code].
- SearchBot needs a good blueprint for its candidates and rollouts [P].
- DORA: "Stronger agents require better sampling methods to find candidate actions" [P].
- Metamon and PA-Agent reached top-10% humans with distilled or offline-RL policies alone [P].

**Cost.**

- Offline training on oldboi; the self-play data generation is the expensive part.
- On the phone, one small-MLP forward per enemy hero per determinization. That is negligible next to playouts.

**Risk.** A prior trained on old s10 games overfits the benchmark. Keep a human-data or self-play mix.

### 3. Double-oracle / PGS-style iterated best response for the team's joint card choice

**What to build.**

- Start both teams on the book's card for every hero.
- Alternately, per side and per hero, add the card that best responds to the current restricted mix (PGS steps 3–5;
  DOαβ's restricted-game loop).
- Stop when no side gains. Solve the restricted matrix (technique 1).

**Evidence.**

- DOαβ is "several orders of magnitude faster" than full backward induction, because supports stay small [P AIJ].
- PGS beat alpha-beta and UCT in large combats by linear per-unit improvement [P via the AIIDE-17 description].
- Inference-time double oracle cut DORA's exploitability from 90.5% to 85.8% and helped weaker models a lot (0.89)
  [P].
- NaïveMCTS is the bandit alternative for large joint spaces [P].

**Cost.** Each oracle step is one hero's cards × the current opposing support. Typically 2–4 iterations, so about
O(heroes × h × support) playouts. This keeps 3v3 and 4v4 within about the cost of today's per-hero search, unlike the
measured 4–8× of full joint enumeration.

### 4. A better leaf rather than a longer horizon: short rollouts plus a value net trained on search-backed values

**What to build.** Keep the turn-end horizon and invest in the leaf. This is the E/E2 track: NET6 and a net trained on
search values.

**Evidence.**

- SearchBot found no gain past 3–4 movement phases with a value net at the leaf [P].
- AIJ: DOαβ with a good evaluation beats knowledge-free samplers, and with a weak evaluation it loses. With evaluation
  functions, every sampler improved [P].
- Foul Play replaced rollouts with an evaluation function [P code].
- GoA's own longer-horizon attempts lost (CONTEXT; not re-verified by me).

**Cost.** Leaf inference only, already paid by NET6. Training is offline.

### 5. Deliberate mixing at the card play: sample among near-best cards (Foul Play's ≥75%-of-best rule), RM's mix, or piKL toward the prior

**Evidence.**

- Foul Play, the 2025 champion, samples among near-best root moves [P code].
- SearchBot plays a sample from its RM policy [P].
- piKL: mild KL regularization raised score by about 5% over unregularized RM or Hedge in Diplomacy [P].
- RNR: restricted Nash responses beat best responses on robustness [P-abs].
- GoA's own oracle: about 89% predictability of old s10's card was worth 60.9% to a reader [REPO].

**Cost.** Zero playouts.

**Risk.** It probably costs a little against the fixed old s10, since against a deterministic bot the best response is
optimal. Its value shows only in the exploitability half of the two-part gate.

### Prerequisite for all five (measurement, not a technique)

Adopt the literature's two metrics:

- **win rate against old s10**, with power for ±3 points: about 1000+ mirrored games, as in AIJ's "≥1000 matches per
  pair";
- **win rate conceded to an exploiter**: the existing `oracle/` harness pointed at the hybrid, and ideally a
  best-response search with the true s10 policy as its model.

Also log root payoff matrices to measure how often GoA's card play has a pure saddle point (§6.3). That one diagnostic
decides whether technique 1 or technique 5 can matter at all.

---

## Sources opened (local copies in `scratchpad/audit/r1papers/`)

- **Bošanský et al., AIJ 2016.** Preprint: https://dke.maastrichtuniversity.nl/m.winands/documents/sm-journal.pdf (`sm-journal.txt`).
- **Lisý et al., NIPS 2013.** https://arxiv.org/abs/1310.8613 (`lisy2013.txt`).
- **Kovařík & Lisý.** https://arxiv.org/abs/1804.09045 (`kovarik2018.txt`).
- **Lanctot et al., Tron.** https://dke.maastrichtuniversity.nl/m.winands/documents/sm-tron-bnaic2013.pdf (`sm-tron.txt`).
- **Tak, Lanctot, Winands, CIG 2014.** Abstract only: https://cris.maastrichtuniversity.nl/portal/en/publications/monte-carlo-tree-search-variants-for-simultaneous-move-games(5e4c8118-4db7-49bf-b12d-d16ee90fe167).html
- **Johanson, Zinkevich, Bowling, NIPS 2007.** Abstract: https://proceedings.neurips.cc/paper/2007/hash/6e7b33fdea3adc80ebd648fffb665bb8-Abstract.html
- **Gray et al., ICLR 2021.** https://arxiv.org/abs/2010.02923 (`dip_searchbot.txt`).
- **Anthony et al., NeurIPS 2020.** https://arxiv.org/abs/2006.04635 (`dip_brpi.txt`).
- **Bakhtin et al., NeurIPS 2021.** https://arxiv.org/abs/2110.02924 (`dip_dora.txt`).
- **Jacob et al., ICML 2022.** https://arxiv.org/abs/2112.07544 (`dip_pikl.txt`).
- **Bakhtin et al., ICLR 2023.** https://arxiv.org/abs/2210.05492 (`dip_dilpikl.txt`).
- **Farina, Celli, Gatti, Sandholm, NeurIPS 2018.** Abstract: https://proceedings.neurips.cc/paper_files/paper/2018/file/c17028c9b6e0c5deaad29665d582284a-Metadata.json
- **Foul Play.** https://github.com/pmariglia/foul-play (`repos/main.py`).
- **poke-engine.** https://github.com/pmariglia/poke-engine (`repos/mcts.rs`, `repos/poke-engine-README.md`).
- **PokéAgent Challenge.** https://arxiv.org/abs/2603.15563 (`pokeagent.txt`).
- **Metamon.** https://arxiv.org/abs/2504.04395 (`poke_metamon.txt`).
- **PokéChamp.** https://arxiv.org/abs/2503.04094 (`poke_champ.txt`).
- **PokéLLMon.** https://arxiv.org/abs/2402.01118 (`poke_llmon.txt`).
- **Race for the Galaxy AI.** https://github.com/bnordli/rftg (`rftg/ai.c`, `rftg/README`).
- **TAG.** https://arxiv.org/abs/2009.12065 (`tag.txt`); EXAG slides https://rdgain.github.io/assets/pdf/gaina2020tag.pdf (`tag_aiide.txt`, chart values not extractable).
- **Goodman & Lucas.** https://arxiv.org/abs/2006.08659 (`oppmodel.txt`).
- **Ontañón, JAIR 2017.** https://arxiv.org/abs/1710.04805 (`naive_jair.txt`).
- **Churchill, Lin, Synnaeve, AIIDE-17 WS.** https://cdn.aaai.org/ojs/12962/12962-52-16479-1-2-20201228.pdf (`abcd_analysis.txt`).
- **Goodfriend.** https://arxiv.org/abs/2402.08112 (`microrts_drl.txt`).
- **OpenAI Five.** https://arxiv.org/abs/1912.06680 (`openai5.txt`).
- **AlphaStar blog.** https://deepmind.google/discover/blog/alphastar-grandmaster-level-in-starcraft-ii-using-multi-agent-reinforcement-learning/ (abstract-level only).

**Not opened, UNVERIFIED, not used in conclusions:**

- Lanctot–Lisý–Winands Goofspiel CGW 2013 full text
- Churchill & Buro CIG 2013 PDF (download timed out)
- Cicero (Science 2022)
- AlphaStar Nature paper
- keldon.net (connection refused)
- VGC AI competition
- TAG per-game agent numbers
