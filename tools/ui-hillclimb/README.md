# UI/UX hill climb

Two independent judges score the app's UI and UX from its Roborazzi screenshots; work packages fix what they
find; a change is kept only when the judges prefer it side by side; repeat until **both judges give 9/10 or
more on every aspect**.

- **Judges:** `gpt-6.1-sol` (reasoning effort high, Azure AI Foundry, `hc.py judge`) and Claude Opus 5.5
  (effort high, a Claude Code agent reading the same images). Same brief (`rubric.md`), same corpus
  (`corpus.tsv`: about 70 screens in flow order with captions), blind to earlier rounds.
- **Aspects (12):** hierarchy, aesthetic, art_assets, typography, color_contrast, information, affordance,
  navigation, feedback, accessibility, table_fitness, consistency.
- **Loop:** `collect` → both judges score → issues merged into work packages on disjoint files → one agent
  per package in its own worktree (builds queue on `build.sh`'s lock: oldboi runs one build at a time) →
  `compare` before/after (both judges, random left/right) → merge the winners → `cleanup.sh` (merged worktrees, build
  leftovers, judge scratch) → re-record every screenshot → `cleanup.sh` again → next round.
- **Art:** official assets first (`app/src/main/assets/official/`, `official/SOURCES.md`); redraw a frame that is
  too soft (ADR-058); generated painted art (`hc.py imagegen`, `gpt-image-2.5`) only for the app's own decorative
  art, never for anything that carries game data (cards, portraits, symbols, boards). Record every generated
  file with its prompt in `third_party/ASSETS.md`.

The API key lives in `~/.config/goa-hillclimb/azure_key` (or `$GOA_HC_KEY_FILE`); never commit it. Run state (corpora,
verdicts, gate pairs, `history.md`) lives on disk in `~/goa-hillclimb/`: not in a session scratchpad, whose `/tmp` is a
tmpfs a host restart wipes.

```
python3 -I tools/ui-hillclimb/hc.py collect <dir>/corpus [--root <checkout>] [--only id,id]
python3 -I tools/ui-hillclimb/hc.py judge <dir>/corpus <dir>/score-sol.json
python3 -I tools/ui-hillclimb/hc.py blind <before>/corpus <after>/corpus <cmp> --only id,id   # hidden X|Y order
python3 -I tools/ui-hillclimb/hc.py compare <cmp> <cmp>/sol.json --note "..."
python3 -I tools/ui-hillclimb/hc.py unblind <cmp> <cmp>/opus-raw.json <cmp>/opus.json claude-opus-5-5  # after the Opus agent
python3 -I tools/ui-hillclimb/hc.py imagegen out.png "prompt" --size 1536x1024 [--ref style.png]
python3 -I tools/ui-hillclimb/hc.py board <runs_dir>
tools/ui-hillclimb/build.sh :feature:timer:recordRoborazziDebug
tools/ui-hillclimb/build.sh --max-workers=1 recordRoborazziDebug --continue   # whole app: one worker, or it OOMs at the 5 GB cap
tools/ui-hillclimb/cleanup.sh --scratch ~/goa-hillclimb --worktree <merged worktree>   # after every judge round and merge
```
