#!/bin/sh
# Expert iteration, one round (Wave 4 Track H; RESULTS.md "Wave 4 — Track H"): the search plays games and records its
# own backed-up values (the expert), the leaf nets are fine-tuned on them (the apprentice), and the new nets are checked
# OFFLINE on held-out positions against the search's values and the results. No tournament: whether the new leaf
# plays better is the integrator's one measurement.
#
# Usage, from the root of a checkout (after `goa-run --compile`):
#   tools/ai/expert-iteration/round.sh <round-dir> [selfplay] [train] [dump] [offline]     (no stage: all four)
#
# Environment (defaults in brackets):
#   SPEC      the search that plays and is recorded [W2: AiPlayers.SEARCH_10 with SEARCH_10_LEAF, what ships]
#   GAMES     self-play games [600; about 0.07 games a second at 3 threads, so ~2.5 hours]
#   FIRST     first game number [20000]: training line-ups (Teams.TRAIN), never the tournament line-ups 4000-5999
#   SIZES     team sizes [2*1,3*1]
#   PREV      earlier rounds' search games and values, as "games.jsonl:values.tsv,…" [Track E's games with E2's values]
#   BOOK      the book's self-play games, comma list [Track E's book-a/b/c]
#   INIT      the nets to fine-tune, one per seed, comma list [g1,g2,g3: NET6's newer half; round 1 adopted none]
#   SV_ONLY   1: train and early-stop on the searched positions only, stopping on how well the net imitates the
#             search (round 1's run 2); 0: every position, stopping on the results (run 1) [1]
#   NAME      prefix of the new nets [ei<round-dir basename>]; nets <NAME>1..3 land in $NETS
#   LAMBDA    weight of the game result in a searched position's target [0.2, RESULTS.md "Wave 4 — Track H"]
#   SV_SCALE  coin-equivalents per logit to read the search's values [37.852: the six-net leaf's mean coin scale]
#   SV_CAP    cap on that logit; "fit" = NET6's 99th-percentile |logit| on the training split's searched positions
#             [fit; 3.28 in round 1]: a playout that wins inside the turn scores thousands of coin-equivalents
#   TEACHER   the λ-return teacher of the book positions [avg(cur1,cur2,cur3,g1,g2,g3)]
#   NETS      nets directory [~/.goa-qa/trackH/nets]; CACHE features cache [~/.goa-qa/trackH/cache]
#   GOA_RUN   the run gate [the swarm's goa-run]
#
# Stages:
#   selfplay  LearnedSelfPlayTest with AI_SV_LIVE: the games ($R/sp-<round>.jsonl) and the search's values,
#             recorded while the games are played (same rows as SearchValueTest writes after the fact).
#   train     LearnedTrainTest, one JVM, three seeds: book positions keep λ-returns (λ 0.7) from TEACHER; positions
#             the search scored get LAMBDA · result + (1 − LAMBDA) · σ(value / SV_SCALE).
#   dump      LearnedTrainTest "dump held-out logits": every held-out position (line-up % 10 = 9) and every searched
#             position, with each net's logit ($R/dump.tsv).
#   offline   tools/ai/expert-iteration/offline.py: the tables ($R/offline.md).
# Adopt the new nets only if, on the held-out SEARCHED positions (the leaf's own distribution, first table of
# offline.md), the ensemble with them (cur+NEW in place of NET6) has a Δ log-loss interval below 0 and a lower RMS to
# the search's values. A gain on the book's positions alone is not a better leaf (Track E, V3-history §2.3.2).
# Then write them as weights (one goa-run each: AI_WRITE_NET=<name>:src/main/kotlin/dev/quinton/goa/core/game/ai/
# LearnedWeightsE1.kt LearnedTrainTest, from core/game), add a HybridLeaf entry over them like NET6, and leave the
# measurement in games to the integrator. Round 1 (2026-11-24) adopted nothing; see RESULTS.md "Wave 4 — Track H".
set -eu
[ $# -ge 1 ] || { sed -n '2,40p' "$0"; exit 2; }
R=$(cd "$(dirname "$1")" && pwd)/$(basename "$1"); shift
mkdir -p "$R"
ROOT=$(git rev-parse --show-toplevel)
Q=${Q:-$HOME/.goa-qa}
E=$Q/trackE/data
GOA_RUN=${GOA_RUN:-/tmp/claude-1000/-home-quinton-goa-companion/bb386302-b635-4d0f-906f-1e109ea75466/scratchpad/goa-run}
SPEC=${SPEC:-W2}
GAMES=${GAMES:-600}
FIRST=${FIRST:-20000}
SIZES=${SIZES:-2*1,3*1}
PREV=${PREV:-$E/x-w-vs-w2.jsonl:$Q/trackE2/data/sv-x.tsv,$E/x-g-vs-w2.jsonl:,$E/x-g6-vs-w2.jsonl:,$E/x-g6-vs-w2-b.jsonl:,$E/x-g6-vs-w2-3v3.jsonl:}
BOOK=${BOOK:-$E/book-a.jsonl,$E/book-b.jsonl,$E/book-c.jsonl}
INIT=${INIT:-g1,g2,g3}
SV_ONLY=${SV_ONLY:-1}
NAME=${NAME:-ei$(basename "$R")}
LAMBDA=${LAMBDA:-0.2}
SV_SCALE=${SV_SCALE:-37.852}
SV_CAP=${SV_CAP:-fit}
TEACHER=${TEACHER:-avg(cur1,cur2,cur3,g1,g2,g3)}
NETS=${NETS:-$Q/trackH/nets}
CACHE=${CACHE:-$Q/trackH/cache}
STAGES=${*:-selfplay train dump offline}
case "$FIRST" in 4???|5???) echo "FIRST $FIRST: the tournament line-ups 4000-5999 are reserved" >&2; exit 2 ;; esac

