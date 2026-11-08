---
name: sol-review
description: Independent review by gpt-6.1-sol (OpenAI, via the project's Azure AI Foundry key) of a code change, a plan, or UI screenshots, in a short fix-and-re-review loop. Use when the user says "sol review", "sol loop", "council", "second opinion", or asks for GPT/Sol to review; also for UI judging from screenshots. Council mode adds a Claude Opus subagent so two providers score the work.
---

# Sol review

Adapted from Theo Browne's `council` and `sol-loop` skills (t3dotgg/tstack, MIT). Sol is reached through
`tools/ai/azure_ai.py`, not a T3 provider, so it works in any harness on a machine with the secrets file.

## Before you start

- **Credentials:** `~/.config/goa-companion/secrets/azure-ai.env` (see `tools/ai/azure-ai.env.example`). If it's missing,
  stop and tell the user. Never paste the key into a prompt, a file in the repo, a commit or a PR.
- **Rate limit:** gpt-6.1-sol allows **333,000 tokens per minute**, shared by every process on the machine. The client
  waits when a minute's budget is used and, on HTTP 429, **waits a minute and retries**. Don't add your own retry
  loop, don't start parallel Sol requests to "go faster", and keep each request well under ~250k tokens (split a huge
  diff by paths).
- **Image limit per request:** at most 50 images (pack two screenshots per image if you need more; see
  `tools/ui-hillclimb/hc.py packed`).
- Address known issues and run the relevant checks first. Review the code you intend to ship, not a draft.

## 1. Scope

`git status --short`: don't stash, discard or include unrelated changes. Find the base: a PR's
`git merge-base HEAD origin/<baseRefName>`, else `git merge-base HEAD origin/main`, else what the user names. The change is
base → working tree (untracked files included). State the intent and what's out of scope in 1–2 sentences.

## 2. Ask Sol (round 1)

Build the brief in a temp file. Sol can't read the repo, so include the diff and any surrounding code it needs:

```
Review only. You cannot run code.
Project: Guards of Atlantis companion (Android, Kotlin/Compose; rules in docs/DOMAIN.md must stay exact).
Intent: <intent>  Out of scope: <scope>  Checks run: <results>
Find bugs, broken callers, mismatches with the intent. Skip style unless it hides a bug. Hold to the smallest change.
Flag code, tests or guards the intent does not need. Rare cases, small perf, extra compat: nits.
blocker = the intended case is wrong. should = a real bug callers will hit. nit = the rest.
Reply:
VERDICT: APPROVE or CHANGES
SCORE: <n>/10
S1 [blocker|should|nit] <file>:<line> <claim>. Fix: <suggestion>   (stable ids)
--- diff ---
<git diff <base> -- <paths>>
--- context ---
<relevant files or excerpts>
```

```
python3 -I tools/ai/azure_ai.py ask --effort high --prompt-file <brief.txt> --out <reply.txt>
```

For a **UI review** add `--image <screenshot>` per screen (Roborazzi PNGs, JPEG-compressed if large) and ask for the
judge brief in `tools/ui-hillclimb/rubric.md` instead of the code brief.

## 3. Answer findings

Check each finding and its severity yourself. Another path, rare case or small cost is a nit, whatever its label.
- **Agree:** make the smallest fix, preferring to delete or narrow over adding a lock, cache, helper or option. Add one
  focused test only if the intent needs it.
- **Disagree:** say why, with evidence (file:line, a test, output).
- **Out of scope or nit:** decline in one line and list it as a follow-up.

Keep a ledger: id, severity, outcome (fixed / disagree / declined), and token usage from the reply. Don't guess numbers.
Re-run the checks. If review grew the diff, cut back first.

## 4. Next round

Budget: **3 rounds**. Extend to 5 only for rules accuracy, data loss, sync/state or security while a blocker or should
stays open with evidence. Stop early when a round returns only nits. Later rounds send the round-1 brief, the ledger,
and the change since the last round, and ask Sol to mark each old id `resolved` or `still open: <reason>`.

## 5. Stop and report

Agreed means APPROVE on the current diff, with no blocker or should open and no concern of your own. If an item stays
disputed after two replies, or the budget ends, show both sides in 2–3 lines and let the user decide. Budget end is
not approval. Report: rounds, model, scores per round, what changed, fixed and declined findings with reasons, and
token usage.

## Council mode ("council")

Two providers, each scoring out of 10, until both are satisfied:
- **gpt-6.1-sol** through the steps above.
- **Claude Opus** as a read-only subagent (`Agent`, `model: opus`, `effort: high`) with the same brief and access to
  the repo. Tell it not to edit, commit or start subagents.

Run them in parallel, merge the findings, fix, and run a fresh round on the new code. If the user names a third member
(e.g. Gemini) and the harness can't reach it, say so; don't substitute a model. Fight scope creep across rounds. If the
work ends in a PR, add this before the model line in its description (no score from a member: write `no score`):

```markdown
## Council review

Rounds: 2

| Delegate | Round 1 | Round 2 |
| --- | --- | --- |
| Claude Opus | 7/10 | 9/10 |
| GPT Sol (6.1) | 8/10 | 9/10 |
```
