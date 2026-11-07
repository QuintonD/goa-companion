#!/bin/sh
# Free disk after every judge round and every merge (oldboi ran out of disk mid-round: stalled builds, "Disk quota
# exceeded"). Safe to run any time:
#  - only the worktrees you name (--worktree <path>, repeatable) are removed, and only when clean AND merged into the
#    integration branch (a fresh agent worktree with no commits counts as "merged", so never sweep them all);
#  - build outputs are touched only while holding the build lock, so a running build is never cut short;
#  - judge scratch (packed and composite images) of finished rounds is dropped, verdict JSON is kept.
# Usage: tools/ui-hillclimb/cleanup.sh [--branch feature/ui-hillclimb] [--scratch <dir>] [--worktree <path>]...
repo="$(git rev-parse --show-toplevel)" || exit 1
cd "$repo" || exit 1
branch=feature/ui-hillclimb; scratch=""; worktrees=""
while [ $# -gt 0 ]; do
  case "$1" in
    --branch) branch="$2"; shift 2 ;;
    --scratch) scratch="$2"; shift 2 ;;
    --worktree) worktrees="$worktrees $2"; shift 2 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done
before=$(df -h / | awk 'NR==2{print $4}')

# 1. Merged, clean worktrees.
for w in $worktrees; do
  [ -d "$w" ] || { echo "no worktree $w"; continue; }
  b=$(git -C "$w" branch --show-current)
  if [ -n "$(git -C "$w" status --porcelain)" ]; then echo "keep $w (uncommitted work)"; continue; fi
  if ! git merge-base --is-ancestor "$b" "$branch" 2>/dev/null; then echo "keep $w ($b not merged)"; continue; fi
  git worktree remove -f -f "$w" && echo "removed $w ($b merged)"
done
git worktree prune

# 2. Build leftovers, only when no build holds the lock.
lock="$HOME/.cache/goa-hillclimb-build.lock"
mkdir -p "$HOME/.cache"
if flock -n "$lock" true; then
  flock -n "$lock" sh -c '
    # Robolectric native-runtime copies and other JVM temp (build.sh points java.io.tmpdir here).
    find "$HOME/.cache/goa-build-tmp" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null
    find /tmp -maxdepth 1 -user "$(id -u)" \( -name "robolectric*" -o -name "android-tmp*" \) -exec rm -rf {} + 2>/dev/null
    # Roborazzi intermediates (regenerated on the next record/verify).
    find "'"$repo"'" -path "*/build/intermediates/roborazzi" -prune -exec rm -rf {} + 2>/dev/null
    find "'"$repo"'" -path "*/build/outputs/roborazzi" -prune -exec rm -rf {} + 2>/dev/null
    # Local build-cache entries nobody has touched for 6 hours (old worktree states are never reused).
    find "$HOME/.gradle/caches/build-cache-1" -maxdepth 1 -type f -mmin +360 -delete 2>/dev/null
  '
  echo "build leftovers cleared"
else
  echo "a build holds the lock: build leftovers kept"
fi

# 3. Judge scratch: composites are re-creatable; keep manifests and verdicts.
if [ -n "$scratch" ] && [ -d "$scratch" ]; then
  find "$scratch" \( -name "packed" -type d \) -prune -exec rm -rf {} + 2>/dev/null
  find "$scratch" -name "cmp-*.jpg" -delete 2>/dev/null
fi

echo "free on /: $before -> $(df -h / | awk 'NR==2{print $4}')"