# This round's files, named after the round: the trainer keys search values by the games file's name.
G=$R/sp-$(basename "$R").jsonl
V=$R/sp-$(basename "$R").sv.tsv
# The games and values of every round so far plus this one.
games=$BOOK
values=""
for p in $(echo "$PREV" | tr ',' ' '); do
  games="$games,${p%%:*}"
  v=${p#*:}; [ -n "$v" ] && values="${values:+$values,}$v"
done
if [ -s "$G" ]; then games="$games,$G"; values="${values:+$values,}$V"; fi

run() { (cd "$ROOT" && nice -n 10 "$GOA_RUN" "$@"); }

for stage in $STAGES; do
  case $stage in
  selfplay)
    [ -s "$G" ] && { echo "selfplay: $G exists, skipped" >&2; continue; }
    AI_SELFPLAY="$GAMES:$G" AI_FIRST=$FIRST AI_THREADS=3 AI_SIZES="$SIZES" AI_VARIANTS="$SPEC*1" \
      AI_SV_LIVE="$V" AI_NETS="$NETS" \
      run dev.quinton.goa.core.game.ai.learned.LearnedSelfPlayTest > "$R/selfplay.log" 2>&1
    games="$games,$G"; values="${values:+$values,}$V"
    ;;
  train)
    cap=$SV_CAP
    if [ "$cap" = fit ]; then
      # NET6's confidence on the searched positions of the training split (a dump with the six nets only).
      AI_DUMP="$R/fit.tsv" AI_DUMP_FILES="$games" AI_SV="$values" AI_DUMP_NETS=cur1,cur2,cur3,g1,g2,g3 \
        AI_NETS="$NETS" AI_CACHE="$CACHE" AI_RESOLVED=0 AI_THREADS=3 \
        run dev.quinton.goa.core.game.ai.learned.LearnedTrainTest > "$R/fit.log" 2>&1
      cap=$(python3 -I "$ROOT/tools/ai/expert-iteration/offline.py" "$R/fit.tsv" --nets-dir "$NETS" --print-cap)
    fi
    echo "train: search values σ(v / $SV_SCALE) capped at ±$cap, λ $LAMBDA" | tee "$R/train.settings"
    runs=""
    i=0
    for init in $(echo "$INIT" | tr ',' ' '); do
      i=$((i + 1))
      runs="$runs|$NAME$i:AI_INIT=$init;AI_SEED=$i"
    done
    AI_TRAIN="$games" AI_SV="$values" AI_SV_SCALE=$SV_SCALE AI_SV_CLAMP=$cap AI_SV_LAMBDA=$LAMBDA AI_SV_ONLY=$SV_ONLY AI_TEACHER="$TEACHER" AI_LR=1e-4 \
      AI_NETS="$NETS" AI_CACHE="$CACHE" AI_RESOLVED=0 AI_THREADS=3 AI_TRAIN_RUNS="$runs" \
      run dev.quinton.goa.core.game.ai.learned.LearnedTrainTest > "$R/train.log" 2>&1
    ;;
  dump)
    nets=cur1,cur2,cur3,g1,g2,g3
    for init in $(echo "$INIT" | tr ',' ' '); do case $init in cur*|g?) ;; *) nets="$nets,$init" ;; esac; done
    i=0
    for _ in $(echo "$INIT" | tr ',' ' '); do i=$((i + 1)); nets="$nets,$NAME$i"; done
    AI_DUMP="$R/dump.tsv" AI_DUMP_FILES="$games" AI_SV="$values" AI_DUMP_NETS="$nets" AI_NETS="$NETS" \
      AI_CACHE="$CACHE" AI_RESOLVED=0 AI_THREADS=3 \
      run dev.quinton.goa.core.game.ai.learned.LearnedTrainTest > "$R/dump.log" 2>&1
    ;;
  offline)
    ens="--ens NEW=${NAME}1+${NAME}2+${NAME}3 --ens cur+NEW=cur1+cur2+cur3+${NAME}1+${NAME}2+${NAME}3"
    ens="$ens --ens NET6+NEW=cur1+cur2+cur3+g1+g2+g3+${NAME}1+${NAME}2+${NAME}3"
    # shellcheck disable=SC2086
    python3 -I "$ROOT/tools/ai/expert-iteration/offline.py" "$R/dump.tsv" --nets-dir "$NETS" $ens --members \
      --sv-scale "$SV_SCALE" $( [ "$SV_CAP" = fit ] || echo "--sv-cap $SV_CAP" ) \
      > "$R/offline.md"
    echo "offline: $R/offline.md"
    ;;
  *) echo "unknown stage $stage" >&2; exit 2 ;;
  esac
done
