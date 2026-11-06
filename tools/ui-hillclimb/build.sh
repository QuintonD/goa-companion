#!/bin/sh
# goa-gradle for the checkout you are in (a worktree too), one build at a time on this machine:
# parallel hill-climb agents queue on the lock instead of starting builds side by side (oldboi, ADR-038).
# Usage: tools/ui-hillclimb/build.sh <gradle tasks...>
root="$(git rev-parse --show-toplevel)" || exit 1
[ -f "$root/local.properties" ] || cp "$HOME/goa-companion/local.properties" "$root/local.properties" 2>/dev/null
mkdir -p "$HOME/.cache"
echo "[build.sh] waiting for the build lock ($root)" >&2
exec flock "$HOME/.cache/goa-hillclimb-build.lock" env GOA_DIR="$root" goa-gradle "$@"
