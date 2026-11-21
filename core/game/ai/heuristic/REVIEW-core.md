# Review ledger: the general book (gpt-6.1-sol, 2026-11-21)

Independent review of `ai/heuristic/` (not `heroes/`), verdict CHANGES 6/10. Each finding was checked against the
code, the card text and the engine before it was changed. Tests: `HeuristicReviewCoreTest` (one case per finding).

| Id | Reviewer | Ours | Outcome | Why | Commit |
|----|----------|------|---------|-----|--------|
| C1 | blocker | should | fixed | Agreed: a look answered an enemy's forced discard (and, in `Lookahead.finish` and the teammate prelude, an enemy's defense) from the sampled hand. `Lookahead.hiddenAnswer` now ends every look there; `pooled` prices it from the pool (strike on a defense, `discardWorth` on a discard); the G14 prelude is skipped when the teammate's action needs such an answer. | a3bd2215 |
| C2 | blocker | minor | fixed | Agreed: `expectedAttacks` read a facedown RESOLVED card (engine `turnFaceDown`, Cards.kt; resampled by the Determinizer). Rare (only effects that turn cards facedown), so minor; both zones now need `!faceDown`. | 185dd94a |
| C3 | should | should | fixed | Agreed: after the reveal `possibleCards` carries the played card (`playingNow`), which has left the hand; `defensesOf` and `defensePool` counted it as a defense (Arien's revealed Blue, defense 5, hid a sure kill). Both now drop `playingNow`. | 185dd94a |
| C4 | should | should | fixed | Agreed: before the reveal, a hero that already played facedown with one card left still defends with it this turn; `heroIsDefenseless` now uses only this turn's play (`playsThisTurn`). | 185dd94a |
| C5 | should | should | fixed | Agreed: `goalZoneOf` read `laneMargin` from `ctx.team` and the push direction from `h.team`, so an enemy hero's goal zone pointed the wrong way. The margin is now from `h.team`. | 185dd94a |
| C6 | should | minor | fixed | Agreed: `kills` called an empty hand defeated by a "discards a card, if able" card (engine `forceDiscard`: no card, no effect). The attack/discard-or-defeat test now comes first, and "or is defeated" with a card in hand is a discard. The same error in `strikeFrom` (a discard-only card priced by `heroStrikeWorth`) now uses `discardStrike`. | 185dd94a |

## Left as they are (noted, not in a finding)

- An enemy decision inside a look that is not a defense or a card choice (rare: an enemy "may" answer to our action)
  is still answered by that enemy's book from its own view; pricing those needs a viewer-aware context, not a one-line
  rule. Brogan's "discard so it is not removed?" is a hidden card choice: it now ends the look, unpriced (0).
- The engine itself resolves some hidden-card answers against the sampled hand inside a look without asking: a
  question with one option (`Game.ask`, `auto`: a forced discard of the last card in hand) and "discards a card of
  that colour, or is defeated" when no card matches. The look cannot see those without engine help.
- `strikeFrom` still prices a non-attack card that neither attacks nor forces a discard (`reach > 0`) through
  `heroStrikeWorth`.

## Measurement

`AI_H_TOURNAMENT=h10:s8 AI_H_GAMES=96 AI_H_FIRST=1000` (192 mirrored games, development line-ups):

| Code | h10 vs s8 | 95% Wilson | h10 answer mean / p95 / max |
|------|-----------|------------|------------------------------|
| before (88280d5c) | 103-89, 53.6% | 46.6-60.6% | 3.29 / 10.98 / 337 ms |
| after (a3bd2215) | 107-85, 55.7% | 48.7-62.6% | 3.27 / 10.76 / 340 ms |

No drop (the difference is inside the noise).

## Round 2 (gpt-6.1-sol, 7/10, CHANGES)

25 of 29 round-1 findings resolved, including both disagreements (H3, H2-03). Open after round 2, and what was done:

| Id | Reviewer | Outcome | Reason |
|---|---|---|---|
| H2-04 | still open | fixed | Snorri's horn threat: a minion opens the repeat only when a non-handicap gold may be held and it is not an immune Heavy. |
| H2-05 | still open | fixed | Whisper's Blood Fury: a first target with no card to defend with is defeated and leaves, so its own discard no longer primes the repeat. |
| R2-2 | should | fixed | `strikeFrom` gives no hero-strike value to a card that neither attacks nor forces a discard. |
| C1 (rest), R2-1 | blocker | declined, follow-up | Inside a one-ply look the engine can still settle an enemy's response by itself (a forced discard of the last card, "discard a card of that colour or be defeated" with no match), and other rare enemy questions are answered from that enemy's sampled view. This is not a leak of the real hidden cards (the copy is sampled from public knowledge; `NoPeekingTest` holds): it is the bias of reasoning on one determinized sample. Removing it needs an engine-level "stop before a hidden-dependent resolution" hook in `EngineSession`, shared with the web build — out of this round's scope. Recorded as a follow-up in ADR-085. |
